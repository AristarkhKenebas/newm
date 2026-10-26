package newm

import dev.caulis.core.OverlayCanvas
import dev.caulis.geometry.Rect
import dev.caulis.render.Paint

private val confViewCornerRadius = configuredValue("view.corner_radius", 12.0)
private val confFocusD = configuredValue("focus.distance", 4.0)
private val confFocusW = configuredValue("focus.width", 2.0)
private val confAnimTime = configuredValue("focus.anim_time", 0.3)
private val confAnimT = configuredValue("anim_time", 0.3)
private val confAnimateOnChange = configuredValue("focus.animate_on_change", false)
private val confEnabled = configuredValue("focus.enabled", true)
// "auto": the colours' accent (the palette's, else newm's light blue).
private val confColor = configuredValue("focus.color", "auto")

/**
 * newm's widget/focus_border.py: the line around the focused view, a little outside it, gliding
 * along when the view moves or the focus changes. pywm drew one widget per output; Caulis draws
 * it once, just below the focused window (the director's decoration of it).
 */
class FocusBorders(private val wm: Layout) : Animatable {

    // (z index, the box: x, y, width, height; and the workspace it is on). NONE: no border.
    // (z index, the box, its workspace, and how opaque the window is: the border fades with it.)
    private data class Current(val z: Double, val box: Box, val workspace: Box?, val alpha: Double = 1.0) {
        companion object {
            val NONE = Current(-999.0, Box.ZERO, null)
        }
    }

    private val border = object : Animate<WidgetDown>() {
        fun at(default: WidgetDown) = process(default)
        fun start(from: WidgetDown, to: WidgetDown, dt: Double) = startAnimation(WidgetDownInterpolation(from, to), dt)
        override fun animDamage() = wm.damage()
    }

    private var skipNextAnimate = false
    var currentView: View? = null
        private set
    private var current = Current.NONE
    private var cornerRadius = confViewCornerRadius() + confFocusD()

    /** The configuration changed (pywm: the widgets are made anew). */
    fun update() = damage()

    private fun reducer(c: Current, opacity: Double): WidgetDown {
        val d = confFocusD()
        if (c.box.w == 0.0 || c.box.h == 0.0) return WidgetDown(0.0, Box.ZERO, opacity = 0.0)
        return WidgetDown(c.z, Box(c.box.x - d, c.box.y - d, c.box.w + 2 * d, c.box.h + 2 * d), opacity = opacity * c.alpha, workspace = c.workspace)
    }

    private fun setBoxAndRadius(layoutState: LayoutState? = null) {
        val state = layoutState ?: wm.state
        val view = currentView
        val up = view?.upState
        if (view != null && up != null) {
            val down = view.reducer(up, state)
            current = if (down.isFullscreen) Current.NONE else Current(down.zIndex - 0.01, down.logicalBox, down.workspace, down.opacity)
            cornerRadius = down.cornerRadius + confFocusD()
        } else {
            current = Current.NONE
            cornerRadius = confViewCornerRadius() + confFocusD()
        }
    }

    fun updateFocus(view: View, presentStates: Pair<LayoutState?, LayoutState?>? = null) {
        if (view === currentView) return
        var animate = confAnimateOnChange()
        if (presentStates != null) {
            skipNextAnimate = true
            animate = true
        }
        var old = current
        currentView = view
        // A window opening: the border goes with it, from where (and how see-through) it starts.
        presentStates?.first?.let { start ->
            setBoxAndRadius(start)
            old = current
        }
        setBoxAndRadius(presentStates?.second)
        // From nothing (the first window): it appears in place, not flying in from the corner.
        if (old.box.w <= 0.0 || old.box.h <= 0.0) old = current.copy(alpha = 0.0)
        if (animate) border.start(reducer(old, 1.0), reducer(current, 1.0), if (presentStates != null) confAnimT() else confAnimTime()) else damage()
    }

    fun unfocus() {
        skipNextAnimate = true
        val old = current
        currentView = null
        setBoxAndRadius()
        border.start(reducer(old, 1.0), reducer(current, 1.0), confAnimTime())
    }

    override fun animate(oldState: LayoutState, newState: LayoutState, dt: Double) {
        if (skipNextAnimate) {
            skipNextAnimate = false
            return
        }
        val view = currentView ?: return
        val up = view.upState ?: return
        fun of(state: LayoutState): Current {
            val down = view.reducer(up, state)
            return if (down.isFullscreen) Current.NONE else Current(down.zIndex - 0.01, down.logicalBox, down.workspace, down.opacity)
        }
        var old = of(oldState)
        val new = of(newState)
        if (old.box.w <= 0.0 || old.box.h <= 0.0) old = new.copy(alpha = 0.0)
        current = new
        border.start(reducer(old, oldState.backgroundOpacity), reducer(new, newState.backgroundOpacity), dt)
    }

    override fun flushAnimation() = border.flushAnimationOwn()

    fun damage() {
        setBoxAndRadius()
        wm.damage()
    }

    /** True while it glides. */
    val animating: Boolean get() = border.animating

    /** Draws the border now (with the focused window's pictures below it). */
    fun draw(canvas: OverlayCanvas) {
        if (!confEnabled()) return
        // A menu of newm's has the keys (the launcher, the clipboard): no window looks chosen.
        if (wm.caulis.desktop.keyboardWidget != null) return
        val down = border.at(reducer(current, 1.0))
        if (down.opacity <= 0.0 || down.box.w <= 0.0 || down.box.h <= 0.0) return
        val b = down.box
        val rect = Rect(Math.round(b.x).toInt(), Math.round(b.y).toInt(), Math.round(b.w).toInt(), Math.round(b.h).toInt())
        canvas.border(rect, confFocusW().toFloat(), Paint.solid(borderColor()), cornerRadius.toFloat(), down.opacity.toFloat())
    }

    // The configured colour, or for "auto" the palette's accent (a little see-through).
    private fun borderColor(): dev.caulis.render.Color {
        val configured = confColor()
        if (configured != "auto") return runCatching { color(configured) }.getOrElse { color("#88C0D0B0") }
        val dark = wm.caulis.colorSchemeNow == dev.caulis.core.appearance.ColorScheme.DARK
        val accent = wm.barModel.palette?.scheme(dark)?.primary ?: return color("#88C0D0B0")
        return accent.withAlpha(0.8f)
    }
}
