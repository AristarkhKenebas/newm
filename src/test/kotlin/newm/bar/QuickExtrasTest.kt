package newm.bar

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Airplane mode as rfkill's sysfs says it: every radio blocked by software. */
class QuickExtrasTest {

    @Test
    fun `airplane mode is every radio blocked`() {
        val dir = Files.createTempDirectory("rfkill")
        try {
            assertFalse(rfkillAllBlocked(dir))
            for ((name, soft) in listOf("rfkill0" to "1", "rfkill1" to "0")) {
                Files.createDirectories(dir.resolve(name))
                Files.writeString(dir.resolve("$name/soft"), "$soft\n")
            }
            assertFalse(rfkillAllBlocked(dir))
            Files.writeString(dir.resolve("rfkill1/soft"), "1\n")
            assertTrue(rfkillAllBlocked(dir))
            assertFalse(rfkillAllBlocked(dir.resolve("none")))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
