package com.carevalojesus.pokeapi.ui.screens.trade

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeScreen(
    onNavigateToScan: () -> Unit,
    viewModel: TradeViewModel = viewModel()
) {
    val tradeablePokemon by viewModel.tradeablePokemon.collectAsState(initial = emptyList())
    val trades by viewModel.trades.collectAsState(initial = emptyList())
    val uiState by viewModel.uiState.collectAsState()

    var selectedPokemonId by remember { mutableIntStateOf(0) }
    var requestedIdText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Intercambio") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val state = uiState) {
                is TradeUiState.QrGenerated -> {
                    // Show generated QR
                    Text(
                        text = "Muestra este QR al otro entrenador",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = state.bitmap.asImageBitmap(),
                            contentDescription = "QR de intercambio",
                            modifier = Modifier.size(280.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.resetState() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Volver")
                    }
                }

                is TradeUiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.resetState() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Intentar de nuevo")
                    }
                }

                else -> {
                    // Create trade form
                    Text(
                        text = "Selecciona el Pokemon a ofrecer",
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (tradeablePokemon.isEmpty()) {
                        Text(
                            text = "No tienes Pokemon disponibles para intercambiar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(tradeablePokemon) { pokemon ->
                                val isSelected = selectedPokemonId == pokemon.pokemonId
                                val imageUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/${pokemon.pokemonId}.png"
                                Card(
                                    modifier = Modifier
                                        .clickable { selectedPokemonId = pokemon.pokemonId }
                                        .then(
                                            if (isSelected) Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(12.dp)
                                            ) else Modifier
                                        ),
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = if (isSelected) 6.dp else 2.dp
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        AsyncImage(
                                            model = imageUrl,
                                            contentDescription = "#${pokemon.pokemonId}",
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Text(
                                            text = "#${pokemon.pokemonId}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = requestedIdText,
                        onValueChange = { requestedIdText = it.filter { c -> c.isDigit() } },
                        label = { Text("ID del Pokemon solicitado") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            val requestedId = requestedIdText.toIntOrNull() ?: 0
                            if (selectedPokemonId > 0 && requestedId > 0) {
                                viewModel.createTradeOffer(selectedPokemonId, requestedId)
                            }
                        },
                        enabled = selectedPokemonId > 0 && requestedIdText.isNotEmpty()
                                && uiState !is TradeUiState.Creating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (uiState is TradeUiState.Creating) "Generando..."
                            else "Generar QR de intercambio"
                        )
                    }

                    OutlinedButton(
                        onClick = onNavigateToScan,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Escanear QR de intercambio")
                    }

                    // Trade history
                    if (trades.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Historial de intercambios",
                            style = MaterialTheme.typography.titleMedium
                        )
                        trades.forEach { trade ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Ofrecido: #${trade.offeredPokemonId} ↔ Recibido: #${trade.requestedPokemonId}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Estado: ${trade.status}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
