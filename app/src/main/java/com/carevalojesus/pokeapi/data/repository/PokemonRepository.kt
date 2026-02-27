package com.carevalojesus.pokeapi.data.repository

import com.carevalojesus.pokeapi.data.remote.RetrofitClient
import com.carevalojesus.pokeapi.domain.model.PokemonDetail
import com.carevalojesus.pokeapi.domain.model.PokemonItem
import com.carevalojesus.pokeapi.domain.model.PokemonPage
import com.carevalojesus.pokeapi.domain.model.Stat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class PokemonRepository {

    private val api = RetrofitClient.api

    private val typeTranslations = mapOf(
        "normal" to "Normal",
        "fire" to "Fuego",
        "water" to "Agua",
        "electric" to "Eléctrico",
        "grass" to "Planta",
        "ice" to "Hielo",
        "fighting" to "Lucha",
        "poison" to "Veneno",
        "ground" to "Tierra",
        "flying" to "Volador",
        "psychic" to "Psíquico",
        "bug" to "Bicho",
        "rock" to "Roca",
        "ghost" to "Fantasma",
        "dragon" to "Dragón",
        "dark" to "Siniestro",
        "steel" to "Acero",
        "fairy" to "Hada"
    )

    private val statTranslations = mapOf(
        "hp" to "PS",
        "attack" to "Ataque",
        "defense" to "Defensa",
        "special-attack" to "At. Esp.",
        "special-defense" to "Def. Esp.",
        "speed" to "Velocidad"
    )

    suspend fun getPokemonList(): List<PokemonItem> {
        val response = api.getPokemonList()
        return response.results.map { entry ->
            val id = entry.url.trimEnd('/').split("/").last().toInt()
            PokemonItem(
                id = id,
                name = entry.name.replaceFirstChar { it.uppercase() },
                imageUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/$id.png"
            )
        }
    }

    suspend fun getPokemonPage(offset: Int, limit: Int = 20): PokemonPage = coroutineScope {
        val response = api.getPokemonList(limit = limit, offset = offset)
        val items = response.results.map { entry ->
            val id = entry.url.trimEnd('/').split("/").last().toInt()
            async {
                try {
                    val detail = api.getPokemonDetail(id)
                    PokemonItem(
                        id = id,
                        name = entry.name.replaceFirstChar { it.uppercase() },
                        imageUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/$id.png",
                        types = detail.types.map { it.type.name }
                    )
                } catch (_: Exception) {
                    PokemonItem(
                        id = id,
                        name = entry.name.replaceFirstChar { it.uppercase() },
                        imageUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/$id.png"
                    )
                }
            }
        }.awaitAll()
        PokemonPage(
            pokemon = items,
            totalCount = response.count,
            hasMore = offset + limit < response.count
        )
    }

    suspend fun getPokemonDetail(id: Int): PokemonDetail = coroutineScope {
        val detailDeferred = async { api.getPokemonDetail(id) }
        val speciesDeferred = async { api.getPokemonSpecies(id) }

        val response = detailDeferred.await()
        val species = speciesDeferred.await()

        val spanishName = species.names
            .firstOrNull { it.language.name == "es" }?.name
            ?: response.name.replaceFirstChar { it.uppercase() }

        val genus = species.genera
            .firstOrNull { it.language.name == "es" }?.genus ?: ""

        val description = species.flavorTextEntries
            .lastOrNull { it.language.name == "es" }?.flavorText
            ?.replace("\n", " ")
            ?.replace("\u000c", " ")
            ?: ""

        PokemonDetail(
            id = response.id,
            name = spanishName,
            height = response.height,
            weight = response.weight,
            types = response.types.map { typeSlot ->
                typeTranslations[typeSlot.type.name] ?: typeSlot.type.name.replaceFirstChar { it.uppercase() }
            },
            imageUrl = response.sprites.other?.officialArtwork?.frontDefault
                ?: response.sprites.frontDefault ?: "",
            stats = response.stats.map { statSlot ->
                Stat(
                    name = statTranslations[statSlot.stat.name] ?: statSlot.stat.name,
                    value = statSlot.baseStat
                )
            },
            genus = genus,
            description = description
        )
    }
}
