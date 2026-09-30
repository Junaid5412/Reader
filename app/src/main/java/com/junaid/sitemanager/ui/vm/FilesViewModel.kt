package com.junaid.sitemanager.ui.vm

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.junaid.sitemanager.SiteManagerApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BackupFile(val file: File, val size: Long, val modified: Long)

class FilesViewModel(app: Application) : AndroidViewModel(app) {
    private val smApp = app as SiteManagerApp
    private val repo = smApp.repository

    suspend fun listBackups(): List<BackupFile> = withContext(Dispatchers.IO) {
        repo.backupsDir().listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { BackupFile(it, it.length(), it.lastModified()) }
            ?: emptyList()
    }

    fun deleteBackup(file: File, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val name = file.name
            file.delete()
            repo.log("user", "Deleted backup file: $name")
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun shareIntent(file: File): Intent {
        val ctx = getApplication<Application>()
        val uri = FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
