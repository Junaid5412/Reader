package dev.pocket.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import dev.pocket.data.Prefs
import dev.pocket.data.StackPaths
import dev.pocket.data.ZipUtil
import dev.pocket.server.ServerManager
import dev.pocket.ui.components.ConfirmDialog
import dev.pocket.ui.components.SectionTitle
import dev.pocket.ui.components.shareFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val autostart by Prefs.autostartFlow(ctx).collectAsState(initial = false)
    val themeMode by Prefs.themeModeFlow(ctx).collectAsState(initial = 0)
    var showReinstall by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("General")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Start on boot")
                            Text(
                                "Launch servers when the phone boots",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autostart,
                            onCheckedChange = { scope.launch { Prefs.setAutostart(ctx, it) } }
                        )
                    }
                    HorizontalDivider()
                    Column(
                        Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Theme")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("System" to 0, "Light" to 1, "Dark" to 2).forEach { (label, mode) ->
                                FilterChip(
                                    selected = themeMode == mode,
                                    onClick = { scope.launch { Prefs.setThemeMode(ctx, mode) } },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }

            SectionTitle("Backup")
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Back up all sites and the MySQL data directory into one ZIP file. " +
                            "MySQL is briefly stopped so the backup is consistent.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                note = null
                                try {
                                    backupAll(ctx)
                                    note = "Backup created — pick where to share it."
                                } catch (e: Exception) {
                                    note = "Backup failed: ${e.message}"
                                }
                                busy = false
                            }
                        },
                        enabled = !busy
                    ) { Text(if (busy) "Working…" else "Create backup") }
                    note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            SectionTitle("Danger zone")
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Delete the server binaries and configs, then run the installer again. " +
                            "Your site files are kept.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = { showReinstall = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Reinstall stack") }
                }
            }

            SectionTitle("About")
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("PocketHost", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Run nginx + PHP + MariaDB on your phone. No root required.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Stack: nginx, PHP-FPM, MariaDB (Termux builds) • phpMyAdmin 5.2.2",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showReinstall) {
        ConfirmDialog(
            title = "Reinstall stack?",
            text = "This deletes the server binaries and configs, then runs the installer again. Your site files are kept.",
            confirmLabel = "Reinstall",
            onConfirm = {
                scope.launch(Dispatchers.IO) {
                    ServerManager.stopAll()
                    // Small delay so processes fully exit before we delete their files.
                    kotlinx.coroutines.delay(1500)
                    StackPaths.prefix(ctx).deleteRecursively()
                    Prefs.setInstalled(ctx, false)
                }
            },
            onDismiss = { showReinstall = false }
        )
    }
}

/** Zip htdocs + the MySQL datadir (stopping MySQL first) and share the ZIP. */
private suspend fun backupAll(ctx: Context) {
    val wasMysqlRunning = ServerManager.isRunning(ServerManager.Svc.MYSQL)
    if (wasMysqlRunning) {
        ServerManager.stopSvc(ServerManager.Svc.MYSQL)
        kotlinx.coroutines.delay(2000)
    }
    try {
        withContext(Dispatchers.IO) {
            val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val staging = File(ctx.cacheDir, "backup-staging").apply {
                deleteRecursively()
                mkdirs()
            }
            ZipUtil.copyDir(StackPaths.htdocs(ctx), File(staging, "htdocs"))
            ZipUtil.copyDir(File(StackPaths.prefix(ctx), "var/mysql"), File(staging, "mysql"))
            val out = File(ctx.cacheDir, "pockethost-backup-$ts.zip")
            if (out.exists()) out.delete()
            ZipUtil.zipDir(staging, out)
            staging.deleteRecursively()
            withContext(Dispatchers.Main) { shareFile(ctx, out, "Share backup") }
        }
    } finally {
        if (wasMysqlRunning) ServerManager.startSvc(ctx, ServerManager.Svc.MYSQL)
    }
}
