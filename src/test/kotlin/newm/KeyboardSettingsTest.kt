package newm

import dev.caulis.wm.Caulis
import kotlin.test.Test
import kotlin.test.assertEquals

/** keyboard { layouts, options }: onto Caulis, with a switch between several layouts when no options are given. */
class KeyboardSettingsTest {

    @Test
    fun `several layouts without options switch with Alt+Shift, options taken out on a reload are gone`() {
        val caulis = Caulis()
        caulis.applyHostSettings(mapOf("pywm" to mapOf("xkb_layout" to "us,ru")))
        assertEquals("us,ru", caulis.keyboardLayouts)
        assertEquals("grp:alt_shift_toggle", caulis.keyboardOptions, "a way to switch, as the docs say")
        caulis.applyHostSettings(mapOf("pywm" to mapOf("xkb_layout" to "us,ru", "xkb_options" to "grp:caps_toggle")))
        assertEquals("grp:caps_toggle", caulis.keyboardOptions)
        caulis.applyHostSettings(mapOf("pywm" to mapOf("xkb_layout" to "us")))
        assertEquals("", caulis.keyboardOptions, "taken out of the configuration: not kept")
    }
}
