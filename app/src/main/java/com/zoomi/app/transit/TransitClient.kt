package com.zoomi.app.transit

import com.zoomi.app.net.ZoomiHttp
import com.zoomi.core.transit.TransitPlan
import com.zoomi.core.transit.TransitParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException
import java.util.Locale

/**
 * Public transport journeys from Transitous (MOTIS 2), a volunteer-run, FOSS-only service.
 * Online only. Coverage depends on which agencies publish GTFS feeds (see transitous.org/sources).
 * Sends start and destination coordinates; one request per search (Transitous asks to keep usage light).
 */
class TransitClient(private val baseUrl: String = "https://api.transitous.org") {
    suspend fun plan(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): TransitPlan =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/api/v5/plan".toHttpUrl().newBuilder()
                .addQueryParameter("fromPlace", String.format(Locale.US, "%.6f,%.6f", fromLat, fromLon))
                .addQueryParameter("toPlace", String.format(Locale.US, "%.6f,%.6f", toLat, toLon))
                .addQueryParameter("numItineraries", "5")
                .build()
            ZoomiHttp.client.newCall(Request.Builder().url(url).get().build()).execute().use { res ->
                if (!res.isSuccessful) throw IOException("Transitous HTTP ${res.code}")
                TransitParser.parseFull(res.body.string())
            }
        }
}
