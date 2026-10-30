package newm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The launcher's search: typos, the other keyboard layout, a word's first letters; exact matches first. */
class LauncherSearchTest {

    private fun app(name: String, keywords: String = "", comment: String = "", command: String = name.lowercase()) =
        DesktopApp(name, comment, command, keywords, id = name.lowercase())

    private val apps = listOf(
        app("Firefox", "Internet;WWW;Browser;", "Browse the web"),
        app("Files", "folder;manager;", command = "nautilus"),
        app("GNOME Text Editor", "text;editor;", command = "gnome-text-editor"),
        app("foot", "terminal;"),
        app("Telegram Desktop", "chat;Телеграм;"),
        app("Calculator", "calc;"),
    )

    private fun names(query: String, typos: Int = 1) = DesktopApp.matching(apps, query, typos).map { it.name }

    @Test
    fun `the distance counts changes, additions, removals and swaps`() {
        assertEquals(0, LauncherSearch.distance("firefox", "firefox", 2))
        assertEquals(1, LauncherSearch.distance("fierfox", "firefox", 2))
        assertEquals(1, LauncherSearch.distance("firfox", "firefox", 2))
        assertEquals(1, LauncherSearch.distance("firefoxx", "firefox", 2))
        assertEquals(1, LauncherSearch.distance("firefix", "firefox", 2))
        assertEquals(3, LauncherSearch.distance("abc", "xyz", 2))
        assertEquals(2, LauncherSearch.distance("ab", "ba ", 1))
    }

    @Test
    fun `typos find the program, exact matches come first`() {
        assertEquals("Firefox", names("fierfox").first())
        assertEquals("Firefox", names("firfox").first())
        assertEquals(setOf("Firefox", "Files"), names("fi").take(2).toSet())
        // With typos off: only exact.
        assertTrue(names("fierfox", typos = 0).isEmpty())
        // Short words: never with typos ("fot" is not "foot", but it is in no name either).
        assertTrue(names("fpt").isEmpty())
    }

    @Test
    fun `typed on the other layout`() {
        assertEquals("Firefox", names("ашкуащч").first())
        assertEquals("Telegram Desktop", names("ntktuhfv").first())
        assertEquals("firefox", LauncherSearch.latinOf("ашкуащч"))
        assertEquals("телеграм", LauncherSearch.cyrillicOf("ntktuhfv"))
    }

    @Test
    fun `first letters of the words`() {
        assertEquals("GNOME Text Editor", names("gte").first())
    }

    @Test
    fun `scores rank kinds`() {
        val s0 = LauncherSearch.score("Firefox", "", "firefox", "fire", 1)!!
        val s6 = LauncherSearch.score("Firefox", "", "firefox", "fierf", 1)!!
        assertTrue(s0 < s6)
        assertNull(LauncherSearch.score("Firefox", "", "firefox", "zzzz", 1))
        assertEquals(0, LauncherSearch.typosAllowed(3, 2))
        assertEquals(1, LauncherSearch.typosAllowed(5, 2))
        assertEquals(2, LauncherSearch.typosAllowed(9, 2))
    }
}
