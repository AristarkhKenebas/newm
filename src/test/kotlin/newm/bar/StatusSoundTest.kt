package newm.bar

import dev.caulis.log.Log
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The bar's sound with fake wpctl and pactl (in a PATH of their own): a failing tool shows the
 * sound as unavailable — never hidden — and it comes back; `pactl subscribe` that ends (the
 * sound server restarted) is started again.
 */
class StatusSoundTest {

    private val dir: Path = Files.createTempDirectory("newm-sound-test")
    private val bin: Path = Files.createDirectories(dir.resolve("bin"))
    private val wpctlSays = dir.resolve("wpctl-says")
    private val starts = dir.resolve("subscribe-starts")
    private val stop = dir.resolve("subscribe-stop")
    private val lines = CopyOnWriteArrayList<String>()
    private var status: Status? = null

    init {
        Log.listener = { lines += it }
        Log.setLevel("bar", 2)
        tool("wpctl", """
            case "$(cat '$wpctlSays')" in
              ok) echo "Volume: 0.45" ;;
              muted) echo "Volume: 0.30 [MUTED]" ;;
              stamped) echo "E $(date +%H:%M:%S.%N)             mod.rt ../pipewire/src/modules/module-rt.c:295:pw_rtkit_bus_get: Failed to connect to system bus"; exit 2 ;;
              *) echo "Could not connect to PipeWire"; exit 1 ;;
            esac
        """)
        tool("pactl", """
            if [ "$1" = subscribe ]; then
              echo start >> '$starts'
              echo "Event 'change' on sink #56"
              while [ ! -e '$stop' ]; do sleep 0.05; done
              rm -f '$stop'
              exit 0
            fi
            echo "Connection failure: Connection refused"; exit 1
        """)
    }

    private fun tool(name: String, body: String) {
        val file = bin.resolve(name)
        Files.writeString(file, "#!/bin/sh\nPATH=/usr/bin:/bin\n" + body.trimIndent() + "\n")
        file.toFile().setExecutable(true)
    }

    private fun waitFor(what: String, seconds: Long = 10, condition: () -> Boolean) {
        val deadline = System.nanoTime() + seconds * 1_000_000_000
        while (!condition()) {
            check(System.nanoTime() < deadline) { "waited in vain for $what; the log:\n" + lines.joinToString("\n") }
            Thread.sleep(20)
        }
    }

    private fun started(): Status = Status(1) { it() }.also { s ->
        s.toolPath = bin.toString()
        status = s
        s.start()
    }

    @AfterTest
    fun cleanUp() {
        status?.close()
        Log.listener = null
        Log.clearLevel("bar")
        dir.toFile().deleteRecursively()
    }

    @Test
    fun `a failing tool shows the sound unavailable, and it comes back`() {
        Files.writeString(wpctlSays, "fail")
        val s = started()
        waitFor("unavailable") { s.volumeUnavailable != null }
        assertNull(s.volume)
        assertTrue("wpctl ended with 1: Could not connect to PipeWire" in s.volumeUnavailable!!, s.volumeUnavailable)
        assertTrue("pactl ended with 1" in s.volumeUnavailable!!, s.volumeUnavailable)
        assertTrue(lines.any { "The volume cannot be read" in it })
        // The sound server is back: soon read again (the first retries come quickly).
        Files.writeString(wpctlSays, "ok")
        waitFor("the volume back", seconds = 5) { s.volume != null }
        assertEquals(Volume(45, false), s.volume)
        assertNull(s.volumeUnavailable)
        assertTrue(lines.any { "The volume can be read again" in it })
    }

    @Test
    fun `pactl subscribe that ends is started again`() {
        Files.writeString(wpctlSays, "ok")
        val s = started()
        waitFor("the first subscribe") { Files.exists(starts) && Files.readAllLines(starts).size == 1 }
        waitFor("the volume") { s.volume != null }
        // The sound server restarts: pactl subscribe ends; meanwhile the volume cannot be read.
        Files.writeString(wpctlSays, "fail")
        Files.writeString(stop, "")
        waitFor("unavailable while it restarts") { s.volumeUnavailable != null }
        Files.writeString(wpctlSays, "muted")
        waitFor("subscribe started again") { Files.readAllLines(starts).size == 2 }
        waitFor("the volume read after it") { s.volume == Volume(30, true) }
        assertNull(s.volumeUnavailable)
        assertTrue(lines.any { "pactl subscribe ended" in it })
        assertTrue(lines.any { "follows the sound server again" in it })
    }

    @Test
    fun `no sound tools at all, nothing shown, nothing said to be unavailable`() {
        Files.delete(bin.resolve("wpctl"))
        Files.delete(bin.resolve("pactl"))
        val s = started()
        waitFor("a reading") { lines.any { "The volume cannot be read" in it } }
        Thread.sleep(100)
        assertNull(s.volume)
        assertNull(s.volumeUnavailable)
        assertNotNull(lines.firstOrNull { "wpctl is not installed" in it })
    }

    @Test
    fun `the same failure with the tool's own times in it is warned once`() {
        Log.setLevel("bar", 3)
        Files.writeString(wpctlSays, "stamped")
        val s = started()
        waitFor("three failed readings") { lines.count { "still cannot be read" in it } >= 2 }
        assertEquals(1, lines.count { "The volume cannot be read (" in it }, lines.joinToString("\n"))
        assertTrue("wpctl ended with 2: Failed to connect to system bus" in s.volumeUnavailable.orEmpty(), s.volumeUnavailable)
    }

    @Test
    fun `a failure's reason without times, places and numbers`() {
        assertEquals(
            "wpctl ended with 2: Failed to connect to system bus; pactl ended with 1: Connection failure: Connection refused",
            volumeReason("wpctl ended with 2: E 23:59:34.042409             mod.rt ../pipewire/src/modules/module-rt.c:295:pw_rtkit_bus_get: Failed to connect to system bus; pactl ended with 1: Connection failure: Connection refused"),
        )
        assertEquals(volumeReason("wpctl ended with 2: W 10:00:01.1 a b.c:1:f: x 5"), volumeReason("wpctl ended with 2: W 11:22:33.4 a b.c:9:f: x 7"))
        assertEquals("wpctl is not installed", volumeReason("wpctl is not installed"))
    }
}
