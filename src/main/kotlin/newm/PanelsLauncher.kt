package newm

import dev.caulis.log.Logger

private val log = Logger("panels")

private val allPanels = listOf("lock", "launcher", "top_bar", "bottom_bar", "bar")

// (Each asked for once: the lock screen and the launcher have a default command.)
private val confCmds: Map<String, () -> String?> = allPanels.associateWith { panel ->
    configuredValue<String?>(
        "panels.$panel.cmd",
        when (panel) {
            // (newm's own lock screen and launcher otherwise: no program needed.)
            else -> null
        },
    )
}

private val confCwds: Map<String, () -> String?> = allPanels.associateWith { configuredValue<String?>("panels.$it.cwd", null) }

/** newm's PanelLauncher: one panel program (the lock screen, the launcher, a bar), started again if it dies. */
class PanelLauncher(val panel: String, private val spawn: (List<String>, java.nio.file.Path?) -> Process?) {
    private var process: Process? = null
    private var toldMissing = false

    private fun onPath(program: String) = (System.getenv("PATH") ?: "/usr/bin").split(':').any { java.nio.file.Files.isExecutable(java.nio.file.Path.of(it, program)) }

    val pid: Long? get() = process?.pid()

    private fun start() {
        process = null
        val cmd = confCmds.getValue(panel)() ?: return
        val cwd = confCwds.getValue(panel)()
        log.info { "Starting $cmd in $cwd" }
        // (Inside the compositor: its environment, its socket.)
        process = spawn(cmd.split(" "), cwd?.let { java.nio.file.Path.of(it) })
    }

    fun check() {
        val cmd = confCmds.getValue(panel)() ?: return
        // newm-panel-basic (newm's Python panels) when it is not installed: newm's own are used.
        if ("newm-panel-basic" in cmd && !onPath("newm-panel-basic")) {
            if (!toldMissing) log.info { "$panel: newm-panel-basic is not installed, newm's own $panel is used" }
            toldMissing = true
            return
        }
        if (process?.isAlive != true) {
            log.info { "Subprocess for panel $panel died" }
            start()
        }
    }

    fun stop() {
        process?.destroyForcibly()
        process = null
    }
}

/**
 * newm's PanelsLauncher: keeps the panel programs running (checked every 25 seconds, as newm),
 * and knows which window is which panel by its process.
 */
class PanelsLauncher(private val every: (Long, () -> Unit) -> AutoCloseable, spawn: (List<String>, java.nio.file.Path?) -> Process?) {
    private val panels = allPanels.map { PanelLauncher(it, spawn) }
    private var ticker: AutoCloseable? = null

    fun start() {
        panels.forEach(PanelLauncher::check)
        ticker = every(25_000) { panels.forEach(PanelLauncher::check) }
    }

    fun stop() {
        ticker?.close()
        ticker = null
        panels.forEach(PanelLauncher::stop)
    }

    fun panelForPid(pid: Int): String? = panels.firstOrNull { it.pid == pid.toLong() }?.panel
}
