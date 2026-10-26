# Every setting

**English** · [Русский](../ru/settings.md)

The `config.kts` newm writes lists every setting with a note. This page is the short
reference. In Lua and JSON the names are the same, in snake_case (`cornerRadius` →
`corner_radius`).

## Sections

| Section | Settings |
|---|---|
| (top level) | `animTime`, `blendTime`, `cornerRadius` (the screen's corners), `lockOnWakeup` |
| `background { }` | `path`, `anim`, `timeScale` |
| `view { }` | `cornerRadius`, `padding`, `fullscreenPadding`, `sendFullscreen`, `acceptFullscreen`, `floatingMinSize`, `borderWsSwitch`, `fillFirst`, `fade`, `unfocusedOpacity`, `rules { }` |
| `focus { }` | `enabled`, `color`, `distance`, `width`, `animTime`, `animateOnChange` |
| `panels { }` | `topBar { }`, `bottomBar { }`, `bar { }`, `lock { }`, `launcher { }`: see below |
| `keyboard { }` | `layouts`, `variants`, `options`, `model`, `numLock` |
| `cursor { }` | `theme`, `size`, `hideAfter` |
| `input { }` | `tapToClick`, `naturalScroll`, `focusFollowsMouse`, `xwayland`, `titleBars` |
| `output("name") { }` | `scale`, `width`, `height`, `refreshHz`, `x`, `y`, `transform`, `anim` |
| `energy { }` | `idleTimes`, `screensOffAfter`, `suspendCommand`, `onIdle { }` |
| `gestures { }` | `bind(GestureAction..., modifier, kinds)`, `lpInertia`, `lpFreq`, `enabled`, `scalePx` |
| `tuning { }` | `swipe { }`, `swipeZoom { }`, `move { }`, `resize { }`, `moveResize { }`, `grid { }`: how gestures feel |
| `keys { }` | `"combination" { action }` |
| events | `onStartup { }`, `onReconfigure { }`, `everyFrame { }` |

### panels

| Part | Settings |
|---|---|
| `topBar { }`, `bottomBar { }` | newm's own bars: `enabled`, `style` (`"islands"`, `"classic"`), `height`, `margin`, `cornerRadius`, `theme`, `font`, `fontSize`, `left`, `center`, `right`, `clock`, `date`, `islandColor`, `textColor`, `accentColor`, `iconSize`, `showPercent`, `showPassive`, `titleWidth`, `blur`, `texts { }`; the classic look also `background`, `foreground` (see [Bars](bars.md)) |
| `bar { }` | a bar program: `cmd`, `cwd`, `visibleNormal`, `visibleFullscreen` |
| `lock { }` | the lock screen: `w`, `h` (parts of the screen), `cornerRadius`, `program` (a screen locker instead: `"swaylock"`, `"gtklock"`, `"hyprlock"`) |
| `launcher { }` | the launcher: `w`, `h`, `cornerRadius`, `cmd` |

## Everything a key can do

Inside `keys { }`:

| Call | What it does |
|---|---|
| `move(di, dj)` | focus the window beside (`-1, 0` left, `0, 1` down) |
| `moveFocusedView(di, dj)` | move the focused window by tiles |
| `resizeFocusedView(di, dj)` | make it wider or taller by tiles (negative: smaller) |
| `basicScale(ds)` | zoom: `1` out, `-1` in |
| `basicMove(di, dj)` | move the view over the wall |
| `toggleOverview(onlyActiveWorkspace = false)` | the overview |
| `toggleFullscreen()` | fullscreen, and back |
| `toggleFocusedViewFloating()` | float, and back |
| `moveInStack(1)` | the next window of a stack |
| `cycleViews(1)`, `cycleViews(-1)`, `gotoView(index)` | windows in turn, or by number |
| `moveWorkspace(1)`, `changeFocusedViewWorkspace(1)` | the next monitor's workspace; send the window there |
| `swallowFocusedView()` | the window takes the place of the one it was started from |
| `closeFocusedView()` | close the window |
| `enterLauncherOverlay()` | the launcher |
| `ensureLocked(dim = true)` | lock |
| `updateConfig()` | load the configuration again |
| `terminate()` | log out |
| `launch("command")` | start a program |
| `command("name")` | one of `newm cmd`'s commands |

In Lua and JSON an action is a text. `"terminal"` opens the terminal newm found (`$TERMINAL`, else the first installed of alacritty, foot, kitty, ...). Otherwise it is the call's name in snake_case and its arguments,
`"move -1 0"`, `"toggle_overview only_active_workspace=true"`, `"launch firefox"`. The
compositor's own actions work too: `"volume +5"`, `"brightness -5"`, `"mic-mute"`, `"screenshot"`.

## newm cmd

From a terminal, a script or a bar inside newm:

```sh
newm cmd debug            # the layout: every window with its app id and place, the monitors
newm cmd config           # every setting in use, and its default
newm cmd update-config    # load the configuration again
newm cmd lock
newm cmd open-launcher
newm cmd inhibit-idle     # no dimming or locking while it runs (Ctrl+C ends it)
newm cmd clean            # tidy up the wall
newm cmd restart          # start newm again (dev/newm-dev uses it)
newm cmd bar power        # a popup of the bar: calendar, volume, network, battery, power, tray 0, close
```

Other commands of `newm`: `newm help`, `newm version`, `newm install-session`.
