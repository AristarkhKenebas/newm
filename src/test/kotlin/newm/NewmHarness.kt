@file:OptIn(dev.caulis.log.InternalCaulisApi::class)

package newm

import dev.caulis.ffi.SharedMemory
import dev.caulis.protocol.client.Connection
import dev.caulis.protocol.client.wayland.WlCompositor
import dev.caulis.protocol.client.wayland.WlRegistry
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
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The harness of the layout tests: newm on a headless Caulis with real windows (one monitor, or
// as many as the setup says), a client drawing plain pictures, and helpers to wait and look.
internal class T(val caulis: Caulis, val layout: Layout, val client: Client) {
    fun <X> onLoop(action: () -> X): X {
        val result = CompletableFuture<X>()
        caulis.post { runCatching(action).fold(result::complete, result::completeExceptionally) }
        return try {
            result.get(10, TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.ExecutionException) {
            throw e.cause ?: e
        }
    }

    fun window(title: String) = caulis.desktop.windows.first { it.title == title }

    fun open(title: String, appId: String = title, parent: Client.Window? = null): Client.Window {
        val w = client.window(title, appId, parent)
        w.draw()
        eventually { assertTrue(onLoop { caulis.desktop.windows.any { it.title == title } && layout.viewOf(window(title))?.let { layout.state.findViewOrNull(it) } != null }) }
        settle()
        return w
    }

    fun focus(title: String) {
        onLoop { layout.focusView(layout.viewOf(window(title))!!) }
        eventually { assertEquals(title, focused()) }
        settle()
    }

    fun focused(): String? = onLoop { caulis.desktop.focusedWindow?.title }

    /** The monitor's bar zone at the top (the room the bar keeps). */
    fun barZone(): dev.caulis.geometry.Rect = onLoop {
        val ws = layout.workspaces.first()
        dev.caulis.geometry.Rect(ws.posX, ws.posY, ws.width, wsState().topExcluded.toInt().coerceAtLeast(1))
    }

    /** On screens: only the windows of the screen in view show, and only within it (the bars' room free). */
    fun assertCleanView() = eventually {
        onLoop {
            val ws = layout.workspaces.first()
            val s = wsState()
            val usable = dev.caulis.geometry.Rect(
                (ws.posX + s.leftExcluded).toInt(), (ws.posY + s.topExcluded).toInt(), s.usableWidth().toInt(), s.usableHeight().toInt(),
            )
            val page = Screens.pageAt(s.i + .5, s.j + .5)
            for (v in layout.tiles(ws)) {
                val shown = layout.shownArea(v) ?: continue
                val vs = s.viewState(v)
                if (vs.page == page) assertTrue(usable.intersect(shown) == shown, "${v.title} of the screen in view, within it: $shown in $usable")
                else assertEquals(null, shown.intersect(dev.caulis.geometry.Rect(ws.posX, ws.posY, ws.width, ws.height)), "${v.title} of screen ${vs.page + 1} not in view (screen ${page + 1})")
            }
        }
    }

    /** No window shows in the bar's room. */
    fun assertBarFree() = eventually {
        val zone = barZone()
        onLoop { for (v in layout.tiles()) assertEquals(null, layout.shownArea(v)?.intersect(zone), "${v.title} under the bar") }
    }

    fun wsState() = layout.state.workspaceState(layout.workspaces.first())

    /** A tile: i, j, w, h (rounded to a hundredth). */
    fun tile(title: String): List<Double> = onLoop {
        val s = layout.state.viewState(layout.viewOf(window(title))!!)
        listOf(s.i, s.j, s.w, s.h).map { Math.round(it * 100) / 100.0 }
    }

    /** The view: i, j, size. */
    fun view(): List<Double> = onLoop { wsState().let { listOf(it.i, it.j, it.size).map { x -> Math.round(x * 100) / 100.0 } } }

    // Fingers moving to (dx, dy) slowly, held a moment (the low-pass catches up), then lifted.
    fun fingers(kind: String, dx: Double, dy: Double) {
        val g = NewmGesture(kind)
        assertTrue(onLoop { layout.gesture(g) }, "the gesture is taken")
        for (n in 1..30) {
            val f = n / 30.0
            onLoop { g.update(mapOf("delta_x" to dx * f, "delta_y" to dy * f, "delta2_s" to 0.0)) }
            Thread.sleep(16)
        }
        Thread.sleep(600)
        onLoop { g.terminate() }
    }

    // Nothing moves any more (the layout's animations done).
    fun settle() {
        Thread.sleep(150)
        client.roundtrips(3)
    }

    fun eventually(check: () -> Unit) {
        val deadline = System.nanoTime() + 8_000_000_000
        while (true) {
            try {
                check()
                return
            } catch (e: AssertionError) {
                if (System.nanoTime() > deadline) throw e
                client.roundtrips(1)
                Thread.sleep(30)
            }
        }
    }
}

internal fun withNewm(settings: Map<String, Any?>, setup: Caulis.() -> Unit = {}, test: (T) -> Unit) {
    assumeTrue(Files.isDirectory(Path.of("/dev/dri")), "no GPU")
    val dir = Files.createTempDirectory("newm-layout-test")
    // (The tests of newm's own wall without screens; those of screens say so.)
    Config.loadConfig(if ("screens" in settings) settings else settings + ("screens" to mapOf("enabled" to false)))
    val caulis = Caulis().apply {
        useConfigFile = false
        backend = Caulis.Backend.HEADLESS
        socketDirectory = dir
        exitAfterSeconds = 90 // (never a hanging test run)
        keyMode(LOCKED_MODE) {}
        setup()
    }
    val layout = Layout(caulis)
    val started = CompletableFuture<Unit>()
    caulis.onStart { started.complete(Unit) }
    val thread = Thread.ofPlatform().start { caulis.run() }
    try {
        started.get(20, TimeUnit.SECONDS)
        val socket = Files.list(dir).use { files -> files.filter { it.fileName.toString().startsWith("wayland-") && !it.toString().endsWith(".lock") }.findFirst().get() }
        Client(Connection.connect(socket)).use { client -> test(T(caulis, layout, client)) }
    } finally {
        runCatching { caulis.post { quit() } }
        thread.join(10_000)
        dir.toFile().deleteRecursively()
        Config.loadConfig(emptyMap())
    }
}

/** Windows with plain pictures (a title, an app id, a parent). */
internal class Client(val connection: Connection) : AutoCloseable {
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
    private val wmBase = registry.bind(names.getValue("xdg_wm_base"), XdgWmBase, 2).also { b ->
        b.events = object : XdgWmBase.Events {
            override fun ping(proxy: XdgWmBase, serial: Int) = proxy.pong(serial)
        }
    }
    private val memory = SharedMemory.create("pictures", 512L * 512 * 4)

    inner class Window(private val surface: WlSurface, val toplevel: XdgToplevel, private val xdgSurface: XdgSurface) {
        @Volatile var width = 0
        @Volatile var height = 0
        @Volatile var closed = false

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

        fun close() {
            closed = true
            toplevel.destroy()
            xdgSurface.destroy()
            surface.destroy()
            connection.roundtrip()
        }
    }

    fun window(title: String, appId: String, parent: Window?): Window {
        val surface = compositor.createSurface()
        val xdgSurface = wmBase.getXdgSurface(surface)
        val toplevel = xdgSurface.getToplevel()
        toplevel.setTitle(title)
        toplevel.setAppId(appId)
        parent?.let { toplevel.setParent(it.toplevel) }
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
        surface.commit()
        connection.roundtrip()
        return window
    }

    fun roundtrips(n: Int) = repeat(n) {
        connection.roundtrip()
        Thread.sleep(20)
    }

    override fun close() {
        memory.close()
        connection.close()
    }
}
