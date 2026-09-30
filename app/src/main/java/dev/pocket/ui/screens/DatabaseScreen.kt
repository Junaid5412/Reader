package dev.pocket.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.pocket.data.SiteRepository
import dev.pocket.server.ServerManager
import dev.pocket.server.ServerService
import dev.pocket.server.serverAction
import dev.pocket.ui.components.StatusDot
import dev.pocket.ui.components.openUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseScreen(repo: SiteRepository) {
    val ctx = LocalContext.current
    val sites by repo.sites.collectAsState(initial = emptyList())
    val phpMyAdmin = sites.firstOrNull { it.slug == "phpmyadmin" }
    val states by ServerManager.states.collectAsState()
    val mysqlRunning = states[ServerManager.Svc.MYSQL]?.running == true

    Scaffold(topBar = { TopAppBar(title = { Text("Database") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("phpMyAdmin", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Manage your MySQL databases from the browser.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Username: root    Password: root",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { phpMyAdmin?.let { openUrl(ctx, repo.siteUrl(it)) } },
                        enabled = phpMyAdmin != null
                    ) {
                        Text(
                            if (phpMyAdmin != null) "Open phpMyAdmin  •  127.0.0.1:${phpMyAdmin.port}"
                            else "phpMyAdmin not installed"
                        )
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusDot(running = mysqlRunning)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (mysqlRunning) "MySQL is running" else "MySQL is stopped",
                        modifier = Modifier.weight(1f)
                    )
                    if (!mysqlRunning) {
                        Button(onClick = {
                            ctx.serverAction(
                                ServerService.ACTION_START_SVC,
                                ServerManager.Svc.MYSQL
                            )
                        }) { Text("Start") }
                    }
                }
            }

            Text(
                "Tip: WordPress and most PHP apps connect with host 127.0.0.1, user root, password root.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
