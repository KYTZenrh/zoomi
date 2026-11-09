package com.zoomi.core.nav

import com.zoomi.core.geo.GeoMath
import com.zoomi.core.location.LatLon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class TravelMode(val valhallaCosting: String?, val label: String) {
    CAR("auto", "Car"), BIKE("bicycle", "Bike"), WALK("pedestrian", "Walk"), TRANSIT(null, "Transit")
}

/** Why a route request failed, in terms the user can act on. */
enum class RouteFailure { NO_ROUTE, TOO_FAR_FOR_SERVER, NO_ROAD_NEAR_DESTINATION, OTHER }

object RouteErrors {
    /**
     * Valhalla errors arrive either as {"error_code":442,"error":"No path could be found for input"}
     * or, with format=osrm, as {"code":"NoRoute"|"DistanceExceeded"|..., "message": ...}.
     */
    fun classify(codeOrNumber: String?, message: String?): RouteFailure {
        val c = codeOrNumber?.lowercase() ?: ""
        val m = message?.lowercase() ?: ""
        return when {
            c == "distanceexceeded" || c == "154" || "exceeds the max distance" in m -> RouteFailure.TOO_FAR_FOR_SERVER
            c == "noroute" || c == "442" || "no path could be found" in m || "impossible route" in m -> RouteFailure.NO_ROUTE
            c == "nosegment" || c == "171" || "no suitable edges" in m -> RouteFailure.NO_ROAD_NEAR_DESTINATION
            else -> RouteFailure.OTHER
        }
    }
}

/**
 * Valhalla snaps an unreachable destination (island, sea, off-road) to the nearest road it CAN
 * reach and happily returns a route ending there. We compare where the route ends with where the
 * user wanted to go; a big gap means "not reachable this way".
 */
object Reachability {
    fun gapMeters(requested: LatLon, routeEnd: LatLon) = GeoMath.haversine(requested, routeEnd)

    fun thresholdMeters(mode: TravelMode) = when (mode) {
        TravelMode.CAR -> 2_000.0
        else -> 1_000.0
    }

    fun reachable(mode: TravelMode, requested: LatLon, routeEnd: LatLon) =
        gapMeters(requested, routeEnd) <= thresholdMeters(mode)
}

enum class CrossingKind { FERRY, BRIDGE, TUNNEL }

data class Crossing(val kind: CrossingKind, val name: String?, val lengthKm: Double)

/**
 * Ferries, long bridges and long tunnels on a route, from Valhalla trace_attributes edges.
 * Bridges/tunnels shorter than [minLengthKm] (overpasses, underpasses) are left out; ferries always count.
 * Valhalla doesn't say what a bridge crosses, so a "bridge" here may cross a valley or a railway, not water.
 */
object Crossings {
    private val json = Json { ignoreUnknownKeys = true }

    data class Edge(val names: List<String>, val lengthKm: Double, val bridge: Boolean, val tunnel: Boolean, val ferry: Boolean)

    fun parseTraceAttributes(text: String): List<Edge> = runCatching {
        val root = json.parseToJsonElement(text) as JsonObject
        (root["edges"] as JsonArray).mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            Edge(
                names = (o["names"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList(),
                lengthKm = o["length"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                bridge = o["bridge"]?.jsonPrimitive?.booleanOrNull ?: false,
                tunnel = o["tunnel"]?.jsonPrimitive?.booleanOrNull ?: false,
                ferry = (o["use"] as? JsonPrimitive)?.content == "ferry",
            )
        }
    }.getOrDefault(emptyList())

    fun summarize(edges: List<Edge>, minLengthKm: Double = 0.3): List<Crossing> {
        val out = mutableListOf<Crossing>()
        var kind: CrossingKind? = null
        var len = 0.0
        val names = mutableListOf<String>()
        fun flush() {
            val k = kind ?: return
            if (k == CrossingKind.FERRY || len >= minLengthKm) out += Crossing(k, pickName(k, names), len)
            kind = null; len = 0.0; names.clear()
        }
        for (e in edges) {
            val k = when { e.ferry -> CrossingKind.FERRY; e.bridge -> CrossingKind.BRIDGE; e.tunnel -> CrossingKind.TUNNEL; else -> null }
            if (k != kind) flush()
            if (k != null) { kind = k; len += e.lengthKm; names += e.names }
        }
        flush()
        return out
    }

    /** Prefer a name that says what it is ("... Bridge", "Podul ...", "Tunelul ..."), else the first non-ref name. */
    private fun pickName(kind: CrossingKind, names: List<String>): String? {
        val words = when (kind) {
            CrossingKind.BRIDGE -> listOf("bridge", "pod", "мост", "híd", "brücke", "most")
            CrossingKind.TUNNEL -> listOf("tunnel", "tunel", "тунел")
            CrossingKind.FERRY -> listOf("ferry", "bac", "feribot", "ферибот")
        }
        names.firstOrNull { n -> words.any { it in n.lowercase() } }?.let { return it }
        return names.firstOrNull { n -> !Regex("^[A-Z]{0,3}\\s?\\d+[A-Z]?$").matches(n) && !n.startsWith("E ") }
    }
}
