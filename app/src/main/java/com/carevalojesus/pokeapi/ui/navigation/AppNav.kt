package com.carevalojesus.pokeapi.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.carevalojesus.pokeapi.PokeApiApplication
import com.carevalojesus.pokeapi.ui.screens.detail.PokemonDetailScreen
import com.carevalojesus.pokeapi.ui.screens.home.HomeScreen
import com.carevalojesus.pokeapi.ui.screens.starter.StarterScreen
import com.carevalojesus.pokeapi.ui.screens.trade.TradeConfirmScreen
import com.carevalojesus.pokeapi.ui.screens.trade.TradeScanScreen
import kotlinx.coroutines.flow.map

@Composable
fun AppNav() {
    val context = LocalContext.current
    val app = context.applicationContext as PokeApiApplication
    val profileState by app.userRepository.getProfile()
        .map { profile ->
            when {
                profile == null -> ProfileCheckState.NoProfile
                !profile.starterChosen -> ProfileCheckState.NeedsStarter
                else -> ProfileCheckState.Ready
            }
        }
        .collectAsState(initial = ProfileCheckState.Loading)

    when (profileState) {
        ProfileCheckState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        ProfileCheckState.NoProfile, ProfileCheckState.NeedsStarter, ProfileCheckState.Ready -> {
            val startDestination = if (profileState == ProfileCheckState.Ready) "home" else "starter"
            AppNavContent(startDestination = startDestination)
        }
    }
}

private enum class ProfileCheckState {
    Loading, NoProfile, NeedsStarter, Ready
}

@Composable
private fun AppNavContent(startDestination: String) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
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
                onTradeScanned = { tradeJson ->
                    navController.navigate("trade/confirm/$tradeJson")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "trade/confirm/{tradeJson}",
            arguments = listOf(navArgument("tradeJson") { type = NavType.StringType })
        ) { backStackEntry ->
            val tradeJson = backStackEntry.arguments?.getString("tradeJson") ?: ""
            TradeConfirmScreen(
                tradeJson = tradeJson,
                onComplete = {
                    navController.popBackStack("home", inclusive = false)
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
