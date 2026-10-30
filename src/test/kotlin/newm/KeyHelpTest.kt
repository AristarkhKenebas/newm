package newm

import dev.caulis.wm.Caulis
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The keys' help (Super+/): every key bound, in words, grouped, in the user's language. */
class KeyHelpTest {

    // Every named action on a key of its own (config.json's way), with its arguments.
    private fun allActions(): Pair<KeyHelpSources, Map<String, String>> {
        val keys = (('a'..'z').map { it.toString() } + (0..9).map { it.toString() } + (1..35).map { "F$it" })
        val combos = LinkedHashMap<String, String>()
        val data = LinkedHashMap<String, Any?>()
        KeyActions.ALL.forEachIndexed { n, action ->
            val combo = "Super+Ctrl+Alt+${keys[n]}"
            val args = when (action.name) {
                "launch" -> " foot"
                "go-to-window" -> " 1"
                "caulis" -> " volume +5"
                else -> ""
            }
            data[combo] = action.name + args
            combos[action.name] = KeyNotation.toNewm(combo)
        }
        val (s, problems) = DataConfig.settings(mapOf("keys" to data), Caulis(), "config.json")
        assertEquals(emptyList(), problems)
        @Suppress("UNCHECKED_CAST")
        val sources = KeyHelpSources(bindings = s["key_help"] as List<Pair<String, String>>, actions = s["key_actions"] as Map<String, String>)
        return sources to combos
    }

    @Test
    fun `every bound action shows with its readable key, words and group`() {
        val (sources, combos) = allActions()
        val entries = keyHelpEntries(sources)
        for (action in KeyActions.ALL) {
            val group = assertNotNull(keyHelpGroupOf(action.name), "${action.name} has a group")
            val key = keyHelpWords(combos.getValue(action.name))
            assertTrue(key.startsWith("Super+Ctrl+Alt+"), key)
            assertTrue(entries.any { key in it.keys && it.what == action.description && it.group == group }, "${action.name} ($key) in the help: $entries")
        }
    }

    @Test
    fun `keys of config kts in both forms, a laptop's keys, Caulis's own, the overlays`() {
        val file = Files.createTempFile("config", ".kts")
        try {
            Files.writeString(
                file,
                """
                keys {
                    "L-b"("Firefox") { launch("firefox") }
                    "L-y" { sideBySide() }
                }
                bind("Super+Backspace", "close-window")
                bind("Super+Shift+F", "launch", "thunar", description = "Files")
                bind("VolumeUp", "caulis", "volume +5", description = "Louder")
                """.trimIndent(),
            )
            val compiled = KtsConfig.compile(file)
            val config = NewmConfig(Caulis())
            assertEquals(emptyList(), compiled.problems + KtsConfig.run(compiled, config, file.fileName.toString()))
            @Suppress("UNCHECKED_CAST")
            val sources = KeyHelpSources(
                bindings = config.values["key_help"] as List<Pair<String, String>>,
                actions = config.values["key_actions"] as Map<String, String>,
                caulisKeys = mapOf("volumeUp" to "XF86AudioRaiseVolume", "volumeMute" to "XF86AudioMute", "quit" to "Ctrl+Alt+BackSpace", "consoles" to "Ctrl+Alt"),
            )
            val entries = keyHelpEntries(sources)
            fun line(key: String) = entries.firstOrNull { key in it.keys }
            assertEquals(KeyHelpEntry(KeyHelpGroup.WINDOWS, listOf("Super+Backspace"), "Close the window"), line("Super+Backspace"))
            assertEquals(KeyHelpGroup.PROGRAMS, line("Super+Shift+F")?.group)
            assertEquals("Files", line("Super+Shift+F")?.what)
            assertEquals("Firefox", line("Super+B")?.what)
            assertEquals(KeyHelpGroup.OWN, line("Super+Y")?.group, "a key without words is listed too")
            // newm's binding of the volume key wins over Caulis's; Caulis's mute, quit and consoles are told.
            assertEquals(listOf("Louder"), entries.filter { "Volume Up" in it.keys }.map { it.what })
            assertEquals(KeyHelpGroup.SYSTEM, line("Volume Up")?.group)
            assertEquals("Sound off, and on", line("Mute")?.what)
            assertNotNull(line("Ctrl+Alt+Backspace"))
            assertNotNull(line("Ctrl+Alt+F1 … F12"))
            // The mouse, the touchpad, the launcher, the clipboard's history, the screenshot chooser.
            assertEquals("Move the window", line("Super + left drag")?.what)
            assertNotNull(line("3 fingers"))
            assertEquals("Start the chosen one", entries.first { it.group == KeyHelpGroup.LAUNCHER && "Return" in it.keys }.what)
            assertTrue(entries.any { it.group == KeyHelpGroup.CLIPBOARD_HISTORY && "Delete" in it.keys })
            assertTrue(entries.any { it.group == KeyHelpGroup.CHOOSER && "W" in it.keys && it.what == "A window" })
            // The groups in their order.
            assertEquals(entries.map { it.group.ordinal }.sorted(), entries.map { it.group.ordinal })
            // Searching: by key, by words.
            assertEquals(listOf("Files"), entries.filter { keyHelpMatches(it, "shift+f") && it.group == KeyHelpGroup.PROGRAMS }.map { it.what })
            assertTrue(entries.filter { keyHelpMatches(it, "close window") }.all { "lose" in it.what || "window" in it.what.lowercase() })
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `the help is in Russian for a Russian user`() {
        assertEquals("ru", TemplateText.systemLanguage { name -> mapOf("LANGUAGE" to "ru", "LANG" to "en_US.UTF-8")[name] })
        val (sources, _) = allActions()
        val all = keyHelpEntries(KeyHelpSources(sources.bindings, sources.actions, caulisKeys = mapOf("volumeUp" to "XF86AudioRaiseVolume", "quit" to "Ctrl+Alt+BackSpace", "consoles" to "Ctrl+Alt"), launcherView = "categories"))
        Config.loadConfig(mapOf("language" to "ru"))
        try {
            val cyrillic = Regex("[а-яё]", RegexOption.IGNORE_CASE)
            val untranslated = (all.map { it.what } + KeyHelpGroup.entries.map { it.title } + listOf("Your own (no words given)", "Type to search keys", "Esc closes"))
                .distinct().filter { !cyrillic.containsMatchIn(newm.bar.tr(it)) }
            assertEquals(emptyList(), untranslated)
            assertEquals("3 пальца", keyHelpKeyText("3 fingers"))
            assertEquals("Super + тянуть ПКМ", keyHelpKeyText("Super + right drag"))
            assertEquals("Delete", keyHelpKeyText("Delete"), "a key's name stays")
            assertTrue(all.any { keyHelpMatches(it, "закрыть окно") }, "Russian words are searched")
        } finally {
            Config.loadConfig(mapOf("language" to "en"))
        }
    }
}
