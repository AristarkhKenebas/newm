# Bars

**English** · [Русский](../ru/bars.md)

newm has its own bar, and any other bar program works just as well. Having no bar at all is
fine too. Windows always make room for whatever bar there is.

## newm's own bar

It is on at the start: three see-through islands floating at the top of the screen. It
steps aside while a window is fullscreen and stays in the overview.

- **Left: the menu and the window.** The menu button (three lines) opens the launcher. Next
  to it, the focused window's icon and title: a click shows every window (the overview), a
  middle click closes the window. Add `"wall"` for a small map of your wall: each window a tile, the focused one in the
  accent colour, the part on screen outlined; a click shows every window (the overview), the
  wheel goes through the windows.
- **Middle: the clock.** A click opens a calendar (the arrows change the month, a click on
  the month's name goes back to today).
- **Right: the tray and the computer.**
  - **The tray**: icons of programs running in the background (Telegram, Discord, Steam,
    ...). A click activates the program, a right click opens its menu, a middle click does
    its second action, and the wheel scrolls it. Hovering shows the program's tooltip.
    nm-applet and blueman are left out, since the bar shows Wi-Fi and Bluetooth itself
    (`trayHide`).
  - **The keyboard layout** (EN, RU), when there are several: a click switches.
  - **Bluetooth** (when the computer has it): on and off, your devices (connect, disconnect,
    forget, their battery), and the devices nearby to pair, all in the popup.
  - **Wi-Fi** (or the cable): on and off, the networks in reach; a click connects (a new
    network asks its password in a small window in the middle of the screen; a wrong password
    of a saved network asks again and changes only the password, not the rest you set up for
    it), a known one can be disconnected or forgotten. Networks are looked for only when the
    popup opens (`network { scanOnOpen = false }`: only with its refresh button).
  - **Sound**: the wheel changes the volume, a middle click mutes. A click opens the volume,
    which speaker or headphones to use, and the microphone.
  - **The battery**: how long it lasts or how long until it is full, a switch for the percent
    at the bar, and the power mode (power saver, balanced, performance).
  - **Notifications** (a bell; a small number on it counts the new ones): the history of
    notifications, newest first, by program. A click on one brings its program forward, its
    × closes it; "Clear all", and the Do not disturb switch (no cards, the history only; the
    middle button on the bell switches it too). See [Notifications](configuration.md#notifications).
  - **The quick settings** (a grid of four squares; a cup while caffeine is on): see below.
    The wheel over it changes the brightness.
  - **The power button**: lock, suspend, hibernate (when the computer can), log out, restart,
    shut down. The last three ask once more ("Click again"), so a slip of the hand does not
    end your session. Which entries, which ask, and what each runs: `power { }` below.

A click anywhere else closes a popup. Hovering over an island says what it is (after
`hintDelayMs`). What every click, middle click and wheel does can be changed: see
[Clicks and the wheel](#clicks-and-the-wheel).

### The quick settings

As on a phone:

- **the brightness** slider (when the screen's brightness can be set: brightnessctl);
- **Wi-Fi** and **Bluetooth**: the square switches them on and off, the words open their
  popup;
- **Caffeine**: the screen stays on (no locking, going dark or sleeping); the words offer
  30 minutes, 1 hour, 2 hours or until turned off;
- **light or dark**: for newm and your programs together, kept for the next start;
- **a screenshot**: the chooser;
- **the clipboard's history**;
- **the colours**: made from the wallpaper (with their style: tonal, vibrant, expressive,
  neutral, monochrome) or the classic newm colours;
- **the keys' help**;
- **do not disturb** (no notifications' cards);
- **power saving** (when power-profiles-daemon runs); turned off, it goes back to the mode
  it came from.

Which tiles, in which order, and tiles of your own:

```kotlin
quickSettings {
    // two wide tiles make a row; a wide one alone shares its row with up to two small ones; other small ones go five to a row
    tiles = listOf("wifi", "bluetooth", "caffeine", "dark", "screenshot", "clipboard", "colors", "keys", "dnd", "power_saver", "vpn")
    tile("vpn", icon = "lock", label = "VPN", wide = false) {
        onClick { launch("vpn-toggle") }
        isOn("vpn-status --quiet")      // a command (on when it succeeds), or isOn { ... } in Kotlin
    }
}
caffeine { choices = listOf(30, 60, 120, 0) }   // minutes; 0: until turned off
```

An icon is a Material symbol newm carries (`wifi`, `bluetooth`, `coffee`, `dark_mode`,
`palette`, `keyboard`, ...), one of newm's own (`lock`, `bell`, `power`, `settings`, `moon`,
`sun`, ...), an icon of your icon theme (`firefox`), or a picture's file.

### What the bar needs

Each part reads the computer through a usual service or program. Without it, that part is
empty or does nothing, and the rest works:

| Part | Needs |
|---|---|
| Wi-Fi | NetworkManager |
| Bluetooth | BlueZ (bluetoothd) |
| the volume popup | `pactl` (PulseAudio, or PipeWire's pulse part) |
| the volume keys | `wpctl` (WirePlumber) |
| the brightness | `brightnessctl` |
| power modes, power saving | power-profiles-daemon |
| the battery | nothing (`/sys/class/power_supply`) |

The commands can be your own (a `{percent}` in them is the value):

```kotlin
sound {
    wheelStep = 2                                   // percent a notch of the wheel over the volume
    setVolume = "pamixer --set-volume {percent}"    // unset: wpctl, else pactl
    getVolume = "pamixer --get-volume-human"        // "45%", or wpctl's "Volume: 0.45 [MUTED]"
}
brightness {
    wheelStep = 5                                   // percent a notch of the wheel over the quick settings
    minimum = 1                                     // the lowest the bar sets
    device = "intel_backlight"                      // unset: the first in /sys/class/backlight
    set = "light -S {percent}"                      // unset: brightnessctl (of device)
    get = "light -G"                                // unset: read from /sys/class/backlight
}
network { wifiName = "iwgetid -r" }                 // unset: nmcli, else iwgetid
battery {
    low = 20                                        // the warning colour below this
    critical = 10                                   // the danger colour below this
    onCritical { launch("notify-send -u critical 'Battery low'") }   // once each time, not charging
}
power {
    entries = listOf("lock", "suspend", "hibernate", "logout", "reboot", "poweroff")
    confirm = listOf("logout", "reboot", "poweroff")   // ask once more
    suspend = "systemctl suspend-then-hibernate"   // a command; unset: energy { suspendCommand }
    reboot = "systemctl reboot"                    // hibernate, poweroff: systemctl (loginctl without it)
    onLock { ensureLocked() }                      // or an action: onSuspend { }, onLogout { }, ...
}
calendar {
    weekend = listOf("SATURDAY", "SUNDAY")          // drawn faint
    firstDay = "MONDAY"                             // unset: the language's
    weekNumbers = true                              // the weeks' numbers at the left
}
```

### Settings

All of them optional:

```kotlin
panels {
    topBar {
        enabled = true
        height = 32                     // an island's height
        margin = 6                      // the gap to the screen's edges
        cornerRadius = 12
        theme = "auto"                  // "auto", "light", "dark"
        font = "Inter"                  // unset: the system's sans-serif
        fontSize = 13
        left = listOf("window")
        center = listOf("clock")
        right = listOf("tray", "layout", "bluetooth", "network", "volume", "battery", "notifications", "power")
        clock = "HH:mm"                 // java.time patterns: "h:mm a", "HH:mm:ss"
        date = "EEE d MMM"              // "" for no date
        accentColor = "#88C0D0"         // also islandColor, textColor
        iconSize = 16
        showPercent = true              // the volume's and battery's percent
        showPassive = false             // tray icons that say they are idle
        titleWidth = 360                // the window title's widest
        trayHide = listOf("nm-applet", "blueman")
        menuButton = true               // the menu button first on the left
        quickButton = true              // the quick settings before the power button
        notificationsButton = true      // the bell before the quick settings
        layoutNames = mapOf("us" to "EN", "de(neo)" to "NEO")   // the keyboard layouts' labels
        outputs = listOf("eDP-1")       // the monitors with the bar; empty (the default): all
        clockHint = "EEEE, d MMMM yyyy" // the clock's hint; "" for none
        hintDelayMs = 800               // how long the pointer stays before a hint shows
    }
}
```

Each island shows its modules in order, and an empty list leaves that island out. The
modules are `menu`, `wall`, `window`, `clock`, `tray`, `layout`, `bluetooth`, `network`,
`volume`, `battery`, `notifications`, `quick`, `power` and `texts`, and your own (below).
`notifications` and `quick` are always added before `power` (also to a `right` you write),
unless you place them yourself or turn them off (`quickButton = false`,
`notificationsButton = false`). `menu` is added to newm's own `left` only: a `left` you write
is shown as you wrote it (or turn it off with `menuButton = false`). A name
that is not a module shows as it is, as a text.

The clock, the date and the calendar follow `language`. On a monitor not in `outputs` the bar
is not drawn (windows still keep its room).

### Modules of your own

```kotlin
panels {
    topBar {
        right = listOf("vpn", "tray", "volume", "battery", "power")
        module("vpn") {
            command("vpn-status")       // its output's first line is the text (or text { "..." } in Kotlin)
            everySeconds = 10           // asked this often (5)
            timeoutSeconds = 5          // a command taking longer is stopped
            icon = "lock"               // optional: as a quick settings tile's
            onClick { launch("vpn-toggle") }
            onMiddle { }                // also onRight { }
            onScroll { dy -> }          // the wheel's notches: down more, up fewer
        }
    }
}
```

The command runs as in a shell, away from the compositor, so a slow one never stops the
screen. A module with no text and no icon is not shown.

### Clicks and the wheel

What a click, the middle and right buttons and the wheel do on a module:

```kotlin
panels {
    topBar {
        on("window") { middle(null) }                       // the middle click closes nothing
        on("volume") { middle { launch("pavucontrol") } }
        on("clock") { click { launch("gnome-calendar") } }
        on("wall") { scrollUp { move(0, -1) }; scrollDown { move(0, 1) } }
        on("quick") { off() }                               // nothing at all
        // The wheel over the bar's empty space and the clock: the focus goes that way (newm's default)
        wheel { up { move(0, -1) }; down { move(0, 1) }; left { move(-1, 0) }; right { move(1, 0) } }
    }
}
```

The slots are `click`, `middle`, `right`, `scrollUp` and `scrollDown`, each an action or
`null` for nothing; what you leave out stays newm's (the right button does what a click does).
The modules are those above and your own. newm's own: `wall` (click: the overview; wheel: the
windows), `window` (click: the overview; middle: close it), `clock` (the calendar), `layout`
(click and wheel: switch), `volume` (the popup; middle: mute; wheel: the volume), `quick` (the
popup; wheel: the brightness), `notifications` (the history; middle: do not disturb), and the
others' clicks open their popups. `wheel { off() }` makes the empty space's wheel do nothing.

In JSON (and Lua tables) the same:

```json
"panels": { "top_bar": {
  "on": { "window": { "middle": null }, "volume": { "middle": "launch pavucontrol" } },
  "wheel": { "up": "move 0 -1", "down": "move 0 1", "left": false, "right": false },
  "modules": { "vpn": { "command": "vpn-status", "every_seconds": 10, "icon": "lock", "on_click": "launch vpn-toggle" } }
} }
```

`texts` shows the texts of `texts { ... }`, asked for every second:

```kotlin
panels {
    topBar {
        right = listOf("texts", "tray", "battery", "power")
        texts { listOf(cpu(), memory(), temperature()) }
    }
}
```

Ready-made texts are `wifi()`, `cpu()`, `memory()`, `temperature()`, `battery()` and
`user()`, plus `date()` and `time()`, which take a pattern (`time("HH:mm")`). Any Kotlin that
makes a text works too.

`bottomBar { }` is the same at the bottom of the screen.

### The popups from a key

`newm cmd bar calendar` (or `volume`, `network`, `bluetooth`, `battery`, `power`, `quick`,
`colors`, `tray 0`, `close`) opens a popup, so a key can do it too:

```kotlin
keys { "L-Escape"("The power menu") { command("bar", "power") } }
```

### The classic bar

`style = "classic"` brings back the original newm's bar: a strip of texts spread over the
screen's edge. Your folder's `bars/classic.kts` has it ready to paste, top and bottom.

## No bar

```kotlin
panels {
    topBar { enabled = false }
}
```

## waybar, or any other bar

Turn newm's own bar off, and let newm start yours:

```kotlin
panels {
    topBar { enabled = false }
    bar { cmd = "waybar" }
}
```

- newm starts the bar again if it ends.
- `visibleFullscreen = true` keeps it over fullscreen windows (it steps aside by default).

You can also start the bar in `onStartup { launch("waybar") }`, or from your own scripts.
Any bar that uses the layer-shell protocol works: waybar, yambar, eww, ags, sfwbar,
ironbar, and others.

**waybar in newm's look**: your folder has one, in `bars/waybar/`. Copy it to waybar's folder:

```sh
mkdir -p ~/.config/waybar && cp ~/.config/newm/bars/waybar/* ~/.config/waybar/
```

Bars at the left or right edge work as well: windows make room on that side.

## Other panels

Notifications (mako, dunst, swaync), wallpaper programs (swaybg, swww), docks and
on-screen keyboards all work the same way: start them in `onStartup { }`. newm shows its own
messages itself, but programs' notifications need one of these programs. If you use a
wallpaper program, set `background { path = "" }`, so that newm does not draw its own
wallpaper.
