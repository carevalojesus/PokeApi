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
        val unlockedIds = mutableListOf<Int>()
        val firstAward = awardOnce(
            eventKey = "view_pokemon_$pokemonId",
            category = "view_pokemon",
            points = pointsConfig.viewPokemonUnique
        )
        totalAwarded += firstAward.first
        unlockedIds += firstAward.second

        val uniqueViewed = firebaseRepository.countMissionEventsByPrefix("view_pokemon_")
        if (uniqueViewed > 0 && uniqueViewed % 10 == 0) {
            val milestoneAward = awardOnce(
                eventKey = "view_milestone_$uniqueViewed",
                category = "view_milestone",
                points = pointsConfig.viewMilestone10
            )
            totalAwarded += milestoneAward.first
            unlockedIds += milestoneAward.second
        }

        return finalizeAward(totalAwarded, unlockedIds.distinct())
    }

    suspend fun onTradeCompleted(tradeId: String, side: String): MissionAwardResult {
        if (tradeId.isBlank()) return noAward()
        val award = awardOnce(
            eventKey = "trade_${side}_$tradeId",
            category = "trade",
            points = pointsConfig.tradeCompleted
        )
        return finalizeAward(award.first, award.second)
    }

    suspend fun onRewardQrScanned(campaignId: String): MissionAwardResult {
        if (campaignId.isBlank()) return noAward()
        val award = awardOnce(
            eventKey = "reward_scan_$campaignId",
            category = "reward_scan",
            points = pointsConfig.rewardQrScan
        )
        return finalizeAward(award.first, award.second)
    }

    suspend fun onDailyLogin(): MissionAwardResult {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val award = awardOnce(
            eventKey = "daily_login_$today",
            category = "daily_login",
            points = pointsConfig.dailyLogin
        )
        return finalizeAward(award.first, award.second)
    }

    suspend fun onProfileCompleted(): MissionAwardResult {
        val award = awardOnce(
            eventKey = "profile_completed_once",
            category = "profile",
            points = pointsConfig.profileCompleted
        )
        return finalizeAward(award.first, award.second)
    }

    suspend fun onPokemonFavorited(pokemonId: Int): MissionAwardResult {
        if (pokemonId <= 0) return noAward()
        val award = awardOnce(
            eventKey = "favorite_pokemon_$pokemonId",
            category = "favorite",
            points = pointsConfig.favoriteUnique
        )
        return finalizeAward(award.first, award.second)
    }

    suspend fun onMarketplaceCompleted(): MissionAwardResult {
        val award = awardOnce(
            eventKey = "marketplace_complete_once",
            category = "marketplace",
            points = pointsConfig.marketplaceCompleteBonus
        )
        return finalizeAward(award.first, award.second)
    }

    private suspend fun awardOnce(eventKey: String, category: String, points: Int): Pair<Int, List<Int>> {
        if (points <= 0) return 0 to emptyList()
        val remote = firebaseRepository.awardMissionPoints(
            eventKey = eventKey,
            category = category,
            points = points
        )
        userRepository.setLocalPoints(remote.totalPoints)
        if (remote.awardedPoints <= 0) return 0 to emptyList()

        pointEventDao.insert(
            PointEventEntity(
                eventKey = eventKey,
                category = category,
                pointsAwarded = remote.awardedPoints
            )
        )
        syncUnlockedPokemon(remote.unlockedPokemonIds)
        return remote.awardedPoints to remote.unlockedPokemonIds
    }

    private suspend fun finalizeAward(awardedPoints: Int, unlockedIds: List<Int>): MissionAwardResult {
        if (awardedPoints <= 0) {
            val current = userRepository.getPoints()
            return MissionAwardResult(
                awardedPoints = 0,
                totalPoints = current,
                unlockedPokemonIds = emptyList()
            )
        }

        val totalPoints = userRepository.getPoints()
        runCatching { syncTrainerStats(totalPoints) }
        return MissionAwardResult(
            awardedPoints = awardedPoints,
            totalPoints = totalPoints,
            unlockedPokemonIds = unlockedIds.distinct()
        )
    }

    private suspend fun syncUnlockedPokemon(unlockedIds: List<Int>) {
        unlockedIds.forEach { unlockId ->
            unlockRepository.unlock(unlockId)
            ownedPokemonRepository.add(
                pokemonId = unlockId,
                nickname = PokemonNames.getName(unlockId),
                obtainedVia = "points_mission"
            )
        }
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
