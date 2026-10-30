package newm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which window is a panel program's: only the panel program running now. */
class PanelsLauncherTest {

    @Test
    fun `a panel program that ended is no panel any more`() {
        Config.loadConfig(mapOf("panels" to mapOf("lock" to mapOf("cmd" to "sleep 30"))))
        val started = ArrayList<Process>()
        val launcher = PanelsLauncher({ _, _ -> AutoCloseable {} }) { cmd, _ -> ProcessBuilder(cmd).start().also { started += it } }
        try {
            launcher.start()
            val lock = started.single()
            val pid = lock.pid().toInt()
            assertEquals("lock", launcher.panelForPid(pid))
            // It ended (its number may go to another program): its windows are no lock screen.
            lock.destroyForcibly().waitFor()
            assertNull(launcher.panelForPid(pid))
        } finally {
            started.forEach { it.destroyForcibly() }
            Config.loadConfig(emptyMap())
        }
    }
}
