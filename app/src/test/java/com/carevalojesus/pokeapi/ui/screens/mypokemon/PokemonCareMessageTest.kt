package com.carevalojesus.pokeapi.ui.screens.mypokemon

import com.carevalojesus.pokeapi.data.firebase.PokemonCareState
import org.junit.Assert.assertTrue
import org.junit.Test

class PokemonCareMessageTest {

    @Test
    fun buildPokemonCareMessage_hungryState_mentionsHunger() {
        val state = PokemonCareState(
            pokemonId = 25,
            hunger = 15,
            energy = 70,
            happiness = 70,
            sleeping = false,
            wantsToWakeUp = false,
            updatedAtMillis = 0L
        )

        val message = buildPokemonCareMessage("Pikachu", state)
        assertTrue(message.contains("hambre", ignoreCase = true))
    }

    @Test
    fun buildPokemonCareMessage_sleepingWantsWake_mentionsWake() {
        val state = PokemonCareState(
            pokemonId = 25,
            hunger = 70,
            energy = 96,
            happiness = 70,
            sleeping = true,
            wantsToWakeUp = true,
            updatedAtMillis = 0L
        )

        val message = buildPokemonCareMessage("Pikachu", state)
        assertTrue(message.contains("despert", ignoreCase = true))
    }

    @Test
    fun buildPokemonCareMessage_penalty_mentionsPointsLost() {
        val state = PokemonCareState(
            pokemonId = 25,
            hunger = 0,
            energy = 10,
            happiness = 20,
            sleeping = false,
            wantsToWakeUp = false,
            updatedAtMillis = 0L,
            lastPenaltyPoints = 35
        )

        val message = buildPokemonCareMessage("Pikachu", state)
        assertTrue(message.contains("35"))
        assertTrue(message.contains("puntos", ignoreCase = true))
    }
}
