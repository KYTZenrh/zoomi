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
        /** Offline regions built by this repository's GitHub Actions workflow (release "regions"). */
        const val DEFAULT_CATALOG = "https://github.com/KYTZenrh/zoomi/releases/download/regions/catalog.json"
        /** Google's speech engine if installed (Android falls back to the phone default otherwise). */
        const val DEFAULT_TTS_ENGINE = "com.google.android.tts"
        const val DEFAULT_IMAGERY = "MAPTILER"
    }

    private val themeKey = stringPreferencesKey("theme_mode")
    private val courseUpKey = booleanPreferencesKey("course_up")
    private val valhallaKey = stringPreferencesKey("valhalla_url")
    private val photonKey = stringPreferencesKey("photon_url")
    private val voiceMutedKey = booleanPreferencesKey("voice_muted")
    private val placesKey = stringPreferencesKey("saved_places")
    private val catalogKey = stringPreferencesKey("region_catalog_url")
    private val satelliteKey = booleanPreferencesKey("map_satellite")
    private val imageryUrlKey = stringPreferencesKey("imagery_url")
    private val imageryAttrKey = stringPreferencesKey("imagery_attribution")
    private val imagerySourceKey = stringPreferencesKey("imagery_source") // EOX | ESRI | CUSTOM
    private val esriKeyKey = stringPreferencesKey("esri_api_key")
    private val maptilerKeyKey = stringPreferencesKey("maptiler_api_key")
    private val maptilerTileSizeKey = androidx.datastore.preferences.core.intPreferencesKey("maptiler_tile_size")
    private val guidanceLangKey = stringPreferencesKey("guidance_language")
    private val ttsEngineKey = stringPreferencesKey("tts_engine")
    private val ttsVoiceKey = stringPreferencesKey("tts_voice")
    private val puckColorKey = stringPreferencesKey("puck_color")
    private val avoidTollsKey = booleanPreferencesKey("avoid_tolls")
    private val avoidHighwaysKey = booleanPreferencesKey("avoid_highways")
    private val avoidFerriesKey = booleanPreferencesKey("avoid_ferries")
    private val speedAlertKey = booleanPreferencesKey("speed_alert")
    private val seededKey = booleanPreferencesKey("seeded_build_defaults")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.AUTO
    }
    val courseUp: Flow<Boolean> = context.dataStore.data.map { it[courseUpKey] ?: true }
    val valhallaUrl: Flow<String> = context.dataStore.data.map { it[valhallaKey] ?: DEFAULT_VALHALLA }
    val photonUrl: Flow<String> = context.dataStore.data.map { it[photonKey] ?: DEFAULT_PHOTON }
    val voiceMuted: Flow<Boolean> = context.dataStore.data.map { it[voiceMutedKey] ?: false }
    val guidanceLanguage: Flow<com.zoomi.app.nav.GuidanceLanguage> = context.dataStore.data.map { p ->
        p[guidanceLangKey]?.let { runCatching { com.zoomi.app.nav.GuidanceLanguage.valueOf(it) }.getOrNull() }
            ?: com.zoomi.app.nav.GuidanceLanguage.AUTO
    }
    /** Speech engine package (null = phone default) and voice name (null = engine default for the language). */
    val ttsEngine: Flow<String?> = context.dataStore.data.map { p ->
        // "" = the user picked "Phone default"; missing = Zoomi's default engine.
        (p[ttsEngineKey] ?: DEFAULT_TTS_ENGINE).ifBlank { null }
    }
    val ttsVoice: Flow<String?> = context.dataStore.data.map { it[ttsVoiceKey] }
    val satellite: Flow<Boolean> = context.dataStore.data.map { it[satelliteKey] ?: false }
    /** Selected satellite imagery. Falls back to EOxCloudless when the chosen source isn't fully configured. */
    val imagery: Flow<com.zoomi.app.map.Imagery> = context.dataStore.data.map { p ->
        val url = p[imageryUrlKey]
        val key = p[esriKeyKey]
        when (p[imagerySourceKey] ?: DEFAULT_IMAGERY) {
            "ESRI" -> if (!key.isNullOrBlank()) com.zoomi.app.map.Imagery.esri(key) else com.zoomi.app.map.Imagery.EOX
            "MAPTILER" -> p[maptilerKeyKey]?.takeIf { it.isNotBlank() }
                ?.let { com.zoomi.app.map.Imagery.mapTiler(it, p[maptilerTileSizeKey] ?: 512) } ?: com.zoomi.app.map.Imagery.EOX
            "CUSTOM" -> if (!url.isNullOrBlank())
                com.zoomi.app.map.Imagery(url, p[imageryAttrKey]?.ifBlank { null } ?: "Imagery: custom source")
            else com.zoomi.app.map.Imagery.EOX
            else -> com.zoomi.app.map.Imagery.EOX
        }
    }
    val imagerySource: Flow<String> = context.dataStore.data.map { p ->
        p[imagerySourceKey] ?: DEFAULT_IMAGERY
    }
    val puckColor: Flow<com.zoomi.app.map.PuckColor> = context.dataStore.data.map { p ->
        p[puckColorKey]?.let { runCatching { com.zoomi.app.map.PuckColor.valueOf(it) }.getOrNull() } ?: com.zoomi.app.map.PuckColor.VIOLET
    }
    val avoidTolls: Flow<Boolean> = context.dataStore.data.map { it[avoidTollsKey] ?: false }
    val avoidHighways: Flow<Boolean> = context.dataStore.data.map { it[avoidHighwaysKey] ?: false }
    val avoidFerries: Flow<Boolean> = context.dataStore.data.map { it[avoidFerriesKey] ?: false }
    val speedAlert: Flow<Boolean> = context.dataStore.data.map { it[speedAlertKey] ?: true }
    val esriKey: Flow<String> = context.dataStore.data.map { it[esriKeyKey] ?: "" }
    val maptilerKey: Flow<String> = context.dataStore.data.map { it[maptilerKeyKey] ?: "" }
    val imageryCustomUrl: Flow<String> = context.dataStore.data.map { it[imageryUrlKey] ?: "" }
    val imageryCustomAttribution: Flow<String> = context.dataStore.data.map { it[imageryAttrKey] ?: "" }
    val catalogUrl: Flow<String> = context.dataStore.data.map { it[catalogKey] ?: DEFAULT_CATALOG }
    val savedPlaces: Flow<List<SavedPlace>> = context.dataStore.data.map { SavedPlaces.decode(it[placesKey]) }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[themeKey] = mode.name }
    suspend fun setCourseUp(value: Boolean) = context.dataStore.edit { it[courseUpKey] = value }
    suspend fun setValhallaUrl(url: String?) = context.dataStore.edit {
        if (url.isNullOrBlank()) it.remove(valhallaKey) else it[valhallaKey] = url.trim()
    }
    suspend fun setPhotonUrl(url: String?) = context.dataStore.edit {
        if (url.isNullOrBlank()) it.remove(photonKey) else it[photonKey] = url.trim()
    }
    suspend fun setCatalogUrl(url: String) = context.dataStore.edit {
        if (url.isBlank()) it.remove(catalogKey) else it[catalogKey] = url.trim()
    }
    suspend fun setSatellite(on: Boolean) = context.dataStore.edit { it[satelliteKey] = on }
    suspend fun setImagery(url: String, attribution: String) = context.dataStore.edit {
        if (url.isBlank()) { it.remove(imageryUrlKey); it.remove(imageryAttrKey) }
        else { it[imageryUrlKey] = url.trim(); it[imageryAttrKey] = attribution.trim() }
    }
    /** Stored only on this phone, with the tile size measured from a real tile. */
    suspend fun setMaptilerKey(key: String, tileSize: Int) = context.dataStore.edit {
        if (key.isBlank()) { it.remove(maptilerKeyKey); it.remove(maptilerTileSizeKey) }
        else { it[maptilerKeyKey] = key.trim(); it[maptilerTileSizeKey] = tileSize }
    }
    suspend fun setImagerySource(source: String) = context.dataStore.edit { it[imagerySourceKey] = source }
    /** Stored only on this phone (never synced, never in the repo). */
    suspend fun setEsriKey(key: String) = context.dataStore.edit {
        if (key.isBlank()) it.remove(esriKeyKey) else it[esriKeyKey] = key.trim()
    }
    suspend fun setGuidanceLanguage(l: com.zoomi.app.nav.GuidanceLanguage) = context.dataStore.edit {
        it[guidanceLangKey] = l.name
        it.remove(ttsVoiceKey) // a voice belongs to one language
    }
    suspend fun setTtsEngine(pkg: String?) = context.dataStore.edit {
        it[ttsEngineKey] = pkg ?: "" // "" = phone default (kept, so the Zoomi default doesn't come back)
        it.remove(ttsVoiceKey)
    }
    suspend fun setTtsVoice(name: String?) = context.dataStore.edit { if (name == null) it.remove(ttsVoiceKey) else it[ttsVoiceKey] = name }
    suspend fun setPuckColor(c: com.zoomi.app.map.PuckColor) = context.dataStore.edit { it[puckColorKey] = c.name }
    suspend fun setAvoidTolls(v: Boolean) = context.dataStore.edit { it[avoidTollsKey] = v }
    suspend fun setAvoidHighways(v: Boolean) = context.dataStore.edit { it[avoidHighwaysKey] = v }
    suspend fun setAvoidFerries(v: Boolean) = context.dataStore.edit { it[avoidFerriesKey] = v }
    suspend fun setSpeedAlert(v: Boolean) = context.dataStore.edit { it[speedAlertKey] = v }

    /**
     * A personal build can carry a MapTiler key (local.properties, never in the repo). Save it once on
     * first start, so it stays after updating to a build without the key.
     */
    suspend fun seedBuildDefaults(maptilerKey: String) = context.dataStore.edit {
        if (it[seededKey] == true) return@edit
        if (maptilerKey.isNotBlank() && it[maptilerKeyKey].isNullOrBlank()) {
            it[maptilerKeyKey] = maptilerKey
            it[maptilerTileSizeKey] = 512
        }
        it[seededKey] = true
    }

    suspend fun setVoiceMuted(value: Boolean) = context.dataStore.edit { it[voiceMutedKey] = value }

    suspend fun updatePlaces(transform: (List<SavedPlace>) -> List<SavedPlace>) = context.dataStore.edit {
        it[placesKey] = SavedPlaces.encode(transform(SavedPlaces.decode(it[placesKey])))
    }
}
