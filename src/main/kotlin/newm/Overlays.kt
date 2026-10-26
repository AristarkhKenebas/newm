package newm

import dev.caulis.log.Logger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val log = Logger("overlay")

private val confAnimT = configuredValue("anim_time", .3)
private val confGestureBindingMoveResize = configuredValue<List<String?>>("gesture_bindings.move_resize", listOf("L", "move-1", "swipe-2"))

/**
 * newm's MoveResizeFloatingOverlay: a floating view follows the mouse (until a button) or one
 * finger; two fingers resize it (in pixels). Onto another workspace when it crosses over.
 */
class MoveResizeFloatingOverlay(layout: Layout, private val view: View) : Overlay(layout) {
    private var i = 0.0
    private var j = 0.0
    private var w = 0.0
    private var h = 0.0
    private var workspace = layout.workspaces[0]
    private var wsState = layout.state.workspaceState(workspace)
    private var motionMode = true
    private var gestureMode = false
    private var gestureLastDx = 0.0
    private var gestureLastDy = 0.0

    init {
        val found = layout.state.findViewOrNull(view)
        if (found != null) {
            val (s, ws, wsHandle) = found
            wsState = ws
            workspace = layout.workspaces.first { it.handle == wsHandle }
            i = s.floatPos.first
            j = s.floatPos.second
            w = s.floatSize.first.toDouble()
            h = s.floatSize.second.toDouble()
            layout.update(layout.state.settingWorkspaceState(workspace, wsState.replacingViewState(view) { scaleOrigin = w to h }))
        } else {
            log.warn("Unexpected: Could not access view $view state")
        }
    }

    private fun place(ni: Double, nj: Double, nw: Double, nh: Double) {
        val (ws, box) = view.transformToClosestWs(workspace, ni, nj, nw, nh)
        if (ws !== workspace) {
            log.trace { "Move floating - switching workspace ${workspace.handle} -> ${ws.handle}" }
            layout.state.moveViewState(view, workspace, ws)
            workspace = ws
        }
        i = box[0]
        j = box[1]
        w = pyRound(box[2]).toDouble() // (pixels)
        h = pyRound(box[3]).toDouble()
        layout.state.updateViewState(view) { floatPos = i to j; floatSize = w.toInt() to h.toInt() }
        layout.damage()
    }

    fun move(dx: Double, dy: Double) = place(i + dx * wsState.size, j + dy * wsState.size, w, h)

    fun resize(dx: Double, dy: Double) = place(i, j, w + pyRound(dx * workspace.width), h + pyRound(dy * workspace.height))

    private fun gesture(step: (Double, Double) -> Unit): (Map<String, Double>) -> Unit = { values ->
        if (gestureMode) {
            val dx = values["delta_x"] ?: 0.0
            val dy = values["delta_y"] ?: 0.0
            step(dx - gestureLastDx, dy - gestureLastDy)
            gestureLastDx = dx
            gestureLastDy = dy
        }
    }

    private fun gestureFinish() {
        gestureMode = false
        gestureLastDx = 0.0
        gestureLastDy = 0.0
        if (!layout.hasModifier(confGestureBindingMoveResize()[0])) {
            log.trace { "MoveResizeFloatingOverlay: Exiting on gesture end" }
            layout.exitOverlay()
        }
    }

    override fun onMotion(timeMillis: Int, dx: Double, dy: Double): Boolean {
        if (motionMode) move(dx / workspace.width, dy / workspace.height)
        return false
    }

    override fun onButton(timeMillis: Int, button: Int, pressed: Boolean): Boolean {
        if (motionMode) {
            log.trace { "MoveFloatingOverlay: Exiting on mouse release" }
            layout.exitOverlay()
            return true
        }
        return false
    }

    override fun onGesture(gesture: NewmGesture): Boolean {
        val binding = confGestureBindingMoveResize()
        val step: (Double, Double) -> Unit = when (gesture.kind) {
            binding[2] -> ::resize
            binding[1] -> ::move
            else -> return false
        }
        log.trace { "MoveResizeFloatingOverlay: New ${gesture.kind}" }
        motionMode = false
        gestureMode = true
        LowpassGesture(gesture, confLpInertia(), confLpFreq(), layout::every).listener(GestureListener(gesture(step), ::gestureFinish))
        return true
    }

    override fun onModifiers(modifiers: Int, lastModifiers: Int): Boolean {
        if (modBit(confGestureBindingMoveResize()[0]) and Mod.pressed(lastModifiers, modifiers) != 0) {
            if (!gestureMode && !motionMode) {
                log.trace { "MoveResizeFloatingOverlay: Requesting close after Mod release" }
                layout.exitOverlay()
            }
            return true
        }
        return false
    }

    override fun exitTransition(): Pair<LayoutState?, Double?> {
        if (layout.state.findViewOrNull(view) == null) {
            log.warn("Unexpected: Error accessing view $view state")
            return null to 0.0
        }
        val state = layout.state.copy()
        state.updateViewState(view) { scaleOrigin = null }
        state.constrain()
        return state to confAnimT()
    }
}

private val confSwipeLockDist = configuredValue("swipe.lock_dist", 0.01)
private val confSwipeGestureFactor = configuredValue("swipe.gesture_factor", 4.0)
private val confSwipeGridOvr = configuredValue("swipe.grid_ovr", 0.2)
private val confSwipeGridM = configuredValue("swipe.grid_m", 1.0)
private val confGridMinDist = configuredValue("grid.min_dist", .05)
private val confGestureBindingSwipe = configuredValue<List<String?>>("gesture_bindings.swipe", listOf(null, "swipe-3"))

/** newm's SwipeOverlay: three fingers move the view over the workspace's tiles, along one axis, snapping to whole tiles. */
class SwipeOverlay(layout: Layout) : Overlay(layout) {
    private val workspace = layout.activeWorkspace()
    private val wsState = layout.state.workspaceState(workspace)
    private val size = wsState.size
    private var i = wsState.i
    private var j = wsState.j
    private var lockedX: Boolean? = null
    private var initialX = i
    private var initialY = j
    private val invalid = BooleanArray(2)
    private val iGrid: Grid
    private val jGrid: Grid
    private var hasGesture = false

    init {
        val extent = wsState.extent().map { pyRound(it) }
        val minI = extent[0]
        val minJ = extent[1]
        val maxI = extent[2] - (pyRound(size) - 1)
        val maxJ = extent[3] - (pyRound(size) - 1)
        if (maxI < minI) invalid[0] = true
        if (maxJ < minJ) invalid[1] = true
        iGrid = Grid("i", minI, maxI, i, confSwipeGridOvr(), confSwipeGridM())
        jGrid = Grid("j", minJ, maxJ, j, confSwipeGridOvr(), confSwipeGridM())
        setState()
    }

    override fun exitTransition(): Pair<LayoutState?, Double?> {
        var (fi, ti) = iGrid.final(throwDistMax = size - confGridMinDist())
        var (fj, tj) = jGrid.final(throwDistMax = size - confGridMinDist())
        var t: Double? = null
        lockedX?.let { x ->
            if (x) {
                fj = pyRound(initialY)
                t = ti
            } else {
                fi = pyRound(initialX)
                t = tj
            }
        }
        return layout.state.replacingWorkspaceState(workspace) { copy(i = fi.toDouble(), j = fj.toDouble()) } to t
    }

    private fun setState() {
        if (!invalid[0]) wsState.i = iGrid.at(i)
        if (!invalid[1]) wsState.j = jGrid.at(j)
        layout.damage()
    }

    override fun onGesture(gesture: NewmGesture): Boolean {
        if (gesture.kind != confGestureBindingSwipe()[1]) {
            layout.exitOverlay()
            return false
        }
        if (!hasGesture) {
            LowpassGesture(gesture, confLpInertia(), confLpFreq(), layout::every).listener(GestureListener(::onUpdate) { layout.exitOverlay() })
            hasGesture = true
        }
        return true
    }

    private fun onUpdate(values: Map<String, Double>) {
        val dx = values["delta_x"] ?: 0.0
        val dy = values["delta_y"] ?: 0.0
        if (lockedX == null && dx * dx + dy * dy > confSwipeLockDist() * confSwipeLockDist()) {
            val x = abs(dx) > abs(dy)
            lockedX = x
            if (x) initialX += confSwipeGestureFactor() * size * dx else initialY += confSwipeGestureFactor() * size * dy
        }
        lockedX?.let { x ->
            if (x) i = initialX - confSwipeGestureFactor() * size * dx else j = initialY - confSwipeGestureFactor() * size * dy
        }
        setState()
    }

    override fun onMotion(timeMillis: Int, dx: Double, dy: Double): Boolean = false

    override fun onAxis(timeMillis: Int, vertical: Boolean, delta: Double, steps: Double): Boolean = false
}

private val confZoomGridOvr = configuredValue("swipe_zoom.grid_ovr", 0.2)
private val confZoomGridM = configuredValue("swipe_zoom.grid_m", 1.0)
private val confZoomHyst = configuredValue("swipe_zoom.hyst", 0.2)
private val confZoomGestureFactor = configuredValue("swipe_zoom.gesture_factor", 4.0)
private val confGestureBindingSwipeToZoom = configuredValue<List<String?>>("gesture_bindings.swipe_to_zoom", listOf(null, "swipe-4"))

/** newm's SwipeToZoomOverlay: four fingers up and down show more or fewer tiles (keeping the focused view in sight). */
class SwipeToZoomOverlay(layout: Layout) : Overlay(layout) {
    private val workspace = layout.activeWorkspace()
    private val wsState = layout.state.workspaceState(workspace)
    private val i = wsState.i
    private val j = wsState.j
    private var size = wsState.size
    private val initialSize = pyRound(size)
    private val focused = layout.findFocusedView()
    private var focusedBottomRight: Pair<Double, Double>? = null
    private val grid: Grid
    private val hyst = Hysteresis(confZoomHyst(), size)
    private var hasGesture = false

    init {
        var minSize = 1
        if (focused != null) {
            var state: ViewState? = layout.state.findViewOrNull(focused)?.first
            // A floating view focused: a parent in the layout decides the smallest size.
            if (focused.isFloat(layout.state)) {
                var found = false
                var v: View? = focused
                for (n in 0 until 5) {
                    v = v?.parent ?: break
                    state = layout.state.findViewOrNull(v)?.first
                    if (!v.isFloat(layout.state)) {
                        found = true
                        break
                    }
                }
                if (!found) state = null
            }
            if (state != null) {
                // The focused view cannot be zoomed out of the screen.
                val square = pyRound(max(state.w, state.h))
                minSize = if (square in 1 until initialSize) square else initialSize
                if (i + size > state.i + state.w - 0.1 && j + size > state.j + state.h - 0.1) {
                    focusedBottomRight = state.i + state.w to state.j + state.h
                }
            }
        }
        grid = Grid("size", minSize, initialSize + 1, initialSize.toDouble(), confZoomGridOvr(), confZoomGridM())
        setState()
    }

    override fun exitTransition(): Pair<LayoutState?, Double?> {
        val (finalSize, t) = grid.final()
        var state = layout.state.replacingWorkspaceState(workspace) { copy(size = finalSize.toDouble(), sizeOrigin = null) }
        focused?.let { state = state.focusingView(it) }
        if (finalSize != initialSize) {
            state = state.settingWorkspaceState(workspace, state.workspaceState(workspace).withoutFullscreen(drop = true))
        }
        return state to t
    }

    private fun setState() {
        wsState.sizeOrigin = hyst(wsState.size).toDouble()
        wsState.size = grid.at(size)
        // The constraints, at once: the focused view's bottom right corner stays in view.
        focusedBottomRight?.let { (bi, bj) ->
            wsState.i = max(i, bi - wsState.size)
            wsState.j = max(j, bj - wsState.size)
        }
        layout.state.constrain()
        layout.damage()
    }

    override fun onGesture(gesture: NewmGesture): Boolean {
        if (gesture.kind != confGestureBindingSwipeToZoom()[1]) {
            layout.exitOverlay()
            return false
        }
        if (!hasGesture) {
            LowpassGesture(gesture, confLpInertia(), confLpFreq(), layout::every).listener(GestureListener(::onUpdate) { layout.exitOverlay() })
            hasGesture = true
        }
        return true
    }

    private fun onUpdate(values: Map<String, Double>) {
        size = initialSize - confZoomGestureFactor() * (values["delta_y"] ?: 0.0)
        setState()
    }

    override fun onMotion(timeMillis: Int, dx: Double, dy: Double): Boolean = false

    override fun onAxis(timeMillis: Int, vertical: Boolean, delta: Double, steps: Double): Boolean = false
}

private val confLauncherGestureFactor = configuredValue("panels.launcher.gesture_factor", 200.0)
private val confGestureBindingLauncher = configuredValue<List<String?>>("gesture_bindings.launcher", listOf(null, "swipe-5"))

/** newm's LauncherOverlay: the launcher panel slides in (five fingers, or a key) and out again (Escape, or the gesture again). */
class LauncherOverlay(layout: Layout) : Overlay(layout) {
    private var isOpened = false
    private var hasGesture = false

    override fun onGesture(gesture: NewmGesture): Boolean {
        log.trace { "LauncherOverlay: new gesture" }
        if (gesture.kind != confGestureBindingLauncher()[1]) return false
        // (The first gesture opens it, the next one closes it.)
        LowpassGesture(gesture, confLpInertia(), confLpFreq(), layout::every).listener(GestureListener(::onUpdate) { onUpdate(null) })
        hasGesture = true
        return true
    }

    private fun onUpdate(values: Map<String, Double>?) {
        val moved = if (values != null) (values["delta2_s"] ?: 0.0) * confLauncherGestureFactor() else 1.0
        if (!isOpened) {
            layout.state.launcherPerc = min(max(moved, 0.0), 1.0)
            if (values == null) {
                isOpened = true
                layout.panels().filter { it.panel == "launcher" }.forEach { it.focus() }
            }
        } else {
            layout.state.launcherPerc = min(max(1.0 - moved, 0.0), 1.0)
            if (values == null) layout.exitOverlay()
        }
        layout.damage()
    }

    override fun onKey(timeMillis: Int, keycode: Int, pressed: Boolean, keysyms: String): Boolean {
        if (keysyms == "Escape") {
            if (!pressed) layout.exitOverlay()
            return true
        }
        return false
    }

    override fun enterTransition(): Pair<LayoutState?, Double?> {
        if (hasGesture) return null to null
        log.trace { "Entering LauncherOverlay with animation..." }
        return layout.state.copy(launcherPerc = 1.0) to confAnimT()
    }

    override fun exitTransition(): Pair<LayoutState?, Double?> {
        log.trace { "Exiting LauncherOverlay with animation..." }
        return layout.state.copy(launcherPerc = 0.0) to confAnimT()
    }

    override fun postInit() {
        val launcher = layout.panels().filter { it.panel == "launcher" }
        if (launcher.isNotEmpty()) launcher[0].focus() else log.error("Could not find launcher")
    }
}
