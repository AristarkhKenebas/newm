package newm

import kotlin.test.Test
import kotlin.test.assertTrue

/** The bar's system texts are made on the compositor's thread: none of them may wait for a program. */
class SystemInfoTest {

    @Test
    fun `the Wi-Fi's name is asked on a thread of its own, the bar never waits for nmcli`() {
        val before = SystemInfo.wifiCommand
        // (NetworkManager busy: nmcli takes seconds.)
        SystemInfo.wifiCommand = listOf("sh", "-c", "sleep 2; echo yes:Slow")
        try {
            val start = System.nanoTime()
            SystemInfo.wifi()
            val millis = (System.nanoTime() - start) / 1_000_000
            assertTrue(millis < 500, "answered at once from what it knew ($millis ms)")
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!SystemInfo.wifi().startsWith("Slow") && System.nanoTime() < deadline) Thread.sleep(100)
            assertTrue(SystemInfo.wifi().startsWith("Slow"), "the name once nmcli answered: ${SystemInfo.wifi()}")
        } finally {
            SystemInfo.wifiCommand = before
        }
    }
}
