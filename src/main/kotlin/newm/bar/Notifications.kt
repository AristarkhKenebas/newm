package newm.bar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import dev.caulis.core.dbus.DBusScope
import dev.caulis.core.dbus.DBusMessage
import dev.caulis.core.dbus.DBusMethod
import dev.caulis.core.dbus.ObjectPath
import dev.caulis.log.Logger
import newm.Json
import newm.configuredValue
import newm.lockShown
import newm.privateFolder
import newm.writePrivately
import java.nio.file.Files
import java.nio.file.Path

private val log = Logger("notifications")

// notifications { enabled = true }: newm takes org.freedesktop.Notifications (false: it leaves it to another daemon).
private val confEnabled = configuredValue("notifications.enabled", true)

// notifications { takeOver }: another daemon holding the name — "stale" (the default: one left
// from another session, started before newm or showing on another display, is closed),
// "always" (any is closed), "never" (left alone; newm's own stay off until it quits).
// Only on the screen: nested or headless newm never closes another daemon (see takeOverDecision).
private val confTakeOver = configuredValue("notifications.take_over", "stale")

/**
 * Whether another notification daemon is closed for newm's own (notifications.takeOver [mode]):
 * only when newm is the session's compositor (on the screen — never nested or headless, nor
 * before it knows), then any with "always", with "stale" one of the same user started before
 * newm or showing on another Wayland display; "never" (or anything else) leaves it alone.
 */
internal fun takeOverDecision(
    mode: String,
    backend: dev.caulis.wm.Caulis.Backend?,
    alive: Boolean,
    sameUser: Boolean,
    startedBefore: Boolean,
    otherDisplay: String?,
    ourDisplay: String?,
): Boolean {
    if (backend != dev.caulis.wm.Caulis.Backend.SCREEN || !alive) return false
    return when (mode.trim().lowercase()) {
        "always" -> true
        "stale" -> sameUser && (startedBefore || (otherDisplay != null && ourDisplay != null && otherDisplay != ourDisplay))
        else -> false
    }
}

// notifications { seconds = 6 }: how long a card stays when the program does not say.
private val confSeconds = configuredValue("notifications.seconds", 6)

// notifications { doNotDisturb = false }: no cards at all, the history only (the bell's switch changes it while running).
private val confDoNotDisturb = configuredValue("notifications.do_not_disturb", false)

// notifications { readAfterFocusSeconds = 3 }: a program's window focused this long, its notifications are read.
private val confReadAfterFocus = configuredValue("notifications.read_after_focus_seconds", 3)

// notifications { keepHours = 24, keep = 100 }: older or more ones go.
private val confKeepHours = configuredValue("notifications.keep_hours", 24)
private val confKeep = configuredValue("notifications.keep", 100)

// notifications { remember = true }: the history kept across restarts (~/.local/state/newm/notifications.json).
private val confRemember = configuredValue("notifications.remember", true)

// notifications { maxCards = 3 }: the most cards on screen at once (the oldest one goes to the history when one more comes).
private val confMaxCards = configuredValue("notifications.max_cards", 3)

/** The most cards on screen at once (1..10). */
internal fun mostCards(): Int = confMaxCards().coerceIn(1, 10)

// notifications { criticalSeconds = 0 }: how long a critical one's card stays (0: until closed).
private val confCriticalSeconds = configuredValue("notifications.critical_seconds", 0)

// notifications { showInFullscreen = false }: cards over a fullscreen window too.
internal val confShowInFullscreen = configuredValue("notifications.show_in_fullscreen", false)

// notifications { rule { n -> ... } }: rules for some notifications (Kotlin, a function of config.lua, or a table: see NotificationRule).
private val confRules = configuredValue<List<Any?>>("notifications.rules", emptyList())

// notifications { onNotification { n -> } }: told of each notification that comes (after the rules).
private val confOnNotification = configuredValue<Any?>("notifications.on_notification", null)

/**
 * What the rules say of a notification as it comes (`notifications { rule { n -> if (n.app == "Spotify") mute() } }`):
 * [mute] — no card, the history only; [hide] — dropped, not kept at all; [urgency] (0 low,
 * 1 normal, 2 critical) and [seconds] (how long its card stays, 0: until closed) instead of the program's.
 */
class NotificationRule internal constructor() {
    internal var muted = false
    internal var hidden = false
    internal var urgencyNow: Int? = null
    internal var secondsNow: Int? = null

    /** No card: the history only. */
    fun mute() { muted = true }

    /** Not shown, not kept. */
    fun hide() { hidden = true }

    /** Its urgency: 0 low, 1 normal, 2 critical. */
    fun urgency(level: Int) { urgencyNow = level.coerceIn(0, 2) }

    /** How long its card stays, in seconds (0: until closed). */
    fun seconds(seconds: Int) { secondsNow = seconds.coerceAtLeast(0) }

    // A rule as a table (config.json, config.lua): what it matches (app, app_matches, entry, summary,
    // summary_matches, body_matches) and what it says (mute, hide, urgency, seconds).
    internal fun apply(table: Map<*, *>, n: Notification) {
        fun text(key: String) = table[key]?.toString()
        fun matches(key: String, value: String) = text(key)?.let { runCatching { Regex(it).containsMatchIn(value) }.getOrDefault(false) } ?: true
        if (text("app")?.let { !it.equals(n.app, ignoreCase = true) } == true) return
        if (text("entry")?.let { !it.equals(n.entry, ignoreCase = true) } == true) return
        if (text("summary")?.let { it != n.summary } == true) return
        if (!matches("app_matches", n.app) || !matches("summary_matches", n.summary) || !matches("body_matches", n.body)) return
        if (table["mute"] == true) mute()
        if (table["hide"] == true) hide()
        when (val u = table["urgency"]) {
            is Number -> urgency(u.toInt())
            "low" -> urgency(0)
            "normal" -> urgency(1)
            "critical" -> urgency(2)
        }
        (table["seconds"] as? Number)?.let { seconds(it.toInt()) }
    }
}

/** [n] as config.lua's functions see it: a table. */
internal fun Notification.asTable(): Map<String, Any?> = mapOf(
    "id" to id, "app" to app, "entry" to entry, "summary" to summary, "body" to body, "urgency" to urgency, "icon" to icon,
)

// The longest texts kept: a program may send a book.
private const val LONGEST_APP = 64
private const val LONGEST_SUMMARY = 200
private const val LONGEST_BODY = 2000
private const val LONGEST_LABEL = 40
private const val MOST_ACTIONS = 6
private const val LONGEST_KEY = 64
private const val LONGEST_PATH = 1024

/**
 * A notification as newm keeps it: what the program sent (its words without markup), when,
 * and whether the program still knows it ([live]: not closed yet, not from an earlier run).
 */
data class Notification(
    val id: Int,
    /** The program's name, as it says it ("Telegram Desktop"). */
    val app: String,
    /** A picture: an icon's name or a file (app_icon, or the image-path hint). */
    val icon: String = "",
    /** The program's .desktop file's name (the desktop-entry hint: "org.telegram.desktop"). */
    val entry: String = "",
    val summary: String = "",
    val body: String = "",
    /** The buttons, key to label ("default" is the click on the card itself). */
    val actions: List<Pair<String, String>> = emptyList(),
    /** 0 low, 1 normal, 2 critical (stays until closed). */
    val urgency: Int = 1,
    /** How long its card stays, as the program asked (milliseconds; -1: newm's choice, 0: until closed). */
    val timeout: Int = -1,
    /** When it came (milliseconds since 1970). */
    val at: Long = 0,
    /** The process that sent it (0: not known yet). */
    val pid: Int = 0,
    /** Kept after an action is invoked (the resident hint). */
    val resident: Boolean = false,
    /** Never kept in the history (the transient hint): gone with its card. */
    val transient: Boolean = false,
    val live: Boolean = true,
    /** Seen in the bell's list (the badge counts the others). */
    val read: Boolean = false,
) {
    /** The "default" action is offered (a click on it means it). */
    val hasDefault: Boolean get() = actions.any { it.first == "default" }

    /** The buttons to show: the actions but "default". */
    val buttons: List<Pair<String, String>> get() = actions.filter { it.first != "default" }
}

/**
 * [this] belongs to the window of process [pid] and app id [appId]: the same process sent it,
 * or its .desktop name or its program's name is the window's app id (case aside), or its
 * name's first word is a part of it ("Telegram Desktop" and "org.telegram.desktop").
 */
internal fun Notification.belongsTo(pid: Int, appId: String): Boolean {
    if (this.pid > 0 && this.pid == pid) return true
    val id = appId.lowercase().trim()
    if (id.isEmpty()) return false
    val entry = entry.lowercase().removeSuffix(".desktop")
    val name = app.lowercase().trim()
    if (entry.isNotEmpty() && (entry == id || entry.substringAfterLast('.') == id)) return true
    if (name.isEmpty()) return false
    if (name == id || name.replace(" ", "") == id || name.replace(' ', '-') == id) return true
    val first = name.split(' ', '-', '_').firstOrNull().orEmpty()
    return first.length >= 3 && first in id.split('.', '-', '_')
}

/**
 * The markup the specification allows (<b>, <i>, <u>, <a href>, <img>) taken out: the words alone, entities read.
 *
 * One pass over the characters, no regular expressions: a notification's body comes from any
 * program, and a pattern that backtracks let a crafted body freeze the desktop for seconds.
 * A tag runs from "<" to the first ">" ("<br>" is a new line, "<img … alt="…">" says its
 * alt text, every other tag goes); a "<" with no ">" after it stays as written.
 */
internal fun plainText(markup: String): String {
    val text = StringBuilder(markup.length)
    var i = 0
    while (i < markup.length) {
        val c = markup[i]
        if (c != '<') { text.append(c); i++; continue }
        val end = markup.indexOf('>', i + 1)
        // (No ">" after this one means none after any later "<" either: the rest is words.)
        if (end < 0) { text.append(markup, i, markup.length); break }
        tagText(markup, i + 1, end)?.let(text::append)
        i = end + 1
    }
    return readEntities(text).trim()
}

// What the tag between [from] and [to] (without its "<" and ">") stands for: a new line for <br>,
// the alt text for <img>, else nothing.
private fun tagText(markup: String, from: Int, to: Int): String? {
    val tag = markup.substring(from, to)
    val name = tag.trimEnd().removeSuffix("/").trimEnd()
    if (name.equals("br", ignoreCase = true)) return "\n"
    if (!tag.startsWith("img", ignoreCase = true)) return null
    val alt = tag.indexOf("alt=\"", ignoreCase = true).takeIf { it >= 0 } ?: return null
    val close = tag.indexOf('"', alt + 5).takeIf { it >= 0 } ?: return null
    return tag.substring(alt + 5, close)
}

// [text] with the entities newm knows (&amp; &lt; &gt; &quot; &apos; &nbsp; &#N; &#xN;) read; any other "&" stays.
private fun readEntities(text: CharSequence): String {
    if ('&' !in text) return text.toString()
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        // (The longest known entity, "&#x10FFFF;", is ten long: the ";" is looked for no further.)
        val semi = if (c == '&') (i + 2..minOf(i + 10, text.length - 1)).firstOrNull { text[it] == ';' } else null
        val read = semi?.let { entity(text.substring(i + 1, it)) }
        if (read == null) { out.append(c); i++; continue }
        out.append(read)
        i = semi + 1
    }
    return out.toString()
}

private fun entity(name: String): String? = when (name) {
    "amp" -> "&"
    "lt" -> "<"
    "gt" -> ">"
    "quot" -> "\""
    "apos" -> "'"
    "nbsp" -> " "
    else -> {
        val code = when {
            name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).takeIf { it.isNotEmpty() && it.all { d -> d.isDigit() || d.lowercaseChar() in 'a'..'f' } }?.toIntOrNull(16)
            name.startsWith("#") -> name.drop(1).takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()
            else -> null
        }
        code?.let { runCatching { String(Character.toChars(it)) }.getOrNull() }
    }
}

/**
 * The history and its rules, without the compositor (so the rules can be tested): newest
 * first; a notification replacing another keeps its number; the read ones (their program's
 * window looked at long enough), the old ones and those over the most kept go.
 */
class NotificationHistory {
    /** Every notification kept, newest first. */
    var items by mutableStateOf(listOf<Notification>())
        private set

    private var nextId = 1

    /** How many are not read yet (the bell's badge). */
    val unread: Int get() = items.count { !it.read }

    fun find(id: Int): Notification? = items.firstOrNull { it.id == id }

    /**
     * Takes [n] (its id is given here): in the place of [replaces] when that one is still
     * here (the same number then), else as a new one on top.
     */
    fun add(n: Notification, replaces: Int = 0): Notification {
        val old = replaces.takeIf { it > 0 }?.let(::find)
        if (old != null) {
            val updated = n.copy(id = old.id, pid = if (n.pid > 0) n.pid else old.pid)
            // (Newest first: the changed one goes on top, as it is new again.)
            items = listOf(updated) + items.filter { it.id != old.id }
            return updated
        }
        val added = n.copy(id = nextId++)
        if (nextId <= 0) nextId = 1 // (never 0: 0 means "none" on the bus)
        items = listOf(added) + items
        return added
    }

    /** Changes [id] by [change] (where it is). */
    fun update(id: Int, change: (Notification) -> Notification) {
        items = items.map { if (it.id == id) change(it) else it }
    }

    fun remove(id: Int): Notification? = find(id)?.also { gone -> items = items.filter { it.id != gone.id } }

    /** Takes out those [which] says; gives them back. */
    fun removeAll(which: (Notification) -> Boolean): List<Notification> {
        val gone = items.filter(which)
        if (gone.isNotEmpty()) items = items.filter { !which(it) }
        return gone
    }

    fun markAllRead() {
        if (items.any { !it.read }) items = items.map { if (it.read) it else it.copy(read = true) }
    }

    /**
     * The old ones out: older than [keepHours] (critical ones too), and past the [keep] newest;
     * gives back those that went.
     */
    fun trim(keepHours: Int, keep: Int, now: Long): List<Notification> {
        val oldest = now - keepHours.coerceAtLeast(0) * 3_600_000L
        val keptCount = keep.coerceAtLeast(0)
        val gone = items.filterIndexed { i, n -> n.at < oldest || i >= keptCount }
        if (gone.isNotEmpty()) items = items - gone.toSet()
        return gone
    }

    // The window focused now (its process and app id) and since when; null: none (or locked).
    private var focusPid = 0
    private var focusApp = ""
    private var focusSince = 0L
    private var focusing = false

    /** The window of [pid] with [appId] has the focus from [now] on (pid 0 and "": none). */
    fun focus(pid: Int, appId: String, now: Long) {
        if (focusing && pid == focusPid && appId == focusApp) return
        focusPid = pid
        focusApp = appId
        focusSince = now
        focusing = pid > 0 || appId.isNotEmpty()
    }

    /**
     * The notifications of the focused window's program it has had for [readAfterMillis]
     * (a glance is not reading; one that came while it was focused, that long after it came):
     * read, out of the history; gives them back.
     */
    fun readByFocus(readAfterMillis: Long, now: Long): List<Notification> {
        if (!focusing) return emptyList()
        return removeAll { n -> n.belongsTo(focusPid, focusApp) && now - maxOf(focusSince, n.at) >= readAfterMillis }
    }

    /** The history as JSON (newest first; [keep] at most, the transient ones never). */
    fun toJson(keep: Int, extra: Map<String, Any?> = emptyMap()): String = Json.write(extra + mapOf(
        "notifications" to items.filter { !it.transient }.take(keep.coerceAtLeast(0)).map { n ->
            mapOf(
                "id" to n.id, "app" to n.app, "icon" to n.icon, "entry" to n.entry, "summary" to n.summary, "body" to n.body,
                "urgency" to n.urgency, "at" to n.at, "read" to n.read,
            )
        },
    ))

    /** Takes back a history written by [toJson] (its programs know none of them any more); gives the rest of what was there. */
    fun fromJson(text: String): Map<String, Any?> {
        val json = Json.parseObject(text)
        val list = (json["notifications"] as? List<*>).orEmpty().mapNotNull { e ->
            val m = e as? Map<*, *> ?: return@mapNotNull null
            Notification(
                id = (m["id"] as? Number)?.toInt() ?: return@mapNotNull null,
                app = m["app"] as? String ?: "",
                icon = m["icon"] as? String ?: "",
                entry = m["entry"] as? String ?: "",
                summary = m["summary"] as? String ?: "",
                body = m["body"] as? String ?: "",
                urgency = (m["urgency"] as? Number)?.toInt() ?: 1,
                at = (m["at"] as? Number)?.toLong() ?: 0L,
                read = m["read"] as? Boolean ?: false,
                live = false,
            )
        }
        items = (list + items.filter { n -> list.none { it.id == n.id } }).sortedByDescending { it.at }
        nextId = maxOf(nextId, (items.maxOfOrNull { it.id } ?: 0) + 1)
        return json - "notifications"
    }
}

/** Why a notification closed, as the specification numbers them (NotificationClosed's reason). */
enum class ClosedBecause(val code: Int) { EXPIRED(1), DISMISSED(2), CLOSED_BY_CALL(3), UNDEFINED(4) }

/** A card on screen: the notification it shows ([id]), how long it has left, the pointer over it. */
class NotificationCard(val id: Int, var left: Long) {
    var hovered by mutableStateOf(false)

    /** Going away (its last frames). */
    var leaving by mutableStateOf(false)

    /** How tall it is (desktop units; 0: not measured yet). */
    var height by mutableStateOf(0)

    /** Changed each time its notification is replaced (its look starts again). */
    var serial by mutableStateOf(0)
}

/**
 * newm's notification server (org.freedesktop.Notifications, the specification's 1.2): cards
 * at the top right below the bar, and a history behind the bar's bell. A program's
 * notifications are read once its window has had the focus a moment; old ones go by
 * themselves. When another daemon (mako, dunst, swaync) has the bus name, newm leaves it to
 * it and says so once; when that one quits, newm takes the name.
 */
class NotificationCenter(private val model: BarModel) {
    private val wm get() = model.wm

    val history = NotificationHistory()

    /** The cards on screen, newest first. */
    var cards by mutableStateOf(listOf<NotificationCard>())
        private set

    /** The cards' coming and going as a whole (done by the compositor). */
    val cardsFade = Fade("notifications", inMillis = 240, outMillis = 220)

    /** Pictures sent with notifications (image-data), by their number. */
    val pictures = mutableStateMapOf<Int, ImageBitmap>()

    // The picture last asked for each notification (by its number): a late one of a replaced notification is not taken.
    private val pictureAsked = HashMap<Int, Any>()

    /** Do not disturb as chosen at the bell (null: the configuration's). */
    private var dndChoice by mutableStateOf<Boolean?>(null)

    /** No cards: the history only. */
    val doNotDisturb: Boolean get() = dndChoice ?: confDoNotDisturb()

    /** The bus name is newm's now: notifications come here. */
    var serving by mutableStateOf(false)
        private set

    /** Another program has the name (its process' name), or null. */
    var otherDaemon by mutableStateOf<String?>(null)
        private set

    private var bus: DBusScope? = null
    private val exported = ArrayList<AutoCloseable>()
    private var watch: NameWatch? = null
    private var toldOther = false
    private var lastTick = System.currentTimeMillis()
    private var dirtySince = 0L

    // The file of the history (~/.local/state/newm/notifications.json).
    private val file: Path by lazy {
        val state = System.getenv("XDG_STATE_HOME")?.takeIf { it.startsWith("/") } ?: "${System.getenv("HOME") ?: System.getProperty("user.home")}/.local/state"
        Path.of(state, "newm", "notifications.json")
    }

    // One thread for writing the file (never the compositor's).
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "newm-notifications").apply { isDaemon = true } }

    /** Starts serving (when notifications.enabled), the cards' clock and the history's rules. */
    fun start() {
        if (confRemember()) load()
        wm.caulis.onFocusChanged { window -> history.focus(window?.pid ?: 0, window?.appId.orEmpty(), System.currentTimeMillis()) }
        wm.every(200) { tick() }
        // The old ones: every few minutes (and once now, for what was read back).
        wm.every(180_000) { trim() }
        trim()
        val bus = wm.caulis.sessionBus ?: return log.warn("No session bus: newm cannot show notifications")
        this.bus = bus
        exported += bus.export(PATH, IFACE, mapOf(
            "GetCapabilities" to DBusMethod("", "as") { listOf(listOf("body", "actions", "icon-static", "persistence")) },
            "Notify" to DBusMethod("susssasa{sv}i", "u") { call -> listOf(notify(call)) },
            "CloseNotification" to DBusMethod("u", "") { call ->
                close((call.body[0] as Number).toInt(), ClosedBecause.CLOSED_BY_CALL)
                emptyList()
            },
            "GetServerInformation" to DBusMethod("", "ssss") {
                listOf("newm", "newm", newm.Layout::class.java.`package`?.implementationVersion ?: "dev", "1.2")
            },
        ))
        // Who has the name, now and later: nobody (or the one before quit) — newm takes it.
        watch = NameWatch(bus, NAME) { owner -> ownerChanged(owner) }
    }

    private fun ownerChanged(owner: String?) {
        val bus = bus ?: return
        when {
            owner == null -> {
                serving = false
                if (confEnabled()) bus.requestName(NAME) { ours -> if (ours) log.info { "newm shows notifications" } }
            }
            owner == bus.uniqueName -> {
                serving = true
                otherDaemon = null
            }
            else -> {
                serving = false
                // Another daemon: left alone, its name said once.
                bus.call("org.freedesktop.DBus", ObjectPath("/org/freedesktop/DBus"), "org.freedesktop.DBus", "GetConnectionUnixProcessID", "s", listOf(owner)) { reply ->
                    val pid = (reply.body.firstOrNull() as? Number)?.toInt()
                    val name = pid?.let { runCatching { Files.readString(Path.of("/proc/$it/comm")).trim() }.getOrNull() } ?: owner
                    otherDaemon = name
                    if (!confEnabled()) return@call log.info { "Notifications go to $name (notifications.enabled is off)" }
                    // Left from another session (mako of a session before, on a display that is gone):
                    // closed, and the name is newm's as soon as it is free.
                    if (pid != null && shouldTakeOver(pid)) {
                        log.warn("Closing $name (pid $pid), which holds $NAME but belongs to another session (notifications.takeOver = \"${confTakeOver()}\")")
                        ProcessHandle.of(pid.toLong()).ifPresent { it.destroy() }
                        wm.caulis.notify(tr("%s, left from another session, was closed: notifications are newm's now").replace("%s", name), title = "newm")
                        return@call
                    }
                    log.warn("Notifications go to $name (pid ${pid ?: "?"}), which has $NAME: newm's own are off until it quits")
                    if (!toldOther) {
                        toldOther = true
                        wm.caulis.notify(tr("Notifications go to %s — newm's own are off").replace("%s", name), title = "newm")
                    }
                }
            }
        }
    }

    // True if the daemon [pid] is to be closed for newm's own (notifications.takeOver): any with
    // "always"; with "stale" one started before newm, or showing on another Wayland display.
    // (Never when newm runs nested or headless: then it is not the session's compositor, and
    // the daemon is the live session's own — a test newm on the user's bus closed their mako.)
    private fun shouldTakeOver(pid: Int): Boolean {
        val backend = wm.caulis.runningBackend
        val other = ProcessHandle.of(pid.toLong()).orElse(null)
        val started = other?.info()?.startInstant()?.orElse(null)
        val ours = ProcessHandle.current().info().startInstant().orElse(null)
        val display = runCatching { String(Files.readAllBytes(Path.of("/proc/$pid/environ"))) }.getOrNull()
            ?.split('\u0000')?.firstOrNull { it.startsWith("WAYLAND_DISPLAY=") }?.substringAfter('=')
        val take = takeOverDecision(
            mode = confTakeOver(),
            backend = backend,
            alive = other != null,
            sameUser = other != null && other.info().user().orElse("") == ProcessHandle.current().info().user().orElse("?"),
            startedBefore = started != null && ours != null && started.isBefore(ours),
            otherDisplay = display,
            ourDisplay = wm.caulis.programEnvironment()["WAYLAND_DISPLAY"],
        )
        if (!take && backend != null && backend != dev.caulis.wm.Caulis.Backend.SCREEN) {
            log.info { "Not taking $NAME over: newm runs ${backend.name.lowercase()}, not as the session's compositor" }
        }
        return take
    }

    // notifications.enabled changed (a reload): the name taken or given back.
    private var enabledBefore: Boolean? = null

    private fun followEnabled() {
        val on = confEnabled()
        if (enabledBefore == on) return
        val first = enabledBefore == null
        enabledBefore = on
        if (first) return
        val bus = bus ?: return
        if (on && watch?.owner == null) {
            bus.requestName(NAME)
        } else if (!on && serving) {
            log.info { "notifications.enabled is off: $NAME is given back" }
            bus.call("org.freedesktop.DBus", ObjectPath("/org/freedesktop/DBus"), "org.freedesktop.DBus", "ReleaseName", "s", listOf(NAME)) {}
            for (c in cards) closeCard(c.id)
        }
    }

    // Notify(app_name, replaces_id, app_icon, summary, body, actions, hints, expire_timeout) -> id.
    private fun notify(call: DBusMessage): Int {
        val b = call.body
        val hints = dictOf(b[6])
        // (A program may send anything: a few actions, short keys, the texts cut before they are read.)
        val actionList = (b[5] as? List<*>).orEmpty().take(2 * MOST_ACTIONS).map { it.toString() }
        val actions = actionList.chunked(2).filter { it.size == 2 && it[0].length <= LONGEST_KEY }.map { (key, label) -> key to plainText(label.take(4 * LONGEST_LABEL)).take(LONGEST_LABEL) }
        val imagePath = (hints["image-path"] ?: hints["image_path"]) as? String
        val incoming = Notification(
                id = 0,
                app = (b[0] as String).take(4 * LONGEST_APP).trim().take(LONGEST_APP),
                icon = (imagePath?.takeIf { it.isNotBlank() } ?: b[2] as String).take(LONGEST_PATH).removePrefix("file://"),
                entry = (hints["desktop-entry"] as? String).orEmpty().take(LONGEST_APP),
                summary = plainText((b[3] as String).take(4 * LONGEST_SUMMARY)).take(LONGEST_SUMMARY),
                body = plainText((b[4] as String).take(4 * LONGEST_BODY)).take(LONGEST_BODY),
                actions = actions,
                urgency = (hints["urgency"] as? Number)?.toInt()?.coerceIn(0, 2) ?: 1,
                timeout = (b[7] as Number).toInt(),
                at = System.currentTimeMillis(),
                resident = hints["resident"] == true,
                transient = hints["transient"] == true,
            )
        // The configuration's rules: muted, hidden, another urgency or time.
        val rule = NotificationRule()
        for ((k, r) in confRules().withIndex()) {
            try {
                @Suppress("UNCHECKED_CAST")
                when (r) {
                    is Map<*, *> -> rule.apply(r, incoming)
                    is newm.LuaCallable -> (r(incoming.asTable()) as? Map<*, *>)?.let { rule.apply(it, incoming) }
                    is Function2<*, *, *> -> (r as (NotificationRule, Notification) -> Unit)(rule, incoming)
                    else -> log.warn("notifications.rules[$k]: a rule is a function or a table")
                }
            } catch (e: Exception) {
                log.warn("notifications.rules[$k] failed: $e")
            }
        }
        val ruled = incoming.copy(urgency = rule.urgencyNow ?: incoming.urgency, timeout = rule.secondsNow?.let { it * 1000 } ?: incoming.timeout)
        val n = history.add(ruled, replaces = (b[1] as Number).toInt())
        // (A flood of them never piles up: the most kept, at once.)
        if (history.items.size > confKeep().coerceAtLeast(1)) trim()
        runHook(confOnNotification(), n, { n.asTable() }, "notifications.on_notification")
        if (rule.hidden) {
            log.info { "Notification ${n.id} from ${n.app.ifEmpty { "?" }}: hidden by a rule" }
            history.remove(n.id)
            closed(n, ClosedBecause.DISMISSED)
            return n.id
        }
        // Its picture (an avatar, a screenshot): sent along, or a file (image-path, or a file as app_icon).
        // (Made on the icons' thread, not the compositor's: a big one takes a while. Only the
        // newest asked for a notification is taken: one replacing it may have asked again.)
        pictures.remove(n.id)
        val asked = Any().also { pictureAsked[n.id] = it }
        val take = { p: ImageBitmap? ->
            if (pictureAsked[n.id] === asked) {
                pictureAsked.remove(n.id)
                if (p != null && history.find(n.id) != null) pictures[n.id] = p
            }
        }
        val data = hints["image-data"] ?: hints["image_data"] ?: hints["icon_data"]
        if (data != null) IconTheme.make(post = { task -> wm.caulis.post { task() } }, make = { pictureOf(data) }, done = take)
        else if (n.icon.startsWith("/")) IconTheme.load(n.icon, NOTIFICATION_PICTURE, null, post = { task -> wm.caulis.post { task() } }, done = take)
        log.info { "Notification ${n.id} from ${n.app.ifEmpty { "?" }}${if (n.urgency == 2) " (critical)" else ""}" }
        // (Never what it says: its summary and body are the user's business.)
        log.trace { "Notification ${n.id}: ${n.summary.length} + ${n.body.length} characters, ${n.actions.size} actions" }
        // Who sent it: its window is the one to focus.
        call.sender?.let { sender ->
            bus?.call("org.freedesktop.DBus", ObjectPath("/org/freedesktop/DBus"), "org.freedesktop.DBus", "GetConnectionUnixProcessID", "s", listOf(sender)) { reply ->
                val pid = (reply.body.firstOrNull() as? Number)?.toInt() ?: return@call
                history.update(n.id) { it.copy(pid = pid) }
            }
        }
        if (rule.muted) log.trace { "Notification ${n.id}: muted by a rule, in the history" } else showCard(n)
        changed()
        return n.id
    }

    // A card for [n] (or its card again, when it replaced one) — unless the screen is locked,
    // a window fills the screen, or do not disturb is on (it is in the history then).
    private fun showCard(n: Notification) {
        val existing = cards.firstOrNull { it.id == n.id && !it.leaving }
        val millis = cardMillis(n)
        if (existing != null) {
            existing.left = millis
            existing.serial++
            return
        }
        val why = when {
            doNotDisturb -> "do not disturb"
            wm.lockShown() -> "the screen is locked"
            fullscreen() && !confShowInFullscreen() -> "a window is fullscreen"
            else -> null
        }
        if (why != null) {
            log.trace { "Notification ${n.id}: no card ($why), in the history" }
            if (n.transient) history.remove(n.id)?.let { closed(it, ClosedBecause.EXPIRED) }
            return
        }
        cards = listOf(NotificationCard(n.id, millis)) + cards
        // At most three (notifications.max_cards): the oldest goes to the history.
        cards.filter { !it.leaving }.drop(mostCards()).forEach { closeCard(it.id) }
        cardsFade.show()
    }

    // How long [n]'s card stays (Long.MAX_VALUE: until closed).
    private fun cardMillis(n: Notification): Long = when {
        n.urgency == 2 -> confCriticalSeconds().takeIf { it > 0 }?.let { it * 1000L } ?: Long.MAX_VALUE
        n.timeout == 0 -> Long.MAX_VALUE
        n.timeout > 0 -> n.timeout.toLong()
        else -> confSeconds().coerceAtLeast(1) * 1000L
    }

    private fun fullscreen(): Boolean = runCatching { wm.state.workspaceState(wm.activeWorkspace()).isFullscreen() }.getOrDefault(false) ||
        wm.caulis.desktop.focusedWindow?.fullscreen == true

    /** [id]'s card goes (its notification stays in the history, unless it is transient). */
    fun closeCard(id: Int) {
        val card = cards.firstOrNull { it.id == id && !it.leaving } ?: return
        card.leaving = true
        history.find(id)?.takeIf { it.transient }?.let { history.remove(id); closed(it, ClosedBecause.EXPIRED) }
        // (Its last frames, then out.)
        wm.caulis.after(260) {
            cards = cards.filter { it !== card }
            if (cards.isEmpty()) cardsFade.hide()
        }
    }

    /** [id] out of the history and off the screen; the program is told why. */
    fun close(id: Int, why: ClosedBecause) {
        closeCard(id)
        history.remove(id)?.let { closed(it, why) }
        pictures.remove(id)
        changed()
    }

    /** Everything out of the history ("Clear all"). */
    fun clearAll() {
        for (n in history.items.toList()) close(n.id, ClosedBecause.DISMISSED)
        log.info { "History cleared" }
    }

    // Tells the program (once: only while it still knows the notification).
    private fun closed(n: Notification, why: ClosedBecause) {
        if (!n.live) return
        history.update(n.id) { it.copy(live = false) }
        if (serving) bus?.emitSignal(PATH, IFACE, "NotificationClosed", "uu", listOf(n.id, why.code))
        log.trace { "Notification ${n.id} closed: ${why.name.lowercase()}" }
    }

    /**
     * [id] chosen (a click on it: [action] "default"; or one of its buttons): the program is
     * told (with a token to bring its window forward), its window is focused, its card goes;
     * a button (or a click in the history) is the end of it — out of the history too.
     */
    fun invoke(id: Int, action: String = "default", fromHistory: Boolean = false) {
        val n = history.find(id) ?: return
        val offered = n.actions.any { it.first == action }
        if (n.live && offered && serving) {
            val bus = bus
            if (bus != null) {
                // A good xdg-activation token, so the program may bring its window forward itself.
                runCatching { wm.caulis.programEnvironment()["XDG_ACTIVATION_TOKEN"] }.getOrNull()?.let { token ->
                    bus.emitSignal(PATH, IFACE, "ActivationToken", "us", listOf(n.id, token))
                }
                bus.emitSignal(PATH, IFACE, "ActionInvoked", "us", listOf(n.id, action))
            }
            log.info { "Notification ${n.id}: \"$action\" invoked" }
        }
        focusProgram(n)
        if (action != "default" || fromHistory) {
            if (n.resident && !fromHistory) closeCard(id) else close(id, ClosedBecause.DISMISSED)
        } else {
            closeCard(id)
            // (The program was told; it stays in the history to look at, but the program may forget it.)
            if (!n.resident && offered) closed(n, ClosedBecause.DISMISSED)
            history.update(id) { it.copy(read = true) }
            changed()
        }
        if (model.popup?.kind == PopupKind.NOTIFICATIONS) model.close()
    }

    /** The window of [n]'s program focused (its process' first, else by its name), if there is one. */
    private fun focusProgram(n: Notification) {
        val windows = runCatching { wm.caulis.desktop.allWindows }.getOrDefault(emptyList()).filter { it.mapped }
        val window = windows.firstOrNull { n.pid > 0 && it.pid == n.pid } ?: windows.firstOrNull { n.belongsTo(it.pid, it.appId) }
            ?: return log.trace { "Notification ${n.id}: no window of ${n.app} to focus" }
        log.info { "Notification ${n.id}: focusing $window" }
        wm.viewOf(window)?.let { wm.focusView(it) } ?: wm.focusWindow(window)
    }

    /** The bell's list was looked at: the badge goes. */
    fun markAllRead() {
        history.markAllRead()
        changed()
    }

    /** Do not disturb on or off (kept in the history's file). */
    fun setDoNotDisturb(on: Boolean) {
        dndChoice = on
        log.info { "Do not disturb ${if (on) "on" else "off"}" }
        if (on) for (c in cards) closeCard(c.id)
        changed()
    }

    // Five times a second: the cards' time (not while the pointer is over one), the read ones.
    private fun tick() {
        val now = System.currentTimeMillis()
        val dt = (now - lastTick).coerceIn(0, 1000)
        lastTick = now
        followEnabled()
        // (Five times a second: with no card and no notification there is nothing to count down
        // or read, and nothing is looked at — not even the focus.)
        if (dirtySince > 0 && now - dirtySince > 1000) save()
        if (cards.isEmpty() && history.items.isEmpty()) return
        // (The compositor is going: nothing more to do.)
        if (!wm.caulis.isRunning) return
        val hidden = wm.lockShown()
        for (c in cards) {
            if (c.leaving || c.hovered || c.left == Long.MAX_VALUE || hidden) continue
            c.left -= dt
            if (c.left <= 0) closeCard(c.id)
        }
        // Locked: nobody reads (the focus counts again from the unlocking on).
        if (hidden) {
            history.focus(0, "", now)
        } else {
            val w = wm.caulis.desktop.focusedWindow
            history.focus(w?.pid ?: 0, w?.appId.orEmpty(), now)
            val read = history.readByFocus(confReadAfterFocus().coerceAtLeast(0) * 1000L, now)
            // (Nobody dismissed them: "undefined" — the specification's 4 — not "dismissed".)
            for (n in read) {
                log.info { "Notification ${n.id} read (${n.app}'s window has the focus)" }
                closeCard(n.id)
                closed(n, ClosedBecause.UNDEFINED)
                pictures.remove(n.id)
            }
            if (read.isNotEmpty()) changed()
        }
    }

    private fun trim() {
        val gone = history.trim(confKeepHours(), confKeep(), System.currentTimeMillis())
        for (n in gone) {
            closeCard(n.id)
            closed(n, ClosedBecause.EXPIRED)
            pictures.remove(n.id)
        }
        if (gone.isNotEmpty()) {
            log.info { "${gone.size} old notifications out of the history" }
            changed()
        }
    }

    // Something to write (a moment later: many changes at once are written once).
    private fun changed() {
        if (dirtySince == 0L) dirtySince = System.currentTimeMillis()
    }

    private fun save() {
        dirtySince = 0L
        if (!confRemember()) return
        val text = history.toJson(confKeep(), mapOf("do_not_disturb" to dndChoice))
        writer.execute {
            try {
                privateFolder(file.parent)
                writePrivately(file, text)
            } catch (e: Exception) {
                log.warn("The notifications' history cannot be written to $file: $e")
            }
        }
    }

    private fun load() {
        if (!Files.isRegularFile(file)) return
        try {
            val rest = history.fromJson(Files.readString(file))
            dndChoice = rest["do_not_disturb"] as? Boolean
            // (Only what is kept: a file written with other settings may hold many more, old ones.)
            val gone = history.trim(confKeepHours(), confKeep(), System.currentTimeMillis())
            if (gone.isNotEmpty()) changed()
            log.info { "${history.items.size} notifications read back from $file" + if (gone.isEmpty()) "" else " (${gone.size} older ones left out)" }
        } catch (e: Exception) {
            log.warn("The notifications' history in $file cannot be read: $e")
        }
    }

    /** Stops serving (newm ends): the name is given back with the connection. */
    fun stop() {
        exported.forEach(AutoCloseable::close)
        exported.clear()
        if (dirtySince > 0) save()
    }

    companion object {
        const val NAME = "org.freedesktop.Notifications"
        const val PATH = "/org/freedesktop/Notifications"
        const val IFACE = "org.freedesktop.Notifications"
    }
}

// image-data (iiibiiay): width, height, bytes a row, alpha, bits, channels, the pixels (RGB or RGBA).
private fun pictureOf(data: Any?): ImageBitmap? {
    val v = (data as? dev.caulis.core.dbus.Variant)?.value ?: data
    val parts = v as? List<*> ?: return null
    if (parts.size < 7) return null
    return try {
        val w = (parts[0] as Number).toInt()
        val h = (parts[1] as Number).toInt()
        val stride = (parts[2] as Number).toInt()
        val channels = (parts[5] as Number).toInt()
        val bytes = parts[6] as List<*>
        // (Checked before anything is made of it: a picture of a sensible size, its bytes all there.)
        if (w <= 0 || h <= 0 || w.toLong() * h > MAX_NOTIFICATION_PIXELS || bytes.size.toLong() > MAX_NOTIFICATION_PIXELS * 4) return null
        IconTheme.fromRgba(w, h, stride, channels, ByteArray(bytes.size) { (bytes[it] as Number).toByte() }, NOTIFICATION_PICTURE)
    } catch (e: Exception) {
        log.trace { "A notification's picture cannot be read: $e" }
        null
    }
}

/** The biggest picture a notification may send along (image-data, pixels): an avatar or a screenshot, drawn at 96 pixels anyway. */
internal const val MAX_NOTIFICATION_PIXELS = 2048L * 2048

/** A notification's picture, in pixels (drawn smaller, sharp on scaled screens). */
internal const val NOTIFICATION_PICTURE = 96
