package newm.bar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.caulis.core.dbus.DBusScope
import dev.caulis.core.dbus.DBusError
import dev.caulis.core.dbus.DBusMethod
import dev.caulis.core.dbus.ObjectPath
import dev.caulis.core.dbus.Variant
import dev.caulis.log.Logger
import dev.caulis.wm.Caulis

private val log = Logger("bluetooth")

private const val BLUEZ = "org.bluez"
private const val ADAPTER = "org.bluez.Adapter1"
private const val DEVICE = "org.bluez.Device1"
private const val BATTERY = "org.bluez.Battery1"
private const val AGENT_PATH = "/dev/caulis/newm/BluetoothAgent"

/** A Bluetooth device: paired ones, and those found by a scan. */
data class BluetoothDevice(
    val path: String,
    val name: String,
    /** BlueZ's icon name ("audio-headset", "input-mouse", "phone"). */
    val icon: String,
    val paired: Boolean,
    val connected: Boolean,
    val battery: Int?,
)

/**
 * Bluetooth from the bar, through BlueZ (the system bus): on and off, the devices (paired
 * first), connecting and disconnecting, finding new ones and pairing them. Pairing asks
 * nothing (headphones, mice, speakers: "just works"); newm confirms only pairings started
 * from its bar, never one a device nearby asks for. Runs on the compositor's thread.
 */
class Bluetooth(private val caulis: Caulis) {

    /** There is an adapter (else the bar shows no Bluetooth). */
    var available by mutableStateOf(false)
        private set
    var powered by mutableStateOf(false)
        private set
    var scanning by mutableStateOf(false)
        private set
    var devices by mutableStateOf<List<BluetoothDevice>>(emptyList())
        private set

    /** The device being connected (or paired), and what went wrong last. */
    var busy by mutableStateOf<String?>(null)
        private set
    var problem by mutableStateOf<String?>(null)
        private set

    /** Told when a device connects or goes (a card on the screen). */
    var onConnection: (ConnectionEvent) -> Unit = {}

    // The devices connected at the last look (null: not looked yet, nothing to tell).
    private var connectedBefore: Set<String>? = null

    private var bus: DBusScope? = null
    private var bluez: NameWatch? = null
    private var adapter: String? = null
    private var refreshQueued = false
    private var pairing: String? = null
    private val lastNames = HashMap<String, String>()
    private val lastIcons = HashMap<String, String>()

    // The scan's stop after twenty seconds (a new scan's own stop replaces it).
    private var scanStop: AutoCloseable? = null

    fun start() {
        if (bus != null) return
        val bus = caulis.systemBus ?: return
        this.bus = bus
        // Only BlueZ's own signals (another program on the bus could send the same ones).
        bus.onSignal("org.freedesktop.DBus.ObjectManager", "InterfacesAdded", sender = "org.bluez") { m -> if (bluez?.sent(m) == true) refreshSoon() }
        bus.onSignal("org.freedesktop.DBus.ObjectManager", "InterfacesRemoved", sender = "org.bluez") { m -> if (bluez?.sent(m) == true) refreshSoon() }
        bus.onSignal(PROPERTIES_IFACE, "PropertiesChanged", sender = "org.bluez") { m -> if (bluez?.sent(m) == true && m.path?.startsWith("/org/bluez") == true) refreshSoon() }
        // BlueZ may start later than newm, or start again: the bar follows it.
        bluez = NameWatch(bus, BLUEZ) { owner ->
            if (owner == null) {
                log.info { "BlueZ is not running: no Bluetooth in the bar" }
                adapter = null
                available = false
                powered = false
                scanning = false
                devices = emptyList()
                return@NameWatch
            }
            log.info { "BlueZ is running: Bluetooth in the bar" }
            registerAgent(bus)
            refresh()
        }
    }

    fun refreshSoon() {
        if (refreshQueued) return
        refreshQueued = true
        caulis.after(300) {
            refreshQueued = false
            refresh()
        }
    }

    fun refresh() {
        val bus = bus ?: return
        if (bluez?.owner == null) return
        bus.call(BLUEZ, ObjectPath("/"), "org.freedesktop.DBus.ObjectManager", "GetManagedObjects") { reply ->
            if (reply.isError) return@call
            val found = ArrayList<BluetoothDevice>()
            var adapterSeen: String? = null
            for (entry in reply.body.firstOrNull() as? List<*> ?: emptyList<Any?>()) {
                val pair = entry as? List<*> ?: continue
                val path = (pair[0] as ObjectPath).value
                val ifaces = (pair[1] as? List<*>).orEmpty().mapNotNull { it as? List<*> }.associate { it[0].toString() to dictOf(it[1]) }
                ifaces[ADAPTER]?.let { a ->
                    if (adapterSeen == null) {
                        adapterSeen = path
                        powered = a["Powered"] == true
                        scanning = a["Discovering"] == true
                    }
                }
                ifaces[DEVICE]?.let { d ->
                    val name = (d["Alias"] ?: d["Name"])?.toString() ?: return@let
                    // Found ones without a name (a MAC address as alias) are left out: nobody knows what they are.
                    if (d["Paired"] != true && d["Name"] == null) return@let
                    found += BluetoothDevice(path, name, d["Icon"]?.toString() ?: "bluetooth", d["Paired"] == true, d["Connected"] == true,
                        (ifaces[BATTERY]?.get("Percentage") as? Number)?.toInt())
                }
            }
            adapter = adapterSeen
            available = adapterSeen != null
            devices = found.sortedWith(compareByDescending<BluetoothDevice> { it.connected }.thenByDescending { it.paired }.thenBy { it.name.lowercase() })
            val connected = found.filter { it.connected }.associate { it.path to it.name }
            connectedBefore?.let { before ->
                for ((path, name) in connected) if (path !in before) onConnection(ConnectionEvent(ConnectionEvent.Kind.BLUETOOTH, true, name, lastIcons[path] ?: found.firstOrNull { it.path == path }?.icon))
                for (path in before) if (path !in connected) onConnection(ConnectionEvent(ConnectionEvent.Kind.BLUETOOTH, false, lastNames[path], lastIcons[path]))
            }
            connectedBefore = connected.keys
            lastNames.putAll(found.associate { it.path to it.name })
            lastIcons.putAll(found.associate { it.path to it.icon })
        }
    }

    /** Bluetooth on or off. */
    fun switchPower(on: Boolean) {
        val adapter = adapter ?: return
        problem = null
        bus?.setProperty(BLUEZ, adapter, ADAPTER, "Powered", Variant("b", on)) { error ->
            if (error != null) problem = if ("Blocked" in error || "rfkill" in error.lowercase()) "Bluetooth is blocked (airplane mode?)" else verbatim(error)
            refreshSoon()
        }
        log.info { "Bluetooth ${if (on) "on" else "off"}" }
    }

    /** Looks for devices nearby for twenty seconds (or stops looking). */
    fun scan(on: Boolean = !scanning) {
        val adapter = adapter ?: return
        bus?.call(BLUEZ, ObjectPath(adapter), ADAPTER, if (on) "StartDiscovery" else "StopDiscovery") { reply -> errorOf(reply)?.let { log.trace { "scan: $it" } } }
        // (An earlier scan's stop would end this one early.)
        scanStop?.close()
        scanStop = if (on) caulis.after(20_000) {
            scanStop = null
            if (scanning) scan(false)
        } else null
        log.info { if (on) "Bluetooth: looking for devices" else "Bluetooth: no longer looking for devices" }
    }

    /** Connects [device] (pairing it first when it is new). */
    fun connect(device: BluetoothDevice) {
        val bus = bus ?: return
        problem = null
        busy = device.path
        log.info { "Bluetooth: connecting ${device.name}" }
        fun connectNow() = bus.call(BLUEZ, ObjectPath(device.path), DEVICE, "Connect") { reply ->
            busy = null
            errorOf(reply)?.let { problem = "${verbatim(device.name)}: ${verbatim(it.substringAfter(": ").ifBlank { it })}" }
            refreshSoon()
        }
        if (device.paired) return connectNow()
        pairing = device.path
        bus.call(BLUEZ, ObjectPath(device.path), DEVICE, "Pair") { reply ->
            pairing = null
            val error = errorOf(reply)
            if (error != null && "AlreadyExists" !in error) {
                busy = null
                problem = "${verbatim(device.name)}: could not pair"
                log.warn("Bluetooth: pairing ${device.name}: $error")
                return@call
            }
            bus.setProperty(BLUEZ, device.path, DEVICE, "Trusted", Variant("b", true))
            connectNow()
        }
    }

    fun disconnect(device: BluetoothDevice) {
        busy = device.path
        bus?.call(BLUEZ, ObjectPath(device.path), DEVICE, "Disconnect") { _ ->
            busy = null
            refreshSoon()
        }
        log.info { "Bluetooth: disconnecting ${device.name}" }
    }

    /** Forgets [device] (it has to pair again). */
    fun forget(device: BluetoothDevice) {
        val adapter = adapter ?: return
        bus?.call(BLUEZ, ObjectPath(adapter), ADAPTER, "RemoveDevice", "o", listOf(ObjectPath(device.path))) { _ -> refreshSoon() }
        log.info { "Bluetooth: forgetting ${device.name}" }
    }

    // newm's pairing agent: "just works" pairings the bar started; everything else refused.
    private var agentExported = false

    private fun registerAgent(bus: DBusScope) {
        if (agentExported) return registerWithBluez(bus)
        agentExported = true
        fun ours(path: Any?): Boolean = (path as? ObjectPath)?.value == pairing
        val refused = { _: Any? -> throw DBusError("org.bluez.Error.Rejected", "newm confirms only pairings started from its bar") }
        bus.export(AGENT_PATH, "org.bluez.Agent1", mapOf(
            "Release" to DBusMethod("", "") { emptyList() },
            "Cancel" to DBusMethod("", "") { emptyList() },
            "RequestConfirmation" to DBusMethod("ou", "") { m -> if (ours(m.body[0])) emptyList() else refused(null) },
            "RequestAuthorization" to DBusMethod("o", "") { m -> if (ours(m.body[0])) emptyList() else refused(null) },
            "AuthorizeService" to DBusMethod("os", "") { m ->
                // Paired devices of yours may use their services (headphones' audio).
                val device = devices.firstOrNull { it.path == (m.body[0] as? ObjectPath)?.value }
                if (device?.paired == true || ours(m.body[0])) emptyList() else refused(null)
            },
            "RequestPinCode" to DBusMethod("o", "s") { refused(null) },
            "RequestPasskey" to DBusMethod("o", "u") { refused(null) },
            "DisplayPinCode" to DBusMethod("os", "") { emptyList() },
            "DisplayPasskey" to DBusMethod("ouq", "") { emptyList() },
        ))
        registerWithBluez(bus)
    }

    // Tells BlueZ of the agent (again after BlueZ started again: it forgets it).
    private fun registerWithBluez(bus: DBusScope) {
        bus.call(BLUEZ, ObjectPath("/org/bluez"), "org.bluez.AgentManager1", "RegisterAgent", "os", listOf(ObjectPath(AGENT_PATH), "NoInputNoOutput")) { reply ->
            if (reply.isError) log.trace { "Bluetooth agent: ${errorOf(reply)}" } else log.info { "Bluetooth pairing agent registered" }
        }
    }
}
