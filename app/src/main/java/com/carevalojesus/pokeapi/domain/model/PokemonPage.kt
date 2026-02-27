package com.carevalojesus.pokeapi.domain.model

data class PokemonPage(
    val pokemon: List<PokemonItem>,
    val totalCount: Int,
    val hasMore: Boolean
)
