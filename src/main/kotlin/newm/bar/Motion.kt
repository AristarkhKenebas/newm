package newm.bar

import dev.caulis.compose.WidgetLook

/**
 * Something appearing and going away by the clock (not drawn anew for it: the compositor shows
 * the picture see-through and a little smaller, see [appearance]). [show] and [hide] go on from
 * where it is now, so a quick second press turns it round smoothly.
 */
class Fade(private val inMillis: Long = 200, private val outMillis: Long = 150) {
    private var from = 0f
    private var to = 0f
    private var since = 0L

    // The clock starts with the first frame shown, not with the press: a first frame that takes
    // long to draw (a menu opened the first time) would else eat the whole animation.
    private var waiting = false

    private fun raw(now: Long): Float {
        if (waiting) return from
        val millis = if (to > from) inMillis else outMillis
        val t = ((now - since) / 1_000_000.0 / millis).coerceIn(0.0, 1.0).toFloat()
        return from + (to - from) * t
    }

    fun show() = go(1f)

    fun hide() = go(0f)

    private fun go(target: Float) {
        val now = System.nanoTime()
        if (to == target) return
        from = raw(now)
        to = target
        since = now
        waiting = target > from
    }

    /** Its first frame is on screen now: the clock starts. */
    fun started(now: Long = System.nanoTime()) {
        if (!waiting) return
        waiting = false
        since = now
    }

    /** True once shown (or appearing). */
    val shown: Boolean get() = to > 0f

    /** 0 (gone) .. 1 (shown), eased: quick at first, gently at the end. */
    fun value(now: Long = System.nanoTime()): Float {
        val v = raw(now)
        return if (to > from) 1f - (1f - v) * (1f - v) * (1f - v) else v * v * (3f - 2f * v)
    }

    /** Still changing. */
    val moving: Boolean get() = waiting || raw(System.nanoTime()) != to

    /** Visible at all (shown, or still going away). */
    val visible: Boolean get() = to > 0f || moving

    /** How it looks now: see-through, a little smaller and moved by [rise] while it comes and goes. */
    fun appearance(rise: Float = -8f, smallest: Float = 0.96f, unfold: Boolean = false): WidgetLook {
        started()
        val v = value()
        if (v >= 1f) return WidgetLook.SHOWN
        return WidgetLook(alpha = v, scale = smallest + (1f - smallest) * v, dy = (1f - v) * rise, reveal = if (unfold) 0.55f + 0.45f * v else 1f)
    }
}
