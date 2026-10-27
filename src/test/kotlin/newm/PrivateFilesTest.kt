package newm

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals

/** What newm keeps of the session (session.json, notifications, pins) is written whole, for its user only. */
class PrivateFilesTest {

    @Test
    fun `a private file is replaced whole and stays 0600`() {
        val dir = Files.createTempDirectory("newm-private")
        val file = dir.resolve("session.json")
        Files.writeString(file, "old")
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"))
        writePrivately(file, "new")
        assertEquals("new", Files.readString(file))
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
        writePrivately(dir.resolve("fresh.json"), "x")
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve("fresh.json"))))
        // (No half-written file left beside them.)
        assertEquals(setOf("session.json", "fresh.json"), Files.list(dir).use { s -> s.map { it.fileName.toString() }.toList().toSet() })
    }

    @Test
    fun `the greeter's users are read from passwd lines`() {
        val lines = listOf("root:x:0:0::/root:/bin/bash", "anna:x:1000:1000:Anna:/home/anna:/usr/bin/zsh", "broken:x", "")
        assertEquals(listOf("root" to "/bin/bash", "anna" to "/usr/bin/zsh"), Users.passwdUsers(lines))
    }
}
