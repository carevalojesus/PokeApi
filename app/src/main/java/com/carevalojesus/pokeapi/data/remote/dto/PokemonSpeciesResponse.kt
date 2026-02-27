package com.carevalojesus.pokeapi.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PokemonSpeciesResponse(
    val names: List<SpeciesName>,
    val genera: List<Genus>,
    @SerializedName("flavor_text_entries")
    val flavorTextEntries: List<FlavorTextEntry>
)

data class SpeciesName(
    val name: String,
    val language: Language
)

data class Genus(
    val genus: String,
    val language: Language
)

data class FlavorTextEntry(
    @SerializedName("flavor_text")
    val flavorText: String,
    val language: Language
)

data class Language(
    val name: String
)
