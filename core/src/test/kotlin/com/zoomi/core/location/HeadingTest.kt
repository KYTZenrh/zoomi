package com.zoomi.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingFusionTest {
    @Test fun noHeadingUntilGnssCourse() {
        val f = HeadingFusion()
        assertNull(f.onGyro(0, 30f).degrees)
        assertNull(f.onGnss(1f, 90f, 5f).degrees)
        assertEquals(90f, f.onGnss(10f, 90f, 5f).degrees!!, 0.01f)
    }

    @Test fun stoppedCarDoesNotDrift() {
        val f = HeadingFusion()
        f.onGnss(10f, 45f, 3f)
        var t = 0L
        repeat(600) { // 60 s at 10 Hz of small gyro noise
            f.onGyro(t, if (it % 2 == 0) 0.3f else -0.4f)
            t += 100
        }
        assertEquals(45f, f.estimate.degrees!!, 0.01f)
    }

    @Test fun slowTurnTrackedByGyro() {
        val f = HeadingFusion()
        f.onGnss(10f, 0f, 3f)
        var t = 0L
        f.onGyro(t, 0f)
        // Turning right at 15 deg/s for 6 s at walking pace (GNSS course unusable) -> ~90.
        repeat(60) { t += 100; f.onGyro(t, 15f) }
        assertEquals(90f, f.estimate.degrees!!, 1f)
        assertEquals(HeadingSource.GYRO, f.estimate.source)
    }

    @Test fun gnssCorrectsGyro() {
        val f = HeadingFusion()
        f.onGnss(10f, 0f, 3f)
        f.onGyro(0, 0f); f.onGyro(100, 50f)
        assertEquals(181f, f.onGnss(12f, 181f, 4f).degrees!!, 0.01f)
    }

    @Test fun wrapsAround() {
        val f = HeadingFusion()
        f.onGnss(10f, 350f, 3f)
        f.onGyro(0, 0f)
        repeat(10) { f.onGyro((it + 1) * 100L, 20f) } // +20 deg
        assertEquals(10f, f.estimate.degrees!!, 0.5f)
    }

    @Test fun withdrawsWhenTooUncertain() {
        val f = HeadingFusion(driftDegPerSec = 1f, maxUncertaintyDeg = 30f)
        f.onGnss(10f, 0f, 5f)
        var t = 0L
        f.onGyro(t, 0f)
        repeat(300) { t += 100; f.onGyro(t, 0f) } // 30 s of drift budget
        assertNull(f.estimate.degrees)
    }

    @Test fun sensorGapNotIntegrated() {
        val f = HeadingFusion()
        f.onGnss(10f, 0f, 3f)
        f.onGyro(0, 0f)
        f.onGyro(5_000, 90f) // 5 s gap: must not add 450 deg
        assertEquals(0f, f.estimate.degrees!!, 0.01f)
    }

    @Test fun yawRateIndependentOfMount() {
        // Phone flat (screen up): up = +Z. CCW rotation of 0.1 rad/s around Z = turning left.
        val flat = HeadingFusion.yawRateDegPerSec(0f, 0f, 0.1f, 0f, 0f, 9.81f)
        assertEquals(-5.73f, flat, 0.01f)
        // Phone upright in a portrait mount: up = +Y. Same car turn = CCW around Y.
        val upright = HeadingFusion.yawRateDegPerSec(0f, 0.1f, 0f, 0f, 9.81f, 0f)
        assertEquals(-5.73f, upright, 0.01f)
        // Tilted 45 deg: half the rotation shows on each axis.
        val k = 0.0707107f
        val tilted = HeadingFusion.yawRateDegPerSec(0f, k, k, 0f, 6.9367f, 6.9367f)
        assertEquals(-5.73f, tilted, 0.02f)
    }
}

class DeadReckonerTest {
    // A straight road going east along latitude 45, ~3.9 km long, plus a turn north.
    private val route = listOf(LatLon(45.0, 26.0), LatLon(45.0, 26.05), LatLon(45.03, 26.05))

    @Test fun advancesAlongRouteAtLastSpeed() {
        val d = DeadReckoner()
        d.setRoute(route)
        d.onFix(0, 45.0, 26.0, 20.0, 5.0)
        val p = d.estimate(30_000)!! // 30 s * 20 m/s = 600 m east
        val expectedLon = 26.0 + 600.0 / (111_195.0 * Math.cos(Math.toRadians(45.0)))
        assertEquals(45.0, p.lat, 1e-4)
        assertEquals(expectedLon, p.lon, 2e-4)
        assertEquals(90f, p.bearingDeg, 0.5f)
        assertTrue(p.accuracyM > 90f)
    }

    @Test fun followsTheTurn() {
        // Caps raised: the turn is ~3.9 km away, beyond the default 4 km safety cap.
        val d = DeadReckoner(maxSeconds = 600.0, maxDistanceM = 10_000.0)
        d.setRoute(route)
        val firstLeg = Polyline(route).project(LatLon(45.0, 26.05))
        d.onFix(0, 45.0, 26.0, 25.0, 5.0)
        val p = d.estimate(((firstLeg + 500) / 25.0 * 1000).toLong())!!
        assertEquals(0f, p.bearingDeg, 0.5f) // heading north on the second leg
        assertTrue(p.lat > 45.0)
    }

    @Test fun capsTimeAndDistance() {
        val d = DeadReckoner(maxSeconds = 60.0)
        d.setRoute(route)
        d.onFix(0, 45.0, 26.0, 10.0, 5.0)
        val p60 = d.estimate(60_000)!!
        val p600 = d.estimate(600_000)!!
        assertEquals(p60.lon, p600.lon, 1e-9)
    }

    @Test fun nothingWithoutRouteOrWhenStopped() {
        val d = DeadReckoner()
        d.onFix(0, 45.0, 26.0, 20.0, 5.0)
        assertNull(d.estimate(10_000))
        d.setRoute(route)
        d.onFix(0, 45.0, 26.0, 0.2, 5.0)
        assertNull(d.estimate(10_000))
        d.onFix(0, 45.0, 26.0, 15.0, 5.0)
        assertNotNull(d.estimate(10_000))
    }

    @Test fun projectsOffRouteFixOntoRoute() {
        val pl = Polyline(route)
        val s = pl.project(LatLon(45.0005, 26.025)) // ~55 m north of the middle of leg 1
        assertEquals(pl.project(LatLon(45.0, 26.025)), s, 1.0)
    }
}
