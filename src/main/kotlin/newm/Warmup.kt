package newm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import dev.caulis.compose.ComposeWidget
import dev.caulis.log.Logger
import newm.bar.POPUP_SHADOW
import newm.bar.POPUP_TALLEST
import newm.bar.POPUP_WIDEST
import newm.bar.Popup
import newm.bar.PopupContent
import newm.bar.PopupKind

private val log = Logger("warmup")

// warmup { enabled = true; restMillis = 800; compose = true; collect = true }
private val confEnabled = configuredValue("warmup.enabled", true)
private val confRestMillis = configuredValue("warmup.rest_ms", 800)
private val confCollect = configuredValue("warmup.collect", true)
private val confOwnThread = configuredValue("warmup.own_thread", true)

/**
 * True inside a menu drawn ahead (Warmup): what a menu does when it opens (a Wi-Fi scan, asking
 * Bluetooth or the sound server, power profiles) is left out then — drawing ahead must not
 * touch the system at every login. Menus check it in their LaunchedEffects.
 */
internal val LocalWarmingUp = staticCompositionLocalOf { false }

/** Drawing ahead is on and done on threads of its own (warmup.enabled, warmup.own_thread). */
internal fun warmsOnThread(): Boolean = confEnabled() && confOwnThread()

/**
 * Draws newm's menus once, off screen, a moment after the start: the first time one is drawn
 * is slow (classes loaded, fonts, icons), and it showed — the first opening stuttered and its
 * animation fell apart. By default on a thread of its own (warmup.own_thread): the menus are
 * composed and drawn there in scenes of their own, one after another, never on the
 * compositor's thread — it took that thread for up to ~300 ms a menu before, and the pointer
 * stood still meanwhile. Only while the pointer rests (warmup.rest_ms), so the start's other
 * work is not slowed. Nothing a menu does when it opens (a Wi-Fi scan, Bluetooth, the sound
 * server, commands) happens then ([LocalWarmingUp]). Then the memory the start used is given
 * back (warmup.collect, Caulis's trimMemory). warmup.own_thread = false: drawn on the
 * compositor's thread as before, one a frame. Off (warmup.enabled = false): each menu is slow
 * the first time it opens instead.
 */
internal class Warmup(private val wm: Layout) {
    private var item by mutableIntStateOf(-1)

    private val items: List<Pair<Pair<Int, Int>, @Composable () -> Unit>> by lazy {
        val bar = wm.barsNow().firstOrNull()
        val popups = if (bar == null) emptyList() else listOf(
            PopupKind.QUICK, PopupKind.VOLUME, PopupKind.NETWORK, PopupKind.BLUETOOTH, PopupKind.BATTERY, PopupKind.POWER, PopupKind.CALENDAR, PopupKind.COLORS,
        ).map { kind ->
            val popup = Popup(kind, bar, 0.0)
            (POPUP_WIDEST + 2 * POPUP_SHADOW to POPUP_TALLEST + 2 * POPUP_SHADOW) to @Composable { PopupContent(wm.barModel, popup) }
        }
        // (A card first: a program may send one right after the start.)
        listOf((480 to 260) to @Composable { newm.bar.WarmNotificationCard(wm.barModel) }) + popups + listOf(
            (720 to 560) to @Composable { LauncherPanel(wm, emptyList(), 0) },
            (1060 to 700) to @Composable { KeyHelpPanel(wm) },
            (980 to 620) to @Composable { wm.clipboardHistory.WarmPanel() },
        )
    }

    private val widget: ComposeWidget by lazy {
        ComposeWidget(wm.caulis.desktop, { task -> wm.caulis.post { task.run() } }, place = { null }) { _ ->
            items.getOrNull(item)?.second?.let { content -> CompositionLocalProvider(LocalWarmingUp provides true) { content() } }
        }
    }

    fun start() {
        if (!confEnabled()) return log.info { "Menus are not drawn ahead (warmup.enabled = false)" }
        wm.caulis.after(2500) { if (confOwnThread()) startOnThread() else step(0, again = false) }
    }

    // The menus drawn on a thread of their own, each in a scene of its own (Caulis's
    // ComposeWarmup.draw): what they read of newm is only looked at, what their effects would
    // do is left out (LocalWarmingUp). Classes, fonts and compiled code are shared: the
    // compositor's thread finds them ready when a menu opens.
    private fun startOnThread() {
        val list = items // (made here, on the compositor's thread: the bar's popups)
        val rest = confRestMillis().toLong().coerceIn(0, 60_000)
        val desktop = wm.caulis.desktop
        log.info { "Menus drawn ahead on a thread of their own: ${list.size}" }
        Thread.ofPlatform().daemon().name("newm-warmup").start {
            val started = System.nanoTime()
            var drawn = 0
            for ((index, entry) in list.withIndex()) {
                // (While the pointer moves, the start's other work goes first.)
                while (true) {
                    val moved = (System.nanoTime() - desktop.lastPointerMoveNanos) / 1_000_000
                    if (desktop.lastPointerMoveNanos == 0L || moved >= rest) break
                    Thread.sleep((rest - moved).coerceIn(50, rest.coerceAtLeast(50)))
                }
                val (size, content) = entry
                val itemStarted = System.nanoTime()
                try {
                    dev.caulis.compose.ComposeWarmup.draw(size.first, size.second, frames = 2) {
                        CompositionLocalProvider(LocalWarmingUp provides true) { content() }
                    }
                    drawn++
                } catch (e: Throwable) {
                    if (e is VirtualMachineError && e !is StackOverflowError) throw e
                    log.warn("Drawing ahead #$index failed: $e")
                }
                log.trace { "Drawn ahead #$index on its own thread in ${(System.nanoTime() - itemStarted) / 1_000_000} ms" }
            }
            val millis = (System.nanoTime() - started) / 1_000_000
            wm.caulis.post {
                log.info { "Menus drawn ahead: $drawn of ${list.size}, in $millis ms on their own thread" }
                freeAfterwards()
            }
        }
    }

    // Draws item [index] ([again]: its second time; the first lays out what waited for it).
    private fun step(index: Int, again: Boolean) {
        val monitor = wm.caulis.desktop.monitors.firstOrNull() ?: return
        if (index >= items.size) {
            item = -1
            widget.close()
            runCatching { wm.panelsUi.prewarmLauncher() }
            log.info { "Menus drawn ahead: ${items.size}" }
            freeAfterwards()
            return
        }
        // (Not while something moves on screen, nor while the pointer moves: later.)
        if (wm.caulis.desktop.overlay != null || wm.barModel.popup != null) {
            wm.caulis.after(1000) { step(index, again) }
            return
        }
        val rest = confRestMillis().toLong().coerceIn(0, 60_000)
        val moved = (System.nanoTime() - wm.caulis.desktop.lastPointerMoveNanos) / 1_000_000
        if (wm.caulis.desktop.lastPointerMoveNanos != 0L && moved < rest) {
            wm.caulis.after((rest - moved).coerceAtLeast(50)) { step(index, again) }
            return
        }
        item = index
        val (size, _) = items[index]
        val started = System.nanoTime()
        runCatching { widget.prewarm(monitor, size.first, size.second) }
            .onFailure { log.warn("Drawing ahead failed: $it") }
        log.trace { "Drawn ahead #$index${if (again) " again" else ""} in ${(System.nanoTime() - started) / 1_000_000} ms" }
        wm.caulis.after(120) { if (again) step(index + 1, again = false) else step(index, again = true) }
    }

    // The start's garbage given back, on a thread of its own: Java's heap (warmup.collect: one
    // full collection, a pause of some 10-50 ms while the pointer rests — Java grew its heap to
    // 100-230 MB for the start and gave it back only minutes later, if at all) and the C
    // library's (Caulis's trimMemory).
    private fun freeAfterwards() {
        val collect = confCollect()
        val trim = wm.caulis.trimMemory
        if (!collect && !trim) return
        val skia = org.jetbrains.skia.Graphics
        log.info { "Skia's caches: pictures ${skia.resourceCacheTotalUsed / 1024} of ${skia.resourceCacheTotalLimit / 1024} kB, fonts ${skia.fontCacheUsed / 1024} of ${skia.fontCacheLimit / 1024} kB" }
        Thread.ofPlatform().daemon().name("newm-free").start {
            if (collect) {
                val heap = Runtime.getRuntime()
                val before = heap.totalMemory()
                val started = System.nanoTime()
                System.gc()
                log.info { "Java's heap after the start: ${before / 1_048_576} MB -> ${heap.totalMemory() / 1_048_576} MB (${(heap.totalMemory() - heap.freeMemory()) / 1_048_576} MB used), collected in ${(System.nanoTime() - started) / 1_000_000} ms" }
            }
            if (trim) dev.caulis.ffi.NativeMemory.trim()
        }
    }
}
