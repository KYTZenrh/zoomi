package com.zoomi.core.nav

import com.zoomi.core.net.MinIntervalLimiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class FormattingTest {
    @Test fun distances() {
        assertEquals("0 m", Formatting.distance(3.0))
        assertEquals("120 m", Formatting.distance(118.0))
        assertEquals("450 m", Formatting.distance(437.0))
        assertEquals("1.0 km", Formatting.distance(960.0))
        assertEquals("5.3 km", Formatting.distance(5_340.0))
        assertEquals("12 km", Formatting.distance(12_400.0))
        assertEquals("–", Formatting.distance(-1.0))
    }

    @Test fun durations() {
        assertEquals("< 1 min", Formatting.duration(20.0))
        assertEquals("7 min", Formatting.duration(7 * 60.0 + 10))
        assertEquals("1 h 05 min", Formatting.duration(3_900.0))
    }

    @Test fun arrivalClockUsesZone() {
        val now = Instant.parse("2026-11-09T19:00:00Z").toEpochMilli()
        assertEquals("21:25", Formatting.arrivalClock(now, 25 * 60.0, ZoneId.of("Europe/Bucharest")))
    }
}

class ValhallaLocaleTest {
    @Test fun picks() {
        assertEquals("ro-RO", ValhallaLocale.pick("ro-RO"))
        assertEquals("ro-RO", ValhallaLocale.pick("ro"))
        assertEquals("ro-RO", ValhallaLocale.pick("ro_MD"))
        assertEquals("en-GB", ValhallaLocale.pick("en-GB"))
        assertEquals("en-US", ValhallaLocale.pick("en-IE"))
        assertEquals("de-DE", ValhallaLocale.pick("de-AT"))
        assertEquals("en-US", ValhallaLocale.pick("xx-YY"))
    }
}

class ManeuverShapesTest {
    @Test fun maneuvers() {
        assertEquals(ArrowShape.ARRIVE, ManeuverShapes.forManeuver("ARRIVE", null))
        assertEquals(ArrowShape.ROUNDABOUT, ManeuverShapes.forManeuver("EXIT_ROUNDABOUT", "RIGHT"))
        assertEquals(ArrowShape.SLIGHT_LEFT, ManeuverShapes.forManeuver("TURN", "SLIGHT_LEFT"))
        assertEquals(ArrowShape.UTURN, ManeuverShapes.forManeuver("CONTINUE", "U_TURN"))
        assertEquals(ArrowShape.STRAIGHT, ManeuverShapes.forManeuver("DEPART", null))
    }

    @Test fun lanes() {
        assertEquals(ArrowShape.SLIGHT_RIGHT, ManeuverShapes.forDirection("slight right"))
        assertEquals(ArrowShape.UTURN, ManeuverShapes.forDirection("uturn"))
        assertEquals(ArrowShape.STRAIGHT, ManeuverShapes.forDirection("straight"))
    }

    @Test fun routeDelta() {
        assertEquals("Fastest", RouteCompare.deltaLabel(600.0, 600.0))
        assertEquals("+4 min", RouteCompare.deltaLabel(840.0, 600.0))
    }
}

class EtaShareTest {
    @Test fun containsDestinationClockAndLinkButNoOrigin() {
        val now = Instant.parse("2026-11-09T19:00:00Z").toEpochMilli()
        val t = EtaShare.text("Palatul Culturii", 47.15731, 27.58626, now, 600.0, ZoneId.of("Europe/Bucharest"))
        assertTrue(t.contains("Palatul Culturii"))
        assertTrue(t.contains("21:10"))
        assertTrue(t.contains("mlat=47.15731&mlon=27.58626"))
    }
}

class MinIntervalLimiterTest {
    @Test fun spacesRequests() {
        val l = MinIntervalLimiter(1_000)
        assertEquals(0, l.reserve(10_000))
        assertEquals(1_000, l.reserve(10_000))      // second immediately after: wait 1 s
        assertEquals(1_500, l.reserve(10_500))      // third: slot at 12_000
        assertEquals(0, l.reserve(20_000))          // long idle: no wait
    }
}
