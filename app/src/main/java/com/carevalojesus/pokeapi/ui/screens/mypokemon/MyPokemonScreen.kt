package com.carevalojesus.pokeapi.ui.screens.mypokemon

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.carevalojesus.pokeapi.data.repository.AggregatedPokemon
import com.carevalojesus.pokeapi.data.firebase.PokemonCareState
import com.carevalojesus.pokeapi.util.PokemonNames

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MyPokemonScreen(
    onPokemonClick: (Int) -> Unit,
    viewModel: MyPokemonViewModel = viewModel()
) {
    val aggregatedPokemon by viewModel.aggregatedPokemon.collectAsState()
    val profile by viewModel.profile.collectAsState(initial = null)
    val starterChangeResult by viewModel.starterChangeResult.collectAsState()
    val careState by viewModel.careState.collectAsState()

    var pokemonToChangeStarter by remember { mutableStateOf<AggregatedPokemon?>(null) }
    var pokemonForCare by remember { mutableStateOf<AggregatedPokemon?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(starterChangeResult) {
        when (val result = starterChangeResult) {
            is StarterChangeResult.Success -> {
                snackbarHostState.showSnackbar(
                    "¡Pokémon principal cambiado! Te quedan ${result.changesRemaining} cambios."
                )
                viewModel.clearStarterChangeResult()
            }
            is StarterChangeResult.NoChangesLeft -> {
                snackbarHostState.showSnackbar(
                    "Ya no te quedan cambios de Pokémon principal."
                )
                viewModel.clearStarterChangeResult()
            }
            is StarterChangeResult.Error -> {
                snackbarHostState.showSnackbar("Error al cambiar Pokémon principal.")
                viewModel.clearStarterChangeResult()
            }
            null -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis Pokémon") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (aggregatedPokemon.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aún no tienes Pokémon.\nVisita la Pokédex para desbloquear más.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(aggregatedPokemon, key = { it.pokemonId }) { pokemon ->
                    val name = pokemon.nickname.ifEmpty {
                        PokemonNames.getName(pokemon.pokemonId)
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp)
                            .combinedClickable(
                                onClick = {
                                    if (pokemon.isStarter) {
                                        pokemonForCare = pokemon
                                        viewModel.loadPokemonCare(pokemon.pokemonId)
                                    } else {
                                        onPokemonClick(pokemon.pokemonId)
                                    }
                                },
                                onLongClick = {
                                    if (!pokemon.isStarter) {
                                        pokemonToChangeStarter = pokemon
                                    }
                                }
                            ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // Quantity badge (top-start)
                            if (pokemon.count > 1) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                ) {
                                    Text("x${pokemon.count}")
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    model = PokemonNames.getImageUrl(pokemon.pokemonId),
                                    contentDescription = name,
                                    modifier = Modifier.size(80.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "#${pokemon.pokemonId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleSmall,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (!pokemon.isStarter) {
                                    Text(
                                        text = "Mantener para hacer principal",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            // Badges (top-end)
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (pokemon.isStarter) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text("Principal")
                                    }
                                }
                                if (pokemon.hasNewFromTrade) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.tertiary
                                    ) {
                                        Text("Intercambio")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pokemonForCare?.let { pokemon ->
        PokemonCareDialog(
            pokemon = pokemon,
            careState = careState,
            onDismiss = {
                pokemonForCare = null
                viewModel.clearCareError()
            },
            onFeed = { viewModel.feedPokemon(pokemon.pokemonId) },
            onSleep = { viewModel.startSleep(pokemon.pokemonId) },
            onWake = { viewModel.wakePokemon(pokemon.pokemonId) },
            onOpenDetail = { onPokemonClick(pokemon.pokemonId) }
        )
    }

    // Dialog to confirm starter change
    pokemonToChangeStarter?.let { pokemon ->
        val remaining = profile?.starterChangesRemaining ?: 3
        val pokemonName = pokemon.nickname.ifEmpty { PokemonNames.getName(pokemon.pokemonId) }

        AlertDialog(
            onDismissRequest = { pokemonToChangeStarter = null },
            title = { Text("Cambiar Pokémon principal") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = PokemonNames.getImageUrl(pokemon.pokemonId),
                            contentDescription = pokemonName,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = pokemonName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "#${pokemon.pokemonId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    if (remaining > 0) {
                        Text(
                            text = "¿Quieres que $pokemonName sea tu Pokémon principal?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Te quedan $remaining cambios disponibles.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Ya no te quedan cambios de Pokémon principal.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.changeStarter(pokemon.representativeId, pokemon.pokemonId)
                        pokemonToChangeStarter = null
                    },
                    enabled = remaining > 0
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pokemonToChangeStarter = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun PokemonCareDialog(
    pokemon: AggregatedPokemon,
    careState: PokemonCareUiState,
    onDismiss: () -> Unit,
    onFeed: () -> Unit,
    onSleep: () -> Unit,
    onWake: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val name = pokemon.nickname.ifEmpty { PokemonNames.getName(pokemon.pokemonId) }
    val state = careState.state

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cuidar a $name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (careState.isLoading && state == null) {
                    Text("Cargando estado...")
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else if (state != null) {
                    StatBar("Hambre", state.hunger)
                    StatBar("Energía", state.energy)
                    StatBar("Ánimo", state.happiness)
                    Text(
                        text = careState.aiMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (careState.lastAwardedPoints > 0) {
                        Text(
                            text = "+${careState.lastAwardedPoints} puntos (total ${careState.totalPoints})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if ((state.lastPenaltyPoints) > 0) {
                        Text(
                            text = "-${state.lastPenaltyPoints} puntos por descuido",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (state.pokemonLost) {
                        Text(
                            text = "Perdiste 1 copia de este Pokémon por descuido",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (!careState.error.isNullOrBlank()) {
                    Text(
                        text = careState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onFeed,
                        enabled = !careState.isLoading && state != null && !(state.sleeping),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Dar comida")
                    }
                    Button(
                        onClick = if (state?.sleeping == true) onWake else onSleep,
                        enabled = !careState.isLoading && state != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (state?.sleeping == true) "Despertar" else "Dormir")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenDetail) {
                Text("Ver detalle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
private fun StatBar(label: String, value: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text("$value%", style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(
            progress = { (value.coerceIn(0, 100) / 100f) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
