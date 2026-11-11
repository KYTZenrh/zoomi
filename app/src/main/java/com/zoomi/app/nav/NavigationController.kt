package com.zoomi.app.nav

import android.content.Context
import android.location.Location
import com.stadiamaps.ferrostar.core.AndroidTtsObserver
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.NavigationState
import com.stadiamaps.ferrostar.core.annotation.Speed
import com.stadiamaps.ferrostar.core.annotation.valhalla.valhallaExtendedOSRMAnnotationPublisher
import com.stadiamaps.ferrostar.core.http.OkHttpClientProvider
import com.stadiamaps.ferrostar.core.location.toUserLocation
import com.stadiamaps.ferrostar.core.service.FerrostarForegroundServiceManager
import com.zoomi.app.data.SettingsRepository
import com.zoomi.app.location.PositionEngine
import com.zoomi.app.net.NetworkMonitor
import com.zoomi.app.net.ZoomiHttp
import com.zoomi.app.regions.RegionStore
import com.zoomi.core.nav.ValhallaLocale
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import uniffi.ferrostar.CourseFiltering
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.NavigationControllerConfig
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteDeviationTracking
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointAdvanceMode
import uniffi.ferrostar.WaypointKind
import uniffi.ferrostar.stepAdvanceDistanceEntryAndExit
import uniffi.ferrostar.stepAdvanceDistanceToEndOfStep
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/** Speed limit for the current road, when OSM has it. */
data class SpeedLimit(val value: Int, val unit: String)

/**
 * Owns the Ferrostar navigation core: route requests (online or on-device Valhalla), step
 * tracking, off-route detection with automatic reroute, voice (Android TTS), and the foreground
 * service that keeps guidance running with the screen off.
 */
class NavigationController(
    context: Context,
    position: PositionEngine,
    regions: RegionStore,
    network: NetworkMonitor,
) {
    private val appContext = context.applicationContext
    private var tts = newTts(null)
    @Volatile private var ttsVoiceName: String? = null
    @Volatile private var ttsMuted = false

    /** Guidance language (affects instruction text from Valhalla and the TTS voice). */
    @Volatile var guidanceLanguage: GuidanceLanguage = GuidanceLanguage.AUTO

    private fun newTts(engine: String?): AndroidTtsObserver =
        AndroidTtsObserver(appContext, engine = engine, statusObserver = object : com.stadiamaps.ferrostar.core.AndroidTtsStatusListener {
            override fun onTtsInitialized(tts: android.speech.tts.TextToSpeech?, status: Int) {
                if (status == android.speech.tts.TextToSpeech.SUCCESS) applyVoice(tts)
            }
            override fun onTtsShutdownAndRelease() = Unit
            override fun onTtsSpeakError(utteranceId: String, status: Int) {
                android.util.Log.w("Zoomi", "TTS error $status for $utteranceId")
            }
        }).also { it.start() }

    private fun applyVoice(t: android.speech.tts.TextToSpeech?) {
        t ?: return
        val voice = ttsVoiceName?.let { n -> t.voices?.firstOrNull { it.name == n } }
        if (voice != null) t.voice = voice else t.language = Locale.forLanguageTag(VoiceCatalog.languageTag(guidanceLanguage))
    }

    /** Switch speech engine / voice. A new engine needs a new TTS instance. */
    fun configureVoice(engine: String?, voiceName: String?, language: GuidanceLanguage) {
        guidanceLanguage = language
        ttsVoiceName = voiceName
        if (engine != currentEngine) {
            tts.shutdown()
            tts = newTts(engine)
            currentEngine = engine
            core.spokenInstructionObserver = tts
            tts.setMuted(ttsMuted)
        } else {
            applyVoice(tts.tts)
        }
    }
    private var currentEngine: String? = null
    private val annotations = valhallaExtendedOSRMAnnotationPublisher()
    private val foreground = FerrostarForegroundServiceManager(appContext, ZoomiNotificationBuilder(appContext))

    /** Updated from settings. */
    @Volatile var valhallaUrl: String = SettingsRepository.DEFAULT_VALHALLA

    val language: String get() = ValhallaLocale.pick(VoiceCatalog.languageTag(guidanceLanguage))

    val routeProvider = HybridRouteProvider(
        appContext, regions,
        valhallaUrl = { valhallaUrl },
        optionsJson = {
            buildJsonObject {
                put("alternates", 2)
                put("units", "kilometers")
                put("language", language)
            }.toString()
        },
        isOnline = { network.online.value },
    )

    private val config = NavigationControllerConfig(
        WaypointAdvanceMode.WaypointWithinRange(100.0),
        stepAdvanceDistanceEntryAndExit(30u, 5u, 32u),
        stepAdvanceDistanceToEndOfStep(10u, 32u),
        RouteDeviationTracking.StaticThreshold(25u, 50.0),
        CourseFiltering.SNAP_TO_ROUTE,
    )

    private val core = FerrostarCore(
        routeProvider,
        OkHttpClientProvider(ZoomiHttp.client),
        GnssNavigationLocationProvider(position),
        config,
        foreground,
    ).apply {
        // Defaults kept: on deviation, fetch new routes and switch to the first automatically.
        minimumTimeBeforeRecalculation = 5.seconds
        spokenInstructionObserver = tts
    }

    val state: StateFlow<NavigationState> = core.state

    suspend fun routes(from: Location, toLat: Double, toLon: Double): List<Route> =
        core.getRoutes(startLocation(from), listOf(Waypoint(GeographicCoordinate(toLat, toLon), WaypointKind.BREAK)))

    /**
     * Valhalla treats a start heading as "the vehicle is facing this way" and will route forwards
     * from it (even via a loop). A GPS bearing is only meaningful while moving, so when parked or
     * walking slowly we send no heading and let Valhalla pick the best direction.
     */
    private fun startLocation(from: Location): uniffi.ferrostar.UserLocation {
        val u = from.toUserLocation()
        val moving = from.hasSpeed() && from.speed >= 4f &&
            (!from.hasBearingAccuracy() || from.bearingAccuracyDegrees <= 30f)
        return if (moving) u else uniffi.ferrostar.UserLocation(u.coordinates, u.horizontalAccuracy, null, u.timestamp, u.speed)
    }

    fun start(route: Route) = core.startNavigation(route, config)

    /** Travel mode for the next route requests and reroutes (Valhalla costing). */
    fun setCosting(costing: String) { routeProvider.costing = costing }

    /** Ferries, long bridges and tunnels along a route. */
    suspend fun crossings(route: Route): List<com.zoomi.core.nav.Crossing> {
        val shape = route.geometry.map { com.zoomi.core.location.LatLon(it.lat, it.lng) }
        val text = routeProvider.traceAttributes(shape)
        return com.zoomi.core.nav.Crossings.summarize(com.zoomi.core.nav.Crossings.parseTraceAttributes(text))
    }

    fun stop() {
        core.stopNavigation()
        tts.stopAndClearQueue()
    }

    fun setMuted(muted: Boolean) { ttsMuted = muted; tts.setMuted(muted) }

    fun speedLimit(state: NavigationState): SpeedLimit? =
        when (val s = runCatching { annotations.map(state).speed }.getOrNull()) {
            is Speed.Value -> SpeedLimit(Math.round(s.value).toInt(), s.unit.text)
            else -> null
        }
}
