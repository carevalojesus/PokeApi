package com.carevalojesus.pokeapi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MarketplaceItemDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: MarketplaceItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MarketplaceItemEntity)

    @Query("SELECT itemId FROM marketplace_items")
    fun getOwnedItemIdsFlow(): Flow<List<String>>

    @Query("SELECT itemId FROM marketplace_items")
    suspend fun getOwnedItemIdsOnce(): List<String>

    @Query("SELECT itemId FROM marketplace_items WHERE equipped = 1")
    fun getEquippedItemIdsFlow(): Flow<List<String>>

    @Query("SELECT itemId FROM marketplace_items WHERE equipped = 1")
    suspend fun getEquippedItemIdsOnce(): List<String>

    @Query("SELECT COUNT(*) FROM marketplace_items")
    suspend fun countOwnedItems(): Int

    @Query("SELECT category FROM marketplace_items WHERE itemId = :itemId LIMIT 1")
    suspend fun getCategoryForItem(itemId: String): String?

    @Query("UPDATE marketplace_items SET equipped = 0 WHERE category = :category")
    suspend fun unequipCategory(category: String)

    @Query("UPDATE marketplace_items SET equipped = 1 WHERE itemId = :itemId")
    suspend fun equipItem(itemId: String)

    @Query("DELETE FROM marketplace_items WHERE itemId = :itemId")
    suspend fun deleteById(itemId: String)

    @Query("DELETE FROM marketplace_items")
    suspend fun deleteAll()
}
