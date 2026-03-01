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
import kotlin.math.roundToInt

enum class AppUserRole {
    ADMIN,
    TRAINER
}

data class CampaignQrData(
    val campaignId: String,
    val campaignName: String,
    val qrCodes: List<CampaignQrCodeData>
) {
    val qrPayload: String
        get() = qrCodes.firstOrNull()?.payload ?: "pokeapi://reward?campaignId=$campaignId"
}

data class CampaignQrCodeData(
    val codeId: String,
    val payload: String
)

data class InventoryCount(
    val pokemonId: Int,
    val count: Int
)

data class PokemonCareState(
    val pokemonId: Int,
    val hunger: Int,
    val energy: Int,
    val happiness: Int,
    val sleeping: Boolean,
    val wantsToWakeUp: Boolean,
    val updatedAtMillis: Long,
    val lastPenaltyPoints: Int = 0,
    val pokemonLost: Boolean = false
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
    val qrCount: Int,
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

data class MarketplaceItemFirebase(
    val itemId: String,
    val category: String,
    val equipped: Boolean,
    val purchasedAt: Long
)

data class TrainerSetupState(
    val firstName: String,
    val lastName: String,
    val birthDate: String,
    val gender: String,
    val starterChosen: Boolean,
    val starterPokemonId: Int,
    val starterChangesRemaining: Int,
    val profilePhotoUrl: String
)

sealed interface TradeResult {
    data class Success(val tradeId: String, val tradeData: TradeFirestoreData? = null) : TradeResult
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
        private const val CODES = "codes"
        private const val NOTIFICATIONS = "notifications"
        private const val TRADES = "trades"
        private const val MISSION_EVENTS = "mission_events"
        private const val UNLOCKED = "unlocked"
        private const val FAVORITES = "favorites"
        private const val MARKETPLACE = "marketplace"
        private const val PET_CARE = "pet_care"
        private const val PET_NEGLECT_GRACE_MS = 3 * 60 * 60 * 1000L
        private const val PET_NEGLECT_PENALTY_COOLDOWN_MS = 4 * 60 * 60 * 1000L
    }

    private data class PetCareMeta(
        val neglectSinceMillis: Long,
        val lastPenaltyAtMillis: Long,
        val neglectStrikes: Int
    )

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
        qrCount: Int = 1,
        rewardCount: Int = 3,
        pool: List<Int> = UnlockRepository.UNLOCK_POOL
    ): Result<CampaignQrData> {
        return runCatching {
            val uid = auth.currentUser?.uid ?: error("Debes iniciar sesion")
            val id = UUID.randomUUID().toString()
            val safeName = campaignName.trim().ifEmpty { "Campana QR" }
            val normalizedQrCount = qrCount.coerceIn(1, 500)
            val codes = (1..normalizedQrCount).map {
                val codeId = UUID.randomUUID().toString().replace("-", "").take(16)
                CampaignQrCodeData(
                    codeId = codeId,
                    payload = "pokeapi://reward?campaignId=$id&codeId=$codeId"
                )
            }
            val campaignRef = firestore.collection(CAMPAIGNS).document(id)
            val batch = firestore.batch()

            batch.set(
                campaignRef,
                mapOf(
                    "campaignId" to id,
                    "name" to safeName,
                    "active" to true,
                    "rewardCount" to rewardCount,
                    "qrCount" to normalizedQrCount,
                    "requiresCode" to true,
                    "qrPayload" to codes.first().payload,
                    "pool" to pool,
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            codes.forEachIndexed { index, code ->
                batch.set(
                    campaignRef.collection(CODES).document(code.codeId),
                    mapOf(
                        "codeId" to code.codeId,
                        "payload" to code.payload,
                        "index" to index + 1,
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()

            CampaignQrData(
                campaignId = id,
                campaignName = safeName,
                qrCodes = codes
            )
        }
    }

    suspend fun claimRewardFromCampaign(campaignId: String, codeId: String? = null): ClaimRewardResult {
        val uid = auth.currentUser?.uid ?: return ClaimRewardResult.Error("Debes iniciar sesion")

        return try {
            val rewardIds = firestore.runTransaction { tx ->
                val campaignRef = firestore.collection(CAMPAIGNS).document(campaignId)
                val claimRef = campaignRef.collection(CLAIMS).document(uid)
                val trainerRef = firestore.collection(TRAINERS).document(uid)
                val normalizedCodeId = codeId?.trim().orEmpty()
                val codeRef = if (normalizedCodeId.isNotBlank()) {
                    campaignRef.collection(CODES).document(normalizedCodeId)
                } else {
                    null
                }

                val campaignSnap = tx.get(campaignRef)
                if (!campaignSnap.exists()) throw IllegalStateException("Campana no existe")

                val active = campaignSnap.getBoolean("active") ?: false
                if (!active) throw IllegalStateException("Campana inactiva")
                val requiresCode = campaignSnap.getBoolean("requiresCode") ?: false
                if (requiresCode && codeRef == null) {
                    throw IllegalStateException("QR invalido")
                }

                val claimSnap = tx.get(claimRef)
                if (claimSnap.exists()) throw AlreadyClaimedException()

                if (codeRef != null) {
                    val codeSnap = tx.get(codeRef)
                    if (!codeSnap.exists()) throw IllegalStateException("QR invalido")
                    val codeActive = codeSnap.getBoolean("active") ?: false
                    if (!codeActive) throw IllegalStateException("QR ya utilizado")
                }

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
                        "codeId" to normalizedCodeId,
                        "rewardIds" to reward,
                        "claimedAt" to FieldValue.serverTimestamp()
                    )
                )

                if (codeRef != null) {
                    tx.update(
                        codeRef,
                        mapOf(
                            "active" to false,
                            "claimedBy" to uid,
                            "claimedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                tx.set(
                    trainerRef,
                    mapOf(
                        "uid" to uid,
                        "email" to (auth.currentUser?.email ?: "unknown"),
                        "displayName" to (auth.currentUser?.displayName
                            ?: auth.currentUser?.email
                            ?: "Entrenador"),
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

    suspend fun updateStarterSelection(
        starterPokemonId: Int,
        starterChangesRemaining: Int? = null
    ): Result<Unit> {
        return runCatching {
            val uid = auth.currentUser?.uid ?: error("No hay sesion activa")
            val updates = mutableMapOf<String, Any>(
                "starterChosen" to true,
                "starterPokemonId" to starterPokemonId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (starterChangesRemaining != null) {
                updates["starterChangesRemaining"] = starterChangesRemaining.coerceAtLeast(0)
            }
            firestore.collection(TRAINERS).document(uid)
                .set(updates, SetOptions.merge())
                .await()
        }
    }

    suspend fun getCurrentTrainerSetupState(): TrainerSetupState? {
        val uid = auth.currentUser?.uid ?: return null
        val doc = firestore.collection(TRAINERS).document(uid).get().await()
        if (!doc.exists()) return null

        val starterPokemonId = (doc.getLong("starterPokemonId") ?: 0L).toInt()
        val starterChosen = doc.getBoolean("starterChosen") ?: (starterPokemonId > 0)

        return TrainerSetupState(
            firstName = doc.getString("firstName") ?: "",
            lastName = doc.getString("lastName") ?: "",
            birthDate = doc.getString("birthDate") ?: "",
            gender = doc.getString("gender") ?: "",
            starterChosen = starterChosen,
            starterPokemonId = starterPokemonId,
            starterChangesRemaining = (doc.getLong("starterChangesRemaining") ?: 3L).toInt(),
            profilePhotoUrl = doc.getString("profilePhotoUrl") ?: ""
        )
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
                        val qrCount = (doc.getLong("qrCount") ?: 1L).toInt().coerceAtLeast(1)
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
                            qrCount = qrCount,
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

    suspend fun getCampaignQrCodes(campaignId: String): Result<List<CampaignQrCodeData>> {
        return runCatching {
            firestore.collection(CAMPAIGNS)
                .document(campaignId)
                .collection(CODES)
                .orderBy("index", com.google.firebase.firestore.Query.Direction.ASCENDING)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    val codeId = doc.getString("codeId") ?: return@mapNotNull null
                    val payload = doc.getString("payload")
                        ?: "pokeapi://reward?campaignId=$campaignId&codeId=$codeId"
                    CampaignQrCodeData(codeId = codeId, payload = payload)
                }
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
            val codeDocs = firestore.collection(CAMPAIGNS)
                .document(campaignId)
                .collection(CODES)
                .get()
                .await()
                .documents
            for (doc in codeDocs) {
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

    suspend fun getCurrentUserInventory(): List<InventoryCount> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        val docs = firestore.collection(TRAINERS)
            .document(uid)
            .collection("inventory")
            .get()
            .await()
            .documents
        return docs.mapNotNull { inv ->
            val pokemonId = (inv.getLong("pokemonId") ?: return@mapNotNull null).toInt()
            val count = (inv.getLong("count") ?: 0L).toInt()
            InventoryCount(pokemonId = pokemonId, count = count)
        }
    }

    suspend fun ensureInventoryHasAtLeast(pokemonId: Int, minCount: Int = 1) {
        val uid = auth.currentUser?.uid ?: return
        if (pokemonId <= 0 || minCount <= 0) return
        val trainerRef = firestore.collection(TRAINERS).document(uid)
        val invRef = trainerRef.collection("inventory").document(pokemonId.toString())

        firestore.runTransaction { tx ->
            val snap = tx.get(invRef)
            val current = (snap.getLong("count") ?: 0L).toInt()
            if (current >= minCount) return@runTransaction
            tx.set(
                invRef,
                mapOf(
                    "pokemonId" to pokemonId,
                    "count" to minCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }.await()
    }

    suspend fun getPokemonCareState(pokemonId: Int): PokemonCareState {
        val uid = auth.currentUser?.uid ?: error("Debes iniciar sesión")
        require(pokemonId in 1..151) { "pokemonId inválido" }

        val careRef = firestore.collection(TRAINERS)
            .document(uid)
            .collection(PET_CARE)
            .document(pokemonId.toString())

        val now = System.currentTimeMillis()
        return firestore.runTransaction { tx ->
            val snapshot = tx.get(careRef)
            val current = readAndDecayCareState(snapshot, pokemonId, now)
            val meta = readPetCareMeta(snapshot)
            val (next, nextMeta) = applyNeglectConsequences(
                tx = tx,
                trainerRef = firestore.collection(TRAINERS).document(uid),
                pokemonId = pokemonId,
                state = current,
                meta = meta,
                nowMillis = now
            )
            tx.set(careRef, toCareMap(next, nextMeta), SetOptions.merge())
            next
        }.await()
    }

    suspend fun feedPokemon(pokemonId: Int): PokemonCareState {
        val uid = auth.currentUser?.uid ?: error("Debes iniciar sesión")
        require(pokemonId in 1..151) { "pokemonId inválido" }

        val careRef = firestore.collection(TRAINERS)
            .document(uid)
            .collection(PET_CARE)
            .document(pokemonId.toString())

        val now = System.currentTimeMillis()
        return firestore.runTransaction { tx ->
            val snapshot = tx.get(careRef)
            val current = readAndDecayCareState(snapshot, pokemonId, now)
            val meta = readPetCareMeta(snapshot)
            val afterAction = if (current.sleeping) {
                current
            } else {
                current.copy(
                    hunger = (current.hunger + 28).coerceAtMost(100),
                    happiness = (current.happiness + 8).coerceAtMost(100),
                    updatedAtMillis = now
                )
            }
            val (next, nextMeta) = applyNeglectConsequences(
                tx = tx,
                trainerRef = firestore.collection(TRAINERS).document(uid),
                pokemonId = pokemonId,
                state = afterAction,
                meta = meta,
                nowMillis = now
            )
            tx.set(careRef, toCareMap(next, nextMeta), SetOptions.merge())
            next
        }.await()
    }

    suspend fun startPokemonSleep(pokemonId: Int): PokemonCareState {
        val uid = auth.currentUser?.uid ?: error("Debes iniciar sesión")
        require(pokemonId in 1..151) { "pokemonId inválido" }

        val careRef = firestore.collection(TRAINERS)
            .document(uid)
            .collection(PET_CARE)
            .document(pokemonId.toString())

        val now = System.currentTimeMillis()
        return firestore.runTransaction { tx ->
            val snapshot = tx.get(careRef)
            val current = readAndDecayCareState(snapshot, pokemonId, now)
            val meta = readPetCareMeta(snapshot)
            val afterAction = if (current.sleeping) {
                current
            } else {
                current.copy(
                    sleeping = true,
                    happiness = (current.happiness + 4).coerceAtMost(100),
                    updatedAtMillis = now
                )
            }
            val (next, nextMeta) = applyNeglectConsequences(
                tx = tx,
                trainerRef = firestore.collection(TRAINERS).document(uid),
                pokemonId = pokemonId,
                state = afterAction,
                meta = meta,
                nowMillis = now
            )
            tx.set(careRef, toCareMap(next, nextMeta), SetOptions.merge())
            next
        }.await()
    }

    suspend fun wakePokemon(pokemonId: Int): PokemonCareState {
        val uid = auth.currentUser?.uid ?: error("Debes iniciar sesión")
        require(pokemonId in 1..151) { "pokemonId inválido" }

        val careRef = firestore.collection(TRAINERS)
            .document(uid)
            .collection(PET_CARE)
            .document(pokemonId.toString())

        val now = System.currentTimeMillis()
        return firestore.runTransaction { tx ->
            val snapshot = tx.get(careRef)
            val current = readAndDecayCareState(snapshot, pokemonId, now)
            val meta = readPetCareMeta(snapshot)
            val afterAction = if (!current.sleeping) {
                current
            } else {
                current.copy(
                    sleeping = false,
                    energy = (current.energy + 10).coerceAtMost(100),
                    happiness = (current.happiness + 6).coerceAtMost(100),
                    updatedAtMillis = now
                )
            }
            val (next, nextMeta) = applyNeglectConsequences(
                tx = tx,
                trainerRef = firestore.collection(TRAINERS).document(uid),
                pokemonId = pokemonId,
                state = afterAction,
                meta = meta,
                nowMillis = now
            )
            tx.set(careRef, toCareMap(next, nextMeta), SetOptions.merge())
            next
        }.await()
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
            val tradeData = firestore.runTransaction { tx ->
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
                val requestPokemonName = tradeSnap.getString("requestPokemonName") ?: ""

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

                TradeFirestoreData(
                    tradeId = tradeId,
                    creatorUid = creatorUid,
                    acceptorUid = uid,
                    offerPokemonId = offerPokemonId,
                    requestPokemonId = requestPokemonId,
                    offerPokemonName = offerPokemonName,
                    requestPokemonName = requestPokemonName,
                    status = "accepted"
                )
            }.await()

            TradeResult.Success(tradeId, tradeData)
        } catch (e: Exception) {
            TradeResult.Error(e.cause?.message ?: e.message ?: "Error al aceptar intercambio")
        }
    }

    suspend fun completeTrade(tradeId: String): TradeResult {
        val uid = auth.currentUser?.uid ?: return TradeResult.Error("Debes iniciar sesión")

        return try {
            val tradeData = firestore.runTransaction { tx ->
                val tradeRef = firestore.collection(TRADES).document(tradeId)
                val tradeSnap = tx.get(tradeRef)

                if (!tradeSnap.exists()) throw IllegalStateException("Intercambio no encontrado")
                val status = tradeSnap.getString("status") ?: ""
                if (status != "accepted") throw IllegalStateException("Este intercambio no está listo para completar")

                val creatorUid = tradeSnap.getString("creatorUid") ?: ""
                if (uid != creatorUid) throw IllegalStateException("Solo el creador puede completar el intercambio")

                val offerPokemonId = (tradeSnap.getLong("offerPokemonId") ?: 0L).toInt()
                val requestPokemonId = (tradeSnap.getLong("requestPokemonId") ?: 0L).toInt()
                val offerPokemonName = tradeSnap.getString("offerPokemonName") ?: ""
                val requestPokemonName = tradeSnap.getString("requestPokemonName") ?: ""
                val acceptorUid = tradeSnap.getString("acceptorUid") ?: ""

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

                TradeFirestoreData(
                    tradeId = tradeId,
                    creatorUid = creatorUid,
                    acceptorUid = acceptorUid,
                    offerPokemonId = offerPokemonId,
                    requestPokemonId = requestPokemonId,
                    offerPokemonName = offerPokemonName,
                    requestPokemonName = requestPokemonName,
                    status = "completed"
                )
            }.await()

            TradeResult.Success(tradeId, tradeData)
        } catch (e: Exception) {
            TradeResult.Error(e.cause?.message ?: e.message ?: "Error al completar intercambio")
        }
    }

    private fun readAndDecayCareState(
        snapshot: com.google.firebase.firestore.DocumentSnapshot,
        pokemonId: Int,
        nowMillis: Long
    ): PokemonCareState {
        val base = if (!snapshot.exists()) {
            PokemonCareState(
                pokemonId = pokemonId,
                hunger = 82,
                energy = 78,
                happiness = 75,
                sleeping = false,
                wantsToWakeUp = false,
                updatedAtMillis = nowMillis
            )
        } else {
            PokemonCareState(
                pokemonId = (snapshot.getLong("pokemonId") ?: pokemonId.toLong()).toInt(),
                hunger = (snapshot.getLong("hunger") ?: 82L).toInt().coerceIn(0, 100),
                energy = (snapshot.getLong("energy") ?: 78L).toInt().coerceIn(0, 100),
                happiness = (snapshot.getLong("happiness") ?: 75L).toInt().coerceIn(0, 100),
                sleeping = snapshot.getBoolean("sleeping") ?: false,
                wantsToWakeUp = false,
                updatedAtMillis = snapshot.getLong("updatedAtMillis")
                    ?: ((snapshot.getTimestamp("updatedAt")?.seconds ?: (nowMillis / 1000L)) * 1000L)
            )
        }

        val elapsedMinutes = ((nowMillis - base.updatedAtMillis).coerceAtLeast(0L) / 60_000.0)
        if (elapsedMinutes <= 0.0) {
            val wantsWake = base.sleeping && base.energy >= 95
            return base.copy(wantsToWakeUp = wantsWake, updatedAtMillis = nowMillis)
        }

        val hours = elapsedMinutes / 60.0
        val hungerDecay = (hours * 6.0).roundToInt()
        val awakeEnergyDecay = (hours * 5.0).roundToInt()
        val sleepEnergyGain = (hours * 12.0).roundToInt()
        val happinessDelta = when {
            base.sleeping -> (hours * 2.5).roundToInt()
            base.hunger < 30 -> -(hours * 6.0).roundToInt()
            else -> -(hours * 2.0).roundToInt()
        }

        val nextHunger = (base.hunger - hungerDecay).coerceIn(0, 100)
        val nextEnergy = if (base.sleeping) {
            (base.energy + sleepEnergyGain).coerceIn(0, 100)
        } else {
            (base.energy - awakeEnergyDecay).coerceIn(0, 100)
        }
        val nextHappiness = (base.happiness + happinessDelta).coerceIn(0, 100)
        val wantsWake = base.sleeping && nextEnergy >= 95

        return base.copy(
            hunger = nextHunger,
            energy = nextEnergy,
            happiness = nextHappiness,
            wantsToWakeUp = wantsWake,
            updatedAtMillis = nowMillis
        )
    }

    private fun readPetCareMeta(snapshot: com.google.firebase.firestore.DocumentSnapshot): PetCareMeta {
        return PetCareMeta(
            neglectSinceMillis = snapshot.getLong("neglectSinceMillis") ?: 0L,
            lastPenaltyAtMillis = snapshot.getLong("lastPenaltyAtMillis") ?: 0L,
            neglectStrikes = (snapshot.getLong("neglectStrikes") ?: 0L).toInt().coerceAtLeast(0)
        )
    }

    private fun applyNeglectConsequences(
        tx: com.google.firebase.firestore.Transaction,
        trainerRef: com.google.firebase.firestore.DocumentReference,
        pokemonId: Int,
        state: PokemonCareState,
        meta: PetCareMeta,
        nowMillis: Long
    ): Pair<PokemonCareState, PetCareMeta> {
        val critical = state.hunger == 0 || state.energy == 0
        if (!critical) {
            return state.copy(lastPenaltyPoints = 0, pokemonLost = false) to meta.copy(neglectSinceMillis = 0L)
        }

        val neglectSince = if (meta.neglectSinceMillis == 0L) nowMillis else meta.neglectSinceMillis
        val inGrace = nowMillis - neglectSince < PET_NEGLECT_GRACE_MS
        val inCooldown = meta.lastPenaltyAtMillis > 0L &&
            nowMillis - meta.lastPenaltyAtMillis < PET_NEGLECT_PENALTY_COOLDOWN_MS

        if (inGrace || inCooldown) {
            return state.copy(lastPenaltyPoints = 0, pokemonLost = false) to meta.copy(neglectSinceMillis = neglectSince)
        }

        val trainerSnap = tx.get(trainerRef)
        val currentPoints = (trainerSnap.getLong("points") ?: 0L).toInt()
        val basePenalty = (25 + (meta.neglectStrikes * 10)).coerceAtMost(80)
        var pointsToLose = basePenalty
        var pokemonLost = false

        if (meta.neglectStrikes >= 2) {
            val invRef = trainerRef.collection("inventory").document(pokemonId.toString())
            val invSnap = tx.get(invRef)
            val currentCount = (invSnap.getLong("count") ?: 0L).toInt()
            if (currentCount > 1) {
                tx.set(
                    invRef,
                    mapOf(
                        "pokemonId" to pokemonId,
                        "count" to (currentCount - 1),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                pokemonLost = true
            } else {
                pointsToLose += 20
            }
        }

        val updatedPoints = (currentPoints - pointsToLose).coerceAtLeast(0)
        tx.set(
            trainerRef,
            mapOf(
                "points" to updatedPoints,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )

        val nextState = state.copy(
            happiness = (state.happiness - 15).coerceAtLeast(0),
            updatedAtMillis = nowMillis,
            lastPenaltyPoints = pointsToLose,
            pokemonLost = pokemonLost
        )
        val nextMeta = meta.copy(
            neglectSinceMillis = nowMillis,
            lastPenaltyAtMillis = nowMillis,
            neglectStrikes = (meta.neglectStrikes + 1).coerceAtMost(99)
        )
        return nextState to nextMeta
    }

    private fun toCareMap(state: PokemonCareState, meta: PetCareMeta): Map<String, Any> {
        return mapOf(
            "pokemonId" to state.pokemonId,
            "hunger" to state.hunger.coerceIn(0, 100),
            "energy" to state.energy.coerceIn(0, 100),
            "happiness" to state.happiness.coerceIn(0, 100),
            "sleeping" to state.sleeping,
            "updatedAtMillis" to state.updatedAtMillis,
            "neglectSinceMillis" to meta.neglectSinceMillis,
            "lastPenaltyAtMillis" to meta.lastPenaltyAtMillis,
            "neglectStrikes" to meta.neglectStrikes,
            "updatedAt" to FieldValue.serverTimestamp()
        )
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
            data["starterChosen"] = false
            data["starterPokemonId"] = 0
            data["starterChangesRemaining"] = 3
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

    // ── Favorites ──────────────────────────────────────────────

    suspend fun addFavorite(pokemonId: Int) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection(TRAINERS).document(uid)
            .collection(FAVORITES).document(pokemonId.toString())
            .set(
                mapOf(
                    "pokemonId" to pokemonId,
                    "addedAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun removeFavorite(pokemonId: Int) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection(TRAINERS).document(uid)
            .collection(FAVORITES).document(pokemonId.toString())
            .delete().await()
    }

    suspend fun getFavorites(): List<Int> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        val docs = firestore.collection(TRAINERS).document(uid)
            .collection(FAVORITES).get().await().documents
        return docs.mapNotNull { it.getLong("pokemonId")?.toInt() }
    }

    // ── Marketplace ─────────────────────────────────────────────

    suspend fun addMarketplaceItem(itemId: String, category: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection(TRAINERS).document(uid)
            .collection(MARKETPLACE).document(itemId)
            .set(
                mapOf(
                    "itemId" to itemId,
                    "category" to category,
                    "equipped" to false,
                    "purchasedAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun updateMarketplaceItemEquipped(itemId: String, category: String, equipped: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        if (equipped) {
            // Unequip all items in same category first
            val docs = firestore.collection(TRAINERS).document(uid)
                .collection(MARKETPLACE)
                .whereEqualTo("category", category)
                .whereEqualTo("equipped", true)
                .get().await().documents
            val batch = firestore.batch()
            docs.forEach { doc ->
                batch.update(doc.reference, "equipped", false)
            }
            batch.update(
                firestore.collection(TRAINERS).document(uid)
                    .collection(MARKETPLACE).document(itemId),
                "equipped", true
            )
            batch.commit().await()
        } else {
            firestore.collection(TRAINERS).document(uid)
                .collection(MARKETPLACE).document(itemId)
                .update("equipped", false).await()
        }
    }

    suspend fun getMarketplaceItems(): List<MarketplaceItemFirebase> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        val docs = firestore.collection(TRAINERS).document(uid)
            .collection(MARKETPLACE).get().await().documents
        return docs.mapNotNull { doc ->
            val itemId = doc.getString("itemId") ?: return@mapNotNull null
            MarketplaceItemFirebase(
                itemId = itemId,
                category = doc.getString("category") ?: "",
                equipped = doc.getBoolean("equipped") ?: false,
                purchasedAt = doc.getTimestamp("purchasedAt")?.seconds ?: 0L
            )
        }
    }

    // ── Reset database ────────────────────────────────────────────

    suspend fun resetDatabaseKeepingAdmin(): Result<Unit> {
        return runCatching {
            val uid = auth.currentUser?.uid ?: error("No hay sesión activa")
            val adminDoc = firestore.collection(TRAINERS).document(uid).get().await()
            val role = adminDoc.getString("role")?.lowercase()
            if (role != "admin") error("Solo un admin puede resetear la base de datos")

            // 1. Delete all trainers (role == "trainer") and their subcollections
            val trainerDocs = firestore.collection(TRAINERS)
                .whereEqualTo("role", "trainer")
                .get()
                .await()
                .documents

            val trainerSubcollections = listOf(
                "inventory", UNLOCKED, NOTIFICATIONS, MISSION_EVENTS,
                FAVORITES, MARKETPLACE, PET_CARE
            )

            for (trainerDoc in trainerDocs) {
                for (subcol in trainerSubcollections) {
                    deleteSubcollection(trainerDoc.reference.collection(subcol))
                }
                trainerDoc.reference.delete().await()
            }

            // 2. Delete all trades
            deleteSubcollection(firestore.collection(TRADES))

            // 3. Delete all campaigns (with their claims subcollection)
            val campaignDocs = firestore.collection(CAMPAIGNS)
                .get()
                .await()
                .documents
            for (campaignDoc in campaignDocs) {
                deleteSubcollection(campaignDoc.reference.collection(CODES))
                deleteSubcollection(campaignDoc.reference.collection(CLAIMS))
                campaignDoc.reference.delete().await()
            }

            // 4. Clean admin's own subcollections
            val adminRef = firestore.collection(TRAINERS).document(uid)
            for (subcol in trainerSubcollections) {
                deleteSubcollection(adminRef.collection(subcol))
            }

            // 5. Reset admin stats
            adminRef.set(
                mapOf(
                    "ownedPokemonCount" to 0,
                    "unlockedPokemonCount" to 0,
                    "points" to 0,
                    "starterChosen" to false,
                    "starterPokemonId" to 0,
                    "starterChangesRemaining" to 3,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        }
    }

    private suspend fun deleteSubcollection(
        collection: com.google.firebase.firestore.CollectionReference
    ) {
        val docs = collection.get().await().documents
        if (docs.isEmpty()) return
        val chunks = docs.chunked(500)
        for (chunk in chunks) {
            val batch = firestore.batch()
            chunk.forEach { doc -> batch.delete(doc.reference) }
            batch.commit().await()
        }
    }

    // ── Notifications ───────────────────────────────────────────

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
