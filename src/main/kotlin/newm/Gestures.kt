package newm

import dev.caulis.log.Logger

private val log = Logger("gestures")

/** newm's gestures/gesture_listener.py: told the values of a gesture as it goes, and its end. */
class GestureListener(private val onUpdate: ((Map<String, Double>) -> Unit)?, private val onTerminate: (() -> Unit)?) {
    fun update(values: Map<String, Double>) = onUpdate?.invoke(values) ?: Unit
    fun terminate() = onTerminate?.invoke() ?: Unit
}

/**
 * newm's gestures/gesture.py: one gesture — kind "move-1" or "swipe-2" to "swipe-5" — and the
 * values it has reached ("delta_x", "delta_y", "delta2_s", "rotation", "scale").
 */
open class NewmGesture(val kind: String) {
    private val listeners = ArrayList<GestureListener>()

    fun listener(l: GestureListener) {
        listeners += l
    }

    internal fun terminate() = listeners.toList().forEach(GestureListener::terminate)

    internal fun update(values: Map<String, Double>) = listeners.toList().forEach { it.update(values) }

    override fun toString() = "Gesture($kind)"
}

/** newm's gestures/lowpass.py. */
class Lowpass(private val inertia: Double) {
    private var last: Double? = null

    fun next(value: Double): Double {
        val l = last
        last = if (l == null) value else inertia * l + (1.0 - inertia) * value
        return last!!
    }
}

/**
 * newm's gestures/lowpass_gesture.py: [gesture] smoothed — its values, low-passed, told
 * [frequency] times a second (a thread there; a timer on the compositor's thread here).
 */
class LowpassGesture(gesture: NewmGesture, private val inertia: Double, frequency: Double, every: (Long, () -> Unit) -> AutoCloseable) : NewmGesture(gesture.kind) {
    private val lowpasses = HashMap<String, Lowpass>()
    private var values: Map<String, Double>? = null
    private val timer: AutoCloseable

    init {
        gesture.listener(GestureListener({ values = it }, ::onTerminate))
        timer = every(Math.round(1000.0 / frequency).coerceAtLeast(1)) {
            val v = values ?: return@every
            update(v.mapValues { (k, x) -> lowpasses.getOrPut(k) { Lowpass(inertia) }.next(x) })
        }
    }

    private fun onTerminate() {
        timer.close()
        terminate()
    }
}

/**
 * newm's gestures/provider: where gestures come from. Answers: 0 let it slip to the next
 * provider, 1 not taken but no other provider either (the program gets it), 2 taken.
 */
abstract class GestureProvider(protected val onGesture: (NewmGesture) -> Boolean) {
    open fun onTouchpad(gesture: dev.caulis.core.input.Gesture): Int = 0
    open fun onMotion(timeMillis: Int, dx: Double, dy: Double): Int = 0
    open fun onAxis(timeMillis: Int, vertical: Boolean, delta: Double): Int = 0
    open fun onButton(timeMillis: Int, button: Int, pressed: Boolean): Int = 0

    /** A running gesture nobody took is forgotten, so a new one can start. */
    open fun resetGesture() {}
    open fun start() {}
    open fun stop() {}
}

private val confScale = configuredValue("gestures.c.scale_px", 800.0)

// mouse { superMove = "button" (Super+left drag moves, Super+right drag resizes) | "motion" (Super and a move alone moves, as newm) }
private val confSuperMove = configuredValue("mouse.super_move", "button")

/**
 * newm's CGestureProvider (gestures/provider/c_gestures.py): the touchpad's swipes as the
 * compositor gets them from libinput (pywm there, Caulis here), the pointer's movement as
 * "move-1" and two-finger scrolling as "swipe-2". A gesture of the last two ends when nothing
 * came for a quarter of a second.
 */
class CGestureProvider(
    onGesture: (NewmGesture) -> Boolean,
    private val every: (Long, () -> Unit) -> AutoCloseable,
    /** For a drag with a button: the pixels a gesture's 1 is (the window then follows the pointer one to one). */
    private val dragScale: () -> Double = { confScale() },
) : GestureProvider(onGesture) {
    private var captured: NewmGesture? = null
    private var reference = 0.0 to 0.0
    private var d2s = 0.0
    private var active: Boolean? = null
    private var ticker: AutoCloseable? = null

    // The mouse button a gesture is held with (Super+left: moving, Super+right: resizing), or null.
    private var heldButton: Int? = null

    override fun start() {
        ticker = every(250) {
            // (Held with a button: it ends when the button is let go, not when the mouse rests.)
            if (heldButton != null) return@every
            when (active) {
                true -> active = false
                false -> finish()
                null -> {}
            }
        }
    }

    override fun stop() {
        ticker?.close()
        ticker = null
        // (A reload during a gesture: it ends here, so what follows it — moving a window — ends too.)
        if (captured != null) log.info { "Gesture ended: its provider stops" }
        finish()
    }

    private fun start(n: Int): Int {
        if (captured != null) return 2
        val gesture = NewmGesture(if (n > 1) "swipe-$n" else "move-1")
        if (onGesture(gesture)) {
            captured = gesture
            reference = 0.0 to 0.0
            d2s = 0.0
            return 2
        }
        return 0
    }

    private fun update(dx0: Double, dy0: Double): Int {
        val c = captured ?: return 0
        val scale = if (heldButton != null) dragScale() else confScale()
        val dx = dx0 / scale
        val dy = dy0 / scale
        reference = reference.first + dx to reference.second + dy
        d2s += dx * dx + dy * dy
        c.update(mapOf("delta_x" to reference.first, "delta_y" to reference.second, "delta2_s" to d2s))
        return 2
    }

    private fun finish(): Int {
        val c = captured ?: return 0
        c.terminate()
        captured = null
        active = null
        return 2
    }

    override fun onTouchpad(gesture: dev.caulis.core.input.Gesture): Int = when (gesture) {
        is dev.caulis.core.input.Gesture.SwipeBegin -> start(gesture.fingers)
        is dev.caulis.core.input.Gesture.SwipeUpdate -> update(gesture.dx, gesture.dy)
        else -> finish() // (its end, or not a swipe)
    }

    override fun onMotion(timeMillis: Int, dx: Double, dy: Double): Int {
        // By default a window moves with a button held (as Hyprland's Super+drag), not with
        // Super and the mouse alone: that moved windows by surprise.
        if (confSuperMove() != "motion" && heldButton == null) return 0
        if (heldButton == null) start(1)
        active = true
        return update(dx, dy)
    }

    override fun onButton(timeMillis: Int, button: Int, pressed: Boolean): Int {
        if (confSuperMove() == "motion") return 0
        if (pressed) {
            if (captured != null || button != BTN_LEFT && button != BTN_RIGHT) return 0
            // Left: moving ("move-1"); right: resizing ("swipe-2", as two fingers do). Taken
            // only when the layout wants it (Super held): else the program gets the click.
            if (start(if (button == BTN_LEFT) 1 else 2) != 2) return 0
            heldButton = button
            active = true
            return 2
        }
        if (button != heldButton) return 0
        heldButton = null
        finish()
        return 2
    }

    override fun onAxis(timeMillis: Int, vertical: Boolean, delta: Double): Int {
        start(2)
        active = true
        // (As newm: assumes natural scrolling.)
        return update(if (!vertical) -delta else 0.0, if (vertical) -delta else 0.0)
    }

    override fun resetGesture() {
        log.trace { "Gesture reset" }
        // A gesture still captured ends (else what follows it waited for an end that never came).
        finish()
    }

    private companion object {
        const val BTN_LEFT = 272
        const val BTN_RIGHT = 273
    }
}
