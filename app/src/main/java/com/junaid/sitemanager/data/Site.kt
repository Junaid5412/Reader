package com.junaid.sitemanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sites")
data class Site(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val adminUrl: String = "",
    val notes: String = "",
    val status: String = STATUS_UNKNOWN,
    val lastChecked: Long = 0L,
    val lastHttpCode: Int = 0,
    val lastLatencyMs: Long = 0L,
    val lastError: String = ""
) {
    companion object {
        const val STATUS_UP = "up"
        const val STATUS_DOWN = "down"
        const val STATUS_UNKNOWN = "unknown"
    }
}
