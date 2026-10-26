package newm.bar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.caulis.core.dbus.DBusConnection
import dev.caulis.core.dbus.Variant
import dev.caulis.log.Logger
import dev.caulis.wm.Caulis

private val log = Logger("power")

private const val SERVICE = "net.hadess.PowerProfiles"
private const val PATH = "/net/hadess/PowerProfiles"

/** The computer's power profile (power-profiles-daemon): "power-saver", "balanced", "performance". */
class PowerProfiles(private val caulis: Caulis) {
    var profiles by mutableStateOf<List<String>>(emptyList())
        private set
    var active by mutableStateOf<String?>(null)
        private set

    private var bus: DBusConnection? = null

    fun start() {
        if (bus != null) return
        val bus = caulis.systemBus ?: return
        this.bus = bus
        bus.onSignal(PROPERTIES_IFACE, "PropertiesChanged", PATH) { refresh() }
        refresh()
    }

    fun refresh() {
        bus?.getAll(SERVICE, PATH, SERVICE) { props ->
            if (props == null) return@getAll
            active = props["ActiveProfile"] as? String
            profiles = (props["Profiles"] as? List<*>).orEmpty().mapNotNull { (dictOf(it)["Profile"] as? String) }
        }
    }

    fun use(profile: String) {
        bus?.setProperty(SERVICE, PATH, SERVICE, "ActiveProfile", Variant("s", profile)) { error ->
            if (error != null) log.warn("Power profile $profile: $error") else active = profile
        }
        log.info { "Power profile: $profile" }
    }
}
