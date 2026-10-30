package newm

import dev.caulis.log.Logger

private val log = Logger("keys")

/** Modifier bits as pywm (and wlroots, and Caulis's KeyInput) number them. */
object Mod {
    const val SHIFT = 1
    const val CAPS = 2
    const val CTRL = 4
    const val ALT = 8
    const val MOD2 = 16
    const val MOD3 = 32
    const val LOGO = 64
    const val MOD5 = 128

    // Caps Lock and Num Lock (mod2) say nothing about a shortcut: left out when comparing.
    const val LOCKS = CAPS or MOD2

    /** The bits held in [modifiers] that were not in [before] (PyWMModifiers.pressed). */
    fun pressed(modifiers: Int, before: Int): Int = modifiers and before.inv()
}

/**
 * newm's key_processor.py: one key (or a modifier alone) or the change of the modifiers.
 * [keysyms] is the key for bindings (Latin on any layout: "d" for the key of "в"), [layoutKeysyms]
 * what it is in the layout in use ("Cyrillic_ve"), for bindings written on that one.
 */
class KeyEvent private constructor(
    val isMod: Boolean,
    val pressed: Boolean,
    val keysyms: String,
    val modifiers: Int,
    val lastModifiers: Int,
    val layoutKeysyms: String = keysyms,
) {
    /** The same key as [keysym]. */
    internal fun withKeysyms(keysym: String) = KeyEvent(isMod, pressed, keysym, modifiers, lastModifiers, layoutKeysyms)

    companion object {
        fun fromKey(pressed: Boolean, keysyms: String, modifiers: Int, layoutKeysyms: String = keysyms) =
            KeyEvent(false, pressed, keysyms, modifiers, 0, layoutKeysyms)
        fun fromMod(modifiers: Int, lastModifiers: Int) = KeyEvent(true, false, "", modifiers, lastModifiers)
    }
}

/** One press of a binding: "L-h" (Logo+h), "L-C-k", "L-H" (Logo+Shift+h), "L-" (Logo tapped alone). */
class KeyPress(keys: String) {
    var mod = 0
        private set
    var keysym: String
        private set
    private val lockSafe: Boolean
    private var readyToFire = false

    init {
        val parts = keys.split("-")
        for (k in parts.dropLast(1)) {
            when (k) {
                "S" -> mod = mod or Mod.SHIFT
                "L" -> mod = mod or Mod.LOGO
                "C" -> mod = mod or Mod.CTRL
                "A" -> mod = mod or Mod.ALT
                "1" -> mod = mod or Mod.ALT // (mod1)
                "2" -> mod = mod or Mod.MOD2
                "3" -> mod = mod or Mod.MOD3
            }
        }
        keysym = parts.last().trim()
        if (keysym.uppercase() == "SPC") keysym = "space"
        if (keysym.length == 1) {
            val k = keysym.lowercase()
            if (k != keysym) {
                mod = mod or Mod.SHIFT
                keysym = k
            }
        }
        lockSafe = keysym.startsWith("XF86")
    }

    /** 1: fires, 0: goes on (taken), -1: not this one. */
    fun process(event: KeyEvent, locked: Boolean): Int {
        if (locked && !lockSafe) return -1

        if (keysym == "") {
            if (event.isMod) {
                if (Mod.pressed(event.modifiers, event.lastModifiers) and Mod.LOCKS.inv() == mod) {
                    readyToFire = true
                    return 0
                }
                if (readyToFire && Mod.pressed(event.lastModifiers, event.modifiers) and Mod.LOCKS.inv() == mod) {
                    readyToFire = false
                    return 1
                }
            } else {
                readyToFire = false
            }
        } else {
            if (event.isMod) return 0
            if (event.pressed && event.keysyms == keysym && event.modifiers and Mod.LOCKS.inv() == mod) {
                readyToFire = true
                return 0
            }
            if (!event.pressed && event.keysyms == keysym && readyToFire) {
                readyToFire = false
                return 1
            }
        }
        return -1
    }

    /** True if [event] with the key [keysym] is this press: going down with its modifiers, or let go after it. */
    fun takes(event: KeyEvent, keysym: String): Boolean =
        this.keysym == keysym && if (event.pressed) event.modifiers and Mod.LOCKS.inv() == mod else readyToFire

    fun clear() {
        readyToFire = false
    }
}

/** A sequence of presses ("L-a L-b") and what it does. */
class KeyBinding(keys: String, private val action: () -> Unit) {
    private val presses = keys.trim().split(" ").filter { it.isNotEmpty() }.map(::KeyPress)
    private var at = 0

    fun process(event: KeyEvent, locked: Boolean): Int {
        when (presses[at].process(event, locked)) {
            1 -> {
                if (at == presses.size - 1) {
                    try {
                        action()
                    } catch (e: Exception) {
                        log.error("A key binding failed", e)
                    }
                    at = 0
                    return 1
                }
                at += 1
                return 0
            }
            0 -> return 0
            else -> at = 0
        }
        return -1
    }

    /** True if the press it waits for is [event] with the key [keysym]. */
    fun takes(event: KeyEvent, keysym: String): Boolean = presses[at].takes(event, keysym)

    fun clear() {
        at = 0
        presses.forEach(KeyPress::clear)
    }
}

/** newm's KeyProcessor: the configuration's key bindings, fed with keys and modifier changes. */
class KeyProcessor {
    private var bindings = ArrayList<KeyBinding>()

    fun clear() {
        bindings = ArrayList()
    }

    fun registerBindings(vararg bindings: Pair<String, () -> Unit>) {
        for ((keys, action) in bindings) this.bindings += KeyBinding(keys, action)
    }

    fun onEvent(given: KeyEvent, locked: Boolean): Boolean {
        val event = matching(given)
        var taken = false
        var triggered = false
        for (b in bindings) {
            if (triggered) {
                b.clear()
            } else {
                when (b.process(event, locked)) {
                    1 -> {
                        triggered = true
                        taken = true
                    }
                    0 -> taken = true
                }
            }
        }
        return taken
    }

    // A binding on the key as the layout in use has it ("L-Cyrillic_ve") wins over the Latin one
    // ("L-d"): the event with that key then; else as it is.
    private fun matching(event: KeyEvent): KeyEvent {
        val own = event.layoutKeysyms
        if (event.isMod || own == event.keysyms || bindings.none { it.takes(event, own) }) return event
        log.trace { "A binding on the layout's own symbol" }
        return event.withKeysyms(own)
    }

    fun onKey(pressed: Boolean, keysyms: String, modifiers: Int, locked: Boolean, layoutKeysyms: String = keysyms): Boolean =
        onEvent(KeyEvent.fromKey(pressed, keysyms, modifiers, layoutKeysyms), locked)

    fun onModifiers(modifiers: Int, lastModifiers: Int, locked: Boolean): Boolean =
        onEvent(KeyEvent.fromMod(modifiers, lastModifiers), locked)

    fun onOtherAction() = bindings.forEach(KeyBinding::clear)
}
