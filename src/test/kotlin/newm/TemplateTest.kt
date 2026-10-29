package newm

import dev.caulis.wm.Caulis
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The template newm writes the first time (config.kts): it compiles as it is, in every language,
 * with every setting turned on (the commented ones too), and its notes are translated whole.
 */
class TemplateTest {

    private val template = TemplateTest::class.java.getResourceAsStream("/newm/templates/config.kts")!!.use { String(it.readAllBytes()) }

    // Compiles and runs [text] as config.kts: its settings.
    private fun run(text: String): Map<String, Any?> {
        val file = Files.createTempFile("config", ".kts")
        try {
            Files.writeString(file, text)
            val compiled = KtsConfig.compile(file)
            assertEquals(emptyList(), compiled.problems)
            val config = NewmConfig(Caulis())
            assertEquals(emptyList(), KtsConfig.run(compiled, config))
            return config.values
        } finally {
            Files.deleteIfExists(file)
        }
    }

    // [text] with every setting turned off by // turned on ("// numLock = true" → "numLock = true").
    private fun uncommented(text: String): String = text.lines().joinToString("\n") { line ->
        val indent = line.takeWhile { it == ' ' }
        val rest = line.substring(indent.length)
        if (rest.startsWith("//") && TemplateText.isCode(rest.substring(2))) indent + rest.substring(2).trimStart() else line
    }

    @Test
    fun `every note has its words in every language, and no words are left over`() {
        val notes = TemplateText.notes(template)
        assertTrue(notes.size > 100, "${notes.size} notes")
        for (language in TemplateText.LANGUAGES) {
            val table = TemplateText.table(language)
            assertEquals(emptyList(), notes.filter { it !in table }, "notes of config.kts without words in config.$language.txt")
            assertEquals(emptyList(), table.keys.filter { it !in notes }, "words in config.$language.txt for notes config.kts has no more")
        }
    }

    @Test
    fun `each language has the same code, and the way back gives the template again`() {
        for (language in TemplateText.LANGUAGES) {
            val translated = TemplateText.translate(template, language)
            assertTrue(translated != template, "$language: nothing translated")
            assertEquals(TemplateText.code(template), TemplateText.code(translated), "$language: the code changed")
            assertEquals(template, TemplateText.translate(translated, "en"), "$language: back to English")
        }
        assertEquals(template, TemplateText.translate(template, "en"))
    }

    @Test
    fun `the template compiles as it is, in English and in each language`() {
        val values = run(template)
        @Suppress("UNCHECKED_CAST")
        assertEquals("newm", (values["animations"] as Map<String, Any?>)["preset"], "animations on in the template")
        for (language in TemplateText.LANGUAGES) run(TemplateText.translate(template, language))
    }

    @Test
    fun `the template compiles with every setting turned on`() {
        val all = uncommented(template)
        assertTrue("layouts = \"us,ru\"" in all && "bind(\"Super+Backspace\", \"close-window\")" in all && "\n    // bind(" !in all, all)
        val values = run(all)
        @Suppress("UNCHECKED_CAST")
        val keys = values["key_help"] as List<Pair<String, String>>
        assertTrue(keys.any { it.first == "L-BackSpace" }, keys.toString())
        assertTrue(keys.none { it.first == "L-s" }, "unbind(\"Super+S\") took it away")
    }

    @Test
    fun `the language follows LANGUAGE, LC_ALL, LC_MESSAGES and LANG`() {
        fun lang(vararg env: Pair<String, String>) = TemplateText.systemLanguage { name -> env.toMap()[name] }
        assertEquals("ru", lang("LANG" to "ru_RU.UTF-8"))
        assertEquals("ru", lang("LC_MESSAGES" to "ru_RU.UTF-8", "LANG" to "en_US.UTF-8"))
        assertEquals("en", lang("LC_ALL" to "en_GB.UTF-8", "LANG" to "ru_RU.UTF-8"))
        assertEquals("ru", lang("LANGUAGE" to "ru:en", "LANG" to "en_US.UTF-8"))
        assertEquals("en", lang("LANG" to "de_DE.UTF-8"))
    }

    @Test
    fun `the templates written for a Russian user have Russian notes, from the system's folder too`() {
        val dir = Files.createTempDirectory("newm-templates")
        val system = Files.createTempDirectory("newm-etc")
        try {
            Templates.copyTo(dir, "ru")
            val ru = Files.readString(dir.resolve("config.kts"))
            assertTrue("// newm — ваша конфигурация" in ru, ru.take(200))
            // /etc/newm written in Russian, copied for an English user: English again.
            Files.writeString(system.resolve("config.kts"), ru)
            val user = Files.createTempDirectory("newm-user")
            Templates.copyFolder(system, user, "en")
            assertEquals(template, Files.readString(user.resolve("config.kts")))
            user.toFile().deleteRecursively()
        } finally {
            dir.toFile().deleteRecursively()
            system.toFile().deleteRecursively()
        }
    }
}
