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

    /**
     * How long animations take, in seconds (moving, zooming, opening) — config.py's way;
     * `animations { millis }` says the same. Animations run only when turned on: [animations].
     */
    var animTime by setting<Double>("anim_time")

    /** How long newm takes to fade in at the start and out at the end, in seconds (the animations "startup" and "quit"). */
    var blendTime by setting<Double>("blend_time")

    /**
     * Animations, Hyprland-like: off unless turned on, all at once or each by name with its
     * time, curve and look — `animations { preset = "newm" }` is newm's look as it always was
     * (see the guide's animations page and [NewmAnimations.TREE] for every name).
     */
    fun animations(configure: AnimationsSection.() -> Unit) = section("animations", ::AnimationsSection, configure)

    /**
     * A look of newm's own, all of it at once: "jadefire" (jade and amber on near-black green,
     * ember for what is urgent) sets the colours of the bar, the menus, the cards and the lock
     * screen, the focus border's shader and a moving wallpaper — what the configuration sets
     * itself wins (a picture of your own keeps its place). Unset: none.
     */
    var theme by setting<String>("theme")

    /** The screen's own corners rounded off, in pixels (0: square). */
    var cornerRadius by setting<Int>("corner_radius")

    /** Lock the screen when the computer wakes up. */
    var lockOnWakeup by setting<Boolean>("lock_on_wakeup")

    /**
     * Light or dark, for newm and the programs: "auto" (the default: what the quick settings
     * chose last, else dark), "dark" or "light".
     */
    var colorScheme by setting<String>("color_scheme")

    /** The language of newm's menus: "auto" (the system's), "en" or "ru". */
    var language by setting<String>("language")

    /**
     * The user of greetd's greeter (newm as the login screen). Started by greetd, newm knows it is
     * the greeter by greetd's socket (GREETD_SOCK) whatever the user is called; this is for the rest.
     */
    var greeterUser by setting<String>("greeter_user")

    /** What greetd starts once the greeter let a user in ("newm", a script of your own); unset: this newm. */
    var greeterCommand by setting<String>("greeter_command")

    /**
     * `newm cmd unlock` unlocks without the password (off: any program of yours could do it
     * over the session bus). For a test setup only.
     */
    var enableUnlockCommand by setting<Boolean>("enable_unlock_command")

    /** How a time of day is written ("HH:mm", java.time's pattern; "h:mm a" for 3:45 PM): the notifications' history, the lock screen, the clipboard. */
    var timeFormat by setting<String>("time_format")

    /** The bar's power menu: its entries, which ask once more, what each runs. */
    fun power(configure: PowerSection.() -> Unit) = section("power", ::PowerSection, configure)

    /** The screen's brightness from the bar: the wheel's step, the lowest, the device and the commands. */
    fun brightness(configure: BrightnessSection.() -> Unit) = section("brightness", ::BrightnessSection, configure)

    /** The battery at the bar: when it is low or critical, and what runs then. */
    fun battery(configure: BatterySection.() -> Unit) = section("battery", ::BatterySection, configure)

    /** The clock's calendar: the weekend, the first day, the weeks' numbers. */
    fun calendar(configure: CalendarSection.() -> Unit) = section("calendar", ::CalendarSection, configure)

    /** The quick settings' tiles (the bar's grid button), and tiles of your own. */
    fun quickSettings(configure: QuickSettingsSection.() -> Unit) = section("quick_settings", ::QuickSettingsSection, configure)

    /** The caffeine tile's times. */
    fun caffeine(configure: CaffeineSection.() -> Unit) = section("caffeine", ::CaffeineSection, configure)

    /** The network at the bar (the Wi-Fi's name). */
    fun network(configure: NetworkSection.() -> Unit) = section("network", ::NetworkSection, configure)

    /** The tray: how many icons a program may show, how big a picture it may send. */
    fun tray(configure: TraySection.() -> Unit) = section("tray", ::TraySection, configure)

    /** The wallpaper. */
    fun background(configure: BackgroundSection.() -> Unit) = section("background", ::BackgroundSection, configure)

    /** The start's work done ahead (menus drawn, Compose made ready) and the memory given back after it. */
    fun warmup(configure: WarmupSection.() -> Unit) = section("warmup", ::WarmupSection, configure)

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

    /** The compositor underneath: quitting while locked, a lock that fails, programs on quit. */
    fun compositor(configure: CompositorSection.() -> Unit) = section("pywm", ::CompositorSection, configure)

    /** The mouse cursor. */
    fun cursor(configure: CursorSection.() -> Unit) = section("pywm", ::CursorSection, configure)

    /** Touchpads and mice. */
    fun input(configure: InputSection.() -> Unit) = section("pywm", ::InputSection, configure)

    /** How windows look and where they go. */
    fun view(configure: ViewSection.() -> Unit) = section("view", ::ViewSection, configure)

    /** Where new windows and a program's child windows (dialogs, viewers) open, and where the focus goes when they close. */
    fun windows(configure: WindowsSection.() -> Unit) = section("windows", ::WindowsSection, configure)

    /**
     * The wall in whole screens: how many windows a screen holds and how they are tiled there
     * (`screens { windowsPerScreen = 4; layout = "grid" }`); the next window opens on the next screen.
     */
    fun screens(configure: ScreensSection.() -> Unit) = section("screens", ::ScreensSection, configure)

    /**
     * Resizing windows on screens: by the quarter grabbed (as Hyprland), at their borders,
     * within their screen (`resize { quarters = true; onBorder = true; clampToScreen = true }`).
     */
    fun resize(configure: ResizeSection.() -> Unit) = section("resize", ::ResizeSection, configure)

    /** The line around the focused window. */
    fun focus(configure: FocusSection.() -> Unit) = section("focus", ::FocusSection, configure)

    /**
     * Special workspaces (Hyprland's `special`): shown above the wall with
     * `toggleSpecialWorkspace("name")`, windows moved in with `moveToSpecialWorkspace("name")`.
     */
    fun specialWorkspace(configure: SpecialWorkspaceSection.() -> Unit) = section("special_workspace", ::SpecialWorkspaceSection, configure)

    /** The lock screen, the launcher and the bars. */
    fun panels(configure: PanelsSection.() -> Unit) = section("panels", ::PanelsSection, configure)

    /** newm's launcher: the same as panels { launcher { } }. */
    fun launcher(configure: LauncherPanelSection.() -> Unit) = LauncherPanelSection(nested("panels", "launcher")).configure()

    /** newm's lock screen: the same as panels { lock { } }. */
    fun lock(configure: LockPanelSection.() -> Unit) = LockPanelSection(nested("panels", "lock")).configure()

    /** The windows opened again after `newm cmd restart keep` (and, if asked, after logging in). */
    fun session(configure: SessionSection.() -> Unit) = section("session", ::SessionSection, configure)

    /**
     * Programs started with the session, one after another once newm is shown — once per
     * session (a reload starts only what it adds). Caulis's own autostart:
     *
     * ```kotlin
     * autostart {
     *     stagger = 400.milliseconds
     *     app("waybar")
     *     app("nm-applet", "--indicator") { delay = 2.seconds; ifNotRunning = true; restart = OnCrash(maxTimes = 3, within = 1.minutes) }
     *     shell("sleep 1; notify-send hi")
     *     desktopEntries = false   // true: also the .desktop files of ~/.config/autostart
     * }
     * ```
     */
    fun autostart(configure: dev.caulis.wm.Autostart.() -> Unit) {
        (values.getOrPut("autostart") { dev.caulis.wm.Autostart() } as dev.caulis.wm.Autostart).configure()
    }

    /** The start: dark until newm is ready, then it fades in (on by default). */
    fun reveal(configure: RevealSection.() -> Unit) = section("reveal", ::RevealSection, configure)

    /** The sound when newm starts (on by default: "airy"). */
    fun startupSound(configure: StartupSoundSection.() -> Unit) = section("startup_sound", ::StartupSoundSection, configure)

    /**
     * The terminal newm opens ("foot", "kitty -1"): the "terminal" action, programs that run in
     * one (Terminal=true), defaultTerminal(). Unset: \$TERMINAL, else the first installed.
     */
    var terminal by setting<String>("terminal")

    /**
     * An environment variable for every program newm starts, also handed to the session's
     * services (portals, systemd's user programs) — Hyprland's `env = XCURSOR_SIZE,24`:
     * `env("XCURSOR_SIZE", "24")`. Programs started already keep theirs.
     */
    fun env(name: String, value: String) {
        require(name.isNotEmpty() && '=' !in name) { "env: \"$name\" is not a variable's name" }
        nested("env")[name] = value
    }

    /**
     * Which programs may take pictures of the screen, see the window list, read the clipboard,
     * show panels, lock the screen, arrange monitors — Hyprland's permissions:
     * `permissions { rule("/usr/bin/grim", "screencopy", "allow") }`. Nothing said: all may.
     */
    fun permissions(configure: PermissionsSection.() -> Unit) = section("permissions", ::PermissionsSection, configure)

    /** Settings of Hyprland's misc { }: window swallowing, focus on activation, waking the screens, VRR, middle-click paste. */
    fun misc(configure: MiscSection.() -> Unit) = section("misc", ::MiscSection, configure)

    /**
     * A Caulis rule for some windows: how they behave (Hyprland's window rules newm's
     * view { rules } has not) — `windowRule { appId("mpv"); keepAwake = true }`,
     * `windowRule { appId(Regex("firefox|chromium")); ignore(WindowRequest.MAXIMIZE) }`
     * (suppress_event), `windowRule { title("Sharing indicator"); hiddenFromCapture = true }`,
     * minSize, maxSize, takesFocus, specialWorkspace (`windowRule { appId("spotify"); specialWorkspace = "music" }`:
     * it opens hidden in that special workspace). Where on the wall windows go is newm's: view { rules }.
     */
    fun windowRule(configure: dev.caulis.wm.WindowRule.() -> Unit) {
        @Suppress("UNCHECKED_CAST")
        (values.getOrPut("window_rules") { ArrayList<Any?>() } as MutableList<Any?>) += configure
    }

    /** A Caulis rule for some panels (Hyprland's layer rules): `layerRule { namespace("fuzzel"); blur = true; dimAround = 0.4f }`. */
    fun layerRule(configure: dev.caulis.wm.PanelRule.() -> Unit) {
        @Suppress("UNCHECKED_CAST")
        (values.getOrPut("layer_rules") { ArrayList<Any?>() } as MutableList<Any?>) += configure
    }

    /** Where the colours of the bar, menus and borders come from (unset: the colours menu's choice, else the wallpaper). */
    fun colors(configure: ColorsSection.() -> Unit) = section("colors", ::ColorsSection, configure)

    /** The screenshots' settings (the keys: screenshot(), screenshotScreen(), screenshotWindow() in keys { }). */
    fun screenshots(configure: ScreenshotsSection.() -> Unit) = section("screenshots", ::ScreenshotsSection, configure)

    /** What shows on the screen by itself (a card when Wi-Fi, a cable or Bluetooth comes or goes). */
    fun osd(configure: OsdSection.() -> Unit) = section("osd", ::OsdSection, configure)

    /** newm's notifications (Telegram's messages, a download done): cards at the top right, a history behind the bar's bell. */
    fun notifications(configure: NotificationsSection.() -> Unit) = section("notifications", ::NotificationsSection, configure)

    /** The volume's slider. */
    fun sound(configure: SoundSection.() -> Unit) = section("sound", ::SoundSection, configure)

    /** The overview (Super alone): clicks there. */
    fun overview(configure: OverviewSection.() -> Unit) = section("overview", ::OverviewSection, configure)

    /** minimap { enabled = true }: the wall's map in a corner of each monitor (off by default). */
    fun minimap(configure: MinimapSection.() -> Unit) = section("minimap", ::MinimapSection, configure)

    /** The mouse with Super: moving and resizing windows. */
    fun mouse(configure: MouseSection.() -> Unit) = section("mouse", ::MouseSection, configure)

    /** The clipboard's history (its key: toggleClipboard() in keys { }). */
    fun clipboard(configure: ClipboardSection.() -> Unit) = section("clipboard", ::ClipboardSection, configure)

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
        keySection.configure()
        val keys = keySection
        val now = keys.bindings.toList()
        values["key_bindings"] = { layout: Layout -> now.map { (combo, action) -> combo to { action.invoke(layout) } } }
        values["key_help"] = now.map { (combo, _) -> combo to keys.descriptions[combo].orEmpty() }
    }

    // The keys of every keys { } (a later one adds to and changes an earlier one).
    private val keySection = KeysSection()

    /** A key and an action by its name, outside keys { } too: `bind("Super+Backspace", "close-window")`. */
    fun bind(combination: String, action: String, vararg args: String) = keys { bind(combination, action, *args) }

    /** The key does nothing any more: `unbind("Super+Q")`. */
    fun unbind(combination: String) = keys { unbind(combination) }

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

    /** The terminal: [terminal] if set above, else \$TERMINAL, else alacritty, foot, kitty, ... (the first there is). */
    fun defaultTerminal(): String = terminal?.takeIf { it.isNotBlank() } ?: newm.defaultTerminal()
}

class WarmupSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** newm's menus drawn once, off screen, a moment after the start: their first opening is quick (true). */
    var enabled by setting<Boolean>("enabled")

    /** Each is drawn only once the pointer rested this long, in milliseconds (800): the start's other work goes first. */
    var restMillis by setting<Int>("rest_ms")

    /** The menus (and the bars, before newm shows) are drawn ahead on a thread of their own (true); false: on the compositor's thread (it holds the pointer ~0.1-0.4 s each). */
    var ownThread by setting<Boolean>("own_thread")

    /** Compose (the bar, the menus) made ready on a thread of its own while newm starts (true). */
    var compose by setting<Boolean>("compose")

    /** Java's heap made small again after the start: one full collection while the pointer rests, some 10-50 ms (true). */
    var collect by setting<Boolean>("collect")
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

    /**
     * A moving wallpaper drawn by the GPU instead of the picture: one of newm's own by name —
     * "dragon-eye", "ember-sky", "scales", "deep-forest", "molten" — or a GLSL file of yours
     * (`vec4 wallpaper(vec2 at)`, Caulis's wallpaper shaders; read again when saved). It moves
     * with the view as the picture does and answers the pointer and clicks. "" (the default): the picture.
     */
    var shader by setting<String>("shader")

    /** How often it is drawn anew, a second (30), and on battery (15; 0: as [fps]). */
    var fps by setting<Int>("fps")
    var fpsOnBattery by setting<Int>("fps_on_battery")

    /** It stands still after so many seconds without input (120; 0: never), and while the screen is dimmed. */
    var pauseAfter by setting<Int>("pause_after")

    /** How sharp: 1.0 every pixel, 0.5 half each way (a quarter of the work); 0 (the default): 1.0 up to 2560x1600, 0.5 above. */
    var resolution by setting<Double>("resolution")

    /** Its pace and its strength (1.0 each). */
    var speed by setting<Double>("speed")
    var intensity by setting<Double>("intensity")

    /**
     * Its own settings by name: `shaderValues = mapOf("eyeSize" to 0.6, "blink" to 0.0)` — dragon-eye:
     * eyeSize (0.38), blink (1: on); ember-sky: embers (1); scales: scaleSize (1); deep-forest:
     * spores (1); molten: blobs (7). A file of yours reads each as a `float` of its name.
     */
    var shaderValues by setting<Map<String, Double>>("shader_values")

    /** Its colours: jade, amber, ember, the dark (`listOf("#3ddc97", "#f2b33d", "#e8552d", "#060908")`); unset: the shader's own. */
    var shaderColors by setting<List<String>>("shader_colors")
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

    /**
     * Which keys are modifiers in key bindings: "xkb" (the default: as the layout and its
     * options say — caps:super makes Caps Lock a Super) or "physical" (the printed keys).
     */
    var bindingModifiers by setting<String>("binding_modifiers")

    /**
     * True (the default): key bindings work on every layout by the key they have in the first
     * Latin layout — with "us,ru", Super+D is the key of "в", Super+Shift+M that of "Ь" (as in
     * GNOME and KDE); a binding on the layout's own symbol ("L-Cyrillic_ve") still wins on it.
     * False: the layout in use's symbols only (pywm's way: on Russian, Super+D does nothing).
     */
    var shortcutsOnLatinLayout by setting<Boolean>("shortcuts_on_latin_layout")

    /** Keys held down repeat this many times a second (Hyprland's repeat_rate; 25 by default). */
    var repeatRate by setting<Int>("repeat_rate")

    /** ... after being held this long, milliseconds (Hyprland's repeat_delay; 600 by default). */
    var repeatDelay by setting<Int>("repeat_delay")
}

/** The compositor underneath: quitting, locking, the programs it started. */
class CompositorSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** Ctrl+Alt+BackSpace quits on a locked screen too (default false: it would get round the lock). */
    var quitWhileLocked by setting<Boolean>("quit_while_locked")

    /**
     * When a lock does not show in [lockShowMillis] (the locker failed): "own-lock" (the
     * default: Caulis's own plain lock screen until a locker unlocks) or "notice" (only said).
     */
    var lockFailure by setting<String>("lock_failure")

    /** How long a lock may take to show before it counts as failed, in milliseconds (default 3000). */
    var lockShowMillis by setting<Int>("lock_show_millis")

    /** Programs started inside are asked to end when newm quits (default true). */
    var stopProgramsOnQuit by setting<Boolean>("stop_programs_on_quit")

    /**
     * How programs are started: "native" (the default: posix_spawn, still working after the
     * system updated Java while newm runs) or "java" (Java's own way; natively after it failed).
     */
    var processStart by setting<String>("process_start")

    /** Said once when Java was updated under the running newm ("": not said, only logged). */
    var javaUpdatedNotice by setting<String>("java_updated_notice")

    /**
     * The Java options of the short-lived Java that compiles config.kts (default
     * "-XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss4m -Djava.awt.headless=true"; for the next
     * compilation — the first one of a start takes NEWM_CONFIG_COMPILER_OPTS).
     */
    var configCompilerJvmOptions by setting<String>("config_compiler_jvm_options")

    /** At most this many seconds for compiling config.kts (180), then it is stopped and said (the start's first: NEWM_CONFIG_COMPILER_TIMEOUT). */
    var configCompilerTimeoutSeconds by setting<Int>("config_compiler_timeout_seconds")

    /** The main loop busy this long (ms) with one thing is logged with what it was (log area native, level 2); 0: not watched (50). */
    var stallReportMillis by setting<Int>("stall_report_ms")

    /** Memory the C library freed (Skia, the GPU driver) given back after the start and every [trimMemoryEveryMinutes] (true). */
    var trimMemory by setting<Boolean>("trim_memory")

    /** How often [trimMemory] gives memory back after the start, minutes; 0: only after the start (2). */
    var trimMemoryEveryMinutes by setting<Int>("trim_memory_every_minutes")

    /** At most this many of glibc's malloc pools for newm itself; 0: glibc's own (2; only at the start). */
    var mallocPools by setting<Int>("malloc_pools")

    /** The cursor (on the monitor's cursor plane) moves the moment the pointer does, not with the next frame (true). */
    var quickCursor by setting<Boolean>("quick_cursor")

    /** Threads the bar and the menus are drawn into pixels on (composed on newm's own); 0: all on newm's own (2). */
    var widgetThreads by setting<Int>("widget_threads")

    /** Memory blocks of this many kB or more go back to the system the moment they are freed (256; 0: glibc's own; only at the start). */
    var mallocMapAboveKb by setting<Int>("malloc_map_above_kb")

    /** Keyboards, mice and touchpads read on a thread of their own: the cursor keeps moving while newm is busy (true; on screens, not nested). */
    var inputThread by setting<Boolean>("input_thread")

    /** Only the EGL driver for the GPU in use is loaded, not every one installed (true; only at the start). */
    var glVendorByDriver by setting<Boolean>("gl_vendor_by_driver")

    /** A menu, the launcher or cards not shown for this many seconds give their memory back; made anew when shown (60; 0: kept). */
    var keepHiddenMenusSeconds by setting<Int>("keep_hidden_menus_seconds")
}

class CursorSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** An installed cursor theme ("Adwaita", "Bibata-Modern-Ice"); unset: Caulis's own. */
    var theme by setting<String>("xcursor_theme")
    var size by setting<Int>("xcursor_size")

    /** Hide the cursor after the mouse was still this many seconds (0: never; newm issue 74). */
    var hideAfter by setting<Int>("hide_cursor_after")
}

class InputSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /**
     * Drawing tablets and their pens: `tablet { monitor = "eDP-1"; leftHanded = true; lowerButton = "right" }`.
     * Out of the box the pen is the pointer everywhere, and programs that know tablets (Krita,
     * GIMP) get its pressure and tilt too.
     */
    fun tablet(configure: TabletSection.() -> Unit) {
        @Suppress("UNCHECKED_CAST")
        val now = (values["tablet"] as? MutableMap<String, Any?>) ?: LinkedHashMap<String, Any?>().also { values["tablet"] = it }
        TabletSection(now).configure()
    }

    var tapToClick by setting<Boolean>("tap_to_click")
    var naturalScroll by setting<Boolean>("natural_scroll")

    /** The window under the mouse gets the keyboard. */
    var focusFollowsMouse by setting<Boolean>("focus_follows_mouse")

    /** X11 programs (through Xwayland). */
    var xwayland by setting<Boolean>("enable_xwayland")

    /** Programs draw their own title bars (true), or windows have none (false, newm's look). */
    var titleBars by setting<Boolean>("encourage_csd")

    /** Every mouse and touchpad: how fast the pointer goes, -1.0 to 1.0 (Hyprland's sensitivity; 0: as it is). */
    var pointerSpeed by setting<Double>("pointer_speed")

    /** "adaptive" (faster when moved fast) or "flat" (Hyprland's accel_profile). */
    var accelProfile by setting<String>("accel_profile")

    /** Buttons swapped for the left hand. */
    var leftHanded by setting<Boolean>("left_handed")

    /** Scrolling this many times faster (1.0: as it is). */
    var scrollFactor by setting<Double>("scroll_factor")

    /** How touchpads and mice scroll: "two-finger", "edge", "button" (with [scrollButton] held) or "none". */
    var scrollMethod by setting<String>("scroll_method")

    /** The button held to scroll by moving ("button"): a Linux button code, 274 the middle one. */
    var scrollButton by setting<Int>("scroll_button")

    /** Left and right pressed together are the middle button. */
    var middleButtonEmulation by setting<Boolean>("middle_button_emulation")

    /** Touchpads only (Hyprland's input:touchpad): `touchpad { disableWhileTyping = true; clickFinger = true }`. */
    fun touchpad(configure: TouchpadSection.() -> Unit) = section("touchpad", ::TouchpadSection, configure)

    /** One device by its name (`newm cmd debug` lists them; * matches anything) — Hyprland's device { }: `device("epic-mouse-v1") { pointerSpeed = -0.5 }`. */
    fun device(name: String, configure: DeviceSection.() -> Unit) = DeviceSection(nested("devices", name)).configure()
}

/** A touchpad's own settings (unset: those of input { }, then the touchpad's own). */
class TouchpadSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    var naturalScroll by setting<Boolean>("natural_scroll")
    var scrollFactor by setting<Double>("scroll_factor")
    var pointerSpeed by setting<Double>("pointer_speed")

    /** Not used while typing (and a moment after). */
    var disableWhileTyping by setting<Boolean>("disable_while_typing")

    /** A tap, then a drag drags. */
    var tapAndDrag by setting<Boolean>("tap_and_drag")

    /** Lifting the finger in a drag does not end it at once. */
    var dragLock by setting<Boolean>("drag_lock")

    /** A click by the number of fingers (two: right, three: middle) instead of where it is pressed (Hyprland's clickfinger_behavior). */
    var clickFinger by setting<Boolean>("click_finger")

    /** Two and three fingers tapped: "lrm" (right, middle) or "lmr" (middle, right), Hyprland's tap_button_map. */
    var tapButtons by setting<String>("tap_buttons")

    /** Off while a mouse is plugged in. */
    var offWithMouse by setting<Boolean>("off_with_mouse")
}

/** One input device's own settings (see [InputSection.device]). */
class DeviceSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** False: not used at all (a keyboard you do not trust: Hyprland's keyboard permission). */
    var enabled by setting<Boolean>("enabled")
    var pointerSpeed by setting<Double>("pointer_speed")
    var accelProfile by setting<String>("accel_profile")
    var naturalScroll by setting<Boolean>("natural_scroll")
    var leftHanded by setting<Boolean>("left_handed")
    var scrollFactor by setting<Double>("scroll_factor")
    var middleButtonEmulation by setting<Boolean>("middle_button_emulation")
    var tapToClick by setting<Boolean>("tap_to_click")
    var disableWhileTyping by setting<Boolean>("disable_while_typing")
}

/** What a rule says about a window (see [ViewSection.rules]). */
class Rule internal constructor(internal val values: Map<String, Any?>)

class ViewSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** How opaque windows without the focus are (1.0: as they are; 0.85: a little see-through). */
    var unfocusedOpacity by setting<Double>("unfocused_opacity")

    /** A window being resized is drawn at its own size, not stretched: no flicker when its new picture comes (true). */
    var crispResize by setting<Boolean>("crisp_resize")

    /** Gone (the screen split into finer tiles is gone): it does nothing. */
    @Deprecated("Does nothing any more: windows are drawn as newm drew them (see fitDrawnSize)")
    var minSizeKeepsShape: Boolean? = null

    /** Gone with [minSizeKeepsShape]: it does nothing. */
    @Deprecated("Does nothing any more")
    var minSizeShrink: Double? = null

    /**
     * A program that drew another size than it was asked is drawn in its own shape inside its
     * place, made smaller to fit (true); false (the default, as newm): stretched to its place.
     */
    var fitDrawnSize by setting<Boolean>("fit_drawn_size")

    /** A window made wider or taller by keys pushes its neighbours aside (true); false (the default, as newm): it goes over them. */
    var resizePushes by setting<Boolean>("resize_pushes")

    /** The first window of an empty workspace takes the whole screen (true); false (the default, as newm): a quarter, zoomed out. */
    var fillFirst by setting<Boolean>("fill_first")

    /** Windows fade in when they open and out when they close. */
    var fade by setting<Boolean>("fade")

    /** Before windows { newPlacement }: true is "beside-focused", false "original". */
    @Deprecated("windows { newPlacement = \"beside-focused\" } (true) or \"original\" (false)")
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

    /** A floating window that chooses no size of its own opens at its smallest (one that does keeps its own). */
    var floatingMinSize by setting<Boolean>("floating_min_size")

    /** How far past a workspace's edge a window may be dragged before it moves to the next. */
    var borderWsSwitch by setting<Double>("border_ws_switch")

    /** A fullscreen window stays fullscreen when the view moves or zooms away from it (false: it leaves fullscreen then). */
    var stickyFullscreen by setting<Boolean>("sticky_fullscreen")

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

    /**
     * A rule: floating (with a size and a place: 0..1 of the screen), how opaque, blur behind
     * it, no focus border (focus = false), or the special workspace it opens in, hidden there
     * until shown (specialWorkspace = "music": `special-workspace music` shows it).
     */
    fun rule(
        float: Boolean? = null,
        floatSize: Pair<Int, Int>? = null,
        floatPos: Pair<Double, Double>? = null,
        opacity: Double? = null,
        blur: Blur? = null,
        focus: Boolean? = null,
        specialWorkspace: String? = null,
    ): Rule = Rule(
        buildMap {
            specialWorkspace?.let { put("special_workspace", it) }
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

/**
 * Where windows open: `windows { newPlacement = "compact"; childPlacement = "below" }`.
 */
class WindowsSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /**
     * Where a new window opens: "compact" (the default: the free place next to the focused
     * window that keeps all windows close together and near a square, with the smallest move
     * of the view — nothing moves aside unless no place next to it is free; on screens: the
     * focused screen, else a neighbouring screen with room, else a new screen right after it),
     * "right-of-focused" (right of the focused window, the windows right of it move on),
     * "beside-focused" (the first free tile right of it in its row, nothing moves), "end" (right
     * of all windows, in the focused one's row), "original" (newm's: the first free tile in
     * view, row by row).
     */
    var newPlacement by setting<String>("new_placement")

    /**
     * Where a program's child window opens (a dialog, a photo viewer): "below" (the default:
     * tiled below its parent — in free room there, else what is below moves down), "right"
     * (tiled right of it, likewise), "over" (floating over its middle, as newm). On screens: right
     * after its window on its screen; a full screen: the next one if it has room, else a new one.
     */
    var childPlacement by setting<String>("child_placement")

    /** A child window closing gives the focus back to its parent, or to the window focused when it came (true); false: newm's nearest window. */
    var returnFocusToParent by setting<Boolean>("return_focus_to_parent")

    /**
     * A window without a parent counts as the child of the focused window when the same program
     * (connection and app id) opened it (Telegram's photo viewer): true by default.
     */
    var childBySameApp by setting<Boolean>("child_by_same_app")

    /** Programs whose new windows are windows of their own even then (app ids: terminals, browsers by default). */
    var childBySameAppExcept by setting<List<String>>("child_by_same_app_except")
}

/**
 * The wall in whole screens: `screens { windowsPerScreen = 2; layout = "columns" }`. A screen is
 * exactly the monitor's room left by the bars; it holds up to [windowsPerScreen] windows, tiled
 * by [layout]; the next window opens on the next screen (right of it, or below: [overflow]).
 * Moving between windows of one screen leaves the view; to another screen it moves one screen.
 */
class ScreensSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** Screens (true, the default); false: newm's free wall of tiles, as the original. */
    var enabled by setting<Boolean>("enabled")

    /** How many windows a screen holds (2 by default); the next one opens on the next screen. */
    var windowsPerScreen by setting<Int>("windows_per_screen")

    /**
     * How a screen's windows are tiled: "columns" (the default: side by side), "rows" (one above
     * the other), "grid" (halves, then quarters, as Hyprland's), "dwindle" (each new one takes
     * half of the last one's room), "spiral", "master" (one big window and strips beside it,
     * as i3 and xmonad), "centered-master", "monocle" (one window a screen, as the original
     * newm), or the name of one of [customLayouts].
     */
    var layout by setting<String>("layout")

    /** The master's share of the screen ("master", "centered-master"): 0.6 by default. */
    var masterRatio by setting<Double>("master_ratio")

    /** Where the master is: "left" (the default), "right", "top" or "bottom". */
    var masterSide by setting<String>("master_side")

    /** Room between the windows and around them, in pixels (0 by default: view { padding } is there anyway). */
    var gaps by setting<Int>("gaps")

    /** Where the next screen is: "right" (the default) or "down". */
    var overflow by setting<String>("overflow")

    /** A window closing closes the gap it leaves (true, the default), as [pullBackMode] says; false: screens stay as they are. */
    var pullBack by setting<Boolean>("pull_back")

    /**
     * How [pullBack] closes the gap: "compact" (the default: a neighbouring screen whose windows
     * all fit into the room left moves in, one screen fewer; nothing else moves) or "shift" (the
     * windows of every screen after it move up one place until its screen is full again).
     */
    var pullBackMode by setting<String>("pull_back_mode")

    /** Windows go on under the bars, as newm's wall (true); false (the default): under a bar only the bar shows. */
    var windowsUnderBar by setting<Boolean>("windows_under_bar")

    /** The layouts Super+Space goes through: "columns", "rows", "grid", "master", "monocle" by default. */
    var cycle by setting<List<String>>("cycle")

    /**
     * Layouts of your own by name (any of Caulis' tiling layouts, or your own
     * dev.caulis.tiling.Layout), to use in [layout] and [cycle]:
     * `customLayout("wide-master", dev.caulis.tiling.MasterStack(ratio = 0.7))`.
     */
    fun customLayout(name: String, layout: dev.caulis.tiling.Layout) {
        @Suppress("UNCHECKED_CAST")
        val map = values.getOrPut("custom_layouts") { LinkedHashMap<String, Any?>() } as MutableMap<String, Any?>
        map[name] = layout
    }
}

/**
 * Resizing windows on screens (screens { }): `resize { quarters = true; clampToScreen = true }`.
 * A window grows only within its screen — its neighbours there make way; to give one more room,
 * zoom out and resize there (it still stays within its screen). In the overview nothing is resized.
 */
class ResizeSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** The button that resizes with Super held ("right"; unset: mouse { resizeButton }). */
    var mouseButton by setting<String>("mouse_button")

    /** The quarter of the window grabbed decides the corner that moves (true, as Hyprland); false: always the bottom right one. */
    var quarters by setting<Boolean>("quarters")

    /** The room right beside a window, dragged without Super, resizes it (true). */
    var onBorder by setting<Boolean>("on_border")

    /** How far from a window its border can be grabbed, in pixels (10). */
    var borderGrabSize by setting<Int>("border_grab_size")

    /** The button that drags a border ("left"). */
    var borderButton by setting<String>("border_button")

    /** Nothing grows past its screen's edges (true); false: a window at a screen's edge may grow over the next screen. */
    var clampToScreen by setting<Boolean>("clamp_to_screen")

    /** A corner dragged keeps the window's shape (false). */
    var keepAspect by setting<Boolean>("keep_aspect")

    /** The smallest a window gets, as a part of its screen (0.1). */
    var minSize by setting<Double>("min_size")

    /** How far a resizing key moves an edge, as a part of the screen (0.1). */
    var keyStep by setting<Double>("key_step")
}

/**
 * newm's animations (see [NewmAnimations]): on or off, the global time and curve, curves of your
 * own and each animation by name.
 *
 * ```kotlin
 * animations {
 *     preset = "newm"                                  // newm's usual animations, all on
 *     millis = 300                                     // the global time
 *     curve("snappy", 0.05, 0.9, 0.1, 1.05)            // a Bézier curve, as Hyprland's
 *     animation("windows", millis = 250, curve = "snappy")
 *     animation("popupsIn", style = "popin 90%")
 *     off("gestureSmoothing")                          // right under the fingers
 * }
 * ```
 */
class AnimationsSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** "newm": all of newm's usual animations on (as it always looked); "none": all off (the default). */
    var preset by setting<String>("preset")

    /** All on (unless one says otherwise) or off; wins over [preset]. */
    var enabled by setting<Boolean>("enabled")

    /** The global time in milliseconds, for those without their own (else anim_time's, 300). */
    var millis by setting<Int>("millis")

    /** The global curve by name: "linear" (newm's usual), "ease-out", "ease-in-out", "smooth", "overshoot" or one of yours. */
    var curve by setting<String>("curve")

    /** A Bézier curve of your own called [name] (the numbers of Hyprland's bezier, CSS's cubic-bezier). */
    fun curve(name: String, x1: Double, y1: Double, x2: Double, y2: Double) {
        nested("curves")[name] = dev.caulis.core.Curve.bezier(x1, y1, x2, y2)
    }

    /** A spring of your own called [name]: it takes as long as it needs to rest (less [damping] bounces). */
    fun spring(name: String, stiffness: Double = 200.0, damping: Double = 20.0, mass: Double = 1.0) {
        nested("curves")[name] = dev.caulis.core.Curve.spring(stiffness, damping, mass)
    }

    /** Any curve of your own called [name]: `curve("square") { t -> t * t }`. */
    fun curve(name: String, curve: dev.caulis.core.Curve) {
        nested("curves")[name] = curve
    }

    /**
     * The animation [name] (and those under it): on or off, its time ([millis], or [speed] in
     * Hyprland's tenths of a second), its curve by name, its look ([style], for those that have looks).
     */
    fun animation(name: String, enabled: Boolean? = null, millis: Int? = null, curve: String? = null, style: String? = null, speed: Double? = null) {
        require(name in NewmAnimations.TREE) { NewmAnimations.TREE.unknown(name) }
        val entry = nested(name)
        enabled?.let { entry["enabled"] = it }
        millis?.let { entry["millis"] = it }
        speed?.let { entry["speed"] = it }
        curve?.let { entry["curve"] = it }
        style?.let { entry["style"] = it }
    }

    /** Just [name] (and those under it) on, even while the rest is off. */
    fun on(name: String) = animation(name, enabled = true)

    /** [name] (and those under it) off. */
    fun off(name: String) = animation(name, enabled = false)
}

class FocusSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    var enabled by setting<Boolean>("enabled")

    /** "#RRGGBB" or "#RRGGBBAA". */
    var color by setting<String>("color")

    /** How far outside the window, in pixels. */
    var distance by setting<Double>("distance")
    var width by setting<Double>("width")

    /** How long its own glide takes, seconds (the animation "border"; runs when that is on). */
    var animTime by setting<Double>("anim_time")

    /** The line glides to the next window when the focus changes. */
    var animateOnChange by setting<Boolean>("animate_on_change")

    /**
     * A window wanting attention: a colour or Hyprland's text. Around a window that has not the
     * focus (focusing it takes the wish away), as Hyprland; "" or unset: no such border.
     */
    var urgentColor by setting<String>("urgent_color")

    /**
     * A GLSL file of one's own, `vec4 paint(vec2 at)`, read again when saved (see Caulis's
     * border shaders: time, progress, focused, urgent, colors[0] = color, colors[1] = urgentColor);
     * or one of newm's by name: "jadefire" (jade flowing into amber, a spark going round, an
     * urgent window pulsing ember).
     */
    var shader by setting<String>("shader")

    /** False: a shader's own time (and a turning gradient) stands still. */
    var animate by setting<Boolean>("animate")

    /** A moving border is drawn anew at most this many times a second (0, the default: every frame; 30 is half the work at 60 Hz). */
    var fps by setting<Int>("fps")
}

class SpecialWorkspaceSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** How much darker the wall behind a shown one is, 0..1 (0.4). */
    var dim by setting<Double>("dim")

    /** The wall behind it blurred this far (0: not, the default) and this smooth. */
    var blurRadius by setting<Int>("blur_radius")
    var blurPasses by setting<Int>("blur_passes")

    /** Space between it and the screen's edges, pixels (40). */
    var gap by setting<Int>("gap")

    /** How much of the screen it takes, 0.2..1 (1.0). */
    var size by setting<Double>("size")

    /** The animation its windows come and go with, by name ("scratchpads"; off unless animations are on). */
    var animation by setting<String>("animation")

    /** It hides when its last window closes or leaves (true). */
    var hideWhenEmpty by setting<Boolean>("hide_when_empty")

    /** Windows opening while it is shown go into it (true). */
    var newWindowsJoin by setting<Boolean>("new_windows_join")

    /** A click on the dimmed wall hides it (true). */
    var clickOutsideHides by setting<Boolean>("click_outside_hides")

    /** The overview hides it (true): the overview shows the wall's windows only. */
    var hideInOverview by setting<Boolean>("hide_in_overview")

    /** How round its windows' corners are, pixels (-1, the default: as view { cornerRadius }). */
    var cornerRadius by setting<Double>("corner_radius")

    /** Room around each of its windows, pixels: twice it between two (-1, the default: as view { padding }). */
    var padding by setting<Double>("padding")
}

class PanelsSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** The lock screen (newm's own; a program instead with cmd). */
    fun lock(configure: LockPanelSection.() -> Unit) = section("lock", ::LockPanelSection, configure)

    /** The launcher (newm's own; a program instead with cmd). */
    fun launcher(configure: LauncherPanelSection.() -> Unit) = section("launcher", ::LauncherPanelSection, configure)

    /** A bar program of your own (waybar, ...): started and kept running, room made for it. */
    fun bar(configure: BarProgramSection.() -> Unit) = section("bar", ::BarProgramSection, configure)

    /** A bar program of the old newm's kind at the top (newm-panel-basic bar): its room is at the top. */
    fun topBarProgram(configure: BarProgramSection.() -> Unit) = section("top_bar", ::BarProgramSection, configure)

    /** The same at the bottom. */
    fun bottomBarProgram(configure: BarProgramSection.() -> Unit) = section("bottom_bar", ::BarProgramSection, configure)

    /** newm's own bar at the top. */
    fun topBar(configure: NativeBarSection.() -> Unit) = NativeBarSection(nested("top_bar", "native")).configure()

    /** newm's own bar at the bottom. */
    fun bottomBar(configure: NativeBarSection.() -> Unit) = NativeBarSection(nested("bottom_bar", "native")).configure()
}

open class PanelSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** A program to show instead of newm's own panel (newm-panel-basic, ...). */
    var cmd by setting<String>("cmd")

    /** The folder that program starts in. */
    var cwd by setting<String>("cwd")

    /** lock { }: a screen locker of the session-lock kind to start instead (swaylock, gtklock, hyprlock). */
    var program by setting<String>("program")

    /** Its size: parts of the screen (0..1): the lock screen's, and a launcher program's (cmd; newm's own launcher: width, height). */
    var w by setting<Double>("w")
    var h by setting<Double>("h")
    var cornerRadius by setting<Double>("corner_radius")
}

/** lock { }: the lock screen. */
class LockPanelSection internal constructor(values: MutableMap<String, Any?>) : PanelSection(values) {
    /** newm's own lock screen's colours ("#RRGGBB" or "#RRGGBBAA"); unset: the bar's colours, light or dark as everything. */
    var background by setting<String>("background")
    var text by setting<String>("text")
    var accent by setting<String>("accent")

    /** The clock's and the date's patterns (java.time); unset: the top-level timeFormat ("HH:mm"), and "EEEE, d MMMM". "" for none. */
    var clock by setting<String>("clock")
    var date by setting<String>("date")

    /** The PAM service the password is checked with ("login": /etc/pam.d/login). */
    var pamService by setting<String>("pam_service")

    /** Seconds a password check may take before the lock screen asks again (60: longer than pam_fprintd waits for a finger). */
    var checkTimeout by setting<Int>("check_timeout")

    /** Seconds sleep waits for the lock screen to show before it goes on anyway (3; 0..30). */
    var sleepWait by setting<Double>("sleep_wait")

    /**
     * What newm's lock screen shows besides the password: "clock" (with the date) and
     * "battery" (the default); an empty list: nothing. The bar, its popups, the tray and
     * notifications never show over the lock screen, and nothing but it takes input.
     */
    var showOnLock by setting<List<String>>("show_on_lock")

    /**
     * The keyboard layout when the screen locks: "keep" (the default: the one in use, shown
     * beside the password — a click switches it) or "first" (the first of keyboard { layouts },
     * "us" of "us,ru", and back to the one before after unlocking).
     */
    var layoutOnLock by setting<String>("layout_on_lock")

    /**
     * Programs' panels (a waybar, mako's pop-ups), menus and X11's unmanaged windows stay drawn
     * behind newm's own lock screen (false: hidden, as the windows are — what they show may be private).
     */
    var showPanels by setting<Boolean>("show_panels")

    /** An eye beside the password shows it while held (true). */
    var revealButton by setting<Boolean>("reveal_button")

    /** A small keyboard on the lock screen, following the layout in use (false): for touch screens and broken keyboards. */
    var onScreenKeyboard by setting<Boolean>("on_screen_keyboard")

    /**
     * pam_faillock's state is read (true): after too many wrong passwords the lock screen counts
     * down until the account unlocks instead of taking passwords that would make it longer.
     */
    var faillock by setting<Boolean>("faillock")

    /** Where pam_faillock's settings are ("/etc/security/faillock.conf"). */
    var faillockConf by setting<String>("faillock_conf")
}

/** launcher { }: newm's launcher (Logo+D, five fingers). */
class LauncherPanelSection internal constructor(values: MutableMap<String, Any?>) : PanelSection(values) {
    /** newm's own launcher's size in pixels (720 x 560 at most, smaller on a small screen) and its top, a part of the screen's height (0.16). */
    var width by setting<Int>("width")
    var height by setting<Int>("height")
    var top by setting<Double>("top")

    /** How many of the programs started most come first (6; 0: none). */
    var frequent by setting<Int>("frequent")

    /** Which programs were started is kept, in ~/.local/state/newm/launcher.json (true; false: nothing is written, nothing comes first). */
    var remember by setting<Boolean>("remember")

    /** Programs left out, by name or .desktop id ("htop", "org.gnome.Extensions"). */
    var hide by setting<List<String>>("hide")

    /** Enter with nothing found runs what was typed as a command (false: nothing happens). */
    var runTyped by setting<Boolean>("run_typed")

    /**
     * How many letters of a word typed may be wrong (changed, missing, extra, two swapped) and it
     * still finds the program: "fierfox" finds Firefox (1; 2: two in words of 8 letters or more;
     * 0: only what is typed exactly). Words under 4 letters always match exactly.
     */
    var typos by setting<Int>("typos")

    /**
     * What the search finds: "programs" (and your entries), "actions" (newm's: "Lock the screen",
     * "Screenshot of the window", "Reload this configuration"), "files" (those used lately, by
     * any program: recently-used.xbel), "calculator" (a sum typed: "2+2*3", Enter copies the
     * result), "command" (typed after [commandPrefix]: "> htop" runs it). All of them by default.
     */
    var search by setting<List<String>>("search")

    /** What starts a command in the search ("> htop"); "" for none. */
    var commandPrefix by setting<String>("command_prefix")

    /** How many of the files used lately are searched (200; 0: none). Only their names are searched, only when something is typed. */
    var recentFiles by setting<Int>("recent_files")

    /** The program a file found opens with ("xdg-open"). */
    var openWith by setting<String>("open_with")

    /** At most this many actions, and files, are shown (6). */
    var perKind by setting<Int>("per_kind")

    /** Programs always shown first with nothing typed, in this order, by .desktop id or name: `listOf("firefox", "foot")` (none). */
    var pinned by setting<List<String>>("pinned")

    /** How many of the programs started last are shown first with nothing typed, after the pinned ones (0, the default: none; at most 24). Needs remember. */
    var recent by setting<Int>("recent")

    /**
     * How the programs are shown: "list" (newm's, the default), "grid" (pages of big icons as
     * Launchpad: the arrows go across and down, Page Up/Down and the wheel turn the pages) or
     * "categories" (the XDG categories beside the list: left and right go through them).
     * Typing searches everything in each view.
     */
    var view by setting<String>("view")

    /** Smaller rows in the list (and the categories): no comment under the name (false). */
    var compact by setting<Boolean>("compact")

    /** The grid's programs across and down on a page (6, 4). */
    var gridColumns by setting<Int>("grid_columns")
    var gridRows by setting<Int>("grid_rows")

    /**
     * The categories' headings and the XDG categories under each, in order:
     * `mapOf("Internet" to listOf("Network", "WebBrowser"), "Games" to listOf("Game"))` (newm's
     * nine by default; a program in none of them is under "Other").
     */
    var categories by setting<Map<String, List<String>>>("categories")

    /** Entries of your own, with what they do: `entries { "Lock"("Lock the screen") { ensureLocked() } }`. */
    fun entries(configure: LauncherEntries.() -> Unit) {
        @Suppress("UNCHECKED_CAST")
        val list = (values["entries"] as? MutableList<Map<String, Any?>>) ?: ArrayList<Map<String, Any?>>().also { values["entries"] = it }
        LauncherEntries(list).configure()
    }

    /**
     * Keys of your own in the launcher: `keys { "C-n" { next() }; "C-Return" { runTyped() } }`
     * (next, previous, first, last, start, runTyped, close). Ctrl+N/P, Ctrl+J/K, Tab and
     * Shift+Tab, the arrows, Enter and Escape work without it.
     */
    fun keys(configure: LauncherKeys.() -> Unit) {
        @Suppress("UNCHECKED_CAST")
        val map = (values["keys"] as? MutableMap<String, Any?>) ?: LinkedHashMap<String, Any?>().also { values["keys"] = it }
        LauncherKeys(map).configure()
    }
}

/** launcher { entries { } }: an entry is a name (and words under it) and what it does. */
@NewmConfigDsl
class LauncherEntries internal constructor(private val list: MutableList<Map<String, Any?>>) {
    operator fun String.invoke(action: Layout.() -> Unit) = add(this, "", action)
    operator fun String.invoke(comment: String, action: Layout.() -> Unit) = add(this, comment, action)

    private fun add(name: String, comment: String, action: Layout.() -> Unit) {
        list.removeAll { it["name"] == name }
        list += mapOf("name" to name, "comment" to comment, "action" to { layout: Layout -> layout.action() })
    }
}

/** launcher { keys { } }: a key ("C-n", "A-j", "Tab") and what it does in the launcher. */
@NewmConfigDsl
class LauncherKeys internal constructor(private val map: MutableMap<String, Any?>) {
    operator fun String.invoke(action: LauncherControl.() -> Unit) {
        map[this] = { c: LauncherControl -> c.action() }
    }
}

class BarProgramSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    var cmd by setting<String>("cmd")

    /** The folder the program starts in. */
    var cwd by setting<String>("cwd")
    var visibleNormal by setting<Boolean>("visible_normal")
    var visibleFullscreen by setting<Boolean>("visible_fullscreen")
}

/** session { }: windows opened again. */
class RevealSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** Dark until ready, then the fade (true, the default); false: newm shows as soon as it draws. */
    var enabled by setting<Boolean>("enabled")

    /** How long the fade takes, in milliseconds (600). */
    var fadeMillis by setting<Int>("fade_ms")

    /** The longest the screen stays dark, in milliseconds (4000). */
    var maxWaitMillis by setting<Int>("max_wait_ms")

    /** The colour until then ("#000000"). */
    var color by setting<String>("color")
}

class StartupSoundSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** Played at all (true, the default). */
    var enabled by setting<Boolean>("enabled")

    /** One of the sounds made by Caulis itself: "airy" (the default), "classic", "glass", "soft". */
    var preset by setting<String>("preset")

    /** A sound file of yours instead ("~/sounds/hello.ogg": ogg, wav, flac — what the player plays). */
    var file by setting<String>("file")

    /** How loud, 0 to 1 (0.8). */
    var volume by setting<Double>("volume")

    /** Waits this long more before it plays, in milliseconds (0). */
    var delayMillis by setting<Int>("delay_ms")

    /** "reveal" (the default: as newm fades in) or "start" (right away, while still dark). */
    var at by setting<String>("at")

    /** Your player's command, its words: listOf("mpv", "--no-video", "{file}"); unset: pw-play, paplay or aplay. */
    var player by setting<List<String>>("player")
}

class SessionSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** The windows open when you logged out (or the computer went off) open again when you log in (false: only after `newm cmd restart keep`). */
    var restoreAtLogin by setting<Boolean>("restore_at_login")

    /** Windows kept for `restart keep` longer ago than this are not opened again (10 minutes; 0: no limit). */
    var maxAgeMinutes by setting<Int>("max_age_minutes")

    /** At most this many programs are started again (20); a notice offers the rest. */
    var maxWindows by setting<Int>("max_windows")

    /** Programs never opened again, by app id or command ("steam", "org.telegram.desktop"). */
    var exclude by setting<List<String>>("exclude")

    /** The programs start one after another, this many milliseconds apart (300; 0: all at once). */
    var staggerMs by setting<Int>("stagger_ms")

    /**
     * What of a program's command line is kept: "options" (the default: its options, no files,
     * addresses or what looks like a password), "all", or "none" (the program alone). A
     * terminal always comes back alone, in its folder (never what ran in it).
     */
    var arguments by setting<String>("arguments")

    /**
     * Programs that run another one (python3 app.py, electron app.asar, wine game.exe): with
     * arguments = "options" their program (the first argument that is not an option) is kept too.
     * By name, the version digits at the end aside ("python3.12", "electron43").
     */
    var interpreters by setting<List<String>>("interpreters")

    /** Options whose value, written after them, is kept with them: "-P work", "--user-data-dir /x". */
    var keepValues by setting<List<String>>("keep_values")

    /** An option with one of these words in its name is left out, its value too ("--api-key", "--password"). */
    var secretWords by setting<List<String>>("secret_words")

    /** Options whose value is written right after them ("-phunter2"): left out with it. */
    var secretShortOptions by setting<List<String>>("secret_short_options")
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

    /**
     * The bar's look: "islands" (newm's own, the default), "solid" (one strip along the edge),
     * "floating" (one rounded bar in the gaps), "minimal" (the modules alone, with a soft
     * shadow), "macos" (a thin strip, 28 high, the clock at the right end) or "classic" (the
     * original newm's strip of texts). The modules are the same in each.
     */
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

    /** The notifications' bell (their history, do not disturb) before the quick actions' button (false: none; or place "notifications" yourself). */
    var notificationsButton by setting<Boolean>("notifications_button")

    /**
     * What the wheel over the clock and the bar's empty space does, each way:
     * `wheel { up { move(0, -1) }; down { move(0, 1) }; left { move(-1, 0) }; right { move(1, 0) } }`
     * (the default: the focus goes to the next window that way). `wheel { off() }`: nothing.
     */
    fun wheel(configure: BarWheel.() -> Unit) {
        @Suppress("UNCHECKED_CAST")
        val now = (values["wheel"] as? Map<String, Any?>)?.let(::LinkedHashMap) ?: LinkedHashMap()
        BarWheel(now).configure()
        values["wheel"] = now
    }

    /** Tray icons that say they are not needed now ("Passive") shown too. */
    var showPassive by setting<Boolean>("show_passive")

    /** The keyboard layouts' labels: `mapOf("us" to "EN", "de(neo)" to "NEO")` (by the layout's name or its code; others: "us" EN, else the code's first two letters). */
    var layoutNames by setting<Map<String, String>>("layout_names")

    /** The monitors with this bar (`listOf("eDP-1")`); empty, the default: all. */
    var outputs by setting<List<String>>("outputs")

    /** The clock's hint (java.time's pattern: "EEEE, d MMMM yyyy"; "" for none). */
    var clockHint by setting<String>("clock_hint")

    /** How long the pointer stays on a part of the bar before its hint shows, in milliseconds (800). */
    var hintDelayMs by setting<Int>("hint_delay_ms")

    /**
     * A module of your own, shown where [name] is in left / center / right:
     * `module("vpn") { command("vpn-status"); everySeconds = 10; icon = "lock"; onClick { launch("vpn-toggle") } }`.
     */
    fun module(name: String, configure: BarModule.() -> Unit) {
        val modules = nested("modules")
        @Suppress("UNCHECKED_CAST") // (what config.kts and config.json put there: maps by name)
        val module = (modules[name] as? MutableMap<String, Any?>) ?: LinkedHashMap<String, Any?>().also { modules[name] = it }
        BarModule(module).configure()
    }

    /**
     * What clicks and the wheel do on a module instead of newm's: `on("window") { middle { closeFocusedView() } }`,
     * `on("volume") { middle(null) }` (nothing). Modules: "wall", "window", "clock", "layout", "bluetooth",
     * "network", "volume", "battery", "power", "menu", "quick", "notifications", and yours.
     */
    fun on(module: String, configure: BarClicks.() -> Unit) {
        val on = nested("on")
        @Suppress("UNCHECKED_CAST") // (what config.kts and config.json put there: maps by name)
        val slots = (on[module] as? MutableMap<String, Any?>) ?: LinkedHashMap<String, Any?>().also { on[module] = it }
        BarClicks(slots).configure()
    }
}

/** colors { }: where newm's colours come from. */
class ColorsSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** "wallpaper" (made from it, as phones do), "nord" (the classic ones), "jadefire" (newm's own look), or a colour of yours ("#5b8cc8"). */
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

    /** What the chooser starts with: "region" (the default), "window" or "screen". */
    var start by setting<String>("start")

    /** The chooser starts with the kind used last (true: a window after a window); false (the default): with [start] each time. */
    var rememberKind by setting<Boolean>("remember_kind")

    /** A click (not a drag) while choosing a part takes the window under the pointer (true); false (the default): a window by its key, W, or button. */
    var clickPicksWindow by setting<Boolean>("click_picks_window")

    /**
     * The part chosen last is there again when the chooser opens (true, the default; Enter takes
     * it at once); dropped when the monitors change. False: none each time.
     */
    var rememberSelection by setting<Boolean>("remember_selection")

    /** That part is kept across restarts too (false by default: for this session only). */
    var rememberAcrossRestarts by setting<Boolean>("remember_across_restarts")

    /** The first Escape clears a part shown, a second closes (true, the default); false: Escape closes and forgets the part. */
    var escapeClearsSelection by setting<Boolean>("escape_clears_selection")

    /** Seconds to wait before taking it (0). */
    var delay by setting<Int>("delay")

    /** How a part is chosen: "adjust" (the default: dragged and resized after) or "new" (each drag a new part). */
    var selection by setting<String>("selection")

    /** What the chooser starts with over the overview: "windows" (the default: several, whole), "region", "window", "screen", or "" (the one used last). */
    var overviewStart by setting<String>("overview_start")

    /** A part of the overview (windows drawn small) is put together from the windows at their own size: sharp (true). */
    var sharpWindows by setting<Boolean>("sharp_windows")

    /** Behind the windows of such a picture: "wallpaper" (the default: what was behind them), "none" (see-through), "#RRGGBB", or "theme" (the bar's colour). */
    var sharpBackground by setting<String>("sharp_background")

    /** The file's name: {date}, {time}, {kind} ("Screenshot from {date} {time}"). */
    var fileName by setting<String>("file_name")

    /** "png" (the default) or "jpg"; the JPEG's quality 0..100 (92). */
    var format by setting<String>("format")
    var quality by setting<Int>("quality")

    /** A command run with each file saved, {file} being it (an editor: "satty --filename {file}"). */
    var then by setting<String>("then")
}

/** sound { }: the volume's slider. */
class SoundSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** How loud it goes, in percent (150; past 100 the sound is amplified, as Linux can). */
    var maxVolume by setting<Int>("max_volume")

    /** Percent a notch of the wheel over the bar's volume (2). */
    var wheelStep by setting<Int>("wheel_step")

    /** A command setting the volume from the bar ({percent}); unset: wpctl, else pactl. */
    var setVolume by setting<String>("set_volume")

    /** A command saying the volume ("45%", or wpctl's "Volume: 0.45 [MUTED]"); unset: wpctl, else pactl. */
    var getVolume by setting<String>("get_volume")

    /** How often the bar asks the volume, in seconds (10; a program run each time — with followVolume, six times rarer). */
    var volumeEverySeconds by setting<Int>("volume_every_seconds")

    /** The sound server's news of each change is followed (`pactl subscribe`): the bar's volume changes at once, without asking every few seconds (true). */
    var followVolume by setting<Boolean>("follow_volume")
}

/** notifications { }: newm's own notification server (org.freedesktop.Notifications). */
class NotificationsSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** newm shows notifications (true; false: it leaves them to another daemon, mako or dunst). */
    var enabled by setting<Boolean>("enabled")

    /** How long a card stays when the program does not say, in seconds (6; critical ones stay until closed). */
    var seconds by setting<Int>("seconds")

    /** No cards at all, the history only (false; the bell's switch changes it while newm runs). */
    var doNotDisturb by setting<Boolean>("do_not_disturb")

    /** A program's window focused this long (seconds, 3), its notifications are read: out of the history. A glance is not reading. */
    var readAfterFocusSeconds by setting<Int>("read_after_focus_seconds")

    /** The history keeps notifications this many hours (24), and this many at most (100). */
    var keepHours by setting<Int>("keep_hours")
    var keep by setting<Int>("keep")

    /** The history kept across restarts (true), in ~/.local/state/newm/notifications.json, readable by you alone. */
    var remember by setting<Boolean>("remember")

    /**
     * Another notification daemon holding the name: "stale" (the default: one left from another
     * session — started before newm, or on another display — is closed), "always", "never".
     */
    var takeOver by setting<String>("take_over")

    /** Where the cards are (and the compositor's notices): "top_right" (the default), "top_left", "top", "bottom_right", "bottom_left", "bottom". */
    var corner by setting<String>("corner")

    /** The cards' width (380), the gap between them and to the screen's edge (10), the most at once (3). */
    var width by setting<Int>("width")
    var gap by setting<Int>("gap")
    var maxCards by setting<Int>("max_cards")

    /** The monitor of the cards: "focused" (the default), "pointer", "first", or a name ("eDP-1"). */
    var monitor by setting<String>("monitor")

    /** How long a critical one's card stays, in seconds (0, the default: until closed). */
    var criticalSeconds by setting<Int>("critical_seconds")

    /** Cards over a fullscreen window too (false: they wait in the history). */
    var showInFullscreen by setting<Boolean>("show_in_fullscreen")

    /**
     * A rule for notifications as they come (several: each in turn):
     * `rule { n -> if (n.app == "Spotify") mute() }` — mute() no card, the history only;
     * hide() not kept at all; urgency(0..2); seconds(n) how long its card stays.
     */
    fun rule(decide: newm.bar.NotificationRule.(newm.bar.Notification) -> Unit) = addNotificationRule(decide)

    /** Told of each notification that comes, after the rules (n.app, n.summary, n.body, n.urgency). */
    fun onNotification(hook: (newm.bar.Notification) -> Unit) {
        values["on_notification"] = hook
    }
}

/** minimap { }: a small map of the wall in a corner of each monitor; a click on it goes there. */
class MinimapSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** Shown (false, the default). */
    var enabled by setting<Boolean>("enabled")

    /** Which corner: "top-left", "top-right", "bottom-left", "bottom-right" (the default). */
    var corner by setting<String>("corner")

    /** How wide it is at most, in desktop units (240); its height follows the wall's shape. */
    var width by setting<Double>("width")

    /** How far from the monitor's edges, within the room the bars leave (16). */
    var margin by setting<Double>("margin")

    /** Seconds it shows after the view or a window moved (2.0); 0: always. */
    var showFor by setting<Double>("show_for")

    /** How strong its background is, 0..1 (0.85). */
    var opacity by setting<Double>("opacity")
}

/** overview { }: the windows seen from afar (Super alone). */
class OverviewSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** The windows take clicks there (false, the default: they are only looked at). */
    var input by setting<Boolean>("input")

    /** A click on a window chooses it: the overview ends there, the window focused (true). */
    var choose by setting<Boolean>("choose")

    /** The window under the pointer is the chosen one there: its border, and Super+drag moves it (true). */
    var followPointer by setting<Boolean>("follow_pointer")

    /** A window pressed on and dragged there moves, onto another monitor's overview too, without Super (true). */
    var drag by setting<Boolean>("drag")

    /** How far the pointer goes with the button held before it is a drag, not a click (desktop units, 8). */
    var dragThreshold by setting<Double>("drag_threshold")
}

/** mouse { }: the mouse with Super. */
class MouseSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /**
     * "button" (the default, as Hyprland): Super + left drag moves a window, Super + right
     * drag resizes it. "motion": Super and the mouse moved alone move it (as newm had it).
     */
    var superMove by setting<String>("super_move")

    /** The button that moves a window with Super held: "left" (the default), "right", "middle", "side", "extra", or "none". */
    var moveButton by setting<String>("move_button")

    /** The button that resizes it: "right" (the default), ..., or "none". */
    var resizeButton by setting<String>("resize_button")

    /** Lines of the wheel that make one step on the bar (1.0; more: a touchpad has to move further). */
    var barWheelLines by setting<Double>("bar_wheel_lines")
}

/** osd { }: what shows on the screen by itself. */
class OsdSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** Wi-Fi, a cable or a Bluetooth device came or went: "cards" (in the middle, the default), "text" (a short line), "off". */
    var connections by setting<String>("connections")

    /** How long a level (volume, brightness) shows, in seconds (1.3), and where: "bottom" (the default), "top", "center", this far from that edge (130 pixels). */
    var seconds by setting<Number>("seconds")
    var position by setting<String>("position")
    var distance by setting<Int>("distance")

    /** How long a toast (words in the middle) and a connection's card show, in seconds (1.6, 2.8). */
    var toastSeconds by setting<Number>("toast_seconds")
    var cardSeconds by setting<Number>("card_seconds")

    /** Connections shown over a fullscreen window too (false). */
    var inFullscreen by setting<Boolean>("in_fullscreen")

    /** Told of each connection that comes or goes (e.kind, e.connected, e.name), whatever is shown. */
    fun onConnection(hook: (newm.bar.ConnectionEvent) -> Unit) = setConnectionHook(hook)
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

    /** All pictures kept, in all (megabytes, 64; at most 256): the oldest unpinned ones go first. */
    var picturesTotalMb by setting<Int>("pictures_total_mb")

    /** "Clear" clears at once (true, the default; false: it asks first, "Clear" again clears). */
    var clearAtOnce by setting<Boolean>("clear_at_once")

    /** What choosing an entry does: "copy" (the default: it is on the clipboard again) or "paste" (copied, then pasted into the focused window with Ctrl+V; Ctrl+Shift+V in a terminal). */
    var onChoose by setting<String>("on_choose")

    /** The keys "paste" sends ("C-v"), and those sent to a terminal ("C-S-v"). */
    var pasteKey by setting<String>("paste_key")
    var terminalPasteKey by setting<String>("terminal_paste_key")

    /**
     * The paste keys go in the first Latin layout while the layout in use is another script's
     * (true, the default; the layout comes back right after): programs that match Ctrl+V in the
     * layout in use only (Alacritty) paste on the Russian layout too. False: the layout in use.
     */
    var pasteOnLatinLayout by setting<Boolean>("paste_on_latin_layout")

    /** The window's size in pixels (980 x 620 at most, smaller on a small screen) and its top, a part of the screen's height (0.13). */
    var width by setting<Int>("width")
    var height by setting<Int>("height")
    var top by setting<Double>("top")

    /** The key that pins the chosen entry ("C-p"). */
    var pinKey by setting<String>("pin_key")
}

class EnergySection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** Seconds without input until: dimmed ("idle", told to onIdle), locked, suspended (dimAfter, lockAfter, suspendAfter say the same one by one). */
    var idleTimes by setting<List<Int>>("idle_times")
    var suspendCommand by setting<String>("suspend_command")

    /** Seconds without input until "idle" is told to onIdle (dimming); until the screen locks; until the computer is suspended. 0: never. Unset: idleTimes'. */
    var dimAfter by setting<Int>("dim_after")
    var lockAfter by setting<Int>("lock_after")
    var suspendAfter by setting<Int>("suspend_after")

    /** Something of your own after [seconds] without input (once, until someone is back): `after(900) { launch("playerctl pause") }`. */
    fun after(seconds: Int, action: Layout.() -> Unit) {
        @Suppress("UNCHECKED_CAST")
        val list = (values["after"] as? MutableList<Map<String, Any?>>) ?: ArrayList<Map<String, Any?>>().also { values["after"] = it }
        list += mapOf("seconds" to seconds, "action" to { layout: Layout -> layout.action() })
    }

    /** Seconds without input until the screens go dark (they come back with a key or the mouse; newm issue 159). */
    var screensOffAfter by setting<Int>("screens_off_after")

    /**
     * What keeps the computer awake (no dimming, locking, sleeping): "wayland" (a program's
     * idle-inhibit: mpv), "dbus" (org.freedesktop.ScreenSaver and PowerManagement.Inhibit: Firefox,
     * Chromium, Telegram playing a video), "logind" (systemd-inhibit --what=idle), "mpris" (a media
     * player playing while a window of its own shows). All four by default.
     */
    var inhibitFrom by setting<List<String>>("inhibit_from")

    /** Media players whose playing keeps nothing awake (music: nothing to watch), by bus name or name, any case: spotify, rhythmbox, ... */
    var mprisIgnore by setting<List<String>>("mpris_ignore")

    /** How often logind is asked for its idle inhibitors, in seconds (10). */
    var logindCheckSeconds by setting<Int>("logind_check_seconds")

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

    /** What a gesture does, said in a small pill while it runs (true). */
    var hints by setting<Boolean>("hints")

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

    /** Gestures sent by another program over D-Bus (newm's org.newm.gestures; true). */
    var dbusEnabled: Boolean?
        get() = nested("dbus")["enabled"] as Boolean?
        set(value) {
            nested("dbus")["enabled"] = value
        }

    /**
     * A gesture of your own: [kind] ("swipe-3", "swipe-4", "swipe-5"; with [modifier] "L" held:
     * "move-1", "swipe-2" too) towards [direction] ("up", "down", "left", "right"; null: any)
     * runs [action] once: `on("swipe-4", "down") { toggleOverview() }`. It wins over bind()'s
     * for that way; the other ways keep theirs. [description] is said in the keys' help.
     */
    fun on(kind: String, direction: String? = null, description: String = "", modifier: String? = null, action: Layout.() -> Unit) {
        @Suppress("UNCHECKED_CAST")
        val list = (values["on"] as? MutableList<Map<String, Any?>>) ?: ArrayList<Map<String, Any?>>().also { values["on"] = it }
        list.removeAll { it["kind"] == kind && it["direction"] == direction && it["modifier"] == modifier }
        list += mapOf("kind" to kind, "direction" to direction, "modifier" to modifier, "description" to description, "action" to { layout: Layout -> layout.action() })
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

        /** grid { debug = true }: each step of the snapping in the log (area "grid", for tuning). */
        var debug by setting<Boolean>("debug")
    }
}

/** The keys: `"L-h" { move(-1, 0) }` — the action runs with the layout as `this`. */
@NewmConfigDsl
class KeysSection internal constructor() {
    // In newm's notation ("L-S-q"), the last binding of a key winning; with their words.
    internal val bindings = LinkedHashMap<String, Layout.() -> Unit>()
    internal val descriptions = LinkedHashMap<String, String>()

    private fun put(combination: String, description: String?, action: Layout.() -> Unit) {
        val key = KeyNotation.toNewm(combination)
        bindings.remove(key)
        bindings[key] = action
        if (description != null) descriptions[key] = description else descriptions.remove(key)
    }

    /** A key and what it does: `"Super+Backspace" { closeFocusedView() }` (or newm's "L-BackSpace"). */
    operator fun String.invoke(action: Layout.() -> Unit) = put(this, null, action)

    /** A key with what it does in words: shown in the keys' help (Logo+/). */
    operator fun String.invoke(description: String, action: Layout.() -> Unit) = put(this, description, action)

    /**
     * A key and an action by its name, in one line: `bind("Super+Backspace", "close-window")`,
     * `bind("Super+Return", "launch", "foot")`, `bind("Super+1", "go-to-window", "1")`. Every
     * name: the guide's keys page ([KeyActions.ALL]). A name not known says so on screen.
     */
    fun bind(combination: String, action: String, vararg args: String, description: String? = null) {
        val named = KeyActions.byName(action) ?: throw IllegalArgumentException(
            "bind(\"$combination\", \"$action\"): there is no action \"$action\" (there are: ${KeyActions.names()})",
        )
        val list = args.toList()
        put(combination, description ?: named.description) { named.run(this, list) }
    }

    /** The key does nothing any more (a binding of the template you do not want). */
    fun unbind(combination: String) {
        val key = KeyNotation.toNewm(combination)
        bindings.remove(key)
        descriptions.remove(key)
    }
}

/** topBar { wheel { } }: an action for each way the wheel turns over the bar. */
class BarWheel internal constructor(private val ways: MutableMap<String, Any?>) {
    fun up(action: Layout.() -> Unit) { ways["up"] = action }
    fun down(action: Layout.() -> Unit) { ways["down"] = action }
    fun left(action: Layout.() -> Unit) { ways["left"] = action }
    fun right(action: Layout.() -> Unit) { ways["right"] = action }

    /** The wheel over the bar does nothing (each way). */
    fun off() {
        for (way in listOf("up", "down", "left", "right")) ways[way] = null
    }
}

/** input { tablet { } }: a drawing tablet's place on the screens, its pen's buttons. */
class TabletSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** The monitor the tablet covers ("eDP-1"); unset: a pen screen its own monitor, other tablets all monitors as one. */
    var monitor by setting<String>("monitor")

    /** The monitor's shape kept: a strip of the tablet goes unused, nothing is stretched (true). */
    var keepAspect by setting<Boolean>("keep_aspect")

    /** Turned around for the left hand (false). */
    var leftHanded by setting<Boolean>("left_handed")

    /** The pen is only ever the pointer: no pressure or tilt for programs (false). */
    var pointerOnly by setting<Boolean>("pointer_only")

    /** How hard (0.01..1) the pen presses for a touch; unset: as libinput decides. */
    var tipPressure by setting<Double>("tip_pressure")

    /** What the pen's buttons are: "left", "right", "middle", "side", "extra" or "none" (lower: right, upper: middle, third: side). */
    var lowerButton by setting<String>("lower_button")
    var upperButton by setting<String>("upper_button")
    var thirdButton by setting<String>("third_button")

    /** Only a part of the tablet used: fractions of it, from its top left (0, 0, 1, 1: all). */
    fun area(x: Double, y: Double, width: Double, height: Double) {
        values["area"] = listOf(x, y, width, height)
    }
}
