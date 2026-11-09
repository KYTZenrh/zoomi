package com.zoomi.core.theme

enum class ThemeMode { LIGHT, DARK, AUTO }

enum class ThemeReason { USER_LIGHT, USER_DARK, DAY, NIGHT, TUNNEL, SYSTEM_FALLBACK }

data class ThemeDecision(val dark: Boolean, val reason: ThemeReason)

/**
 * One snapshot of everything the Auto theme looks at.
 *
 * @param lastFixAgeMs age of the last GNSS fix, or null if there has never been one this session.
 * @param onTunnelRoad true/false if the map data under the vehicle says tunnel / not tunnel,
 *                     null if unknown (map not rendered there, not following, etc.).
 * @param lux ambient light in lux, or null when the phone has no light sensor.
 */
data class ThemeInputs(
    val nowMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val lux: Float?,
    val lastFixAgeMs: Long?,
    val onTunnelRoad: Boolean?,
    val systemDark: Boolean,
)

/**
 * Decides light vs dark for Light / Dark / Auto, without flicker.
 *
 * Day/night: sun elevation with hysteresis. It turns dark below [darkBelowDeg] and light again
 * only above [lightAboveDeg], and never switches more than once per [minSunDwellMs].
 *
 * Tunnel: handled by [TunnelDetector]. The light sensor alone never forces dark mode,
 * because a phone in a mount is often shaded by the dashboard or a sun visor.
 *
 * Not thread-safe; call from one thread.
 */
class AutoThemeController(
    private val darkBelowDeg: Double = -2.0,
    private val lightAboveDeg: Double = 1.0,
    private val minSunDwellMs: Long = 60_000,
    private val tunnelDetector: TunnelDetector = TunnelDetector(),
) {
    private var sunDark: Boolean? = null
    private var lastSunSwitchMs: Long = Long.MIN_VALUE

    val inTunnel: Boolean get() = tunnelDetector.inTunnel

    fun decide(mode: ThemeMode, input: ThemeInputs): ThemeDecision {
        // Always feed the detector so its timers stay correct when the user switches to Auto.
        val tunnel = tunnelDetector.update(input)
        return when (mode) {
            ThemeMode.LIGHT -> ThemeDecision(false, ThemeReason.USER_LIGHT)
            ThemeMode.DARK -> ThemeDecision(true, ThemeReason.USER_DARK)
            ThemeMode.AUTO -> auto(input, tunnel)
        }
    }

    private fun auto(input: ThemeInputs, tunnel: Boolean): ThemeDecision {
        val night = updateSun(input)
        return when {
            tunnel -> ThemeDecision(true, ThemeReason.TUNNEL)
            night == null -> ThemeDecision(input.systemDark, ThemeReason.SYSTEM_FALLBACK)
            night -> ThemeDecision(true, ThemeReason.NIGHT)
            else -> ThemeDecision(false, ThemeReason.DAY)
        }
    }

    /** Returns sun-based darkness, or null if position is unknown. */
    private fun updateSun(input: ThemeInputs): Boolean? {
        val lat = input.latitude ?: return sunDark
        val lon = input.longitude ?: return sunDark
        val elevation = SolarCalculator.elevationDegrees(input.nowMs, lat, lon)
        val current = sunDark
        if (current == null) {
            // First evaluation: no hysteresis band to respect yet, pick by the midpoint.
            sunDark = elevation < (darkBelowDeg + lightAboveDeg) / 2
            lastSunSwitchMs = input.nowMs
            return sunDark
        }
        val wanted = when {
            elevation < darkBelowDeg -> true
            elevation > lightAboveDeg -> false
            else -> current
        }
        if (wanted != current && input.nowMs - lastSunSwitchMs >= minSunDwellMs) {
            sunDark = wanted
            lastSunSwitchMs = input.nowMs
        }
        return sunDark
    }
}
