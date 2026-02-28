package com.carevalojesus.pokeapi.ui.screens.marketplace

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.carevalojesus.pokeapi.data.repository.MarketplaceCatalog
import com.carevalojesus.pokeapi.data.repository.MarketplaceItem

@Composable
fun MarketplaceScreen(
    onBackToMyPokemon: () -> Unit,
    viewModel: MarketplaceViewModel = viewModel()
) {
    val points by viewModel.points.collectAsState()
    val ownedIds by viewModel.ownedItemIds.collectAsState()
    val equippedIds by viewModel.equippedItemIds.collectAsState()
    val processingItemId by viewModel.processingItemId.collectAsState()
    val feedback by viewModel.feedback.collectAsState()

    val catalog = viewModel.catalog
    val total = catalog.size
    val ownedCount = ownedIds.size
    val progress = if (total == 0) 0f else ownedCount.toFloat() / total.toFloat()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onBackToMyPokemon) {
                            Text("Volver a Mis Pokémon")
                        }
                    }
                    Text(
                        text = "Marketplace de puntos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Puntos disponibles: $points",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Colección: $ownedCount/$total",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Bono por completar todo: +${viewModel.completionBonus} puntos (una sola vez)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Todos los artículos son cosméticos/coleccionables y no afectan combate ni estadísticas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!feedback.isNullOrBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = feedback ?: "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(onClick = { viewModel.clearFeedback() }) {
                            Text("Cerrar")
                        }
                    }
                }
            }
        }

        items(catalog, key = { it.id }) { item ->
            MarketplaceItemCard(
                item = item,
                owned = item.id in ownedIds,
                equipped = item.id in equippedIds,
                canBuy = points >= item.cost,
                loading = processingItemId == item.id,
                onBuy = { viewModel.buy(item.id) },
                onEquip = { viewModel.equip(item.id) }
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun MarketplaceItemCard(
    item: MarketplaceItem,
    owned: Boolean,
    equipped: Boolean,
    canBuy: Boolean,
    loading: Boolean,
    onBuy: () -> Unit,
    onEquip: () -> Unit
) {
    var showAnimated by rememberSaveable(item.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val gifImageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
    LaunchedEffect(owned) {
        if (!owned) showAnimated = false
    }

    val imageUrl = if (owned && showAnimated) item.animatedImageUrl else item.imageUrl
    val imageModifier = Modifier
        .size(72.dp)
        .clip(MaterialTheme.shapes.medium)
        .then(
            if (owned) {
                Modifier.clickable { showAnimated = !showAnimated }
            } else {
                Modifier
            }
        )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    imageLoader = gifImageLoader,
                    model = imageUrl,
                    contentDescription = "Imagen de ${item.name}",
                    contentScale = ContentScale.Crop,
                    colorFilter = if (owned) null else ColorFilter.tint(
                        color = MaterialTheme.colorScheme.onSurface,
                        blendMode = BlendMode.SrcIn
                    ),
                    alpha = if (owned) 1f else 0.35f,
                    modifier = imageModifier
                )
                Spacer(modifier = Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${item.cost} pts",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Categoría: ${MarketplaceCatalog.categoryLabel(item.category)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = when {
                    !owned -> "???? - Cómpralo para revelarlo"
                    showAnimated -> "Toca la imagen para volver a vista fija"
                    else -> "Toca la imagen para ver su animación"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            when {
                owned -> {
                    if (equipped) {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Equipado")
                        }
                    } else {
                        Button(
                            onClick = onEquip,
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (loading) "Procesando..." else "Equipar")
                        }
                    }
                }

                else -> {
                    Button(
                        onClick = onBuy,
                        enabled = canBuy && !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (loading) "Procesando..." else "Comprar")
                    }
                }
            }
        }
    }
}
