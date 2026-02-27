package com.carevalojesus.pokeapi.data.remote.dto

data class PokemonListResponse(
    val count: Int,
    val results: List<PokemonListEntry>
)

data class PokemonListEntry(
    val name: String,
    val url: String
)
