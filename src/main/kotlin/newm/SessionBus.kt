package newm

import dev.caulis.log.Logger
import java.nio.file.Files
import java.nio.file.Path

private val log = Logger("dbus")

/**
 * newm's D-Bus session bus: its bar's tray, notifications and the portals talk over it. With
 * systemd the login starts one for the user (its user units dbus.socket or dbus-broker); without
 * systemd (Void, Gentoo with OpenRC, Alpine, ...) nothing does, so newm is started inside
 * `dbus-run-session`, which makes a bus for as long as newm runs.
 *
 * `newm install-session` writes the login screen's session that way when it is needed, and the
 * start script starts newm again inside dbus-run-session when there is no bus (NEWM_DBUS=off:
 * it does not). This is how both tell, with the system's files given (tests give their own).
 */
object SessionBus {
    /** Where systemd keeps its user units (the system's own, the distribution's, the admin's). */
    private val USER_UNIT_DIRS = listOf("etc/systemd/user", "usr/lib/systemd/user", "lib/systemd/user", "usr/local/lib/systemd/user")

    /** The user units that start a session bus at login (dbus-daemon's or dbus-broker's). */
    private val BUS_UNITS = listOf("dbus.socket", "dbus-broker.service")

    /** True when systemd is the init (PID 1): its folder /run/systemd/system, as sd_booted looks. */
    fun systemdIsInit(root: Path = Path.of("/")): Boolean {
        if (Files.isDirectory(root.resolve("run/systemd/system"))) return true
        val comm = runCatching { Files.readString(root.resolve("proc/1/comm")).trim() }.getOrNull()
        return comm == "systemd"
    }

    /** True when one of systemd's user units starts a session bus (dbus.socket, dbus-broker). */
    fun userBusUnit(root: Path = Path.of("/")): Boolean =
        USER_UNIT_DIRS.any { dir -> BUS_UNITS.any { unit -> Files.exists(root.resolve(dir).resolve(unit)) } }

    /** True when a session bus will be there at login without newm's help: systemd, with a bus unit. */
    fun busAtLogin(root: Path = Path.of("/")): Boolean = systemdIsInit(root) && userBusUnit(root)

    /**
     * True when this process has a session bus: DBUS_SESSION_BUS_ADDRESS says where, or there is
     * one at $XDG_RUNTIME_DIR/bus (where libdbus and sd-bus look when it is not set).
     */
    fun busNow(env: Map<String, String> = System.getenv(), exists: (Path) -> Boolean = Files::exists): Boolean {
        if (!env["DBUS_SESSION_BUS_ADDRESS"].isNullOrBlank()) return true
        val runtime = env["XDG_RUNTIME_DIR"]?.takeIf { it.startsWith("/") } ?: return false
        return exists(Path.of(runtime, "bus"))
    }

    /** dbus-run-session's full path when it is installed ([path]: PATH's folders), else null. */
    fun findDbusRunSession(path: String? = System.getenv("PATH"), isExecutable: (Path) -> Boolean = Files::isExecutable): Path? =
        // (Also the usual folders: a sudo or a login screen's PATH may be short.)
        ((path ?: "").split(':').filter { it.startsWith("/") } + listOf("/usr/bin", "/bin", "/usr/local/bin"))
            .distinct().map { Path.of(it, "dbus-run-session") }.firstOrNull(isExecutable)

    /**
     * What the session file's Exec= starts: [launcher] (already quoted for Exec=), or the same
     * inside dbus-run-session. [wanted]: what `--dbus-run-session` / `--no-dbus-run-session`
     * said (null: newm decides by [busAtLogin]). [warning]: what to tell the one installing.
     */
    data class Plan(val exec: String, val wrapped: Boolean, val warning: String?)

    fun plan(launcher: String, wanted: Boolean?, busAtLogin: Boolean, dbusRunSession: Path?): Plan {
        val wrap = wanted ?: !busAtLogin
        if (!wrap) return Plan(launcher, false, null)
        if (dbusRunSession == null) {
            // Not installed: newm alone (its start script tries again at login), and what to install said.
            return Plan(
                launcher, false,
                "this system starts no D-Bus session bus at login, and dbus-run-session is not installed. " +
                    "Install D-Bus (Void: xbps-install dbus; Alpine: apk add dbus; Gentoo: emerge sys-apps/dbus; " +
                    "Debian: apt install dbus-bin) and run  newm install-session  again: without it newm's tray, " +
                    "notifications and portals do not work.",
            )
        }
        val why = if (wanted == true) "asked for with --dbus-run-session" else "no systemd user session starts a D-Bus bus here"
        return Plan("${desktopExecPath(dbusRunSession)} $launcher", true, null).also { log.info { "Session inside dbus-run-session ($why)" } }
    }

    /**
     * At newm's start: a clear warning in the log when there is no session bus (the start script
     * starts newm inside dbus-run-session itself unless NEWM_DBUS=off or it is not installed).
     * The words to show on the screen, or null when there is a bus.
     */
    fun checkAtStart(env: Map<String, String> = System.getenv()): String? {
        if (busNow(env)) return null
        val off = env["NEWM_DBUS"] == "off"
        val reason = if (off) "NEWM_DBUS=off" else if (findDbusRunSession(env["PATH"]) == null) "dbus-run-session is not installed" else "it was started without its start script"
        log.warn(
            "No D-Bus session bus (DBUS_SESSION_BUS_ADDRESS is not set, nothing at \$XDG_RUNTIME_DIR/bus; $reason): " +
                "the tray, notifications and portals will not work. Install D-Bus (the package with dbus-run-session) " +
                "or start newm as  dbus-run-session newm.",
        )
        return newm.bar.tr("No D-Bus session bus: the tray, notifications and portals do not work. Install D-Bus (dbus-run-session), see newm's log.")
    }
}
