package newm

import dev.caulis.core.dbus.DBusConnection
import dev.caulis.core.dbus.DBusError
import dev.caulis.core.dbus.DBusMethod
import dev.caulis.core.dbus.ObjectPath
import dev.caulis.core.dbus.Variant
import dev.caulis.event.EventLoop
import dev.caulis.log.Logger

private val log = Logger("dbus")

private const val PROPERTIES = "org.freedesktop.DBus.Properties"

// org.freedesktop.DBus.Properties of an object with read-only [props] on [iface].
private fun properties(iface: String, props: () -> Map<String, Variant>) = mapOf(
    "Get" to DBusMethod("ss", "v") { call ->
        val value = props()[call.body[1] as String]?.takeIf { call.body[0] == iface }
            ?: throw DBusError("org.freedesktop.DBus.Error.UnknownProperty", "no ${call.body[0]}.${call.body[1]}")
        listOf(value)
    },
    "GetAll" to DBusMethod("s", "a{sv}") { call -> listOf(if (call.body[0] == iface) props() else emptyMap()) },
)

/** A request to the lock screen panel (newm's AuthRequest): its data, and the panel's reply. */
class AuthRequest(val data: Map<String, Any?>, val callback: (Map<String, Any?>) -> Unit) {
    var replied = false
}

/**
 * newm's dbus/endpoint.py: newm on the session bus — org.newm.Command (newm-cmd), org.newm.Auth
 * (the lock screen panel asks for the user and password), org.newm.Gestures (gestures from
 * other programs). dasbus on a thread there; Caulis's own D-Bus on the compositor's thread here.
 */
class DBusEndpoint(private val layout: Layout) {
    var gestureProvider: DBusGestureProvider? = null
    private var bus: DBusConnection? = null
    private val exported = ArrayList<AutoCloseable>()
    private var latest = ""
    private var nextRequest = 1
    private var nextGesture = 1

    fun start() {
        val bus = layout.caulis.sessionBus ?: return log.warn("No session bus: newm-cmd and the lock screen panel cannot reach newm")
        this.bus = bus
        exported += bus.export(
            "/org/newm/Command", "org.newm.Command",
            mapOf("Call" to DBusMethod("s", "s") { call -> listOf(command(call.body[0] as String)) }),
        )
        exported += bus.export("/org/newm/Auth", "org.newm.Auth", emptyMap())
        exported += bus.export("/org/newm/Auth", PROPERTIES, properties("org.newm.Auth") { mapOf("Latest" to Variant("s", latest)) })
        exported += bus.export(
            "/org/newm/Gestures", "org.newm.Gestures",
            mapOf("New" to DBusMethod("s", "s") { call -> listOf(newGesture(call.body[0] as String)) }),
        )
        for (name in listOf("org.newm.Command", "org.newm.Auth", "org.newm.Auth.Request", "org.newm.Gestures", "org.newm.Gestures.Gesture")) {
            bus.requestName(name)
        }
    }

    fun stop() {
        exported.forEach(AutoCloseable::close)
        exported.clear()
    }

    // newm's Command.Call: {"cmd": ..., "arg": ...} or {"cmd": "launcher", "app": ...}.
    private fun command(args: String): String {
        val json = Json.parseObject(args)
        return try {
            val result = if (json["cmd"] == "launcher") {
                layout.launchApp(json["app"] as String)
                "OK"
            } else {
                layout.command(json["cmd"] as String, json["arg"] as String?)
            }
            Json.write(mapOf("msg" to result))
        } catch (e: Exception) {
            Json.write(mapOf("exception" to e.toString()))
        }
    }

    /** Offers [req] to the lock screen panel (it answers on the object's Reply). */
    fun publishAuthRequest(req: AuthRequest) {
        val bus = bus ?: return
        val path = "/org/newm/Auth/Request/${nextRequest++}"
        exported += bus.export(
            path, "org.newm.Auth.Request",
            mapOf(
                "Reply" to DBusMethod("s", "") { call ->
                    if (!req.replied) {
                        req.replied = true
                        req.callback(Json.parseObject(call.body[0] as String))
                    }
                    emptyList()
                },
            ),
        )
        exported += bus.export(path, PROPERTIES, properties("org.newm.Auth.Request") {
            mapOf("Data" to Variant("s", Json.write(req.data)), "Replied" to Variant("b", req.replied))
        })
        latest = path
        bus.emitSignal("/org/newm/Auth", "org.newm.Auth", "Request", "s", listOf(path))
    }

    private fun newGesture(kind: String): String {
        val bus = bus ?: return ""
        val provider = gestureProvider ?: return ""
        val gesture = NewmGesture(kind)
        if (!provider.offer(gesture)) return ""
        val path = "/org/newm/Gestures/Gesture/${nextGesture++}"
        var handle: AutoCloseable? = null
        handle = bus.export(
            path, "org.newm.Gestures.Gesture",
            mapOf(
                "Update" to DBusMethod("asad", "") { call ->
                    @Suppress("UNCHECKED_CAST")
                    val keys = call.body[0] as List<String>
                    @Suppress("UNCHECKED_CAST")
                    val values = call.body[1] as List<Double>
                    gesture.update(keys.zip(values).toMap())
                    emptyList()
                },
                "Terminate" to DBusMethod("", "") {
                    gesture.terminate()
                    handle?.close()
                    emptyList()
                },
            ),
        )
        return path
    }
}

/** newm's DBusGestureProvider: gestures other programs start over D-Bus (newm's own gesture tools). */
class DBusGestureProvider(onGesture: (NewmGesture) -> Boolean) : GestureProvider(onGesture) {
    fun offer(gesture: NewmGesture): Boolean = onGesture(gesture)
}

/** newm-cmd: sends a command to the running newm and prints its answer (newm's cmd.py). */
fun newmCmd(args: List<String>) {
    val command = args.firstOrNull() ?: run {
        System.err.println("Usage: newm cmd <command> [arguments]   (lock, config, update-config, debug, inhibit-idle, launcher <app>, clean, unlock, restart)")
        return
    }
    fun send(message: Map<String, Any?>): Map<String, Any?>? {
        EventLoop().use { loop ->
            val bus = DBusConnection.session(loop) ?: error("no session bus")
            bus.use {
                var answer: String? = null
                var failed: String? = null
                bus.call("org.newm.Command", ObjectPath("/org/newm/Command"), "org.newm.Command", "Call", "s", listOf(Json.write(message))) { reply ->
                    if (reply.isError) failed = "${reply.errorName}: ${reply.body.firstOrNull()}" else answer = reply.body.firstOrNull() as String?
                }
                val end = System.nanoTime() + 10_000_000_000
                while (answer == null && failed == null && System.nanoTime() < end) loop.dispatch(50)
                failed?.let { error("newm did not answer: $it") }
                return answer?.let { a -> runCatching { Json.parseObject(a) }.getOrNull() }
            }
        }
    }
    when (command) {
        "inhibit-idle" -> try {
            send(mapOf("cmd" to "inhibit-idle"))
            while (true) Thread.sleep(10_000)
        } finally {
            send(mapOf("cmd" to "finish-inhibit-idle"))
        }
        "launcher" -> send(mapOf("cmd" to "launcher", "app" to args.drop(1).joinToString(" ")))
        else -> send(mapOf("cmd" to command, "arg" to args.drop(1).joinToString(" ")))?.get("msg")?.let(::println)
    }
}
