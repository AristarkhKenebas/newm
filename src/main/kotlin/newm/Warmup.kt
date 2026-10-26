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

/**
 * Draws newm's menus once, off screen, a moment after the start: the first time one is drawn
 * is slow (classes loaded, fonts, icons), and it showed — the first opening stuttered and its
 * animation fell apart. One a frame, a little apart, never while something moves on screen.
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
        wm.caulis.after(2500) { step(0) }
    }

    private fun step(index: Int) {
        val monitor = wm.caulis.desktop.monitors.firstOrNull() ?: return
        if (index >= items.size) {
            item = -1
            widget.close()
            runCatching { wm.panelsUi.prewarmLauncher() }
            log.info { "Menus drawn ahead: ${items.size}" }
            return
        }
        // (Not while something moves on screen: later.)
        if (wm.caulis.desktop.overlay != null || wm.barModel.popup != null) {
            wm.caulis.after(1000) { step(index) }
            return
        }
        item = index
        val (size, _) = items[index]
        val started = System.nanoTime()
        runCatching { widget.prewarm(monitor, size.first, size.second); widget.prewarm(monitor, size.first, size.second) }
            .onFailure { log.warn("Drawing ahead failed: $it") }
        log.trace { "Drawn ahead #$index in ${(System.nanoTime() - started) / 1_000_000} ms" }
        wm.caulis.after(120) { step(index + 1) }
    }
}
