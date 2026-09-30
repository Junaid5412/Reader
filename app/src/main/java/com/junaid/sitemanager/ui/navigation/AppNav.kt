package com.junaid.sitemanager.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.junaid.sitemanager.ui.screens.AboutScreen
import com.junaid.sitemanager.ui.screens.BrowserScreen
import com.junaid.sitemanager.ui.screens.DashboardScreen
import com.junaid.sitemanager.ui.screens.FilesScreen
import com.junaid.sitemanager.ui.screens.LogsScreen
import com.junaid.sitemanager.ui.screens.SettingsScreen
import com.junaid.sitemanager.ui.screens.SiteEditScreen
import com.junaid.sitemanager.ui.screens.SitesScreen

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val tabs = listOf(
        Route.Dashboard to ("Dashboard" to Icons.Filled.Dashboard),
        Route.Sites to ("Sites" to Icons.Filled.List),
        Route.Files to ("Files" to Icons.Filled.Folder),
        Route.Logs to ("Logs" to Icons.Filled.ReceiptLong),
        Route.Settings to ("Settings" to Icons.Filled.Settings),
    )
    val tabRoutes = tabs.map { it.first.route }

    Scaffold(
        bottomBar = {
            if (currentRoute in tabRoutes) {
                NavigationBar {
                    tabs.forEach { (route, labelIcon) ->
                        val (label, icon) = labelIcon
                        NavigationBarItem(
                            selected = currentRoute == route.route,
                            onClick = {
                                navController.navigate(route.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = Route.Dashboard.route,
            modifier = Modifier.padding(inner)
        ) {
            composable(Route.Dashboard.route) {
                DashboardScreen(onOpenBrowser = { url ->
                    navController.navigate(Route.Browser.path(url))
                })
            }
            composable(Route.Sites.route) {
                SitesScreen(
                    onAdd = { navController.navigate(Route.SiteEdit.path(0L)) },
                    onEdit = { id -> navController.navigate(Route.SiteEdit.path(id)) },
                    onOpenBrowser = { url -> navController.navigate(Route.Browser.path(url)) }
                )
            }
            composable(
                Route.SiteEdit.PATTERN,
                arguments = listOf(navArgument("siteId") {
                    type = NavType.LongType
                    defaultValue = 0L
                })
            ) { entry ->
                SiteEditScreen(
                    siteId = entry.arguments?.getLong("siteId") ?: 0L,
                    onDone = { navController.popBackStack() }
                )
            }
            composable(
                Route.Browser.PATTERN,
                arguments = listOf(navArgument(Route.Browser.ARG_URL) {
                    type = NavType.StringType
                    defaultValue = ""
                })
            ) { entry ->
                BrowserScreen(initialUrl = Route.Browser.decode(entry.arguments?.getString(Route.Browser.ARG_URL)))
            }
            composable(Route.Files.route) { FilesScreen() }
            composable(Route.Logs.route) { LogsScreen() }
            composable(Route.Settings.route) {
                SettingsScreen(onAbout = { navController.navigate(Route.About.route) })
            }
            composable(Route.About.route) { AboutScreen() }
        }
    }
}
