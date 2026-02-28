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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PokeApiApplication : Application() {

    val database by lazy { PokeDatabase.getInstance(this) }
    val favoritesRepository by lazy { FavoritesRepository(database.favoriteDao()) }
    val firebaseRepository by lazy {
        FirebaseRepository(
            firestore = FirebaseFirestore.getInstance(),
            auth = FirebaseAuth.getInstance()
        )
    }
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
            missionRepository = missionRepository
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
    }
}
