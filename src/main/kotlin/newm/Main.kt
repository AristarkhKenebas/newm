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
        "install-session" -> kotlin.system.exitProcess(installSession(args.drop(1)))
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
            // (newm's bar then gives them its own look: BarModel.applyNoticeStyle.)
            noticeStyle = dev.caulis.core.NoticeStyle.cards(dark = scheme == dev.caulis.core.appearance.ColorScheme.DARK)
            applySettings()
        }
        // The start: dark until newm is warm, then it fades in, with a sound (Caulis has both off).
        startupDefaults()
        // Only for newm itself (dev/newm-dev's debugger port, its notice, that it restarts newm): not for
        // programs started inside (a newm started in a terminal here restarts on its own).
        environment["NEWM_OPTS"] = null
        environment["NEWM_NOTICE"] = null
        environment["NEWM_SUPERVISED"] = null
        // The keys are newm's: of Caulis's usual ones (Super+1..9 workspaces, Super+Q, window
        // dragging, ... which would fight newm's wall) only a laptop's keys and the consoles stay.
        val laptopKeys = listOf("volumeUp", "volumeDown", "volumeMute", "micMute", "brightnessUp", "brightnessDown", "touchpad", "displays", "mediaPlay", "mediaNext", "mediaPrevious")
        val kept = keys.all().filterKeys { it in laptopKeys }
        keys.off()
        kept.forEach { (name, combination) -> keys[name] = combination }
        // While newm is locked: none of Caulis's keys (see Layout.ensureLocked).
        keyMode(LOCKED_MODE) {}
        lateinit var layout: Layout
        val config = ConfigLoader(this, DeferredApi { LayoutApi(layout) })
        // No D-Bus session bus (a system without systemd, started without dbus-run-session): said in
        // the log at once, and on the screen once newm runs.
        SessionBus.checkAtStart()?.let { words -> onStart { notify(words, dev.caulis.core.NoticeLevel.WARNING, "newm") } }
        // Before newm's own start (on_startup): programs the configuration starts run inside newm.
        onStart { config.started() }
        // On the real screen and not among the login screen's sessions yet: told once how.
        onStart { if (System.getenv("WAYLAND_DISPLAY") == null) sessionHint()?.let { notify(it, title = "newm") } }
        // The first time: where the keys are told.
        onStart {
            val flag = logPathDir()?.resolve("welcomed")
            if (flag != null && !java.nio.file.Files.exists(flag)) {
                notify(newm.bar.tr("Super+/ shows every key. Super+D starts a program, Super+S puts two windows side by side. On a touchpad: three fingers move, four zoom."), title = newm.bar.tr("Welcome to newm"))
                runCatching { java.nio.file.Files.writeString(flag, "") }
            }
        }
        // What the one who started newm has to say (dev/newm-dev: "the new build crashed, the one before runs").
        onStart { System.getenv("NEWM_NOTICE")?.takeIf { it.isNotBlank() }?.let { notify(it, title = "newm") } }
        layout = Layout(this)
        // Permission rules that say "ask" (permissions { }): asked with a card of newm's.
        askPermission = permissionAsker(layout)

        // The configuration's warnings while it is first taken on (a value of the wrong kind):
        // Caulis shows them only once it runs, so they are kept and shown then.
        val early = java.util.concurrent.CopyOnWriteArrayList<dev.caulis.log.Log.Entry>()
        val watching = dev.caulis.log.Log.watch { if (it.area == "config" && (it.isWarning || it.isError)) early += it }
        val settings = config.load() ?: config.loadDefault() ?: defaultConfig(layout)
        Config.loadConfig(settings)
        // Compose made ready on a thread of its own while newm starts (its first scene, the
        // bar's, took ~0.7 s of the compositor's thread: the pointer stood still).
        if (configuredValue("warmup.compose", true)()) dev.caulis.compose.ComposeWarmup.start()
        applyHostSettings(settings)
        watching.close()
        onStart { early.forEach { notify(it.message, if (it.isError) dev.caulis.core.NoticeLevel.ERROR else dev.caulis.core.NoticeLevel.WARNING, "newm") } }

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
    // (A mistake here never stops newm: the rest of the settings still count, and it is shown.)
    try {
        applyHostSettingsNow(settings)
    } catch (e: Exception) {
        hostLog.warn("The pywm and outputs settings could not all be taken on: $e", e)
    }
}

private val hostLog = dev.caulis.log.Logger("config")

@Suppress("UNCHECKED_CAST")
private fun dev.caulis.wm.Caulis.applyHostSettingsNow(settings: Map<String, Any?>) {
    // The keyboard's layouts: the configuration's, else the system's (with a switch between them).
    val pywm = settings["pywm"] as? Map<String, Any?>
    if (pywm?.get("xkb_layout") == null) {
        val system = SystemKeyboard.detect()
        keyboardLayouts = system.layouts
        if (pywm?.get("xkb_options") == null) keyboardOptions = system.options
    }
    // The window under the pointer gets the keys, unless the configuration says otherwise.
    if (pywm?.get("focus_follows_mouse") == null) focusFollowsMouse = true
    // Java updated under the running newm (a system update): said once, with what to do.
    if (pywm?.get("java_updated_notice") == null) javaUpdatedNotice = "Java was updated: restart newm when convenient (newm cmd restart keep)"
    pywm?.let { applyPywmSettings(this, it) }
    (settings["outputs"] as? List<Map<String, Any?>>)?.let { applyOutputs(this, it) }
    // env(...), permissions { }, misc { } (see Misc.kt).
    applyMiscSettings(this, settings)
    // autostart { }, reveal { }, startupSound { } (see Startup.kt).
    applyStartupSettings(this, settings, greeter = Users.isGreeter())
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
    privateFolder(java.nio.file.Path.of(state, "newm"))
} catch (_: Exception) {
    null
}

// ~/.local/state/newm/newm.log, the last start's moved to newm.log.old (null if it cannot be made).
private fun logPath(): java.nio.file.Path? = try {
    val state = System.getenv("XDG_STATE_HOME")?.takeIf { it.startsWith("/") }
        ?: "${System.getenv("HOME") ?: System.getProperty("user.home")}/.local/state"
    // (Only its user may read it: the log tells what was done.)
    val dir = privateFolder(java.nio.file.Path.of(state, "newm"))
    val file = dir.resolve("newm.log")
    if (java.nio.file.Files.exists(file)) java.nio.file.Files.move(file, dir.resolve("newm.log.old"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    file
} catch (_: Exception) {
    null
}
