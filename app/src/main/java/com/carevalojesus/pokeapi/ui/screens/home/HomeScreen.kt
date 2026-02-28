package com.carevalojesus.pokeapi.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.carevalojesus.pokeapi.R
import com.carevalojesus.pokeapi.ui.screens.mypokemon.MyPokemonScreen
import com.carevalojesus.pokeapi.ui.screens.pokedex.PokedexScreen
import com.carevalojesus.pokeapi.ui.screens.profile.ProfileScreen
import com.carevalojesus.pokeapi.ui.screens.trade.TradeScreen

sealed class HomeTab(val route: String, val label: String, val icon: ImageVector) {
    data object MyPokemon : HomeTab("tab_my_pokemon", "Mis Pokemon", Icons.Default.Face)
    data object Pokedex : HomeTab("tab_pokedex", "Pokedex", Icons.Default.List)
    data object Trade : HomeTab("tab_trade", "Intercambio", Icons.Default.Refresh)
    data object Profile : HomeTab("tab_profile", "Perfil", Icons.Default.Person)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPokemonClick: (Int) -> Unit,
    onNavigateToTradeScan: () -> Unit,
    onNavigateToRewardScan: () -> Unit,
    onLogout: () -> Unit
) {
    val tabs = listOf(HomeTab.MyPokemon, HomeTab.Pokedex, HomeTab.Trade, HomeTab.Profile)
    val tabNavController = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Image(
                        painter = painterResource(id = R.drawable.senati_logo),
                        contentDescription = "SENATI",
                        modifier = Modifier.height(40.dp),
                        contentScale = ContentScale.FillHeight
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            tabNavController.navigate(tab.route) {
                                popUpTo(tabNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = tabNavController,
            startDestination = HomeTab.MyPokemon.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(HomeTab.MyPokemon.route) {
                MyPokemonScreen(onPokemonClick = onPokemonClick)
            }
            composable(HomeTab.Pokedex.route) {
                PokedexScreen(onPokemonClick = onPokemonClick)
            }
            composable(HomeTab.Trade.route) {
                TradeScreen(
                    onNavigateToScan = onNavigateToTradeScan,
                    onNavigateToRewardScan = onNavigateToRewardScan
                )
            }
            composable(HomeTab.Profile.route) {
                ProfileScreen(onLogout = onLogout)
            }
        }
    }
}
