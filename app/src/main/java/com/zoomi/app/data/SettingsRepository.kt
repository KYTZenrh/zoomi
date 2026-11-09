package com.zoomi.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zoomi.core.places.SavedPlace
import com.zoomi.core.places.SavedPlaces
import com.zoomi.core.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "zoomi_settings")

/** Local settings and saved places. Stored only on the device (milestone (e) adds optional encrypted sync). */
class SettingsRepository(private val context: Context) {
    companion object {
        /** FOSSGIS community Valhalla server: 1 req/s, attribution + fixthemap link required. */
        const val DEFAULT_VALHALLA = "https://valhalla1.openstreetmap.de/route"
        /** komoot's public Photon server: fair use. */
        const val DEFAULT_PHOTON = "https://photon.komoot.io"
    }

    private val themeKey = stringPreferencesKey("theme_mode")
    private val courseUpKey = booleanPreferencesKey("course_up")
    private val valhallaKey = stringPreferencesKey("valhalla_url")
    private val photonKey = stringPreferencesKey("photon_url")
    private val voiceMutedKey = booleanPreferencesKey("voice_muted")
    private val placesKey = stringPreferencesKey("saved_places")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.AUTO
    }
    val courseUp: Flow<Boolean> = context.dataStore.data.map { it[courseUpKey] ?: true }
    val valhallaUrl: Flow<String> = context.dataStore.data.map { it[valhallaKey] ?: DEFAULT_VALHALLA }
    val photonUrl: Flow<String> = context.dataStore.data.map { it[photonKey] ?: DEFAULT_PHOTON }
    val voiceMuted: Flow<Boolean> = context.dataStore.data.map { it[voiceMutedKey] ?: false }
    val savedPlaces: Flow<List<SavedPlace>> = context.dataStore.data.map { SavedPlaces.decode(it[placesKey]) }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[themeKey] = mode.name }
    suspend fun setCourseUp(value: Boolean) = context.dataStore.edit { it[courseUpKey] = value }
    suspend fun setValhallaUrl(url: String?) = context.dataStore.edit {
        if (url.isNullOrBlank()) it.remove(valhallaKey) else it[valhallaKey] = url.trim()
    }
    suspend fun setPhotonUrl(url: String?) = context.dataStore.edit {
        if (url.isNullOrBlank()) it.remove(photonKey) else it[photonKey] = url.trim()
    }
    suspend fun setVoiceMuted(value: Boolean) = context.dataStore.edit { it[voiceMutedKey] = value }

    suspend fun updatePlaces(transform: (List<SavedPlace>) -> List<SavedPlace>) = context.dataStore.edit {
        it[placesKey] = SavedPlaces.encode(transform(SavedPlaces.decode(it[placesKey])))
    }
}
