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

    /**
     * True if [reply] answers this request: a password (a text, not too long) for a password
     * request, one of the users offered for a user request. Anything else is not taken.
     */
    fun accepts(reply: Map<String, Any?>): Boolean = when (data["kind"]) {
        "auth_request_cred" -> (reply["cred"] as? String)?.let { it.length <= MAX_CRED_LENGTH } == true
        "auth_request_user" -> reply["user"] is String && (data["users"] as? List<*>)?.contains(reply["user"]) == true
        else -> false
    }

    companion object {
        /** The longest password taken (characters); a reply longer is not one. */
        const val MAX_CRED_LENGTH = 4096
    }
}

/**
 * True if the process [pid] is [root] or was started by it (at any depth: a panel program run
 * through a shell or python); [parentOf] tells a process's parent (null: gone).
 */
internal fun isInProcessTree(pid: Long, root: Long, parentOf: (Long) -> Long?): Boolean =
    isInProcessTree(pid, root, null, parentOf) { null }

/**
 * The same, [root] being also the process started at [rootStart] (/proc's start time, see
 * [processStartTime]; null: not checked): a process that got [root]'s number after it ended
 * is not it. [startOf] tells a process's start time.
 */
internal fun isInProcessTree(pid: Long, root: Long, rootStart: Long?, parentOf: (Long) -> Long?, startOf: (Long) -> Long?): Boolean {
    var now: Long? = pid
    // (Deep enough for any wrapper; a loop in what /proc says ends here too.)
    repeat(64) {
        val p = now ?: return false
        if (p == root) return rootStart == null || startOf(p) == rootStart
        if (p <= 1L) return false
        now = parentOf(p)
    }
    return false
}

/** The parent of process [pid], or null (gone, or not to be seen). */
internal fun parentPid(pid: Long): Long? = ProcessHandle.of(pid).flatMap { it.parent() }.map { it.pid() }.orElse(null)

/**
 * When process [pid] started (field 22 of /proc/<pid>/stat, in clock ticks since boot), or null
 * (gone, or not to be read): with the PID it names one process, a PID used again later is not it.
 */
internal fun processStartTime(pid: Long): Long? = runCatching {
    startTimeOfStat(java.nio.file.Files.readString(java.nio.file.Path.of("/proc/$pid/stat")))
}.getOrNull()

// (The program's name, field 2, is in parentheses and may hold spaces or ")": the fields are counted after the last ")".)
internal fun startTimeOfStat(stat: String): Long? =
    stat.substringAfterLast(')', "").trim().split(' ').getOrNull(22 - 3)?.toLongOrNull()

/**
 * newm's dbus/endpoint.py: newm on the session bus — org.newm.Command (newm-cmd), org.newm.Auth
 * (the lock screen panel asks for the user and password), org.newm.Gestures (gestures from
 * other programs). dasbus on a thread there; Caulis's own D-Bus on the compositor's thread here.
 */
class DBusEndpoint(private val layout: Layout) {
    var gestureProvider: DBusGestureProvider? = null
    private var bus: dev.caulis.core.dbus.DBusScope? = null
    private val exported = ArrayList<AutoCloseable>()
    private var latest = ""
    private var nextRequest = 1
    // The request on offer now: its objects (closed once it is answered, or a newer one replaces it).
    private var request: List<AutoCloseable> = emptyList()
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
        closeRequest()
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
        // (The one before is not answered any more: its objects go.)
        closeRequest()
        val objects = ArrayList<AutoCloseable>()
        objects += bus.export(
            path, "org.newm.Auth.Request",
            mapOf(
                // Only the lock panel program (panels.lock.cmd, or a program it started) may answer,
                // and only with what the request asks: any other program of the session could
                // otherwise answer first — a wrong password each time, or nonsense that jams it.
                "Reply" to DBusMethod("s", "") { call ->
                    val text = call.body[0] as String
                    val data = if (text.length > 2 * AuthRequest.MAX_CRED_LENGTH + 256) null else runCatching { Json.parseObject(text) }.getOrNull()
                    val sender = call.sender
                    when {
                        req.replied -> {}
                        data == null || !req.accepts(data) -> log.warn("A reply to the lock screen that is not one (from $sender): ignored")
                        sender == null -> log.warn("A reply to the lock screen from no one: ignored")
                        else -> fromLockPanel(bus, sender) { ok ->
                            if (!ok) {
                                log.warn("A reply to the lock screen from $sender, not the lock panel program: ignored")
                            } else if (!req.replied && request === objects) {
                                req.replied = true
                                req.callback(data)
                                // Answered: its objects go.
                                closeRequest()
                            }
                        }
                    }
                    emptyList()
                },
            ),
        )
        objects += bus.export(path, PROPERTIES, properties("org.newm.Auth.Request") {
            mapOf("Data" to Variant("s", Json.write(req.data)), "Replied" to Variant("b", req.replied))
        })
        request = objects
        latest = path
        bus.emitSignal("/org/newm/Auth", "org.newm.Auth", "Request", "s", listOf(path))
    }

    // Whether the bus connection [sender] belongs to the lock panel program's process (asked of the bus: its PID).
    private fun fromLockPanel(bus: dev.caulis.core.dbus.DBusScope, sender: String, then: (Boolean) -> Unit) {
        val panel = layout.panelLauncher.pidOf("lock") ?: return then(false)
        val panelStart = layout.panelLauncher.startTimeOf("lock")
        bus.call(
            "org.freedesktop.DBus", ObjectPath("/org/freedesktop/DBus"), "org.freedesktop.DBus", "GetConnectionUnixProcessID", "s", listOf(sender),
        ) { reply ->
            val pid = if (reply.isError) null else (reply.body.firstOrNull() as? Number)?.toLong()?.and(0xFFFF_FFFFL)
            then(pid != null && isInProcessTree(pid, panel, panelStart, ::parentPid, ::processStartTime))
        }
    }

    private fun closeRequest() {
        if (request.isEmpty()) return
        request.forEach { runCatching { it.close() } }
        request = emptyList()
        latest = ""
        log.trace { "The lock screen's request closed" }
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
        "inhibit-idle" -> {
            send(mapOf("cmd" to "inhibit-idle"))
            // Ctrl+C (or kill) ends us without running a finally: a shutdown hook says it is over.
            Runtime.getRuntime().addShutdownHook(Thread({ runCatching { send(mapOf("cmd" to "finish-inhibit-idle")) } }, "newm-cmd-finish"))
            while (true) Thread.sleep(10_000)
        }
        "launcher" -> send(mapOf("cmd" to "launcher", "app" to args.drop(1).joinToString(" ")))
        else -> send(mapOf("cmd" to command, "arg" to args.drop(1).joinToString(" ")))?.get("msg")?.let(::println)
    }
}
