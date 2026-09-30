package com.junaid.sitemanager.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("About", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Site Manager", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text("Version 1.0", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Site Manager keeps all of your websites in one place: monitor them with " +
                        "real HTTP checks, browse them in the built-in WebView, back up the full " +
                        "configuration as JSON, and review every event in the log.\n\n" +
                        "All data stays on your device — sites, logs and settings are stored " +
                        "locally and backups live in the app files folder.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
