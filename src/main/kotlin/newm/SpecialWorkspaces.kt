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
// Its windows look as the wall's: view { cornerRadius, padding } unless these say otherwise (-1: as view { }).
private val confCornerRadius = configuredValue("special_workspace.corner_radius", -1.0)
private val confPadding = configuredValue("special_workspace.padding", -1.0)
private val confViewCornerRadius = configuredValue("view.corner_radius", 12.0)
private val confViewPadding = configuredValue("view.padding", 6.0)
private val confUnfocusedOpacity = configuredValue("view.unfocused_opacity", 1.0)

/**
 * Hyprland's special workspaces in newm: Caulis draws them above the wall, on the focused
 * monitor at the screen's own place (the wall's camera does not move them), with their own
 * small layout: Caulis's dwindle inside the gap, centred, as big as size says — a screen of
 * its own, tiled as Hyprland's are (newm's screens { layout } places windows on the wall's
 * screens; a special workspace is no part of the wall). Its windows look as the wall's:
 * view { cornerRadius, padding } (padding around each window: twice it between two), the
 * focus border of focus { }, view { rules }' blur and opacity — specialWorkspace
 * { cornerRadius, padding } may differ.
 *
 * newm's views stay on the wall: a window moved into a special workspace leaves the wall (its
 * view goes), one moved out comes back to the wall as a new view, placed as a new window is
 * (next to the window focused before). While locked they hide (Caulis's hideOnLock, always on
 * in newm: nothing of a session shows on its lock).
 */
internal class NewmSpecialWorkspaces(private val wm: Layout) {

    private val log = dev.caulis.log.Logger("special")

    // Special workspaces the overview is hiding (gone at the loop's next turn).
    private val hiding = HashSet<String>()

    // Views on their way off the wall (told once, gone at the loop's next turn).
    private val leaving = HashSet<View>()

    // Whether the overview was shown at the last frame (it hides special workspaces as it opens).
    private var overviewBefore = false

    // The look of each window in a special workspace, worked out once (view { rules } are asked
    // then), until the configuration changes or it leaves.
    private val looks = java.util.WeakHashMap<dev.caulis.core.Window, dev.caulis.core.Effects>()

    // The corner radius and padding of special workspaces' windows (pixels).
    private fun cornerRadius(): Double = confCornerRadius().takeIf { it >= 0.0 } ?: confViewCornerRadius()
    private fun padding(): Double = confPadding().takeIf { it >= 0.0 } ?: confViewPadding()

    // Its own small layout: Caulis's dwindle, newm's padding around each window (twice it between two).
    private fun layout(): dev.caulis.tiling.Layout {
        val padding = Math.round(padding()).toInt().coerceIn(0, 200)
        return dev.caulis.tiling.Dwindle(dev.caulis.tiling.Gaps(inner = 2 * padding, outer = padding))
    }

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
                layout = layout(),
            )
        } catch (e: IllegalArgumentException) {
            log.warn("special_workspace: ${e.message} (the usual settings instead)")
            SpecialWorkspaces(layout = layout())
        }.also {
            // (The looks anew: the configuration changed.)
            looks.clear()
        }
    }

    /**
     * How Caulis draws [window] when it is in a special workspace (else [fx] as it is): newm's
     * corner radius, its focus border, view { rules }' blur and opacity, view { unfocusedOpacity }.
     */
    fun effectsOf(window: dev.caulis.core.Window, fx: dev.caulis.core.Effects): dev.caulis.core.Effects {
        if (wm.caulis.desktop.scratchpadOf(window)?.isSpecial != true) {
            looks.remove(window)
            return fx
        }
        return looks.getOrPut(window) {
            val rules = View.rulesOf(wm, window)
            val opacity = ((rules["opacity"] as? Number)?.toDouble() ?: 1.0).coerceIn(0.0, 1.0)
            val blur = (rules["blur"] as? Map<*, *>)?.let { b ->
                dev.caulis.core.Blur(((b["radius"] as? Number)?.toInt() ?: 6).coerceAtLeast(1), ((b["passes"] as? Number)?.toInt() ?: 4).coerceIn(1, dev.caulis.render.MAX_BLUR_PASSES))
            }
            val border = if (rules["focus"] != false) wm.focusBorders.caulisBorder() else null
            fx.copy(
                cornerRadius = Math.round(cornerRadius()).toInt().coerceIn(0, 500),
                border = border,
                blur = blur ?: fx.blur,
                opacity = opacity.toFloat(),
                inactiveOpacity = (opacity * confUnfocusedOpacity().coerceIn(0.0, 1.0)).toFloat(),
            ).also { log.info { "$window in a special workspace: newm's look (corners ${it.cornerRadius}, border ${if (border != null) "on" else "off"}, opacity $opacity${if (blur != null) ", blur" else ""})" } }
        }
    }

    /** The special workspace [name] is shown on the focused monitor now. */
    fun shownHere(name: String): Boolean {
        val desktop = wm.caulis.desktop
        val plain = name.trim().removePrefix("special:").trim().ifEmpty { SpecialWorkspaces.DEFAULT_NAME }
        return desktop.shownSpecialOn(desktop.focusedMonitor)?.name == plain
    }

    /**
     * Super+drag on a window of a shown special workspace: it follows the pointer, and dropped
     * on the dimmed wall around the special workspace it comes back onto the wall (a view of
     * newm's, placed as a new window is); dropped within it, back to its tile. Null: the
     * pointer is on no such window.
     */
    fun dragOverlay(): Overlay? {
        val desktop = wm.caulis.desktop
        val window = desktop.windowAt(desktop.pointerX, desktop.pointerY) ?: return null
        val pad = desktop.scratchpadOf(window)?.takeIf { it.isSpecial } ?: return null
        log.info { "$window of the special workspace ${pad.name} dragged" }
        return SpecialDragOverlay(wm, window)
    }

    // Windows told in the log as opening in a special workspace (once each).
    private val toldOpening = java.util.Collections.newSetFromMap(java.util.WeakHashMap<dev.caulis.core.Window, Boolean>())

    /**
     * True while [window] has not appeared yet and a rule sends it into a special workspace
     * (Caulis' windowRule { specialWorkspace }, or view { rules } rule(specialWorkspace)):
     * newm leaves it alone, so it opens straight in there — never first on the wall.
     */
    fun opensInSpecial(window: dev.caulis.core.Window): Boolean {
        if (window.mapped) return false
        val name = try {
            wm.caulis.desktop.placementFor(window)?.specialWorkspace
        } catch (e: Exception) {
            log.warn("A rule failed for $window: $e")
            null
        } ?: return false
        if (toldOpening.add(window)) log.info { "$window: a rule sends it into the special workspace $name: not on the wall" }
        return true
    }

    /**
     * view { rules } saying rule(specialWorkspace = "music") for a window: put to Caulis's
     * rules (Desktop.placementFor), which decide where a window opens before its first frame.
     * Caulis' own windowRule { specialWorkspace } says first.
     */
    fun takeOverRules() {
        val desktop = wm.caulis.desktop
        val caulisRules = desktop.placementFor
        desktop.placementFor = { window ->
            val placement = caulisRules(window)
            if (placement?.specialWorkspace != null) placement
            else View.specialWorkspaceRuleOf(wm, window)?.let { name ->
                try {
                    (placement ?: dev.caulis.core.WindowPlacement.USUAL).and(dev.caulis.core.WindowPlacement(specialWorkspace = name))
                } catch (e: IllegalArgumentException) {
                    log.warn("view { rules }: rule(specialWorkspace = \"$name\") for $window: ${e.message}")
                    null
                }
            } ?: placement
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
        // (As the overview opens only: one shown while it is open — Super+M there — stays.)
        val opening = overview && !overviewBefore
        overviewBefore = overview
        if (opening && confHideInOverview()) {
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

/**
 * A window of a special workspace dragged with Super and the mouse (or one finger): it
 * follows, and is dropped where the gesture ends — outside the special workspace onto the
 * wall (Caulis's dropFromSpecialWorkspace; newm then makes it a view), else back to its tile.
 */
internal class SpecialDragOverlay(layout: Layout, private val window: dev.caulis.core.Window) : Overlay(layout) {
    private val log = dev.caulis.log.Logger("special")
    private val desktop = layout.caulis.desktop
    private val startX = desktop.pointerX
    private val startY = desktop.pointerY
    private var dx = 0.0
    private var dy = 0.0
    private var dropped = false

    // The pixels a gesture's 1 is: as the gesture provider counts them for a drag (one to one).
    private fun scale(): Double = layout.dragScale()

    private fun moved(ndx: Double, ndy: Double) {
        dx = ndx
        dy = ndy
        desktop.dragInSpecialWorkspace(window, Math.round(dx).toInt(), Math.round(dy).toInt())
    }

    private fun drop() {
        if (dropped) return
        dropped = true
        val x = startX + dx
        val y = startY + dy
        val out = desktop.dropFromSpecialWorkspace(window, x, y)
        log.info { "$window dropped ${if (out) "on the wall: out of its special workspace" else "within its special workspace: back to its tile"}" }
        layout.exitOverlay()
    }

    // (Dropped before the layout's queue started it — a quick flick: it ends as it starts.)
    override fun postInit() {
        if (dropped) layout.exitOverlay()
    }

    override fun onMotion(timeMillis: Int, dx: Double, dy: Double): Boolean = false

    override fun onGesture(gesture: NewmGesture): Boolean {
        gesture.listener(GestureListener({ values -> moved((values["delta_x"] ?: 0.0) * scale(), (values["delta_y"] ?: 0.0) * scale()) }, ::drop))
        return true
    }

    override fun preDestroy() {
        // (Ended some other way — a reload, the lock: back to its tile.)
        if (!dropped) {
            dropped = true
            desktop.dropFromSpecialWorkspace(window, startX, startY)
        }
    }
}
