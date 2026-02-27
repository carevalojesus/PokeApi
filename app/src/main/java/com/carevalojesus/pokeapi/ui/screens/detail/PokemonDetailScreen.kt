package com.carevalojesus.pokeapi.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.carevalojesus.pokeapi.ui.theme.pokemonTypeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokemonDetailScreen(
    pokemonId: Int,
    onBack: () -> Unit,
    viewModel: PokemonDetailViewModel = viewModel()
) {
    LaunchedEffect(pokemonId) {
        viewModel.loadPokemon(pokemonId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()

    var unlockEvent by remember { mutableStateOf<UnlockEvent?>(null) }

    LaunchedEffect(Unit) {
        viewModel.unlockEvent.collect { event ->
            unlockEvent = event
        }
    }

    // Unlock dialog
    unlockEvent?.let { event ->
        AlertDialog(
            onDismissRequest = { unlockEvent = null },
            title = {
                Text(
                    text = "Pokemon desbloqueado!",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = event.imageUrl,
                        contentDescription = event.pokemonName,
                        modifier = Modifier.size(120.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "#${event.pokemonId} ${event.pokemonName}",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ha sido agregado a tu coleccion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { unlockEvent = null }) {
                    Text("Genial!")
                }
            }
        )
    }

    // Determinar color de tipo para la portada
    val typeColor = when (val state = uiState) {
        is PokemonDetailUiState.Success ->
            state.pokemon.rawTypes.firstOrNull()?.let { pokemonTypeColors[it] }
        else -> null
    }
    val coverColor = typeColor ?: MaterialTheme.colorScheme.primaryContainer

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite
                            else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isFavorite) "Quitar favorito" else "Agregar favorito",
                            tint = if (isFavorite) Color.Red else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = coverColor,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is PokemonDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is PokemonDetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            is PokemonDetailUiState.Success -> {
                val pokemon = state.pokemon

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Portada con color de tipo
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        coverColor,
                                        coverColor.copy(alpha = 0.6f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = pokemon.imageUrl,
                                contentDescription = pokemon.name,
                                modifier = Modifier.size(180.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "#${pokemon.id} ${pokemon.name}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                            if (pokemon.genus.isNotEmpty()) {
                                Text(
                                    text = pokemon.genus,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Chips de tipo sobre la transicion
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-16).dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        pokemon.rawTypes.forEachIndexed { index, rawType ->
                            val chipColor = pokemonTypeColors[rawType] ?: coverColor
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        pokemon.types.getOrElse(index) { rawType },
                                        color = Color.White
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = chipColor
                                ),
                                border = null
                            )
                            if (index < pokemon.rawTypes.lastIndex) {
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                    }

                    // Contenido debajo de la portada
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (pokemon.description.isNotEmpty()) {
                            Card(
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = pokemon.description,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Justify
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            InfoCard(label = "Altura", value = "${pokemon.height / 10.0} m")
                            InfoCard(label = "Peso", value = "${pokemon.weight / 10.0} kg")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Estadisticas base",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        pokemon.stats.forEach { stat ->
                            StatBar(
                                name = stat.name,
                                value = stat.value,
                                barColor = coverColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun InfoCard(label: String, value: String) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall)
            Text(text = value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun StatBar(name: String, value: Int, barColor: Color = MaterialTheme.colorScheme.primary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "$value",
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.bodyMedium
        )
        LinearProgressIndicator(
            progress = { value / 255f },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = barColor,
        )
    }
}
