package newm

import dev.caulis.core.Background
import dev.caulis.wm.Caulis
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** newm's moving wallpapers: its own shaders are there and compile, and their settings reach them. */
class ShaderWallpaperTest {

    @Test
    fun `background's shader settings reach newm`() {
        val config = NewmConfig(Caulis()).apply {
            background {
                shader = "dragon-eye"
                fps = 24
                fpsOnBattery = 10
                pauseAfter = 60
                resolution = 0.5
                speed = 0.5
                intensity = 1.2
                shaderValues = mapOf("blink" to 0.0)
                shaderColors = listOf("#3ddc97", "#f2b33d")
            }
        }
        @Suppress("UNCHECKED_CAST")
        val b = config.values["background"] as Map<String, Any?>
        assertEquals("dragon-eye", b["shader"])
        assertEquals(24, b["fps"])
        assertEquals(10, b["fps_on_battery"])
        assertEquals(60, b["pause_after"])
        assertEquals(0.5, b["resolution"])
        assertEquals(mapOf("blink" to 0.0), b["shader_values"])
        assertEquals(listOf("#3ddc97", "#f2b33d"), b["shader_colors"])
    }

    @Test
    fun `each of newm's own has its settings, and none reads one it is not given`() {
        for (name in ShaderWallpapers.BUILT_IN) {
            val shader = assertNotNull(ShaderWallpapers.builtIn(name), name)
            assertTrue("vec4 wallpaper(vec2 at)" in shader.code, "$name has the wallpaper function")
            assertTrue(shader.usesTime, "$name moves")
            assertTrue("speed" in shader.values && "intensity" in shader.values, "$name has speed and intensity")
        }
    }

    @Test
    fun `newm's own shaders compile on the GPU`() {
        assumeTrue(Files.isDirectory(Path.of("/dev/dri")), "no GPU")
        val dir = Files.createTempDirectory("newm-wallpaper-test")
        val lines = CopyOnWriteArrayList<String>()
        dev.caulis.log.Log.listener = { lines += it }
        val caulis = Caulis().apply {
            useConfigFile = false
            backend = Caulis.Backend.HEADLESS
            socketDirectory = dir
            exitAfterSeconds = 60 // (never a hanging test run)
        }
        val started = CompletableFuture<Unit>()
        caulis.onStart { started.complete(Unit) }
        val thread = Thread.ofPlatform().start { caulis.run() }
        try {
            started.get(20, TimeUnit.SECONDS)
            // (One that does not compile is said: the frames are drawn, the check below means something.)
            caulis.post { desktop.background = Background.shader(dev.caulis.core.WallpaperShader("not GLSL")) }
            val deadline = System.nanoTime() + 5_000_000_000L
            while (lines.none { "does not compile" in it } && System.nanoTime() < deadline) Thread.sleep(50)
            assertTrue(lines.any { "does not compile" in it }, "a broken shader is said to be broken")
            lines.clear()
            for (name in ShaderWallpapers.BUILT_IN) {
                val shader = ShaderWallpapers.builtIn(name)!!
                for (resolution in listOf(1f, 0.5f)) {
                    caulis.post { desktop.background = Background.shader(shader.with(resolution = resolution)) }
                    Thread.sleep(400)
                }
            }
            val problems = lines.filter { "does not compile" in it }
            assertTrue(problems.isEmpty(), problems.joinToString("\n"))
        } finally {
            dev.caulis.log.Log.listener = null
            runCatching { caulis.post { quit() } }
            thread.join(10_000)
            dir.toFile().deleteRecursively()
        }
    }
}
