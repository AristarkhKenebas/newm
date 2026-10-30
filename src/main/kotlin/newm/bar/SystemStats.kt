package newm.bar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.caulis.log.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private val log = Logger("bar")

/** The computer's load: the processor's use (0..100), the memory's (used and total, KiB) and a temperature (°C), each null when unknown. */
data class SystemLoad(val cpuPercent: Int?, val memoryUsedKib: Long?, val memoryTotalKib: Long?, val celsius: Int?) {
    val memoryPercent: Int? get() = if (memoryUsedKib != null && memoryTotalKib != null && memoryTotalKib > 0) (memoryUsedKib * 100 / memoryTotalKib).toInt() else null
}

/**
 * Reads the processor's use (/proc/stat), the memory (/proc/meminfo) and a temperature
 * (/sys/class/hwmon, /sys/class/thermal) every [periodSeconds] on a thread of its own — only
 * while a bar shows "cpu", "memory" or "temperature" ([start] when one is shown, [stop] when
 * none is) — and sets [load] on the compositor's thread through [post].
 */
class SystemStats internal constructor(
    private val post: (() -> Unit) -> Unit,
    private val root: Path = Path.of("/"),
) : AutoCloseable {

    var load by mutableStateOf(SystemLoad(null, null, null, null))
        private set

    /** The sensor read (its hwmon or thermal zone name: "k10temp", "coretemp", "x86_pkg_temp"); empty: the first of those known. */
    var sensor: String = ""

    private var worker: java.util.concurrent.ScheduledExecutorService? = null
    private var task: ScheduledFuture<*>? = null
    private var lastIdle = -1L
    private var lastTotal = -1L

    /** Starts reading every [periodSeconds] (again with a new period). */
    fun start(periodSeconds: Long) {
        stop()
        val w = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "newm-bar-system").apply { isDaemon = true } }
        worker = w
        log.info { "Reading the processor, memory and temperature every $periodSeconds s" }
        task = w.scheduleWithFixedDelay({
            val now = try { read() } catch (e: Exception) { log.trace { "System load: $e" }; return@scheduleWithFixedDelay }
            post { load = now }
        }, 0, periodSeconds.coerceAtLeast(1), TimeUnit.SECONDS)
    }

    /** Stops reading (and lets its thread go). */
    fun stop() {
        task?.cancel(false)
        task = null
        worker?.shutdownNow()
        worker = null
        lastIdle = -1L
        lastTotal = -1L
    }

    val running: Boolean get() = task != null

    override fun close() = stop()

    // One reading (the processor's use since the one before: the first is null).
    internal fun read(): SystemLoad {
        val cpu = runCatching { Files.readAllLines(root.resolve("proc/stat")).firstOrNull { it.startsWith("cpu ") } }.getOrNull()?.let { line ->
            val (idle, total) = cpuTimes(line) ?: return@let null
            val percent = if (lastTotal >= 0 && total > lastTotal) (100 - (idle - lastIdle) * 100 / (total - lastTotal)).toInt().coerceIn(0, 100) else null
            lastIdle = idle
            lastTotal = total
            percent
        }
        val (used, total) = runCatching { memory(Files.readString(root.resolve("proc/meminfo"))) }.getOrNull() ?: (null to null)
        return SystemLoad(cpu, used, total, runCatching { temperature() }.getOrNull())
    }

    // The sensor's temperature: hwmon's temp1 of a known processor sensor, else a thermal zone.
    private fun temperature(): Int? {
        val wanted = sensor.trim().lowercase()
        val known = listOf("k10temp", "coretemp", "zenpower", "cpu_thermal", "x86_pkg_temp", "acpitz")
        val hwmon = root.resolve("sys/class/hwmon")
        val candidates = ArrayList<Pair<String, Path>>()
        if (Files.isDirectory(hwmon)) Files.list(hwmon).use { dirs ->
            for (d in dirs.toList()) {
                val name = runCatching { Files.readString(d.resolve("name")).trim() }.getOrNull() ?: continue
                val input = d.resolve("temp1_input")
                if (Files.exists(input)) candidates += name to input
            }
        }
        val thermal = root.resolve("sys/class/thermal")
        if (Files.isDirectory(thermal)) Files.list(thermal).use { dirs ->
            for (d in dirs.toList().filter { it.fileName.toString().startsWith("thermal_zone") }) {
                val type = runCatching { Files.readString(d.resolve("type")).trim() }.getOrNull() ?: continue
                candidates += type to d.resolve("temp")
            }
        }
        val chosen = if (wanted.isNotEmpty()) candidates.firstOrNull { it.first.lowercase() == wanted }
        else known.firstNotNullOfOrNull { k -> candidates.firstOrNull { it.first == k } } ?: candidates.firstOrNull()
        val milli = chosen?.let { runCatching { Files.readString(it.second).trim().toLong() }.getOrNull() } ?: return null
        return (milli / 1000).toInt()
    }

    companion object {
        /** The idle and total times of /proc/stat's "cpu" line, or null. */
        fun cpuTimes(line: String): Pair<Long, Long>? {
            val n = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
            if (n.size < 4) return null
            // idle + iowait are idle; guest times are in user and nice already.
            val idle = n[3] + (n.getOrNull(4) ?: 0)
            val total = n.take(8).sum()
            return idle to total
        }

        /** The memory used (MemTotal less MemAvailable) and the total, KiB, of /proc/meminfo's text. */
        fun memory(meminfo: String): Pair<Long, Long>? {
            fun field(name: String) = Regex("""(?m)^$name:\s+(\d+)""").find(meminfo)?.groupValues?.get(1)?.toLongOrNull()
            val total = field("MemTotal") ?: return null
            val available = field("MemAvailable") ?: return null
            return (total - available).coerceAtLeast(0) to total
        }
    }
}
