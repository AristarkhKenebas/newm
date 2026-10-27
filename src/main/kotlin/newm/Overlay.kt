package newm

import dev.caulis.core.input.Gesture
import dev.caulis.log.Logger

private val log = Logger("overlay")

/**
 * newm's overlay/overlay.py: a mode the layout goes into for a while (moving and resizing with
 * fingers, swiping between workspaces, zooming, the launcher) that takes input first, with an
 * animation into it and out of it.
 */
abstract class Overlay(protected val layout: Layout) {
    var ready = false
        private set

    fun init() {
        val (wmState, dt) = enterTransition()
        if (wmState != null && dt != null) {
            log.trace { "Overlay: Enter animation" }
            layout.animateTo({ null to wmState }, dt, ::enterFinished, overlaySafe = true)
        } else {
            ready = true
        }
        postInit()
    }

    private fun enterFinished() {
        log.trace { "Overlay: Enter animation completed" }
        ready = true
    }

    fun destroy() {
        preDestroy()
        ready = false
        val (wmState, dt) = exitTransition()
        if (wmState != null && dt != null) {
            log.trace { "Overlay: Exit animation" }
            layout.animateTo({ null to wmState }, dt, ::exitFinished, overlaySafe = true)
        } else {
            layout.onOverlayDestroyed()
        }
    }

    private fun exitFinished() {
        log.trace { "Overlay: Exit animation completed" }
        layout.onOverlayDestroyed()
    }

    // --- what each overlay does

    open fun postInit() {}
    open fun preDestroy() {}
    // Where the layout goes when it comes and goes, and as which animation (see NewmAnimations).
    protected open fun enterTransition(): Pair<LayoutState?, dev.caulis.core.ResolvedAnimation?> = null to null
    protected open fun exitTransition(): Pair<LayoutState?, dev.caulis.core.ResolvedAnimation?> = null to null

    open fun onKey(timeMillis: Int, keycode: Int, pressed: Boolean, keysyms: String): Boolean = true
    open fun onModifiers(modifiers: Int, lastModifiers: Int): Boolean = false
    open fun onMotion(timeMillis: Int, dx: Double, dy: Double): Boolean = false
    open fun onButton(timeMillis: Int, button: Int, pressed: Boolean): Boolean = false
    open fun onAxis(timeMillis: Int, vertical: Boolean, delta: Double, steps: Double): Boolean = false
    open fun onGesture(gesture: NewmGesture): Boolean = false
}
