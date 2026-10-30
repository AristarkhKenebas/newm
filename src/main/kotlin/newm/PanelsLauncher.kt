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


    val pid: Long? get() = process?.pid()
    val alive: Boolean get() = process?.isAlive == true
    /** When its process started (/proc's start time; null: none, or not read). */
    var startTime: Long? = null
        private set

    private fun start() {
        process = null
        val cmd = confCmds.getValue(panel)() ?: return
        val cwd = confCwds.getValue(panel)()
        log.info { "Starting $cmd${cwd?.let { " in $it" }.orEmpty()}" }
        // (Inside the compositor: its environment, its socket.)
        process = spawn(cmd.split(" "), cwd?.let { java.nio.file.Path.of(it) })
        startTime = process?.pid()?.let(::processStartTime)
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
            if (process != null) log.info { "Subprocess for panel $panel died" }
            start()
        }
    }

    fun stop() {
        process?.destroyForcibly()
        process = null
        startTime = null
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

    // (Only a panel program running now, and the very process started: a window of a process
    // that got its number after it ended is no panel — never the lock screen's place on top.)
    fun panelForPid(pid: Int): String? = panels.firstOrNull { p ->
        p.alive && p.pid == pid.toLong() && p.startTime.let { it == null || it == processStartTime(pid.toLong()) }
    }?.panel

    /** The process of [panel]'s program now (null: none running). */
    fun pidOf(panel: String): Long? = panels.firstOrNull { it.panel == panel }?.takeIf { it.alive }?.pid

    /** When the process of [panel]'s program now started (see [processStartTime]; null: none, or not read). */
    fun startTimeOf(panel: String): Long? = panels.firstOrNull { it.panel == panel }?.takeIf { it.alive }?.startTime
}
