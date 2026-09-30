package com.junaid.sitemanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.junaid.sitemanager.SiteManagerApp
import com.junaid.sitemanager.monitor.MonitorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * When "autostart on boot" is enabled, runs one real monitoring check cycle
 * after the device finishes booting.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as SiteManagerApp
                if (app.settings.autostart.first()) {
                    MonitorManager.runSingleCycle(app)
                    app.repository.log("monitor", "Autostart: check cycle ran after boot")
                }
            } catch (_: Exception) {
                // Best effort only.
            } finally {
                pending.finish()
            }
        }
    }
}
