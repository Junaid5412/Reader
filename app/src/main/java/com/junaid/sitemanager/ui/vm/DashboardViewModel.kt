package com.junaid.sitemanager.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.junaid.sitemanager.SiteManagerApp
import com.junaid.sitemanager.data.Site
import com.junaid.sitemanager.monitor.MonitorManager
import com.junaid.sitemanager.monitor.MonitorService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val smApp = app as SiteManagerApp
    private val repo = smApp.repository

    val sites = repo.sites
    val monitoring = MonitorManager.monitoring

    val upCount = repo.sites.map { list -> list.count { it.status == Site.STATUS_UP } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val downCount = repo.sites.map { list -> list.count { it.status == Site.STATUS_DOWN } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun startMonitoring() {
        val ctx = getApplication<Application>()
        MonitorService.start(ctx)
        viewModelScope.launch { repo.log("user", "User started monitoring") }
    }

    fun stopMonitoring() {
        val ctx = getApplication<Application>()
        MonitorService.stop(ctx)
        viewModelScope.launch { repo.log("user", "User stopped monitoring") }
    }

    fun checkNow() {
        viewModelScope.launch {
            runCatching { MonitorManager.runSingleCycle(smApp) }
                .onFailure { runCatching { repo.log("error", "Manual check failed: ${it.message}") } }
        }
    }
}
