package newm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A bar on several monitors: on each or on the primary one, and a monitor's own modules. */
class BarOutputsTest {

    @Test
    fun `on each monitor, or on the primary one`() {
        val monitors = listOf("eDP-1", "HDMI-A-1")
        assertTrue(barShownOn("all", "", "HDMI-A-1", monitors))
        assertTrue(barShownOn("primary", "", "eDP-1", monitors))
        assertFalse(barShownOn("primary", "", "HDMI-A-1", monitors))
        assertTrue(barShownOn("primary", "HDMI-A-1", "HDMI-A-1", monitors))
        assertFalse(barShownOn("Primary", "HDMI-A-1", "eDP-1", monitors))
    }

    @Test
    fun `a monitor's own lists`() {
        val per = mapOf("HDMI-A-1" to mapOf("left" to listOf("window"), "right" to listOf("clock")))
        assertEquals(listOf("window"), perOutputList(per, "HDMI-A-1", "left"))
        assertNull(perOutputList(per, "HDMI-A-1", "center"))
        assertNull(perOutputList(per, "eDP-1", "left"))
    }
}
