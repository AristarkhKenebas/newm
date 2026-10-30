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
private val confSelection = configuredValue("screenshots.selection", "adjust")
private val confOverviewStart = configuredValue("screenshots.overview_start", "windows")
private val confSharp = configuredValue("screenshots.sharp_windows", true)
private val confSharpBackground = configuredValue("screenshots.sharp_background", "wallpaper")
private val confFileName = configuredValue("screenshots.file_name", "Screenshot from {date} {time}")
private val confFormat = configuredValue("screenshots.format", "png")
private val confQuality = configuredValue("screenshots.quality", 92)
private val confThen = configuredValue<String?>("screenshots.then", null)
// screenshots { rememberKind = false }: the chooser starts choosing a part each time (true: the kind used last, a window after a window).
private val confRememberKind = configuredValue("screenshots.remember_kind", false)
// screenshots { clickPicksWindow = false }: a click (not a drag) while choosing a part takes the window under it (true).
private val confClickPicksWindow = configuredValue("screenshots.click_picks_window", false)
// screenshots { rememberSelection = true }: the part chosen last is there again when the chooser opens (false: none).
private val confRememberSelection = configuredValue("screenshots.remember_selection", true)
// screenshots { rememberAcrossRestarts = false }: that part is kept across restarts too (true).
private val confRememberAcrossRestarts = configuredValue("screenshots.remember_across_restarts", false)
// screenshots { escapeClearsSelection = true }: the first Escape clears a part shown, a second closes (false: Escape closes and forgets it).
private val confEscapeClearsSelection = configuredValue("screenshots.escape_clears_selection", true)
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
        keys(choose = null, screen = null, window = null, selective = null)
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

    /** The chooser's keys (for the keys' help). */
    fun chooserKeys(): dev.caulis.screenshot.ChooserKeys = shots.chooser.keys

    /** The configuration's settings, again after each reload. */
    fun apply() {
        confFolder()?.takeIf { it.isNotBlank() }?.let { shots.folder = Path.of(expandHome(it)) }
        shots.save = confSave()
        shots.copy = confCopy()
        shots.pointer = confPointer()
        shots.delaySeconds = confDelay().coerceIn(0, 60)
        confStart()?.let { name -> ShotKind.entries.firstOrNull { it.name.equals(name, true) }?.let { shots.kind = it } }
        shots.selection = if (confSelection().trim().equals("new", true)) dev.caulis.screenshot.Screenshots.Selection.NEW else dev.caulis.screenshot.Screenshots.Selection.ADJUST
        shots.overviewKind = confOverviewStart().trim().let { name -> ShotKind.entries.firstOrNull { it.name.equals(name, true) } }
        shots.sharpWindows = confSharp()
        shots.sharpCornerRadius = confCornerRadius().toFloat()
        val behind = confSharpBackground().trim().lowercase()
        shots.sharpWallpaper = behind == "wallpaper"
        shots.sharpBackground = when (val bg = behind) {
            "none", "", "transparent", "wallpaper" -> null
            "theme" -> wm.barModel.colorsNow().island.let { dev.caulis.render.Color(it.red, it.green, it.blue, 1f) }
            else -> runCatching { dev.caulis.render.Color.hex(bg) }.getOrNull()
        }
        shots.fileName = confFileName()
        shots.format = dev.caulis.screenshot.ShotFormat.parse(confFormat()) ?: dev.caulis.screenshot.ShotFormat.PNG.also { log.warn("screenshots.format \"${confFormat()}\" is not png or jpg: png") }
        shots.jpegQuality = confQuality().coerceIn(10, 100) / 100f
        shots.then = confThen()
        // (A window is chosen by its key, W, or the toolbar's button; not by where the pointer happens to be.)
        shots.chooser = shots.chooser.copy(
            rememberKind = confRememberKind(), clickPicksWindow = confClickPicksWindow(), rememberSelection = confRememberSelection(),
            rememberAcrossRestarts = confRememberAcrossRestarts(), escapeClearsSelection = confEscapeClearsSelection(),
        )
        shots.style = style()
    }

    // The chooser in newm's look: the bar's colours (the wallpaper's), a pill, newm's words.
    private fun style(): dev.caulis.screenshot.ScreenshotStyle {
        val c = wm.barModel.colorsNow()
        fun of(x: androidx.compose.ui.graphics.Color, alpha: Float = x.alpha) = dev.caulis.render.Color(x.red, x.green, x.blue, alpha)
        val d = dev.caulis.screenshot.ScreenshotWords()
        val tr: (String) -> String = { newm.bar.tr(it) }
        val words = dev.caulis.screenshot.ScreenshotWords(
            area = tr(d.area), window = tr(d.window), screen = tr(d.screen), windows = tr(d.windows), adjustArea = tr(d.adjustArea),
            newArea = tr(d.newArea), pointerIn = tr(d.pointerIn), pointerOut = tr(d.pointerOut), pointerNotHere = tr(d.pointerNotHere),
            timer = tr(d.timer), timerOff = tr(d.timerOff), capture = tr(d.capture), close = tr(d.close), oneWindow = tr(d.oneWindow),
            manyWindows = tr(d.manyWindows), dragFirst = tr(d.dragFirst), pointFirst = tr(d.pointFirst), pickFirst = tr(d.pickFirst),
            noFocusedWindow = tr(d.noFocusedWindow), hiddenWindow = tr(d.hiddenWindow), countdown = tr(d.countdown), title = tr(d.title),
            savedTo = tr(d.savedTo), copied = tr(d.copied), notSaved = tr(d.notSaved), clearSelection = tr(d.clearSelection),
        )
        return dev.caulis.screenshot.ScreenshotStyle(
            background = of(c.island, 0.96f), text = of(c.text), dim = of(c.dim), border = of(c.edge), accent = of(c.accent),
            onAccent = of(newm.bar.onAccent(c)), cornerRadius = 18f, words = words,
        )
    }

    private fun enabled(): Boolean = confEnabled().also { if (!it) log.info { "Screenshots are off (screenshots { enabled = false })" } }

    /** The chooser: with the kind used last (over the overview: several windows, whole). */
    fun choose() {
        // (Its look again: the colours may have changed with the wallpaper.)
        shots.style = style()
        if (enabled()) shots.choose()
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
