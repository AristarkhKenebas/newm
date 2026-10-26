package newm

import dev.caulis.core.Monitor

/** A rectangle with fractions: x, y, width, height (newm's 4-tuples). */
data class Box(val x: Double, val y: Double, val w: Double, val h: Double) {
    fun mix(to: Box, at: Double) = Box(x + (to.x - x) * at, y + (to.y - y) * at, w + (to.w - w) * at, h + (to.h - h) * at)

    companion object {
        val ZERO = Box(0.0, 0.0, 0.0, 0.0)
        val NO_MASK = Box(-1.0, -1.0, -1.0, -1.0)
    }
}

/**
 * How a view is shown at one moment (pywm's view "downstream state" plus newm's
 * CustomDownstreamState): where it is drawn ([box], desktop units — stretched when it is not
 * the window's own size), the part of it shown ([mask], relative to [box]; [Box.NO_MASK]:
 * all), its order, look and the size it is asked to have.
 */
data class ViewDown(
    var zIndex: Double = 0.0,
    var box: Box = Box.ZERO,
    var mask: Box = Box.NO_MASK,
    var opacity: Double = 1.0,
    var cornerRadius: Double = 0.0,
    var acceptsInput: Boolean = false,
    var lockEnabled: Boolean = false,
    var floating: Boolean? = null,
    var workspace: Box? = null,
    var fixedOutput: Monitor? = null,
    /** The size asked of the program ((-1, -1): its own). */
    var size: Pair<Int, Int> = -1 to -1,
    var logicalBox: Box = box,
    var isFullscreen: Boolean = false,
    /**
     * Being resized (the size asked changes, the zoom does not): desktop units per pixel of the
     * program's picture, which is then drawn at its own size from the place's top left and cut
     * to it (not stretched: stretched, it jumped when the new picture came — a flicker). 0: not.
     */
    var resizeScale: Double = 0.0,
)

/** The layout's own downstream state: how far the lock screen and the launcher are in (0..1). */
data class LayoutDown(val lockPerc: Double, val launcherPerc: Double = 0.0)

/** newm's interpolation.py: a state at a point of the way between two. */
fun interface Interpolation<T> {
    fun get(at: Double): T
}

private val confSizeAdjustment = configuredValue("interpolation.size_adjustment", .5)

// view { crispResize = true }: a window being resized drawn at its own size, not stretched (false: as newm).
private val confCrispResize = configuredValue("view.crisp_resize", true)

class LayoutDownInterpolation(state0: LayoutDown, state1: LayoutDown) : Interpolation<LayoutDown> {
    private val lock = state0.lockPerc to state1.lockPerc
    private val launcher = state0.launcherPerc to state1.launcherPerc

    override fun get(at: Double): LayoutDown {
        val t = at.coerceIn(0.0, 1.0)
        var lockPerc = lock.first + t * (lock.second - lock.first)
        if (lockPerc < 0.0001) lockPerc = 0.0
        var launcherPerc = launcher.first + t * (launcher.second - launcher.first)
        if (launcherPerc < 0.0001) launcherPerc = 0.0
        return LayoutDown(lockPerc, launcherPerc)
    }
}

class ViewDownInterpolation(layout: Layout, state0: ViewDown, state1: ViewDown) : Interpolation<ViewDown> {
    private val zIndex = state0.zIndex to state1.zIndex
    private val box = state0.box to state1.box
    private val mask = state0.mask to state1.mask
    private val cornerRadius = state0.cornerRadius to state1.cornerRadius
    private val acceptsInput = state1.acceptsInput
    private val size = state0.size to state1.size
    private val opacity = state0.opacity to state1.opacity
    private val lockEnabled = state0.lockEnabled
    private val floating = state0.floating to state1.floating
    private val fixedOutput = state0.fixedOutput to state1.fixedOutput
    private val workspace: Box?
    private var anim = true
    private var sizeAdjustment: Double
    private var resizeScale = 0.0

    init {
        val w0 = state0.workspace
        val w1 = state1.workspace
        workspace = if (w0 != null && w1 != null) {
            var x = w0.x
            var y = w0.y
            var w = w0.w
            var h = w0.h
            if (w1.x < x) {
                w += (x - w1.x)
                x = w1.x
            }
            if (w1.y < y) {
                h += (y - w1.y)
                y = w1.y
            }
            if (w1.x + w1.w > x + w) w = w1.x + w1.w - x
            if (w1.y + w1.h > y + h) h = w1.y + w1.h - y
            Box(x, y, w, h)
        } else {
            null
        }

        if (workspace != null) {
            for (ws in layout.workspaces) {
                if (!ws.preventAnim) continue
                if (ws.posX <= workspace.x && workspace.x <= workspace.x + workspace.w && workspace.x + workspace.w <= ws.posX + ws.width &&
                    ws.posY <= workspace.y && workspace.y <= workspace.y + workspace.h && workspace.y + workspace.h <= ws.posY + ws.height
                ) {
                    anim = false
                    break
                }
            }
        }

        // If the initial window is not visible - trigger (computationally intensive) size change immediately
        // If the final window is not visible - trigger (computationally intensive) size change only afterwards
        sizeAdjustment = confSizeAdjustment()
        fun outside(b: Box, ws: Box) = (b.x + b.w - 1.0 < ws.x) || (b.y + b.h - 1.0 < ws.y) || (ws.x + ws.w - 1.0 < b.x) || (ws.y + ws.h - 1.0 < b.y)
        if (w0 != null && outside(state0.box, w0)) sizeAdjustment = 0.0
        if (w1 != null && outside(state1.box, w1)) sizeAdjustment = 0.99
        // Resized, not zoomed (the same units per pixel before and after): the program is asked
        // for its new size at once and drawn unstretched while its place glides (see resizeScale).
        val (a0, a1) = size
        if (confCrispResize() && a0.first > 0 && a0.second > 0 && a1.first > 0 && a1.second > 0 && a0 != a1 && state1.box.w > 0 && state0.box.w > 0) {
            val k0 = state0.box.w / a0.first
            val k1 = state1.box.w / a1.first
            if (Math.abs(k0 - k1) < 0.02 * k1) {
                sizeAdjustment = -1.0
                resizeScale = k1
            }
        }
    }

    override fun get(at: Double): ViewDown {
        val t = (if (!anim) 1.0 else at).coerceIn(0.0, 1.0)
        return ViewDown(
            zIndex = if (t > 0.5) zIndex.second else zIndex.first,
            box = box.first.mix(box.second, t),
            mask = mask.first.mix(mask.second, t),
            cornerRadius = cornerRadius.first + t * (cornerRadius.second - cornerRadius.first),
            acceptsInput = acceptsInput,
        ).also { res ->
            res.opacity = opacity.first + t * (opacity.second - opacity.first)
            res.size = if (t > sizeAdjustment) size.second else size.first
            res.floating = if (t > sizeAdjustment) floating.second else floating.first
            res.lockEnabled = lockEnabled
            res.workspace = workspace
            res.fixedOutput = if (t > sizeAdjustment) fixedOutput.second else fixedOutput.first
            res.logicalBox = res.box
            if (t < 1.0) res.resizeScale = resizeScale
        }
    }
}

/** newm's animate.py: something that animates between two layout states. */
interface Animatable {
    fun flushAnimation()
    fun animate(oldState: LayoutState, newState: LayoutState, dt: Double)
}

/** newm's Animate: runs an [Interpolation] for its duration, frame by frame. */
abstract class Animate<T> {
    private class Running<T>(val interpolation: Interpolation<T>, var start: Double, val duration: Double, var frame: Int)

    @Volatile
    private var animation: Running<T>? = null

    protected fun process(defaultState: T): T {
        val a = animation ?: return defaultState
        a.frame += 1
        if (a.frame == 1) a.start = now() - 1.0 / 120.0
        val perc = minOf((now() - a.start) / a.duration, 1.0)
        animDamage()
        return a.interpolation.get(perc)
    }

    open fun flushAnimationOwn() {
        animation = null
    }

    protected fun startAnimation(interpolation: Interpolation<T>, dt: Double) {
        animation = Running(interpolation, -1.0, dt, 0)
        animDamage()
    }

    /** When the animation running now ends (seconds, as [now]), or null. */
    fun finalTime(): Double? = animation?.takeIf { it.start > 0.0 }?.let { it.start + it.duration }

    /** True while an animation runs. */
    val animating: Boolean get() = animation != null

    protected abstract fun animDamage()
}

/** pywm's widget downstream state: where a widget of newm's own is drawn, how strongly, in which order. */
data class WidgetDown(
    val zIndex: Double,
    val box: Box,
    val opacity: Double = 1.0,
    val lockEnabled: Boolean = false,
    val workspace: Box? = null,
    val cornerRadius: Double = 0.0,
)

/** newm's WidgetDownstreamInterpolation ([preventAnim]: the widget's workspace shows changes at once). */
class WidgetDownInterpolation(state0: WidgetDown, state1: WidgetDown, private val preventAnim: Boolean = false) : Interpolation<WidgetDown> {
    private val from = state0
    private val to = state1

    override fun get(at: Double): WidgetDown {
        val t = (if (preventAnim) 1.0 else at).coerceIn(0.0, 1.0)
        return WidgetDown(
            zIndex = if (t > 0.5) to.zIndex else from.zIndex,
            box = from.box.mix(to.box, t),
            opacity = from.opacity + (to.opacity - from.opacity) * t,
            lockEnabled = from.lockEnabled,
            workspace = if (t > 0.5) to.workspace else from.workspace,
            cornerRadius = from.cornerRadius + (to.cornerRadius - from.cornerRadius) * t,
        )
    }
}

/** A colour as newm's configuration writes it: "#RRGGBB" or "#RRGGBBAA". */
fun color(text: String): dev.caulis.render.Color = dev.caulis.render.Color.hex(text)
