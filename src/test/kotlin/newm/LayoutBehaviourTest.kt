package newm

import dev.caulis.ffi.SharedMemory
import dev.caulis.protocol.client.Connection
import dev.caulis.protocol.client.wayland.WlCompositor
import dev.caulis.protocol.client.wayland.WlRegistry
import dev.caulis.protocol.client.wayland.WlShm
import dev.caulis.protocol.client.wayland.WlSurface
import dev.caulis.protocol.client.xdgshell.XdgSurface
import dev.caulis.protocol.client.xdgshell.XdgToplevel
import dev.caulis.protocol.client.xdgshell.XdgWmBase
import dev.caulis.wm.Caulis
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * newm's window management on a headless Caulis, with real windows: where windows open, how
 * the view zooms, swipes, the overview, fullscreen, resizing and closing — the tiles (i, j,
 * w, h) and the view (i, j, size) the original newm's algorithm gives (worked out by hand from
 * legacy/newm/layout.py and state.py, step by step, in the comments).
 */
@OptIn(dev.caulis.log.InternalCaulisApi::class)
class LayoutBehaviourTest {

    // (Tests of a placement before "compact" became the default: they keep checking that one.)
    private val RIGHT_OF_FOCUSED = mapOf("windows" to mapOf("new_placement" to "right-of-focused"))

    @Test
    fun `newm's original placement - first at 0 0, then beside, then below`() = withNewm(mapOf("windows" to mapOf("new_placement" to "original"))) { t ->
        // The view starts at i = j = -0.5, size 2. The first window: (0, 0); focusing it keeps
        // size 2 and constrain centres the one tile: i = j = 0 + .5 - 1 = -0.5.
        t.open("w1")
        t.eventually { assertEquals(listOf(0.0, 0.0, 1.0, 1.0), t.tile("w1")); assertEquals(listOf(-0.5, -0.5, 2.0), t.view()) }
        // The view (i -1..1) shows more than the windows (0..0): right of them, (1, 0). Focused,
        // the view goes to i = max(-0.5, 1 + 1 - 2) = 0; one row: j = -0.5.
        t.open("w2")
        t.eventually { assertEquals(listOf(1.0, 0.0, 1.0, 1.0), t.tile("w2")); assertEquals(listOf(0.0, -0.5, 2.0), t.view()) }
        // Across the view (0..1) is no more than the windows; down (-1..1) is: below them, (0, 1).
        t.open("w3")
        t.eventually { assertEquals(listOf(0.0, 1.0, 1.0, 1.0), t.tile("w3")); assertEquals(listOf(0.0, 0.0, 2.0), t.view()) }
    }

    @Test
    fun `a new window opens right of the focused one, the rest move right`() = withNewm(RIGHT_OF_FOCUSED) { t ->
        t.open("w1")
        t.open("w2")
        t.open("w3")
        t.eventually {
            assertEquals(listOf(0.0, 0.0, 1.0, 1.0), t.tile("w1"))
            assertEquals(listOf(1.0, 0.0, 1.0, 1.0), t.tile("w2"))
            assertEquals(listOf(2.0, 0.0, 1.0, 1.0), t.tile("w3"))
        }
        t.focus("w1")
        t.open("w4")
        t.eventually {
            assertEquals(listOf(1.0, 0.0, 1.0, 1.0), t.tile("w4"))
            assertEquals(listOf(2.0, 0.0, 1.0, 1.0), t.tile("w2"))
            assertEquals(listOf(3.0, 0.0, 1.0, 1.0), t.tile("w3"))
            assertEquals("w4", t.focused())
        }
    }

    @Test
    fun `a child window opens below its parent, and closing it gives the focus back`() = withNewm(emptyMap()) { t ->
        val w1 = t.open("w1")
        t.open("w2")
        t.open("w3")
        t.eventually { assertEquals("w3", t.focused()) }
        // A dialog of w1 (an xdg parent), while w3 has the focus.
        val dialog = t.open("dialog", parent = w1)
        t.eventually {
            assertEquals(listOf(0.0, 1.0, 1.0, 1.0), t.tile("dialog"))
            assertEquals("dialog", t.focused())
        }
        dialog.close()
        t.eventually { assertEquals("w1", t.focused()) }
    }

    @Test
    fun `a window of the focused program without a parent counts as its child`() = withNewm(emptyMap()) { t ->
        t.open("w1", appId = "org.telegram.desktop")
        t.open("w2", appId = "other")
        t.focus("w1")
        // Telegram's photo viewer: no parent, the same program and app id, w1 focused.
        val viewer = t.open("viewer", appId = "org.telegram.desktop")
        t.eventually { assertEquals(listOf(0.0, 1.0, 1.0, 1.0), t.tile("viewer")) }
        viewer.close()
        t.eventually { assertEquals("w1", t.focused()) }
    }

    @Test
    fun `childPlacement over floats it over its parent, as newm`() = withNewm(mapOf("windows" to mapOf("child_placement" to "over"))) { t ->
        val w1 = t.open("w1")
        t.open("dialog", parent = w1)
        t.eventually { assertTrue(t.onLoop { t.layout.viewOf(t.window("dialog"))!!.isFloat(t.layout.state) }) }
    }

    @Test
    fun `fullscreen in and out`() = withNewm(emptyMap()) { t ->
        t.open("w1")
        t.open("w2")
        t.eventually { assertEquals(listOf(0.0, -0.5, 2.0), t.view()) }
        t.onLoop { t.layout.toggleFullscreen() }
        // with_fullscreen: the view on w2's tile, one by one.
        t.eventually {
            assertTrue(t.onLoop { t.wsState().isFullscreen() })
            assertEquals(listOf(1.0, 0.0, 1.0), t.view())
        }
        t.onLoop { t.layout.toggleFullscreen() }
        // without_fullscreen: the view before (its i 0 is within 1 - 2 + 1 .. : kept).
        t.eventually {
            assertFalse(t.onLoop { t.wsState().isFullscreen() })
            assertEquals(listOf(0.0, -0.5, 2.0), t.view())
        }
    }

    @Test
    fun `at idle the layout's queue sleeps and no frames are held on, after an animation too`() = withNewm(emptyMap()) { t ->
        assertEquals(Caulis.Backend.HEADLESS, t.caulis.runningBackend)
        t.open("w1")
        t.open("w2")
        t.eventually {
            assertFalse(t.onLoop { t.layout.queueTicking }, "the queue sleeps")
            assertEquals(0, t.onLoop { t.layout.framesHeld }, "no frames held on")
        }
        // An animation wakes the queue; done, it sleeps again and lets the frames go.
        t.onLoop { t.layout.toggleOverview() }
        t.eventually { assertEquals(listOf(-1.0, -1.5, 4.0), t.view()) }
        t.onLoop { t.layout.toggleOverview() }
        t.eventually {
            assertEquals(listOf(0.0, -0.5, 2.0), t.view())
            assertFalse(t.onLoop { t.layout.queueTicking }, "the queue sleeps again")
            assertEquals(0, t.onLoop { t.layout.framesHeld }, "no frames held on again")
        }
    }

    @Test
    fun `the overview shows all, and back`() = withNewm(emptyMap()) { t ->
        t.open("w1")
        t.open("w2")
        t.eventually { assertEquals(listOf(0.0, -0.5, 2.0), t.view()) }
        t.onLoop { t.layout.toggleOverview() }
        // Windows 0..1 by 0..0: width 4, height 3, size 4; i = 0 - (4 - 2) / 2, j = 0 - (4 - 1) / 2.
        t.eventually { assertEquals(listOf(-1.0, -1.5, 4.0), t.view()) }
        t.onLoop { t.layout.toggleOverview() }
        t.eventually { assertEquals(listOf(0.0, -0.5, 2.0), t.view()) }
    }

    @Test
    fun `resizing by keys - wider goes over the neighbour, narrower than one tile reaches left, as newm`() = withNewm(emptyMap()) { t ->
        t.open("w1")
        t.open("w2")
        t.focus("w1")
        t.onLoop { t.layout.resizeFocusedView(1, 0) }
        t.eventually {
            assertEquals(listOf(0.0, 0.0, 2.0, 1.0), t.tile("w1"))
            assertEquals(listOf(1.0, 0.0, 1.0, 1.0), t.tile("w2"))
        }
        t.onLoop { t.layout.resizeFocusedView(-1, 0) }
        t.eventually { assertEquals(listOf(0.0, 0.0, 1.0, 1.0), t.tile("w1")) }
        // One tile narrower: newm makes it two wide, from one tile left (constrain then closes the empty column).
        t.onLoop { t.layout.resizeFocusedView(-1, 0) }
        t.eventually { assertEquals(2.0, t.tile("w1")[2]) }
    }

    @Test
    fun `four fingers up zoom out by one tile, snapping to it`() = withNewm(emptyMap()) { t ->
        t.open("w1")
        t.open("w2")
        t.eventually { assertEquals(listOf(0.0, -0.5, 2.0), t.view()) }
        // size = 2 - 4 * delta_y: delta_y -0.25 is size 3 (the grid's top: one more than before).
        t.fingers("swipe-4", 0.0, -0.25)
        // Constrained: 3 > the windows' 2 x 1, centred: i = 0 + 1 - 1.5, j = 0 + .5 - 1.5.
        t.eventually { assertEquals(listOf(-0.5, -1.0, 3.0), t.view()) }
        // The programs are asked for their size at the new zoom (none left at the old one).
        t.eventually { assertEquals(null, t.onLoop { t.wsState().sizeOrigin }) }
    }

    @Test
    fun `three fingers move the view over the tiles, snapping to one`() = withNewm(RIGHT_OF_FOCUSED) { t ->
        t.open("w1")
        t.open("w2")
        t.open("w3")
        t.eventually { assertEquals(listOf(1.0, -0.5, 2.0), t.view()) }
        // i = 1 - 4 * 2 * delta_x: fingers right by an eighth is one tile left.
        t.fingers("swipe-3", 0.125, 0.0)
        t.eventually { assertEquals(listOf(0.0, -0.5, 2.0), t.view()) }
    }

    @Test
    fun `closing the focused window focuses the nearest and closes its column`() = withNewm(RIGHT_OF_FOCUSED) { t ->
        t.open("w1")
        val w2 = t.open("w2")
        t.open("w3")
        t.focus("w2")
        w2.close()
        t.eventually {
            assertEquals(listOf(1.0, 0.0, 1.0, 1.0), t.tile("w3"))
            assertTrue(t.focused() == "w1" || t.focused() == "w3")
        }
    }

    // --- screens (screens { })

    private fun screens(vararg extra: Pair<String, Any?>, windows: Map<String, Any?> = emptyMap()): Map<String, Any?> =
        mapOf("screens" to mapOf("enabled" to true, *extra), "windows" to windows)

    @Test
    fun `screens - two windows share a screen, the third opens on the next one`() = withNewm(screens()) { t ->
        t.open("w1")
        t.eventually { assertEquals(listOf(0.0, 0.0, 1.0, 1.0), t.tile("w1")); assertEquals(listOf(0.0, 0.0, 1.0), t.view()) }
        t.open("w2")
        t.eventually {
            assertEquals(listOf(0.0, 0.0, 0.5, 1.0), t.tile("w1"))
            assertEquals(listOf(0.5, 0.0, 0.5, 1.0), t.tile("w2"))
            assertEquals(listOf(0.0, 0.0, 1.0), t.view())
        }
        t.open("w3")
        t.eventually {
            assertEquals(listOf(1.0, 0.0, 1.0, 1.0), t.tile("w3"))
            assertEquals(listOf(1.0, 0.0, 1.0), t.view(), "exactly one screen on")
        }
        t.assertCleanView()
    }

    @Test
    fun `screens - each layout, one to five windows, exact places`() {
        // (Fractions of a screen, to a hundredth; the headless monitor is wider than tall.)
        val expected = mapOf(
            "columns" to listOf(
                listOf(listOf(0.0, 0.0, 1.0, 1.0)),
                listOf(listOf(0.0, 0.0, 0.5, 1.0), listOf(0.5, 0.0, 0.5, 1.0)),
                listOf(listOf(0.0, 0.0, 0.33, 1.0), listOf(0.33, 0.0, 0.33, 1.0), listOf(0.67, 0.0, 0.33, 1.0)),
                listOf(listOf(0.0, 0.0, 0.25, 1.0), listOf(0.25, 0.0, 0.25, 1.0), listOf(0.5, 0.0, 0.25, 1.0), listOf(0.75, 0.0, 0.25, 1.0)),
                listOf(listOf(0.0, 0.0, 0.2, 1.0), listOf(0.2, 0.0, 0.2, 1.0), listOf(0.4, 0.0, 0.2, 1.0), listOf(0.6, 0.0, 0.2, 1.0), listOf(0.8, 0.0, 0.2, 1.0)),
            ),
            "rows" to listOf(
                listOf(listOf(0.0, 0.0, 1.0, 1.0)),
                listOf(listOf(0.0, 0.0, 1.0, 0.5), listOf(0.0, 0.5, 1.0, 0.5)),
                listOf(listOf(0.0, 0.0, 1.0, 0.33), listOf(0.0, 0.33, 1.0, 0.33), listOf(0.0, 0.67, 1.0, 0.33)),
                listOf(listOf(0.0, 0.0, 1.0, 0.25), listOf(0.0, 0.25, 1.0, 0.25), listOf(0.0, 0.5, 1.0, 0.25), listOf(0.0, 0.75, 1.0, 0.25)),
                listOf(listOf(0.0, 0.0, 1.0, 0.2), listOf(0.0, 0.2, 1.0, 0.2), listOf(0.0, 0.4, 1.0, 0.2), listOf(0.0, 0.6, 1.0, 0.2), listOf(0.0, 0.8, 1.0, 0.2)),
            ),
            // Hyprland's halves and quarters.
            "grid" to listOf(
                listOf(listOf(0.0, 0.0, 1.0, 1.0)),
                listOf(listOf(0.0, 0.0, 0.5, 1.0), listOf(0.5, 0.0, 0.5, 1.0)),
                listOf(listOf(0.0, 0.0, 0.5, 1.0), listOf(0.5, 0.0, 0.5, 0.5), listOf(0.5, 0.5, 0.5, 0.5)),
                listOf(listOf(0.0, 0.0, 0.5, 0.5), listOf(0.0, 0.5, 0.5, 0.5), listOf(0.5, 0.0, 0.5, 0.5), listOf(0.5, 0.5, 0.5, 0.5)),
                listOf(listOf(0.0, 0.0, 0.33, 1.0), listOf(0.33, 0.0, 0.33, 0.5), listOf(0.33, 0.5, 0.33, 0.5), listOf(0.67, 0.0, 0.33, 0.5), listOf(0.67, 0.5, 0.33, 0.5)),
            ),
            // i3's master and strips (masterRatio 0.6, at the left).
            "master" to listOf(
                listOf(listOf(0.0, 0.0, 1.0, 1.0)),
                listOf(listOf(0.0, 0.0, 0.6, 1.0), listOf(0.6, 0.0, 0.4, 1.0)),
                listOf(listOf(0.0, 0.0, 0.6, 1.0), listOf(0.6, 0.0, 0.4, 0.5), listOf(0.6, 0.5, 0.4, 0.5)),
                listOf(listOf(0.0, 0.0, 0.6, 1.0), listOf(0.6, 0.0, 0.4, 0.33), listOf(0.6, 0.33, 0.4, 0.33), listOf(0.6, 0.67, 0.4, 0.33)),
                listOf(listOf(0.0, 0.0, 0.6, 1.0), listOf(0.6, 0.0, 0.4, 0.25), listOf(0.6, 0.25, 0.4, 0.25), listOf(0.6, 0.5, 0.4, 0.25), listOf(0.6, 0.75, 0.4, 0.25)),
            ),
            // One a screen: each on its own.
            "monocle" to listOf(
                listOf(listOf(0.0, 0.0, 1.0, 1.0)),
                listOf(listOf(0.0, 0.0, 1.0, 1.0), listOf(1.0, 0.0, 1.0, 1.0)),
                listOf(listOf(0.0, 0.0, 1.0, 1.0), listOf(1.0, 0.0, 1.0, 1.0), listOf(2.0, 0.0, 1.0, 1.0)),
                listOf(listOf(0.0, 0.0, 1.0, 1.0), listOf(1.0, 0.0, 1.0, 1.0), listOf(2.0, 0.0, 1.0, 1.0), listOf(3.0, 0.0, 1.0, 1.0)),
                listOf(listOf(0.0, 0.0, 1.0, 1.0), listOf(1.0, 0.0, 1.0, 1.0), listOf(2.0, 0.0, 1.0, 1.0), listOf(3.0, 0.0, 1.0, 1.0), listOf(4.0, 0.0, 1.0, 1.0)),
            ),
        )
        for ((layout, steps) in expected) withNewm(screens("windows_per_screen" to 5, "layout" to layout)) { t ->
            for ((n, places) in steps.withIndex()) {
                t.open("w${n + 1}")
                t.eventually {
                    for ((k, place) in places.withIndex()) assertEquals(place, t.tile("w${k + 1}"), "$layout, ${n + 1} windows: w${k + 1}")
                }
            }
            t.assertCleanView()
        }
    }

    @Test
    fun `screens - moving within a screen leaves the view, to the next one moves exactly one screen`() = withNewm(screens()) { t ->
        t.open("w1")
        t.open("w2")
        t.open("w3")
        t.focus("w1")
        t.eventually { assertEquals(listOf(0.0, 0.0, 1.0), t.view()) }
        t.onLoop { t.layout.move(1, 0) }
        t.eventually { assertEquals("w2", t.focused()); assertEquals(listOf(0.0, 0.0, 1.0), t.view(), "within the screen: the view stays") }
        t.assertCleanView()
        t.onLoop { t.layout.move(1, 0) }
        t.eventually { assertEquals("w3", t.focused()); assertEquals(listOf(1.0, 0.0, 1.0), t.view(), "the next screen: one screen on") }
        t.assertCleanView()
        t.onLoop { t.layout.move(-1, 0) }
        t.eventually { assertEquals("w2", t.focused()); assertEquals(listOf(0.0, 0.0, 1.0), t.view()) }
        // Three fingers snap to whole screens (i = 0 - 4 * 1 * delta_x: a quarter left is one screen on).
        t.fingers("swipe-3", -0.25, 0.0)
        t.eventually { assertEquals(listOf(1.0, 0.0, 1.0), t.view()) }
        t.assertCleanView()
    }

    @Test
    fun `screens - the lower window shows nothing of the upper one through the bar`() =
        withNewm(screens("overflow" to "down", "windows_per_screen" to 1) + bar()) { t ->
            t.open("upper")
            t.open("lower")
            t.eventually {
                assertEquals(listOf(0.0, 1.0, 1.0, 1.0), t.tile("lower"))
                assertEquals(listOf(0.0, 1.0, 1.0), t.view())
                assertTrue(t.onLoop { t.wsState().topExcluded } > 0.0, "a bar at the top")
            }
            t.assertCleanView()
            t.assertBarFree()
        }

    @Test
    fun `newm's wall too - the window above is cut at the bar, unless windowsUnderBar`() {
        for (under in listOf(false, true)) withNewm(mapOf("screens" to mapOf("enabled" to false, "windows_under_bar" to under)) + bar()) { t ->
            val upper = t.open("upper")
            t.open("lower", parent = upper)
            t.eventually { assertEquals(listOf(0.0, 1.0, 1.0, 1.0), t.tile("lower")) }
            t.focus("lower")
            t.onLoop { t.layout.fitFocusedView() }
            t.eventually { assertEquals(listOf(0.0, 1.0, 1.0), t.view()) }
            val zone = t.barZone()
            val inBar = t.onLoop { t.layout.shownArea(t.layout.viewOf(t.window("upper"))!!)?.intersect(zone) }
            if (under) assertTrue(inBar != null, "under the bar, as newm") else assertEquals(null, inBar, "nothing of the upper window under the bar")
        }
    }

    @Test
    fun `screens - closing pulls the next screen's window back, or not`() {
        withNewm(screens()) { t ->
            t.open("w1")
            val w2 = t.open("w2")
            t.open("w3")
            t.focus("w2")
            w2.close()
            t.eventually {
                assertEquals(listOf(0.0, 0.0, 0.5, 1.0), t.tile("w1"))
                assertEquals(listOf(0.5, 0.0, 0.5, 1.0), t.tile("w3"))
                assertEquals(listOf(0.0, 0.0, 1.0), t.view())
            }
            t.assertCleanView()
        }
        withNewm(screens("pull_back" to false)) { t ->
            t.open("w1")
            val w2 = t.open("w2")
            t.open("w3")
            t.focus("w2")
            w2.close()
            t.eventually {
                assertEquals(listOf(0.0, 0.0, 1.0, 1.0), t.tile("w1"))
                assertEquals(listOf(1.0, 0.0, 1.0, 1.0), t.tile("w3"))
            }
        }
    }

    @Test
    fun `screens - keys move a window between screens, swap, promote, change the layout`() = withNewm(screens()) { t ->
        t.open("w1")
        t.open("w2")
        t.open("w3")
        t.onLoop { t.layout.moveFocusedViewToScreen(-1) }
        // The screen before is full: w3 and its last window change places.
        t.eventually {
            assertEquals(listOf(0.5, 0.0, 0.5, 1.0), t.tile("w3"))
            assertEquals(listOf(1.0, 0.0, 1.0, 1.0), t.tile("w2"))
            assertEquals(listOf(0.0, 0.0, 1.0), t.view())
        }
        t.onLoop { t.layout.swapOnScreen() }
        t.eventually { assertEquals(listOf(0.0, 0.0, 0.5, 1.0), t.tile("w3")); assertEquals(listOf(0.5, 0.0, 0.5, 1.0), t.tile("w1")) }
        t.focus("w1")
        t.onLoop { t.layout.promoteToMaster() }
        t.eventually { assertEquals(listOf(0.0, 0.0, 0.5, 1.0), t.tile("w1")) }
        t.onLoop { t.layout.nextScreenLayout() }
        t.eventually { assertEquals(listOf(0.0, 0.0, 1.0, 0.5), t.tile("w1")); assertEquals(listOf(0.0, 0.5, 1.0, 0.5), t.tile("w3")) }
        t.onLoop { t.layout.nextScreenLayout("monocle") }
        // One a screen: w3 goes on a screen of its own right after.
        t.eventually {
            assertEquals(listOf(0.0, 0.0, 1.0, 1.0), t.tile("w1"))
            assertEquals(listOf(1.0, 0.0, 1.0, 1.0), t.tile("w3"))
            assertEquals(listOf(2.0, 0.0, 1.0, 1.0), t.tile("w2"))
        }
        t.assertCleanView()
    }

    @Test
    fun `screens - resizing by keys stays within the screen, the neighbour making way`() = withNewm(screens()) { t ->
        t.open("w1")
        t.open("w2")
        t.open("alone")
        t.focus("w1")
        t.onLoop { t.layout.resizeFocusedView(1, 0) }
        t.eventually { assertEquals(listOf(0.0, 0.0, 0.6, 1.0), t.tile("w1")); assertEquals(listOf(0.6, 0.0, 0.4, 1.0), t.tile("w2")) }
        repeat(8) { t.onLoop { t.layout.resizeFocusedView(1, 0) } }
        // Never past the screen; the neighbour keeps its smallest part (resize { minSize } 0.1).
        t.eventually { assertEquals(listOf(0.0, 0.0, 0.9, 1.0), t.tile("w1")); assertEquals(listOf(0.9, 0.0, 0.1, 1.0), t.tile("w2")) }
        t.onLoop { t.layout.resizeFocusedView(0, 1) }
        t.eventually { assertEquals(listOf(0.0, 0.0, 0.9, 1.0), t.tile("w1"), "taller than the screen: no") }
        // The right one wider: its left edge (the split) goes left.
        t.focus("w2")
        repeat(2) { t.onLoop { t.layout.resizeFocusedView(1, 0) } }
        t.eventually { assertEquals(listOf(0.0, 0.0, 0.7, 1.0), t.tile("w1")); assertEquals(listOf(0.7, 0.0, 0.3, 1.0), t.tile("w2")) }
        // Zoomed out it still stays within its screen.
        t.onLoop { t.layout.basicScale(1) }
        t.focus("alone")
        t.onLoop { t.layout.resizeFocusedView(1, 0) }
        t.eventually { assertEquals(listOf(1.0, 0.0, 1.0, 1.0), t.tile("alone")) }
        t.onLoop { t.layout.basicScale(-1) }
        t.eventually { assertEquals(1.0, t.view()[2]) }
        t.assertCleanView()
    }

    @Test
    fun `screens - the mouse resizes by the quarter grabbed, within the screen, not in the overview`() =
        withNewm(screens() + mapOf("gesture_bindings" to mapOf("move_resize" to listOf(null, "move-1", "swipe-2")))) { t ->
            t.open("w1")
            t.open("w2")
            val ws = t.onLoop { t.layout.workspaces.first().let { it.width to it.height } }
            // The top right quarter of w1, dragged right: w1 wider, w2 narrower.
            t.onLoop { t.caulis.simulatePointer(ws.first * 0.4, ws.second * 0.2) }
            t.fingers("swipe-2", 0.1 / 2.0, 0.0)
            t.eventually { assertTrue(t.tile("w1")[2] > 0.55, "w1 wider: ${t.tile("w1")}"); assertEquals(1.0, t.tile("w1")[2] + t.tile("w2")[2], 0.011) }
            // Far right: never past its neighbour's smallest part, nothing leaves the screen.
            t.fingers("swipe-2", 5.0, 0.0)
            t.eventually { assertEquals(listOf(0.0, 0.0, 0.9, 1.0), t.tile("w1")); assertEquals(listOf(0.9, 0.0, 0.1, 1.0), t.tile("w2")) }
            // In the overview: nothing.
            t.onLoop { t.layout.toggleOverview() }
            t.eventually { assertTrue(t.onLoop { t.wsState().isInOverview() }) }
            t.fingers("swipe-2", -2.0, 0.0)
            t.eventually { assertEquals(listOf(0.0, 0.0, 0.9, 1.0), t.tile("w1")) }
        }

    @Test
    fun `screens - the room between two windows dragged resizes them`() = withNewm(screens()) { t ->
        t.open("w1")
        t.open("w2")
        val ws = t.onLoop { t.layout.workspaces.first().let { it.width to it.height } }
        // Between the two (each is view { padding } inside its half): no window under the pointer.
        t.onLoop { t.caulis.simulatePointer(ws.first / 2.0, ws.second / 2.0) }
        t.onLoop { t.caulis.simulateButton(272, true) }
        t.settle()
        repeat(10) { t.onLoop { t.caulis.simulateMotion(-ws.first * 0.02, 0.0) }; Thread.sleep(16) }
        t.onLoop { t.caulis.simulateButton(272, false) }
        t.eventually { assertEquals(listOf(0.0, 0.0, 0.3, 1.0), t.tile("w1")); assertEquals(listOf(0.3, 0.0, 0.7, 1.0), t.tile("w2")) }
    }

    @Test
    fun `screens - the next screen below, and off gives newm's wall again`() = withNewm(screens("overflow" to "down")) { t ->
        t.open("w1")
        t.open("w2")
        t.open("w3")
        t.eventually { assertEquals(listOf(0.0, 1.0, 1.0, 1.0), t.tile("w3")); assertEquals(listOf(0.0, 1.0, 1.0), t.view()) }
        t.assertCleanView()
        t.onLoop { Config.loadConfig(mapOf("screens" to mapOf("enabled" to false))); t.layout.command("clean") }
        t.eventually {
            assertEquals(listOf(0.0, 0.0, 1.0, 1.0), t.tile("w1"))
            assertEquals(listOf(1.0, 0.0, 1.0, 1.0), t.tile("w2"))
            assertEquals(listOf(2.0, 0.0, 1.0, 1.0), t.tile("w3"))
        }
    }

    @Test
    fun `screens - a window swallowing its terminal takes its place, and the terminal comes back there`() =
        withNewm(screens() + mapOf("misc" to mapOf("swallow" to true))) { t ->
            // (The test's windows share one process: a made-up tree instead — mpv (300) started
            // by a shell (200) in foot (100).)
            val before = Swallowing.processOf to Swallowing.parentOf
            Swallowing.processOf = { v -> when (v.appId) { "foot" -> 100L; "mpv" -> 300L; else -> 999L } }
            Swallowing.parentOf = { p -> mapOf(300L to 200L, 200L to 100L, 100L to 1L, 999L to 1L)[p] }
            try {
                t.open("term", appId = "foot")
                t.eventually { assertEquals(listOf(0.0, 0.0, 1.0, 1.0), t.tile("term")) }
                val gui = t.open("player", appId = "mpv")
                fun swallowed() = t.onLoop { t.layout.state.viewState(t.layout.viewOf(t.window("term"))!!).swallowed }
                // The player in the terminal's place on its screen (the whole of it), the terminal hidden.
                t.eventually {
                    assertTrue(swallowed() != null, "swallowed")
                    assertEquals(listOf(0.0, 0.0, 1.0, 1.0), t.tile("player"))
                    assertEquals(listOf(0.0, 0.0, 1.0), t.view())
                }
                gui.close()
                t.eventually {
                    assertEquals(null, swallowed())
                    assertEquals(listOf(0.0, 0.0, 1.0, 1.0), t.tile("term"))
                    assertTrue(t.onLoop { t.layout.shownArea(t.layout.viewOf(t.window("term"))!!) } != null, "the terminal shows again")
                    assertEquals(listOf(0.0, 0.0, 1.0), t.view())
                }
                t.assertCleanView()
            } finally {
                Swallowing.processOf = before.first
                Swallowing.parentOf = before.second
            }
        }

    @Test
    fun `a swallowed terminal never gets the focus while hidden`() = withNewm(mapOf("misc" to mapOf("swallow" to true))) { t ->
        val before = Swallowing.processOf to Swallowing.parentOf
        Swallowing.processOf = { v -> when (v.appId) { "foot" -> 100L; "mpv" -> 300L; else -> 999L } }
        Swallowing.parentOf = { p -> mapOf(300L to 200L, 200L to 100L, 100L to 1L, 999L to 1L)[p] }
        try {
            t.open("term", appId = "foot")
            val gui = t.open("player", appId = "mpv")
            fun swallowed() = t.onLoop { t.layout.state.viewState(t.layout.viewOf(t.window("term"))!!).swallowed }
            t.eventually { assertTrue(swallowed() != null, "swallowed") }
            t.open("w3")
            t.focus("w3")
            // Towards the player (which has the terminal's place, the terminal behind it): the player.
            val (p, w) = t.tile("player") to t.tile("w3")
            val dx = if (p[0] < w[0]) -1 else 1
            val dy = if (p[1] < w[1]) -1 else if (p[1] > w[1]) 1 else 0
            t.onLoop { if (dy != 0) t.layout.move(0, dy) else t.layout.move(dx, 0) }
            t.eventually { assertEquals("player", t.focused()) }
            // In turn: never the hidden terminal.
            repeat(3) {
                t.onLoop { t.layout.moveNextView() }
                t.eventually { assertTrue(t.focused() in setOf("player", "w3"), "focused: ${t.focused()}") }
            }
            // The player moved: the terminal behind it goes along, and comes back there.
            t.focus("player")
            t.onLoop { t.layout.moveFocusedView(0, 1) }
            t.eventually { assertEquals(t.tile("player"), t.tile("term"), "behind the player") }
            val place = t.tile("player")
            gui.close()
            t.eventually { assertEquals(place, t.tile("term"), "back where the player was") }
        } finally {
            Swallowing.processOf = before.first
            Swallowing.parentOf = before.second
        }
    }

    @Test
    fun `the window swallowing a terminal is the one started from it, not the other way`() {
        val parents = mapOf(300L to 200L, 200L to 100L, 100L to 1L, 400L to 1L)
        val windows = listOf("foot" to 100L, "other foot of the same server" to 100L, "firefox" to 400L)
        // mpv (300), started by a shell (200) in foot (100): foot's window.
        assertEquals("foot", Swallowing.launcherOf(300L, windows, null, parents::get))
        // The window that had the focus when it opened, of several of that process.
        assertEquals("other foot of the same server", Swallowing.launcherOf(300L, windows, "other foot of the same server", parents::get))
        // foot (100) opening: it was not started from mpv (nor from a window of its own process).
        assertEquals(null, Swallowing.launcherOf(100L, windows + ("mpv" to 300L), null, parents::get))
        assertEquals(null, Swallowing.launcherOf(400L, windows, null, parents::get))
    }

    private fun bar(): Map<String, Any?> = mapOf("panels" to mapOf("top_bar" to mapOf("native" to mapOf("enabled" to true))))

    // --- windows { newPlacement = "compact" } (the default)

    @Test
    fun `compact - four in a square, the bottom left focused - the new one goes beside it, nothing moves, no hole above`() = withNewm(emptyMap()) { t ->
        for (n in 1..4) t.open("w$n")
        val tiles = (1..4).associate { "w$it" to t.tile("w$it") }
        // Four windows opened one after the other: a 2 x 2 block, not a row.
        assertEquals(setOf(0.0, 1.0), tiles.values.map { it[0] }.toSet(), "two columns: $tiles")
        assertEquals(setOf(0.0, 1.0), tiles.values.map { it[1] }.toSet(), "two rows: $tiles")
        val bottomLeft = tiles.entries.first { it.value[0] == 0.0 && it.value[1] == 1.0 }.key
        t.focus(bottomLeft)
        val view = t.view()
        t.open("w5")
        t.eventually {
            for ((k, v) in tiles) assertEquals(v, t.tile(k), "$k stays where it was")
            val w5 = t.tile("w5")
            // Below it or left of it (right-of-focused put it right of it and moved the right column on: a hole above it).
            assertTrue(w5 == listOf(0.0, 2.0, 1.0, 1.0) || w5 == listOf(-1.0, 1.0, 1.0, 1.0), "beside the focused one: $w5")
            assertEquals("w5", t.focused())
        }
        val moved = t.view().zip(view).sumOf { (a, b) -> Math.abs(a - b) }
        assertTrue(moved <= 1.0 + 1e-6, "the view moves one tile at most: ${t.view()} from $view")
    }

    @Test
    fun `compact - a row of three, the middle one focused - the new one opens next to it, nothing moves`() = withNewm(RIGHT_OF_FOCUSED) { t ->
        t.open("w1")
        t.open("w2")
        t.open("w3")
        t.eventually { assertEquals(listOf(2.0, 0.0, 1.0, 1.0), t.tile("w3")) }
        t.onLoop { Config.loadConfig(mapOf("windows" to mapOf("new_placement" to "compact"), "screens" to mapOf("enabled" to false))) }
        t.focus("w2")
        val view = t.view()
        t.open("w4")
        t.eventually {
            assertEquals(listOf(0.0, 0.0, 1.0, 1.0), t.tile("w1"))
            assertEquals(listOf(1.0, 0.0, 1.0, 1.0), t.tile("w2"))
            assertEquals(listOf(2.0, 0.0, 1.0, 1.0), t.tile("w3"))
            val w4 = t.tile("w4")
            assertTrue(w4 == listOf(1.0, 1.0, 1.0, 1.0) || w4 == listOf(1.0, -1.0, 1.0, 1.0), "below or above the focused one: $w4")
        }
        val moved = t.view().zip(view).sumOf { (a, b) -> Math.abs(a - b) }
        assertTrue(moved <= 1.0 + 1e-6, "the view moves one tile at most: ${t.view()} from $view")
    }

    @Test
    fun `compact on screens - the focused screen full, the next one has room - the new window goes there, no screen moves on`() = withNewm(screens()) { t ->
        t.open("w1")
        t.open("w2")
        t.open("w3")
        t.eventually { assertEquals(listOf(1.0, 0.0, 1.0, 1.0), t.tile("w3")) }
        t.focus("w1")
        t.open("w4")
        t.eventually {
            assertEquals(listOf(0.0, 0.0, 0.5, 1.0), t.tile("w1"))
            assertEquals(listOf(0.5, 0.0, 0.5, 1.0), t.tile("w2"))
            assertEquals(listOf(1.0, 0.0, 0.5, 1.0), t.tile("w4"))
            assertEquals(listOf(1.5, 0.0, 0.5, 1.0), t.tile("w3"))
            assertEquals(listOf(1.0, 0.0, 1.0), t.view(), "one screen on")
        }
        t.assertCleanView()
    }

    // --- special workspaces sent to by a rule

    @Test
    fun `a Caulis rule sends spotify into the special workspace music - never on the wall, shown when toggled`() =
        withNewm(emptyMap(), setup = { addRule { appId("spotify"); specialWorkspace = "music" } }) { t -> checkMusic(t, "Spotify") }

    @Test
    fun `a view rule sends spotify into the special workspace music - never on the wall, shown when toggled`() = withNewm(
        mapOf("view" to mapOf("rules" to { v: View -> if (v.appId.equals("spotify", ignoreCase = true)) mapOf("special_workspace" to "music") else null })),
    ) { t -> checkMusic(t, "spotify") }

    // A window with the app id [appId] opens: into "music", hidden; the wall's window and the view stay; toggled: shown.
    private fun checkMusic(t: T, appId: String) {
        t.open("w1")
        val tile = t.tile("w1")
        val view = t.view()
        t.client.window("player", appId, null).draw()
        // (Watched while it opens: never a view of newm's, the wall never moves.)
        val deadline = System.nanoTime() + 1_500_000_000
        while (System.nanoTime() < deadline) {
            t.onLoop {
                assertTrue(t.layout.viewsNow().none { it.title == "player" }, "the player never on the wall")
            }
            assertEquals(tile, t.tile("w1"))
            t.client.roundtrips(1)
        }
        t.eventually {
            t.onLoop {
                val w = t.caulis.desktop.allWindows.first { it.title == "player" }
                val pad = t.caulis.desktop.scratchpadOf(w)
                assertEquals("music", pad?.name, "in the special workspace music")
                assertFalse(pad!!.shown, "hidden until shown")
            }
        }
        assertEquals(tile, t.tile("w1"))
        assertEquals(view, t.view())
        assertEquals("w1", t.focused(), "the focus stays")
        t.onLoop { t.layout.toggleSpecialWorkspace("music") }
        t.eventually { t.onLoop { assertTrue(t.caulis.desktop.scratchpadOf(t.caulis.desktop.allWindows.first { it.title == "player" })!!.shown, "shown") } }
        assertEquals(tile, t.tile("w1"))
    }

    // --- special workspaces by keys and the mouse

    // The window [title] has a view of newm's on the wall now (placed in the layout).
    private fun T.onWall(title: String): Boolean = onLoop {
        val w = caulis.desktop.allWindows.firstOrNull { it.title == title } ?: return@onLoop false
        caulis.desktop.scratchpadOf(w) == null && layout.viewOf(w)?.let { layout.state.findViewOrNull(it) } != null
    }

    private fun T.specialShown(name: String): Boolean = onLoop { caulis.desktop.monitors.any { caulis.desktop.shownSpecialOn(it)?.name == name } }

    private fun T.inSpecial(title: String): String? = onLoop {
        caulis.desktop.allWindows.firstOrNull { it.title == title }?.let { caulis.desktop.scratchpadOf(it)?.name }
    }

    @Test
    fun `a window moved into the special workspace by mistake comes back out by keys - a view again, compact, focused`() = withNewm(emptyMap()) { t ->
        t.open("w1")
        t.open("w2")
        t.open("chat")
        val w1 = t.tile("w1")
        val w2 = t.tile("w2")
        t.onLoop { t.layout.moveToSpecialWorkspace("music") }
        t.eventually {
            assertEquals("music", t.inSpecial("chat"))
            assertFalse(t.onWall("chat"), "its view left the wall")
            assertEquals("chat", t.focused(), "it keeps the focus in there")
        }
        t.settle()
        t.onLoop { t.layout.moveOutOfSpecialWorkspace() }
        t.eventually {
            assertEquals(null, t.inSpecial("chat"))
            assertTrue(t.onWall("chat"), "a view on the wall again")
            assertEquals("chat", t.focused())
            assertFalse(t.specialShown("music"), "the empty special workspace hides")
        }
        t.settle()
        assertEquals(w1, t.tile("w1"), "the others stay")
        assertEquals(w2, t.tile("w2"), "the others stay")
        val chat = t.tile("chat")
        assertEquals(listOf(1.0, 1.0), chat.drop(2), "one tile: $chat")
        assertTrue(chat.take(2) != w1.take(2) && chat.take(2) != w2.take(2), "a free tile: $chat")
        assertTrue(Math.abs(chat[0] - w2[0]) + Math.abs(chat[1] - w2[1]) <= 1.0 + 1e-6, "next to the window focused before (w2 at $w2): $chat")
    }

    @Test
    fun `from-special-workspace with the special workspace hidden brings its last focused window back`() = withNewm(emptyMap()) { t ->
        t.open("w1")
        t.open("chat")
        t.onLoop { t.layout.moveToSpecialWorkspace("music") }
        t.eventually { assertEquals("music", t.inSpecial("chat")) }
        t.onLoop { t.layout.toggleSpecialWorkspace("music") }
        t.eventually { assertEquals("w1", t.focused()) }
        t.settle()
        t.onLoop { t.layout.moveOutOfSpecialWorkspace() }
        t.eventually {
            assertEquals(null, t.inSpecial("chat"))
            assertTrue(t.onWall("chat"))
            assertEquals("chat", t.focused())
        }
    }

    @Test
    fun `special-workspace from the overview leaves the overview and shows it`() = withNewm(emptyMap()) { t ->
        t.open("w1")
        t.open("player")
        t.onLoop { t.layout.moveToSpecialWorkspace("music", silent = true) }
        t.eventually { assertEquals("music", t.inSpecial("player")) }
        t.settle()
        t.onLoop { t.layout.toggleOverview() }
        t.eventually { assertTrue(t.onLoop { t.layout.overviewShown() }) }
        t.settle()
        t.onLoop { t.layout.toggleSpecialWorkspace("music") }
        t.eventually {
            assertFalse(t.onLoop { t.layout.overviewShown() }, "the overview ends")
            assertTrue(t.specialShown("music"), "shown")
        }
        // (And it stays: nothing hides it again a moment later.)
        Thread.sleep(500)
        t.client.roundtrips(2)
        assertTrue(t.specialShown("music"), "still shown")
        assertEquals("player", t.focused())
    }

    @Test
    fun `Super+drag a window of the shown special workspace onto the wall brings it out - inside, back to its tile`() =
        withNewm(mapOf("gesture_bindings" to mapOf("move_resize" to listOf(null, "move-1", "swipe-2")))) { t ->
            t.open("w1")
            t.open("chat")
            t.onLoop { t.layout.moveToSpecialWorkspace("music") }
            t.eventually { assertTrue(t.specialShown("music")) }
            t.settle()
            // Drags the window by (dx, dy) pixels from its middle; returns where it was meanwhile.
            fun drag(dx: Double, dy: Double): Pair<Int, Int> {
                val (x, y) = t.onLoop {
                    val w = t.caulis.desktop.allWindows.first { it.title == "chat" }
                    val cx = w.x + w.visibleRect.width / 2.0
                    val cy = w.y + w.visibleRect.height / 2.0
                    t.caulis.desktop.pointerMoved(0, cx, cy)
                    cx to cy
                }
                val g = NewmGesture("move-1")
                assertTrue(t.onLoop { t.layout.gesture(g) }, "the drag is taken")
                val scale = t.onLoop { t.layout.dragScale() }
                val before = t.onLoop { t.caulis.desktop.allWindows.first { it.title == "chat" }.let { it.x to it.y } }
                t.onLoop { g.update(mapOf("delta_x" to dx / scale, "delta_y" to dy / scale, "delta2_s" to 0.0)) }
                // (The pointer goes along, as it would.)
                t.onLoop { t.caulis.desktop.pointerMoved(0, x + dx, y + dy) }
                val during = t.onLoop { t.caulis.desktop.allWindows.first { it.title == "chat" }.let { it.x to it.y } }
                assertEquals(before.first + Math.round(dx).toInt(), during.first, "it follows the pointer")
                t.onLoop { g.terminate() }
                t.settle()
                return during
            }
            // Within the special workspace: back to its tile.
            val tile = t.onLoop { t.caulis.desktop.allWindows.first { it.title == "chat" }.let { it.x to it.y } }
            drag(30.0, 20.0)
            t.eventually {
                assertEquals("music", t.inSpecial("chat"))
                assertEquals(tile, t.onLoop { t.caulis.desktop.allWindows.first { it.title == "chat" }.let { it.x to it.y } }, "back to its tile")
            }
            // Onto the dimmed wall left of it: out, a view of newm's, focused.
            val (left, middle) = t.onLoop {
                val area = t.caulis.desktop.specialWorkspaceArea(t.caulis.desktop.focusedMonitor)!!
                val w = t.caulis.desktop.allWindows.first { it.title == "chat" }
                area.x to w.x + w.visibleRect.width / 2.0
            }
            drag(left - 10.0 - middle, 0.0)
            t.eventually {
                assertEquals(null, t.inSpecial("chat"))
                assertTrue(t.onWall("chat"), "a view on the wall again")
                assertEquals("chat", t.focused())
            }
        }

    @Test
    fun `windows in a special workspace look like the wall's - corners, focus border, padding, size`() = withNewm(
        mapOf("view" to mapOf("corner_radius" to 20.0, "padding" to 10.0), "focus" to mapOf("width" to 3.0), "special_workspace" to mapOf("gap" to 50, "size" to 0.8)),
    ) { t ->
        t.open("w1")
        t.open("chat")
        t.onLoop { t.layout.moveToSpecialWorkspace("music") }
        t.eventually { assertTrue(t.specialShown("music")) }
        t.settle()
        t.eventually {
            t.onLoop {
                val desktop = t.caulis.desktop
                val w = desktop.allWindows.first { it.title == "chat" }
                val fx = desktop.windowEffects(w)
                assertEquals(20, fx.cornerRadius, "view { cornerRadius }")
                assertEquals(3, fx.border?.width, "the focus border, as wide as focus { width }")
                // One window: the special workspace's area (gap 50, 0.8 of the rest, centred) less the padding around it.
                val monitor = desktop.focusedMonitor.usableArea
                val area = desktop.specialWorkspaceArea(desktop.focusedMonitor)!!
                assertEquals(Math.round((monitor.width - 100) * 0.8).toInt(), area.width, "size 0.8 inside the gap: $area of $monitor")
                assertEquals(area.x + 10, w.x, "padding left")
                assertEquals(area.y + 10, w.y, "padding above")
                assertEquals(area.width - 20 to area.height - 20, w.configuredSize, "padding all round")
                // The wall's windows are not touched (newm draws those).
                assertEquals(0, desktop.windowEffects(t.window("w1")).cornerRadius)
            }
        }
        // specialWorkspace { cornerRadius } of its own.
        t.onLoop {
            Config.loadConfig(mapOf("view" to mapOf("corner_radius" to 20.0, "padding" to 10.0), "special_workspace" to mapOf("corner_radius" to 4.0), "screens" to mapOf("enabled" to false)))
            t.layout.updateConfig()
        }
        t.eventually { assertEquals(4, t.onLoop { t.caulis.desktop.windowEffects(t.caulis.desktop.allWindows.first { it.title == "chat" }).cornerRadius }) }
    }

    // --- the harness

    private class T(val caulis: Caulis, val layout: Layout, val client: Client) {
        fun <X> onLoop(action: () -> X): X {
            val result = CompletableFuture<X>()
            caulis.post { runCatching(action).fold(result::complete, result::completeExceptionally) }
            return try {
                result.get(10, TimeUnit.SECONDS)
            } catch (e: java.util.concurrent.ExecutionException) {
                throw e.cause ?: e
            }
        }

        fun window(title: String) = caulis.desktop.windows.first { it.title == title }

        fun open(title: String, appId: String = title, parent: Client.Window? = null): Client.Window {
            val w = client.window(title, appId, parent)
            w.draw()
            eventually { assertTrue(onLoop { caulis.desktop.windows.any { it.title == title } && layout.viewOf(window(title))?.let { layout.state.findViewOrNull(it) } != null }) }
            settle()
            return w
        }

        fun focus(title: String) {
            onLoop { layout.focusView(layout.viewOf(window(title))!!) }
            eventually { assertEquals(title, focused()) }
            settle()
        }

        fun focused(): String? = onLoop { caulis.desktop.focusedWindow?.title }

        /** The monitor's bar zone at the top (the room the bar keeps). */
        fun barZone(): dev.caulis.geometry.Rect = onLoop {
            val ws = layout.workspaces.first()
            dev.caulis.geometry.Rect(ws.posX, ws.posY, ws.width, wsState().topExcluded.toInt().coerceAtLeast(1))
        }

        /** On screens: only the windows of the screen in view show, and only within it (the bars' room free). */
        fun assertCleanView() = eventually {
            onLoop {
                val ws = layout.workspaces.first()
                val s = wsState()
                val usable = dev.caulis.geometry.Rect(
                    (ws.posX + s.leftExcluded).toInt(), (ws.posY + s.topExcluded).toInt(), s.usableWidth().toInt(), s.usableHeight().toInt(),
                )
                val page = Screens.pageAt(s.i + .5, s.j + .5)
                for (v in layout.tiles(ws)) {
                    val shown = layout.shownArea(v) ?: continue
                    val vs = s.viewState(v)
                    if (vs.page == page) assertTrue(usable.intersect(shown) == shown, "${v.title} of the screen in view, within it: $shown in $usable")
                    else assertEquals(null, shown.intersect(dev.caulis.geometry.Rect(ws.posX, ws.posY, ws.width, ws.height)), "${v.title} of screen ${vs.page + 1} not in view (screen ${page + 1})")
                }
            }
        }

        /** No window shows in the bar's room. */
        fun assertBarFree() = eventually {
            val zone = barZone()
            onLoop { for (v in layout.tiles()) assertEquals(null, layout.shownArea(v)?.intersect(zone), "${v.title} under the bar") }
        }

        fun wsState() = layout.state.workspaceState(layout.workspaces.first())

        /** A tile: i, j, w, h (rounded to a hundredth). */
        fun tile(title: String): List<Double> = onLoop {
            val s = layout.state.viewState(layout.viewOf(window(title))!!)
            listOf(s.i, s.j, s.w, s.h).map { Math.round(it * 100) / 100.0 }
        }

        /** The view: i, j, size. */
        fun view(): List<Double> = onLoop { wsState().let { listOf(it.i, it.j, it.size).map { x -> Math.round(x * 100) / 100.0 } } }

        // Fingers moving to (dx, dy) slowly, held a moment (the low-pass catches up), then lifted.
        fun fingers(kind: String, dx: Double, dy: Double) {
            val g = NewmGesture(kind)
            assertTrue(onLoop { layout.gesture(g) }, "the gesture is taken")
            for (n in 1..30) {
                val f = n / 30.0
                onLoop { g.update(mapOf("delta_x" to dx * f, "delta_y" to dy * f, "delta2_s" to 0.0)) }
                Thread.sleep(16)
            }
            Thread.sleep(600)
            onLoop { g.terminate() }
        }

        // Nothing moves any more (the layout's animations done).
        fun settle() {
            Thread.sleep(150)
            client.roundtrips(3)
        }

        fun eventually(check: () -> Unit) {
            val deadline = System.nanoTime() + 8_000_000_000
            while (true) {
                try {
                    check()
                    return
                } catch (e: AssertionError) {
                    if (System.nanoTime() > deadline) throw e
                    client.roundtrips(1)
                    Thread.sleep(30)
                }
            }
        }
    }

    private fun withNewm(settings: Map<String, Any?>, setup: Caulis.() -> Unit = {}, test: (T) -> Unit) {
        assumeTrue(Files.isDirectory(Path.of("/dev/dri")), "no GPU")
        val dir = Files.createTempDirectory("newm-layout-test")
        // (The tests of newm's own wall without screens; those of screens say so.)
        Config.loadConfig(if ("screens" in settings) settings else settings + ("screens" to mapOf("enabled" to false)))
        val caulis = Caulis().apply {
            useConfigFile = false
            backend = Caulis.Backend.HEADLESS
            socketDirectory = dir
            exitAfterSeconds = 90 // (never a hanging test run)
            keyMode(LOCKED_MODE) {}
            setup()
        }
        val layout = Layout(caulis)
        val started = CompletableFuture<Unit>()
        caulis.onStart { started.complete(Unit) }
        val thread = Thread.ofPlatform().start { caulis.run() }
        try {
            started.get(20, TimeUnit.SECONDS)
            val socket = Files.list(dir).use { files -> files.filter { it.fileName.toString().startsWith("wayland-") && !it.toString().endsWith(".lock") }.findFirst().get() }
            Client(Connection.connect(socket)).use { client -> test(T(caulis, layout, client)) }
        } finally {
            runCatching { caulis.post { quit() } }
            thread.join(10_000)
            dir.toFile().deleteRecursively()
            Config.loadConfig(emptyMap())
        }
    }

    /** Windows with plain pictures (a title, an app id, a parent). */
    private class Client(val connection: Connection) : AutoCloseable {
        private val names = HashMap<String, Int>()
        private val registry = connection.display.getRegistry().also { registry ->
            registry.events = object : WlRegistry.Events {
                override fun global(proxy: WlRegistry, name: Int, `interface`: String, versionArg: Int) {
                    names[`interface`] = name
                }
            }
            connection.roundtrip()
        }
        private val compositor = registry.bind(names.getValue("wl_compositor"), WlCompositor, 4)
        private val shm = registry.bind(names.getValue("wl_shm"), WlShm, 1)
        private val wmBase = registry.bind(names.getValue("xdg_wm_base"), XdgWmBase, 2).also { b ->
            b.events = object : XdgWmBase.Events {
                override fun ping(proxy: XdgWmBase, serial: Int) = proxy.pong(serial)
            }
        }
        private val memory = SharedMemory.create("pictures", 512L * 512 * 4)

        inner class Window(private val surface: WlSurface, val toplevel: XdgToplevel, private val xdgSurface: XdgSurface) {
            @Volatile var width = 0
            @Volatile var height = 0
            @Volatile var closed = false

            fun draw() {
                val w = width.takeIf { it in 1..512 } ?: 64
                val h = height.takeIf { it in 1..512 } ?: 64
                val pool = shm.createPool(memory.fd, 512 * 512 * 4)
                surface.attach(pool.createBuffer(0, w, h, w * 4, WlShm.Format.XRGB8888), 0, 0)
                surface.damageBuffer(0, 0, w, h)
                pool.destroy()
                surface.commit()
                connection.roundtrip()
            }

            fun close() {
                closed = true
                toplevel.destroy()
                xdgSurface.destroy()
                surface.destroy()
                connection.roundtrip()
            }
        }

        fun window(title: String, appId: String, parent: Window?): Window {
            val surface = compositor.createSurface()
            val xdgSurface = wmBase.getXdgSurface(surface)
            val toplevel = xdgSurface.getToplevel()
            toplevel.setTitle(title)
            toplevel.setAppId(appId)
            parent?.let { toplevel.setParent(it.toplevel) }
            val window = Window(surface, toplevel, xdgSurface)
            toplevel.events = object : XdgToplevel.Events {
                override fun configure(proxy: XdgToplevel, width: Int, height: Int, states: ByteArray) {
                    window.width = width
                    window.height = height
                }
            }
            xdgSurface.events = object : XdgSurface.Events {
                override fun configure(proxy: XdgSurface, serial: Int) {
                    if (!window.closed) proxy.ackConfigure(serial)
                }
            }
            surface.commit()
            connection.roundtrip()
            return window
        }

        fun roundtrips(n: Int) = repeat(n) {
            connection.roundtrip()
            Thread.sleep(20)
        }

        override fun close() {
            memory.close()
            connection.close()
        }
    }
}
