package newm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.caulis.compose.ComposeWidget
import dev.caulis.core.DrawingLevel
import dev.caulis.core.Monitor
import dev.caulis.geometry.Rect
import dev.caulis.log.Logger
import newm.bar.tr
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val log = Logger("panels")

private val confLockH = configuredValue("panels.lock.h", 0.6)
private val confLockW = configuredValue("panels.lock.w", 0.7)
private val confLockCornerRadius = configuredValue("panels.lock.corner_radius", 50.0)
// (panels.launcher.w, h, corner_radius: a launcher program's, see View.kt.) newm's own launcher:
// launcher { width = 720; height = 560; top = 0.16 } — pixels at most, and its top as a part of the screen.
private val confLauncherWidth = configuredValue("panels.launcher.width", 720)
private val confLauncherHeight = configuredValue("panels.launcher.height", 560)
private val confLauncherTop = configuredValue("panels.launcher.top", 0.16)

// lock { background; text; accent } ("#RRGGBB[AA]"; unset: the bar's colours) and its clock { clock; date }.
private val confLockBackground = configuredValue("panels.lock.background", "")
private val confLockText = configuredValue("panels.lock.text", "")
private val confLockAccent = configuredValue("panels.lock.accent", "")
private val confLockClock = configuredValue<String?>("panels.lock.clock", null)
private val confLockDate = configuredValue("panels.lock.date", "EEEE, d MMMM")
// The top-level timeFormat (time_format), the lock screen's clock unless lock { clock } says otherwise.
private val confTimeFormat = configuredValue("time_format", "HH:mm")
// lock { showOnLock = listOf("clock", "battery") }: what the lock screen shows besides the password.
private val confShowOnLock = configuredValue("panels.lock.show_on_lock", listOf("clock", "battery"))

// A colour of the configuration ("#RRGGBB" or "#RRGGBBAA"), or null (unset, or not a colour: told once).
private val badColours = HashSet<String>()

private fun colourOf(text: String): Color? {
    if (text.isBlank()) return null
    val hex = text.trim().removePrefix("#")
    val v = hex.toLongOrNull(16)
    if (v == null || hex.length != 6 && hex.length != 8) {
        if (badColours.add(text)) log.warn("\"$text\" is not a colour (\"#RRGGBB\" or \"#RRGGBBAA\"): the bar's is used")
        return null
    }
    return if (hex.length == 6) Color(0xFF000000 or v) else Color(((v and 0xFF) shl 24) or (v ushr 8))
}

// A date pattern of the configuration, or (a mistake in it) null: told once.
private val badPatterns = HashSet<String>()

private fun formatted(now: LocalDateTime, pattern: String): String = try {
    if (pattern.isEmpty()) "" else now.format(DateTimeFormatter.ofPattern(pattern))
} catch (e: IllegalArgumentException) {
    if (badPatterns.add(pattern)) log.warn("\"$pattern\" is not a date pattern of java.time: ${e.message}")
    ""
}

/** The lock screen's colours: its own (lock { }), else the bar's, light or dark as everything. */
private data class LockColors(val background: Color, val text: Color, val dim: Color, val accent: Color, val field: Color)

/**
 * newm's own lock screen and launcher (newm-panel-basic was a program of its own, in a
 * terminal): Compose, inside newm, sliding in as newm's panels do. A panel program of the
 * configuration (panels.lock.cmd, panels.launcher.cmd) that is there wins over them.
 */
class Panels(private val wm: Layout) {

    // --- where they are

    // The monitor of the active workspace (the panel shows there, as newm's did).
    private fun monitorShown(): Monitor? = wm.activeWorkspace().outputs.firstOrNull() ?: wm.caulis.desktop.monitors.firstOrNull()

    private fun area(m: Monitor, perc: Double, w: Double, h: Double): Rect? {
        if (perc <= 0.0 || m !== monitorShown()) return null
        val ws = wm.workspaces.firstOrNull { m in it.outputs } ?: return null
        val pw = Math.round(ws.width * w).toInt()
        val ph = Math.round(ws.height * h).toInt()
        val x = ws.posX + (ws.width - pw) / 2
        val y = ws.posY + (ws.height - ph) / 2 + Math.round((1.0 - perc) * ws.height).toInt()
        return Rect(x, y, pw, ph)
    }

    // The launcher: in the upper middle of the screen, while it is open (or opening).
    private fun launcherArea(m: Monitor): Rect? {
        // (Never over the lock screen: neither drawn nor clickable there.)
        if (wm.layoutDown.launcherPerc <= 0.0 || m !== monitorShown() || wm.lockShown()) return null
        val a = m.area
        val (w, h) = launcherSize(m)
        val top = confLauncherTop().coerceIn(0.0, 1.0)
        // (Never past the screen's bottom.)
        val y = minOf(a.y + (a.height * top).toInt(), a.y + a.height - h).coerceAtLeast(a.y)
        return Rect(a.x + (a.width - w) / 2, y, w, h)
    }

    // The launcher's size on [m]: 720 x 560, smaller on a small screen (60 % and 62 % of it at
    // most); launcher { width, height } as given (within the screen).
    private fun launcherSize(m: Monitor): Pair<Int, Int> {
        val a = m.area
        val w = if (Config.lookup("panels.launcher.width") != null) confLauncherWidth().coerceIn(200, a.width)
        else minOf(720, (a.width * 0.6).toInt()).coerceAtLeast(420)
        val h = if (Config.lookup("panels.launcher.height") != null) confLauncherHeight().coerceIn(120, a.height)
        else minOf(560, (a.height * 0.62).toInt())
        return w to h
    }

    // --- the lock screen

    /** What the lock screen is asked now (the password, or — for the greeter — the user). */
    private var request by mutableStateOf<AuthRequest?>(null)
    private var message by mutableStateOf("")
    private var lockWidget: ComposeWidget? = null

    /** An authentication request: the lock screen asks for it. */
    fun authRequest(req: AuthRequest) {
        val previous = request
        request = req
        val kind = req.data["kind"]
        message = when {
            kind == "auth_request_user" -> "Who are you?"
            // (What PAM said: the account locked, the check taking too long, PAM missing.)
            req.data["error"] is String -> req.data["error"] as String
            previous != null && previous.data["kind"] == "auth_request_cred" -> "That was not it. Again:"
            else -> (req.data["message"] as? String)?.takeIf { it != "Password?" } ?: ""
        }
    }

    /** Something keeps the password from being checked at all: said on the lock screen. */
    fun authProblem(text: String) {
        request = null
        message = text
    }

    /** The widget Caulis locks the session with (newm's lock screen; with a lock panel program, it stays hidden). */
    fun lockScreenWidget(): ComposeWidget? = lockWidget

    /** The lock screen gets the keyboard (and newm's own and Caulis's keys stop meanwhile). */
    fun locked() {
        lockWidget?.let { wm.caulis.desktop.focusWidget(it) }
    }

    fun unlocked() {
        request = null
        message = ""
        if (wm.caulis.desktop.keyboardWidget === lockWidget) wm.caulis.desktop.focusWidget(null)
    }

    // --- the launcher

    private var launcherWidget: ComposeWidget? = null
    private var apps by mutableStateOf<List<DesktopApp>>(emptyList())
    private var opened by mutableIntStateOf(0)

    /** The launcher opens: the keyboard to it, the programs looked up anew. */
    // The launcher is going (it fades while newm's animation runs).
    private var launcherClosing by mutableStateOf(false)

    fun launcherOpened() {
        launcherClosing = false
        // (Locked meanwhile: the keyboard stays the lock screen's.)
        if (!wm.lockShown()) launcherWidget?.let { wm.caulis.desktop.focusWidget(it) }
        Thread({
            val found = DesktopApp.installed()
            wm.caulis.post { apps = found }
        }, "newm-apps").apply { isDaemon = true }.start()
        opened++
    }

    fun launcherClosed() {
        launcherClosing = true
        if (wm.caulis.desktop.keyboardWidget === launcherWidget) wm.caulis.desktop.focusWidget(null)
    }

    /** Draws the launcher once ahead (its own scene, kept): its first opening is as quick as the next. */
    fun prewarmLauncher() {
        val widget = launcherWidget ?: return
        val m = wm.caulis.desktop.monitors.firstOrNull() ?: return
        val (w, h) = launcherSize(m)
        widget.prewarm(m, w, h)
    }

    /** True while newm's own launcher (not a panel program) is in use. */
    val ownLauncher: Boolean get() = wm.panels().none { it.panel == "launcher" }

    /** True while newm's own lock screen (not a panel program) is in use. */
    // (Also when a lock panel window is still about but its program is not running: no one else to take the password.)
    val ownLock: Boolean get() = wm.panels().none { it.panel == "lock" } || wm.panelLauncher.pidOf("lock") == null

    fun start() {
        val desktop = wm.caulis.desktop
        val post: (Runnable) -> Unit = { task -> wm.caulis.post { task.run() } }
        val lock = ComposeWidget(
            desktop, post,
            place = { m -> if (ownLock) area(m, wm.layoutDown.lockPerc, confLockW(), confLockH()) else null },
            level = DrawingLevel.ON_TOP, takesKeyboard = true,
        ) { LockScreen() }
        val launcher = ComposeWidget(
            desktop, post,
            place = { m -> if (ownLauncher) launcherArea(m) else null },
            level = DrawingLevel.ON_TOP, takesKeyboard = true,
            // Coming and going with newm's launcher animation: see-through, smaller, a little higher
            // (along the curve of the animation "launcher", fast at first and gently at the end).
            appearance = { _ ->
                val e = wm.layoutDown.launcherPerc.toFloat().coerceIn(0f, 1f)
                if (e >= 1f) dev.caulis.compose.WidgetLook.SHOWN else dev.caulis.compose.WidgetLook(alpha = e, scale = 0.94f + 0.06f * e, dy = (1f - e) * -16f)
            },
            // A click beside the launcher closes it (and goes no further).
            onPressOutside = { _, _, _ ->
                if (ownLauncher && wm.overlay is LauncherOverlay && wm.layoutDown.launcherPerc > 0.5) {
                    wm.exitOverlay()
                    true
                } else false
            },
        ) { LauncherPanel(wm, apps, opened, launcherClosing) }
        lockWidget = lock
        launcherWidget = launcher
        wm.caulis.addWidget(lock)
        wm.caulis.addWidget(launcher)
    }

    @Composable
    private fun Panel(radius: Double, background: Color, content: @Composable () -> Unit) {
        Box(
            Modifier.fillMaxSize().background(background, RoundedCornerShape(radius.dp)).padding(32.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
    }

    // lock { background, text, accent } over the bar's colours.
    private fun lockColors(): LockColors {
        val bar = wm.barModel.colorsNow()
        val text = colourOf(confLockText()) ?: bar.text
        return LockColors(
            background = colourOf(confLockBackground()) ?: bar.island.copy(alpha = 0.93f),
            text = text,
            dim = if (confLockText().isBlank()) bar.dim else text.copy(alpha = 0.6f),
            accent = colourOf(confLockAccent()) ?: bar.accent,
            field = text.copy(alpha = 0.08f),
        )
    }

    @Composable
    private fun LockScreen() {
        var now by remember { mutableStateOf(LocalDateTime.now()) }
        LaunchedEffect(Unit) {
            while (true) {
                now = LocalDateTime.now()
                delay(1000)
            }
        }
        var password by remember { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        val req = request
        LaunchedEffect(req) { runCatching { focus.requestFocus() } }
        val c = lockColors()
        val panelText = c.text
        val panelDim = c.dim
        val panelAccent = c.accent
        val fieldBackground = c.field
        Panel(confLockCornerRadius(), c.background) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val shows = confShowOnLock().map { it.lowercase() }
                val clock = if ("clock" in shows) formatted(now, confLockClock() ?: confTimeFormat()) else ""
                val date = if ("clock" in shows) formatted(now, confLockDate()) else ""
                if (clock.isNotEmpty()) BasicText(clock, style = TextStyle(color = panelText, fontSize = 72.sp, fontWeight = FontWeight.Light))
                if (date.isNotEmpty()) BasicText(date, style = TextStyle(color = panelDim, fontSize = 18.sp))
                // The battery, if there is one (text only: nothing here but the password field takes input).
                if ("battery" in shows) wm.barModel.status.battery?.let { b ->
                    Spacer(Modifier.height(6.dp))
                    BasicText("${b.percent} %${if (b.charging) " ⚡" else ""}", style = TextStyle(color = panelDim, fontSize = 14.sp))
                }
                Spacer(Modifier.height(40.dp))
                if (req?.data?.get("kind") == "auth_request_user") {
                    @Suppress("UNCHECKED_CAST")
                    val users = req.data["users"] as? List<String> ?: emptyList()
                    BasicText(tr(message), style = TextStyle(color = panelDim, fontSize = 14.sp))
                    Spacer(Modifier.height(12.dp))
                    for (user in users) {
                        BasicText(
                            user,
                            Modifier.clickable { reply(req, mapOf("user" to user)) }.padding(8.dp),
                            style = TextStyle(color = panelText, fontSize = 20.sp),
                        )
                    }
                } else {
                    BasicText((req?.data?.get("user") as String?) ?: SystemInfo.user(), style = TextStyle(color = panelText, fontSize = 20.sp))
                    Spacer(Modifier.height(16.dp))
                    BasicTextField(
                        password, { password = it },
                        Modifier.width(320.dp).focusRequester(focus)
                            .background(fieldBackground, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp)
                            .onPreviewKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                                    request?.let { r -> if (r.data["kind"] == "auth_request_cred") reply(r, mapOf("cred" to password)) }
                                    password = ""
                                    true
                                } else {
                                    false
                                }
                            },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        textStyle = TextStyle(color = panelText, fontSize = 18.sp, textAlign = TextAlign.Center),
                        cursorBrush = SolidColor(panelAccent),
                    )
                    Spacer(Modifier.height(12.dp))
                    BasicText(if (req == null && message.isEmpty()) "" else tr(message), style = TextStyle(color = panelDim, fontSize = 14.sp))
                }
            }
        }
    }

    private fun reply(req: AuthRequest, data: Map<String, Any?>) {
        if (req.replied) return
        req.replied = true
        message = "…"
        req.callback(data)
    }
}

/** A program as the desktop knows it (a .desktop file): its name, what it says, how to start it. */
data class DesktopApp(
    val name: String, val comment: String, val command: String, val keywords: String, val icon: String = "", val id: String = "",
    /** An entry of the configuration (launcher { entries { } }): what it does instead of a command. */
    val run: ((Layout) -> Unit)? = null,
) {
    companion object {
        /** The programs installed (applications folders of XDG_DATA_HOME and XDG_DATA_DIRS; the user's first). */
        fun installed(): List<DesktopApp> {
            val home = System.getenv("HOME") ?: System.getProperty("user.home")
            val dataHome = System.getenv("XDG_DATA_HOME") ?: "$home/.local/share"
            val dataDirs = (System.getenv("XDG_DATA_DIRS") ?: "/usr/local/share:/usr/share").split(':')
            val seen = HashSet<String>()
            val apps = ArrayList<DesktopApp>()
            for (dir in listOf(dataHome) + dataDirs + "/var/lib/flatpak/exports/share" + "$home/.local/share/flatpak/exports/share") {
                val folder = Path.of(dir, "applications")
                if (!Files.isDirectory(folder)) continue
                Files.walk(folder).use { files ->
                    for (file in files.filter { it.toString().endsWith(".desktop") }.toList()) {
                        val id = folder.relativize(file).toString().replace('/', '-')
                        if (!seen.add(id)) continue
                        parse(file)?.copy(id = id.removeSuffix(".desktop"))?.let(apps::add)
                    }
                }
            }
            log.info { "${apps.size} programs for the launcher" }
            return apps.sortedBy { it.name.lowercase() }
        }

        // A .desktop file's [Desktop Entry], or null for one not to show.
        private fun parse(file: Path): DesktopApp? = try {
            val entry = HashMap<String, String>()
            var inEntry = false
            for (line in Files.readAllLines(file)) {
                val t = line.trim()
                if (t.startsWith("[")) {
                    inEntry = t == "[Desktop Entry]"
                    continue
                }
                if (!inEntry || '=' !in t || t.startsWith("#")) continue
                entry.putIfAbsent(t.substringBefore('=').trim(), t.substringAfter('=').trim())
            }
            val show = entry["Type"] == "Application" && entry["NoDisplay"] != "true" && entry["Hidden"] != "true"
            val exec = entry["Exec"]
            if (!show || exec == null) {
                null
            } else {
                // %f, %U and the like: nothing to open, so left out.
                var command = exec.replace(Regex("%[fFuUdDnNickvm]"), "").trim()
                if (entry["Terminal"] == "true") command = "${defaultTerminal()} -e $command"
                DesktopApp(entry["Name"] ?: file.fileName.toString().removeSuffix(".desktop"), entry["Comment"] ?: entry["GenericName"] ?: "", command, entry["Keywords"] ?: "", entry["Icon"] ?: "")
            }
        } catch (_: Exception) {
            null
        }

        /** The programs for what was typed: names first (those starting with it first), then words. */
        // Typed on the Russian layout meaning the Latin one ("ашкуащч" is "firefox").
        private val RU = "йцукенгшщзхъфывапролджэячсмитьбю."
        private val EN = "qwertyuiop[]asdfghjkl;'zxcvbnm,./"

        private fun latinOf(text: String): String = text.map { c -> RU.indexOf(c).let { if (it >= 0) EN[it] else c } }.joinToString("")

        fun matching(apps: List<DesktopApp>, query: String): List<DesktopApp> {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return apps
            val found = matchingExactly(apps, q)
            val latin = latinOf(q)
            return if (found.isNotEmpty() || latin == q) found else matchingExactly(apps, latin)
        }

        private fun matchingExactly(apps: List<DesktopApp>, q: String): List<DesktopApp> {
            fun score(app: DesktopApp): Int? {
                val name = app.name.lowercase()
                return when {
                    name.startsWith(q) -> 0
                    name.split(' ', '-').any { it.startsWith(q) } -> 1
                    q in name -> 2
                    q in app.keywords.lowercase() || q in app.comment.lowercase() -> 3
                    q in app.command.lowercase() -> 4
                    else -> null
                }
            }
            return apps.mapNotNull { app -> score(app)?.let { it to app } }.sortedWith(compareBy({ it.first }, { it.second.name.length })).map { it.second }
        }
    }
}
