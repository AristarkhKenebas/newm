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

/**
 * The windows kept over a restart (`newm cmd restart keep`): each one's program (its command
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

    /** Keeps the windows open now; the number kept. */
    fun save(): Int {
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
            // One start for a program's several windows (each still gets its place).
            val start = shared || pid !in seenPids
            seenPids += pid
            val dir = if (isTerminal(view.appId)) shellFolder(pid, siblings.indexOf(view), siblings.size) else folderOf(pid)
            if (shared) log.info { "Terminal window of a shared process kept on its own: ${command.first()} in $dir" }
            windows += mapOf(
                "app" to view.appId, "title" to view.title, "command" to command, "start" to start,
                "dir" to dir, "workspace" to wm.workspaces.indexOfFirst { it.handle == wsHandle },
                "tiled" to vs.isTiled, "i" to vs.i, "j" to vs.j, "w" to vs.w, "h" to vs.h,
                "fx" to vs.floatPos.first, "fy" to vs.floatPos.second, "fw" to vs.floatSize.first, "fh" to vs.floatSize.second,
                "focused" to view.isFocused(),
            )
        }
        val views = wm.workspaces.map { ws -> wm.state.workspaceState(ws).let { mapOf("i" to it.i, "j" to it.j, "size" to it.size, "unit" to it.unit) } }
        try {
            Files.createDirectories(file.parent)
            Files.writeString(file, Json.write(mapOf("saved" to System.currentTimeMillis(), "windows" to windows, "views" to views)))
        } catch (e: Exception) {
            log.warn("The windows cannot be kept: $e")
            return 0
        }
        log.info { "Kept ${windows.size} windows for the restart" }
        return windows.size
    }

    /** At the start: the windows kept (a few minutes ago at most) open again. */
    fun restoreAtStart() {
        val saved = try {
            Json.parseObject(Files.readString(file)).also { Files.deleteIfExists(file) }
        } catch (_: Exception) {
            return
        }
        val at = (saved["saved"] as? Number)?.toLong() ?: 0L
        if (System.currentTimeMillis() - at > 10 * 60_000L) return log.info { "The windows kept are too old: not opened again" }
        @Suppress("UNCHECKED_CAST")
        val windows = (saved["windows"] as? List<Map<String, Any?>>).orEmpty()
        @Suppress("UNCHECKED_CAST")
        viewsToRestore = (saved["views"] as? List<Map<String, Any?>>).orEmpty()
        focusApp = windows.firstOrNull { it["focused"] == true }?.get("app") as? String
        pending += windows
        for (w in windows) {
            if (w["start"] != true) continue
            @Suppress("UNCHECKED_CAST")
            val command = (w["command"] as? List<String>).orEmpty().takeIf { it.isNotEmpty() } ?: continue
            val dir = (w["dir"] as? String)?.let { Path.of(it) }?.takeIf { Files.isDirectory(it) }
            log.info { "Opening again: ${command.joinToString(" ")}" }
            runCatching { wm.caulis.spawn(command, dir) }.onFailure { log.warn("${command.first()} cannot be started again: $it") }
        }
        // Whatever did not come back in a while: the views as they were all the same.
        wm.caulis.after(15_000) { finish() }
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
                    } else if (entry["tiled"] != true && !isTiled) {
                        val fx = d("fx") ?: floatPos.first
                        val fy = d("fy") ?: floatPos.second
                        floatPos = fx to fy
                    }
                }
                // The view as it was too (the places are in its grid: without it they came out
                // too big, the camera elsewhere, until all windows were back).
                null to s.settingWorkspaceState(ws, withView(next, wm.workspaces.indexOf(ws)))
            }, 0.25)
            if ((entry["app"] as? String).equals(focusApp, ignoreCase = true)) view.focus()
            if (pending.isEmpty()) wm.caulis.after(400) { finish() }
        }
    }

    // [wsState] seen as the workspace at [index] was seen when kept: where, how far, its grid.
    private fun withView(wsState: WorkspaceState, index: Int): WorkspaceState {
        val v = viewsToRestore.getOrNull(index) ?: return wsState
        val i = (v["i"] as? Number)?.toDouble() ?: return wsState
        val j = (v["j"] as? Number)?.toDouble() ?: return wsState
        val size = (v["size"] as? Number)?.toDouble() ?: return wsState
        // (Kept before the grid's unit was: one tile is one tile.)
        val unit = (v["unit"] as? Number)?.toDouble() ?: 1.0
        return wsState.copy(i = i, j = j, size = size, sizeOrigin = null).also { c -> c.unit = unit }
    }

    // All back (or waited enough): each workspace's view as it was.
    private fun finish() {
        val views = viewsToRestore
        if (views.isEmpty()) return
        viewsToRestore = emptyList()
        pending.clear()
        wm.animateTo({ s ->
            var next = s
            for ((index, v) in views.withIndex()) {
                val ws = wm.workspaces.getOrNull(index) ?: continue
                val i = (v["i"] as? Number)?.toDouble() ?: continue
                val j = (v["j"] as? Number)?.toDouble() ?: continue
                val size = (v["size"] as? Number)?.toDouble() ?: continue
                val unit = (v["unit"] as? Number)?.toDouble() ?: 1.0
                next = next.replacingWorkspaceState(ws) { copy(i = i, j = j, size = size).also { c -> c.unit = unit } }
            }
            null to next
        }, 0.3)
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

    private fun isTerminal(appId: String) = appId.lowercase() in TERMINALS

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
