package dev.pocket.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.pocket.data.SiteRepository
import dev.pocket.db.SiteEntity
import dev.pocket.ui.components.ConfirmDialog
import dev.pocket.ui.components.TextFieldDialog
import dev.pocket.ui.components.openUrl
import kotlinx.coroutines.launch

/**
 * Every site is listed with its own link (http://127.0.0.1:PORT) —
 * each site is served by its own nginx server block on its own port.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitesScreen(repo: SiteRepository) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sites by repo.sites.collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SiteEntity?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sites") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add site")
            }
        }
    ) { padding ->
        if (sites.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text("No sites yet — tap + to create one.") }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sites, key = { it.id }) { site ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(site.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "http://127.0.0.1:${site.port}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (site.builtIn) {
                                    AssistChip(onClick = {}, label = { Text("Built-in") })
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { openUrl(ctx, repo.siteUrl(site)) }) {
                                    Text("Open")
                                }
                                if (!site.builtIn) {
                                    OutlinedButton(
                                        onClick = { deleteTarget = site },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        TextFieldDialog(
            title = "New site",
            label = "Site name",
            confirmLabel = "Create",
            onConfirm = { name -> scope.launch { repo.addSite(name) } },
            onDismiss = { showAdd = false }
        )
    }
    deleteTarget?.let { site ->
        ConfirmDialog(
            title = "Delete site?",
            text = "Delete \"${site.name}\" and all its files? This cannot be undone.",
            onConfirm = { scope.launch { repo.deleteSite(site) } },
            onDismiss = { deleteTarget = null }
        )
    }
}
