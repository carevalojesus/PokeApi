package com.carevalojesus.pokeapi

import android.app.Application
import com.carevalojesus.pokeapi.data.local.PokeDatabase
import com.carevalojesus.pokeapi.data.firebase.FirebaseRepository
import com.carevalojesus.pokeapi.data.repository.FavoritesRepository
import com.carevalojesus.pokeapi.data.repository.PokemonRepository
import com.carevalojesus.pokeapi.data.repository.MarketplaceRepository
import com.carevalojesus.pokeapi.data.repository.MissionRepository
import com.carevalojesus.pokeapi.data.repository.OwnedPokemonRepository
import com.carevalojesus.pokeapi.data.repository.TradeRepository
import com.carevalojesus.pokeapi.data.repository.UnlockRepository
import com.carevalojesus.pokeapi.data.repository.UserRepository
import com.carevalojesus.pokeapi.util.PokemonNames
import android.util.Log
import kotlinx.coroutines.flow.first
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

class PokeApiApplication : Application() {

    val database by lazy { PokeDatabase.getInstance(this) }
    val firebaseRepository by lazy {
        FirebaseRepository(
            firestore = FirebaseFirestore.getInstance(),
            auth = FirebaseAuth.getInstance()
        )
    }
    val favoritesRepository by lazy { FavoritesRepository(database.favoriteDao(), firebaseRepository) }
    val userRepository by lazy { UserRepository(database.userProfileDao(), firebaseRepository) }
    val ownedPokemonRepository by lazy { OwnedPokemonRepository(database.ownedPokemonDao()) }
    val unlockRepository by lazy { UnlockRepository(database.unlockedPokemonDao()) }
    val tradeRepository by lazy { TradeRepository(database.tradeDao()) }
    val missionRepository by lazy {
        MissionRepository(
            pointEventDao = database.pointEventDao(),
            userRepository = userRepository,
            ownedPokemonRepository = ownedPokemonRepository,
            unlockRepository = unlockRepository,
            firebaseRepository = firebaseRepository
        )
    }
    val marketplaceRepository by lazy {
        MarketplaceRepository(
            dao = database.marketplaceItemDao(),
            userRepository = userRepository,
            missionRepository = missionRepository,
            firebaseRepository = firebaseRepository
        )
    }

    suspend fun clearAllLocalData() {
        database.userProfileDao().deleteAll()
        database.ownedPokemonDao().deleteAll()
        database.unlockedPokemonDao().deleteAll()
        database.tradeDao().deleteAll()
        database.favoriteDao().deleteAll()
        database.pointEventDao().deleteAll()
        database.marketplaceItemDao().deleteAll()
        PokemonRepository.clearCache()
    }

    suspend fun syncProgressFromFirebase() {
        repeat(3) { attempt ->
            try {
                syncProgressFromFirebaseOnce()
                return
            } catch (e: Exception) {
                Log.e("PokeApiApplication", "syncProgress attempt ${attempt + 1} failed", e)
                if (attempt < 2) delay(1_000L * (attempt + 1))
            }
        }
    }

    private suspend fun syncProgressFromFirebaseOnce() {
        userRepository.syncProfileFromRemote()
        userRepository.syncPointsFromRemote()

        val unlockedIds = firebaseRepository.getUnlockedPokemonIds()
        unlockedIds.forEach { pokemonId ->
            unlockRepository.unlock(pokemonId)
            if (!ownedPokemonRepository.owns(pokemonId)) {
                ownedPokemonRepository.add(
                    pokemonId = pokemonId,
                    nickname = PokemonNames.getName(pokemonId),
                    obtainedVia = "firebase_sync"
                )
            }
        }

        // Reconcile Firebase inventory with local Room
        runCatching {
            val inventory = firebaseRepository.getCurrentUserInventory()
            val localOwned = ownedPokemonRepository.getAll().first()
            val localCounts = localOwned.groupBy { it.pokemonId }
                .mapValues { it.value.size }

            inventory.forEach { (pokemonId, remoteCount) ->
                val localCount = localCounts[pokemonId] ?: 0
                val missing = remoteCount - localCount
                repeat(missing.coerceAtLeast(0)) {
                    ownedPokemonRepository.add(
                        pokemonId = pokemonId,
                        nickname = PokemonNames.getName(pokemonId),
                        obtainedVia = "firebase_reconcile"
                    )
                }
            }
        }

        runCatching { favoritesRepository.syncFromFirebase() }
        runCatching { marketplaceRepository.syncFromFirebase() }
    }
}
