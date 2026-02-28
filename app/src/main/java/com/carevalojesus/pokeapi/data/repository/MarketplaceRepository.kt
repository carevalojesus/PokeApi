package com.carevalojesus.pokeapi.data.repository

import com.carevalojesus.pokeapi.data.firebase.FirebaseRepository
import com.carevalojesus.pokeapi.data.firebase.MarketplaceItemFirebase
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
    private val missionRepository: MissionRepository,
    private val firebaseRepository: FirebaseRepository
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

            runCatching { firebaseRepository.addMarketplaceItem(item.id, item.category.name) }

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
            runCatching {
                firebaseRepository.updateMarketplaceItemEquipped(item.id, category, true)
            }
            MarketplaceEquipResult.Success
        }.getOrElse { error ->
            MarketplaceEquipResult.Error(error.message ?: "No se pudo equipar el artículo")
        }
    }

    suspend fun syncFromFirebase() {
        val remoteItems = firebaseRepository.getMarketplaceItems()
        val localIds = dao.getOwnedItemIdsOnce().toSet()
        val plan = buildMarketplaceSyncPlan(localIds, remoteItems)

        if (plan.remoteEntities.isEmpty()) {
            dao.deleteAll()
            return
        }

        plan.remoteEntities.forEach { entity ->
            dao.upsert(entity)
        }
        plan.idsToDelete.forEach { itemId ->
            dao.deleteById(itemId)
        }
        plan.equippedByCategory.forEach { (category, equippedItemId) ->
            dao.unequipCategory(category)
            dao.equipItem(equippedItemId)
        }
    }
}

internal data class MarketplaceSyncPlan(
    val remoteEntities: List<MarketplaceItemEntity>,
    val idsToDelete: Set<String>,
    val equippedByCategory: Map<String, String>
)

internal fun buildMarketplaceSyncPlan(
    localIds: Set<String>,
    remoteItems: List<MarketplaceItemFirebase>
): MarketplaceSyncPlan {
    val remoteIds = remoteItems.map { it.itemId }.toSet()
    val idsToDelete = localIds - remoteIds

    val remoteEntities = remoteItems.map { item ->
        MarketplaceItemEntity(
            itemId = item.itemId,
            category = item.category,
            equipped = item.equipped,
            purchasedAt = item.purchasedAt * 1000L
        )
    }

    val equippedByCategory = remoteItems
        .filter { it.equipped }
        .groupBy { it.category }
        .mapValues { (_, items) ->
            items.maxByOrNull { it.purchasedAt }!!.itemId
        }

    return MarketplaceSyncPlan(
        remoteEntities = remoteEntities,
        idsToDelete = idsToDelete,
        equippedByCategory = equippedByCategory
    )
}
