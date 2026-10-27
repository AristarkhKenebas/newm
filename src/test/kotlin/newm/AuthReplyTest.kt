package newm

import newm.bar.DBusNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What other programs send newm over D-Bus is checked before it is used. */
class AuthReplyTest {

    private val cred = AuthRequest(mapOf("kind" to "auth_request_cred", "user" to "me", "message" to "Password?")) {}
    private val user = AuthRequest(mapOf("kind" to "auth_request_user", "users" to listOf("anna", "boris"))) {}

    @Test
    fun `a password request takes a password, nothing else`() {
        assertTrue(cred.accepts(mapOf("cred" to "secret")))
        assertFalse(cred.accepts(emptyMap()), "{} (the reply that jammed the lock screen)")
        assertFalse(cred.accepts(mapOf("cred" to 42)))
        assertFalse(cred.accepts(mapOf("cred" to null)))
        assertFalse(cred.accepts(mapOf("user" to "anna")))
        assertFalse(cred.accepts(mapOf("cred" to "x".repeat(AuthRequest.MAX_CRED_LENGTH + 1))))
    }

    @Test
    fun `a user request takes one of the users offered`() {
        assertTrue(user.accepts(mapOf("user" to "anna")))
        assertFalse(user.accepts(mapOf("user" to "root")))
        assertFalse(user.accepts(mapOf("user" to listOf("anna"))))
        assertFalse(user.accepts(mapOf("cred" to "secret")))
    }

    @Test
    fun `only the lock panel program and what it started may answer`() {
        // 1 <- 100 (newm) <- 200 (the panel: sh) <- 300 (python, its D-Bus connection); 400 another program.
        val parents = mapOf(300L to 200L, 200L to 100L, 100L to 1L, 400L to 100L, 500L to 500L)
        val parentOf = { pid: Long -> parents[pid] }
        assertTrue(isInProcessTree(200, 200, parentOf))
        assertTrue(isInProcessTree(300, 200, parentOf))
        assertFalse(isInProcessTree(400, 200, parentOf), "a sibling of the panel")
        assertFalse(isInProcessTree(100, 200, parentOf), "newm itself is not the panel")
        assertFalse(isInProcessTree(999, 200, parentOf), "an unknown process")
        assertFalse(isInProcessTree(500, 200, parentOf), "a loop in what /proc says ends")
    }

    @Test
    fun `the panel's old number used again by another process is not the panel`() {
        val parents = mapOf(300L to 200L, 200L to 100L)
        val starts = mapOf(200L to 5000L, 300L to 5100L)
        assertTrue(isInProcessTree(300, 200, 5000L, { parents[it] }, { starts[it] }))
        // The panel (started at 4000) ended; 200 is now someone else's process (started at 5000).
        assertFalse(isInProcessTree(300, 200, 4000L, { parents[it] }, { starts[it] }))
        assertFalse(isInProcessTree(300, 200, 4000L, { parents[it] }, { null }), "start time not read: not the panel")
    }

    @Test
    fun `a process's start time is read from its stat line`() {
        // (A program's name may hold spaces and parentheses.)
        val stat = "4242 (evil) (name) S 1 4242 4242 0 -1 4194560 100 0 0 0 1 2 0 0 20 0 1 0 987654 1000 10"
        assertEquals(987654L, startTimeOfStat(stat))
        assertEquals(null, startTimeOfStat("4242 (x"))
        val own = ProcessHandle.current().pid()
        assertTrue((processStartTime(own) ?: 0L) > 0L)
        assertEquals(processStartTime(own), processStartTime(own))
    }

    @Test
    fun `tray names and paths as D-Bus allows them`() {
        for (good in listOf("/", "/StatusNotifierItem", "/org/ayatana/NotificationItem/x_1")) assertTrue(DBusNames.isObjectPath(good), good)
        for (bad in listOf("", "a", "/a b", "/bad path!", "/a/", "//a", "/a//b", "/a-b", "/" + "a".repeat(2000))) assertFalse(DBusNames.isObjectPath(bad), bad)
        for (good in listOf(":1.42", "org.kde.StatusNotifierItem-1234-1", "org.x_y.Z")) assertTrue(DBusNames.isBusName(good), good)
        for (bad in listOf("", "not a name!", "org", ":1", "org..kde", "1org.kde", "org.1kde", "org.kde.", "a.b" + ".c".repeat(200))) assertFalse(DBusNames.isBusName(bad), bad)
    }

    @Test
    fun `a session keeps no command run inside a terminal, nor secrets`() {
        assertEquals(listOf("foot"), keptCommand(listOf("foot", "-e", "sudo", "pacman", "-Syu"), terminal = true))
        assertEquals(listOf("/usr/bin/spotify", "--ozone-platform=wayland"), keptCommand(listOf("/usr/bin/spotify", "--ozone-platform=wayland", "spotify:track:1"), terminal = false))
        assertEquals(listOf("xfreerdp"), keptCommand(listOf("xfreerdp", "/p:hunter2", "/v:host"), terminal = false))
        assertEquals(listOf("app", "--fullscreen"), keptCommand(listOf("app", "--password=hunter2", "--fullscreen"), terminal = false))
        assertEquals(listOf("python3", "-u", "app.py"), keptCommand(listOf("python3", "-u", "app.py", "file.txt"), terminal = false))
        assertEquals(listOf("flatpak", "run", "org.x.Y"), keptCommand(listOf("flatpak", "run", "org.x.Y"), terminal = false))
        assertEquals(listOf("mpv", "https://example.org/v"), keptCommand(listOf("mpv", "https://example.org/v"), terminal = false, arguments = "all"))
        assertEquals(listOf("mpv"), keptCommand(listOf("mpv", "--fs"), terminal = false, arguments = "none"))
    }

    // (What "options" keeps: the program comes back as it was started, the secrets do not.)
    private fun kept(vararg command: String, rules: KeptCommandRules = KeptCommandRules()) = keptCommand(command.toList(), terminal = false, rules = rules)

    @Test
    fun `real programs come back as they were started`() {
        // Obsidian, Element, Signal on Arch: the system's Electron and the app's archive.
        assertEquals(listOf("/usr/lib/electron43/electron", "/usr/lib/obsidian/app.asar", "--ozone-platform-hint=auto", "--enable-features=UseOzonePlatform,WaylandWindowDecorations", "--enable-wayland-ime"),
            kept("/usr/lib/electron43/electron", "/usr/lib/obsidian/app.asar", "--ozone-platform-hint=auto", "--enable-features=UseOzonePlatform,WaylandWindowDecorations", "--enable-wayland-ime", "/home/u/notes/today.md"))
        assertEquals(listOf("electron", "--enable-features=WaylandWindowDecorations", "/usr/lib/element/app.asar"),
            kept("electron", "--enable-features=WaylandWindowDecorations", "/usr/lib/element/app.asar"))
        assertEquals(listOf("firefox", "-P", "work", "-private-window"), kept("firefox", "-P", "work", "-private-window", "https://example.org"))
        assertEquals(listOf("code", "--user-data-dir", "/x", "--disable-gpu"), kept("code", "--user-data-dir", "/x", "--disable-gpu", "/home/u/project"))
        assertEquals(listOf("telegram-desktop", "-workdir", "/p"), kept("telegram-desktop", "-workdir", "/p", "--", "tg://resolve?domain=x"))
        assertEquals(listOf("wine", "game.exe"), kept("wine", "game.exe", "save1.dat"))
        assertEquals(listOf("python3", "/home/u/keyboard-tool.py", "--keyboard-layout=us"), kept("python3", "/home/u/keyboard-tool.py", "--keyboard-layout=us"))
        assertEquals(listOf("python3.12", "-m", "http.server"), kept("python3.12", "-m", "http.server", "8000"))
        assertEquals(listOf("java", "-cp", "lib/app.jar", "app.Main"), kept("java", "-cp", "lib/app.jar", "app.Main", "file.txt"))
        assertEquals(listOf("chromium", "--app-id=abcdefghijklmnopabcdefghijklmnop", "--profile-directory", "Default"),
            kept("chromium", "--app-id=abcdefghijklmnopabcdefghijklmnop", "--profile-directory", "Default"))
    }

    @Test
    fun `secrets are left out in any form`() {
        assertEquals(listOf("mysql-workbench"), kept("mysql-workbench", "-phunter2"))
        assertEquals(listOf("chromium", "--incognito"), kept("chromium", "--proxy-server=http://bob:hunter2@proxy:3128", "--incognito"))
        assertEquals(listOf("chromium"), kept("chromium", "--app=https://mail.example.org/"))
        assertEquals(listOf("app", "--verbose"), kept("app", "--token", "abc123", "--verbose"))
        assertEquals(listOf("app"), kept("app", "--api-key=k", "--AccessToken=t", "--db-password=x", "--Cookie=c", "--passphrase", "pp"))
        assertEquals(listOf("app"), kept("app", "--session=Zm9vYmFyYmF6cXV4MTIzNDU2Nzg5MA=="))
        assertEquals(listOf("python3", "tool.py"), kept("python3", "tool.py", "--secret=s", "https://u:p@host/x"))
        assertEquals(listOf("firefox"), kept("firefox", "-P", "https://bob:pw@host/"), "a kept value with a password in it goes, its option with it")
    }

    @Test
    fun `the lists are settings`() {
        val rules = KeptCommandRules(interpreters = listOf("love"), keepValues = listOf("--server"), secretWords = listOf("room"), secretShortOptions = emptyList())
        assertEquals(listOf("love", "game.love"), kept("love", "game.love", "x", rules = rules))
        assertEquals(listOf("client", "--server", "irc.example.org"), kept("client", "--server", "irc.example.org", "--room=x", rules = rules))
        assertEquals(listOf("mysql", "-phunter2"), kept("mysql", "-phunter2", rules = rules))
        assertEquals(listOf("wine"), kept("wine", "game.exe", rules = rules))
    }
}
