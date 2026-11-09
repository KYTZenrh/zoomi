package com.zoomi.core.nav

import java.time.ZoneId
import java.util.Locale

/**
 * Text for the optional "share ETA" action. Shares only the destination and arrival time,
 * never the sender's current position. The user picks the recipient in Android's share sheet.
 */
object EtaShare {
    fun text(
        destinationName: String,
        destLat: Double,
        destLon: Double,
        nowMillis: Long,
        remainingSeconds: Double,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val clock = Formatting.arrivalClock(nowMillis, remainingSeconds, zone)
        val link = String.format(
            Locale.US,
            "https://www.openstreetmap.org/?mlat=%.5f&mlon=%.5f#map=16/%.5f/%.5f",
            destLat, destLon, destLat, destLon,
        )
        return "On my way to $destinationName. Arriving around $clock " +
            "(${Formatting.duration(remainingSeconds)}).\n$link"
    }
}
