package newm.bar

import newm.lockShown
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.luminance
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

// topBar { wheel { up { ... }; down { ... }; left { ... }; right { ... } } }: what the wheel over the
// clock and the bar's empty space does, each way (by default the focus goes to the next window
// that way, as Super+H/J/K/L). A way without an action does nothing.
private val defaultWheel: Map<String, Any?> = mapOf<String, (newm.Layout) -> Unit>(
    "up" to { l -> l.move(0, -1) }, "down" to { l -> l.move(0, 1) },
    "left" to { l -> l.move(-1, 0) }, "right" to { l -> l.move(1, 0) },
)
private val confWheelTop = newm.configuredValue("panels.top_bar.native.wheel", defaultWheel)
private val confWheelBottom = newm.configuredValue("panels.bottom_bar.native.wheel", defaultWheel)

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
enum class PopupKind { CALENDAR, VOLUME, NETWORK, BLUETOOTH, BATTERY, POWER, COLORS, QUICK, NOTIFICATIONS, TRAY_MENU, TOOLTIP }

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

    /** The media players (MPRIS), the processor/memory/temperature and the weather: each started only while a bar shows it. */
    val media = Media(wm.caulis)
    val system = SystemStats({ action -> wm.caulis.post { action() } })
    var weather by mutableStateOf<String?>(null)
        private set
    private var weatherDue = 0L
    private var weatherAsking = false

    /** newm's notifications: their cards and the bell's history (started by the layout, with or without this bar). */
    val notifications = NotificationCenter(this)

    /** The popups' part of the screen (a click on it gives it the keyboard: a password). */
    internal var popupWidget: dev.caulis.compose.ComposeWidget? = null

    var scheme by mutableStateOf(ColorScheme.DEFAULT)
        private set

    var popup by mutableStateOf<Popup?>(null)

    /** The popup on screen: the open one, or the one fading away. */
    var shownPopup by mutableStateOf<Popup?>(null)
        internal set

    // The battery's percent at the bar, as chosen in its popup (~/.local/state/newm/battery-percent; null: the configuration's).
    private val percentFile by lazy {
        val state = System.getenv("XDG_STATE_HOME")?.takeIf { it.startsWith("/") } ?: "${System.getenv("HOME") ?: System.getProperty("user.home")}/.local/state"
        java.nio.file.Path.of(state, "newm", "battery-percent")
    }
    var batteryPercent by mutableStateOf(runCatching { java.nio.file.Files.readString(percentFile).trim().toBooleanStrictOrNull() }.getOrNull())
        private set

    /** Shows the battery's percent at the bar or not (kept). */
    fun showBatteryPercent(on: Boolean) {
        batteryPercent = on
        runCatching {
            java.nio.file.Files.createDirectories(percentFile.parent)
            java.nio.file.Files.writeString(percentFile, on.toString())
        }
    }

    /** The computer can hibernate (the power menu offers it). */
    var canHibernate by mutableStateOf(false)
        private set

    /** The card in the middle of the screen (a network or device came or went). */
    var card by mutableStateOf<OsdCard?>(null)

    /** The card's coming and going (done by the compositor). */
    val cardFade = Fade("osd", inMillis = 260, outMillis = 300)

    // The brightness asked for last and when (quick wheel steps add up before the system says it back).
    private var brightnessWanted = 0
    private var brightnessAt = 0L

    /** The screen's brightness [by] percent brighter (darker when negative), shown at the bottom. */
    fun stepBrightness(by: Int) {
        val now = System.currentTimeMillis()
        val base = if (now - brightnessAt < 1500) brightnessWanted else status.brightness ?: return
        val next = (base + by).coerceIn(brightnessMinimum(), 100)
        brightnessWanted = next
        brightnessAt = now
        setBrightness(this, next / 100f)
        showLevel(Glyph.BRIGHTNESS, "Brightness", next / 100.0)
    }

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
    val popupFade = Fade("popups", inMillis = 280, outMillis = 180)

    /** The colours made from the wallpaper (or a colour of yours); null: the classic Nord ones. */
    var palette by mutableStateOf<dev.caulis.core.appearance.Palette?>(null)

    /** The look of newm's own in use (colors.source = "jadefire"), or null. */
    var theme by mutableStateOf<NewmTheme?>(null)
        private set

    /** Makes the palette anew (the wallpaper, the configuration or the colours menu changed) and writes it out. */
    fun refreshPalette() {
        val next = NewmColors.palette(wm.wallpaperSeed)
        val look = NewmColors.theme()
        if (next == null && palette != null && look == null) log.warn("The colours went back to the plain ones (no wallpaper colour now)")
        if (next != palette || look != theme) {
            palette = next
            theme = look
            NewmColors.export(next, look)
            wm.focusBorders.update()
            applyNoticeStyle()
        }
    }

    /**
     * The compositor's notices (a mistake in the configuration, a program's error) in the bar's
     * look: its colours (the wallpaper's), light or dark as everything, its soft corners.
     */
    fun applyNoticeStyle() {
        val c = colorsNow()
        fun of(x: Color, alpha: Float = x.alpha) = dev.caulis.render.Color(x.red, x.green, x.blue, alpha)
        val dark = wm.caulis.colorSchemeNow == ColorScheme.DARK
        // (Where the notifications' cards are — below the bar, at the right, by default — as wide.)
        val corner = cardsCorner()
        val width = cardsWidth()
        noticePlace = Triple(corner, width, cardsGap())
        val cards = dev.caulis.core.NoticeStyle.cards(width = width, dark = dark)
        val reach = if (corner.top) wm.topBarReach() else wm.barsNow().filter { !it.top }.mapNotNull { b -> b.place()?.let { b.monitor.area.bottom - it.y } }.maxOrNull() ?: 0
        wm.caulis.noticeStyle = cards.copy(
            placement = dev.caulis.core.NoticePlacement.Toast(corner.notices, width = width, margin = (reach + cardsGap()).coerceIn(14, 500)),
            radius = 18,
            padding = 14,
            info = dev.caulis.core.NoticeColors(of(c.accent, 1f), of(c.text, 1f)),
            warning = dev.caulis.core.NoticeColors(of(c.warning, 1f), of(c.text, 1f)),
            error = dev.caulis.core.NoticeColors(of(c.danger, 1f), of(c.text, 1f)),
            painter = dev.caulis.core.CardNoticePainter(of(c.island, 0.97f), of(c.text, 1f)),
        )
    }

    // Where the notices were put last (notifications { corner; width; gap }): a reload changing it puts them anew.
    private var noticePlace: Triple<CardsCorner, Int, Int>? = null

    // The part of the bar the pointer is on, waiting for its hint.
    internal var hintWanted: Any? = null

    /** The on-screen display (a level at the bottom) and a toast (words in the middle) shown now. */
    var osd by mutableStateOf<OsdShow?>(null)
    var toast by mutableStateOf<OsdShow?>(null)

    /** The time for the clock: changed when the clock's text changes (each minute, or second when it shows seconds), and each day. */
    var now by mutableStateOf(LocalDateTime.now())
        private set

    private var started = false

    /** Starts the tray and the status readers (once; at the compositor's start). */
    fun start() {
        if (started) return
        started = true
        scheme = wm.caulis.colorSchemeNow
        wm.caulis.onColorSchemeChanged {
            scheme = it
            applyNoticeStyle()
        }
        applyNoticeStyle()
        network.onConnection = { connectionChanged(it) }
        network.onPasswordWanted = { askPassword(it) }
        network.networksShown = { popup?.kind == PopupKind.NETWORK || popup?.kind == PopupKind.QUICK }
        bluetooth.onConnection = { connectionChanged(it) }
        tray.start()
        status.start()
        // Hibernation offered only where the computer can (logind says "yes").
        wm.caulis.systemBus?.call("org.freedesktop.login1", dev.caulis.core.dbus.ObjectPath("/org/freedesktop/login1"), "org.freedesktop.login1.Manager", "CanHibernate") { reply ->
            canHibernate = reply.body.firstOrNull() == "yes"
        }
        // The charger plugged in or out, the level changed: read at once (not at the next look, seconds later).
        // (UPower's own signals only: another program could send the same ones.)
        wm.caulis.systemBus?.let { bus ->
            val upower = NameWatch(bus, "org.freedesktop.UPower") { owner -> log.trace { "UPower ${if (owner != null) "is running" else "is not running"}" } }
            bus.onSignal(PROPERTIES_IFACE, "PropertiesChanged", sender = "org.freedesktop.UPower") { m ->
                if (upower.sent(m) && m.path?.startsWith("/org/freedesktop/UPower") == true) status.refreshSoon()
            }
        }
        network.start()
        bluetooth.start()
        powerProfiles.start()
        wm.every(1000) {
            // The clock changes only when what it shows would: once a minute with "HH:mm" (the
            // bar is not drawn anew every second for seconds nobody sees), each second with seconds shown.
            val t = LocalDateTime.now()
            if (t.toLocalDate() != now.toLocalDate() || clockPatterns.any { clockText(it, t) != clockText(it, now) }) now = t
            refreshModules()
            followModules()
            watchBattery()
            if (noticePlace != Triple(cardsCorner(), cardsWidth(), cardsGap())) applyNoticeStyle()
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
        palette?.let { NewmColors.barColors(it, dark) } ?: NewmColors.plain(dark)
    }

    // --- the volume from the bar's wheel and the keys: steps added up, one change at a time ---

    private var wheel = 0f

    // The volume asked for last and when (compositor's thread: quick steps add up before the system says it back).
    private var volumeWanted = 0
    private var volumeAt = 0L

    // The value the worker is to set next (-1: none waiting); a step while one waits only changes it.
    private val volumeNext = java.util.concurrent.atomic.AtomicInteger(-1)
    private val volumeWorker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "newm-volume").apply { isDaemon = true } }

    /** The wheel over the volume: [dy] lines (up: louder), sound.wheel_step percent a notch (2). */
    fun scrollVolume(dy: Float) {
        wheel -= dy
        val steps = wheel.toInt()
        if (steps == 0) return
        wheel -= steps
        changeVolume(steps * confVolumeStep())
    }

    /** The volume by [delta] percent: shown at once, set by one command for all the steps that came meanwhile. */
    fun changeVolume(delta: Int) {
        val time = System.currentTimeMillis()
        val now = if (time - volumeAt < 1500) volumeWanted else status.volume?.percent ?: return
        // Up to the popup's most (sound.max_volume); a step up never makes it quieter (set louder elsewhere).
        val next = if (delta > 0) maxOf(now, (now + delta).coerceAtMost(maxVolume())) else (now + delta).coerceAtLeast(0)
        volumeWanted = next
        volumeAt = time
        status.showVolume(next)
        showLevel(Glyph.SPEAKER, "Volume $next %", next / 100.0)
        // (A change waiting takes the new value; none waiting: the worker gets one.)
        if (volumeNext.getAndSet(next) != -1) return
        volumeWorker.execute {
            val target = volumeNext.getAndSet(-1)
            if (target == -1) return@execute
            // sound { setVolume = "..." }: the configuration's command; else wpctl, else pactl.
            val own = confSetVolume()
            val done = if (own != null) commandSucceeds(fillIn(own, "percent" to target)) else {
                runQuietly("wpctl", "set-mute", "@DEFAULT_AUDIO_SINK@", "0")
                runQuietly("wpctl", "set-volume", "@DEFAULT_AUDIO_SINK@", "$target%") || runQuietly("pactl", "set-sink-volume", "@DEFAULT_SINK@", "$target%")
            }
            if (done) log.trace { "Volume $target%" } else log.warn(if (own != null) "The volume cannot be set: \"$own\" failed" else "The volume cannot be set (neither wpctl nor pactl answered)")
            Thread.sleep(40)
            status.refreshSoon()
        }
    }

    fun colorsFor(bar: Bar): BarColors {
        val dark = when (bar.theme) {
            "dark" -> true
            "light" -> false
            else -> scheme == ColorScheme.DARK
        }
        val base = palette?.let { NewmColors.barColors(it, dark) } ?: NewmColors.plain(dark)
        return base.copy(
            island = bar.islandColor?.let(::composeColor) ?: base.island,
            text = bar.textColor?.let(::composeColor) ?: base.text,
            accent = bar.accentColor?.let(::composeColor) ?: base.accent,
        )
    }

    /** Opens [kind] below [anchorX] of [bar], or closes it when it is open already. */
    fun toggle(kind: PopupKind, bar: Bar, anchorX: Double, item: TrayItem? = null) {
        // (Nothing of the bar opens over the lock screen.)
        if (wm.lockShown()) return close()
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
        // (A screen narrower than the popup: at its left edge, never an error.)
        val x = (popup.anchorX - w / 2.0).coerceIn(area.x + 8.0, maxOf(area.x + 8.0, area.right - 8.0 - w)).toInt()
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
        PopupKind.POWER -> 240 to (confPowerEntries().size.coerceAtLeast(1) * 34 + 24)
        PopupKind.QUICK -> 296 to (if (status.brightness != null) 280 else 230) + (if (status.volumeUnavailable != null) 24 else 0)
        PopupKind.COLORS -> 340 to (34 + 6 * 42 + 28)
        PopupKind.NOTIFICATIONS -> 380 to (34 + 38 + 26 + notifications.history.items.let { items ->
            if (items.isEmpty()) 80 else (items.size * 50 + items.map { it.entry.ifBlank { it.app }.lowercase() }.distinct().size * 26).coerceAtMost(7 * 56 + 28)
        }).coerceAtMost(POPUP_TALLEST)
        PopupKind.TOOLTIP -> (popup.text.lines().maxOfOrNull { it.length } ?: 10).coerceIn(4, 60) * 7 + 28 to popup.text.lines().size * 18 + 18
        PopupKind.TRAY_MENU -> {
            val shown = (popup.path.lastOrNull()?.children ?: popup.entries).orEmpty()
            val rows = shown.sumOf { if (it.separator) 9 else 30 } + (if (popup.path.isNotEmpty()) 30 else 0)
            val longest = shown.maxOfOrNull { it.label.length } ?: 12
            (longest * 8 + 64).coerceIn(180, 380) to (rows + 14).coerceIn(44, 600)
        }
    }

    /** The bar's text of the clock ([pattern], java.time's), in the language of newm's words. */
    fun clock(pattern: String): String {
        if (pattern !in clockPatterns) clockPatterns += pattern
        return clockText(pattern, now)
    }

    // The clock patterns the bars use (what [now] changes for).
    private val clockPatterns = java.util.concurrent.CopyOnWriteArraySet<String>()

    private fun clockText(pattern: String, at: LocalDateTime): String = try {
        at.format(DateTimeFormats.of(pattern))
    } catch (_: IllegalArgumentException) {
        at.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    // --- modules of the configuration's own (topBar { module("name") { } }) ---

    /** Their texts now, by name. */
    val moduleTexts = androidx.compose.runtime.mutableStateMapOf<String, String>()

    // When each is due next (ms), and those whose command runs now.
    private val moduleDue = HashMap<String, Long>()
    private val moduleRunning = java.util.Collections.synchronizedSet(HashSet<String>())

    // Each second: the modules whose time has come ask their text again (a command away from the compositor's thread).
    private fun refreshModules() {
        val now = System.currentTimeMillis()
        val all = wm.barsNow().flatMap { it.modules.entries }.distinctBy { it.key }
        for ((name, spec) in all) {
            val m = spec as? Map<*, *> ?: continue
            if (now < (moduleDue[name] ?: 0L)) continue
            val every = ((m["every_seconds"] as? Number)?.toDouble() ?: 5.0).coerceAtLeast(0.5)
            moduleDue[name] = now + (every * 1000).toLong()
            val text = m["text"]
            val command = m["command"] as? String
            when {
                text is Function0<*> -> {
                    val shown = try { text()?.toString().orEmpty() } catch (e: Exception) { log.warn("The module $name's text failed: $e"); continue }
                    if (moduleTexts[name] != shown) moduleTexts[name] = shown
                }
                text != null -> if (moduleTexts[name] != text.toString()) moduleTexts[name] = text.toString()
                command != null -> {
                    if (!moduleRunning.add(name)) continue
                    val timeout = (m["timeout_seconds"] as? Number)?.toLong() ?: 5L
                    inBackground {
                        try {
                            // Its first line (a failed command's output too: a script may say something and end with 1); nothing on a timeout.
                            val out = commandOutput(command, timeout.coerceAtLeast(1), needSuccess = false)?.lines()?.firstOrNull { it.isNotBlank() }?.trim()
                            log.trace { "Module $name: \"$out\"" }
                            if (out != null) wm.caulis.post { if (moduleTexts[name] != out) moduleTexts[name] = out }
                        } finally {
                            moduleRunning.remove(name)
                        }
                    }
                }
            }
        }
    }

    // --- newm's modules that read something only while shown: media, cpu/memory/temperature, weather ---

    // Each second: what the bars show now starts (or stops) what they need.
    private fun followModules() {
        val bars = wm.barsNow()
        val shown = bars.flatMap { it.allModules }.toSet()
        if ("media" in shown) media.start()
        val systemBar = bars.firstOrNull { b -> b.allModules.any { it == "cpu" || it == "memory" || it == "temperature" } }
        if (systemBar != null && !system.running) {
            system.sensor = systemBar.temperatureSensor
            system.start(systemBar.systemEverySeconds.toLong())
        } else if (systemBar == null && system.running) {
            log.info { "No bar shows the processor, memory or temperature: no longer read" }
            system.stop()
        }
        val weatherBar = bars.firstOrNull { "weather" in it.allModules }
        val now = System.currentTimeMillis()
        if (weatherBar != null && !weatherAsking && now >= weatherDue) {
            weatherDue = now + weatherBar.weatherEveryMinutes * 60_000L
            weatherAsking = true
            val url = weatherUrl(weatherBar.weatherUrl, weatherBar.weatherPlace)
            // (Only the place is sent, as the configuration says; nothing else of the computer.)
            log.info { "Asking the weather (every ${weatherBar.weatherEveryMinutes} min)" }
            inBackground {
                val text = try { fetchWeather(url) } catch (e: Exception) { log.trace { "The weather: $e" }; null }
                wm.caulis.post {
                    weatherAsking = false
                    if (text != null) weather = text else if (weather == null) weatherDue = System.currentTimeMillis() + 5 * 60_000L
                }
            }
        }
    }

    // --- the battery nearly empty: battery { onCritical { } }, once each time ---

    private var criticalDone = false

    private fun watchBattery() {
        val b = status.battery ?: return
        val critical = !b.charging && !b.plugged && b.percent <= confBatteryCritical()
        if (critical && !criticalDone) {
            criticalDone = true
            log.warn("The battery is nearly empty: ${b.percent}%")
            runAction(confBatteryOnCritical(), wm, "battery.on_critical")
        } else if (!critical) criticalDone = false
    }

    // Sums of the wheel over modules whose wheel the configuration chose (by bar and module): a notch at a time.
    internal val wheelSums = HashMap<String, Float>()

    /**
     * The whole steps the wheel made over [key] with [dy] lines more (mouse { barWheelLines }
     * lines a step, 1): a hi-res wheel's and a touchpad's small moves add up to one; the rest is kept.
     */
    internal fun notches(key: String, dy: Float): Int {
        val lines = confBarWheelLines().toFloat().coerceIn(0.1f, 20f)
        val sum = (wheelSums[key] ?: 0f) + dy / lines
        val steps = sum.toInt()
        wheelSums[key] = sum - steps
        // (A fling of a touchpad: a few steps at most.)
        return steps.coerceIn(-5, 5)
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
                    val change = event.changes.firstOrNull()
                    val dy = change?.scrollDelta?.y ?: 0f
                    if (dy != 0f && onScroll != null) {
                        onScroll.invoke(dy)
                        change?.consume() // (its own wheel: not the bar's as well)
                    }
                }
                else -> {}
            }
        }
    }
}

/**
 * The wheel over the bar where nothing has a wheel of its own (the clock, the empty space):
 * what the configuration says for each way (topBar { wheel { } }) — by default the focus goes to
 * the next window that way, a horizontal wheel left and right, the usual one up and down.
 */
private fun Modifier.wheelMovesFocus(model: BarModel, top: Boolean): Modifier = pointerInput(model, top) {
    var x = 0f
    var y = 0f
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
            if (event.type != PointerEventType.Scroll) continue
            val change = event.changes.firstOrNull() ?: continue
            if (change.isConsumed) continue
            // Added up: a mouse's notch is one step, a touchpad's small moves make one together.
            x += change.scrollDelta.x
            y += change.scrollDelta.y
            val sx = x.toInt()
            val sy = y.toInt()
            if (sx == 0 && sy == 0) continue
            x -= sx
            y -= sy
            val way = when {
                sx > 0 -> "right"
                sx < 0 -> "left"
                sy > 0 -> "down"
                else -> "up"
            }
            @Suppress("UNCHECKED_CAST")
            val action = (if (top) confWheelTop() else confWheelBottom())[way] as? (newm.Layout) -> Unit ?: continue
            log.trace { "The wheel on the bar: $way" }
            runCatching { action(model.wm) }.onFailure { log.warn("The bar's wheel ($way) failed: $it") }
            change.consume()
        }
    }
}

/** What a module does on a click of each button and on the wheel ([scroll] gets the wheel's lines: down is more). */
private class Clicks(
    val click: (() -> Unit)? = null,
    val right: (() -> Unit)? = null,
    val middle: (() -> Unit)? = null,
    val scroll: ((Float) -> Unit)? = null,
) {
    /** A slot the configuration changed (topBar { on(name) { } }): the hint does not tell newm's own any more. */
    var changed: Set<String> = emptySet()
}

/**
 * newm's [defaults] for module [name], with what the configuration says instead
 * (topBar { on(name) { middle { ... } } }; "on": { name: { "middle": "..." } }): each of click,
 * middle, right, scroll_up and scroll_down an action, or null for nothing. A slot not given
 * stays newm's (the right button: as a click, unless it has its own).
 */
private fun BarModel.clicksOf(bar: Bar, name: String, defaults: Clicks): Clicks {
    val conf = bar.clicks[name] as? Map<*, *> ?: return defaults
    val where = "${if (bar.top) "top" else "bottom"}_bar.on.$name"
    fun slot(key: String, default: (() -> Unit)?): (() -> Unit)? =
        if (key !in conf) default else actionOf(conf[key], wm, "$where.$key")?.let { run -> { log.trace { "$where.$key" }; runAction(run, wm, "$where.$key") } }
    // The wheel: a notch at a time (a touchpad's small moves add up), up or down.
    val up = conf["scroll_up"]
    val down = conf["scroll_down"]
    val scroll: ((Float) -> Unit)? = if ("scroll_up" !in conf && "scroll_down" !in conf) defaults.scroll else { dy ->
        val steps = notches("${bar.top}/$name", dy)
        val action = if (steps > 0) down else up
        repeat(Math.abs(steps)) { runAction(action, wm, "$where.${if (steps > 0) "scroll_down" else "scroll_up"}") }
    }
    return Clicks(
        click = slot("click", defaults.click),
        // (Nothing given for the right button: an empty action, not the click's.)
        right = slot("right", defaults.right ?: defaults.click) ?: {},
        middle = slot("middle", defaults.middle),
        scroll = scroll,
    ).also { it.changed = conf.keys.map { k -> k.toString() }.toSet() }
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
    val bg by animateColorAsState(if (hovered && (onPrimary != null || onScroll != null)) colors.hover else Color.Transparent, newm.NewmAnimations.spec("hover", 120))
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

/** A chip whose buttons and wheel do what [clicks] says. */
@Composable
private fun Chip(colors: BarColors, clicks: Clicks, onX: (Double) -> Unit = {}, onHint: ((Boolean, Double) -> Unit)? = null, content: @Composable () -> Unit) =
    Chip(colors, onX = onX, onPrimary = clicks.click, onSecondary = clicks.right, onMiddle = clicks.middle, onScroll = clicks.scroll, onHint = onHint, content = content)

/** An island: a rounded, see-through piece of the bar (what is behind it is blurred); in the other looks only a group of modules. */
@Composable
private fun Island(bar: Bar, colors: BarColors, index: Int, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(bar.cornerRadius.dp)
    val own = bar.style == "islands"
    Row(
        Modifier
            .fillMaxHeight()
            .then(if (own) Modifier.softShadow(bar.cornerRadius.dp, 4.dp, 0.10f).background(colors.island, shape).border(1.dp, colors.edge, shape) else Modifier)
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
    val style = bar.style
    // Texts alone on the wallpaper (minimal): a soft shadow keeps them readable on any picture.
    val shadow = if (style == "minimal") androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(
        if (colors.text.luminance() > 0.5f) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.7f), Offset(0f, 1f), 5f)) else TextStyle()
    val text = TextStyle(color = colors.text, fontSize = bar.fontSize.sp, fontFamily = family, fontWeight = FontWeight.Medium).merge(shadow)
    val pad = bar.margin.dp
    // solid, macos: one strip along the screen's edge, a hairline on its inner side; floating: one rounded bar in the gaps.
    val strip = style == "solid" || style == "macos"
    val edge = colors.edge
    val stripLook = if (!strip) Modifier else Modifier.background(colors.island).drawBehind {
        val y = if (bar.top) size.height - 0.5f else 0.5f
        drawLine(edge, Offset(0f, y), Offset(size.width, y), 1f)
    }
    val barShape = RoundedCornerShape(bar.cornerRadius.dp)
    val floatingLook = if (style != "floating") Modifier else Modifier.softShadow(bar.cornerRadius.dp, 6.dp, 0.14f).background(colors.island, barShape).border(1.dp, colors.edge, barShape)
    Box(Modifier.fillMaxSize().then(stripLook).wheelMovesFocus(model, bar.top).padding(start = pad, end = pad, top = if (bar.top) pad else 0.dp, bottom = if (bar.top) 0.dp else pad)) {
        Box(Modifier.fillMaxWidth().height(bar.height.dp).then(floatingLook).padding(horizontal = if (strip || style == "floating") 4.dp else 0.dp)) {
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
        "quick" -> QuickModule(bar, model, colors)
        "notifications" -> NotificationsModule(bar, model, colors)
        "texts" -> TextsModule(bar, colors, text)
        "media" -> MediaModule(bar, model, colors, text)
        "cpu", "memory", "temperature" -> SystemModule(name, bar, model, colors, text)
        "screens" -> ScreensModule(bar, model, colors, text)
        "weather" -> WeatherModule(bar, model, colors, text)
        // A module of the configuration's own, else the name as it is.
        else -> if (name in bar.modules) CustomModule(name, bar, model, colors, text) else Chip(colors) { BasicText(name, style = text) }
    }
}

// The wall: each window a tile, the focused one in the accent colour, the screen's part outlined.
@Composable
private fun WallModule(bar: Bar, model: BarModel, colors: BarColors) {
    val wall = bar.wall
    val mapH = (bar.height - 16).coerceAtLeast(10).toFloat()
    val unit = mapH / wall.height
    val mapW = (wall.width * unit * wall.aspect).coerceIn(mapH * 0.8f, mapH * 5f)
    val clicks = model.clicksOf(bar, "wall", Clicks(click = { model.wm.toggleOverview() }, scroll = { dy -> val n = model.notches("${bar.top}/wall", dy); repeat(Math.abs(n)) { model.wm.cycleViews(if (n > 0) 1 else -1) } }))
    Chip(colors, clicks, onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "wall") { if (clicks.changed.isEmpty()) "All windows · the wheel goes through them" else "All windows" } }) {
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
    // (The icon's name is looked up away from the compositor's thread; a late one of a window no longer focused is dropped.)
    val shownFor = remember { java.util.concurrent.atomic.AtomicReference("") }
    LaunchedEffect(focused.appId) {
        icon = null
        val appId = focused.appId
        shownFor.set(appId)
        if (appId.isNotEmpty()) IconTheme.loadApp(appId, TRAY_ICON_PIXELS, post = { model.wm.caulis.post { it() } }) { picture ->
            if (shownFor.get() == appId) icon = picture else log.trace { "The icon of $appId came late: not shown" }
        }
    }
    val clicks = model.clicksOf(bar, "window", Clicks(click = { model.wm.toggleOverview() }, middle = { model.wm.closeFocusedView() }))
    Chip(colors, clicks, onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "window") { focused.title.takeIf { it.isNotEmpty() }?.let(::verbatim) ?: "No window" } }) {
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
    val clicks = model.clicksOf(bar, "clock", Clicks(click = { model.toggle(PopupKind.CALENDAR, bar, bar.originX + x) }))
    Chip(colors, clicks, onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "clock") { bar.clockHint.takeIf { it.isNotBlank() }?.let { verbatim(model.clock(it)) } ?: "" } }, onX = { x = it }) {
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
            onScroll = { dy -> val n = model.notches("tray/${item.key}", dy); if (n != 0) model.tray.scroll(item, n) },
            onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, item) { verbatim(item.tooltip.ifBlank { item.title }) } },
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
    wm.caulis.after(bar.hintDelayMs.toLong()) {
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

// The network's hint: what it is connected to, and the address it has (English: the hint translates it line by line).
private fun networkHint(model: BarModel): String {
    val n = model.status.network
    val ip = model.status.address?.let { "\nAddress ${verbatim(it)}" } ?: ""
    return when {
        model.network.wired -> "Wired connection$ip"
        n.kind == Network.Kind.WIFI -> "Wi-Fi: ${n.name.takeIf { it.isNotEmpty() }?.let(::verbatim) ?: "connected"} · ${verbatim("${n.strength}%")}$ip"
        else -> "Not connected"
    }
}

// The battery's hint, translated here (the percent first, then the words of its popup), kept as it is after.
private fun batteryHint(b: Battery): String = verbatim("${b.percent}% · " + batteryWords(b))

// The keyboard layout ("EN", "RU"): a click or the wheel switches it.
@Composable
private fun LayoutModule(bar: Bar, model: BarModel, colors: BarColors, text: TextStyle) {
    val name = bar.keyboardLayout
    if (name.isEmpty()) return
    val caulis = model.wm.caulis
    val clicks = model.clicksOf(bar, "layout", Clicks(click = { caulis.switchKeyboardLayout(caulis.keyboardLayoutNow + 1); bar.look() },
        scroll = { dy ->
            val n = model.notches("${bar.top}/layout", dy)
            if (n != 0) {
                caulis.switchKeyboardLayout(caulis.keyboardLayoutNow + n)
                bar.look()
            }
        }))
    Chip(colors, clicks, onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "layout") {
        val names = verbatim(caulis.keyboardLayoutNames.joinToString(" · "))
        if ("click" in clicks.changed) "Keyboard: $names" else "Keyboard: $names (a click switches)"
    } }) {
        BasicText(name, style = text.copy(fontWeight = FontWeight.SemiBold, fontSize = (bar.fontSize - 1).sp))
    }
}

@Composable
private fun BluetoothModule(bar: Bar, model: BarModel, colors: BarColors) {
    val bt = model.bluetooth
    if (!bt.available) return
    var x by remember { mutableStateOf(0.0) }
    val connected = bt.devices.any { it.connected }
    Chip(colors, model.clicksOf(bar, "bluetooth", Clicks(click = { model.toggle(PopupKind.BLUETOOTH, bar, bar.originX + x) })), onX = { x = it }, onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "bluetooth") { when { !bt.powered -> "Bluetooth is off"; else -> bt.devices.filter { it.connected }.joinToString("\n") { d -> verbatim(d.name + (d.battery?.let { " · $it%" } ?: "")) }.ifEmpty { "Bluetooth is on, nothing connected" } } } }) {
        GlyphIcon(Glyph.BLUETOOTH, if (connected) colors.accent else if (bt.powered) colors.text else colors.dim, bar.iconSize.dp, slash = !bt.powered)
    }
}

@Composable
private fun NetworkModule(bar: Bar, model: BarModel, colors: BarColors, text: TextStyle) {
    val n = model.status.network
    var x by remember { mutableStateOf(0.0) }
    Chip(colors, model.clicksOf(bar, "network", Clicks(click = { model.toggle(PopupKind.NETWORK, bar, bar.originX + x) })), onX = { x = it },
        onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "network") { networkHint(model) } }) {
        when (if (model.network.wired) Network.Kind.WIRED else n.kind) {
            Network.Kind.WIFI -> GlyphIcon(Glyph.WIFI, colors.text, bar.iconSize.dp, level = n.strength / 100f)
            Network.Kind.WIRED -> GlyphIcon(Glyph.WIRED, colors.text, bar.iconSize.dp)
            Network.Kind.NONE -> GlyphIcon(Glyph.WIFI_OFF, colors.dim, bar.iconSize.dp)
        }
    }
}

@Composable
private fun VolumeModule(bar: Bar, model: BarModel, colors: BarColors, text: TextStyle) {
    // (Not readable now — the sound server restarts, its tools fail: shown as unavailable, never hidden.)
    val v = model.status.volume
    val unavailable = model.status.volumeUnavailable
    if (v == null && unavailable == null) return
    var x by remember { mutableStateOf(0.0) }
    val clicks = model.clicksOf(bar, "volume", Clicks(
        click = { model.toggle(PopupKind.VOLUME, bar, bar.originX + x) },
        middle = { model.wm.caulis.run("volume mute"); model.status.refreshSoon() },
        scroll = { dy -> model.scrollVolume(dy) },
    ))
    Chip(
        colors,
        clicks,
        onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "volume") {
            model.status.volume?.let { v -> (if (v.muted) "Sound is off" else "Volume ${v.percent}%") + (model.sound.outputs.firstOrNull { it.isDefault }?.let { "\n" + verbatim(it.description) } ?: "") }
                ?: ("Sound is unavailable" + (model.status.volumeUnavailable?.let { "\n" + verbatim(it) } ?: ""))
        } },
        onX = { x = it },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (v == null) {
                GlyphIcon(Glyph.SPEAKER, colors.dim, bar.iconSize.dp, level = 0f, slash = true)
                return@Row
            }
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
        b.percent <= confBatteryCritical() -> colors.danger
        b.percent <= confBatteryLow() -> colors.warning
        else -> colors.text
    }
    Chip(colors, model.clicksOf(bar, "battery", Clicks(click = { model.toggle(PopupKind.BATTERY, bar, bar.originX + x) })), onX = { x = it },
        onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "battery") { batteryHint(b) + (if (model.wm.caffeineOn) "\n" + tr("Caffeine: the screen stays on") else "") + newm.idleReasonsLine(model.wm.idleInhibit.reasons) } }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // (Caffeine in the accent; a program keeping the screen on, the cup in the text's colour.)
            if (model.wm.caffeineUntil != null || model.wm.idleInhibit.reasons.isNotEmpty()) {
                GlyphIcon(Glyph.COFFEE, if (model.wm.caffeineUntil != null) colors.accent else colors.text, bar.iconSize.dp)
                Spacer(Modifier.width(6.dp))
            }
            GlyphIcon(Glyph.BATTERY, colors.text, (bar.iconSize + 4).dp, level = b.percent / 100f, bolt = b.plugged, accent = fill)
            if (model.batteryPercent ?: bar.showPercent) {
                Spacer(Modifier.width(4.dp))
                BasicText("${b.percent}%", style = text.copy(color = if (b.percent <= confBatteryCritical() && !b.charging) colors.danger else colors.text))
            }
        }
    }
}

// The media player: the one before, play or pause with the title (and artist) and a thin line of
// where it is, the next one. Nothing while nothing plays.
@Composable
private fun MediaModule(bar: Bar, model: BarModel, colors: BarColors, text: TextStyle) {
    val m = model.media.now ?: return
    val media = model.media
    // (Where it is: drawn anew once a second, only while it plays.)
    var tick by remember { mutableStateOf(System.nanoTime()) }
    LaunchedEffect(m.playing, m.atNanos) {
        while (m.playing) {
            kotlinx.coroutines.delay(1000)
            tick = System.nanoTime()
        }
    }
    if (m.canPrevious) Chip(colors, onPrimary = { media.press("Previous") }) { GlyphIcon(Glyph.SKIP_BACK, colors.text, (bar.iconSize - 3).dp) }
    val clicks = model.clicksOf(bar, "media", Clicks(click = { media.press("PlayPause") }, middle = { media.press("Next") },
        scroll = { dy -> val n = model.notches("${bar.top}/media", dy); if (n != 0) media.press(if (n > 0) "Next" else "Previous") }))
    Chip(colors, clicks, onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "media") {
        verbatim(listOf(m.title, m.artist).filter { it.isNotBlank() }.joinToString(" — ")) + "\n" + tr("%s · a click plays or pauses, the wheel goes on or back").replace("%s", verbatim(m.identity))
    } }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(if (m.playing) Glyph.PAUSE else Glyph.PLAY, colors.text, (bar.iconSize - 2).dp)
            Spacer(Modifier.width(7.dp))
            androidx.compose.foundation.layout.Column(Modifier.widthIn(max = bar.mediaWidth.dp)) {
                BasicText(listOf(m.title, m.artist).filter { it.isNotBlank() }.joinToString(" · "), style = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (m.lengthMicros > 0) {
                    val done = m.progress(tick)
                    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                        drawRoundRect(colors.text.copy(alpha = 0.18f), Offset.Zero, size, CornerRadius(1.dp.toPx()))
                        drawRoundRect(colors.accent, Offset.Zero, Size(size.width * done, size.height), CornerRadius(1.dp.toPx()))
                    }
                }
            }
        }
    }
    if (m.canNext) Chip(colors, onPrimary = { media.press("Next") }) { GlyphIcon(Glyph.SKIP_FORWARD, colors.text, (bar.iconSize - 3).dp) }
}

// The processor's use, the memory's, a temperature: a glyph and a number (a warning's colour when high). A click starts a system monitor.
@Composable
private fun SystemModule(name: String, bar: Bar, model: BarModel, colors: BarColors, text: TextStyle) {
    val load = model.system.load
    val (glyph, value, high) = when (name) {
        "cpu" -> Triple(Glyph.CPU, load.cpuPercent?.let { "$it%" }, (load.cpuPercent ?: 0) >= 90)
        "memory" -> Triple(Glyph.MEMORY, if (bar.memoryAs == "used") load.memoryUsedKib?.let { String.format(java.util.Locale.ROOT, "%.1f GB", it / 1048576.0) } else load.memoryPercent?.let { "$it%" }, (load.memoryPercent ?: 0) >= 90)
        else -> Triple(Glyph.THERMOMETER, load.celsius?.let { "$it°" }, (load.celsius ?: 0) >= bar.temperatureWarning)
    }
    if (value == null && name == "temperature") return
    val monitor = bar.systemMonitor.trim()
    val clicks = model.clicksOf(bar, name, Clicks(click = if (monitor.isEmpty()) null else { { startSystemMonitor(monitor, model.wm) } }))
    Chip(colors, clicks, onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, name) {
        when (name) {
            "cpu" -> "Processor"
            "memory" -> load.memoryUsedKib?.let { used -> tr("Memory: %s").replace("%s", String.format(java.util.Locale.ROOT, "%.1f / %.1f GB", used / 1048576.0, (load.memoryTotalKib ?: 0) / 1048576.0)) } ?: "Memory"
            else -> "Temperature"
        }
    } }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(glyph, if (high) colors.warning else colors.text, (bar.iconSize - 1).dp)
            Spacer(Modifier.width(5.dp))
            // (Digits of one width: the bar does not jump as they change.)
            BasicText(value ?: "…", style = text.copy(color = if (high) colors.warning else colors.text, fontFeatureSettings = "tnum"))
        }
    }
}

// What a click on the processor, memory or temperature starts: "auto" is btop in the terminal, else GNOME's system monitor.
private fun startSystemMonitor(monitor: String, wm: newm.Layout) {
    val command = if (monitor != "auto") monitor else when {
        newm.onPath("btop") -> "${newm.defaultTerminal()} -e btop"
        newm.onPath("gnome-system-monitor") -> "gnome-system-monitor"
        newm.onPath("htop") -> "${newm.defaultTerminal()} -e htop"
        else -> return log.info { "No system monitor installed (btop, gnome-system-monitor, htop): topBar { systemMonitor } names one" }
    }
    log.info { "Starting the system monitor" }
    wm.launch(command)
}

// The screens of the wall (screens { }): a dot for each, the one in view lit; a click goes to one.
@Composable
private fun ScreensModule(bar: Bar, model: BarModel, colors: BarColors, text: TextStyle) {
    val (count, now) = bar.screens
    if (count <= 0) return
    Chip(colors, model.clicksOf(bar, "screens", Clicks(scroll = { dy -> val n = model.notches("${bar.top}/screens", dy); if (n != 0) model.wm.focusScreen(n) })),
        onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "screens") { tr("Screen %s").replace("%s", "${now + 1} / $count") } }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (k in 0 until count) {
                val on = k == now
                Box(
                    Modifier.padding(horizontal = 2.dp).width(if (on) 18.dp else 12.dp).height(10.dp)
                        .background(if (on) colors.accent else colors.text.copy(alpha = 0.28f), RoundedCornerShape(3.dp))
                        .onClick { if (k != now) model.wm.focusScreen(k - now) },
                )
            }
        }
    }
}

// The weather (only when placed: it asks a service on the internet, see topBar { weatherUrl }).
@Composable
private fun WeatherModule(bar: Bar, model: BarModel, colors: BarColors, text: TextStyle) {
    val w = model.weather ?: return
    Chip(colors, model.clicksOf(bar, "weather", Clicks()), onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "weather") {
        if (bar.weatherPlace.isBlank()) "The weather here (wttr.in)" else tr("The weather in %s").replace("%s", verbatim(bar.weatherPlace))
    } }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyph.WEATHER, colors.text, (bar.iconSize - 1).dp)
            Spacer(Modifier.width(5.dp))
            BasicText(w, style = text, maxLines = 1)
        }
    }
}

@Composable
private fun PowerModule(bar: Bar, model: BarModel, colors: BarColors) {
    var x by remember { mutableStateOf(0.0) }
    Chip(colors, model.clicksOf(bar, "power", Clicks(click = { model.toggle(PopupKind.POWER, bar, bar.originX + x) })), onX = { x = it },
        onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "power") { "Lock, sleep, log out, restart, shut down" } }) {
        GlyphIcon(Glyph.POWER, colors.text, bar.iconSize.dp)
    }
}

// Quick actions: a grid; a click opens their tiles (caffeine, a screenshot, the clipboard, the scheme, ...).
@Composable
private fun QuickModule(bar: Bar, model: BarModel, colors: BarColors) {
    var x by remember { mutableStateOf(0.0) }
    // The wheel: the screen's brightness (brightness.wheel_step a notch).
    val clicks = model.clicksOf(bar, "quick", Clicks(click = { model.toggle(PopupKind.QUICK, bar, bar.originX + x) },
        scroll = { dy -> val n = model.notches("${bar.top}/quick", dy); if (n != 0) model.stepBrightness(-n * confBrightnessStep()) }))
    val wheelHint = clicks.changed.none { it.startsWith("scroll") }
    Chip(colors, clicks, onX = { x = it }, onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "quick") {
        "Quick actions" + (model.status.brightness?.let { "\n" + tr("Brightness %s").replace("%s", "$it%") + if (wheelHint) " · " + tr("the wheel changes it") else "" } ?: "") + (if (model.status.battery == null) newm.idleReasonsLine(model.wm.idleInhibit.reasons) else "")
    } }) {
        // Its own picture: caffeine shows beside the battery (here only on a computer without one).
        val cup = (model.wm.caffeineOn || model.wm.idleInhibit.reasons.isNotEmpty()) && model.status.battery == null
        GlyphIcon(if (cup) Glyph.COFFEE else Glyph.GRID, if (cup) colors.accent else colors.text, bar.iconSize.dp)
    }
}

// The bell: the notifications' history (a click), do not disturb (the middle button); a badge counts the new ones.
@Composable
private fun NotificationsModule(bar: Bar, model: BarModel, colors: BarColors) {
    val center = model.notifications
    var x by remember { mutableStateOf(0.0) }
    val unread = center.history.unread
    val quiet = center.doNotDisturb
    val clicks = model.clicksOf(bar, "notifications", Clicks(click = { model.toggle(PopupKind.NOTIFICATIONS, bar, bar.originX + x) }, middle = { center.setDoNotDisturb(!quiet) }))
    Chip(colors, clicks, onHint = { on, mx ->
        model.hint(on, bar, bar.originX + mx, "notifications") {
            val other = center.otherDaemon?.takeIf { !center.serving }
            listOfNotNull(
                when {
                    other != null -> tr("Notifications go to %s — newm's own are off").replace("%s", verbatim(other))
                    unread > 0 -> tr("New notifications: %s").replace("%s", "$unread")
                    else -> "No new notifications"
                },
                if (quiet) "Do not disturb is on" else null,
                if ("middle" !in clicks.changed) "The middle button: do not disturb" else null,
            ).joinToString("\n")
        }
    }, onX = { x = it }) {
        Box {
            GlyphIcon(if (quiet) Glyph.BELL_OFF else Glyph.BELL, if (quiet) colors.dim else colors.text, bar.iconSize.dp)
            if (unread > 0 && !quiet) Box(
                Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-5).dp).height(14.dp).widthIn(min = 14.dp)
                    .background(colors.accent, RoundedCornerShape(7.dp)).border(1.5.dp, colors.island.copy(alpha = 1f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(if (unread > 9) "9+" else "$unread", style = TextStyle(color = onAccent(colors), fontSize = 8.5.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

// The menu's button: three lines; a click opens the launcher (again: closes it).
@Composable
private fun MenuModule(bar: Bar, model: BarModel, colors: BarColors) {
    Chip(colors, model.clicksOf(bar, "menu", Clicks(click = { model.wm.enterLauncherOverlay() })), onHint = { on, mx -> model.hint(on, bar, bar.originX + mx, "menu") { tr("Programs") } }) {
        GlyphIcon(Glyph.MENU, colors.text, (bar.iconSize + 1).dp)
    }
}

// The configuration's own texts (texts { ... }), each a part of its own.
@Composable
private fun TextsModule(bar: Bar, colors: BarColors, text: TextStyle) {
    for (t in bar.texts.filter { it.isNotBlank() }) Chip(colors) { BasicText(t, style = text, maxLines = 1) }
}

/**
 * A module of the configuration's own (topBar { module("name") { } }): its icon and its text
 * (asked every few seconds), and what its clicks and wheel do. Nothing to show: not there.
 */
@Composable
private fun CustomModule(name: String, bar: Bar, model: BarModel, colors: BarColors, text: TextStyle) {
    val spec = bar.modules[name] as? Map<*, *> ?: return
    val shown = model.moduleTexts[name].orEmpty()
    val icon = spec["icon"] as? String
    if (shown.isEmpty() && icon.isNullOrBlank()) return
    val wm = model.wm
    val where = "${if (bar.top) "top" else "bottom"}_bar.modules.$name"
    fun action(key: String): (() -> Unit)? = spec[key]?.let { a -> { runAction(a, wm, "$where.$key") } }
    // The wheel: on_scroll gets the notches (down: more), else on_scroll_up / on_scroll_down.
    val onScroll = spec["on_scroll"]
    val up = spec["on_scroll_up"]
    val down = spec["on_scroll_down"]
    val scroll: ((Float) -> Unit)? = if (onScroll == null && up == null && down == null) null else { dy ->
        val steps = model.notches("${bar.top}/$name/own", dy)
        if (steps != 0) {
            @Suppress("UNCHECKED_CAST")
            when (onScroll) {
                null -> repeat(Math.abs(steps).coerceAtMost(5)) { runAction(if (steps > 0) down else up, wm, "$where.scroll") }
                is newm.LuaCallable -> runCatching { onScroll(steps) }.onFailure { log.warn("$where.on_scroll failed: $it") }
                is Function2<*, *, *> -> runCatching { (onScroll as (newm.Layout, Int) -> Unit)(wm, steps) }.onFailure { log.warn("$where.on_scroll failed: $it") }
                else -> log.warn("$where.on_scroll: a function of the notches is wanted")
            }
        }
    }
    val clicks = model.clicksOf(bar, name, Clicks(click = action("on_click"), right = action("on_right"), middle = action("on_middle"), scroll = scroll))
    Chip(colors, clicks) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!icon.isNullOrBlank()) {
                NamedIcon(icon, colors.text, bar.iconSize.dp, model)
                if (shown.isNotEmpty()) Spacer(Modifier.width(6.dp))
            }
            if (shown.isNotEmpty()) BasicText(shown, style = text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = bar.titleWidth.dp))
        }
    }
}

/**
 * An icon by its name: a Material symbol or an icon newm carries ("wifi", "coffee", "bell"),
 * else one of the icon theme ("firefox", "audio-volume-high") or a picture's file.
 */
@Composable
internal fun NamedIcon(name: String, color: Color, size: androidx.compose.ui.unit.Dp, model: BarModel) {
    if (carriesIcon("material/$name")) return SymbolIcon(name, color, size)
    if (carriesIcon(name)) return OwnIcon(name, color, size)
    var picture by remember(name) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(name) {
        IconTheme.load(name.removePrefix("file://"), TRAY_ICON_PIXELS, null, post = { model.wm.caulis.post { it() } }) { p ->
            if (p == null) log.warn("No icon \"$name\" (newm's own, the icon theme's, or a file)")
            picture = p
        }
    }
    picture?.let { Image(it, null, Modifier.size(size), filterQuality = FilterQuality.High) } ?: Spacer(Modifier.size(size))
}

/** The fonts of the bar: the configured one, else the system's sans-serif. */
internal fun fontFamilyOf(name: String?): FontFamily = name?.let {
    org.jetbrains.skia.FontMgr.default.matchFamilyStyle(it, org.jetbrains.skia.FontStyle.NORMAL)?.let { face -> FontFamily(androidx.compose.ui.text.platform.Typeface(face)) }
} ?: FontFamily.SansSerif
