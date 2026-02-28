package com.carevalojesus.pokeapi.ui.screens.reward

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardClaimScreen(
    payload: String,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: RewardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(payload) {
        viewModel.claimFromPayload(payload)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Canjear recompensa") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val s = state) {
                RewardUiState.Idle, RewardUiState.Loading -> {
                    Text("Procesando canje...")
                }

                RewardUiState.AlreadyClaimed -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Este QR ya fue canjeado por este entrenador.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Volver")
                    }
                }

                is RewardUiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = s.message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Volver")
                    }
                }

                is RewardUiState.Success -> {
                    Text(
                        text = "Recompensa recibida!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            s.rewardIds.zip(s.names).forEach { (id, name) ->
                                Text("- $name (#$id)")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = {
                        viewModel.reset()
                        onDone()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Continuar")
                    }
                }
            }
        }
    }
}
