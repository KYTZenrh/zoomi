package com.zoomi.core.search

import com.zoomi.core.places.PlaceKind
import com.zoomi.core.places.SavedPlace
import com.zoomi.core.places.SavedPlaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotonParserTest {
    // Shape copied from a real photon.komoot.io response (2026-11-09), trimmed.
    private val sample = """
    {"type":"FeatureCollection","features":[
      {"type":"Feature","properties":{"osm_type":"R","osm_id":399268,"osm_key":"tourism","osm_value":"museum",
        "type":"house","housenumber":"1","name":"Palatul Culturii","street":"Bulevardul Ștefan cel Mare și Sfânt",
        "district":"Centru","city":"Iași","county":"Iași","country":"România","postcode":"700028","countrycode":"RO"},
       "geometry":{"type":"Point","coordinates":[27.5862609,47.1573073]}},
      {"type":"Feature","properties":{"osm_key":"historic","osm_value":"castle","name":"Palatul Culturii",
        "street":"Strada Palat","housenumber":"1","city":"Iași","country":"România"},
       "geometry":{"type":"Point","coordinates":[27.58627,47.15731]}},
      {"type":"Feature","properties":{"osm_key":"place","osm_value":"house","street":"Strada Lungă","housenumber":"5","city":"Iași"},
       "geometry":{"type":"Point","coordinates":[27.6,47.2]}},
      {"type":"Feature","properties":{"name":"broken"},"geometry":{"type":"Point","coordinates":[]}}
    ]}
    """.trimIndent()

    @Test fun parsesAndDeduplicates() {
        val r = PhotonParser.parse(sample)
        assertEquals(2, r.size) // same name at ~same point collapsed; broken geometry dropped
        assertEquals("Palatul Culturii", r[0].title)
        assertEquals("Museum, Bulevardul Ștefan cel Mare și Sfânt 1, Centru, Iași, România", r[0].subtitle)
        assertEquals(47.1573073, r[0].lat, 1e-9)
        assertEquals("tourism/museum", r[0].category)
    }

    @Test fun addressOnlyUsesStreetAsTitle() {
        val r = PhotonParser.parse(sample)
        assertEquals("Strada Lungă 5", r[1].title)
        assertEquals("Iași", r[1].subtitle)
    }

    @Test fun kindLabels() {
        assertEquals("Bus stop", PhotonParser.kindLabel("highway", "bus_stop"))
        assertEquals("Museum", PhotonParser.kindLabel("tourism", "museum"))
        assertEquals("Ice cream", PhotonParser.kindLabel("amenity", "ice_cream"))
        assertNull(PhotonParser.kindLabel("place", "house"))
        assertNull(PhotonParser.kindLabel("building", "yes"))
    }

    @Test fun emptyResponse() {
        assertEquals(0, PhotonParser.parse("""{"type":"FeatureCollection","features":[]}""").size)
    }
}

class SearchQueryPolicyTest {
    @Test fun rules() {
        assertNull(SearchQueryPolicy.normalize("  ab "))
        assertEquals("str lunga", SearchQueryPolicy.normalize("  str    lunga "))
    }
}

class SavedPlacesTest {
    private fun p(id: String, kind: PlaceKind, name: String = id) = SavedPlace(id, kind, name, "addr", 47.0, 27.0)

    @Test fun onlyOneHomeAndWork() {
        var l = listOf(p("a", PlaceKind.HOME), p("b", PlaceKind.WORK), p("c", PlaceKind.FAVORITE))
        l = SavedPlaces.upsert(l, p("d", PlaceKind.HOME))
        assertEquals(listOf("d", "b", "c"), l.map { it.id })
        l = SavedPlaces.upsert(l, p("e", PlaceKind.FAVORITE, "aaa"))
        assertEquals(listOf("d", "b", "e", "c"), l.map { it.id })
    }

    @Test fun roundTrip() {
        val l = listOf(p("x", PlaceKind.WORK, "Birou \"nou\""), p("y", PlaceKind.FAVORITE, "Mama"))
        assertEquals(l, SavedPlaces.decode(SavedPlaces.encode(l)))
    }

    @Test fun decodeGarbage() {
        assertEquals(emptyList<SavedPlace>(), SavedPlaces.decode("not json"))
        assertEquals(emptyList<SavedPlace>(), SavedPlaces.decode(null))
    }
}
