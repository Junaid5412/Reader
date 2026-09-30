package com.junaid.sitemanager.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.junaid.sitemanager.ui.util.formatDateTime
import com.junaid.sitemanager.ui.util.humanSize
import com.junaid.sitemanager.ui.vm.BackupFile
import com.junaid.sitemanager.ui.vm.FilesViewModel
import java.io.File

@Composable
fun FilesScreen(vm: FilesViewModel = viewModel()) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<BackupFile>>(emptyList()) }
    var refresh by remember { mutableStateOf(0) }
    var pendingDelete by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(refresh) {
        files = vm.listBackups()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Files", style = MaterialTheme.typography.headlineSmall)
            Text(
                "JSON backups exported from Settings live here. Share or delete them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (files.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "No backup files yet. Export one from Settings → Backup & restore.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
        items(files, key = { it.file.absolutePath }) { backup ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(backup.file.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${humanSize(backup.size)} · ${formatDateTime(backup.modified)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(vm.shareIntent(backup.file), "Share backup")
                            )
                        }
                    }) { Icon(Icons.Filled.Share, contentDescription = "Share") }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { pendingDelete = backup.file }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }

    pendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete file?") },
            text = { Text("“${file.name}” will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteBackup(file) {
                        pendingDelete = null
                        refresh++
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}
