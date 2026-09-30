package com.junaid.sitemanager.monitor

import android.os.SystemClock
import com.junaid.sitemanager.SiteManagerApp
import com.junaid.sitemanager.data.Site
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Runs the site-monitoring loop: every configured interval each site gets a real
 * HTTP check (HEAD first, GET fallback) and its status is written back to Room.
 */
object MonitorManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    private val _monitoring = MutableStateFlow(false)
    val monitoring: StateFlow<Boolean> = _monitoring.asStateFlow()

    fun start(app: SiteManagerApp) {
        if (monitorJob?.isActive == true) return
        _monitoring.value = true
        monitorJob = scope.launch {
            runCatching { app.repository.log("monitor", "Monitoring started") }
            while (isActive) {
                runCatching { runSingleCycle(app) }
                val intervalMin = app.settings.intervalMinutes.first().coerceIn(1, 120)
                delay(intervalMin * 60_000L)
            }
        }
    }

    fun stop(app: SiteManagerApp) {
        monitorJob?.cancel()
        monitorJob = null
        _monitoring.value = false
        scope.launch { runCatching { app.repository.log("monitor", "Monitoring stopped") } }
    }

    suspend fun runSingleCycle(app: SiteManagerApp) {
        val repo = app.repository
        val timeoutSec = app.settings.timeoutSeconds.first().coerceIn(3, 60)
        val sites = repo.sitesOnce()
        if (sites.isEmpty()) {
            repo.log("monitor", "Check cycle: no sites configured")
            return
        }
        var up = 0
        for (site in sites) {
            val result = checkSite(site, timeoutSec)
            repo.updateSiteStatus(site.id, result.status, result.code, result.latencyMs, result.error)
            if (result.status == Site.STATUS_UP) up++
        }
        repo.log("monitor", "Check cycle finished: $up/${sites.size} site(s) up")
    }

    private suspend fun checkSite(site: Site, timeoutSec: Int): CheckResult =
        withContext(Dispatchers.IO) {
            val url = normalizeUrl(site.url)
            val client = newClient(timeoutSec)
            val head = tryRequest(client, url, head = true)
            if (head != null && head.code in 200..399) return@withContext head
            tryRequest(client, url, head = false)
                ?: head
                ?: CheckResult(Site.STATUS_DOWN, 0, 0, "No response")
        }

    private fun tryRequest(client: OkHttpClient, url: String, head: Boolean): CheckResult? {
        return try {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", "SiteManager/1.0")
            if (head) builder.head() else builder.get()
            val start = SystemClock.elapsedRealtime()
            client.newCall(builder.build()).execute().use { resp ->
                val ms = SystemClock.elapsedRealtime() - start
                val status = if (resp.code in 200..399) Site.STATUS_UP else Site.STATUS_DOWN
                CheckResult(
                    status = status,
                    code = resp.code,
                    latencyMs = ms,
                    error = if (status == Site.STATUS_UP) "" else "HTTP ${resp.code}"
                )
            }
        } catch (e: Exception) {
            CheckResult(
                status = Site.STATUS_DOWN,
                code = 0,
                latencyMs = 0,
                error = e.message?.take(140) ?: e.javaClass.simpleName
            )
        }
    }

    private fun normalizeUrl(raw: String): String {
        val t = raw.trim()
        return if (t.startsWith("http://", ignoreCase = true) ||
            t.startsWith("https://", ignoreCase = true)
        ) {
            t
        } else {
            "https://$t"
        }
    }

    private fun newClient(timeoutSec: Int): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
            .callTimeout((timeoutSec + 10).toLong(), TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
}
