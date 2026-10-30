package newm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.caulis.compose.ComposeHandle
import dev.caulis.compose.compose
import dev.caulis.geometry.Rect
import dev.caulis.log.Logger
import dev.caulis.screenshot.ChooserKeys
import kotlinx.coroutines.launch
import newm.bar.BarColors
import newm.bar.softShadow
import newm.bar.tr

private val log = Logger("keys")

/**
 * The keys' help (Super+/): every key that does something now — the configuration's, newm's
 * defaults, Caulis's own (the laptop's keys, the text consoles, quitting), the mouse and the
 * touchpad, and the keys inside the launcher, the clipboard's history and the screenshot
 * chooser — in groups, with words in the user's language. Typing searches it; it scrolls.
 */
internal fun Layout.showKeyHelp(): ComposeHandle =
    caulis.compose(
        appearance = { _ -> keyHelpFade.appearance(rise = 10f, smallest = 0.95f) }, moving = { keyHelpFade.moving },
        // It takes the keyboard while open (the search); a press elsewhere closes it.
        takesKeyboard = true,
        onPressOutside = { _, _, _ ->
            if (keyHelpShown) keyHelpShown = false
            false
        },
        place = { m ->
            // On the monitor with the pointer.
            val d = caulis.desktop
            val target = d.monitors.firstOrNull { it.area.contains(Rect(d.pointerX.toInt(), d.pointerY.toInt(), 1, 1)) } ?: d.monitors.firstOrNull()
            // (Never over the lock screen.)
            if (!keyHelpFade.visible || m !== target || lockShown()) {
                null
            } else {
                // (As wide as before; at most this high: the rest scrolls.)
                val w = minOf(1060, m.area.width - 80)
                val h = minOf(820, m.area.height - 80)
                Rect(m.area.x + (m.area.width - w) / 2, m.area.y + (m.area.height - h) / 2, w, h)
            }
        },
    ) { _ -> if (keyHelpFade.visible || keyHelpShown) KeyHelpPanel(this) }

/** The groups of the keys' help, in their order (the titles in English: [tr] translates them). */
internal enum class KeyHelpGroup(val title: String) {
    PROGRAMS("Programs"),
    WINDOWS("Windows"),
    SCREENS("Screens & layouts"),
    MONITORS("Monitors"),
    SPECIAL("Special workspaces"),
    OVERVIEW("Overview & gestures"),
    MOUSE("Mouse"),
    SCREENSHOTS("Screenshots"),
    CLIPBOARD("Clipboard"),
    SYSTEM("System keys"),
    SESSION("Lock & session"),
    OWN("Your own keys"),
    LAUNCHER("In the launcher"),
    CLIPBOARD_HISTORY("In the clipboard's history"),
    CHOOSER("In the screenshot chooser"),
}

/** One line of the help: its [group], its [keys] (each a combination in words: "Super+Shift+H") and [what] it does, in English. */
internal data class KeyHelpEntry(val group: KeyHelpGroup, val keys: List<String>, val what: String)

/** What the help is made of (each read when it opens). */
internal class KeyHelpSources(
    /** The keys bound now, in newm's notation ("L-S-h"), with their words ("" when none were given). */
    val bindings: List<Pair<String, String>>,
    /** The named action of a key bound by its name ("close-window"), by its combination. */
    val actions: Map<String, String> = emptyMap(),
    /** Caulis's usual keys by name ([dev.caulis.wm.Keys.all]): those newm keeps on (the laptop's, quit, the consoles). */
    val caulisKeys: Map<String, String?> = emptyMap(),
    /** The screenshot chooser's keys (null: screenshots are off). */
    val chooser: ChooserKeys? = ChooserKeys(),
    /** The launcher's own keys (launcher { keys { } }) in newm's notation, with the action's name when it has one. */
    val launcherKeys: Map<String, String?> = emptyMap(),
    /** The launcher's view ("list", "grid", "categories"); null: newm's launcher is not in use. */
    val launcherView: String? = "list",
    /** The clipboard's history is on (its keys are told). */
    val clipboard: Boolean = true,
    /** Its key that pins (clipboard { pinKey }), and whether Enter pastes (clipboard { onChoose = "paste" }). */
    val clipboardPinKey: String = "C-p",
    val clipboardPastes: Boolean = false,
)

// Each named action's group.
private val ACTION_GROUPS: Map<String, KeyHelpGroup> = buildMap {
    fun group(g: KeyHelpGroup, vararg names: String) = names.forEach { put(it, g) }
    group(KeyHelpGroup.PROGRAMS, "terminal", "launch", "launcher")
    group(
        KeyHelpGroup.WINDOWS, "close-window", "focus-left", "focus-right", "focus-up", "focus-down", "next-window", "previous-window",
        "go-to-window", "next-in-stack", "move-window-left", "move-window-right", "move-window-up", "move-window-down", "window-narrower",
        "window-wider", "window-shorter", "window-taller", "fullscreen", "float", "swallow", "fit-window",
    )
    group(
        KeyHelpGroup.SCREENS, "side-by-side", "next-screen", "previous-screen", "window-to-next-screen", "window-to-previous-screen",
        "swap-on-screen", "promote-to-master", "next-layout",
    )
    group(
        KeyHelpGroup.MONITORS, "next-monitor", "previous-monitor", "window-to-next-monitor", "window-to-previous-monitor", "focus-monitor-left",
        "focus-monitor-right", "focus-monitor-up", "focus-monitor-down", "window-to-monitor-left", "window-to-monitor-right", "window-to-monitor-up",
        "window-to-monitor-down",
    )
    group(KeyHelpGroup.SPECIAL, "special-workspace", "to-special-workspace", "from-special-workspace")
    group(KeyHelpGroup.OVERVIEW, "overview", "zoom-out", "zoom-in")
    group(KeyHelpGroup.SCREENSHOTS, "screenshot", "screenshot-screen", "screenshot-window")
    group(KeyHelpGroup.CLIPBOARD, "clipboard")
    group(KeyHelpGroup.SESSION, "key-help", "lock", "reload", "logout")
    group(KeyHelpGroup.SYSTEM, "caulis")
}

/** The group of [action] (a [KeyActions] name), or null for a name not known. */
internal fun keyHelpGroupOf(action: String): KeyHelpGroup? = ACTION_GROUPS[action]

// Caulis's usual keys that newm keeps: their words.
private val CAULIS_WORDS = linkedMapOf(
    "volumeUp" to "Louder", "volumeDown" to "Quieter", "volumeMute" to "Sound off, and on", "micMute" to "Microphone off, and on",
    "brightnessUp" to "Brighter", "brightnessDown" to "Darker", "touchpad" to "Touchpad off, and on",
    "displays" to "Next display mode (laptop screen, mirror, extend)", "mediaPlay" to "Play or pause the music", "mediaNext" to "Next song",
    "mediaPrevious" to "Previous song", "restoreShortcuts" to "Take the keys back from a window that keeps them",
    "quit" to "Quit newm (every program closes)",
)

// A laptop's keys and others by their X11 names, as the help writes them.
private val SPECIAL_KEY_NAMES = mapOf(
    "XF86AudioRaiseVolume" to "Volume Up", "XF86AudioLowerVolume" to "Volume Down", "XF86AudioMute" to "Mute", "XF86AudioMicMute" to "Mic Mute",
    "XF86MonBrightnessUp" to "Brightness Up", "XF86MonBrightnessDown" to "Brightness Down", "XF86AudioPlay" to "Play", "XF86AudioPause" to "Pause",
    "XF86AudioNext" to "Next Track", "XF86AudioPrev" to "Previous Track", "XF86AudioStop" to "Stop", "XF86Display" to "Display",
    "XF86TouchpadToggle" to "Touchpad", "XF86Calculator" to "Calculator", "XF86PowerOff" to "Power", "XF86Search" to "Search",
    "KP_Enter" to "Keypad Enter",
)

/**
 * A combination in newm's notation ("L-S-h", "L-BackSpace", "XF86AudioMute", "L-") as the help
 * writes it: KeyNotation's words ("Super+Shift+H", "Super+Backspace"), a laptop's keys by name
 * ("Mute"), a modifier tapped alone so ("Super alone").
 */
internal fun keyHelpWords(combo: String): String {
    val readable = runCatching { KeyNotation.readable(combo) }.getOrDefault(combo)
    val words = readable.split(' ').joinToString(" ") { step -> step.split('+').joinToString("+") { SPECIAL_KEY_NAMES[it] ?: it } }
    // (A modifier alone: "L-" is Super tapped on its own.)
    return if (combo.trim().endsWith("-") && !combo.trim().endsWith("--")) tr("%s alone").replace("%s", words) else words
}

// A key of Caulis's notation ("Ctrl+Alt+BackSpace", "XF86AudioMute") in newm's, or null.
private fun caulisToNewm(combo: String): String? = runCatching { KeyNotation.toNewm(combo) }.getOrNull()

/** Every line of the help from [sources], grouped in [KeyHelpGroup]'s order; keys that do the same in one line. */
internal fun keyHelpEntries(sources: KeyHelpSources): List<KeyHelpEntry> {
    val out = ArrayList<KeyHelpEntry>()
    fun add(group: KeyHelpGroup, what: String, vararg keys: String) {
        if (keys.isNotEmpty()) out += KeyHelpEntry(group, keys.toList(), what)
    }
    // (Keys in newm's notation, "C-n", in the help's words.)
    fun keys(vararg combos: String) = combos.map(::keyHelpWords).toTypedArray()
    val descriptions = KeyActions.ALL.associateBy { it.description }

    // The configuration's keys (newm's defaults when there is none): by their action's group.
    val bound = HashSet<String>()
    for ((combo, words) in sources.bindings) {
        bound += combo
        val action = sources.actions[combo]
        val group = action?.let(::keyHelpGroupOf)
            ?: descriptions[words]?.name?.let(::keyHelpGroupOf)
            ?: when {
                combo.startsWith("XF86") -> KeyHelpGroup.SYSTEM
                combo == "Print" || words.startsWith("Screenshot") -> KeyHelpGroup.SCREENSHOTS
                words == "These keys" || words == "Lock the screen" || words == "Log out" -> KeyHelpGroup.SESSION
                else -> KeyHelpGroup.OWN
            }
        add(group, words.ifBlank { NO_WORDS }, keyHelpWords(combo))
    }

    // Caulis's keys newm keeps (those newm binds itself win).
    for ((name, combo) in sources.caulisKeys) {
        if (combo == null) continue
        if (name == "consoles") {
            add(KeyHelpGroup.SYSTEM, "Switch to a text console (and back to newm)", "${keyHelpWords(caulisToNewm("$combo+F1") ?: "$combo+F1")} … F12")
            continue
        }
        val words = CAULIS_WORDS[name] ?: continue
        val newm = caulisToNewm(combo) ?: continue
        if (newm in bound) continue
        add(KeyHelpGroup.SYSTEM, words, keyHelpWords(newm))
    }

    // The mouse (mouse { superMove, moveButton, resizeButton }, resize { onBorder, quarters }).
    val moveResize = confMoveResize()
    val mod = modifierWord(moveResize.getOrNull(0))
    if (mod != null && moveResize.getOrNull(1) != null) {
        if (confSuperMove() == "motion") {
            add(KeyHelpGroup.MOUSE, "Move the window", "$mod + mouse")
        } else {
            mouseButtonCode(confMoveButton())?.let { add(KeyHelpGroup.MOUSE, "Move the window", "$mod + ${mouseButtonName(it)} drag") }
            mouseButtonCode(ScreenResize.mouseButton() ?: confResizeButton())?.let {
                add(KeyHelpGroup.MOUSE, if (ScreenResize.quarters()) "Resize the window (at the corner nearest the pointer)" else "Resize the window", "$mod + ${mouseButtonName(it)} drag")
            }
        }
    }
    if (ScreenResize.onBorder()) ScreenResize.borderButton()?.let { add(KeyHelpGroup.MOUSE, "Resize the windows on both sides", "${mouseButtonName(it)} drag between windows") }
    if (confOverviewChooseHelp()) add(KeyHelpGroup.MOUSE, "In the overview: go to that window", "Click a window")
    if (confOverviewDragHelp()) add(KeyHelpGroup.MOUSE, "In the overview: move it, onto another monitor too", "Drag a window")

    // The touchpad's gestures.
    for ((how, what) in gestureHelp()) {
        if (how.contains("drag") || how.endsWith("mouse")) continue // (the mouse: above)
        add(KeyHelpGroup.OVERVIEW, what, how)
    }

    // In the launcher (newm's own): its keys of the configuration first, then its usual ones.
    sources.launcherView?.let { view ->
        for ((combo, action) in sources.launcherKeys) add(KeyHelpGroup.LAUNCHER, launcherActionWords(action), keyHelpWords(combo))
        if (view == "grid") {
            add(KeyHelpGroup.LAUNCHER, "Move in the grid", *keys("Left", "Right", "Up", "Down"))
            add(KeyHelpGroup.LAUNCHER, "A page back, a page on", *keys("Prior", "Next"))
        } else {
            add(KeyHelpGroup.LAUNCHER, "Next program", *keys("Down", "Tab", "C-n", "C-j"))
            add(KeyHelpGroup.LAUNCHER, "Previous program", *keys("Up", "S-Tab", "C-p", "C-k"))
        }
        if (view == "categories") add(KeyHelpGroup.LAUNCHER, "Category before, next (nothing typed)", *keys("Left", "Right"))
        add(KeyHelpGroup.LAUNCHER, "Start the chosen one", *keys("Return"))
        add(KeyHelpGroup.LAUNCHER, "Close", *keys("Escape"))
    }

    // In the clipboard's history.
    if (sources.clipboard) {
        add(KeyHelpGroup.CLIPBOARD_HISTORY, "Choose", *keys("Up", "Down", "j", "k"))
        add(KeyHelpGroup.CLIPBOARD_HISTORY, if (sources.clipboardPastes) "Paste the chosen one" else "Copy the chosen one again", *keys("Return"))
        add(KeyHelpGroup.CLIPBOARD_HISTORY, "Pin, so it stays", *keys(sources.clipboardPinKey))
        add(KeyHelpGroup.CLIPBOARD_HISTORY, "Remove it", *keys("Delete"))
        add(KeyHelpGroup.CLIPBOARD_HISTORY, "Go to the next part (kinds, Clear, the list, its buttons)", *keys("Tab"))
        add(KeyHelpGroup.CLIPBOARD_HISTORY, "Close", *keys("Escape"))
    }

    // In the screenshot chooser (Caulis's: its keys as newm sets them).
    sources.chooser?.let { k ->
        fun keys(names: List<String>, with: String = "") = keys(*names.map { with + it }.toTypedArray())
        add(KeyHelpGroup.CHOOSER, "A part of the screen", *keys(k.area))
        add(KeyHelpGroup.CHOOSER, "A window", *keys(k.window))
        add(KeyHelpGroup.CHOOSER, "The whole screen", *keys(k.screen))
        add(KeyHelpGroup.CHOOSER, "Several windows, whole", *keys(k.windows))
        add(KeyHelpGroup.CHOOSER, "Move and resize the part, or a new one each time", *keys(k.selection))
        add(KeyHelpGroup.CHOOSER, "The pointer in the picture, or not", *keys(k.pointer))
        add(KeyHelpGroup.CHOOSER, "Timer", *keys(k.timer))
        add(KeyHelpGroup.CHOOSER, "Take it", *keys(k.capture))
        add(KeyHelpGroup.CHOOSER, "Next kind (with Shift: the one before)", *keys(k.nextKind))
        add(KeyHelpGroup.CHOOSER, "Only copy it (not saved)", *keys(k.copy, "C-"))
        add(KeyHelpGroup.CHOOSER, "All the windows (over the overview)", *keys(k.all, "C-"))
        add(KeyHelpGroup.CHOOSER, "Move the part (Shift: by ten; Ctrl: resize it)", *keys("Left", "Right", "Up", "Down"))
        add(KeyHelpGroup.CHOOSER, "Close", *keys(k.close))
    }

    // Keys that do the same in a group: one line (keys without words each a line of their own).
    val merged = LinkedHashMap<Any, KeyHelpEntry>()
    for (e in out) {
        val id: Any = if (e.what == NO_WORDS) e else e.group to e.what
        merged[id] = merged[id]?.let { it.copy(keys = (it.keys + e.keys).distinct()) } ?: e
    }
    return merged.values.sortedBy { it.group.ordinal }
}

private const val NO_WORDS = "Your own (no words given)"

// The words of a launcher key's action ("next", "run_typed"; null: a function of the configuration's).
private fun launcherActionWords(action: String?): String = when (action?.trim()?.replace('-', '_')?.lowercase()) {
    "next", "down" -> "Next program"
    "previous", "prev", "up" -> "Previous program"
    "first" -> "First program"
    "last" -> "Last program"
    "start", "choose" -> "Start the chosen one"
    "run_typed", "runtyped", "run" -> "Run what was typed as a command"
    "close", "exit" -> "Close"
    else -> NO_WORDS
}

/** True when [entry] has [query] in its keys, its words (English or the user's) or its group's title. */
internal fun keyHelpMatches(entry: KeyHelpEntry, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return q.split(Regex("\\s+")).all { word ->
        entry.keys.any { it.contains(word, ignoreCase = true) || keyHelpKeyText(it).contains(word, ignoreCase = true) } || entry.what.contains(word, ignoreCase = true) ||
            tr(entry.what).contains(word, ignoreCase = true) || tr(entry.group.title).contains(word, ignoreCase = true) ||
            entry.group.title.contains(word, ignoreCase = true)
    }
}

/** Keys that do the same (the same words) in one row: their combinations, and the words. */
internal fun helpRows(keys: List<Pair<String, String>>): List<Pair<List<String>, String>> {
    val rows = LinkedHashMap<String, MutableList<String>>()
    var unnamed = 0
    for ((combo, what) in keys) rows.getOrPut(what.ifEmpty { "\u0000${unnamed++}" }) { ArrayList() } += combo
    return rows.map { (what, combos) -> combos to (if (what.startsWith("\u0000")) "" else what) }
}

// The gestures as the configuration binds them (gesture_bindings, mouse { }, gestures { on() }).
private val confSwipe = configuredValue<List<String?>>("gesture_bindings.swipe", listOf(null, "swipe-3"))
private val confZoom = configuredValue<List<String?>>("gesture_bindings.swipe_to_zoom", listOf(null, "swipe-4"))
private val confMoveResize = configuredValue<List<String?>>("gesture_bindings.move_resize", listOf("L", "move-1", "swipe-2"))
private val confLauncher = configuredValue<List<String?>>("gesture_bindings.launcher", listOf(null, "swipe-5"))
private val confOwnGestures = configuredValue("gestures.on", emptyList<Map<String, Any?>>())
private val confCGestures = configuredValue("gestures.c.enabled", true)
private val confOverviewChooseHelp = configuredValue("overview.choose", true)
private val confOverviewDragHelp = configuredValue("overview.drag", true)

/** A gesture in words: "3 fingers", "Super + 2 fingers" ([modifier]: newm's letter, "L"; [kind]: "swipe-3", "move-1"). */
internal fun gestureWords(modifier: String?, kind: String?): String {
    val fingers = kind?.substringAfter('-', "")?.toIntOrNull() ?: 0
    val what = when {
        kind == null -> ""
        fingers == 1 -> "1 finger"
        fingers > 1 -> "$fingers fingers"
        else -> kind
    }
    return listOfNotNull(modifierWord(modifier), what.ifEmpty { null }).joinToString(" + ")
}

private fun modifierWord(modifier: String?): String? = when (modifier) {
    null, "" -> null
    "L" -> "Super"
    "A" -> "Alt"
    "C" -> "Ctrl"
    "S" -> "Shift"
    else -> modifier
}

/** The touchpad's and the mouse's gestures and what they do (in English: the panel translates them). */
internal fun gestureHelp(): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    val touchpad = confCGestures()
    val moveResize = confMoveResize()
    if (touchpad) {
        confSwipe().let { b -> b.getOrNull(1)?.let { out += gestureWords(b[0], it) to "Move over the wall" } }
        confZoom().let { b -> b.getOrNull(1)?.let { out += "${gestureWords(b[0], it)} up / down" to "Zoom out / in" } }
        confLauncher().let { b -> b.getOrNull(1)?.let { out += gestureWords(b[0], it) to "Launcher" } }
    }
    // The mouse with the modifier held (mouse { superMove, moveButton, resizeButton }).
    val mod = modifierWord(moveResize.getOrNull(0))
    if (mod != null && moveResize.getOrNull(1) != null) {
        if (confSuperMove() == "motion") out += "$mod + mouse" to "Move the window"
        else {
            mouseButtonCode(confMoveButton())?.let { out += "$mod + ${mouseButtonName(it)} drag" to "Move the window" }
            mouseButtonCode(confResizeButton())?.let { out += "$mod + ${mouseButtonName(it)} drag" to "Resize the window" }
        }
    }
    if (touchpad) moveResize.getOrNull(2)?.let { out += gestureWords(moveResize[0], it) to "Resize the window" }
    for (g in confOwnGestures()) {
        val kind = g["kind"] as? String ?: continue
        val way = g["direction"] as? String
        val words = gestureWords(g["modifier"] as? String, kind) + (way?.let { " $it" } ?: "")
        out += words to ((g["description"] as? String)?.ifBlank { null } ?: "Your own")
    }
    return out
}

// A gesture's or the mouse's words in the user's language: "3 fingers" → "3 пальца", "Super + left drag" →
// "Super + тянуть ЛКМ" (a key's name stays as it is: "Delete" is the key's).
internal fun keyHelpKeyText(key: String): String {
    if (!POINTER_WORDS.containsMatchIn(key)) return key
    val direct = tr(key)
    if (direct != key) return direct
    // "N fingers" anywhere in it (Russian has three forms).
    return Regex("(\\d+) fingers?").replace(key) { m ->
        val n = m.groupValues[1].toInt()
        if (newm.bar.language() != "ru") m.value else "$n " + when {
            n % 10 == 1 && n % 100 != 11 -> "палец"
            n % 10 in 2..4 && n % 100 !in 12..14 -> "пальца"
            else -> "пальцев"
        }
    }.let { text -> MOUSE_WORDS.fold(text) { t, en -> if (en in t) t.replace(en, tr(en)) else t } }
}

private val POINTER_WORDS = Regex("finger|drag|mouse|Click|Drag")

// The mouse's words inside a key's text, translated each (the longer first).
private val MOUSE_WORDS = listOf(
    "left drag between windows", "right drag between windows", "middle drag between windows",
    "left drag", "right drag", "middle drag", "side drag", "extra drag", "mouse", "Click a window", "Drag a window", "up / down",
)

/** The keys bound now and their words, the configuration's or newm's defaults ("" when it gave none). */
internal fun Layout.keyHelpSources(): KeyHelpSources {
    val words = confKeyHelpWords().toMap()
    val combos = LinkedHashSet<String>()
    confKeyHelpWords().forEach { combos += it.first }
    // (Every key that is bound: also those of a configuration that gave no words, config.py's.)
    runCatching { confKeyBindingsHelp()(this).forEach { combos += it.first } }.onFailure { log.warn("The keys' help could not list the bindings: $it") }
    val launcherKeys = confLauncherKeysHelp().mapValues { (_, v) -> v as? String }
    return KeyHelpSources(
        bindings = combos.map { it to words[it].orEmpty() },
        actions = confKeyActions(),
        caulisKeys = caulis.keys.all().filterKeys { it in CAULIS_WORDS || it == "consoles" },
        chooser = if (confScreenshotsHelp()) screenshotChooserKeys() else null,
        launcherKeys = launcherKeys,
        launcherView = if (panelsUi.ownLauncher) launcherView() else null,
        clipboard = confClipboardHelp(),
        clipboardPinKey = confClipboardPinHelp(),
        clipboardPastes = confClipboardChooseHelp().trim().lowercase() == "paste",
    )
}

private val confKeyHelpWords = configuredValue<List<Pair<String, String>>>("key_help", emptyList())
private val confKeyActions = configuredValue<Map<String, String>>("key_actions", emptyMap())
private val confKeyBindingsHelp = configuredValue<(Layout) -> List<Pair<String, () -> Unit>>>("key_bindings") { emptyList() }
private val confLauncherKeysHelp = configuredValue("panels.launcher.keys", emptyMap<String, Any?>())
private val confScreenshotsHelp = configuredValue("screenshots.enabled", true)
private val confClipboardHelp = configuredValue("clipboard.enabled", true)
private val confClipboardPinHelp = configuredValue("clipboard.pin_key", "C-p")
private val confClipboardChooseHelp = configuredValue("clipboard.on_choose", "copy")

/** The help's lines now (read when it opens: the configuration may have changed). */
internal fun Layout.keyHelpEntries(): List<KeyHelpEntry> = keyHelpEntries(keyHelpSources()).also { log.info { "The keys' help: ${it.size} lines" } }

@Composable
internal fun KeyHelpPanel(wm: Layout) {
    val colors = wm.barModel.colorsNow()
    val surface = colors.island.copy(alpha = 0.97f)
    val text = TextStyle(color = colors.text, fontSize = 13.sp)
    val shape = RoundedCornerShape(18.dp)
    val opened = wm.keyHelpOpened
    // Read once each time it opens; only the rows in sight are drawn (a lazy list).
    val entries = remember(opened) { wm.keyHelpEntries() }
    var query by remember(opened) { mutableStateOf("") }
    val shown = remember(entries, query) { entries.filter { keyHelpMatches(it, query) } }
    // Its rows: a group's title, then its lines two by two.
    val rows = remember(shown) {
        buildList<Any> {
            for ((group, lines) in shown.groupBy { it.group }) {
                add(group)
                addAll(lines.chunked(2))
            }
        }
    }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(opened) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) { list.scrollToItem(0) }
    Box(
        Modifier.fillMaxSize().padding(14.dp)
            .softShadow(18.dp, 14.dp)
            .background(surface, shape)
            .border(1.dp, colors.edge, shape)
            .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 10.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                newm.bar.IconButton(newm.bar.Glyph.CLOSE, colors, 28.dp) { wm.keyHelpShown = false }
                Spacer(Modifier.width(10.dp))
                BasicText(tr("Keys"), style = text.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.width(18.dp))
                // The search: typing filters the lines (keys, words, groups).
                Box(
                    Modifier.weight(1f).height(32.dp).background(colors.text.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                        .border(1.dp, colors.text.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) BasicText(tr("Type to search keys"), style = text.copy(color = colors.dim))
                    BasicTextField(
                        query, { query = it },
                        Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val by = when (e.key) {
                                Key.Escape -> {
                                    if (query.isNotEmpty()) query = "" else wm.keyHelpShown = false
                                    return@onPreviewKeyEvent true
                                }
                                Key.DirectionDown -> 1
                                Key.DirectionUp -> -1
                                Key.PageDown -> 8
                                Key.PageUp -> -8
                                else -> return@onPreviewKeyEvent false
                            }
                            scope.launch { list.scrollToItem((list.firstVisibleItemIndex + by).coerceIn(0, (rows.size - 1).coerceAtLeast(0))) }
                            true
                        },
                        singleLine = true,
                        textStyle = text,
                        cursorBrush = SolidColor(colors.accent),
                    )
                }
                Spacer(Modifier.width(14.dp))
                BasicText(tr("Esc closes"), style = text.copy(color = colors.dim, fontSize = 12.sp))
            }
            Spacer(Modifier.height(8.dp))
            if (rows.isEmpty()) BasicText(tr("No key does “%s”").replace("%s", query), style = text.copy(color = colors.dim), modifier = Modifier.padding(12.dp))
            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(rows.size) { i ->
                    when (val row = rows[i]) {
                        is KeyHelpGroup -> BasicText(
                            tr(row.title), style = text.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.accent),
                            modifier = Modifier.padding(top = if (i == 0) 2.dp else 12.dp, bottom = 4.dp),
                        )
                        is List<*> -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            for (line in row) KeyRow(line as KeyHelpEntry, colors, text, Modifier.weight(1f))
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyRow(entry: KeyHelpEntry, colors: BarColors, text: TextStyle, modifier: Modifier) {
    Row(modifier.heightIn(min = 30.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        val faint = text.copy(color = colors.dim, fontSize = 11.sp)
        // The keys: each combination a keycap; long ones one under the other.
        val stacked = entry.keys.size > 1 && entry.keys.sumOf { it.length } > 24
        Box(Modifier.width(250.dp)) {
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { for (k in entry.keys) Cap(keyHelpKeyText(k), colors) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    entry.keys.forEachIndexed { n, k ->
                        if (n > 0) BasicText(" / ", style = faint)
                        Cap(keyHelpKeyText(k), colors)
                    }
                }
            }
        }
        BasicText(tr(entry.what), style = text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// A key as a little keycap.
@Composable
private fun Cap(name: String, colors: BarColors) {
    Box(
        Modifier.background(colors.text.copy(alpha = 0.08f), RoundedCornerShape(6.dp)).border(1.dp, colors.text.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) { BasicText(name, style = TextStyle(color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1) }
}

/**
 * A combination as newm writes it ("L-S-h", "L-", "L-a L-b") in words: a list of steps, each
 * a list of keys (["Super", "Shift", "H"]).
 */
internal fun keyNames(combo: String): List<List<String>> = combo.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { step ->
    val parts = step.split('-')
    val mods = parts.dropLast(1).mapNotNull {
        when (it) {
            "L" -> "Super"
            "S" -> "Shift"
            "C" -> "Ctrl"
            "A", "1" -> "Alt"
            else -> null
        }
    }.toMutableList()
    var key = parts.last()
    if (key.length == 1 && key != key.lowercase()) {
        if ("Shift" !in mods) mods += "Shift"
        key = key.lowercase()
    }
    val name = when {
        key.isEmpty() -> null // "L-": the modifier alone
        key.length == 1 -> key.uppercase()
        else -> KEY_NAMES[key] ?: key
    }
    if (name == null) listOf(tr("%s alone").replace("%s", mods.joinToString("+"))) else mods + name
}

private val KEY_NAMES = mapOf(
    "Return" to "Enter", "space" to "Space", "Left" to "←", "Right" to "→", "Up" to "↑", "Down" to "↓", "Escape" to "Esc",
    "slash" to "/", "minus" to "−", "equal" to "=", "plus" to "+", "comma" to ",", "period" to ".", "Print" to "PrtSc",
    "BackSpace" to "Backspace", "XF86MonBrightnessUp" to "Brightness +", "XF86MonBrightnessDown" to "Brightness −",
    "XF86AudioRaiseVolume" to "Volume +", "XF86AudioLowerVolume" to "Volume −", "XF86AudioMute" to "Mute", "XF86AudioMicMute" to "Mic mute",
    "XF86AudioPlay" to "Play", "XF86AudioNext" to "Next", "XF86AudioPrev" to "Previous",
)
