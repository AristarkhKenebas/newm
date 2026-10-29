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
data class Battery(
    val percent: Int,
    val charging: Boolean,
    val full: Boolean,
    val minutesLeft: Int?,
    /** On the charger (also when it does not charge: full, or kept below a limit to spare the battery). */
    val plugged: Boolean = charging,
)

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
class Status internal constructor(
    private val periodSeconds: Long,
    // Hands what was read to the compositor's thread (tests: at once).
    private val post: (() -> Unit) -> Unit,
) : AutoCloseable {

    constructor(caulis: Caulis, periodSeconds: Long = 2) : this(periodSeconds, { action -> caulis.post { action() } })

    /** The PATH the programs (wpctl, pactl, nmcli) are looked up in; null: newm's own (tests: their fake ones). */
    internal var toolPath: String? = null

    var battery by mutableStateOf<Battery?>(null)
        private set
    var network by mutableStateOf(Network(Network.Kind.NONE))
        private set
    var volume by mutableStateOf<Volume?>(null)
        private set

    /**
     * Why the volume cannot be read now (the sound server or its tools fail, or it restarts),
     * else null: the bar then shows the sound as unavailable instead of hiding it, and asks
     * again — soon at first, then less often (up to sound.volume_every_seconds).
     */
    var volumeUnavailable by mutableStateOf<String?>(null)
        private set

    /** The computer's address on its network (IPv4, else IPv6), if it has one. */
    var address by mutableStateOf<String?>(null)
        private set

    /** The screen's brightness (0..100), if it can be read. */
    var brightness by mutableStateOf<Int?>(null)
        private set

    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "newm-bar-status").apply { isDaemon = true } }
    private var task: ScheduledFuture<*>? = null
    private var wifiNameAt = 0L
    private var wifiName = ""
    // The volume as last read, and when (it costs a program run: wpctl or pactl).
    private var volumeAt = 0L
    // Readings in a row that failed (the next one comes sooner while it fails), and why (worker thread).
    private var volumeFailures = 0
    private var volumeProblem: String? = null
    // What went wrong with the programs run for the reading under way (worker thread).
    private val problems = ArrayList<String>()
    // The sound server says each change (pactl subscribe): no asking every few seconds.
    @Volatile
    private var follower: Process? = null
    @Volatile
    private var closed = false
    private var followThread: Thread? = null

    fun start() {
        if (task != null) return
        task = worker.scheduleWithFixedDelay({ readAll() }, 0, periodSeconds, TimeUnit.SECONDS)
        if (confFollowVolume() && confGetVolume() == null && installedHere("pactl")) followVolume()
    }

    private fun installedHere(program: String): Boolean =
        (toolPath ?: System.getenv("PATH") ?: "/usr/bin").split(':').any { java.nio.file.Files.isExecutable(java.nio.file.Path.of(it.ifEmpty { "." }, program)) }

    /** Reads again now (after the volume was changed, ...). */
    fun refreshSoon() {
        worker.execute { readAll(volumeNow = true) }
    }

    // `pactl subscribe` on a thread of its own: a change of an output (a sink) or of the server
    // (another default output) reads the volume again, once for a burst of them. When it ends —
    // the sound server (pipewire-pulse) restarted or is not up yet — it is started again: after
    // 1 s, then 2, 4, ... up to sound { volumeEverySeconds }; meanwhile the volume is asked as
    // usual. It ends with newm; when pactl is not there the volume is only asked.
    private fun followVolume() {
        followThread = Thread({
            var wait = 1L
            var times = 0
            while (!closed) {
                val began = System.nanoTime()
                val process = try {
                    dev.caulis.ffi.Processes.start(tools(ProcessBuilder("pactl", "subscribe").redirectErrorStream(true))).also { it.outputStream.close() }
                } catch (e: Exception) {
                    if (dev.caulis.ffi.Processes.isNotFound(e)) {
                        log.info { "pactl is not installed: the volume is read every ${confVolumeEvery()} s" }
                        return@Thread
                    }
                    dev.caulis.ffi.Processes.tellStartFailed(log, "pactl", e)
                    null
                }
                if (process != null) {
                    follower = process
                    if (times++ == 0) log.info { "The volume follows the sound server (pactl subscribe)" }
                    else {
                        log.info { "The volume follows the sound server again (pactl subscribe)" }
                        // (A restarted sound server: its volume may be another one now.)
                        runCatching { worker.execute { readVolumeNow() } }
                    }
                    var pending: ScheduledFuture<*>? = null
                    try {
                        process.inputStream.bufferedReader().forEachLine { line ->
                            if (closed) return@forEachLine
                            if ("on sink" in line || "on server" in line) {
                                if (pending?.isDone != false) pending = worker.schedule({ readVolumeNow() }, 100, TimeUnit.MILLISECONDS)
                            }
                        }
                    } catch (_: Exception) {
                    }
                    follower = null
                    runCatching { if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly() }
                }
                if (closed) break
                // (It ran a while: a new trouble, waited for from the start again.)
                if (System.nanoTime() - began > TimeUnit.SECONDS.toNanos(30)) wait = 1
                // At once: whether the sound is there now (unavailable while the server restarts).
                runCatching { worker.execute { readVolumeNow() } }
                if (wait == 1L) log.info { "pactl subscribe ended (the sound server restarted?): following it again in $wait s" }
                else log.trace { "pactl subscribe ended again: again in $wait s" }
                try {
                    Thread.sleep(wait * 1000)
                } catch (_: InterruptedException) {
                    break
                }
                wait = minOf(wait * 2, confVolumeEvery().coerceAtLeast(2).toLong())
            }
        }, "newm-volume-follow").apply { isDaemon = true }.also { it.start() }
    }

    // Reads the volume (a program run), noting whether it failed and why (worker thread).
    private fun readVolumeNow() {
        problems.clear()
        val v = try { readVolume() } catch (e: Exception) { problems += "$e"; null }
        volumeAt = System.nanoTime()
        val unavailable = if (v != null) {
            if (volumeFailures > 0) log.info { "The volume can be read again" }
            volumeFailures = 0
            volumeProblem = null
            null
        } else {
            val why = problems.distinct().joinToString("; ").ifEmpty { "no answer" }
            // (Nothing to ask it with — no wpctl, pactl or sound { getVolume }: nothing to show either.)
            val shown = confGetVolume() != null || installedHere("wpctl") || installedHere("pactl")
            if (volumeFailures == 0 || why != volumeProblem) {
                // (A warning, so the log says it next time: it is why the bar's sound is grey.)
                if (shown) log.warn("The volume cannot be read ($why): the sound shows as unavailable, asked again")
                else log.info { "The volume cannot be read ($why)" }
            }
            volumeFailures++
            volumeProblem = why
            if (shown) why else null
        }
        post {
            if (volume != v) volume = v
            if (volumeUnavailable != unavailable) volumeUnavailable = unavailable
        }
    }

    /** The volume as it is about to be (shown at once, before the sound server says it). */
    fun showVolume(percent: Int) {
        volume = (volume ?: Volume(percent, false)).copy(percent = percent, muted = false)
    }

    private fun readAll(volumeNow: Boolean = false) {
        val b = try { readBattery() } catch (e: Exception) { log.trace { "battery: $e" }; null }
        val n = try { readNetwork() } catch (e: Exception) { log.trace { "network: $e" }; Network(Network.Kind.NONE) }
        // The volume (a program run): when asked for, the first time, and every sound { volumeEverySeconds }
        // (rarely while the sound server says its changes itself).
        // While it fails: again after the period, then twice that, ... up to the usual time.
        val usual = confVolumeEvery().coerceAtLeast(1).toLong() * (if (follower != null) FOLLOWED_SLOWER else 1)
        val every = if (volumeFailures == 0) usual else minOf(confVolumeEvery().coerceAtLeast(1).toLong(), periodSeconds.coerceAtLeast(1) shl minOf(volumeFailures - 1, 6))
        if (volumeNow || volumeAt == 0L || System.nanoTime() - volumeAt >= every * 1_000_000_000L) readVolumeNow()
        val l = try { readBrightness() } catch (e: Exception) { null }
        val a = try { readAddress() } catch (e: Exception) { null }
        post {
            if (battery != b) battery = b
            if (network != n) network = n
            if (brightness != l) brightness = l
            if (address != a) address = a
        }
    }

    private fun read(path: Path): String? = try { Files.readString(path).trim() } catch (_: Exception) { null }

    private fun readBattery(): Battery? {
        val supplies = File("/sys/class/power_supply").listFiles().orEmpty()
        // The laptop's own (not a mouse's or headphones': their scope is "Device").
        val bat = supplies.firstOrNull { read(it.toPath().resolve("type")) == "Battery" && read(it.toPath().resolve("present")) != "0" && read(it.toPath().resolve("scope")) != "Device" } ?: return null
        val dir = bat.toPath()
        val percent = read(dir.resolve("capacity"))?.toIntOrNull() ?: return null
        val status = read(dir.resolve("status")) ?: ""
        val charging = status == "Charging"
        val full = status == "Full" || (status == "Not charging" && percent >= 95)
        // On the charger: an adapter (Mains) or a USB-C charger that is online.
        val plugged = charging || supplies.any { s ->
            val type = read(s.toPath().resolve("type"))
            (type == "Mains" || type?.startsWith("USB") == true) && read(s.toPath().resolve("online")) == "1"
        }
        // Energy (µWh) and power (µW), or charge (µAh) and current (µA).
        // (A pair of the same kind: energy with power, else charge with current — never mixed.)
        val energy = read(dir.resolve("energy_now")) != null && read(dir.resolve("power_now")) != null
        val now = read(dir.resolve(if (energy) "energy_now" else "charge_now"))?.toLongOrNull()
        val whenFull = read(dir.resolve(if (energy) "energy_full" else "charge_full"))?.toLongOrNull()
        val rate = read(dir.resolve(if (energy) "power_now" else "current_now"))?.toLongOrNull()?.let { Math.abs(it) }
        val minutes = if (now != null && rate != null && rate > 0) {
            if (charging && whenFull != null) ((whenFull - now) * 60 / rate).toInt() else if (!charging) (now * 60 / rate).toInt() else null
        } else null
        return Battery(percent.coerceIn(0, 100), charging, full, minutes?.takeIf { it in 1..(48 * 60) && (charging || !plugged) }, plugged)
    }

    private fun readNetwork(): Network {
        // Wi-Fi: its strength from /proc/net/wireless, its name from nmcli (now and then).
        val wireless = read(Path.of("/proc/net/wireless"))?.lines()?.drop(2)?.firstOrNull { it.isNotBlank() }
        if (wireless != null) {
            val quality = wireless.trim().split(Regex("\\s+")).getOrNull(2)?.trimEnd('.')?.toDoubleOrNull() ?: 0.0
            val now = System.nanoTime()
            // (Every network.wifi_name_every_seconds, also when the name came empty: never every tick.)
            if (wifiNameAt == 0L || now - wifiNameAt > confWifiNameEvery().coerceAtLeast(2) * 1_000_000_000L) {
                wifiNameAt = now
                // network { wifiName = "..." }: the configuration's command; else nmcli (what NetworkManager
                // knows already: "--rescan no", the air is never scanned for it), else iwgetid.
                val own = confWifiName()
                wifiName = if (own != null) commandOutput(own, 2)?.lines()?.firstOrNull { it.isNotBlank() }?.trim() ?: ""
                else run("nmcli", "-t", "-f", "active,ssid", "dev", "wifi", "list", "--rescan", "no")?.lines()?.firstOrNull { it.startsWith("yes:") }?.substringAfter(':')
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
        // sound { getVolume = "..." }: the configuration's command — a percent ("45%", "45") or wpctl's
        // "Volume: 0.45"; muted when it says MUTED, [off] or yes.
        confGetVolume()?.let { own ->
            val out = commandOutput(own, 2) ?: return null
            val percent = Regex("Volume:\\s*([0-9.]+)").find(out)?.groupValues?.get(1)?.toDoubleOrNull()?.let { Math.round(it * 100).toInt() }
                ?: Regex("(\\d+)").find(out)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            return Volume(percent, Regex("MUTED|\\[off]|\\byes\\b", RegexOption.IGNORE_CASE).containsMatchIn(out))
        }
        // "Volume: 0.45" or "Volume: 0.45 [MUTED]" (PipeWire's wpctl), else pactl.
        run("wpctl", "get-volume", "@DEFAULT_AUDIO_SINK@")?.let { out ->
            val level = Regex("Volume:\\s*([0-9.]+)").find(out)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@let
            return Volume(Math.round(level * 100).toInt(), "MUTED" in out)
        }
        val percent = run("pactl", "get-sink-volume", "@DEFAULT_SINK@")?.let { Regex("(\\d+)%").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: return null
        val muted = run("pactl", "get-sink-mute", "@DEFAULT_SINK@")?.contains("yes") == true
        return Volume(percent, muted)
    }

    // The first global address of an interface that is up (not loopback, not a virtual bridge).
    private fun readAddress(): String? {
        val all = java.net.NetworkInterface.networkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.name.startsWith("docker") && !it.name.startsWith("br-") && !it.name.startsWith("veth") }
            .flatMap { it.inetAddresses.toList() }
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
        return (all.firstOrNull { it is java.net.Inet4Address } ?: all.firstOrNull())?.hostAddress?.substringBefore('%')
    }

    private fun readBrightness(): Int? {
        // brightness { get = "..." }: the configuration's command, saying a percent.
        confGetBrightness()?.let { own -> return commandOutput(own, 2)?.let { Regex("(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }?.coerceIn(0, 100) }
        // brightness { device = "intel_backlight" }: that one; else the first there is.
        val dir = File("/sys/class/backlight")
        val dev = confBrightnessDevice()?.takeIf { it.isNotBlank() }?.let { File(dir, it) }?.takeIf { it.isDirectory } ?: dir.listFiles()?.firstOrNull() ?: return null
        val now = read(dev.toPath().resolve("brightness"))?.toDoubleOrNull() ?: return null
        val max = read(dev.toPath().resolve("max_brightness"))?.toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        return Math.round(now / max * 100).toInt()
    }

    private fun tools(builder: ProcessBuilder): ProcessBuilder = builder.also { b -> toolPath?.let { b.environment()["PATH"] = it } }

    // A program's output (null if it is not there or fails — said in problems), waiting at most two seconds.
    private fun run(vararg command: String): String? = try {
        val p = dev.caulis.ffi.Processes.start(tools(ProcessBuilder(*command).redirectErrorStream(true)))
        p.outputStream.close()
        val out = outputOf(p, 2)
        if (out == null) {
            p.destroyForcibly()
            problems += "${command.first()} did not answer within 2 s"
            null
        } else if (p.waitFor() != 0) {
            problems += "${command.first()} ended with ${p.exitValue()}" + (out.lineSequence().firstOrNull { it.isNotBlank() }?.let { ": ${it.trim().take(120)}" } ?: "")
            null
        } else out
    } catch (e: Exception) {
        problems += if (dev.caulis.ffi.Processes.isNotFound(e)) "${command.first()} is not installed" else "${command.first()} could not be started: ${dev.caulis.ffi.Processes.reason(e)}"
        null
    }

    override fun close() {
        closed = true
        task?.cancel(false)
        worker.shutdownNow()
        follower?.destroy()
        followThread?.interrupt()
    }

    private companion object {
        // While pactl says each change, the volume is still asked now and then (a change it missed), this much rarer.
        const val FOLLOWED_SLOWER = 6
    }
}
