package com.carevalojesus.pokeapi.ui.screens.trade

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.net.URLDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeConfirmScreen(
    tradeJson: String,
    onComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: TradeViewModel = viewModel()
) {
    val decoded = URLDecoder.decode(tradeJson, "UTF-8")
    val tradeOffer = viewModel.parseTradeOffer(decoded)
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirmar intercambio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (tradeOffer == null) {
                Text(
                    text = "Error: QR invalido",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedButton(onClick = onBack) {
                    Text("Volver")
                }
                return@Scaffold
            }

            when (val state = uiState) {
                is TradeUiState.QrGenerated -> {
                    // Show confirmation QR for peer A to scan
                    Text(
                        text = "Intercambio aceptado!",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Muestra este QR al otro entrenador para completar el intercambio",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Image(
                        bitmap = state.bitmap.asImageBitmap(),
                        contentDescription = "QR de confirmacion",
                        modifier = Modifier.size(280.dp)
                    )
                    Button(
                        onClick = onComplete,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Finalizar")
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
                    OutlinedButton(onClick = onBack) {
                        Text("Volver")
                    }
                }

                else -> {
                    if (tradeOffer.isConfirmation) {
                        // This is a confirmation QR scanned by peer A
                        Text(
                            text = "Confirmacion de intercambio",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "El otro entrenador acepto tu intercambio",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PokemonTradeCard(
                                pokemonId = tradeOffer.offerPokemonId,
                                label = "Recibiras"
                            )
                            Text(
                                text = "↔",
                                style = MaterialTheme.typography.headlineLarge
                            )
                            PokemonTradeCard(
                                pokemonId = tradeOffer.requestPokemonId,
                                label = "Entregaras"
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.completeTradeFromConfirmation(tradeOffer)
                                onComplete()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Completar intercambio")
                        }
                    } else {
                        // This is a trade offer QR scanned by peer B
                        Text(
                            text = "Oferta de intercambio",
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PokemonTradeCard(
                                pokemonId = tradeOffer.offerPokemonId,
                                label = "Te ofrecen"
                            )
                            Text(
                                text = "↔",
                                style = MaterialTheme.typography.headlineLarge
                            )
                            PokemonTradeCard(
                                pokemonId = tradeOffer.requestPokemonId,
                                label = "Te piden"
                            )
                        }

                        Button(
                            onClick = { viewModel.acceptTrade(tradeOffer) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Aceptar intercambio")
                        }

                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Rechazar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PokemonTradeCard(pokemonId: Int, label: String) {
    val imageUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/$pokemonId.png"
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Pokemon #$pokemonId",
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "#$pokemonId",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
