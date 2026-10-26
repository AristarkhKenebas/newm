package newm.bar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.caulis.log.Logger
import org.jetbrains.skia.Data
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM

private val log = Logger("bar")

/** The bar's pictures (Lucide's icons, drawn in the bar's colours, sharp at any scale). */
enum class Glyph {
    WIFI, WIFI_OFF, WIRED, SPEAKER, BATTERY, POWER, LOCK, LOGOUT, MOON, RESTART, CHEVRON_LEFT, CHEVRON_RIGHT,
    BRIGHTNESS, BLUETOOTH, CHECK, MIC, LEAF, BOLT, BALANCE, COFFEE, MENU,
    HEADPHONES, PHONE, MOUSE, KEYBOARD, COMPUTER, MONITOR, GAMEPAD, WATCH, SPEAKER_DEVICE, PRINTER, CAMERA, TABLET,
    CLOSE, COPY, TRASH, SEARCH, SETTINGS, PALETTE, REFRESH, UNPLUGGED, PIN, UNPIN, PICTURE, FILE, CHEVRON_DOWN, GRID, GAUGE, CLIPBOARD, THEME, HIBERNATE,
}

/** The Lucide icon of [glyph] at [level] (0..1), crossed out with [slash]. */
private fun iconName(glyph: Glyph, level: Float, slash: Boolean): String = when (glyph) {
    Glyph.WIFI -> when {
        level > 0.66f -> "wifi"
        level > 0.4f -> "wifi-high"
        level > 0.1f -> "wifi-low"
        else -> "wifi-zero"
    }
    Glyph.WIFI_OFF -> "wifi-off"
    Glyph.WIRED -> "ethernet-port"
    Glyph.SPEAKER -> when {
        slash || level <= 0f -> "volume-x"
        level > 0.6f -> "volume-2"
        level > 0.25f -> "volume-1"
        else -> "volume"
    }
    Glyph.BATTERY -> "battery"
    Glyph.POWER -> "power"
    Glyph.LOCK -> "lock"
    Glyph.LOGOUT -> "log-out"
    Glyph.MOON -> "moon"
    Glyph.RESTART -> "rotate-ccw"
    Glyph.CHEVRON_LEFT -> "chevron-left"
    Glyph.CHEVRON_RIGHT -> "chevron-right"
    Glyph.BRIGHTNESS -> if (level < 0.35f) "sun-dim" else "sun"
    Glyph.BLUETOOTH -> if (slash) "bluetooth-off" else "bluetooth"
    Glyph.CHECK -> "check"
    Glyph.MIC -> if (slash) "mic-off" else "mic"
    Glyph.LEAF -> "leaf"
    Glyph.BOLT -> "zap"
    Glyph.BALANCE -> "scale"
    Glyph.COFFEE -> "coffee"
    Glyph.MENU -> "menu"
    Glyph.HEADPHONES -> "headphones"
    Glyph.PHONE -> "smartphone"
    Glyph.MOUSE -> "mouse"
    Glyph.KEYBOARD -> "keyboard"
    Glyph.COMPUTER -> "laptop"
    Glyph.MONITOR -> "monitor"
    Glyph.GAMEPAD -> "gamepad-2"
    Glyph.WATCH -> "watch"
    Glyph.SPEAKER_DEVICE -> "speaker"
    Glyph.PRINTER -> "printer"
    Glyph.CAMERA -> "camera"
    Glyph.TABLET -> "tablet"
    Glyph.CLOSE -> "x"
    Glyph.COPY -> "copy"
    Glyph.TRASH -> "trash-2"
    Glyph.SEARCH -> "search"
    Glyph.SETTINGS -> "settings"
    Glyph.PALETTE -> "palette"
    Glyph.REFRESH -> "refresh-cw"
    Glyph.UNPLUGGED -> "unplug"
    Glyph.PIN -> "pin"
    Glyph.UNPIN -> "pin-off"
    Glyph.PICTURE -> "image"
    Glyph.FILE -> "file"
    Glyph.CHEVRON_DOWN -> "chevron-down"
    Glyph.GRID -> "layout-grid"
    Glyph.GAUGE -> "gauge"
    Glyph.CLIPBOARD -> "clipboard-list"
    Glyph.THEME -> "sun-moon"
    Glyph.HIBERNATE -> "snowflake"
}

/** The picture for a Bluetooth device's kind (BlueZ's Icon: "audio-headphones", "input-mouse", ...). */
fun glyphOfDevice(icon: String): Glyph = when {
    "headset" in icon || "headphone" in icon || "audio-card" in icon -> Glyph.HEADPHONES
    "speaker" in icon || icon.startsWith("audio") -> Glyph.SPEAKER_DEVICE
    "phone" in icon -> Glyph.PHONE
    "mouse" in icon -> Glyph.MOUSE
    "keyboard" in icon -> Glyph.KEYBOARD
    "gaming" in icon || "joystick" in icon -> Glyph.GAMEPAD
    "computer" in icon -> Glyph.COMPUTER
    "video-display" in icon || "tv" in icon -> Glyph.MONITOR
    "watch" in icon -> Glyph.WATCH
    "printer" in icon -> Glyph.PRINTER
    "camera" in icon -> Glyph.CAMERA
    "tablet" in icon -> Glyph.TABLET
    else -> Glyph.BLUETOOTH
}

// Each icon's SVG text, read once; each drawing of it (colour, size in pixels), kept.
private object Icons {
    private val sources = HashMap<String, String?>()
    private val drawn = object : LinkedHashMap<Triple<String, Int, Int>, ImageBitmap?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Triple<String, Int, Int>, ImageBitmap?>) = size > 300
    }

    fun get(name: String, argb: Int, pixels: Int): ImageBitmap? = drawn.getOrPut(Triple(name, argb, pixels)) { draw(name, argb, pixels) }

    private fun draw(name: String, argb: Int, pixels: Int): ImageBitmap? {
        val source = sources.getOrPut(name) {
            Icons::class.java.getResourceAsStream("/newm/icons/$name.svg")?.use { String(it.readBytes()) }.also { if (it == null) log.warn("No icon $name") }
        } ?: return null
        val hex = "#%06X".format(argb and 0xFFFFFF)
        val alpha = ((argb ushr 24) and 0xFF) / 255f
        val svg = source.replace("currentColor", hex).replace("<svg", "<svg opacity=\"$alpha\"")
        return try {
            val dom = SVGDOM(Data.makeFromBytes(svg.toByteArray()))
            // (Lucide's are 24 units: scaled to the pixels asked for.)
            dom.setContainerSize(24f, 24f)
            val surface = Surface.makeRasterN32Premul(pixels, pixels)
            surface.canvas.scale(pixels / 24f, pixels / 24f)
            dom.render(surface.canvas)
            surface.makeImageSnapshot().toComposeImageBitmap().also { surface.close(); dom.close() }
        } catch (e: Exception) {
            log.warn("The icon $name cannot be drawn: $e")
            null
        }
    }
}

/**
 * [glyph] at [size] in [color]; [level] (0..1) chooses the picture of what has a level
 * (Wi-Fi's strength, the volume) and fills the battery, [slash] crosses it out (muted),
 * [bolt] marks a battery plugged in, [accent] colours the battery's level.
 */
@Composable
fun GlyphIcon(glyph: Glyph, color: Color, size: Dp = 16.dp, level: Float = 1f, slash: Boolean = false, bolt: Boolean = false, accent: Color = color) {
    val l = level.coerceIn(0f, 1f)
    val name = if (glyph == Glyph.BATTERY && bolt) "battery-charging" else iconName(glyph, l, slash)
    val argb = color.toArgb()
    if (glyph == Glyph.BATTERY) return Canvas(Modifier.size(size)) { drawBattery(color, accent, l, bolt) }
    Canvas(Modifier.size(size)) {
        val px = size.toPx().toInt().coerceAtLeast(1)
        Icons.get(name, argb, px)?.let { drawImage(it, dstOffset = IntOffset.Zero, dstSize = IntSize(px, px)) }
    }
}

// The battery, round as the other pictures: a soft body, its level inside in [accent], a small
// cap; plugged in, a bolt in the middle (cut out of the level, so it reads on any colour).
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBattery(color: Color, accent: Color, level: Float, bolt: Boolean) {
    val u = size.minDimension / 24f
    val stroke = 1.8f * u
    val body = androidx.compose.ui.geometry.Rect(2f * u, 7f * u, 19.5f * u, 17f * u)
    val r = 3.2f * u
    drawRoundRect(color, Offset(body.left, body.top), Size(body.width, body.height), CornerRadius(r), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
    drawRoundRect(color, Offset(body.right + 1.2f * u, 10f * u), Size(1.8f * u, 4f * u), CornerRadius(0.9f * u))
    val inset = stroke + 1.1f * u
    val inner = androidx.compose.ui.geometry.Rect(body.left + inset, body.top + inset, body.right - inset, body.bottom - inset)
    if (level > 0f) drawRoundRect(accent, Offset(inner.left, inner.top), Size(maxOf(inner.height * 0.6f, inner.width * level), inner.height), CornerRadius(1.6f * u))
    if (bolt) {
        val cx = body.center.x
        val cy = body.center.y
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx + 1.4f * u, cy - 4.6f * u); lineTo(cx - 2.6f * u, cy + 0.8f * u); lineTo(cx - 0.2f * u, cy + 0.8f * u)
            lineTo(cx - 1.4f * u, cy + 4.6f * u); lineTo(cx + 2.6f * u, cy - 0.8f * u); lineTo(cx + 0.2f * u, cy - 0.8f * u); close()
        }
        // An outline the colour of what is around it, then the bolt.
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(1.4f * u, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawPath(path, if (level > 0.5f) Color.White else color)
    }
}
