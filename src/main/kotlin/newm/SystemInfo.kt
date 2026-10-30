package newm

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * What a bar shows, read from the system (/proc, /sys; nmcli for the Wi-Fi's name): each
 * function gives a short text, empty when there is nothing to say (no battery, no Wi-Fi).
 *
 * ```kotlin
 * panels { topBar { texts { listOf(wifi(), cpu(), memory(), temperature(), battery(), date(), time()) } } }
 * ```
 */
object SystemInfo {

    private fun read(path: String): String? = try {
        Files.readString(Path.of(path)).trim()
    } catch (_: Exception) {
        null
    }

    // The CPU's busy and total times at the last look (the use is between two looks).
    private var lastCpu: Pair<Long, Long>? = null

    /** How busy the processor is: "14%". */
    fun cpu(): String {
        val parts = read("/proc/stat")?.lineSequence()?.firstOrNull()?.split(Regex("\\s+"))?.drop(1)?.mapNotNull { it.toLongOrNull() } ?: return ""
        if (parts.size < 4) return ""
        val idle = parts[3] + (parts.getOrNull(4) ?: 0)
        val total = parts.sum()
        val last = lastCpu
        lastCpu = (total - idle) to total
        if (last == null || total == last.second) return ""
        return "${(100 * ((total - idle) - last.first) / (total - last.second)).coerceIn(0, 100)}%"
    }

    /** The memory in use: "6% (0.35)" as newm's own bar had it is "6%"; here "3.1G". */
    fun memory(): String {
        val info = read("/proc/meminfo")?.lines()?.associate { line ->
            line.substringBefore(':') to (line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L)
        } ?: return ""
        val total = info["MemTotal"] ?: return ""
        val available = info["MemAvailable"] ?: return ""
        return "%.1fG".format((total - available) / 1024.0 / 1024.0)
    }

    /** The warmest of the thermal zones: "39°C". */
    fun temperature(): String {
        val zones = java.io.File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }.orEmpty()
        val milli = zones.mapNotNull { read("${it.path}/temp")?.toLongOrNull() }.maxOrNull() ?: return ""
        return "${milli / 1000}°C"
    }

    /** The battery: "51%", "51% +" while charging; empty without one. */
    fun battery(): String {
        val battery = java.io.File("/sys/class/power_supply").listFiles { f -> f.name.startsWith("BAT") }?.firstOrNull() ?: return ""
        val capacity = read("${battery.path}/capacity") ?: return ""
        val charging = read("${battery.path}/status") == "Charging"
        return "$capacity%${if (charging) " +" else ""}"
    }

    // The Wi-Fi's name, asked of nmcli now and then (not every second), on a thread of its own:
    // the bar's texts are made on the compositor's thread, and nmcli may take seconds
    // (NetworkManager busy; a plain "dev wifi" even scanned first).
    @Volatile
    private var wifiName: String = ""
    private var wifiAsked = 0L
    private val wifiAsking = java.util.concurrent.atomic.AtomicBoolean(false)

    // How the name is asked (tests: a slow one).
    internal var wifiCommand = listOf("nmcli", "-t", "-f", "active,ssid", "dev", "wifi", "list", "--rescan", "no")

    /** The Wi-Fi: "JVB (93%)"; empty without one. */
    fun wifi(): String {
        val now = System.nanoTime()
        if (now - wifiAsked > 10_000_000_000L && wifiAsking.compareAndSet(false, true)) {
            wifiAsked = now
            Thread.ofPlatform().daemon().name("newm-wifi-name").start {
                try {
                    askWifiName()?.let { wifiName = it }
                } finally {
                    wifiAsking.set(false)
                }
            }
        }
        val quality = read("/proc/net/wireless")?.lines()?.drop(2)?.firstOrNull()?.trim()?.split(Regex("\\s+"))?.getOrNull(2)?.trimEnd('.')?.toDoubleOrNull()
        val percent = quality?.let { " (${(it / 70 * 100).toInt().coerceIn(0, 100)}%)" }.orEmpty()
        return if (wifiName.isEmpty() && quality == null) "" else "${wifiName.ifEmpty { "Wi-Fi" }}$percent"
    }

    // The active Wi-Fi's name as nmcli tells it ("" none), or null when it did not answer in time.
    private fun askWifiName(): String? = try {
        val p = dev.caulis.ffi.Processes.start(ProcessBuilder(wifiCommand).redirectErrorStream(true))
        val lines = java.util.concurrent.CompletableFuture.supplyAsync { p.inputStream.bufferedReader().readLines() }
        if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly()
            null
        } else {
            lines.get(1, java.util.concurrent.TimeUnit.SECONDS).firstOrNull { it.startsWith("yes:") }?.substringAfter(':').orEmpty()
        }
    } catch (_: Exception) {
        ""
    }

    /** The date: "21 May 2022" (a pattern of java.time's DateTimeFormatter for another look). */
    fun date(pattern: String = "d MMM yyyy"): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern))

    /** The time: "23:31:34". */
    fun time(pattern: String = "HH:mm:ss"): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern))

    /** The user's name. */
    fun user(): String = System.getProperty("user.name")
}

/** The system's texts by name, for config.json's and config.lua's bars. */
val SYSTEM_TEXTS: Map<String, () -> String> = mapOf(
    "wifi" to SystemInfo::wifi, "cpu" to SystemInfo::cpu, "memory" to SystemInfo::memory, "temperature" to SystemInfo::temperature,
    "battery" to SystemInfo::battery, "date" to { SystemInfo.date() }, "time" to { SystemInfo.time() }, "user" to SystemInfo::user,
)
