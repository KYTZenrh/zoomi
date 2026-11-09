package com.zoomi.core.theme

/**
 * Detects "inside a tunnel" by combining three signals, with debouncing so it can't flicker.
 *
 * Enter when the road under the vehicle is mapped as a tunnel AND at least one of these
 * has been true for its hold time:
 *  - GNSS fix lost (no fix for [gpsLostEnterMs]), or
 *  - ambient light below [darkLux] for [luxEnterMs].
 *
 * Exit (only after at least [minTunnelMs] in tunnel mode) when either:
 *  - a fresh GNSS fix exists AND the map no longer says tunnel, continuously for [exitHoldMs], or
 *  - ambient light above [brightLux] continuously for [luxExitMs] while GNSS is fresh.
 *
 * If map data is unknown (null), we never ENTER on sensor data alone. A car park, a bridge
 * shadow or a pocket would otherwise trigger it. Once inside, unknown map data counts as
 * "maybe still tunnel", so exit then needs the light condition.
 */
class TunnelDetector(
    private val gpsLostEnterMs: Long = 3_000,
    private val darkLux: Float = 30f,
    private val luxEnterMs: Long = 2_000,
    private val brightLux: Float = 200f,
    private val luxExitMs: Long = 5_000,
    private val exitHoldMs: Long = 3_000,
    private val freshFixMs: Long = 2_000,
    private val minTunnelMs: Long = 4_000,
) {
    var inTunnel: Boolean = false
        private set

    private val darkSince = Since()
    private val brightSince = Since()
    private val outsideSince = Since()
    private var enteredAtMs: Long = 0

    fun update(input: ThemeInputs): Boolean {
        val now = input.nowMs
        val lux = input.lux
        darkSince.track(lux != null && lux < darkLux, now)
        brightSince.track(lux != null && lux > brightLux, now)

        val fixAge = input.lastFixAgeMs
        val gpsLost = fixAge == null || fixAge >= gpsLostEnterMs
        val gpsFresh = fixAge != null && fixAge < freshFixMs

        if (!inTunnel) {
            outsideSince.reset()
            val darkLongEnough = darkSince.heldFor(now) >= luxEnterMs
            if (input.onTunnelRoad == true && (gpsLost || darkLongEnough)) {
                inTunnel = true
                enteredAtMs = now
            }
        } else {
            outsideSince.track(gpsFresh && input.onTunnelRoad == false, now)
            val minElapsed = now - enteredAtMs >= minTunnelMs
            val mapSaysOut = outsideSince.heldFor(now) >= exitHoldMs
            val lightSaysOut = gpsFresh && brightSince.heldFor(now) >= luxExitMs
            if (minElapsed && (mapSaysOut || lightSaysOut)) {
                inTunnel = false
            }
        }
        return inTunnel
    }

    /** Tracks since when a condition has been continuously true. */
    private class Since {
        private var since: Long? = null
        fun track(condition: Boolean, now: Long) {
            since = if (condition) (since ?: now) else null
        }
        fun reset() { since = null }
        fun heldFor(now: Long): Long = since?.let { now - it } ?: -1
    }
}
