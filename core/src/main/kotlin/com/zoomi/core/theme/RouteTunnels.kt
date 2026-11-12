package com.zoomi.core.theme

import com.zoomi.core.location.LatLon
import com.zoomi.core.location.Polyline
import com.zoomi.core.nav.Crossings

/**
 * Where the active route runs through tunnels, from Valhalla trace_attributes edges.
 *
 * This is how big navigation apps switch to night colours in tunnels: the route itself says
 * "tunnel from metre A to metre B", so the switch happens at the portal, without waiting for
 * GPS loss or darkness.
 */
class RouteTunnels(points: List<LatLon>, edges: List<Crossings.Edge>, minTunnelM: Double = 80.0) {
    private val line = Polyline(points)
    /** Tunnel stretches as [start, end] metres along [line]. */
    val intervals: List<ClosedFloatingPointRange<Double>>

    init {
        // Edge lengths (km) and our polyline length differ slightly; scale edges onto the line.
        val totalM = edges.sumOf { it.lengthKm } * 1000.0
        val scale = if (totalM > 0) line.length / totalM else 1.0
        val out = mutableListOf<ClosedFloatingPointRange<Double>>()
        var s = 0.0
        var start: Double? = null
        for (e in edges) {
            val len = e.lengthKm * 1000.0 * scale
            if (e.tunnel && start == null) start = s
            if (!e.tunnel && start != null) { if (s - start >= minTunnelM) out += start..s; start = null }
            s += len
        }
        start?.let { if (s - it >= minTunnelM) out += it..s }
        intervals = out
    }

    /**
     * true when [p] is on the route inside a tunnel (with [leadM] metres of lead-in so the colours change
     * at the portal), false when on the route outside tunnels, null when [p] is too far from the route to tell.
     */
    fun at(p: LatLon, leadM: Double = 25.0, maxOffRouteM: Double = 50.0): Boolean? {
        if (line.length <= 0.0) return null
        val s = line.project(p)
        if (Polyline.dist(line.pointAt(s), p) > maxOffRouteM) return null
        return intervals.any { s >= it.start - leadM && s <= it.endInclusive }
    }
}
