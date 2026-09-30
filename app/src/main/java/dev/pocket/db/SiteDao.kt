package dev.pocket.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteDao {

    @Query("SELECT * FROM sites ORDER BY port ASC")
    fun getAll(): Flow<List<SiteEntity>>

    @Query("SELECT * FROM sites ORDER BY port ASC")
    suspend fun getAllOnce(): List<SiteEntity>

    @Query("SELECT * FROM sites WHERE slug = :slug LIMIT 1")
    suspend fun getBySlug(slug: String): SiteEntity?

    @Query("SELECT MAX(port) FROM sites")
    suspend fun maxPort(): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(site: SiteEntity): Long

    @Delete
    suspend fun delete(site: SiteEntity)
}
