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

## Saving, and mistakes

- **Saving** applies the file at once. You never need to log out to try a change.
- **A mistake** (a typo, a missing quote) shows as a card at the top of the screen, with the
  file and the line: `config.kts:42: Unresolved reference ...`. newm keeps the settings that
  worked until you save the file again without the mistake.
- **A broken file at the start** never gives a black screen. newm starts with its own
  defaults and shows what is wrong.
- **Lost track?** Delete the folder `~/.config/newm`. The next start writes a fresh one.

The first save after a start takes a few seconds (Kotlin is compiled once); later saves are
quick.

For everyone on the computer, `/etc/newm/` holds the starting configuration: new users get
a copy of it.

Do you prefer Lua, JSON or Nix? See [other formats](other-formats.md).

## Recipes

### Your own wallpaper

```kotlin
background { path = "~/Pictures/wallpaper.jpg" }
```

`anim = false` keeps it still (by default it moves a little with the view).
An SVG works too (`wallpaper.svg`): it stays sharp when the view zooms in.

### Keyboard layouts

Without this setting newm uses your system's layouts: those of `localectl set-x11-keymap`,
or else your language's next to English (with `LANG=ru_RU`, "us,ru"), and Alt+Shift
switches. The bar shows the layout in use (EN, RU); a click on it switches too. Your own:

```kotlin
keyboard {
    layouts = "us,ru"
    options = "grp:alt_shift_toggle"    // or "grp:win_space_toggle": Super+Space, "grp:caps_toggle": Caps Lock
}
```

A change applies as soon as you save. A key of your own can switch too:
`keys { "L-S-space" { caulis.run("keyboard-layout next") } }`.

### Keys

```kotlin
keys {
    "L-b" { launch("firefox") }
    "L-S-s" { launch("grim -g \"$(slurp)\" ~/shot.png") }
}
```

Keys are written as newm has always written them:

- `L` is Logo, `S` Shift, `C` Ctrl, `A` Alt;
- `"L-S-s"` is Logo+Shift+s, and a capital letter means Shift too (`"L-S"` is the same);
- `"L-"` is Logo tapped alone;
- `"L-a L-b"` is Logo+a, then Logo+b.

Inside `{ }` you can use everything the layout can do (`move(-1, 0)`, `toggleOverview()`, ...):
the list is in [Every setting](settings.md#everything-a-key-can-do).

### Some windows float, some get blur

```kotlin
view {
    rules { view ->
        when (view.appId) {
            "mpv", "pavucontrol" -> rule(float = true)
            "wlogout" -> rule(float = true, floatSize = 1920 to 1080)
            "Alacritty" -> rule(blur = Blur(radius = 6, passes = 4))
            else -> null
        }
    }
}
```

To find a window's app id, open the window, then run `newm cmd debug` in a terminal. It lists
each window with its id.

Blur shows only behind see-through windows. Make the terminal's background see-through in
its own settings (in alacritty: `window.opacity = 0.8`).

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
```

To see the monitors' names, run `newm cmd debug`.

### Dim, lock and sleep when idle

```kotlin
energy {
    idleTimes = listOf(120, 300, 600)   // dim after 2 minutes, lock after 5, sleep after 10
    screensOffAfter = 330               // the screens go dark; a key or the mouse brings them back
}
```

Each step happens once; when you come back, it all starts over. Programs that keep the screen
on (a video player) hold it off.

### A screen locker of your own

newm has its own lock screen. To use swaylock, gtklock or hyprlock instead:

```kotlin
panels {
    lock { program = "swaylock -f -c 2E3440" }
}
```

Logo+p and the idle lock then start it.

### The cursor and the touchpad

```kotlin
cursor { theme = "Adwaita"; size = 24; hideAfter = 5 }   // hidden after 5 s without moving
input { tapToClick = true; naturalScroll = true }
keyboard { numLock = true }                              // Num Lock on from the start
```

### How windows look

```kotlin
view {
    unfocusedOpacity = 0.9      // windows without the focus a little see-through
    fade = true                 // windows fade in and out (on by default)
    fillFirst = true            // the first window takes the whole screen (on by default)
    rules { view ->
        if (view.appId == "catapult") rule(focus = false) else null   // no focus border around it
    }
}
```

### Light or dark programs

```kotlin
caulis.colorScheme = dev.caulis.core.appearance.ColorScheme.DARK
```

`caulis` is the compositor library newm is built on ([Caulis](https://github.com/AristarkhKenebas/caulis)).
Everything its guide describes works here too, for example a scheme that follows the time of
day.

### The clipboard's history

Super+V shows what you copied lately, texts and pictures, newest first. Type to search; Enter
or a click copies one again; Delete or its ✕ takes one away; "Clear all" empties it. It is kept
in memory only, never on disk, and what password managers mark as secret is not kept.

```kotlin
clipboard {
    enabled = true      // false: nothing is kept, Super+V does nothing
    size = 50           // how many things are kept
}
keys {
    "L-v"("Clipboard history") { toggleClipboard() }   // or another key
}
```

### Caffeine

The battery's popup has Caffeine: the screen stays on (no dimming, locking or sleeping) for
30 minutes, an hour, two, or until you turn it off. A cup shows in the bar meanwhile. From a
terminal: `newm cmd caffeine 45`, `newm cmd caffeine on`, `newm cmd caffeine off`.

### The menu's button and the language

The bar starts with a button of three lines that opens the launcher. `bar { menuButton = false }`
takes it away; put `"menu"` into `left`, `center` or `right` to place it yourself.

newm's menus speak Russian when the system's language is Russian. To choose:

```kotlin
language = "en"     // "auto" (the system's, the default), "en" or "ru"
```

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

### Wider windows push their neighbours

Making a window wider (Super+Ctrl+L) or taller moves the windows beside it out of the way; made
smaller again, the room is closed. To stack them on each other as newm did:

```kotlin
view { resizePushes = false }
```

### A projector or a second monitor: mirror or extend

A monitor plugged in for the first time is used beside the laptop's screen and the choice of
modes is shown; the laptop's display key (F7 on many) shows it any time. From a terminal:
`newm cmd displays mirror` (the same picture on both), `extend`, `laptop` or `other` (only that
one). The choice is kept for that monitor.

### Cards when Wi-Fi, a cable or Bluetooth comes or goes

A card with a big picture shows in the middle of the screen for a moment (as on a Mac):

```kotlin
osd { connections = "cards" }   // the default; "text": a short line; "off": nothing
```

The clipboard's history also keeps pinned entries (Ctrl+P or the pin button): they stay
on top, are never pushed out, and pinned texts are kept after a restart
(~/.local/state/newm/clipboard-pins.json). Tab goes through All / Text / Pictures / Pinned.

### Screenshots in detail

```kotlin
screenshots {
    enabled = true
    folder = "~/Pictures/Screenshots"
    save = true; copy = true        // as a file, on the clipboard
    pointer = false                 // the pointer in the picture
    toast = true                    // "Screenshot saved" for a moment
    start = "region"                // the chooser starts with "region", "window" or "screen"
    delay = 0                       // seconds before it is taken
    sharpWindows = true             // a part of the overview: put together from the windows, sharp
    sharpBackground = "none"        // behind them: "none" (see-through), "#RRGGBB", "theme"
    fileName = "Screenshot from {date} {time}"   // {date}, {time}, {kind}
    format = "png"; quality = 92    // or "jpg" (quality 10..100)
    then = "satty --filename {file}"            // a command with each file (an editor)
}
```

In the overview (tap Super) choose a part over the windows: the picture is put together
from the windows at their own size — each as sharp as if it filled the screen — in the
places they have there. The power menu of the bar has "Screenshot…" too.

### The wallpaper: following the view or standing still

```kotlin
background { mode = "static" }   // "follow" (the default): moves and zooms with the view
```

### The clipboard's history in detail

```kotlin
clipboard {
    enabled = true
    size = 50                       // pinned ones are kept on top of these
    pictures = true; files = true   // pictures and copied files are kept too
    keepPins = true                 // pinned texts kept after a restart
    ignore = listOf("keepassxc", "bitwarden", "1password")   // never kept from these
    maxPictureMb = 16; maxText = 200000
}
```

"Clear" removes everything but the pinned ones; its arrow clears only text, pictures or
files (grey when there is none). Tab goes from part to part of the window; the arrows or
h j k l move inside one; Enter does it.

### Quick actions

The bar's grid button (before the power button) opens quick actions: the screen's
brightness, caffeine (its arrow: for how long), a screenshot, the clipboard's history, the
light or dark scheme, the colours and the keys. The wheel over it changes the brightness.
`bar { quickButton = false }` takes it away; put "quick" into a list to place it yourself.

### Moving windows with the mouse, the overview

Super + left drag moves the window under the pointer, Super + right drag resizes it (as in
Hyprland); on a touchpad Super and two fingers resize too. Super and the mouse moved alone do
nothing any more:

```kotlin
mouse { superMove = "button" }   // "motion": Super and the mouse alone move it, as newm had it
```

In the overview (Super alone) the windows take no clicks: a click chooses one and the
overview ends there.

```kotlin
overview {
    input = false     // true: the windows take clicks there, as newm had it
    choose = true     // a click chooses a window
}
```

### Making windows smaller, crisp resizing

A window as small as a tile lets it be — the first one fills the screen as one tile — can
still be made narrower or shorter (Super+Ctrl+H/K, Super+right drag): the grid is made finer
first (each tile 2 x 2, nothing moves on screen). A window is never made smaller than its
program can be (Spotify: 800 x 600).

While a window is resized it is drawn at its own size, not stretched, so it does not flicker
when its new picture comes. `view { crispResize = false }` stretches it, as newm did.

### Restarting with your windows

`newm cmd restart keep` restarts newm (a new build, a changed setting) and opens your windows
again afterwards: each program starts again (a terminal in the folder its shell was in) and its
window goes back to its place. What can be kept is a program's command line; what a program
had open inside it (tabs, files) is its own to bring back.
