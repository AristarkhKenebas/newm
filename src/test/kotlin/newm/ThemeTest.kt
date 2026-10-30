package newm

import newm.bar.NewmThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** newm's own looks (theme = "jadefire"): what they set, and that the configuration's own settings win. */
class ThemeTest {

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.at(path: String): Any? = path.split('.').fold(this as Any?) { m, k -> (m as? Map<String, Any?>)?.get(k) }

    @Test
    fun `the look fills in what the configuration does not say`() {
        val s = NewmThemes.underlay(mapOf("theme" to "jadefire", "focus" to mapOf("width" to 5.0)))
        assertEquals("jadefire", s.at("colors.source"))
        assertEquals("jadefire", s.at("focus.shader"))
        assertEquals(5.0, s.at("focus.width"), "the configuration's own wins")
        assertEquals("dragon-eye", s.at("background.shader"))
        assertEquals("#3ddc97", s.at("panels.lock.accent"))
    }

    @Test
    fun `a picture of your own keeps its place, and no theme changes nothing`() {
        val own = mapOf("theme" to "Jadefire", "background" to mapOf("path" to "~/wall.jpg"))
        val s = NewmThemes.underlay(own)
        assertNull(s.at("background.shader"))
        assertEquals("~/wall.jpg", s.at("background.path"))
        assertEquals("jadefire", s.at("colors.source"), "the name in any case")
        val plain = mapOf("focus" to mapOf("width" to 2.0))
        assertEquals(plain, NewmThemes.underlay(plain))
        assertEquals(mapOf("theme" to "nope"), NewmThemes.underlay(mapOf("theme" to "nope")))
    }

    @Test
    fun `Jadefire's colours and tokens, and its border shader is there`() {
        val look = assertNotNull(NewmThemes.named("jadefire"))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF3DDC97), look.dark.accent, "jade, the accent")
        assertTrue(look.json(dark = true).contains("\"amber\": \"#f2b33d\""))
        assertTrue(look.css(dark = false).contains("@define-color accent #0e8a57;"))
        val border = ThemeTest::class.java.getResourceAsStream("/newm/borders/jadefire.glsl")?.use { String(it.readAllBytes()) }
        assertTrue(border != null && "vec4 paint(vec2 at)" in border)
    }

    @Test
    fun `theme reaches the settings from config kts`() {
        val config = NewmConfig(dev.caulis.wm.Caulis()).apply { theme = "jadefire" }
        assertEquals("jadefire", config.values["theme"])
    }
}
