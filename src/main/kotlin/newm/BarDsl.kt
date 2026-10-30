package newm

import newm.bar.ConnectionEvent
import newm.bar.Notification
import newm.bar.NotificationRule

// The configuration's sections of newm's bar and its popups (config.kts): see NewmConfig.

/** topBar { module("name") { } }: a module of your own, shown where its name is in left / center / right. */
class BarModule internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** Its text, asked every [everySeconds] (on the compositor's thread: keep it quick; a command for slow things). */
    fun text(make: () -> String) {
        values["text"] = make
        values.remove("command")
    }

    /** A command whose output's first line is its text (run as in a shell, away from the compositor, stopped after [timeoutSeconds]). */
    fun command(command: String) {
        values["command"] = command
        values.remove("text")
    }

    /** How often its text is asked (5 seconds). */
    var everySeconds by setting<Number>("every_seconds")

    /** How long its command may take (5 seconds). */
    var timeoutSeconds by setting<Int>("timeout_seconds")

    /** An icon before the text: a Material symbol newm carries ("coffee", "wifi"), one of newm's ("bell"), of the icon theme ("firefox"), or a file. */
    var icon by setting<String>("icon")

    /** What a click, the middle and the right button do. */
    fun onClick(action: Layout.() -> Unit) { values["on_click"] = action }
    fun onMiddle(action: Layout.() -> Unit) { values["on_middle"] = action }
    fun onRight(action: Layout.() -> Unit) { values["on_right"] = action }

    /** What the wheel does: [action] gets the notches (down: more, up: fewer). */
    fun onScroll(action: Layout.(Int) -> Unit) { values["on_scroll"] = action }
}

/**
 * topBar { on("window") { middle { closeFocusedView() } } }: what clicks and the wheel do on a
 * module instead of newm's. Each an action, or null for nothing; what is not given stays newm's.
 */
@NewmConfigDsl
class BarClicks internal constructor(private val slots: MutableMap<String, Any?>) {
    fun click(action: (Layout.() -> Unit)?) { slots["click"] = action }
    fun middle(action: (Layout.() -> Unit)?) { slots["middle"] = action }
    fun right(action: (Layout.() -> Unit)?) { slots["right"] = action }
    fun scrollUp(action: (Layout.() -> Unit)?) { slots["scroll_up"] = action }
    fun scrollDown(action: (Layout.() -> Unit)?) { slots["scroll_down"] = action }

    /** Nothing at all: no clicks, no wheel. */
    fun off() {
        for (slot in listOf("click", "middle", "right", "scroll_up", "scroll_down")) slots[slot] = null
    }
}

/** power { }: the bar's power menu. */
class PowerSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** Its entries, in order: "lock", "suspend", "hibernate" (where the computer can), "logout", "reboot", "poweroff". */
    var entries by setting<List<String>>("entries")

    /** Those that ask once more ("logout", "reboot", "poweroff"). */
    var confirm by setting<List<String>>("confirm")

    /**
     * Commands instead of newm's (run as in a shell): lock and logout are newm's own; suspend is
     * energy { suspendCommand }; the rest systemctl (loginctl where there is no systemctl).
     */
    var lock by setting<String>("lock")
    var suspend by setting<String>("suspend")
    var hibernate by setting<String>("hibernate")
    var logout by setting<String>("logout")
    var reboot by setting<String>("reboot")
    var poweroff by setting<String>("poweroff")

    /** Actions instead of newm's (or instead of a command above). */
    fun onLock(action: Layout.() -> Unit) { values["lock"] = action }
    fun onSuspend(action: Layout.() -> Unit) { values["suspend"] = action }
    fun onHibernate(action: Layout.() -> Unit) { values["hibernate"] = action }
    fun onLogout(action: Layout.() -> Unit) { values["logout"] = action }
    fun onReboot(action: Layout.() -> Unit) { values["reboot"] = action }
    fun onPoweroff(action: Layout.() -> Unit) { values["poweroff"] = action }
}

/** brightness { }: the screen's brightness from the bar (the quick settings' slider, the wheel over their button). */
class BrightnessSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** Percent a notch of the wheel (5), and the lowest it goes (1). */
    var wheelStep by setting<Int>("wheel_step")
    var minimum by setting<Int>("minimum")

    /** The backlight (a name in /sys/class/backlight: "intel_backlight"); unset: the first there is. */
    var device by setting<String>("device")

    /** A command setting it ({percent}, {device}); unset: brightnessctl. */
    var set by setting<String>("set")

    /** A command saying it in percent; unset: read from /sys/class/backlight. */
    var get by setting<String>("get")
}

/** battery { }: the battery at the bar. */
class BatterySection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** Below this percent it is drawn in the warning colour (20), below [critical] in the danger colour (10). */
    var low by setting<Int>("low")
    var critical by setting<Int>("critical")

    /** Runs once each time the battery falls to [critical] while it is not charging (a notice, suspending). */
    fun onCritical(action: Layout.() -> Unit) { values["on_critical"] = action }
}

/** calendar { }: the clock's calendar. */
class CalendarSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** The days drawn faint ("SATURDAY", "SUNDAY"). */
    var weekend by setting<List<String>>("weekend")

    /** The week's first day ("MONDAY"); unset: the language's. */
    var firstDay by setting<String>("first_day")

    /** The weeks' numbers (ISO's) at the left (false). */
    var weekNumbers by setting<Boolean>("week_numbers")
}

/** quickSettings { }: the quick settings' tiles. */
class QuickSettingsSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /**
     * Which tiles, in order: "wifi", "bluetooth", "caffeine" (wide), "dark", "screenshot",
     * "clipboard", "colors", "keys", "dnd", "power_saver" (small), and yours (tile(...)).
     * More, only when named here: "media" (wide: what plays), "mic", "night_light",
     * "airplane", "record", "power_profile" (small).
     */
    var tiles by setting<List<String>>("tiles")

    /** The tile "night_light": how warm the screen gets (0.4; 0..1). A reload of the configuration sets its own screen shader again. */
    var nightLight by setting<Double>("night_light")

    /** The tile "airplane": the command that switches airplane mode ("rfkill toggle all"; "": the tile only shows it). */
    var airplaneCommand by setting<String>("airplane_command")

    /** The tile "record": the command that records the screen until the second click (wf-recorder into ~/Videos). */
    var recordCommand by setting<String>("record_command")

    /** A tile of your own, named in [tiles]: its icon (as a module's), its words (a wide one shows them), what a click does, whether it is on. */
    fun tile(name: String, icon: String? = null, label: String? = null, wide: Boolean = false, configure: QuickTile.() -> Unit) {
        val tile = linkedMapOf<String, Any?>("wide" to wide)
        icon?.let { tile["icon"] = it }
        label?.let { tile["label"] = it }
        QuickTile(tile).configure()
        nested("custom")[name] = tile
    }
}

/** quickSettings { tile(...) { } }: a tile of your own. */
class QuickTile internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    fun onClick(action: Layout.() -> Unit) { values["on_click"] = action }

    /** Whether it is drawn on (asked when the popup shows and after a click; quick). */
    fun isOn(on: () -> Boolean) { values["is_on"] = on }

    /** Whether it is on: [command] succeeds (run away from the compositor). */
    fun isOn(command: String) { values["is_on"] = command }
}

/** caffeine { }: the caffeine tile (the screen stays on). */
class CaffeineSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** Its times in minutes, 0 for until turned off (30, 60, 120, 0). */
    var choices by setting<List<Int>>("choices")
}

/** network { }: the network at the bar. */
class NetworkSection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** A command saying the Wi-Fi's name; unset: nmcli, else iwgetid. */
    var wifiName by setting<String>("wifi_name")

    /** How often the Wi-Fi's name is asked, in seconds (15; it never scans the air). */
    var wifiNameEverySeconds by setting<Int>("wifi_name_every_seconds")

    /** Opening the Wi-Fi popup looks for networks (true; false: only its refresh button does). */
    var scanOnOpen by setting<Boolean>("scan_on_open")
}

/** tray { }: background programs' icons, and what a program may put there. */
class TraySection internal constructor(values: MutableMap<String, Any?>) : Section(values) {
    /** How many icons one program may show (8); more are refused. */
    var maxItemsPerProgram by setting<Int>("max_items_per_program")

    /** The biggest picture taken from a program, in pixels each way (1024); bigger ones are not drawn. */
    var maxIconSize by setting<Int>("max_icon_size")
}

/** The rules' and hooks' setters of notifications { } (see NotificationsSection). */
internal fun Section.addNotificationRule(decide: NotificationRule.(Notification) -> Unit) {
    @Suppress("UNCHECKED_CAST")
    val list = (values["rules"] as? MutableList<Any?>) ?: ArrayList<Any?>().also { values["rules"] = it }
    list += decide
}

internal fun Section.setConnectionHook(hook: (ConnectionEvent) -> Unit) {
    values["on_connection"] = hook
}
