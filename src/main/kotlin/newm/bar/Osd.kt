package newm.bar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.caulis.compose.compose
import dev.caulis.core.Monitor
import dev.caulis.geometry.Rect
import kotlinx.coroutines.delay

/** What the on-screen display shows: a level (volume, brightness) with its picture, or words alone (a toast). */
data class OsdShow(val glyph: Glyph?, val title: String, val level: Double?, val off: Boolean, val serial: Long)

private var osdSerial = 0L

/** Shows a level at the bottom of the screen ([glyph]: the volume's speaker, the brightness' sun). */
fun BarModel.showLevel(glyph: Glyph, title: String, level: Double?, off: Boolean = false) {
    osd = OsdShow(glyph, title, level, off, ++osdSerial)
}

/** A short line at the bottom of the screen, words alone (what a gesture does). */
fun BarModel.hintPill(text: String) {
    osd = OsdShow(null, tr(text), null, false, ++osdSerial)
}

/** A short message in the middle of the screen, words alone ("Connected to Redmi Buds"), gone by itself. */
fun BarModel.toast(text: String) {
    toast = OsdShow(null, tr(text), null, false, ++osdSerial)
}

/** The parts of the screen the on-screen display and the toasts are drawn in. */
fun BarModel.showOsd(): AutoCloseable {
    val caulis = wm.caulis
    fun target(m: Monitor): Boolean {
        val d = caulis.desktop
        val pointed = d.monitors.firstOrNull { it.area.contains(Rect(d.pointerX.toInt(), d.pointerY.toInt(), 1, 1)) } ?: d.monitors.firstOrNull()
        return m === pointed
    }
    val levels = caulis.compose(place = { m ->
        if (osd == null || !target(m)) null else Rect(m.area.x + (m.area.width - 340) / 2, m.area.y + m.area.height - 130, 340, 84)
    }) { _ -> osd?.let { OsdPill(this, it) } }
    val toasts = caulis.compose(place = { m ->
        if (toast == null || !target(m)) null else Rect(m.area.x + (m.area.width - 460) / 2, m.area.y + (m.area.height * 0.42).toInt(), 460, 96)
    }) { _ -> toast?.let { ToastCard(this, it) } }
    // Caulis's own pill (the laptop's keys) is newm's now.
    caulis.onScreenDisplay { title, level, off ->
        val glyph = when {
            title.startsWith("Brightness") -> Glyph.BRIGHTNESS
            title.startsWith("Microphone") -> Glyph.MIC
            title.startsWith("Volume") || title == "Muted" -> Glyph.SPEAKER
            else -> null
        }
        caulis.post { if (glyph != null) showLevel(glyph, title, level, off) else toast(title) }
        true
    }
    return AutoCloseable {
        levels.close()
        toasts.close()
    }
}

@Composable
private fun OsdPill(model: BarModel, show: OsdShow) {
    val colors = model.colorsNow()
    val appear = remember { Animatable(0f) }
    LaunchedEffect(show.serial) {
        appear.animateTo(1f, tween(140))
        delay(1300)
        appear.animateTo(0f, tween(220))
        if (model.osd?.serial == show.serial) model.osd = null
    }
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.fillMaxSize().padding(12.dp)
            .softShadow(22.dp, 10.dp, shown = { appear.value })
            .graphicsLayer { alpha = appear.value; translationY = (1 - appear.value) * 12f * density }
            .background(colors.island.copy(alpha = 0.96f), shape)
            .border(1.dp, colors.edge, shape)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            show.glyph?.let { GlyphIcon(it, if (show.off) colors.dim else colors.text, 20.dp, level = (show.level ?: 1.0).toFloat(), slash = show.off) }
            Spacer(Modifier.width(14.dp))
            val level = show.level
            if (level != null) {
                val shown = level.coerceIn(0.0, 1.0).toFloat()
                Canvas(Modifier.width(190.dp).height(6.dp)) {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(colors.text.copy(alpha = 0.14f), Offset.Zero, size, r)
                    drawRoundRect(if (show.off) colors.dim else colors.accent, Offset.Zero, Size(size.width * shown, size.height), r)
                }
                Spacer(Modifier.width(12.dp))
                BasicText(if (show.off) "off" else "${Math.round(level * 100)}", style = TextStyle(color = colors.dim, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier.width(34.dp))
            } else {
                BasicText(show.title, style = TextStyle(color = colors.text, fontSize = 13.sp))
            }
        }
    }
}

@Composable
private fun ToastCard(model: BarModel, show: OsdShow) {
    val colors = model.colorsNow()
    val appear = remember { Animatable(0f) }
    LaunchedEffect(show.serial) {
        appear.animateTo(1f, tween(180))
        delay(1600)
        appear.animateTo(0f, tween(260))
        if (model.toast?.serial == show.serial) model.toast = null
    }
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.fillMaxSize().padding(14.dp)
            .softShadow(18.dp, 12.dp, shown = { appear.value })
            .graphicsLayer { alpha = appear.value; scaleX = 0.94f + 0.06f * appear.value; scaleY = scaleX }
            .background(colors.island.copy(alpha = 0.96f), shape)
            .border(1.dp, colors.edge, shape)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(show.title, style = TextStyle(color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center), maxLines = 2)
    }
}
