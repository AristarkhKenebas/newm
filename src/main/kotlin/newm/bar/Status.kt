package newm.bar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.caulis.log.Logger
import dev.caulis.wm.Caulis
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private val log = Logger("bar")

/** The battery: [percent] full, charging or not, and how long until full or empty (minutes, if known). */
data class Battery(val percent: Int, val charging: Boolean, val full: Boolean, val minutesLeft: Int?)

/** The network: Wi-Fi (its name and strength 0..100), a cable, or none. */
data class Network(val kind: Kind, val name: String = "", val strength: Int = 0) {
    enum class Kind { WIFI, WIRED, NONE }
}

/** The sound's volume (0..100, more when raised above) and whether it is muted. */
data class Volume(val percent: Int, val muted: Boolean)

/**
 * What the bar shows of the computer — battery, network, volume, brightness — read on a
 * thread of its own every [periodSeconds] (never on the compositor's: nmcli and wpctl take
 * a while), and set on the compositor's thread, where Compose reads it.
 */
class Status(private val caulis: Caulis, private val periodSeconds: Long = 2) : AutoCloseable {

    var battery by mutableStateOf<Battery?>(null)
        private set
    var network by mutableStateOf(Network(Network.Kind.NONE))
        private set
    var volume by mutableStateOf<Volume?>(null)
        private set

    /** The screen's brightness (0..100), if it can be read. */
    var brightness by mutableStateOf<Int?>(null)
        private set

    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "newm-bar-status").apply { isDaemon = true } }
    private var task: ScheduledFuture<*>? = null
    private var wifiNameAt = 0L
    private var wifiName = ""

    fun start() {
        if (task != null) return
        task = worker.scheduleWithFixedDelay({ readAll() }, 0, periodSeconds, TimeUnit.SECONDS)
    }

    /** Reads again now (after the volume was changed, ...). */
    fun refreshSoon() {
        worker.execute { readAll() }
    }

    private fun readAll() {
        val b = try { readBattery() } catch (e: Exception) { log.trace { "battery: $e" }; null }
        val n = try { readNetwork() } catch (e: Exception) { log.trace { "network: $e" }; Network(Network.Kind.NONE) }
        val v = try { readVolume() } catch (e: Exception) { log.trace { "volume: $e" }; null }
        val l = try { readBrightness() } catch (e: Exception) { null }
        caulis.post {
            if (battery != b) battery = b
            if (network != n) network = n
            if (volume != v) volume = v
            if (brightness != l) brightness = l
        }
    }

    private fun read(path: Path): String? = try { Files.readString(path).trim() } catch (_: Exception) { null }

    private fun readBattery(): Battery? {
        val supplies = File("/sys/class/power_supply").listFiles().orEmpty()
        val bat = supplies.firstOrNull { read(it.toPath().resolve("type")) == "Battery" && read(it.toPath().resolve("present")) != "0" } ?: return null
        val dir = bat.toPath()
        val percent = read(dir.resolve("capacity"))?.toIntOrNull() ?: return null
        val status = read(dir.resolve("status")) ?: ""
        val charging = status == "Charging"
        val full = status == "Full" || (status == "Not charging" && percent >= 95)
        // Energy (µWh) and power (µW), or charge (µAh) and current (µA).
        val now = (read(dir.resolve("energy_now")) ?: read(dir.resolve("charge_now")))?.toLongOrNull()
        val whenFull = (read(dir.resolve("energy_full")) ?: read(dir.resolve("charge_full")))?.toLongOrNull()
        val rate = (read(dir.resolve("power_now")) ?: read(dir.resolve("current_now")))?.toLongOrNull()?.let { Math.abs(it) }
        val minutes = if (now != null && rate != null && rate > 0) {
            if (charging && whenFull != null) ((whenFull - now) * 60 / rate).toInt() else if (!charging) (now * 60 / rate).toInt() else null
        } else null
        return Battery(percent.coerceIn(0, 100), charging, full, minutes?.takeIf { it in 1..(48 * 60) })
    }

    private fun readNetwork(): Network {
        // Wi-Fi: its strength from /proc/net/wireless, its name from nmcli (now and then).
        val wireless = read(Path.of("/proc/net/wireless"))?.lines()?.drop(2)?.firstOrNull { it.isNotBlank() }
        if (wireless != null) {
            val quality = wireless.trim().split(Regex("\\s+")).getOrNull(2)?.trimEnd('.')?.toDoubleOrNull() ?: 0.0
            val now = System.nanoTime()
            if (now - wifiNameAt > 15_000_000_000L || wifiName.isEmpty()) {
                wifiNameAt = now
                wifiName = run("nmcli", "-t", "-f", "active,ssid", "dev", "wifi")?.lines()?.firstOrNull { it.startsWith("yes:") }?.substringAfter(':')
                    ?: run("iwgetid", "-r")?.trim() ?: ""
            }
            return Network(Network.Kind.WIFI, wifiName, (quality / 70 * 100).toInt().coerceIn(0, 100))
        }
        // A cable: an ethernet device that is up.
        val wired = File("/sys/class/net").listFiles().orEmpty().any { dev ->
            val name = dev.name
            name != "lo" && !Files.exists(dev.toPath().resolve("wireless")) && read(dev.toPath().resolve("operstate")) == "up" &&
                read(dev.toPath().resolve("type")) == "1" && (name.startsWith("e") || Files.exists(dev.toPath().resolve("device")))
        }
        return if (wired) Network(Network.Kind.WIRED, "Wired") else Network(Network.Kind.NONE)
    }

    private fun readVolume(): Volume? {
        // "Volume: 0.45" or "Volume: 0.45 [MUTED]" (PipeWire's wpctl), else pactl.
        run("wpctl", "get-volume", "@DEFAULT_AUDIO_SINK@")?.let { out ->
            val level = Regex("Volume:\\s*([0-9.]+)").find(out)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@let
            return Volume((level * 100).toInt(), "MUTED" in out)
        }
        val percent = run("pactl", "get-sink-volume", "@DEFAULT_SINK@")?.let { Regex("(\\d+)%").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: return null
        val muted = run("pactl", "get-sink-mute", "@DEFAULT_SINK@")?.contains("yes") == true
        return Volume(percent, muted)
    }

    private fun readBrightness(): Int? {
        val dev = File("/sys/class/backlight").listFiles()?.firstOrNull() ?: return null
        val now = read(dev.toPath().resolve("brightness"))?.toDoubleOrNull() ?: return null
        val max = read(dev.toPath().resolve("max_brightness"))?.toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        return (now / max * 100).toInt()
    }

    // A program's output (null if it is not there or fails), waiting at most two seconds.
    private fun run(vararg command: String): String? = try {
        val p = ProcessBuilder(*command).redirectErrorStream(true).start()
        p.outputStream.close()
        if (!p.waitFor(2, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            null
        } else {
            p.inputStream.bufferedReader().readText().takeIf { p.exitValue() == 0 }
        }
    } catch (_: Exception) {
        null
    }

    override fun close() {
        task?.cancel(false)
        worker.shutdownNow()
    }
}
