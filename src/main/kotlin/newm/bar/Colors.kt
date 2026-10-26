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

    /** The palette for the setting, the wallpaper's colour being [wallpaperSeed]; null: the classic colours. */
    fun palette(wallpaperSeed: dev.caulis.render.Color?): Palette? {
        val (source, style) = setting()
        return when {
            source == "nord" || source == "classic" -> null
            source == "wallpaper" -> wallpaperSeed?.let { Palette(it, style) }
            else -> runCatching { Palette(dev.caulis.render.Color.hex(source), style) }.getOrElse {
                log.warn("colors.source \"$source\" is not \"wallpaper\", \"nord\" or a colour")
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

    /** Writes [palette] (both looks) for other programs; nothing for the classic colours. */
    fun export(palette: Palette?) {
        try {
            Files.createDirectories(cacheDir)
            val files = listOf("colors.json", "colors-light.json", "colors.css", "colors-light.css", "palette")
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
