package newm

import dev.caulis.log.Logger

/**
 * newm's config.py: settings by dotted path ("view.corner_radius"), each read through a
 * [configuredValue] that falls back to its default and follows a reload. Where the user's
 * configuration comes from (config.kts) is [loadConfig]'s caller's business: a nested map of
 * the same names as newm's config.py.
 */
object Config {
    private val log = Logger("config")

    // What the configuration says (nested maps by path part).
    private var provider: Map<String, Any?> = emptyMap()

    // Every value asked for, by path: reloads update them.
    private val consumers = LinkedHashMap<String, ConfiguredValue<*>>()

    /** Takes [settings] (nested maps, newm's names) as the configuration; every value follows. */
    fun loadConfig(settings: Map<String, Any?>) {
        provider = settings
        for ((path, value) in consumers) value.update(lookup(path))
        log.info { "Config loaded (${consumers.size} values in use)" }
    }

    /** Every value in use, its setting and default (newm's print_config). */
    fun printConfig(): String = consumers.values.joinToString("\n") { it.toString() }

    internal fun lookup(path: String): Any? {
        var at: Any? = provider
        for (part in path.split('.')) {
            at = (at as? Map<*, *>)?.get(part) ?: return null
        }
        return at
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> value(path: String, default: T): () -> T {
        consumers[path]?.let { return it as () -> T }
        return ConfiguredValue(path, lookup(path), default).also { consumers[path] = it }
    }
}

/** One setting: its value, or its default when not set (or set to null). */
class ConfiguredValue<T>(private val name: String, value: Any?, private val default: T) : () -> T {
    private var current: T = default

    init {
        update(value)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun update(value: Any?) {
        current = if (value == null) default else coerce(value, default) as T
    }

    override fun invoke(): T = current

    override fun toString() = "%-60s %-40s %s".format(name, current, if (current != default) "(default: $default)" else "")

    private companion object {
        // Numbers as the default has them (Python mixes 1 and 1.0 freely).
        fun coerce(value: Any, default: Any?): Any = when {
            value is Number && default is Double -> value.toDouble()
            value is Number && default is Float -> value.toFloat()
            value is Number && default is Int -> value.toInt()
            value is Number && default is Long -> value.toLong()
            // A list of numbers as the default's numbers are ([1, 5, 15] for [1.0, 5.0, 15.0]).
            value is List<*> && default is List<*> && default.firstOrNull() is Number ->
                value.map { item -> if (item is Number) coerce(item, default.first()) else item }
            else -> value
        }
    }
}

/** newm's configured_value. */
fun <T> configuredValue(path: String, default: T): () -> T = Config.value(path, default)
