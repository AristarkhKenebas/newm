package newm.bar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The notifications' history: replacing, reading by focus, the old ones going, written and read back. */
class NotificationHistoryTest {
    private val hour = 3_600_000L

    private fun telegram(at: Long, summary: String = "Kirill") =
        Notification(id = 0, app = "Telegram Desktop", entry = "org.telegram.desktop", summary = summary, body = "Hello", at = at, pid = 4242)

    @Test
    fun `replaces_id keeps the number, an unknown one makes a new notification`() {
        val h = NotificationHistory()
        val first = h.add(telegram(1000))
        val second = h.add(telegram(2000, "Anna"))
        val replaced = h.add(telegram(3000, "Kirill (2)"), replaces = first.id)
        assertEquals(first.id, replaced.id)
        assertEquals(2, h.items.size)
        // The changed one is the newest now.
        assertEquals(listOf(first.id, second.id), h.items.map { it.id })
        assertEquals("Kirill (2)", h.find(first.id)?.summary)
        val fresh = h.add(telegram(4000), replaces = 999)
        assertTrue(fresh.id != 999 && fresh.id > second.id)
    }

    @Test
    fun `a glance is not reading, the focus held is`() {
        val h = NotificationHistory()
        h.add(telegram(0))
        h.add(Notification(id = 0, app = "Firefox", summary = "Download done", at = 0))
        // Telegram's window (another process: matched by its app id) focused for a second, then another one.
        h.focus(9999, "org.telegram.desktop", 10_000)
        assertEquals(emptyList(), h.readByFocus(3000, 11_000))
        h.focus(1, "foot", 11_000)
        assertEquals(emptyList(), h.readByFocus(3000, 20_000))
        // Focused again, long enough: Telegram's go, Firefox's stays.
        h.focus(9999, "org.telegram.desktop", 30_000)
        val read = h.readByFocus(3000, 33_000)
        assertEquals(listOf("Telegram Desktop"), read.map { it.app })
        assertEquals(listOf("Firefox"), h.items.map { it.app })
    }

    @Test
    fun `one that comes while its window is focused is read that long after it came`() {
        val h = NotificationHistory()
        h.focus(4242, "whatever", 0)
        h.add(telegram(10_000))
        assertEquals(emptyList(), h.readByFocus(3000, 12_000))
        assertEquals(1, h.readByFocus(3000, 13_000).size)
    }

    @Test
    fun `nothing focused reads nothing`() {
        val h = NotificationHistory()
        h.add(telegram(0))
        h.focus(0, "", 0)
        assertEquals(emptyList(), h.readByFocus(0, 100_000))
    }

    @Test
    fun `older than keepHours go (critical ones too), and past the most kept`() {
        val h = NotificationHistory()
        val now = 100 * hour
        h.add(telegram(now - 30 * hour).copy(urgency = 2))
        h.add(telegram(now - 2 * hour))
        h.add(telegram(now - hour))
        h.add(telegram(now))
        val gone = h.trim(keepHours = 24, keep = 100, now = now)
        assertEquals(1, gone.size)
        assertEquals(2, gone.single().urgency)
        assertEquals(3, h.items.size)
        // Two at most: the oldest goes.
        val more = h.trim(keepHours = 24, keep = 2, now = now)
        assertEquals(listOf(now - 2 * hour), more.map { it.at })
        assertEquals(listOf(now, now - hour), h.items.map { it.at })
    }

    @Test
    fun `written and read back, as no program knows them any more`() {
        val h = NotificationHistory()
        h.add(telegram(1000)).also { h.update(it.id) { n -> n.copy(read = true) } }
        h.add(telegram(2000, "Anna"))
        h.add(telegram(3000, "Transient").copy(transient = true))
        val text = h.toJson(100, mapOf("do_not_disturb" to true))
        val back = NotificationHistory()
        val rest = back.fromJson(text)
        assertEquals(true, rest["do_not_disturb"])
        assertEquals(listOf("Anna", "Kirill"), back.items.map { it.summary })
        assertTrue(back.items.none { it.live })
        assertEquals(1, back.unread)
        // New ones get new numbers.
        assertTrue(back.add(telegram(4000)).id > h.items.maxOf { it.id } - 1)
        assertEquals(1, NotificationHistory().apply { fromJson(h.toJson(1)) }.items.size)
    }

    @Test
    fun `belongs to the window of its process or its program`() {
        val n = telegram(0)
        assertTrue(n.belongsTo(4242, "anything"))
        assertTrue(n.belongsTo(1, "org.telegram.desktop"))
        assertTrue(n.belongsTo(1, "ORG.TELEGRAM.DESKTOP"))
        assertTrue(n.copy(entry = "", pid = 0).belongsTo(1, "org.telegram.desktop"))
        assertFalse(n.copy(pid = 0).belongsTo(1, "foot"))
        assertTrue(Notification(id = 1, app = "Firefox").belongsTo(5, "firefox"))
        assertFalse(Notification(id = 1, app = "notify-send").belongsTo(5, "foot"))
        assertFalse(Notification(id = 1, app = "Firefox").belongsTo(5, ""))
    }

    @Test
    fun `markup is taken out, entities read`() {
        assertEquals("Hello world & you\nnext <3", plainText("<b>Hello</b> <i>world</i> &amp; <a href=\"x\">you</a><br/>next &lt;3"))
        assertEquals("a 😀", plainText("a &#x1F600;"))
        assertEquals("pic", plainText("<img src=\"x.png\" alt=\"pic\"/>"))
        assertEquals("a\nb\nc", plainText("a<BR>b<br />c"))
        assertEquals("1 < 2 and &unknown; &#xZZ; &", plainText("1 < 2 and &unknown; &#xZZ; &"))
        assertEquals("x &amp;", plainText("x &amp;amp;"))
    }

    @Test
    fun `crafted markup is read in linear time`() {
        // The review's body: a backtracking pattern took seconds on it, the compositor frozen meanwhile.
        val images = "<img alt=\"x\" ".repeat(700).take(8000)
        val opened = "<".repeat(8000)
        val entities = "&#1111111".repeat(900).take(8000)
        val started = System.nanoTime()
        repeat(20) { plainText(images); plainText(opened); plainText(entities) }
        val millis = (System.nanoTime() - started) / 1_000_000
        assertTrue(millis < 1000, "20 rounds took $millis ms")
        assertEquals(images.trim(), plainText(images))
    }
}
