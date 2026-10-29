# Your configuration

**English** · [Русский](../ru/configuration.md)

Your configuration is `~/.config/newm/config.kts`. newm writes it the first time it starts,
with a note on every setting. It is Kotlin, but you rarely need to know that: it is a list of
sections with settings in them.

```kotlin
view {
    cornerRadius = 14.0     // rounded windows
    padding = 8.0           // the gap around each window
}
```

- `// ...` is a note. Remove the `//` in front of a line to use what it says.
- Settings that take fractions are written with a dot: `8.0`.
- Texts go in quotes: `"alacritty"`. Colours are `"#RRGGBB"`, or `"#RRGGBBAA"`, where the
  last two digits make the colour see-through (`00` invisible, `FF` solid).
- A setting you leave out keeps newm's default. [Every setting](settings.md) lists them all,
  with their defaults; `newm cmd config` prints the ones in use.

## Saving, and mistakes

- **Saving** applies the file at once. You never need to log out to try a change.
  **Logo+Shift+C** loads it again by hand.
- **A mistake** (a typo, a missing quote) shows as a card in the top right corner, with the
  file and the line: `config.kts:42: Unresolved reference ...`. newm keeps the settings that
  worked until you save the file again without the mistake. A value of the wrong kind (a
  word where a number goes) is left out, and a card says which.
- **A broken file at the start** never gives a black screen. newm starts with its own
  starting configuration and shows what is wrong.
- **Lost track?** Rename or delete the folder `~/.config/newm`. The next start writes a fresh
  one.

The first load after a start takes a few seconds (Kotlin is compiled); later saves are
quicker.

### Which file

newm reads **one** file of `~/.config/newm/`, the first it finds: `config.kts`, `config.lua`,
`config.json`, `config.py` (see [other formats](other-formats.md)).

With none there, newm makes the folder: a copy of `/etc/newm/` if the system has one (its
`config.kts`, `config.lua` or `config.json`; NixOS's `programs.newm.settings` writes
`config.json` there), else newm's own `config.kts`. This happens once: after that the folder
is yours, and changes to `/etc/newm` do not reach it.

## Recipes

### Your own wallpaper

```kotlin
background { path = "~/Pictures/wallpaper.jpg" }
```

An SVG works too (`wallpaper.svg`): it stays sharp when the view zooms in.

By default the wallpaper moves and zooms with the view, and the overview shows more of it.
To keep it still:

```kotlin
background { mode = "static" }   // "follow" (the default)
```

### Keyboard layouts

Without this setting newm uses your system's layouts: those of `localectl set-x11-keymap`,
or else your language's next to English (with `LANG=ru_RU`, "us,ru"), and Alt+Shift
switches. The bar shows the layout in use (EN, RU); a click on it switches too. Your own:

```kotlin
keyboard {
    layouts = "us,ru"
    options = "grp:alt_shift_toggle"    // or "grp:win_space_toggle": Logo+Space, "grp:caps_toggle": Caps Lock
    // variants = ",phonetic"           // one per layout
    // numLock = true                   // Num Lock on from the start
}
```

A change applies as soon as you save. A key of your own can switch too:
`keys { "L-S-space" { caulis.run("keyboard-layout next") } }`.

### Keys

```kotlin
bind("Super+Backspace", "close-window")      // close the window with Super+Backspace too
bind("Super+B", "launch", "firefox")
unbind("Super+S")                            // a key of the template taken away
keys {
    "Super+E"("Files") { launch("nautilus") }   // with code of your own
}
```

Keys are written as you say them (`"Super+Shift+Q"`, `"Ctrl+Alt+T"`, any case; Super is also
Logo, Win, Mod or Meta), actions by name (`close-window`, `launch`, `fullscreen`, ...). newm's
old way (`"L-q"`, `"L-S-h"`) works too. Every key name and action, and ready-made sets:
[Key bindings](keys.md). `newm cmd keys` lists the keys bound now.

### Some windows float, some get blur

```kotlin
view {
    rules { view ->
        when (view.appId) {
            "mpv", "pavucontrol" -> rule(float = true)
            "wlogout" -> rule(float = true, floatSize = 1920 to 1080, floatPos = 0.5 to 0.5)
            "Alacritty" -> rule(blur = Blur(radius = 6, passes = 4))
            "catapult" -> rule(focus = false)     // no focus border around it
            else -> null
        }
    }
}
```

To find a window's app id, open the window, then run `newm cmd debug` in a terminal. It lists
each window with its id. `view.title` is the window's title, if you need it.

Blur shows only behind see-through windows. Make the terminal's background see-through in
its own settings (in alacritty: `window.opacity = 0.85`).

### Programs at the start

```kotlin
onStartup {
    launch("mako")
    launch("nm-applet --indicator")
}
```

### A bar

newm has its own bar, and any other bar works too (waybar, ...). It is also fine to have no
bar at all. See [Bars](bars.md).

### Monitors

```kotlin
output("eDP-1") { scale = 1.25 }
output("HDMI-A-1") { width = 2560; height = 1440; refreshHz = 144.0; x = 1920; y = 0 }
output("DP-2") { transform = 1 }   // turned by 90°
```

To see the monitors' names, run `newm cmd debug` (they are in the "Outputs" of each
workspace).

### Lock and sleep when idle

```kotlin
energy {
    idleTimes = listOf(120, 300, 600)   // seconds: "idle", lock, sleep (see below)
    screensOffAfter = 330               // the screens go dark; a key or the mouse brings them back (0: never)
    suspendCommand = "systemctl suspend"
}
lockOnWakeup = true                     // locked after a suspend
```

The first time is only told to `onIdle` (newm itself shows nothing then; a script of yours
can dim the screen), the second locks, the third suspends. Each step happens once; when you
come back, it all starts over. Programs that keep the screen on (a video player) hold it
off. A shorter list leaves the last steps out: `idleTimes = listOf(120, 300)` locks but never
puts the computer to sleep.

The same one by one, with 0 for never, and steps of your own:

```kotlin
energy {
    dimAfter = 120          // "idle" told to onIdle
    lockAfter = 0           // never locks by itself
    suspendAfter = 1800     // sleeps after half an hour
    after(900) { launch("playerctl pause") }   // your own, once, until you are back
}
```

```kotlin
val brightness = { how: String -> launch("brightnessctl $how") }
energy {
    onIdle { what ->            // "idle", "idle-lock", "idle-presuspend", "idle-suspend", "active", "sleep", "wakeup"
        if (what == "idle") brightness("-s set 30%")     // dim, and remember the brightness
        if (what == "active") brightness("-r")          // back as it was
    }
}
```

### Caffeine

The quick settings have Caffeine: its square keeps the screen on (no locking, going dark or
sleeping) until you turn it off; its words offer 30 minutes, an hour or two. A cup shows in the bar
meanwhile. From a terminal: `newm cmd caffeine 45`, `newm cmd caffeine on`,
`newm cmd caffeine off`. While `newm cmd inhibit-idle` runs, the same. Other times:
`caffeine { choices = listOf(15, 45, 90, 0) }` (minutes; 0 is until turned off).

### Videos keep the screen on

Watching a video never dims, locks or suspends: programs that play one keep the screen on, and
the bar says which (a cup beside the battery, its hint, and the Caffeine tile: "Firefox:
video-playing"). newm listens to all the ways they ask:

- **wayland**: a program's idle-inhibit while its window shows (mpv, VLC, browsers on Wayland);
- **dbus**: `org.freedesktop.ScreenSaver.Inhibit` and `org.freedesktop.PowerManagement.Inhibit`
  on the session bus (Firefox, Chromium, Telegram); a program that quits or crashes lets go;
- **logind**: `systemd-inhibit --what=idle ...`;
- **mpris**: a media player that says it is playing, while a window of its own shows — for
  players that ask in none of the ways above. Music players are left out (`mprisIgnore`).

```kotlin
energy {
    inhibitFrom = listOf("wayland", "dbus", "logind", "mpris")   // the default: all four
    // mprisIgnore = listOf("spotify", "rhythmbox")              // players whose playing keeps nothing on
    // logindCheckSeconds = 10                                   // how often logind is asked
}
```

While the screen is locked they count for nothing (a paused video behind the lock screen does
not keep it on); `caulis.idleInhibitorsWhileLocked = true` changes that.

### A screen locker of your own

newm has its own lock screen. To use swaylock, gtklock or hyprlock instead:

```kotlin
panels {
    lock { program = "swaylock -f -c 2E3440" }
}
```

Logo+P and the idle lock then start it.

newm's own lock screen takes the bar's colours (light or dark, as everything); its own, and
its clock:

```kotlin
lock {
    background = "#2E3440EE"; text = "#ECEFF4"; accent = "#88C0D0"   // the old Nord look
    clock = "HH:mm:ss"          // unset: timeFormat ("HH:mm")
    date = "EEEE, d MMMM"       // "" for none
}
```

### The launcher

```kotlin
launcher {
    width = 720; height = 560; top = 0.16   // pixels; its top as a part of the screen
    frequent = 6                // the programs you start most, first (0: none)
    remember = true             // false: nothing is written about what you start
    hide = listOf("htop", "org.gnome.Extensions")   // by name or .desktop id
    runTyped = true             // Enter with nothing found runs what you typed
    entries {
        "Lock"("Lock the screen") { ensureLocked() }
        "Log out" { terminate() }
    }
    keys { "C-Return" { runTyped() } }   // next(), previous(), first(), last(), start(), runTyped(), close()
}
terminal = "foot"               // the terminal for programs that run in one, and for "terminal"
```

Ctrl+N / Ctrl+P, Ctrl+J / Ctrl+K and Tab / Shift+Tab choose, besides the arrows.

### The cursor, the touchpad and the mouse

```kotlin
cursor { theme = "Adwaita"; size = 24; hideAfter = 5 }   // hidden after 5 s without moving
input {
    tapToClick = true
    naturalScroll = true
    focusFollowsMouse = true    // the window under the pointer gets the keys (false: a click)
}
mouse { superMove = "button" }  // Logo + left drag moves, + right drag resizes;
                                // "motion": Logo and the mouse alone move it, as newm had it
```

### A drawing tablet

A tablet works out of the box: its pen is the pointer everywhere (the tip clicks, its buttons
are the right and middle buttons), and programs that know tablets — Krita, GIMP, Inkscape —
get the pen's pressure, tilt and the rest. To change how it lies on the screens:

```kotlin
input {
    tablet {
        monitor = "eDP-1"           // the monitor it covers (unset: a pen screen its own, others all as one)
        keepAspect = true           // the monitor's shape kept: a strip of the tablet goes unused
        area(0.0, 0.0, 0.8, 0.8)    // only a part of it used (fractions, from its top left)
        leftHanded = false          // turned around
        pointerOnly = false         // true: no pressure or tilt for programs, the pen is a mouse
        tipPressure = 0.05          // how hard for a touch (unset: as libinput decides)
        lowerButton = "right"; upperButton = "middle"; thirdButton = "side"   // or "left", "extra", "none"
    }
}
```

The tablet's own buttons (a pad) go to the program with the keyboard.

### The overview

```kotlin
overview {
    input = false          // true: the windows take clicks there, as newm had it
    choose = true          // a click chooses a window and ends the overview
    followPointer = true   // the window under the pointer is the chosen one (its border, Logo + drag)
}
```

### How windows look and behave

```kotlin
view {
    cornerRadius = 14.0
    padding = 8.0
    unfocusedOpacity = 0.9       // windows without the focus a little see-through
    fade = true                  // windows fade in and out
    fillFirst = false            // true: the first window takes the whole screen (false: a quarter, as newm)
    resizePushes = false         // true: a window made bigger by keys pushes its neighbours aside
    crispResize = false          // true: a window being resized is drawn at its own size, not stretched
    fitDrawnSize = false         // true: a program that drew another size is fitted in its shape, not stretched
    floatingMinSize = true       // a floating window that asks for no size opens at its smallest
}
windows {
    newPlacement = "right-of-focused"  // or "beside-focused", "end", "original" (newm's)
    childPlacement = "below"           // a dialog, a photo viewer: "below" its window, "right", "over" (floating, as newm)
    returnFocusToParent = true         // closing it gives the focus back to its window
}
focus {
    color = "auto"               // the colours' accent; or "#RRGGBB" / "#RRGGBBAA"
    width = 2.0; distance = 4.0
}
```

### Light or dark, and the colours

Light or dark — the bar, the menus, the launcher, the lock screen and your programs
together — is switched in the quick settings and kept for the next start
(`~/.local/state/newm/color-scheme`; dark until you choose). To fix it:

```kotlin
colorScheme = "dark"   // "auto" (the quick settings' last choice, the default), "dark" or "light"
```

The colours of the bar, the menus and the focus border are made from the wallpaper. The
quick settings' palette tile chooses among them; to fix them:

```kotlin
colors {
    source = "wallpaper"   // "wallpaper", "nord" (the classic newm colours) or a colour: "#5b8cc8"
    style = "tonal"        // "tonal", "vibrant", "expressive", "neutral", "monochrome"
}
```

`newm cmd colors` says which colours are in use; `newm cmd colors nord` changes them.

### The language

newm's menus speak Russian when the system's language is Russian. To choose:

```kotlin
language = "en"     // "auto" (the system's, the default), "en" or "ru"
```

### Screenshots

The keys are in `keys { }` (Logo+Shift+S and Print: the chooser; Logo+Print: the screen;
Alt+Print: the window). How the chooser works is in [Everyday use](using.md#screenshots).

```kotlin
screenshots {
    enabled = true
    folder = "~/Pictures/Screenshots"
    save = true; copy = true        // as a file, on the clipboard
    pointer = false                 // the pointer in the picture
    toast = true                    // "Screenshot saved" for a moment
    start = "region"                // the chooser starts with "region", "window" or "screen" (unset: the last used)
    delay = 0                       // seconds before it is taken
    selection = "adjust"            // an area: "adjust" (drag, then move and resize it) or "new" (each drag a new one)
    overviewStart = "windows"       // over the overview: "windows" (several, whole), "region", ... or "" (the last used)
    sharpWindows = true             // an area of the overview: put together from the windows, sharp
    sharpBackground = "wallpaper"   // behind them: "wallpaper", "none" (see-through), "#RRGGBB", "theme"
    fileName = "Screenshot from {date} {time}"   // {date}, {time}, {kind}
    format = "png"; quality = 92    // or "jpg"
    then = "satty --filename {file}"            // a command with each file (an editor)
}
```

### The clipboard's history

```kotlin
clipboard {
    enabled = true                  // false: nothing is kept, Logo+V does nothing
    size = 50                       // how many things are kept (pinned ones on top of these)
    pictures = true; files = true   // pictures and copied files are kept too
    keepPins = true                 // pinned texts kept after a restart (~/.local/state/newm/clipboard-pins.json)
    ignore = listOf("keepassxc", "bitwarden", "1password")   // never kept from these
    maxPictureMb = 16; maxText = 200000
    clearAtOnce = true              // false: "Clear" asks first
    onChoose = "copy"               // "paste": also pasted into your window (Ctrl+V; Ctrl+Shift+V in a terminal)
    width = 980; height = 620; top = 0.13
    pinKey = "C-p"
}
```

### Notifications

newm is the notification server itself (org.freedesktop.Notifications): Telegram's messages,
a finished download, a low battery come as cards at the top right, below the bar, in the
bar's colours. A card stays a few seconds (while the pointer is over it, it waits; critical
ones stay until closed); a click on it brings its program forward and does its main action,
its buttons do theirs, its × closes it. None show while the screen is locked or a window is
fullscreen: they go to the history behind the bar's bell all the same.

The history is smart: once you look at a program's window for a few seconds (a glance does not
count), its notifications are read and leave the history; old ones go by themselves.

```kotlin
notifications {
    enabled = true                  // false: newm leaves notifications to another daemon (mako, dunst, swaync)
    seconds = 6                     // how long a card stays when the program does not say
    doNotDisturb = false            // true: no cards, the history only (the bell's switch changes it too)
    readAfterFocusSeconds = 3       // a program's window focused this long: its notifications are read
    keepHours = 24; keep = 100      // older or more ones leave the history
    remember = true                 // the history kept across restarts (~/.local/state/newm/notifications.json)
    takeOver = "stale"              // another daemon left from an old session is closed ("always", "never")
    corner = "top_right"            // "top_left", "top", "bottom_right", "bottom_left", "bottom" (the compositor's notices follow)
    width = 380; gap = 10           // the cards' width, the gap between them and to the edge
    maxCards = 3                    // the most at once: the oldest goes to the history
    monitor = "focused"             // "pointer", "first", or a monitor's name ("eDP-1")
    criticalSeconds = 0             // how long a critical one stays (0: until closed)
    showInFullscreen = false        // true: cards over a fullscreen window too
    // Rules, each in turn: mute() no card (the history only), hide() not kept at all, urgency(0..2), seconds(n)
    rule { n -> if (n.app == "Spotify") mute() }
    rule { n -> if (n.summary.contains("password", ignoreCase = true)) hide() }
    onNotification { n -> println("${n.app}: ${n.summary}") }   // told of each one (after the rules)
}
timeFormat = "HH:mm"                // how times are written ("h:mm a"): the history, the lock screen, the clipboard
```

In JSON and Lua a rule is a table: what it matches (`app`, `entry`, `summary`, `app_matches`,
`summary_matches`, `body_matches`: the last three regular expressions) and what it says
(`mute`, `hide`, `urgency`, `seconds`):

```json
"notifications": { "rules": [ { "app": "Spotify", "mute": true }, { "body_matches": "(?i)code: \\d+", "seconds": 30 } ] }
```

When another daemon already has the name (a mako left over from another session, say),
newm does not fight it: it says once "Notifications go to mako — newm's own are off", and
takes the name as soon as that one quits.

### Sound, and messages on screen

```kotlin
sound { maxVolume = 150 }           // the volume slider's top, in percent (past 100: amplified)
osd {
    connections = "cards"           // Wi-Fi, a cable, Bluetooth came or went: "cards", "text" (a short line), "off"
    seconds = 1.3                   // how long the volume's or brightness' level shows
    position = "bottom"             // where: "bottom", "top", "center"
    distance = 130                  // that far from the edge, in pixels
    toastSeconds = 1.6              // a short message in the middle
    cardSeconds = 2.8               // a connection's card
    inFullscreen = false            // connections shown over a fullscreen window too
    onConnection { e -> }           // told of each (e.kind: WIFI, WIRED, BLUETOOTH; e.connected; e.name)
}
```

The volume's and brightness' wheel steps, their commands, the battery's levels and the power
menu: see [Bars](bars.md#what-the-bar-needs).

### A scratchpad (a terminal that drops down)

A window kept apart from the wall that one key shows above everything and hides again, as
sway's scratchpad. Its program starts on the first press:

```kotlin
caulis.scratchpad("drop", command = "foot --app-id drop",
    style = dev.caulis.core.Scratchpad.Center(width = 0.7, height = 0.6))
keys {
    "L-grave"("Terminal that drops down") { scratchpad("drop") }
}
```

Windows belong to it by their app id (here "drop"). Other looks: `Scratchpad.Edge()` slides in
from the top (`Edge(side = Side.RIGHT)` from the right), `Scratchpad.Overlay()` is a whole
workspace above the current one. From a terminal: `newm cmd scratchpad drop`.

### A projector or a second monitor: mirror or extend

A monitor plugged in for the first time is used beside the laptop's screen and the choice of
modes is shown; the laptop's display key (F7 on many) shows it any time. From a terminal:
`newm cmd displays mirror` (the same picture on both), `extend`, `laptop` or `other` (only that
one). The choice is kept for that monitor.

### Everything Caulis can do

`caulis` in your configuration is the compositor library newm is built on
([Caulis](https://github.com/AristarkhKenebas/caulis)). What its guide describes works here
too, for what newm has no setting of its own.

## Restarting newm

`newm cmd restart` ends newm and starts it again (newm's start script does that; so does
`dev/newm-dev`, with a new build). Every program closes, as when you log out: save your work
first. `newm cmd restart keep` also keeps your windows: at the next start each program starts again (a terminal in the folder its
shell was in) and its window goes back to its place. What a program had open inside it
(tabs, files) is its own to bring back.

```kotlin
session {
    restoreAtLogin = false      // true: the windows of your last session open again when you log in
    maxAgeMinutes = 10          // restart keep: kept longer ago than this, not opened (0: no limit)
    maxWindows = 20             // at most so many programs; the launcher offers the rest ("Reopen")
    exclude = listOf("steam")   // never opened again (app id or program)
    staggerMs = 300             // one program after another, not all at once
}
```

Under `dev/newm-dev` newm starts again at once ([Working on newm](development.md)). Started
from a login screen or a console, newm ends and you are back there: start it again and the
windows come back.

## When the screen flickers

`newm cmd full-redraw on` draws every frame whole (a little slower) and
`newm cmd full-redraw off` goes back. If the flicker is gone while it is on, say so in a bug
report: it comes from drawing only what changed.
