package com.zoomi.core.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Reference times for Bucharest (44.4268 N, 26.1025 E) are from api.sunrise-sunset.org,
 * fetched 2026-11-09. Civil twilight is defined as the sun centre at -6 degrees.
 */
class SolarCalculatorTest {
    private val lat = 44.4268
    private val lon = 26.1025

    private fun elev(iso: String) = SolarCalculator.elevationDegrees(Instant.parse(iso).toEpochMilli(), lat, lon)

    @Test fun summerCivilTwilightIsMinusSix() {
        assertEquals(-6.0, elev("2026-06-21T01:54:21Z"), 0.3)
        assertEquals(-6.0, elev("2026-06-21T18:40:27Z"), 0.3)
    }

    @Test fun winterCivilTwilightIsMinusSix() {
        assertEquals(-6.0, elev("2026-12-21T05:15:10Z"), 0.3)
        assertEquals(-6.0, elev("2026-12-21T15:12:04Z"), 0.3)
    }

    @Test fun sunriseAndSunsetAreAtTheHorizon() {
        // Apparent centre is roughly -0.27 deg when the upper limb touches the horizon.
        for (t in listOf("2026-06-21T02:31:05Z", "2026-06-21T18:03:42Z", "2026-12-21T05:48:27Z", "2026-12-21T14:38:46Z")) {
            assertEquals("at $t", -0.27, elev(t), 0.4)
        }
    }

    @Test fun solarNoonElevationMatchesGeometry() {
        // noon elevation = 90 - latitude + declination (+/-23.43 at the solstices)
        assertEquals(90 - lat + 23.43, elev("2026-06-21T10:17:24Z"), 0.3)
        assertEquals(90 - lat - 23.43, elev("2026-12-21T10:13:37Z"), 0.3)
    }

    @Test fun midnightIsDark() {
        assertTrue(elev("2026-06-21T21:30:00Z") < -10)
    }
}
