package com.carevalojesus.pokeapi.ui.screens.trade

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.carevalojesus.pokeapi.util.PokemonNames

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeScreen(
    onNavigateToScan: () -> Unit,
    onNavigateToRewardScan: () -> Unit,
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
                    // Trade summary + QR
                    Text(
                        text = "Muestra este QR al otro entrenador",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Visual summary of the trade
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TradePreviewCard(
                                pokemonId = state.offerPokemonId,
                                pokemonName = state.offerPokemonName,
                                label = "Compartes"
                            )
                            Text(
                                text = "↔",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TradePreviewCard(
                                pokemonId = state.requestPokemonId,
                                pokemonName = state.requestPokemonName,
                                label = "Recibes"
                            )
                        }
                    }

                    // QR code
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Box(modifier = Modifier.padding(16.dp)) {
                                Image(
                                    bitmap = state.bitmap.asImageBitmap(),
                                    contentDescription = "QR de intercambio",
                                    modifier = Modifier.size(260.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Entregarás una copia de tu Pokémon y recibirás una del otro entrenador.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedButton(
                        onClick = { viewModel.resetState() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Volver")
                    }
                }

                is TradeUiState.TradeSuccess -> {
                    // Success celebration
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
                                text = "¡Intercambio exitoso!",
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
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { viewModel.resetState() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Continuar")
                            }
                        }
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
                        text = "Selecciona tu Pokémon",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (tradeablePokemon.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = "No tienes Pokémon con copias suficientes para intercambiar. Necesitas al menos 2 copias de un Pokémon.",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(tradeablePokemon) { pokemon ->
                                val isSelected = selectedPokemonId == pokemon.pokemonId
                                val name = pokemon.nickname.ifEmpty {
                                    PokemonNames.getName(pokemon.pokemonId)
                                }
                                Card(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .clickable { selectedPokemonId = pokemon.pokemonId }
                                        .then(
                                            if (isSelected) Modifier.border(
                                                3.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(12.dp)
                                            ) else Modifier
                                        ),
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = if (isSelected) 8.dp else 2.dp
                                    ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Box {
                                        Column(
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            AsyncImage(
                                                model = PokemonNames.getImageUrl(pokemon.pokemonId),
                                                contentDescription = name,
                                                modifier = Modifier.size(72.dp)
                                            )
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "#${pokemon.pokemonId}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        // Quantity badge
                                        Text(
                                            text = "x${pokemon.count}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondary,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.secondary,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Requested Pokemon section
                    Text(
                        text = "Pokémon que deseas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = requestedIdText,
                        onValueChange = { requestedIdText = it.filter { c -> c.isDigit() } },
                        label = { Text("Número del Pokémon (1-151)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Real-time preview of requested Pokemon
                    val requestedId = requestedIdText.toIntOrNull()
                    if (requestedId != null && requestedId in 1..151) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = PokemonNames.getImageUrl(requestedId),
                                    contentDescription = PokemonNames.getName(requestedId),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = PokemonNames.getName(requestedId),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "#$requestedId",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Trade summary before generating
                    if (selectedPokemonId > 0 && requestedId != null && requestedId in 1..151) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Resumen del intercambio",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AsyncImage(
                                            model = PokemonNames.getImageUrl(selectedPokemonId),
                                            contentDescription = null,
                                            modifier = Modifier.size(56.dp)
                                        )
                                        Text(
                                            text = PokemonNames.getName(selectedPokemonId),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Text(
                                        text = "↔",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AsyncImage(
                                            model = PokemonNames.getImageUrl(requestedId),
                                            contentDescription = null,
                                            modifier = Modifier.size(56.dp)
                                        )
                                        Text(
                                            text = PokemonNames.getName(requestedId),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val reqId = requestedIdText.toIntOrNull() ?: 0
                            if (selectedPokemonId > 0 && reqId > 0) {
                                viewModel.createTradeOffer(selectedPokemonId, reqId)
                            }
                        },
                        enabled = selectedPokemonId > 0 && requestedIdText.isNotEmpty()
                                && uiState !is TradeUiState.Creating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (uiState is TradeUiState.Creating) "Generando..."
                            else "Generar QR de intercambio",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    OutlinedButton(
                        onClick = onNavigateToScan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Escanear QR de intercambio",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    OutlinedButton(
                        onClick = onNavigateToRewardScan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Escanear QR de recompensa",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    // Info card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "Al intercambiar, entregas una copia de tu Pokémon y recibes una del otro entrenador. " +
                                    "Necesitas al menos 2 copias para poder intercambiar.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Trade history
                    if (trades.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Historial de intercambios",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        trades.forEach { trade ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = PokemonNames.getImageUrl(trade.offeredPokemonId),
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "↔",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    AsyncImage(
                                        model = PokemonNames.getImageUrl(trade.requestedPokemonId),
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${PokemonNames.getName(trade.offeredPokemonId)} ↔ ${PokemonNames.getName(trade.requestedPokemonId)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = if (trade.status == "completed") "Completado" else "Pendiente",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (trade.status == "completed")
                                                MaterialTheme.colorScheme.tertiary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
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
}

@Composable
private fun TradePreviewCard(pokemonId: Int, pokemonName: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = PokemonNames.getImageUrl(pokemonId),
            contentDescription = pokemonName,
            modifier = Modifier.size(80.dp)
        )
        Text(
            text = pokemonName.ifEmpty { PokemonNames.getName(pokemonId) },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
