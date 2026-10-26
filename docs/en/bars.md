# Bars

**English** · [Русский](../ru/bars.md)

newm has its own bar, and any other bar program works just as well. Having no bar at all is
fine too. Windows always make room for whatever bar there is.

## newm's own bar

It is on at the start. In `config.kts`:

```kotlin
panels {
    topBar {
        enabled = true
        height = 28
        fontSize = 12
        font = "JetBrains Mono"
        background = "#ECEFF4B0"        // see-through: the wallpaper shows, blurred
        foreground = "#2E3440"
        blur = true
        texts { listOf(wifi(), cpu(), memory(), temperature(), battery(), date(), time()) }
    }
}
```

The bar shows the texts that `texts` gives, and asks for them every second. Ready-made
pieces:

- `wifi()`, `cpu()`, `memory()`, `temperature()`, `battery()`, `user()`;
- `date()` and `time()`, which take a pattern: `time("HH:mm")`, `date("EEE d MMM")`.

Any Kotlin that makes a text works too.

`bottomBar { }` is the same at the bottom. Your folder's `bars/classic.kts` is the bar of
the original newm, with a top and a bottom bar, ready to paste.

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
