package com.zoomi.app.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap

/** Zoomi's own position markers, drawn in code (no third-party assets). */
object PuckIcons {
    const val ARROW = "zoomi-puck-arrow"

    /** A rounded navigation chevron pointing up (north), rotated on the map by bearing. */
    fun arrow(density: Float, brand: Int): Bitmap {
        val size = (56 * density).toInt()
        val bmp = createBitmap(size, size)
        val c = Canvas(bmp)
        val s = size.toFloat()
        val path = Path().apply {
            moveTo(s * 0.5f, s * 0.08f)
            lineTo(s * 0.86f, s * 0.88f)
            lineTo(s * 0.5f, s * 0.70f)
            lineTo(s * 0.14f, s * 0.88f)
            close()
        }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 6 * density
            strokeJoin = Paint.Join.ROUND
        }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f * density
            strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brand; style = Paint.Style.FILL }
        c.drawPath(path, shadow)
        c.drawPath(path, fill)
        c.drawPath(path, outline)
        return bmp
    }
}
