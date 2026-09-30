package dev.pocket.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.pocket.data.StackPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_LOG_LINES = 300

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen() {
    val ctx = LocalContext.current
    val tabs = remember { listOf("nginx", "php-fpm", "mysql") }
    var tab by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var lines by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(tab, tick) {
        lines = withContext(Dispatchers.IO) { readLogTail(ctx, tabs[tab]) }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                actions = {
                    IconButton(onClick = { tick++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(t) }
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(8.dp)
            ) {
                items(lines) { line ->
                    Text(
                        line,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun readLogTail(ctx: Context, name: String): List<String> {
    val logs = StackPaths.logsDir(ctx)
    val candidates = when (name) {
        "nginx" -> listOf(File(logs, "nginx_error.log"), File(logs, "nginx.log"))
        "php-fpm" -> listOf(File(logs, "php-fpm.log"), File(logs, "php_errors.log"))
        else -> listOf(File(logs, "mysql.log"), File(logs, "mariadb.log"))
    }
    val f = candidates.firstOrNull { it.exists() }
        ?: return listOf("(no log file yet — start the service first)")
    return try {
        f.readLines().takeLast(MAX_LOG_LINES).ifEmpty { listOf("(log is empty)") }
    } catch (_: Exception) {
        listOf("(could not read log)")
    }
}
