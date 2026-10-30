package newm.bar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.caulis.core.ScreenShader
import dev.caulis.log.Logger
import java.nio.file.Files
import java.nio.file.Path

private val log = Logger("quick")

/** Whether every radio of rfkill's [dir] (/sys/class/rfkill) is blocked by software: airplane mode (false without radios). */
internal fun rfkillAllBlocked(dir: Path): Boolean {
    if (!Files.isDirectory(dir)) return false
    val soft = Files.list(dir).use { s -> s.toList() }.mapNotNull { d -> runCatching { Files.readString(d.resolve("soft")).trim() }.getOrNull() }
    return soft.isNotEmpty() && soft.all { it == "1" }
}

/**
 * The quick settings' tiles that keep a state of their own: the night light (the screen
 * warmer through Caulis' screen shader, and back to the one before), airplane mode (rfkill's
 * state, read; switched by a command of the configuration) and a screen recording (a command
 * of the configuration, running until the second click). Runs on the compositor's thread.
 */
class QuickExtras(private val wm: newm.Layout) {

    var nightLight by mutableStateOf(false)
        private set
    var airplane by mutableStateOf(false)
        private set
    var recording by mutableStateOf(false)
        private set

    private var shaderBefore: ScreenShader? = null
    private var recorder: Process? = null

    /** The night light on (quickSettings { nightLight } warm) or off (the shader before it back). A reload of the configuration sets its own shader again. */
    fun toggleNightLight() {
        val caulis = wm.caulis
        if (!nightLight) {
            shaderBefore = caulis.screenShader
            caulis.screenShader = ScreenShader.warm(confNightLight().toFloat().coerceIn(0f, 1f))
        } else {
            caulis.screenShader = shaderBefore
            shaderBefore = null
        }
        nightLight = !nightLight
        log.info { "Night light ${if (nightLight) "on" else "off"}" }
    }

    /** Reads whether all radios are blocked (away from the compositor's thread). */
    fun readAirplane() = inBackground {
        val on = rfkillAllBlocked(Path.of("/sys/class/rfkill"))
        wm.caulis.post { airplane = on }
    }

    /** Runs quickSettings { airplaneCommand } (nothing when it is ""), then reads the state again. */
    fun toggleAirplane() {
        val command = confAirplaneCommand().trim()
        if (command.isEmpty()) return log.info { "Airplane mode: no command (quickSettings { airplaneCommand })" }
        log.info { "Airplane mode: switching" }
        inBackground {
            commandOutput(command, 10, needSuccess = false)
            readAirplane()
        }
    }

    /** Starts quickSettings { recordCommand }, or ends the recording it started (SIGTERM, as wf-recorder wants to stop). */
    fun toggleRecording() {
        val running = recorder
        if (running != null && running.isAlive) {
            log.info { "Screen recording: stopping" }
            running.destroy()
            return
        }
        val command = confRecordCommand().trim()
        if (command.isEmpty()) return log.info { "Screen recording: no command (quickSettings { recordCommand })" }
        // (exec: the recorder itself gets the signal, not a shell around it.)
        val process = wm.caulis.spawn(listOf("/bin/sh", "-c", "exec $command")) ?: return log.warn("The screen recording could not start")
        log.info { "Screen recording: started" }
        recorder = process
        recording = true
        process.onExit().thenRun {
            wm.caulis.post {
                if (recorder === process) {
                    recorder = null
                    recording = false
                    log.info { "Screen recording: ended (${process.exitValue()})" }
                }
            }
        }
    }
}
