package com.zoomi.core.location

/**
 * Milestone (a) heading logic: trust the GNSS course only when the vehicle is really moving,
 * otherwise HOLD the last good course. A stopped car does not rotate, and GNSS bearing at
 * walking speed or standstill is noise. That noise is the main cause of the
 * "arrow pointing sideways" problem.
 *
 * Full sensor fusion (gyro while slow) and dead reckoning arrive in milestone (c).
 *
 * [course] is null until the first trustworthy course, so the UI shows a plain dot rather than
 * an arrow that might be wrong.
 */
class CourseTracker(
    private val minSpeedMps: Float = 2.5f,
    private val maxBearingAccuracyDeg: Float = 25f,
) {
    var course: Float? = null
        private set

    fun update(speedMps: Float?, bearingDeg: Float?, bearingAccuracyDeg: Float?): Float? {
        if (speedMps != null && bearingDeg != null && speedMps >= minSpeedMps &&
            (bearingAccuracyDeg == null || bearingAccuracyDeg <= maxBearingAccuracyDeg)
        ) {
            course = ((bearingDeg % 360f) + 360f) % 360f
        }
        return course
    }

    fun reset() { course = null }
}
