package newm

import kotlin.test.Test
import kotlin.test.assertEquals

/** The launcher's views: the categories programs go under, the grid's steps. */
class LauncherViewsTest {

    private fun app(name: String, vararg categories: String) = DesktopApp(name, "", name.lowercase(), "", id = name.lowercase(), categories = categories.toList())

    @Test
    fun `programs go under the first heading one of their categories is in`() {
        val h = DEFAULT_LAUNCHER_CATEGORIES
        assertEquals("Internet", categoryOf(app("Firefox", "Network", "WebBrowser"), h))
        assertEquals("Development", categoryOf(app("Code", "Utility", "TextEditor", "Development", "IDE"), h))
        assertEquals("System", categoryOf(app("foot", "System", "TerminalEmulator"), h))
        assertEquals("Accessories", categoryOf(app("Files", "GNOME", "GTK", "Utility", "Core", "FileManager"), h))
        assertEquals("Other", categoryOf(app("Odd", "X-Something"), h))
        assertEquals("Other", categoryOf(app("None"), h))
        val apps = listOf(app("Odd"), app("Firefox", "Network"), app("Blender", "Graphics", "3DGraphics"))
        assertEquals(listOf("Internet", "Graphics", "Other"), categoriesOf(apps, h))
        // Headings of one's own, in their order.
        assertEquals(listOf("Web", "Other"), categoriesOf(apps.take(2), linkedMapOf("Web" to listOf("Network"))))
    }

    @Test
    fun `a step in the grid stays within it`() {
        assertEquals(7, gridStep(1, 6, 20))
        assertEquals(1, gridStep(1, -6, 20))
        assertEquals(19, gridStep(19, 1, 20))
        assertEquals(0, gridStep(0, -1, 20))
        assertEquals(0, gridStep(5, 1, 0))
    }
}
