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
}
