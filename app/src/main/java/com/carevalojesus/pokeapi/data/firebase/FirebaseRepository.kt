package com.carevalojesus.pokeapi.data.firebase

import com.carevalojesus.pokeapi.data.repository.UnlockRepository
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
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
    val points: Int = 0,
    val createdAt: com.google.firebase.Timestamp? = null,
    val updatedAt: com.google.firebase.Timestamp? = null,
    val lastClaimAt: com.google.firebase.Timestamp? = null,
    val profilePhotoUrl: String = "",
    val lastSeen: com.google.firebase.Timestamp? = null
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

data class TrainerRewardClaim(
    val campaignId: String,
    val campaignName: String,
    val rewardIds: List<Int>,
    val claimedAt: com.google.firebase.Timestamp?
)

data class FirebaseMissionAward(
    val awardedPoints: Int,
    val totalPoints: Int,
    val unlockedPokemonIds: List<Int>
)

data class TradeFirestoreData(
    val tradeId: String,
    val creatorUid: String,
    val acceptorUid: String = "",
    val offerPokemonId: Int,
    val requestPokemonId: Int,
    val offerPokemonName: String,
    val requestPokemonName: String,
    val status: String, // "pending", "accepted", "completed"
    val createdAt: com.google.firebase.Timestamp? = null,
    val acceptedAt: com.google.firebase.Timestamp? = null,
    val completedAt: com.google.firebase.Timestamp? = null
)

sealed interface TradeResult {
    data class Success(val tradeId: String) : TradeResult
    data class Error(val message: String) : TradeResult
}

class AlreadyClaimedException : Exception("ALREADY_CLAIMED")

sealed interface ClaimRewardResult {
    data class Success(val rewardIds: List<Int>) : ClaimRewardResult
    data object AlreadyClaimed : ClaimRewardResult
    data class Error(val message: String) : ClaimRewardResult
}

class FirebaseRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {

    companion object {
        private const val TRAINERS = "trainers"
        private const val CAMPAIGNS = "campaigns"
        private const val CLAIMS = "claims"
        private const val NOTIFICATIONS = "notifications"
        private const val TRADES = "trades"
        private const val MISSION_EVENTS = "mission_events"
        private const val UNLOCKED = "unlocked"
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
                if (claimSnap.exists()) throw AlreadyClaimedException()

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

                val reward = weightedRandomSelection(pool, rewardCount)

                // ALL READS first (Firestore requires reads before writes)
                val inventorySnapshots = reward.map { pokemonId ->
                    val invRef = trainerRef.collection("inventory").document(pokemonId.toString())
                    pokemonId to tx.get(invRef)
                }

                // ALL WRITES after
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
                        "displayName" to (auth.currentUser?.email ?: "Entrenador"),
                        "lastClaimAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )

                inventorySnapshots.forEach { (pokemonId, invSnap) ->
                    val invRef = trainerRef.collection("inventory").document(pokemonId.toString())
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
        } catch (_: AlreadyClaimedException) {
            ClaimRewardResult.AlreadyClaimed
        } catch (e: Exception) {
            val cause = e.cause
            if (cause is AlreadyClaimedException) {
                ClaimRewardResult.AlreadyClaimed
            } else {
                ClaimRewardResult.Error(e.message ?: "No se pudo canjear")
            }
        }
    }

    fun observeTrainersWithInventory(): Flow<List<TrainerWithInventory>> = callbackFlow {
        val registration = firestore.collection(TRAINERS)
            .whereEqualTo("role", "trainer")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                launch {
                    val trainers = snapshot.documents.map { doc ->
                        async {
                            buildTrainerFromDoc(doc)
                        }
                    }.awaitAll()
                    trySend(trainers.sortedBy { it.displayName.lowercase() })
                }
            }
        awaitClose { registration.remove() }
    }

    private suspend fun buildTrainerFromDoc(doc: com.google.firebase.firestore.DocumentSnapshot): TrainerWithInventory {
        val uid = doc.id
        val name = doc.getString("displayName")?.takeIf { it.isNotBlank() } ?: "Entrenador"
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
        val createdAt = doc.getTimestamp("createdAt")
        val updatedAt = doc.getTimestamp("updatedAt")
        val lastClaimAt = doc.getTimestamp("lastClaimAt")
        val profilePhotoUrl = doc.getString("profilePhotoUrl") ?: ""
        val lastSeen = doc.getTimestamp("lastSeen")

        return TrainerWithInventory(
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
            points = points,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastClaimAt = lastClaimAt,
            profilePhotoUrl = profilePhotoUrl,
            lastSeen = lastSeen
        )
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
            val claimsDocs = firestore.collection(CAMPAIGNS)
                .document(campaignId)
                .collection(CLAIMS)
                .get()
                .await()
                .documents
            for (doc in claimsDocs) {
                doc.reference.delete().await()
            }
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

            val chunks = trainers.chunked(500)
            for (chunk in chunks) {
                val batch = firestore.batch()
                chunk.forEach { trainer ->
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
            }
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

    suspend fun getTrainerPoints(): Int {
        val uid = auth.currentUser?.uid ?: return 0
        val doc = firestore.collection(TRAINERS).document(uid).get().await()
        return (doc.getLong("points") ?: 0L).toInt()
    }

    suspend fun getUnlockedPokemonIds(): List<Int> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        val docs = firestore.collection(TRAINERS)
            .document(uid)
            .collection(UNLOCKED)
            .get()
            .await()
            .documents
        return docs.mapNotNull { it.getLong("pokemonId")?.toInt() }.distinct()
    }

    suspend fun addTrainerPoints(amount: Int): Int {
        if (amount <= 0) return getTrainerPoints()
        val uid = auth.currentUser?.uid ?: return 0
        val trainerRef = firestore.collection(TRAINERS).document(uid)
        return firestore.runTransaction { tx ->
            val snap = tx.get(trainerRef)
            val current = (snap.getLong("points") ?: 0L).toInt()
            val updated = (current + amount).coerceAtMost(1_000_000)
            tx.set(
                trainerRef,
                mapOf(
                    "points" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            updated
        }.await()
    }

    suspend fun spendTrainerPoints(amount: Int): Pair<Boolean, Int> {
        if (amount <= 0) return true to getTrainerPoints()
        val uid = auth.currentUser?.uid ?: return false to 0
        val trainerRef = firestore.collection(TRAINERS).document(uid)
        return firestore.runTransaction { tx ->
            val snap = tx.get(trainerRef)
            val current = (snap.getLong("points") ?: 0L).toInt()
            if (current < amount) {
                false to current
            } else {
                val updated = current - amount
                tx.set(
                    trainerRef,
                    mapOf(
                        "points" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                true to updated
            }
        }.await()
    }

    suspend fun awardMissionPoints(eventKey: String, category: String, points: Int): FirebaseMissionAward {
        if (points <= 0) {
            val current = getTrainerPoints()
            return FirebaseMissionAward(awardedPoints = 0, totalPoints = current, unlockedPokemonIds = emptyList())
        }
        val uid = auth.currentUser?.uid ?: return FirebaseMissionAward(0, 0, emptyList())
        val trainerRef = firestore.collection(TRAINERS).document(uid)
        val eventRef = trainerRef.collection(MISSION_EVENTS).document(eventKey)

        val (awarded, totalPoints) = firestore.runTransaction { tx ->
            val eventSnap = tx.get(eventRef)
            if (eventSnap.exists()) {
                val current = (tx.get(trainerRef).getLong("points") ?: 0L).toInt()
                0 to current
            } else {
                val trainerSnap = tx.get(trainerRef)
                val current = (trainerSnap.getLong("points") ?: 0L).toInt()
                val updated = (current + points).coerceAtMost(1_000_000)
                tx.set(
                    eventRef,
                    mapOf(
                        "eventKey" to eventKey,
                        "category" to category,
                        "pointsAwarded" to points,
                        "createdAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                tx.set(
                    trainerRef,
                    mapOf(
                        "points" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                points to updated
            }
        }.await()

        val unlockedIds = if (awarded > 0) applyUnlocksFromPoints(totalPoints) else emptyList()
        return FirebaseMissionAward(
            awardedPoints = awarded,
            totalPoints = totalPoints,
            unlockedPokemonIds = unlockedIds
        )
    }

    suspend fun countMissionEventsByPrefix(prefix: String): Int {
        val uid = auth.currentUser?.uid ?: return 0
        val docs = firestore.collection(TRAINERS)
            .document(uid)
            .collection(MISSION_EVENTS)
            .whereGreaterThanOrEqualTo("eventKey", prefix)
            .whereLessThan("eventKey", "$prefix\uf8ff")
            .get()
            .await()
            .documents
        return docs.size
    }

    private suspend fun applyUnlocksFromPoints(totalPoints: Int): List<Int> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        val expectedUnlockCount = (totalPoints / 10).coerceAtLeast(0)
        val unlockedCol = firestore.collection(TRAINERS).document(uid).collection(UNLOCKED)
        val unlockedDocs = unlockedCol.get().await().documents
        val unlockedIds = unlockedDocs.mapNotNull { it.getLong("pokemonId")?.toInt() }.toSet()
        val missingCount = (expectedUnlockCount - unlockedIds.size).coerceAtLeast(0)
        if (missingCount == 0) return emptyList()

        val available = UnlockRepository.UNLOCK_POOL.filter { it !in unlockedIds }.shuffled().take(missingCount)
        if (available.isEmpty()) return emptyList()

        val trainerRef = firestore.collection(TRAINERS).document(uid)
        val batch = firestore.batch()
        val inventoryToUpdate = mutableMapOf<Int, Int>()

        for (pokemonId in available) {
            val unlockRef = trainerRef.collection(UNLOCKED).document(pokemonId.toString())
            batch.set(
                unlockRef,
                mapOf(
                    "pokemonId" to pokemonId,
                    "unlockedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )

            val invRef = trainerRef.collection("inventory").document(pokemonId.toString())
            val invSnap = invRef.get().await()
            val currentCount = (invSnap.getLong("count") ?: 0L).toInt()
            inventoryToUpdate[pokemonId] = currentCount + 1
        }

        for ((pokemonId, count) in inventoryToUpdate) {
            val invRef = trainerRef.collection("inventory").document(pokemonId.toString())
            batch.set(
                invRef,
                mapOf(
                    "pokemonId" to pokemonId,
                    "count" to count,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }

        batch.commit().await()
        return available
    }

    suspend fun uploadProfilePhoto(uid: String, photoBytes: ByteArray): String {
        val ref = storage.reference.child("profile_photos/$uid.jpg")
        ref.putBytes(photoBytes).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        firestore.collection(TRAINERS).document(uid).set(
            mapOf("profilePhotoUrl" to downloadUrl),
            SetOptions.merge()
        ).await()
        return downloadUrl
    }

    suspend fun updatePresence() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection(TRAINERS).document(uid).set(
            mapOf("lastSeen" to FieldValue.serverTimestamp()),
            SetOptions.merge()
        ).await()
    }

    fun getCurrentUserUid(): String? = auth.currentUser?.uid

    fun getCurrentUserEmail(): String? = auth.currentUser?.email

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        return runCatching {
            val user = auth.currentUser ?: error("No hay sesion activa")
            val email = user.email ?: error("No se pudo obtener el email")
            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()
            user.updatePassword(newPassword).await()
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return runCatching {
            auth.sendPasswordResetEmail(email.trim().lowercase()).await()
        }
    }

    suspend fun createTrade(
        offerPokemonId: Int,
        requestPokemonId: Int,
        offerPokemonName: String,
        requestPokemonName: String
    ): TradeResult {
        val uid = auth.currentUser?.uid ?: return TradeResult.Error("Debes iniciar sesión")
        val tradeId = UUID.randomUUID().toString()

        return try {
            firestore.runTransaction { tx ->
                val trainerRef = firestore.collection(TRAINERS).document(uid)
                val invRef = trainerRef.collection("inventory").document(offerPokemonId.toString())

                val invSnap = tx.get(invRef)
                val count = (invSnap.getLong("count") ?: 0L).toInt()
                if (count < 2) throw IllegalStateException("No tienes suficientes copias para intercambiar")

                val tradeRef = firestore.collection(TRADES).document(tradeId)
                tx.set(tradeRef, mapOf(
                    "tradeId" to tradeId,
                    "creatorUid" to uid,
                    "offerPokemonId" to offerPokemonId,
                    "requestPokemonId" to requestPokemonId,
                    "offerPokemonName" to offerPokemonName,
                    "requestPokemonName" to requestPokemonName,
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp()
                ))
            }.await()

            TradeResult.Success(tradeId)
        } catch (e: Exception) {
            TradeResult.Error(e.cause?.message ?: e.message ?: "Error al crear intercambio")
        }
    }

    suspend fun fetchTrade(tradeId: String): TradeFirestoreData? {
        return try {
            val doc = firestore.collection(TRADES).document(tradeId).get().await()
            if (!doc.exists()) return null
            TradeFirestoreData(
                tradeId = doc.getString("tradeId") ?: tradeId,
                creatorUid = doc.getString("creatorUid") ?: "",
                acceptorUid = doc.getString("acceptorUid") ?: "",
                offerPokemonId = (doc.getLong("offerPokemonId") ?: 0L).toInt(),
                requestPokemonId = (doc.getLong("requestPokemonId") ?: 0L).toInt(),
                offerPokemonName = doc.getString("offerPokemonName") ?: "",
                requestPokemonName = doc.getString("requestPokemonName") ?: "",
                status = doc.getString("status") ?: "",
                createdAt = doc.getTimestamp("createdAt"),
                acceptedAt = doc.getTimestamp("acceptedAt"),
                completedAt = doc.getTimestamp("completedAt")
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun acceptTrade(tradeId: String): TradeResult {
        val uid = auth.currentUser?.uid ?: return TradeResult.Error("Debes iniciar sesión")

        return try {
            firestore.runTransaction { tx ->
                val tradeRef = firestore.collection(TRADES).document(tradeId)
                val tradeSnap = tx.get(tradeRef)

                if (!tradeSnap.exists()) throw IllegalStateException("Intercambio no encontrado")
                val status = tradeSnap.getString("status") ?: ""
                if (status != "pending") throw IllegalStateException("Este intercambio ya no está disponible")

                val creatorUid = tradeSnap.getString("creatorUid") ?: ""
                if (uid == creatorUid) throw IllegalStateException("No puedes aceptar tu propio intercambio")

                val requestPokemonId = (tradeSnap.getLong("requestPokemonId") ?: 0L).toInt()
                val offerPokemonId = (tradeSnap.getLong("offerPokemonId") ?: 0L).toInt()
                val offerPokemonName = tradeSnap.getString("offerPokemonName") ?: ""

                // Read B's inventory for the requested pokemon
                val trainerBRef = firestore.collection(TRAINERS).document(uid)
                val bInvRef = trainerBRef.collection("inventory").document(requestPokemonId.toString())
                val bInvSnap = tx.get(bInvRef)
                val bCount = (bInvSnap.getLong("count") ?: 0L).toInt()
                if (bCount < 2) throw IllegalStateException("No tienes suficientes copias del Pokémon solicitado")

                // Read B's inventory for the offered pokemon (to increment)
                val bOfferInvRef = trainerBRef.collection("inventory").document(offerPokemonId.toString())
                val bOfferInvSnap = tx.get(bOfferInvRef)
                val bOfferCount = (bOfferInvSnap.getLong("count") ?: 0L).toInt()

                // Write: update trade status
                tx.update(tradeRef, mapOf(
                    "status" to "accepted",
                    "acceptorUid" to uid,
                    "acceptedAt" to FieldValue.serverTimestamp()
                ))

                // Write: decrement B's requested pokemon
                tx.set(bInvRef, mapOf(
                    "pokemonId" to requestPokemonId,
                    "count" to (bCount - 1),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))

                // Write: increment B's offered pokemon
                tx.set(bOfferInvRef, mapOf(
                    "pokemonId" to offerPokemonId,
                    "count" to (bOfferCount + 1),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }.await()

            TradeResult.Success(tradeId)
        } catch (e: Exception) {
            TradeResult.Error(e.cause?.message ?: e.message ?: "Error al aceptar intercambio")
        }
    }

    suspend fun completeTrade(tradeId: String): TradeResult {
        val uid = auth.currentUser?.uid ?: return TradeResult.Error("Debes iniciar sesión")

        return try {
            firestore.runTransaction { tx ->
                val tradeRef = firestore.collection(TRADES).document(tradeId)
                val tradeSnap = tx.get(tradeRef)

                if (!tradeSnap.exists()) throw IllegalStateException("Intercambio no encontrado")
                val status = tradeSnap.getString("status") ?: ""
                if (status != "accepted") throw IllegalStateException("Este intercambio no está listo para completar")

                val creatorUid = tradeSnap.getString("creatorUid") ?: ""
                if (uid != creatorUid) throw IllegalStateException("Solo el creador puede completar el intercambio")

                val offerPokemonId = (tradeSnap.getLong("offerPokemonId") ?: 0L).toInt()
                val requestPokemonId = (tradeSnap.getLong("requestPokemonId") ?: 0L).toInt()
                val requestPokemonName = tradeSnap.getString("requestPokemonName") ?: ""

                // Read A's inventory for the offered pokemon
                val trainerARef = firestore.collection(TRAINERS).document(uid)
                val aInvRef = trainerARef.collection("inventory").document(offerPokemonId.toString())
                val aInvSnap = tx.get(aInvRef)
                val aCount = (aInvSnap.getLong("count") ?: 0L).toInt()
                if (aCount < 1) throw IllegalStateException("Ya no tienes el Pokémon ofrecido")

                // Read A's inventory for the requested pokemon (to increment)
                val aReqInvRef = trainerARef.collection("inventory").document(requestPokemonId.toString())
                val aReqInvSnap = tx.get(aReqInvRef)
                val aReqCount = (aReqInvSnap.getLong("count") ?: 0L).toInt()

                // Write: update trade status
                tx.update(tradeRef, mapOf(
                    "status" to "completed",
                    "completedAt" to FieldValue.serverTimestamp()
                ))

                // Write: decrement A's offered pokemon
                tx.set(aInvRef, mapOf(
                    "pokemonId" to offerPokemonId,
                    "count" to (aCount - 1),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))

                // Write: increment A's requested pokemon
                tx.set(aReqInvRef, mapOf(
                    "pokemonId" to requestPokemonId,
                    "count" to (aReqCount + 1),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }.await()

            TradeResult.Success(tradeId)
        } catch (e: Exception) {
            TradeResult.Error(e.cause?.message ?: e.message ?: "Error al completar intercambio")
        }
    }

    private fun weightedRandomSelection(pool: List<Int>, count: Int): List<Int> {
        if (pool.isEmpty()) return emptyList()
        val ultraRare = setOf(150, 151)       // Mewtwo, Mew: peso 1
        val rare = setOf(144, 145, 146)        // Articuno, Zapdos, Moltres: peso 3

        val weighted = pool.flatMap { id ->
            when (id) {
                in ultraRare -> listOf(id)
                in rare -> List(3) { id }
                else -> List(10) { id }
            }
        }
        return weighted.shuffled().distinct().take(count)
    }

    private suspend fun ensureTrainerDoc(uid: String, email: String) {
        val docRef = firestore.collection(TRAINERS).document(uid)
        val doc = docRef.get().await()
        val data = mutableMapOf<String, Any>(
            "uid" to uid,
            "email" to email,
            "role" to "trainer",
            "displayName" to if (email.isBlank()) "Entrenador" else email,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!doc.exists()) {
            data["createdAt"] = FieldValue.serverTimestamp()
        }
        docRef.set(data, SetOptions.merge()).await()
    }

    suspend fun getTrainerRewardHistory(trainerUid: String): Result<List<TrainerRewardClaim>> {
        return runCatching {
            val campaignDocs = firestore.collection(CAMPAIGNS).get().await().documents

            coroutineScope {
                campaignDocs.mapNotNull { doc ->
                    async {
                        val campaignId = doc.id
                        val campaignName = doc.getString("name") ?: "Sin nombre"
                        val claimDoc = firestore.collection(CAMPAIGNS)
                            .document(campaignId)
                            .collection(CLAIMS)
                            .document(trainerUid)
                            .get()
                            .await()

                        if (!claimDoc.exists()) return@async null

                        val rewardIdsRaw = claimDoc.get("rewardIds") as? List<*> ?: emptyList<Any>()
                        val rewardIds = rewardIdsRaw.mapNotNull {
                            when (it) {
                                is Long -> it.toInt()
                                is Int -> it
                                else -> null
                            }
                        }
                        val claimedAt = claimDoc.getTimestamp("claimedAt")

                        TrainerRewardClaim(
                            campaignId = campaignId,
                            campaignName = campaignName,
                            rewardIds = rewardIds,
                            claimedAt = claimedAt
                        )
                    }
                }.awaitAll().filterNotNull()
            }.sortedByDescending { it.claimedAt }
        }
    }

    private suspend fun notifyAdminsNewTrainer(trainerEmail: String) {
        runCatching {
            val createdBy = auth.currentUser?.uid ?: return
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
                        "createdAt" to FieldValue.serverTimestamp(),
                        "createdBy" to createdBy
                    )
                )
            }
            batch.commit().await()
        }
    }
}
