package newm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.caulis.compose.compose
import newm.bar.connectionChanged
import newm.bar.hintPill
import newm.bar.showOsd
import newm.bar.showPopups
import newm.bar.showNotificationCards
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
// windows { newPlacement }: where a new window opens — "compact" (the free place next to the
// focused one keeping the wall compact, see Placement), "right-of-focused" (others move right),
// "beside-focused" (the first free tile right of it in its row), "end" (right of all), "original" (newm's).
private val confNewPlacement = configuredValue("windows.new_placement", Placement.COMPACT)
// (Before windows { newPlacement }: view { placeBesideFocused }, true "beside-focused", false "original".)
private val confPlaceBesideFocused = configuredValue<Boolean?>("view.place_beside_focused", null)
// windows { returnFocusToParent = true }: a child window closing gives the focus back to its parent
// (or to the window focused when it came); false: newm's nearest window.
private val confReturnFocusToParent = configuredValue("windows.return_focus_to_parent", true)
// view { fitDrawnSize = false }: a program that drew another size than asked is drawn at its own
// shape inside its place (true), not stretched to it (false, as newm).
private val confFitDrawnSize = configuredValue("view.fit_drawn_size", false)
// What a gesture does, shown at the bottom while it runs.
private val confGestureHints = configuredValue("gestures.hints", true)
private val confLockProgram = configuredValue<String?>("panels.lock.program", null)
private val confBackgroundPath = configuredValue<String?>("background.path", "resource:wallpaper.jpg")
private val confSendFullscreenToViews = configuredValue("view.send_fullscreen", true)
private val confKeyHelp = configuredValue<List<Pair<String, String>>>("key_help", emptyList())
private val confKeyBindings = configuredValue<(Layout) -> List<Pair<String, () -> Unit>>>("key_bindings") { emptyList() }
// newm fading in at the start and out at the end: the usual time of "startup" and "quit".
private val confBlendT = configuredValue("blend_time", 1.0)
private val confIdleTimes = configuredValue("energy.idle_times", listOf(120, 300, 600))
private val confSuspendCommand = configuredValue("energy.suspend_command", "systemctl suspend")
// energy { dimAfter; lockAfter; suspendAfter } (seconds, 0: never; unset: idle_times'), and after(seconds) { } of your own.
private val confDimAfter = configuredValue<Any?>("energy.dim_after", null)
private val confLockAfter = configuredValue<Any?>("energy.lock_after", null)
private val confSuspendAfter = configuredValue<Any?>("energy.suspend_after", null)
private val confIdleActions = configuredValue("energy.after", emptyList<Map<String, Any?>>())

/**
 * code "lock": on lock (dim the screen now); "idle": idle_times[0] passed; "idle-lock":
 * idle_times[1] passed, locked too; "idle-presuspend": 5 s before idle_times[2]; "idle-suspend":
 * idle_times[2] passed, suspended too; "active": activity after idle; "sleep", "wakeup".
 */
private val confIdleCallback = configuredValue<(String) -> Unit>("energy.idle_callback") { }
private val confOnStartup = configuredValue<() -> Unit>("on_startup") { }
private val confOnReconfigure = configuredValue<() -> Unit>("on_reconfigure") { }
private val confLockOnWakeup = configuredValue("lock_on_wakeup", true)
// lock { sleepWait = 3 }: seconds sleep waits for the lock screen to show (0..30).
private val confSleepWait = configuredValue<Number>("panels.lock.sleep_wait", 3.0)
// Frames held on longer than this (seconds) are told in the log (something forgot to let go).
private const val FRAMES_HELD_WARN_SECONDS = 10

// (Its default does nothing: then the layout's queue sleeps while nothing is queued.)
private val noSynchronousUpdate: () -> Unit = { }
private val confSynchronousUpdate = configuredValue("synchronous_update", noSynchronousUpdate)
private val confEnableCGestures = configuredValue("gestures.c.enabled", true)
private val confEnableDBusGestures = configuredValue("gestures.dbus.enabled", true)
// (Off unless asked for: any program of the user's could unlock over the session bus without the password.)
private val confEnableUnlockCommand = configuredValue("enable_unlock_command", false)
private val confGestureBindingSwipeToZoom = configuredValue<List<String?>>("gesture_bindings.swipe_to_zoom", listOf(null, "swipe-4"))
private val confGestureBindingSwipe = configuredValue<List<String?>>("gesture_bindings.swipe", listOf(null, "swipe-3"))
// A window made wider or taller by keys pushes its neighbours aside (false, as newm: it stacks on them).
private val confResizePushes = configuredValue("view.resize_pushes", false)
// overview { input = false: windows take no clicks there; choose = true: a click chooses one (and leaves) }
private val confOverviewInput = configuredValue("overview.input", false)
private val confOverviewChoose = configuredValue("overview.choose", true)
private val confOverviewFollow = configuredValue("overview.follow_pointer", true)
private val confGestureBindingMoveResize = configuredValue<List<String?>>("gesture_bindings.move_resize", listOf("L", "move-1", "swipe-2"))
private val confGestureBindingLauncher = configuredValue<List<String?>>("gesture_bindings.launcher", listOf(null, "swipe-5"))
// gestures { on("swipe-4", "down") { } }: kind, direction, modifier, description, action.
private val confGestureOn = configuredValue("gestures.on", emptyList<Map<String, Any?>>())

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
    // (A hair's breadth counts as touching: windows on screens lie at thirds, fifths, ...)
    val eps = 1e-6
    val di = max(0.0, i2 - (i1 + w1)).takeIf { i2 - (i1 + w1) > -eps } ?: return 1000.0
    if (jm == 1) {
        val dj = max(0.0, j2 - (j1 + h1)).takeIf { j2 - (j1 + h1) > -eps } ?: return 1000.0
        return di + dj
    }
    val dj = when {
        j2 >= j1 + h1 - eps -> max(0.0, j2 - (j1 + h1))
        j1 >= j2 + h2 - eps -> max(0.0, j1 - (j2 + h2))
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
    private val curve: dev.caulis.core.Curve = dev.caulis.core.Curve.LINEAR,
) {
    private var initialState: LayoutState? = null
    private var finalState: LayoutState? = null
    private var started = false
    private var finish: Double? = null
    // The workspaces it was made for (the monitors may change while it runs).
    private var generation = -1

    // (0: at once — the animation is off.)
    private val duration = max(0.0, duration)

    fun checkFinished(): Boolean {
        if (started && finalState == null) return finished(null)
        if (started && finish == null) finish = layout.finalTimeAll()
        val f = finish
        if (f != null && now() > f) return finished(finalState)
        return false
    }

    // Its final state written, `then` run and the parts' animations flushed — whatever fails
    // on the way, so the queue goes on.
    // (`then` runs even when the final state fails: it may let go of what holds frames on.)
    private fun finished(state: LayoutState?): Boolean {
        try {
            if (state != null) {
                // Made before the monitors changed: on the workspaces there are now (else a
                // workspace gone came back with it).
                if (generation != layout.layoutGeneration) {
                    log.info { "Animation made for older workspaces: its final state moved onto the ones now" }
                    state.withWorkspaces(layout)
                }
                layout.update(state)
            }
        } catch (e: Exception) {
            log.error("Finishing an animation", e)
        }
        try {
            then?.invoke()
        } catch (e: Exception) {
            log.error("After an animation", e)
        } finally {
            layout.doFlushAnimation()
        }
        return true
    }

    /** Given up (it failed): what was to follow it runs all the same. */
    fun abandon() {
        finished(null)
    }

    fun start() {
        generation = layout.layoutGeneration
        // (What the reducer starts along with it — the focus border — goes as long, along the same curve.)
        layout.runningAnimation = duration to curve
        try {
            reducer(layout.state).let { (i, f) ->
                initialState = i
                finalState = f
            }
            initialState?.let(layout::update)
            val f = finalState
            if (f != null) {
                // Enforce constraints on the final state.
                f.constrainAndValidate()
                if (f == layout.state) {
                    log.trace { "Skipping moot animation" }
                    finalState = null
                } else {
                    layout.animateAll(f, duration, curve)
                }
            } else {
                log.trace { "Animation decided not to take place anymore" }
            }
        } catch (e: Exception) {
            // (No final state: it ends at once and the queue goes on.)
            log.error("During animation start", e)
            initialState = null
            finalState = null
        } finally {
            started = true
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
    private var started = false

    // About 30 times a second while something is queued, runs or the configuration wants
    // every_frame (synchronous_update); asleep otherwise (an idle compositor wakes for nothing).
    private val tickMillis = 1000L / 30

    fun start() {
        started = true
        wake()
    }

    fun stop() {
        started = false
        ticker?.close()
        ticker = null
    }

    /** Ticks again if there is work (something queued or running, or every_frame configured). */
    fun wake() {
        if (!started || ticker != null || !busy()) return
        log.trace { "Layout queue ticking" }
        ticker = layout.every(tickMillis) { tick() }
    }

    /** Whether the queue ticks now (for tests and the log). */
    val ticking: Boolean get() = ticker != null

    private fun busy(): Boolean =
        pending.isNotEmpty() || currentOverlay != null || currentAnimation != null || confSynchronousUpdate() !== noSynchronousUpdate

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
        wake()
    }

    fun onOverlayDestroyed() {
        log.trace { "Finishing overlay..." }
        currentOverlay = null
        layout.exitConstantDamage()
    }

    private fun tick() {
        try {
            val first = pending.firstOrNull()
            // (Frames held from the start of each, let go when it ends — or fails to start.)
            if (first is Overlay) {
                if (currentAnimation == null && currentOverlay == null) {
                    log.trace { "Starting overlay..." }
                    pending.removeAt(0)
                    currentOverlay = first
                    layout.enterConstantDamage()
                    try {
                        layout.startOverlay(first)
                    } catch (e: Exception) {
                        log.error("An overlay failed to start: left", e)
                        currentOverlay = null
                        layout.overlayFailed(first)
                        layout.exitConstantDamage()
                    }
                }
            } else if (first is Animation) {
                if (currentAnimation == null && (currentOverlay == null || first.overlaySafe)) {
                    log.trace { "Starting animation..." }
                    pending.removeAt(0)
                    currentAnimation = first
                    layout.enterConstantDamage()
                    try {
                        first.start()
                    } catch (e: Exception) {
                        log.error("An animation failed to start: left", e)
                        currentAnimation = null
                        first.abandon()
                        layout.exitConstantDamage()
                    }
                }
            }
            currentAnimation?.let { a ->
                // (One that fails to finish is done all the same: never the queue stopped for good.)
                val done = try {
                    a.checkFinished()
                } catch (e: Exception) {
                    log.error("Unexpected while finishing an animation", e)
                    a.abandon()
                    true
                }
                if (done) {
                    log.trace { "Finishing animation..." }
                    currentAnimation = null
                    layout.exitConstantDamage()
                }
            }
            confSynchronousUpdate()()
        } catch (e: Exception) {
            log.error("Unexpected during the layout's queue", e)
        }
        // Nothing left to do: asleep until the next push.
        if (!busy()) {
            ticker?.close()
            ticker = null
            log.trace { "Layout queue idle" }
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

    /** What keeps the computer awake besides Wayland (D-Bus, MPRIS, logind), and in words for the bar. */
    val idleInhibit = IdleInhibitState(this)

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

    // Hyprland's special workspaces (Caulis's), above the wall.
    private val specials = NewmSpecialWorkspaces(this)
    private var backgrounds: List<Background> = emptyList()
    // (Compose state: the bars' parts follow when a reload makes them anew.)
    private var topBars: List<Bar> by androidx.compose.runtime.mutableStateOf<List<Bar>>(emptyList())

    /** How far down from a monitor's top the top bar reaches (0: none), for what must stay below it. */
    fun topBarReach(): Int = topBars.mapNotNull { bar -> bar.place()?.let { it.bottom - bar.monitor.area.y } }.maxOrNull() ?: 0
    private var bottomBars: List<Bar> by androidx.compose.runtime.mutableStateOf<List<Bar>>(emptyList())

    /** The wallpaper's most telling colour (the colours are made from it), once it was read. */
    var wallpaperSeed: dev.caulis.render.Color? = null

    /** newm's own bars now (top and bottom, every monitor). */
    fun barsNow(): List<Bar> = topBars + bottomBars

    /** What newm's bars share: the tray, the computer's state, the popups (started with the first islands bar). */
    val barModel: newm.bar.BarModel = newm.bar.BarModel(this)
    private val corners = Corners(this)
    private var wallpaper: Pair<String?, Wallpaper?>? = null
    private val queue = LayoutQueue(this)

    private var idleInhibitUser = false

    /** Caffeine: the screen stays on (no dimming, locking or sleeping) until then (Long.MAX_VALUE: until turned off); null: off. */
    var caffeineUntil by androidx.compose.runtime.mutableStateOf<Long?>(null)
        private set
    val caffeineOn: Boolean get() = caffeineUntil?.let { it > System.currentTimeMillis() } == true

    /** Caffeine for [minutes] (0: until turned off; null: off now). */
    fun caffeine(minutes: Int?) {
        caffeineUntil = minutes?.let { if (it <= 0) Long.MAX_VALUE else System.currentTimeMillis() + it * 60_000L }
        log.info { if (minutes == null) "Caffeine off" else if (minutes <= 0) "Caffeine on" else "Caffeine for $minutes min" }
    }

    // The workspace the cursor is on, and the one a focused view overrides it with.
    private var activeWorkspacePair: Pair<Workspace, Workspace?> = workspaces[0] to null

    // pywm's part: the views by handle, and by window.
    private val views = LinkedHashMap<Int, View>()
    private val viewsByWindow = java.util.IdentityHashMap<Window, View>()
    private var nextHandle = 1
    private var constantDamage = 0
    private var modifiers = 0
    private val requestedSize = java.util.IdentityHashMap<Window, Pair<Int, Int>>()
    // Views whose placing failed (told once each).
    private val brokenViews = HashSet<Int>()
    // The view with the focus now, and the one that had it when its window closed.
    private var focusedView: View? = null
    private var closingFocused: View? = null
    private var lostFocus: Pair<Window, Double>? = null
    private var focusedWindowNow: Window? = null

    /** The last view with the focus that was shown then (the one a new window opens beside, or is a child of). */
    internal var lastShownFocused: View? = null
        get() = field?.takeIf { !it.destroyed }
    /** The layout's state of this frame (the lock screen's and the launcher's way in). */
    var layoutDown = LayoutDown(0.0)
        private set

    // The monitors' room left by bar programs, as last seen.
    private var lastUsable: List<dev.caulis.geometry.Rect>? = null
    // The monitors' places, sizes and scales, as the workspaces were made for them.
    private var lastGeometry: List<Triple<String, dev.caulis.geometry.Rect, Double>>? = null

    /** Counted up each time the workspaces are made anew (an animation made before is told by it). */
    internal var layoutGeneration = 0
        private set

    private fun geometryNow() = desktopOrNull()?.monitors?.map { Triple(it.name, it.area, it.scale) }

    init {
        caulis.director = director()
        caulis.onKey { key -> onKeyInput(key) }
        caulis.onPointerMotion { dx, dy -> onMotion(now().toMillis(), dx, dy) }
        caulis.onPointerButton { button, pressed -> onButton(now().toMillis(), button, pressed) }
        caulis.onScroll { vertical, value, _ -> onAxis(now().toMillis(), vertical, value) }
        caulis.onGesture { gesture -> onTouchpadGesture(gesture) }
        // (Not while Caulis is taking everything down at the end: the desktop is gone then, and
        // nothing is left to rearrange.)
        caulis.onWindowClosed { window -> if (caulis.isRunning) viewsByWindow[window]?.destroy() }
        caulis.onFocusChanged { window ->
            // The focused view, and the one that had it while it closes (Caulis moves the focus
            // on before newm hears of the window closing).
            val before = focusedView
            if (before != null && !before.window.mapped) closingFocused = before
            // (Caulis may move the focus on while the closing window still counts as mapped: the
            // one that lost it just now, for destroyView.)
            val beforeWindow = focusedWindowNow
            if (beforeWindow != null && window !== beforeWindow) lostFocus = beforeWindow to now()
            focusedWindowNow = window
            focusedView = window?.let(::viewOf)
            // (A window not shown yet — Caulis focuses a new one at once — is not where the next one opens beside.)
            focusedView?.takeIf { state.findViewOrNull(it) != null }?.let { lastShownFocused = it }
            for (v in views.values.toList()) v.onFocusChange()
        }
        caulis.onMonitorAdded { onLayoutChange() }
        caulis.onMonitorRemoved { onLayoutChange() }
        caulis.onSleep { onSleep() }
        // One lock screen: newm's (or the configuration's locker), never Caulis's swaylock as well.
        // Shown: newm's once it covers the screen, a locker program's once it locked the session
        // (ext-session-lock) — sleep waits for that (lock { sleepWait }), and it is not locked twice.
        caulis.locker(shows = { lockScreenShows() }) { ensureLocked() }
        caulis.onWake { onWakeup() }
        // Idle: Caulis says when nobody is there (programs that keep the screen on hold it off),
        // newm counts the seconds and goes through energy.idle_times.
        caulis.onIdle(1, resume = { idleSince = null; onIdle(0.0, false) }) { idleSince = now() - 1.0 }
        caulis.onStart { caulis.every(1000) { idleTick() } }
        caulis.onStart { main() }
        // view { rules } may send windows into special workspaces too (as Caulis' rules do).
        caulis.onStart { specials.takeOverRules() }
        // Windows in special workspaces look as the wall's (Caulis draws them: newm's corners, border, rules).
        caulis.effectsFor { window, fx -> specials.effectsOf(window, fx) }
    }

    // --- pywm's part ---------------------------------------------------------------------------

    /** Runs [action] every [millis] on the compositor's thread; close the result to stop. */
    fun every(millis: Long, action: () -> Unit): AutoCloseable = caulis.every(millis) { action() }

    /** The view of [window] (made when it is first placed). */
    fun viewOf(window: Window): View? = viewsByWindow[window]

    fun focusWindow(window: Window) {
        // (A window waiting for a modal dialog: Caulis gives the dialog the focus.)
        caulis.desktop.focus(window)
    }

    /** Draws anew. */
    fun damage() {
        desktopOrNull()?.requestRedraw()
    }

    // What runs once a drawn frame (an overlay following the fingers): paced by the screen.
    private val frameActions = ArrayList<() -> Unit>()

    /**
     * Runs [action] once every drawn frame (the screen's own pace), until the result is closed;
     * frames are drawn meanwhile. Close it on every way the caller ends.
     */
    fun everyFrame(action: () -> Unit): AutoCloseable {
        frameActions += action
        enterConstantDamage()
        var closed = false
        return AutoCloseable {
            if (!closed) {
                closed = true
                frameActions -= action
                exitConstantDamage()
            }
        }
    }

    // Since when frames are held on without pause (seconds), and whether that was told.
    private var framesHeldSince: Double? = null
    private var framesHeldTold = false

    // Frames held on for long (an enter without its exit somewhere): every monitor is drawn
    // every refresh then. Said once, with who holds them.
    private fun watchHeldFrames() {
        if (constantDamage <= 0) {
            if (framesHeldTold) log.info { "Frames are no longer held on" }
            framesHeldSince = null
            framesHeldTold = false
            return
        }
        val since = framesHeldSince ?: now().also { framesHeldSince = it }
        if (!framesHeldTold && now() - since > FRAMES_HELD_WARN_SECONDS) {
            framesHeldTold = true
            val who = listOfNotNull(
                overlay?.let { "overlay ${it::class.simpleName}" },
                "queue".takeIf { queue.ticking },
                "${frameActions.size} frame actions".takeIf { frameActions.isNotEmpty() },
                views.values.filter { it.holdsFrames }.takeIf { it.isNotEmpty() }?.joinToString(prefix = "views ") { it.handle.toString() },
            )
            log.warn("Frames held on for over $FRAMES_HELD_WARN_SECONDS s (count $constantDamage): ${who.joinToString().ifEmpty { "nobody known" }}")
        }
    }

    /** Whether the layout's queue ticks now (asleep at idle). */
    internal val queueTicking: Boolean get() = queue.ticking

    /** How many hold frames on without pause now (0 at idle). */
    internal val framesHeld: Int get() = constantDamage

    fun enterConstantDamage() {
        constantDamage++
        damage()
    }

    fun exitConstantDamage() {
        constantDamage = max(0, constantDamage - 1)
    }

    // newm's own lock: shown (or coming, going), or in force for Caulis.
    private fun isLocked(): Boolean = state.lockPerc > 0.0 || caulis.desktop.lockedInternally

    // The desktop, once the compositor runs.
    private fun desktopOrNull(): dev.caulis.core.Desktop? = try {
        caulis.desktop
    } catch (_: IllegalStateException) {
        null
    }

    // Where each view is drawn: newm's downstream state of it, as Caulis places windows.
    private fun director() = object : WindowDirector {
        // Windows of Caulis's scratchpads (caulis.scratchpad(...) in the configuration) are the
        // desktop's: they slide in above newm's wall and away again (newm issue 70).
        // A window a rule sends into a special workspace is not newm's either: it opens straight
        // in there (newm placing it first would move the wall's windows and the view for nothing).
        override fun manages(window: Window): Boolean = caulis.desktop.scratchpadOf(window) == null && !specials.opensInSpecial(window)

        override fun place(window: Window, now: Long): WindowPlace? {
            val view = viewsByWindow.getOrPut(window) {
                View(this@Layout, window, nextHandle++).also { views[it.handle] = it }
            }
            val up = Up(window, window === caulis.desktop.focusedWindow)
            val previous = view.upState
            // (A view that cannot be placed — a broken rule, a bug — is hidden, never left where
            // Caulis would put it: outside the layout and the lock screen. Told once.)
            val down = try {
                view.process(up)
            } catch (e: Exception) {
                if (brokenViews.add(view.handle)) log.error("$view cannot be placed: hidden", e)
                return null
            }
            // The program asked to draw at the size newm wants.
            val (w, h) = down.size
            if (w > 0 && h > 0 && requestedSize[window] != w to h) {
                requestedSize[window] = w to h
                down.floating?.let { window.tiled = !it }
                window.configure(w, h)
            } else if (previous != null && previous.size != up.size && up.size != requestedSize[window]) {
                view.onResized(up.size.first, up.size.second, clientLeading = true)
            }
            return placeOf(down, up.size, steady = !animating && !view.animating)
        }

        override fun decorate(window: Window, place: WindowPlace, canvas: OverlayCanvas, above: Boolean) {
            if (above) return
            // What is behind it blurred (a see-through terminal), if its rule says so.
            viewsByWindow[window]?.blur?.let { (size, passes) ->
                val area = place.clip?.let { place.rect.intersect(it) } ?: place.rect
                canvas.blur(area, radius = place.cornerRadius ?: 0f, size = size, passes = passes)
            }
            val focused = focusBorders.currentView
            if (focused?.window === window && focused.wantsFocusBorder) focusBorders.draw(canvas)
            // (One wanting attention, not focused: its own border, if focus { urgentColor } says one.)
            else if (window.urgent && viewsByWindow[window]?.wantsFocusBorder == true) focusBorders.drawUrgent(place, canvas)
        }

        // Asked once a frame: the layout's own state of this frame (pywm processed it every frame).
        override fun animating(now: Long): Boolean {
            for (action in frameActions.toList()) {
                try {
                    action()
                } catch (e: Exception) {
                    log.error("Unexpected in a frame's action", e)
                }
            }
            layoutDown = process(reducer(state))
            specials.everyFrame(overviewShown())
            // A monitor got another mode, scale or place (Caulis says nothing of it): workspaces anew.
            val geometry = geometryNow()
            if (geometry != lastGeometry) {
                if (lastGeometry != null) {
                    log.info { "Monitors changed (mode, scale or place): workspaces made anew" }
                    caulis.post { onLayoutChange() }
                }
                lastGeometry = geometry
            }
            // A bar program came, went or changed its height: windows make way anew.
            val usable = desktopOrNull()?.monitors?.map { it.usableArea }
            if (usable != lastUsable) {
                if (lastUsable != null) animateTo({ s -> null to s.copy() }, "camera")
                lastUsable = usable
            }
            val moving = constantDamage > 0 || animating || focusBorders.animating || views.values.any { it.animating } ||
                topBars.any { it.animating } || bottomBars.any { it.animating }
            if (moving) log.trace {
                "Moving: " + listOfNotNull("damage $constantDamage".takeIf { constantDamage > 0 }, "layout".takeIf { animating }, "borders".takeIf { focusBorders.animating },
                    views.values.filter { it.animating }.takeIf { it.isNotEmpty() }?.joinToString(prefix = "views ") { it.handle.toString() },
                    "bars".takeIf { topBars.any { it.animating } || bottomBars.any { it.animating } }).joinToString()
            }
            return moving
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
    private fun placeOf(down: ViewDown, drawn: Pair<Int, Int>? = null, steady: Boolean = true): WindowPlace? {
        var b = down.box
        if (b.w <= 0.0 || b.h <= 0.0) return null
        // The program drew another size than asked: stretched to its place, as newm (pywm) did;
        // with view { fitDrawnSize }: its picture at its own shape (zoomed as the view is) in the
        // middle of its place, made smaller to fit when bigger. (Not while moving: there the
        // picture follows the place until the program catches up.)
        val tileBox = b
        val asked = down.size
        var cut = false
        // Being resized: its picture at its own size from the top left, cut to the gliding place.
        if (down.resizeScale > 0.0 && drawn != null && drawn.first > 0 && drawn.second > 0) {
            b = Box(b.x, b.y, drawn.first * down.resizeScale, drawn.second * down.resizeScale)
            cut = true
        } else if (confFitDrawnSize() && drawn != null && asked.first > 0 && asked.second > 0 && drawn.first > 0 && drawn.second > 0 && drawn != asked &&
            // (Bigger than asked, it will stay so: fitted while the view moves too, never stretched to
            // its place and back — a harmonica. Smaller: it is catching up, it follows its place.)
            (steady || drawn.first >= asked.first && drawn.second >= asked.second)
        ) {
            val sx = b.w / asked.first
            val sy = b.h / asked.second
            var w = drawn.first * sx
            var h = drawn.second * sy
            // Bigger than its place: made smaller as a whole to fit it (never cut, never wider or
            // taller than it is: only shrunk, which stays sharp).
            val fit = minOf(1.0, b.w / w, b.h / h)
            w *= fit
            h *= fit
            b = Box(b.x + (b.w - w) / 2, b.y + (b.h - h) / 2, w, h)
            cut = true
        }
        // While locked, only the lock screen's views are shown and take input.
        val lockHidden = if (down.lockEnabled) 0.0 else layoutDown.lockPerc
        val alpha = (down.opacity * (1.0 - lockHidden)).coerceIn(0.0, 1.0)
        if (alpha <= 0.0) return null
        var clip: Rect? = null
        if (down.mask != Box.NO_MASK) {
            val m = down.mask
            clip = Rect(Math.floor(tileBox.x + m.x).toInt(), Math.floor(tileBox.y + m.y).toInt(), Math.ceil(m.w).toInt(), Math.ceil(m.h).toInt())
        }
        down.workspace?.let { ws ->
            val wsRect = Rect(ws.x.toInt(), ws.y.toInt(), ws.w.toInt(), ws.h.toInt())
            clip = (clip ?: wsRect).intersect(wsRect) ?: return null // (outside its workspace: not shown)
        }
        if (cut) {
            val t = Rect(Math.floor(tileBox.x).toInt(), Math.floor(tileBox.y).toInt(), Math.ceil(tileBox.w).toInt(), Math.ceil(tileBox.h).toInt())
            clip = (clip ?: t).intersect(t) ?: return null
        }
        if (clip?.let { it.width <= 0 || it.height <= 0 } == true) return null
        return WindowPlace(
            b.x, b.y, b.w, b.h, alpha = alpha.toFloat(), cornerRadius = down.cornerRadius.toFloat(), clip = clip,
            z = down.zIndex, takesInput = down.acceptsInput && lockHidden == 0.0 && (confOverviewInput() || !overviewShown()),
        )
    }

    /** Where [view]'s window shows in the layout's state now: its place cut by its clip (null: not shown). */
    internal fun shownArea(view: View): Rect? {
        val up = view.upState ?: return null
        val p = placeOf(view.reducer(up, state)) ?: return null
        val rect = Rect(Math.floor(p.x).toInt(), Math.floor(p.y).toInt(), Math.ceil(p.width).toInt(), Math.ceil(p.height).toInt())
        return p.clip?.let { rect.intersect(it) } ?: rect
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
            best?.focusViewHint = w.focusViewHint
        }
        workspaces = ws.ifEmpty { workspaces }
        for (w in workspaces.filter { it.handle < 0 }) {
            var h = 0
            while (h in workspaces.map { it.handle }) h++
            w.handle = h
        }
        log.trace { "Setup of newm workspaces" }
        for (w in workspaces) log.trace { "  $w" }
        layoutGeneration++
        state = state.withWorkspaces(this)
        updateActiveWorkspace()
    }

    private fun updateActiveWorkspace() {
        // (The workspaces made now, never the old ones of the same handle: their size was then.)
        val check1 = workspaces.firstOrNull { it.handle == activeWorkspacePair.first.handle } ?: workspaces[0]
        val check2 = activeWorkspacePair.second?.let { w -> workspaces.firstOrNull { it.handle == w.handle } }
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

    // The monitors the bars and backgrounds were made for last (the same objects).
    private var widgetsMonitors: List<Monitor> = emptyList()

    // The workspaces (layoutGeneration) the bars were made for.
    private var barsGeneration = -1

    // [keepBars]: bars made already for these monitors and workspaces stay (the start: the
    // first monitor was announced just before newm's main, which made them then).
    private fun setupWidgets(keepBars: Boolean = false) {
        val monitors = desktopOrNull()?.monitors ?: return
        val sameMonitors = widgetsMonitors.size == monitors.size && widgetsMonitors.indices.all { widgetsMonitors[it] === monitors[it] }
        val keep = keepBars && sameMonitors && barsGeneration == layoutGeneration &&
            topBars.isNotEmpty() == confNativeTopBarEnabled() && bottomBars.isNotEmpty() == confNativeBottomBarEnabled()
        widgetsMonitors = monitors.toList()
        fun workspaceFor(m: Monitor): Workspace =
            workspaces.firstOrNull { w -> w.posX <= m.area.x && m.area.x < w.posX + w.width && w.posY <= m.area.y && m.area.y < w.posY + w.height }
                ?: workspaces[0].also { log.warn("Workspaces do not cover whole area") }

        if (keep) {
            log.trace { "The bars made a moment ago stay" }
        } else {
            barModel.close()
            // (Diagnostics: the bars made anew show for a frame as new — if they flash, this says when.)
            if (topBars.isNotEmpty() || bottomBars.isNotEmpty()) log.warn("The bars are made anew (the layout or the configuration changed)")
            topBars = if (confNativeTopBarEnabled()) monitors.map { Bar(this, it, top = true).also(Bar::refresh).also(Bar::look) } else emptyList()
            bottomBars = if (confNativeBottomBarEnabled()) monitors.map { Bar(this, it, top = false).also(Bar::refresh).also(Bar::look) } else emptyList()
            barsGeneration = layoutGeneration
        }
        barModel.refreshPalette()
        barModel.applyNoticeStyle() // (below the bars made now)
        shots?.apply()
        // The tray and the rest only with newm's islands bar (else another bar's tray stays its own).
        if ((topBars + bottomBars).any { it.islands }) barModel.start()

        // A moving wallpaper (background.shader) instead of the picture: drawn by the GPU, no picture read.
        val shaderName = ShaderWallpapers.configured()
        val shader = shaderName?.let { ShaderWallpapers.shader(this, it) }
        if (shader != null) {
            wallpaper?.second?.texture?.close()
            wallpaper = null
            // (Its first colour, if given, is the colours' seed: as a picture's telling colour.)
            wallpaperSeed = shader.colors.firstOrNull() ?: ShaderWallpapers.SEED
            backgrounds = monitors.map { Background(this, it, workspaceFor(it), null, ShaderWallpapers.forMonitor(shader, it)) }
            focusBorders.update()
            damage()
            return
        }
        // The wallpaper (newm's background.path; an output may name its own: one picture for all
        // here), read on a thread of its own: shown when it is ready.
        val path = confBackgroundPath()?.takeIf { it.isNotBlank() }
        if (wallpaper?.first != path) {
            wallpaper?.second?.texture?.close()
            wallpaper = path to null
            if (path != null) {
                Thread({
                    val image = Wallpaper.read(path)
                    // Its colour, for the colours made from it.
                    val seed = image?.let { runCatching { dev.caulis.core.appearance.Palette.seedOf(it) }.getOrNull() }
                    caulis.post {
                        if (image != null && wallpaper?.first == path) {
                            wallpaper = path to Wallpaper.of(caulis.desktop.renderer, image)
                            wallpaperSeed = seed
                            wallpaperArrived()
                        }
                    }
                }, "newm-wallpaper").apply { isDaemon = true }.start()
            }
        }
        backgrounds = wallpaper?.second?.let { picture -> monitors.map { Background(this, it, workspaceFor(it), picture) } } ?: emptyList()

        focusBorders.update()
        damage()
    }

    // The wallpaper read (on its own thread) is ready: shown, and its colour for the colours
    // made from it — the bars stay as they are (they were made anew for it before: a second
    // whole composition of every bar at each start).
    private fun wallpaperArrived() {
        val monitors = desktopOrNull()?.monitors ?: return
        val picture = wallpaper?.second ?: return
        log.info { "The wallpaper is ready: shown on ${monitors.size} monitor(s)" }
        backgrounds = monitors.map { m ->
            val ws = workspaces.firstOrNull { w -> w.posX <= m.area.x && m.area.x < w.posX + w.width && w.posY <= m.area.y && m.area.y < w.posY + w.height } ?: workspaces[0]
            Background(this, m, ws, picture)
        }
        barModel.refreshPalette()
        focusBorders.update()
        damage()
    }

    /** For a drag with a mouse button: the pixels a gesture's 1 is (the window follows the pointer one to one). */
    internal fun dragScale(): Double = moveResizeFactor() * ((activeMonitor() ?: caulis.desktop.monitors.firstOrNull())?.area?.width?.toDouble() ?: 1920.0)

    private fun setup(reconfigure: Boolean = true) {
        setupWidgets(keepBars = !reconfigure)
        // Sleep locks first (lock_on_wakeup) and waits up to lock { sleepWait } seconds for the lock screen to show.
        caulis.lockBeforeSleep = confLockOnWakeup()
        caulis.sleepLockWaitMillis = (confSleepWait().toDouble() * 1000).toLong().coerceIn(0L, 30_000L)
        caulis.specialWorkspaces = specials.settings()
        // Animations on or off: Caulis's own (scale changes, notices, ...) follow — but never its
        // windows' (newm moves and draws its windows itself: Caulis's would come on top, a second
        // pop where the window was). With preset = "newm" alone (newm's look as it always was)
        // and set nowhere they stay as Caulis's settings say (off by default).
        NewmAnimations.settings()
        NewmAnimations.caulisEnabled()?.let { on ->
            val now = caulis.animations
            val windowsOff = now.entries["windows"]?.enabled == false
            if (now.enabled != on || (on && !windowsOff)) {
                log.info { "Caulis's animations ${if (on) "on too (its windows' not: newm's are)" else "off too"}" }
                caulis.animations = NewmAnimations.caulisAnimations(now, on)
            }
        }
        keyProcessor.clear()
        // (Each action knows the key that runs it, for what it says: "Logo+S puts it beside".)
        keyProcessor.registerBindings(*keyBindingsOf(confKeyBindings()(this)).map { (combo, action) ->
            combo to {
                runningKey = combo
                try {
                    action()
                } finally {
                    runningKey = null
                }
            }
        }.toTypedArray())

        // Stop and set up the gestures anew.
        gestureProviders.forEach(GestureProvider::stop)
        val dbusProvider = if (confEnableDBusGestures()) DBusGestureProvider(::gestureProviderCallback) else null
        dbusEndpoint.gestureProvider = dbusProvider
        gestureProviders = listOfNotNull(if (confEnableCGestures()) CGestureProvider(::gestureProviderCallback, ::every, ::dragScale) else null, dbusProvider)
        gestureProviders.forEach(GestureProvider::start)

        if (reconfigure) views.values.forEach(View::update)
    }

    fun reducer(state: LayoutState): LayoutDown = LayoutDown(state.lockPerc, state.launcherPerc)

    override fun animate(oldState: LayoutState, newState: LayoutState, dt: Double, curve: dev.caulis.core.Curve) {
        startAnimation(LayoutDownInterpolation(reducer(oldState), reducer(newState)), dt, curve)
    }

    override fun flushAnimation() = flushAnimationOwn()

    override fun animDamage() = damage()

    private fun main() {
        log.info { "Layout main" }
        // (Made already when the first monitor was announced just before: not again.)
        val geometry = geometryNow()
        if (geometry == null || geometry != lastGeometry || barsGeneration != layoutGeneration) setupWorkspaces()
        lastGeometry = geometry
        setup(reconfigure = false)
        addWidgetDrawings({ backgrounds }, corners) { topBars + bottomBars }
        panelsUi.start()
        clipboardHistory.start()
        Warmup(this).start()
        // The windows kept by `restart keep` (or, with session.restore_at_login, of the last session), opened
        // again — through Caulis's start queue: once newm is shown, one after another.
        session.restoreAtStart()
        session.startKeeping()
        // The native bars (Compose), their texts asked for every second.
        val topBarWidget = caulis.compose(place = { m -> topBars.firstOrNull { it.monitor === m }?.place() }) { m ->
            topBars.firstOrNull { it.monitor === m }?.let { if (it.islands) newm.bar.IslandBar(it, barModel) else BarContent(it) }
        }
        val bottomBarWidget = caulis.compose(place = { m -> bottomBars.firstOrNull { it.monitor === m }?.place() }) { m ->
            bottomBars.firstOrNull { it.monitor === m }?.let { if (it.islands) newm.bar.IslandBar(it, barModel) else BarContent(it) }
        }
        // The bars drawn once ahead while the screen is still dark (reveal): newm fades in whole,
        // no bar popping in. (Right after the widgets are made, which is the next moment.)
        caulis.whenWarm("the bars") { done ->
            fun onThisThread() = caulis.after(0) {
                try {
                    for ((widget, bars) in listOf(topBarWidget to topBars, bottomBarWidget to bottomBars)) {
                        for (bar in bars) bar.place()?.let { r -> widget.prewarm(bar.monitor, r.width, r.height) }
                    }
                } finally {
                    done()
                }
            }
            // Each bar's scene made on a thread of its own first (warmup.own_thread): composed
            // and drawn there, handed to the widget — the compositor's thread then only shows
            // its pixels (its first composition held it ~0.2-0.3 s: the pointer stood still).
            // (The widgets are there the next moment; the bars not placed yet while newm comes
            // in: as big as they will be.)
            caulis.after(0) {
                val ahead = if (!warmsOnThread()) emptyList() else listOf(topBarWidget to topBars, bottomBarWidget to bottomBars).flatMap { (handle, bars) ->
                    val widget = handle.widget ?: return@flatMap emptyList()
                    bars.map { b -> Triple(widget, b, b.place() ?: dev.caulis.geometry.Rect(b.monitor.area.x, b.monitor.area.y, b.monitor.area.width, b.room)) }
                }
                if (ahead.isEmpty()) {
                    onThisThread()
                    return@after
                }
                Thread.ofPlatform().daemon().name("newm-bar-warmup").start {
                    val started = System.nanoTime()
                    for ((widget, bar, r) in ahead) {
                        try {
                            widget.prepareAhead(bar.monitor, r.width, r.height)
                        } catch (e: Throwable) {
                            if (e is VirtualMachineError && e !is StackOverflowError) throw e
                            log.warn("Making the bar ahead failed: $e")
                        }
                    }
                    log.info { "The bars made ahead on a thread of their own in ${(System.nanoTime() - started) / 1_000_000} ms" }
                    caulis.post { onThisThread() }
                }
            }
        }
        barModel.showPopups()
        barModel.showOsd()
        barModel.showNotificationCards()
        showKeyHelp()
        every(1000) {
            (topBars + bottomBars).forEach(Bar::refresh)
            watchHeldFrames()
        }
        // The wall and the focused window, as the bars show them; a popup goes with its bar.
        every(200) {
            val bars = topBars + bottomBars
            bars.forEach(Bar::look)
            barModel.popup?.let { p -> if (p.bar !in bars || p.bar.place() == null || lockShown()) barModel.close() }
        }
        queue.start()
        panelLauncher.start()
        dbusEndpoint.start()
        idleInhibit.start()
        barModel.notifications.start()
        try {
            confOnStartup()()
        } catch (e: Exception) {
            log.error("on_startup", e)
        }
        // Fade in: the wallpaper once newm is shown (with reveal { }: as the dark screen begins to
        // fade, so the two go together; without it, half a second after the start as before).
        val fadeIn: () -> Unit = { animateTo({ s -> null to s.copy(backgroundOpacity = 1.0) }, NewmAnimations.resolveSeconds("startup", confBlendT())) }
        if (caulis.reveal.enabled && !caulis.revealed) caulis.onReveal { fadeIn() } else caulis.after(500) { fadeIn() }

        // The greeter: locked at once, asking for the user, when the lock panel is there (or
        // newm's own lock screen, without a lock panel program).
        if (authBackend.isGreeter()) {
            lateinit var waiting: AutoCloseable
            waiting = every(500) {
                if (panels().any { it.panel == "lock" } || Config.lookup("panels.lock.cmd") == null) {
                    waiting.close()
                    ensureLocked()
                    authBackend.initSession()
                }
            }
        }
    }

    private fun terminateNow() {
        idleInhibit.stop()
        dbusEndpoint.stop()
        barModel.notifications.stop()
        panelLauncher.stop()
        gestureProviders.forEach(GestureProvider::stop)
        queue.stop()
        caulis.run("quit")
    }

    /** Animates to what [reducer] gives, [duration] seconds (newm's own way, a straight line). */
    fun animateTo(reducer: Reducer, duration: Double, then: (() -> Unit)? = null, overlaySafe: Boolean = false) {
        queue.push(Animation(this, reducer, duration, then, overlaySafe))
    }

    /** Animates to what [reducer] gives as [animation] says (off: there at once). */
    fun animateTo(reducer: Reducer, animation: dev.caulis.core.ResolvedAnimation, then: (() -> Unit)? = null, overlaySafe: Boolean = false) {
        queue.push(Animation(this, reducer, animation.seconds, then, overlaySafe, animation.curve))
    }

    /** Animates to what [reducer] gives as the animation [name] says (see [NewmAnimations]). */
    fun animateTo(reducer: Reducer, name: String, then: (() -> Unit)? = null, overlaySafe: Boolean = false) =
        animateTo(reducer, NewmAnimations[name], then, overlaySafe)

    /** The time (seconds) and curve of the animation that started last (what its reducer starts goes along). */
    internal var runningAnimation: Pair<Double, dev.caulis.core.Curve> = 0.0 to dev.caulis.core.Curve.LINEAR

    fun update(newState: LayoutState) {
        state = newState
        damage()
        // Fullscreen left another way (the view moved, a gesture zoomed): the programs told so too,
        // or a browser stays without its tabs and address bar in an ordinary tile.
        if (toldFullscreen.isNotEmpty() || fullscreenComing.isNotEmpty()) {
            fullscreenComing.removeIf { it.destroyed }
            for (v in fullscreenComing.toList()) {
                if (newState.findViewOrNull(v)?.second?.isFullscreen() == true) {
                    fullscreenComing -= v
                    toldFullscreen += v
                }
            }
            for (v in toldFullscreen.toList()) {
                val wsState = newState.findViewOrNull(v)?.second
                if (wsState == null || !wsState.isFullscreen()) {
                    toldFullscreen -= v
                    if (!v.destroyed) v.setFullscreen(false)
                }
            }
        }
    }

    // The views told they are fullscreen (newm's fullscreen of their workspace).
    private val toldFullscreen = LinkedHashSet<View>()
    private val fullscreenComing = LinkedHashSet<View>()

    private fun allAnimates(): List<Animatable> = listOf(this) + views.values + backgrounds + topBars + bottomBars + focusBorders

    fun doFlushAnimation() = allAnimates().forEach(Animatable::flushAnimation)

    /** Every animated part starts towards [newState]. */
    internal fun animateAll(newState: LayoutState, duration: Double, curve: dev.caulis.core.Curve = dev.caulis.core.Curve.LINEAR) {
        for (a in allAnimates()) a.animate(state, newState, duration, curve)
    }

    fun finalTimeAll(): Double? = (allAnimates().filterIsInstance<Animate<*>>() + listOf(this)).mapNotNull { it.finalTime() }.maxOrNull()

    internal fun trustedUnlock() {
        if (isLocked()) {
            log.info { "Unlocked" }
            caulis.desktop.unlockInternally()
            lockAnimating = false
            panelsUi.unlocked()
            safelyEnterMode(null)
            animateTo({ s -> null to s.copy(lockPerc = 0.0, backgroundOpacity = 1.0) }, "lock")
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
            // The size it was asked for and the one it drew (they differ: it is fitted or shrunk).
            append("      asked ${requestedSize[v.window]?.let { "${it.first}x${it.second}" } ?: "-"}, drawn ${Up(v.window, false).size.let { "${it.first}x${it.second}" }}\n")
        }
    }

    fun findFocusedBox(): Pair<Workspace, DoubleArray> {
        // A dialog's place is its window's (moving from it goes from there, newm issue 140).
        val view = findFocusedView()?.let { v -> generateSequence(v) { it.parent }.last() } ?: return workspaces[0] to doubleArrayOf(0.0, 0.0, 1.0, 1.0)
        val (s, _, wsHandle) = state.findViewOrNull(view) ?: return workspaces[0] to doubleArrayOf(0.0, 0.0, 1.0, 1.0)
        val ws = workspaces.firstOrNull { it.handle == wsHandle } ?: return workspaces[0] to doubleArrayOf(0.0, 0.0, 1.0, 1.0)
        return ws to doubleArrayOf(s.i, s.j, s.w, s.h)
    }

    /**
     * newm's place_initial: where a new view of [w] x [h] tiles goes. If the view shows more
     * than the views take: at 0, 0 (the first), else beside them; else the first free place in
     * view, row by row; else right of the focused one.
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
                    if (isFree(wsState, i, j, w, h)) {
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

    // All tiles of [w] x [h] at [i], [j] free.
    private fun isFree(wsState: WorkspaceState, i: Int, j: Int, w: Int, h: Int): Boolean =
        (j until j + h).all { jp -> (i until i + w).all { ip -> wsState.isTileFree(ip, jp) } }

    /**
     * Where the new tiled [view] of [w] x [h] tiles goes on [wsState] (room made there: what
     * is in the way moves aside). A child window (see [View.childOf]) by windows { childPlacement }:
     * below its parent or right of it; any other by windows { newPlacement }.
     */
    internal fun placeNew(view: View, workspace: Workspace, wsState: WorkspaceState, w: Int, h: Int): Pair<Int, Int> {
        val tiled = tiles(workspace).filter { it !== view }
        if (tiled.isEmpty()) return placeInitial(workspace, wsState, w, h)
        // The tiled window it comes from: its parent's (a dialog's window), or the focused one.
        fun tiledOf(v: View?): ViewState? = generateSequence(v) { it.parent }.firstNotNullOfOrNull { a -> wsState.viewStates[a.handle]?.takeIf { it.isTiled && it.w > 0 } }
        val child = view.openedAsChildOf()
        if (child != null) {
            val p = tiledOf(child)
            // (Free room there already: it goes there, nothing moves; else what is in the way makes room.)
            when (confChildPlacement().takeIf { p != null }) {
                "below" -> return (pyRound(p!!.i) to pyRound(p.j + p.h)).takeIf { (i, j) -> isFree(wsState, i, j, w, h) }
                    ?.also { log.info { "Child window ${view.appId} below its parent, at ${it.first}, ${it.second} (free)" } }
                    ?: insertAt(wsState, pyRound(p.i), pyRound(p.j + p.h), w, h, columns = false).also { log.info { "Child window ${view.appId} below its parent, at ${it.first}, ${it.second}" } }
                "right" -> return (pyRound(p!!.i + p.w) to pyRound(p.j)).takeIf { (i, j) -> isFree(wsState, i, j, w, h) }
                    ?.also { log.info { "Child window ${view.appId} right of its parent, at ${it.first}, ${it.second} (free)" } }
                    ?: insertAt(wsState, pyRound(p.i + p.w), pyRound(p.j), w, h, columns = true).also { log.info { "Child window ${view.appId} right of its parent, at ${it.first}, ${it.second}" } }
                else -> {}
            }
        }
        val placement = confPlaceBesideFocused()?.let { if (it) "beside-focused" else "original" } ?: confNewPlacement()
        val anchor = tiledOf(view.focusedAtOpen) ?: tiledOf(findFocusedView()?.takeIf { it !== view }) ?: workspace.focusViewHint?.let { h0 -> wsState.viewStates[h0]?.takeIf { it.isTiled && it.w > 0 } }
        return when (placement) {
            Placement.COMPACT -> Placement.placeOnWall(wsState, anchor, w, h, except = view.handle)
            "right-of-focused" -> if (anchor == null) placeInitial(workspace, wsState, w, h)
                else insertAt(wsState, pyRound(anchor.i + anchor.w), pyRound(anchor.j), w, h, columns = true)
            "beside-focused" -> if (anchor == null) placeInitial(workspace, wsState, w, h) else {
                // The first free place right of it in its row.
                var i = pyRound(anchor.i + anchor.w)
                val j = pyRound(anchor.j)
                while (!isFree(wsState, i, j, w, h)) i += 1
                i to j
            }
            "end" -> {
                // Right of all windows, in the focused one's row (or the first row in view).
                val e = wsState.extent()
                val j = anchor?.let { pyRound(it.j) } ?: max(floor(e[1]), floor(wsState.j))
                var i = floor(e[2]) + 1
                while (!isFree(wsState, i, j, w, h)) i += 1
                i to j
            }
            "original" -> placeInitial(workspace, wsState, w, h)
            else -> {
                if (warnedPlacement.add(placement)) log.warn("windows { newPlacement = \"$placement\" } is none of ${Placement.NAMES.joinToString()}: compact")
                Placement.placeOnWall(wsState, anchor, w, h, except = view.handle)
            }
        }
    }

    private val warnedPlacement = HashSet<String>()

    /**
     * On screens (screens { }): puts the new tiled [view] on a screen of [wsState] and tiles
     * them anew; its state there is given back. A child window goes right after its window on
     * its screen (a full screen's last window onto the next screen if that has room), any other
     * by windows { newPlacement }: next to the focused one, else on a neighbour with room, else a
     * new screen after it ("compact"); after the focused one ("right-of-focused", "beside-focused"),
     * on the last screen ("end"), on the first with room ("original"). A full screen: on a new
     * one right after it.
     */
    internal fun placeOnScreen(view: View, workspace: Workspace, wsState: WorkspaceState): ViewState {
        val screens = wsState.readScreens()
        screens.forEach { s -> s.windows.remove(view.handle) }
        fun tiledOf(v: View?): Int? = generateSequence(v) { it.parent }.firstOrNull { a -> a !== view && wsState.viewStates[a.handle]?.let { it.isTiled && it.swallowed == null } == true }?.handle
        val child = view.openedAsChildOf()?.takeIf { confChildPlacement() != "over" }?.let(::tiledOf)
        val placement = if (child != null) "child" else confPlaceBesideFocused()?.let { if (it) "beside-focused" else "original" } ?: confNewPlacement()
        val anchor = child ?: tiledOf(view.focusedAtOpen) ?: tiledOf(findFocusedView()?.takeIf { it !== view })
            ?: workspace.focusViewHint?.takeIf { h -> h != view.handle && wsState.viewStates[h]?.isTiled == true }
        fun room(s: Screen) = s.windows.size < Screens.capacity(s.layout ?: Screens.defaultLayout())
        val anchorScreen = anchor?.let { a -> screens.indexOfFirst { a in it.windows } }?.takeIf { it >= 0 }
        val known = placement in Placement.NAMES || placement == "child"
        when {
            placement == "child" && anchorScreen != null -> Placement.placeChildOnScreens(screens, anchorScreen, anchor, view.handle)
            placement == Placement.COMPACT || !known ->
                Placement.placeOnScreens(screens, anchorScreen, anchor?.takeIf { anchorScreen != null }, view.handle, Screens.pageAt(wsState.i + .5, wsState.j + .5))
            placement == "original" || (anchorScreen == null && placement != "end") -> screens.firstOrNull(::room)?.windows?.add(view.handle)
                ?: screens.add(Screen(null, mutableListOf(view.handle)))
            placement == "end" -> screens.lastOrNull()?.takeIf(::room)?.windows?.add(view.handle) ?: screens.add(Screen(null, mutableListOf(view.handle)))
            else -> {
                val s = screens[anchorScreen!!]
                if (room(s)) s.windows.add(s.windows.indexOf(anchor) + 1, view.handle)
                else screens.add(anchorScreen + 1, Screen(null, mutableListOf(view.handle)))
            }
        }
        if (!known && warnedPlacement.add(placement)) {
            log.warn("windows { newPlacement = \"$placement\" } is none of ${Placement.NAMES.joinToString()}: compact")
        }
        wsState.viewStates[view.handle] = ViewState(isTiled = true, stackIdx = view.handle)
        wsState.writeScreens(screens)
        return wsState.viewState(view)
    }

    // Room for [w] x [h] tiles at [i], [j]: with [columns], every window from column [i] on
    // moves [w] right (a column put in); else every window from row [j] on moves [h] down. A
    // window reaching over that line from before stays; if it is in the way still, the place
    // moves on right until free.
    private fun insertAt(wsState: WorkspaceState, i: Int, j: Int, w: Int, h: Int, columns: Boolean): Pair<Int, Int> {
        for (s in wsState.viewStates.values) {
            if (!s.isTiled) continue
            if (columns && s.i >= i - 0.01) s.i += w
            if (!columns && s.j >= j - 0.01) s.j += h
        }
        var pi = i
        while (!isFree(wsState, pi, j, w, h)) pi += 1
        log.trace { "Room made at $pi, $j (${if (columns) "columns" else "rows"})" }
        return pi to j
    }

    /** The monitors changed: workspaces and widgets anew. */
    fun onLayoutChange() {
        val geometry = geometryNow()
        // (A monitor newm already made its workspaces and bars for — the first one, told right
        // after the start — changes nothing: the bars are not made a second time.)
        val monitorsNow = desktopOrNull()?.monitors
        if (geometry == lastGeometry && geometry != null && monitorsNow != null && widgetsMonitors.size == monitorsNow.size && widgetsMonitors.indices.all { widgetsMonitors[it] === monitorsNow[it] }) {
            log.trace { "Monitors as before: workspaces and bars kept" }
            return
        }
        lastGeometry = geometry
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
        fun pywm(keysym: String) = if (keysym.length == 1) keysym.lowercase() else keysym
        // keyboard { shortcutsOnLatinLayout } (default true): "d" for the key of "в" on the Russian
        // layout, as the first Latin layout has it; false: the layout in use's own (pywm's way).
        val own = pywm(key.layoutKeysym)
        val keysym = if (caulis.bindingsUseFirstLatinLayout) pywm(key.keysym) else own
        return onKey(key.timeMillis, key.evdevCode, key.pressed, keysym, own)
    }

    fun onKey(timeMillis: Int, keycode: Int, pressed: Boolean, keysyms: String, layoutKeysyms: String = keysyms): Boolean {
        // These are processed via onModifiers.
        if (keysyms in listOf("Super_L", "Super_R", "Alt_L", "Alt_R", "Logo_L", "Logo_R")) return false
        // The keys' help open: a key (but its own) closes it, and does nothing else.
        if (keyHelpShown && keysyms != "slash" && keysyms !in listOf("Shift_L", "Shift_R", "Control_L", "Control_R")) {
            if (pressed) keyHelpShown = false
            return true
        }
        overlay?.takeIf { it.ready }?.let { o ->
            log.trace { "...passing to overlay $o" }
            if (o.onKey(timeMillis, keycode, pressed, keysyms)) return true
        }
        return keyProcessor.onKey(pressed, keysyms, modifiers, isLocked(), layoutKeysyms)
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
        // In the overview the window under the pointer is the chosen one: its border, and what
        // Super+drag moves (else it stayed the window one came from).
        if (overlay == null && confOverviewFollow() && overviewShown()) {
            // (A window whose modal dialog has the focus already: its dialog keeps it.)
            viewUnderPointer(dx, dy)?.takeIf { v ->
                !v.isFocused() && v.window.mapped && caulis.desktop.modalDialogOf(v.window)?.let { it === caulis.desktop.focusedWindow } != true
            }?.let { v ->
                log.trace { "Overview: $v under the pointer, focused" }
                v.focus()
            }
        }
        for (g in gestureProviders) {
            val res = g.onMotion(timeMillis, dx, dy)
            if (res == 2) return true
            if (res == 1) break
        }
        overlay?.takeIf { it.ready }?.let { return it.onMotion(timeMillis, dx, dy) }
        return false
    }

    // resize { onBorder }: on screens, a press in the room right beside a window (no window
    // under the pointer, within borderGrabSize of it) drags that window's border.
    private fun resizeAtBorder(button: Int): Boolean {
        if (!Screens.on() || !ScreenResize.onBorder() || button != ScreenResize.borderButton() || overviewShown()) return false
        val d = caulis.desktop
        val x = d.pointerX
        val y = d.pointerY
        if (d.windowAt(x, y) != null) return false
        val g = ScreenResize.borderGrab()
        for (v in tiles(activeWorkspace())) {
            val r = shownArea(v) ?: continue
            if (x < r.x - g || x > r.x + r.width + g || y < r.y - g || y > r.y + r.height + g) continue
            val ex = when { x < r.x -> -1; x > r.x + r.width -> 1; else -> 0 }
            val ey = when { y < r.y -> -1; y > r.y + r.height -> 1; else -> 0 }
            if (ex == 0 && ey == 0) continue
            log.info { "Border of ${v.appId} grabbed" }
            if (!v.isFocused()) v.focus()
            enterOverlay(ScreenResizeOverlay(this, v, ex, ey, button))
            return true
        }
        return false
    }

    /** The window drawn under the pointer now (also where windows take no clicks: the overview). */
    fun viewUnderPointer(dx: Double = 0.0, dy: Double = 0.0): View? {
        val d = caulis.desktop
        // (A motion is heard before the pointer moves: [dx], [dy] say where it goes.)
        val x = d.pointerX + dx
        val y = d.pointerY + dy
        return views.values.lastOrNull { v ->
            d.drawnNow(v.window) && d.shownRect(v.window)?.let { r -> x >= r.x && x < r.right && y >= r.y && y < r.bottom } == true
        }
    }

    /** The active workspace is seen from afar (the overview). */
    fun overviewShown(): Boolean = runCatching { state.workspaceState(activeWorkspace()).isInOverview() }.getOrDefault(false)

    // In the overview a click on a window chooses it: the overview ends there (newm let it
    // click inside the window, small as it is). overview { choose = false }: not.
    private fun chooseInOverview(button: Int, pressed: Boolean): Boolean {
        if (confOverviewInput() || !overviewShown() || overlay != null) return false
        if (button != 272) return true // (no other button inside the windows either)
        if (pressed) return true
        if (!confOverviewChoose()) return true
        val d = caulis.desktop
        val view = views.values.lastOrNull { v ->
            d.drawnNow(v.window) && d.shownRect(v.window)?.let { r -> d.pointerX >= r.x && d.pointerX < r.right && d.pointerY >= r.y && d.pointerY < r.bottom } == true
        } ?: return true
        log.info { "Chosen in the overview: $view" }
        animateTo({ s ->
            view.focus()
            null to s.withOverviewSet(false, activeWorkspace(), view)
        }, "overview")
        return true
    }

    fun onButton(timeMillis: Int, button: Int, pressed: Boolean): Boolean {
        if (isLocked()) return false
        // A click while Super is held: no "Super alone" (the overview) when it is let go.
        if (pressed) keyProcessor.onOtherAction()
        for (g in gestureProviders) {
            val res = g.onButton(timeMillis, button, pressed)
            if (res == 2) return true
            if (res == 1) break
        }
        if (overlay == null && chooseInOverview(button, pressed)) return true
        if (overlay == null && pressed && resizeAtBorder(button)) return true
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

    /** A gesture as a provider gives it (for tests: fingers without a touchpad). */
    internal fun gesture(gesture: NewmGesture): Boolean = gestureProviderCallback(gesture)

    private fun gestureProviderCallback(gesture: NewmGesture): Boolean {
        if (isLocked()) return false
        log.trace { "Gesture $gesture..." }
        val o = overlay
        if (o != null && o.ready) {
            log.trace { "...passing to overlay $o" }
            return o.onGesture(gesture)
        }
        if (o != null) return false

        // gestures { on("swipe-4", "down") { ... } }: the configuration's own first, for the way the fingers go.
        val own = ownGestureBindings(gesture.kind)
        if (own.isNotEmpty()) {
            log.trace { "...own binding (${own.size} for ${gesture.kind})" }
            GestureChooser(gesture, own)
            return true
        }
        return builtinGesture(gesture)
    }

    // gestures.on: the configuration's own gestures of [kind] whose modifier is held. (Unheld
    // "move-1" and "swipe-2" are the mouse and two-finger scrolling: never taken.)
    private fun ownGestureBindings(kind: String): List<Map<String, Any?>> = confGestureOn().filter { b ->
        val modifier = b["modifier"] as? String
        b["kind"] == kind && hasModifier(modifier) && !(modifier == null && (kind == "move-1" || kind == "swipe-2"))
    }

    // newm's own gestures (gesture_bindings): moving and resizing, swiping, zooming, the launcher.
    private fun builtinGesture(gesture: NewmGesture): Boolean {
        fun enter(overlay: Overlay): Boolean {
            overlay.onGesture(gesture)
            enterOverlay(overlay)
            return true
        }
        val moveResize = confGestureBindingMoveResize()
        if (hasModifier(moveResize[0]) && (gesture.kind == moveResize[1] || gesture.kind == moveResize[2])) {
            log.trace { "...MoveResize" }
            // A window of a special workspace under the pointer: dragged there, out onto the wall maybe.
            if (gesture.kind == moveResize[1] && !overviewShown()) specials.dragOverlay()?.let { return enter(it) }
            // The window under the pointer (as Hyprland's Super+drag), focused; else the focused one.
            // (In the overview windows take no clicks: the one drawn under the pointer there.)
            val under = if (overviewShown()) viewUnderPointer() else caulis.desktop.let { d -> d.windowAt(d.pointerX, d.pointerY) }?.let(::viewOf)
            // (In the overview windows are not resized: they are seen from afar.)
            if (gesture.kind == moveResize[2] && overviewShown()) {
                log.info { "Resizing is off in the overview" }
                return true
            }
            val view = under?.also { if (!it.isFocused()) it.focus() } ?: findFocusedView()
            if (view != null && view.isFloat(state)) return enter(MoveResizeFloatingOverlay(this, view))
            // On screens: the window's edges by the quarter grabbed, within its screen.
            if (view != null && view.isTiled(state) && Screens.on() && gesture.kind == moveResize[2]) {
                val d = caulis.desktop
                val (ex, ey) = shownArea(view)?.let { grabbedEdges(it, d.pointerX, d.pointerY) } ?: (1 to 1)
                return enter(ScreenResizeOverlay(this, view, ex, ey))
            }
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

    /**
     * A gesture of gestures.on: once the fingers have gone far enough one way, the binding for
     * that way runs (once); a way no binding has goes on as newm's own gesture of that kind
     * (zooming, ...). Ended before: the binding for any way, if there is one.
     */
    private inner class GestureChooser(private val gesture: NewmGesture, private val bindings: List<Map<String, Any?>>) {
        private var decided = false

        init {
            gesture.listener(GestureListener(::update, ::end))
        }

        private fun update(values: Map<String, Double>) {
            if (decided) return
            val dx = values["delta_x"] ?: 0.0
            val dy = values["delta_y"] ?: 0.0
            // (A twentieth of a screen: not a twitch of the fingers.)
            if (maxOf(Math.abs(dx), Math.abs(dy)) < 0.05) return
            decided = true
            val way = if (Math.abs(dx) > Math.abs(dy)) (if (dx > 0) "right" else "left") else (if (dy > 0) "down" else "up")
            val binding = bindings.firstOrNull { it["direction"] == way } ?: bindings.firstOrNull { it["direction"] == null }
            if (binding != null) run(binding, way)
            else {
                log.trace { "Gesture ${gesture.kind} $way: no own binding, newm's own" }
                builtinGesture(gesture)
            }
        }

        private fun end() {
            if (decided) return
            decided = true
            bindings.firstOrNull { it["direction"] == null }?.let { run(it, "any way") }
        }

        private fun run(binding: Map<String, Any?>, way: String) {
            log.info { "Gesture ${gesture.kind} $way: the configuration's own" }
            @Suppress("UNCHECKED_CAST")
            val action = binding["action"] as? (Layout) -> Unit ?: return
            try {
                action(this@Layout)
            } catch (e: Exception) {
                log.warn("The gesture ${gesture.kind} $way failed: $e", e)
            }
        }
    }

    // --- idle, sleep -----------------------------------------------------------------------------

    // Since when nobody has been there (null: someone is).
    private var idleSince: Double? = null
    // What of the idle stages happened since someone was last there ("idle", "idle-lock", ..., "after:<n>").
    private val idleDone = HashSet<String>()
    private var screensOff = false

    // A stage's seconds: its own setting (energy.dim_after, ...), else idle_times' [index]; 0: never.
    private fun idleSeconds(own: Any?, index: Int): Double =
        (own as? Number)?.toDouble() ?: confIdleTimes().getOrNull(index)?.toDouble() ?: 0.0

    // Each second: what keeps the computer awake is looked at (the bar shows it); while newm's own
    // keep-awake (caffeine, newm cmd inhibit-idle) holds, nobody-there counts from now — when it
    // ends, the screen does not dim or lock at once for the time that passed meanwhile.
    private fun idleTick() {
        idleInhibit.tick()
        // A moving wallpaper stands still while nobody is there, and goes slower on battery.
        ShaderWallpapers.tick(this, idleSince?.let { now() - it } ?: 0.0, "idle" in idleDone)
        val since = idleSince ?: return
        if (caffeineOn || idleInhibitUser) {
            idleSince = now()
            // (Dimmed or dark already when it began: lit again, as when someone is back.)
            if (idleDone.any { !it.startsWith("after:") } || screensOff) onIdle(0.0, false)
            return
        }
        onIdle(now() - since, false)
    }

    /**
     * [elapsed] seconds without input (0: someone is back). Each stage (dim_after: "idle",
     * lock_after: "idle-lock" and locked, 5 s before suspend_after: "idle-presuspend",
     * suspend_after: "idle-suspend" and suspended, and energy { after(n) { } }) happens once until
     * someone is back: newm called them again and again, locking a locked screen over and over
     * (newm issue 98).
     */
    fun onIdle(elapsed: Double, idleInhibited: Boolean) {
        if (caffeineUntil != null && !caffeineOn) caffeine(null)
        val inhibited = idleInhibited || idleInhibitUser || caffeineOn
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
            if (idleDone.any { !it.startsWith("after:") }) confIdleCallback()("active")
            idleDone.clear()
            return
        }
        if (inhibited) return
        val dim = idleSeconds(confDimAfter(), 0)
        val lock = idleSeconds(confLockAfter(), 1)
        val suspend = idleSeconds(confSuspendAfter(), 2)
        // Each stage that is due, in the order of their times.
        val stages = ArrayList<Triple<Double, String, () -> Unit>>()
        if (dim > 0) stages += Triple(dim, "idle") { confIdleCallback()("idle") }
        if (lock > 0) stages += Triple(lock, "idle-lock") {
            confIdleCallback()("idle-lock")
            ensureLocked()
        }
        if (suspend > 0) {
            stages += Triple((suspend - 5.0).coerceAtLeast(0.0), "idle-presuspend") { confIdleCallback()("idle-presuspend") }
            stages += Triple(suspend, "idle-suspend") {
                confIdleCallback()("idle-suspend")
                caulis.launch(confSuspendCommand())
            }
        }
        for ((i, a) in confIdleActions().withIndex()) {
            val seconds = (a["seconds"] as? Number)?.toDouble()?.takeIf { it > 0 } ?: continue
            @Suppress("UNCHECKED_CAST")
            val action = a["action"] as? (Layout) -> Unit ?: continue
            stages += Triple(seconds, "after:$i") { action(this) }
        }
        for ((at, name, run) in stages.sortedBy { it.first }) {
            if (elapsed <= at || name in idleDone) continue
            idleDone += name
            log.info { "Idle for ${elapsed.toInt()} s: $name" }
            try {
                run()
            } catch (e: Exception) {
                log.warn("The idle stage $name failed: $e", e)
            }
        }
    }

    fun onSleep() {
        confIdleCallback()("sleep")
        // (At once, no sliding in; Caulis then waits until it shows before the computer sleeps.)
        if (confLockOnWakeup()) ensureLocked(anim = false)
    }

    // True while the lock screen shows: a locker program's lock is in force, or newm's own covers the screen.
    private fun lockScreenShows(): Boolean {
        val d = desktopOrNull() ?: return false
        return if (!confLockProgram().isNullOrBlank()) d.locked else d.lockedInternally && layoutDown.lockPerc >= 0.99
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
        focusBorders.damage() // (no border while a window is moved or resized)
        overlay.init()
        // What a gesture does, said while it runs (they are not known by heart at first).
        // (Said with the gestures the configuration binds: gesture_bindings.)
        if (confGestureHints()) when (overlay) {
            is SwipeOverlay -> confGestureBindingSwipe().let { b ->
                barModel.hintPill("%s: moving over the wall — let go to stay".replace("%s", gestureWords(b.getOrNull(0), b.getOrNull(1))))
            }
            is SwipeToZoomOverlay -> confGestureBindingSwipeToZoom().let { b ->
                barModel.hintPill("%s: up shows more windows, down fewer".replace("%s", gestureWords(b.getOrNull(0), b.getOrNull(1))))
            }
            is MoveResizeOverlay, is MoveResizeFloatingOverlay -> confGestureBindingMoveResize().getOrNull(2).let { resize ->
                barModel.hintPill(if (resize == null) "Moving the window" else "Moving the window — %s resize it".replace("%s", gestureWords(null, resize)))
            }
            else -> {}
        }
    }

    // The overlay going away now (its exit animation runs): it is destroyed once, not again.
    private var destroyingOverlay: Overlay? = null

    fun forceCloseOverlay() {
        val o = overlay ?: return
        log.trace { "Force-closing $o" }
        try {
            if (o !== destroyingOverlay) {
                destroyingOverlay = o
                o.destroy()
            }
        } finally {
            overlay = null
        }
    }

    fun exitOverlay() {
        log.trace { "Going to exit overlay..." }
        val o = overlay ?: return log.trace { "...aborted" }
        if (o === destroyingOverlay) return log.trace { "...already going" }
        log.trace { "...destroy" }
        destroyingOverlay = o
        o.destroy()
    }

    /** [o] failed to start: it is gone, whatever it began (its timers) ended. */
    fun overlayFailed(o: Overlay) {
        try {
            o.preDestroy()
        } catch (e: Exception) {
            log.error("Cleaning up a failed overlay", e)
        }
        if (overlay === o) overlay = null
        if (destroyingOverlay === o) destroyingOverlay = null
        focusBorders.damage()
        resetGesture()
    }

    fun onOverlayDestroyed() {
        log.trace { "Overlay destroyed" }
        destroyingOverlay = null
        queue.onOverlayDestroyed()
        overlay = null
        focusBorders.damage()
        log.trace { "Resetting gesture" }
        resetGesture()
    }

    // --- views going -------------------------------------------------------------------------------

    fun destroyView(view: View) {
        log.info { "Destroying view $view" }
        val window = view.window
        viewsByWindow.remove(window)
        requestedSize.remove(window)
        // (Caulis gave the focus to another window already: it had it if it had it last.)
        // (Gone into a special workspace: it keeps the focus there, no other view takes it.)
        val justLost = lostFocus?.let { (w, at) -> w === window && now() - at < 0.5 } == true
        val wasFocused = (view.isFocused() || view === closingFocused || view === focusedView || justLost) && caulis.desktop.scratchpadOf(window) == null
        if (justLost) lostFocus = null
        if (closingFocused === view) closingFocused = null
        if (focusedView === view) focusedView = null
        val (s, wsState, closedWs) = state.findViewOrNull(view) ?: run {
            // (A view destroyed before it was shown.)
            views.remove(view.handle)
            view.releaseDamage()
            return
        }

        var bestView: Int? = null
        if (wasFocused) {
            log.trace { "Finding view to focus since $view closes..." }
            var p = view.parent
            while (p != null && !p.isTiled(state) && p.parent != null) p = p.parent
            if (p != null) bestView = p.handle
            // A child window (a parent of its own, or the same program's window focused when it
            // came): back to that one, or to the window focused before it opened.
            if (confReturnFocusToParent()) view.returnFocusTo()?.let { back ->
                log.info { "Focus back to ${back.appId} (${back.handle}) as its child window closes" }
                bestView = back.handle
            }

            if (bestView == null) {
                var bestScore = 1000.0
                for ((k, other) in wsState.views) {
                    if (!other.isTiled || k == view.handle || (other.swallowed != null && other.swallowed != view.handle)) continue
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

        // On screens: the window's screen tiled anew, taking windows of the next ones (screens { pullBack }).
        // (Not for a swallowed one: hidden behind its swallower, nothing visible closed.)
        val closedPage = s.page.takeIf { s.isTiled && s.swallowed == null && Screens.on() }
        fun LayoutState.withoutIt(): LayoutState {
            val r = unswallowing(view).withoutViewState(view)
            if (closedPage != null) r.workspaceStates[closedWs]?.pullBackScreens(closedPage)
            return r
        }
        val bv = bestView?.takeIf { it in views }
        if (bv != null) {
            log.trace { "Found view to focus: ${views[bv]}" }
            animateTo({ st ->
                val next = views[bv]
                val result = if (next != null) {
                    val r = if (closedPage != null) st.withoutIt().constrain().focusingView(next) else st.unswallowing(view).focusingView(next).withoutViewState(view).constrain()
                    focusBorders.updateFocus(next, null to r)
                    next.focus()
                    r
                } else {
                    // (The view may be gone meanwhile.)
                    focusBorders.unfocus()
                    st.withoutIt().constrain()
                }
                null to result
            }, "windowsOut", { views.remove(view.handle); view.releaseDamage() })
        } else {
            if (wasFocused) focusBorders.unfocus()
            animateTo({ st -> null to st.withoutIt().constrain() }, "windowsOut", { views.remove(view.handle); view.releaseDamage() })
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
        // (Drawn above the lock screen, though they get no input: not while it is locked.)
        if (cmd in SHOWN_ABOVE_THE_LOCK && (isLocked() || desktopOrNull()?.locked == true)) {
            log.info { "newm cmd $cmd while the screen is locked: refused" }
            return "The screen is locked"
        }
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
            // Every key bound now, as it is written in words ("Super+Shift+H  Move the window left").
            "keys" -> confKeyHelp().ifEmpty { confKeyBindings()(this).map { it.first to "" } }.let { all ->
                val rows = all.map { (combo, words) -> KeyNotation.readable(combo) to newm.bar.tr(words) }
                val width = rows.maxOfOrNull { it.first.length } ?: 0
                rows.joinToString("\n") { (k, w) -> if (w.isBlank()) k else k.padEnd(width + 2) + w }.ifEmpty { "no keys" }
            }
            "inhibit-idle" -> {
                idleInhibitUser = true
                null
            }
            // `newm cmd displays extend|mirror|laptop|other`: how the laptop's screen and a monitor are used (newm issue 169).
            "displays" -> {
                val word = arg?.trim()?.lowercase().orEmpty().let { if (it == "other") "external" else it }
                val mode = dev.caulis.wm.DisplayMode.of(word) ?: return "displays extend | mirror | laptop | other"
                caulis.useDisplays(mode)
                null
            }
            // `newm cmd special [name]`: shows or hides a special workspace (Hyprland's togglespecialworkspace).
            "special" -> {
                toggleSpecialWorkspace(arg?.trim()?.takeIf { it.isNotEmpty() } ?: "special")
                null
            }
            // `newm cmd scratchpad <name>`: shows or hides one of caulis.scratchpad(...).
            "scratchpad" -> {
                arg?.trim()?.takeIf { it.isNotEmpty() }?.let(::scratchpad)
                null
            }
            // full-redraw [on | off]: every frame drawn whole (to find out whether a flicker
            // comes from drawing only what changed).
            // log <area> <0..3> | log <area> reset: one area of the log in more detail, now (render, input, ...).
            "log" -> {
                val w = arg.orEmpty().trim().split(Regex("\\s+"))
                // (Caulis' areas, and newm's own: bar, sound, network, bluetooth, screenshot, auth, ...)
                val area = w.getOrNull(0)?.takeIf { it.matches(Regex("[a-z][a-z-]*")) }
                    ?: return "log <area> <0..3>; Caulis' areas: ${dev.caulis.log.Log.AREAS.keys.joinToString()}; newm's: layout, view, state, gestures, keys, bar, tray, sound, network, bluetooth, power, screenshot, clipboard, launcher, panels, auth, session, config"
                when (val level = w.getOrNull(1)) {
                    "reset" -> dev.caulis.log.Log.clearLevel(area)
                    else -> dev.caulis.log.Log.setLevel(area, level?.toIntOrNull()?.coerceIn(0, 3) ?: return "a level is 0, 1, 2 or 3")
                }
                log.info { "The log of $area: ${w.getOrNull(1)}" }
                null
            }
            "full-redraw" -> desktopOrNull()?.let { d ->
                d.fullRedraw = when (arg?.trim()) { "on" -> true; "off" -> false; else -> !d.fullRedraw }
                if (d.fullRedraw) "on" else "off"
            }
            "caffeine" -> {
                // caffeine [minutes | on | off]
                when (val a = arg?.trim()?.ifEmpty { null }) {
                    null -> caffeine(if (caffeineOn) null else 0)
                    "off" -> caffeine(null)
                    "on" -> caffeine(0)
                    else -> a.toIntOrNull()?.let { caffeine(it) }
                }
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
            // The programs of autostart { } and how each is.
            "autostart" -> caulis.autostartStatus().joinToString("\n").ifEmpty { "no program started with the session" }
            // The startup sound now (to try it), or one of the presets: `newm cmd play-startup-sound glass`.
            "play-startup-sound" -> try {
                caulis.playStartupSound(arg?.trim()?.lowercase()?.ifEmpty { null })
                null
            } catch (e: IllegalArgumentException) {
                e.message
            }
            // The windows left out when the session came back (more than session.max_windows).
            "restore-rest" -> {
                val n = session.restLeft()
                restoreRest()
                if (n == 0) "nothing left" else "$n programs"
            }
            "close-launcher" -> {
                if (overlay is LauncherOverlay) exitOverlay()
                null
            }
            "bar" -> barCommand(arg.orEmpty())
            // `newm cmd colors [wallpaper[:style] | nord | #RRGGBB[:style] | caulis-palette:…]`: the colours, or what they are now.
            "colors" -> {
                val choice = arg?.trim().orEmpty()
                if (choice.isNotEmpty()) {
                    newm.bar.NewmColors.choose(choice)
                    barModel.refreshPalette()
                }
                barModel.palette?.code ?: "nord (the classic colours)"
            }
            // For newm's own tests only (NEWM_TEST_INPUT=1): the pointer moved, clicked, scrolled.
            "test-pointer" -> if (System.getenv("NEWM_TEST_INPUT") == "1") testPointer(arg.orEmpty()) else "Disabled"
            // "56+42" presses Alt and Shift together, then lets them go (evdev codes).
            "test-card" -> if (System.getenv("NEWM_TEST_INPUT") == "1") {
                // test-card wifi|wired|bluetooth on|off [name]
                val w = arg.orEmpty().trim().split(Regex("\\s+"), limit = 3)
                val kind = newm.bar.ConnectionEvent.Kind.valueOf(w.getOrElse(0) { "wifi" }.uppercase())
                barModel.connectionChanged(newm.bar.ConnectionEvent(kind, w.getOrNull(1) != "off", w.getOrNull(2), "audio-headphones"))
                null
            } else "NEWM_TEST_INPUT=1 only"
            "test-ask-password" -> if (System.getenv("NEWM_TEST_INPUT") == "1") {
                barModel.askPassword(newm.bar.WifiNetwork(arg ?: "Test", 70, true, "/", false, false))
                null
            } else "NEWM_TEST_INPUT=1 only"
            // `newm cmd test-key-raw <evdev code> down|up`: one key, held or let go (tests only).
            "test-key-raw" -> if (System.getenv("NEWM_TEST_INPUT") == "1") {
                val w = arg.orEmpty().trim().split(Regex("\\s+"))
                w.getOrNull(0)?.toIntOrNull()?.let { caulis.simulateKey(it, w.getOrNull(1) != "up") }
                null
            } else "Disabled"
            "test-keys" -> if (System.getenv("NEWM_TEST_INPUT") == "1") {
                val codes = arg.orEmpty().trim().split('+').mapNotNull { it.trim().toIntOrNull() }
                codes.forEach { caulis.simulateKey(it, true) }
                codes.reversed().forEach { caulis.simulateKey(it, false) }
                null
            } else "Disabled"
            "restart" -> if (isLocked() || desktopOrNull()?.locked == true) {
                // (The start script starts newm again, and the new newm is not locked: a restart
                // now would unlock the screen without a password — any program could ask for it.)
                log.warn("newm cmd restart while the screen is locked: refused (unlock first)")
                "The screen is locked: unlock it first, then restart"
            } else {
                // `restart keep`: the windows open now open again after it, each in its place.
                val keep = arg?.trim()?.removePrefix("--") in setOf("keep", "restore", "windows", "session")
                if (keep) session.save() else session.forgetForRestart()
                // For dev/newm-dev: newm ends, and the script starts it again (a new build).
                restartRequested = true
                terminate()
                null
            }
            "clean" -> {
                animateTo({ s -> null to s.copy().clean(views.keys.toList()) }, "windowsMove")
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
            // (As a mouse moves: by steps, through onPointerMotion.)
            "move" -> caulis.desktop.let { d -> caulis.simulateMotion(x - d.pointerX, y - d.pointerY) }
            "scroll" -> {
                caulis.simulatePointer(x, y)
                caulis.simulateScroll(w.getOrNull(3)?.toIntOrNull() ?: 1)
            }
            // (Held and let go apart: a drag.)
            "left-down", "left-up", "right-down", "right-up", "middle-down", "middle-up" -> {
                caulis.simulatePointer(x, y)
                val button = when (w[2].substringBefore('-')) { "right" -> 273; "middle" -> 274; else -> 272 }
                caulis.simulateButton(button, w[2].endsWith("-down"))
            }
            else -> caulis.simulatePointer(x, y, when (w[2]) { "right" -> 273; "middle" -> 274; else -> 272 })
        }
        return null
    }

    // The monitor with the pointer.
    private fun activeMonitor(): Monitor? = desktopOrNull()?.let { d -> d.monitors.firstOrNull { m -> d.pointerX >= m.area.x && d.pointerX < m.area.right && d.pointerY >= m.area.y && d.pointerY < m.area.bottom } }

    /** The windows left out when the session came back (more than session { maxWindows }): opened now. */
    fun restoreRest() = session.restoreRest()

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

    /** Every window's view now (panels too). */
    internal fun viewsNow(): List<View> = views.values.toList()

    /** The windows kept over a restart. */
    internal val session by lazy { SessionKeeper(this) }

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
            queue.wake() // (every_frame may be configured now)
            confOnReconfigure()()
        }
    }

    fun ensureLocked(anim: Boolean = true, dim: Boolean = false) {
        // What is newm's own on top goes first: the clipboard's history, the launcher (any
        // overlay), the keys' help, the bar's popup.
        clipboardHistory.close()
        if (overlay != null) exitOverlay()
        keyHelpShown = false
        barModel.close()
        fun focusLock() {
            lockAnimating = false
            // Meanwhile no keys of newm's or Caulis's but the laptop's (XF86) ones.
            safelyEnterMode(LOCKED_MODE)
            val lockScreen = panels().filter { it.panel == "lock" }
            if (lockScreen.isNotEmpty()) lockScreen[0].focus() else panelsUi.locked()
        }
        // A locker of one's own (swaylock, gtklock: ext-session-lock), which Caulis shows (newm issue 154).
        confLockProgram()?.takeIf { it.isNotBlank() }?.let { program ->
            // (Started once: asked again before it locked — the lock before sleep right after Super+L — it is not started twice.)
            val now = System.currentTimeMillis()
            if (!caulis.desktop.locked && now - lockProgramStarted > LOCK_PROGRAM_PATIENCE_MS) {
                lockProgramStarted = now
                log.info { "Locking with $program" }
                caulis.launch(program)
            }
            if (dim) confIdleCallback()("lock")
            return
        }
        // Locked already: nothing more (a second lock asked for the password again, newm issue 98).
        if (caulis.desktop.lockedInternally && (state.lockPerc >= 1.0 || lockAnimating)) {
            if (dim) confIdleCallback()("lock")
            return
        }
        // Locked at once for Caulis (keys only to the lock screen, no window takes the focus,
        // nothing else clickable); the lock screen slides in after.
        if (!caulis.desktop.lockedInternally) {
            lockSession()
            authBackend.lock()
        }
        lockAnimating = true
        animateTo({ s -> (if (anim) null else s.copy(lockPerc = 1.0, backgroundOpacity = 0.5)) to s.copy(lockPerc = 1.0, backgroundOpacity = 0.5) }, "lock", ::focusLock)
        if (dim) confIdleCallback()("lock")
    }

    // Tells Caulis the session is locked by newm's lock screen (or the lock panel program's window).
    private fun lockSession() {
        val screen = panelsUi.lockScreenWidget() ?: return log.error("No lock screen to lock with")
        var panelPid = panelLauncher.pidOf("lock")
        log.info { "Locked${if (panelPid != null) " (the lock panel program's window takes the password)" else ""}" }
        // (Programs' panels, menus and X11's unmanaged windows: hidden too unless lock { showPanels }.)
        caulis.desktop.panelsShowWhileLocked = confShowPanels()
        caulis.desktop.lockInternally(screen, allowSurface = { surface ->
            // (Only the lock panel program's own windows: any other program stays behind the lock.
            // Its process is asked for each time: started again while locked, the new one's window
            // takes the password; its old number, used again by another program, does not.)
            val now = panelLauncher.pidOf("lock")
            if (now != panelPid) {
                log.info { if (now != null) "The lock panel program is running again (another process): its window takes the password" else "The lock panel program is gone: newm's own lock screen takes the password" }
                panelPid = now
            }
            now != null && runCatching { surface.resource.client.pid.toLong() }.getOrNull()
                ?.let { isInProcessTree(it, now, panelLauncher.startTimeOf("lock"), ::parentPid, ::processStartTime) } == true
        })
    }

    // The lock animation is under way (asked again meanwhile: not asked twice).
    private var lockAnimating = false
    // When the configuration's locker program was last started (milliseconds).
    private var lockProgramStarted = 0L

    fun terminate() {
        // session { restoreAtLogin }: the windows kept for the next login (`restart keep` kept them already).
        if (!restartRequested && session.keepsForLogin) session.save(reason = "login")
        animateTo({ s -> s.copy(final = true) to s.copy(final = true, backgroundOpacity = 0.0) }, NewmAnimations.resolveSeconds("quit", confBlendT()), ::terminateNow)
    }

    // --- 3. global or workspace state, the viewpoint ------------------------------------------------------

    /** The launcher, or (open already) away with it. */
    fun enterLauncherOverlay() {
        if (overlay is LauncherOverlay) exitOverlay() else enterOverlay(LauncherOverlay(this))
    }

    fun toggleOverview(onlyActiveWorkspace: Boolean = false) {
        animateTo({ s ->
            val overview = if (onlyActiveWorkspace) !s.workspaceState(activeWorkspace()).isInOverview() else !s.allInOverview()
            // Leaving it: the window under the pointer is the one chosen (Super, a look, the
            // pointer on another, Super again), else the focused one.
            val focused = if (!overview) (viewUnderPointer()?.also { if (!it.isFocused()) it.focus() } ?: findFocusedView()) else null
            null to s.withOverviewSet(overview, if (onlyActiveWorkspace) activeWorkspace() else null, focused)
        }, "overview")
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

            // Told to the programs of that workspace's tiles, only when it changes.
            fun tell(on: Boolean) {
                if (confSendFullscreenToViews()) for (v in tiles(ws)) {
                    v.setFullscreen(on)
                    // (Counted once the layout is fullscreen: the animation there is still to come.)
                    if (on) fullscreenComing += v else { fullscreenComing -= v; toldFullscreen -= v }
                }
            }
            when {
                fs -> {
                    tell(false)
                    null to s.settingWorkspaceState(ws, wsState.withoutFullscreen())
                }
                view != null -> {
                    tell(true)
                    null to s.settingWorkspaceState(ws, wsState.withFullscreen(view))
                }
                else -> null to null
            }
        }, "fullscreen")
    }

    fun basicMove(deltaI: Int, deltaJ: Int) {
        val ws = activeWorkspace()
        animateTo({ s ->
            null to s.replacingWorkspaceState(ws) { copy(i = i + deltaI, j = j + deltaJ) }
        }, "camera")
    }

    /** Zooms so the focused window fills the screen: its tiles, one by one (the view as big as the window). */
    fun fitFocusedView() {
        val view = findFocusedView() ?: return
        animateTo({ s ->
            val (vs, _, wsHandle) = s.findViewOrNull(view) ?: return@animateTo null to s
            val ws = workspaces.firstOrNull { it.handle == wsHandle }
            if (!vs.isTiled || ws == null) return@animateTo null to s
            // (On screens: its screen, whole.)
            if (Screens.on() && vs.page >= 0) return@animateTo null to s.replacingWorkspaceState(ws) { copy(size = 1.0).focusingScreen(vs.page) }
            val size = max(1.0, max(vs.w, vs.h))
            null to s.replacingWorkspaceState(ws) { copy(size = size, i = vs.i - (size - vs.w) / 2, j = vs.j - (size - vs.h) / 2) }
        }, "camera")
    }

    /**
     * Two windows on one screen: the focused one and its neighbour in its row (right, else
     * left) become halves of the screen — both two tiles tall, seen two by two. Again: back to
     * one tile each, the focused one filling the screen.
     */
    /** The key whose action runs now (newm's notation, "L-s"), or null (a command, the bar, a gesture). */
    internal var runningKey: String? = null
        private set

    fun sideBySide() {
        // (On screens windows are side by side already: the screen's next layout instead.)
        if (Screens.on()) return nextScreenLayout()
        // (The key that asked, for the words if there is no neighbour.)
        val key = runningKey
        val view = findFocusedView() ?: return
        animateTo({ s0 ->
            val s = s0.copy()
            val (fs, wsState, wsHandle) = s.findViewOrNull(view) ?: return@animateTo null to s0
            val ws = workspaces.firstOrNull { it.handle == wsHandle } ?: return@animateTo null to s0
            if (!fs.isTiled || wsState.isFullscreen()) return@animateTo null to s0
            val u = 1.0
            fun near(a: Double, b: Double) = Math.abs(a - b) < 0.01
            // The window right beside it in its row (its right neighbour, else its left one).
            val row = wsState.viewStates.entries.filter { (h, v) -> h != view.handle && v.isTiled && near(v.j, fs.j) }
            val other = row.firstOrNull { near(it.value.i, fs.i + fs.w) } ?: row.firstOrNull { near(it.value.i + it.value.w, fs.i) }
            if (other == null) {
                val keyWords = key?.let { k -> keyNames(k).joinToString(", ") { it.joinToString("+") } }
                caulis.notify(
                    if (keyWords != null) newm.bar.tr("Open a second window first: %s puts it beside this one.").replace("%s", keyWords)
                    else newm.bar.tr("Open a second window first: then the two go side by side."),
                    title = "newm",
                )
                return@animateTo null to s0
            }
            val os = other.value
            if (near(fs.w, u) && near(os.w, u) && near(fs.h, 2 * u) && near(os.h, 2 * u)) {
                // Apart again: one tile each, the focused one on the screen.
                fs.h = u
                os.h = u
                val next = s.replacingWorkspaceState(ws) { copy(size = u, i = fs.i, j = fs.j) }
                next.constrain()
                return@animateTo null to next
            }
            // The rows below make room, then both are one tile wide and two tall, side by side.
            val fj = fs.j
            for ((h, v) in wsState.viewStates) {
                if (h == view.handle || h == other.key || !v.isTiled) continue
                if (v.j >= fj + minOf(fs.h, os.h) - 0.01) v.j += 2 * u - minOf(fs.h, os.h)
            }
            val left = minOf(fs.i, os.i)
            fs.w = u
            fs.h = 2 * u
            os.w = u
            os.h = 2 * u
            os.j = fj
            if (fs.i < os.i) os.i = fs.i + u else fs.i = os.i + u
            val next = s.replacingWorkspaceState(ws) { copy(size = 2 * u, i = left, j = fj) }
            log.info { "Side by side: ${view.appId} and ${views[other.key]?.appId} (a tile: $u)" }
            null to next.constrain()
        }, "windowsMove")
    }

    // Screenshots (made when the compositor runs).
    private var shots: Shots? = null

    private fun shotsNow(): Shots = shots ?: Shots(this).also { shots = it }.also { it.apply() }

    /** A screenshot: the chooser (a part of the screen first, as Super+Shift+S on Windows). */
    fun screenshot() = shotsNow().choose()

    /** A screenshot of the screen with the pointer, at once (saved and copied). */
    fun screenshotScreen() = shotsNow().screen()

    /** A screenshot of the focused window, at once. */
    fun screenshotWindow() = shotsNow().window()

    /**
     * Shows the scratchpad [name] (one of caulis.scratchpad(...) of the configuration) above
     * the wall, or hides it; its program is started on the first press (newm issue 70).
     */
    fun scratchpad(name: String) = caulis.toggleScratchpad(name)

    /**
     * Shows the special workspace [name] (Hyprland's togglespecialworkspace; "special" without
     * a name) above the wall on the focused monitor, or hides it.
     */
    fun toggleSpecialWorkspace(name: String = "special") {
        // In the overview: it ends, and the special workspace shows (above the wall as usual).
        if (!specials.shownHere(name)) leaveOverviewFor("special workspace $name")
        caulis.toggleSpecialWorkspace(name)
    }

    /** Moves the focused window into the special workspace [name] — shown with it, or [silent]ly waiting there. */
    fun moveToSpecialWorkspace(name: String = "special", silent: Boolean = false) {
        if (!silent) leaveOverviewFor("special workspace $name")
        caulis.moveToSpecialWorkspace(null, name, silent)
    }

    /**
     * Moves the focused window out of its special workspace back onto the wall — or, with no
     * window of one focused (the special workspace hidden), the one focused last in it. It
     * comes back as a new view, placed as a new window is, focused.
     */
    fun moveOutOfSpecialWorkspace() = caulis.moveOutOfSpecialWorkspace(null)

    // A special workspace is to show: the overview ends first (it shows newm's views only, and
    // hides special workspaces as it opens).
    private fun leaveOverviewFor(what: String) {
        if (!overviewShown()) return
        log.info { "The overview ends: $what shows" }
        animateTo({ s -> null to s.withOverviewSet(false, null, findFocusedView()) }, "overview")
    }

    /** The clipboard's history (Logo+V): what was copied lately, to copy again. */
    val clipboardHistory by lazy { ClipboardHistory(this) }

    fun toggleClipboard() = clipboardHistory.toggle()

    /** The keys' help: every key of the configuration and what it does (Logo+/). */
    private var keyHelpOpen by androidx.compose.runtime.mutableStateOf(false)
    var keyHelpShown: Boolean
        get() = keyHelpOpen
        set(value) {
            keyHelpOpen = value
            if (value) keyHelpFade.show() else keyHelpFade.hide()
        }

    /** The keys' help coming and going (done by the compositor). */
    internal val keyHelpFade = newm.bar.Fade("keyHelp", inMillis = 260, outMillis = 180)

    fun toggleKeyHelp() {
        keyHelpShown = !keyHelpShown
    }

    /** The keys and what they do, as the configuration says (for the help): those given words, if any are. */
    fun keyHelp(): List<Pair<String, String>> {
        val all = confKeyHelp().ifEmpty { confKeyBindings()(this).map { it.first to "" } }
        // Keys with words are the help ("L-b"("Firefox") { ... }); without any (config.py), all of them.
        val described = all.filter { it.second.isNotBlank() }
        return described.ifEmpty { all }
    }

    fun basicScale(deltaS: Int) {
        val ws = activeWorkspace()
        animateTo({ s ->
            null to s.replacingWorkspaceState(ws) { copy(size = max(1.0, size + deltaS)) }
        }, "camera")
    }

    // --- 4. focus -----------------------------------------------------------------------------------------

    fun focusView(view: View) {
        animateTo({ s ->
            view.focus()
            null to s.focusingView(view)
        }, "camera")
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
            // (A swallowed window is hidden behind the one that took its place: never focused so.)
            if (!s.isTiled || s.swallowed != null) continue
            val sc = score(i, j, w, h, deltaI, deltaJ, s.i, s.j, s.w, s.h)
            if (sc < bestScore) {
                bestScore = sc
                best = k
            }
        }
        best?.let { views[it] }?.let(::focusView)
    }

    fun moveNextView(dv: Int = 1, activeWorkspace: Boolean = true) {
        val list = shownViews(if (activeWorkspace) activeWorkspace() else null)
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
        shownViews(if (activeWorkspace) activeWorkspace() else null).getOrNull(index)?.let(::focusView)
    }

    // The windows in turn, less those swallowed (hidden behind the window that took their place).
    private fun shownViews(workspace: Workspace?): List<View> =
        views(workspace).filter { v -> state.findViewOrNull(v)?.first?.swallowed == null }

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
        }, "windowsMove")
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
        }, "windowsMove")
    }

    fun moveFocusedView(di: Int, dj: Int) {
        // On screens: it changes places with the window that way on its screen, or goes onto the next screen that way.
        if (Screens.on()) return onScreens("move ${di} ${dj}") { view, ws, wsState, screens, k ->
            val s0 = wsState.viewState(view)
            val sameScreen = screens[k].windows.filter { it != view.handle }
            val other = sameScreen.mapNotNull { h -> wsState.viewStates[h]?.let { h to score(s0.i, s0.j, s0.w, s0.h, di, dj, it.i, it.j, it.w, it.h) } }
                .filter { it.second < 1000.0 }.minByOrNull { it.second }?.first
            val along = if (Screens.down()) dj else di
            when {
                other != null -> swapIn(screens[k].windows, view.handle, other)
                along != 0 -> toScreen(screens, k, view.handle, along)
                else -> false
            }
        }
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
        }, "windowsMove")
    }

    // --- screens (screens { }) ---------------------------------------------------------------------------

    /**
     * Changes the screens of the focused tiled window's workspace by [change] (given the view,
     * its workspace and state, the screens and the index of its own; true: changed), then
     * tiles them anew and brings the view onto the window's screen. [what] is for the log.
     */
    private fun onScreens(what: String, change: (View, Workspace, WorkspaceState, MutableList<Screen>, Int) -> Boolean) {
        if (!Screens.on()) return
        val view = findFocusedView()?.let { v -> generateSequence(v) { it.parent }.firstOrNull { it.isTiled(state) } } ?: return
        animateTo({ s ->
            val (_, wsState0, wsHandle) = s.findViewOrNull(view) ?: return@animateTo null to null
            val ws = workspaces.firstOrNull { it.handle == wsHandle } ?: return@animateTo null to null
            if (wsState0.isFullscreen()) return@animateTo null to null
            val wsState = wsState0.copy()
            val screens = wsState.readScreens()
            val k = screens.indexOfFirst { view.handle in it.windows }.takeIf { it >= 0 } ?: return@animateTo null to null
            if (!change(view, ws, wsState, screens, k)) return@animateTo null to null
            wsState.writeScreens(screens)
            wsState.validateStackIndices(view)
            log.info { "Screens: $what: ${view.appId} now on screen ${wsState.viewState(view).page + 1}, place ${wsState.viewState(view).slot + 1}" }
            null to s.settingWorkspaceState(ws, wsState.focusingView(view))
        }, "windowsMove")
    }

    // [a] and [b] change places in [windows].
    private fun swapIn(windows: MutableList<Int>, a: Int, b: Int): Boolean {
        val x = windows.indexOf(a)
        val y = windows.indexOf(b)
        if (x < 0 || y < 0 || x == y) return false
        windows[x] = b
        windows[y] = a
        return true
    }

    // Window [handle] of screen [k] onto the screen [by] (1: next, -1: previous): first there going on, last going back;
    // a full screen gives it its window there in exchange; past the last (or first) one, a screen of its own.
    private fun toScreen(screens: MutableList<Screen>, k: Int, handle: Int, by: Int): Boolean {
        val from = screens[k].windows
        val t = k + by.coerceIn(-1, 1)
        if (t < 0 || t >= screens.size) {
            if (from.size <= 1) return false
            from.remove(handle)
            screens.add(if (t < 0) 0 else screens.size, Screen(null, mutableListOf(handle)))
            return true
        }
        val to = screens[t].windows
        val at = if (by > 0) 0 else to.size
        if (to.size >= Screens.capacity(screens[t].layout ?: Screens.defaultLayout())) {
            val back = if (by > 0) to.first() else to.last()
            val place = from.indexOf(handle)
            from[place] = back
            to[to.indexOf(back)] = handle
        } else {
            from.remove(handle)
            to.add(at.coerceAtMost(to.size), handle)
        }
        return true
    }

    /** The focused window onto the next screen ([by] 1) or the one before (-1). */
    fun moveFocusedViewToScreen(by: Int = 1) = onScreens("to screen ${if (by > 0) "+" else ""}$by") { view, _, _, screens, k -> toScreen(screens, k, view.handle, by) }

    /** The focused window changes places with the next one on its screen (the last with the first). */
    fun swapOnScreen(by: Int = 1) = onScreens("swap") { view, _, _, screens, k ->
        val w = screens[k].windows
        if (w.size < 2) false else swapIn(w, view.handle, w[(w.indexOf(view.handle) + by).mod(w.size)])
    }

    /** The focused window becomes its screen's first (the master); the master already: it changes places with the second. */
    fun promoteToMaster() = onScreens("master") { view, _, _, screens, k ->
        val w = screens[k].windows
        when {
            w.size < 2 -> false
            w[0] == view.handle -> swapIn(w, w[0], w[1])
            else -> {
                w.remove(view.handle)
                w.add(0, view.handle)
                true
            }
        }
    }

    /** The focused window's screen takes the next layout of screens { cycle } (or [name]). */
    fun nextScreenLayout(name: String? = null) {
        val view = findFocusedView()
        if (view == null) {
            // (No window: the screen in view.)
            val ws = activeWorkspace()
            val wsState = state.workspaceState(ws)
            val page = Screens.pageAt(wsState.i + .5, wsState.j + .5)
            animateTo({ s ->
                val next = s.workspaceState(ws).copy()
                next.pageLayouts[page] = name ?: Screens.nextLayout(Screens.layoutOf(next, page))
                null to s.settingWorkspaceState(ws, next)
            }, "windowsMove")
            return
        }
        onScreens("layout") { _, _, _, screens, k ->
            val layout = name ?: Screens.nextLayout(screens[k].layout ?: Screens.defaultLayout())
            screens[k].layout = layout
            log.info { "Screen ${k + 1}: layout $layout" }
            true
        }
    }

    /** The view onto the next screen ([by] 1) or the one before (-1), its first window focused. */
    fun focusScreen(by: Int = 1) {
        if (!Screens.on()) return basicMove(if (Screens.down()) 0 else by, if (Screens.down()) by else 0)
        val ws = activeWorkspace()
        val wsState = state.workspaceState(ws)
        val screens = wsState.readScreens()
        val page = findFocusedView()?.let { wsState.viewStateOrNull(it)?.page } ?: Screens.pageAt(wsState.i + .5, wsState.j + .5)
        val target = screens.getOrNull(page + by)?.windows?.firstOrNull() ?: return
        views[target]?.let(::focusView)
    }

    fun resizeFocusedView(di: Int, dj: Int) {
        // On screens: its edge moves within its screen (the right or bottom one; on the screen's edge, the other one), the neighbours making way.
        if (Screens.on()) {
            val view = findFocusedView()?.let { v -> generateSequence(v) { it.parent }.firstOrNull { it.isTiled(state) } } ?: return
            val step = ScreenResize.keyStep()
            animateTo({ s ->
                val (_, wsState0, wsHandle) = s.findViewOrNull(view) ?: return@animateTo null to null
                val ws = workspaces.firstOrNull { it.handle == wsHandle } ?: return@animateTo null to null
                val next = wsState0.copy()
                // (Wider: the right edge right; on the screen's edge, resizeOnScreen moves the left one — so it goes the other way.)
                val vs = next.viewState(view)
                val (oi, oj) = Screens.origin(vs.page)
                val rightOnEdge = vs.i - oi + vs.w > 1 - 1e-4
                val bottomOnEdge = vs.j - oj + vs.h > 1 - 1e-4
                val dx = di * step * (if (rightOnEdge) -1 else 1)
                val dy = dj * step * (if (bottomOnEdge) -1 else 1)
                if (!next.resizeOnScreen(view.handle, if (di != 0) 1 else 0, if (dj != 0) 1 else 0, dx, dy)) return@animateTo null to null
                next.validateStackIndices(view)
                null to s.settingWorkspaceState(ws, next.focusingView(view))
            }, "windowsMove")
            return
        }
        animateTo({ s ->
            val view = findFocusedView() ?: return@animateTo null to s
            try {
                val (vs, wsState, wsHandle) = s.findView(view)
                val ws = workspaces.first { it.handle == wsHandle }
                var i = vs.i
                var j = vs.j
                var w = vs.w + di
                var h = vs.h + dj
                // (Narrower than one tile: two wide, reaching left; shorter: two tall, reaching up — as newm.)
                if (w == 0.0) {
                    w = 2.0
                    i -= 1
                }
                if (h == 0.0) {
                    h = 2.0
                    j -= 1
                }
                val next = wsState.replacingViewState(view) { this.i = i; this.j = j; this.w = w; this.h = h }
                if (confResizePushes()) makeRoom(next, view.handle, vs, i, j, w, h)
                // (The new state's stacks, not the live one's: that is changed only through update.)
                next.validateStackIndices(view)
                null to s.settingWorkspaceState(ws, next.focusingView(view))
            } catch (e: Exception) {
                log.warn("The window could not be resized: $e")
                null to s
            }
        }, "windowsMove")
    }

    // A window grown over its neighbours pushes them aside (the columns right of it, the rows
    // below it) instead of stacking on them; shrunk, the room it leaves is closed again when
    // nothing else is there (newm issue 160; view { resizePushes = false }: as newm did).
    private fun makeRoom(ws: WorkspaceState, handle: Int, before: ViewState, i: Double, j: Double, w: Double, h: Double) {
        val others = ws.viewStates.filter { (k, v) -> k != handle && v.isTiled }.values
        val oldRight = before.i + before.w
        val oldBottom = before.j + before.h
        fun overlapsRows(v: ViewState) = v.j < j + h && v.j + v.h > j
        fun overlapsCols(v: ViewState) = v.i < i + w && v.i + v.w > i
        when {
            // Wider to the right: what was right of it in its rows moves on.
            i + w > oldRight && i == before.i -> if (others.any { it.i >= oldRight && it.i < i + w && overlapsRows(it) })
                others.filter { it.i >= oldRight }.forEach { it.i += (i + w) - oldRight }
            // Wider to the left (from a width of one): what was left of it moves away.
            i < before.i -> if (others.any { it.i + it.w > i && it.i < before.i && overlapsRows(it) })
                others.filter { it.i + it.w <= before.i }.forEach { it.i -= before.i - i }
            // Narrower: the column it left is closed when nothing is in it.
            i + w < oldRight -> if (others.none { it.i < oldRight && it.i + it.w > i + w })
                others.filter { it.i >= oldRight }.forEach { it.i -= oldRight - (i + w) }
        }
        when {
            j + h > oldBottom && j == before.j -> if (others.any { it.j >= oldBottom && it.j < j + h && overlapsCols(it) })
                others.filter { it.j >= oldBottom }.forEach { it.j += (j + h) - oldBottom }
            j < before.j -> if (others.any { it.j + it.h > j && it.j < before.j && overlapsCols(it) })
                others.filter { it.j + it.h <= before.j }.forEach { it.j -= before.j - j }
            j + h < oldBottom -> if (others.none { it.j < oldBottom && it.j + it.h > j + h })
                others.filter { it.j >= oldBottom }.forEach { it.j -= oldBottom - (j + h) }
        }
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
        }, "windowsMove")
    }

    private fun safelyEnterMode(mode: String?) {
        try {
            caulis.enterKeyMode(mode)
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

// A locker program started is given this long to lock before it is started again (milliseconds).
private const val LOCK_PROGRAM_PATIENCE_MS = 10_000L

private fun Double.toMillis(): Int = (this * 1000).toLong().toInt()

// Commands whose launcher or popup would show over the lock screen (refused while locked).
private val SHOWN_ABOVE_THE_LOCK = setOf("open-launcher", "bar")
