package com.junaid.sitemanager.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.junaid.sitemanager.ui.vm.SettingsViewModel
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun SettingsScreen(
    onAbout: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val interval by vm.intervalMinutes.collectAsStateWithLifecycle(initialValue = 5)
    val timeout by vm.timeoutSeconds.collectAsStateWithLifecycle(initialValue = 10)
    val autostart by vm.autostart.collectAsStateWithLifecycle(initialValue = false)
    val themeMode by vm.themeMode.collectAsStateWithLifecycle(initialValue = "system")

    var intervalSlider by remember { mutableFloatStateOf(interval.toFloat()) }
    var timeoutSlider by remember { mutableFloatStateOf(timeout.toFloat()) }
    var configText by remember { mutableStateOf("") }
    var configOpen by remember { mutableStateOf(false) }

    LaunchedEffect(interval) { intervalSlider = interval.toFloat() }
    LaunchedEffect(timeout) { timeoutSlider = timeout.toFloat() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use {
                        it.bufferedReader().readText()
                    } ?: throw IllegalStateException("Could not read file")
                    vm.importConfig(text).getOrThrow()
                }.onSuccess { count ->
                    snackbar.showSnackbar("Imported $count site(s)")
                }.onFailure { e ->
                    snackbar.showSnackbar("Import failed: ${e.message}")
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onAbout) {
                    Icon(Icons.Filled.Info, contentDescription = "About")
                }
            }

            SectionCard("Monitoring") {
                Text("Check interval: ${intervalSlider.toInt()} min", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = intervalSlider,
                    onValueChange = { intervalSlider = it },
                    onValueChangeFinished = { vm.setInterval(intervalSlider.toInt()) },
                    valueRange = 1f..120f,
                    steps = 118
                )
                Text("Request timeout: ${timeoutSlider.toInt()} s", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = timeoutSlider,
                    onValueChange = { timeoutSlider = it },
                    onValueChangeFinished = { vm.setTimeout(timeoutSlider.toInt()) },
                    valueRange = 3f..60f,
                    steps = 56
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Autostart on boot", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Runs a check cycle when the device restarts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autostart, onCheckedChange = vm::setAutostart)
                }
            }

            SectionCard("Appearance") {
                Column(Modifier.selectableGroup()) {
                    listOf("system" to "System default", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = themeMode == value,
                                    role = Role.RadioButton,
                                    onClick = { vm.setThemeMode(value) }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = themeMode == value, onClick = { vm.setThemeMode(value) })
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            }

            SectionCard("Backup & restore") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            runCatching { vm.exportBackup() }.onSuccess { name ->
                                snackbar.showSnackbar("Backup saved: $name")
                            }.onFailure { e ->
                                snackbar.showSnackbar("Export failed: ${e.message}")
                            }
                        }
                    }) { Text("Export backup") }
                    OutlinedButton(onClick = {
                        importLauncher.launch(arrayOf("application/json"))
                    }) { Text("Restore from file") }
                }
                Text(
                    "Backups are JSON files in the app files folder and can be managed from the Files tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard("Raw config") {
                Text(
                    "View or edit the full JSON config. It is validated before import.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            configText = vm.currentConfigJson()
                            configOpen = true
                        }
                    }) { Text("Edit config") }
                }
            }
        }
        SnackbarHost(snackbar)
    }

    if (configOpen) {
        ConfigEditorDialog(
            initial = configText,
            onDismiss = { configOpen = false },
            onSave = { text ->
                scope.launch {
                    runCatching { JSONObject(text).toString() }.onFailure {
                        snackbar.showSnackbar("Invalid JSON: ${it.message}")
                        return@launch
                    }
                    vm.importConfig(text).onSuccess { count ->
                        configOpen = false
                        snackbar.showSnackbar("Config applied: $count site(s)")
                    }.onFailure { e ->
                        snackbar.showSnackbar("Config invalid: ${e.message}")
                    }
                }
            }
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Divider()
            content()
        }
    }
}

@Composable
private fun ConfigEditorDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(initial) { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Raw config (JSON)") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onSave(text) }) { Text("Validate & apply") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
