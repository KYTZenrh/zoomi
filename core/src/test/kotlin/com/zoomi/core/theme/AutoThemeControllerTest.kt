package com.zoomi.core.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AutoThemeControllerTest {
    private val lat = 44.4268
    private val lon = 26.1025

    private fun input(nowMs: Long, lux: Float? = 1000f, fixAge: Long? = 500, tunnel: Boolean? = false, systemDark: Boolean = false) =
        ThemeInputs(nowMs, lat, lon, lux, fixAge, tunnel, systemDark)

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test fun userModesIgnoreSun() {
        val c = AutoThemeController()
        val midnight = ms("2026-06-21T22:00:00Z")
        assertFalse(c.decide(ThemeMode.LIGHT, input(midnight)).dark)
        assertTrue(c.decide(ThemeMode.DARK, input(ms("2026-06-21T10:00:00Z"))).dark)
    }

    @Test fun autoFollowsDayAndNight() {
        val c = AutoThemeController()
        assertEquals(ThemeReason.DAY, c.decide(ThemeMode.AUTO, input(ms("2026-06-21T10:00:00Z"))).reason)
        val c2 = AutoThemeController()
        assertEquals(ThemeReason.NIGHT, c2.decide(ThemeMode.AUTO, input(ms("2026-06-21T22:00:00Z"))).reason)
    }

    @Test fun switchesToDarkAfterSunsetPlusHysteresisOnly() {
        val c = AutoThemeController(minSunDwellMs = 0)
        // Step through the sunset (18:03:42Z) minute by minute and record when it turns dark.
        var t = ms("2026-06-21T17:30:00Z")
        val end = ms("2026-06-21T19:00:00Z")
        var firstDark: Long? = null
        while (t <= end) {
            val d = c.decide(ThemeMode.AUTO, input(t)).dark
            if (d && firstDark == null) firstDark = t
            if (firstDark != null) assertTrue("must stay dark after switching", d)
            t += 60_000
        }
        val sunset = ms("2026-06-21T18:03:42Z")
        // -2 deg is reached some minutes after sunset; must not switch before sunset.
        assertTrue(firstDark!! > sunset)
        assertTrue(firstDark < sunset + 20 * 60_000)
    }

    @Test fun noFlickerAroundThresholdWithDwell() {
        val c = AutoThemeController(minSunDwellMs = 60_000)
        var t = ms("2026-06-21T18:00:00Z")
        var switches = 0
        var last: Boolean? = null
        repeat(60 * 60) { // one hour, every second
            val d = c.decide(ThemeMode.AUTO, input(t, lux = if (it % 2 == 0) 5f else 5000f)).dark
            if (last != null && d != last) switches++
            last = d
            t += 1_000
        }
        assertEquals("exactly one day->night switch, light-sensor noise ignored", 1, switches)
    }

    @Test fun lightSensorAloneNeverDarkensDuringDay() {
        val c = AutoThemeController()
        var t = ms("2026-06-21T10:00:00Z")
        repeat(120) {
            assertFalse(c.decide(ThemeMode.AUTO, input(t, lux = 1f, fixAge = 500, tunnel = null)).dark)
            t += 1_000
        }
    }

    @Test fun fallsBackToSystemWithoutPosition() {
        val c = AutoThemeController()
        val i = ThemeInputs(ms("2026-06-21T10:00:00Z"), null, null, 500f, null, null, systemDark = true)
        val d = c.decide(ThemeMode.AUTO, i)
        assertTrue(d.dark)
        assertEquals(ThemeReason.SYSTEM_FALLBACK, d.reason)
    }
}
