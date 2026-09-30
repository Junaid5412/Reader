package com.junaid.sitemanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteDao {

    @Query("SELECT * FROM sites ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Site>>

    @Query("SELECT * FROM sites")
    suspend fun getAll(): List<Site>

    @Query("SELECT * FROM sites WHERE id = :id")
    suspend fun getById(id: Long): Site?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(site: Site): Long

    @Delete
    suspend fun delete(site: Site)

    @Query(
        "UPDATE sites SET status = :status, lastChecked = :checked, " +
            "lastHttpCode = :code, lastLatencyMs = :latency, lastError = :error WHERE id = :id"
    )
    suspend fun updateStatus(
        id: Long,
        status: String,
        checked: Long,
        code: Int,
        latency: Long,
        error: String
    )
}
