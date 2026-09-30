package dev.pocket.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sites",
    indices = [
        Index(value = ["slug"], unique = true),
        Index(value = ["port"], unique = true)
    ]
)
data class SiteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val slug: String,
    val port: Int,
    val builtIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
