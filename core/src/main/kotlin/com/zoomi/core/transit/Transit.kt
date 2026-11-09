package com.zoomi.core.transit

import com.zoomi.core.geo.EncodedPolyline
import com.zoomi.core.location.LatLon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

data class TransitLeg(
    val mode: String,               // WALK, BUS, TRAM, SUBWAY, RAIL, FERRY, ...
    val line: String?,              // "43", "IR 1662"
    val headsign: String?,
    val agency: String?,
    val from: String,
    val to: String,
    val start: Instant,
    val end: Instant,
    val realTime: Boolean,
    val geometry: List<LatLon>,
) {
    val isWalk get() = mode == "WALK" || mode == "BIKE" || mode == "CAR"
}

data class Itinerary(val start: Instant, val end: Instant, val transfers: Int, val legs: List<TransitLeg>) {
    val durationSec get() = end.epochSecond - start.epochSecond
    val geometry get() = legs.flatMap { it.geometry }
}

/** [direct] = MOTIS's non-transit alternatives (e.g. just walking), returned when they beat transit. */
data class TransitPlan(val itineraries: List<Itinerary>, val direct: List<Itinerary>)

/** Parses MOTIS 2 (Transitous) /api/v5/plan responses. */
object TransitParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parsePlan(text: String): List<Itinerary> = parseFull(text).itineraries

    fun parseFull(text: String): TransitPlan = runCatching {
        val root = json.parseToJsonElement(text) as JsonObject
        fun list(k: String) = (root[k] as? JsonArray)?.mapNotNull { parseItinerary(it as? JsonObject) } ?: emptyList()
        TransitPlan(list("itineraries"), list("direct"))
    }.getOrDefault(TransitPlan(emptyList(), emptyList()))

    private fun parseItinerary(o: JsonObject?): Itinerary? {
        o ?: return null
        val legs = (o["legs"] as? JsonArray)?.mapNotNull { parseLeg(it as? JsonObject) } ?: return null
        if (legs.isEmpty()) return null
        return Itinerary(
            start = Instant.parse(o.str("startTime") ?: return null),
            end = Instant.parse(o.str("endTime") ?: return null),
            transfers = o["transfers"]?.jsonPrimitive?.intOrNull ?: 0,
            legs = legs,
        )
    }

    private fun parseLeg(o: JsonObject?): TransitLeg? {
        o ?: return null
        val geom = (o["legGeometry"] as? JsonObject)?.let { g ->
            val pts = g.str("points") ?: return@let emptyList()
            EncodedPolyline.decode(pts, g["precision"]?.jsonPrimitive?.intOrNull ?: 6)
        } ?: emptyList()
        fun place(k: String) = (o[k] as? JsonObject)?.str("name")?.let { if (it == "START") "Start" else if (it == "END") "Destination" else it } ?: "?"
        return TransitLeg(
            mode = o.str("mode") ?: return null,
            line = o.str("routeShortName") ?: o.str("displayName"),
            headsign = o.str("headsign"),
            agency = o.str("agencyName"),
            from = place("from"),
            to = place("to"),
            start = Instant.parse(o.str("startTime") ?: return null),
            end = Instant.parse(o.str("endTime") ?: return null),
            realTime = o["realTime"]?.jsonPrimitive?.booleanOrNull ?: false,
            geometry = geom,
        )
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
