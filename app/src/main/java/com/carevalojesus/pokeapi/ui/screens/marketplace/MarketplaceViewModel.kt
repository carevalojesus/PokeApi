package com.carevalojesus.pokeapi.ui.screens.marketplace

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.data.repository.MarketplaceCatalog
import com.carevalojesus.pokeapi.data.repository.MarketplaceEquipResult
import com.carevalojesus.pokeapi.data.repository.MarketplacePurchaseResult
import com.carevalojesus.pokeapi.data.repository.MissionPoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MarketplaceViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PokeApiApplication
    private val marketplaceRepository = app.marketplaceRepository
    private val userRepository = app.userRepository

    val catalog = MarketplaceCatalog.items
    val completionBonus = MissionPoints.current.marketplaceCompleteBonus

    val points: StateFlow<Int> = userRepository.getProfile()
        .map { it?.points ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val ownedItemIds: StateFlow<Set<String>> = marketplaceRepository.getOwnedItemIdsFlow()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val equippedItemIds: StateFlow<Set<String>> = marketplaceRepository.getEquippedItemIdsFlow()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _processingItemId = MutableStateFlow<String?>(null)
    val processingItemId: StateFlow<String?> = _processingItemId

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback

    fun buy(itemId: String) {
        val item = catalog.firstOrNull { it.id == itemId } ?: return
        if (_processingItemId.value != null) return

        viewModelScope.launch {
            _processingItemId.value = itemId
            when (val result = marketplaceRepository.buyItem(item)) {
                is MarketplacePurchaseResult.Success -> {
                    _feedback.value = if (result.awardedCompletionBonus > 0) {
                        "Comprado: ${item.name}. Bono colección completa +${result.awardedCompletionBonus} puntos."
                    } else {
                        "Comprado: ${item.name}. Te quedan ${result.remainingPoints} puntos."
                    }
                }

                MarketplacePurchaseResult.AlreadyOwned -> {
                    _feedback.value = "Ya tienes este artículo."
                }

                MarketplacePurchaseResult.NotEnoughPoints -> {
                    _feedback.value = "No tienes puntos suficientes."
                }

                is MarketplacePurchaseResult.Error -> {
                    _feedback.value = result.message
                }
            }
            _processingItemId.value = null
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun equip(itemId: String) {
        val item = catalog.firstOrNull { it.id == itemId } ?: return
        if (_processingItemId.value != null) return

        viewModelScope.launch {
            _processingItemId.value = itemId
            when (val result = marketplaceRepository.equipItem(item)) {
                MarketplaceEquipResult.Success -> {
                    _feedback.value = "Equipado: ${item.name}."
                }
                MarketplaceEquipResult.NotOwned -> {
                    _feedback.value = "Debes comprar este artículo antes de equiparlo."
                }
                is MarketplaceEquipResult.Error -> {
                    _feedback.value = result.message
                }
            }
            _processingItemId.value = null
        }
    }
}
