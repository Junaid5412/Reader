package dev.pocket.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.pocket.data.SiteRepository
import dev.pocket.server.ServerManager
import dev.pocket.server.ServerService
import dev.pocket.server.serverAction
import dev.pocket.ui.components.StatusDot
import dev.pocket.ui.components.openUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(repo: SiteRepository) {
    val ctx = LocalContext.current
    val states by ServerManager.states.collectAsState()
    val sites by repo.sites.collectAsState(initial = emptyList())
    val welcome = sites.firstOrNull { it.slug == "welcome" }
    val phpMyAdmin = sites.firstOrNull { it.slug == "phpmyadmin" }
    val anyRunning = states.values.any { it.running }

    Scaffold(topBar = { TopAppBar(title = { Text("PocketHost") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { ctx.serverAction(ServerService.ACTION_START_ALL) },
                    modifier = Modifier.weight(1f)
                ) { Text("Start all") }
                OutlinedButton(
                    onClick = { ctx.serverAction(ServerService.ACTION_STOP_ALL) },
                    modifier = Modifier.weight(1f),
                    enabled = anyRunning
                ) { Text("Stop all") }
            }

            ServiceCard(
                title = "nginx",
                subtitle = "Web server",
                state = states[ServerManager.Svc.NGINX],
                onStart = { ctx.serverAction(ServerService.ACTION_START_SVC, ServerManager.Svc.NGINX) },
                onStop = { ctx.serverAction(ServerService.ACTION_STOP_SVC, ServerManager.Svc.NGINX) },
                onRestart = { ctx.serverAction(ServerService.ACTION_RESTART_SVC, ServerManager.Svc.NGINX) }
            )
            ServiceCard(
                title = "PHP",
                subtitle = "PHP-FPM  •  127.0.0.1:9000",
                state = states[ServerManager.Svc.PHP],
                onStart = { ctx.serverAction(ServerService.ACTION_START_SVC, ServerManager.Svc.PHP) },
                onStop = { ctx.serverAction(ServerService.ACTION_STOP_SVC, ServerManager.Svc.PHP) },
                onRestart = { ctx.serverAction(ServerService.ACTION_RESTART_SVC, ServerManager.Svc.PHP) }
            )
            ServiceCard(
                title = "MariaDB",
                subtitle = "MySQL  •  127.0.0.1:3306",
                state = states[ServerManager.Svc.MYSQL],
                onStart = { ctx.serverAction(ServerService.ACTION_START_SVC, ServerManager.Svc.MYSQL) },
                onStop = { ctx.serverAction(ServerService.ACTION_STOP_SVC, ServerManager.Svc.MYSQL) },
                onRestart = { ctx.serverAction(ServerService.ACTION_RESTART_SVC, ServerManager.Svc.MYSQL) }
            )

            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Quick links", style = MaterialTheme.typography.titleMedium)
                    if (welcome != null) {
                        OutlinedButton(onClick = { openUrl(ctx, repo.siteUrl(welcome)) }) {
                            Text("Open welcome site  •  127.0.0.1:${welcome.port}")
                        }
                    }
                    if (phpMyAdmin != null) {
                        OutlinedButton(onClick = { openUrl(ctx, repo.siteUrl(phpMyAdmin)) }) {
                            Text("Open phpMyAdmin  •  127.0.0.1:${phpMyAdmin.port}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceCard(
    title: String,
    subtitle: String,
    state: ServerManager.SvcState?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit
) {
    val running = state?.running == true
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(running = running)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    if (running) "Running" else "Stopped",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (running) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (running && state?.pid != null) {
                Text("PID ${state.pid}", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!running) {
                    Button(onClick = onStart) { Text("Start") }
                } else {
                    OutlinedButton(onClick = onStop) { Text("Stop") }
                }
                OutlinedButton(onClick = onRestart, enabled = running) { Text("Restart") }
            }
        }
    }
}
