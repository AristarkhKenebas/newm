package newm.bar

import androidx.compose.ui.graphics.Color
import dev.caulis.core.appearance.Palette
import dev.caulis.core.appearance.PaletteStyle
import dev.caulis.log.Logger
import newm.configuredValue
import java.nio.file.Files
import java.nio.file.Path

private val log = Logger("colors")

// colors { source = "wallpaper" | "#RRGGBB" | "nord"; style = "tonal" | "vibrant" | "expressive" | "neutral" | "monochrome" }
private val confSource = configuredValue<String?>("colors.source", null)
private val confStyle = configuredValue<String?>("colors.style", null)

/**
 * newm's colours: made from the wallpaper (or a colour of yours) as phones do (Caulis's
 * [Palette]), or the classic Nord ones. The choice of the colours menu is kept in
 * ~/.local/state/newm/colors (the configuration's colors { } wins over it); the palette in use
 * is written to ~/.cache/newm/colors.json and colors.css for other programs (waybar, GTK).
 */
object NewmColors {
    private val home = System.getenv("HOME") ?: System.getProperty("user.home")
    private val stateFile: Path by lazy {
        Path.of(System.getenv("XDG_STATE_HOME")?.takeIf { it.startsWith("/") } ?: "$home/.local/state", "newm", "colors")
    }
    private val cacheDir: Path by lazy { Path.of(System.getenv("XDG_CACHE_HOME")?.takeIf { it.startsWith("/") } ?: "$home/.cache", "newm") }

    // The menu's choice as kept: read from the file once, then changed by choose() (the popup asks for it at each drawing).
    @Volatile
    private var choice: String? = null
    @Volatile
    private var choiceRead = false

    /** What the colours menu chose last ("wallpaper:vibrant", "nord", "caulis-palette:#…:tonal"), if anything. */
    fun chosen(): String? {
        if (!choiceRead) {
            choice = try {
                Files.readString(stateFile).trim().ifEmpty { null }
            } catch (_: Exception) {
                null
            }
            choiceRead = true
            log.trace { "Colours chosen before: ${choice ?: "none"}" }
        }
        return choice
    }

    /** Keeps the colours menu's choice. */
    fun choose(choice: String) {
        this.choice = choice
        choiceRead = true
        log.info { "Colours chosen: $choice" }
        try {
            Files.createDirectories(stateFile.parent)
            Files.writeString(stateFile, choice)
        } catch (e: Exception) {
            log.warn("The colours chosen cannot be kept: $e")
        }
    }

    /** The source ("wallpaper", "nord" or a colour) and style in use: the configuration's, else the menu's, else the wallpaper's tonal. */
    fun setting(): Pair<String, PaletteStyle> {
        val chosen = chosen()?.split(':')
        val source = confSource() ?: chosen?.getOrNull(0)?.let { if (it == "caulis-palette") chosen.getOrNull(1) else it } ?: "wallpaper"
        val style = (confStyle() ?: chosen?.lastOrNull()?.takeIf { it != source })?.let { runCatching { PaletteStyle.named(it) }.getOrNull() } ?: PaletteStyle.TONAL_SPOT
        return source to style
    }

    /** The look of newm's own the setting names (colors.source = "jadefire"), or null. */
    fun theme(): NewmTheme? = NewmThemes.named(setting().first)

    /** The bar's colours when no palette is made: the look's ([theme]), else the classic Nord ones. */
    fun plain(dark: Boolean): BarColors = theme()?.let { if (dark) it.dark else it.light } ?: if (dark) BarColors.DARK else BarColors.LIGHT

    /** The palette for the setting, the wallpaper's colour being [wallpaperSeed]; null: the classic colours or a look of newm's own. */
    fun palette(wallpaperSeed: dev.caulis.render.Color?): Palette? {
        val (source, style) = setting()
        return when {
            source == "nord" || source == "classic" -> null
            NewmThemes.named(source) != null -> null
            source == "wallpaper" -> wallpaperSeed?.let { Palette(it, style) }
            else -> runCatching { Palette(dev.caulis.render.Color.hex(source), style) }.getOrElse {
                log.warn("colors.source \"$source\" is not \"wallpaper\", \"nord\", \"jadefire\" or a colour")
                null
            }
        }
    }

    /** The bar's colours of [palette] in the dark or light look. */
    fun barColors(palette: Palette, dark: Boolean): BarColors {
        val s = palette.scheme(dark)
        fun c(x: dev.caulis.render.Color, alpha: Float = 1f) = Color(x.red, x.green, x.blue, alpha)
        return BarColors(
            island = c(s.surface, 0.80f),
            text = c(s.onSurface),
            dim = c(s.onSurfaceVariant),
            accent = c(s.primary),
            hover = c(s.onSurface, if (dark) 0.10f else 0.07f),
            edge = if (dark) c(s.outlineVariant, 0.45f) else Color(1f, 1f, 1f, 0.5f),
            warning = c(s.tertiary),
            danger = c(s.error),
        )
    }

    /** Writes [palette] (both looks) for other programs — or a look of newm's own ([theme]): its tokens; nothing for the classic colours. */
    fun export(palette: Palette?, theme: NewmTheme? = null) {
        try {
            Files.createDirectories(cacheDir)
            val files = listOf("colors.json", "colors-light.json", "colors.css", "colors-light.css", "palette")
            if (palette == null && theme != null) {
                Files.writeString(cacheDir.resolve("colors.json"), theme.json(dark = true))
                Files.writeString(cacheDir.resolve("colors-light.json"), theme.json(dark = false))
                Files.writeString(cacheDir.resolve("colors.css"), theme.css(dark = true))
                Files.writeString(cacheDir.resolve("colors-light.css"), theme.css(dark = false))
                Files.writeString(cacheDir.resolve("palette"), theme.name + "\n")
                log.info { "Colours: newm's look ${theme.name} (in $cacheDir)" }
                return
            }
            if (palette == null) {
                files.forEach { Files.deleteIfExists(cacheDir.resolve(it)) }
                return
            }
            Files.writeString(cacheDir.resolve("colors.json"), palette.scheme(true).toJson())
            Files.writeString(cacheDir.resolve("colors-light.json"), palette.scheme(false).toJson())
            Files.writeString(cacheDir.resolve("colors.css"), palette.scheme(true).toCss())
            Files.writeString(cacheDir.resolve("colors-light.css"), palette.scheme(false).toCss())
            Files.writeString(cacheDir.resolve("palette"), palette.code + "\n")
            log.info { "Colours: ${palette.code} (in $cacheDir)" }
        } catch (e: Exception) {
            log.warn("The colours cannot be written to $cacheDir: $e")
        }
    }
}

/**
 * A look of newm's own: fixed colours, dark and light ([dark], [light]: the bar's, the menus',
 * the cards', the lock screen's), its colour tokens by name for other programs ([tokens]), and
 * what goes with it when `theme = "<name>"` is set: a focus border shader and a moving
 * wallpaper of newm's (see [NewmThemes.underlay]).
 */
class NewmTheme(
    val name: String,
    val dark: BarColors,
    val light: BarColors,
    /** Its colour tokens, dark look: name to "#rrggbb". */
    val tokens: Map<String, String>,
    /** The same, light look. */
    val lightTokens: Map<String, String>,
    /** What `theme = name` sets where the configuration says nothing (newm's own names: "focus.shader" to "jadefire"). */
    val settings: Map<String, Any?>,
) {
    fun json(dark: Boolean): String = (if (dark) tokens else lightTokens).entries.joinToString(",\n", "{\n", "\n}\n") { (k, v) -> "  \"$k\": \"$v\"" }
    fun css(dark: Boolean): String = (if (dark) tokens else lightTokens).entries.joinToString("\n", postfix = "\n") { (k, v) -> "@define-color $k $v;" }
}

/** newm's own looks, by name: `theme = "jadefire"`, or only their colours with `colors { source = "jadefire" }`. */
object NewmThemes {
    /**
     * Jadefire: a glowing reptile's eye in the dark — deep emerald melting into jade, amber
     * round the slit, a rim of ember fire, set in near-black green. newm's brand look.
     */
    val JADEFIRE = NewmTheme(
        name = "jadefire",
        dark = BarColors(
            island = Color(0xD6111A15), text = Color(0xFFE8F0EA), dim = Color(0xFFA3B5AA), accent = Color(0xFF3DDC97),
            hover = Color(0x1CE8F0EA), edge = Color(0x383DDC97), warning = Color(0xFFF2B33D), danger = Color(0xFFE8552D),
        ),
        light = BarColors(
            island = Color(0xDBF2F5EF), text = Color(0xFF10201A), dim = Color(0xFF4A5D52), accent = Color(0xFF0E8A57),
            hover = Color(0x1410201A), edge = Color(0x80FFFFFF), warning = Color(0xFFB7791F), danger = Color(0xFFC2410C),
        ),
        tokens = linkedMapOf(
            "abyss" to "#060908", "night" to "#0b110e", "basalt" to "#111a15", "moss" to "#18241d", "slate" to "#22322a",
            "emerald_deep" to "#0b5d3b", "emerald" to "#13995f", "jade" to "#3ddc97", "jade_pale" to "#a6f4cf",
            "amber" to "#f2b33d", "gold" to "#ffd772", "ember" to "#e8552d", "ember_deep" to "#b3261e",
            "bone" to "#e8f0ea", "lichen" to "#a3b5aa", "fog" to "#66786e",
            "background" to "#0b110e", "surface" to "#111a15", "text" to "#e8f0ea", "accent" to "#3ddc97", "highlight" to "#f2b33d", "alert" to "#e8552d",
        ),
        lightTokens = linkedMapOf(
            "paper" to "#f2f5ef", "mist" to "#e3ebe4", "stone" to "#cbd8cf",
            "emerald_deep" to "#0b5d3b", "emerald" to "#0e8a57", "jade" to "#2fb37a",
            "amber" to "#b7791f", "ember" to "#c2410c", "ink" to "#10201a", "lichen" to "#4a5d52",
            "background" to "#f2f5ef", "surface" to "#e3ebe4", "text" to "#10201a", "accent" to "#0e8a57", "highlight" to "#b7791f", "alert" to "#c2410c",
        ),
        settings = mapOf(
            "colors" to mapOf("source" to "jadefire"),
            "focus" to mapOf("shader" to "jadefire", "width" to 3.0),
            "background" to mapOf("shader" to "dragon-eye"),
            "panels" to mapOf("lock" to mapOf("background" to "#0b110eF2", "text" to "#e8f0ea", "accent" to "#3ddc97")),
        ),
    )

    /** Every look by name. */
    val ALL = listOf(JADEFIRE)

    fun named(name: String?): NewmTheme? = name?.trim()?.lowercase()?.let { n -> ALL.firstOrNull { it.name == n } }

    /**
     * [settings] with the look `theme = "<name>"` names underneath: what the configuration says
     * wins, the look fills in the rest (a wallpaper of the configuration's own — background.path
     * or .shader — keeps the look's moving one away).
     */
    fun underlay(settings: Map<String, Any?>): Map<String, Any?> {
        val name = settings["theme"] as? String ?: return settings
        val theme = named(name) ?: return settings.also { log.warn("theme \"$name\" is not one of newm's looks (${ALL.joinToString { it.name }})") }
        log.info { "Look: ${theme.name}" }
        @Suppress("UNCHECKED_CAST")
        val background = settings["background"] as? Map<String, Any?>
        val ownWallpaper = background != null && ("path" in background || "shader" in background)
        val look = if (ownWallpaper) theme.settings - "background" else theme.settings
        return merged(look, settings)
    }

    // [over] laid over [under]: maps merged deeply, everything else from [over].
    @Suppress("UNCHECKED_CAST")
    private fun merged(under: Map<String, Any?>, over: Map<String, Any?>): Map<String, Any?> {
        val out = LinkedHashMap(under)
        for ((k, v) in over) {
            val below = out[k]
            out[k] = if (v is Map<*, *> && below is Map<*, *>) merged(below as Map<String, Any?>, v as Map<String, Any?>) else v
        }
        return out
    }
}
