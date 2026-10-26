package newm.bar

import androidx.compose.ui.graphics.toPixelMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Tray icons' pixels (ARGB32, network byte order: Electron programs, Discord) come out in their colours. */
class IconThemeTest {

    @Test
    fun `IconPixmap pixels`() {
        // 2x1: opaque red, half-transparent blue.
        val argb = byteArrayOf(0xff.toByte(), 0xff.toByte(), 0, 0, 0x80.toByte(), 0, 0, 0xff.toByte())
        val picture = assertNotNull(IconTheme.fromArgb(2, 1, argb, 2))
        val pixels = picture.toPixelMap()
        val red = pixels[0, 0]
        assertEquals(1f, red.red, 0.02f)
        assertEquals(0f, red.blue, 0.02f)
        assertEquals(1f, red.alpha, 0.02f)
        val blue = pixels[1, 0]
        assertEquals(0.5f, blue.alpha, 0.05f)
        assertEquals(1f, blue.blue, 0.05f)
    }
}
