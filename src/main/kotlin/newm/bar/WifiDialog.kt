package newm.bar

import newm.lockShown
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.caulis.compose.ComposeWidget
import dev.caulis.core.DrawingLevel
import dev.caulis.core.Monitor
import dev.caulis.geometry.Rect

private const val DIALOG_WIDTH = 400
private const val DIALOG_HEIGHT = 250


private val log = dev.caulis.log.Logger("bar")

/**
 * The window that asks for a Wi-Fi network's password, in the middle of the screen. The
 * password goes to NetworkManager over D-Bus, never on a command line or into the log.
 */
class WifiPasswordDialog(private val model: BarModel) {
    /** The network asked about, or null: closed. */
    var network by mutableStateOf<WifiNetwork?>(null)
        private set
    private var shown by mutableStateOf<WifiNetwork?>(null)
    private val fade = Fade("popups", inMillis = 200, outMillis = 140)
    private var widget: ComposeWidget? = null
    private var opened by mutableStateOf(0)

    fun start(): AutoCloseable {
        val caulis = model.wm.caulis
        val w = ComposeWidget(caulis.desktop, { task -> caulis.post { task.run() } }, place = { m -> placeOn(m) },
            level = DrawingLevel.ON_TOP, takesKeyboard = true,
            appearance = { _ -> fade.appearance(rise = 10f, smallest = 0.94f) },
            moving = { fade.moving },
        ) { _ -> shown?.let { Dialog(it) } }
        widget = w
        // Locked: the window goes (asked again once unlocked); never typed into over the lock screen.
        val lockWatch = caulis.desktop.onLockChanged { locked ->
            if (locked) {
                network?.let { askAfterUnlock = it }
                cancel()
            } else {
                askAfterUnlock?.let { askAfterUnlock = null; caulis.post { ask(it) } }
            }
        }
        val added = caulis.desktop.addWidget(w)
        return AutoCloseable {
            lockWatch.close()
            added.close()
        }
    }

    // On the monitor with the pointer, in its middle.
    private fun placeOn(m: Monitor): Rect? {
        if (model.wm.lockShown()) return null
        if (!fade.visible) {
            if (network == null && shown != null) model.wm.caulis.post { if (!fade.visible && network == null) shown = null }
            return null
        }
        val d = model.wm.caulis.desktop
        val target = d.monitors.firstOrNull { it.area.contains(Rect(d.pointerX.toInt(), d.pointerY.toInt(), 1, 1)) } ?: d.monitors.firstOrNull()
        if (m !== target) return null
        return Rect(m.area.x + (m.area.width - DIALOG_WIDTH) / 2, m.area.y + (m.area.height - DIALOG_HEIGHT) / 2 - 40, DIALOG_WIDTH, DIALOG_HEIGHT)
    }

    // A network that wanted its password while the screen was locked: asked once it is unlocked.
    private var askAfterUnlock: WifiNetwork? = null

    /** Asks for [target]'s password (the bar's popup closes meanwhile); while locked, once unlocked. */
    fun ask(target: WifiNetwork) {
        if (model.wm.lockShown()) {
            askAfterUnlock = target
            return log.info { "Wi-Fi password wanted while locked: asked once unlocked" }
        }
        network = target
        shown = target
        opened++
        fade.show()
        // The keyboard only if newm's bar or popups have it (Connect was just clicked): a
        // program you went on typing in while NetworkManager tried (seconds) keeps it — what
        // you type there never goes to NetworkManager as a password. A click on the dialog
        // gives it the keyboard.
        val d = model.wm.caulis.desktop
        val yours = d.keyboardWidget != null || d.keyboardFocus == null
        model.close()
        if (yours) widget?.let { d.focusWidget(it) } else log.info { "Wi-Fi password wanted: asked without taking the keyboard from the program in use" }
        quietKeys()
    }

    // While the password is typed, keys are not written to the log, even with input at trace
    // (Caulis logs each key's code then); as it was again once the window closes.
    private var inputLevelBefore: Int? = null

    private fun quietKeys() {
        val log = dev.caulis.log.Log
        if (inputLevelBefore != null || log.levelOf("input") < 3) return
        inputLevelBefore = log.levelOf("input")
        log.setLevel("input", 2)
    }

    private fun loudKeysAgain() {
        val before = inputLevelBefore ?: return
        inputLevelBefore = null
        val log = dev.caulis.log.Log
        if (before == log.level) log.clearLevel("input") else log.setLevel("input", before)
    }

    fun cancel() {
        loudKeysAgain()
        network = null
        fade.hide()
        val d = model.wm.caulis.desktop
        if (d.keyboardWidget === widget) d.focusWidget(null)
    }

    private fun connect(target: WifiNetwork, password: String) {
        // (Not one NetworkManager would refuse: WEP 5/13 characters or 10/26 hex digits, WPA 8 or more.)
        if (passwordProblem(target.security, password) != null) return
        model.network.connect(target, password)
        cancel()
    }

    @Composable
    private fun Dialog(target: WifiNetwork) {
        val colors = model.colorsNow()
        val text = TextStyle(color = colors.text, fontSize = 13.sp)
        var password by remember(opened) { mutableStateOf("") }
        val shape = RoundedCornerShape(22.dp)
        Box(Modifier.fillMaxSize().padding(14.dp).softShadow(22.dp, 14.dp).background(colors.island.copy(alpha = 0.98f), shape).border(1.dp, colors.edge, shape).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
                        GlyphIcon(Glyph.WIFI, onAccent(colors), 19.dp, level = target.strength / 100f)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        BasicText(tr("Wi-Fi password"), style = text.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                        BasicText(tr("For “%s”").replace("%s", target.ssid), style = text.copy(color = colors.dim, fontSize = 12.sp), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(2.dp))
                PasswordField(password, colors, text, onChange = { password = it }, onSubmit = { connect(target, password) }, onCancel = ::cancel)
                BasicText(passwordProblem(target.security, password)?.takeIf { password.isNotEmpty() }?.let(::tr) ?: " ", style = text.copy(color = colors.dim, fontSize = 11.sp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    PillButton("Cancel", colors, text) { cancel() }
                    PillButton("Connect", colors, text, PillKind.PRIMARY) { connect(target, password) }
                }
            }
        }
    }
}
