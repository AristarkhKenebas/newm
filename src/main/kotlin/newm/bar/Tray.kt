package newm.bar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import dev.caulis.core.dbus.DBusConnection
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

/** The size tray icons are drawn at (pixels: sharp up to a scale of 2). */
internal const val TRAY_ICON_PIXELS = 40

/** A program's icon in the tray (a StatusNotifierItem: Telegram, nm-applet, Steam, ...). */
class TrayItem internal constructor(
    /** Its bus name (":1.42" or "org.kde.StatusNotifierItem-1234-1") and object path. */
    val service: String,
    val path: String,
) {
    /** The unique bus name behind [service] (signals come from it). */
    internal var owner: String = service

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

    private var bus: DBusConnection? = null
    private var ownWatcher = false
    private val hosts = LinkedHashSet<String>()

    /** Starts it (once the compositor runs). */
    fun start() {
        val bus = caulis.sessionBus ?: return log.warn("No session bus: no tray")
        this.bus = bus
        bus.onSignal(BUS, "NameOwnerChanged") { m -> nameOwnerChanged(m) }
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

    private fun exportWatcher(bus: DBusConnection) {
        bus.export(WATCHER_PATH, WATCHER, mapOf(
            "RegisterStatusNotifierItem" to DBusMethod("s", "") { m ->
                val arg = m.body.firstOrNull() as? String ?: throw DBusError("org.freedesktop.DBus.Error.InvalidArgs", "a service")
                val sender = m.sender ?: arg
                // A path alone (libappindicator) is on the sender; a name is at the usual path.
                if (arg.startsWith("/")) add(sender, arg) else add(arg, "/StatusNotifierItem")
                emptyList()
            },
            "RegisterStatusNotifierHost" to DBusMethod("s", "") { m ->
                (m.body.firstOrNull() as? String)?.let { hosts += it }
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

    private fun hostOfOther(bus: DBusConnection) {
        val host = "org.kde.StatusNotifierHost-${ProcessHandle.current().pid()}"
        bus.requestName(host)
        bus.call(WATCHER, ObjectPath(WATCHER_PATH), WATCHER, "RegisterStatusNotifierHost", "s", listOf(host))
        bus.onSignal(WATCHER, "StatusNotifierItemRegistered") { m -> (m.body.firstOrNull() as? String)?.let(::addByKey) }
        bus.onSignal(WATCHER, "StatusNotifierItemUnregistered") { m -> (m.body.firstOrNull() as? String)?.let { key -> remove { it.key == key || it.service == key } } }
        bus.call(WATCHER, ObjectPath(WATCHER_PATH), PROPERTIES, "Get", "ss", listOf(WATCHER, "RegisteredStatusNotifierItems")) { reply ->
            @Suppress("UNCHECKED_CAST")
            ((reply.body.firstOrNull() as? Variant)?.value as? List<String>)?.forEach(::addByKey)
        }
    }

    // "org.kde.StatusNotifierItem-1-1/StatusNotifierItem" or ":1.5/org/ayatana/NotificationItem/x".
    private fun addByKey(key: String) {
        val slash = key.indexOf('/')
        if (slash < 0) add(key, "/StatusNotifierItem") else add(key.substring(0, slash), key.substring(slash))
    }

    // --- the items --------------------------------------------------------------------------------

    private fun add(service: String, path: String) {
        if (items.any { it.service == service && it.path == path }) return
        val item = TrayItem(service, path)
        items += item
        log.info { "Tray icon: $service$path" }
        val bus = bus ?: return
        bus.call(BUS, ObjectPath("/org/freedesktop/DBus"), BUS, "GetNameOwner", "s", listOf(service)) { reply ->
            (reply.body.firstOrNull() as? String)?.takeIf { !reply.isError }?.let { item.owner = it }
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
    private fun refresh(item: TrayItem) {
        val bus = bus ?: return
        bus.call(item.service, ObjectPath(item.path), PROPERTIES, "GetAll", "s", listOf(ITEM)) { reply ->
            if (reply.isError) return@call log.warn("Tray icon ${item.service}${item.path}: ${reply.errorName} ${reply.body.firstOrNull()}")
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
            val title = tip.getOrNull(2) as? String ?: ""
            val text = (tip.getOrNull(3) as? String ?: "").replace(Regex("<[^>]*>"), "")
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
        val best = pixmaps?.mapNotNull { it as? List<*> }?.maxByOrNull { (it[0] as Int) * (it[1] as Int) } ?: return null
        val bytes = (best[2] as? List<*>)?.let { list -> ByteArray(list.size) { (list[it] as Int).toByte() } } ?: return null
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
