package newm

import dev.caulis.log.Logger
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private val log = Logger("layout")

// screens { pullBackMode }: how a gap a closing window leaves is closed — "compact" (a
// neighbouring screen whose windows fit moves in, nothing else) or "shift" (each screen after
// it takes from the next until full).
private val confPullBackMode = configuredValue("screens.pull_back_mode", "compact")

/**
 * windows { newPlacement = "compact" } (the default): a new window goes where the wall stays
 * compact around what you are looking at — the free place next to the focused window that
 * keeps all windows' box small and near a square, with the smallest move of the view; nothing
 * moves aside unless no place next to the focused window is free (then as few windows as can be).
 * On screens the same along the screens: the focused one's screen, a neighbour with room, a new
 * screen right after it.
 */
internal object Placement {
    const val COMPACT = "compact"

    /** The ways a new window can be placed (windows { newPlacement }). */
    val NAMES = listOf(COMPACT, "right-of-focused", "beside-focused", "end", "original")

    /** Some tiles: from [i], [j] (whole tiles), [w] x [h] of them. */
    data class Box(val i: Int, val j: Int, val w: Int, val h: Int) {
        val right get() = i + w
        val bottom get() = j + h
        fun overlaps(o: Box) = i < o.right && o.i < right && j < o.bottom && o.j < bottom
        fun moved(di: Int, dj: Int) = Box(i + di, j + dj, w, h)
    }

    /** The tiles a window's state takes (as newm counts them: a part of a tile is the tile). */
    fun boxOf(s: ViewState): Box {
        val (i, j, w, h) = s.ijwh().toList()
        val i0 = floor(i + 1e-6).toInt()
        val j0 = floor(j + 1e-6).toInt()
        val i1 = max(i0 + 1, ceil(i + w - 1e-6).toInt())
        val j1 = max(j0 + 1, ceil(j + h - 1e-6).toInt())
        return Box(i0, j0, i1 - i0, j1 - j0)
    }

    /** The tiled windows of [ws] and their tiles (but [except]). */
    fun boxes(ws: WorkspaceState, except: Int? = null): Map<Int, Box> =
        ws.viewStates.filter { (h, s) -> h != except && s.isTiled && s.swallowed == null && s.w > 0 && s.h > 0 }.mapValues { boxOf(it.value) }

    /** The box around all of [boxes] (null: none). */
    fun bounds(boxes: Collection<Box>): Box? {
        if (boxes.isEmpty()) return null
        val i0 = boxes.minOf { it.i }
        val j0 = boxes.minOf { it.j }
        return Box(i0, j0, boxes.maxOf { it.right } - i0, boxes.maxOf { it.bottom } - j0)
    }

    /**
     * How compact [boxes] lie: the side of the square around them, squared, per window (1.0:
     * one window; a 2 x 2 block of four: 1.0; a row of four: 4.0). Smaller is more compact.
     */
    fun compactness(boxes: Collection<Box>): Double {
        val b = bounds(boxes) ?: return 0.0
        val side = max(b.w, b.h).toDouble()
        return side * side / boxes.sumOf { it.w * it.h }
    }

    // Tiles between [a] and [b] counted as steps from one tile to the next (1: side by side, 2: corner to corner).
    private fun distance(a: Box, b: Box): Int {
        fun gap(a0: Int, a1: Int, b0: Int, b1: Int) = when {
            b0 >= a1 -> b0 - a1 + 1
            a0 >= b1 -> a0 - b1 + 1
            else -> 0
        }
        return gap(a.i, a.right, b.i, b.right) + gap(a.j, a.bottom, b.j, b.bottom)
    }

    // Which side of [anchor] [b] is on: right 0, below 1, left 2, above 3, else 4 (the order ties are decided in).
    private fun side(anchor: Box, b: Box): Int = when {
        b.i >= anchor.right -> 0
        b.j >= anchor.bottom -> 1
        b.right <= anchor.i -> 2
        b.bottom <= anchor.j -> 3
        else -> 4
    }

    /** How far the view of [ws] moves to show [b] (newm's focusing: the least move), in tiles. */
    fun cameraMove(ws: WorkspaceState, b: Box): Double {
        val size = max(ws.size, max(b.w, b.h).toDouble())
        val ti = max(min(ws.i, b.i.toDouble()), b.right - size)
        val tj = max(min(ws.j, b.j.toDouble()), b.bottom - size)
        return abs(ti - ws.i) + abs(tj - ws.j) + abs(size - ws.size)
    }

    // [b] lies within the view [vi], [vj], [size] (a hair's breadth either way).
    private fun inView(vi: Double, vj: Double, size: Double, b: Box): Boolean =
        b.i >= vi - 1e-6 && b.j >= vj - 1e-6 && b.right <= vi + size + 1e-6 && b.bottom <= vj + size + 1e-6

    /**
     * What placing [b] costs (smaller is better): steps from [anchor], the square around all
     * windows growing (twice), their box growing, how far it is from a square, the view's
     * move (half), the focused window leaving the view.
     */
    private fun cost(ws: WorkspaceState, all: Collection<Box>, before: Box, anchor: Box, b: Box): Double {
        val after = bounds(all + b)!!
        val grow = max(after.w, after.h) - max(before.w, before.h)
        val area = after.w * after.h - before.w * before.h
        val square = abs(after.w - after.h)
        val camera = cameraMove(ws, b)
        // (The view focusing the new one: does the focused one stay in sight?)
        val size = max(ws.size, max(b.w, b.h).toDouble())
        val vi = max(min(ws.i, b.i.toDouble()), b.right - size)
        val vj = max(min(ws.j, b.j.toDouble()), b.bottom - size)
        val anchorGone = if (ws.size > 1.0 + 1e-6 && inView(ws.i, ws.j, ws.size, anchor) && !inView(vi, vj, size, anchor)) 1.0 else 0.0
        return distance(anchor, b) + 2.0 * grow + area + square + 0.5 * camera + anchorGone
    }

    /**
     * Where a new window of [w] x [h] tiles goes on newm's wall [ws] by "compact", next to
     * [anchorState] (the focused window; null: the middle of the view). Windows in the way move
     * aside (in [ws]) only when nothing next to it is free. [except]: the new window's own handle.
     */
    fun placeOnWall(ws: WorkspaceState, anchorState: ViewState?, w: Int, h: Int, except: Int? = null): Pair<Int, Int> {
        val taken = boxes(ws, except)
        val before = bounds(taken.values) ?: return 0 to 0
        val anchor = anchorState?.let(::boxOf)
            ?: Box(floor(ws.i + ws.size / 2).toInt(), floor(ws.j + ws.size / 2).toInt(), 1, 1).let { c ->
                // (No window focused: the window nearest the middle of the view.)
                taken.values.minByOrNull { distance(c, it) } ?: c
            }
        fun free(b: Box) = taken.values.none { it.overlaps(b) }
        val area = Box(min(before.i, anchor.i) - w, min(before.j, anchor.j) - h, 0, 0).let { a ->
            Box(a.i, a.j, max(before.right, anchor.right) + w - a.i + 1, max(before.bottom, anchor.bottom) + h - a.j + 1)
        }
        data class Choice(val box: Box, val cost: Double, val side: Int)
        val choices = ArrayList<Choice>()
        for (j in area.j until area.bottom) for (i in area.i until area.right) {
            val b = Box(i, j, w, h)
            if (!free(b)) continue
            choices += Choice(b, cost(ws, taken.values, before, anchor, b), side(anchor, b))
        }
        val best = choices.minWithOrNull(compareBy<Choice>({ it.cost }, { it.side }, { it.box.j }, { it.box.i }))
        if (best != null && choices.any { distance(anchor, it.box) == 1 }) {
            log.info { "Compact: the new window at ${best.box.i}, ${best.box.j} (costs ${"%.1f".format(best.cost)}; ${choices.size} free places looked at)" }
            return best.box.i to best.box.j
        }
        // Nothing free next to it: the side where the fewest windows move aside.
        val sides = listOf(
            Box(anchor.right, anchor.j, w, h) to (1 to 0),
            Box(anchor.i, anchor.bottom, w, h) to (0 to 1),
            Box(anchor.i - w, anchor.j, w, h) to (-1 to 0),
            Box(anchor.i, anchor.j - h, w, h) to (0 to -1),
        )
        val plans = sides.map { (b, d) -> Triple(b, d, push(taken, b, d.first, d.second)) }
        val (b, d, plan) = plans.withIndex().minWith(compareBy({ it.value.third.size }, { it.index })).value
        for ((handle, by) in plan) ws.viewStates[handle]?.let { s -> s.i += d.first * by; s.j += d.second * by }
        log.info { "Compact: nothing free next to the focused window: ${plan.size} windows move aside, the new one at ${b.i}, ${b.j}" }
        return b.i to b.j
    }

    // The windows of [taken] that move (and by how many tiles) when [b] goes where it is,
    // pushing what is in its way in the direction [di], [dj] (and what that runs into, on).
    private fun push(taken: Map<Int, Box>, b: Box, di: Int, dj: Int): Map<Int, Int> {
        val by = HashMap<Int, Int>()
        val queue = ArrayDeque(listOf(b))
        var steps = 0
        while (queue.isNotEmpty() && steps++ < 10_000) {
            val pusher = queue.removeFirst()
            for ((h, box) in taken) {
                val now = box.moved(di * (by[h] ?: 0), dj * (by[h] ?: 0))
                if (!now.overlaps(pusher)) continue
                val need = when {
                    di > 0 -> pusher.right - now.i
                    di < 0 -> now.right - pusher.i
                    dj > 0 -> pusher.bottom - now.j
                    else -> now.bottom - pusher.j
                }
                if (need <= 0) continue
                by[h] = (by[h] ?: 0) + need
                queue += box.moved(di * by.getValue(h), dj * by.getValue(h))
            }
        }
        return by
    }

    /**
     * On screens: the new window [handle] onto [screens] by "compact", next to [anchor] (the
     * focused window) on its screen [k]: there if it has room, else at the near end of a
     * neighbouring screen with room (the next, then the one before), else on a new screen
     * right after it (the fewest screens move on). Null [k]: the screen in view, [page].
     */
    fun placeOnScreens(screens: MutableList<Screen>, k: Int?, anchor: Int?, handle: Int, page: Int) {
        fun room(s: Screen) = s.windows.size < Screens.capacity(s.layout ?: Screens.defaultLayout())
        if (screens.isEmpty()) {
            screens += Screen(null, mutableListOf(handle))
            return
        }
        val at = k ?: page.coerceIn(0, screens.size - 1)
        val s = screens[at]
        when {
            room(s) -> {
                val after = anchor?.let { s.windows.indexOf(it) }?.takeIf { it >= 0 }
                s.windows.add(if (after != null) after + 1 else s.windows.size, handle)
                log.info { "Compact: the new window on screen ${at + 1}, with the focused one" }
            }
            at + 1 < screens.size && room(screens[at + 1]) -> {
                screens[at + 1].windows.add(0, handle)
                log.info { "Compact: screen ${at + 1} full, the new window on the next one (${at + 2}), which had room" }
            }
            at > 0 && room(screens[at - 1]) -> {
                screens[at - 1].windows.add(handle)
                log.info { "Compact: screen ${at + 1} full, the new window on the one before (${at}), which had room" }
            }
            else -> {
                screens.add(at + 1, Screen(null, mutableListOf(handle)))
                log.info { "Compact: no room next to screen ${at + 1}: a new screen after it (${screens.size - at - 2} screens move on)" }
            }
        }
    }

    /**
     * A child window [child] of [parent] on screen [k]: right after its window there; on a
     * full screen at the start of the next one if that has room, else on a new screen right
     * after it.
     */
    fun placeChildOnScreens(screens: MutableList<Screen>, k: Int, parent: Int, child: Int) {
        fun room(s: Screen) = s.windows.size < Screens.capacity(s.layout ?: Screens.defaultLayout())
        val s = screens[k]
        val next = screens.getOrNull(k + 1)
        when {
            room(s) -> s.windows.add(s.windows.indexOf(parent) + 1, child)
            next != null && room(next) -> {
                next.windows.add(0, child)
                log.info { "Child window: screen ${k + 1} full, onto the next one (it had room)" }
            }
            else -> screens.add(k + 1, Screen(null, mutableListOf(child)))
        }
    }

    /** screens { pullBackMode }: "compact" or "shift" (anything else: compact, told once). */
    fun pullBackMode(): String = when (val m = confPullBackMode()) {
        "compact", "shift" -> m
        else -> "compact".also { if (warned.add(m)) log.warn("screens { pullBackMode = \"$m\" } is neither compact nor shift: compact") }
    }

    private val warned = HashSet<String>()

    /**
     * After a window of screen [k] of [screens] went (screens { pullBack }, "compact"): a
     * neighbouring screen whose windows all fit into its room moves in (the next one first),
     * so one screen fewer; else nothing moves. True if anything changed.
     */
    fun closeGap(screens: MutableList<Screen>, k: Int): Boolean {
        fun cap(s: Screen) = Screens.capacity(s.layout ?: Screens.defaultLayout())
        val here = screens.getOrNull(k) ?: return false
        if (here.windows.isEmpty()) return false
        for (a in listOf(k, k - 1)) {
            val left = screens.getOrNull(a) ?: continue
            val right = screens.getOrNull(a + 1) ?: continue
            if (right.windows.isEmpty() || left.windows.size + right.windows.size > cap(left)) continue
            left.windows += right.windows
            right.windows.clear()
            log.info { "Screens: a window closed; screens ${a + 1} and ${a + 2} fit on one: put together" }
            return true
        }
        return false
    }
}
