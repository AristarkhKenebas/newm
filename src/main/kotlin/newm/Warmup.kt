package newm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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

/**
 * Draws newm's menus once, off screen, a moment after the start: the first time one is drawn
 * is slow (classes loaded, fonts, icons), and it showed — the first opening stuttered and its
 * animation fell apart. One a frame, a little apart, never while something moves on screen,
 * and only while the pointer rests (warmup.rest_ms): each takes the compositor's thread for
 * up to a few hundred milliseconds at first, and the pointer stood still meanwhile. Then the
 * memory the start used is given back (warmup.collect, Caulis's trimMemory). Off
 * (warmup.enabled = false): each menu is slow the first time it opens instead.
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
        popups + listOf(
            (720 to 560) to @Composable { LauncherPanel(wm, emptyList(), 0) },
            (1060 to 700) to @Composable { KeyHelpPanel(wm) },
            (980 to 620) to @Composable { wm.clipboardHistory.WarmPanel() },
        )
    }

    private val widget: ComposeWidget by lazy {
        ComposeWidget(wm.caulis.desktop, { task -> wm.caulis.post { task.run() } }, place = { null }) { _ ->
            items.getOrNull(item)?.second?.invoke()
        }
    }

    fun start() {
        if (!confEnabled()) return log.info { "Menus are not drawn ahead (warmup.enabled = false)" }
        wm.caulis.after(2500) { step(0, again = false) }
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
