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
enum class PopupKind { CALENDAR, VOLUME, NETWORK, BATTERY, POWER, TRAY_MENU, TOOLTIP }

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
}

/**
 * Everything newm's bars share: the tray, what the computer says (battery, network, volume),
 * the colours of the scheme in use, the popup open now. Made once and started with the
 * compositor: a reload of the configuration keeps the tray and its programs.
 */
class BarModel(val wm: Layout) {
    val tray = Tray(wm.caulis)
    val status = Status(wm.caulis)

    var scheme by mutableStateOf(ColorScheme.DEFAULT)
        private set

    var popup by mutableStateOf<Popup?>(null)

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
        tray.start()
        status.start()
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

    fun colorsFor(bar: Bar): BarColors {
        val base = when (bar.theme) {
            "dark" -> BarColors.DARK
            "light" -> BarColors.LIGHT
            else -> if (scheme == ColorScheme.DARK) BarColors.DARK else BarColors.LIGHT
        }
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
        if (kind == PopupKind.TRAY_MENU && item != null) tray.menuOf(item) { entries -> if (popup === next) next.entries = entries }
        if (kind == PopupKind.VOLUME || kind == PopupKind.BATTERY || kind == PopupKind.NETWORK) status.refreshSoon()
    }

    fun close() {
        val open = popup ?: return
        popup = null
        if (open.kind == PopupKind.TRAY_MENU && open.item != null) tray.menuClosed(open.item)
    }

    /** Where [popup] is on the screen (desktop units), or null. */
    fun popupRect(popup: Popup): Rect? {
        val bar = popup.bar
        val place = bar.place() ?: return null
        val (w, h) = popupSize(popup)
        val area = bar.monitor.area
        val x = (popup.anchorX - w / 2.0).coerceIn(area.x + 8.0, area.right - 8.0 - w).toInt()
        val y = if (bar.top) place.y + place.height - bar.margin + 6 else place.y - h - 6 + bar.margin
        // (With room around it for its shadow.)
        return Rect(x - POPUP_SHADOW, y - POPUP_SHADOW, w + 2 * POPUP_SHADOW, h + 2 * POPUP_SHADOW)
    }

    private fun popupSize(popup: Popup): Pair<Int, Int> = when (popup.kind) {
        PopupKind.CALENDAR -> 292 to 282
        PopupKind.VOLUME -> 300 to (if (status.brightness != null) 142 else 96)
        PopupKind.NETWORK -> 280 to 100
        PopupKind.BATTERY -> 260 to 88
        PopupKind.POWER -> 240 to 192
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
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered && (onPrimary != null || onScroll != null)) colors.hover else Color.Transparent, tween(120))
    LaunchedEffect(hovered) { onHover?.invoke(hovered) }
    val density = LocalDensity.current
    Box(
        modifier
            .fillMaxHeight()
            .padding(vertical = 3.dp)
            .background(bg, RoundedCornerShape(9.dp))
            .hoverable(source)
            .onGloballyPositioned { c -> with(density) { onX(c.boundsInRoot().center.x.toDp().value.toDouble()) } }
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
            .shadow(5.dp, shape, ambientColor = Color(0x30000000), spotColor = Color(0x40000000))
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
        "network" -> NetworkModule(bar, model, colors, text)
        "volume" -> VolumeModule(bar, model, colors, text)
        "battery" -> BatteryModule(bar, model, colors, text)
        "power" -> PowerModule(bar, model, colors)
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
    Chip(colors, onPrimary = { model.wm.toggleOverview() }, onScroll = { dy -> model.wm.cycleViews(if (dy > 0) 1 else -1) }) {
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
    Chip(colors, onPrimary = { model.wm.toggleOverview() }, onMiddle = { model.wm.closeFocusedView() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Image(it, null, Modifier.size(16.dp), filterQuality = FilterQuality.High)
                Spacer(Modifier.width(7.dp))
            }
            BasicText(
                focused.title.ifEmpty { if (focused.appId.isEmpty()) "Desktop" else focused.appId },
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
    Chip(colors, onX = { x = it }, onPrimary = { model.toggle(PopupKind.CALENDAR, bar, bar.originX + x) }) {
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
    val items = model.tray.items.filter { it.status != "Passive" || bar.showPassive }
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
            onHover = { on -> model.hoverTooltip(on, bar, bar.originX + x, item) },
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

/** A tooltip for a tray icon, after the pointer stayed a moment. */
private fun BarModel.hoverTooltip(on: Boolean, bar: Bar, x: Double, item: TrayItem) {
    val open = popup
    if (!on) {
        if (open?.kind == PopupKind.TOOLTIP && open.item === item) close()
        return
    }
    if (open != null && open.kind != PopupKind.TOOLTIP) return
    wm.caulis.after(600) {
        val current = popup
        if (current != null && current.kind != PopupKind.TOOLTIP) return@after
        val text = item.tooltip.ifBlank { item.title }.trim()
        if (text.isEmpty()) return@after
        popup = Popup(PopupKind.TOOLTIP, bar, x, item).also { it.text = text.take(300) }
    }
}

// The keyboard layout ("EN", "RU"): a click or the wheel switches it.
@Composable
private fun LayoutModule(bar: Bar, model: BarModel, colors: BarColors, text: TextStyle) {
    val name = bar.keyboardLayout
    if (name.isEmpty()) return
    val caulis = model.wm.caulis
    Chip(colors, onPrimary = { caulis.switchKeyboardLayout(caulis.keyboardLayoutNow + 1); bar.look() },
        onScroll = { dy -> caulis.switchKeyboardLayout(caulis.keyboardLayoutNow + if (dy > 0) 1 else -1); bar.look() }) {
        BasicText(name, style = text.copy(fontWeight = FontWeight.SemiBold, fontSize = (bar.fontSize - 1).sp))
    }
}

@Composable
private fun NetworkModule(bar: Bar, model: BarModel, colors: BarColors, text: TextStyle) {
    val n = model.status.network
    var x by remember { mutableStateOf(0.0) }
    Chip(colors, onX = { x = it }, onPrimary = { model.toggle(PopupKind.NETWORK, bar, bar.originX + x) }) {
        when (n.kind) {
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
        onX = { x = it },
        onPrimary = { model.toggle(PopupKind.VOLUME, bar, bar.originX + x) },
        onMiddle = { model.wm.caulis.run("volume mute"); model.status.refreshSoon() },
        onScroll = { dy -> model.wm.caulis.run(if (dy < 0) "volume +5" else "volume -5"); model.status.refreshSoon() },
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
        b.charging || b.full -> colors.accent
        b.percent <= 10 -> colors.danger
        b.percent <= 20 -> colors.warning
        else -> colors.text
    }
    Chip(colors, onX = { x = it }, onPrimary = { model.toggle(PopupKind.BATTERY, bar, bar.originX + x) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyph.BATTERY, colors.text, (bar.iconSize + 4).dp, level = b.percent / 100f, bolt = b.charging, accent = fill)
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
    Chip(colors, onX = { x = it }, onPrimary = { model.toggle(PopupKind.POWER, bar, bar.originX + x) }) {
        GlyphIcon(Glyph.POWER, colors.text, bar.iconSize.dp)
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
