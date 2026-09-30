package com.junaid.sitemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.junaid.sitemanager.data.Site
import com.junaid.sitemanager.ui.util.relativeTime
import com.junaid.sitemanager.ui.vm.DashboardViewModel

@Composable
fun DashboardScreen(
    onOpenBrowser: (String) -> Unit,
    vm: DashboardViewModel = viewModel()
) {
    val sites by vm.sites.collectAsStateWithLifecycle(initialValue = emptyList())
    val monitoring by vm.monitoring.collectAsStateWithLifecycle()
    val upCount by vm.upCount.collectAsStateWithLifecycle()
    val downCount by vm.downCount.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Dashboard", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Total", sites.size.toString(), modifier = Modifier.weight(1f))
                StatCard("Up", upCount.toString(), modifier = Modifier.weight(1f))
                StatCard("Down", downCount.toString(), modifier = Modifier.weight(1f))
            }
        }
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (monitoring) "Monitoring is running" else "Monitoring is stopped",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Background checks keep site statuses fresh and write results to the log.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (monitoring) {
                            Button(onClick = vm::stopMonitoring) { Text("Stop") }
                        } else {
                            Button(onClick = vm::startMonitoring) { Text("Start monitoring") }
                        }
                        OutlinedButton(onClick = vm::checkNow) { Text("Check now") }
                    }
                }
            }
        }
        item {
            Text("Sites", style = MaterialTheme.typography.titleLarge)
        }
        if (sites.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "No sites yet. Add your first site from the Sites tab.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
        items(sites, key = { it.id }) { site ->
            SiteRow(site = site, onOpen = { onOpenBrowser(site.url) })
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private data class StatusUi(val icon: ImageVector, val tint: Color, val label: String)

@Composable
private fun SiteRow(site: Site, onOpen: () -> Unit) {
    val statusUi = when (site.status) {
        Site.STATUS_UP -> StatusUi(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.primary, "Up")
        Site.STATUS_DOWN -> StatusUi(Icons.Filled.Error, MaterialTheme.colorScheme.error, "Down")
        else -> StatusUi(Icons.Filled.HelpOutline, MaterialTheme.colorScheme.onSurfaceVariant, "Not checked")
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(statusUi.icon, contentDescription = statusUi.label, tint = statusUi.tint, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(site.name, style = MaterialTheme.typography.titleMedium)
                Text(site.url, style = MaterialTheme.typography.bodySmall)
                Text(
                    buildStatusLine(site),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onOpen) { Text("Open") }
        }
    }
}

private fun buildStatusLine(site: Site): String {
    val code = if (site.lastHttpCode > 0) "HTTP ${site.lastHttpCode}" else ""
    val latency = if (site.lastLatencyMs > 0) "${site.lastLatencyMs} ms" else ""
    val detail = listOf(code, latency).filter { it.isNotEmpty() }.joinToString(" · ")
    val whenChecked = "last checked ${relativeTime(site.lastChecked)}"
    val err = if (site.status == Site.STATUS_DOWN && site.lastError.isNotBlank()) {
        " — ${site.lastError}"
    } else ""
    return listOf(detail, whenChecked).filter { it.isNotEmpty() }.joinToString(" · ") + err
}
