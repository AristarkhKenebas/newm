package newm

import dev.caulis.render.Color
import dev.caulis.wm.Autostart
import dev.caulis.wm.Caulis
import dev.caulis.wm.Restart
import dev.caulis.wm.SoundMoment

/*
 * The start of a session in newm: autostart { } (Caulis's own, see dev.caulis.wm.Autostart),
 * reveal { } (dark until newm is warm, then it fades in) and startupSound { } — on in newm,
 * off in Caulis. Written in config.kts, or as data (config.json, config.lua):
 *
 *   "autostart": { "stagger_ms": 400, "entries": [ { "app": ["waybar"] }, { "shell": "sleep 1; notify-send hi" } ] },
 *   "reveal": { "enabled": true, "fade_ms": 600 },
 *   "startup_sound": { "preset": "airy", "volume": 0.8 }
 */

private val startLog = dev.caulis.log.Logger("config")

/** newm's own defaults for the start (on top of Caulis's, which has them off). */
internal fun Caulis.startupDefaults() {
    reveal.enabled = true
    startupSound.enabled = true
    startupSound.preset = "airy"
}

/**
 * The start's settings of [settings] (newm's own names) onto [caulis]: what is not said goes
 * back to newm's default. Autostart's entries are replaced (a reload starts only the new ones).
 * [greeter]: newm is greetd's login screen (no startup sound unless asked for).
 */
@Suppress("UNCHECKED_CAST")
internal fun applyStartupSettings(caulis: Caulis, settings: Map<String, Any?>, greeter: Boolean = false) {
    val reveal = settings["reveal"] as? Map<String, Any?> ?: emptyMap()
    caulis.reveal.apply {
        enabled = reveal["enabled"] as? Boolean ?: true
        fadeMillis = (reveal["fade_ms"] as? Number)?.toLong()?.coerceIn(0, 60_000) ?: 600
        maxWaitMillis = (reveal["max_wait_ms"] as? Number)?.toLong()?.coerceIn(0, 120_000) ?: 4000
        color = (reveal["color"] as? String)?.let(::colorOf) ?: Color(0f, 0f, 0f)
    }
    val sound = settings["startup_sound"] as? Map<String, Any?> ?: emptyMap()
    caulis.startupSound.apply {
        // (Off by default on the login screen: it would sound twice, there and once logged in.)
        enabled = sound["enabled"] as? Boolean ?: !greeter
        preset = (sound["preset"] as? String)?.lowercase()?.takeIf { it in dev.caulis.wm.StartupChimes.PRESETS }
            ?: "airy".also { p -> sound["preset"]?.let { startLog.warn("startup_sound.preset: there is no \"$it\" (there are ${dev.caulis.wm.StartupChimes.PRESETS.joinToString()}); $p instead") } }
        file = sound["file"] as? String
        volume = (sound["volume"] as? Number)?.toDouble()?.coerceIn(0.0, 1.0) ?: 0.8
        delayMillis = (sound["delay_ms"] as? Number)?.toLong()?.coerceIn(0, 60_000) ?: 0
        at = if ((sound["at"] as? String).equals("start", ignoreCase = true)) SoundMoment.START else SoundMoment.REVEAL
        player = (sound["player"] as? List<*>)?.map { it.toString() }?.takeIf { it.isNotEmpty() }
    }
    val wanted = settings["autostart"] as? Autostart ?: Autostart()
    caulis.autostart.apply {
        clear()
        staggerMillis = wanted.staggerMillis
        afterReveal = wanted.afterReveal
        desktopEntries = wanted.desktopEntries
        desktopEntryFolders = wanted.desktopEntryFolders
        afterTimeoutMillis = wanted.afterTimeoutMillis
        wanted.entries.forEach(::add)
    }
}

// "#rrggbb" or "#rrggbbaa", else null (and a warning).
private fun colorOf(text: String): Color? = runCatching { Color.hex(text) }.getOrElse {
    startLog.warn("reveal.color: \"$text\" is not a colour (#rrggbb)")
    null
}

/**
 * autostart as data (config.json, config.lua): `{ "stagger_ms", "after_reveal",
 * "desktop_entries", "after_timeout_ms", "entries": [ { "app": [...] | "shell": "..." |
 * "script": "...", "arguments", "name", "delay_ms", "if_not_running", "restart", "after",
 * "env", "dir" } ] }`; what is wrong goes to [problems] (the rest still counts).
 */
internal fun autostartOf(value: Any?, name: String, problems: MutableList<String>): Autostart {
    val a = Autostart()
    val map = value as? Map<*, *> ?: return a.also { problems += "$name: autostart is a table" }
    fun number(key: String, apply: (Long) -> Unit) = map[key]?.let { v ->
        val n = (v as? Number)?.toLong() ?: return@let problems.add("$name: autostart.$key is a number").let { }
        runCatching { apply(n) }.onFailure { problems += "$name: autostart.$key: ${it.message}" }
    }
    number("stagger_ms") { a.staggerMillis = it }
    number("after_timeout_ms") { a.afterTimeoutMillis = it }
    (map["after_reveal"] as? Boolean)?.let { a.afterReveal = it }
    (map["desktop_entries"] as? Boolean)?.let { a.desktopEntries = it }
    val known = setOf("stagger_ms", "after_timeout_ms", "after_reveal", "desktop_entries", "entries")
    map.keys.map { it.toString() }.filter { it !in known }.forEach { problems += "$name: autostart.$it: there is no such setting (there are: ${known.joinToString()})" }
    val entries = when (val e = map["entries"]) {
        null -> emptyList()
        is List<*> -> e
        // (Lua: a table numbered from 1.)
        is Map<*, *> -> e.entries.sortedBy { it.key.toString().toIntOrNull() ?: Int.MAX_VALUE }.map { it.value }
        else -> emptyList<Any?>().also { problems += "$name: autostart.entries is a list" }
    }
    for ((i, item) in entries.withIndex()) {
        val where = "$name: autostart.entries[${i + 1}]"
        val e = item as? Map<*, *> ?: run { problems += "$where is a table"; continue }
        try {
            entryOf(a, e)
        } catch (ex: IllegalArgumentException) {
            problems += "$where: ${ex.message}"
        }
    }
    return a
}

private val ENTRY_KEYS = setOf("app", "shell", "script", "arguments", "name", "delay_ms", "if_not_running", "restart", "after", "env", "dir")

private fun words(value: Any?): List<String>? = when (value) {
    null -> null
    is String -> value.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    is List<*> -> value.map { it.toString() }
    is Map<*, *> -> value.entries.sortedBy { it.key.toString().toIntOrNull() ?: Int.MAX_VALUE }.map { it.value.toString() }
    else -> null
}

private fun entryOf(a: Autostart, e: Map<*, *>) {
    val unknown = e.keys.map { it.toString() }.filter { it !in ENTRY_KEYS }
    require(unknown.isEmpty()) { "there is no ${unknown.joinToString()} (there are: ${ENTRY_KEYS.joinToString()})" }
    val app = words(e["app"])
    val kinds = listOfNotNull(app, e["script"], e["shell"])
    require(kinds.size == 1) { "one of app, script or shell" }
    val entry = when {
        app != null -> a.app(app)
        e["script"] != null -> a.script(e["script"].toString(), *words(e["arguments"]).orEmpty().toTypedArray())
        else -> a.shell(e["shell"].toString())
    }
    (e["name"] as? String)?.let { entry.name = it }
    (e["delay_ms"] as? Number)?.let { entry.delayMillis = it.toLong() }
    (e["if_not_running"] as? Boolean)?.let { entry.ifNotRunning = it }
    e["restart"]?.let { entry.restart = restartOf(it) }
    words(e["after"])?.forEach { entry.after(it) }
    (e["env"] as? Map<*, *>)?.forEach { (k, v) -> entry.env(k.toString(), v.toString()) }
    (e["dir"] as? String)?.let { entry.dir = it }
}

// "on_crash" (or "on-crash"), "always", "never", or { when, max_times, within_ms }.
private fun restartOf(value: Any): Restart {
    val r = value as? Map<*, *>
    val kind = (if (r != null) r["when"]?.toString() ?: "on_crash" else value.toString()).lowercase().replace('-', '_')
    val times = (r?.get("max_times") as? Number)?.toInt() ?: 3
    val within = (r?.get("within_ms") as? Number)?.toLong() ?: 60_000L
    return when (kind) {
        "never" -> Restart.NEVER
        "always" -> Restart.always(times, within)
        "on_crash" -> Restart.onCrash(times, within)
        else -> throw IllegalArgumentException("restart is never, on_crash, always or { when, max_times, within_ms }, not \"$value\"")
    }
}
