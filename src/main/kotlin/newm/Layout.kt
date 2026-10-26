package newm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.caulis.compose.compose
import newm.bar.showPopups
import dev.caulis.core.Monitor
import dev.caulis.core.OverlayCanvas
import dev.caulis.core.Window
import dev.caulis.core.WindowDirector
import dev.caulis.core.WindowPlace
import dev.caulis.core.WindowRequest
import dev.caulis.geometry.Rect
import dev.caulis.log.Logger
import dev.caulis.wm.Caulis
import dev.caulis.wm.KeyInput
import kotlin.math.max

private val log = Logger("layout")

// newm's own wallpaper unless another is named; "" for none (a wallpaper program instead, swaybg).
private val confScreensOffAfter = configuredValue("energy.screens_off_after", 0)
private val confLockProgram = configuredValue<String?>("panels.lock.program", null)
private val confBackgroundPath = configuredValue<String?>("background.path", "resource:wallpaper.jpg")
private val confSendFullscreenToViews = configuredValue("view.send_fullscreen", true)
private val confKeyBindings = configuredValue<(Layout) -> List<Pair<String, () -> Unit>>>("key_bindings") { emptyList() }
private val confAnimT = configuredValue("anim_time", .3)
private val confBlendT = configuredValue("blend_time", 1.0)
private val confIdleTimes = configuredValue("energy.idle_times", listOf(120, 300, 600))
private val confSuspendCommand = configuredValue("energy.suspend_command", "systemctl suspend")

/**
 * code "lock": on lock (dim the screen now); "idle": idle_times[0] passed; "idle-lock":
 * idle_times[1] passed, locked too; "idle-presuspend": 5 s before idle_times[2]; "idle-suspend":
 * idle_times[2] passed, suspended too; "active": activity after idle; "sleep", "wakeup".
 */
private val confIdleCallback = configuredValue<(String) -> Unit>("energy.idle_callback") { }
private val confOnStartup = configuredValue<() -> Unit>("on_startup") { }
private val confOnReconfigure = configuredValue<() -> Unit>("on_reconfigure") { }
private val confLockOnWakeup = configuredValue("lock_on_wakeup", true)
private val confSynchronousUpdate = configuredValue<() -> Unit>("synchronous_update") { }
private val confEnableCGestures = configuredValue("gestures.c.enabled", true)
private val confEnableDBusGestures = configuredValue("gestures.dbus.enabled", true)
private val confEnableUnlockCommand = configuredValue("enable_unlock_command", true)
private val confGestureBindingSwipeToZoom = configuredValue<List<String?>>("gesture_bindings.swipe_to_zoom", listOf(null, "swipe-4"))
private val confGestureBindingSwipe = configuredValue<List<String?>>("gesture_bindings.swipe", listOf(null, "swipe-3"))
private val confGestureBindingMoveResize = configuredValue<List<String?>>("gesture_bindings.move_resize", listOf("L", "move-1", "swipe-2"))
private val confGestureBindingLauncher = configuredValue<List<String?>>("gesture_bindings.launcher", listOf(null, "swipe-5"))

/** A layout reducer: the state now → (the animation's first state or null, its last state or null). */
typealias Reducer = (LayoutState) -> Pair<LayoutState?, LayoutState?>

// newm's _score: how far the box (i2, j2, w2, h2) lies in the direction (im, jm) from the first one.
internal fun score(
    i1In: Double, j1In: Double, w1In: Double, h1In: Double, imIn: Int, jmIn: Int,
    i2In: Double, j2In: Double, w2In: Double, h2In: Double,
): Double {
    var i1 = i1In; var j1 = j1In; var w1 = w1In; var h1 = h1In
    var i2 = i2In; var j2 = j2In; var w2 = w2In; var h2 = h2In
    var im = imIn; var jm = jmIn
    if (i1 == i2 && j1 == j2 && w1 == w2 && h1 == h2) return 1000.0
    if (im < 0) {
        im *= -1; i1 *= -1; i2 *= -1; i1 -= w1; i2 -= w2
    }
    if (jm < 0) {
        jm *= -1; j1 *= -1; j2 *= -1; j1 -= h1; j2 -= h2
    }
    if (jm == 1 && im == 0) {
        im = 1; jm = 0
        run { val t = i1; i1 = j1; j1 = t }
        run { val t = w1; w1 = h1; h1 = t }
        run { val t = i2; i2 = j2; j2 = t }
        run { val t = w2; w2 = h2; h2 = t }
    }
    // Now either im == 1, jm == 0 or im == jm == 1.
    val di = i2 - (i1 + w1)
    if (di < 0) return 1000.0
    if (jm == 1) {
        val dj = j2 - (j1 + h1)
        if (dj < 0) return 1000.0
        return di + dj
    }
    val dj = when {
        j2 >= j1 + h1 -> j2 - (j1 + h1)
        j1 >= j2 + h2 -> j1 - (j2 + h2)
        else -> -1.0
    }
    return di + dj
}

/** newm's Animation: a reducer run when its turn comes, then animated to its final state. */
class Animation(
    private val layout: Layout,
    private val reducer: Reducer,
    duration: Double,
    private val then: (() -> Unit)?,
    val overlaySafe: Boolean = false,
) {
    private var initialState: LayoutState? = null
    private var finalState: LayoutState? = null
    private var started = false
    private var finish: Double? = null

    // (Prevent division by zero.)
    private val duration = max(0.1, duration)

    fun checkFinished(): Boolean {
        if (started && finalState == null) {
            then?.invoke()
            layout.doFlushAnimation()
            return true
        }
        if (started && finish == null) finish = layout.finalTimeAll()
        val f = finish
        if (f != null && now() > f) {
            finalState?.let(layout::update)
            then?.invoke()
            layout.doFlushAnimation()
            return true
        }
        return false
    }

    fun start() {
        try {
            reducer(layout.state).let { (i, f) ->
                initialState = i
                finalState = f
            }
        } catch (e: Exception) {
            log.error("During animation reducer", e)
            initialState = null
            finalState = null
        }
        initialState?.let(layout::update)
        started = true
        val f = finalState
        if (f != null) {
            // Enforce constraints on the final state.
            f.constrainAndValidate()
            if (f == layout.state) {
                log.trace { "Skipping moot animation" }
                finalState = null
            } else {
                layout.animateAll(f, duration)
            }
        } else {
            log.trace { "Animation decided not to take place anymore" }
        }
    }

    override fun toString() = "$initialState -> $finalState ($duration${if (then != null) ", then" else ""})"
}

/**
 * newm's LayoutThread: overlays and animations one after the other (an overlay-safe animation
 * also while an overlay is open), looked at 30 times a second. newm ran it on a thread of its
 * own; here it runs on the compositor's thread, so the state is never touched by two at once.
 */
class LayoutQueue(private val layout: Layout) {
    private val pending = ArrayList<Any>() // Overlay or Animation
    private var currentOverlay: Overlay? = null
    private var currentAnimation: Animation? = null
    private var ticker: AutoCloseable? = null

    fun start() {
        ticker = layout.every(1000L / 30) { tick() }
    }

    fun stop() {
        ticker?.close()
        ticker = null
    }

    fun push(next: Any) {
        if (next is Overlay) {
            if (currentOverlay != null || pending.any { it is Overlay }) {
                log.trace { "Rejecting queued overlay" }
                return
            }
            log.trace { "Queuing overlay" }
            pending += next
        } else if (next is Animation) {
            if (next.overlaySafe) {
                log.trace { "Overlay-safe animation not queued" }
                pending.add(0, next)
            } else {
                log.trace { "Queuing animation" }
                pending += next
            }
        }
    }

    fun onOverlayDestroyed() {
        log.trace { "Finishing overlay..." }
        currentOverlay = null
        layout.exitConstantDamage()
    }

    private fun tick() {
        try {
            val first = pending.firstOrNull()
            if (first is Overlay) {
                if (currentAnimation == null && currentOverlay == null) {
                    log.trace { "Starting overlay..." }
                    pending.removeAt(0)
                    currentOverlay = first
                    layout.startOverlay(first)
                    layout.enterConstantDamage()
                }
            } else if (first is Animation) {
                if (currentAnimation == null && (currentOverlay == null || first.overlaySafe)) {
                    log.trace { "Starting animation..." }
                    pending.removeAt(0)
                    currentAnimation = first
                    first.start()
                    layout.enterConstantDamage()
                }
            }
            currentAnimation?.let { a ->
                if (a.checkFinished()) {
                    log.trace { "Finishing animation..." }
                    currentAnimation = null
                    layout.exitConstantDamage()
                }
            }
            confSynchronousUpdate()()
        } catch (e: Exception) {
            log.error("Unexpected during the layout's queue", e)
        }
    }
}

/**
 * newm's layout.py: the window manager. Every window is a [View] on an endless grid of tiles
 * per workspace (one workspace per group of overlapping monitors); the [state] says where
 * each is and what is in view, and every change is an animation from one state to the next.
 * pywm drew it; here Caulis does, with newm as its WindowDirector.
 */
class Layout(val caulis: Caulis) : Animate<LayoutDown>(), Animatable {

    val keyProcessor = KeyProcessor()
    val dbusEndpoint = DBusEndpoint(this)

    /** newm's own lock screen and launcher. */
    val panelsUi = Panels(this)
    val authBackend = AuthBackend(this)
    val panelLauncher = PanelsLauncher(::every) { command, directory -> caulis.spawn(command, directory) }
    private var gestureProviders: List<GestureProvider> = emptyList()

    var workspaces: List<Workspace> = listOf(Workspace(null, 0, 0, 1280, 720).also { it.handle = 0 })
        private set

    var state = LayoutState(this)
        private set

    var overlay: Overlay? = null
        private set

    val focusBorders = FocusBorders(this)
    private var backgrounds: List<Background> = emptyList()
    // (Compose state: the bars' parts follow when a reload makes them anew.)
    private var topBars: List<Bar> by androidx.compose.runtime.mutableStateOf<List<Bar>>(emptyList())
    private var bottomBars: List<Bar> by androidx.compose.runtime.mutableStateOf<List<Bar>>(emptyList())

    /** What newm's bars share: the tray, the computer's state, the popups (started with the first islands bar). */
    val barModel = newm.bar.BarModel(this)
    private val corners = Corners(this)
    private var wallpaper: Pair<String?, Wallpaper?>? = null
    private val queue = LayoutQueue(this)

    private var idleInhibitUser = false

    // The workspace the cursor is on, and the one a focused view overrides it with.
    private var activeWorkspacePair: Pair<Workspace, Workspace?> = workspaces[0] to null

    // pywm's part: the views by handle, and by window.
    private val views = LinkedHashMap<Int, View>()
    private val viewsByWindow = java.util.IdentityHashMap<Window, View>()
    private var nextHandle = 1
    private var constantDamage = 0
    private var modifiers = 0
    private val requestedSize = java.util.IdentityHashMap<Window, Pair<Int, Int>>()
    /** The layout's state of this frame (the lock screen's and the launcher's way in). */
    var layoutDown = LayoutDown(0.0)
        private set

    // The monitors' room left by bar programs, as last seen.
    private var lastUsable: List<dev.caulis.geometry.Rect>? = null

    init {
        caulis.director = director()
        caulis.onKey { key -> onKeyInput(key) }
        caulis.onPointerMotion { dx, dy -> onMotion(now().toMillis(), dx, dy) }
        caulis.onPointerButton { button, pressed -> onButton(now().toMillis(), button, pressed) }
        caulis.onScroll { vertical, value, _ -> onAxis(now().toMillis(), vertical, value) }
        caulis.onGesture { gesture -> onTouchpadGesture(gesture) }
        caulis.onWindowClosed { window -> viewsByWindow[window]?.destroy() }
        caulis.onFocusChanged { _ -> for (v in views.values.toList()) v.onFocusChange() }
        caulis.onMonitorAdded { onLayoutChange() }
        caulis.onMonitorRemoved { onLayoutChange() }
        caulis.onSleep { onSleep() }
        caulis.onWake { onWakeup() }
        // Idle: Caulis says when nobody is there (programs that keep the screen on hold it off),
        // newm counts the seconds and goes through energy.idle_times.
        caulis.onIdle(1, resume = { idleSince = null; onIdle(0.0, false) }) { idleSince = now() - 1.0 }
        caulis.onStart { caulis.every(1000) { idleSince?.let { onIdle(now() - it, false) } } }
        caulis.onStart { main() }
    }

    // --- pywm's part ---------------------------------------------------------------------------

    /** Runs [action] every [millis] on the compositor's thread; close the result to stop. */
    fun every(millis: Long, action: () -> Unit): AutoCloseable = caulis.every(millis) { action() }

    /** The view of [window] (made when it is first placed). */
    fun viewOf(window: Window): View? = viewsByWindow[window]

    fun focusWindow(window: Window) {
        caulis.desktop.focus(window)
    }

    /** Draws anew. */
    fun damage() {
        desktopOrNull()?.requestRedraw()
    }

    fun enterConstantDamage() {
        constantDamage++
        damage()
    }

    fun exitConstantDamage() {
        constantDamage = max(0, constantDamage - 1)
    }

    private fun isLocked(): Boolean = state.lockPerc > 0.0

    // The desktop, once the compositor runs.
    private fun desktopOrNull(): dev.caulis.core.Desktop? = try {
        caulis.desktop
    } catch (_: IllegalStateException) {
        null
    }

    // Where each view is drawn: newm's downstream state of it, as Caulis places windows.
    private fun director() = object : WindowDirector {
        override fun place(window: Window, now: Long): WindowPlace? {
            val view = viewsByWindow.getOrPut(window) {
                View(this@Layout, window, nextHandle++).also { views[it.handle] = it }
            }
            val up = Up(window, window === caulis.desktop.focusedWindow)
            val previous = view.upState
            val down = view.process(up)
            // The program asked to draw at the size newm wants.
            val (w, h) = down.size
            if (w > 0 && h > 0 && requestedSize[window] != w to h) {
                requestedSize[window] = w to h
                down.floating?.let { window.tiled = !it }
                window.configure(w, h)
            } else if (previous != null && previous.size != up.size && up.size != requestedSize[window]) {
                view.onResized(up.size.first, up.size.second, clientLeading = true)
            }
            return placeOf(down)
        }

        override fun decorate(window: Window, place: WindowPlace, canvas: OverlayCanvas, above: Boolean) {
            if (above) return
            // What is behind it blurred (a see-through terminal), if its rule says so.
            viewsByWindow[window]?.blur?.let { (size, passes) ->
                val area = place.clip?.let { place.rect.intersect(it) } ?: place.rect
                if (area != null) canvas.blur(area, radius = place.cornerRadius ?: 0f, size = size, passes = passes)
            }
            val focused = focusBorders.currentView
            if (focused?.window === window && focused.wantsFocusBorder) focusBorders.draw(canvas)
        }

        // Asked once a frame: the layout's own state of this frame (pywm processed it every frame).
        override fun animating(now: Long): Boolean {
            layoutDown = process(reducer(state))
            // A bar program came, went or changed its height: windows make way anew.
            val usable = desktopOrNull()?.monitors?.map { it.usableArea }
            if (usable != lastUsable) {
                if (lastUsable != null) animateTo({ s -> null to s.copy() }, confAnimT())
                lastUsable = usable
            }
            return constantDamage > 0 || animating || focusBorders.animating || views.values.any { it.animating } ||
                topBars.any { it.animating } || bottomBars.any { it.animating }
        }

        override fun requested(window: Window, request: WindowRequest, on: Boolean): Boolean {
            val view = viewsByWindow[window] ?: return true
            if (request == WindowRequest.FULLSCREEN) view.onFullscreenRequest(on)
            return true // (maximize and minimize: newm has neither)
        }

        override fun moveRequested(window: Window): Boolean = true

        override fun resizeRequested(window: Window, edges: Int): Boolean = true
    }

    // A view's downstream state as Caulis places windows: its box, the part shown (the mask,
    // within its workspace), how strongly, in which order, and whether it takes input.
    private fun placeOf(down: ViewDown): WindowPlace? {
        val b = down.box
        if (b.w <= 0.0 || b.h <= 0.0) return null
        // While locked, only the lock screen's views are shown and take input.
        val lockHidden = if (down.lockEnabled) 0.0 else layoutDown.lockPerc
        val alpha = (down.opacity * (1.0 - lockHidden)).coerceIn(0.0, 1.0)
        if (alpha <= 0.0) return null
        var clip: Rect? = null
        if (down.mask != Box.NO_MASK) {
            val m = down.mask
            clip = Rect(Math.floor(b.x + m.x).toInt(), Math.floor(b.y + m.y).toInt(), Math.ceil(m.w).toInt(), Math.ceil(m.h).toInt())
        }
        down.workspace?.let { ws ->
            val wsRect = Rect(ws.x.toInt(), ws.y.toInt(), ws.w.toInt(), ws.h.toInt())
            clip = (clip ?: wsRect).intersect(wsRect) ?: return null // (outside its workspace: not shown)
        }
        if (clip?.let { it.width <= 0 || it.height <= 0 } == true) return null
        return WindowPlace(
            b.x, b.y, b.w, b.h, alpha = alpha.toFloat(), cornerRadius = down.cornerRadius.toFloat(), clip = clip,
            z = down.zIndex, takesInput = down.acceptsInput && lockHidden == 0.0,
        )
    }

    // --- setup -----------------------------------------------------------------------------------

    private fun setupWorkspaces() {
        val monitors = caulis.desktop.monitors
        val ws = monitors.map { m -> Workspace(m, m.area.x, m.area.y, m.area.width, m.area.height, preventAnim = false) }.toMutableList()
        var i = 0
        var j = ws.size - 1
        while (i < ws.size && j < ws.size && i < j) {
            if (ws[i].swallow(ws[j])) {
                ws.removeAt(j)
            } else if (j == i + 1) {
                j = ws.size - 1
                i += 1
            } else {
                j -= 1
            }
        }
        for (w in workspaces) {
            var bestScore = 0.1
            var best: Workspace? = null
            for (wp in ws) {
                if (wp.handle >= 0) continue
                val s = w.score(wp)
                if (s > bestScore) {
                    bestScore = s
                    best = wp
                }
            }
            best?.handle = w.handle
        }
        workspaces = ws.ifEmpty { workspaces }
        for (w in workspaces.filter { it.handle < 0 }) {
            var h = 0
            while (h in workspaces.map { it.handle }) h++
            w.handle = h
        }
        log.trace { "Setup of newm workspaces" }
        for (w in workspaces) log.trace { "  $w" }
        state = state.withWorkspaces(this)
        updateActiveWorkspace()
    }

    private fun updateActiveWorkspace() {
        var (check1, check2) = activeWorkspacePair
        if (check1.handle !in workspaces.map { it.handle }) check1 = workspaces[0]
        if (check2 != null && check2.handle !in workspaces.map { it.handle }) check2 = null
        activeWorkspacePair = check1 to check2

        val d = desktopOrNull() ?: return
        val ws = workspaces.firstOrNull { w ->
            w.posX <= d.pointerX && d.pointerX < w.posX + w.width && w.posY <= d.pointerY && d.pointerY < w.posY + w.height
        }
        if (ws == null) {
            log.warn("Workspaces do not cover whole area")
        } else if (activeWorkspacePair.first !== ws) {
            activeWorkspacePair = ws to null
        }
    }

    private fun setupWidgets() {
        val monitors = desktopOrNull()?.monitors ?: return
        fun workspaceFor(m: Monitor): Workspace =
            workspaces.firstOrNull { w -> w.posX <= m.area.x && m.area.x < w.posX + w.width && w.posY <= m.area.y && m.area.y < w.posY + w.height }
                ?: workspaces[0].also { log.warn("Workspaces do not cover whole area") }

        barModel.close()
        topBars = if (confNativeTopBarEnabled()) monitors.map { Bar(this, it, top = true).also(Bar::refresh).also(Bar::look) } else emptyList()
        bottomBars = if (confNativeBottomBarEnabled()) monitors.map { Bar(this, it, top = false).also(Bar::refresh).also(Bar::look) } else emptyList()
        // The tray and the rest only with newm's islands bar (else another bar's tray stays its own).
        if ((topBars + bottomBars).any { it.islands }) barModel.start()

        // The wallpaper (newm's background.path; an output may name its own: one picture for all
        // here), read on a thread of its own: shown when it is ready.
        val path = confBackgroundPath()?.takeIf { it.isNotBlank() }
        if (wallpaper?.first != path) {
            wallpaper?.second?.texture?.close()
            wallpaper = path to null
            if (path != null) {
                Thread({
                    val image = Wallpaper.read(path)
                    caulis.post {
                        if (image != null && wallpaper?.first == path) {
                            wallpaper = path to Wallpaper.of(caulis.desktop.renderer, image)
                            setupWidgets()
                        }
                    }
                }, "newm-wallpaper").apply { isDaemon = true }.start()
            }
        }
        backgrounds = wallpaper?.second?.let { picture -> monitors.map { Background(this, it, workspaceFor(it), picture) } } ?: emptyList()

        focusBorders.update()
        damage()
    }

    private fun setup(reconfigure: Boolean = true) {
        setupWidgets()
        keyProcessor.clear()
        keyProcessor.registerBindings(*keyBindingsOf(confKeyBindings()(this)).toTypedArray())

        // Stop and set up the gestures anew.
        gestureProviders.forEach(GestureProvider::stop)
        val dbusProvider = if (confEnableDBusGestures()) DBusGestureProvider(::gestureProviderCallback) else null
        dbusEndpoint.gestureProvider = dbusProvider
        gestureProviders = listOfNotNull(if (confEnableCGestures()) CGestureProvider(::gestureProviderCallback, ::every) else null, dbusProvider)
        gestureProviders.forEach(GestureProvider::start)

        if (reconfigure) views.values.forEach(View::update)
    }

    fun reducer(state: LayoutState): LayoutDown = LayoutDown(state.lockPerc, state.launcherPerc)

    override fun animate(oldState: LayoutState, newState: LayoutState, dt: Double) {
        startAnimation(LayoutDownInterpolation(reducer(oldState), reducer(newState)), dt)
    }

    override fun flushAnimation() = flushAnimationOwn()

    override fun animDamage() = damage()

    private fun main() {
        log.info { "Layout main" }
        setupWorkspaces()
        setup(reconfigure = false)
        addWidgetDrawings({ backgrounds }, corners) { topBars + bottomBars }
        panelsUi.start()
        // The native bars (Compose), their texts asked for every second.
        caulis.compose(place = { m -> topBars.firstOrNull { it.monitor === m }?.place() }) { m ->
            topBars.firstOrNull { it.monitor === m }?.let { if (it.islands) newm.bar.IslandBar(it, barModel) else BarContent(it) }
        }
        caulis.compose(place = { m -> bottomBars.firstOrNull { it.monitor === m }?.place() }) { m ->
            bottomBars.firstOrNull { it.monitor === m }?.let { if (it.islands) newm.bar.IslandBar(it, barModel) else BarContent(it) }
        }
        barModel.showPopups()
        every(1000) { (topBars + bottomBars).forEach(Bar::refresh) }
        // The wall and the focused window, as the bars show them; a popup goes with its bar.
        every(200) {
            val bars = topBars + bottomBars
            bars.forEach(Bar::look)
            barModel.popup?.let { p -> if (p.bar !in bars || p.bar.place() == null || isLocked()) barModel.close() }
        }
        queue.start()
        panelLauncher.start()
        dbusEndpoint.start()
        try {
            confOnStartup()()
        } catch (e: Exception) {
            log.error("on_startup", e)
        }
        // Fade in.
        caulis.after(500) { animateTo({ s -> null to s.copy(backgroundOpacity = 1.0) }, confBlendT()) }

        // The greeter: locked at once, asking for the user, when the lock panel is there.
        if (authBackend.isGreeter()) {
            lateinit var waiting: AutoCloseable
            waiting = every(500) {
                if (panels().any { it.panel == "lock" }) {
                    waiting.close()
                    ensureLocked()
                    authBackend.initSession()
                }
            }
        }
    }

    private fun terminateNow() {
        dbusEndpoint.stop()
        panelLauncher.stop()
        gestureProviders.forEach(GestureProvider::stop)
        queue.stop()
        caulis.run("quit")
    }

    fun animateTo(reducer: Reducer, duration: Double, then: (() -> Unit)? = null, overlaySafe: Boolean = false) {
        queue.push(Animation(this, reducer, duration, then, overlaySafe))
    }

    fun update(newState: LayoutState) {
        state = newState
        damage()
    }

    private fun allAnimates(): List<Animatable> = listOf(this) + views.values + backgrounds + topBars + bottomBars + focusBorders

    fun doFlushAnimation() = allAnimates().forEach(Animatable::flushAnimation)

    /** Every animated part starts towards [newState]. */
    internal fun animateAll(newState: LayoutState, duration: Double) {
        for (a in allAnimates()) a.animate(state, newState, duration)
    }

    fun finalTimeAll(): Double? = (allAnimates().filterIsInstance<Animate<*>>() + listOf(this)).mapNotNull { it.finalTime() }.maxOrNull()

    internal fun trustedUnlock() {
        if (isLocked()) {
            panelsUi.unlocked()
            safelyEnterMode(null)
            animateTo({ s -> null to s.copy(lockPerc = 0.0, backgroundOpacity = 1.0) }, confAnimT())
        }
    }

    // --- utilities ---------------------------------------------------------------------------------

    override fun toString() = "<Layout>"

    fun debugStr(): String = buildString {
        append("$this\n  $state\n\n")
        for (w in workspaces) append("$w\n      ${state.workspaceState(w)}\n")
        for ((i, v) in views) {
            val found = state.findViewOrNull(v)
            append("%2d: %s on workspace %d\n      %s\n".format(i, v, found?.third ?: -1, found?.first))
        }
    }

    fun findFocusedBox(): Pair<Workspace, DoubleArray> {
        val view = findFocusedView() ?: return workspaces[0] to doubleArrayOf(0.0, 0.0, 1.0, 1.0)
        val (s, _, wsHandle) = state.findViewOrNull(view) ?: return workspaces[0] to doubleArrayOf(0.0, 0.0, 1.0, 1.0)
        val ws = workspaces.firstOrNull { it.handle == wsHandle } ?: return workspaces[0] to doubleArrayOf(0.0, 0.0, 1.0, 1.0)
        return ws to doubleArrayOf(s.i, s.j, s.w, s.h)
    }

    /**
     * Where a new view of [w] x [h] tiles goes: if the view shows more than the views take, at
     * 0, 0 (the first) or beside them; else the first free place in view, else right of the
     * focused one.
     */
    fun placeInitial(workspace: Workspace, wsState: WorkspaceState, w: Int, h: Int): Pair<Int, Int> {
        var placeI = 0
        var placeJ = 0
        val extent = wsState.extent()
        val minI = floor(extent[0])
        val minJ = floor(extent[1])
        val maxI = floor(extent[2])
        val maxJ = floor(extent[3])
        val viewMinI = floor(wsState.i)
        val viewMinJ = floor(wsState.j)
        val viewMaxI = ceil(wsState.i + wsState.size - 1)
        val viewMaxJ = ceil(wsState.j + wsState.size - 1)

        if (tiles(workspace).isEmpty()) {
            placeI = 0
            placeJ = 0
        } else if (viewMaxI - viewMinI > maxI - minI) {
            placeI = maxI + 1
            placeJ = max(minJ, viewMinJ)
        } else if (viewMaxJ - viewMinJ > maxJ - minJ) {
            placeI = max(minI, viewMinI)
            placeJ = maxJ + 1
        } else {
            var found = false
            outer@ for (j in floor(wsState.j) until ceil(wsState.j + wsState.size)) {
                for (i in floor(wsState.i) until ceil(wsState.i + wsState.size)) {
                    var free = true
                    check@ for (jp in j until j + h) for (ip in i until i + w) {
                        if (!wsState.isTileFree(ip, jp)) {
                            free = false
                            break@check
                        }
                    }
                    if (free) {
                        placeI = i
                        placeJ = j
                        found = true
                        break@outer
                    }
                }
            }
            if (!found) {
                var (ws, box) = findFocusedBox()
                if (ws.handle != workspace.handle) box = doubleArrayOf(0.0, 0.0, 1.0, 1.0)
                placeI = pyRound(box[0] + box[2])
                placeJ = pyRound(box[1])
                while (!wsState.isTileFree(placeI, placeJ)) placeI += 1
            }
        }
        log.trace { "Found initial placement at $placeI, $placeJ" }
        return placeI to placeJ
    }

    /** The monitors changed: workspaces and widgets anew. */
    fun onLayoutChange() {
        setupWorkspaces()
        setupWidgets()
    }

    /** The widths of the bars at the left and right edges of [ws] (their exclusive zones). */
    fun barWidths(ws: Workspace): Pair<Double, Double> {
        var left = 0.0
        var right = 0.0
        for (m in ws.outputs.filterNotNull()) {
            left = max(left, (m.usableArea.x - m.area.x).toDouble())
            right = max(right, (m.area.right - m.usableArea.right).toDouble())
        }
        return left to right
    }

    /**
     * The heights of the bars on [ws]: room bar programs keep at a monitor's edge (waybar,
     * ironbar, ...: their exclusive zone), and newm's own panel programs ("bar", "top_bar", "bottom_bar").
     */
    fun barHeights(ws: Workspace, state: LayoutState): Pair<Double, Double> {
        var top = 0.0
        var bottom = 0.0
        for (m in ws.outputs.filterNotNull()) {
            top = max(top, (m.usableArea.y - m.area.y).toDouble())
            bottom = max(bottom, (m.area.bottom - m.usableArea.bottom).toDouble())
        }
        for (p in panels(ws)) {
            val up = p.upState ?: continue
            if (p.panel !in listOf("bar", "top_bar", "bottom_bar")) continue
            val box = p.reducer(up, state).box
            when {
                p.panel == "top_bar" -> top = box.h
                p.panel == "bottom_bar" -> bottom = box.h
                box.y <= 1.0 -> top = box.h
                else -> bottom = box.h
            }
        }
        return top to bottom
    }

    // --- input -----------------------------------------------------------------------------------

    /** True if the modifier of a binding's letter is held ("L", "A", "C", "S"; null: none needed). */
    fun hasModifier(letter: String?): Boolean = when (letter) {
        null -> true
        "L" -> modifiers and Mod.LOGO != 0
        "A" -> modifiers and Mod.ALT != 0
        "C" -> modifiers and Mod.CTRL != 0
        "S" -> modifiers and Mod.SHIFT != 0
        else -> false
    }

    // pywm's on_modifiers and on_key, from Caulis's raw keys.
    private fun onKeyInput(key: KeyInput): Boolean {
        modifiers = key.modifiers
        if (key.isModifier && key.modifiers != key.modifiersBefore) onModifiers(key.modifiers, key.modifiersBefore)
        // (Single letters as pywm gives them: without Shift's capital.)
        val keysym = if (key.keysym.length == 1) key.keysym.lowercase() else key.keysym
        return onKey(key.timeMillis, key.evdevCode, key.pressed, keysym)
    }

    fun onKey(timeMillis: Int, keycode: Int, pressed: Boolean, keysyms: String): Boolean {
        // These are processed via onModifiers.
        if (keysyms in listOf("Super_L", "Super_R", "Alt_L", "Alt_R", "Logo_L", "Logo_R")) return false
        overlay?.takeIf { it.ready }?.let { o ->
            log.trace { "...passing to overlay $o" }
            if (o.onKey(timeMillis, keycode, pressed, keysyms)) return true
        }
        return keyProcessor.onKey(pressed, keysyms, modifiers, isLocked())
    }

    fun onModifiers(modifiers: Int, lastModifiers: Int): Boolean {
        if (Mod.pressed(modifiers, lastModifiers) != 0) {
            // A move gesture started before the modifier is allowed to start again (with it).
            log.trace { "Resetting gesture" }
            resetGesture()
        }
        if (isLocked()) return false
        overlay?.takeIf { it.ready }?.let { if (it.onModifiers(modifiers, lastModifiers)) return false }
        keyProcessor.onModifiers(modifiers, lastModifiers, isLocked())
        // Never taken: modifiers always reach programs.
        return false
    }

    fun onMotion(timeMillis: Int, dx: Double, dy: Double): Boolean {
        updateActiveWorkspace()
        if (isLocked()) return false
        for (g in gestureProviders) {
            val res = g.onMotion(timeMillis, dx, dy)
            if (res == 2) return true
            if (res == 1) break
        }
        overlay?.takeIf { it.ready }?.let { return it.onMotion(timeMillis, dx, dy) }
        return false
    }

    fun onButton(timeMillis: Int, button: Int, pressed: Boolean): Boolean {
        if (isLocked()) return false
        overlay?.takeIf { it.ready }?.let { return it.onButton(timeMillis, button, pressed) }
        return false
    }

    fun onAxis(timeMillis: Int, vertical: Boolean, delta: Double): Boolean {
        if (isLocked()) return false
        for (g in gestureProviders) {
            val res = g.onAxis(timeMillis, vertical, delta)
            if (res == 2) return true
            if (res == 1) break
        }
        overlay?.takeIf { it.ready }?.let { return it.onAxis(timeMillis, vertical, delta, 0.0) }
        return false
    }

    // --- gestures --------------------------------------------------------------------------------

    private fun onTouchpadGesture(gesture: dev.caulis.core.input.Gesture): Boolean {
        for (g in gestureProviders) {
            val res = g.onTouchpad(gesture)
            if (res == 2) return true
            if (res == 1) break
        }
        return false
    }

    fun resetGesture() = gestureProviders.forEach(GestureProvider::resetGesture)

    private fun gestureProviderCallback(gesture: NewmGesture): Boolean {
        if (isLocked()) return false
        log.trace { "Gesture $gesture..." }
        val o = overlay
        if (o != null && o.ready) {
            log.trace { "...passing to overlay $o" }
            return o.onGesture(gesture)
        }
        if (o != null) return false

        fun enter(overlay: Overlay): Boolean {
            overlay.onGesture(gesture)
            enterOverlay(overlay)
            return true
        }
        val moveResize = confGestureBindingMoveResize()
        if (hasModifier(moveResize[0]) && (gesture.kind == moveResize[1] || gesture.kind == moveResize[2])) {
            log.trace { "...MoveResize" }
            val view = findFocusedView()
            if (view != null && view.isFloat(state)) return enter(MoveResizeFloatingOverlay(this, view))
            if (view != null && view.isTiled(state)) return enter(MoveResizeOverlay(this, view))
        }
        val swipe = confGestureBindingSwipe()
        if (hasModifier(swipe[0]) && gesture.kind == swipe[1]) {
            log.trace { "...Swipe" }
            return enter(SwipeOverlay(this))
        }
        if (!state.workspaceState(activeWorkspace()).isInOverview()) {
            val zoom = confGestureBindingSwipeToZoom()
            if (hasModifier(zoom[0]) && gesture.kind == zoom[1]) {
                log.trace { "...SwipeToZoom" }
                return enter(SwipeToZoomOverlay(this))
            }
        }
        val launcher = confGestureBindingLauncher()
        if (hasModifier(launcher[0]) && gesture.kind == launcher[1]) {
            log.trace { "...Launcher" }
            return enter(LauncherOverlay(this))
        }
        return false
    }

    // --- idle, sleep -----------------------------------------------------------------------------

    // Since when nobody has been there (null: someone is), and the stage of idle_times reached.
    private var idleSince: Double? = null
    private var idleStage = 0
    private var screensOff = false

    /**
     * [elapsed] seconds without input (0: someone is back). Each stage of energy.idle_times
     * happens once until someone is back: newm called them again and again, locking a locked
     * screen over and over (newm issue 98).
     */
    fun onIdle(elapsed: Double, idleInhibited: Boolean) {
        val inhibited = idleInhibited || idleInhibitUser
        if (elapsed == 0.0 && screensOff) {
            screensOff = false
            caulis.run("dpms on")
        }
        val offAfter = confScreensOffAfter()
        if (elapsed > 0 && offAfter > 0 && elapsed > offAfter && !screensOff && !inhibited) {
            log.info { "Idle for ${elapsed.toInt()} s: screens off" }
            screensOff = true
            caulis.run("dpms off")
        }
        if (elapsed == 0.0) {
            if (idleStage > 0) confIdleCallback()("active")
            idleStage = 0
            return
        }
        if (inhibited) return
        val times = confIdleTimes()
        val stage = when {
            times.size > 2 && elapsed > times[2] -> 4
            times.size > 2 && elapsed > times[2] - 5.0 -> 3
            times.size > 1 && elapsed > times[1] -> 2
            times.isNotEmpty() && elapsed > times[0] -> 1
            else -> 0
        }
        if (stage <= idleStage) return
        idleStage = stage
        log.info { "Idle for ${elapsed.toInt()} s: stage $stage" }
        when (stage) {
            4 -> {
                confIdleCallback()("idle-suspend")
                caulis.launch(confSuspendCommand())
            }
            3 -> confIdleCallback()("idle-presuspend")
            2 -> {
                confIdleCallback()("idle-lock")
                ensureLocked()
            }
            1 -> confIdleCallback()("idle")
        }
    }

    fun onSleep() {
        confIdleCallback()("sleep")
        if (confLockOnWakeup()) ensureLocked(anim = false)
    }

    fun onWakeup() {
        confIdleCallback()("wakeup")
        if (confLockOnWakeup()) ensureLocked()
    }

    // --- overlays ----------------------------------------------------------------------------------

    fun enterOverlay(overlay: Overlay) = queue.push(overlay)

    fun startOverlay(overlay: Overlay) {
        log.trace { "Going to enter $overlay..." }
        keyProcessor.onOtherAction()
        this.overlay = overlay
        overlay.init()
    }

    fun forceCloseOverlay() {
        val o = overlay ?: return
        log.trace { "Force-closing $o" }
        try {
            o.destroy()
        } finally {
            overlay = null
        }
    }

    fun exitOverlay() {
        log.trace { "Going to exit overlay..." }
        val o = overlay ?: return log.trace { "...aborted" }
        log.trace { "...destroy" }
        o.destroy()
    }

    fun onOverlayDestroyed() {
        log.trace { "Overlay destroyed" }
        queue.onOverlayDestroyed()
        overlay = null
        log.trace { "Resetting gesture" }
        resetGesture()
    }

    // --- views going -------------------------------------------------------------------------------

    fun destroyView(view: View) {
        log.info { "Destroying view $view" }
        val window = view.window
        viewsByWindow.remove(window)
        requestedSize.remove(window)
        val (s, wsState, _) = state.findViewOrNull(view) ?: run {
            // (A view destroyed before it was shown.)
            views.remove(view.handle)
            return
        }

        var bestView: Int? = null
        if (view.isFocused()) {
            log.trace { "Finding view to focus since $view closes..." }
            var p = view.parent
            while (p != null && !p.isTiled(state) && p.parent != null) p = p.parent
            if (p != null) bestView = p.handle

            if (bestView == null) {
                var bestScore = 1000.0
                for ((k, other) in wsState.views) {
                    if (!other.isTiled || k == view.handle) continue
                    var i = s.i
                    var j = s.j
                    var w = s.w
                    var h = s.h
                    if (s.isLayer) {
                        i = wsState.i + 0.5 * wsState.size
                        j = wsState.j + 0.5 * wsState.size
                        w = 0.0
                        h = 0.0
                    } else if (!s.isTiled) {
                        i = s.floatPos.first
                        j = s.floatPos.second
                        w = 0.0
                        h = 0.0
                    }
                    val di = other.i - i + other.w / 2.0 - w / 2.0
                    val dj = other.j - j + other.h / 2.0 - h / 2.0
                    val sc = di * di + dj * dj
                    log.trace { "View ($k) has score $sc" }
                    if (sc < bestScore) {
                        bestScore = sc
                        bestView = k
                    }
                }
            }
        }

        val bv = bestView?.takeIf { it in views }
        if (bv != null) {
            log.trace { "Found view to focus: ${views[bv]}" }
            animateTo({ st ->
                val next = views[bv]
                val result = if (next != null) {
                    val r = st.unswallowing(view).focusingView(next).withoutViewState(view).constrain()
                    focusBorders.updateFocus(next, null to r)
                    next.focus()
                    r
                } else {
                    // (The view may be gone meanwhile.)
                    focusBorders.unfocus()
                    st.unswallowing(view).withoutViewState(view).constrain()
                }
                null to result
            }, confAnimT(), { views.remove(view.handle) })
        } else {
            if (view.isFocused()) focusBorders.unfocus()
            animateTo({ st -> null to st.unswallowing(view).withoutViewState(view).constrain() }, confAnimT(), { views.remove(view.handle) })
        }
    }

    fun focusHint(view: View) {
        val wsHandle = state.findViewOrNull(view)?.third ?: return log.warn("Missing state: $this")
        val ws = workspaces.firstOrNull { it.handle == wsHandle } ?: return
        ws.focusViewHint = view.handle
        activeWorkspacePair = activeWorkspacePair.first to ws
    }

    /** newm's commands (newm-cmd): restart (newm's own), lock, config, update-config, debug, inhibit-idle, finish-inhibit-idle, close-launcher, clean, unlock. */
    fun command(cmd: String, arg: String? = null): String? {
        log.trace { "Received command $cmd" }
        return when (cmd) {
            "lock" -> {
                ensureLocked(anim = arg?.contains("anim") == true, dim = arg?.contains("dim") == true)
                null
            }
            "config" -> Config.printConfig()
            "update-config" -> {
                updateConfig()
                null
            }
            "debug" -> debugStr()
            "inhibit-idle" -> {
                idleInhibitUser = true
                null
            }
            "finish-inhibit-idle" -> {
                idleInhibitUser = false
                null
            }
            "open-launcher" -> {
                enterLauncherOverlay()
                null
            }
            "close-launcher" -> {
                if (overlay is LauncherOverlay) exitOverlay()
                null
            }
            "bar" -> barCommand(arg.orEmpty())
            // For newm's own tests only (NEWM_TEST_INPUT=1): the pointer moved, clicked, scrolled.
            "test-pointer" -> if (System.getenv("NEWM_TEST_INPUT") == "1") testPointer(arg.orEmpty()) else "Disabled"
            // "56+42" presses Alt and Shift together, then lets them go (evdev codes).
            "test-keys" -> if (System.getenv("NEWM_TEST_INPUT") == "1") {
                val codes = arg.orEmpty().trim().split('+').mapNotNull { it.trim().toIntOrNull() }
                codes.forEach { caulis.simulateKey(it, true) }
                codes.reversed().forEach { caulis.simulateKey(it, false) }
                null
            } else "Disabled"
            "restart" -> {
                // For dev/newm-dev: newm ends, and the script starts it again (a new build).
                restartRequested = true
                terminate()
                null
            }
            "clean" -> {
                animateTo({ s -> null to s.copy().clean(views.keys.toList()) }, confAnimT())
                null
            }
            "unlock" -> if (confEnableUnlockCommand()) {
                trustedUnlock()
                null
            } else {
                "Disabled"
            }
            else -> "Unknown command $cmd"
        }
    }

    // `newm cmd bar calendar|volume|network|battery|power|tray <n>|close`: a popup of the bar opened (or closed).
    private fun barCommand(arg: String): String? {
        val words = arg.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val bar = (topBars + bottomBars).firstOrNull { it.islands && it.monitor === activeMonitor() } ?: (topBars + bottomBars).firstOrNull { it.islands }
            ?: return "newm's islands bar is off"
        val x = bar.originX + bar.monitor.area.width / 2.0
        when (val what = words.firstOrNull() ?: "close") {
            "close" -> barModel.close()
            "tray" -> {
                val item = barModel.tray.items.getOrNull(words.getOrNull(1)?.toIntOrNull() ?: 0) ?: return "No such tray icon"
                barModel.toggle(newm.bar.PopupKind.TRAY_MENU, bar, x, item)
            }
            else -> {
                val kind = newm.bar.PopupKind.entries.firstOrNull { it.name.equals(what, ignoreCase = true) } ?: return "Unknown popup $what"
                barModel.toggle(kind, bar, x)
            }
        }
        return null
    }

    // "x y" moves the pointer there; "x y left|right|middle" clicks there too; "x y scroll N" scrolls.
    private fun testPointer(arg: String): String? {
        val w = arg.trim().split(Regex("\\s+"))
        val x = w.getOrNull(0)?.toDoubleOrNull() ?: return "x y [left|right|middle|scroll n]"
        val y = w.getOrNull(1)?.toDoubleOrNull() ?: return "x y [left|right|middle|scroll n]"
        when (w.getOrNull(2)) {
            null -> caulis.simulatePointer(x, y)
            "scroll" -> {
                caulis.simulatePointer(x, y)
                caulis.simulateScroll(w.getOrNull(3)?.toIntOrNull() ?: 1)
            }
            else -> caulis.simulatePointer(x, y, when (w[2]) { "right" -> 273; "middle" -> 274; else -> 272 })
        }
        return null
    }

    // The monitor with the pointer.
    private fun activeMonitor(): Monitor? = desktopOrNull()?.let { d -> d.monitors.firstOrNull { m -> d.pointerX >= m.area.x && d.pointerX < m.area.right && d.pointerY >= m.area.y && d.pointerY < m.area.bottom } }

    fun launchApp(cmd: String) {
        exitOverlay()
        caulis.launch(cmd)
    }

    fun isViewOnWorkspace(view: View, workspace: Workspace?): Boolean {
        if (workspace == null) return true
        val wsHandle = state.findViewOrNull(view)?.third ?: return false.also { log.warn("Missing state: $this") }
        return workspace.handle == wsHandle
    }

    // --- the API for the configuration: 1. getters ---------------------------------------------------

    fun activeWorkspace(): Workspace = activeWorkspacePair.second ?: activeWorkspacePair.first

    fun tiles(workspace: Workspace? = null): List<View> = views.values.filter { it.isTiled(state) && isViewOnWorkspace(it, workspace) }

    fun floats(workspace: Workspace? = null): List<View> = views.values.filter { it.isFloat(state) && isViewOnWorkspace(it, workspace) }

    fun panels(workspace: Workspace? = null): List<View> = views.values.filter { it.isPanel() && isViewOnWorkspace(it, workspace) }

    fun views(workspace: Workspace? = null): List<View> = views.values.filter { !it.isPanel() && isViewOnWorkspace(it, workspace) }

    fun findFocusedView(): View? = views.values.firstOrNull { it.isFocused() }

    // --- 2. general purpose ----------------------------------------------------------------------------

    /**
     * Loads the configuration again (as newm's update_config), calling what it is given once
     * the new one is in (later: config.kts is compiled meanwhile); set by main.
     */
    var reloadConfig: (then: () -> Unit) -> Unit = { then -> then() }

    fun updateConfig() {
        reloadConfig {
            setup()
            damage()
            confOnReconfigure()()
        }
    }

    fun ensureLocked(anim: Boolean = true, dim: Boolean = false) {
        fun focusLock() {
            // Meanwhile no keys of newm's or Caulis's but the laptop's (XF86) ones.
            safelyEnterMode(LOCKED_MODE)
            val lockScreen = panels().filter { it.panel == "lock" }
            if (lockScreen.isNotEmpty()) lockScreen[0].focus() else panelsUi.locked()
        }
        // A locker of one's own (swaylock, gtklock: ext-session-lock), which Caulis shows (newm issue 154).
        confLockProgram()?.takeIf { it.isNotBlank() }?.let { program ->
            if (!caulis.desktop.locked) {
                log.info { "Locking with $program" }
                caulis.launch(program)
            }
            if (dim) confIdleCallback()("lock")
            return
        }
        // Locked already: nothing more (a second lock asked for the password again, newm issue 98).
        if (state.lockPerc >= 1.0) {
            if (dim) confIdleCallback()("lock")
            return
        }
        authBackend.lock()
        animateTo({ s -> (if (anim) null else s.copy(lockPerc = 1.0, backgroundOpacity = 0.5)) to s.copy(lockPerc = 1.0, backgroundOpacity = 0.5) }, confAnimT(), ::focusLock)
        if (dim) confIdleCallback()("lock")
    }

    fun terminate() {
        animateTo({ s -> s.copy(final = true) to s.copy(final = true, backgroundOpacity = 0.0) }, confBlendT(), ::terminateNow)
    }

    // --- 3. global or workspace state, the viewpoint ------------------------------------------------------

    fun enterLauncherOverlay() = enterOverlay(LauncherOverlay(this))

    fun toggleOverview(onlyActiveWorkspace: Boolean = false) {
        animateTo({ s ->
            val overview = if (onlyActiveWorkspace) !s.workspaceState(activeWorkspace()).isInOverview() else !s.allInOverview()
            val focused = if (!overview) findFocusedView() else null
            null to s.withOverviewSet(overview, if (onlyActiveWorkspace) activeWorkspace() else null, focused)
        }, confAnimT())
    }

    fun toggleFullscreen(definedState: Boolean? = null) {
        val activeWs = activeWorkspace()
        animateTo({ s0 ->
            var s = s0
            if (s.workspaceState(activeWorkspace()).isInOverview()) s = s.withOverviewSet(false, onlyWorkspace = activeWorkspace())

            var view = findFocusedView()
            if (view != null && !view.isTiled(s)) view = null
            while (view != null && view.parent != null && !view.isTiled(s)) view = view.parent

            val ws: Workspace
            val wsState: WorkspaceState
            if (view != null) {
                val (_, vws, wsHandle) = s.findView(view)
                ws = workspaces.first { it.handle == wsHandle }
                wsState = vws
            } else {
                ws = activeWs
                wsState = s.workspaceState(activeWs)
            }

            val fs = wsState.isFullscreen()
            if (fs == definedState) return@animateTo null to null

            if (confSendFullscreenToViews()) for (v in tiles()) v.setFullscreen(!fs)

            when {
                fs -> null to s.settingWorkspaceState(ws, wsState.withoutFullscreen())
                view != null -> null to s.settingWorkspaceState(ws, wsState.withFullscreen(view))
                else -> null to null
            }
        }, confAnimT())
    }

    fun basicMove(deltaI: Int, deltaJ: Int) {
        val ws = activeWorkspace()
        animateTo({ s ->
            null to s.replacingWorkspaceState(ws) { copy(i = i + deltaI, j = j + deltaJ) }
        }, confAnimT())
    }

    fun basicScale(deltaS: Int) {
        val ws = activeWorkspace()
        animateTo({ s ->
            null to s.replacingWorkspaceState(ws) { copy(size = max(1.0, size + deltaS)) }
        }, confAnimT())
    }

    // --- 4. focus -----------------------------------------------------------------------------------------

    fun focusView(view: View) {
        animateTo({ s ->
            view.focus()
            null to s.focusingView(view)
        }, confAnimT())
    }

    fun moveInStack(@Suppress("UNUSED_PARAMETER") delta: Int) {
        val view = findFocusedView() ?: return
        try {
            val (viewState, wsState, _) = state.findView(view)
            val (sid, idx, siz) = viewState.stackData
            val nidx = (idx + 1) % siz
            val next = wsState.views.entries.filter { (_, s) -> s.stackData.id == sid && s.stackData.index == nidx }.map { it.key }
            if (next.isNotEmpty() && next[0] != view.handle) views[next[0]]?.focus()
        } catch (e: Exception) {
            log.error("Unexpected", e)
        }
    }

    fun move(deltaI: Int, deltaJ: Int) {
        val (ws, box) = findFocusedBox()
        val (i, j, w, h) = box.toList()
        val wsState = state.workspaceState(ws)
        if ((i + w > wsState.i + wsState.size && deltaI > 0) || (i < wsState.i && deltaI < 0) ||
            (j + h > wsState.j + wsState.size && deltaJ > 0) || (j < wsState.j && deltaJ < 0)
        ) {
            findFocusedView()?.let {
                focusView(it)
                return
            }
        }
        var best: Int? = null
        var bestScore = 1000.0
        for ((k, s) in wsState.views) {
            if (!s.isTiled) continue
            val sc = score(i, j, w, h, deltaI, deltaJ, s.i, s.j, s.w, s.h)
            if (sc < bestScore) {
                bestScore = sc
                best = k
            }
        }
        best?.let { views[it] }?.let(::focusView)
    }

    fun moveNextView(dv: Int = 1, activeWorkspace: Boolean = true) {
        val list = views(if (activeWorkspace) activeWorkspace() else null)
        val focused = findFocusedView()
        if (focused != null && focused in list) {
            focusView(list[Math.floorMod(list.indexOf(focused) + dv, list.size)])
        } else if (list.isNotEmpty()) {
            focusView(list[0])
        }
    }

    /** The next (or, with -1, the previous) window in turn (CRAG666's cycle_views, newm PR #180). */
    fun cycleViews(steps: Int = 1, activeWorkspace: Boolean = true) = moveNextView(steps, activeWorkspace)

    /** The window before (newm PR #165's move_prev_view). */
    fun movePrevView(activeWorkspace: Boolean = true) = moveNextView(-1, activeWorkspace)

    /** The window at [index] in turn, from 0 (newm PR #165's goto_view). */
    fun gotoView(index: Int, activeWorkspace: Boolean = true) {
        views(if (activeWorkspace) activeWorkspace() else null).getOrNull(index)?.let(::focusView)
    }

    /** Starts [command] inside newm (as in a shell). */
    fun launch(command: String) = caulis.launch(command)

    fun moveWorkspace(ds: Int = 1) {
        val ws = activeWorkspace()
        val i = workspaces.indexOfFirst { it.handle == ws.handle }
        val wsNew = workspaces[Math.floorMod(i + ds, workspaces.size)]
        val list = views(wsNew)
        wsNew.focusViewHint?.let { hint ->
            val view = list.filter { it.handle == hint }
            if (view.size == 1) {
                focusView(view[0])
                return
            }
        }
        if (list.isNotEmpty()) focusView(list[0])
    }

    // --- 5. the focused view --------------------------------------------------------------------------------

    fun closeFocusedView() {
        views.values.firstOrNull { it.isFocused() }?.close()
    }

    fun toggleFocusedViewFloating() {
        animateTo({ s ->
            val view = findFocusedView() ?: return@animateTo null to s
            try {
                val (vs, wsState, wsHandle) = s.findView(view)
                val ws = workspaces.first { it.handle == wsHandle }
                val (s1, s2) = view.toggleFloating(vs, ws, wsState)
                val wsState1 = wsState.withViewState(view, s1)
                val wsState2 = wsState.replacingViewState(view) {
                    isTiled = s2.isTiled; i = s2.i; j = s2.j; w = s2.w; h = s2.h; floatPos = s2.floatPos; floatSize = s2.floatSize
                }
                wsState2.validateStackIndices(view)
                s.settingWorkspaceState(ws, wsState1) to s.settingWorkspaceState(ws, wsState2)
            } catch (e: Exception) {
                null to s
            }
        }, confAnimT())
    }

    fun changeFocusedViewWorkspace(ds: Int = 1) {
        animateTo({ s ->
            val view = findFocusedView() ?: return@animateTo null to s
            try {
                val (vs, _, wsHandle) = s.findView(view)
                val i = workspaces.indexOfFirst { it.handle == wsHandle }
                val ws = workspaces[i]
                if (!vs.isTiled) return@animateTo null to null
                val wsNew = workspaces[Math.floorMod(i + ds, workspaces.size)]
                if (ws === wsNew) return@animateTo null to null
                val without = s.withoutViewState(view)
                view.showTiled(wsNew, without, without.workspaceState(wsNew))
            } catch (e: Exception) {
                null to s
            }
        }, confAnimT())
    }

    fun moveFocusedView(di: Int, dj: Int) {
        animateTo({ s ->
            val view = findFocusedView() ?: return@animateTo null to s
            try {
                val (vs, wsState, wsHandle) = s.findView(view)
                val ws = workspaces.first { it.handle == wsHandle }
                val next = wsState.replacingViewState(view) { i = vs.i + di; j = vs.j + dj }.focusingView(view)
                next.validateStackIndices(view)
                null to s.settingWorkspaceState(ws, next)
            } catch (e: Exception) {
                null to s
            }
        }, confAnimT())
    }

    fun resizeFocusedView(di: Int, dj: Int) {
        animateTo({ s ->
            val view = findFocusedView() ?: return@animateTo null to s
            try {
                val vs = s.viewState(view)
                var i = vs.i
                var j = vs.j
                var w = vs.w + di
                var h = vs.h + dj
                if (w == 0.0) {
                    w = 2.0
                    i -= 1
                }
                if (h == 0.0) {
                    h = 2.0
                    j -= 1
                }
                val (_, wsState, wsHandle) = s.findView(view)
                val ws = workspaces.first { it.handle == wsHandle }
                val next = wsState.replacingViewState(view) { this.i = i; this.j = j; this.w = w; this.h = h }.focusingView(view)
                s.validateStackIndices(view)
                null to s.settingWorkspaceState(ws, next)
            } catch (e: Exception) {
                null to s
            }
        }, confAnimT())
    }

    fun swallowFocusedView() {
        animateTo({ s ->
            val view = findFocusedView() ?: return@animateTo null to null
            if (!(view.isTiled(s) || view.isFloat(s))) return@animateTo null to null
            val by = view.findSwallower() ?: return@animateTo null to null
            val next = s.focusingView(by)
            if (view.isTiled(s)) {
                val bs = s.viewState(by)
                next.updateViewState(view) { swallowed = by.handle; i = bs.i; j = bs.j; w = bs.w; h = bs.h }
            } else {
                next.updateViewState(view) { swallowed = by.handle }
            }
            next.constrain()
            focusBorders.updateFocus(by, null to next)
            by.focus()
            null to next
        }, confAnimT())
    }

    private fun safelyEnterMode(mode: String?) {
        try {
            caulis.enterMode(mode)
        } catch (e: Exception) {
            log.warn("Key mode $mode: $e")
        }
    }

    /** The view with [handle] (as config.py's functions know it), if it is still there. */
    fun viewByHandle(handle: Int): View? = views[handle]

    // --- 6. legacy ----------------------------------------------------------------------------------------

    fun closeView() = closeFocusedView()
}

/** Caulis's key mode while newm is locked: no keys of its own (the emergency ones neither). */
const val LOCKED_MODE = "newm-locked"

private fun Double.toMillis(): Int = (this * 1000).toLong().toInt()
