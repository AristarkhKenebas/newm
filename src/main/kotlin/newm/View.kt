package newm

import dev.caulis.core.Monitor
import dev.caulis.core.Window
import dev.caulis.log.Logger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val log = Logger("view")

private val confCornerRadius = configuredValue("view.corner_radius", 12.0)
private val confPadding = configuredValue("view.padding", 6.0)
private val confFullscreenPadding = configuredValue("view.fullscreen_padding", 0.0)
// Windows without the focus a little see-through (1: not at all; newm issue 139).
private val confUnfocusedOpacity = configuredValue("view.unfocused_opacity", 1.0)
// Windows fade in when they open and out when they close.
private val confFade = configuredValue("view.fade", true)
// The first window of an empty workspace takes the whole screen (false, as newm: a quarter, zoomed out).
private val confFillFirst = configuredValue("view.fill_first", false)

// windows { childPlacement = "below" | "over" | "right" }: where a program's child window (a
// dialog, a photo viewer) opens — tiled below its parent, floating over it (newm), or right of it.
internal val confChildPlacement = configuredValue("windows.child_placement", "below")
// windows { childBySameApp = true }: a window without a parent opened by the program whose window
// has the focus (the same connection and app id: Telegram's photo viewer) counts as its child.
private val confChildBySameApp = configuredValue("windows.child_by_same_app", true)
// ... but not for these programs (their new windows are windows of their own: terminals, browsers).
private val confChildBySameAppExcept = configuredValue("windows.child_by_same_app_except",
    listOf("foot", "footclient", "kitty", "Alacritty", "org.wezfurlong.wezterm", "com.mitchellh.ghostty", "firefox", "chromium", "google-chrome", "brave-browser"))
private val confBorderWsSwitch = configuredValue("view.border_ws_switch", 10.0)
private val confRulesCallback = configuredValue<(View) -> Map<String, Any?>?>("view.rules") { null }
private val confFloatingMinSize = configuredValue("view.floating_min_size", true)
private val confAcceptFullscreenFromViews = configuredValue("view.accept_fullscreen", true)

// Launcher and lock panels are handled in a special way - define size and corner radius here
private val confPanelLockH = configuredValue("panels.lock.h", 0.6)
private val confPanelLockW = configuredValue("panels.lock.w", 0.7)
private val confPanelLockCornerRadius = configuredValue("panels.lock.corner_radius", 50.0)
private val confPanelLauncherH = configuredValue("panels.launcher.h", 0.8)
private val confPanelLauncherW = configuredValue("panels.launcher.w", 0.8)
private val confPanelLauncherCornerRadius = configuredValue("panels.launcher.corner_radius", 0.0)


/**
 * Wait this long before accepting that view won't accept our requested size (newm's
 * RESIZE_PATIENCE: some clients never answer the first configure before mapping).
 */
private const val RESIZE_PATIENCE = .3

/**
 * What newm reads of a window (pywm's view "upstream state"), from the Caulis window: its size
 * and the offset of its visible part (client-side shadows), its size limits, whether it wants
 * to float, has the focus, draws its own decorations.
 */
class Up(window: Window, focused: Boolean) {
    val isMapped = window.mapped
    val size: Pair<Int, Int> = window.visibleRect.let { it.width to it.height }
    val offset: Pair<Int, Int> = window.visibleRect.let { it.x to it.y }
    /** min_w, max_w, min_h, max_h (0: none). */
    val sizeConstraints = intArrayOf(window.minWidth, window.maxWidth, window.minHeight, window.maxHeight)
    val isFloating = window.parent != null || window.fixedSize || window.prefersFloating
    /** Its size cannot change (smallest = largest). */
    val fixedSize = window.fixedSize
    /** An X11 dialog, palette or splash screen. */
    val prefersFloating = window.prefersFloating
    val isFocused = focused
    val showsCsd = !window.serverDecorated
    val fixedOutput: Monitor? = null
}

/** newm's view.py: one window, its place in the layout and how it is shown. */
class View(val wm: Layout, val window: Window, val handle: Int) : Animate<ViewDown>(), Animatable {

    private var rules: Map<String, Any?> = emptyMap()

    /** False if its rule says "focus": false (no focus border around it). */
    val wantsFocusBorder: Boolean get() = rules["focus"] != false

    /** Blur behind it, from its rule: (how far it spreads, passes), or null. */
    val blur: Pair<Int, Int>?
        get() = (rules["blur"] as? Map<*, *>)?.let { b -> ((b["radius"] as? Number)?.toInt() ?: 6) to ((b["passes"] as? Number)?.toInt() ?: 4) }
    private var opacity = 1.0

    // State machine
    private var mapped = false
    var destroyed = false
        private set
    private var waitingForShow = false

    // Initial state while waiting for map
    private val initialTime = now()
    private var initialKind = 0 // 0: panel, 1: layer, 2: float, 3: tiled

    /** It opened floating (else tiled, a panel or a layer). */
    internal val opensFloating: Boolean get() = initialKind == 2
    private var initialState: ViewDown? = null

    var panel: String? = null

    var upState: Up? = null

    val appId: String get() = window.appId
    val title: String get() = window.title
    val pid: Int get() = window.pid
    val role: String get() = "toplevel"
    val parent: View? get() = window.parent?.let(wm::viewOf)

    // The window that had the focus when this one came (it goes back there when this one closes, if it is its child).
    internal val focusedAtOpen: View? = wm.lastShownFocused

    /**
     * The window this one is a child of, for where it opens and where the focus goes when it
     * closes: its parent (xdg parent, X11 transient), else — windows { childBySameApp } — the
     * window of the same program (connection and app id) that had the focus when it came
     * (Telegram's photo viewer has no parent). Null: a window of its own.
     */
    val childOf: View? get() {
        parent?.let { return it }
        if (!confChildBySameApp()) return null
        val f = focusedAtOpen?.takeIf { !it.destroyed && it !== this } ?: return null
        if (appId.isEmpty() || f.appId != appId || appId in confChildBySameAppExcept()) return null
        return f.takeIf { runCatching { window.sameProgramAs(f.window) }.getOrDefault(false) }
    }

    // Decided once when it opens (the focus moves on afterwards).
    private var childOfAtOpen: View? = null
    private var childDecided = false

    /** [childOf] as it was when the window opened (null: a window of its own). */
    internal fun openedAsChildOf(): View? {
        if (!childDecided) {
            childDecided = true
            childOfAtOpen = childOf
            childOfAtOpen?.let { p -> if (parent == null) log.info { "$appId: a window without a parent from the focused window of the same program: its child (${p.handle})" } }
        }
        return childOfAtOpen?.takeIf { !it.destroyed }
    }

    /** Where the focus goes back when this window closes: its parent, or the window focused when it came. Null: none of these. */
    internal fun returnFocusTo(): View? {
        val p = openedAsChildOf() ?: return null
        return p.takeIf { !it.destroyed } ?: focusedAtOpen?.takeIf { !it.destroyed && it !== this }
    }
    val isXwayland: Boolean get() = window.javaClass.name.contains("xwayland", ignoreCase = true)

    override fun toString(): String {
        val up = upState ?: return "<View $handle>"
        return "<View $handle (${parent?.let { "child(${it.handle})" } ?: "root"}): $title, $appId, $role, $pid, xwayland=$isXwayland, floating=${up.isFloating}, focused=${up.isFocused}, panel=$panel, size_constraints=${up.sizeConstraints.toList()}>"
    }

    fun isFloat(state: LayoutState): Boolean {
        if (isPanel()) return false
        val s = state.findViewOrNull(this)?.first ?: return false
        return !s.isTiled && !s.isLayer
    }

    fun isTiled(state: LayoutState): Boolean {
        if (isPanel()) return false
        return state.findViewOrNull(this)?.first?.isTiled ?: false
    }

    fun isPanel(): Boolean = panel != null

    // A rule's [value] as two numbers ([1, 2]), or null (told once per window when it is something else).
    private var badRuleTold = false

    private fun numberPair(value: Any?, name: String): Pair<Double, Double>? {
        if (value == null) return null
        val list = value as? List<*>
        val a = (list?.getOrNull(0) as? Number)?.toDouble()
        val b = (list?.getOrNull(1) as? Number)?.toDouble()
        if (a == null || b == null) {
            if (!badRuleTold) log.warn("Rule: $name is two numbers ([800, 600]), not $value: left out for $appId")
            badRuleTold = true
            return null
        }
        return a to b
    }

    private data class Floating(val floats: Boolean, val sizeHint: Pair<Int, Int>?, val posHint: Pair<Double, Double>?)

    @Suppress("UNCHECKED_CAST")
    private fun decideFloating(): Floating {
        val up = upState
        var floats = up != null && up.isFloating
        // (Two numbers each; anything else in a rule is not taken: a typo never breaks the window's place.)
        val sizeHint = numberPair(rules["float_size"], "float_size")?.let { it.first.toInt() to it.second.toInt() }
        val posHint = numberPair(rules["float_pos"], "float_pos")

        if (up != null) {
            // (Not for being a child: that is windows { childPlacement } below.)
            floats = up.prefersFloating && parent == null
            val c = up.sizeConstraints
            if (c[0] > 0 && c[2] > 0 && c[0] == c[1] && c[2] == c[3]) floats = true
        }
        // A child window: floating over its parent (newm: every window with a parent), or tiled beside it.
        if (openedAsChildOf() != null && confChildPlacement() == "over") floats = true
        // A rule says last (view.rules: float = false tiles a dialog or a fixed-size window too).
        (rules["float"] as? Boolean)?.let { rule ->
            if (rule != floats) log.info { "Rule: ${if (rule) "floating" else "tiled"} although it would ${if (floats) "float" else "tile"}: $this" }
            floats = rule
        }
        return Floating(floats, sizeHint, posHint)
    }

    // --- Panel

    private fun initPanel(): ViewDown = ViewDown()

    private fun reducerPanel(state: LayoutState, ws: Workspace): ViewDown {
        val result = ViewDown(opacity = opacity)
        result.logicalBox = Box.ZERO
        if (panel == "launcher") {
            result.zIndex = 1000.0
            result.acceptsInput = true
            result.cornerRadius = confPanelLauncherCornerRadius()
            result.size = pyRound(ws.width * confPanelLauncherW()) to pyRound(ws.height * confPanelLauncherH())
            result.box = Box(
                ws.posX + (ws.width - result.size.first) / 2.0,
                ws.posY + (ws.height - result.size.second) / 2.0 + (1.0 - state.launcherPerc) * ws.height,
                result.size.first.toDouble(), result.size.second.toDouble(),
            )
        } else if (panel == "lock") {
            result.zIndex = 10000.0
            result.acceptsInput = true
            result.cornerRadius = confPanelLockCornerRadius()
            result.lockEnabled = true
            result.size = pyRound(ws.width * confPanelLockW()) to pyRound(ws.height * confPanelLockH())
            result.box = Box(
                ws.posX + (ws.width - result.size.first) / 2.0,
                ws.posY + (ws.height - result.size.second) / 2.0 + (1.0 - state.lockPerc) * ws.height,
                result.size.first.toDouble(), result.size.second.toDouble(),
            )
        }
        result.logicalBox = result.box
        return result
    }

    private fun showPanel(ws: Workspace, state: LayoutState, wsState: WorkspaceState): Pair<LayoutState?, LayoutState?> {
        log.info { "Show - panel: $panel $this" }
        damage()
        // Place dummy ViewState
        val wsState1 = wsState.withViewState(this, ViewState(isTiled = false, isLayer = true))
        return state.settingWorkspaceState(ws, wsState1) to null
    }

    // --- Floating

    /**
     * Two options: CSD or no decoration necessary (no masking), or SSD (masking and corner
     * radius). Heuristic based on the decoration protocols and the offset.
     */
    private fun needsSsd(up: Up): Boolean {
        var ssd = true
        if (up.showsCsd) ssd = false
        if (up.offset != (0 to 0)) ssd = false // Assume some CSD
        if (appId == "catapult") ssd = false
        return ssd
    }

    private fun initFloating(up: Up, sizeHint: Pair<Int, Int>?): ViewDown {
        val result = ViewDown()
        var (width, height) = up.size
        if (sizeHint != null) {
            width = sizeHint.first
            height = sizeHint.second
        } else if (confFloatingMinSize() && (width <= 0 || height <= 0)) {
            // (Only for a program that chose no size itself: the size it drew is its own wish —
            // a dialog squeezed to its smallest came out narrow and askew, as WakaTime's key one.)
            if (up.sizeConstraints[0] > 0 && up.sizeConstraints[2] > 0) {
                width = up.sizeConstraints[0]
                height = up.sizeConstraints[2]
            }
        }
        val (minW, maxW0, minH, maxH0) = up.sizeConstraints.toList()
        val maxW = if (maxW0 <= 0) width else maxW0
        val maxH = if (maxH0 <= 0) height else maxH0
        width = max(minW, min(maxW, width))
        height = max(minH, min(maxH, height))
        result.floating = true
        result.size = width to height
        log.trace { "Floating size decision: ${up.size} (${up.sizeConstraints.toList()}) --> ${width}x$height" }
        return result
    }

    internal fun reducerFloating(up: Up, state: LayoutState, selfState: ViewState, ws: Workspace, wsState: WorkspaceState): ViewDown {
        val result = ViewDown()
        result.floating = true
        result.acceptsInput = true
        result.cornerRadius = confCornerRadius() / max(1.0, wsState.size / 2.0)

        // z_index based on hierarchy
        var depth = 0
        var p = parent
        while (p != null) {
            depth += 1
            p = p.parent
        }
        result.zIndex = depth + 2.0
        // Keep focused view on top
        if (isFocused()) result.zIndex += 1

        // Handle client size
        result.size = if (selfState.floatSize.first > 0) selfState.floatSize else (-1 to -1)
        var (width, height) = up.size.let { it.first.toDouble() to it.second.toDouble() }
        // For animation purposes
        if (selfState.floatSize == (0 to 0)) {
            width = 0.0
            height = 0.0
        }

        // Handle box
        val size = wsState.sizeOrigin ?: wsState.size
        width = pyRound(width * size / wsState.size).toDouble()
        height = pyRound(height * size / wsState.size).toDouble()

        val (i, j) = selfState.floatPos
        var x = i - wsState.i
        var y = j - wsState.j
        x *= ws.width / wsState.size
        y *= ws.height / wsState.size
        result.box = Box(x, y, width, height)

        if (needsSsd(up)) {
            result.mask = Box(0.0, 0.0, width, height)
        } else {
            result.cornerRadius = 0.0
            result.mask = Box(-ws.width.toDouble(), -ws.height.toDouble(), width + 2 * ws.width, height + 2 * ws.height)
        }

        result.opacity = opacity * (if (result.lockEnabled && !state.final) 1.0 else state.backgroundOpacity)
        if (!isFocused()) result.opacity *= confUnfocusedOpacity()
        result.box = Box(result.box.x + ws.posX, result.box.y + ws.posY, result.box.w, result.box.h)
        result.logicalBox = result.box

        if (selfState.swallowed != null) {
            val b = result.box
            result.box = Box(b.x + 0.5 * b.w, b.y + 0.5 * b.h, 0.0, 0.0)
            result.logicalBox = result.box
        }
        // Workspaces don't really matter for floating windows, just leave them attached to initial workspace
        result.workspace = null
        return result
    }

    private fun showFloating(ws: Workspace, state: LayoutState, wsState: WorkspaceState, posHint: Pair<Double, Double>?): Pair<LayoutState?, LayoutState?> {
        log.trace { "Show - floating: $this" }
        val initial = initialState ?: return null to null

        val referenceState = when {
            state.allInOverview() -> state.withOverviewSet(false)
            wsState.isInOverview() -> state.withOverviewSet(false, onlyWorkspace = ws)
            else -> state.copy()
        }
        val referenceWsState = referenceState.workspaceState(ws)

        val (w, h) = initial.size
        var ci = referenceWsState.i + referenceWsState.size / 2.0
        var cj = referenceWsState.j + referenceWsState.size / 2.0
        val parentView = openedAsChildOf()
        if (posHint != null) {
            ci = referenceWsState.i + posHint.first * referenceWsState.size
            cj = referenceWsState.j + posHint.second * referenceWsState.size
            log.trace { "Respecting position hint $posHint -> $ci $cj" }
        } else if (parentView != null) {
            val pState = referenceState.findViewOrNull(parentView)?.first
            if (pState == null) {
                log.warn("Unexpected: Could not access parent $parentView state")
            } else if (pState.isTiled) {
                ci = pState.i + pState.w / 2.0
                cj = pState.j + pState.h / 2.0
            } else {
                ci = pState.floatPos.first + pState.floatSize.first * referenceWsState.size / ws.width / 2.0
                cj = pState.floatPos.second + pState.floatSize.second * referenceWsState.size / ws.height / 2.0
            }
        }

        val wt = w.toDouble() / ws.width * referenceWsState.size
        val ht = h.toDouble() / ws.height * referenceWsState.size
        val i = ci - wt / 2.0
        val j = cj - ht / 2.0

        val wsState1 = wsState.withViewState(this, ViewState(isTiled = false, floatPos = ci to cj, floatSize = 0 to 0, stackIdx = handle))
        val wsState2 = referenceWsState.withViewState(this, ViewState(isTiled = false, floatPos = i to j, floatSize = w to h, stackIdx = handle))
        val state1 = state.settingWorkspaceState(ws, wsState1)
        val state2 = referenceState.settingWorkspaceState(ws, wsState2)

        focus()
        wm.focusBorders.updateFocus(this, state1 to state2)
        return state1 to state2
    }

    // --- Tiled

    /**
     * Make a best-guess assumption w=h=1 and workspace size unchanged to ask the view to open
     * with correct size. The view may still turn out floating when it maps.
     */
    private fun initTiled(up: Up, ws: Workspace): ViewDown {
        val wsState = forFirstWindow(wm.state.workspaceState(ws))
        // On screens: the place it will get, asked for at once (a half, a quarter of the screen).
        if (Screens.on()) {
            val placed = runCatching { wm.placeOnScreen(this, ws, wsState.copy()) }.getOrNull()
            if (placed != null) {
                val result = reducerTiled(up, wm.state, ViewState(i = placed.i, j = placed.j, w = placed.w, h = placed.h), ws, wsState.focusingScreen(placed.page), ignoreMinSize = true)
                result.box = Box.ZERO
                result.floating = false
                return result
            }
        }
        val minW = up.sizeConstraints[0]
        val minH = up.sizeConstraints[2]
        var w = 1.0
        var h = 1.0
        var result = ViewDown()
        repeat(3) {
            result = reducerTiled(up, wm.state, ViewState(w = w, h = h), ws, wsState, ignoreMinSize = true)
            if (result.size.first < minW) w += 1
            if (result.size.second < minH) h += 1
        }
        result.box = Box.ZERO
        // Tiled from the first configure (pywm left it open): programs that size themselves by
        // cells (foot, kitty) then take the tile's size exactly instead of the nearest cell.
        result.floating = false
        return result
    }

    internal fun reducerTiled(up: Up, state: LayoutState, selfState: ViewState, ws: Workspace, wsState: WorkspaceState, ignoreMinSize: Boolean = false): ViewDown {
        val result = ViewDown()
        result.floating = false
        result.acceptsInput = true
        result.cornerRadius = confCornerRadius()
        if (wsState.isFullscreen() && confFullscreenPadding() == 0.0) result.cornerRadius = 0.0

        // z_index based on hierarchy
        var depth = 0
        var p = parent
        while (p != null) {
            depth += 1
            p = p.parent
        }
        result.zIndex = depth.toDouble()
        // Keep focused view on top
        if (isFocused()) result.zIndex += 1

        // Handle box
        var i = selfState.i
        var j = selfState.j
        var w = selfState.w
        var h = selfState.h
        val usableHeight = wsState.usableHeight()
        val usableWidth = wsState.usableWidth()

        var stackLen = 1
        if (!wsState.isFullscreen()) {
            val stackIdx = selfState.stackData.index
            stackLen = selfState.stackData.length
            if (stackLen > 1) {
                i += 0.05 * stackIdx / (stackLen - 1)
                j += 0.05 * usableWidth / usableHeight * stackIdx / (stackLen - 1)
                w -= 0.05
                h -= 0.05 * usableWidth / usableHeight
            }
        }

        var x = i - wsState.i
        var y = j - wsState.j
        x *= usableWidth / wsState.size
        y *= usableHeight / wsState.size
        w *= usableWidth / wsState.size
        h *= usableHeight / wsState.size
        x += wsState.leftExcluded
        y += wsState.topExcluded

        result.cornerRadius /= max(1.0, wsState.size / 2.0)
        result.isFullscreen = wsState.isFullscreen()

        val padding = if (result.isFullscreen) confFullscreenPadding() else confPadding()
        val paddingScaled = padding / max(1.0, wsState.size / 2.0)
        var paddingForSize = paddingScaled
        wsState.sizeOrigin?.let { paddingForSize = padding / max(1.0, it / 2.0) }

        if (w != 0.0 && h != 0.0) {
            x += paddingScaled
            y += paddingScaled
            w -= 2 * paddingScaled
            h -= 2 * paddingScaled
        }

        // Handle client size
        var (wForSize, hForSize) = selfState.scaleOrigin ?: (selfState.w to selfState.h)
        val size = wsState.sizeOrigin ?: wsState.size
        if (stackLen > 1) {
            wForSize -= 0.05
            hForSize -= 0.05 * usableWidth / usableHeight
        }
        wForSize *= usableWidth / size
        hForSize *= usableHeight / size
        wForSize -= 2 * paddingForSize
        hForSize -= 2 * paddingForSize

        var width = pyRound(wForSize)
        var height = pyRound(hForSize)
        result.size = width to height

        // Override: Keep aspect-ratio of windows
        if (!ignoreMinSize) {
            val (minW, maxW, minH, maxH) = up.sizeConstraints.toList()
            if (width < minW && minW > 0) width = minW
            if (width > maxW && maxW > 0) width = maxW
            if (height < minH && minH > 0) height = minH
            if (height > maxH && maxH > 0) height = maxH // (newm set width = minW here: its bug)

            val oldAr = if (result.size.first > 0) result.size.second.toDouble() / result.size.first else 1.0
            val newAr = if (width > 0) height.toDouble() / width else 1.0
            if (abs(oldAr - newAr) > 0.001) {
                if (newAr < oldAr) h *= newAr / oldAr // new width is larger - would appear scaled up vertically
                else w *= oldAr / newAr // new width is smaller - would appear scaled up horizontally
            }
        }

        result.logicalBox = Box(x + ws.posX, y + ws.posY, w, h)

        // (pywm placed the whole surface, client-side shadows too: newm moved it by their size
        // and masked them off. Caulis places the window's visible part itself and leaves the
        // shadows out — moving it again cut off the top of programs with shadows: Firefox's
        // tabs, GTK programs' header bars.)
        result.mask = Box(0.0, 0.0, w, h)
        result.size = width to height
        result.box = Box(x, y, w, h)

        result.opacity = opacity * (if (result.lockEnabled && !state.final) 1.0 else state.backgroundOpacity)
        if (!isFocused()) result.opacity *= confUnfocusedOpacity()
        // Opening: see-through until it has appeared; closing (no size any more) too (newm issue 183).
        if (confFade()) result.opacity *= if (selfState.w == 0.0 || selfState.h == 0.0) 0.0 else selfState.appear
        result.box = Box(result.box.x + ws.posX, result.box.y + ws.posY, result.box.w, result.box.h)

        if (selfState.swallowed != null) {
            val b = result.box
            result.mask = Box.ZERO
            result.box = Box(b.x + 0.5 * b.w, b.y + 0.5 * b.h, 0.0, 0.0)
            result.logicalBox = result.box
        }

        result.workspace = if (selfState.moveOrigin != null && selfState.scaleOrigin == null) {
            null // No fixed output during a move
        } else if (!confWindowsUnderBar()) {
            // Cut at the bars' edge: under a bar only the bar shows (the window above or below
            // the one in view showed through it, newm's wall going on under the bars).
            Box(ws.posX + wsState.leftExcluded, ws.posY + wsState.topExcluded, usableWidth, usableHeight)
        } else {
            Box(ws.posX.toDouble(), ws.posY.toDouble(), ws.width.toDouble(), ws.height.toDouble())
        }
        return result
    }

    internal fun showTiled(ws: Workspace, state: LayoutState, wsState: WorkspaceState): Pair<LayoutState?, LayoutState?> {
        log.trace { "Show - tiled: $this" }
        val up = upState
        var minW = (up?.sizeConstraints?.get(0) ?: 0).toDouble()
        var minH = (up?.sizeConstraints?.get(2) ?: 0).toDouble()
        val size = wsState.sizeOrigin ?: wsState.size
        minW *= size / wsState.usableWidth()
        minH *= size / wsState.usableHeight()

        val referenceState = when {
            state.allInOverview() -> state.withOverviewSet(false)
            wsState.isInOverview() -> state.withOverviewSet(false, onlyWorkspace = ws)
            else -> state.copy()
        }
        val referenceWsState = forFirstWindow(referenceState.workspaceState(ws))

        // On screens: its place on a screen (the others there make room, gliding).
        if (Screens.on()) return showOnScreen(ws, state, wsState, referenceState, referenceWsState)

        // One tile, or more if the program needs it.
        val w = max(ceil(minW), 1)
        val h = max(ceil(minH), 1)

        // (Room is made in the reference state: what moves aside glides there.)
        val (i1, j1) = wm.placeNew(this, ws, referenceWsState, w, h)

        // It opens in its place, a little smaller and see-through, and grows to its size as it
        // appears (newm grew it out of a point in its middle while the view moved: it seemed to
        // fly in at a slant).
        val start = if (confFade()) 0.85 else 0.0
        val wsState1 = wsState.withViewState(
            this,
            ViewState(
                isTiled = true, i = i1 + (1 - start) / 2 * w, j = j1 + (1 - start) / 2 * h, w = start * w, h = start * h,
                scaleOrigin = w.toDouble() to h.toDouble(), moveOrigin = MoveOrigin(i1.toDouble(), j1.toDouble(), ws), stackIdx = handle,
                appear = 0.0,
            ),
        )
        val wsState2 = referenceWsState.withViewState(
            this,
            ViewState(isTiled = true, i = i1.toDouble(), j = j1.toDouble(), w = w.toDouble(), h = h.toDouble(), stackIdx = handle),
        ).focusingView(this)
        val state1 = state.settingWorkspaceState(ws, wsState1)
        val state2 = referenceState.settingWorkspaceState(ws, wsState2)

        focus()
        wm.focusBorders.updateFocus(this, state1 to state2)
        return state1 to state2
    }

    private fun showOnScreen(ws: Workspace, state: LayoutState, wsState: WorkspaceState, referenceState: LayoutState, referenceWsState: WorkspaceState): Pair<LayoutState?, LayoutState?> {
        val placed = wm.placeOnScreen(this, ws, referenceWsState)
        val (i1, j1, w, h) = listOf(placed.i, placed.j, placed.w, placed.h)
        val start = if (confFade()) 0.85 else 0.0
        val wsState1 = wsState.withViewState(
            this,
            placed.copy(
                i = i1 + (1 - start) / 2 * w, j = j1 + (1 - start) / 2 * h, w = start * w, h = start * h,
                scaleOrigin = w to h, moveOrigin = MoveOrigin(i1, j1, ws), stackIdx = handle, appear = 0.0,
            ),
        )
        val wsState2 = referenceWsState.focusingView(this)
        val state1 = state.settingWorkspaceState(ws, wsState1)
        val state2 = referenceState.settingWorkspaceState(ws, wsState2)
        log.info { "$appId on screen ${placed.page + 1}, place ${placed.slot + 1}" }
        focus()
        wm.focusBorders.updateFocus(this, state1 to state2)
        return state1 to state2
    }

    // The workspace as the first window sees it: zoomed in on one tile, if it is empty (on screens: always, the first screen).
    private fun forFirstWindow(wsState: WorkspaceState): WorkspaceState {
        if (!(confFillFirst() || Screens.on()) || wsState.viewStates.values.any { it.isTiled } || wsState.isInOverview() || wsState.isFullscreen()) return wsState
        return wsState.copy(i = 0.0, j = 0.0, size = 1.0, sizeOrigin = null)
    }

    // --- Init and map

    companion object {
        /**
         * The special workspace view { rules } send [window] into (rule(specialWorkspace = "music")),
         * asked before it appears (a view of its own for the rules to look at, not put anywhere).
         */
        /**
         * What view { rules } say for [window] (opacity, blur, focus, ...), asked for a window
         * without a view of newm's (one in a special workspace); empty if none or failing.
         */
        internal fun rulesOf(wm: Layout, window: Window): Map<String, Any?> = try {
            confRulesCallback()(View(wm, window, -1)) ?: emptyMap()
        } catch (e: Exception) {
            log.error("In rules callback (special workspace window)", e)
            emptyMap()
        }

        internal fun specialWorkspaceRuleOf(wm: Layout, window: Window): String? = try {
            (confRulesCallback()(View(wm, window, -1))?.get("special_workspace") as? String)?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            log.error("In rules callback (special workspace)", e)
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readRules() {
        try {
            val r = confRulesCallback()(this)
            if (r != null) {
                rules = r
                log.trace { "View $this rules: $rules" }
            } else {
                log.trace { "No rules for view $this" }
            }
            (rules["opacity"] as? Number)?.let { opacity = it.toDouble() }
        } catch (e: Exception) {
            log.error("In rules callback", e)
        }
    }

    private fun workspaceFor(): Workspace {
        var ws = wm.activeWorkspace()
        val output = upState?.fixedOutput
        if (output != null) {
            val wss = wm.workspaces.filter { output in it.outputs }
            if (wss.size != 1) log.warn("Unexpected: Could not find output $output in workspaces") else ws = wss[0]
        }
        return ws
    }

    fun init(): ViewDown {
        if (initialState == null) {
            log.info { "Init: $this" }
            readRules()
        }
        val up = upState ?: return ViewDown()
        val ws = workspaceFor()

        if (pid > 0) panel = wm.panelLauncher.panelForPid(pid)

        val state = if (panel != null && role != "layer") {
            initialKind = 0
            initPanel()
        } else {
            val (floats, sizeHint, _) = decideFloating()
            if (floats) {
                initialKind = 2
                initFloating(up, sizeHint)
            } else {
                initialKind = 3
                initTiled(up, ws)
            }
        }
        initialState = state
        damage()
        // Until show animation starts
        holdDamage()
        return state
    }

    fun show(state: LayoutState): Pair<LayoutState?, LayoutState?> {
        if (mapped) {
            log.warn("Unexpected - duplicate show")
            return null to null
        }
        if (destroyed) {
            log.trace { "Preventing show of destroyed view" }
            return null to null
        }
        log.info { "Show: $this" }
        releaseDamage()
        readRules()

        val ws = workspaceFor()
        val wsState = state.workspaceState(ws)
        val result = when (initialKind) {
            0 -> showPanel(ws, state, wsState)
            1 -> null to null // (layers: placed by Caulis for now)
            else -> {
                val (_, _, posHint) = decideFloating()
                if (initialKind == 2) showFloating(ws, state, wsState, posHint) else showTiled(ws, state, wsState)
            }
        }
        if (result != (null to null)) {
            mapped = true
            if (isFocused() || initialKind >= 2) wm.lastShownFocused = this
            // (A window kept over a restart goes back to its place.)
            val shownView = this
            wm.caulis.post { wm.session.shown(shownView) }
            // (misc { swallow }: a window started from a terminal takes its place.)
            wm.caulis.post { Swallowing.shown(wm, shownView) }
        }
        return result
    }

    /** Each frame: how the view is shown now (the animation's state, or the layout's). */
    fun process(up: Up): ViewDown {
        upState = up
        if (mapped) return process(reducer(up, wm.state))

        damage()
        val kind = initialKind
        init()
        val initial = initialState ?: return ViewDown()

        if (kind != initialKind) {
            log.trace { "View $this changed kind: $kind -> $initialKind" }
            return initial
        }

        if (up.size != initial.size && initial.size.first > 0 && initial.size.second > 0 && up.size.first > 0 && up.size.second > 0) {
            if (now() - initialTime < RESIZE_PATIENCE) {
                forceSize()
                return initial
            } else if (!waitingForShow) {
                log.info { "Size negotiation failed - Allowing view custom size ${up.size} (instead of ${initial.size})" }
            }
        }

        if (up.isMapped && !waitingForShow) {
            wm.animateTo({ s -> show(s) }, "windowsIn", null, initialKind <= 1) // overlay_safe for panel and layer
            waitingForShow = true
        }
        return initial
    }

    // --- Animation logic

    fun reducer(up: Up, state: LayoutState): ViewDown {
        val found = state.findViewOrNull(this)
        if (found == null) {
            // Valid: an animation is queued, after which the show animation of this view is queued.
            initialState?.let { return it }
            log.warn("Missing state: $this")
            return ViewDown(size = up.size)
        }
        val (selfState, wsState, wsHandle) = found
        val ws = wm.workspaces.first { it.handle == wsHandle }
        return when {
            panel != null && role != "layer" -> reducerPanel(state, ws)
            selfState.isTiled -> reducerTiled(up, state, selfState, ws, wsState)
            selfState.isLayer -> ViewDown() // (layers: Caulis's own for now)
            else -> reducerFloating(up, state, selfState, ws, wsState)
        }
    }

    override fun animate(oldState: LayoutState, newState: LayoutState, dt: Double, curve: dev.caulis.core.Curve) {
        val up = upState ?: return
        val cur = reducer(up, oldState)
        val nxt = reducer(up, newState)
        startAnimation(ViewDownInterpolation(wm, cur, nxt), dt, curve)
    }

    override fun flushAnimation() = flushAnimationOwn()

    override fun animDamage() = damage()

    // --- Public API

    // (Asked of Caulis now: the upstream state is a frame old when the focus has just changed,
    // and the border stayed on the window before.)
    fun isFocused(): Boolean = window === runCatching { wm.caulis.desktop.focusedWindow }.getOrNull()

    fun focus() = wm.focusWindow(window)

    fun close() = window.close()

    fun damage() = wm.damage()

    // Asks the program again for the size it was asked first.
    // (Asked again now and then, not every frame: each configure is one more for the program to answer.)
    private var lastForced = 0.0

    private fun forceSize() {
        val size = initialState?.size ?: return
        if (now() - lastForced < .1) return
        lastForced = now()
        if (size.first > 0 && size.second > 0) window.configure(size.first, size.second)
    }

    fun setFullscreen(fullscreen: Boolean) {
        if (window.fullscreen == fullscreen) return
        window.fullscreen = fullscreen
        window.configure()
    }

    // Frames drawn without pause while this view waits (for its show animation, for its
    // closing one): held once, let go once (newm held them for good, so after a few windows
    // every frame was drawn anew, even with nothing moving).
    private var holdsDamage = false

    /** Whether this view holds frames on now (for the log when they stay held). */
    val holdsFrames: Boolean get() = holdsDamage

    private fun holdDamage() {
        if (holdsDamage) return
        holdsDamage = true
        wm.enterConstantDamage()
    }

    /** The view does not wait any more: frames only when something moves. */
    fun releaseDamage() {
        if (!holdsDamage) return
        holdsDamage = false
        wm.exitConstantDamage()
    }

    fun destroy() {
        // Until exit animation
        holdDamage()
        destroyed = true
        wm.destroyView(this)
    }

    fun toggleFloating(state: ViewState, ws: Workspace, wsState: WorkspaceState): Pair<ViewState, ViewState> {
        val padding = if (!wsState.isFullscreen()) confPadding() else 0.0
        if (state.isTiled) {
            val floatSize = upState?.size ?: (100 to 100)
            val floatPos = state.i + 0.1 to state.j - 0.1
            return state to state.copy(isTiled = false, floatSize = floatSize, floatPos = floatPos)
        }
        val w = max(1, pyRound((state.floatSize.first + 2 * padding) / wsState.usableWidth() * wsState.size))
        val h = max(1, pyRound((state.floatSize.second + 2 * padding) / wsState.usableHeight() * wsState.size))
        val i = pyRound(state.floatPos.first)
        val j = pyRound(state.floatPos.second)
        return state to state.copy(isTiled = true, i = i.toDouble(), j = j.toDouble(), w = w.toDouble(), h = h.toDouble())
    }

    fun transformToClosestWs(ws: Workspace, i0: Double, j0: Double, w0: Double, h0: Double): Pair<Workspace, DoubleArray> {
        val up = upState
        if (panel != null || up == null) return ws to doubleArrayOf(i0, j0, w0, h0)

        val wsState = wm.state.workspaceState(ws)
        val border = confBorderWsSwitch()
        if (isFloat(wm.state)) {
            val down = reducerFloating(up, wm.state, ViewState(isTiled = false, floatPos = i0 to j0, floatSize = w0.toInt() to h0.toInt()), ws, wsState)
            val b = down.box
            val cx = b.x + .5 * b.w
            val cy = b.y + .5 * b.h
            if (ws.posX - border <= cx && cx <= ws.posX + ws.width + border && ws.posY - border <= cy && cy <= ws.posY + ws.height + border) {
                return ws to doubleArrayOf(i0, j0, w0, h0)
            }
            for (wsp in wm.workspaces) {
                if (wsp.posX < cx && cx < wsp.posX + wsp.width && wsp.posY < cy && cy < wsp.posY + wsp.height) {
                    val statep = wm.state.copy()
                    statep.moveViewState(this, ws, wsp)
                    statep.updateViewState(this) { floatPos = 0.0 to 0.0 }
                    val (viewState, wspState, _) = statep.findView(this)
                    val t = reducerFloating(up, statep, viewState, wsp, wspState).box
                    val ip = (cx - (t.x + .5 * t.w)) * wspState.size / wsp.width
                    val jp = (cy - (t.y + .5 * t.h)) * wspState.size / wsp.height
                    return wsp to doubleArrayOf(ip, jp, w0, h0)
                }
            }
        } else {
            val down = reducerTiled(up, wm.state, ViewState(i = i0, j = j0, w = w0, h = h0), ws, wsState)
            val b = down.box
            val cx = b.x + .5 * b.w
            val cy = b.y + .5 * b.h
            if (ws.posX - border <= cx && cx <= ws.posX + ws.width + border && ws.posY - border <= cy && cy <= ws.posY + ws.height + border) {
                return ws to doubleArrayOf(i0, j0, w0, h0)
            }
            for (wsp in wm.workspaces) {
                if (wsp.posX < cx && cx < wsp.posX + wsp.width && wsp.posY < cy && cy < wsp.posY + wsp.height) {
                    // It keeps its size in tiles (on screens its share of a screen): a tile is
                    // a screen's worth on each monitor, so what it takes of one stays the same.
                    val (wp, hp) = w0 to h0
                    var ip = 0.0
                    var jp = 0.0
                    val statep = wm.state.copy()
                    statep.moveViewState(this, ws, wsp)
                    statep.updateViewState(this) { this.i = ip; this.j = jp; this.w = wp; this.h = hp }
                    repeat(3) {
                        statep.updateViewState(this) { this.i = ip; this.j = jp }
                        val (viewState, wspState, _) = statep.findView(this)
                        val t = reducerTiled(up, statep, viewState, wsp, wspState).box
                        ip += (cx - (t.x + .5 * t.w)) * wspState.size / wsp.width
                        jp += (cy - (t.y + .5 * t.h)) * wspState.size / wsp.height
                    }
                    return wsp to doubleArrayOf(ip, jp, wp, hp)
                }
            }
        }
        log.trace { "View outside of workspaces - defaulting" }
        return ws to doubleArrayOf(i0, j0, w0, h0)
    }

    fun update() = readRules()

    /**
     * The view that swallows this one (newm's find_swallower): one whose program was started
     * by this view's (a player started from a terminal) — the topmost of them if several are.
     */
    fun findSwallower(): View? {
        fun parentOf(pid: Long): Long? = java.lang.ProcessHandle.of(pid).flatMap { it.parent() }.map { it.pid() }.orElse(null)
        // The process and its ancestors (as newm walks them: down to 1).
        fun lineage(pid: Long): Sequence<Long> = generateSequence(pid) { p -> parentOf(p) }.takeWhile { it > 1 }
        if (pid <= 0) return null
        val res = wm.views().filter { v -> v !== this && v.pid > 0 && pid.toLong() in lineage(v.pid.toLong()) }
        return when (res.size) {
            0 -> null
            1 -> res[0]
            else -> {
                val pids = res.map { it.pid.toLong() }.toSet()
                // (Not one started by another of them.)
                res.firstOrNull { v -> lineage(v.pid.toLong()).drop(1).none { it in pids } } ?: null.also { log.trace { "Unexpected" } }
            }
        }
    }

    // --- Callbacks

    /** The program asked for fullscreen (true) or to leave it. */
    fun onFullscreenRequest(fullscreen: Boolean) {
        log.trace { "Client requested ${if (fullscreen) "" else "no "}fullscreen - following" }
        if (isFocused() && confAcceptFullscreenFromViews()) wm.toggleFullscreen(fullscreen)
        setFullscreen(fullscreen)
    }

    fun onResized(width: Int, height: Int, clientLeading: Boolean) {
        if (clientLeading && upState?.isFloating == true) {
            val selfState = wm.state.findViewOrNull(this)?.first
            if (selfState != null && selfState.scaleOrigin == null) {
                wm.state.updateViewState(this) { floatSize = width to height }
                damage()
            }
        }
        wm.focusBorders.damage()
    }

    fun onFocusChange() {
        if (isFocused()) {
            wm.focusHint(this)
            wm.focusBorders.updateFocus(this)
        }
    }
}
