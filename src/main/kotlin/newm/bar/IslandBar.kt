package newm.bar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.caulis.core.appearance.ColorScheme
import dev.caulis.geometry.Rect
import dev.caulis.log.Logger
import newm.Bar
import newm.Layout
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val log = Logger("bar")

/** The bar's colours: its islands, text, faint text, accent, hover, the islands' edge, warnings. */
data class BarColors(
    val island: Color,
    val text: Color,
    val dim: Color,
    val accent: Color,
    val hover: Color,
    val edge: Color,
    val warning: Color,
    val danger: Color,
) {
    companion object {
        /** Light: Nord's snow, as newm's look. */
        val LIGHT = BarColors(
            island = Color(0xC8ECEFF4), text = Color(0xFF2E3440), dim = Color(0xFF4C566A), accent = Color(0xFF5E81AC),
            hover = Color(0x162E3440), edge = Color(0x80FFFFFF), warning = Color(0xFFD08770), danger = Color(0xFFBF616A),
        )

        /** Dark: Nord's polar night. */
        val DARK = BarColors(
            island = Color(0xC82E3440), text = Color(0xFFECEFF4), dim = Color(0xFFB8C0CE), accent = Color(0xFF88C0D0),
            hover = Color(0x1EECEFF4), edge = Color(0x22FFFFFF), warning = Color(0xFFEBCB8B), danger = Color(0xFFE06C75),
        )
    }
}

/** A newm wall, small: its windows' tiles and the part of it on screen (0..1 of [width] x [height] tile units). */
data class WallMap(val tiles: List<Tile>, val view: Tile?, val width: Float, val height: Float, val aspect: Float) {
    data class Tile(val x: Float, val y: Float, val w: Float, val h: Float, val focused: Boolean = false)

    companion object {
        val EMPTY = WallMap(emptyList(), null, 1f, 1f, 16f / 9f)
    }
}

/** The focused window: its title and app id (empty: none). */
data class Focused(val title: String, val appId: String)

/** What a bar's popup shows. */
enum class PopupKind { CALENDAR, VOLUME, NETWORK, BLUETOOTH, BATTERY, POWER, COLORS, TRAY_MENU, TOOLTIP }

/** An open popup: below [anchorX] (desktop units) of [bar]. */
class Popup(val kind: PopupKind, val bar: Bar, val anchorX: Double, val item: TrayItem? = null) {
    /** A tray menu's entries (read when it opens), and the submenus gone into. */
    var entries by mutableStateOf<List<MenuEntry>?>(null)
    var path by mutableStateOf<List<MenuEntry>>(emptyList())

    /** The power entry waiting for a second click. */
    var confirming by mutableStateOf<String?>(null)

    /** The month the calendar shows (0: this one). */
    var monthOffset by mutableStateOf(0)

    /** A tooltip's text. */
    var text by mutableStateOf("")

    /** The entry opened in place (a network asking for its password, a device's buttons), and the password typed. */
    var expanded by mutableStateOf<String?>(null)
    var password by mutableStateOf("")

    /** How tall it is now, measured (0: not yet). */
    internal var measured by mutableStateOf(0)

    /** What a hint is of. */
    internal var hintKey: Any? = null
}

/**
 * Everything newm's bars share: the tray, what the computer says (battery, network, volume),
 * the colours of the scheme in use, the popup open now. Made once and started with the
 * compositor: a reload of the configuration keeps the tray and its programs.
 */
class BarModel(val wm: Layout) {
    val tray = Tray(wm.caulis)
    val status = Status(wm.caulis)
    val network = NetworkManager(wm.caulis)
    val bluetooth = Bluetooth(wm.caulis)
    val sound = Sound(wm.caulis)
    val powerProfiles = PowerProfiles(wm.caulis)

    /** The popups' part of the screen (a click on it gives it the keyboard: a password). */
    internal var popupWidget: dev.caulis.compose.ComposeWidget? = null

    var scheme by mutableStateOf(ColorScheme.DEFAULT)
        private set

    var popup by mutableStateOf<Popup?>(null)

    /** The popup on screen: the open one, or the one fading away. */
    var shownPopup by mutableStateOf<Popup?>(null)
        internal set

    /** The card in the middle of the screen (a network or device came or went). */
    var card by mutableStateOf<OsdCard?>(null)

    /** The card's coming and going (done by the compositor). */
    val cardFade = Fade(inMillis = 220, outMillis = 260)

    /** The window asking for a Wi-Fi password. */
    val passwordDialog = WifiPasswordDialog(this)

    /** Asks for [network]'s password in a window in the middle of the screen. */
    fun askPassword(network: WifiNetwork) = passwordDialog.ask(network)

    // Another popup on screen: its scene starts over (one scene changing its whole content
    // tripped Compose up).
    internal fun showPopup(next: Popup) {
        if (shownPopup !== next) popupWidget?.restart()
        shownPopup = next
        popupFade.show()
    }

    /** The popups' coming and going (done by the compositor). */
    val popupFade = Fade(inMillis = 190, outMillis = 130)

    /** The colours made from the wallpaper (or a colour of yours); null: the classic Nord ones. */
    var palette by mutableStateOf<dev.caulis.core.appearance.Palette?>(null)
        private set

    /** Makes the palette anew (the wallpaper, the configuration or the colours menu changed) and writes it out. */
    fun refreshPalette() {
        val next = NewmColors.palette(wm.wallpaperSeed)
        if (next != palette) {
            palette = next
            NewmColors.export(next)
            wm.focusBorders.update()
        }
    }

    // The part of the bar the pointer is on, waiting for its hint.
    internal var hintWanted: Any? = null

    /** The on-screen display (a level at the bottom) and a toast (words in the middle) shown now. */
    var osd by mutableStateOf<OsdShow?>(null)
    var toast by mutableStateOf<OsdShow?>(null)

    /** Clock text, changed each second (the bar reads it). */
    var now by mutableStateOf(LocalDateTime.now())
        private set

    private var started = false

    /** Starts the tray and the status readers (once; at the compositor's start). */
    fun start() {
        if (started) return
        started = true
        scheme = wm.caulis.colorSchemeNow
        wm.caulis.onColorSchemeChanged { scheme = it }
        network.onConnection = { connectionChanged(it) }
        bluetooth.onConnection = { connectionChanged(it) }
        tray.start()
        status.start()
        network.start()
        bluetooth.start()
        powerProfiles.start()
        wm.every(1000) {
            val t = LocalDateTime.now()
            if (t.minute != now.minute || t.second != now.second) now = t
        }
        // A click anywhere else closes the popup.
        wm.caulis.onPointerButton { _, pressed ->
            val open = popup
            if (pressed && open != null && open.kind != PopupKind.TOOLTIP) {
                val d = wm.caulis.desktop
                val inPopup = popupRect(open)?.let { r -> d.pointerX >= r.x && d.pointerX < r.right && d.pointerY >= r.y && d.pointerY < r.bottom } == true
                val onBar = open.bar.place()?.let { r -> d.pointerX >= r.x && d.pointerX < r.right && d.pointerY >= r.y && d.pointerY < r.bottom } == true
                if (!inPopup && !onBar) close()
            }
            false
        }
        log.info { "newm's bar: tray and status started" }
    }

    /** The colours of the scheme in use, for what belongs to no bar (the on-screen display). */
    fun colorsNow(): BarColors = wm.barsNow().firstOrNull()?.let(::colorsFor) ?: run {
        val dark = wm.caulis.colorSchemeNow == ColorScheme.DARK
        palette?.let { NewmColors.barColors(it, dark) } ?: if (dark) BarColors.DARK else BarColors.LIGHT
    }

    // --- the volume from the bar's wheel and the keys: steps added up, one change at a time ---

    private var wheel = 0f
    @Volatile
    private var volumeWanted: Int? = null
    private val volumeWorker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "newm-volume").apply { isDaemon = true } }

    /** The wheel over the volume: [dy] lines (up: louder), two percent a notch. */
    fun scrollVolume(dy: Float) {
        wheel -= dy
        val steps = wheel.toInt()
        if (steps == 0) return
        wheel -= steps
        changeVolume(steps * 2)
    }

    /** The volume by [delta] percent: shown at once, set by one command for all the steps that came meanwhile. */
    fun changeVolume(delta: Int) {
        val now = volumeWanted ?: status.volume?.percent ?: return
        val next = (now + delta).coerceIn(0, 100)
        val first = volumeWanted == null
        volumeWanted = next
        status.showVolume(next)
        showLevel(Glyph.SPEAKER, "Volume $next %", next / 100.0)
        if (!first) return
        volumeWorker.execute {
            var applied = -1
            while (true) {
                val target = volumeWanted ?: break
                if (target == applied) break
                runQuietly("wpctl", "set-mute", "@DEFAULT_AUDIO_SINK@", "0")
                runQuietly("wpctl", "set-volume", "@DEFAULT_AUDIO_SINK@", "$target%") || runQuietly("pactl", "set-sink-volume", "@DEFAULT_SINK@", "$target%")
                applied = target
                Thread.sleep(40)
            }
            wm.caulis.post { if (volumeWanted == applied) volumeWanted = null }
            status.refreshSoon()
        }
    }

    fun colorsFor(bar: Bar): BarColors {
        val dark = when (bar.theme) {
            "dark" -> true
            "light" -> false
            else -> scheme == ColorScheme.DARK
        }
        val base = palette?.let { NewmColors.barColors(it, dark) } ?: if (dark) BarColors.DARK else BarColors.LIGHT
        return base.copy(
            island = bar.islandColor?.let(::composeColor) ?: base.island,
            text = bar.textColor?.let(::composeColor) ?: base.text,
            accent = bar.accentColor?.let(::composeColor) ?: base.accent,
        )
    }

    /** Opens [kind] below [anchorX] of [bar], or closes it when it is open already. */
    fun toggle(kind: PopupKind, bar: Bar, anchorX: Double, item: TrayItem? = null) {
        val open = popup
        if (open != null && open.kind == kind && open.item === item && open.bar === bar) return close()
        close()
        val next = Popup(kind, bar, anchorX, item)
        popup = next
        showPopup(next)
        if (kind == PopupKind.TRAY_MENU && item != null) tray.menuOf(item) { entries -> if (popup === next) next.entries = entries }
        when (kind) {
            PopupKind.VOLUME -> { status.refreshSoon(); sound.refresh() }
            PopupKind.BATTERY -> { status.refreshSoon(); powerProfiles.refresh() }
            PopupKind.NETWORK -> network.refresh()
            PopupKind.BLUETOOTH -> bluetooth.refresh()
            else -> {}
        }
    }

    /** The open popup takes the keys (a password is to be typed). */
    fun wantKeyboard() {
        popupWidget?.let { wm.caulis.desktop.focusWidget(it) }
    }

    fun close() {
        val open = popup ?: return
        popup = null
        // (Still shown while it fades away.)
        popupFade.hide()
        // The keys go back to the window that had them.
        val d = wm.caulis.desktop
        if (popupWidget != null && d.keyboardWidget === popupWidget) d.focusWidget(null)
        if (open.kind == PopupKind.TRAY_MENU && open.item != null) tray.menuClosed(open.item)
    }

    /** Where [popup] is on the screen (desktop units), or null. */
    fun popupRect(popup: Popup): Rect? {
        val bar = popup.bar
        val place = bar.place() ?: return null
        // As tall as what it shows now (measured; at first a guess), growing and shrinking smoothly.
        val (w0, h0) = popupSize(popup)
        val w = w0.coerceAtMost(POPUP_WIDEST)
        val h = (popup.measured.takeIf { it > 0 } ?: h0).coerceAtMost(POPUP_TALLEST)
        val area = bar.monitor.area
        val x = (popup.anchorX - w / 2.0).coerceIn(area.x + 8.0, area.right - 8.0 - w).toInt()
        val y = if (bar.top) place.y + place.height - bar.margin + 6 else place.y - h - 6 + bar.margin
        // (With room around it for its shadow.)
        return Rect(x - POPUP_SHADOW, y - POPUP_SHADOW, w + 2 * POPUP_SHADOW, h + 2 * POPUP_SHADOW)
    }

    /** How wide [popup] is (desktop units, without its shadow). */
    fun popupWidth(popup: Popup): Int = popupSize(popup).first.coerceAtMost(POPUP_WIDEST)

    private fun popupSize(popup: Popup): Pair<Int, Int> = when (popup.kind) {
        PopupKind.CALENDAR -> 292 to 282
        PopupKind.VOLUME -> 340 to (34 + 36 + (if (sound.outputs.size > 1) 26 + sound.outputs.size * 34 else 0) +
            (if (sound.inputs.isNotEmpty()) 26 + 36 + (if (sound.inputs.size > 1) sound.inputs.size * 34 else 0) else 0) +
            (if (status.brightness != null) 26 + 36 else 0) + 26).coerceAtMost(560)
        PopupKind.NETWORK -> 350 to (34 + (if (network.problem != null) 20 else 0) + (if (network.wired) 42 else 0) +
            (if (!network.wifiEnabled || network.networks.isEmpty()) 32 else network.networks.size.coerceAtMost(8) * 42) +
            (if (popup.expanded != null) 76 else 0) + 26).coerceAtMost(520)
        PopupKind.BLUETOOTH -> 350 to (34 + (if (bluetooth.problem != null) 20 else 0) + (if (!bluetooth.powered) 32 else {
            val shown = bluetooth.devices.take(9)
            shown.sumOf { if (it.connected || !it.paired || bluetooth.busy == it.path) 42 else 34 } +
                (if (shown.any { it.paired }) 26 else 0) + (if (bluetooth.scanning || shown.any { !it.paired }) 26 else 0) + (if (shown.isEmpty()) 30 else 0)
        }) + (if (popup.expanded != null) 34 else 0) + 28).coerceAtMost(520)
        PopupKind.BATTERY -> 310 to ((if (status.battery != null) 56 + 8 else 0) + (if (status.brightness != null) 26 + 36 + 8 else 0) +
            (if (powerProfiles.profiles.isNotEmpty()) 26 + 8 + powerProfiles.profiles.size * (34 + 8) else 0) + 26).coerceAtLeast(60)
        PopupKind.POWER -> 240 to 228
        PopupKind.COLORS -> 340 to (34 + 6 * 42 + 28)
        PopupKind.TOOLTIP -> (popup.text.lines().maxOfOrNull { it.length } ?: 10).coerceIn(4, 60) * 7 + 28 to popup.text.lines().size * 18 + 18
        PopupKind.TRAY_MENU -> {
            val shown = (popup.path.lastOrNull()?.children ?: popup.entries).orEmpty()
            val rows = shown.sumOf { if (it.separator) 9 else 30 } + (if (popup.path.isNotEmpty()) 30 else 0)
            val longest = shown.maxOfOrNull { it.label.length } ?: 12
            (longest * 8 + 64).coerceIn(180, 380) to (rows + 14).coerceIn(44, 600)
        }
    }

    /** The bar's text of the clock ([pattern], java.time's). */
    fun clock(pattern: String): String = try {
        now.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    } catch (_: IllegalArgumentException) {
        now.format(DateTimeFormatter.ofPattern("HH:mm"))
    }
}

/** The room around a popup for its shadow. */
internal const val POPUP_SHADOW = 14

/** The widest a popup gets. */
internal const val POPUP_WIDEST = 400

/** The tallest a popup gets (its rows scroll beyond). */
internal const val POPUP_TALLEST = 600

internal fun composeColor(text: String): Color? = try {
    val c = newm.color(text)
    Color(c.red, c.green, c.blue, c.alpha)
} catch (_: IllegalArgumentException) {
    log.warn("\"$text\" is not a colour (#RRGGBB or #RRGGBBAA)")
    null
}

// --- the bar ------------------------------------------------------------------------------------

/** A button of the bar: what it does on a click of each button and on the wheel. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private fun Modifier.buttons(
    onPrimary: (() -> Unit)? = null,
    onSecondary: (() -> Unit)? = null,
    onMiddle: (() -> Unit)? = null,
    onScroll: ((Float) -> Unit)? = null,
): Modifier = pointerInput(onPrimary, onSecondary, onMiddle, onScroll) {
    awaitPointerEventScope {
        var pressed: PointerButton? = null
        while (true) {
            val event = awaitPointerEvent()
            when (event.type) {
                PointerEventType.Press -> pressed = event.button
                PointerEventType.Release -> {
                    val inside = event.changes.firstOrNull()?.position?.let { it.x >= 0 && it.y >= 0 && it.x <= size.width && it.y <= size.height } != false
                    if (inside && pressed == event.button) when (event.button) {
                        PointerButton.Primary -> onPrimary?.invoke()
                        PointerButton.Secondary -> (onSecondary ?: onPrimary)?.invoke()
                        PointerButton.Tertiary -> onMiddle?.invoke()
                        else -> {}
                    }
                    pressed = null
                }
                PointerEventType.Scroll -> {
                    val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (dy != 0f) onScroll?.invoke(dy)
                }
                else -> {}
            }
        }
    }
}

/** A part of an island that lights up under the pointer. */
@Composable
private fun Chip(
    colors: BarColors,
    modifier: Modifier = Modifier,
    onX: (Double) -> Unit = {},
    onPrimary: (() -> Unit)? = null,
    onSecondary: (() -> Unit)? = null,
    onMiddle: (() -> Unit)? = null,
    onScroll: ((Float) -> Unit)? = null,
    onHover: ((Boolean) -> Unit)? = null,
    /** Told when the pointer comes and goes, with the chip's middle (for a hint below it). */
    onHint: ((Boolean, Double) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered && (onPrimary != null || onScroll != null)) colors.hover else Color.Transparent, tween(120))
    var middle by remember { mutableStateOf(0.0) }
    LaunchedEffect(hovered) {
        onHover?.invoke(hovered)
        onHint?.invoke(hovered, middle)
    }
    val density = LocalDensity.current
    Box(
        modifier
            .fillMaxHeight()
            .padding(vertical = 3.dp)
            .background(bg, RoundedCornerShape(9.dp))
            .hoverable(source)
            .onGloballyPositioned { c -> with(density) { middle = c.boundsInRoot().center.x.toDp().value.toDouble(); onX(middle) } }
            .buttons(onPrimary, onSecondary, onMiddle, onScroll)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** An island: a rounded, see-through piece of the bar (what is behind it is blurred). */
@Composable
private fun Island(bar: Bar, colors: BarColors, index: Int, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(bar.cornerRadius.dp)
    Row(
        Modifier
            .fillMaxHeight()
            .softShadow(bar.cornerRadius.dp, 4.dp, 0.10f)
            .background(colors.island, shape)
            .border(1.dp, colors.edge, shape)
            .onGloballyPositioned { c ->
                val b = c.boundsInRoot()
                with(density) { bar.setIsland(index, Rect(b.left.toDp().value.toInt(), b.top.toDp().value.toInt(), b.width.toDp().value.toInt(), b.height.toDp().value.toInt())) }
            }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/**
 * newm's bar: three islands — the wall and the focused window, the clock, the tray and the
 * computer's state — floating at the screen's edge, in the light or dark scheme. Each part
 * opens a popup on a click (the calendar, the volume, the network, the battery, the power
 * menu, a tray icon's menu).
 */
@Composable
fun IslandBar(bar: Bar, model: BarModel) {
    val colors = model.colorsFor(bar)
    val family = bar.fontFamily
    val text = TextStyle(color = colors.text, fontSize = bar.fontSize.sp, fontFamily = family, fontWeight = FontWeight.Medium)
    val pad = bar.margin.dp
    Box(Modifier.fillMaxSize().padding(start = pad, end = pad, top = if (bar.top) pad else 0.dp, bottom = if (bar.top) 0.dp else pad)) {
        Box(Modifier.fillMaxWidth().height(bar.height.dp)) {
            Row(Modifier.align(Alignment.CenterStart).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bar.left.isNotEmpty()) Island(bar, colors, 0) { Modules(bar.left, bar, model, colors, text) }
            }
            if (bar.center.isNotEmpty()) Row(Modifier.align(Alignment.Center).fillMaxHeight()) {
                Island(bar, colors, 1) { Modules(bar.center, bar, model, colors, text) }
            }
            Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight()) {
                if (bar.right.isNotEmpty()) Island(bar, colors, 2) { Modules(bar.right, bar, model, colors, text) }
            }
        }
    }
}

@Composable
private fun Modules(names: List<String>, bar: Bar, model: BarModel, colors: BarColors, text: TextStyle) {
    for (name in names) when (name) {
        "wall" -> WallModule(bar, model, colors)
        "window" -> WindowModule(bar, model, colors, text)
        "clock" -> ClockModule(bar, model, colors, text)
        "tray" -> TrayModule(bar, model, colors)
        "layout" -> LayoutModule(bar, model, colors, text)
        "bluetooth" -> BluetoothModule(bar, model, colors)
        "network" -> NetworkModule(bar, model, colors, text)
        "volume" -> VolumeModule(bar, model, colors, text)
        "battery" -> BatteryModule(bar, model, colors, text)
        "power" -> PowerModule(bar, model, colors)
        "menu" -> MenuModule(bar, model, colors)
        "texts" -> TextsModule(bar, colors, text)
        else -> Chip(colors) { BasicText(name, style = text) }
    }
}

// The wall: each window a tile, the focused one in the accent colour, the screen's part outlined.
@Composable
private fun WallModule(bar: Bar, model: BarModel, colors: BarColors) {
    val wall = bar.wall
    val mapH = (bar.height - 16).coerceAtLeast(10).toFloat()
    val unit = mapH / wall.height
    val mapW = (wall.width * unit * wall.aspect).coerceIn(mapH * 0.8f, mapH * 5f)
    Chip(colors, onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "wall") { "All windows · the wheel goes through them" } }, onPrimary = { model.wm.toggleOverview() }, onScroll = { dy -> model.wm.cycleViews(if (dy > 0) 1 else -1) }) {
        Canvas(Modifier.width(mapW.dp).height(mapH.dp)) {
            val sx = size.width / wall.width
            val sy = size.height / wall.height
            val r = CornerRadius(2.dp.toPx())
            val gap = 1.dp.toPx()
            if (wall.tiles.isEmpty()) {
                drawRoundRect(colors.dim.copy(alpha = 0.25f), Offset.Zero, size, r)
            }
            for (t in wall.tiles) {
                drawRoundRect(if (t.focused) colors.accent else colors.text.copy(alpha = 0.32f), Offset(t.x * sx + gap, t.y * sy + gap), Size((t.w * sx - 2 * gap).coerceAtLeast(1f), (t.h * sy - 2 * gap).coerceAtLeast(1f)), r)
            }
            wall.view?.let { v ->
                drawRoundRect(colors.text.copy(alpha = 0.85f), Offset(v.x * sx, v.y * sy), Size(v.w * sx, v.h * sy), CornerRadius(3.dp.toPx()), style = Stroke(1.4.dp.toPx()))
            }
        }
    }
}

// The focused window: its icon and its title.
@Composable
private fun WindowModule(bar: Bar, model: BarModel, colors: BarColors, text: TextStyle) {
    val focused = bar.focused
    var icon by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(focused.appId) {
        icon = null
        if (focused.appId.isNotEmpty()) IconTheme.load(AppIcons.iconName(focused.appId), TRAY_ICON_PIXELS, null, post = { model.wm.caulis.post { it() } }) { icon = it }
    }
    Chip(colors, onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "window") { focused.title.ifEmpty { tr("No window") } } }, onPrimary = { model.wm.toggleOverview() }, onMiddle = { model.wm.closeFocusedView() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Image(it, null, Modifier.size(16.dp), filterQuality = FilterQuality.High)
                Spacer(Modifier.width(7.dp))
            }
            BasicText(
                focused.title.ifEmpty { if (focused.appId.isEmpty()) tr("Desktop") else focused.appId },
                style = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = bar.titleWidth.dp),
            )
        }
    }
}

@Composable
private fun ClockModule(bar: Bar, model: BarModel, colors: BarColors, text: TextStyle) {
    var x by remember { mutableStateOf(0.0) }
    Chip(colors, onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "clock") { model.clock("EEEE, d MMMM yyyy") } }, onX = { x = it }, onPrimary = { model.toggle(PopupKind.CALENDAR, bar, bar.originX + x) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(model.clock(bar.clockPattern), style = text.copy(fontWeight = FontWeight.SemiBold))
            if (bar.datePattern.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                BasicText(model.clock(bar.datePattern), style = text.copy(color = colors.dim, fontWeight = FontWeight.Normal))
            }
        }
    }
}

@Composable
private fun TrayModule(bar: Bar, model: BarModel, colors: BarColors) {
    val hidden = bar.trayHide.map { it.lowercase() }
    val items = model.tray.items.filter { item ->
        (item.status != "Passive" || bar.showPassive) &&
            hidden.none { h -> h.isNotEmpty() && (item.id.lowercase().contains(h) || item.title.lowercase().contains(h) || item.service.lowercase().contains(h)) }
    }
    for (item in items) {
        var x by remember(item) { mutableStateOf(0.0) }
        fun screenX() = (bar.originX + x).toInt()
        fun screenY() = bar.place()?.let { if (bar.top) it.y + it.height else it.y } ?: 0
        val showMenu = { model.toggle(PopupKind.TRAY_MENU, bar, bar.originX + x, item) }
        Chip(
            colors,
            onX = { x = it },
            onPrimary = { model.close(); model.tray.activate(item, screenX(), screenY(), showMenu) },
            onSecondary = { if (model.tray.hasMenu(item)) showMenu() else model.tray.contextMenu(item, screenX(), screenY()) },
            onMiddle = { model.tray.secondaryActivate(item, screenX(), screenY()) },
            onScroll = { dy -> model.tray.scroll(item, if (dy > 0) 1 else -1) },
            onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, item) { item.tooltip.ifBlank { item.title } } },
        ) {
            val picture = item.icon
            if (picture != null) {
                Image(picture, item.title, Modifier.size(bar.iconSize.dp), filterQuality = FilterQuality.High)
            } else {
                // No picture (yet): the program's first letter.
                Box(Modifier.size(bar.iconSize.dp).background(colors.accent.copy(alpha = 0.3f), RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                    BasicText(item.title.take(1).uppercase().ifEmpty { "•" }, style = TextStyle(color = colors.text, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

/** A hint below a part of the bar ([key] says which), after the pointer stayed a moment; [text] is asked then. */
internal fun BarModel.hint(on: Boolean, bar: Bar, x: Double, key: Any, text: () -> String) {
    val open = popup
    if (!on) {
        if (hintWanted === key) hintWanted = null
        if (open?.kind == PopupKind.TOOLTIP && open.hintKey == key) close()
        return
    }
    hintWanted = key
    if (open != null && open.kind != PopupKind.TOOLTIP) return
    // (A moment later: not for a pointer passing by.)
    wm.caulis.after(800) {
        if (hintWanted !== key) return@after
        val current = popup
        if (current != null && current.kind != PopupKind.TOOLTIP) return@after
        val words = tr(text().trim())
        if (words.isEmpty()) return@after
        val hint = Popup(PopupKind.TOOLTIP, bar, x).also {
            it.text = words.take(300)
            it.hintKey = key
        }
        popup = hint
        showPopup(hint)
    }
}

// The network's hint: what it is connected to, and the address it has.
private fun networkHint(model: BarModel): String {
    val n = model.status.network
    val ip = model.status.address?.let { "\nAddress $it" } ?: ""
    return when {
        model.network.wired -> "Wired connection$ip"
        n.kind == Network.Kind.WIFI -> "Wi-Fi: ${n.name.ifEmpty { "connected" }} · ${n.strength}%$ip"
        else -> "Not connected"
    }
}

private fun batteryHint(b: Battery): String = "${b.percent}% · " + when {
    b.full -> "fully charged"
    b.charging -> b.minutesLeft?.let { "charging, full in ${hoursOf(it)}" } ?: "charging"
    b.plugged -> "plugged in, held at its charge limit"
    else -> b.minutesLeft?.let { "${hoursOf(it)} left" } ?: "on battery"
}

// The keyboard layout ("EN", "RU"): a click or the wheel switches it.
@Composable
private fun LayoutModule(bar: Bar, model: BarModel, colors: BarColors, text: TextStyle) {
    val name = bar.keyboardLayout
    if (name.isEmpty()) return
    val caulis = model.wm.caulis
    Chip(colors, onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "layout") { "Keyboard: " + caulis.keyboardLayoutNames.joinToString(" · ") + " (a click switches)" } }, onPrimary = { caulis.switchKeyboardLayout(caulis.keyboardLayoutNow + 1); bar.look() },
        onScroll = { dy -> caulis.switchKeyboardLayout(caulis.keyboardLayoutNow + if (dy > 0) 1 else -1); bar.look() }) {
        BasicText(name, style = text.copy(fontWeight = FontWeight.SemiBold, fontSize = (bar.fontSize - 1).sp))
    }
}

@Composable
private fun BluetoothModule(bar: Bar, model: BarModel, colors: BarColors) {
    val bt = model.bluetooth
    if (!bt.available) return
    var x by remember { mutableStateOf(0.0) }
    val connected = bt.devices.any { it.connected }
    Chip(colors, onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "bluetooth") { when { !bt.powered -> "Bluetooth is off"; else -> bt.devices.filter { it.connected }.joinToString("\n") { d -> d.name + (d.battery?.let { " · $it%" } ?: "") }.ifEmpty { "Bluetooth is on, nothing connected" } } } }, onX = { x = it }, onPrimary = { model.toggle(PopupKind.BLUETOOTH, bar, bar.originX + x) }) {
        GlyphIcon(Glyph.BLUETOOTH, if (connected) colors.accent else if (bt.powered) colors.text else colors.dim, bar.iconSize.dp, slash = !bt.powered)
    }
}

@Composable
private fun NetworkModule(bar: Bar, model: BarModel, colors: BarColors, text: TextStyle) {
    val n = model.status.network
    var x by remember { mutableStateOf(0.0) }
    Chip(colors, onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "network") { networkHint(model) } }, onX = { x = it }, onPrimary = { model.toggle(PopupKind.NETWORK, bar, bar.originX + x) }) {
        when (if (model.network.wired) Network.Kind.WIRED else n.kind) {
            Network.Kind.WIFI -> GlyphIcon(Glyph.WIFI, colors.text, bar.iconSize.dp, level = n.strength / 100f)
            Network.Kind.WIRED -> GlyphIcon(Glyph.WIRED, colors.text, bar.iconSize.dp)
            Network.Kind.NONE -> GlyphIcon(Glyph.WIFI_OFF, colors.dim, bar.iconSize.dp)
        }
    }
}

@Composable
private fun VolumeModule(bar: Bar, model: BarModel, colors: BarColors, text: TextStyle) {
    val v = model.status.volume ?: return
    var x by remember { mutableStateOf(0.0) }
    Chip(
        colors,
        onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "volume") { model.status.volume?.let { v -> if (v.muted) "Sound is off" else "Volume ${v.percent}%" } + (model.sound.outputs.firstOrNull { it.isDefault }?.let { "\n" + it.description } ?: "") } },
        onX = { x = it },
        onPrimary = { model.toggle(PopupKind.VOLUME, bar, bar.originX + x) },
        onMiddle = { model.wm.caulis.run("volume mute"); model.status.refreshSoon() },
        onScroll = { dy -> model.scrollVolume(dy) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyph.SPEAKER, if (v.muted) colors.dim else colors.text, bar.iconSize.dp, level = v.percent / 100f, slash = v.muted)
            if (bar.showPercent) {
                Spacer(Modifier.width(5.dp))
                BasicText("${v.percent}%", style = text.copy(color = if (v.muted) colors.dim else colors.text))
            }
        }
    }
}

@Composable
private fun BatteryModule(bar: Bar, model: BarModel, colors: BarColors, text: TextStyle) {
    val b = model.status.battery ?: return
    var x by remember { mutableStateOf(0.0) }
    val fill = when {
        b.charging || b.full || b.plugged -> colors.accent
        b.percent <= 10 -> colors.danger
        b.percent <= 20 -> colors.warning
        else -> colors.text
    }
    Chip(colors, onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "battery") { batteryHint(b) + if (model.wm.caffeineOn) "\n" + tr("Caffeine: the screen stays on") else "" } }, onX = { x = it }, onPrimary = { model.toggle(PopupKind.BATTERY, bar, bar.originX + x) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (model.wm.caffeineUntil != null) {
                GlyphIcon(Glyph.COFFEE, colors.accent, bar.iconSize.dp)
                Spacer(Modifier.width(6.dp))
            }
            GlyphIcon(Glyph.BATTERY, colors.text, (bar.iconSize + 4).dp, level = b.percent / 100f, bolt = b.plugged, accent = fill)
            if (bar.showPercent) {
                Spacer(Modifier.width(4.dp))
                BasicText("${b.percent}%", style = text.copy(color = if (b.percent <= 10 && !b.charging) colors.danger else colors.text))
            }
        }
    }
}

@Composable
private fun PowerModule(bar: Bar, model: BarModel, colors: BarColors) {
    var x by remember { mutableStateOf(0.0) }
    Chip(colors, onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "power") { "Lock, sleep, log out, restart, shut down" } }, onX = { x = it }, onPrimary = { model.toggle(PopupKind.POWER, bar, bar.originX + x) }) {
        GlyphIcon(Glyph.POWER, colors.text, bar.iconSize.dp)
    }
}

// The menu's button: three lines; a click opens the launcher (again: closes it).
@Composable
private fun MenuModule(bar: Bar, model: BarModel, colors: BarColors) {
    Chip(colors, onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "menu") { tr("Programs") } }, onPrimary = { model.wm.enterLauncherOverlay() }) {
        GlyphIcon(Glyph.MENU, colors.text, (bar.iconSize + 1).dp)
    }
}

// The configuration's own texts (texts { ... }), each a part of its own.
@Composable
private fun TextsModule(bar: Bar, colors: BarColors, text: TextStyle) {
    for (t in bar.texts.filter { it.isNotBlank() }) Chip(colors) { BasicText(t, style = text, maxLines = 1) }
}

/** The fonts of the bar: the configured one, else the system's sans-serif. */
internal fun fontFamilyOf(name: String?): FontFamily = name?.let {
    org.jetbrains.skia.FontMgr.default.matchFamilyStyle(it, org.jetbrains.skia.FontStyle.NORMAL)?.let { face -> FontFamily(androidx.compose.ui.text.platform.Typeface(face)) }
} ?: FontFamily.SansSerif
