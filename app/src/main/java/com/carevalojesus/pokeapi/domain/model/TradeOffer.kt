package com.carevalojesus.pokeapi.domain.model

data class TradeOffer(
    val tradeId: String,
    val fromUserId: String,
    val offerPokemonId: Int,
    val requestPokemonId: Int,
    val offerPokemonName: String = "",
    val requestPokemonName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val nonce: String = "",
    val isConfirmation: Boolean = false
)
