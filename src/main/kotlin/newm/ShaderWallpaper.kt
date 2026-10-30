package newm

import dev.caulis.core.Monitor
import dev.caulis.core.WallpaperShader
import dev.caulis.log.Logger

// background { shader = "dragon-eye" | "~/shaders/mine.glsl" }: a moving wallpaper drawn by the GPU instead of the picture.
private val confShader = configuredValue("background.shader", "")
// How often it is drawn anew, a second (30); on battery (15; 0: as fps).
private val confFps = configuredValue("background.fps", 30)
private val confFpsOnBattery = configuredValue("background.fps_on_battery", 15)
// It stands still after so many seconds without input (120; 0: never) and while the screen is dimmed.
private val confPauseAfter = configuredValue("background.pause_after", 120)
// How sharp: 1.0 every pixel, 0.5 half each way (a quarter of the work); 0 (the default): 1.0 up to 2560x1600, 0.5 above.
private val confResolution = configuredValue("background.resolution", 0.0)
// Its pace and strength (1.0 each), and its own settings by name (eyeSize, blink, embers, scaleSize, spores, blobs).
private val confSpeed = configuredValue("background.speed", 1.0)
private val confIntensity = configuredValue("background.intensity", 1.0)
private val confValues = configuredValue<Map<String, Any?>>("background.shader_values", emptyMap())
// Its colours ("#RRGGBB": jade, amber, ember, the dark); empty: the shader's own.
private val confColors = configuredValue("background.shader_colors", emptyList<String>())

private val log = Logger("wallpaper")

/**
 * newm's moving wallpapers: a GLSL shader (Caulis's [WallpaperShader]) instead of a picture —
 * one of newm's own by name ([BUILT_IN]) or a file of yours (read again when saved). It moves
 * and zooms with the view as the picture does, answers the pointer and clicks, is drawn only
 * as often as it asks ([confFps]; fewer on battery), only while seen, and stands still while
 * nobody is there.
 */
object ShaderWallpapers {
    /** newm's own, by name: in resources/newm/wallpapers. */
    val BUILT_IN = listOf("dragon-eye", "ember-sky", "scales", "deep-forest", "molten")

    // Each built-in's own settings and their defaults (what its code reads besides speed and intensity).
    private val DEFAULTS = mapOf(
        "dragon-eye" to mapOf("eyeSize" to 0.38, "blink" to 1.0),
        "ember-sky" to mapOf("embers" to 1.0),
        "scales" to mapOf("scaleSize" to 1.0),
        "deep-forest" to mapOf("spores" to 1.0),
        "molten" to mapOf("blobs" to 7.0),
    )

    /** The colours' seed with a shader wallpaper that names no colours: Jadefire's jade. */
    val SEED: dev.caulis.render.Color = dev.caulis.render.Color.hex("#3ddc97")

    /** The shader configured (background.shader), or null: the picture. */
    fun configured(): String? = confShader().trim().takeIf { it.isNotEmpty() }

    // The shader of [name] made last (a file's is followed: made once per name).
    private var made: Pair<String, WallpaperShader>? = null

    /** The shader for [name] (a built-in's name, or a file), its settings as configured; null if there is none such. */
    fun shader(layout: Layout, name: String): WallpaperShader? {
        val base = made?.takeIf { it.first == name }?.second ?: make(layout, name)?.also { made = name to it } ?: return null
        val values = LinkedHashMap<String, Float>()
        DEFAULTS[name]?.forEach { (k, v) -> values[k] = v.toFloat() }
        values["speed"] = confSpeed().toFloat()
        values["intensity"] = confIntensity().toFloat()
        for ((k, v) in confValues()) (v as? Number)?.let { values[k] = it.toFloat() } ?: log.warn("background.shader_values: $k is not a number")
        val colors = confColors().mapNotNull { text -> runCatching { color(text) }.getOrElse { log.warn("background.shader_colors: \"$text\" is not a colour"); null } }.take(WallpaperShader.MAX_COLORS)
        return try {
            base.with(colors = colors, values = values, framesPerSecond = confFps().coerceIn(1, 240))
        } catch (e: IllegalArgumentException) {
            log.warn("The wallpaper shader's settings cannot be used: ${e.message}")
            base.with(framesPerSecond = confFps().coerceIn(1, 240))
        }
    }

    /** newm's own shader [name] with its settings' defaults (null: no such one). */
    fun builtIn(name: String): WallpaperShader? {
        if (name !in BUILT_IN) return null
        val code = ShaderWallpapers::class.java.getResourceAsStream("/newm/wallpapers/$name.glsl")?.use { String(it.readAllBytes()) }
        if (code == null) {
            log.warn("The wallpaper shader $name is missing from newm")
            return null
        }
        val values = LinkedHashMap<String, Float>()
        values["speed"] = 1f
        values["intensity"] = 1f
        DEFAULTS[name]?.forEach { (k, v) -> values[k] = v.toFloat() }
        return WallpaperShader(code).with(values = values)
    }

    private fun make(layout: Layout, name: String): WallpaperShader? {
        if (name in BUILT_IN) return builtIn(name)?.also { log.info { "Wallpaper shader: $name (newm's own)" } }
        val path = layout.caulis.file(name)
        if (!java.nio.file.Files.isRegularFile(path)) {
            log.warn("The wallpaper shader \"$name\" is neither one of newm's (${BUILT_IN.joinToString()}) nor a file")
            return null
        }
        log.info { "Wallpaper shader: $path (read again when saved)" }
        return WallpaperShader.file(path)
    }

    /** [shader] as sharp as [monitor] needs (background.resolution; by default half on screens above 2560x1600). */
    fun forMonitor(shader: WallpaperShader, monitor: Monitor): WallpaperShader {
        val wanted = confResolution().takeIf { it > 0.0 } ?: if (monitor.pixelWidth.toLong() * monitor.pixelHeight > 2560L * 1600) 0.5 else 1.0
        return shader.with(resolution = wanted.toFloat().coerceIn(0.1f, 1f))
    }

    // Whether they stand still now, and the battery's state as last looked at.
    private var paused = false
    private var onBattery = false
    private var batteryAsked = 0L

    /**
     * Once a second (with newm's idle clock): they stand still after background.pause_after
     * seconds without input or once the screen is dimmed ([idleFor], [dimmed]), and are drawn
     * at most background.fps_on_battery times a second on battery.
     */
    fun tick(layout: Layout, idleFor: Double, dimmed: Boolean) {
        val desktop = layout.caulis.desktop
        val after = confPauseAfter()
        val pause = configured() != null && (dimmed || (after > 0 && idleFor > after))
        if (pause != paused) {
            paused = pause
            log.info { if (pause) "The wallpaper stands still (nobody there)" else "The wallpaper moves again" }
            desktop.wallpaperShadersPaused = pause
        }
        val now = System.nanoTime()
        if (now - batteryAsked > 10_000_000_000L || batteryAsked == 0L) {
            batteryAsked = now
            val discharging = java.io.File("/sys/class/power_supply").listFiles { f -> f.name.startsWith("BAT") }.orEmpty()
                .any { runCatching { it.resolve("status").readText().trim() == "Discharging" }.getOrDefault(false) }
            if (discharging != onBattery) {
                onBattery = discharging
                log.info { if (discharging) "On battery: the wallpaper at most ${confFpsOnBattery()} frames a second" else "On power: the wallpaper at its own pace" }
            }
        }
        val limit = if (onBattery) confFpsOnBattery().coerceIn(0, 240) else 0
        if (desktop.wallpaperFpsLimit != limit) desktop.wallpaperFpsLimit = limit
    }
}
