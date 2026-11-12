package com.zoomi.core.regions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegionTest {
    private val ro = """
      {"id":"romania","name":"Romania","bbox":[20.2,43.6,29.8,48.3],"osmDate":"2026-11-09","valhallaVersion":"3.9.1",
       "files":[{"name":"map.pmtiles","url":"https://x/map.pmtiles","size":100,"sha256":"AB"},
                {"name":"routing.tar","url":"https://x/routing.tar","size":200}]}
    """.trimIndent()

    @Test fun parsesManifest() {
        val r = RegionManifests.parseOne(ro)!!
        assertEquals("romania", r.id)
        assertEquals("map.pmtiles", r.mapFile!!.name)
        assertEquals("routing.tar", r.routingFile!!.name)
        assertEquals("ab", r.mapFile!!.sha256)
        assertEquals(300L, r.totalSize)
    }

    @Test fun rejectsPathTraversal() {
        assertNull(RegionManifests.parseOne(ro.replace("\"romania\"", "\"../../etc\"")))
        val r = RegionManifests.parseOne(ro.replace("\"map.pmtiles\"", "\"../evil.pmtiles\""))!!
        assertNull(r.mapFile) // file dropped, routing remains
    }

    @Test fun rejectsBadBbox() {
        assertNull(RegionManifests.parseOne(ro.replace("[20.2,43.6,29.8,48.3]", "[29.8,43.6,20.2,48.3]")))
    }

    @Test fun coveringPicksSmallestContainingAll() {
        val big = RegionManifests.parseOne(ro.replace("\"romania\"", "\"europe\"").replace("[20.2,43.6,29.8,48.3]", "[-10,35,40,70]"))!!
        val small = RegionManifests.parseOne(ro)!!
        val iasi = 47.16 to 27.59
        val bucharest = 44.43 to 26.10
        val vienna = 48.21 to 16.37
        assertEquals("romania", RegionManifests.covering(listOf(big, small), listOf(iasi, bucharest))!!.id)
        assertEquals("europe", RegionManifests.covering(listOf(big, small), listOf(iasi, vienna))!!.id)
        assertNull(RegionManifests.covering(listOf(small), listOf(iasi, vienna)))
    }

    /** Exact file produced by data-pipeline/package_region.py (pretty-printed, no URLs). */
    @Test fun parsesPipelineOutput() {
        val text = javaClass.getResource("/region-romania.json")!!.readText()
        val r = RegionManifests.parseOne(text)
        assertEquals("romania", r?.id)
        assertEquals(2, r!!.files.size)
    }

    @Test fun encodeRoundTrip() {
        val r = RegionManifests.parseOne(ro)!!
        assertEquals(r, RegionManifests.parseOne(RegionManifests.encode(r)))
    }

    @Test fun catalog() {
        assertEquals(1, RegionManifests.parseCatalog("""{"regions":[$ro, {"id":"bad"}]}""").size)
        assertEquals(0, RegionManifests.parseCatalog("nope").size)
    }

    /** Regression: Iasi is inside Moldova's bbox; with real borders Romania must win. */
    @Test fun areaBeatsOverlappingBbox() {
        fun region(id: String, bbox: String, ring: String, size: Long = 1) = RegionManifests.parseOne(
            """{"id":"$id","bbox":$bbox,"files":[{"name":"map.pmtiles","url":"u","size":$size}],
               "area":[{"hole":false,"ring":$ring}]}"""
        )!!
        // Simplified boxes: Romania west of the Prut (lon 27.0-28.2 near Iasi), Moldova east of it.
        val ro = region("romania", "[20.2,43.6,30.3,48.3]", "[20.2,43.6, 28.2,43.6, 28.2,48.3, 20.2,48.3]")
        val md = region("moldova", "[26.6,45.4,30.2,48.5]", "[28.2,45.4, 30.2,45.4, 30.2,48.5, 28.2,48.5]")
        val iasi = 47.16 to 27.59
        assertEquals("romania", RegionManifests.covering(listOf(ro, md), listOf(iasi))?.id)
        assertEquals("moldova", RegionManifests.covering(listOf(ro, md), listOf(47.0 to 28.86))?.id) // Chisinau
        val back = RegionManifests.parseOne(RegionManifests.encode(ro))!!
        assertEquals(ro.area, back.area)
    }

    @Test fun holeIsExcluded() {
        val r = RegionManifests.parseOne(
            """{"id":"za","bbox":[0,0,10,10],"files":[{"name":"map.pmtiles","url":"u"}],
               "area":[{"hole":false,"ring":[0,0,10,0,10,10,0,10]},{"hole":true,"ring":[4,4,6,4,6,6,4,6]}]}"""
        )!!
        assertEquals(true, r.contains(1.0, 1.0))
        assertEquals(false, r.contains(5.0, 5.0))
        assertNull(RegionManifests.covering(listOf(r), listOf(5.0 to 5.0)))
    }
}
