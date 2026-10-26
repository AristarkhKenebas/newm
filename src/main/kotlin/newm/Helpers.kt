package newm

import dev.caulis.log.Logger

private val log = Logger("helpers")

/** newm's helper/execute.py: runs [command] in a shell and gives back what it printed. */
fun execute(command: String): String = try {
    val process = ProcessBuilder("/bin/sh", "-c", command).redirectErrorStream(false).start()
    process.inputStream.bufferedReader().readText().also { process.waitFor() }
} catch (e: java.io.IOException) {
    log.warn("\"$command\" could not run: $e")
    ""
}

// A command in the background (os.system("... &")).
private fun background(command: String) {
    try {
        ProcessBuilder("/bin/sh", "-c", command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    } catch (e: java.io.IOException) {
        log.warn("\"$command\" could not run: $e")
    }
}

/** newm's helper/bar_display.py: shows a value 0..1 (a volume, a brightness). */
fun interface BarDisplay {
    fun display(value: Double)
}

/** newm's WobRunner: the value shown by wob (started inside the compositor, restarted if it quit). */
class WobRunner(private val command: String = "wob", private val spawn: (List<String>) -> Process?) : BarDisplay {
    private var wob: Process? = null

    override fun display(value: Double) {
        if (wob?.isAlive != true) {
            log.trace { "Restarting wob..." }
            wob = spawn(command.split(" "))
        }
        val w = wob ?: return
        try {
            w.outputStream.write("${pyRound(value * 100)}\n".toByteArray())
            w.outputStream.flush()
        } catch (e: java.io.IOException) {
            log.warn("wob does not listen: $e")
        }
    }
}

/**
 * newm's helper/backlight_manager.py: the screen's (or keyboard's) brightness with
 * brightnessctl, gliding to a new value, dimmed when idle or locked and back when active.
 */
open class BacklightManager(
    private val args: String = "",
    private val dimFactors: Pair<Double, Double> = 0.5 to 0.33,
    private val animTime: Double = 0.3,
    private val barDisplay: BarDisplay? = null,
) {
    private var current = 0
    private var max = 1
    private var enabled = true
    private var predim: Int
    private var next: Int
    // (Start, end, last step; -1: no glide.)
    private var animTs = Triple(-1.0, -1.0, -1.0)

    init {
        try {
            current = getCurrent()
            max = getMax()
        } catch (e: Exception) {
            log.warn("Disabling BacklightManager: $e")
            enabled = false
        }
        predim = current
        next = current
    }

    fun update() {
        if (!enabled || animTs.first < 0.0) return
        val t = now()
        if (t - animTs.third > 1.0 / 30.0) animTs = Triple(animTs.first, animTs.second, t) else return
        if (t > animTs.second) {
            current = next
            animTs = Triple(-1.0, -1.0, -1.0)
        } else {
            current = pyRound(current + (next - current) / (animTs.second - animTs.first) * (t - animTs.first))
        }
        write(current)
    }

    fun callback(code: String) {
        if (code == "sleep") {
            current = 1 // (at zero, systemd would resume with 100 %)
            next = 1
            write(current)
            return
        }
        var n = next
        when (code) {
            "wakeup" -> n = predim
            "lock", "idle-lock" -> n = pyRound(predim * dimFactors.first)
            "idle" -> n = pyRound(predim * dimFactors.second)
            "idle-presuspend" -> n = 0
            "active" -> n = predim
        }
        if (kotlin.math.abs(n - next) > 0.5 && animTs.first < 0) {
            val t = now()
            animTs = Triple(t, t + animTime, 0.0)
            barDisplay?.display(n.toDouble() / max)
        }
        next = n
    }

    fun get(): Double = predim.toDouble() / max

    fun set(value: Double) {
        predim = maxOf(0, minOf(max, (max * value).toInt()))
        next = predim
        animTs = Triple(now(), now() + animTime, 0.0)
        barDisplay?.display(next.toDouble() / max)
    }

    // Override these for another command or device.
    protected open fun getMax(): Int = execute("brightnessctl $args m").trim().toInt()

    protected open fun getCurrent(): Int = execute("brightnessctl $args g").trim().toInt()

    protected open fun write(value: Int) = background("brightnessctl $args s $current > /dev/null")
}

/** newm's helper/pactl.py: the volume of a sink with pactl, shown on [barDisplay]. */
class PaCtl(private val sink: Int = 0, private val barDisplay: BarDisplay? = null) {
    private val matcher = Regex(".*?(\\d+)%.*")

    fun mute() {
        background("pactl set-sink-mute $sink toggle")
        barDisplay?.display(0.0)
    }

    fun volumeAdj(perc: Int) {
        background("pactl set-sink-volume $sink ${if (perc >= 0) "+" else ""}$perc%")
        val display = barDisplay ?: return
        val vol = execute("pactl list sinks | grep '^[[:space:]]Volume:' | head -n $(( $sink + 1 )) | tail -n 1")
        matcher.find(vol)?.let { display.display(it.groupValues[1].toDouble() / 100.0) }
    }
}
