package newm

import dev.caulis.wm.caulis

/**
 * newm on Caulis: the compositor with newm's layout, configured by ~/.config/newm/config.kts
 * (or a config.py of before), loaded again whenever it is saved. `newm cmd <command>` talks
 * to a running newm instead.
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "cmd" -> return newmCmd(args.drop(1))
        "install-session" -> kotlin.system.exitProcess(installSession())
        "-h", "--help", "help" -> return println(USAGE)
        "-v", "--version", "version" -> return println("newm ${Layout::class.java.`package`?.implementationVersion ?: "(from sources)"} on Caulis")
        null -> {}
        else -> {
            System.err.println("newm: \"${args.first()}\" is not a command\n\n$USAGE")
            kotlin.system.exitProcess(2)
        }
    }
    caulis {
        configName = "newm"
        desktopName = "newm"
        // newm reads its own configuration (not Caulis's config.json or config.kts).
        useConfigFile = false
        // The log in a file too: ~/.local/state/newm/newm.log (the one before as newm.log.old).
        logFile = logPath()
        // No title bars: newm's look (a config may want programs' own: titleBars = true).
        titleBars = dev.caulis.core.TitleBars.NONE
        // Dark behind everything until the wallpaper has faded in (Caulis's own grey-blue otherwise).
        background = dev.caulis.core.Background.color(dev.caulis.render.Color(0.06f, 0.065f, 0.08f))
        // Notices (a mistake in the configuration, ...) as neat cards at the top.
        noticeStyle = dev.caulis.core.NoticeStyle.cards(dark = colorScheme == dev.caulis.core.appearance.ColorScheme.DARK)
        // (Light or dark as programs are, also when that changes.)
        onColorSchemeChanged { scheme ->
            noticeStyle = dev.caulis.core.NoticeStyle.cards(dark = scheme == dev.caulis.core.appearance.ColorScheme.DARK)
            applySettings()
        }
        // Only for newm itself (dev/newm-dev's debugger port, its notice): not for programs started inside.
        environment["NEWM_OPTS"] = null
        environment["NEWM_NOTICE"] = null
        // The keys are newm's: of Caulis's usual ones (Super+1..9 workspaces, Super+Q, window
        // dragging, ... which would fight newm's wall) only a laptop's keys and the consoles stay.
        val laptopKeys = listOf("volumeUp", "volumeDown", "volumeMute", "micMute", "brightnessUp", "brightnessDown", "touchpad", "displays", "mediaPlay", "mediaNext", "mediaPrevious")
        val kept = keys.all().filterKeys { it in laptopKeys }
        keys.off()
        kept.forEach { (name, combination) -> keys[name] = combination }
        // While newm is locked: none of Caulis's keys (see Layout.ensureLocked).
        mode(LOCKED_MODE) {}
        lateinit var layout: Layout
        val config = ConfigLoader(this, DeferredApi { LayoutApi(layout) })
        // Before newm's own start (on_startup): programs the configuration starts run inside newm.
        onStart { config.started() }
        // On the real screen and not among the login screen's sessions yet: told once how.
        onStart { if (System.getenv("WAYLAND_DISPLAY") == null) sessionHint()?.let { notify(it, title = "newm") } }
        // The first time: where the keys are told.
        onStart {
            val flag = logPathDir()?.resolve("welcomed")
            if (flag != null && !java.nio.file.Files.exists(flag)) {
                notify("Super+/ shows every key. Super+D starts a program, Super+S puts two windows side by side. On a touchpad: three fingers move, four zoom.", title = "Welcome to newm")
                runCatching { java.nio.file.Files.writeString(flag, "") }
            }
        }
        // What the one who started newm has to say (dev/newm-dev: "the new build crashed, the one before runs").
        onStart { System.getenv("NEWM_NOTICE")?.takeIf { it.isNotBlank() }?.let { notify(it, title = "newm") } }
        layout = Layout(this)

        val settings = config.load() ?: config.loadDefault() ?: defaultConfig(layout)
        Config.loadConfig(settings)
        applyHostSettings(settings)

        // updateConfig, and every save: on a mistake the settings stay as they were.
        layout.reloadConfig = { apply ->
            config.loadAgain { next ->
                Config.loadConfig(next)
                applyHostSettings(next)
                applySettings()
                apply()
            }
        }
        config.reload = { layout.updateConfig() }
        onUnload { config.close() }
    }
    if (restartRequested) kotlin.system.exitProcess(RESTART_EXIT)
}

/** True once `newm cmd restart` asked newm to end so that it is started again (exit code [RESTART_EXIT]). */
@Volatile
internal var restartRequested = false

/** newm's exit code when it is to be started again (`newm cmd restart`, for dev/newm-dev). */
const val RESTART_EXIT = 3

// The pywm and outputs sections, as Caulis's settings.
@Suppress("UNCHECKED_CAST")
private fun dev.caulis.wm.Caulis.applyHostSettings(settings: Map<String, Any?>) {
    // The keyboard's layouts: the configuration's, else the system's (with a switch between them).
    val pywm = settings["pywm"] as? Map<String, Any?>
    if (pywm?.get("xkb_layout") == null) {
        val system = SystemKeyboard.detect()
        keyboardLayouts = system.layouts
        if (pywm?.get("xkb_options") == null) keyboardOptions = system.options
    }
    // The window under the pointer gets the keys, unless the configuration says otherwise.
    if (pywm?.get("focus_follows_mouse") == null) focusFollowsMouse = true
    pywm?.let { applyPywmSettings(this, it) }
    (settings["outputs"] as? List<Map<String, Any?>>)?.let { applyOutputs(this, it) }
    // Light or dark: always one of them (never "the programs' own", which left newm light).
    ColorChoice.apply(this)
}

// LayoutApi once the layout is there (config.py's Python starts before it).
private class DeferredApi(make: () -> LayoutApi) : PyConfig.Handler, LayoutApiSource {
    private val api by lazy(make)
    override fun api() = api
    override fun layout(method: String, args: List<Any?>, kwargs: Map<String, Any?>) = api.layout(method, args, kwargs)
    override fun view(handle: Int, method: String, args: List<Any?>, kwargs: Map<String, Any?>) = api.view(handle, method, args, kwargs)
    override fun viewData(view: View) = api.viewData(view)
    override fun viewOf(handle: Int) = api.viewOf(handle)
}

// ~/.local/state/newm (made if needed), or null.
private fun logPathDir(): java.nio.file.Path? = try {
    val state = System.getenv("XDG_STATE_HOME")?.takeIf { it.startsWith("/") }
        ?: "${System.getenv("HOME") ?: System.getProperty("user.home")}/.local/state"
    java.nio.file.Path.of(state, "newm").also { java.nio.file.Files.createDirectories(it) }
} catch (_: Exception) {
    null
}

// ~/.local/state/newm/newm.log, the last start's moved to newm.log.old (null if it cannot be made).
private fun logPath(): java.nio.file.Path? = try {
    val state = System.getenv("XDG_STATE_HOME")?.takeIf { it.startsWith("/") }
        ?: "${System.getenv("HOME") ?: System.getProperty("user.home")}/.local/state"
    val dir = java.nio.file.Path.of(state, "newm")
    java.nio.file.Files.createDirectories(dir)
    val file = dir.resolve("newm.log")
    if (java.nio.file.Files.exists(file)) java.nio.file.Files.move(file, dir.resolve("newm.log.old"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    file
} catch (_: Exception) {
    null
}
