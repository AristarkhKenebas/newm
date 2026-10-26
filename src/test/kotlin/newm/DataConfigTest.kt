package newm

import dev.caulis.wm.Caulis
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** config.lua and config.json (Nix writes it): newm's templates load, keys and rules work, mistakes are named. */
class DataConfigTest {

    private fun template(name: String): Path {
        val file = Files.createTempFile("newm", name)
        DataConfigTest::class.java.getResourceAsStream("/newm/templates/examples/$name")!!.use { Files.copy(it, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
        return file
    }

    @Suppress("UNCHECKED_CAST")
    private fun check(settings: Map<String, Any?>) {
        assertEquals(8L, (settings["view"] as Map<String, Any?>)["padding"])
        assertEquals("us", (settings["pywm"] as Map<String, Any?>)["xkb_layout"])
        val bindings = (settings["key_bindings"] as (Layout) -> List<Pair<String, () -> Unit>>)(Layout(Caulis()))
        assertTrue(bindings.size > 30 && bindings.any { it.first == "L-Return" }, "${bindings.size}")
        val texts = (((settings["panels"] as Map<String, Any?>)["top_bar"] as Map<String, Any?>)["native"] as Map<String, Any?>)["texts"] as () -> Any?
        assertEquals(7, (texts() as List<*>).size)
    }

    @Test
    fun `the Lua template`() {
        val file = template("config.lua")
        try {
            val (settings, problems) = LuaConfig.load(file, Caulis()) { error("not used") }
            assertEquals(emptyList(), problems)
            check(settings)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `the JSON template, and a mistake named`() {
        val file = template("config.json")
        try {
            val (settings, problems) = DataConfig.settings(Json.parseObject(Files.readString(file)), Caulis(), "config.json")
            assertEquals(emptyList(), problems)
            check(settings)
        } finally {
            Files.deleteIfExists(file)
        }
        val (_, problems) = DataConfig.settings(mapOf("keys" to mapOf("L-x" to "mvoe -1 0")), Caulis(), "config.json")
        assertTrue(problems.single().startsWith("config.json: keys.L-x: \"mvoe -1 0\" is neither"), problems.toString())
    }
}
