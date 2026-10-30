package newm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.caulis.log.Logger
import dev.caulis.wm.Caulis
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

private val log = Logger("lock")

// lock { layoutOnLock = "keep" }: "first" switches to the first keyboard layout (us of "us,ru")
// when the screen locks, and back to the one before after unlocking; "keep" leaves it (shown either way).
internal val confLayoutOnLock = configuredValue("panels.lock.layout_on_lock", "keep")
// lock { showPanels = false }: programs' panels (waybar, mako's pop-ups), menus and X11's
// unmanaged windows stay drawn while newm's own lock screen is up (true) — what they show may be
// private, so not by default (as newm's windows, which fade out).
internal val confShowPanels = configuredValue("panels.lock.show_panels", false)
// lock { revealButton = true }: an eye beside the password shows it while held.
internal val confRevealButton = configuredValue("panels.lock.reveal_button", true)
// lock { onScreenKeyboard = false }: a small keyboard on the lock screen (touch screens, a broken keyboard).
internal val confOnScreenKeyboard = configuredValue("panels.lock.on_screen_keyboard", false)
// lock { faillock = true }: pam_faillock's state is read, so a locked account shows a countdown instead of taking passwords.
internal val confFaillock = configuredValue("panels.lock.faillock", true)
// lock { faillockConf = "/etc/security/faillock.conf" }: where pam_faillock's settings are.
internal val confFaillockConf = configuredValue("panels.lock.faillock_conf", "/etc/security/faillock.conf")

/**
 * pam_faillock's state of a user: the wrong passwords it counts now, how many lock the account
 * ([deny]; 0: never), and until when it is locked (seconds since 1970; 0: not locked, -1: until
 * an administrator unlocks it).
 */
internal data class FaillockState(val failures: Int, val deny: Int, val lockedUntil: Long, val unlockTime: Long) {
    val locked: Boolean get() = lockedUntil != 0L

    /** Wrong passwords left before the account locks (null: it never does). */
    val triesLeft: Int? get() = if (deny > 0) (deny - failures).coerceAtLeast(0) else null
}

/** pam_faillock's records (its tally files, which the user may read) and settings (faillock.conf). */
internal object Faillock {
    /** faillock.conf's settings that matter here, with pam_faillock's defaults. */
    data class Conf(val dir: String = "/var/run/faillock", val deny: Int = 3, val failInterval: Long = 900, val unlockTime: Long = 600)

    // A record: char source[52], u16 reserved, u16 status, u64 time.
    private const val RECORD = 64
    private const val VALID = 0x1

    fun parseConf(text: String): Conf {
        var conf = Conf()
        for (raw in text.lines()) {
            val line = raw.substringBefore('#').trim()
            if ('=' !in line) continue
            val key = line.substringBefore('=').trim()
            val value = line.substringAfter('=').trim()
            conf = when (key) {
                "dir" -> conf.copy(dir = value)
                "deny" -> value.toIntOrNull()?.let { conf.copy(deny = it) } ?: conf
                "fail_interval" -> value.toLongOrNull()?.let { conf.copy(failInterval = it) } ?: conf
                "unlock_time" -> if (value == "never") conf.copy(unlockTime = 0) else value.toLongOrNull()?.let { conf.copy(unlockTime = it) } ?: conf
                else -> conf
            }
        }
        return conf
    }

    /** The state from a tally file's [bytes] at [now] (seconds since 1970), counted as pam_faillock counts. */
    fun state(bytes: ByteArray, conf: Conf, now: Long): FaillockState {
        val times = ArrayList<Long>()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        for (n in 0 until bytes.size / RECORD) {
            val status = buffer.getShort(n * RECORD + 54).toInt() and 0xffff
            if (status and VALID != 0) times += buffer.getLong(n * RECORD + 56)
        }
        val latest = times.maxOrNull() ?: return FaillockState(0, conf.deny, 0, conf.unlockTime)
        // (pam_faillock counts those close enough to the latest; for "tries left", to now.)
        val nearLatest = times.count { latest - it < conf.failInterval }
        val lockedUntil = when {
            conf.deny <= 0 || nearLatest < conf.deny -> 0L
            conf.unlockTime <= 0 -> -1L
            latest + conf.unlockTime >= now -> latest + conf.unlockTime
            else -> 0L
        }
        val recent = if (lockedUntil == 0L && conf.deny > 0 && nearLatest >= conf.deny) 0 else times.count { now - it < conf.failInterval }
        return FaillockState(recent, conf.deny, lockedUntil, conf.unlockTime)
    }

    /** [user]'s state now, or null (no pam_faillock here, or its files cannot be read). */
    fun read(user: String, confPath: String = confFaillockConf(), now: Long = System.currentTimeMillis() / 1000): FaillockState? = try {
        val conf = Path.of(confPath).takeIf { Files.isReadable(it) }?.let { parseConf(Files.readString(it)) } ?: Conf()
        val tally = Path.of(conf.dir, user)
        if (!Files.isReadable(tally)) null else state(Files.readAllBytes(tally), conf, now)
    } catch (e: Exception) {
        log.trace { "pam_faillock's state cannot be read: $e" }
        null
    }

    /** "1:54" for [seconds]. */
    fun countdown(seconds: Long): String = "%d:%02d".format(seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)
}

/** The keyboard layout's short name now ("EN", "RU"; "" with one layout). */
internal fun lockLayoutLabel(caulis: Caulis): String {
    val names = caulis.keyboardLayoutNames
    if (names.size <= 1) return ""
    // (With the bar's labels: layoutNames = mapOf("ru" to "ЙЦ") reads the same on both.)
    return names.getOrNull(caulis.keyboardLayoutNow)?.let { layoutLabel(it, barLayoutNames()) }.orEmpty()
}

/** An eye: held, the password shows. */
@Composable
internal fun RevealEye(color: Color, onReveal: (Boolean) -> Unit) {
    Canvas(
        Modifier.size(28.dp).pointerInput(Unit) {
            detectTapGestures(onPress = {
                onReveal(true)
                try {
                    tryAwaitRelease()
                } finally {
                    onReveal(false)
                }
            })
        },
    ) {
        val w = size.width
        val h = size.height
        drawOval(color, Offset(w * 0.1f, h * 0.28f), Size(w * 0.8f, h * 0.44f), style = Stroke(w * 0.07f))
        drawCircle(color, w * 0.12f, Offset(w / 2, h / 2))
    }
}

// The keys of the on-screen keyboard (evdev codes): digits, three rows of letters (with what
// the layout puts beside them: х ъ, ж э, б ю on the Russian one).
private val OSK_ROWS = listOf((2..11).toList(), (16..27).toList(), (30..40).toList(), (44..53).toList())

/**
 * A small keyboard for the lock screen (lock { onScreenKeyboard }): its keys type what the
 * keyboard layout in use types ([Caulis.keyText]); Shift for one character, Backspace, the layout
 * switch and Enter.
 */
@Composable
internal fun OnScreenKeyboard(caulis: Caulis, layoutLabel: String, text: Color, key: Color, onText: (String) -> Unit, onBackspace: () -> Unit, onEnter: () -> Unit, onLayout: () -> Unit) {
    var shift by remember { mutableStateOf(false) }
    val style = TextStyle(color = text, fontSize = 16.sp)

    @Composable
    fun Key(label: String, wide: Float = 1f, action: () -> Unit) {
        Box(
            Modifier.padding(2.dp).width((34 * wide).dp).height(38.dp).background(key, RoundedCornerShape(8.dp)).clickable { action() },
            contentAlignment = Alignment.Center,
        ) { BasicText(label, style = style) }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        for (row in OSK_ROWS) {
            Row(horizontalArrangement = Arrangement.Center) {
                for (code in row) {
                    val label = caulis.keyText(code, shift) ?: continue
                    Key(label) {
                        onText(label)
                        shift = false
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.Center) {
            Key("⇧", 1.6f) { shift = !shift }
            if (layoutLabel.isNotEmpty()) Key(layoutLabel, 1.6f) { onLayout() }
            Key(" ", 4f) { onText(" ") }
            Key("⌫", 1.6f) { onBackspace() }
            Key("⏎", 1.6f) { onEnter() }
        }
    }
}

/** A short line on the lock screen: dimmed, or in [color] when it matters (Caps Lock, a locked account). */
@Composable
internal fun LockNote(text: String, color: Color, bold: Boolean = false) {
    if (text.isEmpty()) return
    BasicText(text, Modifier.padding(top = 4.dp), style = TextStyle(color = color, fontSize = 14.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal))
}
