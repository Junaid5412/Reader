package com.junaid.sitemanager

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.room.Room
import com.junaid.sitemanager.data.AppDatabase
import com.junaid.sitemanager.data.SettingsStore
import com.junaid.sitemanager.data.SiteRepository

class SiteManagerApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var repository: SiteRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(this, AppDatabase::class.java, "site-manager.db").build()
        settings = SettingsStore(this)
        repository = SiteRepository(this, database, settings)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MONITOR_CHANNEL_ID,
                "Site monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Ongoing site monitoring status" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val MONITOR_CHANNEL_ID = "monitor_channel"
    }
}
