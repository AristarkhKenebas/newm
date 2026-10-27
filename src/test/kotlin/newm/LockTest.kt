package newm

import dev.caulis.ffi.SharedMemory
import dev.caulis.protocol.client.Connection
import dev.caulis.protocol.client.wayland.WlCompositor
import dev.caulis.protocol.client.wayland.WlKeyboard
import dev.caulis.protocol.client.wayland.WlRegistry
import dev.caulis.protocol.client.wayland.WlSeat
import dev.caulis.protocol.client.wayland.WlShm
import dev.caulis.protocol.client.wayland.WlSurface
import dev.caulis.protocol.client.xdgshell.XdgSurface
import dev.caulis.protocol.client.xdgshell.XdgToplevel
import dev.caulis.protocol.client.xdgshell.XdgWmBase
import dev.caulis.wm.Caulis
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * newm's own lock screen is a lock for Caulis too (desktop.locked): the keys stay with it when
 * a window opens, when the focused window closes, and when a click lands beside it.
 */
@OptIn(dev.caulis.log.InternalCaulisApi::class)
class LockTest {

    @Test
    fun `the keys stay on the lock screen whatever the windows do`() = withNewm { caulis, layout, client ->
        val keys = CopyOnWriteArrayList<Int>()
        client.keyboard.events = object : WlKeyboard.Events {
            override fun key(proxy: WlKeyboard, serial: Int, time: Int, key: Int, state: Int) {
                keys += key
            }
        }
        // Two windows, the second with the focus.
        val first = client.window()
        first.draw()
        val second = client.window()
        second.draw()
        client.waitFor { onLoop(caulis) { caulis.desktop.windows.size == 2 && caulis.desktop.keyboardFocus === caulis.desktop.windows.last().surface } }

        onLoop(caulis) { layout.ensureLocked(anim = false) }
        val screen = onLoop(caulis) { layout.panelsUi.lockScreenWidget() }
        onLoop(caulis) {
            assertTrue(caulis.desktop.locked, "Caulis knows newm is locked")
            assertSame(screen, caulis.desktop.keyboardWidget)
            assertNull(caulis.desktop.keyboardFocus, "no window has the keyboard")
        }

        // A click beside the lock screen (a corner of the screen).
        onLoop(caulis) {
            caulis.simulatePointer(2.0, 2.0)
            caulis.simulateButton(BTN_LEFT, true)
            caulis.simulateButton(BTN_LEFT, false)
        }
        onLoop(caulis) { assertSame(screen, caulis.desktop.keyboardWidget, "a click beside it leaves the keys there") }

        // A new window opens.
        val third = client.window()
        third.draw()
        client.waitFor { onLoop(caulis) { caulis.desktop.windows.size == 3 } }
        onLoop(caulis) {
            assertSame(screen, caulis.desktop.keyboardWidget, "a new window does not take the keys")
            assertNull(caulis.desktop.keyboardFocus)
        }

        // The focused window closes.
        second.close()
        client.waitFor { onLoop(caulis) { caulis.desktop.windows.size == 2 } }
        onLoop(caulis) {
            assertSame(screen, caulis.desktop.keyboardWidget, "the focused window closing does not hand the keys on")
            assertNull(caulis.desktop.keyboardFocus)
        }

        // Typed meanwhile: no program heard it.
        onLoop(caulis) {
            caulis.simulateKey(KEY_A, true)
            caulis.simulateKey(KEY_A, false)
        }
        client.roundtrips(5)
        assertTrue(keys.isEmpty(), "keys typed on the lock screen reached a program: $keys")

        // Unlocked (as after the right password): a window has the keys again.
        onLoop(caulis) { layout.trustedUnlock() }
        onLoop(caulis) {
            assertFalse(caulis.desktop.locked)
            assertTrue(caulis.desktop.keyboardFocus != null, "a window has the keyboard again")
        }
        first.close()
        third.close()
    }

    // --- the harness: newm's layout on a headless Caulis, and a small Wayland client

    @Test
    fun `locked at once, before the lock screen slides in, with animations on or off`() = withNewm { caulis, layout, _ ->
        for (preset in listOf("newm", "none")) {
            Config.loadConfig(mapOf("animations" to mapOf("preset" to preset)))
            try {
                onLoop(caulis) {
                    layout.ensureLocked(anim = true)
                    // (In the same turn: no frame with the desktop open while the lock screen is on its way.)
                    assertTrue(caulis.desktop.locked, "locked at once (animations $preset)")
                    assertNull(caulis.desktop.keyboardFocus, "no window has the keyboard (animations $preset)")
                }
                onLoop(caulis) { layout.trustedUnlock() }
            } finally {
                Config.loadConfig(emptyMap())
            }
        }
    }

    private fun withNewm(test: (Caulis, Layout, Client) -> Unit) {
        assumeTrue(Files.isDirectory(Path.of("/dev/dri")), "no GPU")
        val dir = Files.createTempDirectory("newm-lock-test")
        val caulis = Caulis().apply {
            useConfigFile = false
            mode = Caulis.Mode.HEADLESS
            socketDirectory = dir
            exitAfterSeconds = 60 // (never a hanging test run)
            mode(LOCKED_MODE) {}
        }
        val layout = Layout(caulis)
        val started = CompletableFuture<Unit>()
        caulis.onStart { started.complete(Unit) }
        val thread = Thread.ofPlatform().start { caulis.run() }
        try {
            started.get(20, TimeUnit.SECONDS)
            val socket = Files.list(dir).use { files -> files.filter { it.fileName.toString().startsWith("wayland-") && !it.toString().endsWith(".lock") }.findFirst().get() }
            Client(Connection.connect(socket)).use { client -> test(caulis, layout, client) }
        } finally {
            runCatching { caulis.post { quit() } }
            thread.join(10_000)
            dir.toFile().deleteRecursively()
        }
    }

    // Runs [action] on the compositor's thread and returns what it gives.
    private fun <T> onLoop(caulis: Caulis, action: () -> T): T {
        val result = CompletableFuture<T>()
        caulis.post { runCatching(action).fold(result::complete, result::completeExceptionally) }
        return try {
            result.get(10, TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.ExecutionException) {
            throw e.cause ?: e
        }
    }

    /** Windows with plain pictures, and the keyboard. */
    private class Client(val connection: Connection) : AutoCloseable {
        private val names = HashMap<String, Int>()
        private val registry = connection.display.getRegistry().also { registry ->
            registry.events = object : WlRegistry.Events {
                override fun global(proxy: WlRegistry, name: Int, `interface`: String, versionArg: Int) {
                    names[`interface`] = name
                }
            }
            connection.roundtrip()
        }
        private val compositor = registry.bind(names.getValue("wl_compositor"), WlCompositor, 4)
        private val shm = registry.bind(names.getValue("wl_shm"), WlShm, 1)
        private val wmBase = registry.bind(names.getValue("xdg_wm_base"), XdgWmBase, 2)
        private val seat = registry.bind(names.getValue("wl_seat"), WlSeat, 5)
        val keyboard: WlKeyboard = seat.getKeyboard()
        private val memory = SharedMemory.create("pictures", 512L * 512 * 4)

        inner class Window(private val surface: WlSurface, private val toplevel: XdgToplevel, private val xdgSurface: XdgSurface) {
            var width = 0
            var height = 0

            fun draw() {
                val w = width.takeIf { it in 1..512 } ?: 64
                val h = height.takeIf { it in 1..512 } ?: 64
                val pool = shm.createPool(memory.fd, 512 * 512 * 4)
                surface.attach(pool.createBuffer(0, w, h, w * 4, WlShm.Format.XRGB8888), 0, 0)
                surface.damageBuffer(0, 0, w, h)
                pool.destroy()
                surface.commit()
                connection.roundtrip()
            }

            @Volatile var closed = false

            fun close() {
                closed = true
                toplevel.destroy()
                xdgSurface.destroy()
                surface.destroy()
                connection.roundtrip()
            }
        }

        fun window(): Window {
            val surface = compositor.createSurface()
            val xdgSurface = wmBase.getXdgSurface(surface)
            val toplevel = xdgSurface.getToplevel()
            val window = Window(surface, toplevel, xdgSurface)
            toplevel.events = object : XdgToplevel.Events {
                override fun configure(proxy: XdgToplevel, width: Int, height: Int, states: ByteArray) {
                    window.width = width
                    window.height = height
                }
            }
            xdgSurface.events = object : XdgSurface.Events {
                override fun configure(proxy: XdgSurface, serial: Int) {
                    if (!window.closed) proxy.ackConfigure(serial)
                }
            }
            wmBase.events = object : XdgWmBase.Events {
                override fun ping(proxy: XdgWmBase, serial: Int) = proxy.pong(serial)
            }
            surface.commit()
            connection.roundtrip()
            return window
        }

        fun roundtrips(n: Int) = repeat(n) {
            connection.roundtrip()
            Thread.sleep(20)
        }

        fun waitFor(condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000
            while (!condition()) {
                check(System.nanoTime() < deadline) { "timed out" }
                connection.roundtrip()
                Thread.sleep(20)
            }
        }

        override fun close() {
            memory.close()
            connection.close()
        }
    }

    private companion object {
        const val BTN_LEFT = 0x110
        const val KEY_A = 30
    }
}
