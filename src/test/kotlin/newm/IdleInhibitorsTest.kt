package newm

import dev.caulis.core.dbus.DBusConnection
import dev.caulis.core.dbus.DBusMethod
import dev.caulis.core.dbus.DBusScope
import dev.caulis.core.dbus.ObjectPath
import dev.caulis.core.dbus.Variant
import dev.caulis.event.EventLoop
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Programs keep the screen on over D-Bus (org.freedesktop.ScreenSaver, PowerManagement.Inhibit,
 * MPRIS) — on a private bus of the test's own, never the desktop's — and logind's idle inhibitors.
 */
class IdleInhibitorsTest {

    // What is held now: reason -> pid.
    private val held = LinkedHashMap<String, Int>()
    private val inhibitors = IdleInhibitors(hold = { reason, pid ->
        held[reason] = pid
        AutoCloseable { held.remove(reason) }
    })

    @Test
    fun `ScreenSaver and PowerManagement inhibitors are held until let go or their program leaves the bus`() = withBus { loop, address ->
        val server = DBusConnection.open(loop, address)!!
        loop.until { server.uniqueName != null }
        inhibitors.startDBus(DBusScope(server))
        loop.until(500) { false } // (the names taken)

        // A browser: Inhibit, then UnInhibit.
        val browser = DBusConnection.open(loop, address)!!
        var cookie: Int? = null
        browser.call("org.freedesktop.ScreenSaver", ObjectPath("/org/freedesktop/ScreenSaver"), "org.freedesktop.ScreenSaver", "Inhibit", "ss", listOf("Firefox", "video-playing")) { cookie = it.body.firstOrNull() as Int? }
        loop.until { cookie != null }
        assertEquals(mapOf("Firefox: video-playing" to 0), held)
        // Another program cannot let go of the browser's.
        val other = DBusConnection.open(loop, address)!!
        var answered = false
        other.call("org.freedesktop.ScreenSaver", ObjectPath("/ScreenSaver"), "org.freedesktop.ScreenSaver", "UnInhibit", "u", listOf(cookie!!)) { answered = true }
        loop.until { answered }
        assertEquals(setOf("Firefox: video-playing"), held.keys)
        answered = false
        browser.call("org.freedesktop.ScreenSaver", ObjectPath("/org/freedesktop/ScreenSaver"), "org.freedesktop.ScreenSaver", "UnInhibit", "u", listOf(cookie!!)) { answered = true }
        loop.until { answered }
        assertEquals(emptyMap(), held)

        // Telegram through PowerManagement, at /ScreenSaver too, then it quits without letting go.
        var cookies = 0
        browser.call("org.freedesktop.PowerManagement", ObjectPath("/org/freedesktop/PowerManagement/Inhibit"), "org.freedesktop.PowerManagement.Inhibit", "Inhibit", "ss", listOf("Telegram", "Playing video")) { if (!it.isError) cookies++ }
        browser.call("org.freedesktop.ScreenSaver", ObjectPath("/ScreenSaver"), "org.freedesktop.ScreenSaver", "Inhibit", "ss", listOf("Chromium", "")) { if (!it.isError) cookies++ }
        loop.until { cookies == 2 }
        assertEquals(setOf("Telegram: Playing video", "Chromium"), held.keys)
        browser.close()
        loop.until { held.isEmpty() }
        other.close()
        inhibitors.stop()
        server.close()
    }

    @Test
    fun `a media player playing is held with its process, paused it is let go, music players never`() = withBus { loop, address ->
        val server = DBusConnection.open(loop, address)!!
        loop.until { server.uniqueName != null }
        // A player on the bus before newm listens, and one after.
        val player = DBusConnection.open(loop, address)!!
        var status = "Playing"
        fun export(connection: DBusConnection, identity: String, playing: () -> String) {
            connection.export("/org/mpris/MediaPlayer2", "org.freedesktop.DBus.Properties", mapOf(
                "Get" to DBusMethod("ss", "v") { call ->
                    listOf(if (call.body[1] == "Identity") Variant("s", identity) else Variant("s", playing()))
                },
            ))
        }
        export(player, "Video Player") { status }
        var named = false
        player.requestName("org.mpris.MediaPlayer2.videoplayer") { named = it }
        loop.until { named }
        inhibitors.startMpris(DBusScope(server))
        val pid = ProcessHandle.current().pid().toInt()
        loop.until { held.isNotEmpty() }
        assertEquals(mapOf("Video Player is playing" to pid), held)

        // Paused: let go.
        status = "Paused"
        player.emitSignal("/org/mpris/MediaPlayer2", "org.freedesktop.DBus.Properties", "PropertiesChanged", "sa{sv}as",
            listOf("org.mpris.MediaPlayer2.Player", mapOf("PlaybackStatus" to Variant("s", "Paused")), emptyList<String>()))
        loop.until { held.isEmpty() }

        // A music player comes and plays: nothing held.
        val music = DBusConnection.open(loop, address)!!
        export(music, "Spotify") { "Playing" }
        named = false
        music.requestName("org.mpris.MediaPlayer2.spotify") { named = it }
        loop.until { named }
        loop.until(500) { false }
        assertEquals(emptyMap(), held)

        // Playing again, then the player quits: let go.
        player.emitSignal("/org/mpris/MediaPlayer2", "org.freedesktop.DBus.Properties", "PropertiesChanged", "sa{sv}as",
            listOf("org.mpris.MediaPlayer2.Player", mapOf("PlaybackStatus" to Variant("s", "Playing")), emptyList<String>()))
        loop.until { held.isNotEmpty() }
        player.close()
        loop.until { held.isEmpty() }
        music.close()
        inhibitors.stop()
        server.close()
    }

    @Test
    fun `logind's idle inhibitors that block are held, newm's own and delay ones are not`() {
        val own = 4242
        inhibitors.logindInhibitors(listOf(
            IdleInhibitors.LogindInhibitor("idle", "presentation", "slides", "block", 100),
            IdleInhibitors.LogindInhibitor("sleep:idle", "newm", "", "block", own),
            IdleInhibitors.LogindInhibitor("idle", "player", "video", "delay", 101),
            IdleInhibitors.LogindInhibitor("sleep", "updates", "upgrading", "block", 102),
        ), own)
        assertEquals(setOf("presentation: slides"), held.keys)
        inhibitors.logindInhibitors(emptyList(), own)
        assertEquals(emptyMap(), held)
    }

    // A private session bus of the test's own (dbus-daemon in a temporary folder), and a loop for it.
    private fun withBus(test: (EventLoop, String) -> Unit) {
        val daemon = listOf("/usr/bin/dbus-daemon", "/run/current-system/sw/bin/dbus-daemon").firstOrNull { Files.isExecutable(Path.of(it)) }
        assumeTrue(daemon != null, "no dbus-daemon")
        val dir = Files.createTempDirectory("newm-bus")
        val address = "unix:path=$dir/bus"
        val process = ProcessBuilder(daemon, "--session", "--nofork", "--nopidfile", "--address=$address")
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        try {
            val deadline = System.nanoTime() + 5_000_000_000
            while (!Files.exists(dir.resolve("bus")) && System.nanoTime() < deadline) Thread.sleep(20)
            assertTrue(Files.exists(dir.resolve("bus")), "the private bus started")
            EventLoop().use { loop -> test(loop, address) }
        } finally {
            process.destroy()
            process.waitFor(5, TimeUnit.SECONDS)
            dir.toFile().deleteRecursively()
        }
    }

    // Runs the loop until [done] (or [millis] passed: then it must be done).
    private fun EventLoop.until(millis: Long = 5000, done: () -> Boolean) {
        val end = System.nanoTime() + millis * 1_000_000
        while (!done() && System.nanoTime() < end) dispatch(20)
        if (millis == 5000L) check(done()) { "timed out" }
    }
}
