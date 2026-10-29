package newm

import kotlin.test.Test
import kotlin.test.assertEquals

/** The right island: the quick actions' button and the bell are there also in a list of one's own. */
class BarModulesTest {

    @Test
    fun `a right list of one's own from before still gets the quick actions and the bell`() {
        // (The owner's config.kts, written by an older template.)
        val own = listOf("tray", "layout", "bluetooth", "network", "volume", "battery", "power")
        assertEquals(
            listOf("tray", "layout", "bluetooth", "network", "volume", "battery", "notifications", "quick", "power"),
            rightModules(own, listOf("window"), listOf("clock"), quick = true, bell = true),
        )
    }

    @Test
    fun `placed elsewhere or turned off, not added`() {
        val own = listOf("volume", "power")
        assertEquals(own, rightModules(own, listOf("quick"), emptyList(), quick = true, bell = false))
        assertEquals(own, rightModules(own, emptyList(), emptyList(), quick = false, bell = false))
        assertEquals(listOf("notifications", "quick", "volume", "power"), rightModules(listOf("quick", "volume", "power"), emptyList(), emptyList(), quick = true, bell = true))
        assertEquals(listOf("volume", "notifications", "quick"), rightModules(listOf("volume"), emptyList(), emptyList(), quick = true, bell = true))
    }
}
