package com.carevalojesus.pokeapi.ui.screens.admin

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carevalojesus.pokeapi.data.firebase.CampaignInfo
import com.carevalojesus.pokeapi.data.firebase.TrainerWithInventory
import com.carevalojesus.pokeapi.util.PokemonNames
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onLogout: () -> Unit,
    viewModel: AdminViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val trainers by viewModel.trainers.collectAsState()
    val campaigns by viewModel.campaigns.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.refreshTrainers()
        viewModel.refreshCampaigns()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Panel Administrador") },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Cerrar sesion")
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
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Entrenadores") },
                    icon = { Icon(Icons.Default.Face, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Campanas") },
                    icon = { Icon(Icons.Default.List, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Generar QR") },
                    icon = { Icon(Icons.Default.Star, contentDescription = null) }
                )
            }

            when (selectedTab) {
                0 -> TrainersTab(
                    trainers = trainers,
                    onRefresh = { viewModel.refreshTrainers() }
                )
                1 -> CampaignsTab(
                    campaigns = campaigns,
                    onRefresh = { viewModel.refreshCampaigns() },
                    onToggle = { id, active -> viewModel.toggleCampaign(id, active) },
                    onDelete = { id -> viewModel.deleteCampaign(id) }
                )
                2 -> QrTab(
                    uiState = uiState,
                    onCreateCampaign = { viewModel.createCampaign(it) }
                )
            }
        }
    }
}

@Composable
private fun TrainersTab(
    trainers: List<TrainerWithInventory>,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Registrados: ${trainers.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = onRefresh) {
                    Text("Actualizar")
                }
            }
        }

        if (trainers.isEmpty()) {
            item {
                Text(
                    text = "No hay entrenadores registrados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(trainers) { trainer ->
            TrainerCard(trainer)
        }
    }
}

@Composable
private fun TrainerCard(trainer: TrainerWithInventory) {
    val totalPokemon = trainer.inventory.sumOf { it.count }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val fullName = listOf(trainer.firstName, trainer.lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            Text(
                text = fullName.ifEmpty { trainer.displayName },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (trainer.email.isNotBlank()) {
                Text(
                    text = trainer.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val infoItems = buildList {
                if (trainer.gender.isNotBlank()) add(trainer.gender)
                if (trainer.birthDate.isNotBlank()) add("Nac: ${trainer.birthDate}")
            }
            if (infoItems.isNotEmpty()) {
                Text(
                    text = infoItems.joinToString(" | "),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Stats from Firestore sync
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Pokemon propios: ${trainer.ownedPokemonCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Desbloqueados: ${trainer.unlockedPokemonCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Cloud inventory summary
            Row {
                Text(
                    text = "Inventario cloud: ",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${trainer.inventory.size} especies, $totalPokemon total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (trainer.inventory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                trainer.inventory.forEach { item ->
                    Text(
                        text = "  ${PokemonNames.getName(item.pokemonId)} (#${item.pokemonId}) x${item.count}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "UID: ${trainer.uid}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun CampaignsTab(
    campaigns: List<CampaignInfo>,
    onRefresh: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    var deleteTarget by remember { mutableStateOf<CampaignInfo?>(null) }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar campana") },
            text = { Text("Seguro que deseas eliminar \"${deleteTarget!!.name}\"? Esta accion no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(deleteTarget!!.campaignId)
                    deleteTarget = null
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Campanas: ${campaigns.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = onRefresh) {
                    Text("Actualizar")
                }
            }
        }

        if (campaigns.isEmpty()) {
            item {
                Text(
                    text = "No hay campanas creadas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(campaigns, key = { it.campaignId }) { campaign ->
            CampaignCard(
                campaign = campaign,
                onToggle = { onToggle(campaign.campaignId, !campaign.active) },
                onDelete = { deleteTarget = campaign }
            )
        }
    }
}

@Composable
private fun CampaignCard(
    campaign: CampaignInfo,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = campaign.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(if (campaign.active) "Activa" else "Inactiva")
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (campaign.active)
                            Color(0xFF4CAF50).copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        labelColor = if (campaign.active)
                            Color(0xFF2E7D32)
                        else
                            MaterialTheme.colorScheme.error
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Date
            if (campaign.createdAt != null) {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                Text(
                    text = "Creada: ${dateFormat.format(campaign.createdAt.toDate())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Escaneos: ${campaign.claimCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Recompensas/escaneo: ${campaign.rewardCount}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onToggle,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (campaign.active) "Desactivar" else "Activar")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun QrTab(
    uiState: AdminUiState,
    onCreateCampaign: (String) -> Unit
) {
    var campaignName by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Generar QR de recompensa",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Cada entrenador que escanee el QR recibira 3 Pokemon aleatorios",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = campaignName,
                onValueChange = { campaignName = it },
                label = { Text("Nombre de campana") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onCreateCampaign(campaignName) },
                enabled = uiState !is AdminUiState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (uiState is AdminUiState.Loading) "Generando..." else "Crear campana y QR"
                )
            }
        }

        when (uiState) {
            is AdminUiState.CampaignCreated -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Campana: ${uiState.campaignName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ID: ${uiState.campaignId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Image(
                                bitmap = uiState.bitmap.asImageBitmap(),
                                contentDescription = "QR de campana",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            is AdminUiState.Error -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = uiState.message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            else -> Unit
        }
    }
}
