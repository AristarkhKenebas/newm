package newm.bar

import dev.caulis.core.dbus.DBusScope
import dev.caulis.core.dbus.DBusMessage
import dev.caulis.core.dbus.ObjectPath
import dev.caulis.core.dbus.Variant

internal const val PROPERTIES_IFACE = "org.freedesktop.DBus.Properties"

/** All of [iface]'s properties of [path] at [service], unwrapped; [done] gets null on an error. */
internal fun DBusScope.getAll(service: String, path: String, iface: String, done: (Map<String, Any?>?) -> Unit) {
    call(service, ObjectPath(path), PROPERTIES_IFACE, "GetAll", "s", listOf(iface)) { reply ->
        done(if (reply.isError) null else dictOf(reply.body.firstOrNull()))
    }
}

/** Sets [iface]'s property [name] of [path] at [service]; [done] gets the error's text, or null. */
internal fun DBusScope.setProperty(service: String, path: String, iface: String, name: String, value: Variant, done: (String?) -> Unit = {}) {
    call(service, ObjectPath(path), PROPERTIES_IFACE, "Set", "ssv", listOf(iface, name, value)) { reply -> done(errorOf(reply)) }
}

/** An a{sv} as read (a list of [key, Variant]) as a map of plain values. */
internal fun dictOf(value: Any?): Map<String, Any?> {
    val map = LinkedHashMap<String, Any?>()
    for (entry in value as? List<*> ?: return map) {
        val pair = entry as? List<*> ?: continue
        map[pair[0].toString()] = (pair[1] as? Variant)?.value ?: pair[1]
    }
    return map
}

/** An ay as read (a list of bytes as numbers) as text. */
internal fun textOf(bytes: Any?): String = (bytes as? List<*>)?.let { list -> String(ByteArray(list.size) { (list[it] as Number).toByte() }, Charsets.UTF_8) } ?: ""

/** A reply's error in words (its name's last part and its message), or null. */
internal fun errorOf(reply: DBusMessage): String? =
    if (!reply.isError) null else listOfNotNull(reply.errorName?.substringAfterLast('.'), reply.body.firstOrNull() as? String).joinToString(": ")

/**
 * Follows who has the bus name [name] (a service: NetworkManager, BlueZ): [owner] is its unique
 * name now (null: not running), so signals can be taken from it alone; [changed] hears the
 * first answer and then each start, stop or restart (it may come later than newm).
 */
internal class NameWatch(bus: DBusScope, val name: String, private val changed: (String?) -> Unit) {
    var owner: String? = null
        private set
    private var answered = false

    init {
        bus.onSignal("org.freedesktop.DBus", "NameOwnerChanged", "/org/freedesktop/DBus", sender = "org.freedesktop.DBus") { m ->
            if (m.body.getOrNull(0) == name) set((m.body.getOrNull(2) as? String)?.ifEmpty { null })
        }
        bus.call("org.freedesktop.DBus", ObjectPath("/org/freedesktop/DBus"), "org.freedesktop.DBus", "GetNameOwner", "s", listOf(name)) { reply ->
            // (A start told by the signal meanwhile is newer than this answer.)
            if (!answered) set(if (reply.isError) null else reply.body.firstOrNull() as? String)
        }
    }

    /** [message] comes from the service (not from another program sending the same signal). */
    fun sent(message: DBusMessage): Boolean = owner != null && message.sender == owner

    private fun set(now: String?) {
        if (answered && now == owner) return
        answered = true
        owner = now
        changed(now)
    }
}
