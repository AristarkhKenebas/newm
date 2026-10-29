package newm

import dev.caulis.log.Logger

private val log = Logger("helpers")

/** newm's helper/execute.py: runs [command] in a shell and gives back what it printed. */
fun execute(command: String): String = try {
    val process = dev.caulis.ffi.Processes.start(ProcessBuilder("/bin/sh", "-c", command).redirectErrorStream(false))
    process.inputStream.bufferedReader().readText().also { process.waitFor() }
} catch (e: java.io.IOException) {
    log.warn("\"$command\" could not run: $e")
    ""
}

// A command in the background (os.system("... &")).
private fun background(command: String) {
    try {
        dev.caulis.ffi.Processes.start(ProcessBuilder("/bin/sh", "-c", command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD))
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
    // Its glide's time in seconds (null: the animation "brightness", anim_time usually; off: at once).
    private val animTime: Double? = null,
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

    private fun glideSeconds(): Double = (animTime?.let { NewmAnimations.resolveSeconds("brightness", it) } ?: NewmAnimations["brightness"]).seconds

    fun update() {
        if (!enabled || animTs.first < 0.0) return
        val t = now()
        if (t - animTs.third > 1.0 / 30.0) animTs = Triple(animTs.first, animTs.second, t) else return
        if (t >= animTs.second) {
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
            animTs = Triple(t, t + glideSeconds(), 0.0)
            barDisplay?.display(n.toDouble() / max)
        }
        next = n
    }

    fun get(): Double = predim.toDouble() / max

    fun set(value: Double) {
        predim = maxOf(0, minOf(max, (max * value).toInt()))
        next = predim
        animTs = Triple(now(), now() + glideSeconds(), 0.0)
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

/** [path] with a leading "~" or "~/" as the home folder ("a~b" and "~user" stay as they are). */
internal fun expandHome(path: String): String {
    val home = System.getenv("HOME") ?: System.getProperty("user.home")
    return when {
        path == "~" -> home
        path.startsWith("~/") -> home + path.substring(1)
        else -> path
    }
}

/**
 * The lock screen shows (or is coming, or going), or a locker of one's own (ext-session-lock)
 * locked the session: what is newm's own on top (the launcher, the clipboard's history) is not
 * drawn then, nor clickable.
 */
internal fun Layout.lockShown(): Boolean = layoutDown.lockPerc > 0.0 || state.lockPerc > 0.0 || caulis.desktop.locked

/** [dir] made if needed (with its parents) and kept for its user only (0700: clipboard pins, logs). */
internal fun privateFolder(dir: java.nio.file.Path): java.nio.file.Path {
    val owner = java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")
    java.nio.file.Files.createDirectories(dir, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(owner))
    runCatching { java.nio.file.Files.setPosixFilePermissions(dir, owner) }
    return dir
}

/**
 * Writes [text] to [file], readable by its user only (0600): what may hold secrets. Whole or not
 * at all: written to a new file beside it (made 0600 at once), which then takes its place — newm
 * ending halfway leaves the old one as it was.
 */
internal fun writePrivately(file: java.nio.file.Path, text: String) {
    val owner = java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")
    val dir = file.toAbsolutePath().parent
    val temp = java.nio.file.Files.createTempFile(dir, ".${file.fileName}.", ".new", java.nio.file.attribute.PosixFilePermissions.asFileAttribute(owner))
    try {
        java.nio.file.Files.writeString(temp, text)
        try {
            java.nio.file.Files.move(temp, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        java.nio.file.Files.deleteIfExists(temp)
    }
}
