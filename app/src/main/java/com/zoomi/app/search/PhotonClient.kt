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
