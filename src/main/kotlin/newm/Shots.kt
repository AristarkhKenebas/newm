package newm

import dev.caulis.log.Logger
import dev.caulis.screenshot.ShotKind
import dev.caulis.screenshot.Screenshots
import dev.caulis.screenshot.screenshots
import newm.bar.toast
import java.nio.file.Path

private val log = Logger("screenshot")

// screenshots { enabled, folder, save, copy, pointer }
private val confEnabled = configuredValue("screenshots.enabled", true)
private val confFolder = configuredValue<String?>("screenshots.folder", null)
private val confSave = configuredValue("screenshots.save", true)
private val confCopy = configuredValue("screenshots.copy", true)
private val confPointer = configuredValue("screenshots.pointer", false)
private val confToast = configuredValue("screenshots.toast", true)
private val confStart = configuredValue<String?>("screenshots.start", null)
private val confDelay = configuredValue("screenshots.delay", 0)
private val confSharp = configuredValue("screenshots.sharp_windows", true)
private val confSharpBackground = configuredValue("screenshots.sharp_background", "none")
private val confFileName = configuredValue("screenshots.file_name", "Screenshot from {date} {time}")
private val confFormat = configuredValue("screenshots.format", "png")
private val confQuality = configuredValue("screenshots.quality", 92)
private val confThen = configuredValue<String?>("screenshots.then", null)
private val confCornerRadius = configuredValue("view.corner_radius", 12.0)

/**
 * newm's screenshots (Caulis's chooser: the screen stands still, a part, a window or the screen
 * is chosen, saved as PNG and put on the clipboard). The keys are the configuration's
 * (as Windows has them: Super+Shift+S chooses a part, Super+Print takes the screen, Print
 * chooses); screenshots { enabled = false } turns them off.
 */
internal class Shots(private val wm: Layout) {
    private val shots: Screenshots = wm.caulis.screenshots {
        // (No keys of Caulis's own: newm's keys call these.)
        keys(choose = null, screen = null, window = null)
        notify = false
        onTaken = { path ->
            if (confToast()) wm.caulis.post {
                wm.barModel.toast(when {
                    path != null && copy -> "Screenshot saved and copied"
                    path != null -> "Screenshot saved"
                    else -> "Screenshot copied"
                })
            }
        }
    }

    /** The configuration's settings, again after each reload. */
    fun apply() {
        confFolder()?.takeIf { it.isNotBlank() }?.let { shots.folder = Path.of(it.replaceFirst("~", System.getenv("HOME") ?: System.getProperty("user.home"))) }
        shots.save = confSave()
        shots.copy = confCopy()
        shots.pointer = confPointer()
        shots.delaySeconds = confDelay().coerceIn(0, 60)
        confStart()?.let { name -> ShotKind.entries.firstOrNull { it.name.equals(name, true) }?.let { shots.kind = it } }
        shots.sharpWindows = confSharp()
        shots.sharpCornerRadius = confCornerRadius().toFloat()
        shots.sharpBackground = when (val bg = confSharpBackground().trim().lowercase()) {
            "none", "", "transparent" -> null
            "theme" -> wm.barModel.colorsNow().island.let { dev.caulis.render.Color(it.red, it.green, it.blue, 1f) }
            else -> runCatching { dev.caulis.render.Color.hex(bg) }.getOrNull()
        }
        shots.fileName = confFileName()
        shots.format = confFormat()
        shots.jpegQuality = confQuality().coerceIn(10, 100) / 100f
        shots.then = confThen()
        shots.style = style()
    }

    // The chooser in newm's look: the bar's colours (the wallpaper's), a pill, newm's words.
    private fun style(): dev.caulis.screenshot.ScreenshotStyle {
        val c = wm.barModel.colorsNow()
        fun of(x: androidx.compose.ui.graphics.Color, alpha: Float = x.alpha) = dev.caulis.render.Color(x.red, x.green, x.blue, alpha)
        val words = listOf("Area", "Window", "Screen", "Pointer", "Timer", "Capture", "%s s", "Drag to choose a part first", "Point at a window first",
            "No window has the focus", "Screenshot in %s")
        return dev.caulis.screenshot.ScreenshotStyle(
            background = of(c.island, 0.96f), text = of(c.text), dim = of(c.dim), border = of(c.edge), accent = of(c.accent),
            onAccent = of(newm.bar.onAccent(c)), cornerRadius = 32f, labels = words.associateWith { newm.bar.tr(it) },
        )
    }

    private fun enabled(): Boolean = confEnabled().also { if (!it) log.info { "Screenshots are off (screenshots { enabled = false })" } }

    /** The chooser, starting with a part of the screen. */
    fun choose() {
        // (Its look again: the colours may have changed with the wallpaper.)
        shots.style = style()
        if (enabled()) shots.choose(ShotKind.REGION)
    }

    /** The monitor with the pointer, at once. */
    fun screen() {
        if (enabled()) shots.takeMonitor()
    }

    /** The focused window, at once. */
    fun window() {
        if (enabled()) shots.takeFocused()
    }
}
