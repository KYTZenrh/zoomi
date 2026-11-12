package com.zoomi.core.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelDetectorTest {
    private fun inp(now: Long, lux: Float?, fixAge: Long?, tunnel: Boolean?, speed: Float? = 15f, acc: Float? = 5f) =
        ThemeInputs(now, 44.0, 26.0, lux, fixAge, tunnel, false, speed, acc)

    /** Driving at 15 m/s with fresh GPS just before the tunnel. */
    private fun TunnelDetector.driveUpTo(now: Long) = update(inp(now, 5000f, 200, false))

    @Test fun entersOnTunnelRoadWhenGpsLost() {
        val d = TunnelDetector()
        d.driveUpTo(0)
        assertFalse(d.update(inp(1_000, 5000f, 200, true)))
        assertTrue(d.update(inp(4_000, 5000f, 3_100, true)))
    }

    @Test fun entersOnTunnelRoadWhenDarkForTwoSeconds() {
        val d = TunnelDetector()
        d.driveUpTo(0)
        assertFalse(d.update(inp(1_000, 10f, 200, true)))
        assertFalse(d.update(inp(2_000, 10f, 200, true)))
        assertTrue(d.update(inp(3_000, 10f, 200, true)))
    }

    /** Darkness alone (sun visor, phone in a holder) never switches while GPS still works. */
    @Test fun neverEntersOnDarknessWithWorkingGps() {
        val d = TunnelDetector()
        d.driveUpTo(0)
        for (t in 1_000L..60_000L step 1_000) {
            assertFalse(d.update(inp(t, 1f, 300, null)))
            assertFalse(d.update(inp(t, 1f, 300, false)))
        }
    }

    /** Losing GPS in daylight (urban canyon) is not "underground". */
    @Test fun gpsLossInDaylightIsNotUnderground() {
        val d = TunnelDetector()
        d.driveUpTo(0)
        for (t in 1_000L..30_000L step 1_000) assertFalse(d.update(inp(t, 3000f, 10_000, null)))
    }

    /** Driving into an underground car park: no tunnel in the map, but GPS gone and very dark. */
    @Test fun entersUndergroundWhenGpsLostAndVeryDark() {
        val d = TunnelDetector()
        d.driveUpTo(0)
        assertFalse(d.update(inp(2_000, 5f, 2_000, null)))
        assertFalse(d.update(inp(4_000, 5f, 4_000, null)))
        assertTrue(d.update(inp(6_000, 5f, 6_000, null)))
        // Back outside in daylight: bright light with fresh GPS ends it after 5 s.
        for (t in 20_000L until 25_000L step 1_000) assertTrue(d.update(inp(t, 5000f, 300, null, acc = 8f)))
        assertFalse(d.update(inp(25_000, 5000f, 300, null, acc = 8f)))
    }

    @Test fun undergroundNeedsRecentDriving() {
        val d = TunnelDetector()
        for (t in 0L..60_000L step 1_000) assertFalse(d.update(inp(t, 2f, 20_000, null, speed = 0f)))
    }

    /** Navigation route says "tunnel here": switch at the portal even though GPS still works. */
    @Test fun entersImmediatelyWhenRouteSaysTunnel() {
        val d = TunnelDetector()
        d.driveUpTo(0)
        assertTrue(d.update(inp(1_000, 5000f, 200, null).copy(routeTunnel = true)))
        // Bright light and good GPS inside the tunnel (lit tunnel, GPS repeaters) don't end it.
        for (t in 2_000L..30_000L step 1_000) assertTrue(d.update(inp(t, 5000f, 200, null, acc = 5f).copy(routeTunnel = true)))
        // Route says out, GPS fresh and daylight: back to day within seconds.
        d.update(inp(31_000, 5000f, 200, null).copy(routeTunnel = false))
        assertFalse(d.update(inp(34_000, 5000f, 200, null).copy(routeTunnel = false)))
    }

    @Test fun routeTunnelNeedsMotion() {
        val d = TunnelDetector()
        for (t in 0L..20_000L step 1_000) assertFalse(d.update(inp(t, 5000f, 200, null, speed = 0f).copy(routeTunnel = true)))
    }

    /** Regression: phone indoors (29 lux), stationary, next to a mapped underpass. Seen on device 2026-11-09. */
    @Test fun stationaryIndoorsNearTunnelRoadNeverEnters() {
        val d = TunnelDetector()
        for (t in 0L..120_000L step 1_000) {
            assertFalse(d.update(inp(t, 29f, 300, true, speed = 0f, acc = 12f)))
        }
    }

    @Test fun motionMustBeRecent() {
        val d = TunnelDetector(recentMotionMs = 15_000)
        d.driveUpTo(0)
        // Parked for 20 s next to the tunnel, dark: too long since moving.
        assertFalse(d.update(inp(20_000, 10f, 200, true, speed = 0f)))
        assertFalse(d.update(inp(23_000, 10f, 200, true, speed = 0f)))
    }

    @Test fun exitsWhenOutOfTunnelWithFreshGps() {
        val d = TunnelDetector()
        d.driveUpTo(0)
        d.update(inp(1_000, 10f, 5_000, true))
        assertTrue(d.inTunnel)
        assertTrue(d.update(inp(10_000, 10f, 500, false)))
        assertTrue(d.update(inp(12_000, 10f, 500, false)))
        assertFalse(d.update(inp(13_000, 10f, 500, false)))
    }

    /** Regression: map stopped following (unknown), dim light; good GPS must still end tunnel mode. */
    @Test fun exitsOnGoodGpsWhenMapUnknown() {
        val d = TunnelDetector()
        d.driveUpTo(0)
        d.update(inp(1_000, 10f, 5_000, true))
        assertTrue(d.inTunnel)
        for (t in 10_000L until 18_000L step 1_000) assertTrue(d.update(inp(t, 29f, 300, null, speed = 0f, acc = 10f)))
        assertFalse(d.update(inp(18_000, 29f, 300, null, speed = 0f, acc = 10f)))
    }

    @Test fun inaccurateGpsDoesNotEndTunnelWhileMapUnknown() {
        val d = TunnelDetector()
        d.driveUpTo(0)
        d.update(inp(1_000, 10f, 5_000, true))
        for (t in 10_000L..40_000L step 1_000) assertTrue(d.update(inp(t, 10f, 300, null, acc = 80f)))
    }

    @Test fun lightFlickerInsideTunnelDoesNotExit() {
        val d = TunnelDetector()
        d.driveUpTo(0)
        d.update(inp(500, 10f, 5_000, true))
        var exits = 0
        for (t in 1_000L..30_000L step 500) {
            val lux = if ((t / 500) % 2 == 0L) 400f else 15f
            if (!d.update(inp(t, lux, 5_000 + t, true))) exits++
        }
        assertEquals(0, exits)
    }

    @Test fun respectsMinimumTunnelTime() {
        val d = TunnelDetector(minTunnelMs = 4_000)
        d.driveUpTo(0)
        d.update(inp(500, 10f, 5_000, true))
        for (t in 1_000L..4_000L step 500) assertTrue(d.update(inp(t, 9000f, 100, false)))
    }
}
