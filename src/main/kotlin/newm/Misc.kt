package newm

import dev.caulis.log.Logger
import dev.caulis.wm.Caulis

/*
 * What Hyprland users look for and newm had elsewhere or not at all: env("NAME", "value"),
 * permissions { }, and misc { } (window swallowing, focus on activation, waking the screens,
 * variable refresh rate, middle-click paste, the colour under everything).
 */

private val log = Logger("config")

/** permissions { }: Hyprland's permission rules, checked here (a mistake is shown at its line). */
class PermissionsSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /**
     * Programs whose path ("/usr/bin/grim") or name ("grim") matches [program] (a regular
     * expression) get [mode] — "allow", "ask" or "deny" — for [feature]: "screencopy" (pictures
     * and recordings of the screen; "ask" shows a card with allow / deny), "window-list" (docks,
     * taskbars), "clipboard" (clipboard managers), "panels" (bars, wallpapers, launchers),
     * "lock", "monitors" (arranging, switching off), "gamma" (night light). The first rule that
     * matches decides.
     */
    fun rule(program: String, feature: String, mode: String) {
        dev.caulis.wm.Permissions().rule(program, feature, mode) // (checked: a mistake is said here)
        @Suppress("UNCHECKED_CAST")
        val rules = values.getOrPut("rules") { ArrayList<Map<String, Any?>>() } as MutableList<Map<String, Any?>>
        rules += linkedMapOf("program" to program, "feature" to feature, "mode" to mode)
    }

    /** What [feature] gets for programs no rule is about (Hyprland's enforce_permissions: default("screencopy", "ask")). */
    fun default(feature: String, mode: String) {
        dev.caulis.wm.Permissions().default(feature, mode)
        nested("defaults")[feature] = mode
    }
}

/** misc { }: Hyprland's misc settings that newm has. */
class MiscSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /**
     * Window swallowing (Hyprland's enable_swallow): a window opened from a terminal takes
     * the terminal's place, and the terminal comes back when it closes. False by default.
     */
    var swallow by setting<Boolean>("swallow")

    /** The terminals that swallow (a regular expression for their app id; Hyprland's swallow_regex). */
    var swallowApps by setting<String>("swallow_apps")

    /** Terminals whose title matches this (a regular expression) do not swallow (Hyprland's swallow_exception_regex): "wev|vim". */
    var swallowExceptTitles by setting<String>("swallow_except_titles")

    /** A window asking for the focus (a link opened in the browser) gets it; false: it is only marked (true by default). */
    var focusOnActivate by setting<Boolean>("focus_on_activate")

    /** A key, a click or the mouse moving switches the screens on again (true by default; Hyprland's key_press/mouse_move_enables_dpms). */
    var wakeOnInput by setting<Boolean>("wake_on_input")

    /** Variable refresh rate (FreeSync) on every monitor that has it (Hyprland's vrr; false by default). output("DP-1") { } is not needed. */
    var vrr by setting<Boolean>("vrr")

    /** Middle-click paste of what is selected (true by default). Takes effect at the next start. */
    var middleClickPaste by setting<Boolean>("middle_click_paste")

    /** The colour under everything, where no window or wallpaper is ("#1e1e2e"; Hyprland's background_color). */
    var backgroundColor by setting<String>("background_color")
}

// The variables env(...) set last: a reload without one takes it away again.
private val envSet = LinkedHashSet<String>()

/** env, permissions and misc of the configuration ([settings]: newm's names) as Caulis' settings. */
@Suppress("UNCHECKED_CAST")
fun applyMiscSettings(caulis: Caulis, settings: Map<String, Any?>) {
    // env: for every program started from now on (and the session's services at the start).
    val env = (settings["env"] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v?.toString() }.orEmpty()
    for (gone in envSet - env.keys) caulis.environment.remove(gone)
    envSet.clear()
    for ((name, value) in env) {
        caulis.environment[name] = value
        envSet += name
    }
    if (env.isNotEmpty()) log.info { "env: ${env.keys.joinToString()} set for the programs newm starts" }

    // permissions: replaced as a whole (none said: as before, every program may).
    caulis.permissions.clear()
    (settings["permissions"] as? Map<*, *>)?.let { p ->
        try {
            (p["rules"] as? List<*>)?.forEach { r ->
                val m = r as? Map<*, *> ?: return@forEach
                caulis.permissions.rule(m["program"].toString(), m["feature"].toString(), m["mode"].toString())
            }
            (p["defaults"] as? Map<*, *>)?.forEach { (feature, mode) -> caulis.permissions.default(feature.toString(), mode.toString()) }
            log.info { "permissions: ${caulis.permissions.rules.size} rules, defaults ${caulis.permissions.defaults}" }
        } catch (e: IllegalArgumentException) {
            log.warn("permissions: ${e.message}")
        }
    }

    // misc: each as said; one said before and not now goes back to its default.
    val misc = settings["misc"] as? Map<*, *> ?: emptyMap<String, Any?>()
    fun flag(name: String, default: Boolean, set: (Boolean) -> Unit) {
        val value = misc[name]
        when {
            value is Boolean -> set(value)
            value != null -> log.warn("misc.$name: true or false, not $value")
            name in miscGiven -> set(default)
        }
    }
    flag("focus_on_activate", true) { caulis.focusOnActivate = it }
    flag("wake_on_input", true) { caulis.wakeScreensOnInput = it }
    flag("vrr", false) { caulis.adaptiveSync = it }
    flag("middle_click_paste", true) { caulis.primarySelection = it }
    (misc["background_color"] as? String)?.let { text ->
        try {
            caulis.backgroundColor = dev.caulis.render.Color.hex(text)
        } catch (e: IllegalArgumentException) {
            log.warn("misc.background_color: \"$text\" is not a colour (${e.message})")
        }
    }
    miscGiven.clear()
    misc.keys.forEach { miscGiven += it.toString() }

    // windowRule { } and layerRule { }: those of before taken out, these put in.
    windowRulesAdded.forEach { caulis.removeRule(it) }
    windowRulesAdded.clear()
    (settings["window_rules"] as? List<*>)?.forEach { configure ->
        try {
            @Suppress("UNCHECKED_CAST")
            windowRulesAdded += caulis.addRule(configure as dev.caulis.wm.WindowRule.() -> Unit)
        } catch (e: Exception) {
            log.warn("windowRule: $e")
        }
    }
    layerRulesAdded.forEach { caulis.removePanelRule(it) }
    layerRulesAdded.clear()
    (settings["layer_rules"] as? List<*>)?.forEach { configure ->
        try {
            @Suppress("UNCHECKED_CAST")
            layerRulesAdded += caulis.addPanelRule(configure as dev.caulis.wm.PanelRule.() -> Unit)
        } catch (e: Exception) {
            log.warn("layerRule: $e")
        }
    }
}

// The Caulis rules windowRule { } and layerRule { } added last time.
private val windowRulesAdded = ArrayList<dev.caulis.wm.WindowRule>()
private val layerRulesAdded = ArrayList<dev.caulis.wm.PanelRule>()

// The misc settings the configuration gave last time.
private val miscGiven = HashSet<String>()

/** Window swallowing (misc { swallow }): a window opened from a terminal takes its place. */
object Swallowing {
    private val log = Logger("windows")

    // A window's process, and a process's parent (tests: a made-up tree of processes).
    internal var processOf: (View) -> Long = { it.pid.toLong() }
    internal var parentOf: (Long) -> Long? = ::parentPid

    /**
     * The window whose program started [view]'s (a terminal a player was started from): the
     * nearest of [view]'s process's ancestors that has a window, not [view]'s own process (a
     * terminal server's new window is not swallowing its others); of several windows of that
     * process, the one that had the focus when [view] opened, else the first.
     */
    internal fun launcherOf(view: View, candidates: List<View>): View? =
        launcherOf(processOf(view), candidates.filter { it !== view }.map { it to processOf(it) }, view.focusedAtOpen, parentOf)

    internal fun <V> launcherOf(pid: Long, candidates: List<Pair<V, Long>>, preferred: V?, parentOf: (Long) -> Long?): V? {
        if (pid <= 1) return null
        var p = parentOf(pid)
        // (Deep enough for any wrapper; a loop in what /proc says ends here too.)
        repeat(64) {
            val ancestor = p ?: return null
            if (ancestor <= 1) return null
            val there = candidates.filter { it.second == ancestor }.map { it.first }
            if (there.isNotEmpty()) return preferred?.takeIf { it in there } ?: there.first()
            p = parentOf(ancestor)
        }
        return null
    }
    private val confSwallow = configuredValue("misc.swallow", false)
    private val confApps = configuredValue("misc.swallow_apps", "(?i)foot|footclient|alacritty|kitty|org\\.wezfurlong\\.wezterm|com\\.mitchellh\\.ghostty|xterm|urxvt|konsole|org\\.kde\\.konsole|org\\.gnome\\.Console|org\\.gnome\\.Terminal|gnome-terminal.*|xfce4-terminal")
    private val confExcept = configuredValue("misc.swallow_except_titles", "")

    /** [view] was just shown: it swallows the terminal it was started from, if the settings say so. */
    fun shown(wm: Layout, view: View) {
        if (!confSwallow()) return
        // (The terminal that started it: newm's findSwallower looks the other way, from the
        // terminal to what it started, for the swallow key on the focused terminal.)
        val by = try {
            launcherOf(view, wm.views())
        } catch (e: Exception) {
            null.also { log.trace { "Swallowing: no terminal found ($e)" } }
        } ?: return
        val apps = regex(confApps(), "swallow_apps") ?: return
        if (!apps.matches(by.appId)) return
        if (confExcept().isNotEmpty() && regex(confExcept(), "swallow_except_titles")?.containsMatchIn(by.title) == true) return
        log.info { "${view.appId} takes the place of the terminal ${by.appId} it was started from (misc.swallow)" }
        wm.animateTo({ s ->
            // (Both tiled and still there; the window takes the terminal's tiles, the terminal hides behind it.)
            val vs = s.findViewOrNull(view)?.first ?: return@animateTo null to null
            val bs = s.findViewOrNull(by)?.first ?: return@animateTo null to null
            if (!vs.isTiled || !bs.isTiled || bs.swallowed != null) return@animateTo null to null
            val next = s.focusingView(view)
            // (On screens: its screen and place there too, so the terminal comes back to them when it closes.)
            next.updateViewState(view) { i = bs.i; j = bs.j; w = bs.w; h = bs.h; page = bs.page; slot = bs.slot; sized = null }
            next.updateViewState(by) { swallowed = view.handle }
            null to next.constrain()
        }, "windowsMove")
    }

    private fun regex(text: String, name: String): Regex? = try {
        Regex(text)
    } catch (e: Exception) {
        null.also { log.warn("misc.$name: \"$text\" is not a regular expression") }
    }
}

/** Caulis' permission questions as newm's cards: allow while it runs, allow until newm quits, deny. */
fun permissionAsker(wm: Layout): (dev.caulis.wm.PermissionQuestion) -> Unit = { question ->
    val name = newm.bar.verbatim(question.name)
    val summary = when (question.feature) {
        "screencopy" -> newm.bar.tr("May $name take pictures of the screen?")
        else -> newm.bar.tr("May $name use ${question.feature}?")
    }
    // (The card's buttons answer; closing it is no.)
    wm.caulis.post {
        wm.barModel.notifications.ask(
            summary = summary,
            body = question.program,
            buttons = listOf("allow" to newm.bar.tr("Allow"), "always" to newm.bar.tr("Allow until newm quits"), "deny" to newm.bar.tr("Deny")),
        ) { answer ->
            question.answer(
                when (answer) {
                    "allow" -> dev.caulis.wm.PermissionAnswer.ALLOW_WHILE_RUNNING
                    "always" -> dev.caulis.wm.PermissionAnswer.ALLOW_UNTIL_QUIT
                    else -> dev.caulis.wm.PermissionAnswer.DENY
                },
            )
        }
    }
}

/**
 * input { }, touchpad { } and device("name") { } settings ([m]: newm's names) onto Caulis'
 * [into] — Hyprland's input, input:touchpad and device { }. A wrong value is said and left out.
 */
internal fun applyDeviceSettings(into: dev.caulis.core.input.DeviceSettings, m: Map<*, *>, where: String) {
    for ((k, value) in m) {
        if (value == null) continue
        val key = k.toString()
        fun wrong(kind: String) = log.warn("$where.$key: $kind wanted, not $value: left out")
        fun flag(set: (Boolean) -> Unit) = (value as? Boolean)?.let(set) ?: wrong("true or false")
        fun number(set: (Double) -> Unit) = (value as? Number)?.let { set(it.toDouble()) } ?: wrong("a number")
        fun <E : Enum<E>> choice(entries: List<E>, names: Map<String, E>, set: (E) -> Unit) =
            (value as? String)?.let { v -> names[v.lowercase().replace('_', '-')] ?: entries.firstOrNull { it.name.equals(v.replace('-', '_'), ignoreCase = true) } }?.let(set)
                ?: wrong(names.keys.joinToString(", ") { "\"$it\"" })
        when (key) {
            "enabled" -> flag { into.enabled = it }
            "pointer_speed" -> number { into.speed = it.coerceIn(-1.0, 1.0) }
            "accel_profile" -> choice(dev.caulis.core.input.AccelProfile.entries, mapOf("flat" to dev.caulis.core.input.AccelProfile.FLAT, "adaptive" to dev.caulis.core.input.AccelProfile.ADAPTIVE)) { into.accelProfile = it }
            "natural_scroll" -> flag { into.naturalScroll = it }
            "left_handed" -> flag { into.leftHanded = it }
            "scroll_factor" -> number { into.scrollFactor = it.coerceIn(0.01, 100.0) }
            "scroll_method" -> choice(dev.caulis.core.input.ScrollMethod.entries, mapOf(
                "two-finger" to dev.caulis.core.input.ScrollMethod.TWO_FINGER, "2fg" to dev.caulis.core.input.ScrollMethod.TWO_FINGER,
                "edge" to dev.caulis.core.input.ScrollMethod.EDGE, "button" to dev.caulis.core.input.ScrollMethod.BUTTON,
                "on-button-down" to dev.caulis.core.input.ScrollMethod.BUTTON, "none" to dev.caulis.core.input.ScrollMethod.NONE, "no-scroll" to dev.caulis.core.input.ScrollMethod.NONE,
            )) { into.scrollMethod = it }
            "scroll_button" -> number { into.scrollButton = it.toInt() }
            "middle_button_emulation" -> flag { into.middleEmulation = it }
            "tap_to_click" -> flag { into.tap = it }
            "disable_while_typing" -> flag { into.disableWhileTyping = it }
            "tap_and_drag" -> flag { into.tapDrag = it }
            "drag_lock" -> flag { into.dragLock = it }
            "click_finger" -> flag { into.clickMethod = if (it) dev.caulis.core.input.ClickMethod.FINGERS else dev.caulis.core.input.ClickMethod.AREAS }
            "tap_buttons" -> choice(dev.caulis.core.input.TapButtons.entries, mapOf("lrm" to dev.caulis.core.input.TapButtons.LEFT_RIGHT_MIDDLE, "lmr" to dev.caulis.core.input.TapButtons.LEFT_MIDDLE_RIGHT)) { into.tapButtons = it }
            "off_with_mouse" -> flag { into.offWithMouse = it }
            else -> log.warn("$where.$key: there is no such setting")
        }
    }
}
