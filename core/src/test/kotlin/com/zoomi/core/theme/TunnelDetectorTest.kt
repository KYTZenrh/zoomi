package com.zoomi.core.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelDetectorTest {
    private fun inp(now: Long, lux: Float?, fixAge: Long?, tunnel: Boolean?) =
        ThemeInputs(now, 44.0, 26.0, lux, fixAge, tunnel, false)

    @Test fun entersOnTunnelRoadWhenGpsLost() {
        val d = TunnelDetector()
        // Approaching: on tunnel road, GPS still fine, bright -> not yet.
        assertFalse(d.update(inp(0, 5000f, 200, true)))
        // GPS lost for 3 s -> enter.
        assertTrue(d.update(inp(4_000, 5000f, 3_100, true)))
    }

    @Test fun entersOnTunnelRoadWhenDarkForTwoSeconds() {
        val d = TunnelDetector()
        assertFalse(d.update(inp(0, 10f, 200, true)))
        assertFalse(d.update(inp(1_000, 10f, 200, true)))
        assertTrue(d.update(inp(2_000, 10f, 200, true)))
    }

    @Test fun neverEntersWithoutMapEvidence() {
        val d = TunnelDetector()
        for (t in 0L..60_000L step 1_000) {
            assertFalse(d.update(inp(t, 1f, 10_000, null)))
            assertFalse(d.update(inp(t, 1f, 10_000, false)))
        }
    }

    @Test fun exitsWhenOutOfTunnelWithFreshGps() {
        val d = TunnelDetector()
        d.update(inp(0, 10f, 5_000, true))
        assertTrue(d.inTunnel)
        // Leaves tunnel at t=10s: fresh fix, map says not tunnel; needs 3 s hold.
        assertTrue(d.update(inp(10_000, 10f, 500, false)))
        assertTrue(d.update(inp(12_000, 10f, 500, false)))
        assertFalse(d.update(inp(13_000, 10f, 500, false)))
    }

    @Test fun lightFlickerInsideTunnelDoesNotExit() {
        val d = TunnelDetector()
        d.update(inp(0, 10f, 5_000, true))
        var exits = 0
        for (t in 1_000L..30_000L step 500) {
            // Tunnel lights flashing past: lux alternates, GPS still lost.
            val lux = if ((t / 500) % 2 == 0L) 400f else 15f
            if (!d.update(inp(t, lux, 5_000 + t, true))) exits++
        }
        assertEquals(0, exits)
    }

    @Test fun respectsMinimumTunnelTime() {
        val d = TunnelDetector(minTunnelMs = 4_000)
        d.update(inp(0, 10f, 5_000, true))
        // Immediately everything says "outside", but must stay for min time.
        for (t in 500L..3_500L step 500) assertTrue(d.update(inp(t, 9000f, 100, false)))
    }
}
