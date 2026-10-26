package newm

import dev.caulis.core.appearance.ColorScheme
import dev.caulis.log.Logger
import dev.caulis.wm.Caulis
import java.nio.file.Files
import java.nio.file.Path

private val log = Logger("config")

private val confColorScheme = configuredValue("color_scheme", "auto")

/**
 * Light or dark (newm's bar, menus, launcher, lock, and the programs): always one of the two.
 * The configuration's `colorScheme = "dark" | "light"`, or "auto": what was chosen last in the
 * quick settings (kept in ~/.local/state/newm/color-scheme), else dark.
 */
internal object ColorChoice {
    private val file: Path by lazy {
        val state = System.getenv("XDG_STATE_HOME")?.takeIf { it.startsWith("/") } ?: "${System.getenv("HOME") ?: System.getProperty("user.home")}/.local/state"
        Path.of(state, "newm", "color-scheme")
    }

    // The configuration's value applied last: a reload with the same one keeps a choice made since.
    private var applied: String? = null

    /** At the start and after each reload of the configuration. */
    fun apply(caulis: Caulis) {
        val wanted = confColorScheme().trim().lowercase()
        if (wanted == applied) return
        applied = wanted
        val scheme = when (wanted) {
            "dark" -> ColorScheme.DARK
            "light" -> ColorScheme.LIGHT
            else -> saved() ?: ColorScheme.DARK
        }
        log.info { "Colours: ${scheme.name.lowercase()} (colorScheme = \"$wanted\")" }
        caulis.colorScheme = scheme
    }

    /** Chosen by hand (the quick settings): used now and kept for the next start. */
    fun choose(caulis: Caulis, scheme: ColorScheme) {
        caulis.colorScheme = scheme
        log.info { "Colours chosen: ${scheme.name.lowercase()}" }
        try {
            privateFolder(file.parent)
            Files.writeString(file, scheme.name.lowercase())
        } catch (e: Exception) {
            log.warn("The colour scheme cannot be kept: $e")
        }
    }

    private fun saved(): ColorScheme? = try {
        when (Files.readString(file).trim()) {
            "dark" -> ColorScheme.DARK
            "light" -> ColorScheme.LIGHT
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
