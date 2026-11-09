package com.zoomi.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zoomi.core.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "zoomi_settings")

/** Local settings. Stored only on the device (milestone (e) adds optional encrypted sync). */
class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val courseUpKey = booleanPreferencesKey("course_up")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.AUTO
    }

    val courseUp: Flow<Boolean> = context.dataStore.data.map { it[courseUpKey] ?: true }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setCourseUp(value: Boolean) {
        context.dataStore.edit { it[courseUpKey] = value }
    }
}
