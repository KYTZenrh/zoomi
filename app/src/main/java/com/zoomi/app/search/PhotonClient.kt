package com.zoomi.app.search

import com.zoomi.app.net.ZoomiHttp
import com.zoomi.core.search.Place
import com.zoomi.core.search.PhotonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException
import java.util.Locale

/**
 * Search via a Photon server (default: komoot's public instance, fair use, 1 req/s enforced in [ZoomiHttp]).
 * Sends only the typed text and, for ranking, a rounded position (~1 km precision).
 */
class PhotonClient(private val baseUrl: () -> String) {

    suspend fun search(query: String, nearLat: Double?, nearLon: Double?): List<Place> {
        val url = baseUrl().trimEnd('/').plus("/api/").toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("limit", "10")
            .apply {
                if (nearLat != null && nearLon != null) {
                    addQueryParameter("lat", String.format(Locale.US, "%.2f", nearLat))
                    addQueryParameter("lon", String.format(Locale.US, "%.2f", nearLon))
                }
            }.build()
        return get(url.toString())
    }

    /**
     * Airports (OSM aeroway=aerodrome) within ~150 km of a point, nearest first. Information only:
     * Zoomi has no flight data. Photon needs a query word, so we ask for both the English and the
     * Romanian/French word inside a bounding box and merge (2 requests, rate-limited).
     */
    suspend fun airportsNear(lat: Double, lon: Double): List<Place> {
        val bbox = String.format(Locale.US, "%.3f,%.3f,%.3f,%.3f", lon - 1.9, lat - 1.35, lon + 1.9, lat + 1.35)
        val found = listOf("airport", "aeroport").flatMap { q ->
            val url = baseUrl().trimEnd('/').plus("/api/").toHttpUrl().newBuilder()
                .addQueryParameter("q", q)
                .addQueryParameter("osm_tag", "aeroway:aerodrome")
                .addQueryParameter("limit", "8")
                .addQueryParameter("bbox", bbox)
                .build()
            runCatching { get(url.toString()) }.getOrDefault(emptyList())
        }
        val here = com.zoomi.core.location.LatLon(lat, lon)
        return found.filter { it.category == "aeroway/aerodrome" }
            .distinctBy { Math.round(it.lat * 100) to Math.round(it.lon * 100) }
            .sortedBy { com.zoomi.core.geo.GeoMath.haversine(here, com.zoomi.core.location.LatLon(it.lat, it.lon)) }
            .take(3)
    }

    suspend fun reverse(lat: Double, lon: Double): Place? {
        val url = baseUrl().trimEnd('/').plus("/reverse").toHttpUrl().newBuilder()
            .addQueryParameter("lat", String.format(Locale.US, "%.6f", lat))
            .addQueryParameter("lon", String.format(Locale.US, "%.6f", lon))
            .build()
        return get(url.toString()).firstOrNull()
    }

    private suspend fun get(url: String): List<Place> = withContext(Dispatchers.IO) {
        ZoomiHttp.client.newCall(Request.Builder().url(url).get().build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("Search server answered HTTP ${res.code}")
            PhotonParser.parse(res.body.string())
        }
    }
}
