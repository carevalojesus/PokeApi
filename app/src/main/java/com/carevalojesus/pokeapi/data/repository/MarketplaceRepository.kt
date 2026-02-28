package com.carevalojesus.pokeapi.data.repository

import com.carevalojesus.pokeapi.data.local.MarketplaceItemDao
import com.carevalojesus.pokeapi.data.local.MarketplaceItemEntity
import kotlinx.coroutines.flow.Flow

sealed interface MarketplacePurchaseResult {
    data class Success(
        val remainingPoints: Int,
        val awardedCompletionBonus: Int = 0
    ) : MarketplacePurchaseResult

    data object AlreadyOwned : MarketplacePurchaseResult
    data object NotEnoughPoints : MarketplacePurchaseResult
    data class Error(val message: String) : MarketplacePurchaseResult
}

sealed interface MarketplaceEquipResult {
    data object Success : MarketplaceEquipResult
    data object NotOwned : MarketplaceEquipResult
    data class Error(val message: String) : MarketplaceEquipResult
}

class MarketplaceRepository(
    private val dao: MarketplaceItemDao,
    private val userRepository: UserRepository,
    private val missionRepository: MissionRepository
) {

    fun getOwnedItemIdsFlow(): Flow<List<String>> = dao.getOwnedItemIdsFlow()
    fun getEquippedItemIdsFlow(): Flow<List<String>> = dao.getEquippedItemIdsFlow()

    suspend fun buyItem(item: MarketplaceItem): MarketplacePurchaseResult {
        return runCatching {
            val spent = userRepository.spendPoints(item.cost)
            if (!spent) {
                return MarketplacePurchaseResult.NotEnoughPoints
            }

            val inserted = dao.insert(
                MarketplaceItemEntity(
                    itemId = item.id,
                    category = item.category.name,
                    equipped = false
                )
            )
            if (inserted == -1L) {
                userRepository.refundPoints(item.cost)
                return MarketplacePurchaseResult.AlreadyOwned
            }

            var completionBonus = 0
            val ownedCount = dao.countOwnedItems()
            if (ownedCount >= MarketplaceCatalog.items.size) {
                val bonusResult = missionRepository.onMarketplaceCompleted()
                completionBonus = bonusResult.awardedPoints
            }

            MarketplacePurchaseResult.Success(
                remainingPoints = userRepository.getPoints(),
                awardedCompletionBonus = completionBonus
            )
        }.getOrElse { error ->
            MarketplacePurchaseResult.Error(error.message ?: "No se pudo comprar el artículo")
        }
    }

    suspend fun equipItem(item: MarketplaceItem): MarketplaceEquipResult {
        return runCatching {
            val category = dao.getCategoryForItem(item.id) ?: return MarketplaceEquipResult.NotOwned
            dao.unequipCategory(category)
            dao.equipItem(item.id)
            MarketplaceEquipResult.Success
        }.getOrElse { error ->
            MarketplaceEquipResult.Error(error.message ?: "No se pudo equipar el artículo")
        }
    }
}
