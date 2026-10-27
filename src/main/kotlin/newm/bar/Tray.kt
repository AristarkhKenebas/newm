package newm.bar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import dev.caulis.core.dbus.DBusScope
import dev.caulis.core.dbus.DBusError
import dev.caulis.core.dbus.DBusMessage
import dev.caulis.core.dbus.DBusMethod
import dev.caulis.core.dbus.ObjectPath
import dev.caulis.core.dbus.Variant
import dev.caulis.log.Logger
import dev.caulis.wm.Caulis

private val log = Logger("tray")

private const val WATCHER = "org.kde.StatusNotifierWatcher"
private const val WATCHER_PATH = "/StatusNotifierWatcher"
private const val ITEM = "org.kde.StatusNotifierItem"
private const val PROPERTIES = "org.freedesktop.DBus.Properties"
private const val MENU = "com.canonical.dbusmenu"
private const val BUS = "org.freedesktop.DBus"
private const val INVALID_ARGS = "org.freedesktop.DBus.Error.InvalidArgs"

/** The size tray icons are drawn at (pixels: sharp up to a scale of 2). */
internal const val TRAY_ICON_PIXELS = 40

// tray { maxItemsPerProgram = 8; maxIconSize = 1024 }: how many icons one program may put in the
// tray, and the biggest picture (pixels each way) taken from a program; bigger ones are not drawn.
private val confMaxPerProgram = newm.configuredValue("tray.max_items_per_program", 8)
private val confMaxIconSize = newm.configuredValue("tray.max_icon_size", 1024)
// (Hosts that registered: told nothing but kept; a program asking again and again is not kept for ever.)
private const val MAX_HOSTS = 64
// A first reading of an icon's properties that failed: asked again so often, so many ms apart (then more).
private const val GETALL_RETRIES = 2
private const val GETALL_RETRY_MILLIS = 500L

/**
 * D-Bus's rules for names and paths: what a program registers is checked before newm sends
 * anything to it (a message to a malformed name or path makes the bus drop newm's connection).
 */
internal object DBusNames {
    private val pathPart = Regex("[A-Za-z0-9_]+")
    private val wellKnownPart = Regex("[A-Za-z_-][A-Za-z0-9_-]*")
    private val uniquePart = Regex("[A-Za-z0-9_-]+")

    /** An object path: "/" or "/a/b_c/d1" (no empty parts, no trailing slash). */
    fun isObjectPath(path: String): Boolean =
        path == "/" || path.length in 2..1024 && path.startsWith("/") && path.substring(1).split('/').all { pathPart.matches(it) }

    /** A bus name: unique (":1.42") or well-known ("org.kde.StatusNotifierItem-12-1"), at most 255 characters. */
    fun isBusName(name: String): Boolean {
        if (name.isEmpty() || name.length > 255) return false
        return if (name.startsWith(":")) {
            val parts = name.substring(1).split('.')
            parts.size >= 2 && parts.all { uniquePart.matches(it) }
        } else {
            val parts = name.split('.')
            parts.size >= 2 && parts.all { wellKnownPart.matches(it) }
        }
    }
}

/** A program's icon in the tray (a StatusNotifierItem: Telegram, nm-applet, Steam, ...). */
class TrayItem internal constructor(
    /** Its bus name (":1.42" or "org.kde.StatusNotifierItem-1234-1") and object path. */
    val service: String,
    val path: String,
) {
    /** The unique bus name behind [service] (signals come from it). */
    internal var owner: String = service

    // Its properties were read once (until then a failed reading is tried again: a program may
    // register its icon a moment before it shows the object).
    internal var read = false

    var title by mutableStateOf("")
        internal set

    /** Its own name ("nm-applet", "blueman", "telegram"). */
    var id by mutableStateOf("")
        internal set
    var tooltip by mutableStateOf("")
        internal set

    /** "Active", "Passive" (may be hidden) or "NeedsAttention". */
    var status by mutableStateOf("Active")
        internal set
    var icon by mutableStateOf<ImageBitmap?>(null)
        internal set

    /** Its menu's object path (com.canonical.dbusmenu), if it has one. */
    internal var menu: String? = null

    /** A click shows the menu rather than activating it. */
    internal var itemIsMenu = false

    /** The name it was registered with ("service/path"). */
    internal val key: String get() = "$service$path"

    override fun toString() = "TrayItem($service$path \"$title\")"
}

/** One entry of a tray item's menu (com.canonical.dbusmenu). */
data class MenuEntry(
    val id: Int,
    val label: String,
    val enabled: Boolean,
    val separator: Boolean,
    /** "checkmark", "radio" or "". */
    val toggle: String,
    val checked: Boolean,
    val visible: Boolean,
    val children: List<MenuEntry>,
)

/**
 * The tray: background programs' icons (StatusNotifierItem). newm is the watcher programs
 * register with (org.kde.StatusNotifierWatcher), and its own host; when another program is the
 * watcher already (a bar of its own), newm is a host of that one. Everything runs on the
 * compositor's thread, with Caulis's session bus.
 */
class Tray(private val caulis: Caulis) {

    /** The icons now, in the order they came. */
    val items = mutableStateListOf<TrayItem>()

    private var bus: DBusScope? = null
    private var ownWatcher = false
    private val hosts = LinkedHashSet<String>()

    /** Starts it (once the compositor runs). */
    fun start() {
        val bus = caulis.sessionBus ?: return log.warn("No session bus: no tray")
        this.bus = bus
        bus.onSignal(BUS, "NameOwnerChanged", sender = BUS) { m -> nameOwnerChanged(m) }
        for (member in listOf("NewIcon", "NewAttentionIcon", "NewOverlayIcon", "NewTitle", "NewToolTip", "NewStatus", "NewMenu")) {
            bus.onSignal(ITEM, member) { m -> items.filter { it.owner == m.sender }.forEach(::refresh) }
        }
        bus.requestName(WATCHER) { ours ->
            ownWatcher = ours
            if (ours) {
                exportWatcher(bus)
                log.info { "The tray's watcher is newm's" }
            } else {
                log.info { "Another program is the tray's watcher: newm shows its icons" }
                hostOfOther(bus)
            }
        }
    }

    // --- newm as the watcher -------------------------------------------------------------------

    private fun exportWatcher(bus: DBusScope) {
        bus.export(WATCHER_PATH, WATCHER, mapOf(
            "RegisterStatusNotifierItem" to DBusMethod("s", "") { m ->
                val arg = m.body.firstOrNull() as? String ?: throw DBusError(INVALID_ARGS, "a service")
                val sender = m.sender ?: throw DBusError(INVALID_ARGS, "no sender")
                // A path alone (libappindicator) is on the sender; a name is at the usual path.
                val (service, path) = if (arg.startsWith("/")) sender to arg else arg to "/StatusNotifierItem"
                if (!DBusNames.isBusName(service) || !DBusNames.isObjectPath(path)) {
                    log.warn("A tray icon with a malformed name or path from $sender: refused")
                    throw DBusError(INVALID_ARGS, "not a bus name or object path")
                }
                val max = confMaxPerProgram().coerceAtLeast(1)
                if (items.count { it.owner == sender || it.service == sender } >= max) {
                    log.warn("$sender has $max tray icons already: another refused (tray { maxItemsPerProgram })")
                    throw DBusError("org.freedesktop.DBus.Error.LimitsExceeded", "too many tray icons")
                }
                add(service, path, sender)
                emptyList()
            },
            "RegisterStatusNotifierHost" to DBusMethod("s", "") { m ->
                (m.body.firstOrNull() as? String)?.takeIf { hosts.size < MAX_HOSTS && DBusNames.isBusName(it) }?.let { hosts += it }
                bus.emitSignal(WATCHER_PATH, WATCHER, "StatusNotifierHostRegistered")
                emptyList()
            },
        ))
        bus.export(WATCHER_PATH, PROPERTIES, mapOf(
            "Get" to DBusMethod("ss", "v") { m -> listOf(watcherProperty(m.body.getOrNull(1) as? String)) },
            "GetAll" to DBusMethod("s", "a{sv}") { _ ->
                listOf(listOf("RegisteredStatusNotifierItems", "IsStatusNotifierHostRegistered", "ProtocolVersion").associateWith { watcherProperty(it) })
            },
        ))
        // newm is a host itself: programs waiting for one show their icons now.
        bus.emitSignal(WATCHER_PATH, WATCHER, "StatusNotifierHostRegistered")
    }

    private fun watcherProperty(name: String?): Variant = when (name) {
        "RegisteredStatusNotifierItems" -> Variant("as", items.map { it.key })
        "IsStatusNotifierHostRegistered" -> Variant("b", true)
        "ProtocolVersion" -> Variant("i", 0)
        else -> throw DBusError("org.freedesktop.DBus.Error.UnknownProperty", "no property $name")
    }

    // --- newm as a host of another watcher ------------------------------------------------------

    private fun hostOfOther(bus: DBusScope) {
        val host = "org.kde.StatusNotifierHost-${ProcessHandle.current().pid()}"
        bus.requestName(host)
        bus.call(WATCHER, ObjectPath(WATCHER_PATH), WATCHER, "RegisterStatusNotifierHost", "s", listOf(host))
        bus.onSignal(WATCHER, "StatusNotifierItemRegistered", sender = WATCHER) { m -> (m.body.firstOrNull() as? String)?.let(::addByKey) }
        bus.onSignal(WATCHER, "StatusNotifierItemUnregistered", sender = WATCHER) { m -> (m.body.firstOrNull() as? String)?.let { key -> remove { it.key == key || it.service == key } } }
        bus.call(WATCHER, ObjectPath(WATCHER_PATH), PROPERTIES, "Get", "ss", listOf(WATCHER, "RegisteredStatusNotifierItems")) { reply ->
            @Suppress("UNCHECKED_CAST")
            ((reply.body.firstOrNull() as? Variant)?.value as? List<String>)?.forEach(::addByKey)
        }
    }

    // "org.kde.StatusNotifierItem-1-1/StatusNotifierItem" or ":1.5/org/ayatana/NotificationItem/x".
    private fun addByKey(key: String) {
        val slash = key.indexOf('/')
        val (service, path) = if (slash < 0) key to "/StatusNotifierItem" else key.substring(0, slash) to key.substring(slash)
        // (Another watcher's list: checked as a program's own registration is.)
        if (!DBusNames.isBusName(service) || !DBusNames.isObjectPath(path)) return log.warn("A tray icon with a malformed name or path: not shown")
        add(service, path)
    }

    // --- the items --------------------------------------------------------------------------------

    // [service] and [path] are checked already (DBusNames); [sender]: who registered it, if known.
    private fun add(service: String, path: String, sender: String? = null) {
        if (items.any { it.service == service && it.path == path }) return
        val item = TrayItem(service, path)
        sender?.let { item.owner = it }
        items += item
        log.info { "Tray icon: $service$path" }
        val bus = bus ?: return
        bus.call(BUS, ObjectPath("/org/freedesktop/DBus"), BUS, "GetNameOwner", "s", listOf(service)) { reply ->
            val owner = (reply.body.firstOrNull() as? String)?.takeIf { !reply.isError }
            // A name no one has: no icon (it would never go, as no owner change comes for it).
            if (owner == null) remove { it === item } else item.owner = owner
        }
        refresh(item)
        if (ownWatcher) bus.emitSignal(WATCHER_PATH, WATCHER, "StatusNotifierItemRegistered", "s", listOf(item.key))
    }

    private fun remove(which: (TrayItem) -> Boolean) {
        for (item in items.filter(which)) {
            items -= item
            log.info { "Tray icon gone: ${item.service}${item.path}" }
            if (ownWatcher) bus?.emitSignal(WATCHER_PATH, WATCHER, "StatusNotifierItemUnregistered", "s", listOf(item.key))
        }
    }

    private fun nameOwnerChanged(m: DBusMessage) {
        val name = m.body.getOrNull(0) as? String ?: return
        val newOwner = m.body.getOrNull(2) as? String ?: return
        if (newOwner.isEmpty()) {
            remove { it.service == name || it.owner == name }
            hosts -= name
        }
    }

    // Reads all of [item]'s properties again (its icon, title, status, menu).
    private fun refresh(item: TrayItem, tries: Int = 0) {
        val bus = bus ?: return
        bus.call(item.service, ObjectPath(item.path), PROPERTIES, "GetAll", "s", listOf(ITEM)) { reply ->
            if (reply.isError) {
                // (Not read yet: asked again a little later, twice.)
                if (!item.read && tries < GETALL_RETRIES) {
                    log.info { "Tray icon ${item.service}${item.path}: ${reply.errorName}, asked again" }
                    caulis.after(GETALL_RETRY_MILLIS * (tries + 1)) { if (items.any { it === item }) refresh(item, tries + 1) }
                    return@call
                }
                // (One that cannot say what it is never shows more than a letter: away with it.)
                log.warn("Tray icon ${item.service}${item.path}: ${reply.errorName}: removed")
                return@call remove { it === item }
            }
            item.read = true
            val props = HashMap<String, Any?>()
            for (entry in reply.body.firstOrNull() as? List<*> ?: emptyList<Any?>()) {
                val pair = entry as? List<*> ?: continue
                props[pair[0] as String] = (pair[1] as? Variant)?.value
            }
            apply(item, props)
        }
    }

    private fun apply(item: TrayItem, props: Map<String, Any?>) {
        item.title = props["Title"] as? String ?: item.title
        item.id = props["Id"] as? String ?: item.id
        item.status = props["Status"] as? String ?: item.status
        item.menu = (props["Menu"] as? ObjectPath)?.value?.takeIf { it != "/" }
        item.itemIsMenu = props["ItemIsMenu"] as? Boolean ?: false
        (props["ToolTip"] as? List<*>)?.let { tip ->
            val title = (tip.getOrNull(2) as? String ?: "").take(1000)
            // (The same markup as a notification's body, taken out the same linear way: a tooltip comes from any program.)
            val text = plainText((tip.getOrNull(3) as? String ?: "").take(4000))
            item.tooltip = listOf(title, text).filter { it.isNotBlank() }.joinToString("\n")
        }
        if (item.tooltip.isBlank()) item.tooltip = item.title
        val attention = item.status == "NeedsAttention"
        val name = (if (attention) props["AttentionIconName"] as? String else null)?.takeIf { it.isNotBlank() } ?: props["IconName"] as? String
        val pixmaps = (if (attention) props["AttentionIconPixmap"] else null) as? List<*> ?: props["IconPixmap"] as? List<*>
        val themePath = props["IconThemePath"] as? String
        val fromPixmap = pixmapOf(pixmaps)
        if (!name.isNullOrBlank()) {
            IconTheme.load(name, TRAY_ICON_PIXELS, themePath, post = { caulis.post { it() } }) { picture -> item.icon = picture ?: fromPixmap ?: item.icon }
        } else if (fromPixmap != null) {
            item.icon = fromPixmap
        }
    }

    // The biggest of an IconPixmap's pictures (a(iiay)).
    private fun pixmapOf(pixmaps: List<*>?): ImageBitmap? {
        // (Only pictures of a sensible size whose bytes are all there: a program may send anything.)
        val max = confMaxIconSize().coerceIn(16, 8192)
        if (pixmaps == null) return null
        val fitting = pixmaps.mapNotNull { it as? List<*> }.filter { p ->
            val w = p.getOrNull(0) as? Int ?: return@filter false
            val h = p.getOrNull(1) as? Int ?: return@filter false
            w in 1..max && h in 1..max && (p.getOrNull(2) as? List<*>)?.size == w * h * 4
        }
        val best = fitting.maxByOrNull { (it[0] as Int) * (it[1] as Int) } ?: return null
        if (fitting.size < pixmaps.size) log.trace { "Tray pictures past ${max}x$max or broken: left out" }
        val list = best[2] as List<*>
        val bytes = ByteArray(list.size) { ((list[it] as? Number)?.toInt() ?: 0).toByte() }
        return IconTheme.fromArgb(best[0] as Int, best[1] as Int, bytes, TRAY_ICON_PIXELS)
    }

    // --- what clicks do -------------------------------------------------------------------------

    /** A left click at ([x], [y]) on the screen: activates it, or [showMenu] when it is a menu. */
    fun activate(item: TrayItem, x: Int, y: Int, showMenu: () -> Unit) {
        if (item.itemIsMenu && item.menu != null) return showMenu()
        bus?.call(item.service, ObjectPath(item.path), ITEM, "Activate", "ii", listOf(x, y)) { reply ->
            // Not every program activates (some have only their menu).
            if (reply.isError && item.menu != null) showMenu()
        }
    }

    /** A middle click. */
    fun secondaryActivate(item: TrayItem, x: Int, y: Int) {
        bus?.call(item.service, ObjectPath(item.path), ITEM, "SecondaryActivate", "ii", listOf(x, y))
    }

    /** The wheel over it. */
    fun scroll(item: TrayItem, delta: Int) {
        bus?.call(item.service, ObjectPath(item.path), ITEM, "Scroll", "is", listOf(delta, "vertical"))
    }

    /** A right click: true if newm shows a menu ([menuOf]); else the program shows its own. */
    fun hasMenu(item: TrayItem): Boolean = item.menu != null

    fun contextMenu(item: TrayItem, x: Int, y: Int) {
        bus?.call(item.service, ObjectPath(item.path), ITEM, "ContextMenu", "ii", listOf(x, y))
    }

    /** [item]'s menu, read now; [done] gets its entries (empty: none). */
    fun menuOf(item: TrayItem, done: (List<MenuEntry>) -> Unit) {
        val bus = bus ?: return done(emptyList())
        val path = item.menu ?: return done(emptyList())
        bus.call(item.service, ObjectPath(path), MENU, "AboutToShow", "i", listOf(0)) { _ ->
            bus.call(item.service, ObjectPath(path), MENU, "GetLayout", "iias", listOf(0, -1, emptyList<String>())) { reply ->
                if (reply.isError) {
                    log.warn("The menu of $item cannot be read: ${reply.errorName}")
                    return@call done(emptyList())
                }
                val root = reply.body.getOrNull(1) as? List<*>
                done(root?.let { entryOf(it) }?.children.orEmpty())
            }
        }
        menuEvent(item, 0, "opened")
    }

    /** An entry of [item]'s menu was clicked. */
    fun menuClicked(item: TrayItem, id: Int) {
        menuEvent(item, id, "clicked")
        menuEvent(item, 0, "closed")
    }

    /** [item]'s menu closed without a click. */
    fun menuClosed(item: TrayItem) = menuEvent(item, 0, "closed")

    private fun menuEvent(item: TrayItem, id: Int, event: String) {
        val path = item.menu ?: return
        bus?.call(item.service, ObjectPath(path), MENU, "Event", "isvu", listOf(id, event, Variant("i", 0), (System.currentTimeMillis() / 1000).toInt()))
    }

    // (ia{sv}av): an id, its properties, its children.
    private fun entryOf(node: List<*>): MenuEntry {
        val props = HashMap<String, Any?>()
        for (entry in node.getOrNull(1) as? List<*> ?: emptyList<Any?>()) {
            val pair = entry as? List<*> ?: continue
            props[pair[0] as String] = (pair[1] as? Variant)?.value
        }
        val children = (node.getOrNull(2) as? List<*>).orEmpty().mapNotNull { ((it as? Variant)?.value as? List<*>)?.let(::entryOf) }
            .filter { it.visible }
        val label = (props["label"] as? String ?: "").replace("__", "\u0000").replace("_", "").replace("\u0000", "_")
        return MenuEntry(
            id = node[0] as Int,
            label = label,
            enabled = props["enabled"] as? Boolean ?: true,
            separator = props["type"] == "separator",
            toggle = props["toggle-type"] as? String ?: "",
            checked = (props["toggle-state"] as? Int) == 1,
            visible = props["visible"] as? Boolean ?: true,
            children = children,
        )
    }
}
