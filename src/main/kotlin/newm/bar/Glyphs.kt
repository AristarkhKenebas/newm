package newm.bar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The bar's own small pictures, drawn as lines (sharp at any scale, in the bar's colours). */
enum class Glyph { WIFI, WIFI_OFF, WIRED, SPEAKER, BATTERY, POWER, LOCK, LOGOUT, MOON, RESTART, CHEVRON_LEFT, CHEVRON_RIGHT, BRIGHTNESS, BLUETOOTH, CHECK, MIC }

/**
 * [glyph] at [size] in [color]; [level] (0..1) fills what has a level (Wi-Fi's strength, the
 * battery, the volume's waves), [slash] crosses it out (muted), [bolt] adds a charging bolt.
 */
@Composable
fun GlyphIcon(glyph: Glyph, color: Color, size: Dp = 16.dp, level: Float = 1f, slash: Boolean = false, bolt: Boolean = false, accent: Color = color) {
    Canvas(Modifier.size(size)) { drawGlyph(glyph, color, level.coerceIn(0f, 1f), slash, bolt, accent) }
}

private fun DrawScope.drawGlyph(glyph: Glyph, color: Color, level: Float, slash: Boolean, bolt: Boolean, accent: Color) {
    val s = size.minDimension
    val w = s * 0.1f
    val line = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val faint = color.copy(alpha = color.alpha * 0.3f)
    when (glyph) {
        Glyph.WIFI, Glyph.WIFI_OFF -> {
            // A dot and three arcs above it, lit by strength.
            val c = Offset(s / 2, s * 0.82f)
            val bars = if (glyph == Glyph.WIFI_OFF) 0 else when {
                level > 0.66f -> 3
                level > 0.33f -> 2
                level > 0.05f -> 1
                else -> 0
            }
            drawCircle(if (glyph == Glyph.WIFI_OFF) faint else color, radius = w * 0.75f, center = c)
            for (k in 1..3) {
                val r = s * (0.2f + 0.22f * (k - 1))
                drawArc(if (k <= bars) color else faint, startAngle = 225f, sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r), style = line)
            }
            if (glyph == Glyph.WIFI_OFF) drawLine(color, Offset(s * 0.18f, s * 0.18f), Offset(s * 0.82f, s * 0.82f), w, StrokeCap.Round)
        }
        Glyph.WIRED -> {
            // A box above, a line, three boxes below.
            val box = s * 0.26f
            drawRoundRect(color, Offset(s / 2 - box / 2, s * 0.08f), Size(box, box), CornerRadius(w), style = line)
            drawLine(color, Offset(s / 2, s * 0.08f + box), Offset(s / 2, s * 0.55f), w, StrokeCap.Round)
            drawLine(color, Offset(s * 0.2f, s * 0.55f), Offset(s * 0.8f, s * 0.55f), w, StrokeCap.Round)
            for (x in listOf(0.2f, 0.5f, 0.8f)) drawLine(color, Offset(s * x, s * 0.55f), Offset(s * x, s * 0.72f), w, StrokeCap.Round)
            for (x in listOf(0.2f, 0.5f, 0.8f)) drawCircle(color, radius = w * 0.9f, center = Offset(s * x, s * 0.82f))
        }
        Glyph.SPEAKER -> {
            val body = Path().apply {
                moveTo(s * 0.12f, s * 0.38f); lineTo(s * 0.28f, s * 0.38f); lineTo(s * 0.48f, s * 0.2f)
                lineTo(s * 0.48f, s * 0.8f); lineTo(s * 0.28f, s * 0.62f); lineTo(s * 0.12f, s * 0.62f); close()
            }
            drawPath(body, color)
            if (slash) {
                drawLine(color, Offset(s * 0.62f, s * 0.38f), Offset(s * 0.86f, s * 0.62f), w, StrokeCap.Round)
                drawLine(color, Offset(s * 0.86f, s * 0.38f), Offset(s * 0.62f, s * 0.62f), w, StrokeCap.Round)
            } else {
                val waves = when {
                    level > 0.6f -> 3
                    level > 0.25f -> 2
                    level > 0f -> 1
                    else -> 0
                }
                val c = Offset(s * 0.48f, s / 2)
                for (k in 1..3) {
                    val r = s * (0.12f + 0.13f * (k - 1))
                    drawArc(if (k <= waves) color else faint, -50f, 100f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = line)
                }
            }
        }
        Glyph.BATTERY -> {
            // A body with its level (accent when low), a nub, a bolt while charging.
            val top = s * 0.28f
            val h = s * 0.44f
            val bodyW = s * 0.78f
            drawRoundRect(color, Offset(s * 0.04f, top), Size(bodyW, h), CornerRadius(s * 0.1f), style = Stroke(w * 0.9f))
            drawRoundRect(color, Offset(s * 0.04f + bodyW + w * 0.4f, top + h * 0.3f), Size(s * 0.08f, h * 0.4f), CornerRadius(w))
            val inset = w * 1.4f
            val fillW = (bodyW - 2 * inset) * level
            if (fillW > 0) drawRoundRect(accent, Offset(s * 0.04f + inset, top + inset), Size(fillW, h - 2 * inset), CornerRadius(s * 0.04f))
            if (bolt) {
                val cx = s * 0.04f + bodyW / 2
                val path = Path().apply {
                    moveTo(cx + s * 0.06f, top - s * 0.06f); lineTo(cx - s * 0.12f, top + h * 0.56f); lineTo(cx, top + h * 0.56f)
                    lineTo(cx - s * 0.06f, top + h + s * 0.06f); lineTo(cx + s * 0.12f, top + h * 0.42f); lineTo(cx, top + h * 0.42f); close()
                }
                drawPath(path, color)
            }
        }
        Glyph.POWER -> {
            val r = s * 0.34f
            drawArc(color, -60f, 300f, false, Offset(s / 2 - r, s / 2 - r + s * 0.04f), Size(2 * r, 2 * r), style = line)
            drawLine(color, Offset(s / 2, s * 0.1f), Offset(s / 2, s * 0.46f), w, StrokeCap.Round)
        }
        Glyph.LOCK -> {
            drawRoundRect(color, Offset(s * 0.2f, s * 0.44f), Size(s * 0.6f, s * 0.44f), CornerRadius(s * 0.08f))
            val r = s * 0.19f
            drawArc(color, 180f, 180f, false, Offset(s / 2 - r, s * 0.14f), Size(2 * r, 2 * r), style = line)
            drawLine(color, Offset(s / 2 - r, s * 0.14f + r), Offset(s / 2 - r, s * 0.44f), w)
            drawLine(color, Offset(s / 2 + r, s * 0.14f + r), Offset(s / 2 + r, s * 0.44f), w)
        }
        Glyph.LOGOUT -> {
            val door = Path().apply { moveTo(s * 0.5f, s * 0.15f); lineTo(s * 0.18f, s * 0.15f); lineTo(s * 0.18f, s * 0.85f); lineTo(s * 0.5f, s * 0.85f) }
            drawPath(door, color, style = line)
            drawLine(color, Offset(s * 0.4f, s / 2), Offset(s * 0.86f, s / 2), w, StrokeCap.Round)
            drawLine(color, Offset(s * 0.7f, s * 0.33f), Offset(s * 0.86f, s / 2), w, StrokeCap.Round)
            drawLine(color, Offset(s * 0.7f, s * 0.67f), Offset(s * 0.86f, s / 2), w, StrokeCap.Round)
        }
        Glyph.MOON -> {
            val moon = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(s * 0.14f, s * 0.14f, s * 0.86f, s * 0.86f))
            }
            val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(s * 0.36f, s * 0.02f, s * 1.02f, s * 0.68f)) }
            drawPath(Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, moon, bite), color)
        }
        Glyph.RESTART -> {
            val r = s * 0.32f
            drawArc(color, -30f, 290f, false, Offset(s / 2 - r, s / 2 - r), Size(2 * r, 2 * r), style = line)
            val tip = Offset(s / 2 + r * 0.87f, s / 2 - r * 0.5f)
            drawLine(color, tip, Offset(tip.x - s * 0.2f, tip.y - s * 0.02f), w, StrokeCap.Round)
            drawLine(color, tip, Offset(tip.x + s * 0.02f, tip.y - s * 0.2f), w, StrokeCap.Round)
        }
        Glyph.CHEVRON_LEFT, Glyph.CHEVRON_RIGHT -> {
            val d = if (glyph == Glyph.CHEVRON_LEFT) 1f else -1f
            val p = Path().apply { moveTo(s / 2 + d * s * 0.14f, s * 0.22f); lineTo(s / 2 - d * s * 0.14f, s / 2); lineTo(s / 2 + d * s * 0.14f, s * 0.78f) }
            drawPath(p, color, style = line)
        }
        Glyph.BLUETOOTH -> {
            // The rune: a line, two arrows' tips crossing it.
            val p = Path().apply {
                moveTo(s * 0.28f, s * 0.32f); lineTo(s * 0.68f, s * 0.68f); lineTo(s * 0.5f, s * 0.86f); lineTo(s * 0.5f, s * 0.14f)
                lineTo(s * 0.68f, s * 0.32f); lineTo(s * 0.28f, s * 0.68f)
            }
            drawPath(p, if (slash) faint else color, style = line)
        }
        Glyph.CHECK -> {
            val p = Path().apply { moveTo(s * 0.18f, s * 0.52f); lineTo(s * 0.42f, s * 0.76f); lineTo(s * 0.84f, s * 0.26f) }
            drawPath(p, color, style = Stroke(width = s * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Glyph.MIC -> {
            drawRoundRect(color, Offset(s * 0.36f, s * 0.1f), Size(s * 0.28f, s * 0.48f), CornerRadius(s * 0.14f))
            val r = s * 0.26f
            drawArc(color, 0f, 180f, false, Offset(s / 2 - r, s * 0.32f), Size(2 * r, 2 * r * 0.9f), style = line)
            drawLine(color, Offset(s / 2, s * 0.74f), Offset(s / 2, s * 0.88f), w, StrokeCap.Round)
            if (slash) drawLine(color, Offset(s * 0.16f, s * 0.12f), Offset(s * 0.84f, s * 0.88f), w, StrokeCap.Round)
        }
        Glyph.BRIGHTNESS -> {
            drawCircle(color, radius = s * 0.18f, center = Offset(s / 2, s / 2))
            for (k in 0 until 8) {
                val a = Math.toRadians(45.0 * k)
                val inner = s * 0.3f
                val outer = s * 0.42f
                drawLine(color, Offset(s / 2 + (inner * Math.cos(a)).toFloat(), s / 2 + (inner * Math.sin(a)).toFloat()),
                    Offset(s / 2 + (outer * Math.cos(a)).toFloat(), s / 2 + (outer * Math.sin(a)).toFloat()), w * 0.9f, StrokeCap.Round)
            }
        }
    }
}
