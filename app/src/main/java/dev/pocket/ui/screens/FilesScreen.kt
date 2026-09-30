package dev.pocket.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.pocket.data.StackPaths
import dev.pocket.data.ZipUtil
import dev.pocket.ui.components.ConfirmDialog
import dev.pocket.ui.components.TextFieldDialog
import dev.pocket.ui.components.shareFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val EDITABLE_EXT = setOf(
    "php", "txt", "html", "htm", "js", "css", "json", "xml",
    "md", "ini", "conf", "log", "sql", "yml", "yaml"
)

private fun isEditable(f: File): Boolean = f.extension.lowercase() in EDITABLE_EXT

/** The site root containing [dir] (htdocs/sites/<slug>), or null if outside sites/. */
private fun siteRootOf(dir: File, htdocs: File): File? {
    val sitesDir = File(htdocs, "sites")
    var d: File? = dir
    while (d != null && d != htdocs) {
        if (d.parentFile == sitesDir) return d
        d = d.parentFile
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(onOpenEditor: (File) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember { StackPaths.htdocs(ctx).apply { mkdirs() } }
    var currentDir by remember { mutableStateOf(root) }
    var tick by remember { mutableIntStateOf(0) }

    val entries = remember(currentDir, tick) {
        currentDir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
    }
    val segments = remember(currentDir) {
        val rel = root.toURI().relativize(currentDir.toURI()).path.trimEnd('/')
        if (rel.isEmpty()) emptyList() else rel.split("/")
    }

    var renameTarget by remember { mutableStateOf<File?>(null) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }

    fun refresh() {
        tick++
    }

    fun ioThenRefresh(block: suspend () -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (_: Exception) { }
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            ioThenRefresh {
                ctx.contentResolver.openInputStream(uri)?.use { ins ->
                    ZipUtil.unzip(ins, currentDir)
                }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Files") },
                actions = {
                    IconButton(onClick = { showNewFolder = true }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                    }
                    IconButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val f = newUniqueFile(currentDir, "newfile", ".php")
                            try {
                                f.createNewFile()
                            } catch (_: Exception) { }
                            withContext(Dispatchers.Main) {
                                refresh()
                                onOpenEditor(f)
                            }
                        }
                    }) {
                        Icon(Icons.Filled.NoteAdd, contentDescription = "New file")
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.Upload, contentDescription = "Import ZIP")
                    }
                    IconButton(onClick = {
                        val target = siteRootOf(currentDir, root) ?: currentDir
                        scope.launch(Dispatchers.IO) {
                            try {
                                val out = File(ctx.cacheDir, "pockethost-${target.name}.zip")
                                if (out.exists()) out.delete()
                                ZipUtil.zipDir(target, out)
                                withContext(Dispatchers.Main) {
                                    shareFile(ctx, out, "Share site ZIP")
                                }
                            } catch (_: Exception) { }
                        }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Export ZIP")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { currentDir = root }) { Text("htdocs") }
                var acc = root
                segments.forEach { seg ->
                    Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val next = File(acc, seg)
                    acc = next
                    TextButton(onClick = { currentDir = next }) { Text(seg) }
                }
            }
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxSize()) {
                if (currentDir != root) {
                    item {
                        ListItem(
                            headlineContent = { Text("..") },
                            leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                            modifier = Modifier.clickable {
                                currentDir = currentDir.parentFile ?: root
                            }
                        )
                    }
                }
                items(entries, key = { it.absolutePath }) { f ->
                    ListItem(
                        headlineContent = { Text(f.name) },
                        supportingContent = {
                            if (f.isFile) Text("${f.length()} bytes")
                        },
                        leadingContent = {
                            Icon(
                                if (f.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                                contentDescription = null
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { renameTarget = f }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Rename")
                                }
                                IconButton(onClick = { deleteTarget = f }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        modifier = Modifier.clickable {
                            if (f.isDirectory) currentDir = f
                            else if (isEditable(f)) onOpenEditor(f)
                        }
                    )
                }
            }
        }
    }

    if (showNewFolder) {
        TextFieldDialog(
            title = "New folder",
            label = "Folder name",
            confirmLabel = "Create",
            onConfirm = { name -> ioThenRefresh { File(currentDir, name).mkdirs() } },
            onDismiss = { showNewFolder = false }
        )
    }
    renameTarget?.let { f ->
        TextFieldDialog(
            title = "Rename",
            label = "Name",
            initial = f.name,
            confirmLabel = "Rename",
            onConfirm = { name -> ioThenRefresh { f.renameTo(File(f.parentFile, name)) } },
            onDismiss = { renameTarget = null }
        )
    }
    deleteTarget?.let { f ->
        ConfirmDialog(
            title = "Delete?",
            text = "Delete \"${f.name}\"? This cannot be undone.",
            onConfirm = { ioThenRefresh { f.deleteRecursively() } },
            onDismiss = { deleteTarget = null }
        )
    }
}

private fun newUniqueFile(dir: File, base: String, ext: String): File {
    var candidate = File(dir, base + ext)
    var i = 2
    while (candidate.exists()) candidate = File(dir, "$base-$i$ext").also { i++ }
    return candidate
}
