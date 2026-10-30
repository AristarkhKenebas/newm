package newm

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The launcher's search besides programs: the calculator, the files used lately, commands, newm's actions. */
class LauncherSourcesTest {

    @Test
    fun `sums are worked out, other text is not`() {
        assertEquals("4", Calculator.evaluate("2+2")?.let(Calculator::format))
        assertEquals("10.5", Calculator.evaluate("(1.5 + 2) * 3")?.let(Calculator::format))
        assertEquals("1024", Calculator.evaluate("2^10")?.let(Calculator::format))
        assertEquals("3", Calculator.evaluate("15 % 4")?.let(Calculator::format))
        assertEquals("3.5", Calculator.evaluate("7/2")?.let(Calculator::format))
        assertEquals("9", Calculator.evaluate("-3*-3")?.let(Calculator::format))
        assertEquals("5", Calculator.evaluate("2,5*2")?.let(Calculator::format))
        assertEquals("4", Calculator.evaluate("sqrt(16)")?.let(Calculator::format))
        assertEquals("0.3333333333", Calculator.evaluate("1/3")?.let(Calculator::format))
        assertEquals("2", Calculator.evaluate("2^3^0")?.let(Calculator::format))
        assertNull(Calculator.evaluate("1/0"))
        assertNull(Calculator.evaluate("2+"))
        assertNull(Calculator.evaluate("firefox"))
        assertNull(Calculator.evaluate("(".repeat(500) + "1" + ")".repeat(500)))
        assertTrue(Calculator.looksLikeSum("2+2"))
        assertTrue(Calculator.looksLikeSum("=7"))
        assertTrue(Calculator.looksLikeSum("sqrt(2)"))
        assertFalse(Calculator.looksLikeSum("42"))
        assertFalse(Calculator.looksLikeSum("-5"))
        assertFalse(Calculator.looksLikeSum("firefox"))
        assertFalse(Calculator.looksLikeSum("vlc 2+2"))
    }

    @Test
    fun `the files used lately, newest first, only those still there`() {
        val dir = Files.createTempDirectory("recent")
        try {
            val a = Files.writeString(dir.resolve("notes one.txt"), "x")
            val b = Files.writeString(dir.resolve("b&c.pdf"), "x")
            val xbel = dir.resolve("recently-used.xbel")
            Files.writeString(xbel, """
                <?xml version="1.0" encoding="UTF-8"?>
                <xbel version="1.0">
                  <bookmark href="file://${a.toString().replace(" ", "%20")}" added="2026-10-01T10:00:00Z" modified="2026-10-01T10:00:00Z" visited="2026-10-01T10:00:00Z"/>
                  <bookmark href="file://${b.toString().replace("&", "&amp;")}" added="2026-10-02T10:00:00Z" modified="2026-10-02T10:00:00Z" visited="2026-10-02T10:00:00Z"/>
                  <bookmark href="file://$dir/gone.txt" added="2026-10-03T10:00:00Z" modified="2026-10-03T10:00:00Z"/>
                  <bookmark href="https://example.org/" added="2026-10-04T10:00:00Z" modified="2026-10-04T10:00:00Z"/>
                </xbel>
            """.trimIndent())
            val files = RecentFiles.read(xbel, most = 10)
            assertEquals(listOf("b&c.pdf", "notes one.txt"), files.map { it.name })
            assertEquals(1, RecentFiles.read(xbel, most = 1).size)
            assertEquals(emptyList(), RecentFiles.read(xbel, most = 0))
            assertEquals(emptyList(), RecentFiles.read(dir.resolve("none.xbel"), most = 5))
            assertEquals(emptyList(), RecentFiles.read(xbel, most = 5, maxBytes = 10))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private val programs = listOf(DesktopApp("Firefox", "", "firefox", "", id = "firefox"), DesktopApp("foot", "", "foot", "", id = "foot"))
    private val actions = listOf(DesktopApp("Lock the screen", "newm", "", "lock", id = "action:lock", kind = "action"))
    private val files = listOf(RecentFile("/home/u/lock-notes.txt", "lock-notes.txt", "2026"))
    private fun results(q: String, sources: Set<String> = LAUNCHER_SOURCES.toSet()) =
        universalResults(programs, actions, files, q, sources, 1, 6, ">", "xdg-open", copy = { _, _ -> })

    @Test
    fun `everything found, by kind`() {
        assertEquals(listOf("calc"), results("2*21").map { it.kind })
        assertEquals("= 42", results("2*21").single().name)
        assertEquals(listOf("action", "file"), results("lock").map { it.kind })
        assertEquals(listOf("app"), results("fire", setOf("programs")).map { it.kind })
        // A command: that and nothing else.
        val command = results("> htop -d 5").single()
        assertEquals("command", command.kind)
        assertEquals("htop -d 5", command.command)
        assertEquals(emptyList(), results(">"))
        // Without the sources: programs only.
        assertEquals(emptyList(), results("lock", setOf("programs")))
        assertEquals(emptyList(), results("2*21", setOf("programs")))
    }

    @Test
    fun `a path is one word for the shell`() {
        assertEquals("'it'\\''s here.txt'", shellQuoted("it's here.txt"))
    }
}
