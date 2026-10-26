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
