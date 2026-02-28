package com.carevalojesus.pokeapi.ui.screens.trade

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.carevalojesus.pokeapi.util.PokemonNames

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeConfirmScreen(
    tradeId: String,
    onComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: TradeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(tradeId) {
        if (tradeId.isNotBlank()) {
            viewModel.fetchTradeFromFirestore(tradeId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Intercambio") },
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
            if (tradeId.isBlank()) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "QR inválido",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "No se pudo leer la información del intercambio",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onBack) {
                    Text("Volver")
                }
                return@Scaffold
            }

            when (val state = uiState) {
                is TradeUiState.Loading -> {
                    Spacer(modifier = Modifier.height(48.dp))
                    CircularProgressIndicator()
                    Text(
                        text = "Cargando intercambio...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is TradeUiState.Creating -> {
                    Spacer(modifier = Modifier.height(48.dp))
                    CircularProgressIndicator()
                    Text(
                        text = "Procesando...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is TradeUiState.TradeLoaded -> {
                    val trade = state.trade
                    val uid = state.currentUserUid

                    when {
                        // completed
                        trade.status == "completed" -> {
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(
                                text = "Intercambio completado",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Este intercambio ya fue completado",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = onBack) {
                                Text("Volver")
                            }
                        }

                        // pending + I am creator → waiting
                        trade.status == "pending" && uid == trade.creatorUid -> {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Esperando aceptación",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Esperando que alguien escanee tu QR y acepte el intercambio",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            TradeVisualization(
                                leftPokemonId = trade.offerPokemonId,
                                leftPokemonName = trade.offerPokemonName,
                                leftLabel = "Compartes",
                                rightPokemonId = trade.requestPokemonId,
                                rightPokemonName = trade.requestPokemonName,
                                rightLabel = "Recibes"
                            )

                            OutlinedButton(onClick = onBack) {
                                Text("Volver")
                            }
                        }

                        // pending + I am not creator → accept trade
                        trade.status == "pending" && uid != trade.creatorUid -> {
                            Text(
                                text = "Oferta de intercambio",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Un entrenador quiere intercambiar contigo",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            TradeVisualization(
                                leftPokemonId = trade.offerPokemonId,
                                leftPokemonName = trade.offerPokemonName,
                                leftLabel = "Te comparte",
                                rightPokemonId = trade.requestPokemonId,
                                rightPokemonName = trade.requestPokemonName,
                                rightLabel = "Quiere de ti"
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    text = "Entregarás una copia de tu Pokémon y recibirás una del otro entrenador.",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { viewModel.acceptTrade(tradeId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Aceptar intercambio",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }

                            OutlinedButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Rechazar")
                            }
                        }

                        // accepted + I am creator → complete trade
                        trade.status == "accepted" && uid == trade.creatorUid -> {
                            Text(
                                text = "Confirmación recibida",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "El otro entrenador aceptó tu intercambio",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            TradeVisualization(
                                leftPokemonId = trade.requestPokemonId,
                                leftPokemonName = trade.requestPokemonName,
                                leftLabel = "Recibirás",
                                rightPokemonId = trade.offerPokemonId,
                                rightPokemonName = trade.offerPokemonName,
                                rightLabel = "Compartiste"
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    text = "Entregarás una copia de tu Pokémon a cambio de una del otro entrenador.",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { viewModel.completeTrade(tradeId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Completar intercambio",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }

                        // accepted + I am acceptor → waiting for creator
                        trade.status == "accepted" && uid == trade.acceptorUid -> {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Esperando confirmación",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Esperando que el creador del intercambio escanee el QR y complete",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            OutlinedButton(onClick = onBack) {
                                Text("Volver")
                            }
                        }

                        else -> {
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(
                                text = "Estado desconocido",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            OutlinedButton(onClick = onBack) {
                                Text("Volver")
                            }
                        }
                    }
                }

                is TradeUiState.AcceptedShowQr -> {
                    AnimatedVisibility(
                        visible = true,
                        enter = scaleIn() + fadeIn()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "¡Intercambio aceptado!",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = PokemonNames.getImageUrl(state.trade.offerPokemonId),
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Recibiste a ${state.trade.offerPokemonName.ifEmpty { PokemonNames.getName(state.trade.offerPokemonId) }}!",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Se agregó a tu colección",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Ahora muestra este QR al otro entrenador para que reciba su Pokémon",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Card(
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Box(modifier = Modifier.padding(16.dp)) {
                                    Image(
                                        bitmap = state.bitmap.asImageBitmap(),
                                        contentDescription = "QR de confirmación",
                                        modifier = Modifier.size(260.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = onComplete,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Finalizar")
                            }
                        }
                    }
                }

                is TradeUiState.TradeSuccess -> {
                    AnimatedVisibility(
                        visible = true,
                        enter = scaleIn() + fadeIn()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "¡Intercambio completado!",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                MaterialTheme.colorScheme.surface
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = PokemonNames.getImageUrl(state.receivedPokemonId),
                                    contentDescription = state.receivedPokemonName,
                                    modifier = Modifier.size(140.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "¡Obtuviste a ${state.receivedPokemonName}!",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "#${state.receivedPokemonId}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Se agregó a tu colección y Pokédex",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = onComplete,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Continuar")
                            }
                        }
                    }
                }

                is TradeUiState.Error -> {
                    Spacer(modifier = Modifier.height(16.dp))
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
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Volver")
                    }
                }

                else -> {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "Cargando...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TradeVisualization(
    leftPokemonId: Int,
    leftPokemonName: String,
    leftLabel: String,
    rightPokemonId: Int,
    rightPokemonName: String,
    rightLabel: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TradePokemonDisplay(
                pokemonId = leftPokemonId,
                pokemonName = leftPokemonName,
                label = leftLabel
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "↔",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            TradePokemonDisplay(
                pokemonId = rightPokemonId,
                pokemonName = rightPokemonName,
                label = rightLabel
            )
        }
    }
}

@Composable
private fun TradePokemonDisplay(pokemonId: Int, pokemonName: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = PokemonNames.getImageUrl(pokemonId),
                contentDescription = pokemonName,
                modifier = Modifier.size(80.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = pokemonName.ifEmpty { PokemonNames.getName(pokemonId) },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "#$pokemonId",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}
