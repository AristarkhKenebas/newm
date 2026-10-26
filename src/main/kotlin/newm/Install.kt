package newm

import dev.caulis.log.Logger
import java.nio.file.Files
import java.nio.file.Path

private val log = Logger("install")

/** Where display managers (ly, SDDM, GDM) find sessions to choose. */
val SESSION_FILE: Path = Path.of("/usr/share/wayland-sessions/newm.desktop")

/** The newm program itself (its start script, next to the jars it runs). */
fun launcherPath(): Path? = System.getProperty("java.class.path").orEmpty().split(':').firstOrNull { it.endsWith(".jar") }
    ?.let { Path.of(it).toAbsolutePath().parent?.parent?.resolve("bin/newm") }
    ?.takeIf(Files::isExecutable)

/**
 * `newm install-session`: newm in the display manager's list of sessions, and the system's
 * default configuration (/etc/newm/config.kts, which new users get as theirs). Asks for root
 * (sudo, else pkexec) when it is not.
 */
fun installSession(): Int {
    val launcher = launcherPath()?.let { p -> listOf(Path.of("/usr/local/bin/newm"), Path.of("/usr/bin/newm")).firstOrNull { Files.isSymbolicLink(it) && Files.isSameFile(it, p) } ?: p }
        ?: Path.of("newm")
    val session = """
        [Desktop Entry]
        Name=newm
        Comment=Windows on an endless wall, made for touchpads
        Exec=$launcher
        TryExec=$launcher
        Type=Application
        DesktopNames=newm
    """.trimIndent() + "\n"
    val config = Files.createTempFile("newm-config", ".kts")
    val desktop = Files.createTempFile("newm", ".desktop")
    try {
        Install::class.java.getResourceAsStream("/newm/config.kts")!!.use { Files.copy(it, config, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
        Files.writeString(desktop, session)
        val script = "install -Dm644 '$desktop' '$SESSION_FILE' && " +
            "if [ ! -e /etc/newm/config.kts ]; then install -Dm644 '$config' /etc/newm/config.kts; fi"
        val root = (Files.getAttribute(Path.of("/proc/self"), "unix:uid") as Int) == 0
        val command = when {
            root -> listOf("sh", "-c", script)
            onPath("sudo") -> listOf("sudo", "sh", "-c", script)
            else -> listOf("pkexec", "sh", "-c", script)
        }
        println("newm: installing $SESSION_FILE (Exec=$launcher) and /etc/newm/config.kts${if (root) "" else " — as root, so ${command.first()} may ask for your password"}")
        val status = ProcessBuilder(command).inheritIO().start().waitFor()
        if (status == 0) {
            println("newm: done. Choose \"newm\" in your login screen, or type newm on a text console.")
        } else {
            println("newm: that did not work (status $status).")
        }
        return status
    } finally {
        Files.deleteIfExists(config)
        Files.deleteIfExists(desktop)
    }
}

// (A class for the resources.)
private object Install

internal fun onPath(program: String) = (System.getenv("PATH") ?: "/usr/bin").split(':').any { Files.isExecutable(Path.of(it, program)) }

/** The first start on a screen without newm among the sessions: told once how to add it. */
fun sessionHint(): String? =
    if (Files.exists(SESSION_FILE)) null else "To choose newm at login (ly, SDDM, GDM): run  newm install-session  in a terminal."

const val USAGE = """newm — a Wayland compositor: windows on an endless wall, made for touchpads.

  newm                     start newm (on a text console: the whole screen; inside a Wayland session: a window)
  newm cmd <command>       talk to the running newm: lock, debug, config, update-config, open-launcher, clean, ...
  newm install-session     add newm to the login screen's sessions (and /etc/newm/config.kts)
  newm --version

Your configuration: ~/.config/newm/config.kts (made the first time newm starts; saved changes apply at once).
The guide: https://github.com/AristarkhKenebas/newm/blob/master/docs/guide.md"""
