package newm

import dev.caulis.wm.Caulis
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Marks newm's configuration language (so a section's names do not leak into the one inside it). */
@DslMarker
annotation class NewmConfigDsl

/**
 * A part of the configuration: its settings go into [values] under newm's own names (those
 * of config.py), so config.kts and config.py mean the same.
 */
@NewmConfigDsl
open class Section internal constructor(internal val values: MutableMap<String, Any?> = LinkedHashMap()) {

    // A setting under newm's [name]: unset (null) until the configuration gives it.
    protected fun <T> setting(name: String) = object : ReadWriteProperty<Any?, T?> {
        @Suppress("UNCHECKED_CAST")
        override fun getValue(thisRef: Any?, property: KProperty<*>): T? = values[name] as T?
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
            values[name] = value
        }
    }

    // The map under newm's [names] (one inside the other), made if not there yet.
    @Suppress("UNCHECKED_CAST")
    internal fun nested(vararg names: String): MutableMap<String, Any?> =
        names.fold(values) { map, name -> map.getOrPut(name) { LinkedHashMap<String, Any?>() } as MutableMap<String, Any?> }

    // A section inside this one, under newm's [name].
    protected fun <S : Section> section(name: String, make: (MutableMap<String, Any?>) -> S, configure: S.() -> Unit) {
        make(nested(name)).configure()
    }
}

/**
 * The configuration (config.kts): everything newm can be told, as sections. Read top to
 * bottom: settings set twice keep the last value.
 *
 * ```kotlin
 * view { cornerRadius = 20; padding = 8 }
 * focus { color = "#81A1C1AA" }
 * keys {
 *     "L-Return" { launch("alacritty") }
 *     "L-h" { move(-1, 0) }
 * }
 * ```
 */
class NewmConfig(
    /** The compositor underneath: everything Caulis can do (for what newm itself does not have). */
    val caulis: Caulis,
) : Section() {

    /** How long animations take, in seconds (moving, zooming, opening). */
    var animTime by setting<Double>("anim_time")

    /** How long newm takes to fade in at the start and out at the end, in seconds. */
    var blendTime by setting<Double>("blend_time")

    /** The screen's own corners rounded off, in pixels (0: square). */
    var cornerRadius by setting<Int>("corner_radius")

    /** Lock the screen when the computer wakes up. */
    var lockOnWakeup by setting<Boolean>("lock_on_wakeup")

    /** The language of newm's menus: "auto" (the system's), "en" or "ru". */
    var language by setting<String>("language")

    /** The user of greetd's greeter (newm as the login screen). */
    var greeterUser by setting<String>("greeter_user")

    /** The wallpaper. */
    fun background(configure: BackgroundSection.() -> Unit) = section("background", ::BackgroundSection, configure)

    /** A monitor by its connector ("eDP-1", "HDMI-A-1"): its size, rate, scale, place. */
    fun output(name: String, configure: OutputSection.() -> Unit) {
        @Suppress("UNCHECKED_CAST")
        val list = values.getOrPut("outputs") { ArrayList<Map<String, Any?>>() } as MutableList<Map<String, Any?>>
        val section = OutputSection(linkedMapOf("name" to name)).apply(configure)
        list.removeAll { it["name"] == name }
        list += section.values
    }

    /** The keyboard: layouts, options. */
    fun keyboard(configure: KeyboardSection.() -> Unit) = section("pywm", ::KeyboardSection, configure)

    /** The mouse cursor. */
    fun cursor(configure: CursorSection.() -> Unit) = section("pywm", ::CursorSection, configure)

    /** Touchpads and mice. */
    fun input(configure: InputSection.() -> Unit) = section("pywm", ::InputSection, configure)

    /** How windows look and where they go. */
    fun view(configure: ViewSection.() -> Unit) = section("view", ::ViewSection, configure)

    /** The line around the focused window. */
    fun focus(configure: FocusSection.() -> Unit) = section("focus", ::FocusSection, configure)

    /** The lock screen, the launcher and the bars. */
    fun panels(configure: PanelsSection.() -> Unit) = section("panels", ::PanelsSection, configure)

    /** Idle times, what happens then, suspending. */
    /** Where the colours of the bar, menus and borders come from (unset: the colours menu's choice, else the wallpaper). */
    fun colors(configure: ColorsSection.() -> Unit) = section("colors", ::ColorsSection, configure)

    /** The screenshots' settings (the keys: screenshot(), screenshotScreen(), screenshotWindow() in keys { }). */
    fun screenshots(configure: ScreenshotsSection.() -> Unit) = section("screenshots", ::ScreenshotsSection, configure)

    /** What shows on the screen by itself (a card when Wi-Fi, a cable or Bluetooth comes or goes). */
    fun osd(configure: OsdSection.() -> Unit) = section("osd", ::OsdSection, configure)

    /** The overview (Super alone): clicks there. */
    fun overview(configure: OverviewSection.() -> Unit) = section("overview", ::OverviewSection, configure)

    /** The mouse with Super: moving and resizing windows. */
    fun mouse(configure: MouseSection.() -> Unit) = section("mouse", ::MouseSection, configure)

    /** The clipboard's history (its key: toggleClipboard() in keys { }). */
    fun clipboard(configure: ClipboardSection.() -> Unit) = section("clipboard", ::ClipboardSection, configure)

    fun energy(configure: EnergySection.() -> Unit) = section("energy", ::EnergySection, configure)

    /** Touchpad gestures: which fingers do what, and how they feel. */
    fun gestures(configure: GesturesSection.() -> Unit) = GesturesSection(nested("gestures"), nested("gesture_bindings")).configure()

    /** Finer feel of moving around (swipes, zoom, snapping): see the guide. */
    fun tuning(configure: TuningSection.() -> Unit) = TuningSection(values).configure()

    /**
     * The keys: newm's notation — "L-h" is Logo (Super)+h, "L-S-h" with Shift, "L-C-h" with
     * Ctrl, "A-..." Alt; "L-H" is the same as "L-S-h"; "L-" is the Logo key tapped alone;
     * "XF86AudioMute" and the like work on the lock screen too; "L-a L-b" is a sequence.
     */
    fun keys(configure: KeysSection.() -> Unit) {
        val keys = KeysSection().apply(configure)
        values["key_bindings"] = { layout: Layout -> keys.bindings.map { (combo, action) -> combo to { action.invoke(layout) } } }
        values["key_help"] = keys.bindings.map { (combo, _) -> combo to keys.descriptions[combo].orEmpty() }
    }

    /** Runs once newm has started (start programs here). */
    fun onStartup(action: () -> Unit) {
        values["on_startup"] = action
    }

    /** Runs after the configuration was loaded again (it is, whenever config.kts is saved). */
    fun onReconfigure(action: () -> Unit) {
        values["on_reconfigure"] = action
    }

    /** Runs about 30 times a second (kept short: newm waits for it). */
    fun everyFrame(action: () -> Unit) {
        values["synchronous_update"] = action
    }

    /** Starts [command] inside newm (as in a shell: "firefox --private-window"). */
    fun launch(command: String) = caulis.launch(command)

    /** The terminal installed here: \$TERMINAL, else alacritty, foot, kitty, ... (the first there is). */
    fun defaultTerminal(): String = newm.defaultTerminal()
}

class BackgroundSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** The picture ("~/Pictures/wall.jpg"; "resource:wallpaper.jpg" is newm's own). */
    var path by setting<String>("path")

    /**
     * "follow" (the default: it moves and zooms with the view, the overview shows more of it)
     * or "static" (it stands still and fills the screen, whatever the view does).
     */
    var mode by setting<String>("mode")

    /** The wallpaper moves a little with the view (parallax); false is mode = "static". */
    var anim by setting<Boolean>("anim")

    /** How quickly it follows, in seconds. */
    var timeScale by setting<Double>("time_scale")
}

class OutputSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    var scale by setting<Double>("scale")
    var width by setting<Int>("width")
    var height by setting<Int>("height")

    /** The refresh rate in hertz (60.0). */
    var refreshHz: Double?
        get() = (values["mHz"] as Number?)?.toDouble()?.div(1000)
        set(value) {
            values["mHz"] = value?.times(1000)
        }

    /** Where it is, left edge and top edge. */
    var x by setting<Int>("pos_x")
    var y by setting<Int>("pos_y")

    /** Turned: 0 normal, 1 by 90°, 2 by 180°, 3 by 270° (4 to 7: mirrored). */
    var transform by setting<Int>("transform")

    /** False: its wallpaper stands still. */
    var anim by setting<Boolean>("anim")
}

class KeyboardSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** "us", "us,ru". */
    var layouts by setting<String>("xkb_layout")
    var variants by setting<String>("xkb_variant")

    /** "grp:alt_shift_toggle,caps:escape". */
    var options by setting<String>("xkb_options")
    var model by setting<String>("xkb_model")

    /** Num Lock on from the start (newm issue 168). */
    var numLock by setting<Boolean>("numlock")
}

class CursorSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** An installed cursor theme ("Adwaita", "Bibata-Modern-Ice"); unset: Caulis's own. */
    var theme by setting<String>("xcursor_theme")
    var size by setting<Int>("xcursor_size")

    /** Hide the cursor after the mouse was still this many seconds (0: never; newm issue 74). */
    var hideAfter by setting<Int>("hide_cursor_after")
}

class InputSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    var tapToClick by setting<Boolean>("tap_to_click")
    var naturalScroll by setting<Boolean>("natural_scroll")

    /** The window under the mouse gets the keyboard. */
    var focusFollowsMouse by setting<Boolean>("focus_follows_mouse")

    /** X11 programs (through Xwayland). */
    var xwayland by setting<Boolean>("enable_xwayland")

    /** Programs draw their own title bars (true), or windows have none (false, newm's look). */
    var titleBars by setting<Boolean>("encourage_csd")
}

/** What a rule says about a window (see [ViewSection.rules]). */
class Rule internal constructor(internal val values: Map<String, Any?>)

class ViewSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** How opaque windows without the focus are (1.0: as they are; 0.85: a little see-through). */
    var unfocusedOpacity by setting<Double>("unfocused_opacity")

    /** A window made wider or taller pushes its neighbours aside instead of stacking on them (default true). */
    var resizePushes by setting<Boolean>("resize_pushes")

    /** The first window of an empty workspace takes the whole screen (false: a quarter, as newm had it). */
    var fillFirst by setting<Boolean>("fill_first")

    /** Windows fade in when they open and out when they close. */
    var fade by setting<Boolean>("fade")

    /** A new window goes right of the focused one (false: the original newm's first free place in view). */
    var placeBesideFocused by setting<Boolean>("place_beside_focused")

    /** Windows' corners rounded, in pixels. */
    var cornerRadius by setting<Double>("corner_radius")

    /** The gap around each window, in pixels. */
    var padding by setting<Double>("padding")

    /** The gap around a fullscreen window. */
    var fullscreenPadding by setting<Double>("fullscreen_padding")

    /** Programs are told when they are fullscreen. */
    var sendFullscreen by setting<Boolean>("send_fullscreen")

    /** Programs may ask to be fullscreen (a video's button). */
    var acceptFullscreen by setting<Boolean>("accept_fullscreen")

    /** Floating windows open at their smallest size. */
    var floatingMinSize by setting<Boolean>("floating_min_size")

    /** How far past a workspace's edge a window may be dragged before it moves to the next. */
    var borderWsSwitch by setting<Double>("border_ws_switch")

    /**
     * Rules for some windows, decided when they open:
     *
     * ```kotlin
     * rules { view ->
     *     when (view.appId) {
     *         "mpv", "pavucontrol" -> rule(float = true)
     *         "Alacritty", "foot" -> rule(blur = Blur(radius = 6, passes = 4))
     *         else -> null
     *     }
     * }
     * ```
     */
    fun rules(decide: (View) -> Rule?) {
        values["rules"] = { view: View -> decide(view)?.values }
    }

    /** A rule: floating (with a size and a place: 0..1 of the screen), how opaque, blur behind it, no focus border (focus = false). */
    fun rule(
        float: Boolean? = null,
        floatSize: Pair<Int, Int>? = null,
        floatPos: Pair<Double, Double>? = null,
        opacity: Double? = null,
        blur: Blur? = null,
        focus: Boolean? = null,
    ): Rule = Rule(
        buildMap {
            float?.let { put("float", it) }
            floatSize?.let { put("float_size", listOf(it.first, it.second)) }
            floatPos?.let { put("float_pos", listOf(it.first, it.second)) }
            opacity?.let { put("opacity", it) }
            blur?.let { put("blur", mapOf("radius" to it.radius, "passes" to it.passes)) }
            focus?.let { put("focus", it) }
        },
    )
}

/** Blur behind a window: how far it spreads, and how smooth (passes). */
data class Blur(val radius: Int = 6, val passes: Int = 4)

class FocusSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    var enabled by setting<Boolean>("enabled")

    /** "#RRGGBB" or "#RRGGBBAA". */
    var color by setting<String>("color")

    /** How far outside the window, in pixels. */
    var distance by setting<Double>("distance")
    var width by setting<Double>("width")
    var animTime by setting<Double>("anim_time")

    /** The line glides to the next window when the focus changes. */
    var animateOnChange by setting<Boolean>("animate_on_change")
}

class PanelsSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** The lock screen (newm's own; a program instead with cmd). */
    fun lock(configure: PanelSection.() -> Unit) = section("lock", ::PanelSection, configure)

    /** The launcher (newm's own; a program instead with cmd). */
    fun launcher(configure: PanelSection.() -> Unit) = section("launcher", ::PanelSection, configure)

    /** A bar program of your own (waybar, ...): started and kept running, room made for it. */
    fun bar(configure: BarProgramSection.() -> Unit) = section("bar", ::BarProgramSection, configure)

    /** newm's own bar at the top. */
    fun topBar(configure: NativeBarSection.() -> Unit) = NativeBarSection(nested("top_bar", "native")).configure()

    /** newm's own bar at the bottom. */
    fun bottomBar(configure: NativeBarSection.() -> Unit) = NativeBarSection(nested("bottom_bar", "native")).configure()
}

class PanelSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** A program to show instead of newm's own panel (newm-panel-basic, ...). */
    var cmd by setting<String>("cmd")

    /** lock { }: a screen locker of the session-lock kind to start instead (swaylock, gtklock, hyprlock). */
    var program by setting<String>("program")

    /** Its size: parts of the screen (0..1). */
    var w by setting<Double>("w")
    var h by setting<Double>("h")
    var cornerRadius by setting<Double>("corner_radius")
}

class BarProgramSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    var cmd by setting<String>("cmd")
    var visibleNormal by setting<Boolean>("visible_normal")
    var visibleFullscreen by setting<Boolean>("visible_fullscreen")
}

class NativeBarSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    var enabled by setting<Boolean>("enabled")
    var height by setting<Int>("height")
    var fontSize by setting<Int>("font_size")

    /** A font family installed ("Inter", "JetBrains Mono"); unset: the system's sans-serif. */
    var font by setting<String>("font")

    /** Its background: "#RRGGBBAA" (see-through with the last two digits). */
    var background by setting<String>("background")

    /** Its text colour. */
    var foreground by setting<String>("foreground")

    /** What is behind it is blurred. */
    var blur by setting<Boolean>("blur")

    /** What it shows, asked every second (the classic look: spread over it; islands: the module "texts"). */
    fun texts(make: () -> List<String>) {
        values["texts"] = make
    }

    /** "islands" (newm's own, the default) or "classic" (the original newm's strip of texts). */
    var style by setting<String>("style")

    /** The islands' gap to the screen's edges, and their corners. */
    var margin by setting<Int>("margin")
    var cornerRadius by setting<Int>("corner_radius")

    /** "auto" (the light or dark scheme of programs), "light" or "dark". */
    var theme by setting<String>("theme")

    /** Your own colours for the islands, their text and the accent ("#RRGGBB" or "#RRGGBBAA"). */
    var islandColor by setting<String>("island_color")
    var textColor by setting<String>("text_color")
    var accentColor by setting<String>("accent_color")

    /**
     * What each island shows, in order: "wall" (the wall, small), "window" (the focused
     * window), "menu" (the launcher's button), "clock", "tray" (background programs), "network", "volume", "battery",
     * "power", "texts" (those of texts { }). An empty list: no island there.
     */
    var left by setting<List<String>>("left")
    var center by setting<List<String>>("center")
    var right by setting<List<String>>("right")

    /** The clock's and the date's patterns (java.time: "HH:mm", "EEE d MMM"; "" for no date). */
    var clock by setting<String>("clock")
    var date by setting<String>("date")

    /** The size of icons, whether the volume and battery say their percent, the window title's widest. */
    var iconSize by setting<Int>("icon_size")
    var showPercent by setting<Boolean>("show_percent")
    var titleWidth by setting<Int>("title_width")

    /** Tray icons left out, by name or title (default: nm-applet and blueman, whose Wi-Fi and Bluetooth the bar shows itself). */
    var trayHide by setting<List<String>>("tray_hide")

    /** The menu's button (three lines, the launcher) first on the left (false: none; or place "menu" yourself). */
    var menuButton by setting<Boolean>("menu_button")

    /** The quick actions' button (caffeine, a screenshot, the clipboard, the scheme) before the power button (false: none; or place "quick" yourself). */
    var quickButton by setting<Boolean>("quick_button")

    /** Tray icons that say they are not needed now ("Passive") shown too. */
    var showPassive by setting<Boolean>("show_passive")
}

/** colors { }: where newm's colours come from. */
class ColorsSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** "wallpaper" (made from it, as phones do), "nord" (the classic ones), or a colour of yours ("#5b8cc8"). */
    var source by setting<String>("source")

    /** "tonal", "vibrant", "expressive", "neutral" or "monochrome". */
    var style by setting<String>("style")
}

/** screenshots { }: the screenshot chooser's settings (its keys are in keys { }). */
class ScreenshotsSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** Screenshots at all (false: the keys do nothing). */
    var enabled by setting<Boolean>("enabled")

    /** Where they are saved ("~/Pictures/Screenshots" by default). */
    var folder by setting<String>("folder")

    /** Saved as files, put on the clipboard, with the pointer in them. */
    var save by setting<Boolean>("save")
    var copy by setting<Boolean>("copy")
    var pointer by setting<Boolean>("pointer")

    /** A toast when one is taken (true). */
    var toast by setting<Boolean>("toast")

    /** What the chooser starts with: "region", "window" or "screen" (the one used last if not set). */
    var start by setting<String>("start")

    /** Seconds to wait before taking it (0). */
    var delay by setting<Int>("delay")

    /** A part of the overview (windows drawn small) is put together from the windows at their own size: sharp (true). */
    var sharpWindows by setting<Boolean>("sharp_windows")

    /** Behind the windows of such a picture: "none" (see-through, the default), "#RRGGBB", or "theme" (the bar's colour). */
    var sharpBackground by setting<String>("sharp_background")

    /** The file's name: {date}, {time}, {kind} ("Screenshot from {date} {time}"). */
    var fileName by setting<String>("file_name")

    /** "png" (the default) or "jpg"; the JPEG's quality 0..100 (92). */
    var format by setting<String>("format")
    var quality by setting<Int>("quality")

    /** A command run with each file saved, {file} being it (an editor: "satty --filename {file}"). */
    var then by setting<String>("then")
}

/** overview { }: the windows seen from afar (Super alone). */
class OverviewSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** The windows take clicks there (false, the default: they are only looked at). */
    var input by setting<Boolean>("input")

    /** A click on a window chooses it: the overview ends there, the window focused (true). */
    var choose by setting<Boolean>("choose")
}

/** mouse { }: the mouse with Super. */
class MouseSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /**
     * "button" (the default, as Hyprland): Super + left drag moves a window, Super + right
     * drag resizes it. "motion": Super and the mouse moved alone move it (as newm had it).
     */
    var superMove by setting<String>("super_move")
}

/** osd { }: what shows on the screen by itself. */
class OsdSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** Wi-Fi, a cable or a Bluetooth device came or went: "cards" (in the middle, the default), "text" (a short line), "off". */
    var connections by setting<String>("connections")
}

/** clipboard { }: the clipboard's history (kept in memory only, never on disk). */
class ClipboardSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** The history at all (false: nothing is kept, the key does nothing). */
    var enabled by setting<Boolean>("enabled")

    /** How many things are kept (50 by default; pinned ones on top of them). */
    var size by setting<Int>("size")

    /** Pictures kept too (true), and copied files (true). */
    var pictures by setting<Boolean>("pictures")
    var files by setting<Boolean>("files")

    /** Pinned texts kept after a restart (true; false: in memory only, as everything else). */
    var keepPins by setting<Boolean>("keep_pins")

    /** Programs whose copies are never kept, by name or app id ("keepassxc", "bitwarden"). */
    var ignore by setting<List<String>>("ignore")

    /** The biggest picture kept (megabytes, 16) and the longest text (characters, 200000). */
    var maxPictureMb by setting<Int>("max_picture_mb")
    var maxText by setting<Int>("max_text")

    /** "Clear" asks nothing (false: it asks again before clearing everything). */
    var clearAtOnce by setting<Boolean>("clear_at_once")
}

class EnergySection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** Seconds without input until: dimmed, locked, suspended. */
    var idleTimes by setting<List<Int>>("idle_times")
    var suspendCommand by setting<String>("suspend_command")

    /** Seconds without input until the screens go dark (they come back with a key or the mouse; newm issue 159). */
    var screensOffAfter by setting<Int>("screens_off_after")

    /**
     * Told what happens: "idle", "idle-lock", "idle-presuspend", "idle-suspend", "active",
     * "lock", "sleep", "wakeup".
     */
    fun onIdle(action: (String) -> Unit) {
        values["idle_callback"] = action
    }
}

class GesturesSection internal constructor(values: MutableMap<String, Any?>, private val bindings: MutableMap<String, Any?>) : Section(values) {
    /** How smooth gestures feel (0..1: more is smoother and slower). */
    var lpInertia by setting<Double>("lp_inertia")

    /** How often a gesture is looked at, per second. */
    var lpFreq by setting<Double>("lp_freq")

    /** Touchpad gestures from libinput (Caulis). */
    var enabled: Boolean?
        get() = nested("c")["enabled"] as Boolean?
        set(value) {
            nested("c")["enabled"] = value
        }

    /** How many pixels of finger movement are one screen. */
    var scalePx: Double?
        get() = nested("c")["scale_px"] as Double?
        set(value) {
            nested("c")["scale_px"] = value
        }

    /**
     * Which gesture does what: a modifier letter ("L") or null, and the gesture ("swipe-3";
     * move and resize take two: the one-finger move and the two-finger resize).
     */
    fun bind(what: GestureAction, modifier: String?, vararg kinds: String) {
        bindings[what.key] = listOf(modifier, *kinds)
    }
}

/** What a gesture can do. */
enum class GestureAction(internal val key: String) {
    /** Move the view over the workspace's tiles (default: three fingers). */
    SWIPE("swipe"),

    /** Show more or fewer tiles (default: four fingers up and down). */
    SWIPE_TO_ZOOM("swipe_to_zoom"),

    /** Move and resize the focused window (default: Logo with one or two fingers). */
    MOVE_RESIZE("move_resize"),

    /** The launcher (default: five fingers). */
    LAUNCHER("launcher"),
}

class TuningSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    fun swipe(configure: Tuning.() -> Unit) = section("swipe", ::Tuning, configure)
    fun swipeZoom(configure: Tuning.() -> Unit) = section("swipe_zoom", ::Tuning, configure)
    fun move(configure: Tuning.() -> Unit) = section("move", ::Tuning, configure)
    fun resize(configure: Tuning.() -> Unit) = section("resize", ::Tuning, configure)
    fun moveResize(configure: Tuning.() -> Unit) = section("move_resize", ::Tuning, configure)
    fun grid(configure: Tuning.() -> Unit) = section("grid", ::Tuning, configure)
    fun interpolation(configure: Tuning.() -> Unit) = section("interpolation", ::Tuning, configure)

    /** Any of newm's tuning values by name (see the guide). */
    class Tuning internal constructor(values: MutableMap<String, Any?>) : Section(values) {
        var gestureFactor by setting<Double>("gesture_factor")
        var gridM by setting<Double>("grid_m")
        var gridOvr by setting<Double>("grid_ovr")
        var lockDist by setting<Double>("lock_dist")
        var hyst by setting<Double>("hyst")
        var minDist by setting<Double>("min_dist")
        var timeScale by setting<Double>("time_scale")
        var sizeAdjustment by setting<Double>("size_adjustment")
    }
}

/** The keys: `"L-h" { move(-1, 0) }` — the action runs with the layout as `this`. */
@NewmConfigDsl
class KeysSection internal constructor() {
    internal val bindings = ArrayList<Pair<String, Layout.() -> Unit>>()
    internal val descriptions = LinkedHashMap<String, String>()

    operator fun String.invoke(action: Layout.() -> Unit) {
        bindings += this to action
    }

    /** A key with what it does in words: shown in the keys' help (Logo+/). */
    operator fun String.invoke(description: String, action: Layout.() -> Unit) {
        bindings += this to action
        descriptions[this] = description
    }
}
