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
class RoutingException(message: String) : Exception(message)

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

    private val _lastSource = MutableStateFlow<RouteSource?>(null)
    val lastSource: StateFlow<RouteSource?> = _lastSource.asStateFlow()

    private val http = ZoomiHttp.client.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getRoutes(userLocation: UserLocation, waypoints: List<Waypoint>): List<Route> =
        withContext(Dispatchers.IO) {
            RouteAdapter.fromWellKnownRouteProvider(
                WellKnownRouteProvider.Valhalla(valhallaUrl(), "auto", optionsJson())
            ).use { adapter ->
                val req = adapter.generateRequest(userLocation, waypoints) as RouteRequest.HttpPost
                var onlineFailure: String? = null
                if (isOnline()) {
                    try {
                        val routes = adapter.parseResponse(post(req))
                        _lastSource.value = RouteSource.ONLINE
                        return@withContext routes
                    } catch (e: IOException) {
                        Log.w("Zoomi", "online routing failed, trying offline", e)
                        onlineFailure = "routing server unreachable"
                    }
                }
                val points = listOf(userLocation.coordinates.lat to userLocation.coordinates.lng) +
                    waypoints.map { it.coordinate.lat to it.coordinate.lng }
                val region = regions.routingRegionFor(points) ?: throw RoutingException(
                    (if (onlineFailure != null) "The routing server is unreachable" else "You're offline") +
                        " and no downloaded region covers this whole trip. Download a region in Settings → Offline regions."
                )
                val answer = engineLock.withLock { engineFor(region).routeRaw(String(req.body)) }
                errorMessage(answer)?.let { throw RoutingException(it) }
                val routes = adapter.parseResponse(answer.toByteArray())
                _lastSource.value = RouteSource.OFFLINE
                routes
            }
        }

    private fun post(req: RouteRequest.HttpPost): ByteArray {
        val body = req.body.toRequestBody("application/json".toMediaType())
        val r = Request.Builder().url(req.url).post(body).apply { req.headers.forEach { (k, v) -> header(k, v) } }.build()
        http.newCall(r).execute().use { res ->
            val bytes = res.body.bytes()
            if (res.code in 500..599) throw IOException("routing server error HTTP ${res.code}")
            if (!res.isSuccessful) throw RoutingException(errorMessage(String(bytes)) ?: "Routing failed (HTTP ${res.code})")
            return bytes
        }
    }

    /** Valhalla error envelope ({"error": ...}) or OSRM-style ({"code": "NoRoute", "message": ...}). */
    private fun errorMessage(text: String): String? = runCatching {
        val o = json.parseToJsonElement(text) as JsonObject
        val err = (o["error"] as? JsonPrimitive)?.content
        val code = (o["code"] as? JsonPrimitive)?.content
        when {
            err != null -> err
            code != null && code != "Ok" -> (o["message"] as? JsonPrimitive)?.content ?: code
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
