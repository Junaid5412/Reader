package com.junaid.sitemanager.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.junaid.sitemanager.ui.vm.SitesViewModel
import kotlinx.coroutines.launch

@Composable
fun SiteEditScreen(
    siteId: Long,
    onDone: () -> Unit,
    vm: SitesViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(siteId == 0L) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var adminUrl by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(siteId) {
        if (siteId != 0L) {
            vm.site(siteId)?.let { site ->
                name = site.name
                url = site.url
                adminUrl = site.adminUrl
                notes = site.notes
                loaded = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (siteId == 0L) "Add site" else "Edit site",
            style = MaterialTheme.typography.headlineSmall
        )
        if (!loaded && siteId != 0L) {
            Text("Loading…")
            return@Column
        }
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Name") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text("Site URL (https://example.com)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = adminUrl, onValueChange = { adminUrl = it },
            label = { Text("Admin URL (optional)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth(), minLines = 3
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (name.isBlank() || url.isBlank()) {
                    error = "Name and URL are required."
                    return@Button
                }
                scope.launch {
                    vm.saveSite(siteId, name.trim(), url.trim(), adminUrl.trim(), notes.trim())
                    onDone()
                }
            }) { Text("Save") }
            OutlinedButton(onClick = onDone) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
        }
    }
}
