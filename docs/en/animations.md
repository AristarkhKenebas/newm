# Animations

**English** · [Русский](../ru/animations.md)

newm's animations are **off by default**: a window opening, the view moving, the overview, a
menu — every change is there at once. Fingers are still followed exactly: a swipe or a pinch
moves the view with them; only what comes after they lift (the snap) and their smoothing are
animations.

One line brings back newm's look as it always was:

```kotlin
animations { preset = "newm" }
```

The rest of this page is for choosing: as in Hyprland, all animations at once or each by name,
each with its time, its curve and (for menus and cards) its look.

## The model

```kotlin
animations {
    enabled = true                           // all on (the same as preset = "newm"); false: all off
    millis = 300                             // the global time, for those without their own
    curve = "linear"                         // the global curve
    curve("snappy", 0.05, 0.9, 0.1, 1.05)    // a Bézier curve of your own (Hyprland's, CSS's numbers)
    spring("bouncy", stiffness = 300.0, damping = 14.0)

    animation("windows", millis = 250, curve = "snappy")    // windowsIn, windowsOut, ... too
    animation("popupsIn", style = "popin 90%")
    animation("osd", speed = 2.0)            // Hyprland's speed: tenths of a second
    off("gestureSmoothing")                  // the view exactly under the fingers
}
animations { on("overview") }               // only the overview, the rest stays off
```

- `preset`: `"newm"` (all of newm's usual animations) or `"none"` (the default); `"on"` and
  `"off"` are `enabled = true` and `false`.
- `enabled`: on or off for all of them; wins over `preset`. One turned on by name (`on("camera")`)
  runs even while the rest is off; one turned off stays off while the rest is on.
- A setting for a name is for everything under it (`windows` for `windowsIn`, `windowsOut`, ...)
  unless something lower down says otherwise. What nobody says is the animation's usual (the
  table below), then the global `millis` and `curve`.
- `millis`: the time in milliseconds, 0 to 10000 (0: at once). `speed` says it in Hyprland's way,
  tenths of a second.
- `curve`: by name — `"linear"` (newm's own: the same speed all the way), `"ease-out"` (fast at
  first, gently at the end), `"ease-in-out"`, `"smooth"`, `"overshoot"` (a little past the end
  and back), or one of yours. Springs take as long as they need to rest unless a time is given.
  newm's layout clips curves at the end: an overshoot or a bouncy spring does not go past it there.
- `style`: the look of menus, cards and the keys' help as they come and go — `"fade"` (only
  see-through), `"popin 90%"` (growing from 90%), `"slide up 20"` (rising 20 units into place),
  `"slide down 8"` (coming down). The others have one look.
- `animTime` (config.py's `anim_time`) is still the global time when `millis` is not given;
  `blendTime`, `focus { animTime }`, `background { timeScale }` and `gestures { lpInertia }` are
  still the usual times of `startup`/`quit`, `border`, `backgroundMove` and `gestureSmoothing`.

## Every animation

| Name | Under | What it animates | Usual time, curve |
|---|---|---|---|
| `windows` | | every window animation below | |
| `windowsIn` | windows | a window appearing: it takes its place, the others make room | global (300 ms), linear |
| `windowsOut` | windows | a window closing: the others take its place | global, linear |
| `windowsMove` | windows | a window moved, resized, made floating or tiled, side by side, onto another workspace, swallowed and back; `clean` | global, linear |
| `fullscreen` | windows | a window going fullscreen and back | global, linear |
| `moveResize` | windows | a window moved or resized with the mouse or fingers settling on the grid when let go | global, linear |
| `camera` | | the view moving: to the next window, to the focused one, zooming in and out, making room for a bar, keeping a dragged window in sight | global, linear |
| `overview` | | the overview opening and closing, choosing a window in it | global, linear |
| `gestures` | | the two below | |
| `gestureSnap` | gestures | after a swipe or pinch, the view or the window gliding on to where it settles | from the fingers' speed (`grid { timeScale }`), linear |
| `gestureSmoothing` | gestures | the fingers' movement smoothed: the view follows a little behind, softly; the time is how soon it catches up | 75 ms (`gestures { lpInertia = 0.8 }` at 60 Hz) |
| `border` | | the focus border gliding to the focused window (with `focus { animateOnChange }`; with a window's own animation it goes along with that) | `focus { animTime }`, 300 ms, linear |
| `lock` | | the lock screen sliding in and away after unlocking — the screen is locked before it moves | global, linear |
| `launcher` | | newm's launcher coming and going | global, ease-out |
| `launcherList` | launcher | its first rows coming in one after another | 220 ms, ease (Compose's) |
| `background` | | the two below | |
| `backgroundMove` | background | the wallpaper gliding behind the view, catching up softly | `background { timeScale }`, 150 ms |
| `backgroundIn` | background | the wallpaper fading in the first time it shows | 600 ms, ease-out |
| `session` | | the three below | |
| `startup` | session | newm fading in after it started | `blendTime`, 1 s, linear |
| `quit` | session | newm fading out when it quits | `blendTime`, 1 s, linear |
| `sessionRestore` | session | windows of the last session going back to their places | 250 ms, 300 ms |
| `popups` | | the bar's menus (calendar, quick settings, tray menus, the Wi-Fi password) | |
| `popupsIn` | popups | a menu opening | 280 ms (the password 200), ease-out |
| `popupsOut` | popups | a menu closing | 180 ms (the password 140), smooth |
| `popupsResize` | popups | a menu growing as what it shows changes, parts unfolding | a spring (≈ Compose's 700, 0.9), or 260 ms |
| `notifications` | | notification cards | |
| `notificationsIn` | notifications | a card coming (and all of them as a whole) | 260 ms (240), ease |
| `notificationsOut` | notifications | a card going | 220 ms, ease |
| `osd` | | the volume and brightness slider, toasts, the card of a network or device | |
| `osdIn` | osd | it appearing | 140 ms (toast 180, card 260), ease |
| `osdOut` | osd | it going | 220 ms (toast 260, card 300), ease |
| `clipboard` / `clipboardIn` / `clipboardOut` | | the clipboard's history (Logo+V) | 260 / 180 ms |
| `keyHelp` / `keyHelpIn` / `keyHelpOut` | | the keys' help (Logo+/) | 260 / 180 ms |
| `widgets` | | small motions, below | |
| `hover` | widgets | colours changing under the pointer, the scroll bar showing | 100–150 ms, ease |
| `toggles` | widgets | switches, sliders, buttons moving to their new state | 160 ms, ease |
| `scroll` | widgets | a list scrolling to the chosen entry (launcher, clipboard) | Compose's own |
| `spinner` | widgets | the refresh icon turning while it looks, an arrow turning as a part unfolds (off: it stands still) | 900 ms a turn, linear; 260 ms |
| `brightness` | | the screen's brightness gliding to a new level (config.py's `BacklightManager`) | `anim_time`, 300 ms |

"Ease" is Compose's usual curve (a Bézier 0.4, 0, 0.2, 1). Animations of Caulis underneath —
a monitor's scale changing (`fadeScale`), Caulis' notices — follow `enabled` (and the presets
`"on"` and `"none"`, which are `enabled = true` and `false`); never Caulis' own window
animations, as newm moves its windows itself. `preset = "newm"` alone leaves them as they
were before (off): newm's look exactly. By name they are `caulis.animations { }` (see Caulis' guide).

## config.json and config.lua

The same section, with the animations' names as they are (camelCase, as in Hyprland — the one
place where JSON and Lua do not use snake_case). An animation is `true`/`false`, its look, or a
table:

```json
"animations": {
  "preset": "newm",
  "curves": { "snappy": { "bezier": [0.05, 0.9, 0.1, 1.05] }, "bouncy": { "spring": { "stiffness": 300, "damping": 14 } } },
  "windows": { "millis": 250, "curve": "snappy" },
  "popupsIn": "popin 90%",
  "osd": { "speed": 2 },
  "gestureSmoothing": false
}
```

```lua
animations = {
  enabled = true,
  curves = { snappy = { bezier = { 0.05, 0.9, 0.1, 1.05 } } },
  camera = { millis = 200, curve = "snappy" },
  lock = false,
}
```

A name that does not exist, a curve that is not there, a look an animation cannot take or a
time out of range is said when the configuration loads (with the names there are).

## Coming from Hyprland

| Hyprland | newm |
|---|---|
| `animations { enabled = yes }` | `animations { enabled = true }` |
| `bezier = snappy, 0.05, 0.9, 0.1, 1.05` | `curve("snappy", 0.05, 0.9, 0.1, 1.05)` |
| `animation = windowsIn, 1, 3, snappy, popin 80%` | `animation("windowsIn", enabled = true, speed = 3.0, curve = "snappy")` |
| `animation = workspaces, 0` | `off("camera")` (newm has no workspaces to switch: the view moves) |
| `windows`, `windowsIn`, `windowsOut`, `windowsMove`, `border`, `fade` | the same names (fades: `popups`, `notifications`, `osd`, ...) |
| `layers`, `layersIn`, `layersOut` | `popups`, `launcher`, `lock`, `osd` (newm's own panels) |

Windows in newm have no looks to choose (they glide over the wall); `style` is for menus and cards.
