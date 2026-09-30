package com.junaid.sitemanager.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.junaid.sitemanager.SiteManagerApp
import kotlinx.coroutines.launch

class LogsViewModel(app: Application) : AndroidViewModel(app) {
    private val smApp = app as SiteManagerApp
    val logs = smApp.repository.logs

    fun clearLogs() {
        viewModelScope.launch { smApp.repository.clearLogs() }
    }
}
