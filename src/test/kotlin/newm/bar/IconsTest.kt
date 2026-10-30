package newm.bar

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** newm's icons are one set: Material Symbols Rounded, every one there, the names of before too. */
class IconsTest {

    @Test
    fun `every glyph at every level is a Material symbol newm carries`() {
        val missing = Glyph.entries.flatMap { g ->
            listOf(0f, 0.2f, 0.5f, 0.9f).flatMap { level -> listOf(true, false).map { slash -> iconName(g, level, slash) } }
        }.distinct().filter { !carriesIcon("material/$it") }
        assertEquals(emptyList(), missing)
    }

    @Test
    fun `the icons' names of before still show one`() {
        assertEquals(emptyList(), OLD_ICON_NAMES.filterValues { !carriesIcon("material/$it") }.keys.toList())
    }

    @Test
    fun `only Material symbols, each painted in newm's colour, with their notice`() {
        val root = Path.of("src/main/resources/newm/icons")
        assertEquals(listOf("NOTICE", "material"), Files.list(root).map { it.fileName.toString() }.sorted().toList())
        assertTrue(Files.exists(root.resolve("material/LICENSE-material-symbols")))
        val wrong = Files.list(root.resolve("material")).filter { it.toString().endsWith(".svg") }.filter { f ->
            val svg = Files.readString(f)
            !svg.startsWith("<svg fill=\"currentColor\" xmlns=\"http://www.w3.org/2000/svg\" height=\"24\" viewBox=\"0 -960 960 960\" width=\"24\"><path d=\"")
        }.map { it.fileName.toString() }.toList()
        assertEquals(emptyList(), wrong)
    }
}
