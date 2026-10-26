package newm.bar

import dev.caulis.core.dbus.DBusConnection
import dev.caulis.core.dbus.DBusMessage
import dev.caulis.core.dbus.ObjectPath
import dev.caulis.core.dbus.Variant

internal const val PROPERTIES_IFACE = "org.freedesktop.DBus.Properties"

/** All of [iface]'s properties of [path] at [service], unwrapped; [done] gets null on an error. */
internal fun DBusConnection.getAll(service: String, path: String, iface: String, done: (Map<String, Any?>?) -> Unit) {
    call(service, ObjectPath(path), PROPERTIES_IFACE, "GetAll", "s", listOf(iface)) { reply ->
        done(if (reply.isError) null else dictOf(reply.body.firstOrNull()))
    }
}

/** Sets [iface]'s property [name] of [path] at [service]; [done] gets the error's text, or null. */
internal fun DBusConnection.setProperty(service: String, path: String, iface: String, name: String, value: Variant, done: (String?) -> Unit = {}) {
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
