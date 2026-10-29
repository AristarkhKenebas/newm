package newm.bar

import dev.caulis.wm.Caulis.Backend
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Another notification daemon (mako, dunst) is closed for newm's own only when newm is the
 * session's compositor: a nested or headless newm (tests, trying it out) on the user's bus
 * never closes the live session's daemon (it closed the owner's mako once).
 */
class NotificationTakeOverTest {
    private fun decide(
        mode: String = "stale",
        backend: Backend? = Backend.SCREEN,
        startedBefore: Boolean = true,
        otherDisplay: String? = "wayland-1",
        ourDisplay: String? = "wayland-0",
    ) = takeOverDecision(mode, backend, alive = true, sameUser = true, startedBefore = startedBefore, otherDisplay = otherDisplay, ourDisplay = ourDisplay)

    @Test
    fun `headless or nested newm never closes another daemon, whatever takeOver says`() {
        for (backend in listOf(Backend.HEADLESS, Backend.NESTED, null)) {
            for (mode in listOf("stale", "always", "never")) {
                assertFalse(decide(mode = mode, backend = backend), "$mode on $backend")
            }
        }
    }

    @Test
    fun `on the screen a stale daemon is closed, a live one of this session is not`() {
        assertTrue(decide(startedBefore = true))
        assertTrue(decide(startedBefore = false, otherDisplay = "wayland-1", ourDisplay = "wayland-0"))
        assertFalse(decide(startedBefore = false, otherDisplay = "wayland-0", ourDisplay = "wayland-0"))
        assertTrue(decide(mode = "always", startedBefore = false, otherDisplay = null))
        assertFalse(decide(mode = "never"))
    }

    @Test
    fun `another user's daemon or one already gone is left alone`() {
        assertFalse(takeOverDecision("stale", Backend.SCREEN, alive = true, sameUser = false, startedBefore = true, otherDisplay = null, ourDisplay = null))
        assertFalse(takeOverDecision("always", Backend.SCREEN, alive = false, sameUser = true, startedBefore = true, otherDisplay = null, ourDisplay = null))
    }
}
