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
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val log = Logger("panels")

private val confLockH = configuredValue("panels.lock.h", 0.6)
private val confLockW = configuredValue("panels.lock.w", 0.7)
private val confLockCornerRadius = configuredValue("panels.lock.corner_radius", 50.0)
private val confLauncherH = configuredValue("panels.launcher.h", 0.8)
private val confLauncherW = configuredValue("panels.launcher.w", 0.8)
private val confLauncherCornerRadius = configuredValue("panels.launcher.corner_radius", 0.0)

private val panelBackground = Color(0x2E, 0x34, 0x40, 0xEE)
private val panelText = Color(0xEC, 0xEF, 0xF4)
private val panelDim = Color(0xD8, 0xDE, 0xE9, 0x99)
private val panelAccent = Color(0x88, 0xC0, 0xD0)
private val fieldBackground = Color(0x3B, 0x42, 0x52)

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
            previous != null && previous.data["kind"] == "auth_request_cred" -> "That was not it. Again:"
            else -> (req.data["message"] as String?)?.takeIf { it != "Password?" } ?: ""
        }
    }

    /** The lock screen gets the keyboard (and newm's own and Caulis's keys stop meanwhile). */
    fun locked() {
        lockWidget?.let { wm.caulis.desktop.focusWidget(it) }
        message = ""
    }

    fun unlocked() {
        request = null
        if (wm.caulis.desktop.keyboardWidget === lockWidget) wm.caulis.desktop.focusWidget(null)
    }

    // --- the launcher

    private var launcherWidget: ComposeWidget? = null
    private var apps by mutableStateOf<List<DesktopApp>>(emptyList())
    private var opened by mutableIntStateOf(0)

    /** The launcher opens: the keyboard to it, the programs looked up anew. */
    fun launcherOpened() {
        launcherWidget?.let { wm.caulis.desktop.focusWidget(it) }
        Thread({
            val found = DesktopApp.installed()
            wm.caulis.post { apps = found }
        }, "newm-apps").apply { isDaemon = true }.start()
        opened++
    }

    fun launcherClosed() {
        if (wm.caulis.desktop.keyboardWidget === launcherWidget) wm.caulis.desktop.focusWidget(null)
    }

    /** True while newm's own launcher (not a panel program) is in use. */
    val ownLauncher: Boolean get() = wm.panels().none { it.panel == "launcher" }

    /** True while newm's own lock screen (not a panel program) is in use. */
    val ownLock: Boolean get() = wm.panels().none { it.panel == "lock" }

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
            place = { m -> if (ownLauncher) area(m, wm.layoutDown.launcherPerc, confLauncherW(), confLauncherH()) else null },
            level = DrawingLevel.ON_TOP, takesKeyboard = true,
        ) { Launcher() }
        lockWidget = lock
        launcherWidget = launcher
        wm.caulis.addWidget(lock)
        wm.caulis.addWidget(launcher)
    }

    @Composable
    private fun Panel(radius: Double, content: @Composable () -> Unit) {
        Box(
            Modifier.fillMaxSize().background(panelBackground, RoundedCornerShape(radius.dp)).padding(32.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
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
        Panel(confLockCornerRadius()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                BasicText(now.format(DateTimeFormatter.ofPattern("HH:mm")), style = TextStyle(color = panelText, fontSize = 72.sp, fontWeight = FontWeight.Light))
                BasicText(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = TextStyle(color = panelDim, fontSize = 18.sp))
                Spacer(Modifier.height(40.dp))
                if (req?.data?.get("kind") == "auth_request_user") {
                    @Suppress("UNCHECKED_CAST")
                    val users = req.data["users"] as? List<String> ?: emptyList()
                    BasicText(message, style = TextStyle(color = panelDim, fontSize = 14.sp))
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
                    BasicText(if (req == null && message.isEmpty()) "" else message, style = TextStyle(color = panelDim, fontSize = 14.sp))
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

    @Composable
    private fun Launcher() {
        var query by remember(opened) { mutableStateOf("") }
        var selected by remember(opened) { mutableIntStateOf(0) }
        val focus = remember { FocusRequester() }
        val list = rememberLazyListState()
        LaunchedEffect(opened) { runCatching { focus.requestFocus() } }
        val shown = DesktopApp.matching(apps, query)
        if (selected >= shown.size) selected = (shown.size - 1).coerceAtLeast(0)
        LaunchedEffect(selected) { if (shown.isNotEmpty()) list.animateScrollToItem(selected) }
        fun start(app: DesktopApp) {
            query = ""
            wm.launchApp(app.command)
        }
        Panel(confLauncherCornerRadius()) {
            Column(Modifier.fillMaxSize()) {
                BasicTextField(
                    query, { query = it; selected = 0 },
                    Modifier.fillMaxWidth().focusRequester(focus)
                        .background(fieldBackground, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp)
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionDown -> { selected = (selected + 1).coerceAtMost(shown.size - 1); true }
                                Key.DirectionUp -> { selected = (selected - 1).coerceAtLeast(0); true }
                                Key.Enter, Key.NumPadEnter -> { shown.getOrNull(selected)?.let(::start); true }
                                Key.Escape -> { wm.exitOverlay(); true }
                                else -> false
                            }
                        },
                    singleLine = true,
                    textStyle = TextStyle(color = panelText, fontSize = 20.sp),
                    cursorBrush = SolidColor(panelAccent),
                    decorationBox = { field ->
                        if (query.isEmpty()) BasicText("Type to find a program", style = TextStyle(color = panelDim, fontSize = 20.sp))
                        field()
                    },
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(shown) { i, app ->
                        Column(
                            Modifier.fillMaxWidth()
                                .background(if (i == selected) fieldBackground else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { start(app) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            BasicText(app.name, style = TextStyle(color = if (i == selected) panelAccent else panelText, fontSize = 17.sp))
                            if (app.comment.isNotEmpty()) BasicText(app.comment, style = TextStyle(color = panelDim, fontSize = 13.sp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** A program as the desktop knows it (a .desktop file): its name, what it says, how to start it. */
data class DesktopApp(val name: String, val comment: String, val command: String, val keywords: String) {
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
                        parse(file)?.let(apps::add)
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
                if (entry["Terminal"] == "true") command = "${System.getenv("TERMINAL") ?: "alacritty"} -e $command"
                DesktopApp(entry["Name"] ?: file.fileName.toString().removeSuffix(".desktop"), entry["Comment"] ?: entry["GenericName"] ?: "", command, entry["Keywords"] ?: "")
            }
        } catch (_: Exception) {
            null
        }

        /** The programs for what was typed: names first (those starting with it first), then words. */
        fun matching(apps: List<DesktopApp>, query: String): List<DesktopApp> {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return apps
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
            return apps.mapNotNull { app -> score(app)?.let { it to app } }.sortedBy { it.first }.map { it.second }
        }
    }
}
