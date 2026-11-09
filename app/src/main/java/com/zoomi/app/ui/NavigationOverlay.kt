package com.zoomi.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zoomi.app.nav.SpeedLimit
import com.zoomi.core.nav.ArrowShape
import com.zoomi.core.nav.Formatting
import com.zoomi.core.nav.ManeuverShapes
import uniffi.ferrostar.LaneInfo
import uniffi.ferrostar.TripState
import kotlin.math.roundToInt

/**
 * Driving UI: one big instruction at the top, ETA strip at the bottom, speed limit on the left.
 * Everything else is hidden to keep distraction low.
 */
@Composable
fun NavigationOverlay(
    trip: TripState.Navigating,
    rerouting: Boolean,
    speedLimit: SpeedLimit?,
    currentSpeedKmh: Int?,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onShareEta: () -> Unit,
    onEnd: () -> Unit,
) {
    val instr = trip.visualInstruction
    val primary = instr?.primaryContent
    val shape = ManeuverShapes.forManeuver(primary?.maneuverType?.name, primary?.maneuverModifier?.name)

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        // Top: maneuver banner.
        Surface(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 10.dp,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ManeuverIcon(shape, MaterialTheme.colorScheme.onPrimary, 60.dp)
                    Column(Modifier.weight(1f)) {
                        Text(Formatting.distance(trip.progress.distanceToNextManeuver),
                            fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        Text(primary?.text ?: trip.remainingSteps.firstOrNull()?.instruction ?: "",
                            style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        instr?.secondaryContent?.text?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                val lanes = instr?.subContent?.laneInfo ?: primary?.laneInfo
                if (!lanes.isNullOrEmpty()) LaneRow(lanes)
            }
        }

        if (rerouting) {
            Surface(
                Modifier.align(Alignment.Center), shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary,
                shadowElevation = 6.dp,
            ) { Text("Finding a new route…", Modifier.padding(horizontal = 18.dp, vertical = 10.dp), fontWeight = FontWeight.Bold) }
        }

        // Left: speed limit sign (only when OSM has it) + current speed.
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            speedLimit?.let { SpeedLimitSign(it, currentSpeedKmh) }
            currentSpeedKmh?.let {
                Surface(shape = CircleShape, shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.size(64.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$it", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("km/h", fontSize = 10.sp)
                    }
                }
            }
        }

        // Bottom: ETA strip.
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
            shape = RoundedCornerShape(24.dp), shadowElevation = 10.dp,
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Formatting.arrivalClock(System.currentTimeMillis(), trip.progress.durationRemaining),
                        fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("${Formatting.duration(trip.progress.durationRemaining)} · ${Formatting.distance(trip.progress.distanceRemaining)}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onToggleMute, modifier = Modifier.padding(end = 6.dp)) { Text(if (muted) "🔇" else "🔊") }
                OutlinedButton(onClick = onShareEta, modifier = Modifier.padding(end = 6.dp)) { Text("Share") }
                Button(onClick = onEnd, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5484D), contentColor = Color.White)) {
                    Text("End")
                }
            }
        }
    }
}

@Composable
private fun LaneRow(lanes: List<LaneInfo>) {
    Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        lanes.forEach { lane ->
            val dir = if (lane.active) lane.activeDirection ?: lane.directions.firstOrNull() else lane.directions.firstOrNull()
            val shape: ArrowShape = ManeuverShapes.forDirection(dir)
            val color = if (lane.active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f)
            Box(Modifier.padding(horizontal = 4.dp).border(BorderStroke(1.dp, color.copy(alpha = 0.4f)), RoundedCornerShape(6.dp)).padding(4.dp)) {
                ManeuverIcon(shape, color, 28.dp)
            }
        }
    }
}

/** European-style round sign. Turns the ring brighter if you're over the limit. */
@Composable
private fun SpeedLimitSign(limit: SpeedLimit, currentKmh: Int?) {
    val over = currentKmh != null && limit.unit == "km/h" && currentKmh > limit.value + 3
    Box(
        Modifier.size(72.dp).background(Color.White, CircleShape)
            .border(BorderStroke(if (over) 9.dp else 7.dp, Color(0xFFD62828)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("${limit.value}", color = Color.Black, fontSize = if (limit.value >= 100) 24.sp else 28.sp, fontWeight = FontWeight.Bold)
    }
}

/** m/s -> km/h for display. */
fun mpsToKmh(mps: Float): Int = (mps * 3.6f).roundToInt()
