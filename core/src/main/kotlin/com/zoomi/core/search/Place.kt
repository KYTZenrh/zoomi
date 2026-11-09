package com.zoomi.core.search

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A geocoded place: a search result or a picked map point. */
data class Place(
    val title: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double,
    /** e.g. "aeroway/aerodrome", useful later for airport suggestions. */
    val category: String? = null,
)

/** Search input rules: avoid sending pointless requests to a shared public server. */
object SearchQueryPolicy {
    const val MIN_CHARS = 3
    const val DEBOUNCE_MS = 400L

    /** Returns the query to send, or null if it should not be sent. */
    fun normalize(raw: String): String? {
        val q = raw.trim().replace(Regex("\\s+"), " ")
        return if (q.length < MIN_CHARS) null else q.take(200)
    }
}

/** Parses Photon's GeoJSON response (https://github.com/komoot/photon). */
object PhotonParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): List<Place> {
        val root = json.parseToJsonElement(body).jsonObject
        val features = root["features"] as? JsonArray ?: return emptyList()
        return features.mapNotNull { f ->
            val obj = f as? JsonObject ?: return@mapNotNull null
            val coords = obj["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: return@mapNotNull null
            val lon = coords.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lat = coords.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val p = obj["properties"] as? JsonObject ?: JsonObject(emptyMap())
            fun s(key: String) = (p[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

            val street = listOfNotNull(s("street"), s("housenumber")).joinToString(" ").ifBlank { null }
            val title = s("name") ?: street ?: s("city") ?: return@mapNotNull null
            val kind = kindLabel(s("osm_key"), s("osm_value"))
            val subtitle = listOfNotNull(
                kind,
                street.takeIf { it != title },
                s("district").takeIf { it != title },
                s("city").takeIf { it != title },
                s("country"),
            ).distinct().joinToString(", ")
            val category = s("osm_key")?.let { k -> s("osm_value")?.let { v -> "$k/$v" } }
            Place(title, subtitle, lat, lon, category)
        }.distinctBy { Triple(it.title, Math.round(it.lat * 1e4), Math.round(it.lon * 1e4)) }
    }

    /**
     * Short human label for the OSM tag, so same-named results (a museum and its bus stop)
     * can be told apart. Addresses and generic places get no label.
     */
    fun kindLabel(key: String?, value: String?): String? {
        if (key == null || value == null) return null
        KNOWN["$key/$value"]?.let { return it }
        if (key in setOf("place", "building", "highway", "boundary") || value == "yes") return null
        return value.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    private val KNOWN = mapOf(
        "highway/bus_stop" to "Bus stop",
        "public_transport/platform" to "Public transport stop",
        "public_transport/stop_position" to "Public transport stop",
        "railway/tram_stop" to "Tram stop",
        "railway/station" to "Railway station",
        "railway/halt" to "Railway halt",
        "aeroway/aerodrome" to "Airport",
        "amenity/fuel" to "Fuel station",
        "amenity/parking" to "Parking",
        "amenity/hospital" to "Hospital",
        "shop/supermarket" to "Supermarket",
        "historic/castle" to "Historic building",
    )
}
