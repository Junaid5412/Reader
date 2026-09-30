package dev.pocket

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.pocket.data.Installer
import dev.pocket.data.Prefs
import dev.pocket.server.ServerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Starts the servers after device boot when the user enabled autostart.
 * The startForegroundService call is wrapped in try/catch because Android 12+
 * restricts background foreground-service starts.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val autostart = Prefs.autostartFlow(context).first()
                if (autostart && Installer.isInstalled(context)) {
                    val i = Intent(context, ServerService::class.java)
                        .setAction(ServerService.ACTION_START_ALL)
                    try {
                        context.startForegroundService(i)
                    } catch (_: Exception) {
                        // Background start not allowed on this device/version — skip.
                    }
                }
            } catch (_: Exception) {
                // Never crash the boot receiver.
            }
        }
    }
}
