package newm

import dev.caulis.core.Blur
import dev.caulis.core.SpecialWorkspaces

private val confDim = configuredValue("special_workspace.dim", 0.4)
private val confBlurRadius = configuredValue("special_workspace.blur_radius", 0)
private val confBlurPasses = configuredValue("special_workspace.blur_passes", 2)
private val confGap = configuredValue("special_workspace.gap", 40)
private val confSize = configuredValue("special_workspace.size", 1.0)
private val confAnimation = configuredValue("special_workspace.animation", "scratchpads")
private val confHideWhenEmpty = configuredValue("special_workspace.hide_when_empty", true)
private val confNewWindowsJoin = configuredValue("special_workspace.new_windows_join", true)
private val confClickOutsideHides = configuredValue("special_workspace.click_outside_hides", true)
// The overview of the wall shows newm's views only: a special workspace shown hides when it opens.
private val confHideInOverview = configuredValue("special_workspace.hide_in_overview", true)

/**
 * Hyprland's special workspaces in newm: Caulis draws them above the wall, on the focused
 * monitor at the screen's own place (the wall's camera does not move them), with their own
 * small layout (Caulis's dwindle inside the gap). newm's views stay on the wall: a window
 * moved into a special workspace leaves the wall (its view goes), one moved out comes back
 * to the wall as a new view. While locked they hide (Caulis's hideOnLock, always on in newm:
 * nothing of a session shows on its lock).
 */
internal class NewmSpecialWorkspaces(private val wm: Layout) {

    private val log = dev.caulis.log.Logger("special")

    // Special workspaces the overview is hiding (gone at the loop's next turn).
    private val hiding = HashSet<String>()

    // Views on their way off the wall (told once, gone at the loop's next turn).
    private val leaving = HashSet<View>()

    /** The settings of special_workspace { } for Caulis. */
    fun settings(): SpecialWorkspaces {
        val radius = confBlurRadius().coerceIn(0, 64)
        return try {
            SpecialWorkspaces(
                dim = confDim().toFloat().coerceIn(0f, 1f),
                blur = if (radius > 0) Blur(radius, confBlurPasses().coerceIn(1, dev.caulis.render.MAX_BLUR_PASSES)) else null,
                gap = confGap().coerceIn(0, 1000),
                size = confSize().coerceIn(0.2, 1.0),
                animation = confAnimation(),
                hideWhenEmpty = confHideWhenEmpty(),
                newWindowsJoin = confNewWindowsJoin(),
                hideOnLock = true,
                clickOutsideHides = confClickOutsideHides(),
                layout = dev.caulis.tiling.Dwindle(dev.caulis.tiling.Gaps(inner = 8, outer = 0)),
            )
        } catch (e: IllegalArgumentException) {
            log.warn("special_workspace: ${e.message} (the usual settings instead)")
            SpecialWorkspaces(layout = dev.caulis.tiling.Dwindle(dev.caulis.tiling.Gaps(inner = 8, outer = 0)))
        }
    }

    /** Once a frame: views of windows that went into a special workspace go; the overview hides them. */
    fun everyFrame(overview: Boolean) {
        val desktop = wm.caulis.desktop
        for (view in wm.viewsNow()) {
            if (!view.destroyed && !view.isPanel() && view !in leaving && desktop.scratchpadOf(view.window)?.isSpecial == true) {
                log.info { "${view.window} went into a special workspace: its view leaves the wall" }
                leaving += view
                wm.caulis.post {
                    leaving -= view
                    if (wm.viewOf(view.window) === view) view.destroy()
                }
            }
        }
        if (overview && confHideInOverview()) {
            for (monitor in desktop.monitors) desktop.shownSpecialOn(monitor)?.let { pad ->
                // (Hidden at the loop's next turn: told, and asked, once till then.)
                if (!hiding.add(pad.name)) return@let
                log.info { "The overview opens: special workspace ${pad.name} hidden" }
                wm.caulis.post {
                    hiding -= pad.name
                    desktop.hideSpecialWorkspace(pad.name)
                }
            }
        }
    }
}
