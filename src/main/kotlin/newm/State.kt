package newm

import dev.caulis.log.Logger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// newm's state.py: the layout as values (copied, changed, compared), never as live objects.

private val log = Logger("state")

private val confTopBarVn = configuredValue<Boolean?>("panels.top_bar.visible_normal", null)
private val confTopBarVf = configuredValue<Boolean?>("panels.top_bar.visible_fullscreen", null)
private val confBottomBarVn = configuredValue<Boolean?>("panels.bottom_bar.visible_normal", null)
private val confBottomBarVf = configuredValue<Boolean?>("panels.bottom_bar.visible_fullscreen", null)
private val confBarVn = configuredValue<Boolean?>("panels.bar.visible_normal", null)
private val confBarVf = configuredValue<Boolean?>("panels.bar.visible_fullscreen", null)

internal val confNativeTopBarEnabled = configuredValue("panels.top_bar.native.enabled", false)
internal val confNativeBottomBarEnabled = configuredValue("panels.bottom_bar.native.enabled", false)

private val confDontValidateFullscreen = configuredValue("view.sticky_fullscreen", false)

fun topBarVn(): Boolean = confTopBarVn() ?: confBarVn() ?: true
fun bottomBarVn(): Boolean = confBottomBarVn() ?: confBarVn() ?: true
fun topBarVf(): Boolean = confTopBarVf() ?: confBarVf() ?: false
fun bottomBarVf(): Boolean = confBottomBarVf() ?: confBarVf() ?: false

/** Where a view is moved from, while it is moved: i, j and the workspace. */
data class MoveOrigin(val i: Double, val j: Double, val workspace: Workspace)

/** stack_data: the stack's id, the view's place in it and the stack's length. */
data class StackData(val id: Int, val index: Int, val length: Int)

data class ViewState(
    var isTiled: Boolean = true,
    var isLayer: Boolean = false,
    var swallowed: Int? = null,

    // - Tiled views
    var i: Double = 0.0,
    var j: Double = 0.0,
    var w: Double = 0.0,
    var h: Double = 0.0,

    // stack_id / idx / len
    var stackData: StackData = StackData(-1, 0, 1),

    // global stack_idx (Compare z-index) to restore ordering
    var stackIdx: Int = 0,

    var moveOrigin: MoveOrigin? = null,
    var scaleOrigin: Pair<Double, Double>? = null,

    // - Floating views
    var floatPos: Pair<Double, Double> = 0.0 to 0.0,
    var floatSize: Pair<Int, Int> = 0 to 0,

    // - Layer views
    var layerInitial: Boolean = false,

    // How far it has appeared (0: just opening, see-through; 1: there).
    var appear: Double = 1.0,

    // - On screens (screens { }): the screen it is on (from 0; -1: none yet) and its place there (0 first: the master).
    var page: Int = -1,
    var slot: Int = 0,
    // Resized by hand on its screen: its part of the screen (0..1), kept while the screen's windows and layout stay the same.
    var sized: PageRect? = null,
) {
    fun ijwh(): DoubleArray {
        var i = this.i
        var j = this.j
        var w = this.w
        var h = this.h
        moveOrigin?.let { i = it.i; j = it.j }
        scaleOrigin?.let { w = it.first; h = it.second }
        return doubleArrayOf(i, j, w, h)
    }
}

/** A window's part of its screen (0..1 each way), resized by hand; [key]: the screen's windows and layout it was made for. */
data class PageRect(val i: Double, val j: Double, val w: Double, val h: Double, val key: Int)

/** state_before_fullscreen: i, j, size before, and i, j, size in fullscreen. */
data class BeforeFullscreen(val i: Double, val j: Double, val size: Double, val fi: Double, val fj: Double, val fsize: Double)

/** state_before_overview: i, j, size, size_origin, top_bar_dy, bottom_bar_dy. */
data class BeforeOverview(val i: Double, val j: Double, val size: Double, val sizeOrigin: Double?, val topBarDy: Double, val bottomBarDy: Double)

class WorkspaceState(
    val ws: Workspace,
    var i: Double = -0.5,
    var j: Double = -0.5,
    var size: Double = 2.0,
    var sizeOrigin: Double? = null,
    var intermediateRows: MutableList<Int> = ArrayList(),
    var intermediateCols: MutableList<Int> = ArrayList(),
    // Non-null indicates fullscreen
    var stateBeforeFullscreen: BeforeFullscreen? = null,
    var topBarDy: Double = 0.0,
    var bottomBarDy: Double = 0.0,
    var topExcluded: Double = 0.0,
    var bottomExcluded: Double = 0.0,
    // Non-null indicates we are in overview mode
    var stateBeforeOverview: BeforeOverview? = null,
    // Room bars at the left and right edges keep (newm had top and bottom only).
    var leftExcluded: Double = 0.0,
    var rightExcluded: Double = 0.0,
) {
    internal var viewStates: MutableMap<Int, ViewState> = LinkedHashMap()

    /** The screens' own layouts, by screen (changed while running: Super+Space); the others take screens { layout }. */
    internal var pageLayouts: MutableMap<Int, String> = HashMap()

    // --- Register / Unregister

    fun withViewState(view: View, state: ViewState): WorkspaceState {
        viewStates[view.handle] = state
        return this
    }

    fun withoutViewState(view: View): WorkspaceState {
        viewStates.remove(view.handle)
        return this
    }

    // --- Copy / Update

    /** The width tiles share: the workspace's, but for side bars. */
    fun usableWidth(): Double = ws.width - leftExcluded - rightExcluded

    /** The height tiles share: the workspace's, but for bars at the top and bottom. */
    fun usableHeight(): Double = ws.height - topExcluded - bottomExcluded

    fun copy(
        ws: Workspace = this.ws,
        i: Double = this.i, j: Double = this.j, size: Double = this.size, sizeOrigin: Double? = this.sizeOrigin,
        stateBeforeFullscreen: BeforeFullscreen? = this.stateBeforeFullscreen,
        topBarDy: Double = this.topBarDy, bottomBarDy: Double = this.bottomBarDy,
        topExcluded: Double = this.topExcluded, bottomExcluded: Double = this.bottomExcluded,
        stateBeforeOverview: BeforeOverview? = this.stateBeforeOverview,
        leftExcluded: Double = this.leftExcluded, rightExcluded: Double = this.rightExcluded,
    ): WorkspaceState {
        val res = WorkspaceState(
            ws, i, j, size, sizeOrigin, ArrayList(intermediateRows), ArrayList(intermediateCols), stateBeforeFullscreen,
            topBarDy, bottomBarDy, topExcluded, bottomExcluded, stateBeforeOverview, leftExcluded, rightExcluded,
        )
        res.viewStates = viewStates.mapValuesTo(LinkedHashMap()) { it.value.copy() }
        res.pageLayouts = HashMap(pageLayouts)
        return res
    }

    fun replacingViewState(view: View, change: ViewState.() -> Unit): WorkspaceState {
        val res = copy()
        res.updateViewState(view, change)
        return res
    }

    fun updateViewState(view: View, change: ViewState.() -> Unit) {
        val s = viewStates[view.handle]
        if (s == null) log.warn("Unexpected: Unable to update view $view state") else s.change()
    }

    fun validateFullscreen() {
        if (confDontValidateFullscreen()) return
        val before = stateBeforeFullscreen ?: return
        var fi = before.fi
        var fj = before.fj
        val fsize = before.fsize

        var invalid = abs(i - fi) + abs(j - fj) + abs(size - fsize) > .01
        if (abs(i - fi) + abs(j - fj) > 0.01 && abs(size - fsize) < 0.01) {
            for ((_, v) in viewStates) {
                val (i_, j_, w, h) = v.ijwh().toList()
                if (abs(w - size) + abs(h - size) + abs(i_ - i) + abs(j_ - j) < 0.01) {
                    fi = i_
                    fj = j_
                    stateBeforeFullscreen = BeforeFullscreen(before.i, before.j, before.size, fi, fj, fsize)
                    invalid = false
                    break
                }
            }
        }

        if (invalid) {
            stateBeforeFullscreen = null
            val (iStolen, jStolen) = clearIntermediate(pyRound(i), pyRound(j))
            i -= iStolen
            j -= jStolen
            constrain()
        }
    }

    /**
     * Set stack_data = idx, len for every view according to as-is placement; place
     * [movedView] on top of stack if it is set (and set stack_idx analogously).
     */
    fun validateStackIndices(movedView: View? = null) {
        fun overlaps(s1: ViewState, s2: ViewState): Boolean {
            val (i, j, w, h) = s1.ijwh().toList()
            val (i_, j_, w_, h_) = s2.ijwh().toList()
            if (!((i_ - .2 <= i && i < i_ + w_ - .8) || (i - .2 <= i_ && i_ < i + w - .8))) return false
            if (!((j_ - .2 <= j && j < j_ + h_ - .8) || (j - .2 <= j_ && j_ < j + h - .8))) return false
            return true
        }

        fun stacksOverlap(s1: List<Pair<Int, ViewState>>, s2: List<Pair<Int, ViewState>>): Boolean =
            s1.any { (_, s) -> s2.any { (_, sp) -> overlaps(s, sp) } }

        val stacks = viewStates.filter { (_, s) -> s.isTiled && s.swallowed == null }
            .map { (v, s) -> mutableListOf(v to s) }.toMutableList()
        var change = true
        while (change) {
            change = false
            outer@ for (a in stacks.indices) {
                for (b in 0 until a) {
                    if (stacksOverlap(stacks[a], stacks[b])) {
                        stacks[a] += stacks[b]
                        stacks.removeAt(b)
                        change = true
                        break@outer
                    }
                }
            }
        }

        for ((sId, stack) in stacks.withIndex()) {
            if (movedView != null) {
                for ((v, s) in stack) {
                    if (v == movedView.handle) {
                        val maxIdx = stack.maxOf { it.second.stackIdx }
                        if (s.stackIdx < maxIdx) s.stackIdx = maxIdx + 1
                        break
                    }
                }
            }
            val sorted = stack.sortedBy { it.second.stackIdx }
            for ((idx, entry) in sorted.withIndex()) entry.second.stackData = StackData(sId, idx, sorted.size)

            // Occasionally reset stack_idx
            if (stack.size == 1) stack[0].second.stackIdx = stack[0].first
        }
    }

    fun validateBars(wm: Layout, wmState: LayoutState) {
        topExcluded = 0.0
        bottomExcluded = 0.0
        // Bars at the sides (an exclusive zone at the left or right): out of the way but in fullscreen.
        val (left, right) = wm.barWidths(ws)
        leftExcluded = if (isFullscreen()) 0.0 else left
        rightExcluded = if (isFullscreen()) 0.0 else right

        if (topBarVn() || topBarVf() || bottomBarVn() || bottomBarVf()) {
            var (topBarHeight, bottomBarHeight) = wm.barHeights(ws, wmState)

            if (confNativeTopBarEnabled()) topBarHeight = nativeBarRoom(top = true).toDouble()
            if (confNativeBottomBarEnabled()) bottomBarHeight = nativeBarRoom(top = false).toDouble()

            val fs = isFullscreen()
            val topV = if (fs) topBarVf() else topBarVn()
            val bottomV = if (fs) bottomBarVf() else bottomBarVn()

            if (topV) {
                topExcluded = topBarHeight
                topBarDy = 1.0
            } else {
                topExcluded = 0.0
                topBarDy = if (isInOverview()) 1.0 else 0.0
            }

            if (bottomV) {
                bottomExcluded = bottomBarHeight
                bottomBarDy = 1.0
            } else {
                bottomExcluded = 0.0
                bottomBarDy = if (isInOverview()) 1.0 else 0.0
            }
        }
    }

    fun constrain() {
        // On screens: the windows tiled on theirs (no rows or columns to close: a screen left empty goes).
        if (Screens.on()) tileScreens() else untileScreens()
        var (minI, minJ, maxI, maxJ) = extent().toList()
        val minIi = floor(minI)
        val minJi = floor(minJ)
        val maxIi = ceil(maxI)
        val maxJi = ceil(maxJ)
        val iSize = maxIi - minIi + 1
        val jSize = maxJi - minJi + 1

        if (size > iSize) {
            i = minIi + .5 * iSize - .5 * size
        } else {
            if (i < minIi) i = minIi.toDouble()
            if (i + size - 1 > maxIi) i = maxIi - size + 1
        }
        if (size > jSize) {
            j = minJi + .5 * jSize - .5 * size
        } else {
            if (j < minJi) j = minJi.toDouble()
            if (j + size - 1 > maxJi) j = maxJi - size + 1
        }

        if (Screens.on()) {
            // Whole screens in view (not in the overview, nor while a gesture zooms between sizes).
            if (!isInOverview() && abs(size - Math.round(size)) < 1e-6) {
                i = Math.round(i).toDouble()
                j = Math.round(j).toDouble()
            }
            return
        }

        val usedRows = HashSet<Int>()
        val usedCols = HashSet<Int>()
        for ((_, s) in viewStates) {
            if (!s.isTiled) continue
            val (i_, j_, w_, h_) = s.ijwh().map(::pyRound)
            for (x in i_ until i_ + w_) usedCols += x
            for (y in j_ until j_ + h_) usedRows += y
        }

        val cols = usedCols.sorted()
        val rows = usedRows.sorted()
        val removeCols = ArrayList<Int>()
        val removeRows = ArrayList<Int>()
        for (k in 0 until cols.size - 1) for (x in cols[k] + 1 until cols[k + 1]) removeCols += x
        for (k in 0 until rows.size - 1) for (x in rows[k] + 1 until rows[k + 1]) removeRows += x

        for (y in removeRows.reversed()) {
            for ((_, s) in viewStates) {
                if (s.j >= y) s.j -= 1 else if (s.j + s.h - 1 >= y) s.h = max(1.0, s.h - 1)
            }
        }
        for (x in removeCols.reversed()) {
            for ((_, s) in viewStates) {
                if (s.i >= x) s.i -= 1 else if (s.i + s.w - 1 >= x) s.w = max(1.0, s.w - 1)
            }
        }
    }

    private fun insertIntermediateCol(at: Int) {
        for ((_, s) in viewStates) {
            if (s.i >= at) s.i += 1 else if (s.i + s.w - 1 >= at) s.w += 1
        }
        intermediateCols.add(at)
    }

    private fun insertIntermediateRow(at: Int) {
        for ((_, s) in viewStates) {
            if (s.j >= at) s.j += 1 else if (s.j + s.h - 1 >= at) s.h += 1
        }
        intermediateRows.add(at)
    }

    private fun clearIntermediate(iRef: Int? = null, jRef: Int? = null): Pair<Int, Int> {
        var iStolen = 0
        var jStolen = 0
        for (y in intermediateRows.sorted().reversed()) {
            for ((_, s) in viewStates) {
                if (s.j >= y) s.j -= 1 else if (s.j + s.h - 1 >= y) s.h = max(1.0, s.h - 1)
            }
            if (jRef != null && y <= jRef) jStolen += 1
        }
        for (x in intermediateCols.sorted().reversed()) {
            for ((_, s) in viewStates) {
                if (s.i >= x) s.i -= 1 else if (s.i + s.w - 1 >= x) s.w = max(1.0, s.w - 1)
            }
            if (iRef != null && x <= iRef) iStolen += 1
        }
        intermediateRows = ArrayList()
        intermediateCols = ArrayList()
        return iStolen to jStolen
    }

    fun clean(viewHandles: Collection<Int>) {
        val kept = LinkedHashMap<Int, ViewState>()
        for ((h, s) in viewStates) {
            if (h !in viewHandles) log.info { "Detected orphan state: $h - $s" } else kept[h] = s
        }
        viewStates = kept
    }

    // --- Reducers

    fun withOverviewSet(overview: Boolean, view: View? = null): WorkspaceState {
        if (overview == (stateBeforeOverview != null)) return copy()
        if (overview) {
            val (minI, minJ, maxI, maxJ) = extent().toList()
            val width = maxI - minI + 3
            val height = maxJ - minJ + 3
            val size = max(width, height)
            val i = minI - (size - (maxI - minI + 1)) / 2.0
            val j = minJ - (size - (maxJ - minJ + 1)) / 2.0
            return copy(
                i = i, j = j, size = size, sizeOrigin = this.size, topBarDy = 1.0, bottomBarDy = 1.0,
                stateBeforeOverview = BeforeOverview(this.i, this.j, this.size, sizeOrigin, topBarDy, bottomBarDy),
            )
        }
        val before = stateBeforeOverview
        var state = if (before != null) {
            copy(i = before.i, j = before.j, size = before.size, sizeOrigin = before.sizeOrigin, topBarDy = before.topBarDy, bottomBarDy = before.bottomBarDy, stateBeforeOverview = null)
        } else {
            log.warn("Unexpected in WorkspaceState.with_overview_set")
            this
        }
        if (view != null) state = state.focusingView(view)
        return state
    }

    fun focusingView(view: View): WorkspaceState {
        // A floating window (a dialog) has no tiles: its window's are brought into view instead
        // — its own 0, 0 took the view to the wall's corner (IDEA's WakaTime dialog sent one back
        // to the window there). None tiled: the view stays.
        val tiled = generateSequence(view) { it.parent }.firstOrNull { viewStates[it.handle]?.isTiled == true } ?: return this
        val state = viewStates[tiled.handle] ?: return this
        // On screens: the view on its screen, whole (one step to the next screen, none within one).
        if (Screens.on() && state.page >= 0) return focusingScreen(state.page)
        var targetSize = max(max(size, state.w), state.h)
        var targetI = min(i, state.i)
        var targetJ = min(j, state.j)
        targetI = max(targetI, state.i + state.w - targetSize)
        targetJ = max(targetJ, state.j + state.h - targetSize)
        return copy(i = targetI, j = targetJ, size = targetSize)
    }

    fun withFullscreen(view: View): WorkspaceState {
        val state = viewState(view)
        if (Screens.on() && state.page >= 0) return withScreenFullscreen(view, state)
        val i = pyRound(state.i)
        val j = pyRound(state.j)
        val w = pyRound(state.w)
        val h = pyRound(state.h)
        val size = max(w, h)

        val result = copy(stateBeforeFullscreen = BeforeFullscreen(this.i, this.j, this.size, i.toDouble(), j.toDouble(), size.toDouble()), i = i.toDouble(), j = j.toDouble(), size = size.toDouble())
        for (ii in w until size) result.insertIntermediateCol(i + ii)
        for (jj in h until size) result.insertIntermediateRow(j + jj)
        result.updateViewState(view) { this.w = size.toDouble(); this.h = size.toDouble() }
        return result
    }

    fun withoutFullscreen(drop: Boolean = false): WorkspaceState {
        val before = stateBeforeFullscreen ?: return copy()
        var i: Double
        var j: Double
        val size: Double
        if (drop) {
            i = this.i
            j = this.j
            size = this.size
        } else {
            i = before.i
            j = before.j
            size = before.size
            // Possibly invalidate state_before_fullscreen
            if (this.i < i || this.i > i + size - 1) {
                i = this.i
                j = this.j
            }
            if (this.j < j || this.j > j + size - 1) {
                i = this.i
                j = this.j
            }
        }
        val result = copy(stateBeforeFullscreen = null, i = i, j = j, size = size)
        result.clearIntermediate()
        // (The window back in its place on its screen.)
        if (Screens.on()) result.tileScreens()
        return result
    }

    // --- Access information

    fun extent(): DoubleArray {
        var minI = 1000000.0
        var minJ = 1000000.0
        var maxI = -1000000.0
        var maxJ = -1000000.0
        for ((_, s) in viewStates) {
            val origin = s.moveOrigin
            if (origin != null && origin.workspace.handle != ws.handle) continue
            if (s.swallowed != null) continue

            val i: Double
            val j: Double
            val w: Double
            val h: Double
            if (s.isTiled) {
                i = s.i; j = s.j; w = s.w; h = s.h
            } else if (!s.isLayer) {
                i = s.floatPos.first
                j = s.floatPos.second
                w = s.floatSize.first * size / ws.width
                h = s.floatSize.second * size / ws.height
            } else {
                continue
            }
            minI = min(minI, i)
            minJ = min(minJ, j)
            maxI = max(maxI, i + w - 1)
            maxJ = max(maxJ, j + h - 1)
        }
        if (minI == 1000000.0) return doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        return doubleArrayOf(minI, minJ, maxI, maxJ)
    }

    fun isTileFree(i: Int, j: Int): Boolean {
        for ((_, s) in viewStates) {
            if (!s.isTiled) continue
            if (s.swallowed != null) continue
            if (floor(s.i) <= i && i <= ceil(s.i + s.w - 1) && floor(s.j) <= j && j <= ceil(s.j + s.h - 1)) return false
        }
        return true
    }

    fun isFullscreen(): Boolean = stateBeforeFullscreen != null

    fun isInOverview(): Boolean = stateBeforeOverview != null

    fun viewState(view: View): ViewState = viewStates[view.handle] ?: throw NoSuchElementException("no state of view ${view.handle}")

    fun viewStateOrNull(view: View): ViewState? = viewStates[view.handle]

    /** The view states by view handle (read only). */
    val views: Map<Int, ViewState> get() = viewStates

    override fun toString() = "<WorkspaceState i=$i j=$j size=$size sizeOrigin=$sizeOrigin fs=$stateBeforeFullscreen ov=$stateBeforeOverview>"

    override fun equals(other: Any?): Boolean = other is WorkspaceState && ws === other.ws && i == other.i && j == other.j &&
        size == other.size && sizeOrigin == other.sizeOrigin && intermediateRows == other.intermediateRows &&
        intermediateCols == other.intermediateCols && stateBeforeFullscreen == other.stateBeforeFullscreen &&
        topBarDy == other.topBarDy && bottomBarDy == other.bottomBarDy && topExcluded == other.topExcluded &&
        bottomExcluded == other.bottomExcluded && leftExcluded == other.leftExcluded &&
        rightExcluded == other.rightExcluded && stateBeforeOverview == other.stateBeforeOverview && viewStates == other.viewStates &&
        pageLayouts == other.pageLayouts

    override fun hashCode() = java.util.Objects.hash(ws.handle, i, j, size)
}

class LayoutState(
    private val wm: Layout,
    var launcherPerc: Double = 0.0,
    var lockPerc: Double = 0.0,
    var final: Boolean = false,
    var backgroundOpacity: Double = 0.0,
) {
    internal var workspaceStates: MutableMap<Int, WorkspaceState> = LinkedHashMap()

    // --- Register / Unregister

    fun withWorkspaces(layout: Layout): LayoutState {
        // A kept state gets the workspace made anew (its size and monitors now), never the old one.
        for (w in layout.workspaces) {
            val old = workspaceStates[w.handle]
            workspaceStates[w.handle] = when {
                old == null -> WorkspaceState(w)
                old.ws !== w -> old.copy(ws = w)
                else -> old
            }
        }

        val orphans = ArrayList<Pair<Int, ViewState>>()
        val handles = layout.workspaces.map { it.handle }.toSet()
        val orphanWs = workspaceStates.getValue(layout.workspaces[0].handle)
        for (k in workspaceStates.keys.toList()) {
            if (k !in handles) {
                workspaceStates.getValue(k).viewStates.forEach { (l, s) -> orphans += l to s.copy() }
                workspaceStates.remove(k)
            }
        }
        for ((k, o) in orphans) orphanWs.viewStates[k] = o

        validateStackIndices()
        return this
    }

    fun withoutViewState(view: View): LayoutState {
        for ((_, s) in workspaceStates) s.withoutViewState(view)
        return this
    }

    // --- Copy / Update

    fun copy(
        launcherPerc: Double = this.launcherPerc, lockPerc: Double = this.lockPerc,
        final: Boolean = this.final, backgroundOpacity: Double = this.backgroundOpacity,
    ): LayoutState {
        val res = LayoutState(wm, launcherPerc, lockPerc, final, backgroundOpacity)
        res.workspaceStates = workspaceStates.mapValuesTo(LinkedHashMap()) { it.value.copy() }
        return res
    }

    fun replacingWorkspaceState(workspace: Workspace, change: WorkspaceState.() -> WorkspaceState): LayoutState {
        val res = LayoutState(wm, launcherPerc, lockPerc, final, backgroundOpacity)
        res.workspaceStates = workspaceStates.mapValuesTo(LinkedHashMap()) { (h, s) -> if (h == workspace.handle) s.copy().change() else s.copy() }
        return res
    }

    fun settingWorkspaceState(workspace: Workspace, state: WorkspaceState): LayoutState {
        val res = LayoutState(wm, launcherPerc, lockPerc, final, backgroundOpacity)
        res.workspaceStates = workspaceStates.mapValuesTo(LinkedHashMap()) { (h, s) -> if (h != workspace.handle) s.copy() else state }
        return res
    }

    fun updateViewState(view: View, change: ViewState.() -> Unit) {
        val s = findViewOrNull(view)?.first
        if (s == null) log.warn("Unexpected: Unable to update view $view state") else s.change()
    }

    fun moveViewState(view: View, fromWs: Workspace, toWs: Workspace) {
        val from = workspaceStates.getValue(fromWs.handle)
        val to = workspaceStates.getValue(toWs.handle)
        val viewState = from.viewState(view)
        from.withoutViewState(view)
        to.viewStates[view.handle] = viewState
    }

    fun validateFullscreen() = workspaceStates.values.forEach { it.validateFullscreen() }

    fun validateBars() = workspaceStates.values.forEach { it.validateBars(wm, this) }

    fun validateStackIndices(movedView: View? = null) = workspaceStates.values.forEach { it.validateStackIndices(movedView) }

    fun constrain(): LayoutState {
        workspaceStates.values.forEach { it.constrain() }
        return this
    }

    fun clean(viewHandles: Collection<Int>): LayoutState {
        workspaceStates.values.forEach { it.clean(viewHandles) }
        constrain()
        validateFullscreen()
        validateStackIndices()
        validateBars()
        return this
    }

    fun constrainAndValidate(): LayoutState {
        constrain()
        validateFullscreen()
        validateStackIndices()
        validateBars()
        return this
    }

    // --- Reducers

    fun withOverviewSet(overview: Boolean, onlyWorkspace: Workspace? = null, view: View? = null): LayoutState {
        val res = copy()
        for ((h, s) in workspaceStates) {
            if (onlyWorkspace != null && h != onlyWorkspace.handle) continue
            res.workspaceStates[h] = s.withOverviewSet(overview, view)
        }
        return res
    }

    fun focusingView(view: View): LayoutState {
        val res = copy()
        for ((h, s) in workspaceStates) res.workspaceStates[h] = s.focusingView(view)
        return res
    }

    fun unswallowing(view: View): LayoutState {
        val res = copy()
        for ((_, s) in res.workspaceStates) for ((_, vs) in s.viewStates) if (vs.swallowed == view.handle) vs.swallowed = null
        return res
    }

    // --- Access information

    fun workspaceState(workspace: Workspace): WorkspaceState = workspaceStates.getValue(workspace.handle)

    fun viewState(view: View): ViewState = findView(view).first

    fun findView(view: View): Triple<ViewState, WorkspaceState, Int> =
        findViewOrNull(view) ?: throw NoSuchElementException("Could not find view ${view.handle} state")

    fun findViewOrNull(view: View): Triple<ViewState, WorkspaceState, Int>? {
        for ((h, s) in workspaceStates) s.viewStateOrNull(view)?.let { return Triple(it, s, h) }
        return null
    }

    fun allInOverview(): Boolean = workspaceStates.values.all { it.isInOverview() }

    override fun toString() = "<LayoutState launcher=$launcherPerc lock=$lockPerc final=$final background=$backgroundOpacity>"

    override fun equals(other: Any?): Boolean = other is LayoutState && launcherPerc == other.launcherPerc && lockPerc == other.lockPerc &&
        final == other.final && backgroundOpacity == other.backgroundOpacity && workspaceStates == other.workspaceStates

    override fun hashCode() = java.util.Objects.hash(launcherPerc, lockPerc, final)
}
