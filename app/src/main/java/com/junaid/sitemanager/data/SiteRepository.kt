package com.junaid.sitemanager.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SiteRepository(
    private val context: Context,
    private val db: AppDatabase,
    val settings: SettingsStore
) {
    val sites: Flow<List<Site>> = db.siteDao().observeAll()
    val logs: Flow<List<LogEntry>> = db.logDao().observeRecent()

    suspend fun sitesOnce(): List<Site> = db.siteDao().getAll()

    suspend fun log(tag: String, message: String) {
        db.logDao().insert(LogEntry(timestamp = System.currentTimeMillis(), tag = tag, message = message))
    }

    suspend fun addSite(site: Site): Long {
        val id = db.siteDao().upsert(site.copy(id = 0))
        log("sites", "Added site: ${site.name}")
        return id
    }

    suspend fun updateSite(site: Site) {
        db.siteDao().upsert(site)
        log("sites", "Updated site: ${site.name}")
    }

    suspend fun deleteSite(site: Site) {
        db.siteDao().delete(site)
        log("sites", "Deleted site: ${site.name}")
    }

    suspend fun getSite(id: Long): Site? = db.siteDao().getById(id)

    suspend fun updateSiteStatus(id: Long, status: String, code: Int, latencyMs: Long, error: String) {
        db.siteDao().updateStatus(id, status, System.currentTimeMillis(), code, latencyMs, error)
    }

    suspend fun clearLogs() {
        db.logDao().clear()
    }

    fun backupsDir(): File = File(context.filesDir, "backups").apply { mkdirs() }

    suspend fun buildConfigJson(): JSONObject {
        val all = db.siteDao().getAll()
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        val st = JSONObject()
        st.put("intervalMinutes", settings.intervalMinutes.first())
        st.put("timeoutSeconds", settings.timeoutSeconds.first())
        st.put("autostart", settings.autostart.first())
        st.put("themeMode", settings.themeMode.first())
        root.put("settings", st)
        val arr = JSONArray()
        for (site in all) {
            arr.put(
                JSONObject()
                    .put("name", site.name)
                    .put("url", site.url)
                    .put("adminUrl", site.adminUrl)
                    .put("notes", site.notes)
            )
        }
        root.put("sites", arr)
        return root
    }

    suspend fun exportBackup(): File {
        val all = db.siteDao().getAll()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(backupsDir(), "sitemanager-backup-$stamp.json")
        file.writeText(buildConfigJson().toString(2))
        log("backup", "Backup exported: ${file.name} (${all.size} site(s))")
        return file
    }

    /**
     * Validates and imports a JSON backup / config. Returns the number of sites imported.
     * Throws IllegalArgumentException on invalid content.
     */
    suspend fun importBackup(json: String): Int {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JSON: ${e.message}")
        }
        if (root.optInt("version", -1) != 1) {
            throw IllegalArgumentException("Unsupported backup version (expected version: 1)")
        }
        val arr = root.optJSONArray("sites")
            ?: throw IllegalArgumentException("Missing \"sites\" array")
        var count = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name", "").trim()
            val url = o.optString("url", "").trim()
            if (name.isEmpty() || url.isEmpty()) continue
            db.siteDao().upsert(
                Site(
                    name = name,
                    url = url,
                    adminUrl = o.optString("adminUrl", ""),
                    notes = o.optString("notes", "")
                )
            )
            count++
        }
        val st = root.optJSONObject("settings")
        if (st != null) {
            settings.setIntervalMinutes(st.optInt("intervalMinutes", 5).coerceIn(1, 120))
            settings.setTimeoutSeconds(st.optInt("timeoutSeconds", 10).coerceIn(3, 60))
            settings.setAutostart(st.optBoolean("autostart", false))
            val theme = st.optString("themeMode", "system")
            settings.setThemeMode(if (theme in setOf("system", "light", "dark")) theme else "system")
        }
        log("backup", "Config imported: $count site(s)")
        return count
    }
}
