# newm — the guide

newm is a Wayland compositor (the program that draws your windows and passes them your
keyboard and mouse) made for laptops and touchpads. This guide takes you from installing it
to making it yours. No programming needed for most of it: the configuration is a file of
settings you change and save.

- [The idea](#the-idea)
- [Install](#install)
- [First start](#first-start)
- [Everyday use: keys and gestures](#everyday-use-keys-and-gestures)
- [Your configuration](#your-configuration)
- [Recipes](#recipes)
- [Every setting](#every-setting)
- [Everything the layout can do](#everything-the-layout-can-do)
- [newm cmd](#newm-cmd)
- [A config.py of the old newm](#a-configpy-of-the-old-newm)
- [When something goes wrong](#when-something-goes-wrong)

## The idea

Most window managers put windows on your screen. newm puts them on an endless **wall** of
tiles, and your screen is a window onto that wall:

```
   ┌────────┬────────┬────────┐
   │ editor │ terminal        │      ← one window may take one tile or several
   ├────────┼────────┼────────┤
   │ browser         │ music  │
   └────────┴────────┴────────┘
        ╰─── the screen shows 2×2 tiles of it; zoom out to see more ───╯
```

- A new window takes the next free tile, at the size of a tile.
- **Zoom** out (Logo+u, four fingers up) to see more tiles at once, in (Logo+n) to see fewer.
  Windows are told their new size: they stay usable at any zoom.
- **Move** over the wall: focus the window beside (Logo+h/j/k/l, or three fingers).
- **Logo alone** shows every window at once (the overview).

## Install

You need Java 27 (on Arch: `sudo pacman -S jdk-openjdk`) and the usual Wayland pieces:
`xorg-xwayland` for X11 programs, `alacritty` (or set another terminal in the
configuration), and optionally `grim`, `wob`, `brightnessctl`.

```sh
git clone https://github.com/AristarkhKenebas/newm
cd newm
./install.sh            # for everyone (asks for sudo); ./install.sh --user: just for you
```

That builds newm, puts the `newm` command in `/usr/local/bin` and adds newm to your login
screen's sessions. Updating later: `git pull && ./install.sh`.

## First start

- **From a text console** (Ctrl+Alt+F2, log in): type `newm`.
- **From your login screen** (ly, SDDM, GDM): choose "newm".
- **To try it inside your current desktop**: run `newm` in a terminal there. It opens as a
  window, and nothing of your desktop changes.

The first time, newm writes your configuration to `~/.config/newm/config.kts`, with notes on
every setting. Open it in any editor while newm runs. Each time you save it, newm takes it
on at once.

Your first keys: **Logo+Return** opens a terminal, **Logo+d** the launcher, **Logo+q**
closes a window, **Logo+Shift+Escape** logs out.

## Everyday use: keys and gestures

"Logo" is the key with the Windows or Super logo.

| Keys | What they do |
|---|---|
| Logo+Return | a terminal |
| Logo+d | the launcher: type a program's name, Enter starts it, Escape closes |
| Logo+h / j / k / l (or the arrows) | focus the window left / below / above / right |
| Logo+Shift+h / j / k / l | move the focused window that way |
| Logo+Ctrl+h / j / k / l | make it narrower / taller / shorter / wider (in tiles) |
| Logo+u / Logo+n | zoom out (see more tiles) / in |
| Logo (tap it alone) | the overview: every window at once |
| Logo+Tab, Logo+Shift+Tab | every window in turn |
| Logo+t | the next window of a stack |
| Logo+f | fullscreen, and back |
| Logo+space | the window floats above the wall, and back |
| Logo+q | close the window |
| Logo+p | lock the screen |
| Logo+Shift+c | load the configuration again (saving it does that too) |
| Logo+Shift+Escape | log out |
| Brightness, volume, mute keys | work, on the lock screen too |
| Print | a screenshot (grim) |

On a touchpad:

| Fingers | What they do |
|---|---|
| three, any direction | move over the wall |
| four, up / down | zoom out / in |
| five | the launcher |
| Logo held + one finger (or the mouse) | move the focused window |
| Logo held + two fingers | resize it |

## Your configuration

`~/.config/newm/config.kts` is Kotlin, but you rarely need to know that. It is a list of
sections with settings in them:

```kotlin
view {
    cornerRadius = 24.0     // rounded windows
    padding = 8.0           // the gap around each window
}
```

- `// ...` is a note. Remove the `//` in front of a line to use what it says.
- Numbers with a dot (`8.0`) are written so where the setting takes fractions.
- Texts are in quotes: `"alacritty"`, colours as `"#RRGGBB"` or `"#RRGGBBAA"` (the last
  two digits make it see-through).

**Saving** loads it again at once. **A mistake** (a typo, a missing quote) shows up as a
red notice with its line, `config.kts:42: Unresolved reference: ...`. newm keeps the
settings that worked until you save the file without it. Even at the start, a broken
configuration never gives a black screen: newm starts with its own defaults and shows what
is wrong.

Lost track? Delete the file. The next start writes a fresh one.

For everyone on the computer, `/etc/newm/config.kts` is the start for new users (it is
copied to their `~/.config/newm/config.kts`).

## Recipes

**Your own wallpaper**

```kotlin
background { path = "~/Pictures/wallpaper.jpg" }
```

**Keyboard layouts** (Alt+Shift switches)

```kotlin
keyboard {
    layouts = "us,ru"
    options = "grp:alt_shift_toggle"
}
```

**A key that starts a program**

```kotlin
keys {
    "L-b" { launch("firefox") }
    "L-S-s" { launch("grim -g \"$(slurp)\" ~/shot.png") }
}
```

Keys are written as newm always wrote them: `L` Logo, `S` Shift, `C` Ctrl, `A` Alt.
`"L-S-s"` is Logo+Shift+s; a capital letter means Shift too (`"L-S"` is Logo+Shift+s again);
`"L-"` is Logo tapped alone; `"L-a L-b"` is Logo+a, then Logo+b.

**Some windows float** (and some see-through ones get the background blurred)

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

A window's app id: open it, then run `newm cmd debug` in a terminal. Each window is listed
with its id.

**Programs at the start**

```kotlin
onStartup {
    launch("mako")
    launch("nm-applet --indicator")
}
```

**Your own bar instead of newm's** (waybar, ...)

```kotlin
panels {
    topBar { enabled = false }
    bar { cmd = "waybar" }
}
```

**newm's bar, your way**

```kotlin
panels {
    topBar {
        background = "#2E3440CC"
        foreground = "#ECEFF4"
        font = "JetBrains Mono"
        texts { listOf(user(), wifi(), battery(), time("HH:mm")) }
    }
}
```

The bar shows what `texts` gives, asked every second. Ready-made pieces: `wifi()`, `cpu()`,
`memory()`, `temperature()`, `battery()`, `date()`, `time()`, `user()`. Any Kotlin that
makes a text works too.

**A monitor of its own**

```kotlin
output("eDP-1") { scale = 1.25 }
output("HDMI-A-1") { width = 2560; height = 1440; refreshHz = 144.0; x = 1920; y = 0 }
```

Monitor names: `newm cmd debug`.

**Lock and sleep when idle**

```kotlin
energy {
    idleTimes = listOf(120, 300, 600)   // dim after 2 min, lock after 5, suspend after 10
    onIdle { what -> if (what == "idle") launch("notify-send 'Going to sleep soon'") }
}
```

**Light or dark for programs, the cursor, the touchpad**

```kotlin
cursor { theme = "Adwaita"; size = 24 }
input { tapToClick = true; naturalScroll = true }
caulis.colorScheme = dev.caulis.core.appearance.ColorScheme.DARK
```

`caulis` is the compositor underneath ([Caulis](https://github.com/AristarkhKenebas/caulis)).
Everything its guide describes works here too.

## Every setting

The written config.kts lists them all with notes. In short:

| Section | Settings |
|---|---|
| (top) | `animTime`, `blendTime`, `cornerRadius` (the screen's corners), `lockOnWakeup` |
| `background { }` | `path`, `anim`, `timeScale` |
| `view { }` | `cornerRadius`, `padding`, `fullscreenPadding`, `sendFullscreen`, `acceptFullscreen`, `floatingMinSize`, `borderWsSwitch`, `rules { }` |
| `focus { }` | `enabled`, `color`, `distance`, `width`, `animTime`, `animateOnChange` |
| `panels { }` | `topBar { }`, `bottomBar { }` (`enabled`, `height`, `fontSize`, `font`, `background`, `foreground`, `blur`, `texts { }`), `bar { }` (`cmd`, `visibleNormal`, `visibleFullscreen`), `lock { }` and `launcher { }` (`w`, `h`, `cornerRadius`, `cmd`) |
| `keyboard { }` | `layouts`, `variants`, `options`, `model` |
| `cursor { }` | `theme`, `size` |
| `input { }` | `tapToClick`, `naturalScroll`, `focusFollowsMouse`, `xwayland`, `titleBars` |
| `output("name") { }` | `scale`, `width`, `height`, `refreshHz`, `x`, `y`, `transform`, `anim` |
| `energy { }` | `idleTimes`, `suspendCommand`, `onIdle { }` |
| `gestures { }` | `bind(GestureAction..., modifier, kinds)`, `lpInertia`, `lpFreq`, `enabled`, `scalePx` |
| `tuning { }` | `swipe { }`, `swipeZoom { }`, `move { }`, `resize { }`, `moveResize { }`, `grid { }`: newm's fine feel of gestures |
| `keys { }` | `"combination" { action }` |
| events | `onStartup { }`, `onReconfigure { }`, `everyFrame { }` |

## Everything the layout can do

Inside `keys { }` (and with `caulis`...), these are at hand:

| Call | What it does |
|---|---|
| `move(di, dj)` | focus the window beside (`-1, 0` left, `0, 1` down) |
| `moveFocusedView(di, dj)` | move the focused window by tiles |
| `resizeFocusedView(di, dj)` | make it wider / taller by tiles (negative: smaller) |
| `basicScale(ds)` | zoom: `1` out, `-1` in |
| `basicMove(di, dj)` | move the view over the wall |
| `toggleOverview(onlyActiveWorkspace = false)` | the overview |
| `toggleFullscreen()` | fullscreen and back |
| `toggleFocusedViewFloating()` | float and back |
| `moveInStack(1)` | the next window of a stack |
| `cycleViews(1)`, `cycleViews(-1)`, `gotoView(index)` | windows in turn, or by number |
| `moveWorkspace(1)`, `changeFocusedViewWorkspace(1)` | the next monitor's workspace; the window there |
| `swallowFocusedView()` | the window takes the place of the one it was started from |
| `closeFocusedView()` | close |
| `enterLauncherOverlay()` | the launcher |
| `ensureLocked(dim = true)` | lock |
| `updateConfig()` | load the configuration again |
| `terminate()` | log out |
| `launch("command")` | start a program |
| `command("name")` | one of `newm cmd`'s commands |

## newm cmd

From a terminal (or a script, or your bar program) inside newm:

```sh
newm cmd debug            # the layout, every window with its app id and place
newm cmd config           # every setting in use, and its default
newm cmd update-config    # load the configuration again
newm cmd lock
newm cmd open-launcher
newm cmd inhibit-idle     # no dimming or locking while it runs (Ctrl+C ends it)
newm cmd clean            # tidy up the wall
```

## A config.py of the old newm

newm's Python `config.py` still works: put it at `~/.config/newm/config.py` (with no
`config.kts` next to it). newm runs it with Python as before: your key bindings, rules,
`on_startup` and the like. A line or two may need changing where it relied on pywm's own
features (`pywm.renderer_mode`, `texture_shaders`, and similar settings are left out).

Moving to config.kts is worth it: it is newm's own (new settings come there first), any
editor that knows Kotlin colours it, and its mistakes are clearer. Section for section, the names are those of
config.py in camelCase (`corner_radius` → `cornerRadius`).

## When something goes wrong

- **The log**: `~/.local/state/newm/newm.log` (the start before: `newm.log.old`). For more
  detail, start with `CAULIS_LOG=3 newm`.
- **A red notice at the top**: your configuration has a mistake. The notice names the file
  and the line. Fix and save; it goes away.
- **Stuck?** Logo+Shift+Escape logs out. From another console, `pkill newm` ends it.
- **A program opens nowhere or crashes at once**: it may need X11 (`xorg-xwayland`
  installed?), or try it from a terminal inside newm to see what it says.
- **Screen sharing or the file picker does not work**: `xdg-desktop-portal` and
  `xdg-desktop-portal-gtk` (or `-wlr`) installed? newm hands them its environment by itself.
- **It does not appear in the login screen**: run `newm install-session`.
