package com.zoomi.core.nav

import com.zoomi.core.geo.EncodedPolyline
import com.zoomi.core.geo.GeoMath
import com.zoomi.core.location.LatLon
import com.zoomi.core.transit.TransitParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolylineTest {
    @Test fun roundTripPrecision6() {
        val pts = listOf(LatLon(47.158512, 27.592501), LatLon(47.173001, 27.546998), LatLon(-33.9, 151.2))
        val back = EncodedPolyline.decode(EncodedPolyline.encode(pts))
        pts.zip(back).forEach { (a, b) -> assertEquals(a.lat, b.lat, 1e-6); assertEquals(a.lon, b.lon, 1e-6) }
    }

    @Test fun knownGoogleExamplePrecision5() {
        // From Google's polyline algorithm documentation.
        val pts = EncodedPolyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5)
        assertEquals(3, pts.size)
        assertEquals(38.5, pts[0].lat, 1e-5); assertEquals(-120.2, pts[0].lon, 1e-5)
        assertEquals(43.252, pts[2].lat, 1e-5); assertEquals(-126.453, pts[2].lon, 1e-5)
    }

    @Test fun haversine() {
        // Iasi Palatul Culturii -> Bucharest Piata Unirii ~ 330 km straight line.
        val d = GeoMath.haversine(LatLon(47.1573, 27.5863), LatLon(44.4268, 26.1025))
        assertEquals(325_000.0, d, 10_000.0)
    }
}

class ReachabilityTest {
    @Test fun classifiesErrors() {
        assertEquals(RouteFailure.TOO_FAR_FOR_SERVER, RouteErrors.classify("DistanceExceeded", "Path distance exceeds the max distance limit."))
        assertEquals(RouteFailure.NO_ROUTE, RouteErrors.classify("442", "No path could be found for input"))
        assertEquals(RouteFailure.NO_ROUTE, RouteErrors.classify("NoRoute", null))
        // Seen on device 2026-11-09 for Insula Sacalin (Danube delta island, no road or ferry in OSM).
        assertEquals(RouteFailure.NO_ROUTE, RouteErrors.classify(null, "Impossible route between points"))
        assertEquals(RouteFailure.NO_ROAD_NEAR_DESTINATION, RouteErrors.classify(null, "No suitable edges near location"))
        assertEquals(RouteFailure.OTHER, RouteErrors.classify("500", "boom"))
    }

    @Test fun snappedFarAwayIsUnreachable() {
        val snakeIsland = LatLon(45.2555, 30.2040)
        val mainlandSnap = LatLon(45.40, 29.75) // ~38 km away
        assertFalse(Reachability.reachable(TravelMode.CAR, snakeIsland, mainlandSnap))
        assertTrue(Reachability.reachable(TravelMode.CAR, snakeIsland, LatLon(45.2600, 30.2040)))
        assertFalse(Reachability.reachable(TravelMode.WALK, LatLon(45.0, 26.0), LatLon(45.012, 26.0))) // 1.3 km
    }
}

class CrossingsTest {
    // Edges as returned by valhalla1.openstreetmap.de trace_attributes on 2026-11-09 (Giurgiu -> Ruse), trimmed.
    private val sample = """
      {"edges":[
        {"names":["DN5","E 70"],"length":2.1,"bridge":false,"use":"road"},
        {"names":["DN5","E 70","E 85","Giurgiu-Rousse Friendship Bridge"],"length":0.418,"bridge":true,"use":"road"},
        {"names":["DN5","E 70","E 85"],"length":0.724,"bridge":true,"use":"road"},
        {"names":["2","E 70","E 85"],"length":1.065,"bridge":true,"use":"road"},
        {"names":["Bulevard"],"length":0.5,"use":"road"},
        {"names":["Overpass"],"length":0.08,"bridge":true,"use":"road"},
        {"names":[],"length":2.166,"use":"ferry"},
        {"names":["Tunelul Mare"],"length":0.6,"tunnel":true,"use":"road"}
      ]}
    """.trimIndent()

    @Test fun mergesAndFilters() {
        val c = Crossings.summarize(Crossings.parseTraceAttributes(sample))
        assertEquals(3, c.size)
        assertEquals(CrossingKind.BRIDGE, c[0].kind)
        assertEquals("Giurgiu-Rousse Friendship Bridge", c[0].name)
        assertEquals(2.207, c[0].lengthKm, 0.001)
        assertEquals(CrossingKind.FERRY, c[1].kind)
        assertEquals(CrossingKind.TUNNEL, c[2].kind)
        assertEquals("Tunelul Mare", c[2].name)
    }

    @Test fun garbage() { assertEquals(0, Crossings.parseTraceAttributes("x").size) }
}

class TransitParserTest {
    @Test fun parsesRealTransitousResponse() {
        val text = javaClass.getResource("/transitous-plan-iasi.json")!!.readText()
        val its = TransitParser.parsePlan(text)
        assertEquals(2, its.size)
        val bus = its[0].legs[1]
        assertEquals("BUS", bus.mode)
        assertEquals("43", bus.line)
        assertEquals("SCTP Iasi", bus.agency)
        assertEquals("Start", its[0].legs[0].from)
        assertEquals("Destination", its[0].legs.last().to)
        assertTrue(its[0].legs[0].isWalk)
        assertEquals(1620, its[0].durationSec)
        assertTrue(bus.geometry.size >= 2)
        assertEquals(47.1, bus.geometry[0].lat, 0.2)
        assertEquals("TRAM", its[1].legs[1].mode)
    }

    @Test fun directWalkOnly() {
        val text = """{"itineraries":[],"direct":[{"startTime":"2026-11-09T20:00:00Z","endTime":"2026-11-09T20:18:00Z",
            "transfers":0,"legs":[{"mode":"WALK","from":{"name":"START"},"to":{"name":"END"},
            "startTime":"2026-11-09T20:00:00Z","endTime":"2026-11-09T20:18:00Z"}]}]}"""
        val plan = TransitParser.parseFull(text)
        assertEquals(0, plan.itineraries.size)
        assertEquals(1080, plan.direct.single().durationSec)
    }

    @Test fun emptyOrBroken() {
        assertEquals(0, TransitParser.parsePlan("""{"itineraries":[]}""").size)
        assertEquals(0, TransitParser.parsePlan("nope").size)
    }
}
