package newm

import dev.caulis.log.Logger

private val log = Logger("keys")

/**
 * Keys as people write them: "Super+Backspace", "Super+Shift+Q", "Ctrl+Alt+T" (case does not
 * matter; Super is also Logo, Win, Mod, Meta; Ctrl also Control) — and newm's own notation as
 * before ("L-q", "L-S-h", "L-" for Logo tapped alone, "L-a L-b" a sequence). Both become
 * newm's notation, which the key processor reads; [readable] turns it back into words.
 */
object KeyNotation {

    /** A combination that cannot be read: what is wrong, said for the configuration's card. */
    class BadKey(message: String) : IllegalArgumentException(message)

    // The modifiers' names (without case) → newm's letters.
    private val MODIFIERS = mapOf(
        "super" to "L", "logo" to "L", "win" to "L", "windows" to "L", "mod" to "L", "mod4" to "L", "meta" to "L", "cmd" to "L",
        "ctrl" to "C", "control" to "C", "ctl" to "C",
        "alt" to "A", "mod1" to "A", "option" to "A",
        "shift" to "S",
    )

    // Key names (compared without case, "_" and "-") → the xkb keysym newm compares with.
    private val KEYS: Map<String, String> = buildMap {
        fun names(keysym: String, vararg aliases: String) {
            put(norm(keysym), keysym)
            aliases.forEach { put(norm(it), keysym) }
        }
        names("BackSpace", "Backspace", "Bksp")
        names("Return", "Enter", "Ret")
        names("space", "Space", "Spc")
        names("Tab")
        names("Escape", "Esc")
        names("Delete", "Del")
        names("Insert", "Ins")
        names("Home"); names("End")
        names("Prior", "PageUp", "PgUp")
        names("Next", "PageDown", "PgDn", "PgDown")
        names("Left", "ArrowLeft"); names("Right", "ArrowRight"); names("Up", "ArrowUp"); names("Down", "ArrowDown")
        names("Print", "PrintScreen", "PrtSc", "PrtScr", "SysRq")
        names("Pause", "Break")
        names("Menu", "Compose")
        names("Caps_Lock", "CapsLock", "Caps")
        names("Scroll_Lock", "ScrollLock")
        names("Num_Lock", "NumLock")
        names("minus", "-"); names("equal", "=", "Equals"); names("plus", "+")
        names("comma", ","); names("period", ".", "Dot"); names("slash", "/"); names("backslash", "\\")
        names("semicolon", ";"); names("apostrophe", "'", "Quote"); names("grave", "`", "Backtick")
        names("bracketleft", "[", "LeftBracket"); names("bracketright", "]", "RightBracket")
        for (n in 1..35) names("F$n")
        for (d in 0..9) names("KP_$d", "KP$d", "Num$d", "Numpad$d")
        names("KP_Enter", "KPEnter", "NumpadEnter"); names("KP_Add", "KPAdd", "KPPlus", "NumpadPlus")
        names("KP_Subtract", "KPSubtract", "KPMinus", "NumpadMinus"); names("KP_Multiply", "KPMultiply", "NumpadMultiply")
        names("KP_Divide", "KPDivide", "NumpadDivide"); names("KP_Decimal", "KPDecimal", "NumpadDot")
        names("XF86AudioMute", "Mute"); names("XF86AudioLowerVolume", "VolumeDown"); names("XF86AudioRaiseVolume", "VolumeUp")
        names("XF86AudioMicMute", "MicMute"); names("XF86AudioPlay", "Play", "PlayPause"); names("XF86AudioPause")
        names("XF86AudioNext", "NextSong"); names("XF86AudioPrev", "Prev", "PreviousSong"); names("XF86AudioStop", "Stop")
        names("XF86MonBrightnessUp", "BrightnessUp"); names("XF86MonBrightnessDown", "BrightnessDown")
        names("XF86Display", "Display"); names("XF86TouchpadToggle", "TouchpadToggle")
        names("XF86Calculator", "Calculator"); names("XF86Explorer"); names("XF86Search"); names("XF86PowerOff", "Power")
        for (c in 'a'..'z') names(c.toString())
        for (c in '0'..'9') names(c.toString())
    }

    private fun norm(name: String) = if (name.length == 1) name.lowercase() else name.lowercase().replace("_", "").replace("-", "")

    // The keysym of a key's name; an X11 keysym name of another key (XF86..., dead_..., Cyrillic_...) as it is.
    private fun keysym(name: String, whole: String): String {
        KEYS[norm(name)]?.let { return it }
        if (Regex("^(XF86|dead_|Cyrillic_|KP_|ISO_)[A-Za-z0-9_]+$").matches(name)) return name
        val near = KEYS.keys.filter { it.length > 1 }.minByOrNull { distance(it, norm(name)) }?.let { KEYS[it] }
        throw BadKey("\"$whole\": no key is called \"$name\"" + (near?.let { " (did you mean $it?)" } ?: "") +
            " — write keys as \"Super+Backspace\", \"Ctrl+Alt+T\", \"Super+Shift+Q\"")
    }

    /**
     * [combination] in newm's notation ("L-S-q"); readable names and newm's own both taken.
     * A sequence: steps apart with spaces ("Super+A Super+B").
     */
    fun toNewm(combination: String): String {
        val steps = combination.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (steps.isEmpty()) throw BadKey("an empty key: write it as \"Super+Backspace\"")
        return steps.joinToString(" ") { step(it, combination) }
    }

    // newm's notation: modifiers' letters with "-" ("L-S-h", "L-", "Print", "XF86AudioMute").
    private val OLD = Regex("^(?:[LSCA123]-)+.*$")

    private fun step(text: String, whole: String): String {
        if (OLD.matches(text)) return text
        // Readable: modifiers and a key with "+" ("Super++" is Super and +).
        val parts = if (text.endsWith("++")) text.dropLast(2).split('+') + "+" else text.split('+')
        if (parts.any { it.isEmpty() }) throw BadKey("\"$whole\": a \"+\" with nothing beside it")
        val mods = LinkedHashSet<String>()
        var key: String? = null
        for ((n, part) in parts.withIndex()) {
            val mod = MODIFIERS[part.lowercase()]
            val last = n == parts.size - 1
            when {
                // ("Super" alone, or "Super+Shift": the modifiers tapped.)
                mod != null -> mods += mod
                !last -> throw BadKey("\"$whole\": \"$part\" is no modifier (Super, Ctrl, Alt, Shift) — the key comes last")
                else -> key = part
            }
        }
        val sym = key?.let { keysym(it, whole) } ?: ""
        // (newm's order: Logo, Shift, Ctrl, Alt; the key last — "" for the modifiers alone.)
        val order = listOf("L", "S", "C", "A").filter { it in mods }
        if (order.isEmpty()) return sym
        return order.joinToString("-") + "-" + sym
    }

    /** newm's notation in words: "L-S-h" → "Super+Shift+H", "L-" → "Super", "Print" → "Print". */
    fun readable(combination: String): String = combination.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ") { step ->
        val parts = step.split('-')
        val mods = if (parts.size > 1) parts.dropLast(1).mapNotNull {
            when (it) {
                "L" -> "Super"; "S" -> "Shift"; "C" -> "Ctrl"; "A", "1" -> "Alt"; else -> null
            }
        }.toMutableList() else mutableListOf()
        var key = if (parts.size > 1 && step.endsWith("--")) "-" else parts.last()
        if (key.length == 1 && key != key.lowercase()) {
            if ("Shift" !in mods) mods += "Shift"
            key = key.lowercase()
        }
        val name = when {
            key.isEmpty() -> null
            key.length == 1 -> key.uppercase()
            else -> READABLE[key] ?: key
        }
        (listOf("Super", "Shift", "Ctrl", "Alt").filter { it in mods } + listOfNotNull(name)).joinToString("+")
    }

    private val READABLE = mapOf(
        "BackSpace" to "Backspace", "Return" to "Return", "space" to "Space", "Prior" to "PageUp", "Next" to "PageDown",
        "minus" to "Minus", "equal" to "Equal", "plus" to "Plus", "comma" to "Comma", "period" to "Period", "slash" to "Slash",
        "backslash" to "Backslash", "semicolon" to "Semicolon", "apostrophe" to "Apostrophe", "grave" to "Grave",
        "bracketleft" to "BracketLeft", "bracketright" to "BracketRight",
    )

    // How many letters apart two names are (for "did you mean").
    private fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        return d[a.length][b.length]
    }

    /** [combination] in newm's notation, or null and [problems] told why (config.json and config.lua). */
    fun toNewmOrNull(combination: String, where: String, problems: MutableList<String>): String? = try {
        toNewm(combination)
    } catch (e: BadKey) {
        problems += "$where: ${e.message}"
        log.warn("$where: ${e.message}")
        null
    }
}
