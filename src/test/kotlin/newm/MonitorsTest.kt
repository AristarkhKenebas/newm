package newm

import dev.caulis.wm.Caulis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Two monitors of different sizes and scales side by side (1920x1080 at 1, then 2560x1440 at
 * 1.25: 2048x1152 desktop units), each with a wall of its own: windows go from one to the
 * other keeping their size, the focus goes to a monitor, the special workspace follows it.
 */
class MonitorsTest {

    private fun Caulis.twoMonitors() {
        headlessMonitors = 2
        monitor("HEADLESS-1") { mode(1920, 1080); scale = 1.0 }
        monitor("HEADLESS-2") { mode(2560, 1440); scale = 1.25 }
    }

    private val wall = mapOf("screens" to mapOf("enabled" to false))
    private val screens = mapOf("screens" to mapOf("enabled" to true, "windows_per_screen" to 2, "layout" to "columns"))

    // The monitor (its name) whose wall [title] is on.
    private fun T.monitorOf(title: String): String? = onLoop {
        val h = layout.state.findViewOrNull(layout.viewOf(window(title))!!)?.third
        layout.workspaces.firstOrNull { it.handle == h }?.outputs?.firstOrNull()?.name
    }

    // The monitor newm says is worked on (where the special workspace shows).
    private fun T.workMonitor(): String? = onLoop { layout.activeWorkspace().outputs.firstOrNull()?.name }

    @Test
    fun `two monitors, two walls`() = withNewm(wall, setup = { twoMonitors() }) { t ->
        t.onLoop {
            assertEquals(2, t.layout.workspaces.size)
            val (a, b) = t.layout.workspaces
            assertEquals(listOf(0, 0, 1920, 1080), listOf(a.posX, a.posY, a.width, a.height))
            assertEquals(listOf(1920, 0, 2048, 1152), listOf(b.posX, b.posY, b.width, b.height))
        }
    }

    @Test
    fun `on the wall a window goes onto the monitor to the right keeping its size, and back`() = withNewm(wall, setup = { twoMonitors() }) { t ->
        t.open("w1")
        t.onLoop { t.layout.resizeFocusedView(1, 0) }
        t.eventually { assertEquals(listOf(0.0, 0.0, 2.0, 1.0), t.tile("w1")) }
        t.open("w2")
        t.focus("w1")
        // Nothing above: it stays.
        t.onLoop { t.layout.moveFocusedViewToMonitor(0, -1) }
        t.settle()
        assertEquals("HEADLESS-1", t.monitorOf("w1"))
        t.onLoop { t.layout.moveFocusedViewToMonitor(1, 0) }
        t.eventually {
            assertEquals("HEADLESS-2", t.monitorOf("w1"))
            assertEquals(listOf(2.0, 1.0), t.tile("w1").drop(2), "two tiles wide still")
            assertEquals("w1", t.focused())
            assertEquals("HEADLESS-2", t.workMonitor(), "the focus is on that monitor")
        }
        assertEquals("HEADLESS-1", t.monitorOf("w2"))
        // Its picture there: within the second monitor.
        t.eventually { t.onLoop { val r = t.layout.shownArea(t.layout.viewOf(t.window("w1"))!!)!!; assertTrue(r.x >= 1920 && r.right <= 1920 + 2048, "on the second monitor: $r") } }
        // Back, next to w2 (the window focused there last), not over it.
        t.onLoop { t.layout.moveFocusedViewToMonitor(-1, 0) }
        t.eventually {
            assertEquals("HEADLESS-1", t.monitorOf("w1"))
            assertEquals(listOf(2.0, 1.0), t.tile("w1").drop(2))
            val a = t.tile("w1")
            val b = t.tile("w2")
            assertTrue(a[0] + a[2] <= b[0] || b[0] + b[2] <= a[0] || a[1] + a[3] <= b[1] || b[1] + b[3] <= a[1], "no overlap: $a $b")
            assertEquals("HEADLESS-1", t.workMonitor())
        }
    }

    @Test
    fun `window-to-next-monitor keeps the size too`() = withNewm(wall, setup = { twoMonitors() }) { t ->
        t.open("w1")
        t.onLoop { t.layout.resizeFocusedView(0, 1) }
        t.eventually { assertEquals(listOf(1.0, 2.0), t.tile("w1").drop(2)) }
        t.onLoop { KeyActions.byName("window-to-next-monitor")!!.run(t.layout, emptyList()) }
        t.eventually {
            assertEquals("HEADLESS-2", t.monitorOf("w1"))
            assertEquals(listOf(1.0, 2.0), t.tile("w1").drop(2))
        }
    }

    @Test
    fun `on screens a window goes onto a screen of the other monitor, the screen it left tiled anew`() = withNewm(screens, setup = { twoMonitors() }) { t ->
        t.open("w1")
        t.open("w2")
        t.eventually { assertEquals(listOf(0.5, 0.0, 0.5, 1.0), t.tile("w2")) }
        t.onLoop { t.layout.moveFocusedViewToMonitor(1, 0) }
        t.eventually {
            assertEquals("HEADLESS-2", t.monitorOf("w2"))
            assertEquals(listOf(0.0, 0.0, 1.0, 1.0), t.tile("w2"), "the whole first screen there")
            assertEquals(listOf(0.0, 0.0, 1.0, 1.0), t.tile("w1"), "w1 has its screen to itself")
        }
        // A second one goes next to it on that screen.
        t.focus("w1")
        t.onLoop { t.layout.moveFocusedViewToMonitor(1, 0) }
        t.eventually {
            assertEquals("HEADLESS-2", t.monitorOf("w1"))
            assertEquals(listOf(0.5, 0.0, 0.5, 1.0), t.tile("w1"), "after w2, the window focused there")
            assertEquals(listOf(0.0, 0.0, 0.5, 1.0), t.tile("w2"))
        }
    }

    @Test
    fun `the focus goes to an empty monitor - nothing focused, the special workspace and new windows come there`() = withNewm(wall, setup = { twoMonitors() }) { t ->
        t.open("w1")
        t.onLoop { t.layout.focusMonitor(1, 0) }
        t.eventually {
            assertNull(t.focused())
            assertEquals("HEADLESS-2", t.workMonitor())
        }
        t.onLoop { t.layout.toggleSpecialWorkspace() }
        t.eventually { assertEquals("special", t.onLoop { t.caulis.desktop.shownSpecialOn(t.caulis.desktop.monitors[1])?.name }) }
        t.onLoop { t.layout.toggleSpecialWorkspace() }
        t.eventually { assertNull(t.onLoop { t.caulis.desktop.shownSpecialOn(t.caulis.desktop.monitors[1]) }) }
        Thread.sleep(600)
        t.open("w2")
        t.eventually { assertEquals("HEADLESS-2", t.monitorOf("w2")) }
        // Back: the window focused there last.
        t.onLoop { t.layout.focusMonitor(-1, 0) }
        t.eventually {
            assertEquals("w1", t.focused())
            assertEquals("HEADLESS-1", t.workMonitor())
        }
    }

    // Alt+drag in the tests (Super tapped alone would be a key of its own): the move gesture of the mouse and the fingers.
    private val noModifier = mapOf("gesture_bindings" to mapOf("move_resize" to listOf("A", "move-1", "swipe-2")))

    // The fingers' move with Alt held, let go after (the window lands when it is let go).
    private fun T.drag(dx: Double, dy: Double) {
        onLoop { caulis.simulateKey(KEY_LEFTALT, true) }
        eventually { assertTrue(onLoop { layout.hasModifier("A") }, "Alt held") }
        fingers("move-1", dx, dy)
        onLoop { caulis.simulateKey(KEY_LEFTALT, false) }
        settle()
    }

    @Test
    fun `dragged past the monitor's edge a window goes onto the other wall, keeping its size`() =
        withNewm(wall + noModifier, setup = { twoMonitors() }) { t ->
            t.open("w1")
            t.open("w2")
            t.eventually { assertEquals(listOf(1.0, 0.0, 1.0, 1.0), t.tile("w2")) }
            t.onLoop { t.caulis.simulatePointer(1500.0, 500.0) }
            // The view shows two tiles of 960: a gesture's 1 is 4 tiles; 0.4 takes w2's middle
            // (at 1440) 1.6 tiles on, onto the second monitor.
            t.drag(0.4, 0.0)
            t.eventually {
                assertEquals("HEADLESS-2", t.monitorOf("w2"))
                assertEquals(listOf(1.0, 1.0), t.tile("w2").drop(2), "one tile still")
                assertEquals("HEADLESS-2", t.workMonitor())
                t.onLoop { val r = t.layout.shownArea(t.layout.viewOf(t.window("w2"))!!)!!; assertTrue(r.x >= 1920, "on the second monitor: $r") }
            }
            assertEquals("HEADLESS-1", t.monitorOf("w1"))
            // And back, by the fingers the other way.
            t.drag(-0.4, 0.0)
            t.eventually { assertEquals("HEADLESS-1", t.monitorOf("w2")) }
        }

    @Test
    fun `on screens a window dragged onto the other monitor takes a screen there, the one it left tiled anew`() =
        withNewm(screens + noModifier, setup = { twoMonitors() }) { t ->
            t.open("w1")
            t.open("w2")
            t.eventually { assertEquals(listOf(0.5, 0.0, 0.5, 1.0), t.tile("w2")) }
            t.onLoop { t.caulis.simulatePointer(1500.0, 500.0) }
            // One screen in view: a gesture's 1 is 2 screens; 0.3 takes w2's middle (0.75) to 1.35.
            t.drag(0.3, 0.0)
            t.eventually {
                assertEquals("HEADLESS-2", t.monitorOf("w2"))
                assertEquals(listOf(0.0, 0.0, 1.0, 1.0), t.tile("w2"), "a screen of its own there")
                assertEquals(listOf(0.0, 0.0, 1.0, 1.0), t.tile("w1"), "w1 has its screen to itself")
            }
        }

    @Test
    fun `zoomed out a window can still be dragged onto the other monitor`() = withNewm(wall + noModifier, setup = { twoMonitors() }) { t ->
        t.open("w1")
        repeat(6) { t.onLoop { t.layout.basicScale(1) } }
        t.eventually { assertEquals(8.0, t.view()[2]) }
        t.onLoop { t.caulis.simulatePointer(200.0, 200.0) }
        // Eight tiles of 240 in view, w1's middle near the monitor's: more than 4 tiles to the
        // edge (a move reached 3 tiles before).
        t.drag(0.5, 0.0)
        t.eventually { assertEquals("HEADLESS-2", t.monitorOf("w1")) }
    }

    @Test
    fun `in the overview a window is dragged from one monitor's overview into the other's, and a click still chooses`() =
        withNewm(wall, setup = { twoMonitors() }) { t ->
            t.open("w1")
            t.open("w2")
            t.onLoop { t.layout.toggleOverview() }
            t.eventually { assertTrue(t.onLoop { t.layout.overviewShown() }) }
            t.settle()
            Thread.sleep(500)
            val r = t.onLoop { t.layout.shownArea(t.layout.viewOf(t.window("w2"))!!)!! }
            t.onLoop { t.caulis.simulatePointer(r.x + r.width / 2.0, r.y + r.height / 2.0) }
            t.onLoop { t.caulis.simulateButton(272, true) }
            // Right, in steps as a mouse goes, well onto the second monitor.
            repeat(60) {
                t.onLoop { t.caulis.simulateMotion(25.0, 0.0) }
                Thread.sleep(8)
            }
            t.onLoop { t.caulis.simulateButton(272, false) }
            t.eventually {
                assertEquals("HEADLESS-2", t.monitorOf("w2"))
                assertEquals(listOf(1.0, 1.0), t.tile("w2").drop(2), "one tile still")
            }
            assertTrue(t.onLoop { t.layout.overviewShown() }, "still the overview")
            assertEquals("HEADLESS-1", t.monitorOf("w1"))
            // A click (no drag) on w1 chooses it: the overview ends.
            t.settle()
            Thread.sleep(500)
            val a = t.onLoop { t.layout.shownArea(t.layout.viewOf(t.window("w1"))!!)!! }
            t.onLoop { t.caulis.simulatePointer(a.x + a.width / 2.0, a.y + a.height / 2.0, 272) }
            t.eventually {
                assertEquals("w1", t.focused())
                assertTrue(!t.onLoop { t.layout.overviewShown() }, "the overview ended there")
            }
        }

    private companion object {
        const val KEY_LEFTALT = 56
    }
}
