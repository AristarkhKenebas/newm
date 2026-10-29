package newm

import dev.caulis.log.Logger
import java.nio.file.Files
import java.nio.file.Path

private val log = Logger("install")

/** Where display managers (ly, SDDM, GDM) find sessions to choose. */
val SESSION_FILE: Path = Path.of("/usr/share/wayland-sessions/newm.desktop")

/**
 * The newm program itself (its start script, next to the jars it runs). Nix's package runs that
 * script from $out/share/newm through a wrapper ($out/bin/newm: its Java, its libraries): the
 * wrapper then, which works on its own.
 */
fun launcherPath(): Path? {
    val script = System.getProperty("java.class.path").orEmpty().split(':').firstOrNull { it.endsWith(".jar") }
        ?.let { Path.of(it).toAbsolutePath().parent?.parent?.resolve("bin/newm") }
        ?.takeIf(Files::isExecutable) ?: return null
    // /nix/store/<hash>-newm/share/newm/bin/newm → /nix/store/<hash>-newm/bin/newm
    val share = script.parent?.parent
    if (script.startsWith("/nix/store") && share?.fileName?.toString() == "newm" && share.parent?.fileName?.toString() == "share") {
        val wrapper = share.parent.parent.resolve("bin/newm")
        if (Files.isExecutable(wrapper)) return wrapper
    }
    return script
}

// A path as a .desktop file's Exec= takes it: quoted when it has spaces (or other special characters).
private fun desktopExec(path: Path): String {
    val text = path.toString()
    if (text.none { it.isWhitespace() || it in "\"'`$\\<>~|&;*?#()" }) return text
    return "\"" + text.replace(Regex("[\"`$\\\\]")) { "\\" + it.value } + "\""
}

// A path as a .desktop file's Exec= takes it (for SessionBus: dbus-run-session's).
internal fun desktopExecPath(path: Path): String = desktopExec(path)

/**
 * `newm install-session`: newm in the display manager's list of sessions, and the system's
 * default configuration (/etc/newm/config.kts, which new users get as theirs). Asks for root
 * (sudo, else pkexec) when it is not.
 *
 * Where no systemd user session starts a D-Bus session bus at login (Void, Gentoo with OpenRC,
 * Alpine, ...) the session starts newm inside dbus-run-session. [args]: --dbus-run-session or
 * --no-dbus-run-session decide that instead of newm.
 */
fun installSession(args: List<String> = emptyList()): Int {
    var wanted: Boolean? = null
    for (arg in args) {
        when (arg) {
            "--dbus-run-session" -> wanted = true
            "--no-dbus-run-session" -> wanted = false
            else -> {
                System.err.println("newm install-session: \"$arg\" is not an option (--dbus-run-session, --no-dbus-run-session)")
                return 2
            }
        }
    }
    val launcher = launcherPath()?.let { p -> listOf(Path.of("/usr/local/bin/newm"), Path.of("/usr/bin/newm")).firstOrNull { Files.isSymbolicLink(it) && Files.isSameFile(it, p) } ?: p }
        ?: Path.of("newm")
    val busAtLogin = SessionBus.busAtLogin()
    val plan = SessionBus.plan(desktopExec(launcher), wanted, busAtLogin, SessionBus.findDbusRunSession())
    log.info { "Session bus at login: $busAtLogin; asked: ${wanted ?: "nothing"}; Exec=${plan.exec}" }
    plan.warning?.let { warning ->
        log.warn(warning)
        System.err.println("newm: warning: $warning")
    }
    val session = """
        [Desktop Entry]
        Name=newm
        Comment=Windows on an endless wall, made for touchpads
        Exec=${plan.exec}
        TryExec=$launcher
        Type=Application
        DesktopNames=newm
    """.trimIndent() + "\n"
    // (newm's templates, for /etc/newm: what new users start from; those there already stay.)
    val templates = Files.createTempDirectory("newm-templates")
    val desktop = Files.createTempFile("newm", ".desktop")
    try {
        Templates.copyTo(templates)
        Files.writeString(desktop, session)
        val script = "install -Dm644 '$desktop' '$SESSION_FILE' && " +
            Templates.FILES.joinToString(" && ") { name -> "{ [ -e '/etc/newm/$name' ] || install -Dm644 '$templates/$name' '/etc/newm/$name'; }" }
        val root = (Files.getAttribute(Path.of("/proc/self"), "unix:uid") as Int) == 0
        val command = when {
            root -> listOf("sh", "-c", script)
            onPath("sudo") -> listOf("sudo", "sh", "-c", script)
            else -> listOf("pkexec", "sh", "-c", script)
        }
        println("newm: installing $SESSION_FILE (Exec=${plan.exec}) and /etc/newm/config.kts${if (root) "" else " — as root, so ${command.first()} may ask for your password"}")
        val status = dev.caulis.ffi.Processes.start(ProcessBuilder(command).inheritIO()).waitFor()
        if (status == 0) {
            println("newm: done. Choose \"newm\" in your login screen, or type newm on a text console.")
        } else {
            println("newm: that did not work (status $status).")
        }
        return status
    } finally {
        templates.toFile().deleteRecursively()
        Files.deleteIfExists(desktop)
    }
}

// (A class for the resources.)
private object Install

// terminal = "foot" (top level): the terminal newm opens.
private val confTerminal = configuredValue("terminal", "")

/**
 * The terminal to open: the configuration's (terminal = "foot"), else $TERMINAL, else the
 * first of the usual ones that is installed (newm used to expect alacritty).
 */
fun defaultTerminal(): String = confTerminal().takeIf { it.isNotBlank() }
    ?: System.getenv("TERMINAL")?.takeIf { it.isNotBlank() }
    ?: listOf("alacritty", "foot", "kitty", "wezterm", "ghostty", "gnome-terminal", "konsole", "xfce4-terminal", "xterm").firstOrNull(::onPath)
    ?: "alacritty"

internal fun onPath(program: String) = (System.getenv("PATH") ?: "/usr/bin").split(':').any { Files.isExecutable(Path.of(it, program)) }

/** The first start on a screen without newm among the sessions: told once how to add it. */
fun sessionHint(): String? {
    // Started by a login screen already (a session of newm's: "newm", "newm (dev)"), or installed: nothing to say.
    if (Files.exists(SESSION_FILE) || System.getenv("DESKTOP_SESSION") != null || System.getenv("XDG_SESSION_DESKTOP") != null) return null
    val sessions = Path.of(SESSION_FILE.parent.toString())
    val installed = try {
        Files.list(sessions).use { files -> files.anyMatch { it.fileName.toString().startsWith("newm") } }
    } catch (_: Exception) {
        false
    }
    return if (installed) null else newm.bar.tr("To choose newm at login (ly, SDDM, GDM): run  newm install-session  in a terminal.")
}

const val USAGE = """newm — a Wayland compositor: windows on an endless wall, made for touchpads.

  newm                     start newm (on a text console: the whole screen; inside a Wayland session: a window)
  newm cmd <command>       talk to the running newm: lock, debug, config, update-config, open-launcher, clean, ...
  newm install-session     add newm to the login screen's sessions (and /etc/newm: the configuration new users get);
                           without a systemd user session it starts newm inside dbus-run-session
                           (--dbus-run-session / --no-dbus-run-session: always / never)
  newm --version

Your configuration: ~/.config/newm/config.kts (made the first time newm starts; saved changes apply at once).
The guide: https://github.com/AristarkhKenebas/newm/blob/master/docs/en/README.md"""

/** newm's templates (in newm itself): what a user gets the first time, and /etc/newm. */
object Templates {
    val FILES = listOf(
        "config.kts", "README.md", "examples/config.lua", "examples/config.json",
        "bars/classic.kts", "bars/waybar/config.jsonc", "bars/waybar/style.css",
    )

    /**
     * Writes the templates into [dir] (what is there already stays), config.kts with its notes
     * in [language] (the system's: Russian notes for a Russian system, else English).
     */
    fun copyTo(dir: Path, language: String = TemplateText.systemLanguage()) {
        for (name in FILES) {
            val target = dir.resolve(name)
            if (Files.exists(target)) continue
            Files.createDirectories(target.parent)
            if (name == "config.kts") {
                val english = Templates::class.java.getResourceAsStream("/newm/templates/$name")!!.use { String(it.readAllBytes()) }
                Files.writeString(target, TemplateText.translate(english, language))
                log.info { "config.kts written with its notes in \"$language\"" }
            } else {
                Templates::class.java.getResourceAsStream("/newm/templates/$name")!!.use { Files.copy(it, target) }
            }
        }
    }

    /**
     * Copies the files of [from] (the system's /etc/newm) into [to] (what is there already
     * stays); config.kts with the notes newm knows in [language].
     */
    fun copyFolder(from: Path, to: Path, language: String = TemplateText.systemLanguage()) {
        Files.walk(from).use { files ->
            for (file in files.filter(Files::isRegularFile).toList()) {
                val target = to.resolve(from.relativize(file).toString())
                if (Files.exists(target)) continue
                Files.createDirectories(target.parent)
                if (from.relativize(file).toString() == "config.kts") {
                    Files.writeString(target, TemplateText.translate(Files.readString(file), language))
                } else {
                    Files.copy(file, target)
                }
                // (Nix's files are read-only: the user's copy is theirs to change.)
                target.toFile().setWritable(true, true)
            }
        }
    }
}
