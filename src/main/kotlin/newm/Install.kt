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
        println("newm: installing $SESSION_FILE (Exec=$launcher) and /etc/newm/config.kts${if (root) "" else " — as root, so ${command.first()} may ask for your password"}")
        val status = ProcessBuilder(command).inheritIO().start().waitFor()
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

/**
 * The terminal to open: $TERMINAL, else the first of the usual ones that is installed
 * (newm used to expect alacritty).
 */
fun defaultTerminal(): String = System.getenv("TERMINAL")?.takeIf { it.isNotBlank() }
    ?: listOf("alacritty", "foot", "kitty", "wezterm", "ghostty", "gnome-terminal", "konsole", "xfce4-terminal", "xterm").firstOrNull(::onPath)
    ?: "alacritty"

internal fun onPath(program: String) = (System.getenv("PATH") ?: "/usr/bin").split(':').any { Files.isExecutable(Path.of(it, program)) }

/** The first start on a screen without newm among the sessions: told once how to add it. */
fun sessionHint(): String? =
    if (Files.exists(SESSION_FILE)) null else "To choose newm at login (ly, SDDM, GDM): run  newm install-session  in a terminal."

const val USAGE = """newm — a Wayland compositor: windows on an endless wall, made for touchpads.

  newm                     start newm (on a text console: the whole screen; inside a Wayland session: a window)
  newm cmd <command>       talk to the running newm: lock, debug, config, update-config, open-launcher, clean, ...
  newm install-session     add newm to the login screen's sessions (and /etc/newm: the configuration new users get)
  newm --version

Your configuration: ~/.config/newm/config.kts (made the first time newm starts; saved changes apply at once).
The guide: https://github.com/AristarkhKenebas/newm/blob/master/docs/en/README.md"""

/** newm's templates (in newm itself): what a user gets the first time, and /etc/newm. */
object Templates {
    val FILES = listOf(
        "config.kts", "README.md", "examples/config.lua", "examples/config.json",
        "bars/classic.kts", "bars/waybar/config.jsonc", "bars/waybar/style.css",
    )

    /** Writes the templates into [dir] (what is there already stays). */
    fun copyTo(dir: Path) {
        for (name in FILES) {
            val target = dir.resolve(name)
            if (Files.exists(target)) continue
            Files.createDirectories(target.parent)
            Templates::class.java.getResourceAsStream("/newm/templates/$name")!!.use { Files.copy(it, target) }
        }
    }

    /** Copies the files of [from] (the system's /etc/newm) into [to] (what is there already stays). */
    fun copyFolder(from: Path, to: Path) {
        Files.walk(from).use { files ->
            for (file in files.filter(Files::isRegularFile).toList()) {
                val target = to.resolve(from.relativize(file).toString())
                if (Files.exists(target)) continue
                Files.createDirectories(target.parent)
                Files.copy(file, target)
            }
        }
    }
}
