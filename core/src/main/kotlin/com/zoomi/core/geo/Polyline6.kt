package com.zoomi.core.geo

import com.zoomi.core.location.LatLon
import kotlin.math.roundToLong

/** Google encoded polyline with configurable precision (Valhalla and MOTIS use 6). */
object EncodedPolyline {
    fun encode(points: List<LatLon>, precision: Int = 6): String {
        val factor = Math.pow(10.0, precision.toDouble())
        val sb = StringBuilder()
        var lastLat = 0L
        var lastLon = 0L
        for (p in points) {
            val lat = (p.lat * factor).roundToLong()
            val lon = (p.lon * factor).roundToLong()
            write(lat - lastLat, sb); write(lon - lastLon, sb)
            lastLat = lat; lastLon = lon
        }
        return sb.toString()
    }

    fun decode(s: String, precision: Int = 6): List<LatLon> {
        val factor = Math.pow(10.0, precision.toDouble())
        val out = ArrayList<LatLon>()
        var i = 0
        var lat = 0L
        var lon = 0L
        while (i < s.length) {
            val (dLat, i2) = read(s, i); i = i2
            if (i >= s.length) break
            val (dLon, i3) = read(s, i); i = i3
            lat += dLat; lon += dLon
            out += LatLon(lat / factor, lon / factor)
        }
        return out
    }

    private fun write(v: Long, sb: StringBuilder) {
        var x = if (v < 0) (v shl 1).inv() else v shl 1
        while (x >= 0x20) { sb.append(((0x20 or (x and 0x1f).toInt()) + 63).toChar()); x = x shr 5 }
        sb.append((x + 63).toInt().toChar())
    }

    private fun read(s: String, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var i = start
        while (i < s.length) {
            val b = s[i++].code - 63
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
            if (b < 0x20) break
        }
        val v = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        return v to i
    }
}

object GeoMath {
    /** Great-circle distance in metres. */
    fun haversine(a: LatLon, b: LatLon): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLon / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(h))
    }
}
