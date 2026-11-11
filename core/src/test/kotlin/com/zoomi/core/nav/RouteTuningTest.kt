package com.zoomi.core.nav

import com.zoomi.core.location.LatLon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTuningTest {
    // Shape of the body Ferrostar 0.59 generates for Valhalla (trimmed).
    private val req = """{"format":"osrm","costing":"pedestrian","locations":[
        {"lat":47.1585,"lon":27.5925,"street_side_tolerance":18},
        {"lat":47.16,"lon":27.59,"type":"via"},
        {"lat":47.1573,"lon":27.5863,"type":"break"}],"alternates":2}"""

    private fun radii(s: String) = (Json.parseToJsonElement(s) as JsonObject)["locations"]!!.let { it as JsonArray }
        .map { (it as JsonObject)["radius"]?.jsonPrimitive?.int }

    @Test fun walkUsesAccuracyAtStartAndWideEnd() {
        assertEquals(listOf(18, null, 25), radii(RouteTuning.tune(req, TravelMode.WALK, 18.0)))
    }

    @Test fun carKeepsStartTight() {
        assertEquals(listOf(15, null, 10), radii(RouteTuning.tune(req, TravelMode.CAR, 35.0)))
    }

    @Test fun clampsAccuracy() {
        assertEquals(5, RouteTuning.startRadius(TravelMode.WALK, 1.0))
        assertEquals(40, RouteTuning.startRadius(TravelMode.WALK, 500.0))
    }

    @Test fun keepsOtherFieldsAndSurvivesGarbage() {
        val t = RouteTuning.tune(req, TravelMode.BIKE, null)
        assertTrue(t.contains("\"alternates\":2") && t.contains("\"street_side_tolerance\":18"))
        assertEquals("nope", RouteTuning.tune("nope", TravelMode.CAR, null))
        assertEquals(18.0, RouteTuning.startTolerance(req)!!, 0.0)
        assertNull(RouteTuning.startTolerance("nope"))
    }
}

class ArrivalTest {
    private val flat = LatLon(47.1600, 27.5900)

    @Test fun arrivesWhenCloseEvenIfRouteEndIsElsewhere() {
        assertTrue(Arrival.arrived(TravelMode.WALK, LatLon(47.16015, 27.59005), flat, 8.0))   // ~17 m
        assertFalse(Arrival.arrived(TravelMode.WALK, LatLon(47.1604, 27.5900), flat, 8.0))    // ~44 m
        assertTrue(Arrival.arrived(TravelMode.CAR, LatLon(47.1603, 27.5900), flat, 8.0))      // ~33 m
    }

    @Test fun poorFixDoesNotTrigger() {
        assertFalse(Arrival.arrived(TravelMode.WALK, flat, flat, 80.0))
    }
}
