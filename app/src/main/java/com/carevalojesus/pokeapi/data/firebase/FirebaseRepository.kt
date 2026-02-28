package com.carevalojesus.pokeapi.data.firebase

import com.carevalojesus.pokeapi.data.repository.UnlockRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.util.UUID

enum class AppUserRole {
    ADMIN,
    TRAINER
}

data class CampaignQrData(
    val campaignId: String,
    val campaignName: String,
    val qrPayload: String
)

data class InventoryCount(
    val pokemonId: Int,
    val count: Int
)

data class TrainerWithInventory(
    val uid: String,
    val displayName: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val birthDate: String,
    val gender: String,
    val inventory: List<InventoryCount>,
    val ownedPokemonCount: Int = 0,
    val unlockedPokemonCount: Int = 0,
    val points: Int = 0
)

data class CampaignInfo(
    val campaignId: String,
    val name: String,
    val active: Boolean,
    val rewardCount: Int,
    val createdAt: com.google.firebase.Timestamp?,
    val claimCount: Int,
    val qrPayload: String = ""
)

data class AppNotification(
    val id: String,
    val title: String,
    val message: String,
    val type: String,
    val read: Boolean,
    val createdAt: com.google.firebase.Timestamp?
)

sealed interface ClaimRewardResult {
    data class Success(val rewardIds: List<Int>) : ClaimRewardResult
    data object AlreadyClaimed : ClaimRewardResult
    data class Error(val message: String) : ClaimRewardResult
}

class FirebaseRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    companion object {
        private const val TRAINERS = "trainers"
        private const val CAMPAIGNS = "campaigns"
        private const val CLAIMS = "claims"
        private const val NOTIFICATIONS = "notifications"
    }

    suspend fun resolveCurrentUserRole(): AppUserRole? {
        val user = auth.currentUser ?: return null
        val trainerDoc = firestore.collection(TRAINERS).document(user.uid).get().await()
        val role = trainerDoc.getString("role")?.lowercase()
        return if (role == "admin") AppUserRole.ADMIN else AppUserRole.TRAINER
    }

    suspend fun registerTrainer(email: String, password: String): Result<Unit> {
        return runCatching {
            val normalizedEmail = email.trim().lowercase()
            if (!normalizedEmail.endsWith("@senati.pe")) {
                error("Solo se permiten correos institucionales @senati.pe para entrenadores")
            }
            val result = auth.createUserWithEmailAndPassword(normalizedEmail, password).await()
            val uid = result.user?.uid ?: error("No se pudo obtener el UID")
            ensureTrainerDoc(uid, result.user?.email ?: normalizedEmail)
            notifyAdminsNewTrainer(result.user?.email ?: normalizedEmail)
        }
    }

    suspend fun signInTrainer(email: String, password: String): Result<Unit> {
        return runCatching {
            val normalizedEmail = email.trim().lowercase()
            val result = auth.signInWithEmailAndPassword(normalizedEmail, password).await()
            val uid = result.user?.uid ?: error("No se pudo obtener el UID")
            val doc = firestore.collection(TRAINERS).document(uid).get().await()
            val role = doc.getString("role")?.lowercase()
            if (role == "admin") {
                auth.signOut()
                error("Esta cuenta es admin, usa acceso administrador")
            }
            ensureTrainerDoc(uid, result.user?.email ?: normalizedEmail)
        }
    }

    suspend fun signInAdmin(email: String, password: String): Result<Unit> {
        return runCatching {
            val normalizedEmail = email.trim().lowercase()
            val result = auth.signInWithEmailAndPassword(normalizedEmail, password).await()
            val uid = result.user?.uid ?: error("No se pudo obtener el UID")
            val doc = firestore.collection(TRAINERS).document(uid).get().await()
            val role = doc.getString("role")?.lowercase()
            if (role != "admin") {
                auth.signOut()
                error("La cuenta no tiene rol de admin")
            }
        }
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun createRewardCampaign(
        campaignName: String,
        rewardCount: Int = 3,
        pool: List<Int> = UnlockRepository.UNLOCK_POOL
    ): Result<CampaignQrData> {
        return runCatching {
            val uid = auth.currentUser?.uid ?: error("Debes iniciar sesion")
            val id = UUID.randomUUID().toString()
            val safeName = campaignName.trim().ifEmpty { "Campana QR" }
            val payload = "pokeapi://reward?campaignId=$id"

            firestore.collection(CAMPAIGNS).document(id).set(
                mapOf(
                    "campaignId" to id,
                    "name" to safeName,
                    "active" to true,
                    "rewardCount" to rewardCount,
                    "qrPayload" to payload,
                    "pool" to pool,
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            CampaignQrData(
                campaignId = id,
                campaignName = safeName,
                qrPayload = payload
            )
        }
    }

    suspend fun claimRewardFromCampaign(campaignId: String): ClaimRewardResult {
        val uid = auth.currentUser?.uid ?: return ClaimRewardResult.Error("Debes iniciar sesion")

        return try {
            val rewardIds = firestore.runTransaction { tx ->
                val campaignRef = firestore.collection(CAMPAIGNS).document(campaignId)
                val claimRef = campaignRef.collection(CLAIMS).document(uid)
                val trainerRef = firestore.collection(TRAINERS).document(uid)

                val campaignSnap = tx.get(campaignRef)
                if (!campaignSnap.exists()) throw IllegalStateException("Campana no existe")

                val active = campaignSnap.getBoolean("active") ?: false
                if (!active) throw IllegalStateException("Campana inactiva")

                val claimSnap = tx.get(claimRef)
                if (claimSnap.exists()) throw IllegalStateException("ALREADY_CLAIMED")

                val rewardCount = (campaignSnap.getLong("rewardCount") ?: 3L).toInt().coerceAtLeast(1)
                val poolRaw = campaignSnap.get("pool") as? List<*> ?: emptyList<Any>()
                val pool = poolRaw.mapNotNull {
                    when (it) {
                        is Long -> it.toInt()
                        is Int -> it
                        else -> null
                    }
                }.distinct()

                if (pool.isEmpty()) throw IllegalStateException("Campana sin pool")

                val reward = pool.shuffled().take(rewardCount)

                tx.set(
                    claimRef,
                    mapOf(
                        "uid" to uid,
                        "rewardIds" to reward,
                        "claimedAt" to FieldValue.serverTimestamp()
                    )
                )

                tx.set(
                    trainerRef,
                    mapOf(
                        "uid" to uid,
                        "email" to (auth.currentUser?.email ?: ""),
                        "role" to "trainer",
                        "displayName" to (auth.currentUser?.email ?: "Entrenador"),
                        "lastClaimAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )

                reward.forEach { pokemonId ->
                    val invRef = trainerRef.collection("inventory").document(pokemonId.toString())
                    val invSnap = tx.get(invRef)
                    val current = (invSnap.getLong("count") ?: 0L).toInt()
                    tx.set(
                        invRef,
                        mapOf(
                            "pokemonId" to pokemonId,
                            "count" to current + 1,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                reward
            }.await()

            ClaimRewardResult.Success(rewardIds)
        } catch (e: Exception) {
            if (e.message == "ALREADY_CLAIMED") {
                ClaimRewardResult.AlreadyClaimed
            } else {
                ClaimRewardResult.Error(e.message ?: "No se pudo canjear")
            }
        }
    }

    suspend fun getTrainersWithInventory(): Result<List<TrainerWithInventory>> {
        return runCatching {
            val trainerDocs = firestore.collection(TRAINERS)
                .whereEqualTo("role", "trainer")
                .get()
                .await()
                .documents

            coroutineScope {
                trainerDocs.map { doc ->
                    async {
                        val uid = doc.id
                        val name = doc.getString("displayName")
                            ?.takeIf { it.isNotBlank() }
                            ?: "Entrenador"
                        val email = doc.getString("email") ?: ""
                        val firstName = doc.getString("firstName") ?: ""
                        val lastName = doc.getString("lastName") ?: ""
                        val birthDate = doc.getString("birthDate") ?: ""
                        val gender = doc.getString("gender") ?: ""

                        val invDocs = firestore.collection(TRAINERS)
                            .document(uid)
                            .collection("inventory")
                            .get()
                            .await()
                            .documents

                        val inventory = invDocs.mapNotNull { inv ->
                            val pokemonId = (inv.getLong("pokemonId") ?: return@mapNotNull null).toInt()
                            val count = (inv.getLong("count") ?: 0L).toInt()
                            InventoryCount(pokemonId = pokemonId, count = count)
                        }.sortedByDescending { it.count }

                        val ownedCount = (doc.getLong("ownedPokemonCount") ?: 0L).toInt()
                        val unlockedCount = (doc.getLong("unlockedPokemonCount") ?: 0L).toInt()
                        val points = (doc.getLong("points") ?: 0L).toInt()

                        TrainerWithInventory(
                            uid = uid,
                            displayName = name,
                            email = email,
                            firstName = firstName,
                            lastName = lastName,
                            birthDate = birthDate,
                            gender = gender,
                            inventory = inventory,
                            ownedPokemonCount = ownedCount,
                            unlockedPokemonCount = unlockedCount,
                            points = points
                        )
                    }
                }.awaitAll()
            }.sortedBy { it.displayName.lowercase() }
        }
    }

    suspend fun updateTrainerPersonalInfo(
        firstName: String,
        lastName: String,
        birthDate: String,
        gender: String
    ): Result<Unit> {
        return runCatching {
            val uid = auth.currentUser?.uid ?: error("No hay sesion activa")
            val displayName = listOf(firstName, lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifEmpty { auth.currentUser?.email ?: "Entrenador" }

            firestore.collection(TRAINERS).document(uid).set(
                mapOf(
                    "firstName" to firstName,
                    "lastName" to lastName,
                    "birthDate" to birthDate,
                    "gender" to gender,
                    "displayName" to displayName,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        }
    }

    suspend fun getAllCampaigns(): Result<List<CampaignInfo>> {
        return runCatching {
            val campaignDocs = firestore.collection(CAMPAIGNS)
                .get()
                .await()
                .documents

            coroutineScope {
                campaignDocs.map { doc ->
                    async {
                        val id = doc.id
                        val name = doc.getString("name") ?: "Sin nombre"
                        val active = doc.getBoolean("active") ?: false
                        val rewardCount = (doc.getLong("rewardCount") ?: 3L).toInt()
                        val createdAt = doc.getTimestamp("createdAt")
                        val qrPayload = doc.getString("qrPayload")
                            ?: "pokeapi://reward?campaignId=$id"

                        val claimCount = firestore.collection(CAMPAIGNS)
                            .document(id)
                            .collection(CLAIMS)
                            .get()
                            .await()
                            .size()

                        CampaignInfo(
                            campaignId = id,
                            name = name,
                            active = active,
                            rewardCount = rewardCount,
                            createdAt = createdAt,
                            claimCount = claimCount,
                            qrPayload = qrPayload
                        )
                    }
                }.awaitAll()
            }.sortedByDescending { it.createdAt }
        }
    }

    suspend fun toggleCampaignActive(campaignId: String, active: Boolean): Result<Unit> {
        return runCatching {
            firestore.collection(CAMPAIGNS).document(campaignId).update("active", active).await()
        }
    }

    suspend fun deleteCampaign(campaignId: String): Result<Unit> {
        return runCatching {
            firestore.collection(CAMPAIGNS).document(campaignId).delete().await()
        }
    }

    fun observeNotificationsForCurrentUser(): Flow<List<AppNotification>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration = firestore.collection(TRAINERS)
            .document(uid)
            .collection(NOTIFICATIONS)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val items = snapshot?.documents.orEmpty().map { doc ->
                    AppNotification(
                        id = doc.id,
                        title = doc.getString("title") ?: "Notificación",
                        message = doc.getString("message") ?: "",
                        type = doc.getString("type") ?: "general",
                        read = doc.getBoolean("read") ?: false,
                        createdAt = doc.getTimestamp("createdAt")
                    )
                }
                trySend(items)
            }

        awaitClose { registration.remove() }
    }

    suspend fun markNotificationAsRead(notificationId: String): Result<Unit> {
        return runCatching {
            val uid = auth.currentUser?.uid ?: error("No hay sesión activa")
            firestore.collection(TRAINERS)
                .document(uid)
                .collection(NOTIFICATIONS)
                .document(notificationId)
                .set(
                    mapOf(
                        "read" to true,
                        "readAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()
        }
    }

    suspend fun sendAdminBroadcastNotification(title: String, message: String): Result<Int> {
        return runCatching {
            val uid = auth.currentUser?.uid ?: error("No hay sesión activa")
            val actorDoc = firestore.collection(TRAINERS).document(uid).get().await()
            val role = actorDoc.getString("role")?.lowercase()
            if (role != "admin") error("Solo un admin puede enviar notificaciones")

            val safeTitle = title.trim().ifEmpty { "Aviso del administrador" }
            val safeMessage = message.trim()
            if (safeMessage.isBlank()) error("El mensaje no puede estar vacío")

            val trainers = firestore.collection(TRAINERS)
                .whereEqualTo("role", "trainer")
                .get()
                .await()
                .documents

            if (trainers.isEmpty()) return@runCatching 0

            val batch = firestore.batch()
            trainers.forEach { trainer ->
                val notifRef = trainer.reference.collection(NOTIFICATIONS).document(UUID.randomUUID().toString())
                batch.set(
                    notifRef,
                    mapOf(
                        "title" to safeTitle,
                        "message" to safeMessage,
                        "type" to "admin_broadcast",
                        "read" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "createdBy" to uid
                    )
                )
            }
            batch.commit().await()
            trainers.size
        }
    }

    suspend fun syncTrainerStats(ownedCount: Int, unlockedCount: Int, points: Int) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection(TRAINERS).document(uid).set(
            mapOf(
                "ownedPokemonCount" to ownedCount,
                "unlockedPokemonCount" to unlockedCount,
                "points" to points,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    private suspend fun ensureTrainerDoc(uid: String, email: String) {
        firestore.collection(TRAINERS).document(uid).set(
            mapOf(
                "uid" to uid,
                "email" to email,
                "role" to "trainer",
                "displayName" to if (email.isBlank()) "Entrenador" else email,
                "updatedAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    private suspend fun notifyAdminsNewTrainer(trainerEmail: String) {
        runCatching {
            val admins = firestore.collection(TRAINERS)
                .whereEqualTo("role", "admin")
                .get()
                .await()
                .documents

            if (admins.isEmpty()) return

            val batch = firestore.batch()
            admins.forEach { adminDoc ->
                val notifRef = adminDoc.reference.collection(NOTIFICATIONS).document(UUID.randomUUID().toString())
                batch.set(
                    notifRef,
                    mapOf(
                        "title" to "Nuevo entrenador registrado",
                        "message" to "Se registró: $trainerEmail",
                        "type" to "new_trainer",
                        "read" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
        }
    }
}
