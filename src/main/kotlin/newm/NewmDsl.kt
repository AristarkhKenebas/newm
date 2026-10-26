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
        values["key_bindings"] = { layout: Layout -> keys.bindings.map { (combo, action) -> combo to { layout.action() } } }
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
}

class BackgroundSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** The picture ("~/Pictures/wall.jpg"; "resource:wallpaper.jpg" is newm's own). */
    var path by setting<String>("path")

    /** The wallpaper moves a little with the view (parallax). */
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
}

class CursorSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** An installed cursor theme ("Adwaita", "Bibata-Modern-Ice"); unset: Caulis's own. */
    var theme by setting<String>("xcursor_theme")
    var size by setting<Int>("xcursor_size")
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

    /** A rule: floating (with a size and a place: 0..1 of the screen), how opaque, blur behind it. */
    fun rule(
        float: Boolean? = null,
        floatSize: Pair<Int, Int>? = null,
        floatPos: Pair<Double, Double>? = null,
        opacity: Double? = null,
        blur: Blur? = null,
    ): Rule = Rule(
        buildMap {
            float?.let { put("float", it) }
            floatSize?.let { put("float_size", listOf(it.first, it.second)) }
            floatPos?.let { put("float_pos", listOf(it.first, it.second)) }
            opacity?.let { put("opacity", it) }
            blur?.let { put("blur", mapOf("radius" to it.radius, "passes" to it.passes)) }
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

    /** A font family installed ("JetBrains Mono"); unset: a monospace one. */
    var font by setting<String>("font")

    /** Its background: "#RRGGBBAA" (see-through with the last two digits). */
    var background by setting<String>("background")

    /** Its text colour. */
    var foreground by setting<String>("foreground")

    /** What is behind it is blurred. */
    var blur by setting<Boolean>("blur")

    /** What it shows, asked every second: spread evenly over it. */
    fun texts(make: () -> List<String>) {
        values["texts"] = make
    }
}

class EnergySection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** Seconds without input until: dimmed, locked, suspended. */
    var idleTimes by setting<List<Int>>("idle_times")
    var suspendCommand by setting<String>("suspend_command")

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

    operator fun String.invoke(action: Layout.() -> Unit) {
        bindings += this to action
    }
}
