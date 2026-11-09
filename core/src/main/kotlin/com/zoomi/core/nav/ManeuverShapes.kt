package com.zoomi.core.nav

/** Which arrow to draw for a maneuver or a lane. Zoomi draws its own icons from this. */
enum class ArrowShape { STRAIGHT, SLIGHT_LEFT, LEFT, SHARP_LEFT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, UTURN, ROUNDABOUT, ARRIVE }

object ManeuverShapes {

    /**
     * @param type maneuver type name, e.g. "TURN", "ROUNDABOUT", "ARRIVE" (Ferrostar enum names or OSRM strings)
     * @param modifier e.g. "SLIGHT_LEFT", "slight left", "uturn"
     */
    fun forManeuver(type: String?, modifier: String?): ArrowShape {
        val t = norm(type)
        return when {
            t == "arrive" -> ArrowShape.ARRIVE
            t in setOf("roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary") -> ArrowShape.ROUNDABOUT
            else -> forDirection(modifier)
        }
    }

    /** Lane directions use the same vocabulary as maneuver modifiers ("left", "slight right", "uturn"...). */
    fun forDirection(direction: String?): ArrowShape = when (norm(direction)) {
        "uturn", "u turn" -> ArrowShape.UTURN
        "sharp right" -> ArrowShape.SHARP_RIGHT
        "right" -> ArrowShape.RIGHT
        "slight right" -> ArrowShape.SLIGHT_RIGHT
        "slight left" -> ArrowShape.SLIGHT_LEFT
        "left" -> ArrowShape.LEFT
        "sharp left" -> ArrowShape.SHARP_LEFT
        else -> ArrowShape.STRAIGHT
    }

    private fun norm(s: String?) = s?.lowercase()?.replace('_', ' ')?.replace('-', ' ')?.trim() ?: ""
}

/** "+4 min" style comparison against the fastest route. */
object RouteCompare {
    fun deltaLabel(durationSec: Double, fastestSec: Double): String {
        val deltaMin = Math.round((durationSec - fastestSec) / 60.0)
        return if (deltaMin <= 0) "Fastest" else "+$deltaMin min"
    }
}
