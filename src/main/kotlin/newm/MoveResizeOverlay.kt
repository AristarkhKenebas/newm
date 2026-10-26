package newm

import dev.caulis.log.Logger

private val log = Logger("overlay")

private val confMoveGridOvr = configuredValue("move.grid_ovr", 0.2)
private val confMoveGridM = configuredValue("move.grid_m", 3.0)
private val confResizeGridOvr = configuredValue("resize.grid_ovr", 0.1)
private val confResizeGridM = configuredValue("resize.grid_m", 3.0)
private val confHyst = configuredValue("resize.hyst", 0.2)
private val confGestureFactor = configuredValue("move_resize.gesture_factor", 2.0)

/** How far a gesture's 1 moves a window (in tiles, times the view's size). */
internal fun moveResizeFactor(): Double = confGestureFactor()
private val confAnimT = configuredValue("anim_time", .3)
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
        iGrid = Grid("i", pyRound(i - 3), pyRound(i + 3), i, confMoveGridOvr(), confMoveGridM())
        jGrid = Grid("j", pyRound(j - 3), pyRound(j + 3), j, confMoveGridOvr(), confMoveGridM())
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
        var i0 = iGrid.at(i)
        var j0 = jGrid.at(j)
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
        val (ws, box) = view.transformToClosestWs(workspace, fi0 + di, fj0 + dj, w, h)
        val fi = pyRound(box[0]).toDouble()
        val fj = pyRound(box[1]).toDouble()
        w = maxOf(1, pyRound(w)).toDouble()
        h = maxOf(1, pyRound(h)).toDouble()
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
    // The fewest tiles it may have (the program's smallest size).
    private var minW = 1.0
    private var minH = 1.0
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
        // Already as small as a tile lets it be (the first window, filling the screen): the grid
        // is made finer first (nothing moves on screen), so it can be dragged smaller.
        var found = layout.state.findViewOrNull(view)
        found?.let { (vs0, ws0, handle) ->
            val (mw, mh) = view.minTiles(ws0)
            if ((vs0.w <= mw || vs0.h <= mh) && ws0.size * 2 <= MAX_GRID && !ws0.isFullscreen() && !ws0.isInOverview()) {
                layout.update(layout.state.settingWorkspaceState(layout.workspaces.first { it.handle == handle }, ws0.refined()))
                found = layout.state.findViewOrNull(view)
            }
        }
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
        // (Never smaller than the program can be.)
        val (mw, mh) = view.minTiles(wsState)
        minW = mw
        minH = mh
        wGrid = Grid("w", mw.toInt(), maxOf(pyRound(w + 3), mw.toInt()), w, confResizeGridOvr(), confResizeGridM())
        hGrid = Grid("h", mh.toInt(), maxOf(pyRound(h + 3), mh.toInt()), h, confResizeGridOvr(), confResizeGridM())
    }

    override fun onGesture(values: Map<String, Double>) {
        if (closed) return
        val factor = confGestureFactor() * wsState.size
        val dw = factor * (values["delta_x"] ?: 0.0)
        val dh = factor * (values["delta_y"] ?: 0.0)
        val ni: Double
        val nw: Double
        if (w + dw < minW) {
            val d = minW - (w + dw)
            ni = i - d
            nw = minW + d
        } else {
            ni = i
            nw = w + dw
        }
        val nj: Double
        val nh: Double
        if (h + dh < minH) {
            val d = minH - (h + dh)
            nj = j - d
            nh = minH + d
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
    private var wsState = layout.state.workspaceState(workspace)
    private var part: MoveResizePart? = null

    // Gliding after a gesture: (from i or w, from j or h, to i or w, to j or h, start, end).
    private var targetViewPos: DoubleArray? = null
    private var targetViewSize: DoubleArray? = null
    private var targetLayoutPos: DoubleArray? = null

    private var running = true
    private var wantsClose = false
    private var ticker: AutoCloseable? = null

    init {
        val found = layout.state.findViewOrNull(view)
        if (found != null) {
            wsState = found.second
            workspace = layout.workspaces.first { it.handle == found.third }
        } else {
            log.warn("Unexpected: Could not access view $view state")
        }
    }

    // (A thread at 120 Hz there; a timer on the compositor's thread here.)
    override fun postInit() {
        log.trace { "MoveResizeOverlay: Starting..." }
        ticker = layout.every(10) { tick() }
    }

    private fun tick() {
        if (!running) return
        val t = now()
        var inProgress = false
        targetViewPos?.let { (ii, ij, fi, fj, it0, ft) ->
            inProgress = true
            if (t > ft) {
                layout.state.updateViewState(view) { i = fi; j = fj }
                targetViewPos = null
            } else {
                val perc = (t - it0) / (ft - it0)
                layout.state.updateViewState(view) { i = ii + perc * (fi - ii); j = ij + perc * (fj - ij) }
            }
            layout.damage()
        }
        targetViewSize?.let { (iw, ih, fw, fh, it0, ft) ->
            inProgress = true
            if (t > ft) {
                layout.state.updateViewState(view) { w = fw; h = fh; scaleOrigin = null }
                targetViewSize = null
            } else {
                val perc = (t - it0) / (ft - it0)
                layout.state.updateViewState(view) { w = iw + perc * (fw - iw); h = ih + perc * (fh - ih) }
            }
            layout.damage()
        }
        val tl = targetLayoutPos
        if (tl != null) {
            inProgress = true
            val (ii, ij, fi, fj, it0, ft) = tl
            if (t > ft) {
                wsState.i = fi
                wsState.j = fj
                targetLayoutPos = null
            } else {
                val perc = (t - it0) / (ft - it0)
                wsState.i = ii + perc * (fi - ii)
                wsState.j = ij + perc * (fj - ij)
            }
            layout.damage()
        } else if (part != null) {
            // The view kept in sight while it moves.
            val vs = layout.state.findViewOrNull(view)?.first
            if (vs == null) {
                log.warn("Unexpected: Could not access view $view state")
            } else {
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
                    targetLayoutPos = doubleArrayOf(wsState.i, wsState.j, fi, fj, t, t + confAnimT())
                }
            }
        }
        if (!inProgress && wantsClose) {
            running = false
            ticker?.close()
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
        LowpassGesture(gesture, confLpInertia(), confLpFreq(), layout::every).listener(GestureListener(next::onGesture, ::finish))
        return true
    }

    private fun finish() {
        log.trace { "MoveResizeOverlay: Finishing gesture" }
        part?.let { p ->
            val r = p.close()
            part = null
            workspace = r.workspace
            if (r.i != r.fi || r.j != r.fj) targetViewPos = doubleArrayOf(r.i, r.j, r.fi, r.fj, now(), now() + r.t)
            if (r.w != r.fw || r.h != r.fh) targetViewSize = doubleArrayOf(r.w, r.h, r.fw, r.fh, now(), now() + r.t)
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
        ticker?.close()
    }

    override fun exitTransition(): Pair<LayoutState?, Double?> {
        // Clean up any possible mishaps (should not be necessary).
        val vs = layout.state.findViewOrNull(view)?.first ?: run {
            log.warn("Unexpected: Error accessing view $view state")
            return null to 0.0
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
        return state to confAnimT()
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
