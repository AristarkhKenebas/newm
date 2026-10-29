package newm

import dev.caulis.core.Transform
import dev.caulis.log.Logger
import dev.caulis.wm.Caulis

private val log = Logger("config")

/**
 * newm's `pywm = {...}` of a config.py, as Caulis's settings: the keyboard, the cursor, the
 * touchpad, focus following the mouse, X11. What only pywm had (its renderer, shaders) is
 * left out, and the log says so.
 */
fun applyPywmSettings(caulis: Caulis, pywm: Map<String, Any?>) {
    for ((key, value) in pywm) {
        if (value == null) continue
        // A value of the wrong kind (a word where a number goes, ...): left out, and said (a config-area warning is a notice).
        fun wrong(kind: String) = log.warn("pywm.$key: $kind wanted, ${value::class.simpleName} given ($value): left out")
        fun text(set: (String) -> Unit) = (value as? String)?.let(set) ?: wrong("a text")
        fun flag(set: (Boolean) -> Unit) = (value as? Boolean)?.let(set) ?: wrong("true or false")
        fun number(set: (Int) -> Unit) = (value as? Number)?.let { set(it.toInt()) } ?: wrong("a number")
        when (key) {
            "xkb_model" -> text { caulis.keyboardModel = it.ifEmpty { "pc105" } }
            "xkb_layout" -> text { caulis.keyboardLayouts = it.ifEmpty { "us" } }
            "xkb_variant" -> text { caulis.keyboardVariants = it }
            "xkb_options" -> text { caulis.keyboardOptions = it }
            "enable_xwayland" -> flag { caulis.xwayland = it }
            "xcursor_theme" -> text { caulis.cursorTheme = it }
            "xcursor_size" -> number { caulis.cursorSize = it }
            "tap_to_click" -> flag { caulis.input.touchpads.tap = it }
            "natural_scroll" -> flag {
                caulis.input.touchpads.naturalScroll = it
                caulis.input.pointers.naturalScroll = it
            }
            "focus_follows_mouse" -> flag { caulis.focusFollowsMouse = it }
            // input { tablet { } }: Caulis' settings of every drawing tablet.
            "tablet" -> (value as? Map<*, *>)?.let { applyTablet(caulis, it) } ?: wrong("a table")
            // newm's own (not pywm's): from keyboard { numLock } and cursor { hideAfter }.
            "numlock" -> flag { caulis.numLock = it }
            "hide_cursor_after" -> number { caulis.hideCursorAfterSeconds = it }
            // newm's own: keyboard { bindingModifiers } and compositor { }.
            "binding_modifiers" -> text { v ->
                dev.caulis.wm.BindingModifiers.entries.firstOrNull { it.name.equals(v, ignoreCase = true) }?.let { caulis.bindingModifiers = it } ?: wrong("\"xkb\" or \"physical\"")
            }
            "quit_while_locked" -> flag { caulis.keys.quitWhileLocked = it }
            "lock_failure" -> text { v ->
                dev.caulis.wm.LockFailure.entries.firstOrNull { it.name.equals(v.replace('-', '_'), ignoreCase = true) }?.let { caulis.lockFailure = it } ?: wrong("\"own-lock\" or \"notice\"")
            }
            "lock_show_millis" -> number { caulis.lockShowMillis = it.toLong().coerceIn(100, 60_000) }
            "stop_programs_on_quit" -> flag { caulis.stopProgramsOnQuit = it }
            "process_start" -> text { v ->
                dev.caulis.ffi.Processes.Method.entries.firstOrNull { it.name.equals(v, ignoreCase = true) }?.let { caulis.processStart = it } ?: wrong("\"native\" or \"java\"")
            }
            "java_updated_notice" -> text { caulis.javaUpdatedNotice = it.ifEmpty { null } }
            // newm's own: compositor { configCompilerJvmOptions, configCompilerTimeoutSeconds } (the next compilation of config.kts).
            "config_compiler_jvm_options" -> text { KtsConfig.jvmOptions = it.trim().split(Regex("\\s+")).filter(String::isNotEmpty).ifEmpty { KtsConfig.DEFAULT_JVM_OPTIONS } }
            "config_compiler_timeout_seconds" -> number { KtsConfig.timeoutSeconds = it.toLong().coerceIn(5, 3600) }
            // pywm's: programs draw their own title bars (true), or none are drawn at all.
            "encourage_csd" -> flag { caulis.titleBars = if (it) dev.caulis.core.TitleBars.PROGRAM else dev.caulis.core.TitleBars.NONE }
            else -> log.info { "pywm.$key: pywm's own (Caulis draws here), left out" }
        }
    }
}

/** newm's `outputs = [...]`: each monitor's scale, mode, position and rotation. */
fun applyOutputs(caulis: Caulis, outputs: List<Map<String, Any?>>) {
    for (o in outputs) {
        val name = o["name"] as? String ?: continue
        caulis.monitor(name) {
            (o["scale"] as? Number)?.let { scale = it.toDouble() }
            val width = (o["width"] as? Number)?.toInt()
            val height = (o["height"] as? Number)?.toInt()
            // pywm's mHz: millihertz (60000); a plain 60 is taken as hertz.
            val hz = (o["mHz"] as? Number)?.toDouble()?.let { if (it > 1000) it / 1000 else it }?.takeIf { it > 0 }
            if (width != null && height != null && width > 0 && height > 0) mode(width, height, hz)
            val x = (o["pos_x"] as? Number)?.toInt()
            val y = (o["pos_y"] as? Number)?.toInt()
            if (x != null && y != null) position(x, y)
            (o["transform"] as? Number)?.let { transform = Transform.of(it.toInt()) }
        }
    }
}

/** A config.py's key bindings as newm on Caulis takes them ((keys, action) tuples come as two-item lists). */
@Suppress("UNCHECKED_CAST")
fun keyBindingsOf(raw: Any?): List<Pair<String, () -> Unit>> = (raw as? List<*>).orEmpty().mapNotNull { item ->
    when (item) {
        is Pair<*, *> -> item as Pair<String, () -> Unit>
        is List<*> -> {
            val keys = item.getOrNull(0) as? String
            val action = item.getOrNull(1) as? () -> Any?
            if (keys != null && action != null) keys to { action(); Unit } else null
        }
        else -> null
    }
}

/** What config.py's functions may call on `layout` and on views, by newm's (Python) names. */
class LayoutApi(private val layout: Layout) : PyConfig.Handler {

    override fun layout(method: String, args: List<Any?>, kwargs: Map<String, Any?>): Any? {
        fun arg(i: Int, name: String): Any? = if (i < args.size) args[i] else kwargs[name]
        fun int(i: Int, name: String, default: Int): Int = (arg(i, name) as? Number)?.toInt() ?: default
        fun bool(i: Int, name: String, default: Boolean): Boolean = arg(i, name) as? Boolean ?: default
        return when (method) {
            "move" -> layout.move(int(0, "delta_i", 0), int(1, "delta_j", 0))
            "basic_move" -> layout.basicMove(int(0, "delta_i", 0), int(1, "delta_j", 0))
            "basic_scale" -> layout.basicScale(int(0, "delta_s", 0))
            "move_in_stack" -> layout.moveInStack(int(0, "delta", 1))
            "move_next_view" -> layout.moveNextView(int(0, "dv", 1), bool(1, "active_workspace", true))
            "move_workspace" -> layout.moveWorkspace(int(0, "ds", 1))
            "cycle_views" -> layout.cycleViews(int(0, "steps", 1), bool(1, "active_workspace", true))
            "move_prev_view" -> layout.movePrevView(bool(0, "active_workspace", true))
            "goto_view" -> layout.gotoView(int(0, "index", 0), bool(1, "active_workspace", true))
            "focus_view" -> (arg(0, "view") as? View)?.let(layout::focusView)
            "toggle_overview" -> layout.toggleOverview(bool(0, "only_active_workspace", false))
            "fit_focused_view" -> layout.fitFocusedView()
            "side_by_side" -> layout.sideBySide()
            "restore_rest" -> layout.restoreRest()
            "screenshot" -> layout.screenshot()
            "screenshot_screen" -> layout.screenshotScreen()
            "screenshot_window" -> layout.screenshotWindow()
            "toggle_key_help" -> layout.toggleKeyHelp()
            "toggle_clipboard" -> layout.toggleClipboard()
            "scratchpad" -> (arg(0, "name") as? String)?.let(layout::scratchpad)
            "toggle_fullscreen" -> layout.toggleFullscreen(arg(0, "defined_state") as? Boolean)
            "toggle_focused_view_floating" -> layout.toggleFocusedViewFloating()
            "change_focused_view_workspace" -> layout.changeFocusedViewWorkspace(int(0, "ds", 1))
            "move_focused_view" -> layout.moveFocusedView(int(0, "di", 0), int(1, "dj", 0))
            "resize_focused_view" -> layout.resizeFocusedView(int(0, "di", 0), int(1, "dj", 0))
            "close_focused_view", "close_view" -> layout.closeFocusedView()
            "swallow_focused_view" -> layout.swallowFocusedView()
            "enter_launcher_overlay" -> layout.enterLauncherOverlay()
            "exit_overlay" -> layout.exitOverlay()
            "update_config" -> layout.updateConfig()
            "terminate" -> layout.terminate()
            "ensure_locked" -> layout.ensureLocked(bool(0, "anim", true), bool(1, "dim", false))
            "command" -> layout.command(arg(0, "cmd") as String, arg(1, "arg") as String?)
            "launch_app" -> layout.launchApp(arg(0, "cmd") as String)
            "debug_str" -> layout.debugStr()
            "find_focused_view" -> layout.findFocusedView()
            "tiles" -> layout.tiles()
            "floats" -> layout.floats()
            "panels" -> layout.panels()
            "views" -> layout.views()
            else -> throw IllegalArgumentException("newm on Caulis has no layout.$method")
        }
    }

    override fun view(handle: Int, method: String, args: List<Any?>, kwargs: Map<String, Any?>): Any? {
        val view = viewOf(handle) ?: throw IllegalArgumentException("there is no view $handle any more")
        return when (method) {
            "focus" -> view.focus()
            "close" -> view.close()
            "is_tiled" -> view.isTiled(layout.state)
            "is_float" -> view.isFloat(layout.state)
            "is_panel" -> view.isPanel()
            "is_focused" -> view.isFocused()
            "set_fullscreen" -> view.setFullscreen(args.firstOrNull() as? Boolean ?: true)
            else -> throw IllegalArgumentException("newm on Caulis has no view.$method")
        }
    }

    override fun viewData(view: View): Map<String, Any?> = viewData(view, depth = 0)

    private fun viewData(view: View, depth: Int): Map<String, Any?> = mapOf(
        "handle" to view.handle,
        "app_id" to view.appId,
        "title" to view.title,
        "role" to view.role,
        "pid" to view.pid,
        "panel" to view.panel,
        "xwayland" to view.isXwayland,
        "parent" to view.parent?.takeIf { depth < 8 }?.let { viewData(it, depth + 1) },
        "up_state" to view.upState?.let { up ->
            mapOf(
                "is_mapped" to up.isMapped,
                "is_floating" to up.isFloating,
                "is_focused" to up.isFocused,
                "shows_csd" to up.showsCsd,
                "size" to listOf(up.size.first, up.size.second),
                "offset" to listOf(up.offset.first, up.offset.second),
                "size_constraints" to up.sizeConstraints.toList(),
            )
        },
    )

    override fun viewOf(handle: Int): View? = layout.viewByHandle(handle)
}

// A pen button's name ("right", "none") as Linux' button code (0: none), or null if it is none of them.
private fun penButton(name: Any?): Int? = when (name?.toString()?.trim()?.lowercase()) {
    "left" -> 0x110
    "right" -> 0x111
    "middle" -> 0x112
    "side" -> 0x113
    "extra" -> 0x114
    "none", "" -> 0
    else -> null
}

// input { tablet { } } onto Caulis' tablet settings; what is wrong is said and left out.
private fun applyTablet(caulis: dev.caulis.wm.Caulis, tablet: Map<*, *>) {
    val t = caulis.input.tablets
    for ((key, value) in tablet) {
        fun wrong(kind: String) = log.warn("input.tablet.$key: $kind wanted, ${value?.let { it::class.simpleName }} given ($value): left out")
        when (key) {
            "monitor" -> (value as? String)?.let { t.monitor = it.ifBlank { null } } ?: wrong("a monitor's name")
            "keep_aspect" -> (value as? Boolean)?.let { t.keepAspect = it } ?: wrong("true or false")
            "left_handed" -> (value as? Boolean)?.let { t.leftHanded = it } ?: wrong("true or false")
            "pointer_only" -> (value as? Boolean)?.let { t.pointerOnly = it } ?: wrong("true or false")
            "tip_pressure" -> (value as? Number)?.let { t.tipPressure = it.toDouble().coerceIn(0.01, 1.0) } ?: wrong("a number 0.01..1")
            "lower_button" -> penButton(value)?.let { t.lowerButton = it } ?: wrong("left, right, middle, side, extra or none")
            "upper_button" -> penButton(value)?.let { t.upperButton = it } ?: wrong("left, right, middle, side, extra or none")
            "third_button" -> penButton(value)?.let { t.thirdButton = it } ?: wrong("left, right, middle, side, extra or none")
            "area" -> (value as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() }?.takeIf { it.size == 4 }
                ?.let { (x, y, w, h) -> runCatching { t.area = dev.caulis.core.input.TabletArea(x, y, w, h) }.onFailure { e -> log.warn("input.tablet.area: ${e.message}") } } ?: wrong("four numbers [x, y, width, height]")
            else -> log.info { "input.tablet.$key: not a tablet setting, left out" }
        }
    }
    log.info { "Drawing tablets: ${tablet.keys.joinToString()} set" }
}
