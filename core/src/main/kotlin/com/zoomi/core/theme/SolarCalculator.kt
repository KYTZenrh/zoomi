package com.zoomi.core.theme

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sun position computed locally (works offline), using the NOAA solar calculator
 * equations (based on Jean Meeus, "Astronomical Algorithms"). Accuracy is about
 * one minute for sunrise/sunset at mid latitudes, which is far better than the
 * theme switch needs.
 */
object SolarCalculator {

    /** Apparent sun elevation in degrees above the horizon (negative = below). */
    fun elevationDegrees(epochMillis: Long, latitude: Double, longitude: Double): Double {
        val jd = epochMillis / 86_400_000.0 + 2_440_587.5
        val t = (jd - 2_451_545.0) / 36_525.0

        val l0 = normalizeDeg(280.46646 + t * (36000.76983 + t * 0.0003032))
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val mRad = rad(m)
        val c = sin(mRad) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * mRad) * (0.019993 - 0.000101 * t) +
            sin(3 * mRad) * 0.000289
        val trueLong = l0 + c
        val omega = 125.04 - 1934.136 * t
        val lambda = trueLong - 0.00569 - 0.00478 * sin(rad(omega))
        val eps0 = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val eps = eps0 + 0.00256 * cos(rad(omega))
        val decl = asin(sin(rad(eps)) * sin(rad(lambda)))

        val y = tan(rad(eps / 2)) * tan(rad(eps / 2))
        val l0Rad = rad(l0)
        val eqTimeMin = 4 * deg(
            y * sin(2 * l0Rad) - 2 * e * sin(mRad) + 4 * e * y * sin(mRad) * cos(2 * l0Rad) -
                0.5 * y * y * sin(4 * l0Rad) - 1.25 * e * e * sin(2 * mRad)
        )

        val minutesUtc = ((epochMillis % 86_400_000L + 86_400_000L) % 86_400_000L) / 60_000.0
        var trueSolarTime = (minutesUtc + eqTimeMin + 4 * longitude) % 1440.0
        if (trueSolarTime < 0) trueSolarTime += 1440.0
        var hourAngle = trueSolarTime / 4.0 - 180.0
        if (hourAngle < -180) hourAngle += 360.0

        val latRad = rad(latitude)
        val cosZenith = (sin(latRad) * sin(decl) + cos(latRad) * cos(decl) * cos(rad(hourAngle)))
            .coerceIn(-1.0, 1.0)
        val elevation = 90.0 - deg(acos(cosZenith))
        return elevation + refraction(elevation)
    }

    /** Atmospheric refraction correction (degrees), NOAA approximation. */
    private fun refraction(elevation: Double): Double {
        if (elevation > 85.0) return 0.0
        val te = tan(rad(elevation))
        val arcSec = when {
            elevation > 5.0 -> 58.1 / te - 0.07 / (te * te * te) + 0.000086 / (te * te * te * te * te)
            elevation > -0.575 -> 1735.0 + elevation * (-518.2 + elevation * (103.4 + elevation * (-12.79 + elevation * 0.711)))
            else -> -20.774 / te
        }
        return arcSec / 3600.0
    }

    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI
    private fun normalizeDeg(d: Double): Double = ((d % 360.0) + 360.0) % 360.0
}
