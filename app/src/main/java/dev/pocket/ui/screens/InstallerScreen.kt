package dev.pocket.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.pocket.data.Installer

/**
 * Full-screen first-run installer. Runs [Installer.install] once and reports
 * progress; when it finishes, the installer flips the DataStore flag and the
 * activity automatically swaps to the main UI.
 */
@Composable
fun InstallerScreen() {
    val ctx = LocalContext.current
    var stage by remember { mutableStateOf("Preparing…") }
    var frac by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        error = null
        try {
            Installer.install(ctx) { s, f ->
                stage = s
                frac = f
            }
        } catch (e: Exception) {
            error = e.message ?: "Install failed"
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "PocketHost",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text("Localhost for your phone", style = MaterialTheme.typography.bodyMedium)

            val err = error
            if (err != null) {
                Text(
                    "Install failed: $err",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Check that the stack assets are bundled, then retry.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = {
                    stage = "Retrying…"
                    frac = 0f
                    attempt++
                }) { Text("Retry") }
            } else {
                // Float overload is deprecated in newer material3 but still present —
                // using it keeps this compiling across BOM versions.
                @Suppress("DEPRECATION")
                LinearProgressIndicator(
                    progress = frac,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stage, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
