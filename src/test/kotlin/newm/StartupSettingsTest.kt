package newm

import dev.caulis.render.Color
import dev.caulis.wm.Autostart
import dev.caulis.wm.Caulis
import dev.caulis.wm.SoundMoment
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** autostart { }, reveal { }, startupSound { }: from config.kts and as data, onto Caulis — with newm's defaults on. */
class StartupSettingsTest {

    @Test
    fun `newm's defaults - the reveal and the sound on, nothing autostarted`() {
        val caulis = Caulis()
        assertFalse(caulis.reveal.enabled, "off in Caulis")
        assertFalse(caulis.startupSound.enabled, "off in Caulis")
        caulis.startupDefaults()
        applyStartupSettings(caulis, emptyMap())
        assertTrue(caulis.reveal.enabled)
        assertEquals(600, caulis.reveal.fadeMillis)
        assertTrue(caulis.startupSound.enabled)
        assertEquals("airy", caulis.startupSound.preset)
        assertEquals(0.8, caulis.startupSound.volume)
        assertTrue(caulis.autostart.entries.isEmpty())
    }

    @Test
    fun `no startup sound on the login screen unless asked for`() {
        val caulis = Caulis().apply { startupDefaults() }
        applyStartupSettings(caulis, emptyMap(), greeter = true)
        assertFalse(caulis.startupSound.enabled, "greetd's greeter: it would sound twice")
        assertTrue(caulis.reveal.enabled)
        applyStartupSettings(caulis, mapOf("startup_sound" to mapOf("enabled" to true)), greeter = true)
        assertTrue(caulis.startupSound.enabled)
    }

    @Test
    fun `config kts`() {
        val file = Files.createTempFile("config", ".kts")
        try {
            Files.writeString(
                file,
                """
                autostart {
                    stagger = 250.milliseconds
                    app("waybar")
                    app("nm-applet", "--indicator") { delay = 2.seconds; ifNotRunning = true; restart = OnCrash(maxTimes = 3, within = 1.minutes); after("waybar"); env("X", "y"); dir = "~" }
                    script("~/.config/foo/start.sh")
                    shell("sleep 1; notify-send hi")
                    desktopEntries = true
                }
                reveal { fadeMillis = 800; color = "#102030" }
                startupSound { preset = "glass"; volume = 0.5; at = "start"; player = listOf("mpv", "{file}") }
                """.trimIndent(),
            )
            val compiled = KtsConfig.compile(file)
            assertEquals(emptyList(), compiled.problems)
            val caulis = Caulis()
            val config = NewmConfig(caulis)
            assertEquals(emptyList(), KtsConfig.run(compiled, config))
            applyStartupSettings(caulis, config.values)
            val a = caulis.autostart
            assertEquals(250, a.staggerMillis)
            assertTrue(a.desktopEntries)
            assertEquals(listOf("waybar", "nm-applet", "start.sh", "sleep"), a.entries.map { it.name })
            val applet = a.entries[1]
            assertEquals(2000, applet.delayMillis)
            assertTrue(applet.ifNotRunning && applet.restart.wants(1) && !applet.restart.wants(0))
            assertEquals(listOf("waybar"), applet.after)
            assertEquals(800, caulis.reveal.fadeMillis)
            assertEquals(Color.hex("#102030"), caulis.reveal.color)
            assertEquals("glass", caulis.startupSound.preset)
            assertEquals(SoundMoment.START, caulis.startupSound.at)
            assertEquals(listOf("mpv", "{file}"), caulis.startupSound.player)

            // Applied again (a reload): the entries are replaced, not added to.
            applyStartupSettings(caulis, config.values)
            assertEquals(4, caulis.autostart.entries.size)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `as data (config json, config lua)`() {
        val data = linkedMapOf<String, Any?>(
            "autostart" to linkedMapOf(
                "stagger_ms" to 100, "desktop_entries" to true,
                "entries" to listOf(
                    linkedMapOf("app" to listOf("nm-applet", "--indicator"), "delay_ms" to 2000, "if_not_running" to true,
                        "restart" to linkedMapOf("when" to "always", "max_times" to 2), "after" to listOf("waybar"), "env" to mapOf("X" to "y")),
                    linkedMapOf("app" to "waybar"),
                    // (Lua: a table numbered from 1.)
                    linkedMapOf("script" to "~/s.sh", "arguments" to linkedMapOf(1 to "--now")),
                    linkedMapOf("shell" to "notify-send hi", "restart" to "on_crash"),
                ),
            ),
            "reveal" to linkedMapOf("enabled" to false, "fade_ms" to 300),
            "startup_sound" to linkedMapOf("preset" to "soft", "enabled" to false),
        )
        val (s, problems) = DataConfig.settings(data, Caulis(), "config.json")
        assertEquals(emptyList(), problems)
        val caulis = Caulis().apply { startupDefaults() }
        applyStartupSettings(caulis, s)
        val a = caulis.autostart
        assertEquals(100, a.staggerMillis)
        assertEquals(listOf("nm-applet", "waybar", "s.sh", "notify-send"), a.entries.map { it.name })
        assertEquals(listOf("~/s.sh", "--now"), a.entries[2].command)
        assertEquals(Autostart.Kind.SHELL, a.entries[3].kind)
        assertTrue(a.entries[0].restart.wants(0), "always")
        assertFalse(caulis.reveal.enabled)
        assertEquals(300, caulis.reveal.fadeMillis)
        assertFalse(caulis.startupSound.enabled)
        assertEquals("soft", caulis.startupSound.preset)
        assertNull(caulis.startupSound.file)

        val (_, wrong) = DataConfig.settings(
            mapOf("autostart" to mapOf("stagger" to 1, "entries" to listOf(mapOf("app" to "a", "shell" to "b"), mapOf("app" to "x", "restart" to "sometimes")))),
            Caulis(), "config.json",
        )
        assertEquals(3, wrong.size, "$wrong")
    }
}
