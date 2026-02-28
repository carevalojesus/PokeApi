package com.carevalojesus.pokeapi.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.carevalojesus.pokeapi.R
import com.carevalojesus.pokeapi.ui.theme.SenatiBlue
import com.carevalojesus.pokeapi.ui.theme.SenatiSkyBlue
import com.carevalojesus.pokeapi.ui.theme.SenatiWhite
import com.carevalojesus.pokeapi.ui.screens.mypokemon.MyPokemonScreen
import com.carevalojesus.pokeapi.ui.screens.pokedex.PokedexScreen
import com.carevalojesus.pokeapi.ui.screens.profile.ProfileScreen
import com.carevalojesus.pokeapi.ui.screens.marketplace.MarketplaceScreen
import com.carevalojesus.pokeapi.ui.screens.trade.TradeScreen
import com.carevalojesus.pokeapi.ui.notifications.NotificationsScreen
import com.carevalojesus.pokeapi.ui.notifications.NotificationsViewModel

sealed class HomeTab(val route: String, val label: String, val icon: ImageVector) {
    data object MyPokemon : HomeTab("tab_my_pokemon", "Mis Pokémon", Icons.Default.Face)
    data object Pokedex : HomeTab("tab_pokedex", "Pokédex", Icons.Default.List)
    data object Trade : HomeTab("tab_trade", "Intercambio", Icons.Default.Refresh)
    data object Profile : HomeTab("tab_profile", "Perfil", Icons.Default.Person)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPokemonClick: (Int) -> Unit,
    onNavigateToTradeScan: () -> Unit,
    onNavigateToRewardScan: () -> Unit,
    onLogout: () -> Unit,
    notificationsViewModel: NotificationsViewModel = viewModel()
) {
    val tabs = listOf(HomeTab.MyPokemon, HomeTab.Pokedex, HomeTab.Trade, HomeTab.Profile)
    val tabNavController = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val notifications by notificationsViewModel.notifications.collectAsState()
    val unreadCount by notificationsViewModel.unreadCount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Image(
                        painter = painterResource(id = R.drawable.senati_logo),
                        contentDescription = "SENATI",
                        modifier = Modifier.height(36.dp),
                        contentScale = ContentScale.FillHeight,
                        colorFilter = ColorFilter.tint(
                            color = SenatiWhite,
                            blendMode = BlendMode.SrcAtop
                        )
                    )
                },
                actions = {
                    IconButton(onClick = {
                        tabNavController.navigate("tab_notifications") {
                            launchSingleTop = true
                        }
                    }) {
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
                                tint = SenatiWhite
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            tabNavController.navigate("tab_market") {
                                launchSingleTop = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "Marketplace",
                            tint = SenatiWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SenatiBlue,
                    titleContentColor = SenatiWhite
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SenatiBlue,
                contentColor = SenatiWhite
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SenatiBlue,
                            selectedTextColor = SenatiWhite,
                            unselectedIconColor = SenatiWhite.copy(alpha = 0.6f),
                            unselectedTextColor = SenatiWhite.copy(alpha = 0.6f),
                            indicatorColor = SenatiSkyBlue
                        ),
                        onClick = {
                            val currentRoute = navBackStackEntry?.destination?.route
                            if (currentRoute == "tab_market" || currentRoute == "tab_notifications") {
                                tabNavController.popBackStack()
                            }
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
            composable(route = "tab_market") {
                MarketplaceScreen(
                    onBackToMyPokemon = {
                        val popped = tabNavController.popBackStack(
                            route = HomeTab.MyPokemon.route,
                            inclusive = false
                        )
                        if (!popped) {
                            tabNavController.navigate(HomeTab.MyPokemon.route) {
                                popUpTo(tabNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
            composable(route = "tab_notifications") {
                NotificationsScreen(
                    notifications = notifications,
                    onMarkRead = { id -> notificationsViewModel.markAsRead(id) },
                    onBack = { tabNavController.popBackStack() }
                )
            }
            composable(HomeTab.Profile.route) {
                ProfileScreen(onLogout = onLogout)
            }
        }
    }
}
