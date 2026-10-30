package newm

import dev.caulis.core.Drawing
import dev.caulis.core.Monitor
import dev.caulis.core.OverlayCanvas
import dev.caulis.geometry.Rect
import dev.caulis.log.Logger
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private val log = Logger("layout")

// minimap { }: a small map of the wall in a corner of each monitor — its screens (or tiles),
// its windows (the focused one in the accent colour) and the part in view outlined; a click on
// it goes there. Off by default.
private val confMinimapEnabled = configuredValue("minimap.enabled", false)
// "top-left", "top-right", "bottom-left", "bottom-right".
private val confMinimapCorner = configuredValue("minimap.corner", "bottom-right")
// How wide it is at most (desktop units); its height follows the wall's shape.
private val confMinimapWidth = configuredValue("minimap.width", 240.0)
// How far from the monitor's edges (desktop units, within the room the bars leave).
private val confMinimapMargin = configuredValue("minimap.margin", 16.0)
// Seconds it shows after the view or a window moved (0: always).
private val confMinimapShowFor = configuredValue("minimap.show_for", 2.0)
// How strong its background is (0..1).
private val confMinimapOpacity = configuredValue("minimap.opacity", 0.85)

/** The wall's map of one monitor: where it is drawn and how the wall's tiles map onto it. */
internal class MinimapGeometry(val box: Rect, val i0: Double, val j0: Double, val unitX: Double, val unitY: Double) {
    /** The point of the wall (tiles) at [x], [y] of the desktop. */
    fun wallAt(x: Double, y: Double): Pair<Double, Double> = (i0 + (x - box.x - PAD) / unitX) to (j0 + (y - box.y - PAD) / unitY)

    /** The desktop rectangle of the wall's [i], [j], [w], [h]. */
    fun rectOf(i: Double, j: Double, w: Double, h: Double): Rect = Rect(
        (box.x + PAD + (i - i0) * unitX).toInt(), (box.y + PAD + (j - j0) * unitY).toInt(),
        max(1, (w * unitX).toInt()), max(1, (h * unitY).toInt()),
    )

    companion object {
        const val PAD = 8
    }
}

/** minimap { }: the map, drawn above the windows (Caulis drawing), and its clicks. */
internal class Minimap(private val layout: Layout) : Drawing {
    // What was shown last per workspace (the view and the windows), and since when (nanoseconds).
    private val lastSeen = HashMap<Int, String>()
    private val changedAt = HashMap<Int, Long>()

    fun enabled(): Boolean = confMinimapEnabled()

    /** Where the map of [ws] is and how it maps, or null (no windows, or off). */
    fun geometry(ws: Workspace): MinimapGeometry? {
        if (!enabled()) return null
        val s = runCatching { layout.state.workspaceState(ws) }.getOrNull() ?: return null
        val tiles = s.viewStates.values.filter { it.isTiled && it.swallowed == null && it.w > 0 && it.h > 0 }
        if (tiles.isEmpty()) return null
        // The wall's extent: its windows' tiles (on screens: whole screens) and the view.
        var i0 = floor(min(tiles.minOf { it.i }, s.i))
        var j0 = floor(min(tiles.minOf { it.j }, s.j))
        var i1 = ceil(max(tiles.maxOf { it.i + it.w }, s.i + s.size))
        var j1 = ceil(max(tiles.maxOf { it.j + it.h }, s.j + s.size))
        if (i1 - i0 < 1) i1 = i0 + 1
        if (j1 - j0 < 1) j1 = j0 + 1
        // A tile has the monitor's shape (the room the bars leave).
        val aspect = s.usableHeight().coerceAtLeast(1.0) / s.usableWidth().coerceAtLeast(1.0)
        val maxW = confMinimapWidth().coerceIn(40.0, ws.width / 2.0)
        var unitX = (maxW - 2 * MinimapGeometry.PAD) / (i1 - i0)
        var unitY = unitX * aspect
        // (Not taller than half the monitor: then smaller.)
        val maxH = ws.height / 2.0 - 2 * MinimapGeometry.PAD
        if (unitY * (j1 - j0) > maxH) {
            unitY = maxH / (j1 - j0)
            unitX = unitY / aspect
        }
        val w = (unitX * (i1 - i0)).toInt() + 2 * MinimapGeometry.PAD
        val h = (unitY * (j1 - j0)).toInt() + 2 * MinimapGeometry.PAD
        val m = confMinimapMargin().toInt()
        val left = (ws.posX + s.leftExcluded).toInt() + m
        val top = (ws.posY + s.topExcluded).toInt() + m
        val right = (ws.posX + ws.width - s.rightExcluded).toInt() - m - w
        val bottom = (ws.posY + ws.height - s.bottomExcluded).toInt() - m - h
        val (x, y) = when (confMinimapCorner()) {
            "top-left" -> left to top
            "top-right" -> right to top
            "bottom-left" -> left to bottom
            "bottom-right" -> right to bottom
            else -> (right to bottom).also { if (warned.add(confMinimapCorner())) log.warn("minimap { corner = \"${confMinimapCorner()}\" } is none of top-left, top-right, bottom-left, bottom-right: bottom-right") }
        }
        return MinimapGeometry(Rect(x, y, w, h), i0, j0, unitX, unitY)
    }

    private val warned = HashSet<String>()

    // Shown now on [ws]: always (showFor 0), else for showFor seconds after what it shows changed.
    fun shown(ws: Workspace, now: Long = System.nanoTime()): Boolean {
        if (!enabled()) return false
        val showFor = confMinimapShowFor()
        if (showFor <= 0) return true
        val s = runCatching { layout.state.workspaceState(ws) }.getOrNull() ?: return false
        val key = buildString {
            append(Math.round(s.i * 100)).append(',').append(Math.round(s.j * 100)).append(',').append(Math.round(s.size * 100))
            for ((h, v) in s.viewStates) if (v.isTiled) append(';').append(h).append(':').append(Math.round(v.i * 10)).append(',').append(Math.round(v.j * 10))
        }
        if (lastSeen[ws.handle] != key) {
            // (The first look is no change: nothing shows at the start.)
            if (lastSeen.containsKey(ws.handle)) changedAt[ws.handle] = now
            lastSeen[ws.handle] = key
        }
        val at = changedAt[ws.handle] ?: return false
        return now - at < (showFor * 1e9).toLong()
    }

    private fun workspaceOf(monitor: Monitor): Workspace? = layout.workspaces.firstOrNull { monitor in it.outputs }

    override fun draw(monitor: Monitor, canvas: OverlayCanvas) {
        if (!enabled()) return
        val ws = workspaceOf(monitor) ?: return
        if (!shown(ws)) return
        val g = geometry(ws) ?: return
        val s = layout.state.workspaceState(ws)
        val dark = layout.caulis.colorSchemeNow == dev.caulis.core.appearance.ColorScheme.DARK
        val accent = layout.barModel.palette?.scheme(dark)?.primary ?: color("#88C0D0")
        val alpha = confMinimapOpacity().toFloat().coerceIn(0f, 1f)
        canvas.roundedFill(g.box, 10f, color("#14161E"), alpha)
        // On screens: each screen's room, faintly.
        if (Screens.on()) {
            val pages = s.viewStates.values.filter { it.isTiled && it.page >= 0 }.map { it.page }.distinct()
            for (p in pages) {
                val (oi, oj) = Screens.origin(p)
                canvas.roundedFill(inset(g.rectOf(oi, oj, 1.0, 1.0), 1), 4f, color("#FFFFFF"), 0.06f * alpha)
            }
        }
        val focused = layout.findFocusedView()?.handle
        for ((h, v) in s.viewStates) {
            if (!v.isTiled || v.swallowed != null || v.w <= 0 || v.h <= 0) continue
            canvas.roundedFill(inset(g.rectOf(v.i, v.j, v.w, v.h), 2), 3f, if (h == focused) accent else color("#AEB4C8"), if (h == focused) alpha else 0.45f * alpha)
        }
        // The part in view.
        canvas.border(g.rectOf(s.i, s.j, s.size, s.size), 1.5f, dev.caulis.render.Paint.solid(color("#FFFFFF")), radius = 3f, alpha = 0.9f * alpha)
    }

    override fun animating(now: Long): Boolean = enabled() && confMinimapShowFor() > 0 && layout.workspaces.any { w -> changedAt[w.handle]?.let { now - it < (confMinimapShowFor() * 1e9).toLong() + 50_000_000 } == true }

    private fun inset(r: Rect, by: Int) = Rect(r.x + by, r.y + by, max(1, r.width - 2 * by), max(1, r.height - 2 * by))

    /**
     * A click at [x], [y]: on a map shown there, the view goes to the place clicked (on screens
     * that screen, its first window focused; on the wall the window there, else the view centred
     * there). True: taken.
     */
    fun click(x: Double, y: Double): Boolean {
        if (!enabled()) return false
        val ws = layout.workspaces.firstOrNull { w -> x >= w.posX && x < w.posX + w.width && y >= w.posY && y < w.posY + w.height } ?: return false
        if (!shown(ws)) return false
        val g = geometry(ws) ?: return false
        if (x < g.box.x || x >= g.box.x + g.box.width || y < g.box.y || y >= g.box.y + g.box.height) return false
        val (wi, wj) = g.wallAt(x, y)
        log.info { "Minimap: to ${"%.1f".format(wi)}, ${"%.1f".format(wj)} of workspace ${ws.handle}" }
        layout.minimapGoTo(ws, wi, wj)
        return true
    }
}
