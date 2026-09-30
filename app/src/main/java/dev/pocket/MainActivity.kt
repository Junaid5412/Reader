package dev.pocket

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.room.Room
import dev.pocket.data.Prefs
import dev.pocket.data.SiteRepository
import dev.pocket.db.AppDatabase
import dev.pocket.server.ServerManager
import dev.pocket.ui.screens.DashboardScreen
import dev.pocket.ui.screens.DatabaseScreen
import dev.pocket.ui.screens.EditorScreen
import dev.pocket.ui.screens.FilesScreen
import dev.pocket.ui.screens.InstallerScreen
import dev.pocket.ui.screens.LogsScreen
import dev.pocket.ui.screens.SettingsScreen
import dev.pocket.ui.screens.SitesScreen
import dev.pocket.ui.theme.PocketHostTheme

private data class Dest(val route: String, val label: String, val icon: ImageVector)

private val DESTS = listOf(
    Dest("dashboard", "Dashboard", Icons.Filled.Dashboard),
    Dest("sites", "Sites", Icons.Filled.Language),
    Dest("files", "Files", Icons.Filled.Folder),
    Dest("database", "Database", Icons.Filled.Storage),
    Dest("logs", "Logs", Icons.Filled.Terminal),
    Dest("settings", "Settings", Icons.Filled.Settings)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val ctx = LocalContext.current
            val themeMode by Prefs.themeModeFlow(ctx).collectAsState(initial = 0)
            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            PocketHostTheme(darkTheme = darkTheme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val installed by Prefs.installedFlow(ctx).collectAsState(initial = false)
                    if (!installed) {
                        // First run (or after "Reinstall stack"): show the installer.
                        // Installer.install() flips the flag when done, which
                        // automatically swaps us to the main UI.
                        InstallerScreen()
                    } else {
                        val db = remember {
                            Room.databaseBuilder(
                                ctx.applicationContext,
                                AppDatabase::class.java,
                                "pockethost.db"
                            ).build()
                        }
                        DisposableEffect(Unit) { onDispose { db.close() } }
                        val repo = remember {
                            SiteRepository(ctx.applicationContext, db, ServerManager)
                        }
                        MainScaffold(repo = repo)
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScaffold(repo: SiteRepository) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val useRail = LocalConfiguration.current.screenWidthDp >= 600
    val showNav = currentRoute?.startsWith("editor") != true

    fun go(route: String) {
        if (currentRoute != route) {
            navController.navigate(route) { launchSingleTop = true }
        }
    }

    Scaffold(
        bottomBar = {
            if (showNav && !useRail) {
                NavigationBar {
                    DESTS.forEach { d ->
                        NavigationBarItem(
                            selected = currentRoute == d.route,
                            onClick = { go(d.route) },
                            icon = { Icon(d.icon, contentDescription = d.label) },
                            label = { Text(d.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (showNav && useRail) {
                NavigationRail {
                    DESTS.forEach { d ->
                        NavigationRailItem(
                            selected = currentRoute == d.route,
                            onClick = { go(d.route) },
                            icon = { Icon(d.icon, contentDescription = d.label) },
                            label = { Text(d.label) }
                        )
                    }
                }
            }
            NavHost(
                navController = navController,
                startDestination = "dashboard",
                modifier = Modifier.weight(1f)
            ) {
                composable("dashboard") { DashboardScreen(repo = repo) }
                composable("sites") { SitesScreen(repo = repo) }
                composable("files") {
                    FilesScreen(onOpenEditor = { f ->
                        navController.navigate("editor?path=${Uri.encode(f.absolutePath)}")
                    })
                }
                composable(
                    route = "editor?path={path}",
                    arguments = listOf(navArgument("path") { type = NavType.StringType })
                ) { entry ->
                    EditorScreen(
                        path = entry.arguments?.getString("path").orEmpty(),
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("database") { DatabaseScreen(repo = repo) }
                composable("logs") { LogsScreen() }
                composable("settings") { SettingsScreen() }
            }
        }
    }
}
