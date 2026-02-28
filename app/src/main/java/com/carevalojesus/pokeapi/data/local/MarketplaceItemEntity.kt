package com.carevalojesus.pokeapi.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "marketplace_items")
data class MarketplaceItemEntity(
    @PrimaryKey val itemId: String,
    val category: String,
    val equipped: Boolean = false,
    val purchasedAt: Long = System.currentTimeMillis()
)
