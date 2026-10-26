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
    val texture = renderer.createTexture(w, h)
    val row = IntArray(w)
    val bytes = ByteArray(w * h * 4)
    var at = 0
    for (y in 0 until h) {
        image.getRGB(0, y, w, 1, row, 0, w)
        for (argb in row) {
            val a = argb ushr 24 and 0xff
            if (a == 255) {
                bytes[at] = argb.toByte()
                bytes[at + 1] = (argb ushr 8).toByte()
                bytes[at + 2] = (argb ushr 16).toByte()
            } else {
                bytes[at] = ((argb and 0xff) * a / 255).toByte()
                bytes[at + 1] = ((argb ushr 8 and 0xff) * a / 255).toByte()
                bytes[at + 2] = ((argb ushr 16 and 0xff) * a / 255).toByte()
            }
            bytes[at + 3] = a.toByte()
            at += 4
        }
    }
    Arena.ofConfined().use { arena ->
        val pixels = arena.allocate(bytes.size.toLong())
        MemorySegment.copy(bytes, 0, pixels, ValueLayout.JAVA_BYTE, 0, bytes.size)
        texture.upload(pixels, 0, h)
    }
    return texture
}

// --- the background --------------------------------------------------------------------------------

private val confOutputs = configuredValue<List<Map<String, Any?>>>("outputs", emptyList())
private val confTimeScale = configuredValue("background.time_scale", 0.15)
private val confAnimDefault = configuredValue("background.anim", true)

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
    private var preventAnim = !confAnimDefault() || workspace.preventAnim
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

    override fun animate(oldState: LayoutState, newState: LayoutState, dt: Double) {
        if (preventAnim) return
        animCaught = -dt
        target = state(newState)
        wm.damage()
    }

    override fun flushAnimation() {
        animCaught = null
    }

    /** True while it glides towards where the view says. */
    val moving: Boolean get() = !preventAnim && current.delta(target) > 1

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
            if (current.delta(target) > 1) {
                current.approach(target, confTimeScale(), t - lastFrame)
            } else if (current !== target) {
                current = target
            }
            lastFrame = t
        }
        val b = current.box
        val rect = Rect(monitor.area.x + Math.round(b[0]).toInt(), monitor.area.y + Math.round(b[1]).toInt(), Math.round(b[2]).toInt(), Math.round(b[3]).toInt())
        canvas.image(picture.texture, rect, current.opacity.toFloat())
    }
}

/** A wallpaper picture on the GPU ("resource:wallpaper.jpg": newm's own). */
class Wallpaper(val texture: GpuTexture, val width: Int, val height: Int) {
    companion object {
        // The picture read from [path] (slow for a big one: not on the compositor's thread).
        fun read(path: String): BufferedImage? = try {
            val t0 = System.nanoTime()
            val image = if (path.startsWith("resource:")) {
                Wallpaper::class.java.getResourceAsStream("/newm/templates/${path.removePrefix("resource:")}")?.use { javax.imageio.ImageIO.read(it) }
            } else {
                javax.imageio.ImageIO.read(java.io.File(path.replaceFirst("~", System.getenv("HOME") ?: System.getProperty("user.home"))))
            } ?: error("not a picture")
            log.info { "Wallpaper $path: ${image.width}x${image.height}, read in ${(System.nanoTime() - t0) / 1_000_000} ms" }
            image
        } catch (e: Exception) {
            log.warn("The wallpaper $path cannot be shown: $e")
            null
        }

        /** The picture of [image] on the GPU (on the compositor's thread). */
        fun of(renderer: Renderer, image: BufferedImage) = Wallpaper(textureOf(renderer, image), image.width, image.height)
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
private val confLeft = BarSetting("left", listOf("wall", "window"))
private val confCenter = BarSetting("center", listOf("clock"))
private val confRight = BarSetting("right", listOf("tray", "layout", "network", "volume", "battery", "power"))
private val confClock = BarSetting("clock", "HH:mm")
private val confDate = BarSetting("date", "EEE d MMM")
private val confIslandColor = BarSetting<String?>("island_color", null)
private val confTextColor = BarSetting<String?>("text_color", null)
private val confAccentColor = BarSetting<String?>("accent_color", null)
private val confIconSize = BarSetting("icon_size", 16)
private val confShowPercent = BarSetting("show_percent", true)
private val confShowPassive = BarSetting("show_passive", false)
private val confTitleWidth = BarSetting("title_width", 360)

// "us" -> "EN", "ru" -> "RU", "de(neo)" -> "DE".
private fun layoutLabel(name: String): String = when (val code = name.substringBefore('(').trim().lowercase()) {
    "us", "gb" -> "EN"
    else -> code.take(2).uppercase()
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

    /** The modules of each island ("wall", "window", "clock", "tray", "network", "volume", "battery", "power", "texts"). */
    val left: List<String> get() = confLeft(top)
    val center: List<String> get() = confCenter(top)
    val right: List<String> get() = confRight(top)
    val clockPattern: String get() = confClock(top)
    val datePattern: String get() = confDate(top)
    val iconSize: Int get() = confIconSize(top)
    val showPercent: Boolean get() = confShowPercent(top)
    val showPassive: Boolean get() = confShowPassive(top)
    val titleWidth: Int get() = confTitleWidth(top)

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
        fun start(from: WidgetDown, to: WidgetDown, dt: Double) = startAnimation(WidgetDownInterpolation(from, to, workspace.preventAnim), dt)
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

    override fun animate(oldState: LayoutState, newState: LayoutState, dt: Double) = animation.start(reducer(oldState), reducer(newState), dt)

    override fun flushAnimation() = animation.flushAnimationOwn()

    val animating: Boolean get() = animation.animating

    /** Where it is this frame (null: out of sight). */
    fun place(): Rect? {
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
        val k = if (names.size > 1) names.getOrNull(wm.caulis.keyboardLayoutNow)?.let(::layoutLabel).orEmpty() else ""
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
