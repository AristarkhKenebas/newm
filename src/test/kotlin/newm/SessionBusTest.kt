package newm

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Whether a D-Bus session bus is there at login, told from a system's files (fake ones here). */
class SessionBusTest {

    // A fake system's root with [files] in it ("proc/1/comm=systemd": a file with that text).
    private fun system(vararg files: String): Path {
        val root = Files.createTempDirectory("newm-bus")
        for (entry in files) {
            val (name, text) = entry.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val path = root.resolve(name)
            if (name.endsWith("/")) Files.createDirectories(path) else {
                Files.createDirectories(path.parent)
                Files.writeString(path, text)
            }
        }
        return root
    }

    @Test
    fun `systemd with a user bus unit has a bus at login`() {
        assertTrue(SessionBus.busAtLogin(system("run/systemd/system/", "usr/lib/systemd/user/dbus.socket")))
        // dbus-broker's unit, systemd told by PID 1's name.
        assertTrue(SessionBus.busAtLogin(system("proc/1/comm=systemd\n", "lib/systemd/user/dbus-broker.service")))
    }

    @Test
    fun `no systemd, or systemd without a user bus, has none`() {
        // Void (runit), Gentoo (OpenRC), Alpine: another init, though D-Bus's files may be there.
        assertFalse(SessionBus.busAtLogin(system("proc/1/comm=runit\n", "usr/lib/systemd/user/dbus.socket")))
        assertFalse(SessionBus.busAtLogin(system("proc/1/comm=init\n")))
        assertFalse(SessionBus.busAtLogin(system("run/systemd/system/")))
    }

    @Test
    fun `the session file's Exec follows the bus and the flags`() {
        val drs = Path.of("/usr/bin/dbus-run-session")
        assertEquals("/usr/bin/newm", SessionBus.plan("/usr/bin/newm", null, busAtLogin = true, drs).exec)
        val wrapped = SessionBus.plan("/usr/bin/newm", null, busAtLogin = false, drs)
        assertEquals("/usr/bin/dbus-run-session /usr/bin/newm", wrapped.exec)
        assertTrue(wrapped.wrapped)
        assertNull(wrapped.warning)
        // --dbus-run-session / --no-dbus-run-session win.
        assertEquals("/usr/bin/dbus-run-session /usr/bin/newm", SessionBus.plan("/usr/bin/newm", true, busAtLogin = true, drs).exec)
        assertEquals("/usr/bin/newm", SessionBus.plan("/usr/bin/newm", false, busAtLogin = false, drs).exec)
        // Needed but not installed: newm alone, and what to install said.
        val missing = SessionBus.plan("/usr/bin/newm", null, busAtLogin = false, null)
        assertEquals("/usr/bin/newm", missing.exec)
        assertNotNull(missing.warning).let { assertTrue("dbus" in it) }
    }

    @Test
    fun `a bus now is the address or the runtime folder's socket`() {
        assertTrue(SessionBus.busNow(mapOf("DBUS_SESSION_BUS_ADDRESS" to "unix:path=/run/user/1000/bus")) { false })
        assertTrue(SessionBus.busNow(mapOf("XDG_RUNTIME_DIR" to "/run/user/1000")) { it == Path.of("/run/user/1000/bus") })
        assertFalse(SessionBus.busNow(mapOf("XDG_RUNTIME_DIR" to "/run/user/1000")) { false })
        assertFalse(SessionBus.busNow(emptyMap()) { true })
    }

    @Test
    fun `dbus-run-session is found on PATH or in the usual folders`() {
        assertEquals(Path.of("/opt/x/dbus-run-session"), SessionBus.findDbusRunSession("/opt/x:/usr/bin") { it.startsWith("/opt/x") })
        assertEquals(Path.of("/usr/bin/dbus-run-session"), SessionBus.findDbusRunSession("") { it == Path.of("/usr/bin/dbus-run-session") })
        assertNull(SessionBus.findDbusRunSession("/opt/x") { false })
    }
}
