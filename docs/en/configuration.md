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
