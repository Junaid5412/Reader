package com.junaid.sitemanager.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.junaid.sitemanager.SiteManagerApp
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val smApp = app as SiteManagerApp
    val settings = smApp.repository.settings
    private val repo = smApp.repository

    val intervalMinutes = settings.intervalMinutes
    val timeoutSeconds = settings.timeoutSeconds
    val autostart = settings.autostart
    val themeMode = settings.themeMode

    fun setInterval(v: Int) = viewModelScope.launch { settings.setIntervalMinutes(v) }
    fun setTimeout(v: Int) = viewModelScope.launch { settings.setTimeoutSeconds(v) }
    fun setAutostart(v: Boolean) = viewModelScope.launch { settings.setAutostart(v) }
    fun setThemeMode(v: String) = viewModelScope.launch { settings.setThemeMode(v) }

    suspend fun currentConfigJson(): String = repo.buildConfigJson().toString(2)

    suspend fun exportBackup(): String {
        val file = repo.exportBackup()
        return file.name
    }

    suspend fun importConfig(json: String): Result<Int> = runCatching {
        repo.importBackup(json)
    }
}
