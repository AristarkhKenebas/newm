package newm

import dev.caulis.log.Logger

private val log = Logger("overlay")

// How often a moving or resizing window still moves on when no frame is drawn (a monitor off).
private const val SLOW_TICK_MILLIS = 100L

private val confMoveGridOvr = configuredValue("move.grid_ovr", 0.2)
private val confMoveGridM = configuredValue("move.grid_m", 3.0)
private val confResizeGridOvr = configuredValue("resize.grid_ovr", 0.1)
private val confResizeGridM = configuredValue("resize.grid_m", 3.0)
private val confHyst = configuredValue("resize.hyst", 0.2)
private val confGestureFactor = configuredValue("move_resize.gesture_factor", 2.0)

/** How far a gesture's 1 moves a window (in tiles, times the view's size). */
internal fun moveResizeFactor(): Double = confGestureFactor()
internal val confLpFreq = configuredValue("gestures.lp_freq", 60.0)
internal val confLpInertia = configuredValue("gestures.lp_inertia", .8)
private val confGestureBindingMoveResize = configuredValue<List<String?>>("gesture_bindings.move_resize", listOf("L", "move-1", "swipe-2"))

/** Where a move or resize ended: (workspace; i, j, w, h now; i, j, w, h final; how long to get there). */
internal class MoveResizeResult(
    val workspace: Workspace,
    val i: Double, val j: Double, val w: Double, val h: Double,
    val fi: Double, val fj: Double, val fw: Double, val fh: Double,
    val t: Double,
)

internal interface MoveResizePart {
    fun resetGesture() {}
    fun onGesture(values: Map<String, Double>)
    fun close(): MoveResizeResult
}

/** newm's MoveOverlay: the view follows the finger(s), snapping to whole tiles, onto other workspaces too. */
internal class MoveOverlay(private val layout: Layout, private val view: View) : MoveResizePart {
    private var workspace = layout.workspaces[0]
    private var wsState = layout.state.workspaceState(workspace)
    private var i = 0.0
    private var j = 0.0
    private var w = 1.0
    private var h = 1.0

    // In case of a switched workspace.
    private var di = 0.0
    private var dj = 0.0

    private val iGrid: Grid
    private val jGrid: Grid
    // What the window snaps to while it moves: whole tiles; on screens quarters of a screen (a window there is a part of one).
    private val unit = if (Screens.on()) 4.0 else 1.0
    private var lastDx = 0.0
    private var lastDy = 0.0
    private var closed = false

    init {
        val found = layout.state.findViewOrNull(view)
        if (found != null) {
            val (vs, ws, wsHandle) = found
            wsState = ws
            workspace = layout.workspaces.first { it.handle == wsHandle }
            i = vs.i
            j = vs.j
            w = vs.w
            h = vs.h
            layout.update(layout.state.settingWorkspaceState(workspace, wsState.replacingViewState(view) { moveOrigin = MoveOrigin(i, j, workspace) }))
        } else {
            log.warn("Unexpected: Could not access view $view state")
        }
        // (As far as the whole view and the window beyond it each way, at least 3 tiles: so it
        // reaches past the monitor's edge onto the next monitor's wall, zoomed out too.)
        val reachI = maxOf(3.0, wsState.size + w)
        val reachJ = maxOf(3.0, wsState.size + h)
        iGrid = Grid("i", pyRound((i - reachI) * unit), pyRound((i + reachI) * unit), i * unit, confMoveGridOvr(), confMoveGridM())
        jGrid = Grid("j", pyRound((j - reachJ) * unit), pyRound((j + reachJ) * unit), j * unit, confMoveGridOvr(), confMoveGridM())
    }

    override fun resetGesture() {
        lastDx = 0.0
        lastDy = 0.0
    }

    override fun onGesture(values: Map<String, Double>) {
        if (closed) return
        val factor = confGestureFactor() * wsState.size
        val dx = values["delta_x"] ?: 0.0
        val dy = values["delta_y"] ?: 0.0
        i += factor * (dx - lastDx)
        j += factor * (dy - lastDy)
        var i0 = iGrid.at(i * unit) / unit
        var j0 = jGrid.at(j * unit) / unit
        lastDx = dx
        lastDy = dy
        i0 += di
        j0 += dj

        val (ws, box) = view.transformToClosestWs(workspace, i0, j0, w, h)
        val (ni, nj, nw, nh) = box.toList()
        w = nw
        h = nh
        if (ws !== workspace) {
            log.trace { "Move - switching workspace ${workspace.handle} ($i0 $j0) -> ${ws.handle} ($ni $nj)" }
            di += ni - i0
            dj += nj - j0
            layout.state.moveViewState(view, workspace, ws)
            workspace = ws
        }
        layout.state.updateViewState(view) { this.i = ni; this.j = nj; this.w = nw; this.h = nh }
        layout.damage()
    }

    override fun close(): MoveResizeResult {
        closed = true
        val found = layout.state.findViewOrNull(view) ?: run {
            log.warn("Unexpected: Could not access view $view state... returning default placement")
            return MoveResizeResult(workspace, i, j, 1.0, 1.0, pyRound(i).toDouble(), pyRound(j).toDouble(), 1.0, 1.0, 1.0)
        }
        val (s, ws0, wsHandle) = found
        wsState = ws0
        workspace = layout.workspaces.first { it.handle == wsHandle }
        val (fi0, ti) = iGrid.final()
        val (fj0, tj) = jGrid.final()
        val (ws, box) = view.transformToClosestWs(workspace, fi0 / unit + di, fj0 / unit + dj, w, h)
        val fi = if (Screens.on()) box[0] else pyRound(box[0]).toDouble()
        val fj = if (Screens.on()) box[1] else pyRound(box[1]).toDouble()
        // (On screens it keeps its size until it is let go: its place there decides it then.)
        if (!Screens.on()) {
            w = maxOf(1, pyRound(w)).toDouble()
            h = maxOf(1, pyRound(h)).toDouble()
        }
        if (ws !== workspace) {
            log.trace { "Move - switching workspace ${workspace.handle} -> ${ws.handle}" }
            layout.state.moveViewState(view, workspace, ws)
            workspace = ws
        }
        log.trace { "Move - Grid finals: $fi $fj ($ti $tj)" }
        return MoveResizeResult(workspace, s.i, s.j, s.w, s.h, fi, fj, w, h, maxOf(ti, tj))
    }
}

/** newm's ResizeOverlay: the view's size follows the fingers, snapping to whole tiles (growing left and up past 1). */
internal class ResizeOverlay(private val layout: Layout, private val view: View) : MoveResizePart {
    private var workspace = layout.workspaces[0]
    private var wsState = layout.state.workspaceState(workspace)
    private var i = 0.0
    private var j = 0.0
    private var w = 1.0
    private var h = 1.0
    private var hystW: (Double) -> Double = { it }
    private var hystH: (Double) -> Double = { it }
    private val iGrid: Grid
    private val jGrid: Grid
    private val wGrid: Grid
    private val hGrid: Grid
    private var closed = false

    init {
        val found = layout.state.findViewOrNull(view)
        if (found != null) {
            val (vs, ws, wsHandle) = found
            wsState = ws
            workspace = layout.workspaces.first { it.handle == wsHandle }
            i = vs.i
            j = vs.j
            w = vs.w
            h = vs.h
            val hw = Hysteresis(confHyst(), w)
            val hh = Hysteresis(confHyst(), h)
            hystW = { hw(it).toDouble() }
            hystH = { hh(it).toDouble() }
            layout.update(
                layout.state.settingWorkspaceState(
                    workspace,
                    wsState.replacingViewState(view) {
                        moveOrigin = MoveOrigin(i, j, workspace)
                        scaleOrigin = w to h
                    },
                ),
            )
        } else {
            log.warn("Unexpected: Could not access view $view state")
        }
        iGrid = Grid("i", pyRound(i - 3), pyRound(i + 3), i, confMoveGridOvr(), confMoveGridM())
        jGrid = Grid("j", pyRound(j - 3), pyRound(j + 3), j, confMoveGridOvr(), confMoveGridM())
        wGrid = Grid("w", 1, pyRound(w + 3), w, confResizeGridOvr(), confResizeGridM())
        hGrid = Grid("h", 1, pyRound(h + 3), h, confResizeGridOvr(), confResizeGridM())
    }

    override fun onGesture(values: Map<String, Double>) {
        if (closed) return
        val factor = confGestureFactor() * wsState.size
        val dw = factor * (values["delta_x"] ?: 0.0)
        val dh = factor * (values["delta_y"] ?: 0.0)
        val ni: Double
        val nw: Double
        // (Past one tile it grows the other way: left and up.)
        if (w + dw < 1) {
            val d = 1 - (w + dw)
            ni = i - d
            nw = 1 + d
        } else {
            ni = i
            nw = w + dw
        }
        val nj: Double
        val nh: Double
        if (h + dh < 1) {
            val d = 1 - (h + dh)
            nj = j - d
            nh = 1 + d
        } else {
            nj = j
            nh = h + dh
        }
        val w2 = wGrid.at(nw)
        val h2 = hGrid.at(nh)
        val gi = iGrid.at(ni)
        val gj = jGrid.at(nj)
        layout.state.updateViewState(view) {
            this.i = gi; this.j = gj; this.w = w2; this.h = h2
            scaleOrigin = hystW(w2) to hystH(h2)
        }
        layout.damage()
    }

    override fun close(): MoveResizeResult {
        closed = true
        val s = layout.state.findViewOrNull(view)?.first ?: run {
            log.warn("Unexpected: Could not access view $view state... returning default placement")
            return MoveResizeResult(workspace, i, j, w, h, i, j, w, h, 1.0)
        }
        val (fi, ti) = iGrid.final()
        val (fj, tj) = jGrid.final()
        val (fw, tw) = wGrid.final()
        val (fh, th) = hGrid.final()
        log.trace { "Resize - Grid finals: $fi $fj $fw $fh ($ti $tj $tw $th)" }
        return MoveResizeResult(workspace, s.i, s.j, s.w, s.h, fi.toDouble(), fj.toDouble(), fw.toDouble(), fh.toDouble(), maxOf(ti, tj, tw, th))
    }
}

/**
 * newm's MoveResizeOverlay: with the modifier held, one finger (or the mouse) moves the
 * focused tile and two fingers resize it; when a gesture ends the tile glides onto whole
 * tiles, and the view follows so it stays in sight. It closes when the modifier is let go.
 */
class MoveResizeOverlay(layout: Layout, private val view: View) : Overlay(layout) {
    private var workspace = layout.workspaces[0]
    // The workspace it was on at the start (let go on another monitor's: it lands there).
    private var startWorkspace = workspace
    private var part: MoveResizePart? = null
    // The workspace the view glides on (its state read anew each time: a gesture replaces it).
    private var panWorkspace = workspace

    // Gliding after a gesture: (from i or w, from j or h, to i or w, to j or h, start, end).
    private var targetViewPos: DoubleArray? = null
    private var targetViewSize: DoubleArray? = null
    private var targetLayoutPos: DoubleArray? = null

    // The curves of the glides: after the fingers (gestureSnap), and of the view keeping the window in sight (camera).
    private var snap = NewmAnimations["gestureSnap"]
    private var follow = NewmAnimations["camera"]

    // How far along a glide from [start] to [end] is at [t] (0..1, along [animation]'s curve; 1 when it has no time).
    private fun along(t: Double, start: Double, end: Double, animation: dev.caulis.core.ResolvedAnimation): Double {
        if (end <= start) return 1.0
        val time = ((t - start) / (end - start)).coerceIn(0.0, 1.0)
        return try {
            animation.curve.at(time)
        } catch (e: Exception) {
            time
        }
    }

    private var running = true
    private var wantsClose = false
    private var ticker: AutoCloseable? = null

    init {
        val found = layout.state.findViewOrNull(view)
        if (found != null) {
            workspace = layout.workspaces.first { it.handle == found.third }
            startWorkspace = workspace
        } else {
            log.warn("Unexpected: Could not access view $view state")
        }
    }

    // When it last moved on (nanoseconds): the slow timer only steps in when no frame came.
    private var lastTick = 0L
    private var slowTicker: AutoCloseable? = null

    // (A thread at 120 Hz there; once a drawn frame here, the screen's pace. A slow timer as well,
    // for when no frame is drawn — a monitor off — so it still ends.)
    override fun postInit() {
        log.trace { "MoveResizeOverlay: Starting..." }
        ticker = layout.everyFrame { tick() }
        slowTicker = layout.every(SLOW_TICK_MILLIS) {
            if (System.nanoTime() - lastTick >= SLOW_TICK_MILLIS * 1_000_000) tick()
        }
    }

    private fun stopTicking() {
        ticker?.close()
        ticker = null
        slowTicker?.close()
        slowTicker = null
    }

    private fun tick() {
        if (!running) return
        lastTick = System.nanoTime()
        val t = now()
        var inProgress = false
        targetViewPos?.let { (ii, ij, fi, fj, it0, ft) ->
            inProgress = true
            if (t >= ft) {
                layout.state.updateViewState(view) { i = fi; j = fj }
                targetViewPos = null
            } else {
                val perc = along(t, it0, ft, snap)
                layout.state.updateViewState(view) { i = ii + perc * (fi - ii); j = ij + perc * (fj - ij) }
            }
            layout.damage()
        }
        targetViewSize?.let { (iw, ih, fw, fh, it0, ft) ->
            inProgress = true
            if (t >= ft) {
                layout.state.updateViewState(view) { w = fw; h = fh; scaleOrigin = null }
                targetViewSize = null
            } else {
                val perc = along(t, it0, ft, snap)
                layout.state.updateViewState(view) { w = iw + perc * (fw - iw); h = ih + perc * (fh - ih) }
            }
            layout.damage()
        }
        val tl = targetLayoutPos
        if (tl != null) {
            inProgress = true
            val (ii, ij, fi, fj, it0, ft) = tl
            // (The workspace's state now: the one taken at the start is replaced by the gesture.)
            val wsState = runCatching { layout.state.workspaceState(panWorkspace) }.getOrNull()
            if (wsState == null) {
                targetLayoutPos = null
            } else if (t >= ft) {
                wsState.i = fi
                wsState.j = fj
                targetLayoutPos = null
            } else {
                val perc = along(t, it0, ft, follow)
                wsState.i = ii + perc * (fi - ii)
                wsState.j = ij + perc * (fj - ij)
            }
            layout.damage()
        } else if (part != null) {
            // The view kept in sight while it moves (on the workspace it is on now).
            val found = layout.state.findViewOrNull(view)
            if (found == null) {
                log.warn("Unexpected: Could not access view $view state")
            } else {
                val (vs, wsState, wsHandle) = found
                val i = pyRound(vs.i)
                val j = pyRound(vs.j)
                val w = pyRound(vs.w)
                val h = pyRound(vs.h)
                var fi = wsState.i
                var fj = wsState.j
                if (i + w > fi + wsState.size) fi = i + w - wsState.size
                if (j + h > fj + wsState.size) fj = j + h - wsState.size
                if (i < fi) fi = i.toDouble()
                if (j < fj) fj = j.toDouble()
                if (fi != wsState.i || fj != wsState.j) {
                    log.trace { "MoveResizeOverlay: Adjusting viewpoint (${wsState.i} ${wsState.j}) -> ($fi $fj)" }
                    layout.workspaces.firstOrNull { it.handle == wsHandle }?.let { panWorkspace = it }
                    follow = NewmAnimations["camera"]
                    targetLayoutPos = doubleArrayOf(wsState.i, wsState.j, fi, fj, t, t + follow.seconds)
                }
            }
        }
        if (!inProgress && wantsClose) {
            running = false
            stopTicking()
            log.trace { "MoveResizeOverlay: finished" }
            layout.exitOverlay()
        }
    }

    override fun onGesture(gesture: NewmGesture): Boolean {
        if (!running || wantsClose) {
            log.trace { "MoveResizeOverlay: Rejecting gesture" }
            return false
        }
        val binding = confGestureBindingMoveResize()
        val next: MoveResizePart = when (gesture.kind) {
            binding[2] -> {
                log.trace { "MoveResizeOverlay: New TwoFingerSwipePinch" }
                targetViewPos = null
                targetViewSize = null
                ResizeOverlay(layout, view)
            }
            binding[1] -> {
                log.trace { "MoveResizeOverlay: New SingleFingerMove" }
                targetViewPos = null
                MoveOverlay(layout, view)
            }
            else -> return false
        }
        part = next
        LowpassGesture(gesture, NewmAnimations.gestureInertia(confLpInertia(), confLpFreq()), confLpFreq(), layout::every).listener(GestureListener(next::onGesture, ::finish))
        return true
    }

    private fun finish() {
        log.trace { "MoveResizeOverlay: Finishing gesture" }
        part?.let { p ->
            val r = p.close()
            part = null
            workspace = r.workspace
            // (Gliding on as the fingers threw it: gestureSnap, its time from their speed unless it says one.)
            snap = NewmAnimations.resolveSeconds("gestureSnap", r.t)
            if (r.i != r.fi || r.j != r.fj) targetViewPos = doubleArrayOf(r.i, r.j, r.fi, r.fj, now(), now() + snap.seconds)
            if (r.w != r.fw || r.h != r.fh) targetViewSize = doubleArrayOf(r.w, r.h, r.fw, r.fh, now(), now() + snap.seconds)
        }
        if (!layout.hasModifier(confGestureBindingMoveResize()[0])) {
            log.trace { "MoveResizeOverlay: Requesting close after gesture finish" }
            close()
        }
    }

    override fun onMotion(timeMillis: Int, dx: Double, dy: Double): Boolean = false

    override fun onAxis(timeMillis: Int, vertical: Boolean, delta: Double, steps: Double): Boolean = false

    override fun onModifiers(modifiers: Int, lastModifiers: Int): Boolean {
        if (modBit(confGestureBindingMoveResize()[0]) and Mod.pressed(lastModifiers, modifiers) != 0) {
            if (part == null) {
                log.trace { "MoveResizeOverlay: Requesting close after Mod release" }
                close()
            }
            return true
        }
        return false
    }

    private fun close() {
        part?.close()
        wantsClose = true
    }

    override fun preDestroy() {
        running = false
        stopTicking()
    }

    override fun exitTransition(): Pair<LayoutState?, dev.caulis.core.ResolvedAnimation?> {
        // Clean up any possible mishaps (should not be necessary).
        val vs = layout.state.findViewOrNull(view)?.first ?: run {
            log.warn("Unexpected: Error accessing view $view state")
            return null to null
        }
        // Let go on another monitor's wall: it lands there (on screens the screen and place under
        // its middle), the wall it left closes the gap, and that monitor is the one worked on.
        if (workspace !== startWorkspace) {
            layout.workOn(workspace, view)
            if (Screens.on() && vs.isTiled) {
                val back = layout.state.copy()
                back.moveViewState(view, workspace, startWorkspace)
                val landed = Monitors.moved(back, view, workspace, at = vs.i + vs.w / 2 to vs.j + vs.h / 2)
                if (landed != null) {
                    log.info { "${view.appId} let go on the monitor of workspace ${workspace.handle}" }
                    return landed to NewmAnimations["moveResize"]
                }
            }
        }
        // On screens: onto the screen and the place under its middle (a resize changes nothing: the layout sizes it).
        if (Screens.on() && vs.isTiled) {
            val wsState = layout.state.workspaceState(workspace).replacingViewState(view) { scaleOrigin = null; moveOrigin = null }
            wsState.dropOnScreen(view.handle, vs.i + vs.w / 2, vs.j + vs.h / 2)
            wsState.validateStackIndices(view)
            return layout.state.settingWorkspaceState(workspace, wsState.focusingView(view)) to NewmAnimations["moveResize"]
        }
        val i = pyRound(vs.i).toDouble()
        val j = pyRound(vs.j).toDouble()
        val w = pyRound(vs.w).toDouble()
        val h = pyRound(vs.h).toDouble()
        log.trace { "MoveResizeOverlay: Exiting with animation ${vs.i}, ${vs.j}, ${vs.w}, ${vs.h} -> $i, $j, $w, $h" }
        val state = layout.state.settingWorkspaceState(
            workspace,
            layout.state.workspaceState(workspace).replacingViewState(view) {
                this.i = i; this.j = j; this.w = w; this.h = h
                scaleOrigin = null
                moveOrigin = null
            }.focusingView(view),
        )
        state.validateStackIndices(view)
        return state to NewmAnimations["moveResize"]
    }
}

/** The modifier bit of a binding's letter ("L", "A", "C", "S"); 0 for none. */
internal fun modBit(letter: String?): Int = when (letter) {
    "L" -> Mod.LOGO
    "A" -> Mod.ALT
    "C" -> Mod.CTRL
    "S" -> Mod.SHIFT
    else -> 0
}

private operator fun DoubleArray.component6() = this[5]

/**
 * On screens: a window's edges follow the pointer (Super + right drag, two fingers, or its
 * border dragged), its neighbours on the screen making way; nothing leaves the screen (resize
 * { clampToScreen }). Which edges: [edgeX] (-1 left, 1 right) and [edgeY] (-1 top, 1 bottom),
 * from the quarter grabbed. Driven by a gesture, or (a border dragged) by the pointer itself
 * until [button] is let go.
 */
class ScreenResizeOverlay(layout: Layout, private val view: View, private val edgeX: Int, private val edgeY: Int, private val button: Int? = null) : Overlay(layout) {
    private val found = layout.state.findViewOrNull(view)
    private val workspace = found?.third?.let { h -> layout.workspaces.firstOrNull { it.handle == h } }
    // The workspace as it was at the start: each step is made from it (never step on step).
    private val start = found?.second?.copy()
    private var hasGesture = false
    private var px = 0.0
    private var py = 0.0

    init {
        log.info { "Resizing ${view.appId} on its screen from its ${if (edgeY < 0) "top" else "bottom"} ${if (edgeX < 0) "left" else "right"} corner" }
    }

    private fun apply(dx: Double, dy: Double) {
        val ws = workspace ?: return
        val next = start?.copy() ?: return
        next.resizeOnScreen(view.handle, edgeX, edgeY, dx, dy)
        next.validateStackIndices(view)
        layout.update(layout.state.settingWorkspaceState(ws, next))
    }

    override fun onGesture(gesture: NewmGesture): Boolean {
        if (hasGesture || button != null) return false
        hasGesture = true
        LowpassGesture(gesture, NewmAnimations.gestureInertia(confLpInertia(), confLpFreq()), confLpFreq(), layout::every).listener(GestureListener({ values ->
            // (A gesture's 1 is moveResizeFactor() screens wide, times the view's size: as the pointer moves.)
            val f = moveResizeFactor() * (start?.size ?: 1.0)
            apply((values["delta_x"] ?: 0.0) * f, (values["delta_y"] ?: 0.0) * f)
        }) { layout.exitOverlay() })
        return true
    }

    override fun onMotion(timeMillis: Int, dx: Double, dy: Double): Boolean {
        if (button == null) return false
        val s = start ?: return true
        px += dx / s.usableWidth().coerceAtLeast(1.0) * s.size
        py += dy / s.usableHeight().coerceAtLeast(1.0) * s.size
        apply(px, py)
        return true
    }

    override fun onButton(timeMillis: Int, button: Int, pressed: Boolean): Boolean {
        if (this.button == null || button != this.button) return false
        if (!pressed) layout.exitOverlay()
        return true
    }

    override fun onAxis(timeMillis: Int, vertical: Boolean, delta: Double, steps: Double): Boolean = false
}
