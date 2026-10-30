package newm

import dev.caulis.log.Logger
import kotlin.math.floor
import kotlin.math.max

private val log = Logger("layout")

/**
 * Windows and monitors. Each monitor has a wall of its own (one of newm's workspaces); a
 * window goes from one to another by keys (the monitor in a direction, or the next one),
 * dragged with the mouse or the fingers, or in the overview. It keeps its size (its tiles; on
 * screens its share of a screen) and lands where a new window would go there ("compact"), or
 * where it was let go.
 */
internal object Monitors {

    /**
     * The workspace beside [from] in the direction [dx], [dy] (-1, 0 or 1): of those whose
     * middle lies that way, one that lies alongside it (overlapping across the way) before one
     * that is off to a corner, then the nearest. Null: none that way.
     */
    fun beside(workspaces: List<Workspace>, from: Workspace, dx: Int, dy: Int): Workspace? {
        val cx = from.posX + from.width / 2.0
        val cy = from.posY + from.height / 2.0
        fun middle(w: Workspace) = (w.posX + w.width / 2.0) to (w.posY + w.height / 2.0)
        fun alongside(w: Workspace): Boolean =
            if (dx != 0) w.posY < from.posY + from.height && from.posY < w.posY + w.height
            else w.posX < from.posX + from.width && from.posX < w.posX + w.width
        return workspaces.filter { it !== from && it.handle != from.handle }.filter { w ->
            val (ox, oy) = middle(w)
            (dx > 0 && ox > cx) || (dx < 0 && ox < cx) || (dy > 0 && oy > cy) || (dy < 0 && oy < cy)
        }.minWithOrNull(compareBy({ if (alongside(it)) 0 else 1 }, { w ->
            val (ox, oy) = middle(w)
            (ox - cx) * (ox - cx) + (oy - cy) * (oy - cy)
        }))
    }

    /**
     * [s] with [view] moved onto [target]'s wall, or null if it cannot go (it is on [target]
     * already, has no state, or is swallowed). Tiled, it keeps its size: [w] x [h] tiles on
     * newm's wall; on screens it takes a place on a screen. [at]: the place on [target]'s wall
     * (in tiles) its middle was let go at — it goes there (on screens: the screen there, the
     * place nearest to it); null: next to the window last focused there, as "compact" places a
     * new window. Floating, it keeps its place relative to the view. The wall it left closes
     * the gap (screens { pullBack }); the target's view is brought onto it. [s] is not changed.
     */
    fun moved(s: LayoutState, view: View, target: Workspace, at: Pair<Double, Double>? = null): LayoutState? {
        val (vs0, _, fromHandle) = s.findViewOrNull(view) ?: return null
        if (fromHandle == target.handle || vs0.swallowed != null || vs0.isLayer) return null
        val next = s.copy()
        val src = next.workspaceStates[fromHandle] ?: return null
        val tws = next.workspaceStates[target.handle] ?: return null
        val vs = src.viewState(view).copy()
        src.withoutViewState(view)
        val h = view.handle
        if (vs.isTiled && Screens.on()) {
            // (The screen it left: the gap closed as when a window closes.)
            if (vs.page >= 0) if (Screens.pullBack()) src.pullBackScreens(vs.page) else src.tileScreens()
            val screens = tws.readScreens()
            tws.viewStates[h] = ViewState(isTiled = true, stackIdx = vs.stackIdx)
            if (at != null) {
                dropOntoScreens(tws, screens, h, at.first, at.second)
            } else {
                val anchor = target.focusViewHint?.takeIf { a -> a != h && tws.viewStates[a]?.let { it.isTiled && it.swallowed == null } == true }
                val k = anchor?.let { a -> screens.indexOfFirst { a in it.windows } }?.takeIf { it >= 0 }
                Placement.placeOnScreens(screens, k, anchor.takeIf { k != null }, h, Screens.pageAt(tws.i + .5, tws.j + .5))
            }
            tws.writeScreens(screens)
            val now = tws.viewState(view)
            log.info { "${view.appId} onto the monitor of workspace ${target.handle}: screen ${now.page + 1}, place ${now.slot + 1}" }
        } else if (vs.isTiled) {
            val w = max(1, pyRound(vs.w))
            val hh = max(1, pyRound(vs.h))
            val (i, j) = if (at != null) {
                val bi = floor(at.first - w / 2.0 + .5).toInt()
                val bj = floor(at.second - hh / 2.0 + .5).toInt()
                val box = Placement.Box(bi, bj, w, hh)
                if (Placement.boxes(tws).values.none { it.overlaps(box) }) bi to bj
                else Placement.placeOnWall(tws, ViewState(i = bi.toDouble(), j = bj.toDouble(), w = 1.0, h = 1.0), w, hh, except = h)
            } else {
                val anchor = target.focusViewHint?.let { a -> tws.viewStates[a]?.takeIf { a != h && it.isTiled && it.swallowed == null && it.w > 0 } }
                Placement.placeOnWall(tws, anchor, w, hh, except = h)
            }
            tws.viewStates[h] = vs.copy(i = i.toDouble(), j = j.toDouble(), w = w.toDouble(), h = hh.toDouble(), moveOrigin = null, scaleOrigin = null, page = -1, slot = 0, sized = null)
            log.info { "${view.appId} onto the monitor of workspace ${target.handle}: at $i, $j, $w x $hh tiles" }
        } else {
            // Floating: the same place in the view (a smaller view: within it).
            val f = tws.size / src.size.coerceAtLeast(1e-6)
            val (fi, fj) = at ?: ((tws.i + (vs.floatPos.first - src.i) * f) to (tws.j + (vs.floatPos.second - src.j) * f))
            tws.viewStates[h] = vs.copy(floatPos = fi to fj, moveOrigin = null, scaleOrigin = null)
            log.info { "${view.appId} (floating) onto the monitor of workspace ${target.handle}" }
        }
        // Its dialogs and other floating children go along, at the same place relative to it.
        for ((ch, cs) in src.viewStates.toList()) {
            if (cs.isTiled || cs.isLayer) continue
            val child = view.wm.viewByHandle(ch) ?: continue
            if (generateSequence(child.parent) { it.parent }.none { it === view }) continue
            src.viewStates.remove(ch)
            val f = tws.size / src.size.coerceAtLeast(1e-6)
            tws.viewStates[ch] = cs.copy(floatPos = (tws.i + (cs.floatPos.first - src.i) * f) to (tws.j + (cs.floatPos.second - src.j) * f))
        }
        tws.validateStackIndices(view)
        val shown = if (tws.isInOverview()) tws else tws.focusingView(view)
        return next.settingWorkspaceState(target, shown)
    }

    // On screens: window [handle] let go at [ci], [cj] of the wall: onto the screen there, at
    // the place nearest to that point (a full screen gives its last window a screen of its
    // own right after it); past the last screen, a screen of its own.
    private fun dropOntoScreens(ws: WorkspaceState, screens: MutableList<Screen>, handle: Int, ci: Double, cj: Double) {
        val page = Screens.pageAt(ci, cj)
        if (page >= screens.size) {
            screens += Screen(null, mutableListOf(handle))
            return
        }
        val to = screens[page]
        val layout = to.layout ?: Screens.defaultLayout()
        val uw = max(1, Math.round(ws.usableWidth()).toInt())
        val uh = max(1, Math.round(ws.usableHeight()).toInt())
        val (oi, oj) = Screens.origin(page)
        val rects = Screens.arrange(layout, to.windows.size + 1, uw, uh)
        val px = (ci - oi) * uw
        val py = (cj - oj) * uh
        val place = rects.indices.minByOrNull { k ->
            val r = rects[k]
            val dx = r.x + r.width / 2.0 - px
            val dy = r.y + r.height / 2.0 - py
            dx * dx + dy * dy
        } ?: to.windows.size
        to.windows.add(place.coerceAtMost(to.windows.size), handle)
    }
}
