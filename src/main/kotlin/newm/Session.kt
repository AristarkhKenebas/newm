package newm

import dev.caulis.log.Logger
import java.nio.file.Files
import java.nio.file.Path

private val log = Logger("session")

// Terminals: what is kept of them is the folder of the shell inside (not the terminal's own).
private val TERMINALS = setOf("foot", "footclient", "alacritty", "kitty", "org.wezfurlong.wezterm", "com.mitchellh.ghostty", "konsole",
    "org.gnome.terminal", "gnome-terminal-server", "org.gnome.console", "xterm", "urxvt", "st")

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
        for (view in wm.viewsNow()) {
            if (view.panel != null) continue
            val found = wm.state.findViewOrNull(view) ?: continue
            val (vs, _, wsHandle) = found
            if (vs.isLayer) continue
            val pid = view.pid
            if (pid <= 0) continue
            // One start for a program's several windows (each still gets its place).
            val start = pid !in seenPids
            seenPids += pid
            val command = commandOf(pid) ?: continue
            windows += mapOf(
                "app" to view.appId, "title" to view.title, "command" to command, "start" to start,
                "dir" to folderOf(pid, view.appId), "workspace" to wm.workspaces.indexOfFirst { it.handle == wsHandle },
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
        val same = pending.filter { (it["app"] as? String).equals(view.appId, ignoreCase = true) }
        if (same.isEmpty()) return
        // Several of one program (two terminals): the one in the same folder, else of the same title.
        val dir = if (same.size > 1) folderOf(view.pid, view.appId) else null
        val entry = same.firstOrNull { dir != null && it["dir"] == dir } ?: same.firstOrNull { it["title"] == view.title } ?: same.first()
        pending.remove(entry)
        // (A moment later: after newm's own placing and its animation.)
        wm.caulis.after(350) {
            wm.animateTo({ s ->
                val found = s.findViewOrNull(view) ?: return@animateTo null to s
                val (_, wsState, wsHandle) = found
                val ws = wm.workspaces.firstOrNull { it.handle == wsHandle } ?: return@animateTo null to s
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
        val unit = (v["unit"] as? Number)?.toDouble()
        return wsState.copy(i = i, j = j, size = size, sizeOrigin = null).also { c -> unit?.let { c.unit = it } }
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
                val unit = (v["unit"] as? Number)?.toDouble()
                next = next.replacingWorkspaceState(ws) { copy(i = i, j = j, size = size).also { c -> unit?.let { c.unit = it } } }
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

    // Its folder: a terminal's is its shell's (the first program it runs), else its own.
    private fun folderOf(pid: Int, appId: String): String? = try {
        val child = if (appId.lowercase() in TERMINALS) {
            runCatching { Files.readString(Path.of("/proc/$pid/task/$pid/children")).trim().split(' ').firstOrNull()?.toIntOrNull() }.getOrNull()
        } else null
        Files.readSymbolicLink(Path.of("/proc/${child ?: pid}/cwd")).toString()
    } catch (_: Exception) {
        null
    }
}
