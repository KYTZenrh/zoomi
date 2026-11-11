package com.zoomi.core.nav

import com.zoomi.core.geo.GeoMath
import com.zoomi.core.location.LatLon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Adjusts a Valhalla route request before it's sent, so start/end snapping behaves like users expect.
 *
 * By default Valhalla attaches each location to the single nearest edge. With GPS errors of 10–30 m
 * near buildings that is often the wrong path (a footway past the stairs, the path behind the block),
 * and the route then goes there and back. Giving a `radius` makes every edge within it a candidate and
 * lets the path search pick the cheapest one.
 *
 * Car start keeps a small radius: candidate roads within a large radius may not be the one the car is on.
 */
object RouteTuning {
    private val json = Json { ignoreUnknownKeys = true }

    fun startRadius(mode: TravelMode, accuracyM: Double?): Int {
        val acc = (accuracyM ?: 15.0).coerceIn(5.0, 40.0)
        return when (mode) {
            TravelMode.CAR -> minOf(acc, 15.0).toInt()
            else -> acc.toInt()
        }
    }

    fun endRadius(mode: TravelMode): Int = when (mode) {
        TravelMode.CAR -> 10
        else -> 25
    }

    /** Returns the request with `radius` set on the first and last location. Unparseable input is returned unchanged. */
    fun tune(requestJson: String, mode: TravelMode, startAccuracyM: Double?): String = runCatching {
        val root = json.parseToJsonElement(requestJson) as JsonObject
        val locs = root["locations"] as JsonArray
        if (locs.size < 2) return requestJson
        val tuned = locs.mapIndexed { i, l ->
            val o = l as JsonObject
            when (i) {
                0 -> JsonObject(o + ("radius" to JsonPrimitive(startRadius(mode, startAccuracyM))))
                locs.lastIndex -> JsonObject(o + ("radius" to JsonPrimitive(endRadius(mode))))
                else -> o
            }
        }
        JsonObject(root + ("locations" to JsonArray(tuned))).toString()
    }.getOrDefault(requestJson)

    /** Reads the start location's accuracy hint that Ferrostar puts in the request (street_side_tolerance). */
    fun startTolerance(requestJson: String): Double? = runCatching {
        val root = json.parseToJsonElement(requestJson) as JsonObject
        ((root["locations"] as JsonArray)[0] as JsonObject)["street_side_tolerance"].let { (it as JsonPrimitive).doubleOrNull }
    }.getOrNull()
}

/**
 * "You have arrived" as soon as you're close to the destination, like Google/Waze, instead of only
 * when the route line's end is reached (that end may be snapped to a path behind the building).
 */
object Arrival {
    fun thresholdMeters(mode: TravelMode) = when (mode) {
        TravelMode.CAR -> 40.0
        TravelMode.BIKE -> 30.0
        else -> 25.0
    }

    /**
     * @param position real (unsnapped) position
     * @param accuracyM GPS accuracy; very poor fixes don't trigger arrival on their own
     */
    fun arrived(mode: TravelMode, position: LatLon, destination: LatLon, accuracyM: Double?): Boolean {
        if (accuracyM != null && accuracyM > 50) return false
        return GeoMath.haversine(position, destination) <= thresholdMeters(mode)
    }
}
