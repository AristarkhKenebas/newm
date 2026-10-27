package newm.bar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A module's command: its output read without a thread left waiting on it. */
class CommandOutputTest {

    @Test
    fun `a program left running in the background keeps nothing waiting`() {
        val started = System.nanoTime()
        // (sleep keeps the pipe open after sh ended: reading till the pipe closed waited 3 s, a thread for ever.)
        assertEquals("hi\n", commandOutput("sleep 3 & echo hi"))
        assertTrue((System.nanoTime() - started) / 1_000_000 < 2000, "read as soon as sh ended")
    }

    @Test
    fun `a command taking too long is stopped with what it started`() {
        val started = System.nanoTime()
        assertNull(commandOutput("sh -c 'sleep 30'; echo never", seconds = 1))
        assertTrue((System.nanoTime() - started) / 1_000_000 < 3000)
        assertEquals("ok\n", commandOutput("printf 'ok\\n'"))
        assertNull(commandOutput("exit 3"))
        assertEquals("", commandOutput("exit 3", needSuccess = false))
    }
}
