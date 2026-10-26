package newm

import dev.caulis.wm.Caulis
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/** A newm config.py of before runs as it did: its values, its key bindings calling the layout. */
class PyConfigTest {

    // Records what config.py's functions ask of the layout.
    private class Recorder : PyConfig.Handler {
        val calls = ArrayList<Pair<String, List<Any?>>>()
        override fun layout(method: String, args: List<Any?>, kwargs: Map<String, Any?>): Any? {
            calls += method to (args + kwargs.values)
            return null
        }
        override fun view(handle: Int, method: String, args: List<Any?>, kwargs: Map<String, Any?>): Any? = null
        override fun viewData(view: View): Map<String, Any?> = emptyMap()
        override fun viewOf(handle: Int): View? = null
    }

    private val nord: Path = Path.of(System.getProperty("user.home"), "projects/owl_dots/newm/newm_nord/.config/newm/config.py")

    @Test
    fun `the newm_nord config loads, and its key bindings call the layout`() {
        assumeTrue(Files.isRegularFile(nord), "no newm_nord config here")
        val recorder = Recorder()
        val python = assertNotNull(PyConfig.start(recorder))
        python.use {
            val settings = python.load(nord)
            @Suppress("UNCHECKED_CAST")
            assertEquals(10L, (settings["view"] as Map<String, Any?>)["padding"])
            @Suppress("UNCHECKED_CAST")
            assertEquals("us,ru", (settings["pywm"] as Map<String, Any?>)["xkb_layout"])
            @Suppress("UNCHECKED_CAST")
            assertEquals(listOf("L", "move-1", "swipe-2"), (settings["gesture_bindings"] as Map<String, Any?>)["move_resize"])

            val layout = Layout(Caulis())
            @Suppress("UNCHECKED_CAST")
            val bindings = keyBindingsOf((settings["key_bindings"] as PyFunction)(layout))
            assertTrue(bindings.size > 30, "${bindings.size} bindings")
            bindings.first { it.first == "L-s" }.second()
            bindings.first { it.first == "L-" }.second()
            bindings.first { it.first == "C-A-l" }.second()
            assertEquals(
                listOf("move_in_stack" to listOf<Any?>(1L), "toggle_overview" to listOf<Any?>(true), "ensure_locked" to listOf<Any?>(true)),
                recorder.calls,
            )
            // Its mistake (brightness is not defined there) is logged, as newm did, and nothing breaks.
            bindings.first { it.first == "XF86MonBrightnessUp" }.second()
            bindings.first { it.first == "L-s" }.second()
            assertEquals("move_in_stack", recorder.calls.last().first)
        }
    }
}
