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
        when (key) {
            "xkb_model" -> caulis.keyboardModel = (value as String).ifEmpty { "pc105" }
            "xkb_layout" -> caulis.keyboardLayouts = (value as String).ifEmpty { "us" }
            "xkb_variant" -> caulis.keyboardVariants = value as String
            "xkb_options" -> caulis.keyboardOptions = value as String
            "enable_xwayland" -> caulis.xwayland = value as Boolean
            "xcursor_theme" -> caulis.cursorTheme = value as String
            "xcursor_size" -> caulis.cursorSize = (value as Number).toInt()
            "tap_to_click" -> caulis.input.touchpads.tap = value as Boolean
            "natural_scroll" -> {
                caulis.input.touchpads.naturalScroll = value as Boolean
                caulis.input.pointers.naturalScroll = value
            }
            "focus_follows_mouse" -> caulis.focusFollowsMouse = value as Boolean
            // newm's own (not pywm's): from keyboard { numLock } and cursor { hideAfter }.
            "numlock" -> caulis.numLock = value as Boolean
            "hide_cursor_after" -> caulis.hideCursorAfterSeconds = (value as Number).toInt()
            // pywm's: programs draw their own title bars (true), or none are drawn at all.
            "encourage_csd" -> caulis.titleBars = if (value as Boolean) dev.caulis.core.TitleBars.PROGRAM else dev.caulis.core.TitleBars.NONE
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
            "toggle_key_help" -> layout.toggleKeyHelp()
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
