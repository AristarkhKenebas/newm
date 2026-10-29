package newm

import dev.caulis.core.dbus.DBusError
import dev.caulis.core.dbus.DBusMessage
import dev.caulis.core.dbus.DBusMethod
import dev.caulis.core.dbus.DBusScope
import dev.caulis.core.dbus.ObjectPath
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.caulis.log.Logger

private val log = Logger("idle")

/** Where what keeps the computer awake may come from (energy { inhibitFrom }). */
internal val IDLE_INHIBIT_SOURCES = listOf("wayland", "dbus", "logind", "mpris")

// energy { inhibitFrom = listOf("wayland", "dbus", "logind", "mpris") }: which of them keep the computer awake.
internal val confInhibitFrom = configuredValue("energy.inhibit_from", IDLE_INHIBIT_SOURCES)

// energy { mprisIgnore }: players whose playing keeps nothing awake (music: nothing to watch).
internal val DEFAULT_MPRIS_IGNORE = listOf("spotify", "rhythmbox", "lollypop", "amberol", "elisa", "strawberry", "audacious", "clementine", "quodlibet", "cmus", "mpd", "ncspot", "deadbeef", "g4music", "tauon")
internal val confMprisIgnore = configuredValue("energy.mpris_ignore", DEFAULT_MPRIS_IGNORE)

// energy { logindCheckSeconds = 10 }: how often logind's idle inhibitors are asked for (it does not tell).
internal val confLogindCheckSeconds = configuredValue("energy.logind_check_seconds", 10)

private const val DBUS = "org.freedesktop.DBus"
private const val PROPERTIES = "org.freedesktop.DBus.Properties"
private const val MPRIS_PREFIX = "org.mpris.MediaPlayer2."
private const val MPRIS_PATH = "/org/mpris/MediaPlayer2"

/**
 * What keeps the computer awake besides Wayland's idle-inhibit (Caulis's own): programs asking
 * over D-Bus — org.freedesktop.ScreenSaver.Inhibit (Firefox, Chromium, Telegram: a video
 * playing) and org.freedesktop.PowerManagement.Inhibit — media players playing (MPRIS) while
 * a window of theirs shows, and logind's idle inhibitors (systemd-inhibit --what=idle). Each
 * is held through [hold] (Caulis's inhibitIdle) for as long as it lasts; a program that leaves
 * the bus lets go of what it held.
 */
class IdleInhibitors(
    /** Holds the computer awake for [reason] (with a pid: while a window of that process shows) until closed. */
    private val hold: (reason: String, pid: Int) -> AutoCloseable,
    /** True while the screen is locked (ScreenSaver.GetActive). */
    private val locked: () -> Boolean = { false },
) {
    private class Cookie(val owner: String, val what: String, val held: AutoCloseable)

    private val cookies = HashMap<Int, Cookie>()
    private var nextCookie = 1
    private val closeables = ArrayList<AutoCloseable>()
    private var session: DBusScope? = null

    /** What is held now (for tests and the log): the reasons, as told to [hold]. */
    val held: List<String> get() = cookies.values.map { it.what } + players.values.mapNotNull { p -> p.held?.let { p.heldAs } } + logindHeld.keys.map { it.second }

    // --- org.freedesktop.ScreenSaver and org.freedesktop.PowerManagement.Inhibit

    /**
     * Offers org.freedesktop.ScreenSaver (at /org/freedesktop/ScreenSaver and /ScreenSaver) and
     * org.freedesktop.PowerManagement.Inhibit on the session [bus]. A name another program has
     * already (a desktop's power manager) stays theirs: said in the log.
     */
    fun startDBus(bus: DBusScope) {
        session = bus
        val screenSaver = mapOf(
            "Inhibit" to DBusMethod("ss", "u") { call -> listOf(inhibit(call, "ScreenSaver")) },
            "UnInhibit" to DBusMethod("u", "") { call -> unInhibit(call); emptyList() },
            "GetActive" to DBusMethod("", "b") { listOf(locked()) },
            "GetActiveTime" to DBusMethod("", "u") { listOf(0) },
            "GetSessionIdleTime" to DBusMethod("", "u") { listOf(0) },
            // (Programs that poke instead of inhibiting, xdg-screensaver's way: nothing to hold.)
            "SimulateUserActivity" to DBusMethod("", "") { emptyList() },
        )
        for (path in listOf("/org/freedesktop/ScreenSaver", "/ScreenSaver")) closeables += bus.export(path, "org.freedesktop.ScreenSaver", screenSaver)
        closeables += bus.export(
            "/org/freedesktop/PowerManagement/Inhibit", "org.freedesktop.PowerManagement.Inhibit",
            mapOf(
                "Inhibit" to DBusMethod("ss", "u") { call -> listOf(inhibit(call, "PowerManagement")) },
                "UnInhibit" to DBusMethod("u", "") { call -> unInhibit(call); emptyList() },
                "HasInhibit" to DBusMethod("", "b") { listOf(cookies.isNotEmpty()) },
            ),
        )
        for (name in listOf("org.freedesktop.ScreenSaver", "org.freedesktop.PowerManagement", "org.freedesktop.PowerManagement.Inhibit")) {
            bus.requestName(name) { ours -> if (!ours) log.warn("$name belongs to another program: programs that keep the screen on through it are not heard by newm") }
        }
        // A program that leaves the bus (closed, crashed) lets go of what it held.
        watchNames(bus)
        log.info { "org.freedesktop.ScreenSaver and PowerManagement.Inhibit offered: programs keep the screen on through them" }
    }

    // Who comes onto and leaves the bus (once for both D-Bus inhibitors and players).
    private var watchingNames = false

    private fun watchNames(bus: DBusScope) {
        if (watchingNames) return
        watchingNames = true
        closeables += bus.onSignal(DBUS, "NameOwnerChanged", "/org/freedesktop/DBus", sender = DBUS) { m -> nameChanged(m) }
    }

    // Inhibit(application, reason): a cookie for the caller, held until UnInhibit or it leaves the bus.
    private fun inhibit(call: DBusMessage, api: String): Int {
        val sender = call.sender ?: throw DBusError("org.freedesktop.DBus.Error.AccessDenied", "who is asking?")
        // (A program asking again and again without letting go is not given more and more.)
        if (cookies.values.count { it.owner == sender } >= MAX_COOKIES_PER_CALLER) {
            throw DBusError("org.freedesktop.DBus.Error.LimitsExceeded", "too many inhibitors")
        }
        val app = (call.body.getOrNull(0) as? String).orEmpty().take(64).ifBlank { sender }
        val why = (call.body.getOrNull(1) as? String).orEmpty().take(120)
        val what = if (why.isBlank()) app else "$app: $why"
        val cookie = nextCookie++
        cookies[cookie] = Cookie(sender, what, hold(what, 0))
        log.info { "$api.Inhibit by $sender ($what): cookie $cookie" }
        return cookie
    }

    private fun unInhibit(call: DBusMessage) {
        val cookie = call.body.getOrNull(0) as? Int ?: return
        val c = cookies[cookie] ?: return log.info { "UnInhibit of cookie $cookie, not held (any more)" }
        // (Only who asked lets go: another program cannot end a browser's inhibitor.)
        if (c.owner != call.sender) return log.warn("${call.sender} tried to let go of ${c.owner}'s inhibitor: ignored")
        cookies.remove(cookie)
        c.held.close()
        log.info { "UnInhibit by ${c.owner}: cookie $cookie (${c.what})" }
    }

    private fun nameChanged(m: DBusMessage) {
        val name = m.body.getOrNull(0) as? String ?: return
        val newOwner = (m.body.getOrNull(2) as? String).orEmpty()
        if (newOwner.isEmpty()) {
            val gone = cookies.filterValues { it.owner == name }
            for ((cookie, c) in gone) {
                cookies.remove(cookie)
                c.held.close()
                log.info { "$name left the bus: its inhibitor (${c.what}) is let go" }
            }
        }
        if (name.startsWith(MPRIS_PREFIX) && session != null && mprisOn) {
            if (newOwner.isEmpty()) dropPlayer(name) else addPlayer(name, newOwner)
        }
    }

    // --- MPRIS: a player playing while a window of its own shows

    private inner class Player(val name: String, var owner: String) {
        var identity = name.removePrefix(MPRIS_PREFIX).substringBefore(".instance")
        var pid = 0
        var playing = false
        var held: AutoCloseable? = null
        var heldAs = ""
        fun reason() = "$identity is playing"
    }

    private val players = HashMap<String, Player>()
    private var mprisOn = false

    /** Follows the media players on the session [bus]: one playing keeps the computer awake while a window of its process shows. */
    fun startMpris(bus: DBusScope) {
        session = bus
        mprisOn = true
        watchNames(bus)
        closeables += bus.onSignal(PROPERTIES, "PropertiesChanged", MPRIS_PATH) { m ->
            val player = players.values.firstOrNull { it.owner == m.sender } ?: return@onSignal
            if (m.body.getOrNull(0) != "org.mpris.MediaPlayer2.Player") return@onSignal
            val status = newm.bar.dictOf(m.body.getOrNull(1))["PlaybackStatus"] as? String ?: return@onSignal
            player.playing = status == "Playing"
            update(player)
        }
        bus.call(DBUS, ObjectPath("/org/freedesktop/DBus"), DBUS, "ListNames") { reply ->
            @Suppress("UNCHECKED_CAST")
            val names = (reply.body.firstOrNull() as? List<String>).orEmpty()
            for (name in names) if (name.startsWith(MPRIS_PREFIX)) {
                bus.call(DBUS, ObjectPath("/org/freedesktop/DBus"), DBUS, "GetNameOwner", "s", listOf(name)) { r ->
                    (r.body.firstOrNull() as? String)?.takeIf { !r.isError }?.let { addPlayer(name, it) }
                }
            }
        }
    }

    private fun addPlayer(name: String, owner: String) {
        val bus = session ?: return
        dropPlayer(name)
        val player = Player(name, owner)
        players[name] = player
        bus.call(DBUS, ObjectPath("/org/freedesktop/DBus"), DBUS, "GetConnectionUnixProcessID", "s", listOf(owner)) { r ->
            player.pid = if (r.isError) 0 else (r.body.firstOrNull() as? Number)?.toInt() ?: 0
            update(player)
        }
        bus.call(owner, ObjectPath(MPRIS_PATH), PROPERTIES, "Get", "ss", listOf("org.mpris.MediaPlayer2", "Identity")) { r ->
            ((r.body.firstOrNull() as? dev.caulis.core.dbus.Variant)?.value as? String)?.takeIf { !r.isError && it.isNotBlank() }?.let {
                player.identity = it.take(64)
                update(player)
            }
        }
        bus.call(owner, ObjectPath(MPRIS_PATH), PROPERTIES, "Get", "ss", listOf("org.mpris.MediaPlayer2.Player", "PlaybackStatus")) { r ->
            player.playing = !r.isError && (r.body.firstOrNull() as? dev.caulis.core.dbus.Variant)?.value == "Playing"
            update(player)
        }
    }

    private fun dropPlayer(name: String) {
        val player = players.remove(name) ?: return
        player.held?.close()
        player.held = null
    }

    // Holds the player while it plays (and is not one to ignore), with its process's window; lets go otherwise.
    private fun update(player: Player) {
        if (players[player.name] !== player) return
        val ignore = confMprisIgnore().map { it.lowercase() }.filter { it.isNotBlank() }
        val ignored = ignore.any { it in player.name.lowercase() || it in player.identity.lowercase() }
        // (The pid must be known: "while its window shows" cannot be told without it.)
        val wanted = player.playing && !ignored && player.pid > 0
        val reason = player.reason()
        // (Its name learned meanwhile: held anew under it.)
        if (wanted && player.held != null && player.heldAs != reason) {
            player.held?.close()
            player.held = null
        }
        if (wanted && player.held == null) {
            player.held = hold(reason, player.pid)
            player.heldAs = reason
            log.info { "${player.name} plays: kept awake while a window of process ${player.pid} shows" }
        } else if (!wanted && player.held != null) {
            player.held?.close()
            player.held = null
            log.info { "${player.name} ${if (ignored) "is ignored (energy { mprisIgnore })" else "stopped playing"}: let go" }
        }
    }

    // --- logind's idle inhibitors (systemd-inhibit --what=idle)

    private val logindHeld = HashMap<Pair<Int, String>, AutoCloseable>()

    /**
     * Asks logind on the system [bus] for its inhibitors every [everySeconds] (it tells no
     * one when they change) — through [every], which runs a task repeatedly and gives what stops it.
     */
    fun startLogind(bus: DBusScope, everySeconds: Int, every: (millis: Long, task: () -> Unit) -> AutoCloseable) {
        val ownPid = ProcessHandle.current().pid().toInt()
        val ask = {
            bus.call("org.freedesktop.login1", ObjectPath("/org/freedesktop/login1"), "org.freedesktop.login1.Manager", "ListInhibitors") { reply ->
                if (reply.isError) {
                    log.trace { "logind's inhibitors cannot be listed: ${reply.errorName}" }
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val list = (reply.body.firstOrNull() as? List<List<Any?>>).orEmpty()
                    logindInhibitors(list.mapNotNull { e ->
                        val what = e.getOrNull(0) as? String ?: return@mapNotNull null
                        LogindInhibitor(what, e.getOrNull(1) as? String ?: "", e.getOrNull(2) as? String ?: "", e.getOrNull(3) as? String ?: "", (e.getOrNull(5) as? Number)?.toInt() ?: 0)
                    }, ownPid)
                }
            }
        }
        ask()
        closeables += every(everySeconds.coerceIn(1, 3600) * 1000L, ask)
    }

    /** One of logind's inhibitors: what it blocks ("idle:sleep"), who and why, its mode ("block", "delay") and process. */
    internal data class LogindInhibitor(val what: String, val who: String, val why: String, val mode: String, val pid: Int)

    /** Holds the "idle" ones that block (not newm's own), lets go of those gone. */
    internal fun logindInhibitors(list: List<LogindInhibitor>, ownPid: Int) {
        val now = list.filter { "idle" in it.what.split(':') && it.mode == "block" && it.pid != ownPid }
            .map { it.pid to (if (it.why.isBlank()) it.who else "${it.who}: ${it.why}").take(160) }.toSet()
        for (key in logindHeld.keys - now) {
            logindHeld.remove(key)?.close()
            log.info { "logind's idle inhibitor ${key.second} ended" }
        }
        for (key in now - logindHeld.keys) {
            logindHeld[key] = hold(key.second, 0)
            log.info { "logind's idle inhibitor ${key.second} (process ${key.first}): kept awake" }
        }
    }

    /** Lets go of everything and stops offering and listening. */
    fun stop() {
        closeables.forEach { runCatching { it.close() } }
        closeables.clear()
        cookies.values.forEach { it.held.close() }
        cookies.clear()
        players.keys.toList().forEach(::dropPlayer)
        logindHeld.values.forEach { it.close() }
        logindHeld.clear()
        mprisOn = false
        watchingNames = false
        session = null
    }

    private companion object {
        const val MAX_COOKIES_PER_CALLER = 32
    }
}

/**
 * newm's side of [IdleInhibitors]: started as energy { inhibitFrom } says ("wayland" is Caulis's
 * idle-inhibit, switched with it), and what keeps the computer awake now in [reasons] for the bar.
 */
class IdleInhibitState(private val wm: Layout) {
    val inhibitors = IdleInhibitors(hold = { reason, pid -> wm.caulis.inhibitIdle(reason, pid) }, locked = { wm.caulis.desktop.locked })

    /** What keeps the computer awake now, in words ("mpv", "Firefox: video-playing"); newm's caffeine is told apart. */
    var reasons by androidx.compose.runtime.mutableStateOf(emptyList<String>())
        private set

    private var started = false

    fun start() {
        if (started) return
        started = true
        val from = confInhibitFrom().map { it.lowercase() }
        val unknown = from - IDLE_INHIBIT_SOURCES.toSet()
        if (unknown.isNotEmpty()) log.warn("energy { inhibitFrom }: ${unknown.joinToString()} unknown (${IDLE_INHIBIT_SOURCES.joinToString()})")
        wm.caulis.idleInhibitors = "wayland" in from
        val session = wm.caulis.sessionBus
        if (session == null && ("dbus" in from || "mpris" in from)) log.warn("No session bus: programs cannot keep the screen on over D-Bus")
        if (session != null && "dbus" in from) inhibitors.startDBus(session)
        if (session != null && "mpris" in from) inhibitors.startMpris(session)
        if ("logind" in from) {
            val system = wm.caulis.systemBus
            if (system == null) log.info { "No system bus: logind's idle inhibitors are not heard" }
            else inhibitors.startLogind(system, confLogindCheckSeconds()) { millis, task -> wm.caulis.every(millis) { task() } }
        }
        log.info { "Kept awake by: ${from.joinToString().ifEmpty { "nothing but newm's own caffeine" }}" }
    }

    fun stop() {
        inhibitors.stop()
        started = false
    }

    /** Each second: "wayland" follows the setting (it may change on a reload), [reasons] what holds now. */
    fun tick() {
        val wayland = "wayland" in confInhibitFrom().map { it.lowercase() }
        if (wm.caulis.idleInhibitors != wayland) wm.caulis.idleInhibitors = wayland
        val now = wm.caulis.idleInhibitedBy
        if (now != reasons) {
            log.info { if (now.isEmpty()) "Nothing keeps the screen on any more" else "The screen is kept on by: ${now.joinToString()}" }
            reasons = now
        }
    }
}

/** What keeps the screen on, in words for the bar: "mpv, Firefox: video-playing" (others' words as they are). */
fun idleReasonsText(reasons: List<String>): String = reasons.joinToString(", ") { reason ->
    when {
        reason == "keep-awake" -> newm.bar.tr("keep-awake is on")
        reason.endsWith(" is playing") -> newm.bar.tr("%s is playing").replace("%s", newm.bar.verbatim(reason.removeSuffix(" is playing")))
        else -> newm.bar.verbatim(reason)
    }
}

/** A line for a hint: "\nThe screen is kept on: mpv" (nothing when nothing keeps it on). */
fun idleReasonsLine(reasons: List<String>): String =
    if (reasons.isEmpty()) "" else "\n" + newm.bar.tr("The screen is kept on: %s").replace("%s", idleReasonsText(reasons))
