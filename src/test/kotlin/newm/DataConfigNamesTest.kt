package newm

import dev.caulis.wm.Caulis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** config.json / config.lua: the DSL's names in snake_case reach the settings newm reads (and newm's old names still do). */
class DataConfigNamesTest {

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.at(path: String): Any? = path.split('.').fold(this as Any?) { m, k -> (m as? Map<String, Any?>)?.get(k) }

    @Test
    fun `gestures, tuning, outputs, tablet, energy`() {
        val data = linkedMapOf<String, Any?>(
            "gestures" to linkedMapOf("enabled" to false, "scale_px" to 600, "dbus_enabled" to false, "lp_inertia" to 0.5,
                "bind" to linkedMapOf("swipe" to listOf(null, "swipe-4"), "move_resize" to linkedMapOf("modifier" to "A", "kinds" to listOf("move-1", "swipe-2"))),
                "on" to listOf(linkedMapOf("kind" to "swipe-4", "direction" to "down", "action" to "toggle_overview"))),
            "tuning" to linkedMapOf("swipe" to linkedMapOf("gesture_factor" to 3.0), "grid" to linkedMapOf("debug" to true)),
            "outputs" to listOf(linkedMapOf("name" to "eDP-1", "x" to 10, "y" to 20, "refresh_hz" to 60.0)),
            "input" to linkedMapOf("tap_to_click" to true, "tablet" to linkedMapOf("left_handed" to true)),
            "energy" to linkedMapOf("lock_after" to 0, "on_idle" to { _: Any? -> }, "after" to linkedMapOf("900" to "launch true")),
            "every_frame" to { },
        )
        val (s, problems) = DataConfig.settings(data, Caulis(), "config.json")
        assertEquals(emptyList(), problems)
        assertEquals(false, s.at("gestures.c.enabled"))
        assertEquals(600, s.at("gestures.c.scale_px"))
        assertEquals(false, s.at("gestures.dbus.enabled"))
        assertEquals(0.5, s.at("gestures.lp_inertia"))
        assertEquals(listOf(null, "swipe-4"), s.at("gesture_bindings.swipe"))
        assertEquals(listOf("A", "move-1", "swipe-2"), s.at("gesture_bindings.move_resize"))
        @Suppress("UNCHECKED_CAST")
        val on = s.at("gestures.on") as List<Map<String, Any?>>
        assertEquals("down", on.single()["direction"])
        assertTrue(on.single()["action"] is Function1<*, *>)
        assertEquals(3.0, s.at("swipe.gesture_factor"))
        assertEquals(true, s.at("grid.debug"))
        @Suppress("UNCHECKED_CAST")
        val output = (s["outputs"] as List<Map<String, Any?>>).single()
        assertEquals(10, output["pos_x"])
        assertEquals(20, output["pos_y"])
        assertEquals(60000.0, output["mHz"])
        assertEquals(true, s.at("pywm.tablet.left_handed"))
        assertEquals(true, s.at("pywm.tap_to_click"))
        assertEquals(0, s.at("energy.lock_after"))
        assertTrue(s.at("energy.idle_callback") is Function1<*, *>)
        @Suppress("UNCHECKED_CAST")
        assertEquals(900, (s.at("energy.after") as List<Map<String, Any?>>).single()["seconds"])
        assertTrue(s["synchronous_update"] is Function0<*>)
    }

    @Test
    fun `launcher and lock at the top, entries and keys`() {
        val data = linkedMapOf<String, Any?>(
            "launcher" to linkedMapOf("width" to 600, "entries" to linkedMapOf("Lock" to "ensure_locked"), "keys" to linkedMapOf("C-n" to "next")),
            "lock" to linkedMapOf("clock" to "HH:mm:ss"),
            "panels" to linkedMapOf("top_bar" to linkedMapOf("cmd" to "newm-panel-basic bar", "enabled" to true)),
            "terminal" to "foot",
        )
        val (s, problems) = DataConfig.settings(data, Caulis(), "config.json")
        assertEquals(emptyList(), problems)
        assertEquals(600, s.at("panels.launcher.width"))
        assertEquals("HH:mm:ss", s.at("panels.lock.clock"))
        @Suppress("UNCHECKED_CAST")
        val entry = (s.at("panels.launcher.entries") as List<Map<String, Any?>>).single()
        assertEquals("Lock", entry["name"])
        assertTrue(entry["action"] is Function1<*, *>)
        assertTrue(s.at("panels.launcher.keys.C-n") is Function1<*, *>)
        assertEquals("newm-panel-basic bar", s.at("panels.top_bar.cmd"))
        assertEquals(true, s.at("panels.top_bar.native.enabled"))
        assertEquals("foot", s["terminal"])

        val (_, wrong) = DataConfig.settings(mapOf("launcher" to mapOf("keys" to mapOf("C-x" to "jump"))), Caulis(), "config.json")
        assertTrue(wrong.single().contains("launcher.keys.C-x"), wrong.toString())
    }
}
