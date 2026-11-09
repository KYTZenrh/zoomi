package com.zoomi.app.nav

import android.content.Context
import android.util.Log
import com.stadiamaps.ferrostar.core.CustomRouteProvider
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.config.ValhallaConfigFactory
import com.valhalla.valhalla.config.ValhallaConfigManager
import com.valhalla.valhalla.files.ValhallaFile
import com.zoomi.app.net.ZoomiHttp
import com.zoomi.app.regions.InstalledRegion
import com.zoomi.app.regions.RegionStore
import com.zoomi.core.geo.EncodedPolyline
import com.zoomi.core.location.LatLon
import com.zoomi.core.nav.RouteErrors
import com.zoomi.core.nav.RouteFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteAdapter
import uniffi.ferrostar.RouteRequest
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WellKnownRouteProvider
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class RouteSource { ONLINE, OFFLINE }

/** A routing answer we should show the user as-is (e.g. "no route"), not retry elsewhere. */
class RoutingException(message: String, val failure: RouteFailure = RouteFailure.OTHER) : Exception(message)

/**
 * One request format, two engines:
 * 1. Online Valhalla server (when Android reports a validated connection).
 * 2. On-device Valhalla (valhalla-mobile) using a downloaded region's tile extract, when offline
 *    or when the server can't be reached. The SAME request JSON goes to both, and Ferrostar's
 *    own Valhalla parser reads both answers, so online and offline routes behave identically.
 *
 * Used for the first route and for every automatic reroute, so losing signal mid-trip
 * transparently switches to offline rerouting inside downloaded regions.
 */
class HybridRouteProvider(
    context: Context,
    private val regions: RegionStore,
    private val valhallaUrl: () -> String,
    private val optionsJson: () -> String,
    private val isOnline: () -> Boolean,
) : CustomRouteProvider {
    private val appContext = context.applicationContext
    private val engineLock = Mutex()
    private var engine: Valhalla? = null
    private var engineRegion: String? = null

    /** Valhalla costing: "auto", "bicycle" or "pedestrian". */
    @Volatile var costing: String = "auto"

    private val _lastSource = MutableStateFlow<RouteSource?>(null)
    val lastSource: StateFlow<RouteSource?> = _lastSource.asStateFlow()

    private val http = ZoomiHttp.client.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getRoutes(userLocation: UserLocation, waypoints: List<Waypoint>): List<Route> =
        withContext(Dispatchers.IO) {
            RouteAdapter.fromWellKnownRouteProvider(
                WellKnownRouteProvider.Valhalla(valhallaUrl(), costing, optionsJson())
            ).use { adapter ->
                val req = adapter.generateRequest(userLocation, waypoints) as RouteRequest.HttpPost
                val points = listOf(userLocation.coordinates.lat to userLocation.coordinates.lng) +
                    waypoints.map { it.coordinate.lat to it.coordinate.lng }
                val (answer, source) = run(req.url, String(req.body), points) { e, body -> e.routeRaw(body) }
                errorOf(answer)?.let { throw it }
                val routes = adapter.parseResponse(answer.toByteArray())
                _lastSource.value = source
                routes
            }
        }

    /**
     * Valhalla trace_attributes along a route (bridges, tunnels, ferries, names), online or offline
     * with the same rules as routing. Returns raw JSON.
     */
    suspend fun traceAttributes(shape: List<LatLon>): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("encoded_polyline", EncodedPolyline.encode(shape, 6))
            put("shape_match", "edge_walk")
            put("costing", costing)
            put("filters", buildJsonObject {
                put("action", "include")
                put("attributes", buildJsonArray {
                    listOf("edge.names", "edge.length", "edge.bridge", "edge.tunnel", "edge.use").forEach { add(it) }
                })
            })
        }.toString()
        val url = valhallaUrl().replace(Regex("/route/?$"), "/trace_attributes")
        val points = listOfNotNull(shape.firstOrNull(), shape.lastOrNull()).map { it.lat to it.lon }
        val (answer, _) = run(url, body, points) { e, b -> e.traceAttributesRaw(b) }
        errorOf(answer)?.let { throw it }
        answer
    }

    /** Online first (if connected), then the on-device engine for the region covering [points]. */
    private suspend fun run(
        url: String,
        body: String,
        points: List<Pair<Double, Double>>,
        offline: (Valhalla, String) -> String,
    ): Pair<String, RouteSource> {
        var onlineFailure: String? = null
        if (isOnline()) {
            try {
                return post(url, body) to RouteSource.ONLINE
            } catch (e: IOException) {
                Log.w("Zoomi", "online routing failed, trying offline", e)
                onlineFailure = "routing server unreachable"
            }
        }
        val region = regions.routingRegionFor(points) ?: throw RoutingException(
            (if (onlineFailure != null) "The routing server is unreachable" else "You're offline") +
                " and no downloaded region covers this whole trip. Download a region in Settings → Offline regions."
        )
        val answer = engineLock.withLock { offline(engineFor(region), body) }
        return answer to RouteSource.OFFLINE
    }

    /** Returns the body for 2xx and 4xx (4xx bodies carry Valhalla's error); 5xx / network errors throw IOException. */
    private fun post(url: String, body: String): String {
        val r = Request.Builder().url(url).post(body.toRequestBody("application/json".toMediaType())).build()
        http.newCall(r).execute().use { res ->
            val text = res.body.string()
            if (res.code in 500..599) throw IOException("routing server error HTTP ${res.code}")
            if (!res.isSuccessful && errorOf(text) == null) throw RoutingException("Routing failed (HTTP ${res.code})")
            return text
        }
    }

    /** Valhalla error envelope ({"error_code","error"}) or OSRM-style ({"code": "NoRoute", "message"}). */
    private fun errorOf(text: String): RoutingException? = runCatching {
        val o = json.parseToJsonElement(text) as JsonObject
        val err = (o["error"] as? JsonPrimitive)?.content
        val errCode = (o["error_code"] as? JsonPrimitive)?.content
        val code = (o["code"] as? JsonPrimitive)?.content
        val msg = (o["message"] as? JsonPrimitive)?.content
        when {
            err != null -> RoutingException(err, RouteErrors.classify(errCode, err))
            code != null && code != "Ok" -> RoutingException(msg ?: code, RouteErrors.classify(code, msg))
            else -> null
        }
    }.getOrNull()

    private fun engineFor(region: InstalledRegion): Valhalla {
        val id = region.manifest.id
        engine?.let { if (engineRegion == id) return it }
        engine?.close()
        val tar = region.routingFile!!.absolutePath
        val config = ValhallaConfigFactory.usingTileExtract(tar)
        val manager = ValhallaConfigManager(appContext, ValhallaFile(appContext, "valhalla-$id.json"))
        return Valhalla(appContext, config, manager).also { engine = it; engineRegion = id }
    }

    /** Called when a region is deleted, so its file handles are released. */
    suspend fun closeEngine() = engineLock.withLock {
        engine?.close(); engine = null; engineRegion = null
    }
}
