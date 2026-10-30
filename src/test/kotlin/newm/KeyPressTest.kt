package newm

import kotlin.test.Test
import kotlin.test.assertEquals

/** Key combinations as newm writes them fire on their keys (F-keys too: newm issue 175). */
class KeyPressTest {

    private fun fires(combo: String, keysym: String, modifiers: Int): Boolean {
        var fired = false
        val binding = KeyBinding(combo) { fired = true }
        binding.process(KeyEvent.fromKey(true, keysym, modifiers), locked = false)
        binding.process(KeyEvent.fromKey(false, keysym, modifiers), locked = false)
        return fired
    }

    @Test
    fun `F-keys with modifiers`() {
        assertEquals(true, fires("L-F2", "F2", Mod.LOGO))
        assertEquals(true, fires("L-S-F12", "F12", Mod.LOGO or Mod.SHIFT))
        assertEquals(false, fires("L-F2", "F2", 0))
        assertEquals(true, fires("XF86AudioRaiseVolume", "XF86AudioRaiseVolume", 0))
    }

    @Test
    fun `a capital letter means Shift`() {
        assertEquals(true, fires("L-H", "h", Mod.LOGO or Mod.SHIFT))
        assertEquals(false, fires("L-H", "h", Mod.LOGO))
    }
}

class SystemKeyboardTest {
    @Test
    fun `a layout for the system's language`() {
        val k = SystemKeyboard.detect()
        // (Whatever this computer's language: at least "us", and a switch when there are more.)
        kotlin.test.assertTrue(k.layouts.startsWith("us") || ',' !in k.layouts)
        if (',' in k.layouts) kotlin.test.assertTrue(k.options.isNotEmpty(), k.toString())
    }
}

/**
 * On a layout of another script the key is told twice: Latin ("d", for the usual bindings) and as
 * the layout has it ("Cyrillic_ve"); a binding on the latter wins on that layout.
 */
class LatinLayoutKeysTest {

    private fun press(p: KeyProcessor, keysym: String, own: String, modifiers: Int) {
        p.onKey(true, keysym, modifiers, false, own)
        p.onKey(false, keysym, modifiers, false, own)
    }

    @Test
    fun `readable bindings fire by the Latin key on the Russian layout`() {
        val fired = ArrayList<String>()
        val p = KeyProcessor()
        p.registerBindings(
            KeyNotation.toNewm("Super+D") to { fired += "super+d" },
            KeyNotation.toNewm("Super+Shift+M") to { fired += "super+shift+m" },
            KeyNotation.toNewm("Ctrl+Alt+T") to { fired += "ctrl+alt+t" },
        )
        // What Caulis tells on "ru": the key of "в" is d, of "Ь" (with Shift) m, of "е" t.
        press(p, "d", "Cyrillic_ve", Mod.LOGO)
        press(p, "m", "Cyrillic_SOFTSIGN", Mod.LOGO or Mod.SHIFT)
        press(p, "t", "Cyrillic_ie", Mod.CTRL or Mod.ALT)
        assertEquals(listOf("super+d", "super+shift+m", "ctrl+alt+t"), fired)
    }

    @Test
    fun `a binding on the layout's own symbol wins there, the Latin one elsewhere`() {
        val fired = ArrayList<String>()
        val p = KeyProcessor()
        p.registerBindings(
            "L-d" to { fired += "latin" },
            "L-Cyrillic_ve" to { fired += "cyrillic" },
        )
        press(p, "d", "Cyrillic_ve", Mod.LOGO) // Russian
        press(p, "d", "d", Mod.LOGO) // us
        assertEquals(listOf("cyrillic", "latin"), fired)
    }
}
