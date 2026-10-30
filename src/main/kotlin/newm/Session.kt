package newm

import dev.caulis.log.Logger
import java.nio.file.Files
import java.nio.file.Path

private val log = Logger("session")

// Terminals: what is kept of them is the folder of the shell inside (not the terminal's own).
private val TERMINALS = setOf("foot", "footclient", "alacritty", "kitty", "org.wezfurlong.wezterm", "com.mitchellh.ghostty", "konsole",
    "org.gnome.terminal", "gnome-terminal-server", "org.gnome.console", "xterm", "urxvt", "st")

// A terminal's own command for one window, where one process draws several (foot --server with
// footclient, kitty --single-instance, wezterm, konsole): each window comes back as its own.
private val TERMINAL_COMMANDS = mapOf("foot" to "foot", "footclient" to "foot", "kitty" to "kitty", "org.wezfurlong.wezterm" to "wezterm",
    "com.mitchellh.ghostty" to "ghostty", "konsole" to "konsole", "org.gnome.terminal" to "gnome-terminal",
    "gnome-terminal-server" to "gnome-terminal", "org.gnome.console" to "kgx")

// Flags of a terminal that serves windows to others (its command line opens none of its own).
private val SERVER_FLAGS = setOf("--server", "--single-instance", "-1")

// session { restoreAtLogin = false; maxAgeMinutes = 10; maxWindows = 20; exclude = listOf(...); staggerMs = 300 }
private val confRestoreAtLogin = configuredValue("session.restore_at_login", false)
private val confMaxAgeMinutes = configuredValue("session.max_age_minutes", 10)
private val confMaxWindows = configuredValue("session.max_windows", 20)
private val confExclude = configuredValue("session.exclude", emptyList<String>())
private val confStaggerMs = configuredValue("session.stagger_ms", 300)
// session { arguments = "options" }: what of a program's command line is kept — "options" (its
// options, "--ozone-platform=wayland", but no files, addresses or anything that looks like a
// password), "all", or "none" (the program alone). A terminal is always kept alone, in its folder.
private val confArguments = configuredValue("session.arguments", "options")
// session { interpreters; keepValues; secretWords; secretShortOptions }: the lists "options" goes by (see KeptCommandRules).
private val confInterpreters = configuredValue("session.interpreters", KeptCommandRules.INTERPRETERS)
private val confKeepValues = configuredValue("session.keep_values", KeptCommandRules.KEEP_VALUES)
private val confSecretWords = configuredValue("session.secret_words", KeptCommandRules.SECRET_WORDS)
private val confSecretShortOptions = configuredValue("session.secret_short_options", KeptCommandRules.SECRET_SHORT_OPTIONS)

/**
 * What arguments = "options" keeps of a command line, and what never.
 * [interpreters]: programs that run another one (python3 app.py, electron app.asar, wine
 * game.exe): their first argument that is not an option is kept with them (the version digits
 * at the end of a name aside: "python3.12", "electron43"). [keepValues]: options whose value,
 * written after them ("-P work", "--user-data-dir /x"), is kept with them: the profile, the
 * folder a program keeps its things in. [secretWords]: an option is left out when a word of its
 * name is one of these ("--api-key", "--password"), or its name holds one of five letters or
 * more ("--accesstoken"). [secretShortOptions]: options whose value is written right after them
 * ("-phunter2", as mysql takes it) are left out with it — but for a word of letters and
 * dashes ("-private-window").
 */
internal class KeptCommandRules(
    val interpreters: List<String> = INTERPRETERS,
    val keepValues: List<String> = KEEP_VALUES,
    val secretWords: List<String> = SECRET_WORDS,
    val secretShortOptions: List<String> = SECRET_SHORT_OPTIONS,
) {
    companion object {
        val INTERPRETERS = listOf("python", "java", "node", "perl", "ruby", "bash", "sh", "dash", "zsh", "fish", "lua", "luajit",
            "bun", "deno", "php", "electron", "wine", "wine64", "mono", "dotnet")
        val KEEP_VALUES = listOf("-P", "-profile", "--profile", "--profile-directory", "--user-data-dir", "-workdir", "--class",
            "--name", "--app-id", "-cp", "-classpath", "--class-path", "--module-path")
        val SECRET_SHORT_OPTIONS = listOf("-p")
        val SECRET_WORDS = listOf("pass", "password", "passwd", "passphrase", "pwd", "pw", "token", "secret", "key", "apikey",
            "auth", "authorization", "cookie", "cookies", "cred", "creds", "credential", "credentials", "otp", "pin")
    }

    fun isInterpreter(program: String): Boolean =
        program.substringAfterLast('/').trimEnd { it.isDigit() || it == '.' }.let { it.isNotEmpty() && it in interpreters }

    // "--Api-Key=x": the words "api", "key".
    fun isSecretOption(option: String): Boolean {
        val name = option.substringBefore('=').trimStart('-').lowercase()
        val words = name.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        return secretWords.any { secret -> val s = secret.lowercase(); s in words || (s.length >= 5 && s in name) }
    }

    // "-phunter2" (secretShortOptions "-p"), but not "-private-window".
    fun isSecretShortOption(option: String): Boolean = secretShortOptions.any { short ->
        short.length == 2 && option.length > 2 && option.startsWith(short) && option[2] != '-' &&
            !(option.drop(1).all { it.isLowerCase() || it == '-' } && '-' in option.drop(1))
    }
}

// An address with a user and password in it ("http://bob:hunter2@proxy").
private fun hasUserInfo(arg: String): Boolean {
    var at = arg.indexOf("://")
    while (at >= 0) {
        val rest = arg.substring(at + 3)
        val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        if ('@' in host) return true
        at = arg.indexOf("://", at + 3)
    }
    return false
}

// What looks like a token or a key: one long word of letters and digits both (and the usual
// signs of base64 and the like), no folder in it.
private fun looksLikeToken(value: String): Boolean =
    value.length >= 24 && value.none { it == '/' } && value.all { it.isLetterOrDigit() || it in "+=_-." } &&
        value.any { it.isDigit() } && value.any { it.isLetter() }

/**
 * What of [command] is kept to start the program again ([arguments]: "options", "all", "none";
 * see session.arguments; [rules]: what "options" keeps): never a terminal's command inside it,
 * files, addresses or secrets unless asked for.
 */
internal fun keptCommand(command: List<String>, terminal: Boolean, arguments: String = "options", rules: KeptCommandRules = KeptCommandRules()): List<String> {
    val program = command.firstOrNull() ?: return command
    // A terminal: itself, never what ran in it ("foot -e sudo pacman -Syu" would run again).
    if (terminal) return listOf(program)
    // A Flatpak's id ("flatpak run org.x.Y"): as it is.
    if (command.size >= 3 && command[0] == "flatpak" && command[1] == "run") return command.take(3)
    if (arguments == "all") return command
    if (arguments == "none") return listOf(program)
    val kept = arrayListOf(program)
    // An interpreter's program (python3 -u app.py, electron app.asar) is still to be kept.
    var script = rules.isInterpreter(program)
    var i = 1
    while (i < command.size) {
        val arg = command[i]
        val next = command.getOrNull(i + 1)
        val option = arg.length > 1 && arg.startsWith("-")
        // "--": what follows is files and addresses only.
        if (arg == "--") break
        when {
            // A secret in any form ("--password=…", "--token x", "-phunter2", an address with a
            // password): left out, and its value with it.
            option && rules.isSecretOption(arg) -> { if ('=' !in arg && next != null && !next.startsWith("-")) i++ }
            option && arg !in rules.keepValues && rules.isSecretShortOption(arg) -> {}
            hasUserInfo(arg) -> {}
            option && looksLikeToken(arg.substringAfter('=', "")) && arg.substringBefore('=') !in rules.keepValues -> {}
            // Its value with it ("-P work", "--user-data-dir /x").
            option && arg in rules.keepValues && next != null && !next.startsWith("-") -> {
                if (!hasUserInfo(next) && !looksLikeToken(next)) { kept += arg; kept += next }
                i++
            }
            // An address as the value ("--app=https://…"): an address is left out, unless its option is one to keep.
            option && "://" in arg && arg.substringBefore('=') !in rules.keepValues -> {}
            option -> kept += arg
            // The interpreter's program: the first one that is not an option.
            script -> { kept += arg; script = false }
            // Files, addresses, anything else: left out.
            else -> {}
        }
        i++
    }
    return kept
}

/** Whether [appId] is a terminal's (the usual ones, and the configuration's terminal). */
internal fun isTerminalApp(appId: String): Boolean = appId.lowercase().let { it in TERMINALS || it == configuredTerminal() }

// The configuration's terminal (terminal = "foot"): its program's name, a terminal too.
private fun configuredTerminal(): String? = Config.lookup("terminal")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    ?.substringBefore(' ')?.substringAfterLast('/')?.lowercase()

/**
 * The windows kept over a restart (`newm cmd restart keep`; with session.restore_at_login also
 * over a logout): each one's program (its command
 * line, a Flatpak's id), its folder (a terminal's: its shell's), its place (the tile, or where
 * it floats) and which one had the focus, written to ~/.local/state/newm/session.json; at the
 * next start the programs start again and each window goes back to its place as it opens.
 */
internal class SessionKeeper(private val wm: Layout) {
    private val file: Path by lazy {
        val state = System.getenv("XDG_STATE_HOME")?.takeIf { it.startsWith("/") } ?: "${System.getenv("HOME") ?: System.getProperty("user.home")}/.local/state"
        Path.of(state, "newm", "session.json")
    }

    // Windows still to come back (their places), and the views to look at once all are back.
    private val pending = ArrayList<Map<String, Any?>>()
    private var viewsToRestore: List<Map<String, Any?>> = emptyList()
    private var focusApp: String? = null

    // Programs not started yet because there were more than session.max_windows (their windows' entries).
    private var rest: List<Map<String, Any?>> = emptyList()

    /** How many programs were left out at the start (the launcher offers them). */
    fun restLeft(): Int = rest.count { it["start"] == true }

    // While windows come back (and a little after): not kept again meanwhile.
    private var restoringUntil = 0L

    /**
     * session { restoreAtLogin = true }: the windows are kept now and then (and when logging
     * out), so that the next login opens them again even after the computer was turned off.
     */
    fun startKeeping() {
        wm.caulis.every(60_000) {
            if (!confRestoreAtLogin() || stopped || System.currentTimeMillis() < restoringUntil || pending.isNotEmpty()) return@every
            save(reason = "login", quiet = true)
        }
    }

    // Not kept any more now and then (newm is ending).
    private var stopped = false

    /** session { restoreAtLogin }: the windows are kept for the next login. */
    val keepsForLogin: Boolean get() = confRestoreAtLogin()

    /**
     * A restart without `keep`: what was kept now and then for the next login is not opened
     * after it (nor kept again meanwhile). The next logout keeps them again.
     */
    fun forgetForRestart() {
        stopped = true
        val reason = runCatching { Json.parseObject(Files.readString(file))["reason"] }.getOrNull() ?: return
        if (reason == "login" && runCatching { Files.deleteIfExists(file) }.getOrDefault(false)) log.info { "Restart without keep: the windows kept for the next login are not opened after it" }
    }

    /** Keeps the windows open now ([reason]: "restart" for `restart keep`, "login" for the next login); the number kept. */
    fun save(reason: String = "restart", quiet: Boolean = false): Int {
        val seenPids = HashSet<Int>()
        val windows = ArrayList<Map<String, Any?>>()
        val all = wm.viewsNow()
        // A terminal process's windows, in the order they opened.
        val terminalWindows = all.filter { it.panel == null && it.pid > 0 && isTerminal(it.appId) }.groupBy { it.pid }
        for (view in all) {
            if (view.panel != null) continue
            val found = wm.state.findViewOrNull(view) ?: continue
            val (vs, _, wsHandle) = found
            if (vs.isLayer) continue
            val pid = view.pid
            if (pid <= 0) continue
            var command = commandOf(pid) ?: continue
            // A terminal serving several windows (or a server): each window started on its own,
            // in its own shell's folder (never the server's command: it opens no window).
            val siblings = terminalWindows[pid].orEmpty()
            val shared = isTerminal(view.appId) && (siblings.size > 1 || isServer(command))
            if (shared) command = listOf(TERMINAL_COMMANDS[view.appId.lowercase()] ?: command.first())
            // (What ran inside a terminal, files, addresses and passwords are not kept: session.arguments.)
            command = keptCommand(command, isTerminal(view.appId), confArguments(), KeptCommandRules(confInterpreters(), confKeepValues(), confSecretWords(), confSecretShortOptions()))
            // One start for a program's several windows (each still gets its place).
            val start = shared || pid !in seenPids
            seenPids += pid
            val dir = if (isTerminal(view.appId)) shellFolder(pid, siblings.indexOf(view), siblings.size) else folderOf(pid)
            if (shared) log.info { "Terminal window of a shared process kept on its own: ${command.first()} in $dir" }
            windows += mapOf(
                "app" to view.appId, "title" to view.title, "command" to command, "start" to start,
                "dir" to dir, "workspace" to wm.workspaces.indexOfFirst { it.handle == wsHandle },
                "tiled" to vs.isTiled, "i" to vs.i, "j" to vs.j, "w" to vs.w, "h" to vs.h, "page" to vs.page, "slot" to vs.slot,
                "fx" to vs.floatPos.first, "fy" to vs.floatPos.second, "fw" to vs.floatSize.first, "fh" to vs.floatSize.second,
                "focused" to view.isFocused(),
            )
        }
        val views = wm.workspaces.map { ws -> wm.state.workspaceState(ws).let { mapOf("i" to it.i, "j" to it.j, "size" to it.size) } }
        try {
            privateFolder(file.parent)
            // (Readable by you only: a command line may say more than it should.)
            writePrivately(file, Json.write(mapOf("saved" to System.currentTimeMillis(), "reason" to reason, "windows" to windows, "views" to views)))
        } catch (e: Exception) {
            log.warn("The windows cannot be kept: $e")
            return 0
        }
        if (quiet) log.trace { "Kept ${windows.size} windows for the next login" } else log.info { "Kept ${windows.size} windows ($reason)" }
        return windows.size
    }

    /**
     * At the start: the windows kept open again — those of `restart keep` (session.max_age_minutes
     * ago at most), and with session.restore_at_login those of the last session. At most
     * session.max_windows programs, one after another; a notice offers the rest.
     */
    fun restoreAtStart() {
        // The file read on a thread of its own (the compositor's thread does not wait for the
        // disk at the start); what it says is acted on there. (Not kept meanwhile.)
        restoringUntil = System.currentTimeMillis() + 60_000
        Thread.ofPlatform().daemon().name("newm-session-read").start {
            val saved = try {
                Json.parseObject(Files.readString(file)).also { Files.deleteIfExists(file) }
            } catch (_: Exception) {
                null
            }
            wm.caulis.post {
                if (saved != null) restoreFrom(saved) else restoringUntil = 0L
            }
        }
    }

    private fun restoreFrom(saved: Map<String, Any?>) {
        restoringUntil = 0L
        // (Kept before "reason" was written: by `restart keep`.)
        val reason = saved["reason"] as? String ?: "restart"
        if (reason == "login" && !confRestoreAtLogin()) return log.info { "Windows of the last session kept, but session.restore_at_login is off: not opened again" }
        val at = (saved["saved"] as? Number)?.toLong() ?: 0L
        val maxAge = confMaxAgeMinutes()
        if (reason == "restart" && maxAge > 0 && System.currentTimeMillis() - at > maxAge * 60_000L) {
            return log.info { "The windows kept are older than $maxAge minutes: not opened again" }
        }
        @Suppress("UNCHECKED_CAST")
        val all = (saved["windows"] as? List<Map<String, Any?>>).orEmpty()
        // session { exclude }: by app id or by the program's name.
        val exclude = confExclude().map { it.lowercase() }.toSet()
        val windows = all.filter { w -> !excluded(w, exclude) }
        if (windows.size < all.size) log.info { "${all.size - windows.size} windows left out by session.exclude" }
        @Suppress("UNCHECKED_CAST")
        viewsToRestore = (saved["views"] as? List<Map<String, Any?>>).orEmpty()
        focusApp = windows.firstOrNull { it["focused"] == true }?.get("app") as? String
        // At most max_windows programs (the focused one's first): the others wait for the launcher's
        // "Reopen" or `newm cmd restore-rest`.
        val max = confMaxWindows().coerceAtLeast(0)
        val starts = windows.filter { it["start"] == true }.sortedByDescending { (it["app"] as? String).equals(focusApp, ignoreCase = true) }
        val now = starts.take(max)
        val later = starts.drop(max)
        // A program's other windows (start = false) come with it.
        fun programOf(w: Map<String, Any?>) = w["command"] to w["app"]
        val laterPrograms = later.map(::programOf).toSet()
        rest = windows.filter { programOf(it) in laterPrograms }
        pending += windows.filter { programOf(it) !in laterPrograms }
        log.info { "Opening ${now.size} programs again (${windows.size} windows kept, $reason)${if (later.isNotEmpty()) "; ${later.size} left for later" else ""}" }
        startAll(now)
        if (later.isNotEmpty()) {
            wm.caulis.notify(
                newm.bar.tr("%s more windows were open. Open them from the launcher (“Reopen”) or with newm cmd restore-rest.").replace("%s", later.size.toString()),
                title = "newm",
            )
        }
    }

    /** The programs left out at the start (more than session.max_windows): opened now. */
    fun restoreRest() {
        val windows = rest
        if (windows.isEmpty()) return
        rest = emptyList()
        log.info { "Opening the rest again: ${windows.count { it["start"] == true }} programs" }
        pending += windows
        startAll(windows.filter { it["start"] == true })
    }

    // Starts [windows]' programs one after another (session.stagger_ms apart: not all at once),
    // through Caulis's start queue: once newm is shown (after the reveal), after autostart's.
    private fun startAll(windows: List<Map<String, Any?>>) {
        val gap = confStaggerMs().coerceAtLeast(0).toLong()
        // (The reveal may still be on: its longest wait counts too.)
        val waiting = if (wm.caulis.revealed) 0L else wm.caulis.reveal.maxWaitMillis + wm.caulis.reveal.fadeMillis
        restoringUntil = System.currentTimeMillis() + waiting + windows.size * gap + 30_000
        for (w in windows) {
            @Suppress("UNCHECKED_CAST")
            val command = (w["command"] as? List<String>).orEmpty().takeIf { it.isNotEmpty() } ?: continue
            val dir = (w["dir"] as? String)?.let { Path.of(it) }?.takeIf { Files.isDirectory(it) }
            log.info { "Opening again (in the start's queue): ${command.firstOrNull()?.substringAfterLast('/')}" }
            runCatching { wm.caulis.startLater(command, dir, gap) }.onFailure { log.warn("${command.first()} cannot be started again: $it") }
        }
        // Whatever did not come back in a while: the views as they were all the same. (Only the
        // last start's wait ends it: the rest opened meanwhile keeps its places waited for.)
        val round = ++startRound
        wm.caulis.after(15_000 + waiting + windows.size * gap) { if (round == startRound) finish() }
    }

    // A window left out by session.exclude (its app id, or its program's name).
    private fun excluded(w: Map<String, Any?>, exclude: Set<String>): Boolean {
        if (exclude.isEmpty()) return false
        val app = (w["app"] as? String)?.lowercase()
        @Suppress("UNCHECKED_CAST")
        val command = (w["command"] as? List<String>).orEmpty()
        val program = command.firstOrNull()?.substringAfterLast('/')?.lowercase()
        // (A Flatpak: "flatpak run <id>".)
        val flatpak = if (program == "flatpak") command.getOrNull(2)?.lowercase() else null
        return app in exclude || program in exclude || flatpak in exclude
    }

    /** A window came: if one like it is waited for, it goes to that one's place. */
    fun shown(view: View) {
        if (pending.isEmpty()) return
        // (A dialog or a splash screen of the program is not one of its windows kept.)
        if (view.parent != null) return
        val sameApp = pending.filter { (it["app"] as? String).equals(view.appId, ignoreCase = true) }
        if (sameApp.isEmpty()) return
        // The ones that were tiled (or floating) as this one opens, if there are such.
        val same = sameApp.filter { (it["tiled"] == true) == !view.opensFloating }.ifEmpty { sameApp }
        // Several of one program (two terminals): the one in the same folder, else of the same title.
        val dir = if (same.size > 1) (if (isTerminal(view.appId)) shellFolder(view.pid, 0, 1) else folderOf(view.pid)) else null
        val entry = same.firstOrNull { dir != null && it["dir"] == dir } ?: same.firstOrNull { it["title"] == view.title } ?: same.first()
        pending.remove(entry)
        // (A moment later: after newm's own placing and its animation.)
        wm.caulis.after(350) {
            wm.animateTo({ s0 ->
                val found = s0.findViewOrNull(view) ?: return@animateTo null to s0
                val (_, wsState0, wsHandle) = found
                var ws = wm.workspaces.firstOrNull { it.handle == wsHandle } ?: return@animateTo null to s0
                // On the workspace it was on (when that one is still there).
                var s = s0
                var wsState = wsState0
                val target = (entry["workspace"] as? Number)?.toInt()?.let { wm.workspaces.getOrNull(it) }
                if (target != null && target.handle != ws.handle) {
                    log.info { "Kept window back on its workspace ${wm.workspaces.indexOf(target)}: $view" }
                    s = s0.copy()
                    s.moveViewState(view, ws, target)
                    ws = target
                    wsState = s.workspaceState(target)
                }
                fun d(key: String) = (entry[key] as? Number)?.toDouble()
                val next = wsState.replacingViewState(view) {
                    if (entry["tiled"] == true && isTiled) {
                        d("i")?.let { i = it }; d("j")?.let { j = it }; d("w")?.let { w = it }; d("h")?.let { h = it }
                        // (On screens: its screen and place there, the layout tiling it again.)
                        if (Screens.on()) { d("page")?.let { page = it.toInt() }; d("slot")?.let { slot = it.toInt() }; sized = null }
                    } else if (entry["tiled"] != true && !isTiled) {
                        val fx = d("fx") ?: floatPos.first
                        val fy = d("fy") ?: floatPos.second
                        floatPos = fx to fy
                    }
                }
                // The view as it was too (the places are in its grid: without it they came out
                // too big, the camera elsewhere, until all windows were back).
                null to s.settingWorkspaceState(ws, withView(next, wm.workspaces.indexOf(ws)))
            }, NewmAnimations.resolveSeconds("sessionRestore", 0.25))
            if ((entry["app"] as? String).equals(focusApp, ignoreCase = true)) view.focus()
            if (pending.isEmpty()) wm.caulis.after(400) { finish() }
        }
    }

    // [wsState] seen as the workspace at [index] was seen when kept: where and how far.
    private fun withView(wsState: WorkspaceState, index: Int): WorkspaceState {
        val v = viewsToRestore.getOrNull(index) ?: return wsState
        val i = (v["i"] as? Number)?.toDouble() ?: return wsState
        val j = (v["j"] as? Number)?.toDouble() ?: return wsState
        val size = (v["size"] as? Number)?.toDouble() ?: return wsState
        return wsState.copy(i = i, j = j, size = size, sizeOrigin = null)
    }

    // All back (or waited enough): each workspace's view as it was.
    // Counted up with each startAll (the start's programs, the rest later).
    private var startRound = 0

    private fun finish() {
        val views = viewsToRestore
        pending.clear()
        if (views.isEmpty()) return
        viewsToRestore = emptyList()
        wm.animateTo({ s ->
            var next = s
            for ((index, v) in views.withIndex()) {
                val ws = wm.workspaces.getOrNull(index) ?: continue
                val i = (v["i"] as? Number)?.toDouble() ?: continue
                val j = (v["j"] as? Number)?.toDouble() ?: continue
                val size = (v["size"] as? Number)?.toDouble() ?: continue
                next = next.replacingWorkspaceState(ws) { copy(i = i, j = j, size = size) }
            }
            null to next
        }, NewmAnimations.resolveSeconds("sessionRestore", 0.3))
        log.info { "The windows kept are back" }
    }

    // The program's command line; a Flatpak's: "flatpak run <its id>".
    private fun commandOf(pid: Int): List<String>? = try {
        val flatpak = Path.of("/proc/$pid/root/.flatpak-info")
        if (Files.isReadable(flatpak)) {
            val name = Files.readAllLines(flatpak).firstOrNull { it.startsWith("name=") }?.substringAfter('=')
            name?.let { listOf("flatpak", "run", it) }
        } else {
            val args = String(Files.readAllBytes(Path.of("/proc/$pid/cmdline"))).split('\u0000').filter { it.isNotEmpty() }
            // Some programs (Spotify, Chromium's) rewrite theirs into one line with spaces:
            // "…/spotify --ozone-platform=wayland" is no file — split again at the spaces.
            val first = args.firstOrNull()
            if (args.size == 1 && first != null && ' ' in first && !Files.exists(Path.of(first))) first.trim().split(Regex("\\s+"))
            else args.takeIf { it.isNotEmpty() }
        }
    } catch (_: Exception) {
        null
    }

    private fun isTerminal(appId: String) = isTerminalApp(appId)

    private fun isServer(command: List<String>): Boolean =
        command.any { it in SERVER_FLAGS } || (command.firstOrNull()?.substringAfterLast('/') == "foot" && "-s" in command)

    // A program's own folder.
    private fun folderOf(pid: Int): String? = try {
        Files.readSymbolicLink(Path.of("/proc/$pid/cwd")).toString()
    } catch (_: Exception) {
        null
    }

    /**
     * A terminal window's folder: its shell's. A terminal process with [count] windows has a shell
     * for each, started as the windows opened: the [index]th one's when they add up, else the first.
     */
    private fun shellFolder(pid: Int, index: Int, count: Int): String? {
        val children = runCatching {
            Files.list(Path.of("/proc/$pid/task")).use { tasks ->
                tasks.toList().flatMap { t -> runCatching { Files.readString(t.resolve("children")).trim().split(' ').mapNotNull { it.toIntOrNull() } }.getOrDefault(emptyList()) }
            }.sorted()
        }.getOrDefault(emptyList())
        val child = if (count > 1 && children.size == count) children.getOrNull(index) else children.firstOrNull()
        return folderOf(child ?: pid)
    }
}
