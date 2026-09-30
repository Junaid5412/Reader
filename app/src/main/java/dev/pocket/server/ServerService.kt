package dev.pocket.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.pocket.MainActivity
import dev.pocket.R

/** Send a control action to [ServerService], e.g. `ctx.serverAction(ServerService.ACTION_START_ALL)`. */
fun Context.serverAction(action: String, svc: ServerManager.Svc? = null) {
    val i = Intent(this, ServerService::class.java).setAction(action)
    if (svc != null) i.putExtra(EXTRA_SVC, svc.name)
    startForegroundService(i)
}

private const val EXTRA_SVC = "svc"

/**
 * Foreground service that keeps the server processes alive while Android
 * would otherwise kill background work. Shows a persistent notification
 * with a Stop action.
 */
class ServerService : Service() {

    companion object {
        const val ACTION_START_ALL = "dev.pocket.action.START_ALL"
        const val ACTION_STOP_ALL = "dev.pocket.action.STOP_ALL"
        const val ACTION_START_SVC = "dev.pocket.action.START_SVC"
        const val ACTION_STOP_SVC = "dev.pocket.action.STOP_SVC"
        const val ACTION_RESTART_SVC = "dev.pocket.action.RESTART_SVC"
        private const val CHANNEL_ID = "pockethost"
        private const val NOTIF_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALL -> ServerManager.startAll(this)
            ACTION_STOP_ALL -> {
                ServerManager.stopAll()
                stopSelf()
            }
            ACTION_START_SVC -> svcExtra(intent)?.let { ServerManager.startSvc(this, it) }
            ACTION_STOP_SVC -> svcExtra(intent)?.let { ServerManager.stopSvc(it) }
            ACTION_RESTART_SVC -> svcExtra(intent)?.let { ServerManager.restartSvc(this, it) }
        }
        updateNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        ServerManager.stopAll()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun svcExtra(intent: Intent): ServerManager.Svc? =
        intent.getStringExtra(EXTRA_SVC)?.let {
            runCatching { ServerManager.Svc.valueOf(it) }.getOrNull()
        }

    private fun createChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL_ID, "PocketHost servers", NotificationManager.IMPORTANCE_LOW)
        mgr.createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ServerService::class.java).setAction(ACTION_STOP_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PocketHost servers running")
            .setContentText("nginx • PHP • MariaDB on localhost")
            .setSmallIcon(R.drawable.ic_stat_server)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }
}
