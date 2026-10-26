package newm

import dev.caulis.wm.Caulis

/**
 * A configuration as data — config.json (and so Nix, which writes it), and config.lua's
 * tables — turned into newm's settings. The sections and names are newm's own (those of
 * config.py: `view`, `focus`, `panels`, `corner_radius`, ...), plus forms easier to write:
 *
 * ```json
 * {
 *   "view": { "corner_radius": 24, "padding": 8 },
 *   "keyboard": { "layouts": "us,ru", "options": "grp:alt_shift_toggle" },
 *   "keys": { "L-Return": "launch alacritty", "L-h": "move -1 0", "XF86AudioMute": "volume mute" },
 *   "rules": [ { "app_id": "mpv", "float": true }, { "app_id": "Alacritty", "blur": { "radius": 6, "passes": 4 } } ],
 *   "startup": [ "mako" ]
 * }
 * ```
 *
 * An action is "terminal" (the one installed), a layout's method by its name and its arguments ("move -1 0",
 * "toggle_overview only_active_workspace=true", "ensure_locked dim=true"), "launch" and a
 * command, or one of Caulis's actions ("volume +5", "brightness -5", "screenshot").
 */
object DataConfig {

    /** newm's settings of [data], and what is wrong in it. */
    fun settings(data: Map<String, Any?>, caulis: Caulis, name: String): Pair<Map<String, Any?>, List<String>> {
        val problems = ArrayList<String>()
        val out = LinkedHashMap<String, Any?>()
        @Suppress("UNCHECKED_CAST")
        fun section(key: String): MutableMap<String, Any?> = out.getOrPut(key) { LinkedHashMap<String, Any?>() } as MutableMap<String, Any?>

        for ((key, value) in data) {
            when (key) {
                "keys" -> {
                    val keys = value as? Map<*, *> ?: problems.add("$name: keys is a table of combination = action").let { continue }
                    val bindings = keys.entries.mapNotNull { (combo, action) ->
                        val run = actionOf(action, caulis, "$name: keys.$combo", problems) ?: return@mapNotNull null
                        combo.toString() to run
                    }
                    out["key_bindings"] = { layout: Layout -> bindings.map { (combo, run) -> combo to { run(layout) } } }
                }
                "rules" -> {
                    val rules = (value as? List<*>)?.filterIsInstance<Map<*, *>>() ?: problems.add("$name: rules is a list of tables").let { continue }
                    section("view")["rules"] = { view: View -> ruleFor(view, rules) }
                }
                "startup" -> {
                    val commands = (value as? List<*>)?.map { it.toString() } ?: problems.add("$name: startup is a list of commands").let { continue }
                    out["on_startup"] = { commands.forEach(caulis::launch) }
                }
                "keyboard" -> (value as? Map<*, *>)?.let { k ->
                    val pywm = section("pywm")
                    k["layouts"]?.let { pywm["xkb_layout"] = it }
                    k["variants"]?.let { pywm["xkb_variant"] = it }
                    k["options"]?.let { pywm["xkb_options"] = it }
                    k["model"]?.let { pywm["xkb_model"] = it }
                    k["numlock"]?.let { pywm["numlock"] = it }
                }
                "cursor" -> (value as? Map<*, *>)?.let { c ->
                    val pywm = section("pywm")
                    c["theme"]?.let { pywm["xcursor_theme"] = it }
                    c["size"]?.let { pywm["xcursor_size"] = it }
                    c["hide_after"]?.let { pywm["hide_cursor_after"] = it }
                }
                "input" -> (value as? Map<*, *>)?.let { i ->
                    val pywm = section("pywm")
                    i["tap_to_click"]?.let { pywm["tap_to_click"] = it }
                    i["natural_scroll"]?.let { pywm["natural_scroll"] = it }
                    i["focus_follows_mouse"]?.let { pywm["focus_follows_mouse"] = it }
                    i["xwayland"]?.let { pywm["enable_xwayland"] = it }
                    i["title_bars"]?.let { pywm["encourage_csd"] = it }
                }
                else -> out[key] = merge(out[key], value)
            }
        }
        // A bar's texts as names ("wifi", "time"): what the system says (other texts as they are).
        @Suppress("UNCHECKED_CAST")
        for (bar in listOf("top_bar", "bottom_bar")) {
            val panel = (out["panels"] as? MutableMap<String, Any?>)?.get(bar) as? MutableMap<String, Any?> ?: continue
            // newm's own bar also written without "native" ("top_bar": { "enabled": false }).
            if ("native" !in panel) panel.keys.filter { it in NATIVE_BAR_KEYS }.forEach { key ->
                (panel.getOrPut("native") { LinkedHashMap<String, Any?>() } as MutableMap<String, Any?>)[key] = panel.remove(key)
            }
            val native = panel["native"] as? MutableMap<String, Any?> ?: continue
            val names = native["texts"] as? List<*> ?: continue
            native["texts"] = { names.map { n -> SYSTEM_TEXTS[n.toString()]?.invoke() ?: n.toString() } }
        }
        return out to problems
    }

    // A value onto what is there already (sections merge, the rest replaces).
    @Suppress("UNCHECKED_CAST")
    private fun merge(old: Any?, new: Any?): Any? =
        if (old is MutableMap<*, *> && new is Map<*, *>) (old as MutableMap<String, Any?>).also { o -> new.forEach { (k, v) -> o[k.toString()] = merge(o[k.toString()], v) } } else new

    /** What an action runs: a text (as above) or a function of config.lua; null if it is none (told in [problems]). */
    fun actionOf(action: Any?, caulis: Caulis, where: String, problems: MutableList<String>): ((Layout) -> Unit)? = when (action) {
        is String -> parseAction(action, caulis, where, problems)
        is Function0<*> -> { _ -> action() }
        else -> null.also { problems += "$where: an action is a text (\"move -1 0\") or a function" }
    }

    private val layoutMethods = setOf(
        "move", "basic_move", "basic_scale", "move_in_stack", "move_next_view", "move_prev_view", "cycle_views", "goto_view",
        "move_workspace", "toggle_overview", "toggle_fullscreen", "toggle_focused_view_floating", "change_focused_view_workspace",
        "move_focused_view", "resize_focused_view", "close_focused_view", "close_view", "swallow_focused_view",
        "enter_launcher_overlay", "exit_overlay", "update_config", "terminate", "ensure_locked", "command", "launch_app",
    )

    private fun parseAction(text: String, caulis: Caulis, where: String, problems: MutableList<String>): ((Layout) -> Unit)? {
        val trimmed = text.trim()
        val word = trimmed.substringBefore(' ')
        val rest = trimmed.substringAfter(' ', "").trim()
        val method = camelToSnake(word)
        if (method == "terminal") return { _ -> caulis.launch(defaultTerminal()) }
        if (method == "launch") {
            if (rest.isEmpty()) return null.also { problems += "$where: launch needs a command (\"launch alacritty\")" }
            return { _ -> caulis.launch(rest) }
        }
        if (method in layoutMethods) {
            val args = ArrayList<Any?>()
            val kwargs = LinkedHashMap<String, Any?>()
            for (token in rest.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
                if ('=' in token) kwargs[camelToSnake(token.substringBefore('='))] = literal(token.substringAfter('=')) else args += literal(token)
            }
            return { layout -> LayoutApi(layout).layout(method, args, kwargs) }
        }
        // Not newm's: one of Caulis's actions (checked now, so a typo is told at once).
        return try {
            dev.caulis.wm.config.Actions.parse(trimmed)
            val run: (Layout) -> Unit = { caulis.run(trimmed) }
            run
        } catch (e: IllegalArgumentException) {
            problems += "$where: \"$trimmed\" is neither a layout method (move, toggle_overview, ...) nor an action of Caulis (${e.message})"
            null
        }
    }

    private fun literal(token: String): Any? = when {
        token == "true" -> true
        token == "false" -> false
        token == "null" || token == "None" -> null
        token.toLongOrNull() != null -> token.toLong()
        token.toDoubleOrNull() != null -> token.toDouble()
        else -> token.trim('"', '\'')
    }

    private fun camelToSnake(name: String): String = name.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase().replace('-', '_')

    // The first rule that fits the view (app_id, title: exactly, or app_id_matches / title_matches: a regex).
    private fun ruleFor(view: View, rules: List<Map<*, *>>): Map<String, Any?>? {
        for (r in rules) {
            if (r["app_id"]?.let { it != view.appId } == true) continue
            if (r["title"]?.let { it != view.title } == true) continue
            if (r["app_id_matches"]?.let { !Regex(it.toString()).containsMatchIn(view.appId) } == true) continue
            if (r["title_matches"]?.let { !Regex(it.toString()).containsMatchIn(view.title) } == true) continue
            return r.entries.filter { it.key !in setOf("app_id", "title", "app_id_matches", "title_matches") }.associate { it.key.toString() to it.value }
        }
        return null
    }
}

// The settings of newm's own bar (panels.top_bar.native in newm's names).
private val NATIVE_BAR_KEYS = setOf(
    "enabled", "height", "font_size", "font", "background", "foreground", "blur", "texts", "style", "margin", "corner_radius", "theme",
    "island_color", "text_color", "accent_color", "left", "center", "right", "clock", "date", "icon_size", "show_percent", "show_passive", "title_width",
)
