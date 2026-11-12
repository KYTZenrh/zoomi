package com.zoomi.core.theme

/**
 * Detects "inside a tunnel" by combining map data, GNSS and the light sensor, with
 * debouncing so it can't flicker.
 *
 * Enter on ANY of these:
 *  1. Route (navigation): the active route says the vehicle is inside a tunnel stretch
 *     ([ThemeInputs.routeTunnel]) and it was moving recently. Switches right at the portal, like
 *     big navigation apps do; no need to wait for GPS loss.
 *  2. Underground (car parks, unmapped underpasses): moving recently, then GNSS lost for
 *     [undergroundGpsLostMs] AND ambient light below [undergroundLux] for [undergroundLuxMs].
 *  3. Map (when all of these hold):
 *  - the road under the vehicle is mapped as a tunnel,
 *  - the vehicle was moving (>= [movingSpeedMps]) within the last [recentMotionMs]
 *    (you drive into a tunnel; a phone indoors next to a mapped underpass must not trigger), and
 *  - GNSS fix lost for [gpsLostEnterMs], OR ambient light below [darkLux] for [luxEnterMs].
 *
 * Exit (only after at least [minTunnelMs]) when any of these holds:
 *  - fresh GNSS fix AND map says "not tunnel", continuously for [exitHoldMs];
 *  - fresh, accurate (<= [goodAccuracyM]) GNSS fix for [goodFixExitMs] while the map does NOT
 *    positively say tunnel (covers "map unknown", e.g. when the map isn't following you);
 *  - ambient light above [brightLux] for [luxExitMs] while GNSS is fresh.
 *
 * Map data alone never enters, and neither does the light sensor alone.
 */
class TunnelDetector(
    private val undergroundGpsLostMs: Long = 5_000,
    private val undergroundLux: Float = 15f,
    private val undergroundLuxMs: Long = 3_000,
    private val gpsLostEnterMs: Long = 3_000,
    private val darkLux: Float = 30f,
    private val luxEnterMs: Long = 2_000,
    private val brightLux: Float = 200f,
    private val luxExitMs: Long = 5_000,
    private val exitHoldMs: Long = 3_000,
    private val freshFixMs: Long = 2_000,
    private val minTunnelMs: Long = 4_000,
    private val movingSpeedMps: Float = 4f,
    private val recentMotionMs: Long = 15_000,
    private val goodAccuracyM: Float = 25f,
    private val goodFixExitMs: Long = 8_000,
) {
    var inTunnel: Boolean = false
        private set

    private val darkSince = Since()
    private val veryDarkSince = Since()
    private val routeOutSince = Since()
    private val brightSince = Since()
    private val outsideSince = Since()
    private val goodFixSince = Since()
    private var enteredAtMs: Long = 0
    private var lastMovingMs: Long? = null

    fun update(input: ThemeInputs): Boolean {
        val now = input.nowMs
        val lux = input.lux
        darkSince.track(lux != null && lux < darkLux, now)
        brightSince.track(lux != null && lux > brightLux, now)
        veryDarkSince.track(lux != null && lux < undergroundLux, now)

        val fixAge = input.lastFixAgeMs
        val gpsLost = fixAge == null || fixAge >= gpsLostEnterMs
        val gpsFresh = fixAge != null && fixAge < freshFixMs
        if (gpsFresh && (input.speedMps ?: 0f) >= movingSpeedMps) lastMovingMs = now
        val recentlyMoving = lastMovingMs?.let { now - it <= recentMotionMs } ?: false

        if (!inTunnel) {
            outsideSince.reset()
            goodFixSince.reset()
            routeOutSince.reset()
            val darkLongEnough = darkSince.heldFor(now) >= luxEnterMs
            val byRoute = input.routeTunnel == true && recentlyMoving
            val byMap = input.onTunnelRoad == true && recentlyMoving && (gpsLost || darkLongEnough)
            val underground = recentlyMoving && (fixAge == null || fixAge >= undergroundGpsLostMs) &&
                veryDarkSince.heldFor(now) >= undergroundLuxMs
            if (byRoute || byMap || underground) {
                inTunnel = true
                enteredAtMs = now
            }
        } else {
            outsideSince.track(gpsFresh && input.onTunnelRoad == false, now)
            val accurate = input.accuracyM?.let { it <= goodAccuracyM } ?: false
            goodFixSince.track(gpsFresh && accurate && input.onTunnelRoad != true && input.routeTunnel != true, now)
            // The route says we're out (e.g. dead reckoning passed the exit portal): trust it once GPS or light agrees.
            routeOutSince.track(input.routeTunnel == false && (gpsFresh || (lux != null && lux > brightLux)), now)
            val minElapsed = now - enteredAtMs >= minTunnelMs
            val mapSaysOut = input.routeTunnel != true && outsideSince.heldFor(now) >= exitHoldMs
            val routeSaysOut = routeOutSince.heldFor(now) >= exitHoldMs
            val gpsSaysOut = goodFixSince.heldFor(now) >= goodFixExitMs
            val lightSaysOut = gpsFresh && input.routeTunnel != true && brightSince.heldFor(now) >= luxExitMs
            if (minElapsed && (mapSaysOut || routeSaysOut || gpsSaysOut || lightSaysOut)) {
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
