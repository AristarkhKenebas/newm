package newm

import dev.caulis.geometry.Rect
import dev.caulis.log.Logger
import dev.caulis.tiling.Gaps
import dev.caulis.tiling.LayoutWindow
import dev.caulis.tiling.Side
import kotlin.math.max

private val log = Logger("layout")

// screens { }: the wall in whole screens. Each screen holds up to windowsPerScreen windows,
// tiled by its layout; the next window opens on the next screen; the view moves by whole
// screens. enabled = false: newm's free wall of tiles, as before.
private val confScreensEnabled = configuredValue("screens.enabled", true)
private val confWindowsPerScreen = configuredValue("screens.windows_per_screen", 2)
private val confScreensLayout = configuredValue("screens.layout", "columns")
private val confMasterRatio = configuredValue("screens.master_ratio", 0.6)
private val confMasterSide = configuredValue("screens.master_side", "left")
private val confScreensGaps = configuredValue("screens.gaps", 0)
private val confOverflow = configuredValue("screens.overflow", "right")
private val confPullBack = configuredValue("screens.pull_back", true)
private val confLayoutCycle = configuredValue("screens.cycle", listOf("columns", "rows", "grid", "master", "monocle"))
private val confCustomLayouts = configuredValue<Map<String, Any?>>("screens.custom_layouts", emptyMap())

// screens { windowsUnderBar = false }: the windows are cut at the bars' edge, so nothing of a
// window but the screen's own shows under a bar (true: as newm, the wall goes on under them).
internal val confWindowsUnderBar = configuredValue("screens.windows_under_bar", false)

/** One screen of a workspace: its own layout (null: screens { layout }) and its windows' handles in order (the first: the master). */
internal class Screen(var layout: String?, val windows: MutableList<Int>)

/**
 * The wall in screens (screens { }): one screen is a tile — exactly the monitor's room
 * left by the bars — and its windows are parts of it, placed by a layout (Caulis' tiling
 * layouts: columns, rows, dwindle, spiral, master; newm's grid; monocle: one a screen).
 */
internal object Screens {
    /** The names of the layouts there are (besides screens { customLayouts }). */
    val LAYOUTS = listOf("columns", "rows", "grid", "dwindle", "spiral", "master", "centered-master", "monocle")

    fun on(): Boolean = confScreensEnabled()

    /** The next screen is below (true), else right of the one before. */
    fun down(): Boolean = when (val o = confOverflow()) {
        "down" -> true
        "right" -> false
        else -> false.also { if (warned.add("overflow $o")) log.warn("screens { overflow = \"$o\" } is neither right nor down: right") }
    }

    fun pullBack(): Boolean = confPullBack()

    /** Where screen [page] starts on the wall (in tiles). */
    fun origin(page: Int): Pair<Double, Double> = if (down()) 0.0 to page.toDouble() else page.toDouble() to 0.0

    /** The screen at (i, j) on the wall (in tiles). */
    fun pageAt(i: Double, j: Double): Int = kotlin.math.floor(if (down()) j else i).toInt().coerceAtLeast(0)

    /** The screen [page]'s layout on [ws]. */
    fun layoutOf(ws: WorkspaceState, page: Int): String = ws.pageLayouts[page] ?: defaultLayout()

    /** screens { layout }: the layout of a screen without one of its own. */
    fun defaultLayout(): String = confScreensLayout()

    /** How many windows a screen of [layout] holds. */
    fun capacity(layout: String): Int = if (layout == "monocle") 1 else confWindowsPerScreen().coerceAtLeast(1)

    /** The layout after [layout] in screens { cycle }. */
    fun nextLayout(layout: String): String {
        val cycle = confLayoutCycle().ifEmpty { listOf("columns") }
        return cycle[(cycle.indexOf(layout) + 1).mod(cycle.size)]
    }

    private val warned = HashSet<String>()

    // A window as a layout sees it (its order is all a layout here needs; each one its own object).
    private class Slot(val index: Int) : LayoutWindow {
        override val title = ""
        override val appId = ""
        override val minWidth = 0
        override val minHeight = 0
        override val maxWidth = 0
        override val maxHeight = 0
    }

    /** Where [n] windows go on a screen [width] x [height] (pixels, from 0, 0) by [name]. */
    fun arrange(name: String, n: Int, width: Int, height: Int): List<Rect> {
        if (n <= 0) return emptyList()
        val g = confScreensGaps().coerceIn(0, Gaps.MAX)
        val gaps = Gaps(inner = g, outer = g)
        val area = Rect(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
        val side = when (val s = confMasterSide()) {
            "left" -> Side.LEFT
            "right" -> Side.RIGHT
            "top" -> Side.TOP
            "bottom" -> Side.BOTTOM
            else -> Side.LEFT.also { if (warned.add("side $s")) log.warn("screens { masterSide = \"$s\" } is none of left, right, top, bottom: left") }
        }
        val ratio = confMasterRatio().coerceIn(0.05, 0.95)
        val layout = when (name) {
            "grid" -> return grid(gaps.inset(area), n, g)
            "monocle" -> return List(n) { gaps.inset(area) }
            "columns" -> dev.caulis.tiling.Columns(gaps)
            "rows" -> dev.caulis.tiling.Rows(gaps)
            "dwindle" -> dev.caulis.tiling.Dwindle(gaps)
            "spiral" -> dev.caulis.tiling.Spiral(gaps)
            "master" -> dev.caulis.tiling.MasterStack(gaps, ratio, side)
            "centered-master" -> dev.caulis.tiling.CenteredMaster(gaps, ratio)
            else -> confCustomLayouts()[name] as? dev.caulis.tiling.Layout ?: run {
                if (warned.add("layout $name")) log.warn("screens: no layout \"$name\" (there are ${(LAYOUTS + confCustomLayouts().keys).joinToString()}): columns")
                dev.caulis.tiling.Columns(gaps)
            }
        }
        val rects = try {
            layout.createModel().arrange(List(n) { Slot(it) }, area, null)
        } catch (e: Exception) {
            log.error("screens: the layout $name failed: columns instead", e)
            dev.caulis.tiling.Columns(gaps).createModel().arrange(List(n) { Slot(it) }, area, null)
        }
        // (A layout giving fewer places than windows: the rest on the whole screen.)
        return List(n) { rects.getOrNull(it) ?: gaps.inset(area) }
    }

    // Hyprland's halves and quarters as windows come: as many columns as a square needs, the
    // first ones with a window fewer (2: halves; 3: a half and two quarters; 4: quarters).
    private fun grid(area: Rect, n: Int, gap: Int): List<Rect> {
        val columns = Math.ceil(Math.sqrt(n.toDouble())).toInt()
        val rows = (n + columns - 1) / columns
        val fewer = columns * rows - n
        val out = ArrayList<Rect>(n)
        fun part(start: Int, length: Int, k: Int, of: Int): Pair<Int, Int> {
            val a = start + Math.round(k * (length + gap).toDouble() / of).toInt()
            val b = start + Math.round((k + 1) * (length + gap).toDouble() / of).toInt() - gap
            return a to (b - a).coerceAtLeast(1)
        }
        for (c in 0 until columns) {
            val inColumn = if (c < fewer) rows - 1 else rows
            val (x, w) = part(area.x, area.width, c, columns)
            for (r in 0 until inColumn) {
                val (y, h) = part(area.y, area.height, r, inColumn)
                out += Rect(x, y, w, h)
            }
        }
        return out
    }
}

/** The workspace's screens in order, each with its windows in order (tiled ones; a new one without a screen: the screen under its middle, last). */
internal fun WorkspaceState.readScreens(): MutableList<Screen> {
    val tiled = viewStates.entries.filter { (_, s) -> s.isTiled && s.swallowed == null }
    for ((_, s) in tiled) if (s.page < 0) {
        s.page = Screens.pageAt(s.i + s.w / 2, s.j + s.h / 2)
        s.slot = Int.MAX_VALUE / 2
    }
    return tiled.groupBy { it.value.page }.toSortedMap().map { (page, list) ->
        Screen(pageLayouts[page], list.sortedBy { it.value.slot }.map { it.key }.toMutableList())
    }.toMutableList()
}

/**
 * [screens] as the workspace's: empty ones gone, too full ones giving their last windows to a
 * screen of their own right after, each window's screen and place written, then tiled.
 */
internal fun WorkspaceState.writeScreens(screens: List<Screen>) {
    val list = screens.filter { it.windows.isNotEmpty() }.toMutableList()
    var k = 0
    while (k < list.size) {
        val s = list[k]
        val cap = Screens.capacity(s.layout ?: Screens.defaultLayout())
        if (s.windows.size > cap) {
            val rest = s.windows.subList(cap, s.windows.size)
            list.add(k + 1, Screen(null, rest.toMutableList()))
            rest.clear()
        }
        k++
    }
    val layouts = HashMap<Int, String>()
    for ((page, s) in list.withIndex()) {
        s.layout?.let { layouts[page] = it }
        for ((slot, h) in s.windows.withIndex()) viewStates[h]?.let { it.page = page; it.slot = slot }
    }
    pageLayouts = layouts
    placeOnScreens(list)
}

/** The screens as they are, tidied and tiled anew. */
internal fun WorkspaceState.tileScreens() = writeScreens(readScreens())

// Each window of [screens] in its place (not one being moved or opening: it glides there itself);
// the fullscreen one's screen stays as it is (that window covers it).
private fun WorkspaceState.placeOnScreens(screens: List<Screen>) {
    val fullscreenPage = stateBeforeFullscreen?.let { Screens.pageAt(it.fi + .5, it.fj + .5) }
    val uw = max(1, Math.round(usableWidth()).toInt())
    val uh = max(1, Math.round(usableHeight()).toInt())
    for ((page, screen) in screens.withIndex()) {
        if (page == fullscreenPage) continue
        val (oi, oj) = Screens.origin(page)
        // Resized by hand (and still the same windows and layout): their parts as they were made.
        val key = screenKey(page, screen)
        val states = screen.windows.mapNotNull { viewStates[it] }
        if (states.isNotEmpty() && states.all { it.sized?.key == key }) {
            for (s in states) {
                if (s.moveOrigin != null || s.scaleOrigin != null) continue
                val r = s.sized ?: continue
                s.i = oi + r.i; s.j = oj + r.j; s.w = r.w; s.h = r.h
            }
            continue
        }
        states.forEach { it.sized = null }
        val rects = Screens.arrange(Screens.layoutOf(this, page), screen.windows.size, uw, uh)
        for ((k, h) in screen.windows.withIndex()) {
            val s = viewStates[h] ?: continue
            if (s.moveOrigin != null || s.scaleOrigin != null) continue
            val r = rects[k]
            s.i = oi + r.x.toDouble() / uw
            s.j = oj + r.y.toDouble() / uh
            s.w = r.width.toDouble() / uw
            s.h = r.height.toDouble() / uh
        }
    }
    // A swallowed window in its swallower's place.
    for (s in viewStates.values) s.swallowed?.let { viewStates[it] }?.let { b -> s.i = b.i; s.j = b.j; s.w = b.w; s.h = b.h }
}

/**
 * Screens turned off: windows still on screens become tiles of newm's wall again, one beside
 * the other in their order (screen by screen).
 */
internal fun WorkspaceState.untileScreens() {
    if (viewStates.values.none { it.isTiled && it.page >= 0 }) return
    val order = viewStates.entries.filter { it.value.isTiled && it.value.page >= 0 }.sortedWith(compareBy({ it.value.page }, { it.value.slot }))
    for ((n, e) in order.withIndex()) {
        val s = e.value
        s.page = -1
        s.slot = 0
        s.i = n.toDouble()
        s.j = 0.0
        s.w = 1.0
        s.h = 1.0
    }
    pageLayouts = HashMap()
    log.info { "Screens off: ${order.size} windows are tiles of the wall again" }
}

/** The view on screen [page]: the whole screen (zoomed out: that screen in sight, on whole screens). */
internal fun WorkspaceState.focusingScreen(page: Int): WorkspaceState {
    val (pi, pj) = Screens.origin(page)
    if (size <= 1.0 + 1e-6) return copy(i = pi, j = pj, size = 1.0)
    var ti = kotlin.math.min(i, pi)
    var tj = kotlin.math.min(j, pj)
    ti = max(ti, pi + 1 - size)
    tj = max(tj, pj + 1 - size)
    return copy(i = ti, j = tj)
}

/** Fullscreen on screens: the window covers its screen, the view on it. */
internal fun WorkspaceState.withScreenFullscreen(view: View, state: ViewState): WorkspaceState {
    val (pi, pj) = Screens.origin(state.page)
    val result = copy(stateBeforeFullscreen = BeforeFullscreen(i, j, size, pi, pj, 1.0), i = pi, j = pj, size = 1.0)
    result.updateViewState(view) { i = pi; j = pj; w = 1.0; h = 1.0 }
    return result
}

/**
 * After a window of screen [page] went (screens { pullBack }): "compact" (pullBackMode, the
 * default) — a neighbouring screen whose windows fit into the room left moves in, nothing else
 * (a screen left empty goes anyway); "shift" — windows of the screens after it move up until
 * that one is full again.
 */
internal fun WorkspaceState.pullBackScreens(page: Int) {
    if (!Screens.pullBack() || page < 0) return
    val screens = readScreens()
    if (Placement.pullBackMode() == "compact") {
        val k = screens.indexOfFirst { s -> s.windows.firstOrNull()?.let { viewStates[it]?.page } == page }
        if (k >= 0) Placement.closeGap(screens, k)
        writeScreens(screens)
        return
    }
    for (k in screens.indices) {
        if (screens[k].windows.isEmpty()) continue
        val pageNow = viewStates[screens[k].windows.first()]?.page ?: continue
        if (pageNow < page) continue
        // (From this screen on: each takes from the next until it is full.)
        for (q in k until screens.size - 1) {
            val cap = Screens.capacity(screens[q].layout ?: Screens.defaultLayout())
            while (screens[q].windows.size < cap && screens[q + 1].windows.isNotEmpty()) screens[q].windows += screens[q + 1].windows.removeAt(0)
        }
        break
    }
    writeScreens(screens)
}

/**
 * Window [handle] let go with its middle at ([ci], [cj]) (moved with the mouse or fingers):
 * onto the screen there, at the place there nearest to that point — changing places with the
 * window there when the screen is full (or is its own); past the last screen, one of its own.
 */
internal fun WorkspaceState.dropOnScreen(handle: Int, ci: Double, cj: Double) {
    val screens = readScreens()
    val from = screens.indexOfFirst { handle in it.windows }
    if (from < 0) return
    val target = Screens.pageAt(ci, cj)
    if (target >= screens.size) {
        if (screens[from].windows.size > 1) {
            screens[from].windows.remove(handle)
            screens.add(Screen(null, mutableListOf(handle)))
        }
        writeScreens(screens)
        return
    }
    val to = screens[target]
    val full = to.windows.size >= Screens.capacity(to.layout ?: Screens.defaultLayout())
    // The places of the screen there as they would be: its windows (and this one, if it has room).
    val count = if (target == from || full) to.windows.size else to.windows.size + 1
    val uw = max(1, Math.round(usableWidth()).toInt())
    val uh = max(1, Math.round(usableHeight()).toInt())
    val (oi, oj) = Screens.origin(target)
    val rects = Screens.arrange(to.layout ?: Screens.defaultLayout(), count, uw, uh)
    val px = (ci - oi) * uw
    val py = (cj - oj) * uh
    val place = rects.indices.minByOrNull { k ->
        val r = rects[k]
        val dx = r.x + r.width / 2.0 - px
        val dy = r.y + r.height / 2.0 - py
        dx * dx + dy * dy
    } ?: 0
    when {
        target == from || full -> {
            val other = to.windows.getOrNull(place.coerceAtMost(to.windows.size - 1))
            if (other != null && other != handle) {
                val a = screens[from].windows.indexOf(handle)
                val b = to.windows.indexOf(other)
                screens[from].windows[a] = other
                to.windows[b] = handle
            }
        }
        else -> {
            screens[from].windows.remove(handle)
            to.windows.add(place.coerceAtMost(to.windows.size), handle)
        }
    }
    log.info { "Screens: window $handle let go on screen ${target + 1}, place ${place + 1}" }
    writeScreens(screens)
}

// What a hand-made size of a screen's windows holds for: the same windows, in the same order, the same layout.
private fun WorkspaceState.screenKey(page: Int, screen: Screen): Int = java.util.Objects.hash(screen.windows, Screens.layoutOf(this, page))

// resize { }: how windows are resized on screens.
private val confResizeQuarters = configuredValue("resize.quarters", true)
private val confResizeOnBorder = configuredValue("resize.on_border", true)
private val confResizeBorderGrab = configuredValue("resize.border_grab_size", 10)
private val confResizeBorderButton = configuredValue("resize.border_button", "left")
private val confResizeClamp = configuredValue("resize.clamp_to_screen", true)
private val confResizeKeepAspect = configuredValue("resize.keep_aspect", false)
private val confResizeMinSize = configuredValue("resize.min_size", 0.1)
private val confResizeKeyStep = configuredValue("resize.key_step", 0.1)
private val confResizeMouseButton = configuredValue<String?>("resize.mouse_button", null)

/** resize { }: how windows on screens are resized (by the mouse, at their borders, by keys). */
internal object ScreenResize {
    /** The grabbed quarter decides the corner (true); false: always the bottom right one. */
    fun quarters(): Boolean = confResizeQuarters()
    /** A window resized by dragging the room around it (no modifier needed). */
    fun onBorder(): Boolean = confResizeOnBorder()
    /** How far from a window (pixels) its border can be grabbed. */
    fun borderGrab(): Int = confResizeBorderGrab().coerceIn(1, 100)
    /** The button that drags a border. */
    fun borderButton(): Int? = mouseButtonCode(confResizeBorderButton())
    fun keepAspect(): Boolean = confResizeKeepAspect()
    fun keyStep(): Double = confResizeKeyStep().coerceIn(0.01, 0.5)
    /** resize { mouseButton }, else mouse { resizeButton }. */
    fun mouseButton(): String? = confResizeMouseButton()
}

private const val EDGE_EPS = 1e-4

/**
 * Moves edge ([edgeX]: -1 left, 1 right; [edgeY]: -1 top, 1 bottom; 0: not that way) of
 * window [handle] by [dx], [dy] (parts of a screen) on its screen, with the line it lies on:
 * the windows on its other side shrink or grow, nothing gets smaller than resize { minSize },
 * and (resize { clampToScreen }) nothing goes past the screen's edges. An edge on the screen's
 * edge: its other edge moves instead (the split follows the pointer, as Hyprland). The screen
 * keeps these sizes until its windows or layout change. True if anything moved.
 */
internal fun WorkspaceState.resizeOnScreen(handle: Int, edgeX: Int, edgeY: Int, dx: Double, dy: Double): Boolean {
    val screens = readScreens()
    val page = screens.indexOfFirst { handle in it.windows }.takeIf { it >= 0 } ?: return false
    val screen = screens[page]
    val (oi, oj) = Screens.origin(page)
    // The screen's windows' parts of it (x, y, w, h), as shown now.
    val rects = LinkedHashMap<Int, DoubleArray>()
    for (h in screen.windows) viewStates[h]?.let { s -> rects[h] = doubleArrayOf(s.i - oi, s.j - oj, s.w, s.h) }
    if (handle !in rects) return false
    val clamp = confResizeClamp()
    val min = confResizeMinSize().coerceIn(0.01, 0.5)
    var ddy = dy
    if (confResizeKeepAspect() && edgeX != 0 && edgeY != 0) rects[handle]?.let { r -> ddy = dx * edgeX * edgeY * r[3] / r[2] }
    var moved = false
    if (edgeX != 0 && dx != 0.0) moved = moveLine(rects, handle, horizontal = true, far = edgeX > 0, d = dx, clamp, min) || moved
    if (edgeY != 0 && ddy != 0.0) moved = moveLine(rects, handle, horizontal = false, far = edgeY > 0, d = ddy, clamp, min) || moved
    if (!moved) return false
    writeScreens(screens)
    val key = screenKey(page, screen)
    for ((h, r) in rects) viewStates[h]?.sized = PageRect(r[0], r[1], r[2], r[3], key)
    placeOnScreens(screens.filter { it.windows.isNotEmpty() })
    return true
}

// One line of the screen moved by [d]: the edge of [handle] across x ([horizontal]) or y, its [far] one (right, bottom) or near one.
private fun moveLine(rects: MutableMap<Int, DoubleArray>, handle: Int, horizontal: Boolean, far: Boolean, d: Double, clamp: Boolean, min: Double): Boolean {
    val a = if (horizontal) 0 else 1   // start along this way
    val l = if (horizontal) 2 else 3   // length along it
    val r = rects.getValue(handle)
    fun startOf(x: DoubleArray) = x[a]
    fun endOf(x: DoubleArray) = x[a] + x[l]
    var line = if (far) endOf(r) else startOf(r)
    // On the screen's edge: the other edge (the split) instead.
    if (clamp && (line < EDGE_EPS || line > 1 - EDGE_EPS)) {
        line = if (far) startOf(r) else endOf(r)
        if (line < EDGE_EPS || line > 1 - EDGE_EPS) return false
    }
    // The windows on the line, joined to this one across it (a split of a part of the screen moves alone).
    val o = 1 - a
    val ol = 5 - l
    fun across(x: DoubleArray, y: DoubleArray) = x[o] < y[o] + y[ol] - EDGE_EPS && y[o] < x[o] + x[ol] - EDGE_EPS
    fun onLine(x: DoubleArray) = Math.abs(endOf(x) - line) < EDGE_EPS || Math.abs(startOf(x) - line) < EDGE_EPS
    val joined = mutableSetOf(handle)
    var grew = true
    while (grew) {
        grew = false
        for ((h, x) in rects) if (h !in joined && onLine(x) && joined.any { across(x, rects.getValue(it)) }) { joined += h; grew = true }
    }
    val before = joined.filter { Math.abs(endOf(rects.getValue(it)) - line) < EDGE_EPS }
    val after = joined.filter { Math.abs(startOf(rects.getValue(it)) - line) < EDGE_EPS }
    var lo = before.maxOfOrNull { startOf(rects.getValue(it)) + min } ?: if (clamp) 0.0 else -1.0
    var hi = after.minOfOrNull { endOf(rects.getValue(it)) - min } ?: if (clamp) 1.0 else 2.0
    if (clamp) { lo = lo.coerceAtLeast(0.0); hi = hi.coerceAtMost(1.0) }
    if (hi < lo) return false
    val next = (line + d).coerceIn(lo, hi)
    if (Math.abs(next - line) < 1e-9) return false
    for (h in before) rects.getValue(h).let { x -> x[l] = next - x[a] }
    for (h in after) rects.getValue(h).let { x -> x[l] = endOf(x) - next; x[a] = next }
    return true
}

/**
 * The edges a resize of [rect] grabbed at ([px], [py]) moves: the quarter grabbed (resize {
 * quarters }) — its top right quarter the top right corner — else the bottom right one.
 */
internal fun grabbedEdges(rect: Rect, px: Double, py: Double): Pair<Int, Int> {
    if (!ScreenResize.quarters()) return 1 to 1
    val ex = if (px < rect.x + rect.width / 2.0) -1 else 1
    val ey = if (py < rect.y + rect.height / 2.0) -1 else 1
    return ex to ey
}
