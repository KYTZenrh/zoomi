package com.zoomi.app.map

import android.graphics.BitmapFactory
import com.zoomi.app.net.ZoomiHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Result of fetching one sample tile from an imagery source. */
sealed interface ProbeResult {
    data class Ok(val tileSize: Int) : ProbeResult
    data class Failed(val message: String) : ProbeResult
}

/**
 * Downloads one tile (zoom 2, Europe) to check a key works and to learn the real tile size,
 * instead of guessing 256 vs 512 (a wrong guess makes imagery blurry or tiny).
 */
object ImageryProbe {
    suspend fun probe(urlTemplate: String): ProbeResult = withContext(Dispatchers.IO) {
        val url = urlTemplate.replace("{z}", "2").replace("{x}", "2").replace("{y}", "1")
        runCatching {
            ZoomiHttp.client.newCall(Request.Builder().url(url).build()).execute().use { res ->
                when (res.code) {
                    200 -> {
                        val bytes = res.body.bytes()
                        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                        if (o.outWidth in listOf(256, 512)) ProbeResult.Ok(o.outWidth)
                        else ProbeResult.Failed("Unexpected tile (${o.outWidth}×${o.outHeight} px)")
                    }
                    401, 403 -> ProbeResult.Failed("The key was rejected (HTTP ${res.code}).")
                    429 -> ProbeResult.Failed("This key's monthly limit is used up (HTTP 429).")
                    else -> ProbeResult.Failed("Imagery server answered HTTP ${res.code}.")
                }
            }
        }.getOrElse { ProbeResult.Failed("Couldn't reach the imagery server: ${it.message}") }
    }
}
