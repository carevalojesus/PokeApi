package com.carevalojesus.pokeapi.ui.screens.profile

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.data.local.OwnedPokemonEntity
import com.carevalojesus.pokeapi.data.local.TradeEntity
import com.carevalojesus.pokeapi.data.local.UnlockedPokemonEntity
import com.carevalojesus.pokeapi.data.local.UserProfileEntity
import com.carevalojesus.pokeapi.data.repository.MarketplaceCatalog
import com.carevalojesus.pokeapi.data.repository.MarketplaceItem
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PokeApiApplication

    val profile: Flow<UserProfileEntity?> = app.userRepository.getProfile()
    val ownedPokemon: Flow<List<OwnedPokemonEntity>> = app.ownedPokemonRepository.getAll()
    val unlockedPokemon: Flow<List<UnlockedPokemonEntity>> = app.unlockRepository.getAll()
    val trades: Flow<List<TradeEntity>> = app.tradeRepository.getAll()
    val equippedMarketplaceItems: Flow<List<MarketplaceItem>> = app.marketplaceRepository
        .getEquippedItemIdsFlow()
        .map { equippedIds ->
            MarketplaceCatalog.items.filter { it.id in equippedIds.toSet() }
        }

    fun savePersonalInfo(firstName: String, lastName: String, birthDate: String, gender: String) {
        viewModelScope.launch {
            app.userRepository.updatePersonalInfo(firstName, lastName, birthDate, gender)
            app.firebaseRepository.updateTrainerPersonalInfo(firstName, lastName, birthDate, gender)
        }
    }

    fun saveProfilePhoto(uri: Uri) {
        viewModelScope.launch {
            val context: Context = getApplication()
            val destFile = File(context.filesDir, "profile_photo.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            app.userRepository.updateProfilePhoto(destFile.absolutePath)
        }
    }
}
