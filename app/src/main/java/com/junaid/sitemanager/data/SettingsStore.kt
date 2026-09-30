package com.junaid.sitemanager.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("site_manager_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val INTERVAL = intPreferencesKey("interval_minutes")
        val TIMEOUT = intPreferencesKey("timeout_seconds")
        val AUTOSTART = booleanPreferencesKey("autostart")
        val THEME = stringPreferencesKey("theme_mode")
    }

    val intervalMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.INTERVAL] ?: 5 }
    val timeoutSeconds: Flow<Int> = context.dataStore.data.map { it[Keys.TIMEOUT] ?: 10 }
    val autostart: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTOSTART] ?: false }
    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "system" }

    suspend fun setIntervalMinutes(v: Int) {
        context.dataStore.edit { it[Keys.INTERVAL] = v }
    }

    suspend fun setTimeoutSeconds(v: Int) {
        context.dataStore.edit { it[Keys.TIMEOUT] = v }
    }

    suspend fun setAutostart(v: Boolean) {
        context.dataStore.edit { it[Keys.AUTOSTART] = v }
    }

    suspend fun setThemeMode(v: String) {
        context.dataStore.edit { it[Keys.THEME] = v }
    }
}
