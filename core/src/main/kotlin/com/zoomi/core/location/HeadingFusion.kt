package com.zoomi.core.location

import kotlin.math.abs
import kotlin.math.sqrt

enum class HeadingSource { NONE, GNSS, GYRO, HELD }

data class HeadingEstimate(val degrees: Float?, val uncertaintyDeg: Float, val source: HeadingSource)

/**
 * Vehicle heading from GNSS course + gyroscope, without the magnetometer.
 *
 * Why no compass: inside a car the magnetometer is disturbed by the metal body, the engine and
 * phone-mount magnets, which is the classic "arrow pointing sideways" bug. The gyroscope is not.
 *
 * - Moving fast enough with an accurate course: the GNSS course is the truth (snap to it).
 * - Slow or stopped, or between GNSS updates: integrate the gyro's rotation around the vertical
 *   axis from the last good course. A deadband ignores sensor noise while stationary, so a stopped
 *   car's heading does not drift.
 * - Uncertainty grows with time and turning since the last GNSS course; past [maxUncertaintyDeg]
 *   the estimate is withdrawn (UI shows a dot instead of a possibly wrong arrow).
 *
 * Not thread-safe; feed from one thread.
 */
class HeadingFusion(
    private val minSpeedMps: Float = 2.5f,
    private val maxBearingAccuracyDeg: Float = 25f,
    private val gyroDeadbandDegPerSec: Float = 0.6f,
    private val driftDegPerSec: Float = 0.05f,
    private val scaleError: Float = 0.03f,
    private val maxUncertaintyDeg: Float = 60f,
) {
    private var heading: Float? = null
    private var uncertainty = 180f
    private var source = HeadingSource.NONE
    private var lastGyroMs: Long? = null

    val estimate: HeadingEstimate
        get() = HeadingEstimate(heading?.takeIf { uncertainty <= maxUncertaintyDeg }, uncertainty, source)

    fun onGnss(speedMps: Float?, bearingDeg: Float?, bearingAccuracyDeg: Float?): HeadingEstimate {
        if (speedMps != null && bearingDeg != null && speedMps >= minSpeedMps &&
            (bearingAccuracyDeg == null || bearingAccuracyDeg <= maxBearingAccuracyDeg)
        ) {
            heading = norm(bearingDeg)
            uncertainty = bearingAccuracyDeg ?: 10f
            source = HeadingSource.GNSS
        }
        return estimate
    }

    /**
     * @param yawRateDegPerSec rate of change of the compass heading (clockwise positive),
     *        e.g. from [yawRateDegPerSec] (the function below).
     */
    fun onGyro(timeMs: Long, yawRateDegPerSec: Float): HeadingEstimate {
        val last = lastGyroMs
        lastGyroMs = timeMs
        val h = heading ?: return estimate
        if (last == null) return estimate
        val dt = (timeMs - last) / 1000f
        if (dt <= 0f || dt > 1f) return estimate // sensor gap: don't integrate across it
        if (abs(yawRateDegPerSec) >= gyroDeadbandDegPerSec) {
            val delta = yawRateDegPerSec * dt
            heading = norm(h + delta)
            uncertainty += abs(delta) * scaleError
            source = HeadingSource.GYRO
        } else if (source == HeadingSource.GNSS) {
            source = HeadingSource.HELD
        }
        uncertainty += driftDegPerSec * dt
        return estimate
    }

    fun reset() {
        heading = null; uncertainty = 180f; source = HeadingSource.NONE; lastGyroMs = null
    }

    companion object {
        fun norm(d: Float): Float = ((d % 360f) + 360f) % 360f

        /**
         * Vehicle yaw rate (clockwise-positive, deg/s) from raw sensors, independent of how the
         * phone is mounted: the gyro vector projected onto the "up" direction from the gravity sensor.
         *
         * Android conventions: gyroscope = rad/s, counter-clockwise positive around each device axis;
         * TYPE_GRAVITY points away from the Earth's centre in device coordinates (like the
         * accelerometer at rest). Counter-clockwise around "up" = turning left = heading decreasing.
         */
        fun yawRateDegPerSec(gx: Float, gy: Float, gz: Float, upX: Float, upY: Float, upZ: Float): Float {
            val n = sqrt(upX * upX + upY * upY + upZ * upZ)
            if (n < 1e-3f) return 0f
            val ccwRadPerSec = (gx * upX + gy * upY + gz * upZ) / n
            return (-ccwRadPerSec * 180.0 / Math.PI).toFloat()
        }
    }
}
