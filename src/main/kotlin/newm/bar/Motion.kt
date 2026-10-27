package newm.bar

import dev.caulis.compose.WidgetLook

/**
 * Something appearing and going away by the clock (not drawn anew for it: the compositor shows
 * the picture see-through and a little smaller, see [appearance]). [show] and [hide] go on from
 * where it is now, so a quick second press turns it round smoothly. How long and along which
 * curve are the animations "[name]In" and "[name]Out" (see [newm.NewmAnimations]; [inMillis]
 * and [outMillis] their usual times): off, it is there (or gone) at once.
 */
class Fade(private val name: String, private val inMillis: Long = 200, private val outMillis: Long = 150) {
    private var from = 0f
    private var to = 0f
    private var since = 0L

    // The clock starts with the first frame shown, not with the press: a first frame that takes
    // long to draw (a menu opened the first time) would else eat the whole animation.
    private var waiting = false

    // The animation of the way it goes now: coming (fast at first, gently at the end) or going (smooth).
    private fun animation(coming: Boolean) =
        if (coming) newm.NewmAnimations.resolve("${name}In", inMillis, dev.caulis.core.Curve.EASE_OUT)
        else newm.NewmAnimations.resolve("${name}Out", outMillis, dev.caulis.core.Curve.SMOOTH)

    // How much of its time has passed (0..1).
    private fun time(now: Long): Float {
        if (waiting) return 0f
        val animation = animation(to > from)
        if (!animation.on) return 1f
        return ((now - since) / 1_000_000.0 / animation.millis).coerceIn(0.0, 1.0).toFloat()
    }

    fun show() = go(1f)

    fun hide() = go(0f)

    private fun go(target: Float) {
        val now = System.nanoTime()
        if (to == target) return
        from = value(now)
        to = target
        since = now
        // (Without the animation: there at once, not a frame later.)
        waiting = target > from && animation(true).on
    }

    /** Its first frame is on screen now: the clock starts. */
    fun started(now: Long = System.nanoTime()) {
        if (!waiting) return
        waiting = false
        since = now
    }

    /** True once shown (or appearing). */
    val shown: Boolean get() = to > 0f

    /** 0 (gone) .. 1 (shown), along its curve. */
    fun value(now: Long = System.nanoTime()): Float {
        if (waiting) return from
        val t = time(now)
        if (t >= 1f) return to
        val e = try {
            animation(to > from).curve.at(t.toDouble()).toFloat()
        } catch (e: Exception) {
            t
        }
        return from + (to - from) * e
    }

    /** Still changing. */
    val moving: Boolean get() = waiting || time(System.nanoTime()) < 1f

    /** Visible at all (shown, or still going away). */
    val visible: Boolean get() = to > 0f || moving

    /**
     * How it looks now: see-through, a little smaller ([smallest]) and moved by [rise] while it
     * comes and goes — or as the animation's look says ("fade", "popin 90%", "slide up 20").
     */
    fun appearance(rise: Float = -8f, smallest: Float = 0.96f, unfold: Boolean = false): WidgetLook {
        started()
        val v = value()
        if (v >= 1f) return WidgetLook.SHOWN
        val style = animation(to > from).style?.let(newm.FadeStyle::parse)
        val r = style?.rise ?: rise
        val s = style?.smallest ?: smallest
        return WidgetLook(alpha = v, scale = s + (1f - s) * v, dy = (1f - v) * r, reveal = if (unfold && style == null) 0.55f + 0.45f * v else 1f)
    }
}
