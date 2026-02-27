package com.carevalojesus.pokeapi.domain.model

data class PokemonDetail(
    val id: Int,
    val name: String,
    val height: Int,
    val weight: Int,
    val types: List<String>,
    val rawTypes: List<String> = emptyList(),
    val imageUrl: String,
    val stats: List<Stat>,
    val genus: String,
    val description: String
)

data class Stat(
    val name: String,
    val value: Int
)
