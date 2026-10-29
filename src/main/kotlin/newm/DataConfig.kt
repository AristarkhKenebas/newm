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
                    // Keys as people write them ("Super+Backspace") or newm's ("L-BackSpace"); null or false: none.
                    val bindings = LinkedHashMap<String, (Layout) -> Unit>()
                    val help = LinkedHashMap<String, String>()
                    for ((combo, action) in keys.entries) {
                        val key = KeyNotation.toNewmOrNull(combo.toString(), "$name: keys", problems) ?: continue
                        if (action == null || action == false) {
                            bindings.remove(key)
                            help.remove(key)
                            continue
                        }
                        val run = actionOf(action, caulis, "$name: keys.$combo", problems) ?: continue
                        bindings.remove(key)
                        bindings[key] = run
                        // The help (Logo+/) says a named action's words, else the action as it is written ("move -1 0").
                        help[key] = (action as? String)?.let { a -> KeyActions.byName(a.trim().substringBefore(' '))?.description ?: a } ?: ""
                    }
                    out["key_bindings"] = { layout: Layout -> bindings.map { (combo, run) -> combo to { run(layout) } } }
                    out["key_help"] = help.toList()
                }
                "rules" -> {
                    val rules = (value as? List<*>)?.filterIsInstance<Map<*, *>>() ?: problems.add("$name: rules is a list of tables").let { continue }
                    section("view")["rules"] = { view: View -> ruleFor(view, rules) }
                }
                // autostart { stagger_ms, entries [ { app, shell, script, ... } ] }: Caulis's own (see Startup.kt).
                "autostart" -> out["autostart"] = autostartOf(value, name, problems)
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
                    k["binding_modifiers"]?.let { pywm["binding_modifiers"] = it }
                }
                // compositor { quit_while_locked, lock_failure, lock_show_millis, stop_programs_on_quit, process_start, java_updated_notice, config_compiler_* }.
                "compositor" -> (value as? Map<*, *>)?.let { c ->
                    val pywm = section("pywm")
                    for (key in COMPOSITOR_KEYS) c[key]?.let { pywm[key] = it }
                    c.keys.map { it.toString() }.filter { it !in COMPOSITOR_KEYS }.forEach { problems += "$name: compositor.$it: there is no such setting (there are: ${COMPOSITOR_KEYS.joinToString()})" }
                } ?: problems.add("$name: compositor is a table")
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
                    // input { tablet { } } (its names as they are: monitor, left_handed, lower_button, ...).
                    (i["tablet"] as? Map<*, *>)?.let { pywm["tablet"] = merge(pywm["tablet"], it.entries.associate { (k, v) -> k.toString() to v }) }
                }
                // launcher { } and lock { } at the top: the same as panels { launcher { } }, panels { lock { } }.
                "launcher", "lock" -> out["panels"] = merge(out["panels"], linkedMapOf<String, Any?>(key to (value as? Map<*, *> ?: problems.add("$name: $key is a table").let { continue })))
                // gestures { enabled, scale_px, dbus_enabled, bind { }, on [ ] } (and newm's own: gestures.c, gesture_bindings).
                "gestures" -> gestures(value as? Map<*, *> ?: problems.add("$name: gestures is a table").let { continue }, out, caulis, name, problems)
                // tuning { swipe { }, grid { }, ... }: newm's own names at the top.
                "tuning" -> (value as? Map<*, *>)?.forEach { (part, values) -> out[part.toString()] = merge(out[part.toString()], values) }
                    ?: problems.add("$name: tuning is a table of swipe, swipe_zoom, move, resize, move_resize, grid, interpolation")
                // outputs [ { name, x, y, refresh_hz } ] (and newm's own: pos_x, pos_y, mHz).
                "outputs" -> out["outputs"] = (value as? List<*>)?.map { o ->
                    (o as? Map<*, *>)?.entries?.associate { (k, v) ->
                        when (k.toString()) {
                            "x" -> "pos_x" to v
                            "y" -> "pos_y" to v
                            "refresh_hz" -> "mHz" to ((v as? Number)?.toDouble()?.times(1000) ?: v)
                            else -> k.toString() to v
                        }
                    } ?: o
                } ?: value
                // energy { on_idle = function(what) ... end, after = { [300] = "ensure_locked" } }.
                "energy" -> {
                    val energy = (value as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v }?.toMutableMap() ?: problems.add("$name: energy is a table").let { continue }
                    energy.remove("on_idle")?.let { energy["idle_callback"] = it }
                    energy.remove("after")?.let { energy["after"] = idleActions(it, caulis, name, problems) }
                    out["energy"] = merge(out["energy"], energy)
                }
                // animations { preset, enabled, millis, curve, curves, and each animation by name (camelCase, as Hyprland's) }.
                "animations" -> {
                    val given = (value as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: problems.add("$name: animations is a table").let { continue }
                    val found = ArrayList<String>()
                    NewmAnimations.read(given, 0.3, found)
                    found.forEach { problems += "$name: animations: $it" }
                    out["animations"] = merge(out["animations"], given)
                }
                // Events by the DSL's names (newm's own work too: on_startup, synchronous_update).
                "every_frame" -> out["synchronous_update"] = value
                "on_reconfigure", "on_startup" -> out[key] = value
                else -> out[key] = merge(out[key], value)
            }
        }
        // panels.launcher: entries { "Lock" = "ensure_locked" } and keys { "C-n" = "next" } (copied: what was read may not be changeable).
        (out["panels"] as? Map<*, *>)?.let { panels -> (panels["launcher"] as? Map<*, *>)?.let { panels to it } }?.let { (panels, given) ->
            val launcher = LinkedHashMap<String, Any?>().also { l -> given.forEach { (k, v) -> l[k.toString()] = v } }
            out["panels"] = LinkedHashMap<String, Any?>().also { p -> panels.forEach { (k, v) -> p[k.toString()] = v }; p["launcher"] = launcher }
            launcher["entries"]?.let { launcher["entries"] = launcherEntries(it, caulis, name, problems) }
            (launcher["keys"] as? Map<*, *>)?.let { keys ->
                launcher["keys"] = keys.entries.mapNotNull { (combo, action) ->
                    val run = (action as? String)?.let(LauncherControl::byName)
                    if (run == null) problems += "$name: launcher.keys.$combo: next, previous, first, last, start, run_typed or close"
                    run?.let { combo.toString() to it }
                }.toMap()
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
            // The wheel over the bar: an action for each way ("move 0 -1", or a function of config.lua).
            (native["wheel"] as? Map<*, *>)?.let { ways ->
                native["wheel"] = ways.entries.associate { (way, action) ->
                    way.toString() to (if (action == null || action == false) null else actionOf(action, caulis, "$name: $bar.wheel.$way", problems))
                }
            }
            // What clicks and the wheel do on a module ("on": { "window": { "middle": "close_focused_view", "click": null } }).
            (native["on"] as? Map<*, *>)?.let { modules ->
                native["on"] = modules.entries.associate { (module, slots) ->
                    module.toString() to (slots as? Map<*, *>)?.entries?.associate { (slot, action) ->
                        slot.toString() to (if (action == null || action == false) null else actionOf(action, caulis, "$name: $bar.on.$module.$slot", problems))
                    }
                }
            }
            // Modules of one's own ("modules": { "vpn": { "command": "vpn-status", "every_seconds": 10, "on_click": "launch vpn-toggle" } }).
            (native["modules"] as? Map<*, *>)?.let { modules ->
                native["modules"] = modules.entries.associate { (module, spec) ->
                    module.toString() to (spec as? Map<*, *>)?.entries?.associate { (key, value) ->
                        val k = key.toString()
                        k to (if (k in MODULE_ACTIONS && value != null) actionOf(value, caulis, "$name: $bar.modules.$module.$k", problems) else value)
                    }
                }
            }
            val names = native["texts"] as? List<*> ?: continue
            native["texts"] = { names.map { n -> SYSTEM_TEXTS[n.toString()]?.invoke() ?: n.toString() } }
        }
        return out to problems
    }

    // gestures: the DSL's names (enabled, scale_px: gestures.c; dbus_enabled: gestures.dbus; bind:
    // gesture_bindings; on: gestures of your own) and newm's own, as they are.
    private fun gestures(g: Map<*, *>, out: MutableMap<String, Any?>, caulis: Caulis, name: String, problems: MutableList<String>) {
        val gestures = LinkedHashMap<String, Any?>()
        for ((k, v) in g) {
            when (val key = k.toString()) {
                "enabled", "scale_px" -> gestures["c"] = merge(gestures["c"], linkedMapOf<String, Any?>(key to v))
                "dbus_enabled" -> gestures["dbus"] = merge(gestures["dbus"], linkedMapOf<String, Any?>("enabled" to v))
                // bind = { swipe = { nil, "swipe-3" } } (a modifier or nil, then the gestures), or { modifier = "L", kinds = { "move-1", "swipe-2" } }.
                "bind" -> (v as? Map<*, *>)?.forEach { (what, b) ->
                    val binding = when (b) {
                        is List<*> -> b.map { it?.toString() }
                        is Map<*, *> -> listOf(b["modifier"]?.toString()) + ((b["kinds"] as? List<*>) ?: listOfNotNull(b["kind"])).map { it?.toString() }
                        else -> null.also { problems += "$name: gestures.bind.$what is a list: a modifier (or null) and the gestures" }
                    }
                    if (binding != null) out["gesture_bindings"] = merge(out["gesture_bindings"], linkedMapOf<String, Any?>(what.toString() to binding))
                } ?: problems.add("$name: gestures.bind is a table: swipe, swipe_to_zoom, move_resize, launcher")
                "on" -> gestures["on"] = (v as? List<*>)?.mapNotNull { item ->
                    val m = item as? Map<*, *> ?: return@mapNotNull null.also { problems += "$name: gestures.on is a list of tables { kind, direction, action }" }
                    val kind = m["kind"]?.toString() ?: return@mapNotNull null.also { problems += "$name: gestures.on: a kind (\"swipe-4\") is needed" }
                    val action = actionOf(m["action"], caulis, "$name: gestures.on.$kind", problems) ?: return@mapNotNull null
                    mapOf("kind" to kind, "direction" to m["direction"]?.toString(), "modifier" to m["modifier"]?.toString(),
                        "description" to (m["description"]?.toString() ?: ""), "action" to action)
                } ?: emptyList<Any?>().also { problems += "$name: gestures.on is a list of tables { kind, direction, action }" }
                else -> gestures[key] = merge(gestures[key], v)
            }
        }
        out["gestures"] = merge(out["gestures"], gestures)
    }

    // energy.after: { [300] = "ensure_locked" } or [ { seconds = 300, action = ... } ].
    private fun idleActions(value: Any?, caulis: Caulis, name: String, problems: MutableList<String>): List<Map<String, Any?>> {
        val pairs: List<Pair<Any?, Any?>> = when (value) {
            is Map<*, *> -> value.entries.map { (k, v) -> k to v }
            is List<*> -> value.mapNotNull { (it as? Map<*, *>)?.let { m -> m["seconds"] to m["action"] } }
            else -> emptyList<Pair<Any?, Any?>>().also { problems += "$name: energy.after is a table of seconds = action" }
        }
        return pairs.mapNotNull { (seconds, action) ->
            val n = (seconds as? Number)?.toInt() ?: seconds?.toString()?.toIntOrNull()
                ?: return@mapNotNull null.also { problems += "$name: energy.after: \"$seconds\" is not a number of seconds" }
            val run = actionOf(action, caulis, "$name: energy.after.$n", problems) ?: return@mapNotNull null
            mapOf("seconds" to n, "action" to run)
        }
    }

    // panels.launcher.entries: { Lock = "ensure_locked" } or [ { name, comment, action } ].
    private fun launcherEntries(value: Any?, caulis: Caulis, name: String, problems: MutableList<String>): List<Map<String, Any?>> {
        val items: List<Triple<String, String, Any?>> = when (value) {
            is Map<*, *> -> value.entries.map { (k, v) -> Triple(k.toString(), "", v) }
            is List<*> -> value.mapNotNull { (it as? Map<*, *>)?.let { m -> Triple(m["name"]?.toString() ?: return@mapNotNull null, m["comment"]?.toString() ?: "", m["action"]) } }
            else -> emptyList<Triple<String, String, Any?>>().also { problems += "$name: launcher.entries is a table of name = action" }
        }
        return items.mapNotNull { (entry, comment, action) ->
            val run = actionOf(action, caulis, "$name: launcher.entries.$entry", problems) ?: return@mapNotNull null
            mapOf("name" to entry, "comment" to comment, "action" to run)
        }
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
        "fit_focused_view", "toggle_key_help", "toggle_clipboard", "scratchpad", "side_by_side", "screenshot", "screenshot_screen", "screenshot_window",
        "restore_rest",
    )

    private fun parseAction(text: String, caulis: Caulis, where: String, problems: MutableList<String>): ((Layout) -> Unit)? {
        val trimmed = text.trim()
        val word = trimmed.substringBefore(' ')
        val rest = trimmed.substringAfter(' ', "").trim()
        val method = camelToSnake(word)
        // (The terminal asked for when the key is pressed: terminal = "..." of the configuration, then.)
        if (method == "terminal") return { _ -> caulis.launch(defaultTerminal()) }
        // One of newm's actions by name ("close-window", "go-to-window 3"; see KeyActions).
        KeyActions.byName(word)?.takeIf { method != "launch" && method !in layoutMethods }?.let { named ->
            val args = rest.split(Regex("\\s+")).filter { it.isNotEmpty() }
            return { layout -> named.run(layout, args) }
        }
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
    "island_color", "text_color", "accent_color", "left", "center", "right", "clock", "date", "icon_size", "show_percent", "show_passive", "title_width", "tray_hide", "menu_button", "quick_button", "wheel",
    "notifications_button", "layout_names", "outputs", "clock_hint", "hint_delay_ms", "modules", "on",
)

// A bar module's settings that are actions (on_scroll, a function of config.lua, gets the notches as it is).
private val MODULE_ACTIONS = setOf("on_click", "on_middle", "on_right", "on_scroll_up", "on_scroll_down")

// The settings of compositor { } in config.json / config.lua.
private val COMPOSITOR_KEYS = listOf("quit_while_locked", "lock_failure", "lock_show_millis", "stop_programs_on_quit", "process_start", "java_updated_notice", "config_compiler_jvm_options", "config_compiler_timeout_seconds")
