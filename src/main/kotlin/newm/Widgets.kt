package newm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.caulis.core.Drawing
import dev.caulis.core.DrawingLevel
import dev.caulis.core.Monitor
import dev.caulis.core.OverlayCanvas
import dev.caulis.geometry.Rect
import dev.caulis.log.Logger
import dev.caulis.render.GpuTexture
import dev.caulis.render.Renderer
import java.awt.image.BufferedImage
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private val log = Logger("widgets")

/** A picture on the GPU, from [image] (BGRA, alpha premultiplied, as Caulis draws them). */
fun textureOf(renderer: Renderer, image: BufferedImage): GpuTexture {
    val w = image.width
    val h = image.height
    // Java2D turns any picture into premultiplied ARGB ints in one go (its own fast loops, not a
    // colour model asked pixel by pixel: a wallpaper took half a second of the compositor's
    // thread). An ARGB int in this little-endian memory is B, G, R, A: as the GPU takes it.
    val pre = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE)
    val g = pre.createGraphics()
    try {
        g.composite = java.awt.AlphaComposite.Src
        g.drawImage(image, 0, 0, null)
    } finally {
        g.dispose()
    }
    val ints = (pre.raster.dataBuffer as java.awt.image.DataBufferInt).data
    val texture = renderer.createTexture(w, h)
    Arena.ofConfined().use { arena ->
        val pixels = arena.allocate(w.toLong() * h * 4, 4)
        MemorySegment.copy(ints, 0, pixels, ValueLayout.JAVA_INT_UNALIGNED.withOrder(java.nio.ByteOrder.LITTLE_ENDIAN), 0, w * h)
        texture.upload(pixels, 0, h)
    }
    return texture
}

// --- the background --------------------------------------------------------------------------------

private val confOutputs = configuredValue<List<Map<String, Any?>>>("outputs", emptyList())
private val confTimeScale = configuredValue("background.time_scale", 0.15)
private val confAnimDefault = configuredValue("background.anim", true)
// background { mode = "follow" (moves and zooms with the view, newm's) | "static" (stands still, the whole screen) }
private val confBackgroundMode = configuredValue<String?>("background.mode", null)

/**
 * newm's BackgroundState: which part of the wallpaper an output shows — the workspace's
 * extent is the whole picture, the view in it a part, so moving and zooming the view moves
 * the wallpaper a little (parallax).
 */
class BackgroundState(layoutState: LayoutState, wsState: WorkspaceState, wallpaperSize: Pair<Int, Int>, outputSize: Pair<Double, Double>, outputScale: Double) {
    var box: DoubleArray
    var opacity: Double

    init {
        val extent = wsState.extent()
        val x1 = extent[0] - 1
        val y1 = extent[1] - 1
        val x2 = extent[2] + 1 + 1
        val y2 = extent[3] + 1 + 1
        var vx = wsState.i
        var vy = wsState.j
        var vw = wsState.size
        var vh = wsState.size
        if (vx < x1) {
            vw -= (x1 - vx)
            vx = x1
        }
        if (vy < y1) {
            vy -= (y1 - vy) // (as newm has it)
            vy = y1
        }
        if (vx + vw > x2) vw = x2 - vx
        if (vy + vh > y2) vh = y2 - vy

        val ex = x1
        val ey = y1
        val ew = x2 - x1
        val eh = y2 - y1
        opacity = layoutState.backgroundOpacity

        // 1., 2.: the viewport within [0, 1] x [0, 1].
        vx = (vx - ex) / ew
        vy = (vy - ey) / eh
        vw /= ew
        vh /= eh
        // 3.
        vw = min(1.0, vw)
        vh = min(1.0, vh)
        vx = max(0.0, min(1 - vw, vx))
        vy = max(0.0, min(1 - vh, vy))
        // 4.: within the picture's pixels.
        val (width, height) = wallpaperSize.first.toDouble() to wallpaperSize.second.toDouble()
        val (outputWidth, outputHeight) = outputSize
        vx *= width
        vy *= height
        vw *= width
        vh *= height

        val w0 = width / ew
        val h0 = height / eh
        val w1 = outputWidth * outputScale
        val h1 = outputHeight * outputScale
        var vxp: Double
        var vyp: Double
        var vwp: Double
        var vhp: Double
        if (abs(w0 - width) > 0.1 && abs(h0 - height) > 0.1 && width > w1 && height > h1) {
            // 5a., 5b.
            vwp = width + (w1 - width) / (w0 - width) * (vw - width)
            vhp = height + (h1 - height) / (h0 - height) * (vh - height)
            if (abs(vw - vwp) < 0.1 || abs(vh - vhp) < 0.1) {
                vxp = vx
                vyp = vy
            } else {
                vxp = (width - vwp) / (width - vw) * vx
                vyp = (height - vhp) / (height - vh) * vy
            }
        } else {
            vxp = vx
            vyp = vy
            vwp = vw
            vhp = vh
        }

        // 6.: the output's aspect ratio.
        var x = vxp
        var y = vyp
        var w = vwp
        var h = vhp
        if (w / h < outputWidth / outputHeight) {
            val newH = outputHeight * w / outputWidth
            y -= (newH - h) / 2.0
            h = newH
        } else {
            val newW = outputWidth * h / outputHeight
            x -= (newW - w) / 2.0
            w = newW
        }
        // (A safety net: should not be needed.)
        var scaleFac = 1.0
        if (w > width) scaleFac = width / w
        if (h > height) scaleFac = min(scaleFac, height / h)
        w *= scaleFac
        h *= scaleFac
        if (x < 0) x = 0.0
        if (y < 0) y = 0.0
        if (x + w > width) x = width - w
        if (y + h > height) y = height - h

        // 7.: in output coordinates.
        val fx = -x * outputWidth / w
        val fy = -y * outputHeight / h
        val fw = width * outputWidth / w
        val fh = height * outputHeight / h
        box = doubleArrayOf(fx, fy, ceil(fw), ceil(fh))
    }

    fun setMax(wallpaperSize: Pair<Int, Int>, outputSize: Pair<Double, Double>) {
        opacity = 1.0
        var x = 0.0
        var y = 0.0
        var w = outputSize.first
        var h = outputSize.second
        if (w / h > wallpaperSize.first.toDouble() / wallpaperSize.second) {
            val newH = wallpaperSize.second * w / wallpaperSize.first
            y -= (newH - h) / 2.0
            h = newH
        } else {
            val newW = wallpaperSize.first * h / wallpaperSize.second
            x -= (newW - w) / 2.0
            w = newW
        }
        box = doubleArrayOf(x, y, ceil(w), ceil(h))
    }

    fun delta(other: BackgroundState): Double =
        (0 until 4).sumOf { abs(box[it] - other.box[it]) } + 1000 * abs(opacity - other.opacity)

    fun approach(other: BackgroundState, timeScale: Double, dt: Double) {
        val factor = min(1.0, dt / timeScale)
        opacity += (other.opacity - opacity) * factor
        for (k in 0 until 4) box[k] += (other.box[k] - box[k]) * factor
    }

    override fun toString() = "<BackgroundState box=${box.toList()} opacity=$opacity>"
}

/** newm's Background widget: the wallpaper of one output, gliding behind the view's moves. */
class Background(private val wm: Layout, val monitor: Monitor, private val workspace: Workspace, private val picture: Wallpaper) : Animatable {
    private var preventAnim = when (confBackgroundMode()?.lowercase()) {
        "static", "still", "fixed" -> true
        "follow", "move", "parallax" -> workspace.preventAnim
        else -> !confAnimDefault() || workspace.preventAnim
    }
    private val size = picture.width to picture.height
    private val output get() = monitor.area.width.toDouble() to monitor.area.height.toDouble()
    private var current = state(wm.state)
    private var target = state(wm.state)
    private var lastFrame = 0.0
    private var animCaught: Double? = null

    init {
        for (o in confOutputs()) {
            if (o["name"] == monitor.name) {
                val bg = o["background"] as? Map<*, *>
                if (bg?.get("anim") == false) preventAnim = true
            }
        }
        if (preventAnim) current.setMax(size, output)
    }

    private fun state(s: LayoutState) = BackgroundState(s, s.workspaceState(workspace), size, output, monitor.scale)

    override fun animate(oldState: LayoutState, newState: LayoutState, dt: Double, curve: dev.caulis.core.Curve) {
        if (preventAnim) return
        animCaught = -dt
        target = state(newState)
        wm.damage()
    }

    override fun flushAnimation() {
        animCaught = null
    }

    /** True while it glides towards where the view says, or fades in. */
    val moving: Boolean get() = (!preventAnim && current.delta(target) > 1) || picture.fading

    fun draw(canvas: OverlayCanvas) {
        if (!preventAnim) {
            val t = now()
            val caught = animCaught
            if (caught == null) {
                val next = state(wm.state)
                if (next.delta(target) > 1) target = next
            } else if (caught < 0) {
                animCaught = t - 1.0 / 120.0 - caught
                lastFrame = t - 1.0 / 120.0
            }
            // (Gliding: backgroundMove, background.time_scale its usual time; off, right where the view says.)
            val glide = NewmAnimations.resolveSeconds("backgroundMove", confTimeScale())
            if (current.delta(target) > 1 && glide.on) {
                current.approach(target, glide.seconds, t - lastFrame)
            } else if (current !== target) {
                current = target
            }
            lastFrame = t
        }
        val b = current.box
        val rect = Rect(monitor.area.x + Math.round(b[0]).toInt(), monitor.area.y + Math.round(b[1]).toInt(), Math.round(b[2]).toInt(), Math.round(b[3]).toInt())
        canvas.image(picture.texture, rect, current.opacity.toFloat() * picture.appearing())
    }
}

/** A wallpaper picture on the GPU ("resource:wallpaper.jpg": newm's own). */
class Wallpaper(val texture: GpuTexture, val width: Int, val height: Int) {
    // When it was first shown (System.nanoTime): it fades in from then, once (not again on a reload).
    private var shownAt = 0L

    /** How far it has faded in (0..1, along backgroundIn's curve; 600 ms, fast at first, usually). */
    fun appearing(): Float {
        if (shownAt == 0L) shownAt = System.nanoTime()
        val fade = NewmAnimations["backgroundIn"]
        if (!fade.on) return 1f
        return fade.at((System.nanoTime() - shownAt) / (fade.millis * 1_000_000.0)).toFloat().coerceIn(0f, 1f)
    }

    /** Still fading in. */
    val fading: Boolean get() = shownAt == 0L || System.nanoTime() - shownAt < NewmAnimations["backgroundIn"].effectiveMillis * 1_000_000L

    companion object {
        // The picture read from [path] (slow for a big one: not on the compositor's thread). Decoded
        // by Skia into native memory ([WallpaperImage]); Java's own reader only for what Skia cannot read.
        fun read(path: String): BufferedImage? = try {
            val t0 = System.nanoTime()
            val bytes = if (path.startsWith("resource:")) {
                Wallpaper::class.java.getResourceAsStream("/newm/templates/${path.removePrefix("resource:")}")?.use { it.readAllBytes() }
            } else {
                java.io.File(expandHome(path)).readBytes()
            } ?: error("not found")
            val svg = path.lowercase().endsWith(".svg")
            val image = (if (svg) svg(bytes) else decoded(bytes)) ?: javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes)) ?: error("not a picture")
            val (w, h) = (image as? WallpaperImage)?.let { it.fullWidth to it.fullHeight } ?: (image.width to image.height)
            log.info { "Wallpaper $path: ${w}x$h, read in ${(System.nanoTime() - t0) / 1_000_000} ms" }
            image
        } catch (e: Exception) {
            log.warn("The wallpaper $path cannot be shown: $e")
            null
        }

        // The long side an SVG wallpaper is drawn at: sharp on a 4K screen and when zoomed in a little (newm issue 86).
        private const val SVG_LONG_SIDE = 5120

        // [bytes] (JPEG, PNG, WebP, GIF, BMP) decoded by Skia, or null if it cannot.
        private fun decoded(bytes: ByteArray): WallpaperImage? {
            val image = runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
            try {
                val bitmap = org.jetbrains.skia.Bitmap()
                if (!bitmap.allocPixels(org.jetbrains.skia.ImageInfo.makeN32Premul(image.width, image.height)) || !image.readPixels(bitmap)) {
                    bitmap.close()
                    return null
                }
                return WallpaperImage.of(bitmap)
            } finally {
                image.close()
            }
        }

        // An SVG drawn by Skia at [SVG_LONG_SIDE] pixels along its long side, in its own proportions.
        private fun svg(bytes: ByteArray): BufferedImage? {
            val dom = org.jetbrains.skia.svg.SVGDOM(org.jetbrains.skia.Data.makeFromBytes(bytes))
            val root = dom.root ?: return null
            val viewBox = root.viewBox
            var w = viewBox?.width ?: root.width.value
            var h = viewBox?.height ?: root.height.value
            if (w <= 0f || h <= 0f) { w = 16f; h = 9f }
            val scale = SVG_LONG_SIDE / maxOf(w, h)
            val width = (w * scale).toInt().coerceAtLeast(1)
            val height = (h * scale).toInt().coerceAtLeast(1)
            dom.setContainerSize(width.toFloat(), height.toFloat())
            val bitmap = org.jetbrains.skia.Bitmap()
            if (!bitmap.allocPixels(org.jetbrains.skia.ImageInfo.makeN32Premul(width, height))) {
                bitmap.close()
                return null
            }
            org.jetbrains.skia.Canvas(bitmap).use { canvas ->
                canvas.clear(0xFF000000.toInt())
                dom.render(canvas)
            }
            dom.close()
            return WallpaperImage.of(bitmap)
        }

        /** The picture of [image] on the GPU (on the compositor's thread). */
        fun of(renderer: Renderer, image: BufferedImage): Wallpaper {
            if (image is WallpaperImage) image.texture(renderer)?.let { return Wallpaper(it, image.fullWidth, image.fullHeight) }
            return Wallpaper(textureOf(renderer, image), image.width, image.height)
        }
    }
}

/**
 * A wallpaper as [Wallpaper.read] read it: its pixels decoded by Skia into native memory (BGRA,
 * premultiplied: as the GPU takes them) — not through Java's heap: Java's JPEG reader went
 * through ~300 MB of heap for newm's own 3674x2454 picture, Java's heap grew that big and
 * stayed so for a minute; and turning it into the GPU's order took the compositor's thread for
 * a moment. As a picture it is only a small copy of itself ([THUMB] pixels along its long side):
 * enough for the colours made from it (Palette.seedOf looks at ~96 x 96 points of it).
 */
class WallpaperImage private constructor(
    private var pixels: org.jetbrains.skia.Bitmap?,
    val fullWidth: Int,
    val fullHeight: Int,
    width: Int,
    height: Int,
) : BufferedImage(width, height, TYPE_INT_RGB) {

    /** Its pixels on the GPU (then let go: once only), or null if they are gone. */
    internal fun texture(renderer: Renderer): GpuTexture? {
        val bitmap = pixels ?: return null
        pixels = null
        try {
            val pixmap = bitmap.peekPixels() ?: return null
            val texture = renderer.createTexture(fullWidth, fullHeight)
            val rowBytes = pixmap.rowBytes.toLong()
            val all = MemorySegment.ofAddress(pixmap.addr).reinterpret(rowBytes * fullHeight)
            if (rowBytes == fullWidth * 4L) {
                texture.upload(all, 0, fullHeight)
            } else {
                // (Rows with room after them: one at a time.)
                for (y in 0 until fullHeight) texture.upload(all.asSlice(y * rowBytes, fullWidth * 4L), y, 1)
            }
            return texture
        } finally {
            bitmap.close()
        }
    }

    companion object {
        /** The long side of the small copy. */
        const val THUMB = 192

        internal fun of(bitmap: org.jetbrains.skia.Bitmap): WallpaperImage {
            val w = bitmap.width
            val h = bitmap.height
            val s = minOf(1.0, THUMB.toDouble() / maxOf(w, h))
            val tw = (w * s).toInt().coerceAtLeast(1)
            val th = (h * s).toInt().coerceAtLeast(1)
            val image = WallpaperImage(bitmap, w, h, tw, th)
            // (Not changed any more: Skia then looks at its pixels instead of copying them.)
            bitmap.setImmutable()
            // The small copy: Skia scales it down (smoothly), Java keeps it as RGB.
            val small = org.jetbrains.skia.Bitmap()
            try {
                if (small.allocPixels(org.jetbrains.skia.ImageInfo.makeN32Premul(tw, th))) {
                    org.jetbrains.skia.Image.makeFromBitmap(bitmap).use { full ->
                        small.peekPixels()?.let { full.scalePixels(it, org.jetbrains.skia.FilterMipmap(org.jetbrains.skia.FilterMode.LINEAR, org.jetbrains.skia.MipmapMode.NONE), false) }
                    }
                    small.readPixels()?.let { bytes ->
                        val ints = IntArray(tw * th)
                        java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(ints)
                        image.setRGB(0, 0, tw, th, ints, 0, tw)
                    }
                }
            } finally {
                small.close()
            }
            return image
        }
    }
}

// --- the screen's corners ----------------------------------------------------------------------------

private val confCornerRadius = configuredValue("corner_radius", 18)

/** newm's Corner widgets: each output's corners rounded off in black, above everything. */
class Corners(private val wm: Layout) {
    // The four corners at a size in pixels: top-left, top-right, bottom-left, bottom-right.
    private var textures: Pair<Int, List<GpuTexture>>? = null

    private fun texturesFor(pixels: Int): List<GpuTexture> {
        textures?.let { (p, list) -> if (p == pixels) return list else list.forEach(GpuTexture::close) }
        val list = listOf(true to true, false to true, true to false, false to false).map { (left, top) ->
            val image = BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_ARGB)
            // The circle's middle is the corner's inner point: outside it, black.
            val cx = if (left) pixels.toDouble() else 0.0
            val cy = if (top) pixels.toDouble() else 0.0
            for (y in 0 until pixels) for (x in 0 until pixels) {
                val dx = x + 0.5 - cx
                val dy = y + 0.5 - cy
                val d = Math.sqrt(dx * dx + dy * dy)
                val a = (d - pixels + 0.5).coerceIn(0.0, 1.0)
                image.setRGB(x, y, (Math.round(a * 255).toInt() shl 24))
            }
            textureOf(wm.caulis.desktop.renderer, image)
        }
        textures = pixels to list
        return list
    }

    fun draw(monitor: Monitor, canvas: OverlayCanvas) {
        val r = confCornerRadius()
        if (r <= 0) return
        val list = texturesFor(Math.round(monitor.scale * r).toInt())
        val a = monitor.area
        canvas.image(list[0], Rect(a.x, a.y, r, r))
        canvas.image(list[1], Rect(a.right - r, a.y, r, r))
        canvas.image(list[2], Rect(a.x, a.bottom - r, r, r))
        canvas.image(list[3], Rect(a.right - r, a.bottom - r, r, r))
    }
}

// A colour of the configuration, or [fallback] when it is not one.
private fun colorOr(text: String, fallback: String): dev.caulis.render.Color = try {
    color(text)
} catch (_: IllegalArgumentException) {
    log.warn("\"$text\" is not a colour (#RRGGBB or #RRGGBBAA)")
    color(fallback)
}

// --- the native bars ----------------------------------------------------------------------------------

// A setting of newm's own top or bottom bar (panels.top_bar.native.*, panels.bottom_bar.native.*).
private class BarSetting<T>(name: String, default: T) {
    val top = configuredValue("panels.top_bar.native.$name", default)
    val bottom = configuredValue("panels.bottom_bar.native.$name", default)
    operator fun invoke(isTop: Boolean): T = if (isTop) top() else bottom()
}

private val confHeight = BarSetting("height", 32)
private val confFontSize = BarSetting("font_size", 13)
private val confTexts = BarSetting<() -> List<String>>("texts") { emptyList() }
private val confFont = BarSetting<String?>("font", null)
private val confBackground = BarSetting("background", "#000000B3")
private val confForeground = BarSetting("foreground", "#FFFFFF")
private val confBlur = BarSetting("blur", true)
private val confStyle = BarSetting("style", "islands")
private val confMargin = BarSetting("margin", 6)
private val confRadius = BarSetting("corner_radius", 12)
private val confTheme = BarSetting("theme", "auto")
private val confLeft = BarSetting("left", listOf("window"))
private val confCenter = BarSetting("center", listOf("clock"))
private val confRight = BarSetting("right", listOf("tray", "layout", "bluetooth", "network", "volume", "battery", "notifications", "power"))
private val confClock = BarSetting("clock", "HH:mm")
private val confDate = BarSetting("date", "EEE d MMM")
private val confIslandColor = BarSetting<String?>("island_color", null)
private val confTextColor = BarSetting<String?>("text_color", null)
private val confAccentColor = BarSetting<String?>("accent_color", null)
private val confIconSize = BarSetting("icon_size", 16)
private val confShowPercent = BarSetting("show_percent", true)
private val confShowPassive = BarSetting("show_passive", false)
private val confTitleWidth = BarSetting("title_width", 360)
private val confTrayHide = BarSetting("tray_hide", listOf("nm-applet", "blueman"))
private val confMenuButton = BarSetting("menu_button", true)
private val confQuickButton = BarSetting("quick_button", true)
private val confNotificationsButton = BarSetting("notifications_button", true)
// layoutNames = mapOf("us" to "EN"): the keyboard layouts' labels at the bar (by the layout's name, or its code).
private val confLayoutNames = BarSetting<Map<String, Any?>>("layout_names", emptyMap())
// outputs = listOf("eDP-1"): the monitors with the bar (empty: all).
private val confBarOutputs = BarSetting("outputs", emptyList<String>())
// clockHint: the clock's hint (java.time's pattern).
private val confClockHint = BarSetting("clock_hint", "EEEE, d MMMM yyyy")
// hintDelayMs: how long the pointer stays on a part of the bar before its hint shows.
private val confHintDelay = BarSetting("hint_delay_ms", 800)
// module("name") { }: modules of one's own, by name (their text, icon, what clicks do).
private val confModules = BarSetting<Map<String, Any?>>("modules", emptyMap())
// on("window") { middle { } }: what a click, the middle and right buttons, the wheel do on a module (instead of newm's).
private val confOn = BarSetting<Map<String, Any?>>("on", emptyMap())

// The configuration's label of layout [name] ("us", "de(neo)"), else "us" -> "EN", "ru" -> "RU", "de(neo)" -> "DE".
internal fun layoutLabel(name: String, labels: Map<String, Any?>): String {
    val code = name.substringBefore('(').trim().lowercase()
    (labels[name] ?: labels[code])?.let { return it.toString() }
    return when (code) {
        "us", "gb" -> "EN"
        else -> code.take(2).uppercase()
    }
}

// The configuration wrote the bar's list [name] itself ("left", "right" of the top or bottom bar).
private fun listWritten(top: Boolean, name: String) = Config.lookup("panels.${if (top) "top" else "bottom"}_bar.native.$name") != null

/**
 * The right island's modules: [right] with the quick actions' button before the power button
 * and the notifications' bell before that ([quick], [bell]: added) — also in a list of one's
 * own (the quick actions are where the sound, the brightness, Wi-Fi and the rest are reached:
 * a configuration from before them went without them) — unless placed elsewhere already.
 */
internal fun rightModules(right: List<String>, left: List<String>, center: List<String>, quick: Boolean, bell: Boolean): List<String> {
    val withQuick = if (!quick || "quick" in right || "quick" in left || "quick" in center) right
    else right.indexOf("power").let { at -> if (at < 0) right + "quick" else right.take(at) + "quick" + right.drop(at) }
    return if (!bell || "notifications" in withQuick || "notifications" in left || "notifications" in center) withQuick
    else (withQuick.indexOf("quick").takeIf { it >= 0 } ?: withQuick.indexOf("power")).let { at ->
        if (at < 0) withQuick + "notifications" else withQuick.take(at) + "notifications" + withQuick.drop(at)
    }
}

/** The room newm's own bar keeps at the top ([top]) or bottom: its height, and the gaps around islands. */
internal fun nativeBarRoom(top: Boolean): Int {
    val height = confHeight(top).coerceAtLeast(12)
    return if (confStyle(top) != "classic") height + 2 * confMargin(top).coerceAtLeast(0) else height
}

/**
 * newm's own bar at the top or bottom of an output, sliding in and out with the workspace
 * (gone in fullscreen, there in the overview). Two looks: "islands" (newm's: the wall, the
 * window, the clock, the tray and the computer's state, see newm.bar) and "classic" (the
 * original newm's: texts spread over a strip).
 */
class Bar(private val wm: Layout, val monitor: Monitor, val top: Boolean) : Animatable {
    private val workspace = wm.workspaces.firstOrNull { monitor in it.outputs } ?: wm.workspaces[0]

    /** The height of the bar (an island's, in the islands look). */
    val height: Int get() = confHeight(top).coerceAtLeast(12)
    val fontSize: Int get() = confFontSize(top)
    val font: String? get() = confFont(top)
    val fontFamily: FontFamily get() = newm.bar.fontFamilyOf(font)
    val background: dev.caulis.render.Color get() = colorOr(confBackground(top), "#000000B3")
    val foreground: dev.caulis.render.Color get() = colorOr(confForeground(top), "#FFFFFF")

    /** What is behind it is blurred. */
    val blur: Boolean get() = confBlur(top)

    /** "islands" (newm's) or "classic" (the original newm's). */
    val islands: Boolean get() = confStyle(top) != "classic"

    /** The gap between the islands and the screen's edges. */
    val margin: Int get() = if (islands) confMargin(top).coerceAtLeast(0) else 0
    val cornerRadius: Int get() = confRadius(top)

    /** "auto" (the colour scheme of programs), "light" or "dark". */
    val theme: String get() = confTheme(top)
    val islandColor: String? get() = confIslandColor(top)
    val textColor: String? get() = confTextColor(top)
    val accentColor: String? get() = confAccentColor(top)

    /**
     * The modules of each island ("wall", "window", "clock", "tray", "network", "volume", "battery", "notifications", "power", "texts").
     * The menu's button is added to newm's own left list only (a left list the configuration
     * writes is shown as written). The quick actions' and notifications' buttons are always
     * there — also in a right list of one's own written before they existed — unless placed
     * elsewhere or turned off (topBar { quickButton = false; notificationsButton = false }).
     */
    val left: List<String> get() = confLeft(top).let { left ->
        // The menu's button (three lines) first, unless it is placed elsewhere or turned off.
        if (confMenuButton(top) && !listWritten(top, "left") && "menu" !in left && "menu" !in confCenter(top) && "menu" !in confRight(top)) listOf("menu") + left else left
    }
    val center: List<String> get() = confCenter(top)
    val right: List<String> get() = rightModules(confRight(top), confLeft(top), confCenter(top), confQuickButton(top), confNotificationsButton(top))
    val clockPattern: String get() = confClock(top)
    val datePattern: String get() = confDate(top)
    val iconSize: Int get() = confIconSize(top)
    val showPercent: Boolean get() = confShowPercent(top)
    val showPassive: Boolean get() = confShowPassive(top)
    val titleWidth: Int get() = confTitleWidth(top)

    /** The clock's hint (java.time's pattern), and how long the pointer stays before a hint shows (ms). */
    val clockHint: String get() = confClockHint(top)
    val hintDelayMs: Int get() = confHintDelay(top).coerceAtLeast(0)

    /** Modules of the configuration's own, by name: {text | command, every_seconds, icon, on_click, ...}. */
    val modules: Map<String, Any?> get() = confModules(top)

    /** What clicks and the wheel do on a module instead of newm's (module name -> click, middle, right, scroll_up, scroll_down). */
    val clicks: Map<String, Any?> get() = confOn(top)

    /** This monitor has the bar (topBar { outputs }: empty is all). */
    val shownHere: Boolean get() = confBarOutputs(top).let { it.isEmpty() || monitor.name in it }

    /** Tray icons not shown, by their name or title (those the bar shows itself: nm-applet's Wi-Fi, blueman's Bluetooth). */
    val trayHide: List<String> get() = confTrayHide(top)

    /** The room it keeps at the edge: windows stay out of it. */
    val room: Int get() = if (islands) height + 2 * margin else height

    /** The screen's corners' radius (the classic bar is rounded there too). */
    val screenCornerRadius: Int get() = confCornerRadius()

    /** Its left edge on the desktop (for popups below a part of it). */
    val originX: Double get() = monitor.area.x.toDouble()

    /** The texts of the configuration (texts { ... }), asked for every second. */
    var texts by mutableStateOf(listOf<String>())
        private set

    /** The wall, small, and the focused window (the islands look). */
    var wall by mutableStateOf(newm.bar.WallMap.EMPTY)
        private set
    var focused by mutableStateOf(newm.bar.Focused("", ""))
        private set

    /** The keyboard layout in use ("EN", "RU"), empty while there is only one. */
    var keyboardLayout by mutableStateOf("")
        private set

    // The islands where they are (relative to the bar): blurred behind.
    private val islandRects = arrayOfNulls<Rect>(3)

    internal fun setIsland(index: Int, rect: Rect) {
        islandRects[index] = rect
    }

    /** The parts of the screen behind it to blur, with their corners' radius. */
    fun blurAreas(): List<Pair<Rect, Float>> {
        val place = place() ?: return emptyList()
        if (!islands) return listOf(place to 0f)
        return islandRects.filterNotNull().map { r -> Rect(place.x + r.x, place.y + r.y, r.width, r.height) to cornerRadius.toFloat() }
    }

    private val animation = object : Animate<WidgetDown>() {
        fun at(default: WidgetDown) = process(default)
        fun start(from: WidgetDown, to: WidgetDown, dt: Double, curve: dev.caulis.core.Curve) = startAnimation(WidgetDownInterpolation(from, to, workspace.preventAnim), dt, curve)
        override fun animDamage() = wm.damage()
    }

    fun reducer(state: LayoutState): WidgetDown {
        val wsState = state.workspaceState(workspace)
        val a = monitor.area
        val h = room.toDouble()
        val box = if (top) {
            Box(a.x.toDouble(), a.y + wsState.topBarDy * h - h, a.width.toDouble(), h)
        } else {
            Box(a.x.toDouble(), a.y + a.height - wsState.bottomBarDy * h, a.width.toDouble(), h)
        }
        return WidgetDown(1000.0, box)
    }

    override fun animate(oldState: LayoutState, newState: LayoutState, dt: Double, curve: dev.caulis.core.Curve) = animation.start(reducer(oldState), reducer(newState), dt, curve)

    override fun flushAnimation() = animation.flushAnimationOwn()

    val animating: Boolean get() = animation.animating

    /** Where it is this frame (null: out of sight). */
    fun place(): Rect? {
        // (Never over the lock screen: what windows are open, the tray, the network are nobody's business there.)
        if (!shownHere || wm.lockShown()) return null
        val b = animation.at(reducer(wm.state)).box
        val rect = Rect(Math.round(b.x).toInt(), Math.round(b.y).toInt(), Math.round(b.w).toInt(), Math.round(b.h).toInt())
        return rect.intersect(monitor.area)?.takeIf { it.height > 0 }?.let { rect }
    }

    fun refresh() {
        texts = try {
            confTexts(top)()
        } catch (e: Exception) {
            log.warn("The bar's texts failed: $e")
            texts
        }
    }

    /** The wall and the focused window as they are now (set only when they changed). */
    fun look() {
        val wsState = wm.state.workspaceState(workspace)
        val focusedView = wm.findFocusedView()
        val tiles = ArrayList<Triple<Double, Double, Pair<Double, Double>>>()
        val focusedFlags = ArrayList<Boolean>()
        for ((handle, s) in wsState.viewStates) {
            if (!s.isTiled || s.w <= 0.0 || s.h <= 0.0) continue
            tiles += Triple(s.i, s.j, s.w to s.h)
            focusedFlags += focusedView?.handle == handle
        }
        val view = (wsState.stateBeforeOverview?.let { it.i to it.j } ?: (wsState.i to wsState.j)) to (wsState.stateBeforeOverview?.size ?: wsState.size)
        val minI = (tiles.map { it.first } + view.first.first).min()
        val minJ = (tiles.map { it.second } + view.first.second).min()
        val maxI = (tiles.map { it.first + it.third.first } + (view.first.first + view.second)).max()
        val maxJ = (tiles.map { it.second + it.third.second } + (view.first.second + view.second)).max()
        val map = newm.bar.WallMap(
            tiles = tiles.mapIndexed { k, t -> newm.bar.WallMap.Tile((t.first - minI).toFloat(), (t.second - minJ).toFloat(), t.third.first.toFloat(), t.third.second.toFloat(), focusedFlags[k]) },
            view = newm.bar.WallMap.Tile((view.first.first - minI).toFloat(), (view.first.second - minJ).toFloat(), view.second.toFloat(), view.second.toFloat()),
            width = (maxI - minI).toFloat().coerceAtLeast(1f),
            height = (maxJ - minJ).toFloat().coerceAtLeast(1f),
            aspect = workspace.width.toFloat() / workspace.height.coerceAtLeast(1),
        )
        if (map != wall) wall = map
        val f = focusedView?.let { newm.bar.Focused(it.title, it.appId) } ?: newm.bar.Focused("", "")
        if (f != focused) focused = f
        val names = wm.caulis.keyboardLayoutNames
        val k = if (names.size > 1) names.getOrNull(wm.caulis.keyboardLayoutNow)?.let { layoutLabel(it, confLayoutNames(top)) }.orEmpty() else ""
        if (k != keyboardLayout) keyboardLayout = k
    }
}

/** A bar's look: the texts spread evenly, white on a dark strip (Compose). */
@androidx.compose.runtime.Composable
fun BarContent(bar: Bar) {
    val bg = bar.background
    val fg = bar.foreground
    val family = bar.font?.let { name ->
        org.jetbrains.skia.FontMgr.default.matchFamilyStyle(name, org.jetbrains.skia.FontStyle.NORMAL)?.let { FontFamily(androidx.compose.ui.text.platform.Typeface(it)) }
    } ?: FontFamily.Monospace
    // (Rounded where the screen's corners are: they show through.)
    val r = bar.screenCornerRadius.dp
    val shape = if (bar.top) androidx.compose.foundation.shape.RoundedCornerShape(topStart = r, topEnd = r) else androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = r, bottomEnd = r)
    Row(
        Modifier.fillMaxSize().background(Color(bg.red, bg.green, bg.blue, bg.alpha), shape),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (t in bar.texts.filter { it.isNotEmpty() }) BasicText(t, style = TextStyle(color = Color(fg.red, fg.green, fg.blue, fg.alpha), fontSize = bar.fontSize.sp, fontFamily = family))
    }
}

/** The background and the screen's corners as Caulis drawings (one each, every output). */
internal fun Layout.addWidgetDrawings(backgrounds: () -> List<Background>, corners: Corners, bars: () -> List<Bar>) {
    caulis.addDrawing(DrawingLevel.WALLPAPER, object : Drawing {
        override fun draw(monitor: Monitor, canvas: OverlayCanvas) {
            backgrounds().filter { it.monitor === monitor }.forEach { it.draw(canvas) }
        }

        override fun animating(now: Long): Boolean = backgrounds().any { it.moving }
    })
    caulis.addDrawing(DrawingLevel.ON_TOP, object : Drawing {
        override fun draw(monitor: Monitor, canvas: OverlayCanvas) {
            corners.draw(monitor, canvas)
            // Behind a see-through bar: blurred (the bar itself comes above, rounded at the screen's corners).
            for (bar in bars()) if (bar.monitor === monitor && bar.blur) for ((area, radius) in bar.blurAreas()) canvas.blur(area, radius = radius, size = 8, passes = 3)
        }
    })
}
