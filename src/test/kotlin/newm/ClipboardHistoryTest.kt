package newm

import dev.caulis.wm.Caulis
import kotlin.test.Test
import kotlin.test.assertTrue

/** The clipboard's history keeps its pictures within clipboard { picturesTotalMb }. */
class ClipboardHistoryTest {

    @Test
    fun `pictures kept stay within picturesTotalMb, the oldest go first`() {
        Config.loadConfig(mapOf("clipboard" to mapOf("pictures_total_mb" to 2)))
        try {
            val history = ClipboardHistory(Layout(Caulis()))
            // (Five photos' PNGs of 700 KB each.)
            repeat(5) { n -> history.add(ClipEntry(null, ByteArray(700_000) { n.toByte() }, null, 100, 100)) }
            val kept = history.entries.sumOf { it.png?.size ?: 0 }
            assertTrue(kept <= 2 shl 20, "$kept bytes kept")
            assertTrue(history.entries.first().png!![0] == 4.toByte(), "the newest is kept")
        } finally {
            Config.loadConfig(emptyMap())
        }
    }
}
