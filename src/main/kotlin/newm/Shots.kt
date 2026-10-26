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
            wm.caulis.post {
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
    }

    private fun enabled(): Boolean = confEnabled().also { if (!it) log.info { "Screenshots are off (screenshots { enabled = false })" } }

    /** The chooser, starting with a part of the screen. */
    fun choose() {
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
