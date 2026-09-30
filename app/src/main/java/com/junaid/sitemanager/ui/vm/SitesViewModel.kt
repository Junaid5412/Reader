package com.junaid.sitemanager.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.junaid.sitemanager.SiteManagerApp
import com.junaid.sitemanager.data.Site
import kotlinx.coroutines.launch

class SitesViewModel(app: Application) : AndroidViewModel(app) {
    private val smApp = app as SiteManagerApp
    private val repo = smApp.repository

    val sites = repo.sites

    fun saveSite(id: Long, name: String, url: String, adminUrl: String, notes: String) {
        viewModelScope.launch {
            if (id == 0L) {
                repo.addSite(Site(name = name, url = url, adminUrl = adminUrl, notes = notes))
            } else {
                val existing = repo.getSite(id) ?: return@launch
                repo.updateSite(
                    existing.copy(name = name, url = url, adminUrl = adminUrl, notes = notes)
                )
            }
        }
    }

    fun deleteSite(site: Site) {
        viewModelScope.launch { repo.deleteSite(site) }
    }

    suspend fun site(id: Long): Site? = repo.getSite(id)
}
