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
        assertEquals(null, (settings["pywm"] as? Map<String, Any?>)?.get("xkb_layout"))
        val bindings = (settings["key_bindings"] as (Layout) -> List<Pair<String, () -> Unit>>)(Layout(Caulis()))
        assertTrue(bindings.size > 30 && bindings.any { it.first == "L-Return" }, "${bindings.size}")
        val native = ((settings["panels"] as Map<String, Any?>)["top_bar"] as Map<String, Any?>)["native"] as Map<String, Any?>
        assertEquals(listOf("tray", "layout", "bluetooth", "network", "volume", "battery", "notifications", "power"), native["right"])
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
    fun `classic bar texts by name`() {
        val (settings, problems) = DataConfig.settings(mapOf("panels" to mapOf("top_bar" to mapOf("style" to "classic", "texts" to listOf("time", "newm")))), Caulis(), "config.json")
        assertEquals(emptyList(), problems)
        @Suppress("UNCHECKED_CAST")
        val native = ((settings["panels"] as Map<String, Any?>)["top_bar"] as Map<String, Any?>)["native"] as Map<String, Any?>
        assertEquals("classic", native["style"])
        @Suppress("UNCHECKED_CAST")
        assertEquals("newm", ((native["texts"] as () -> List<String>)())[1])
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
