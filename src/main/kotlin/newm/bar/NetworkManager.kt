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

/** A network or device came ([connected]) or went: Wi-Fi ([name]: the network), a cable, a Bluetooth device. */
data class ConnectionEvent(val kind: Kind, val connected: Boolean, val name: String?, val icon: String? = null, val connecting: Boolean = false) {
    enum class Kind { WIFI, WIRED, BLUETOOTH }
}

/**
 * A Wi-Fi network in reach: its name, strength (0..100), whether it asks for a password, whether
 * it is in use or known, and how it is protected (from its access point's flags).
 */
data class WifiNetwork(
    val ssid: String, val strength: Int, val secure: Boolean, val path: String, val active: Boolean, val known: Boolean,
    val security: WifiSecurity = if (secure) WifiSecurity.PSK else WifiSecurity.OPEN,
)

/** How a Wi-Fi network is protected: open (or OWE: encrypted, no password), WEP, WPA's password, WPA3's SAE, or 802.1X (enterprise). */
enum class WifiSecurity { OPEN, OWE, WEP, PSK, SAE, ENTERPRISE }

// NetworkManager's access point flags: privacy (WEP, or any WPA), and the key managements of WpaFlags / RsnFlags.
private const val AP_PRIVACY = 0x1
private const val KEY_PSK = 0x100
private const val KEY_8021X = 0x200
private const val KEY_SAE = 0x400
private const val KEY_OWE = 0x800
private const val KEY_OWE_TM = 0x1000
private const val KEY_EAP_SUITE_B = 0x2000

/** How an access point is protected, from its Flags, WpaFlags and RsnFlags. */
internal fun securityOf(flags: Int, wpaFlags: Int, rsnFlags: Int): WifiSecurity {
    val keys = wpaFlags or rsnFlags
    return when {
        keys and (KEY_8021X or KEY_EAP_SUITE_B) != 0 -> WifiSecurity.ENTERPRISE
        // (WPA3 alone; with WPA2 beside it, its password works for both.)
        keys and KEY_SAE != 0 && keys and KEY_PSK == 0 -> WifiSecurity.SAE
        keys and KEY_PSK != 0 -> WifiSecurity.PSK
        keys and (KEY_OWE or KEY_OWE_TM) != 0 -> WifiSecurity.OWE
        keys == 0 && flags and AP_PRIVACY != 0 -> WifiSecurity.WEP
        else -> WifiSecurity.OPEN
    }
}

/** What is wrong with [password] for a network protected by [security] (English words), or null: it can be tried. */
fun passwordProblem(security: WifiSecurity, password: String): String? = when (security) {
    WifiSecurity.WEP -> {
        val hex = password.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        if (password.length == 5 || password.length == 13 || (hex && (password.length == 10 || password.length == 26))) null
        else "5 or 13 characters, or 10 or 26 hex digits"
    }
    WifiSecurity.SAE -> if (password.isEmpty()) "Password" else null
    else -> if (password.length < 8) "At least 8 characters" else null
}

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

    /** Told when a network comes or goes (a card on the screen). */
    var onConnection: (ConnectionEvent) -> Unit = {}

    /** The networks in reach are on screen (a popup): only then do their strengths' changes count. */
    var networksShown: () -> Boolean = { true }

    /** The network asks for a password (none saved, or a wrong one): a window asks for it. */
    var onPasswordWanted: (WifiNetwork) -> Unit = {}

    // The Wi-Fi device's state (NetworkManager's: 100 connected, 40..90 on the way, 120 failed).
    private var wifiState = 0

    // The network in use at the last look (null: not looked yet), and whether a cable was.
    private var activeBefore: String? = null
    private var lookedOnce = false
    private var wiredBefore = false

    private var bus: DBusConnection? = null
    private var nm: NameWatch? = null
    private var wifiDevice: String? = null
    private var knownSsids = emptyMap<String, String>()
    private var refreshQueued = false

    // A profile made for a new network with a password: removed again when that password was wrong.
    private var newProfile: String? = null

    fun start() {
        if (bus != null) return
        val bus = caulis.systemBus ?: return log.warn("No system bus: Wi-Fi only as /proc says")
        this.bus = bus
        // Only NetworkManager's own signals; of its access points' changes (strengths, many a second) only while they are shown.
        bus.onSignal(PROPERTIES_IFACE, "PropertiesChanged") { m ->
            if (nm?.sent(m) != true || m.path?.startsWith(NM_PATH) != true) return@onSignal
            val iface = m.body.getOrNull(0) as? String
            val names = dictOf(m.body.getOrNull(1)).keys
            val inReachOnly = iface == AP || (iface == WIRELESS && names.isNotEmpty() && names.all { it == "AccessPoints" || it == "LastScan" })
            if (inReachOnly && !networksShown()) return@onSignal
            refreshSoon()
        }
        bus.onSignal(DEVICE, "StateChanged") { m -> if (nm?.sent(m) == true && m.path == wifiDevice) deviceState(m.body) }
        // NetworkManager may start later than newm, or start again: the bar follows it.
        nm = NameWatch(bus, NM) { owner ->
            available = owner != null
            if (owner == null) {
                log.info { "NetworkManager is not running: Wi-Fi only as /proc says" }
                wifiDevice = null
                networks = emptyList()
                wired = false
                connecting = null
                return@NameWatch
            }
            log.info { "NetworkManager is running" }
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
        if (nm?.owner == null) return
        bus.getAll(NM, NM_PATH, NM) { props -> props?.let { wifiEnabled = it["WirelessEnabled"] == true } }
        bus.call(NM, ObjectPath(NM_PATH), NM, "GetDevices") { reply ->
            if (reply.isError) return@call log.trace { "NetworkManager's devices: ${errorOf(reply)}" }
            val devices = (reply.body.firstOrNull() as? List<*>).orEmpty().mapNotNull { (it as? ObjectPath)?.value }
            var wiredSeen = false
            // The Wi-Fi devices and their states (picked again each time: a USB stick comes and goes).
            val wifis = LinkedHashMap<String, Int>()
            fun done() {
                val picked = wifiDevice?.takeIf { it in wifis } ?: wifis.keys.firstOrNull()
                if (picked != wifiDevice) log.info { "Wi-Fi device: ${picked ?: "none"}" }
                wifiDevice = picked
                wifiState = picked?.let { wifis[it] } ?: 0
                if (lookedOnce && wiredSeen != wiredBefore) onConnection(ConnectionEvent(ConnectionEvent.Kind.WIRED, wiredSeen, null))
                wiredBefore = wiredSeen
                wired = wiredSeen
                readNetworks()
            }
            if (devices.isEmpty()) return@call done()
            var pending = devices.size
            for (device in devices) bus.getAll(NM, device, DEVICE) { props ->
                val type = (props?.get("DeviceType") as? Number)?.toInt()
                val state = (props?.get("State") as? Number)?.toInt() ?: 0
                if (type == 2) wifis[device] = state
                if (type == 1 && state == 100) wiredSeen = true
                if (--pending == 0) done()
            }
        }
        readKnown()
    }

    // The access points of the Wi-Fi device, one per name (the strongest), the one in use first.
    private fun readNetworks() {
        val bus = bus ?: return
        val device = wifiDevice ?: return run { networks = emptyList() }
        bus.getAll(NM, device, WIRELESS) { wprops ->
            // (In use only once connected: while connecting the access point is chosen already.)
            val activeAp = (wprops?.get("ActiveAccessPoint") as? ObjectPath)?.value?.takeIf { wifiState == 100 }
            bus.call(NM, ObjectPath(device), WIRELESS, "GetAllAccessPoints") { reply ->
                val aps = (reply.body.firstOrNull() as? List<*>).orEmpty().map { (it as ObjectPath).value }
                if (aps.isEmpty()) return@call run { networks = emptyList() }
                val found = ArrayList<WifiNetwork>()
                var pending = aps.size
                for (ap in aps) bus.getAll(NM, ap, AP) { p ->
                    if (p != null) {
                        val ssid = textOf(p["Ssid"])
                        val security = securityOf((p["Flags"] as? Number)?.toInt() ?: 0, (p["WpaFlags"] as? Number)?.toInt() ?: 0, (p["RsnFlags"] as? Number)?.toInt() ?: 0)
                        val secure = security != WifiSecurity.OPEN && security != WifiSecurity.OWE
                        if (ssid.isNotBlank()) found += WifiNetwork(ssid, (p["Strength"] as? Number)?.toInt() ?: 0, secure, ap, ap == activeAp, ssid in knownSsids, security)
                    }
                    if (--pending == 0) {
                        val active = found.firstOrNull { it.active }?.ssid
                        if (lookedOnce && active != activeBefore) {
                            if (active != null) onConnection(ConnectionEvent(ConnectionEvent.Kind.WIFI, true, active))
                            else activeBefore?.let { onConnection(ConnectionEvent(ConnectionEvent.Kind.WIFI, false, it)) }
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
            if (error != null) {
                problem = "Wi-Fi cannot be switched: ${verbatim(error)}"
                log.warn("Wi-Fi cannot be switched: $error")
            } else wifiEnabled = on
        }
        log.info { "Wi-Fi ${if (on) "on" else "off"}" }
    }

    /** Looking for networks now (after [scan], a few seconds). */
    var scanning by mutableStateOf(false)
        private set

    /** Looks for networks again (they come in a few seconds). */
    fun scan() {
        val device = wifiDevice ?: return
        if (scanning) return
        scanning = true
        bus?.call(NM, ObjectPath(device), WIRELESS, "RequestScan", "a{sv}", listOf(emptyMap<String, Variant>())) { _ -> }
        caulis.after(3500) {
            scanning = false
            refresh()
        }
    }

    /** Whether connecting to [network] needs a password from you (secure and not known; 802.1X is not asked for here). */
    fun needsPassword(network: WifiNetwork): Boolean = network.secure && !network.known && network.security != WifiSecurity.ENTERPRISE

    /** Connects to [network]: a known one as saved, a new one with [password] (null for an open one). */
    fun connect(network: WifiNetwork, password: String? = null) {
        val bus = bus ?: return
        val device = wifiDevice ?: return
        problem = null
        connecting = network.ssid
        log.info { "Connecting to Wi-Fi \"${network.ssid}\"" } // (never the password)
        val known = knownSsids[network.ssid]
        // 802.1X (a user name, certificates) is not set up from the bar; one saved elsewhere (nmcli) connects as saved.
        if (known == null && network.security == WifiSecurity.ENTERPRISE) {
            log.info { "Wi-Fi \"${network.ssid}\" uses 802.1X: not set up from the bar" }
            connecting = null
            problem = "Enterprise (802.1X) networks are not supported here"
            return
        }
        // A password given for a saved network (its saved one was wrong or none): its profile goes, a new one comes.
        if (known != null && password != null) {
            bus.call(NM, ObjectPath(known), "$SETTINGS.Connection", "Delete") { _ ->
                knownSsids = knownSsids - network.ssid
                connect(network.copy(known = false), password)
            }
            return
        }
        if (known != null) {
            bus.call(NM, ObjectPath(NM_PATH), NM, "ActivateConnection", "ooo", listOf(ObjectPath(known), ObjectPath(device), ObjectPath(network.path))) { reply ->
                errorOf(reply)?.let { failed(it) }
            }
        } else {
            val settings = LinkedHashMap<String, Map<String, Variant>>()
            securitySettings(network.security, password)?.let { settings["802-11-wireless-security"] = it }
            bus.call(NM, ObjectPath(NM_PATH), NM, "AddAndActivateConnection", "a{sa{sv}}oo", listOf(settings, ObjectPath(device), ObjectPath(network.path))) { reply ->
                val error = errorOf(reply)
                if (error != null) failed(error) else newProfile = (reply.body.firstOrNull() as? ObjectPath)?.value
            }
        }
    }

    // The security part of a new profile, as the access point's flags ask (the password only here, in the D-Bus call's body).
    private fun securitySettings(security: WifiSecurity, password: String?): Map<String, Variant>? = when (security) {
        WifiSecurity.OPEN -> null
        WifiSecurity.OWE -> mapOf("key-mgmt" to Variant("s", "owe"))
        WifiSecurity.WEP -> password?.let { mapOf("key-mgmt" to Variant("s", "none"), "wep-key0" to Variant("s", it), "wep-key-type" to Variant("u", 1)) }
        WifiSecurity.PSK -> password?.let { mapOf("key-mgmt" to Variant("s", "wpa-psk"), "psk" to Variant("s", it)) }
        WifiSecurity.SAE -> password?.let { mapOf("key-mgmt" to Variant("s", "sae"), "psk" to Variant("s", it)) }
        WifiSecurity.ENTERPRISE -> null
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

    // The Wi-Fi device's state: 100 connected, 40..90 on the way, 120 failed (reason 7: no
    // password saved; 8: the password was refused).
    private fun deviceState(body: List<Any?>) {
        val state = (body.getOrNull(0) as? Number)?.toInt() ?: return
        val before = wifiState
        wifiState = state
        val reason = (body.getOrNull(2) as? Number)?.toInt() ?: 0
        when {
            state == 100 -> {
                connecting = null
                newProfile = null
                problem = null
            }
            // On the way (said once, as it starts): a card says "Connecting".
            state in 40..90 && before !in 40..90 -> connecting?.let { onConnection(ConnectionEvent(ConnectionEvent.Kind.WIFI, false, it, connecting = true)) }
            state == 120 -> {
                val wanted = connecting?.let { name -> networks.firstOrNull { it.ssid == name } }
                failed(if (reason == 7 || reason == 8) "Wrong password" else "Could not connect (reason $reason)")
                // No password, or a wrong one: asked for in a window.
                if ((reason == 7 || reason == 8) && wanted != null && wanted.secure && wanted.security != WifiSecurity.ENTERPRISE) onPasswordWanted(wanted)
            }
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
