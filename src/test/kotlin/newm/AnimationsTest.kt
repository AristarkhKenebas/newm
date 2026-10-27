package newm

import dev.caulis.core.Curve
import dev.caulis.wm.Caulis
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** newm's animations: off by default, newm's look with one line, each by name as Hyprland's. */
class AnimationsTest {

    @AfterTest
    fun reset() = Config.loadConfig(emptyMap())

    @Test
    fun `off by default, all of them`() {
        Config.loadConfig(emptyMap())
        for (name in NewmAnimations.TREE.names) assertEquals(false, NewmAnimations[name].on, "$name is off by default")
        assertEquals(1.0, NewmAnimations["windowsIn"].at(0.0), "off: there at once")
        assertEquals(0.0, NewmAnimations.gestureInertia(0.8, 60.0), "fingers followed exactly")
    }

    @Test
    fun `preset newm is newm's look as it was`() {
        Config.loadConfig(mapOf("animations" to mapOf("preset" to "newm")))
        for (name in NewmAnimations.TREE.names) assertTrue(NewmAnimations[name].on, "$name is on with the preset")
        assertEquals(300L, NewmAnimations["windowsMove"].millis, "anim_time's 0.3 s")
        assertEquals(Curve.LINEAR, NewmAnimations["camera"].curve, "newm's straight line")
        assertEquals(1000L, NewmAnimations.resolveSeconds("startup", 1.0).millis, "blend_time")
        assertEquals(280L, NewmAnimations["popupsIn"].millis)
        assertEquals(Curve.EASE_OUT, NewmAnimations["launcher"].curve)
        assertEquals(0.8, NewmAnimations.gestureInertia(0.8, 60.0), 0.01, "lp_inertia as it was")
        Config.loadConfig(mapOf("anim_time" to 0.5, "animations" to mapOf("preset" to "newm")))
        assertEquals(500L, NewmAnimations["windowsIn"].millis, "config.py's anim_time is the global time")
    }

    @Test
    fun `preset newm leaves Caulis's own animations as they were, and Caulis's windows' never come on`() {
        assertEquals(null, NewmAnimations.caulisEnabled(mapOf("preset" to "newm")), "HEAD's look: Caulis's stay off")
        assertEquals(true, NewmAnimations.caulisEnabled(mapOf("preset" to "newm", "enabled" to true)))
        assertEquals(true, NewmAnimations.caulisEnabled(mapOf("preset" to "on")))
        assertEquals(false, NewmAnimations.caulisEnabled(mapOf("preset" to "none")))
        assertEquals(null, NewmAnimations.caulisEnabled(emptyMap()))
        val caulis = Caulis().animations
        val on = NewmAnimations.caulisAnimations(caulis, true)
        assertTrue(on.enabled)
        // (A window closing: newm's own animation only, no second pop of Caulis's.)
        for (name in listOf("windows", "windowsIn", "windowsOut", "windowsMove", "minimize", "restore")) assertEquals(false, on.settings(220).resolve(name).enabled, name)
        assertTrue(on.settings(220).resolve("fadeScale").enabled)
        assertEquals(false, NewmAnimations.caulisAnimations(on, false).enabled)
    }

    @Test
    fun `each by name, falling back along the tree, curves and looks`() {
        Config.loadConfig(
            mapOf(
                "animations" to mapOf(
                    "curves" to mapOf("snappy" to mapOf("bezier" to listOf(0.05, 0.9, 0.1, 1.05)), "bouncy" to mapOf("spring" to mapOf("stiffness" to 300, "damping" to 14))),
                    "windows" to mapOf("enabled" to true, "millis" to 250, "curve" to "snappy"),
                    "fullscreen" to false,
                    "popups" to mapOf("enabled" to true, "speed" to 2),
                    "popupsIn" to "popin 90%",
                    "osdIn" to mapOf("enabled" to true, "curve" to "bouncy"),
                ),
            ),
        )
        assertEquals(250L, NewmAnimations["windowsOut"].millis)
        assertTrue(NewmAnimations["windowsIn"].curve.at(0.5) > 0.9, "the snappy curve")
        assertEquals(false, NewmAnimations["fullscreen"].on, "its own setting wins")
        assertEquals(false, NewmAnimations["camera"].on, "not under windows: off as all")
        assertEquals(200L, NewmAnimations["popupsOut"].millis, "speed 2: two tenths of a second")
        assertEquals("popin 90%", NewmAnimations["popupsIn"].style)
        assertNotEquals(null, NewmAnimations["osdIn"].curve.naturalMillis, "a spring")
    }

    @Test
    fun `mistakes are named, in config_json and config_lua`() {
        val data = mapOf(
            "animations" to mapOf(
                "enabled" to true,
                "windowz" to true,
                "border" to mapOf("style" to "popin"),
                "popupsIn" to mapOf("curve" to "wobbly", "millis" to 20_000),
                "preset" to "fancy",
            ),
        )
        val (_, problems) = DataConfig.settings(data, Caulis(), "config.json")
        assertTrue(problems.any { "windowz" in it }, problems.toString())
        assertTrue(problems.any { "border has one look only" in it }, problems.toString())
        assertTrue(problems.any { "wobbly" in it }, problems.toString())
        assertTrue(problems.any { "20000" in it }, problems.toString())
        assertTrue(problems.any { "fancy" in it }, problems.toString())
        val (_, fine) = DataConfig.settings(mapOf("animations" to mapOf("preset" to "newm", "gestureSmoothing" to false, "keyHelpOut" to "slide down 20")), Caulis(), "config.lua")
        assertEquals(emptyList(), fine)
    }

    @Test
    fun `a fade without its animation is there at once`() {
        Config.loadConfig(emptyMap())
        val fade = newm.bar.Fade("popups", 280, 180)
        fade.show()
        assertEquals(1f, fade.value(), "shown at once, not a frame later")
        assertEquals(false, fade.moving)
        fade.hide()
        assertEquals(0f, fade.value())
        assertEquals(false, fade.visible)
        Config.loadConfig(mapOf("animations" to mapOf("preset" to "newm")))
        val slow = newm.bar.Fade("popups", 280, 180)
        slow.show()
        slow.started()
        assertTrue(slow.value() < 1f && slow.moving, "with the preset it comes over 280 ms")
    }

    @Test
    fun `the template's animation lines all work when uncommented`() {
        val template = AnimationsTest::class.java.getResourceAsStream("/newm/templates/config.kts")!!.use { String(it.readAllBytes()) }
        val start = template.indexOf("animations {")
        val end = template.indexOf("\n}\n", start)
        val block = template.substring(start, end + 2).lines().joinToString("\n") { line ->
            val code = line.trim().removePrefix("// ")
            if (line.trim().startsWith("// ") && Regex("^(on\\(|animation\\(|preset|enabled|millis|curve|spring)").containsMatchIn(code)) "    " + code.substringBefore("  //").trimEnd() else line
        }
        assertTrue("on(\"windowsIn\")" in block && "spring(\"bouncy\"" in block, block)
        val file = Files.createTempFile("animations", ".kts")
        try {
            Files.writeString(file, block)
            val compiled = KtsConfig.compile(file)
            assertEquals(emptyList(), compiled.problems)
            val config = NewmConfig(Caulis())
            assertEquals(emptyList(), KtsConfig.run(compiled, config))
            @Suppress("UNCHECKED_CAST")
            val section = config.values["animations"] as Map<String, Any?>
            val problems = ArrayList<String>()
            val settings = NewmAnimations.read(section, 0.3, problems)
            assertEquals(emptyList(), problems)
            assertEquals(true, settings.enabled)
            assertEquals(250L, settings.resolve("windowsIn").millis)
            assertEquals("popin 90%", settings.resolve("popupsIn").style)
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
