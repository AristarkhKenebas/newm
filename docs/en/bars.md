# Bars

**English** · [Русский](../ru/bars.md)

newm has its own bar, and any other bar program works just as well. Having no bar at all is
fine too. Windows always make room for whatever bar there is.

## newm's own bar

It is on at the start: three see-through islands floating at the top of the screen.

- **Left: the wall and the window.** A small map of your wall: each window is a tile, the
  focused one in the accent colour, the part on screen outlined. Next to it, the focused
  window's icon and title. A click shows every window (the overview); the wheel goes through
  the windows; a middle click on the title closes the window.
- **Middle: the clock.** A click opens a calendar (the arrows change the month, a click on
  the month's name goes back to today).
- **Right: the tray and the computer.**
  - The tray: icons of programs running in the background (Telegram, Discord, Steam,
    nm-applet, blueman, ...). A click activates the program, a right click opens its menu,
    a middle click does its second action, and the wheel scrolls it. Hovering shows the
    program's tooltip.
  - The network: a click shows its name and strength, and opens the network settings.
  - The volume: the wheel changes it, a middle click mutes. A click opens sliders for the
    volume and the screen's brightness.
  - The battery: a click shows how long it lasts or how long until it is full.
  - The power button: lock, suspend, log out, restart, shut down. The last three ask once
    more, so a slip of the hand does not end your session.

A click anywhere else closes a popup. The colours follow the light or dark scheme of your
programs.

The settings, all of them optional:

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
        left = listOf("wall", "window")
        center = listOf("clock")
        right = listOf("tray", "network", "volume", "battery", "power")
        clock = "HH:mm"                 // java.time patterns: "h:mm a", "HH:mm:ss"
        date = "EEE d MMM"              // "" for no date
        accentColor = "#88C0D0"         // also islandColor, textColor
        iconSize = 16
        showPercent = true              // the volume's and battery's percent
        showPassive = false             // tray icons that say they are idle
        titleWidth = 360                // the window title's widest
    }
}
```

Each island shows its modules in order, and an empty list leaves that island out. The
modules are `wall`, `window`, `clock`, `tray`, `network`, `volume`, `battery`, `power` and
`texts`. The last one shows the texts of `texts { ... }`, asked for every second:

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

`newm cmd bar calendar` (or `volume`, `network`, `battery`, `power`, `tray 0`, `close`) opens
a popup, so a key can do it too:

```kotlin
keys { "L-Escape" { command("bar", "power") } }
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

- newm restarts the bar if it crashes.
- `visibleFullscreen = false` hides it while a window is fullscreen.

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
on-screen keyboards all work the same way: start them in `onStartup { }`. If you use a
wallpaper program, set `background { path = "" }`, so that newm does not draw its own
wallpaper.
