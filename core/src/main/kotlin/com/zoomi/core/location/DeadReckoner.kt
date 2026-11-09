package com.zoomi.core.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

data class ReckonedPosition(val lat: Double, val lon: Double, val bearingDeg: Float, val accuracyM: Float)

/**
 * Keeps the vehicle moving along the planned route when GNSS drops out (tunnels, underpasses,
 * urban canyons), so the map, the instructions and the tunnel theme keep working.
 *
 * Model: constant speed along the route polyline from the last good fix. Limits:
 * - Stops after [maxSeconds] or [maxDistanceM]. Beyond that we hold the last estimate rather than
 *   invent a position.
 * - Accuracy degrades linearly with time (reported to consumers, e.g. Ferrostar ignores
 *   low-accuracy positions for off-route decisions).
 * - Without a route there is nothing to follow, so no dead reckoning (the dot just stays put).
 */
class DeadReckoner(
    private val maxSeconds: Double = 120.0,
    private val maxDistanceM: Double = 4_000.0,
    private val minSpeedMps: Double = 1.0,
) {
    private var route: Polyline? = null
    private var anchorS = 0.0
    private var anchorMs = 0L
    private var anchorSpeed = 0.0
    private var anchorAccuracy = 10.0
    private var hasAnchor = false

    fun setRoute(points: List<LatLon>?) {
        route = points?.takeIf { it.size >= 2 }?.let { Polyline(it) }
        hasAnchor = false
    }

    /** Call with every good GNSS fix while navigating. */
    fun onFix(timeMs: Long, lat: Double, lon: Double, speedMps: Double?, accuracyM: Double?) {
        val r = route ?: return
        anchorS = r.project(LatLon(lat, lon))
        anchorMs = timeMs
        anchorSpeed = speedMps ?: 0.0
        anchorAccuracy = accuracyM ?: 10.0
        hasAnchor = true
    }

    /** Estimated position at [timeMs], or null if dead reckoning isn't possible. */
    fun estimate(timeMs: Long): ReckonedPosition? {
        val r = route ?: return null
        if (!hasAnchor || anchorSpeed < minSpeedMps) return null
        val dt = ((timeMs - anchorMs) / 1000.0).coerceIn(0.0, maxSeconds)
        val travelled = (anchorSpeed * dt).coerceAtMost(maxDistanceM)
        val s = (anchorS + travelled).coerceAtMost(r.length)
        val p = r.pointAt(s)
        val accuracy = anchorAccuracy + 0.15 * travelled // ~15% of distance travelled
        return ReckonedPosition(p.lat, p.lon, r.bearingAt(s), accuracy.toFloat())
    }
}

/** Polyline with cumulative distances (local equirectangular approximation, fine at route scale). */
class Polyline(private val pts: List<LatLon>) {
    private val cum = DoubleArray(pts.size)
    val length: Double

    init {
        for (i in 1 until pts.size) cum[i] = cum[i - 1] + dist(pts[i - 1], pts[i])
        length = cum.last()
    }

    /** Distance along the polyline of the closest point to [p]. */
    fun project(p: LatLon): Double {
        var best = Double.MAX_VALUE
        var bestS = 0.0
        for (i in 0 until pts.size - 1) {
            val a = pts[i]; val b = pts[i + 1]
            val (ax, ay) = xy(a, p); val (bx, by) = xy(b, p)
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else ((-ax) * dx + (-ay) * dy) / len2
            val tc = t.coerceIn(0.0, 1.0)
            val qx = ax + tc * dx; val qy = ay + tc * dy
            val d = qx * qx + qy * qy
            if (d < best) { best = d; bestS = cum[i] + tc * (cum[i + 1] - cum[i]) }
        }
        return bestS
    }

    fun pointAt(s: Double): LatLon {
        val i = segmentIndex(s)
        val segLen = cum[i + 1] - cum[i]
        val t = if (segLen == 0.0) 0.0 else (s - cum[i]) / segLen
        val a = pts[i]; val b = pts[i + 1]
        return LatLon(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon))
    }

    fun bearingAt(s: Double): Float {
        val i = segmentIndex(s)
        return bearing(pts[i], pts[i + 1])
    }

    private fun segmentIndex(s: Double): Int {
        val sc = s.coerceIn(0.0, length)
        var i = 0
        while (i < pts.size - 2 && cum[i + 1] < sc) i++
        return i
    }

    companion object {
        private const val R = 6_371_000.0

        /** Metres east/north of [origin]. */
        fun xy(p: LatLon, origin: LatLon): Pair<Double, Double> {
            val x = Math.toRadians(p.lon - origin.lon) * R * cos(Math.toRadians(origin.lat))
            val y = Math.toRadians(p.lat - origin.lat) * R
            return x to y
        }

        fun dist(a: LatLon, b: LatLon): Double {
            val (x, y) = xy(b, a)
            return sqrt(x * x + y * y)
        }

        fun bearing(a: LatLon, b: LatLon): Float {
            val (x, y) = xy(b, a)
            val deg = Math.toDegrees(atan2(x, y))
            return (((deg % 360) + 360) % 360).toFloat()
        }
    }
}
