package com.junaid.sitemanager.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Site::class, LogEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun siteDao(): SiteDao
    abstract fun logDao(): LogDao
}
