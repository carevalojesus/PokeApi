package com.carevalojesus.pokeapi.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.ui.screens.admin.AdminScreen
import com.carevalojesus.pokeapi.ui.screens.auth.AdminAuthScreen
import com.carevalojesus.pokeapi.ui.screens.auth.AuthUiState
import com.carevalojesus.pokeapi.ui.screens.auth.AuthViewModel
import com.carevalojesus.pokeapi.ui.screens.auth.TrainerAuthScreen
import com.carevalojesus.pokeapi.ui.screens.auth.WelcomeScreen
import com.carevalojesus.pokeapi.ui.screens.profile.ProfileSetupScreen
import com.carevalojesus.pokeapi.ui.screens.detail.PokemonDetailScreen
import com.carevalojesus.pokeapi.ui.screens.home.HomeScreen
import com.carevalojesus.pokeapi.ui.screens.reward.RewardClaimScreen
import com.carevalojesus.pokeapi.ui.screens.reward.RewardScanScreen
import com.carevalojesus.pokeapi.ui.screens.starter.StarterScreen
import com.carevalojesus.pokeapi.ui.screens.trade.TradeConfirmScreen
import com.carevalojesus.pokeapi.ui.screens.trade.TradeScanScreen
import com.carevalojesus.pokeapi.ui.notifications.SystemNotificationsBridge
import kotlinx.coroutines.delay

@Composable
fun AppNav(
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val app = context.applicationContext as? PokeApiApplication

    LaunchedEffect(authState, app) {
        if (app == null) return@LaunchedEffect
        when (authState) {
            AuthUiState.Admin, AuthUiState.Trainer -> {
                SystemNotificationsBridge.start(context, app.firebaseRepository)
            }
            else -> {
                SystemNotificationsBridge.stop()
            }
        }
    }

    when (authState) {
        AuthUiState.Loading -> LoadingState("Validando sesión...")

        AuthUiState.Admin -> AdminScreen(
            onLogout = authViewModel::signOut
        )

        AuthUiState.Trainer -> TrainerAppNav(
            onLogout = authViewModel::signOut
        )

        AuthUiState.LoggedOut, is AuthUiState.Error -> AuthNav(
            authViewModel = authViewModel
        )
    }
}

@Composable
private fun AuthNav(
    authViewModel: AuthViewModel
) {
    val authNavController = rememberNavController()
    val authError by authViewModel.authError.collectAsState()
    val isAuthenticating by authViewModel.isAuthenticating.collectAsState()
    val resetEmailSent by authViewModel.resetEmailSent.collectAsState()

    NavHost(navController = authNavController, startDestination = "welcome") {
        composable(route = "welcome") {
            WelcomeScreen(
                onNavigateToTrainerLogin = {
                    authViewModel.clearError()
                    authNavController.navigate("trainer_auth/false")
                },
                onNavigateToTrainerRegister = {
                    authViewModel.clearError()
                    authNavController.navigate("trainer_auth/true")
                },
                onNavigateToAdminLogin = {
                    authViewModel.clearError()
                    authNavController.navigate("admin_auth")
                }
            )
        }

        composable(
            route = "trainer_auth/{isRegister}",
            arguments = listOf(navArgument("isRegister") { type = NavType.BoolType })
        ) { backStackEntry ->
            val isRegister = backStackEntry.arguments?.getBoolean("isRegister") ?: false
            TrainerAuthScreen(
                isRegisterMode = isRegister,
                onLogin = { email, password ->
                    authViewModel.signInTrainer(email, password)
                },
                onRegister = { email, password ->
                    authViewModel.registerTrainer(email, password)
                },
                onBack = {
                    authViewModel.clearError()
                    authNavController.popBackStack()
                },
                onForgotPassword = { email ->
                    authViewModel.sendPasswordReset(email)
                },
                resetEmailSent = resetEmailSent,
                onClearResetEmailSent = { authViewModel.clearResetEmailSent() },
                errorMessage = authError,
                isLoading = isAuthenticating
            )
        }

        composable(route = "admin_auth") {
            AdminAuthScreen(
                onLogin = { email, password ->
                    authViewModel.signInAdmin(email, password)
                },
                onBack = {
                    authViewModel.clearError()
                    authNavController.popBackStack()
                },
                errorMessage = authError,
                isLoading = isAuthenticating
            )
        }
    }
}

@Composable
private fun TrainerAppNav(onLogout: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as? PokeApiApplication
    val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

    // Heartbeat de presencia: actualiza lastSeen cada 60 segundos
    LaunchedEffect(app) {
        if (app == null) return@LaunchedEffect
        while (true) {
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) break
            try { app.firebaseRepository.updatePresence() } catch (_: Exception) { }
            delay(60_000L)
        }
    }

    val profileState by produceState<ProfileCheckState>(
        initialValue = ProfileCheckState.Loading,
        key1 = app,
        key2 = currentUid
    ) {
        if (app == null) {
            value = ProfileCheckState.Error("No se pudo inicializar la aplicación.")
            return@produceState
        }
        try {
            val remote = app.firebaseRepository.getCurrentTrainerSetupState()
            value = when {
                remote == null -> ProfileCheckState.NeedsProfileSetup
                remote.firstName.isBlank() -> ProfileCheckState.NeedsProfileSetup
                !remote.starterChosen -> ProfileCheckState.NeedsStarter
                else -> ProfileCheckState.Ready
            }
        } catch (error: Exception) {
            value = ProfileCheckState.Error(
                error.message ?: "Error cargando perfil inicial."
            )
        }
    }

    when (profileState) {
        ProfileCheckState.Loading -> LoadingState("Cargando...")
        is ProfileCheckState.Error -> ErrorState((profileState as ProfileCheckState.Error).message)
        ProfileCheckState.NeedsProfileSetup, ProfileCheckState.NeedsStarter, ProfileCheckState.Ready -> {
            val startDestination = when (profileState) {
                ProfileCheckState.NeedsProfileSetup -> "profile_setup"
                ProfileCheckState.NeedsStarter -> "starter"
                else -> "home"
            }
            TrainerNavContent(startDestination = startDestination, onLogout = onLogout)
        }
    }
}

private sealed interface ProfileCheckState {
    data object Loading : ProfileCheckState
    data object NeedsProfileSetup : ProfileCheckState
    data object NeedsStarter : ProfileCheckState
    data object Ready : ProfileCheckState
    data class Error(val message: String) : ProfileCheckState
}

@Composable
private fun TrainerNavContent(startDestination: String, onLogout: () -> Unit) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
        composable(route = "profile_setup") {
            ProfileSetupScreen(
                onComplete = {
                    navController.navigate("starter") {
                        popUpTo("profile_setup") { inclusive = true }
                    }
                }
            )
        }

        composable(route = "starter") {
            StarterScreen(
                onStarterChosen = {
                    navController.navigate("home") {
                        popUpTo("starter") { inclusive = true }
                    }
                }
            )
        }

        composable(route = "home") {
            HomeScreen(
                onPokemonClick = { id ->
                    navController.navigate("detail/$id")
                },
                onNavigateToTradeScan = {
                    navController.navigate("trade/scan")
                },
                onNavigateToRewardScan = {
                    navController.navigate("reward/scan")
                },
                onLogout = {
                    onLogout()
                }
            )
        }

        composable(
            route = "detail/{pokemonId}",
            arguments = listOf(navArgument("pokemonId") { type = NavType.IntType }),
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) { backStackEntry ->
            val pokemonId = backStackEntry.arguments?.getInt("pokemonId") ?: 1
            PokemonDetailScreen(
                pokemonId = pokemonId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(route = "trade/scan") {
            TradeScanScreen(
                onTradeScanned = { tradeId ->
                    navController.navigate("trade/confirm?tradeId=$tradeId")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "trade/confirm?tradeId={tradeId}",
            arguments = listOf(navArgument("tradeId") { type = NavType.StringType; defaultValue = "" })
        ) { backStackEntry ->
            val tradeId = backStackEntry.arguments?.getString("tradeId") ?: ""
            TradeConfirmScreen(
                tradeId = tradeId,
                onComplete = {
                    navController.popBackStack("home", inclusive = false)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(route = "reward/scan") {
            RewardScanScreen(
                onPayloadScanned = { payload ->
                    navController.navigate("reward/claim?payload=$payload")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "reward/claim?payload={payload}",
            arguments = listOf(navArgument("payload") { type = NavType.StringType; defaultValue = "" })
        ) { backStackEntry ->
            val payload = backStackEntry.arguments?.getString("payload") ?: ""
            RewardClaimScreen(
                payload = payload,
                onDone = { navController.popBackStack("home", inclusive = false) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun LoadingState(message: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text(message)
            }
        }
    }
}

@Composable
private fun ErrorState(message: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
