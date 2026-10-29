package newm.bar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.caulis.log.Logger
import dev.caulis.wm.Caulis
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val log = Logger("sound")

/** A sound device: a speaker or headphones (an output), or a microphone (an input). */
data class SoundDevice(val name: String, val description: String, val volume: Int, val muted: Boolean, val isDefault: Boolean)

/**
 * The sound's outputs and inputs, from the bar: their volumes, muting, and which is used —
 * through pactl (PulseAudio, and PipeWire's pulse side), on a thread of its own.
 */
class Sound(private val caulis: Caulis) {

    var outputs by mutableStateOf<List<SoundDevice>>(emptyList())
        private set
    var inputs by mutableStateOf<List<SoundDevice>>(emptyList())
        private set

    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "newm-sound").apply { isDaemon = true } }

    /** Reads the devices again (on the worker; the state follows on the compositor's thread). */
    fun refresh() = worker.execute {
        val defaultSink = run("pactl", "get-default-sink")?.trim()
        val defaultSource = run("pactl", "get-default-source")?.trim()
        val out = devices("sinks", defaultSink)
        val ins = devices("sources", defaultSource).filter { !it.name.endsWith(".monitor") }
        caulis.post {
            if (out != outputs) outputs = out
            if (ins != inputs) inputs = ins
        }
    }

    // `pactl -f json list sinks|sources`: names, descriptions, volume (the channels' average) and mute.
    private fun devices(kind: String, default: String?): List<SoundDevice> {
        val json = run("pactl", "-f", "json", "list", kind) ?: return emptyList()
        return try {
            newm.Json.parseArray(json).mapNotNull { it as? Map<*, *> }.map { d ->
                val name = d["name"].toString()
                val channels = (d["volume"] as? Map<*, *>)?.values?.mapNotNull { (it as? Map<*, *>)?.get("value_percent")?.toString()?.trimEnd('%')?.trim()?.toIntOrNull() }.orEmpty()
                SoundDevice(name, d["description"]?.toString() ?: name, if (channels.isEmpty()) 0 else channels.sum() / channels.size, d["mute"] == true, name == default)
            }
        } catch (e: Exception) {
            log.warn("pactl's list of $kind cannot be read: $e")
            emptyList()
        }
    }

    // Shown at once as it is about to be (pactl and the reading after it take a moment: the
    // popup waited for them, as if it was not listening).
    private fun change(output: Boolean, edit: (SoundDevice) -> SoundDevice) {
        if (output) outputs = outputs.map(edit) else inputs = inputs.map(edit)
    }

    /** Sound now goes to (or comes from) [device]. */
    fun use(device: SoundDevice, output: Boolean) {
        change(output) { it.copy(isDefault = it.name == device.name) }
        act("pactl", if (output) "set-default-sink" else "set-default-source", device.name)
    }

    fun setVolume(device: SoundDevice, percent: Int, output: Boolean) {
        val p = percent.coerceIn(0, 150)
        change(output) { if (it.name == device.name) it.copy(volume = p, muted = false) else it }
        // (While dragging only the last one counts.)
        val serial = ++volumeSerial
        worker.execute {
            if (serial != volumeSerial) return@execute
            run("pactl", if (output) "set-sink-volume" else "set-source-volume", device.name, "$p%")
            if (output) run("pactl", "set-sink-mute", device.name, "0")
            refreshLater()
        }
    }

    @Volatile private var volumeSerial = 0

    fun toggleMute(device: SoundDevice, output: Boolean) {
        change(output) { if (it.name == device.name) it.copy(muted = !it.muted) else it }
        act("pactl", if (output) "set-sink-mute" else "set-source-mute", device.name, "toggle")
    }

    private fun act(vararg command: String) = worker.execute {
        run(*command)
        log.trace { command.joinToString(" ") }
        refreshLater()
    }

    // What it changed read back, once for many changes in a row.
    @Volatile private var refreshQueued = false

    private fun refreshLater() {
        if (refreshQueued) return
        refreshQueued = true
        worker.schedule({
            refreshQueued = false
            refresh()
        }, 300, TimeUnit.MILLISECONDS)
    }

    // The command's output, or null when it failed or took over three seconds (then it is killed:
    // a hung pactl must not hold the worker). The output goes to a file meanwhile, so waiting has a real end.
    private fun run(vararg command: String): String? {
        val out = try {
            java.nio.file.Files.createTempFile("newm-sound", ".out")
        } catch (e: Exception) {
            log.warn("No room for pactl's answer: $e")
            return null
        }
        return try {
            val p = dev.caulis.ffi.Processes.start(ProcessBuilder(*command).redirectErrorStream(true).redirectOutput(out.toFile()))
            p.outputStream.close()
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                log.warn("${command.first()} took too long and was stopped")
                null
            } else java.nio.file.Files.readString(out).takeIf { p.exitValue() == 0 }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { java.nio.file.Files.deleteIfExists(out) }
        }
    }
}
