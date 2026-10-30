package newm

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * windows { newPlacement = "compact" } as worked out on paper: walls of whole tiles (the
 * windows' handles 1, 2, ...), the focused one, and where the new window goes — next to it,
 * the wall staying compact, nothing moving, the view moving the least.
 */
class PlacementTest {

    @BeforeTest
    fun settings() = Config.loadConfig(emptyMap())

    @AfterTest
    fun reset() = Config.loadConfig(emptyMap())

    // A wall of 1 x 1 windows at [tiles] (handle n + 1 for the n-th), the view at [view] (i, j, size).
    private fun wall(vararg tiles: Pair<Int, Int>, view: Triple<Double, Double, Double> = Triple(0.0, 0.0, 1.0)): WorkspaceState {
        val ws = WorkspaceState(Workspace(null, 0, 0, 1920, 1080), i = view.first, j = view.second, size = view.third)
        for ((n, t) in tiles.withIndex()) ws.viewStates[n + 1] = ViewState(isTiled = true, i = t.first.toDouble(), j = t.second.toDouble(), w = 1.0, h = 1.0)
        return ws
    }

    private fun tilesOf(ws: WorkspaceState) = ws.viewStates.mapValues { (_, s) -> s.i.toInt() to s.j.toInt() }

    // Places a new window next to window [focused] and checks what the owner asked for: nothing moved, next to it, no hole left that need not be.
    private fun placeAndCheck(ws: WorkspaceState, focused: Int): Pair<Int, Int> {
        val before = tilesOf(ws)
        val placed = Placement.placeOnWall(ws, ws.viewStates[focused], 1, 1)
        assertEquals(before, tilesOf(ws), "no window moved")
        val f = ws.viewStates.getValue(focused)
        assertEquals(1, Math.abs(placed.first - f.i.toInt()) + Math.abs(placed.second - f.j.toInt()), "next to the focused window: $placed")
        // Compact: of the free places next to it, none keeps the box around all windows smaller
        // (its longer side first, then its area) — no hole left that need not be.
        val taken = Placement.boxes(ws).values
        fun measure(i: Int, j: Int) = Placement.bounds(taken + Placement.Box(i, j, 1, 1))!!.let { maxOf(it.w, it.h) * 1000 + it.w * it.h }
        val fi = f.i.toInt()
        val fj = f.j.toInt()
        val best = listOf(fi + 1 to fj, fi - 1 to fj, fi to fj + 1, fi to fj - 1)
            .filter { (i, j) -> taken.none { it.overlaps(Placement.Box(i, j, 1, 1)) } }.minOf { (i, j) -> measure(i, j) }
        assertEquals(best, measure(placed.first, placed.second), "the most compact of the places next to it: $placed")
        return placed
    }

    @Test
    fun `the owner's case - four in a square, the bottom left focused - the new one goes beside it, nothing moves, no upside-down U`() {
        // 1 2
        // 4 3   (4 focused: the bottom left)
        val ws = wall(0 to 0, 1 to 0, 1 to 1, 0 to 1, view = Triple(0.0, 0.0, 2.0))
        val placed = placeAndCheck(ws, 4)
        // Below it or left of it: either keeps a 3 x 2 box with one empty corner (right-of-focused
        // made 1 _ 2 / 4 N 3, a hole in the top row).
        assertTrue(placed == (0 to 2) || placed == (-1 to 1), "below or left of the focused one: $placed")
        assertTrue(Placement.cameraMove(ws, Placement.Box(placed.first, placed.second, 1, 1)) <= 1.0 + 1e-9, "the view moves one tile at most")
    }

    @Test
    fun `three in a row and one below the first, that one focused - the hole beside it is filled`() {
        // 1 2 3
        // 4 _ _   (4 focused)
        val ws = wall(0 to 0, 1 to 0, 2 to 0, 0 to 1)
        assertEquals(1 to 1, placeAndCheck(ws, 4))
    }

    @Test
    fun `a row of three, the middle one focused - the new one goes below it, not at the end of a sausage`() {
        val ws = wall(0 to 0, 1 to 0, 2 to 0, view = Triple(1.0, 0.0, 1.0))
        val placed = placeAndCheck(ws, 2)
        assertEquals(1 to 1, placed)
        val compact = Placement.compactness(Placement.boxes(ws).values + Placement.Box(placed.first, placed.second, 1, 1))
        val sausage = Placement.compactness(Placement.boxes(ws).values + Placement.Box(3, 0, 1, 1))
        assertTrue(compact < sausage, "more compact than a row of four: $compact < $sausage")
        assertEquals(1.0, Placement.cameraMove(ws, Placement.Box(1, 1, 1, 1)), "one tile down: the least the view can move")
    }

    @Test
    fun `a row of three, the last one focused - below it, not a fourth in the row`() {
        val ws = wall(0 to 0, 1 to 0, 2 to 0, view = Triple(2.0, 0.0, 1.0))
        assertEquals(2 to 1, placeAndCheck(ws, 3))
    }

    @Test
    fun `windows opened one after the other grow into a block, not a row`() {
        val ws = wall(0 to 0)
        var focused = 1
        for (n in 2..9) {
            val (i, j) = placeAndCheck(ws, focused)
            ws.viewStates[n] = ViewState(isTiled = true, i = i.toDouble(), j = j.toDouble(), w = 1.0, h = 1.0)
            focused = n
            val b = Placement.bounds(Placement.boxes(ws).values)!!
            assertTrue(Math.abs(b.w - b.h) <= 1, "near a square after $n windows: ${b.w} x ${b.h}")
        }
        val b = Placement.bounds(Placement.boxes(ws).values)!!
        assertEquals(3 to 3, b.w to b.h, "nine windows: a 3 x 3 block")
    }

    @Test
    fun `nothing free next to the focused one - the fewest windows move aside`() {
        //   1
        // 2 5 3     (5 focused, all four sides taken; 3 has 6 right of it, 1 nothing above)
        //   4
        val ws = wall(1 to 0, 0 to 1, 2 to 1, 1 to 2, 1 to 1, 3 to 1)
        val placed = Placement.placeOnWall(ws, ws.viewStates[5], 1, 1)
        val moved = ws.viewStates.filter { (h, s) -> (s.i.toInt() to s.j.toInt()) != listOf(1 to 0, 0 to 1, 2 to 1, 1 to 2, 1 to 1, 3 to 1)[h - 1] }
        assertEquals(1, moved.size, "one window moved aside: ${moved.keys}")
        assertTrue(Placement.boxes(ws).values.none { it.overlaps(Placement.Box(placed.first, placed.second, 1, 1)) }, "its place is free now")
    }

    @Test
    fun `an empty wall - at 0, 0`() {
        assertEquals(0 to 0, Placement.placeOnWall(wall(), null, 1, 1))
    }

    // --- screens (two windows a screen)

    private fun screens(vararg s: List<Int>) = s.map { Screen(null, it.toMutableList()) }.toMutableList()

    private fun MutableList<Screen>.shape() = map { it.windows.toList() }

    @Test
    fun `screens - room on the focused screen, the new one there after the focused one`() {
        val s = screens(listOf(1), listOf(2, 3))
        Placement.placeOnScreens(s, 0, 1, 9, 0)
        assertEquals(listOf(listOf(1, 9), listOf(2, 3)), s.shape())
    }

    @Test
    fun `screens - the focused screen full, the next has room - there, no screen moves on`() {
        val s = screens(listOf(1, 2), listOf(3), listOf(4, 5))
        Placement.placeOnScreens(s, 0, 1, 9, 0)
        assertEquals(listOf(listOf(1, 2), listOf(9, 3), listOf(4, 5)), s.shape())
    }

    @Test
    fun `screens - the focused screen full, the one before has room - there`() {
        val s = screens(listOf(1), listOf(2, 3), listOf(4, 5))
        Placement.placeOnScreens(s, 1, 2, 9, 1)
        assertEquals(listOf(listOf(1, 9), listOf(2, 3), listOf(4, 5)), s.shape())
    }

    @Test
    fun `screens - no room next to it - a new screen right after the focused one`() {
        val s = screens(listOf(1, 2), listOf(3, 4), listOf(5, 6))
        Placement.placeOnScreens(s, 1, 3, 9, 1)
        assertEquals(listOf(listOf(1, 2), listOf(3, 4), listOf(9), listOf(5, 6)), s.shape())
    }

    @Test
    fun `screens - a child on a full screen goes to the next one if it has room`() {
        val s = screens(listOf(1, 2), listOf(3))
        Placement.placeChildOnScreens(s, 0, 1, 9)
        assertEquals(listOf(listOf(1, 2), listOf(9, 3)), s.shape())
        val t = screens(listOf(1, 2), listOf(3, 4))
        Placement.placeChildOnScreens(t, 0, 1, 9)
        assertEquals(listOf(listOf(1, 2), listOf(9), listOf(3, 4)), t.shape())
    }

    @Test
    fun `screens - closing - a neighbour that fits moves in, else nothing moves`() {
        // (One window of the first screen closed.)
        val a = screens(listOf(1), listOf(3))
        assertTrue(Placement.closeGap(a, 0))
        assertEquals(listOf(listOf(1, 3), emptyList()), a.shape())
        // [1] [3 4] [5 6]: nothing fits — nothing moves (shift moved 3, 4, 5, 6).
        val b = screens(listOf(1), listOf(3, 4), listOf(5, 6))
        assertTrue(!Placement.closeGap(b, 0))
        assertEquals(listOf(listOf(1), listOf(3, 4), listOf(5, 6)), b.shape())
        // [1 2] [3] [5 6]: the one before fits? No (full) — the next? No. [1] [3] [5 6], 2nd: the one before fits.
        val c = screens(listOf(1), listOf(3), listOf(5, 6))
        assertTrue(Placement.closeGap(c, 1))
        assertEquals(listOf(listOf(1, 3), emptyList(), listOf(5, 6)), c.shape())
    }
}
