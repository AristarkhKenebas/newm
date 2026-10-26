package newm

import dev.caulis.wm.Caulis
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** config.kts: newm's own default compiles and says what newm reads; a mistake says its line. */
class KtsConfigTest {

    @Test
    fun `the default config kts gives newm's settings`() {
        val file = Files.createTempFile("config", ".kts")
        try {
            KtsConfigTest::class.java.getResourceAsStream("/newm/config.kts")!!.use { Files.copy(it, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
            val compiled = KtsConfig.compile(file)
            assertEquals(emptyList(), compiled.problems)
            val config = NewmConfig(Caulis())
            assertEquals(emptyList(), KtsConfig.run(compiled, config))
            val values = config.values
            @Suppress("UNCHECKED_CAST")
            val view = values["view"] as Map<String, Any?>
            assertEquals(8.0, view["padding"])
            @Suppress("UNCHECKED_CAST")
            val top = (values["panels"] as Map<String, Any?>)["top_bar"] as Map<String, Map<String, Any?>>
            assertEquals(true, top["native"]?.get("enabled"))
            @Suppress("UNCHECKED_CAST")
            val bindings = (values["key_bindings"] as (Layout) -> List<Pair<String, () -> Unit>>)(Layout(Caulis()))
            assertTrue(bindings.any { it.first == "L-Return" } && bindings.size > 30, "${bindings.size} keys")
            @Suppress("UNCHECKED_CAST")
            val rules = view["rules"] as (View) -> Map<String, Any?>?
            assertTrue(rules.toString().isNotEmpty())
            @Suppress("UNCHECKED_CAST")
            assertEquals("us", (values["pywm"] as Map<String, Any?>)["xkb_layout"])
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `a mistake says its line`() {
        val file = Files.createTempFile("config", ".kts")
        try {
            Files.writeString(file, "view { padding = 8.0 }\nfocus { colour = \"#fff\" }\n")
            val compiled = KtsConfig.compile(file)
            assertTrue(compiled.problems.single().startsWith("${file.fileName}:2: "), compiled.problems.toString())
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
