package com.zoomi.core.theme

import com.zoomi.core.location.LatLon
import com.zoomi.core.nav.Crossings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteTunnelsTest {
    // ~1.1 km straight line east along the equator-ish (lat 45): 0.0 -> 0.0142 deg lon.
    private val pts = listOf(LatLon(45.0, 26.0), LatLon(45.0, 26.0142))
    private fun e(m: Double, tunnel: Boolean) = Crossings.Edge(emptyList(), m / 1000.0, false, tunnel, false)
    private val rt = RouteTunnels(pts, listOf(e(400.0, false), e(300.0, true), e(20.0, false), e(30.0, true), e(366.0, false)))

    private fun at(m: Double) = LatLon(45.0, 26.0 + 0.0142 * m / rt.lengthForTest())
    private fun RouteTunnels.lengthForTest() = 1116.0

    @Test fun keepsLongTunnelDropsShortUnderpass() {
        assertEquals(1, rt.intervals.size)
        assertEquals(400.0, rt.intervals[0].start, 15.0)
        assertEquals(700.0, rt.intervals[0].endInclusive, 15.0)
    }

    @Test fun positionAlongRoute() {
        assertEquals(false, rt.at(at(200.0)))
        assertEquals(true, rt.at(at(390.0))) // lead-in before the portal
        assertEquals(true, rt.at(at(550.0)))
        assertEquals(false, rt.at(at(800.0)))
        assertNull(rt.at(LatLon(45.01, 26.005))) // ~1 km off the route
    }
}
