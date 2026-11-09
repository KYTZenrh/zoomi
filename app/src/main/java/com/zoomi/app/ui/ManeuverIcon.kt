package com.zoomi.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zoomi.core.nav.ArrowShape

/**
 * Zoomi's own maneuver / lane arrows (no third-party icon set).
 * Turns are drawn as a stem coming from the bottom that bends by the turn angle.
 */
@Composable
fun ManeuverIcon(shape: ArrowShape, color: Color, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.minDimension
        val stroke = w * 0.12f
        when (shape) {
            ArrowShape.ARRIVE -> drawArrive(color, w)
            ArrowShape.ROUNDABOUT -> drawRoundabout(color, w, stroke)
            ArrowShape.UTURN -> drawUTurn(color, w, stroke)
            else -> {
                val angle = when (shape) {
                    ArrowShape.SLIGHT_LEFT -> -40f
                    ArrowShape.LEFT -> -90f
                    ArrowShape.SHARP_LEFT -> -135f
                    ArrowShape.SLIGHT_RIGHT -> 40f
                    ArrowShape.RIGHT -> 90f
                    ArrowShape.SHARP_RIGHT -> 135f
                    else -> 0f
                }
                drawTurn(color, w, stroke, angle)
            }
        }
    }
}

private fun DrawScope.drawTurn(color: Color, w: Float, stroke: Float, angle: Float) {
    val pivot = Offset(w * 0.5f, w * 0.52f)
    // Stem from the bottom to the pivot.
    drawLine(color, Offset(w * 0.5f, w * 0.95f), pivot, stroke, StrokeCap.Round)
    // Head segment rotated by the turn angle around the pivot.
    rotate(angle, pivot) {
        val tip = Offset(pivot.x, w * 0.14f)
        drawLine(color, pivot, Offset(tip.x, tip.y + stroke * 1.2f), stroke, StrokeCap.Round)
        drawArrowHead(color, tip, w)
    }
}

private fun DrawScope.drawArrowHead(color: Color, tip: Offset, w: Float) {
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - w * 0.2f, tip.y + w * 0.22f)
        lineTo(tip.x + w * 0.2f, tip.y + w * 0.22f)
        close()
    }
    drawPath(head, color)
}

private fun DrawScope.drawUTurn(color: Color, w: Float, stroke: Float) {
    val path = Path().apply {
        moveTo(w * 0.68f, w * 0.95f)
        lineTo(w * 0.68f, w * 0.40f)
        cubicTo(w * 0.68f, w * 0.12f, w * 0.30f, w * 0.12f, w * 0.30f, w * 0.40f)
        lineTo(w * 0.30f, w * 0.62f)
    }
    drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val tip = Offset(w * 0.30f, w * 0.86f)
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - w * 0.18f, tip.y - w * 0.22f)
        lineTo(tip.x + w * 0.18f, tip.y - w * 0.22f)
        close()
    }
    drawPath(head, color)
}

private fun DrawScope.drawRoundabout(color: Color, w: Float, stroke: Float) {
    val c = Offset(w * 0.5f, w * 0.5f)
    drawCircle(color, radius = w * 0.2f, center = c, style = Stroke(stroke * 0.8f))
    drawLine(color, Offset(w * 0.5f, w * 0.95f), Offset(w * 0.5f, w * 0.72f), stroke, StrokeCap.Round)
    drawLine(color, Offset(w * 0.64f, w * 0.36f), Offset(w * 0.78f, w * 0.22f), stroke, StrokeCap.Round)
    drawArrowHeadRotated(color, Offset(w * 0.86f, w * 0.14f), w, 45f)
}

private fun DrawScope.drawArrowHeadRotated(color: Color, tip: Offset, w: Float, deg: Float) {
    rotate(deg, tip) { drawArrowHead(color, tip, w * 0.8f) }
}

private fun DrawScope.drawArrive(color: Color, w: Float) {
    // Map pin with a hole.
    val path = Path().apply {
        moveTo(w * 0.5f, w * 0.95f)
        cubicTo(w * 0.15f, w * 0.55f, w * 0.15f, w * 0.08f, w * 0.5f, w * 0.08f)
        cubicTo(w * 0.85f, w * 0.08f, w * 0.85f, w * 0.55f, w * 0.5f, w * 0.95f)
        close()
    }
    drawPath(path, color)
    drawCircle(Color.White, radius = w * 0.11f, center = Offset(w * 0.5f, w * 0.36f))
}
