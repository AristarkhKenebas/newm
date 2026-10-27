package newm

import dev.caulis.core.Blur
import dev.caulis.wm.Caulis
import kotlin.test.Test
import kotlin.test.assertEquals

/** specialWorkspace { } and focus { } of config.kts reach Caulis's special workspaces and the focus border. */
class SpecialWorkspaceSettingsTest {

    @Test
    fun `special workspace settings from the DSL`() {
        val config = NewmConfig(Caulis()).apply {
            specialWorkspace {
                dim = 0.6
                blurRadius = 5
                blurPasses = 3
                gap = 20
                size = 0.8
                hideWhenEmpty = false
            }
            focus {
                color = "rgba(33ccffee) rgba(00ff99ee) 45deg"
                shader = "border.glsl"
                animate = false
            }
        }
        try {
            Config.loadConfig(config.values)
            val settings = NewmSpecialWorkspaces(Layout(Caulis())).settings()
            assertEquals(0.6f, settings.dim)
            assertEquals(Blur(5, 3), settings.blur)
            assertEquals(20, settings.gap)
            assertEquals(0.8, settings.size)
            assertEquals(false, settings.hideWhenEmpty)
            assertEquals(true, settings.hideOnLock, "always: newm's lock screen is below them otherwise")
            assertEquals("rgba(33ccffee) rgba(00ff99ee) 45deg", Config.lookup("focus.color"))
            assertEquals(false, Config.lookup("focus.animate"))
        } finally {
            Config.loadConfig(emptyMap())
        }
    }
}
