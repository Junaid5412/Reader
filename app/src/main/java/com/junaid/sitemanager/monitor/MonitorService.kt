package com.junaid.sitemanager.monitor

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.junaid.sitemanager.MainActivity
import com.junaid.sitemanager.R
import com.junaid.sitemanager.SiteManagerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps site monitoring alive with a persistent notification.
 */
class MonitorService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as SiteManagerApp
        when (intent?.action) {
            ACTION_STOP -> {
                MonitorManager.stop(app)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                // ACTION_START or a system restart (null intent): resume monitoring.
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIF_ID,
                            buildNotification("Site monitoring is active"),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } else {
                        startForeground(NOTIF_ID, buildNotification("Site monitoring is active"))
                    }
                } catch (e: Exception) {
                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching {
                            app.repository.log("monitor", "Foreground start failed: ${e.message}")
                        }
                    }
                }
                MonitorManager.start(app)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { MonitorManager.stop(application as SiteManagerApp) }
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, SiteManagerApp.MONITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_monitor)
            .setContentTitle("Site Manager")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.junaid.sitemanager.monitor.START"
        const val ACTION_STOP = "com.junaid.sitemanager.monitor.STOP"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MonitorService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
