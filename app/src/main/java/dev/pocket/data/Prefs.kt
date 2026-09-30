package dev.pocket.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("pockethost")

/** App preferences backed by DataStore. */
object Prefs {
    private val INSTALLED = booleanPreferencesKey("installed")
    private val AUTOSTART = booleanPreferencesKey("autostart")
    private val THEME_MODE = intPreferencesKey("theme_mode") // 0 system, 1 light, 2 dark

    fun installedFlow(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[INSTALLED] == true }

    suspend fun setInstalled(ctx: Context, v: Boolean) {
        ctx.dataStore.edit { it[INSTALLED] = v }
    }

    suspend fun isInstalledFlag(ctx: Context): Boolean =
        ctx.dataStore.data.map { it[INSTALLED] == true }.first()

    fun autostartFlow(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[AUTOSTART] == true }

    suspend fun setAutostart(ctx: Context, v: Boolean) {
        ctx.dataStore.edit { it[AUTOSTART] = v }
    }

    fun themeModeFlow(ctx: Context): Flow<Int> =
        ctx.dataStore.data.map { it[THEME_MODE] ?: 0 }

    suspend fun setThemeMode(ctx: Context, v: Int) {
        ctx.dataStore.edit { it[THEME_MODE] = v }
    }
}
