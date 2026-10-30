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

/**
 * newm's pictures: Material Symbols (Rounded, filled; Google's, Apache-2.0: see
 * resources/newm/icons/NOTICE), drawn in newm's colours, sharp at any scale.
 */
enum class Glyph {
    WIFI, WIFI_OFF, WIRED, SPEAKER, BATTERY, POWER, LOCK, LOGOUT, MOON, RESTART, CHEVRON_LEFT, CHEVRON_RIGHT,
    BRIGHTNESS, BLUETOOTH, CHECK, MIC, LEAF, BOLT, BALANCE, COFFEE, MENU,
    HEADPHONES, PHONE, MOUSE, KEYBOARD, COMPUTER, MONITOR, GAMEPAD, WATCH, SPEAKER_DEVICE, PRINTER, CAMERA, TABLET,
    CLOSE, COPY, TRASH, SEARCH, SETTINGS, PALETTE, REFRESH, UNPLUGGED, PIN, UNPIN, PICTURE, FILE, CHEVRON_DOWN, GRID, GAUGE, CLIPBOARD, THEME, HIBERNATE,
    BELL, BELL_OFF, TERMINAL, CALCULATOR, PLAY, PAUSE, SKIP_BACK, SKIP_FORWARD, CPU, MEMORY, THERMOMETER, WEATHER, EYE,
}

/** The Material symbol of [glyph] at [level] (0..1), crossed out with [slash]. */
internal fun iconName(glyph: Glyph, level: Float, slash: Boolean): String = when (glyph) {
    Glyph.WIFI -> when {
        level > 0.66f -> "signal_wifi_4_bar"
        level > 0.4f -> "network_wifi_3_bar"
        level > 0.1f -> "network_wifi_1_bar"
        else -> "signal_wifi_0_bar"
    }
    Glyph.WIFI_OFF -> "signal_wifi_off"
    Glyph.WIRED -> "lan"
    Glyph.SPEAKER -> when {
        slash || level <= 0f -> "volume_off"
        level > 0.6f -> "volume_up"
        level > 0.25f -> "volume_down"
        else -> "volume_mute"
    }
    Glyph.BATTERY -> "battery_android_frame"
    Glyph.POWER -> "power_settings_new"
    Glyph.LOCK -> "lock"
    Glyph.LOGOUT -> "logout"
    Glyph.MOON -> "bedtime"
    Glyph.RESTART -> "restart_alt"
    Glyph.CHEVRON_LEFT -> "chevron_left"
    Glyph.CHEVRON_RIGHT -> "chevron_right"
    Glyph.BRIGHTNESS -> when {
        level < 0.35f -> "brightness_low"
        level < 0.7f -> "brightness_medium"
        else -> "brightness_high"
    }
    Glyph.BLUETOOTH -> if (slash) "bluetooth_disabled" else "bluetooth"
    Glyph.CHECK -> "check"
    Glyph.MIC -> if (slash) "mic_off" else "mic"
    Glyph.LEAF -> "energy_savings_leaf"
    Glyph.BOLT -> "bolt"
    Glyph.BALANCE -> "balance"
    Glyph.COFFEE -> "coffee"
    Glyph.MENU -> "menu"
    Glyph.HEADPHONES -> "headphones"
    Glyph.PHONE -> "smartphone"
    Glyph.MOUSE -> "mouse"
    Glyph.KEYBOARD -> "keyboard"
    Glyph.COMPUTER -> "computer"
    Glyph.MONITOR -> "monitor"
    Glyph.GAMEPAD -> "sports_esports"
    Glyph.WATCH -> "watch"
    Glyph.SPEAKER_DEVICE -> "speaker"
    Glyph.PRINTER -> "print"
    Glyph.CAMERA -> "photo_camera"
    Glyph.TABLET -> "tablet"
    Glyph.CLOSE -> "close"
    Glyph.COPY -> "content_copy"
    Glyph.TRASH -> "delete"
    Glyph.SEARCH -> "search"
    Glyph.SETTINGS -> "settings"
    Glyph.PALETTE -> "palette"
    Glyph.REFRESH -> "refresh"
    Glyph.UNPLUGGED -> "power_off"
    Glyph.PIN -> "keep"
    Glyph.UNPIN -> "keep_off"
    Glyph.PICTURE -> "image"
    Glyph.FILE -> "draft"
    Glyph.CHEVRON_DOWN -> "expand_more"
    Glyph.GRID -> "grid_view"
    Glyph.GAUGE -> "speed"
    Glyph.CLIPBOARD -> "content_paste"
    Glyph.THEME -> "contrast"
    Glyph.HIBERNATE -> "ac_unit"
    Glyph.BELL -> "notifications"
    Glyph.BELL_OFF -> "notifications_off"
    Glyph.TERMINAL -> "terminal"
    Glyph.CALCULATOR -> "calculate"
    Glyph.PLAY -> "play_arrow"
    Glyph.PAUSE -> "pause"
    Glyph.SKIP_BACK -> "skip_previous"
    Glyph.SKIP_FORWARD -> "skip_next"
    Glyph.CPU -> "memory"
    Glyph.MEMORY -> "memory_alt"
    Glyph.THERMOMETER -> "thermostat"
    Glyph.WEATHER -> "partly_cloudy_day"
    Glyph.EYE -> "visibility"
}

/**
 * The names newm's icons had before (Lucide's, "sunset", "plane", "mic-off") as Material
 * symbols: a configuration's tile or module icon = "plane" still shows one.
 */
internal val OLD_ICON_NAMES: Map<String, String> = mapOf(
    "battery" to "battery_android_frame", "battery-charging" to "battery_android_frame", "bell" to "notifications", "bell-off" to "notifications_off",
    "bluetooth-connected" to "bluetooth_connected", "bluetooth-off" to "bluetooth_disabled", "bluetooth-searching" to "bluetooth_searching",
    "calculator" to "calculate", "camera" to "photo_camera", "chevron-down" to "expand_more", "chevron-left" to "chevron_left",
    "chevron-right" to "chevron_right", "circle-dot" to "screen_record", "clipboard" to "content_paste", "clipboard-list" to "content_paste",
    "cloud-sun" to "partly_cloudy_day", "copy" to "content_copy", "cpu" to "memory", "ethernet-port" to "lan", "file" to "draft",
    "gamepad-2" to "sports_esports", "gauge" to "speed", "headset" to "headset_mic", "laptop" to "computer", "layout-grid" to "grid_view",
    "leaf" to "energy_savings_leaf", "log-out" to "logout", "memory-stick" to "memory_alt", "mic-off" to "mic_off", "moon" to "bedtime",
    "network" to "lan", "pin" to "keep", "pin-off" to "keep_off", "plane" to "flight", "play" to "play_arrow", "plug-zap" to "bolt",
    "power" to "power_settings_new", "printer" to "print", "refresh-cw" to "refresh", "rotate-ccw" to "restart_alt", "scale" to "balance",
    "skip-back" to "skip_previous", "skip-forward" to "skip_next", "snowflake" to "ac_unit", "sun" to "light_mode", "sun-dim" to "brightness_low",
    "sun-moon" to "contrast", "sunset" to "nightlight", "thermometer" to "thermostat", "trash-2" to "delete", "unplug" to "power_off",
    "volume" to "volume_mute", "volume-1" to "volume_down", "volume-2" to "volume_up", "volume-x" to "volume_off", "wifi-high" to "network_wifi_3_bar",
    "wifi-low" to "network_wifi_1_bar", "wifi-off" to "wifi_off", "wifi-zero" to "signal_wifi_0_bar", "x" to "close", "zap" to "bolt",
    "image" to "image", "monitor" to "monitor", "smartphone" to "smartphone", "tablet" to "tablet", "watch" to "watch", "speaker" to "speaker",
)

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
            // At its own size (Material's 960 units in a viewBox that starts above), scaled to the
            // pixels asked for.
            val root = dom.root
            val box = root?.viewBox
            // (Its width and height when it gives them — its viewBox is fitted into those — else the viewBox.)
            val w = root?.width?.takeIf { it.unit != org.jetbrains.skia.svg.SVGLengthUnit.PERCENTAGE && it.value > 0f }?.value ?: box?.width?.takeIf { it > 0f } ?: 24f
            val h = root?.height?.takeIf { it.unit != org.jetbrains.skia.svg.SVGLengthUnit.PERCENTAGE && it.value > 0f }?.value ?: box?.height?.takeIf { it > 0f } ?: 24f
            dom.setContainerSize(w, h)
            val surface = Surface.makeRasterN32Premul(pixels, pixels)
            surface.canvas.scale(pixels / maxOf(w, h), pixels / maxOf(w, h))
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
    val name = "material/" + iconName(glyph, l, slash)
    val argb = color.toArgb()
    Canvas(Modifier.size(size)) {
        val px = size.toPx().toInt().coerceAtLeast(1)
        Icons.get(name, argb, px)?.let { drawImage(it, dstOffset = IntOffset.Zero, dstSize = IntSize(px, px)) }
        if (glyph == Glyph.BATTERY) drawBatteryLevel(color, accent, l, bolt, px)
    }
}

// The battery's level inside Material's frame (battery_android_frame: its room 160..700 by
// -600..-360 of 960 units), in [accent]; plugged in, Material's bolt over it (white on a high
// level, else the frame's colour, outlined so it reads on any colour).
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBatteryLevel(color: Color, accent: Color, level: Float, bolt: Boolean, px: Int) {
    val u = px / 960f
    val top = (960f - 600f) * u
    val height = 240f * u
    if (level > 0f) {
        val width = maxOf(60f, 540f * level) * u
        drawRoundRect(accent, Offset(160f * u, top), Size(width, height), CornerRadius(40f * u))
    }
    if (bolt) {
        val inside = (height * 1.5f).toInt().coerceAtLeast(1)
        val at = IntOffset(((430f * u) - inside / 2f).toInt(), ((960f - 480f) * u - inside / 2f).toInt())
        Icons.get("material/bolt", color.toArgb(), inside)?.let { drawImage(it, dstOffset = at, dstSize = IntSize(inside, inside)) }
        val mark = if (level > 0.5f) Color.White else accent
        val small = (inside * 0.8f).toInt().coerceAtLeast(1)
        val off = IntOffset(at.x + (inside - small) / 2, at.y + (inside - small) / 2)
        Icons.get("material/bolt", mark.toArgb(), small)?.let { drawImage(it, dstOffset = off, dstSize = IntSize(small, small)) }
    }
}

/**
 * A Material Symbols icon (Rounded, filled: Android's own, bold and soft) of the ones newm
 * carries ("wifi", "bluetooth", "coffee", "dark_mode", ...; a name it had before too, "plane"),
 * at [size] in [color].
 */
@Composable
fun SymbolIcon(name: String, color: Color, size: Dp = 20.dp) {
    val argb = color.toArgb()
    Canvas(Modifier.size(size)) {
        val px = this.size.minDimension.toInt().coerceAtLeast(1)
        Icons.get("material/${OLD_ICON_NAMES[name] ?: name}", argb, px)?.let { drawImage(it, dstOffset = IntOffset.Zero, dstSize = IntSize(px, px)) }
    }
}

/** newm carries icon [name] ("material/wifi"). */
internal fun carriesIcon(name: String): Boolean = carried.getOrPut(name) { Icons::class.java.getResource("/newm/icons/$name.svg") != null }

private val carried = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
