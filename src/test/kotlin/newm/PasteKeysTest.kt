package newm

import dev.caulis.ffi.SharedMemory
import dev.caulis.protocol.client.Connection
import dev.caulis.protocol.client.wayland.WlCompositor
import dev.caulis.protocol.client.wayland.WlKeyboard
import dev.caulis.protocol.client.wayland.WlRegistry
import dev.caulis.protocol.client.wayland.WlSeat
import dev.caulis.protocol.client.wayland.WlShm
import dev.caulis.protocol.client.xdgshell.XdgSurface
import dev.caulis.protocol.client.xdgshell.XdgWmBase
import dev.caulis.wm.Caulis
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The clipboard history's paste keys reach the program by their key codes, in the first Latin
 * layout while the Russian one is on (so Alacritty, which matches Ctrl+Shift+V in the layout in
 * use, pastes too), and the Russian layout is back afterwards.
 */
@OptIn(dev.caulis.log.InternalCaulisApi::class)
class PasteKeysTest {

    @Test
    fun `Ctrl+Shift+V on the Russian layout goes as the key of V in the us layout`() = withProgram { caulis, events ->
        onLoop(caulis) { caulis.switchKeyboardLayout(1) }
        waitUntil { events.lastOrNull() == "mods 0 group 1" }
        events.clear()
        onLoop(caulis) { sendKeys(caulis, "C-S-v", onLatinLayout = true) }
        waitUntil { events.lastOrNull() == "mods 0 group 1" }
        assertEquals(
            listOf(
                "mods 0 group 0",
                "key 29 down", "mods 4 group 0", "key 42 down", "mods 5 group 0", "key 47 down",
                "key 47 up", "key 42 up", "mods 4 group 0", "key 29 up", "mods 0 group 0",
                "mods 0 group 1",
            ),
            events.toList(),
        )
        assertEquals(1, onLoop(caulis) { caulis.keyboardLayoutNow }, "the Russian layout is back")
    }

    @Test
    fun `pasteOnLatinLayout false - the layout in use, the same key codes`() = withProgram { caulis, events ->
        onLoop(caulis) { caulis.switchKeyboardLayout(1) }
        waitUntil { events.lastOrNull() == "mods 0 group 1" }
        events.clear()
        onLoop(caulis) { sendKeys(caulis, "C-v", onLatinLayout = false) }
        waitUntil { "key 29 up" in events }
        assertEquals(listOf("key 29 down", "mods 4 group 1", "key 47 down", "key 47 up", "key 29 up", "mods 0 group 1"), events.toList())
    }

    // A headless Caulis with "us,ru" and a program with a window that has the keyboard; [events]
    // what its keyboard hears.
    private fun withProgram(test: (Caulis, MutableList<String>) -> Unit) {
        assumeTrue(Files.isDirectory(Path.of("/dev/dri")), "no GPU")
        val dir = Files.createTempDirectory("newm-paste-test")
        val caulis = Caulis().apply {
            useConfigFile = false
            backend = Caulis.Backend.HEADLESS
            socketDirectory = dir
            exitAfterSeconds = 60
            keyboardLayouts = "us,ru"
            keyboardOptions = "grp:win_space_toggle"
        }
        val started = CompletableFuture<Unit>()
        caulis.onStart { started.complete(Unit) }
        val thread = Thread.ofPlatform().start { caulis.run() }
        try {
            started.get(20, TimeUnit.SECONDS)
            val socket = Files.list(dir).use { files -> files.filter { it.fileName.toString().startsWith("wayland-") && !it.toString().endsWith(".lock") }.findFirst().get() }
            Connection.connect(socket).use { connection ->
                val names = HashMap<String, Int>()
                val registry = connection.display.getRegistry()
                registry.events = object : WlRegistry.Events {
                    override fun global(proxy: WlRegistry, name: Int, `interface`: String, versionArg: Int) {
                        names[`interface`] = name
                    }
                }
                connection.roundtrip()
                val compositor = registry.bind(names.getValue("wl_compositor"), WlCompositor, 4)
                val shm = registry.bind(names.getValue("wl_shm"), WlShm, 1)
                val wmBase = registry.bind(names.getValue("xdg_wm_base"), XdgWmBase, 2)
                val seat = registry.bind(names.getValue("wl_seat"), WlSeat, 5)
                val events = Collections.synchronizedList(ArrayList<String>())
                seat.getKeyboard().events = object : WlKeyboard.Events {
                    override fun key(proxy: WlKeyboard, serial: Int, time: Int, key: Int, state: Int) {
                        events += "key $key ${if (state == 1) "down" else "up"}"
                    }
                    override fun modifiers(proxy: WlKeyboard, serial: Int, modsDepressed: Int, modsLatched: Int, modsLocked: Int, group: Int) {
                        events += "mods $modsDepressed group $group"
                    }
                }
                wmBase.events = object : XdgWmBase.Events {
                    override fun ping(proxy: XdgWmBase, serial: Int) = proxy.pong(serial)
                }
                val surface = compositor.createSurface()
                val xdgSurface = wmBase.getXdgSurface(surface)
                xdgSurface.getToplevel()
                xdgSurface.events = object : XdgSurface.Events {
                    override fun configure(proxy: XdgSurface, serial: Int) = proxy.ackConfigure(serial)
                }
                surface.commit()
                connection.roundtrip()
                SharedMemory.create("picture", 64L * 64 * 4).use { memory ->
                    val pool = shm.createPool(memory.fd, 64 * 64 * 4)
                    surface.attach(pool.createBuffer(0, 64, 64, 64 * 4, WlShm.Format.XRGB8888), 0, 0)
                    surface.damageBuffer(0, 0, 64, 64)
                    pool.destroy()
                    surface.commit()
                    connection.roundtrip()
                    this.connection = connection
                    waitUntil { onLoop(caulis) { caulis.desktop.keyboardFocus != null } }
                    test(caulis, events)
                }
            }
        } finally {
            connection = null
            runCatching { caulis.post { quit() } }
            thread.join(10_000)
            dir.toFile().deleteRecursively()
        }
    }

    // The program's connection, read while waiting.
    private var connection: Connection? = null

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000
        while (!condition()) {
            check(System.nanoTime() < deadline) { "timed out" }
            connection?.roundtrip()
            Thread.sleep(20)
        }
    }

    private fun <T> onLoop(caulis: Caulis, action: () -> T): T {
        val result = CompletableFuture<T>()
        caulis.post { runCatching(action).fold(result::complete, result::completeExceptionally) }
        return try {
            result.get(10, TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.ExecutionException) {
            throw e.cause ?: e
        }
    }
}
