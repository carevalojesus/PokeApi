package com.carevalojesus.pokeapi.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trades")
data class TradeEntity(
    @PrimaryKey val tradeId: String,
    val status: String,
    val offeredPokemonId: Int,
    val requestedPokemonId: Int,
    val peerUserId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
