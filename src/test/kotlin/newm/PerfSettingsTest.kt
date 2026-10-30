package newm

import dev.caulis.wm.Caulis
import kotlin.test.Test
import kotlin.test.assertEquals

/** compositor { } performance settings and warmup { } from config.json reach Caulis and newm. */
class PerfSettingsTest {

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.at(path: String): Any? = path.split('.').fold(this as Any?) { m, k -> (m as? Map<String, Any?>)?.get(k) }

    @Test
    fun `performance settings in compositor and warmup`() {
        val data = linkedMapOf<String, Any?>(
            "compositor" to linkedMapOf(
                "stall_report_ms" to 80, "trim_memory" to false, "trim_memory_every_minutes" to 5,
                "malloc_pools" to 4, "quick_cursor" to false, "widget_threads" to 0,
                "input_thread" to false, "gl_vendor_by_driver" to false, "keep_hidden_menus_seconds" to 30,
            ),
            "warmup" to linkedMapOf("enabled" to true, "rest_ms" to 500, "compose" to false, "collect" to false, "own_thread" to false),
        )
        val (s, problems) = DataConfig.settings(data, Caulis(), "config.json")
        assertEquals(emptyList(), problems)
        assertEquals(500, s.at("warmup.rest_ms"))
        assertEquals(false, s.at("warmup.compose"))
        @Suppress("UNCHECKED_CAST")
        val caulis = Caulis().also { applyPywmSettings(it, s["pywm"] as Map<String, Any?>) }
        assertEquals(80L, caulis.stallReportMillis)
        assertEquals(false, caulis.trimMemory)
        assertEquals(5, caulis.trimMemoryEveryMinutes)
        assertEquals(4, caulis.mallocPools)
        assertEquals(false, caulis.quickCursorMoves)
        assertEquals(0, caulis.widgetThreads)
        assertEquals(false, caulis.inputThread)
        assertEquals(false, caulis.glVendorByDriver)
        assertEquals(30, dev.caulis.compose.ComposeScenes.keepHiddenSeconds)
        assertEquals(false, s.at("warmup.own_thread"))
        dev.caulis.compose.ComposeScenes.keepHiddenSeconds = 60
    }

    @Test
    fun `the DSL writes the same names`() {
        val c = Caulis()
        val config = NewmConfig(c)
        config.compositor { stallReportMillis = 70; widgetThreads = 1; quickCursor = false }
        config.compositor { inputThread = false; glVendorByDriver = false; keepHiddenMenusSeconds = 20 }
        config.warmup { restMillis = 300; collect = false; ownThread = false }
        assertEquals(70, config.values.at("pywm.stall_report_ms"))
        assertEquals(1, config.values.at("pywm.widget_threads"))
        assertEquals(false, config.values.at("pywm.quick_cursor"))
        assertEquals(300, config.values.at("warmup.rest_ms"))
        assertEquals(false, config.values.at("warmup.collect"))
        assertEquals(false, config.values.at("pywm.input_thread"))
        assertEquals(false, config.values.at("pywm.gl_vendor_by_driver"))
        assertEquals(20, config.values.at("pywm.keep_hidden_menus_seconds"))
        assertEquals(false, config.values.at("warmup.own_thread"))
    }
}
