package newm.bar

import dev.caulis.core.dbus.Variant
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the bar's new modules read: the processor and memory, a temperature, a media player, the weather. */
class BarReadingsTest {

    @Test
    fun `the processor's use between two readings, the memory, a temperature`() {
        val root = Files.createTempDirectory("sys")
        try {
            Files.createDirectories(root.resolve("proc"))
            Files.writeString(root.resolve("proc/meminfo"), "MemTotal:       16000000 kB\nMemFree:         1000000 kB\nMemAvailable:    12000000 kB\n")
            val stat = root.resolve("proc/stat")
            Files.writeString(stat, "cpu  100 0 100 800 0 0 0 0 0 0\ncpu0 1 2 3 4\n")
            val hw = root.resolve("sys/class/hwmon/hwmon3")
            Files.createDirectories(hw)
            Files.writeString(hw.resolve("name"), "k10temp\n")
            Files.writeString(hw.resolve("temp1_input"), "54250\n")
            val zone = root.resolve("sys/class/thermal/thermal_zone0")
            Files.createDirectories(zone)
            Files.writeString(zone.resolve("type"), "acpitz\n")
            Files.writeString(zone.resolve("temp"), "40000\n")
            val stats = SystemStats({ it() }, root)
            val first = stats.read()
            assertNull(first.cpuPercent)
            assertEquals(4000000L, first.memoryUsedKib)
            assertEquals(25, first.memoryPercent)
            assertEquals(54, first.celsius)
            // 100 more busy of 200 more in all: half.
            Files.writeString(stat, "cpu  150 0 150 900 0 0 0 0 0 0\n")
            assertEquals(50, stats.read().cpuPercent)
            stats.sensor = "acpitz"
            assertEquals(40, stats.read().celsius)
        } finally {
            root.toFile().deleteRecursively()
        }
        assertEquals(800L + 5 to 1005L, SystemStats.cpuTimes("cpu  100 0 100 800 5 0 0 0 0 0"))
        assertNull(SystemStats.cpuTimes("cpu 1 2"))
        assertNull(SystemStats.memory("MemTotal: 5 kB\n"))
    }

    private fun meta(vararg pairs: Pair<String, Any?>) = pairs.map { (k, v) -> listOf(k, Variant("v", v)) }

    @Test
    fun `what a media player plays, and which one is shown`() {
        val props = mapOf(
            "PlaybackStatus" to "Playing",
            "Metadata" to meta("xesam:title" to "Song", "xesam:artist" to listOf("A", "B"), "mpris:length" to 200_000_000L),
            "Position" to 50_000_000L, "CanGoNext" to true, "CanGoPrevious" to false, "CanPause" to true,
        )
        val m = MediaNow.of("org.mpris.MediaPlayer2.spotify", props, null, nowNanos = 0)!!
        assertEquals("Song", m.title)
        assertEquals("A, B", m.artist)
        assertEquals("Spotify", m.identity)
        assertTrue(m.playing && m.canNext && !m.canPrevious && m.canPlayPause)
        assertEquals(0.25f, m.progress(0))
        // Ten seconds later it has moved on (it plays).
        assertEquals(0.3f, m.progress(10_000_000_000L), 0.0001f)
        assertNull(MediaNow.of("org.mpris.MediaPlayer2.x", mapOf("PlaybackStatus" to "Stopped"), null))
        val paused = m.copy(player = "p2", playing = false)
        val other = m.copy(player = "p3")
        assertEquals("p3", MediaNow.choose(listOf(m, paused, other), lastPlaying = "p3")?.player)
        assertEquals("p2", MediaNow.choose(listOf(paused), lastPlaying = null)?.player)
        assertNull(MediaNow.choose(emptyList(), null))
    }

    @Test
    fun `the weather's address and answer`() {
        assertEquals("https://wttr.in/?format=%c+%t", weatherUrl("https://wttr.in/%s?format=%c+%t", ""))
        assertEquals("https://wttr.in/New%20York?format=%c+%t", weatherUrl("https://wttr.in/%s?format=%c+%t", " New York "))
        assertEquals("☀️ +12°C", cleanWeather("☀️   +12°C\n"))
        assertNull(cleanWeather("<html>busy</html>"))
        assertNull(cleanWeather("Unknown location; please try ~55.7,37.6"))
        assertNull(cleanWeather("\n\n"))
    }
}
