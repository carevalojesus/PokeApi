package com.carevalojesus.pokeapi.ui.screens.admin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import com.carevalojesus.pokeapi.data.firebase.AppNotification
import com.carevalojesus.pokeapi.data.firebase.CampaignInfo
import com.carevalojesus.pokeapi.data.firebase.TrainerRewardClaim
import com.carevalojesus.pokeapi.data.firebase.TrainerWithInventory
import com.carevalojesus.pokeapi.ui.notifications.NotificationsScreen
import com.carevalojesus.pokeapi.ui.notifications.NotificationsViewModel
import com.carevalojesus.pokeapi.ui.screens.profile.PasswordChangeState
import com.carevalojesus.pokeapi.util.PokemonNames
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onLogout: () -> Unit,
    viewModel: AdminViewModel = viewModel(),
    notificationsViewModel: NotificationsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val trainers by viewModel.trainers.collectAsState()
    val campaigns by viewModel.campaigns.collectAsState()
    val broadcastStatus by viewModel.broadcastStatus.collectAsState()
    val notifications by notificationsViewModel.notifications.collectAsState()
    val unreadCount by notificationsViewModel.unreadCount.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showNotificationsInbox by remember { mutableStateOf(false) }
    var selectedTrainer by remember { mutableStateOf<TrainerWithInventory?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshTrainers()
        viewModel.refreshCampaigns()
    }

    // Dialog de QR
    if (uiState is AdminUiState.CampaignCreated) {
        CampaignQrDialog(
            state = uiState as AdminUiState.CampaignCreated,
            onDismiss = { viewModel.resetUiState() }
        )
    }

    // Dialog de bandeja de notificaciones
    if (showNotificationsInbox) {
        NotificationsInboxDialog(
            notifications = notifications,
            onMarkRead = { notificationsViewModel.markAsRead(it) },
            onDismiss = { showNotificationsInbox = false }
        )
    }

    // Cargar historial al seleccionar entrenador
    LaunchedEffect(selectedTrainer) {
        selectedTrainer?.let { viewModel.loadTrainerHistory(it.uid) }
    }

    // Vista de detalle de entrenador (pantalla completa)
    if (selectedTrainer != null) {
        val rewardHistory by viewModel.trainerRewards.collectAsState()
        val historyLoading by viewModel.trainerHistoryLoading.collectAsState()
        TrainerDetailView(
            trainer = selectedTrainer!!,
            rewardHistory = rewardHistory,
            historyLoading = historyLoading,
            onBack = { selectedTrainer = null }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Panel Administrador",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showNotificationsInbox = true }) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge {
                                        Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Notificaciones",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Cerrar sesión",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Entrenadores") },
                    icon = { Icon(Icons.Default.Face, contentDescription = null) },
                    selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Campañas") },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Notificaciones") },
                    icon = { Icon(Icons.Default.Send, contentDescription = null) },
                    selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Perfil") },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                )
            }

            when (selectedTab) {
                0 -> TrainersTab(
                    trainers = trainers,
                    onRefresh = { viewModel.refreshTrainers() },
                    onTrainerClick = { selectedTrainer = it }
                )
                1 -> CampaignsTab(
                    campaigns = campaigns,
                    uiState = uiState,
                    onRefresh = { viewModel.refreshCampaigns() },
                    onToggle = { id, active -> viewModel.toggleCampaign(id, active) },
                    onDelete = { id -> viewModel.deleteCampaign(id) },
                    onShowQr = { campaign -> viewModel.showCampaignQr(campaign) },
                    onCreateCampaign = { viewModel.createCampaign(it) }
                )
                2 -> NotificationsAdminTab(
                    onSendNotification = { title, message ->
                        viewModel.sendNotificationToTrainers(title, message)
                    },
                    sendStatus = broadcastStatus,
                    onDismissSendStatus = { viewModel.clearBroadcastStatus() }
                )
                3 -> AdminProfileTab(onLogout = onLogout)
            }
        }
    }
}

// ─── Vista detalle de entrenador ────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainerDetailView(
    trainer: TrainerWithInventory,
    rewardHistory: List<TrainerRewardClaim>,
    historyLoading: Boolean,
    onBack: () -> Unit
) {
    val fullName = listOf(trainer.firstName, trainer.lastName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    val displayName = fullName.ifEmpty { trainer.displayName }

    val primaryColor = MaterialTheme.colorScheme.primary
    var detailTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil de Entrenador") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = primaryColor,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header con gradiente (siempre visible)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor,
                                primaryColor.copy(alpha = 0.6f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (trainer.profilePhotoUrl.isNotEmpty()) {
                        AsyncImage(
                            model = trainer.profilePhotoUrl,
                            contentDescription = "Foto de $displayName",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .border(3.dp, Color.White, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .border(3.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = trainer.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatLastSeen(trainer.lastSeen),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isTrainerOnline(trainer.lastSeen)) Color(0xFF81C784) else Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            // Stats sobrepuestos
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-16).dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrainerStatCard("Propios", "${trainer.ownedPokemonCount}", Modifier.weight(1f))
                TrainerStatCard("Desbloqueados", "${trainer.unlockedPokemonCount}", Modifier.weight(1f))
                TrainerStatCard("Puntos", "${trainer.points}", Modifier.weight(1f))
            }

            // Tabs internas
            TabRow(
                selectedTabIndex = detailTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[detailTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Tab(
                    selected = detailTab == 0,
                    onClick = { detailTab = 0 },
                    text = { Text("Colección") }
                )
                Tab(
                    selected = detailTab == 1,
                    onClick = { detailTab = 1 },
                    text = { Text("Recompensas") }
                )
                Tab(
                    selected = detailTab == 2,
                    onClick = { detailTab = 2 },
                    text = { Text("Avance") }
                )
            }

            when (detailTab) {
                0 -> TrainerCollectionTab(trainer)
                1 -> TrainerRewardsTab(rewardHistory, historyLoading)
                2 -> TrainerProgressTab(trainer, rewardHistory)
            }
        }
    }
}

// ─── Tab: Colección ─────────────────────────────────────────

@Composable
private fun TrainerCollectionTab(trainer: TrainerWithInventory) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val displayName = listOf(trainer.firstName, trainer.lastName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifEmpty { trainer.displayName }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Info personal
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Información personal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow(label = "Nombre", value = displayName)
                    DetailRow(label = "Email", value = trainer.email)
                    if (trainer.gender.isNotBlank()) {
                        DetailRow(label = "Género", value = trainer.gender)
                    }
                    if (trainer.birthDate.isNotBlank()) {
                        DetailRow(label = "Nacimiento", value = trainer.birthDate)
                    }
                    if (trainer.createdAt != null) {
                        DetailRow(
                            label = "Registro",
                            value = dateFormat.format(trainer.createdAt.toDate())
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "UID: ${trainer.uid}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // Pokémon header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pokémon en inventario",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (trainer.inventory.isNotEmpty()) {
                    val totalPokemon = trainer.inventory.sumOf { it.count }
                    Text(
                        text = "${trainer.inventory.size} especies, $totalPokemon total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (trainer.inventory.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sin Pokémon en inventario",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(trainer.inventory.chunked(3)) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { item ->
                        PokemonInventoryItem(
                            pokemonId = item.pokemonId,
                            count = item.count,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

// ─── Tab: Recompensas ───────────────────────────────────────

@Composable
private fun TrainerRewardsTab(
    rewardHistory: List<TrainerRewardClaim>,
    isLoading: Boolean
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Text(
                text = "Historial de recompensas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Campañas canjeadas por este entrenador",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (rewardHistory.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No ha canjeado recompensas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(rewardHistory, key = { it.campaignId }) { claim ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = claim.campaignName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${claim.rewardIds.size} Pokémon",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        if (claim.claimedAt != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Canjeado: ${dateFormat.format(claim.claimedAt.toDate())}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Pokémon recibidos
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            claim.rewardIds.forEach { pokemonId ->
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        AsyncImage(
                                            model = PokemonNames.getImageUrl(pokemonId),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                        Text(
                                            text = PokemonNames.getName(pokemonId),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

// ─── Tab: Avance ────────────────────────────────────────────

@Composable
private fun TrainerProgressTab(
    trainer: TrainerWithInventory,
    rewardHistory: List<TrainerRewardClaim>
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val totalPokemon = trainer.inventory.sumOf { it.count }
    val totalSpecies = trainer.inventory.size
    // Progreso hacia completar el Pokédex (151 Gen 1)
    val pokedexProgress = totalSpecies / 151f

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Text(
                text = "Avance del entrenador",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Progreso del Pokédex
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Progreso del Pokédex",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$totalSpecies / 151 especies",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { pokedexProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(pokedexProgress * 100).toInt()}% completado",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Estadísticas generales
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Estadísticas",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    ProgressStatRow(
                        label = "Pokémon totales",
                        value = "$totalPokemon"
                    )
                    ProgressStatRow(
                        label = "Especies únicas",
                        value = "$totalSpecies"
                    )
                    ProgressStatRow(
                        label = "Pokémon desbloqueados",
                        value = "${trainer.unlockedPokemonCount}"
                    )
                    ProgressStatRow(
                        label = "Puntos acumulados",
                        value = "${trainer.points}"
                    )
                    ProgressStatRow(
                        label = "Campañas canjeadas",
                        value = "${rewardHistory.size}"
                    )
                    ProgressStatRow(
                        label = "Pokémon por recompensas",
                        value = "${rewardHistory.sumOf { it.rewardIds.size }}"
                    )
                }
            }
        }

        // Línea de tiempo
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Línea de tiempo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (trainer.createdAt != null) {
                        TimelineItem(
                            label = "Fecha de registro",
                            value = dateFormat.format(trainer.createdAt.toDate())
                        )
                    }
                    if (trainer.lastClaimAt != null) {
                        TimelineItem(
                            label = "Última recompensa",
                            value = dateFormat.format(trainer.lastClaimAt.toDate())
                        )
                    }
                    if (trainer.updatedAt != null) {
                        TimelineItem(
                            label = "Última actividad",
                            value = dateFormat.format(trainer.updatedAt.toDate())
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun ProgressStatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TimelineItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TrainerStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PokemonInventoryItem(
    pokemonId: Int,
    count: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = PokemonNames.getImageUrl(pokemonId),
                    contentDescription = PokemonNames.getName(pokemonId),
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = PokemonNames.getName(pokemonId),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "#$pokemonId",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            // Badge de cantidad
            if (count > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "x$count",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

// ─── Dialog: QR de campaña ──────────────────────────────────

@Composable
private fun CampaignQrDialog(
    state: AdminUiState.CampaignCreated,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.isNewlyCreated) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Campaña creada exitosamente",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "QR de campaña",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.campaignName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = "QR de campaña",
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ID: ${state.campaignId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Aceptar", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

// ─── Dialog: Bandeja de notificaciones ──────────────────────

@Composable
private fun NotificationsInboxDialog(
    notifications: List<AppNotification>,
    onMarkRead: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bandeja de entrada",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }
                HorizontalDivider()
                NotificationsScreen(
                    notifications = notifications,
                    onMarkRead = onMarkRead
                )
            }
        }
    }
}

// ─── Entrenadores (listado) ─────────────────────────────────

@Composable
private fun TrainersTab(
    trainers: List<TrainerWithInventory>,
    onRefresh: () -> Unit,
    onTrainerClick: (TrainerWithInventory) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Entrenadores",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${trainers.size} registrados",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Actualizar")
                }
            }
        }

        if (trainers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = "No hay entrenadores registrados",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        items(trainers, key = { it.uid }) { trainer ->
            TrainerCard(
                trainer = trainer,
                onClick = { onTrainerClick(trainer) }
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun TrainerCard(
    trainer: TrainerWithInventory,
    onClick: () -> Unit
) {
    val fullName = listOf(trainer.firstName, trainer.lastName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    val displayName = fullName.ifEmpty { trainer.displayName }
    val initials = displayName
        .split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")
        .ifEmpty { "?" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar con indicador online
            Box {
                if (trainer.profilePhotoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = trainer.profilePhotoUrl,
                        contentDescription = "Foto de $displayName",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                // Indicador online/offline
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .background(
                            color = if (isTrainerOnline(trainer.lastSeen)) Color(0xFF4CAF50) else Color.Gray,
                            shape = CircleShape
                        )
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = trainer.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatChip(
                        label = "Propios",
                        value = "${trainer.ownedPokemonCount}",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    StatChip(
                        label = "Puntos",
                        value = "${trainer.points}",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Ver detalle",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ─── Campañas (creación + listado) ──────────────────────────

@Composable
private fun CampaignsTab(
    campaigns: List<CampaignInfo>,
    uiState: AdminUiState,
    onRefresh: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onShowQr: (CampaignInfo) -> Unit,
    onCreateCampaign: (String) -> Unit
) {
    var deleteTarget by remember { mutableStateOf<CampaignInfo?>(null) }
    var campaignName by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is AdminUiState.CampaignCreated && uiState.isNewlyCreated) {
            campaignName = ""
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar campaña", fontWeight = FontWeight.Bold) },
            text = {
                Text("¿Seguro que deseas eliminar \"${deleteTarget!!.name}\"? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(deleteTarget!!.campaignId)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Campañas",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${campaigns.size} creadas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Actualizar")
                }
            }
        }

        // Formulario de creación integrado
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Nueva campaña",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Cada entrenador que escanee recibirá 3 Pokémon aleatorios",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = campaignName,
                            onValueChange = { campaignName = it },
                            label = { Text("Nombre de campaña") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                        Button(
                            onClick = { onCreateCampaign(campaignName) },
                            enabled = campaignName.isNotBlank() && uiState !is AdminUiState.Loading,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            if (uiState is AdminUiState.Loading) {
                                Text("...", style = MaterialTheme.typography.titleSmall)
                            } else {
                                Text("Crear", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
        }

        if (uiState is AdminUiState.Error) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = (uiState as AdminUiState.Error).message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (campaigns.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = "No hay campañas creadas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        items(campaigns, key = { it.campaignId }) { campaign ->
            CampaignCard(
                campaign = campaign,
                onToggle = { onToggle(campaign.campaignId, !campaign.active) },
                onDelete = { deleteTarget = campaign },
                onShowQr = { onShowQr(campaign) }
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun CampaignCard(
    campaign: CampaignInfo,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onShowQr: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
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
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .background(
                            if (campaign.active)
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                            else
                                MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (campaign.active) "Activa" else "Inactiva",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (campaign.active)
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CampaignStat(label = "Escaneos", value = "${campaign.claimCount}")
                CampaignStat(label = "Recompensas", value = "${campaign.rewardCount}")
            }

            if (campaign.createdAt != null) {
                Spacer(modifier = Modifier.height(6.dp))
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                Text(
                    text = "Creada: ${dateFormat.format(campaign.createdAt.toDate())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShowQr,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Ver QR", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onToggle,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        if (campaign.active) "Desactivar" else "Activar",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                OutlinedButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CampaignStat(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Perfil del admin ────────────────────────────────────────

@Composable
private fun AdminProfileTab(onLogout: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val email = currentUser?.email ?: "Sin email"
    val uid = currentUser?.uid ?: "Desconocido"

    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var passwordChangeState by remember { mutableStateOf<PasswordChangeState>(PasswordChangeState.Idle) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Avatar
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(50.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        // Rol
        Box(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Administrador",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        // Info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Información de cuenta",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                DetailRow(label = "Email", value = email)
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "UID: $uid",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // Cambiar contraseña
        OutlinedButton(
            onClick = { showChangePasswordDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Cambiar contraseña")
        }

        // Cerrar sesión
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cerrar sesión")
        }
    }

    if (showChangePasswordDialog) {
        AdminChangePasswordDialog(
            state = passwordChangeState,
            onChangePassword = { current, new_ ->
                passwordChangeState = PasswordChangeState.Loading
                val user = FirebaseAuth.getInstance().currentUser
                if (user != null && user.email != null) {
                    val credential = EmailAuthProvider.getCredential(user.email!!, current)
                    user.reauthenticate(credential)
                        .addOnSuccessListener {
                            user.updatePassword(new_)
                                .addOnSuccessListener {
                                    passwordChangeState = PasswordChangeState.Success
                                }
                                .addOnFailureListener { e ->
                                    passwordChangeState = PasswordChangeState.Error(
                                        e.message ?: "Error al cambiar contraseña"
                                    )
                                }
                        }
                        .addOnFailureListener { e ->
                            passwordChangeState = PasswordChangeState.Error(
                                e.message ?: "Contraseña actual incorrecta"
                            )
                        }
                }
            },
            onDismiss = {
                showChangePasswordDialog = false
                passwordChangeState = PasswordChangeState.Idle
            }
        )
    }
}

@Composable
private fun AdminChangePasswordDialog(
    state: PasswordChangeState,
    onChangePassword: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    val isLoading = state is PasswordChangeState.Loading
    val validationError = when {
        newPassword.isNotEmpty() && newPassword.length < 6 -> "La nueva contraseña debe tener al menos 6 caracteres"
        confirmPassword.isNotEmpty() && newPassword != confirmPassword -> "Las contraseñas no coinciden"
        else -> null
    }
    val canSubmit = currentPassword.isNotBlank() && newPassword.length >= 6 &&
            newPassword == confirmPassword && !isLoading

    if (state is PasswordChangeState.Success) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Contraseña actualizada") },
            text = { Text("Tu contraseña se ha cambiado correctamente.") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Aceptar") }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Cambiar contraseña") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Contraseña actual") },
                    singleLine = true,
                    enabled = !isLoading,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Nueva contraseña") },
                    singleLine = true,
                    enabled = !isLoading,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirmar nueva contraseña") },
                    singleLine = true,
                    enabled = !isLoading,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (validationError != null) {
                    Text(
                        text = validationError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (state is PasswordChangeState.Error) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.CenterHorizontally),
                        strokeWidth = 2.dp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onChangePassword(currentPassword, newPassword) }, enabled = canSubmit) {
                Text("Cambiar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancelar")
            }
        }
    )
}

// ─── Enviar notificaciones ──────────────────────────────────

@Composable
private fun NotificationsAdminTab(
    onSendNotification: (String, String) -> Unit,
    sendStatus: String?,
    onDismissSendStatus: () -> Unit
) {
    var notifTitle by remember { mutableStateOf("Aviso del administrador") }
    var notifMessage by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Centro de notificaciones",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Envía avisos en tiempo real a todos los entrenadores.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        notifTitle = "Evento especial"
                        notifMessage = "Ya está activa una campaña especial. Revisa el panel y participa hoy."
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Evento")
                }
                OutlinedButton(
                    onClick = {
                        notifTitle = "Mantenimiento"
                        notifMessage = "Habrá mantenimiento breve del sistema. Te avisaremos cuando termine."
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Mantenimiento")
                }
                OutlinedButton(
                    onClick = {
                        notifTitle = "Nuevo premio"
                        notifMessage = "Hay nuevas recompensas disponibles en el marketplace. Revísalas ahora."
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Premio")
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = notifTitle,
                        onValueChange = { notifTitle = it },
                        label = { Text("Título") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = notifMessage,
                        onValueChange = { notifMessage = it },
                        label = { Text("Mensaje") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${notifMessage.length}/180",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onSendNotification(notifTitle.trim(), notifMessage.trim())
                        },
                        enabled = notifTitle.isNotBlank() && notifMessage.isNotBlank() && notifMessage.length <= 180,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enviar notificación")
                    }
                }
            }
        }

        if (notifTitle.isNotBlank() || notifMessage.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Vista previa (cómo la verá el usuario)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = notifTitle.ifBlank { "Aviso del administrador" },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = notifMessage.ifBlank { "Escribe un mensaje para ver la vista previa." },
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

        if (!sendStatus.isNullOrBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(sendStatus)
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(onClick = onDismissSendStatus) {
                            Text("Cerrar")
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

// ─── Helpers de presencia ──────────────────────────────────

private fun isTrainerOnline(lastSeen: com.google.firebase.Timestamp?): Boolean {
    if (lastSeen == null) return false
    val diffMs = System.currentTimeMillis() - lastSeen.toDate().time
    return diffMs < 2 * 60 * 1000 // 2 minutos
}

private fun formatLastSeen(lastSeen: com.google.firebase.Timestamp?): String {
    if (lastSeen == null) return "Sin conexión"
    val diffMs = System.currentTimeMillis() - lastSeen.toDate().time
    val diffMin = diffMs / 60_000
    return when {
        diffMin < 2 -> "En línea"
        diffMin < 60 -> "Hace $diffMin min"
        diffMin < 1440 -> "Hace ${diffMin / 60} h"
        else -> {
            val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            "Última vez: ${fmt.format(lastSeen.toDate())}"
        }
    }
}
