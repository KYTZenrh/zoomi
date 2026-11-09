package com.zoomi.core.nav

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Driver-friendly formatting. Numbers are rounded so they don't jitter while driving. */
object Formatting {

    /** 0–999 m in steps of 10 (50 under 1 km if > 300 m), then km with one decimal under 10 km. */
    fun distance(meters: Double): String {
        if (meters.isNaN() || meters < 0) return "–"
        return when {
            meters < 300 -> "${(meters / 10).roundToInt() * 10} m"
            meters < 950 -> "${(meters / 50).roundToInt() * 50} m"
            meters < 9_950 -> String.format(Locale.US, "%.1f km", meters / 1000)
            else -> "${(meters / 1000).roundToInt()} km"
        }
    }

    /** "< 1 min", "7 min", "1 h 05 min". */
    fun duration(seconds: Double): String {
        if (seconds.isNaN() || seconds < 0) return "–"
        val totalMin = (seconds / 60).roundToLong()
        if (totalMin < 1) return "< 1 min"
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h == 0L) "$m min" else String.format(Locale.US, "%d h %02d min", h, m)
    }

    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    /** Arrival time as local wall-clock "HH:mm". */
    fun arrivalClock(nowMillis: Long, remainingSeconds: Double, zone: ZoneId = ZoneId.systemDefault()): String =
        clock.format(Instant.ofEpochMilli(nowMillis + (remainingSeconds * 1000).roundToLong()).atZone(zone))
}
