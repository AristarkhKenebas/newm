package newm.bar

import dev.caulis.log.Logger
import newm.Layout
import newm.LuaCallable
import newm.configuredValue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val log = Logger("bar")

// --- what the configuration runs ------------------------------------------------------------------

// Actions written as text ("move 0 -1", "launch foot"), parsed once each (null: not an action, told once).
private val parsedActions = HashMap<String, ((Layout) -> Unit)?>()

/**
 * What a setting runs, as a function of the layout: a Kotlin lambda (`Layout.() -> Unit`), a
 * function of config.lua, or an action as config.json writes it ("move 0 -1", "launch foot",
 * "volume mute": parsed once, by DataConfig). null: nothing (also for false, or what is not an
 * action: told in the log).
 */
@Suppress("UNCHECKED_CAST")
internal fun actionOf(value: Any?, wm: Layout, where: String): ((Layout) -> Unit)? = when (value) {
    null, false -> null
    is LuaCallable -> { _ -> value() }
    is Function1<*, *> -> value as (Layout) -> Unit
    is Function0<*> -> { _ -> value() }
    is String -> parsedActions.getOrPut(value) {
        val problems = ArrayList<String>()
        newm.DataConfig.actionOf(value, wm.caulis, where, problems).also { problems.forEach { p -> log.warn(p) } }
    }
    else -> null.also { log.warn("$where: an action is a function or a text (\"move -1 0\"), not $value") }
}

/** Runs [action] (see [actionOf]); a failure is told in the log, never thrown. */
internal fun runAction(action: Any?, wm: Layout, where: String) {
    val run = actionOf(action, wm, where) ?: return
    log.trace { "$where: run" }
    try {
        run(wm)
    } catch (e: Exception) {
        log.warn("$where failed: $e")
    }
}

/**
 * Runs a hook of one value ([value]: `onNotification { n -> }` in config.kts; a function of
 * config.lua gets [forLua] instead, a table of it); a failure is told in the log.
 */
@Suppress("UNCHECKED_CAST")
internal fun runHook(hook: Any?, value: Any?, forLua: () -> Any?, where: String): Any? = try {
    when (hook) {
        null -> null
        is LuaCallable -> hook(forLua())
        is Function1<*, *> -> (hook as (Any?) -> Any?)(value)
        is Function0<*> -> hook()
        else -> null.also { log.warn("$where: a function is wanted, not $hook") }
    }
} catch (e: Exception) {
    log.warn("$where failed: $e")
    null
}

/**
 * Runs a power entry's or a backend's [what]: a command (a text, run as in a shell) or an
 * action (a function); false when there is nothing to run.
 */
internal fun runCommandOrAction(what: Any?, wm: Layout, where: String): Boolean = when (what) {
    null, false -> false
    is String -> true.also { log.info { "$where: $what" }; wm.caulis.launch(what) }
    else -> true.also { runAction(what, wm, where) }
}

// Commands of the configuration (a module's text, a backend): on threads of their own, never the compositor's.
private val commands = Executors.newCachedThreadPool { r -> Thread(r, "newm-bar-command").apply { isDaemon = true } }

/** Runs [task] away from the compositor's thread (a configured command, which may take a while). */
internal fun inBackground(task: () -> Unit) {
    commands.execute { runCatching(task).onFailure { log.warn("A command of the configuration failed: $it") } }
}

/**
 * [command]'s output, run by sh (a pipe and the like work); null when it failed or did not end
 * within [seconds] (then it is stopped, with what it started). Not on the compositor's thread: it waits.
 */
internal fun commandOutput(command: String, seconds: Long = 5, needSuccess: Boolean = true): String? = try {
    val p = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
    p.outputStream.close()
    val out = outputOf(p, seconds)
    if (out == null) {
        p.descendants().forEach { it.destroyForcibly() }
        p.destroyForcibly()
        log.warn("\"$command\" took longer than $seconds s and was stopped")
        null
    } else if (needSuccess && p.exitValue() != 0) {
        log.trace { "\"$command\" ended with ${p.exitValue()}" }
        null
    } else out
} catch (e: Exception) {
    log.trace { "\"$command\" cannot run: $e" }
    null
}

// The most of a command's output read (bytes); the rest is not.
private const val MAX_OUTPUT = 1_000_000

/**
 * What [p] writes until it ends (null: it did not within [seconds]). Read as it comes, never
 * waiting on the pipe itself: a program it started in the background ("cmd &") may keep the
 * pipe open long after, and a thread waiting there would wait for ever (one more each time).
 */
internal fun outputOf(p: Process, seconds: Long): String? {
    val input = p.inputStream
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
    try {
        while (true) {
            val ready = input.available()
            if (ready > 0) {
                val n = input.read(buffer, 0, minOf(ready, buffer.size))
                if (n < 0) break
                if (out.size() < MAX_OUTPUT) out.write(buffer, 0, minOf(n, MAX_OUTPUT - out.size()))
                continue
            }
            // (Ended, and all it wrote before it ended read.)
            if (!p.isAlive && input.available() == 0) break
            if (System.nanoTime() > deadline) return null
            Thread.sleep(10)
        }
    } finally {
        runCatching { input.close() }
    }
    return out.toString(Charsets.UTF_8)
}

/** [command] run by sh, its output thrown away: true when it succeeded within [seconds]. */
internal fun commandSucceeds(command: String, seconds: Long = 3): Boolean = commandOutput(command, seconds) != null

/** [template] with {name} replaced by each value ("brightnessctl -q set {percent}%"). */
internal fun fillIn(template: String, vararg values: Pair<String, Any>): String =
    values.fold(template) { text, (name, value) -> text.replace("{$name}", value.toString()) }

/** A program is installed (on the PATH). */
internal fun installed(program: String): Boolean =
    (System.getenv("PATH") ?: "/usr/bin").split(':').any { java.nio.file.Files.isExecutable(java.nio.file.Path.of(it, program)) }

// --- the settings of the bar's popups and what they do --------------------------------------------

// power { entries; confirm; lock; suspend; hibernate; logout; reboot; poweroff }: the power menu.
internal val confPowerEntries = configuredValue("power.entries", listOf("lock", "suspend", "hibernate", "logout", "reboot", "poweroff"))
internal val confPowerConfirm = configuredValue("power.confirm", listOf("logout", "reboot", "poweroff"))
private val confPowerAction = listOf("lock", "suspend", "hibernate", "logout", "reboot", "poweroff").associateWith { configuredValue<Any?>("power.$it", null) }
private val confSuspendCommand = configuredValue("energy.suspend_command", "systemctl suspend")

/**
 * What the power menu's [entry] does: the configuration's command or action, else newm's —
 * lock the screen, end the session, energy.suspend_command, and systemctl (loginctl where
 * there is no systemctl) for the rest.
 */
internal fun runPowerEntry(entry: String, wm: Layout) {
    log.info { "Power menu: $entry" }
    if (runCommandOrAction(confPowerAction[entry]?.invoke(), wm, "power.$entry")) return
    val manager = if (installed("systemctl") || !installed("loginctl")) "systemctl" else "loginctl"
    when (entry) {
        "lock" -> wm.ensureLocked()
        "logout" -> wm.terminate()
        "suspend" -> wm.caulis.launch(confSuspendCommand())
        "hibernate", "reboot", "poweroff" -> wm.caulis.launch("$manager $entry")
        else -> log.warn("power.entries: no entry \"$entry\" (lock, suspend, hibernate, logout, reboot, poweroff)")
    }
}

// mouse { barWheelLines = 1.0 }: lines of the wheel that make one step on the bar (the wall, the layout, the brightness, the tray, modules).
internal val confBarWheelLines = configuredValue<Number>("mouse.bar_wheel_lines", 1.0)

// sound { wheelStep = 2; setVolume; getVolume }: the volume from the bar.
internal val confVolumeStep = configuredValue("sound.wheel_step", 2)
internal val confSetVolume = configuredValue<String?>("sound.set_volume", null)
internal val confGetVolume = configuredValue<String?>("sound.get_volume", null)
// sound { volumeEverySeconds = 10; followVolume = true }: how often the volume is asked (a program
// run each time), and whether the sound server's own news of changes is followed (pactl subscribe).
internal val confVolumeEvery = configuredValue("sound.volume_every_seconds", 10)
internal val confFollowVolume = configuredValue("sound.follow_volume", true)

// brightness { wheelStep = 5; minimum = 1; device; set; get }: the screen's brightness from the bar.
internal val confBrightnessStep = configuredValue("brightness.wheel_step", 5)
internal val confBrightnessMinimum = configuredValue("brightness.minimum", 1)
internal val confBrightnessDevice = configuredValue<String?>("brightness.device", null)
internal val confSetBrightness = configuredValue<String?>("brightness.set", null)
internal val confGetBrightness = configuredValue<String?>("brightness.get", null)

/** The lowest brightness the bar sets (percent, 0..100). */
internal fun brightnessMinimum(): Int = confBrightnessMinimum().coerceIn(0, 100)

// network { wifiName }: a command saying the Wi-Fi's name (unset: nmcli, else iwgetid).
internal val confWifiName = configuredValue<String?>("network.wifi_name", null)
// network { wifiNameEverySeconds = 15; scanOnOpen = true }: how often the Wi-Fi's name is asked
// (never scanning the air for it), and whether opening the Wi-Fi popup looks for networks.
internal val confWifiNameEvery = configuredValue("network.wifi_name_every_seconds", 15)
internal val confScanOnOpen = configuredValue("network.scan_on_open", true)

// battery { low = 20; critical = 10; onCritical { } }: when the battery is drawn in warning colours, and what runs when it is nearly empty.
internal val confBatteryLow = configuredValue("battery.low", 20)
internal val confBatteryCritical = configuredValue("battery.critical", 10)
internal val confBatteryOnCritical = configuredValue<Any?>("battery.on_critical", null)

// calendar { weekend; firstDay; weekNumbers }: the clock's calendar.
internal val confWeekend = configuredValue("calendar.weekend", listOf("SATURDAY", "SUNDAY"))
internal val confFirstDay = configuredValue<String?>("calendar.first_day", null)
internal val confWeekNumbers = configuredValue("calendar.week_numbers", false)

// quick_settings { tiles; tile(...) { } }: the quick settings' tiles, in order, and tiles of one's own.
internal val QUICK_TILES = listOf("wifi", "bluetooth", "caffeine", "dark", "screenshot", "clipboard", "colors", "keys", "dnd", "power_saver")
internal val confQuickTiles = configuredValue("quick_settings.tiles", QUICK_TILES)
internal val confQuickCustom = configuredValue<Map<String, Any?>>("quick_settings.custom", emptyMap())

// caffeine { choices = [30, 60, 120, 0] }: the caffeine tile's times (minutes; 0: until turned off).
internal val confCaffeineChoices = configuredValue("caffeine.choices", listOf(30, 60, 120, 0))

// time_format = "HH:mm": how a time of day is written (the notifications' history; the lock screen and the clipboard too).
internal val confTimeFormat = configuredValue("time_format", "HH:mm")

/** [time] in the configured time format (time_format), "HH:mm" when it is no pattern. */
internal fun timeText(time: java.time.temporal.TemporalAccessor): String = try {
    DateTimeFormats.of(confTimeFormat()).format(time)
} catch (e: Exception) {
    DateTimeFormats.of("HH:mm").format(time)
}

/** Patterns of java.time made once each, in the language of newm's words. */
internal object DateTimeFormats {
    private val made = java.util.concurrent.ConcurrentHashMap<Pair<String, java.util.Locale>, java.time.format.DateTimeFormatter>()

    /** [pattern] in [dateLocale] (an IllegalArgumentException when it is no pattern). */
    fun of(pattern: String): java.time.format.DateTimeFormatter = dateLocale().let { locale ->
        made.getOrPut(pattern to locale) { java.time.format.DateTimeFormatter.ofPattern(pattern, locale) }
    }
}
