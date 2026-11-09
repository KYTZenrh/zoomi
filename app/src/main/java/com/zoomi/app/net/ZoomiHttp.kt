package com.zoomi.app.net

import com.zoomi.app.BuildConfig
import com.zoomi.core.net.MinIntervalLimiter
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * One HTTP client for all of Zoomi's own requests (search, routing).
 * - Identifies the app with a real User-Agent, as FOSSGIS and komoot require.
 * - Rate-limits public community servers to 1 request/second per host.
 * - No cookies, no tracking headers, no analytics.
 */
object ZoomiHttp {
    val USER_AGENT = "Zoomi-Android/${BuildConfig.VERSION_NAME} (open-source navigation app" +
        (if (BuildConfig.ZOOMI_CONTACT.isNotBlank()) "; ${BuildConfig.ZOOMI_CONTACT})" else ")")

    /**
     * Community servers we space out to at most 1 request/second (FOSSGIS and komoot publish this
     * limit; Transitous asks to keep usage light, so we apply the same rule). Checked 2026-11-09.
     */
    private val ONE_PER_SECOND_HOSTS = setOf("valhalla1.openstreetmap.de", "photon.komoot.io", "api.transitous.org")
    private val limiters = ConcurrentHashMap<String, MinIntervalLimiter>()

    private val politeness = Interceptor { chain ->
        val req = chain.request()
        val host = req.url.host
        if (host in ONE_PER_SECOND_HOSTS) {
            val wait = limiters.getOrPut(host) { MinIntervalLimiter(1_000) }.reserve(System.currentTimeMillis())
            if (wait > 0) Thread.sleep(wait)
        }
        chain.proceed(req.newBuilder().header("User-Agent", USER_AGENT).build())
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(politeness)
            .callTimeout(Duration.ofSeconds(20))
            .build()
    }
}
