package newm

import dev.caulis.wm.Caulis
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The showcase configuration (templates/showcase/config.kts): it compiles, in every language, and its notes are translated whole. */
class ShowcaseTest {

    private val showcase = ShowcaseTest::class.java.getResourceAsStream("/newm/templates/showcase/config.kts")!!.use { String(it.readAllBytes()) }

    // Compiles and runs [text] as config.kts: its settings.
    private fun run(text: String): Map<String, Any?> {
        val file = Files.createTempFile("showcase", ".kts")
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

    @Test
    fun `the showcase compiles, in English and in each language, with the look it promises`() {
        val values = run(showcase)
        assertEquals("jadefire", values["theme"])
        @Suppress("UNCHECKED_CAST")
        assertEquals("dragon-eye", (values["background"] as Map<String, Any?>)["shader"])
        for (language in TemplateText.LANGUAGES) run(TemplateText.translate(showcase, language, "showcase"))
    }

    @Test
    fun `every note has its words in every language, and no words are left over`() {
        val notes = TemplateText.notes(showcase)
        assertTrue(notes.size > 40, "${notes.size} notes")
        for (language in TemplateText.LANGUAGES) {
            val table = TemplateText.table(language, "showcase")
            assertEquals(emptyList(), notes.filter { it !in table }, "notes of the showcase without words in showcase.$language.txt")
            assertEquals(emptyList(), table.keys.filter { it !in notes }, "words in showcase.$language.txt for notes the showcase has no more")
            val translated = TemplateText.translate(showcase, language, "showcase")
            assertEquals(TemplateText.code(showcase), TemplateText.code(translated), "$language: the code changed")
            assertEquals(showcase, TemplateText.translate(translated, "en", "showcase"), "$language: back to English")
        }
    }

    @Test
    fun `it is among the templates written for a new user`() {
        val dir = Files.createTempDirectory("newm-templates")
        try {
            Templates.copyTo(dir, "ru")
            val ru = Files.readString(dir.resolve("showcase/config.kts"))
            assertTrue("theme = \"jadefire\"" in ru && "витрина" in ru, ru.take(300))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
