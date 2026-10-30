package newm

import dev.caulis.core.OverlayCanvas
import dev.caulis.geometry.Rect
import dev.caulis.render.Paint

private val confViewCornerRadius = configuredValue("view.corner_radius", 12.0)
private val confFocusD = configuredValue("focus.distance", 4.0)
private val confFocusW = configuredValue("focus.width", 2.0)
// The border's own glide (the animation "border"): its usual time.
private val confAnimTime = configuredValue("focus.anim_time", 0.3)
private val confAnimateOnChange = configuredValue("focus.animate_on_change", false)
private val confEnabled = configuredValue("focus.enabled", true)
// "auto": the colours' accent (the palette's, else newm's light blue).
private val confColor = configuredValue("focus.color", "auto")
// A window wanting attention: its border while it has not the focus ("": none; the focus takes the wish away).
private val confUrgentColor = configuredValue("focus.urgent_color", "")
// A GLSL file of one's own (vec4 paint(vec2 at), read again when saved); "": the colour.
private val confShader = configuredValue("focus.shader", "")
// False: a shader's own time (and a turning gradient) stands still.
private val confAnimate = configuredValue("focus.animate", true)
// A moving border drawn anew at most this often a second (0: every frame of the screen).
internal val confFocusFps = configuredValue("focus.fps", 0)

private val log = dev.caulis.log.Logger("focus")

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
        fun start(from: WidgetDown, to: WidgetDown, dt: Double, curve: dev.caulis.core.Curve) = startAnimation(WidgetDownInterpolation(from, to), dt, curve)

        // Its own glide, as the animation "border" says.
        fun glide(from: WidgetDown, to: WidgetDown) = NewmAnimations.resolveSeconds("border", confAnimTime()).let { start(from, to, it.seconds, it.curve) }
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
        // (With the layout's animation the window is part of — a window opening, one closing —
        // as long and along the same curve; else its own glide.)
        if (!animate) damage()
        else if (presentStates != null) wm.runningAnimation.let { (dt, curve) -> border.start(reducer(old, 1.0), reducer(current, 1.0), dt, curve) }
        else border.glide(reducer(old, 1.0), reducer(current, 1.0))
    }

    fun unfocus() {
        skipNextAnimate = true
        val old = current
        currentView = null
        setBoxAndRadius()
        border.glide(reducer(old, 1.0), reducer(current, 1.0))
    }

    override fun animate(oldState: LayoutState, newState: LayoutState, dt: Double, curve: dev.caulis.core.Curve) {
        val view = currentView
        val up = view?.upState
        fun of(state: LayoutState): Current {
            val down = view!!.reducer(up!!, state)
            return if (down.isFullscreen) Current.NONE else Current(down.zIndex - 0.01, down.logicalBox, down.workspace, down.opacity)
        }
        if (skipNextAnimate) {
            skipNextAnimate = false
            // (Its own glide runs already; but where it ends is this state's — else, the glide
            // over, it stayed where the window was before: a frame around nothing.)
            if (view != null && up != null && !border.animating) current = of(newState)
            return
        }
        if (view == null || up == null) return
        var old = of(oldState)
        val new = of(newState)
        if (old.box.w <= 0.0 || old.box.h <= 0.0) old = new.copy(alpha = 0.0)
        current = new
        border.start(reducer(old, oldState.backgroundOpacity), reducer(new, newState.backgroundOpacity), dt, curve)
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
        // A window being moved or resized: it is chosen plainly, no border (it lagged behind it).
        if (wm.overlay is MoveResizeOverlay || wm.overlay is MoveResizeFloatingOverlay) return
        // At rest: around the window where it is now (never where it was).
        if (!border.animating) setBoxAndRadius()
        val down = border.at(reducer(current, 1.0))
        if (down.opacity <= 0.0 || down.box.w <= 0.0 || down.box.h <= 0.0) return
        val b = down.box
        val rect = Rect(Math.round(b.x).toInt(), Math.round(b.y).toInt(), Math.round(b.w).toInt(), Math.round(b.h).toInt())
        val urgent = currentView?.window?.urgent == true
        canvas.border(rect, confFocusW().toFloat(), borderPaint(urgent), cornerRadius.toFloat(), down.opacity.toFloat(), dev.caulis.render.PaintState(focused = true, urgent = urgent), confAnimate())
    }

    /**
     * Draws the border of a window wanting attention that has not the focus (as Hyprland does:
     * the focus takes a window's wish for attention away, so the focused one never shows it)
     * around [place], where the window is drawn now — when focus { urgentColor } is set.
     */
    fun drawUrgent(place: dev.caulis.core.WindowPlace, canvas: OverlayCanvas) {
        if (!confEnabled() || confUrgentColor().isBlank() || place.alpha <= 0f) return
        val d = confFocusD()
        val rect = Rect(Math.round(place.x - d).toInt(), Math.round(place.y - d).toInt(), Math.round(place.width + 2 * d).toInt(), Math.round(place.height + 2 * d).toInt())
        if (rect.width <= 0 || rect.height <= 0) return
        val radius = (place.cornerRadius?.toDouble() ?: confViewCornerRadius()) + d
        canvas.border(rect, confFocusW().toFloat(), borderPaint(true), radius.toFloat(), place.alpha, dev.caulis.render.PaintState(focused = false, urgent = true), confAnimate())
    }

    /**
     * The focus border as Caulis draws it around the windows of a special workspace (they have
     * no view of newm's, so no border of this class): as wide, the same paint (the colour, a
     * gradient, the shader; the urgent colour), nothing around the windows without the focus.
     * Null with focus { enabled = false }.
     */
    internal fun caulisBorder(): dev.caulis.core.Border? {
        if (!confEnabled()) return null
        return dev.caulis.core.Border(
            width = Math.round(confFocusW()).toInt().coerceAtLeast(1),
            focused = borderPaint(false),
            unfocused = Paint.solid(dev.caulis.render.Color.TRANSPARENT),
            urgent = confUrgentColor().takeIf { it.isNotBlank() }?.let(::paintOf),
            animate = confAnimate(),
        )
    }

    // The shader of focus.shader, made once per file (it reads the file again when it is saved).
    private var shader: Pair<String, Paint.Shader>? = null

    // What the border is filled with: the shader (handed the colour and the urgent one), the
    // urgent colour, or the colour — each a colour, a gradient or Hyprland's text
    // ("rgba(33ccffee) rgba(00ff99ee) 45deg").
    private fun borderPaint(urgent: Boolean): Paint {
        val usual = paintOf(confColor()) ?: Paint.solid(borderColor())
        val urgentPaint = confUrgentColor().takeIf { it.isNotBlank() }?.let(::paintOf)
        val file = confShader().trim()
        if (file.isNotEmpty()) {
            val made = shader?.takeIf { it.first == file }?.second ?: builtInShader(file)?.also { shader = file to it }
                ?: dev.caulis.core.Paints.file(wm.caulis.file(file)).also { shader = file to it }
            return made.withColors(usual.firstColor(), (urgentPaint ?: usual).firstColor())
        }
        return if (urgent && urgentPaint != null) urgentPaint else usual
    }

    // One of newm's own border shaders by name ("jadefire": jade flowing into amber, a spark going round), or null.
    private fun builtInShader(name: String): Paint.Shader? {
        if (!name.matches(Regex("[a-z0-9-]+"))) return null
        val code = FocusBorders::class.java.getResourceAsStream("/newm/borders/$name.glsl")?.use { String(it.readAllBytes()) } ?: return null
        log.info { "Focus border shader: $name (newm's own)" }
        return Paint.Shader({ code }, name)
    }

    // A colour or gradient as configured (null for "auto" or a mistake, which is logged once).
    // (Read once per text, not each frame.)
    private var badColor: String? = null
    private val paints = HashMap<String, Paint?>()
    private fun paintOf(text: String): Paint? {
        if (text == "auto") return null
        if (text in paints) return paints[text]
        if (paints.size > 16) paints.clear()
        return readPaint(text).also { paints[text] = it }
    }

    private fun readPaint(text: String): Paint? {
        runCatching { color(text) }.getOrNull()?.let { return Paint.solid(it) }
        return try {
            Paint.parse(text)
        } catch (e: IllegalArgumentException) {
            if (badColor != text) {
                badColor = text
                log.warn("focus: \"$text\" is no colour: ${e.message}")
            }
            null
        }
    }

    // The configured colour, or for "auto" the palette's accent (a little see-through).
    private fun borderColor(): dev.caulis.render.Color {
        val configured = confColor()
        if (configured != "auto") return runCatching { color(configured) }.getOrElse { runCatching { Paint.parse(configured).firstColor() }.getOrElse { color("#88C0D0B0") } }
        val dark = wm.caulis.colorSchemeNow == dev.caulis.core.appearance.ColorScheme.DARK
        // (A look of newm's own: its accent.)
        newm.bar.NewmColors.theme()?.let { look -> (if (dark) look.dark else look.light).accent.let { a -> return dev.caulis.render.Color(a.red, a.green, a.blue, 0.85f) } }
        val accent = wm.barModel.palette?.scheme(dark)?.primary ?: return color("#88C0D0B0")
        return accent.withAlpha(0.8f)
    }
}
