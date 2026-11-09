package com.zoomi.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourseTrackerTest {
    @Test fun noCourseUntilMoving() {
        val c = CourseTracker()
        assertNull(c.update(0f, 123f, 10f))
        assertNull(c.update(1.0f, 200f, 10f))
        assertEquals(90f, c.update(10f, 90f, 5f)!!, 0.001f)
    }

    @Test fun holdsCourseWhenStopped() {
        val c = CourseTracker()
        c.update(15f, 45f, 3f)
        // Stopped at a light: GNSS bearing jumps around, must be ignored.
        for (b in listOf(270f, 10f, 180f, 300f)) assertEquals(45f, c.update(0.3f, b, 90f)!!, 0.001f)
    }

    @Test fun ignoresInaccurateBearing() {
        val c = CourseTracker()
        c.update(15f, 45f, 3f)
        assertEquals(45f, c.update(15f, 200f, 60f)!!, 0.001f)
    }

    @Test fun normalizesBearing() {
        val c = CourseTracker()
        assertEquals(10f, c.update(10f, 370f, null)!!, 0.001f)
        assertEquals(350f, c.update(10f, -10f, null)!!, 0.001f)
    }
}
