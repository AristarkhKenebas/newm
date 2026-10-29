package newm

import dev.caulis.wm.Caulis
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Keys written as people say them ("Super+Backspace"), named actions, bind and unbind. */
class KeyNotationTest {

    @Test
    fun `readable names, any case, every alias`() {
        assertEquals("L-BackSpace", KeyNotation.toNewm("Super+Backspace"))
        assertEquals("L-BackSpace", KeyNotation.toNewm("super+backspace"))
        assertEquals("L-BackSpace", KeyNotation.toNewm("Logo+BackSpace"))
        assertEquals("L-BackSpace", KeyNotation.toNewm("Win+BACKSPACE"))
        assertEquals("L-BackSpace", KeyNotation.toNewm("Mod+Backspace"))
        assertEquals("L-BackSpace", KeyNotation.toNewm("Meta+Backspace"))
        assertEquals("L-S-q", KeyNotation.toNewm("Super+Shift+Q"))
        assertEquals("L-S-q", KeyNotation.toNewm("Shift+Super+q"))
        assertEquals("C-A-t", KeyNotation.toNewm("Ctrl+Alt+T"))
        assertEquals("C-A-t", KeyNotation.toNewm("Control+Alt+t"))
        assertEquals("L-Return", KeyNotation.toNewm("Super+Enter"))
        assertEquals("L-space", KeyNotation.toNewm("Super+Space"))
        assertEquals("L-Escape", KeyNotation.toNewm("Super+Esc"))
        assertEquals("L-F1", KeyNotation.toNewm("Super+f1"))
        assertEquals("Print", KeyNotation.toNewm("Print"))
        assertEquals("A-Print", KeyNotation.toNewm("Alt+PrintScreen"))
        assertEquals("L-Left", KeyNotation.toNewm("Super+left"))
        assertEquals("L-minus", KeyNotation.toNewm("Super+-"))
        assertEquals("L-plus", KeyNotation.toNewm("Super++"))
        assertEquals("L-1", KeyNotation.toNewm("Super+1"))
        assertEquals("XF86AudioMute", KeyNotation.toNewm("Mute"))
        assertEquals("XF86AudioMute", KeyNotation.toNewm("XF86AudioMute"))
        assertEquals("XF86Favorites", KeyNotation.toNewm("XF86Favorites"))
        // A modifier alone: tapped.
        assertEquals("L-", KeyNotation.toNewm("Super"))
        // A sequence.
        assertEquals("L-a L-b", KeyNotation.toNewm("Super+A Super+B"))
    }

    @Test
    fun `newm's own notation stays as it was`() {
        for (old in listOf("L-q", "L-S-h", "L-", "L-C", "A-Print", "L-a L-b", "XF86AudioMute", "L-minus", "C-n")) {
            assertEquals(old, KeyNotation.toNewm(old))
        }
    }

    @Test
    fun `a key that cannot be read says which and why`() {
        val e = assertFailsWith<KeyNotation.BadKey> { KeyNotation.toNewm("Super+Backspcae") }
        assertTrue("Backspcae" in e.message!! && "BackSpace" in e.message!!, e.message)
        assertTrue("no modifier" in assertFailsWith<KeyNotation.BadKey> { KeyNotation.toNewm("Q+Super") }.message!!)
        assertFailsWith<KeyNotation.BadKey> { KeyNotation.toNewm("Super+") }
        assertFailsWith<KeyNotation.BadKey> { KeyNotation.toNewm("") }
    }

    @Test
    fun `back to words`() {
        assertEquals("Super+Shift+H", KeyNotation.readable("L-S-h"))
        assertEquals("Super+Shift+H", KeyNotation.readable("L-H"))
        assertEquals("Super", KeyNotation.readable("L-"))
        assertEquals("Super+Backspace", KeyNotation.readable("L-BackSpace"))
        assertEquals("Alt+Print", KeyNotation.readable("A-Print"))
        for (k in listOf("Super+Backspace", "Ctrl+Alt+T", "Super+Shift+Return", "Super+Minus")) assertEquals(k, KeyNotation.readable(KeyNotation.toNewm(k)))
    }

    @Test
    fun `named actions by any spelling, and every name known`() {
        assertEquals("close-window", KeyActions.byName("close-window")?.name)
        assertEquals("close-window", KeyActions.byName("closeWindow")?.name)
        assertEquals("close-window", KeyActions.byName("close_window")?.name)
        assertEquals(null, KeyActions.byName("explode"))
        assertEquals(KeyActions.ALL.size, KeyActions.ALL.map { it.name }.toSet().size, "names twice")
    }

    @Test
    fun `the guide's keys page lists every action, in each language`() {
        for (language in listOf("en", "ru")) {
            val page = java.nio.file.Path.of("docs/$language/keys.md")
            if (!Files.exists(page)) continue // (not run from the project)
            val text = Files.readString(page)
            assertEquals(emptyList(), KeyActions.ALL.map { it.name }.filter { "| `$it` |" !in text }, "actions missing in $page")
        }
    }

    // config.kts's [text] run: its settings, or its problems.
    private fun kts(text: String): Pair<Map<String, Any?>, List<String>> {
        val file = Files.createTempFile("config", ".kts")
        try {
            Files.writeString(file, text)
            val compiled = KtsConfig.compile(file)
            val config = NewmConfig(Caulis())
            val problems = compiled.problems + KtsConfig.run(compiled, config, file.fileName.toString())
            return config.values to problems
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun help(values: Map<String, Any?>) = (values["key_help"] as List<Pair<String, String>>).toMap()

    @Test
    fun `bind and unbind in config kts`() {
        val (values, problems) = kts(
            """
            keys {
                "L-q"("Close the window") { closeFocusedView() }
                "L-s" { sideBySide() }
                bind("Super+Return", "launch", "foot")
            }
            bind("Super+Backspace", "close-window")
            unbind("Super+S")
            keys { "Super+Shift+Q"("Close it too") { closeFocusedView() } }
            """.trimIndent(),
        )
        assertEquals(emptyList(), problems)
        val keys = help(values)
        assertEquals(setOf("L-q", "L-Return", "L-BackSpace", "L-S-q"), keys.keys)
        assertEquals("Close the window", keys["L-BackSpace"])
        assertEquals("Start a program", keys["L-Return"])
    }

    @Test
    fun `a bad key or action in config kts is a problem with its line`() {
        val (_, problems) = kts("keys {\n    bind(\"Super+Bakspace\", \"close-window\")\n}")
        assertTrue(problems.single().contains(":2") && problems.single().contains("Bakspace"), problems.toString())
        val (_, wrong) = kts("bind(\"Super+Q\", \"close-windw\")")
        assertTrue(wrong.single().contains("close-windw") && wrong.single().contains("close-window"), wrong.toString())
    }

    @Test
    fun `config json and lua keys take readable keys and action names`() {
        val data = mapOf(
            "keys" to linkedMapOf(
                "Super+Backspace" to "close-window",
                "Super+Return" to "launch foot",
                "L-h" to "move -1 0",
                "Super+1" to "go-to-window 1",
                "XF86AudioMute" to "volume mute",
                "Super+Q" to false,
                "Super+Bakspace" to "close-window",
            ),
        )
        val (s, problems) = DataConfig.settings(data, Caulis(), "config.json")
        assertTrue(problems.single().contains("Bakspace"), problems.toString())
        @Suppress("UNCHECKED_CAST")
        val keys = (s["key_help"] as List<Pair<String, String>>).toMap()
        assertEquals(setOf("L-BackSpace", "L-Return", "L-h", "L-1", "XF86AudioMute"), keys.keys)
        assertEquals("Close the window", keys["L-BackSpace"])
        val (_, bad) = DataConfig.settings(mapOf("keys" to mapOf("Super+Q" to "close-windw")), Caulis(), "config.json")
        assertTrue(bad.single().contains("close-windw"), bad.toString())
    }
}
