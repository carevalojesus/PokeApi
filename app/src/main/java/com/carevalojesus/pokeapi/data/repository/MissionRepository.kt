package com.carevalojesus.pokeapi.data.repository

import com.carevalojesus.pokeapi.data.firebase.FirebaseRepository
import com.carevalojesus.pokeapi.data.local.PointEventDao
import com.carevalojesus.pokeapi.data.local.PointEventEntity
import com.carevalojesus.pokeapi.util.PokemonNames
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MissionAwardResult(
    val awardedPoints: Int,
    val totalPoints: Int,
    val unlockedPokemonIds: List<Int>
)

class MissionRepository(
    private val pointEventDao: PointEventDao,
    private val userRepository: UserRepository,
    private val ownedPokemonRepository: OwnedPokemonRepository,
    private val unlockRepository: UnlockRepository,
    private val firebaseRepository: FirebaseRepository
) {
    private val pointsConfig: MissionPointsConfig
        get() = MissionPoints.current

    suspend fun onPokemonViewed(pokemonId: Int): MissionAwardResult {
        if (pokemonId <= 0) return noAward()

        var totalAwarded = 0
        totalAwarded += awardOnce(
            eventKey = "view_pokemon_$pokemonId",
            category = "view_pokemon",
            points = pointsConfig.viewPokemonUnique
        )

        val uniqueViewed = pointEventDao.countByPrefix("view_pokemon_")
        if (uniqueViewed > 0 && uniqueViewed % 10 == 0) {
            totalAwarded += awardOnce(
                eventKey = "view_milestone_$uniqueViewed",
                category = "view_milestone",
                points = pointsConfig.viewMilestone10
            )
        }

        return finalizeAward(totalAwarded)
    }

    suspend fun onTradeCompleted(tradeId: String, side: String): MissionAwardResult {
        if (tradeId.isBlank()) return noAward()
        val awarded = awardOnce(
            eventKey = "trade_${side}_$tradeId",
            category = "trade",
            points = pointsConfig.tradeCompleted
        )
        return finalizeAward(awarded)
    }

    suspend fun onRewardQrScanned(campaignId: String): MissionAwardResult {
        if (campaignId.isBlank()) return noAward()
        val awarded = awardOnce(
            eventKey = "reward_scan_$campaignId",
            category = "reward_scan",
            points = pointsConfig.rewardQrScan
        )
        return finalizeAward(awarded)
    }

    suspend fun onDailyLogin(): MissionAwardResult {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val awarded = awardOnce(
            eventKey = "daily_login_$today",
            category = "daily_login",
            points = pointsConfig.dailyLogin
        )
        return finalizeAward(awarded)
    }

    suspend fun onProfileCompleted(): MissionAwardResult {
        val awarded = awardOnce(
            eventKey = "profile_completed_once",
            category = "profile",
            points = pointsConfig.profileCompleted
        )
        return finalizeAward(awarded)
    }

    suspend fun onPokemonFavorited(pokemonId: Int): MissionAwardResult {
        if (pokemonId <= 0) return noAward()
        val awarded = awardOnce(
            eventKey = "favorite_pokemon_$pokemonId",
            category = "favorite",
            points = pointsConfig.favoriteUnique
        )
        return finalizeAward(awarded)
    }

    suspend fun onMarketplaceCompleted(): MissionAwardResult {
        val awarded = awardOnce(
            eventKey = "marketplace_complete_once",
            category = "marketplace",
            points = pointsConfig.marketplaceCompleteBonus
        )
        return finalizeAward(awarded)
    }

    private suspend fun awardOnce(eventKey: String, category: String, points: Int): Int {
        if (points <= 0) return 0
        val rowId = pointEventDao.insert(
            PointEventEntity(
                eventKey = eventKey,
                category = category,
                pointsAwarded = points
            )
        )
        if (rowId == -1L) return 0
        userRepository.addPoints(points)
        return points
    }

    private suspend fun finalizeAward(awardedPoints: Int): MissionAwardResult {
        if (awardedPoints <= 0) {
            val current = userRepository.getPoints()
            return MissionAwardResult(
                awardedPoints = 0,
                totalPoints = current,
                unlockedPokemonIds = emptyList()
            )
        }

        val totalPoints = userRepository.getPoints()
        val unlockedIds = runCatching { applyUnlockThresholds() }.getOrDefault(emptyList())
        runCatching { syncTrainerStats(totalPoints) }
        return MissionAwardResult(
            awardedPoints = awardedPoints,
            totalPoints = totalPoints,
            unlockedPokemonIds = unlockedIds
        )
    }

    private suspend fun applyUnlockThresholds(): List<Int> {
        val points = userRepository.getPoints()
        val unlockedCount = unlockRepository.getAll().snapshotSize()
        val expectedUnlockCount = points / 10
        val missingUnlocks = (expectedUnlockCount - unlockedCount).coerceAtLeast(0)
        if (missingUnlocks == 0) return emptyList()

        val granted = mutableListOf<Int>()
        repeat(missingUnlocks) {
            val unlockId = unlockRepository.getRandomUnlockable() ?: return@repeat
            unlockRepository.unlock(unlockId)
            ownedPokemonRepository.add(
                pokemonId = unlockId,
                nickname = PokemonNames.getName(unlockId),
                obtainedVia = "points_mission"
            )
            granted += unlockId
        }
        return granted
    }

    private suspend fun syncTrainerStats(points: Int) {
        val ownedCount = ownedPokemonRepository.getAll().snapshotSize()
        val unlockedCount = unlockRepository.getAll().snapshotSize()
        firebaseRepository.syncTrainerStats(ownedCount, unlockedCount, points)
    }

    private suspend fun noAward(): MissionAwardResult {
        return MissionAwardResult(
            awardedPoints = 0,
            totalPoints = userRepository.getPoints(),
            unlockedPokemonIds = emptyList()
        )
    }
}

private suspend fun <T> Flow<List<T>>.snapshotSize(): Int = first().size
