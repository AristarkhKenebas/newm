package newm.bar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.caulis.core.dbus.DBusConnection
import dev.caulis.core.dbus.ObjectPath
import dev.caulis.core.dbus.Variant
import dev.caulis.log.Logger
import dev.caulis.wm.Caulis

private val log = Logger("network")

private const val NM = "org.freedesktop.NetworkManager"
private const val NM_PATH = "/org/freedesktop/NetworkManager"
private const val DEVICE = "org.freedesktop.NetworkManager.Device"
private const val WIRELESS = "org.freedesktop.NetworkManager.Device.Wireless"
private const val AP = "org.freedesktop.NetworkManager.AccessPoint"
private const val SETTINGS = "org.freedesktop.NetworkManager.Settings"
private const val SETTINGS_PATH = "/org/freedesktop/NetworkManager/Settings"

/** A Wi-Fi network in reach: its name, strength (0..100), whether it asks for a password, whether it is in use or known. */
data class WifiNetwork(val ssid: String, val strength: Int, val secure: Boolean, val path: String, val active: Boolean, val known: Boolean)

/**
 * Wi-Fi from the bar, through NetworkManager (its D-Bus interface, the system bus): on and
 * off, the networks in reach, connecting (with a password when one is asked: given over
 * D-Bus, never on a command line), disconnecting. Runs on the compositor's thread.
 */
class NetworkManager(private val caulis: Caulis) {

    /** NetworkManager is there (else the bar shows only what /proc says). */
    var available by mutableStateOf(false)
        private set
    var wifiEnabled by mutableStateOf(false)
        private set
    var networks by mutableStateOf<List<WifiNetwork>>(emptyList())
        private set

    /** A cable is connected. */
    var wired by mutableStateOf(false)
        private set

    /** The network being connected to, and what went wrong last (shown in the popup). */
    var connecting by mutableStateOf<String?>(null)
        private set
    var problem by mutableStateOf<String?>(null)
        private set

    /** Told what happened in words ("Connected to Home"): a toast. */
    var onEvent: (String) -> Unit = {}

    // The network in use at the last look (null: not looked yet), and whether a cable was.
    private var activeBefore: String? = null
    private var lookedOnce = false
    private var wiredBefore = false

    private var bus: DBusConnection? = null
    private var wifiDevice: String? = null
    private var knownSsids = emptyMap<String, String>()
    private var refreshQueued = false

    // A profile made for a new network with a password: removed again when that password was wrong.
    private var newProfile: String? = null

    fun start() {
        if (bus != null) return
        val bus = caulis.systemBus ?: return log.warn("No system bus: Wi-Fi only as /proc says")
        this.bus = bus
        bus.call("org.freedesktop.DBus", ObjectPath("/org/freedesktop/DBus"), "org.freedesktop.DBus", "NameHasOwner", "s", listOf(NM)) { reply ->
            available = reply.body.firstOrNull() == true
            if (!available) return@call log.info { "NetworkManager is not running: Wi-Fi only as /proc says" }
            bus.onSignal(PROPERTIES_IFACE, "PropertiesChanged") { m -> if (m.path?.startsWith(NM_PATH) == true) refreshSoon() }
            bus.onSignal(DEVICE, "StateChanged") { m -> if (m.path == wifiDevice) deviceState(m.body) }
            refresh()
        }
    }

    /** Reads everything again (a moment later, once for many changes). */
    fun refreshSoon() {
        if (refreshQueued) return
        refreshQueued = true
        caulis.after(400) {
            refreshQueued = false
            refresh()
        }
    }

    fun refresh() {
        val bus = bus ?: return
        bus.getAll(NM, NM_PATH, NM) { props -> props?.let { wifiEnabled = it["WirelessEnabled"] == true } }
        bus.call(NM, ObjectPath(NM_PATH), NM, "GetDevices") { reply ->
            val devices = (reply.body.firstOrNull() as? List<*>).orEmpty().map { (it as ObjectPath).value }
            var wiredSeen = false
            var pending = devices.size
            for (device in devices) bus.getAll(NM, device, DEVICE) { props ->
                val type = (props?.get("DeviceType") as? Number)?.toInt()
                val state = (props?.get("State") as? Number)?.toInt() ?: 0
                if (type == 2 && wifiDevice == null) wifiDevice = device
                if (type == 1 && state == 100) wiredSeen = true
                if (--pending == 0) {
                    if (lookedOnce && wiredSeen != wiredBefore) onEvent(if (wiredSeen) "Cable connected" else "Cable disconnected")
                    wiredBefore = wiredSeen
                    wired = wiredSeen
                    readNetworks()
                }
            }
        }
        readKnown()
    }

    // The access points of the Wi-Fi device, one per name (the strongest), the one in use first.
    private fun readNetworks() {
        val bus = bus ?: return
        val device = wifiDevice ?: return run { networks = emptyList() }
        bus.getAll(NM, device, WIRELESS) { wprops ->
            val activeAp = (wprops?.get("ActiveAccessPoint") as? ObjectPath)?.value
            bus.call(NM, ObjectPath(device), WIRELESS, "GetAllAccessPoints") { reply ->
                val aps = (reply.body.firstOrNull() as? List<*>).orEmpty().map { (it as ObjectPath).value }
                if (aps.isEmpty()) return@call run { networks = emptyList() }
                val found = ArrayList<WifiNetwork>()
                var pending = aps.size
                for (ap in aps) bus.getAll(NM, ap, AP) { p ->
                    if (p != null) {
                        val ssid = textOf(p["Ssid"])
                        val secure = ((p["Flags"] as? Number)?.toInt() ?: 0) != 0 || ((p["WpaFlags"] as? Number)?.toInt() ?: 0) != 0 || ((p["RsnFlags"] as? Number)?.toInt() ?: 0) != 0
                        if (ssid.isNotBlank()) found += WifiNetwork(ssid, (p["Strength"] as? Number)?.toInt() ?: 0, secure, ap, ap == activeAp, ssid in knownSsids)
                    }
                    if (--pending == 0) {
                        val active = found.firstOrNull { it.active }?.ssid
                        if (lookedOnce && active != activeBefore) {
                            if (active != null) onEvent("Connected to $active") else activeBefore?.let { onEvent("Disconnected from $it") }
                        }
                        activeBefore = active
                        lookedOnce = true
                        networks = found.groupBy { it.ssid }.map { (_, same) -> same.firstOrNull { it.active } ?: same.maxBy { it.strength } }
                            .sortedWith(compareByDescending<WifiNetwork> { it.active }.thenByDescending { it.known }.thenByDescending { it.strength })
                    }
                }
            }
        }
    }

    // The saved Wi-Fi profiles: their names (SSIDs) and paths.
    private fun readKnown() {
        val bus = bus ?: return
        bus.call(NM, ObjectPath(SETTINGS_PATH), SETTINGS, "ListConnections") { reply ->
            val paths = (reply.body.firstOrNull() as? List<*>).orEmpty().map { (it as ObjectPath).value }
            val known = HashMap<String, String>()
            var pending = paths.size
            if (pending == 0) knownSsids = known
            for (path in paths) bus.call(NM, ObjectPath(path), "$SETTINGS.Connection", "GetSettings") { r ->
                val sections = dictOf(r.body.firstOrNull())
                val wifi = (sections["802-11-wireless"] as? List<*>)?.let(::dictOf)
                    ?: (r.body.firstOrNull() as? List<*>)?.mapNotNull { it as? List<*> }?.firstOrNull { it[0] == "802-11-wireless" }?.let { dictOf(it[1]) }
                wifi?.get("ssid")?.let { known[textOf(it)] = path }
                if (--pending == 0) {
                    knownSsids = known
                    networks = networks.map { it.copy(known = it.ssid in known) }
                }
            }
        }
    }

    /** Wi-Fi on or off. */
    fun setWifi(on: Boolean) {
        bus?.setProperty(NM, NM_PATH, NM, "WirelessEnabled", Variant("b", on)) { error ->
            if (error != null) problem = "Wi-Fi cannot be switched: $error" else wifiEnabled = on
        }
        log.info { "Wi-Fi ${if (on) "on" else "off"}" }
    }

    /** Looks for networks again (they come in a few seconds). */
    fun scan() {
        val device = wifiDevice ?: return
        bus?.call(NM, ObjectPath(device), WIRELESS, "RequestScan", "a{sv}", listOf(emptyMap<String, Variant>())) { _ -> }
        caulis.after(3500) { refresh() }
    }

    /** Whether connecting to [network] needs a password from you (secure and not known). */
    fun needsPassword(network: WifiNetwork): Boolean = network.secure && !network.known

    /** Connects to [network]: a known one as saved, a new one with [password] (null for an open one). */
    fun connect(network: WifiNetwork, password: String? = null) {
        val bus = bus ?: return
        val device = wifiDevice ?: return
        problem = null
        connecting = network.ssid
        log.info { "Connecting to Wi-Fi \"${network.ssid}\"" } // (never the password)
        val known = knownSsids[network.ssid]
        if (known != null) {
            bus.call(NM, ObjectPath(NM_PATH), NM, "ActivateConnection", "ooo", listOf(ObjectPath(known), ObjectPath(device), ObjectPath(network.path))) { reply ->
                errorOf(reply)?.let { failed(it) }
            }
        } else {
            val settings = LinkedHashMap<String, Map<String, Variant>>()
            if (network.secure && password != null) settings["802-11-wireless-security"] = mapOf("key-mgmt" to Variant("s", "wpa-psk"), "psk" to Variant("s", password))
            bus.call(NM, ObjectPath(NM_PATH), NM, "AddAndActivateConnection", "a{sa{sv}}oo", listOf(settings, ObjectPath(device), ObjectPath(network.path))) { reply ->
                val error = errorOf(reply)
                if (error != null) failed(error) else newProfile = (reply.body.firstOrNull() as? ObjectPath)?.value
            }
        }
    }

    /** Leaves the Wi-Fi network in use. */
    fun disconnect() {
        val device = wifiDevice ?: return
        bus?.call(NM, ObjectPath(device), DEVICE, "Disconnect") { reply -> errorOf(reply)?.let { problem = it } }
        log.info { "Wi-Fi disconnected" }
    }

    /** Forgets [network]'s saved profile (its password). */
    fun forget(network: WifiNetwork) {
        val profile = knownSsids[network.ssid] ?: return
        bus?.call(NM, ObjectPath(profile), "$SETTINGS.Connection", "Delete") { _ -> refreshSoon() }
        log.info { "Wi-Fi \"${network.ssid}\" forgotten" }
    }

    // The Wi-Fi device's state: 100 connected, 120 failed (reason 7: no or wrong password).
    private fun deviceState(body: List<Any?>) {
        val state = (body.getOrNull(0) as? Number)?.toInt() ?: return
        val reason = (body.getOrNull(2) as? Number)?.toInt() ?: 0
        when (state) {
            100 -> {
                connecting = null
                newProfile = null
                problem = null
            }
            120 -> failed(if (reason == 7 || reason == 8) "Wrong password" else "Could not connect (reason $reason)")
        }
        refreshSoon()
    }

    private fun failed(why: String) {
        log.warn("Wi-Fi \"${connecting ?: ""}\": $why")
        problem = why
        connecting = null
        // A wrong password is not kept: the next try asks again.
        newProfile?.let { profile -> bus?.call(NM, ObjectPath(profile), "$SETTINGS.Connection", "Delete") { _ -> } }
        newProfile = null
        refreshSoon()
    }
}
