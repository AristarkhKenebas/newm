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
import kotlin.test.assertEquals
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

    // --- the lock screen's notes: the layout, Caps Lock, PAM's words, pam_faillock (a fake PAM)

    private fun Layout.notes() = panelsUi.lockNotes()

    private fun press(caulis: Caulis, vararg codes: Int) = onLoop(caulis) {
        for (code in codes) caulis.simulateKey(code, true)
        for (code in codes.reversed()) caulis.simulateKey(code, false)
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000
        while (!condition()) {
            check(System.nanoTime() < deadline) { "timed out: $what" }
            Thread.sleep(30)
        }
    }

    // Locked with newm's own lock screen, the password field ready.
    private fun lockAndWait(caulis: Caulis, layout: Layout) {
        onLoop(caulis) { layout.ensureLocked(anim = false) }
        waitUntil("the lock screen asks") {
            onLoop(caulis) { caulis.desktop.keyboardWidget === layout.panelsUi.lockScreenWidget() && layout.notes().canSubmit }
        }
        // (Compose focuses the field once it has drawn.)
        Thread.sleep(300)
    }

    @Test
    fun `the lock screen shows the layout and Caps Lock, the layout keys work on it, PAM's words show, nothing typed is lost`() =
        withNewm({ keyboardLayouts = "us,ru"; keyboardOptions = "grp:win_space_toggle" }) { caulis, layout, _ ->
            val checked = java.util.concurrent.LinkedBlockingQueue<String>()
            val answer = java.util.concurrent.CountDownLatch(1)
            val before = passwordCheck
            passwordCheck = { _, _, password ->
                checked += password
                answer.await(10, TimeUnit.SECONDS)
                AuthOutcome(false, newm.bar.verbatim("pam_unix: authentication failure"))
            }
            Config.loadConfig(mapOf("language" to "en", "panels" to mapOf("lock" to mapOf("faillock" to false))))
            try {
                lockAndWait(caulis, layout)
                assertEquals("EN", onLoop(caulis) { layout.notes().layout })
                // Super+Space (grp:win_space_toggle) switches the layout on the lock screen too.
                press(caulis, KEY_LEFTMETA, KEY_SPACE)
                waitUntil("RU") { onLoop(caulis) { layout.notes().layout } == "RU" }
                // The key of "a" types "ф" now; Enter checks it.
                press(caulis, KEY_A)
                press(caulis, KEY_ENTER)
                assertEquals("ф", checked.poll(10, TimeUnit.SECONDS))
                onLoop(caulis) { assertFalse(layout.notes().canSubmit, "one check at a time") }
                // (A frame for the lock screen to show the field emptied, as between a person's keys.)
                Thread.sleep(200)
                // Typed while it is checked, and Enter: kept, not checked.
                press(caulis, KEY_B)
                press(caulis, KEY_ENTER)
                Thread.sleep(300)
                assertTrue(checked.isEmpty(), "nothing checked meanwhile")
                answer.countDown()
                waitUntil("asked again") { onLoop(caulis) { layout.notes().canSubmit } }
                val notes = onLoop(caulis) { layout.notes() }
                assertTrue("pam_unix: authentication failure" in notes.message, notes.message)
                assertTrue("The keyboard layout is RU." in notes.message, notes.message)
                assertTrue(notes.failures.startsWith("Wrong passwords since locking: 1"), notes.failures)
                // What was typed meanwhile is still there: Enter checks it now.
                press(caulis, KEY_ENTER)
                assertEquals("и", checked.poll(10, TimeUnit.SECONDS))
                // Caps Lock is told.
                assertFalse(onLoop(caulis) { layout.notes().capsLock })
                press(caulis, KEY_CAPSLOCK)
                waitUntil("Caps Lock") { onLoop(caulis) { layout.notes().capsLock } }
            } finally {
                passwordCheck = before
                Config.loadConfig(emptyMap())
                onLoop(caulis) { layout.trustedUnlock() }
            }
        }

    @Test
    fun `an account pam_faillock locked counts down and takes no password`() = withNewm { caulis, layout, _ ->
        val dir = Files.createTempDirectory("newm-faillock")
        val user = Users.current(Users.currentUid())!!
        val now = System.currentTimeMillis() / 1000
        val tally = java.nio.ByteBuffer.allocate(64 * 3).order(java.nio.ByteOrder.nativeOrder())
        for (n in 0 until 3) {
            tally.putShort(n * 64 + 54, 1)
            tally.putLong(n * 64 + 56, now - 10)
        }
        Files.write(dir.resolve(user), tally.array())
        val conf = dir.resolve("faillock.conf")
        Files.writeString(conf, "dir = $dir\ndeny = 3\nunlock_time = 120\n")
        val checked = java.util.concurrent.LinkedBlockingQueue<String>()
        val before = passwordCheck
        passwordCheck = { _, _, password -> checked += password; AuthOutcome(false) }
        Config.loadConfig(mapOf("language" to "en", "panels" to mapOf("lock" to mapOf("faillock_conf" to conf.toString()))))
        try {
            onLoop(caulis) { layout.ensureLocked(anim = false) }
            waitUntil("the lock screen") { onLoop(caulis) { caulis.desktop.keyboardWidget === layout.panelsUi.lockScreenWidget() } }
            val notes = onLoop(caulis) { layout.notes() }
            assertTrue(notes.lockout.startsWith("Too many wrong passwords: try again in 1:"), notes.lockout)
            assertFalse(notes.canSubmit)
            Thread.sleep(300)
            press(caulis, KEY_A)
            press(caulis, KEY_ENTER)
            Thread.sleep(300)
            assertTrue(checked.isEmpty(), "no password checked while locked")
            // Unlocked by pam_faillock (its records reset): passwords are taken again.
            Files.write(dir.resolve(user), ByteArray(0))
            waitUntil("taken again") { onLoop(caulis) { layout.notes().canSubmit } }
        } finally {
            passwordCheck = before
            Config.loadConfig(emptyMap())
            onLoop(caulis) { layout.trustedUnlock() }
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `newm cmd restart is refused while the screen is locked`() = withNewm { caulis, layout, _ ->
        onLoop(caulis) { layout.ensureLocked(anim = false) }
        try {
            // (The start script would start newm again, unlocked: any program could unlock so.)
            val answer = onLoop(caulis) { layout.command("restart", "keep") }
            assertTrue(answer?.contains("locked") == true, "refused with a reason: $answer")
            assertFalse(restartRequested, "newm is not ending")
            onLoop(caulis) { assertTrue(caulis.desktop.locked, "still locked") }
        } finally {
            restartRequested = false
            onLoop(caulis) { layout.trustedUnlock() }
        }
    }

    @Test
    fun `the launcher and the bar's popups do not open over the lock screen`() = withNewm { caulis, layout, _ ->
        onLoop(caulis) { layout.ensureLocked(anim = false) }
        try {
            // (Any program of the session can send these over D-Bus.)
            assertEquals("The screen is locked", onLoop(caulis) { layout.command("open-launcher") })
            assertEquals("The screen is locked", onLoop(caulis) { layout.command("bar", "power") })
            onLoop(caulis) {
                assertNull(layout.overlay, "no launcher")
                assertNull(layout.barModel.popup, "no popup")
            }
        } finally {
            onLoop(caulis) { layout.trustedUnlock() }
        }
    }

    @Test
    fun `programs' panels are hidden behind newm's lock screen unless lock showPanels`() = withNewm { caulis, layout, _ ->
        for (show in listOf(false, true)) {
            Config.loadConfig(if (show) mapOf("panels" to mapOf("lock" to mapOf("show_panels" to true))) else emptyMap())
            try {
                onLoop(caulis) { layout.ensureLocked(anim = false) }
                onLoop(caulis) { assertEquals(show, caulis.desktop.panelsShowWhileLocked, "lock { showPanels = $show }") }
            } finally {
                Config.loadConfig(emptyMap())
                onLoop(caulis) { layout.trustedUnlock() }
            }
        }
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

    private fun withNewm(configure: Caulis.() -> Unit = {}, test: (Caulis, Layout, Client) -> Unit) {
        assumeTrue(Files.isDirectory(Path.of("/dev/dri")), "no GPU")
        val dir = Files.createTempDirectory("newm-lock-test")
        val caulis = Caulis().apply {
            useConfigFile = false
            backend = Caulis.Backend.HEADLESS
            socketDirectory = dir
            exitAfterSeconds = 60 // (never a hanging test run)
            keyMode(LOCKED_MODE) {}
            configure()
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
        const val KEY_B = 48
        const val KEY_ENTER = 28
        const val KEY_SPACE = 57
        const val KEY_CAPSLOCK = 58
        const val KEY_LEFTMETA = 125
    }
}
