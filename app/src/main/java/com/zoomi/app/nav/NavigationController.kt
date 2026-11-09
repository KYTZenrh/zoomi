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
import com.zoomi.app.location.GnssLocationSource
import com.zoomi.app.net.ZoomiHttp
import com.zoomi.core.nav.ValhallaLocale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration.Companion.seconds
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
import uniffi.ferrostar.WellKnownRouteProvider
import uniffi.ferrostar.stepAdvanceDistanceEntryAndExit
import uniffi.ferrostar.stepAdvanceDistanceToEndOfStep
import java.util.Locale

/** Speed limit for the current road, when OSM has it. */
data class SpeedLimit(val value: Int, val unit: String)

/**
 * Owns the Ferrostar navigation core: route requests (Valhalla), step tracking,
 * off-route detection with automatic reroute, voice (Android TTS), and the
 * foreground service that keeps guidance running with the screen off.
 */
class NavigationController(context: Context, private val gnss: GnssLocationSource) {
    private val appContext = context.applicationContext
    private val tts = AndroidTtsObserver(appContext).also { it.start() }
    private val annotations = valhallaExtendedOSRMAnnotationPublisher()
    private val foreground = FerrostarForegroundServiceManager(appContext, ZoomiNotificationBuilder(appContext))

    private val coreFlow = MutableStateFlow<FerrostarCore?>(null)
    private var coreUrl: String? = null

    /** Live navigation state of whichever core is active. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<NavigationState> = coreFlow.flatMapLatest { it?.state ?: flowOf(NavigationState()) }

    val language: String get() = ValhallaLocale.pick(Locale.getDefault().toLanguageTag())

    private val config = NavigationControllerConfig(
        WaypointAdvanceMode.WaypointWithinRange(100.0),
        stepAdvanceDistanceEntryAndExit(30u, 5u, 32u),
        stepAdvanceDistanceToEndOfStep(10u, 32u),
        RouteDeviationTracking.StaticThreshold(25u, 50.0),
        CourseFiltering.SNAP_TO_ROUTE,
    )

    /** Builds (or reuses) the core for the configured Valhalla endpoint. */
    private fun coreFor(valhallaUrl: String): FerrostarCore {
        coreFlow.value?.let { if (coreUrl == valhallaUrl) return it }
        val options = buildJsonObject {
            put("alternates", 2)
            put("units", "kilometers")
            put("language", language)
        }.toString()
        val c = FerrostarCore(
            WellKnownRouteProvider.Valhalla(valhallaUrl, "auto", options),
            OkHttpClientProvider(ZoomiHttp.client),
            GnssNavigationLocationProvider(gnss),
            config,
            foreground,
        )
        // Defaults: on deviation, fetch new routes and switch to the first automatically.
        c.minimumTimeBeforeRecalculation = 5.seconds
        c.spokenInstructionObserver = tts
        coreUrl = valhallaUrl
        coreFlow.value = c
        return c
    }

    suspend fun routes(valhallaUrl: String, from: Location, toLat: Double, toLon: Double): List<Route> =
        coreFor(valhallaUrl).getRoutes(
            from.toUserLocation(),
            listOf(Waypoint(GeographicCoordinate(toLat, toLon), WaypointKind.BREAK)),
        )

    fun start(valhallaUrl: String, route: Route) {
        coreFor(valhallaUrl).startNavigation(route, config)
    }

    fun stop() {
        coreFlow.value?.stopNavigation()
        tts.stopAndClearQueue()
    }

    fun setMuted(muted: Boolean) = tts.setMuted(muted)

    fun speedLimit(state: NavigationState): SpeedLimit? =
        when (val s = runCatching { annotations.map(state).speed }.getOrNull()) {
            is Speed.Value -> SpeedLimit(Math.round(s.value).toInt(), s.unit.text)
            else -> null
        }
}
