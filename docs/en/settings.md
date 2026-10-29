# Every setting

**English** · [Русский](../ru/settings.md)

The `config.kts` newm writes lists the common settings with a note on each. This page is the
whole reference. A setting you leave out has the default shown here; the starting
`config.kts` sets some of them differently (it is the one you have, so read it there).

In Lua and JSON the names are the same, in snake_case (`cornerRadius` → `corner_radius`,
`topBar` → `top_bar`; one exception: `numLock` is `numlock`). `newm cmd config` prints every
setting in use.

## Top level

| Setting | Default | What |
|---|---|---|
| `animTime` | `0.3` | how long moving and zooming take, seconds (the animations' global time; they run only when on) |
| `blendTime` | `1.0` | fading in at the start and out at the end, seconds (the animations `startup`, `quit`) |
| `animations { }` | off | every animation, Hyprland-like: `preset = "newm"` (newm's usual look), `enabled`, `millis`, `curve`, `curve(name, …)`, `spring(name, …)`, `animation(name, enabled, millis, curve, style, speed)`, `on(name)`, `off(name)` — see [Animations](animations.md) |
| `cornerRadius` | `18` | the screen's own corners rounded, pixels (0: square) |
| `lockOnWakeup` | `true` | lock before the computer sleeps (sleep waits for the lock screen, see `lock { sleepWait }`) and when it wakes up |
| `colorScheme` | `"auto"` | `"dark"`, `"light"`, or `"auto"`: the quick settings' last choice (dark until one) |
| `language` | `"auto"` | newm's menus: `"auto"` (the system's), `"en"`, `"ru"` |
| `enableUnlockCommand` | `false` | `newm cmd unlock` unlocks without the password: only on a computer for testing |
| `greeterUser` | `"greeter"` | the user greetd runs its greeter as (newm as a login screen, see [newm as the login screen](greeter.md)); started by greetd, newm knows it is the greeter by greetd's socket whatever the user is called |
| `greeterCommand` | this newm | what greetd starts once the greeter let a user in; its words are split at spaces (no quotes: a path with spaces needs a script of its own) |
| `timeFormat` | `"HH:mm"` | how a time of day is written (java.time: `"h:mm a"`): the notifications' history, the lock screen, the clipboard |
| `terminal` | `$TERMINAL`, else the first installed (alacritty, foot, kitty, ...) | the terminal newm opens: the `"terminal"` action, `defaultTerminal()`, programs that run in one (Terminal=true); `"foot"`, `"kitty -1"` |

## background { }

| Setting | Default | What |
|---|---|---|
| `path` | `"resource:wallpaper.jpg"` | the picture (`"~/Pictures/wall.jpg"`; an SVG stays sharp); `""` for none |
| `mode` | `"follow"` | `"follow"`: moves and zooms with the view; `"static"`: stands still |
| `anim` | `true` | `false` is the same as `mode = "static"` |
| `timeScale` | `0.15` | how quickly it follows, seconds (the animation `backgroundMove`) |

## view { }

| Setting | Default | What |
|---|---|---|
| `cornerRadius` | `12.0` | windows' corners, pixels |
| `padding` | `6.0` | the gap around each window, pixels |
| `fullscreenPadding` | `0.0` | the gap around a fullscreen window |
| `sendFullscreen` | `true` | programs are told when they are fullscreen |
| `acceptFullscreen` | `true` | programs may ask to be fullscreen (a video's button) |
| `floatingMinSize` | `true` | a floating window whose program asks for no size opens at its smallest |
| `borderWsSwitch` | `10.0` | how far past a monitor's edge a window is dragged before it moves to the next |
| `fillFirst` | `true` | the first window fills the screen (`false`: a quarter, as the old newm) |
| `placeBesideFocused` | `true` | a new window goes right of yours (`false`: the first free tile in view) |
| `fade` | `true` | windows fade in and out |
| `unfocusedOpacity` | `1.0` | how opaque windows without the focus are |
| `resizePushes` | `true` | a window made bigger pushes its neighbours aside (`false`: stacks on them) |
| `crispResize` | `true` | a window being resized is drawn at its own size, not stretched |
| `minSizeKeepsShape` | `true` | a program bigger than its tile is drawn in the tile's shape and shrunk (`false`: its smallest size, fitted inside) |
| `minSizeShrink` | `0.6` | how far such a program may be shrunk (1: never smaller than it allows) |
| `stickyFullscreen` | `false` | a fullscreen window stays fullscreen when the view moves or zooms away from it |
| `rules { view -> ... }` | | rules for some windows: `rule(float, floatSize, floatPos, opacity, blur, focus)`, see [Your configuration](configuration.md#some-windows-float-some-get-blur) |

## focus { }

| Setting | Default | What |
|---|---|---|
| `enabled` | `true` | a line around the focused window |
| `color` | `"auto"` | `"auto"` (the colours' accent), `"#RRGGBB"` or `"#RRGGBBAA"` |
| `distance` | `4.0` | how far outside the window, pixels |
| `width` | `2.0` | its width, pixels |
| `animTime` | `0.3` | how long its own glide takes (the animation `border`) |
| `animateOnChange` | `false` | the line glides to the next window |
| `urgentColor` | `""` | the border of a window wanting attention while it has not the focus (focusing it takes the wish away), as Hyprland's; `""`: none |
| `shader` | `""` | a GLSL file of your own, `vec4 paint(vec2 at)`, read again when saved (see below) |
| `animate` | `true` | `false`: a shader's own time (and a turning gradient) stands still |

`color` and `urgentColor` take Hyprland's way of writing border colours too: a gradient
`"rgba(33ccffee) rgba(00ff99ee) 45deg"`, `"rgb(ff5555)"`, `"0xffaabbcc"`.

A border shader gets what Caulis gives every border shader: `at` (0..1 in the box), `time`,
`size`, `pixel`, `progress` (0..1 along the border, clockwise from the top left), `borderWidth`,
`cornerRadius`, `focused`, `urgent`, `colors[0]` (= `color`), `colors[1]` (= `urgentColor`).
A mistake in it is shown in a notice with its line, and the border is drawn in `color`
meanwhile. Three to start from:

```glsl
// A rainbow turning around the window
vec4 paint(vec2 at) {
    float hue = fract(progress + time * 0.2);
    return vec4(clamp(abs(mod(hue * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0), 1.0);
}
```

```glsl
// A pulsing glow in the configured colour (red while urgent)
vec4 paint(vec2 at) {
    vec3 base = urgent > 0.5 ? vec3(1.0, 0.3, 0.3) : colors[0].rgb;
    return vec4(base * (0.55 + 0.45 * sin(time * 3.0)), 1.0);
}
```

```glsl
// Marching ants
vec4 paint(vec2 at) {
    float dash = step(0.5, fract((progress * 2.0 * (size.x + size.y) - time * 40.0) / 16.0));
    return mix(vec4(0.1, 0.1, 0.1, 1.0), vec4(1.0, 0.85, 0.2, 1.0), dash);
}
```

## specialWorkspace { }

Hyprland's special workspaces: a layer of windows above the wall, on the focused monitor at
the screen's own place (the wall's camera does not move it), with a small tiling layout of
its own; everything behind it dimmed (and blurred). Keys: `toggleSpecialWorkspace("name")`
(`"special"` without a name), `moveToSpecialWorkspace("name", silent = false)`,
`moveOutOfSpecialWorkspace()`; in config.json/Lua the actions `special-workspace toggle [name]`,
`move-to-special [name] [silent]`, `move-out-of-special`, `move-to-workspace special:name`;
from a terminal `newm cmd special [name]`. A window moved in leaves the wall; moved out, it
comes back onto the wall as a new window. The overview shows the wall only: a shown special
workspace hides when it opens. The lock hides it too.

| Setting | Default | What |
|---|---|---|
| `dim` | `0.4` | how much darker the wall behind it is |
| `blurRadius` | `0` | the wall behind it blurred this far (0: not) |
| `blurPasses` | `2` | how smooth the blur is |
| `gap` | `40` | room between it and the screen's edges, pixels |
| `size` | `1.0` | how much of the screen it takes (0.2 to 1) |
| `animation` | `"scratchpads"` | the animation its windows come and go with (off unless animations are on) |
| `hideWhenEmpty` | `true` | it hides when its last window closes or leaves |
| `newWindowsJoin` | `true` | windows opening while it is shown go into it |
| `clickOutsideHides` | `true` | a click on the dimmed wall hides it |
| `hideInOverview` | `true` | the overview hides it |

## colors { }

| Setting | Default | What |
|---|---|---|
| `source` | the colours menu's choice, else `"wallpaper"` | `"wallpaper"`, `"nord"` (the classic colours) or `"#5b8cc8"` |
| `style` | `"tonal"` | `"tonal"`, `"vibrant"`, `"expressive"`, `"neutral"`, `"monochrome"` |

## panels { }

### topBar { }, bottomBar { }: newm's own bars

| Setting | Default | What |
|---|---|---|
| `enabled` | `false` (the starting `config.kts` turns the top one on) | the bar at all |
| `style` | `"islands"` | `"islands"` or `"classic"` (the old newm's strip of texts) |
| `height` | `32` | an island's height |
| `margin` | `6` | the gap to the screen's edges |
| `cornerRadius` | `12` | the islands' corners |
| `theme` | `"auto"` | `"auto"` (light or dark as everything), `"light"`, `"dark"` |
| `font`, `fontSize` | the system's sans-serif, `13` | the text |
| `left`, `center`, `right` | `["window"]`, `["clock"]`, `["tray", "layout", "bluetooth", "network", "volume", "battery", "notifications", "power"]` | the modules of each island (see [Bars](bars.md)) |
| `clock`, `date` | `"HH:mm"`, `"EEE d MMM"` | java.time patterns; `date = ""` for none |
| `islandColor`, `textColor`, `accentColor` | from the colours | your own colours |
| `iconSize` | `16` | the icons' size |
| `showPercent` | `true` | the volume's and the battery's percent |
| `showPassive` | `false` | tray icons that say they are idle |
| `titleWidth` | `360` | the window title's widest |
| `trayHide` | `["nm-applet", "blueman"]` | tray icons left out, by name or title |
| `menuButton` | `true` | the launcher's button first on the left (only when `left` is newm's own list) |
| `quickButton` | `true` | the quick settings' button before the power button (only when `right` is newm's own list) |
| `notificationsButton` | `true` | the notifications' bell before the quick settings' button (only when `right` is newm's own list) |
| `layoutNames` | `{}` | the keyboard layouts' labels: `mapOf("us" to "EN")` (others: `us` EN, else the code's first two letters) |
| `outputs` | `[]` | the monitors with the bar (`["eDP-1"]`); empty: all |
| `clockHint` | `"EEEE, d MMMM yyyy"` | the clock's hint; `""` for none |
| `hintDelayMs` | `800` | how long the pointer stays before a hint shows |
| `module("name") { }` | | a module of your own: `text { }` or `command("...")`, `everySeconds` (5), `timeoutSeconds` (5), `icon`, `onClick { }`, `onMiddle { }`, `onRight { }`, `onScroll { dy -> }` (see [Bars](bars.md#modules-of-your-own)) |
| `on("module") { }` | | what `click`, `middle`, `right`, `scrollUp`, `scrollDown` do on a module (each an action or `null`; `off()`: nothing) |
| `wheel { }` | the focus moves that way | the wheel over the empty space and the clock: `up { }`, `down { }`, `left { }`, `right { }`; `off()` |
| `blur` | `true` | what is behind it is blurred |
| `texts { listOf(...) }` | | the texts of the module `"texts"` |
| `background`, `foreground` | `"#000000B3"`, `"#FFFFFF"` | the classic look's colours |

### bar { }: a bar program of your own

| Setting | Default | What |
|---|---|---|
| `cmd` | | the program (`"waybar"`): started, started again if it ends, room made for it |
| `cwd` | | the folder it starts in |
| `visibleNormal` | `true` | shown normally |
| `visibleFullscreen` | `false` | shown above fullscreen windows |

`topBarProgram { }` and `bottomBarProgram { }` take the same settings, for a bar program of
the old newm's kind (`newm-panel-basic bar`): its room is made at the top, or at the bottom.
`visibleNormal` and `visibleFullscreen` there win over those of `bar { }`. In Lua and JSON they
are `panels.top_bar.cmd`, `panels.top_bar.visible_normal`, ... next to newm's own bar's
settings.

### lock { }, launcher { }

Both can also be written at the top level: `lock { }` is `panels { lock { } }`, `launcher { }`
is `panels { launcher { } }`.

| Setting | Default | What |
|---|---|---|
| `w`, `h` | lock `0.7`, `0.6`; launcher `0.8`, `0.8` | the lock screen's size, as parts of the screen; for the launcher, a launcher program's (`cmd`) only |
| `cornerRadius` | lock `50.0`; launcher `0.0` | the lock screen's corners; for the launcher, a launcher program's |
| `program` (lock) | | a screen locker instead of newm's own: `"swaylock -f"`, `"gtklock"`, `"hyprlock"` |
| `cmd` | | a panel program instead of newm's own (the old newm's panels) |
| `cwd` | | the folder that program starts in |

newm's own lock screen:

| Setting | Default | What |
|---|---|---|
| `background`, `text`, `accent` | the bar's colours, light or dark as everything | `"#RRGGBB"` or `"#RRGGBBAA"` |
| `clock` | the top-level `timeFormat` (`"HH:mm"`) | java.time pattern; `""` for none |
| `date` | `"EEEE, d MMMM"` | java.time pattern; `""` for none |
| `showOnLock` | `listOf("clock", "battery")` | what the lock screen shows besides the password; the bar, popups, tray and notifications never show over it, and only the lock screen takes input |
| `pamService` | `"login"` | the PAM service the password is checked with (`/etc/pam.d/<name>`) |
| `checkTimeout` | `60` | seconds a password check may take before the lock screen asks again. With pam_fprintd before pam_unix in the PAM service, PAM waits for a finger first (30 s): keep this longer |
| `sleepWait` | `3` | seconds sleep waits for the lock screen (or `program`) to show before it goes on anyway (0..30) |

newm's own launcher:

| Setting | Default | What |
|---|---|---|
| `width`, `height` | `720`, `560` (smaller on a small screen) | its size, pixels |
| `top` | `0.16` | where its top is, as a part of the screen's height |
| `frequent` | `6` | how many of the programs you start most come first (0: none) |
| `remember` | `true` | which programs you start is kept (`~/.local/state/newm/launcher.json`); `false`: nothing written |
| `hide` | `[]` | programs left out, by name or .desktop id: `listOf("htop", "org.gnome.Extensions")` |
| `runTyped` | `false` | Enter with nothing found runs what you typed as a command |
| `entries { "Lock"("Lock the screen") { ensureLocked() } }` | | entries of your own, with what they do |
| `keys { "C-Return" { runTyped() } }` | | keys of your own in it: `next()`, `previous()`, `first()`, `last()`, `start()`, `runTyped()`, `close()` |

The launcher's keys without `keys { }`: ↓, Tab, Ctrl+N and Ctrl+J choose the next program;
↑, Shift+Tab, Ctrl+P and Ctrl+K the one before; Enter starts it; Escape closes.

## keyboard { }

| Setting | Default | What |
|---|---|---|
| `layouts` | the system's (see [Your configuration](configuration.md#keyboard-layouts)) | `"us"`, `"us,ru"` |
| `variants` | | one per layout: `",phonetic"` |
| `options` | `"grp:alt_shift_toggle"` when there are several layouts | xkb options: `"grp:caps_toggle,caps:escape"` |
| `model` | `"pc105"` | the keyboard's model |
| `numLock` | `false` | Num Lock on from the start |
| `bindingModifiers` | `"xkb"` | modifiers in key bindings as the layout and its options say (`caps:super`), or `"physical"`: the printed keys |

## compositor { }

| Setting | Default | What |
|---|---|---|
| `quitWhileLocked` | `false` | Ctrl+Alt+BackSpace quits on a locked screen too (it would get round the lock) |
| `lockFailure` | `"own-lock"` | a lock that does not show: Caulis's own plain lock screen until a locker unlocks (Enter starts the locker again), or `"notice"`: only said |
| `lockShowMillis` | `3000` | how long a lock may take to show before it counts as failed |
| `stopProgramsOnQuit` | `true` | programs started inside are asked to end when newm quits |

## cursor { }

| Setting | Default | What |
|---|---|---|
| `theme` | Caulis's own | an installed cursor theme: `"Adwaita"`, `"Bibata-Modern-Ice"` |
| `size` | the theme's | its size: `24`, `32` |
| `hideAfter` | `0` | hide it after this many seconds without moving (0: never) |

## input { }

| Setting | Default | What |
|---|---|---|
| `tapToClick` | the touchpad's own (the starting `config.kts`: `true`) | a tap on the touchpad clicks |
| `naturalScroll` | the device's own (the starting `config.kts`: `false`) | scrolling moves the content, as on phones |
| `focusFollowsMouse` | `true` | the window under the pointer gets the keys (`false`: a click) |
| `xwayland` | `true` | X11 programs |
| `titleBars` | `false` | programs draw their own title bars |

## output("name") { }

One block per monitor, by its connector (`"eDP-1"`, `"HDMI-A-1"`; `newm cmd debug` lists them).

| Setting | What |
|---|---|
| `scale` | `1.25`, `2.0` |
| `width`, `height`, `refreshHz` | the mode: `2560`, `1440`, `144.0` |
| `x`, `y` | where it is, in pixels |
| `transform` | turned: 0 normal, 1 by 90°, 2 by 180°, 3 by 270° (4 to 7: mirrored) |
| `anim` | `false`: its wallpaper stands still |

## overview { }

| Setting | Default | What |
|---|---|---|
| `input` | `false` | the windows take clicks in the overview (`true`: as the old newm) |
| `choose` | `true` | a click chooses a window and ends the overview |
| `followPointer` | `true` | the window under the pointer is the chosen one (its border; what Logo + drag moves) |

## mouse { }

| Setting | Default | What |
|---|---|---|
| `superMove` | `"button"` | Logo + left drag moves a window, Logo + right drag resizes; `"motion"`: Logo and the mouse alone move it |
| `moveButton` | `"left"` | the button that moves: `"left"`, `"right"`, `"middle"`, `"side"`, `"extra"`, or `"none"` |
| `resizeButton` | `"right"` | the button that resizes (or `"none"`) |
| `barWheelLines` | `1.0` | lines of the wheel (a touchpad's small moves added up) that make one step on the bar |

## screenshots { }

| Setting | Default | What |
|---|---|---|
| `enabled` | `true` | `false`: the keys do nothing |
| `folder` | `"~/Pictures/Screenshots"` | where they are saved |
| `save`, `copy` | `true`, `true` | saved as a file, put on the clipboard |
| `pointer` | `false` | the pointer in the picture |
| `toast` | `true` | a short message when one is taken |
| `start` | the one used last | the chooser starts with `"region"`, `"window"` or `"screen"` |
| `delay` | `0` | seconds to wait |
| `selection` | `"adjust"` | an area: `"adjust"` (moved and resized after) or `"new"` (each drag a new one) |
| `overviewStart` | `"windows"` | in the overview: `"windows"` (several, whole), `"region"`, `"window"`, `"screen"`, `""` (the last used) |
| `sharpWindows` | `true` | an area of the overview is put together from the windows at their own size |
| `sharpBackground` | `"wallpaper"` | behind them: `"wallpaper"`, `"none"`, `"#RRGGBB"`, `"theme"` |
| `fileName` | `"Screenshot from {date} {time}"` | with `{date}`, `{time}`, `{kind}` |
| `format`, `quality` | `"png"`, `92` | or `"jpg"` and its quality |
| `then` | | a command run with each file, `{file}` being it |

## clipboard { }

| Setting | Default | What |
|---|---|---|
| `enabled` | `true` | the history at all |
| `size` | `50` | how many things are kept (pinned ones on top of these) |
| `pictures`, `files` | `true`, `true` | pictures and copied files kept too |
| `keepPins` | `true` | pinned texts kept after a restart |
| `ignore` | `["keepassxc", "bitwarden", "1password"]` | programs whose copies are never kept |
| `maxPictureMb`, `maxText` | `16`, `200000` | the biggest picture, the longest text |
| `clearAtOnce` | `true` | "Clear" clears at once; `false`: it asks first ("Really clear?"), and a second click or Enter clears |
| `onChoose` | `"copy"` | choosing an entry: `"copy"` puts it on the clipboard; `"paste"` also pastes it into the window you were in (Ctrl+V sent to it) |
| `pasteKey`, `terminalPasteKey` | `"C-v"`, `"C-S-v"` | the keys `"paste"` sends, to a terminal the second (`"S-Insert"` works too) |
| `width`, `height` | `980`, `620` (smaller on a small screen) | the window's size, pixels |
| `top` | `0.13` | where its top is, as a part of the screen's height |
| `pinKey` | `"C-p"` | the key that pins the chosen entry |

## notifications { }

| Setting | Default | What |
|---|---|---|
| `enabled` | `true` | newm shows notifications (org.freedesktop.Notifications); `false` leaves them to another daemon |
| `seconds` | `6` | how long a card stays when the program does not say (critical ones stay until closed) |
| `doNotDisturb` | `false` | no cards, the history only (the bell's switch changes it while newm runs) |
| `readAfterFocusSeconds` | `3` | a program's window focused this long: its notifications are read, out of the history |
| `keepHours`, `keep` | `24`, `100` | the history keeps them this many hours, and this many at most |
| `remember` | `true` | the history kept across restarts (`~/.local/state/newm/notifications.json`, yours alone) |
| `corner` | `"top_right"` | where the cards are: `"top_left"`, `"top"`, `"bottom_right"`, `"bottom_left"`, `"bottom"` (the compositor's notices too) |
| `width`, `gap`, `maxCards` | `380`, `10`, `3` | the cards' width, the gap between them and to the edge, the most at once |
| `monitor` | `"focused"` | the cards' monitor: `"focused"`, `"pointer"`, `"first"`, or a name |
| `criticalSeconds` | `0` | how long a critical one's card stays (0: until closed) |
| `showInFullscreen` | `false` | cards over a fullscreen window too |
| `rule { n -> }` | | a rule (several: each in turn): `mute()` (the history only), `hide()` (not kept), `urgency(0..2)`, `seconds(n)`; in JSON a table (see [Notifications](configuration.md#notifications)) |
| `onNotification { n -> }` | | told of each notification (`n.app`, `n.summary`, `n.body`, `n.urgency`) |
| `takeOver` | `"stale"` | another daemon (mako, dunst) holding the notifications: `"stale"` closes one left from another session (started before newm, or on another display), `"always"` closes any, `"never"` leaves it (newm's own stay off until it quits) |

## sound { }, osd { }

| Setting | Default | What |
|---|---|---|
| `sound { maxVolume }` | `150` | the volume slider's top, percent |
| `sound { wheelStep }` | `2` | percent a notch of the wheel over the bar's volume |
| `sound { setVolume, getVolume }` | wpctl, else pactl | your own commands (`{percent}`) |
| `osd { connections }` | `"cards"` | Wi-Fi, a cable or Bluetooth came or went: `"cards"`, `"text"`, `"off"` |
| `osd { seconds, position, distance }` | `1.3`, `"bottom"`, `130` | how long the volume's or brightness' level shows; where (`"top"`, `"center"`), how far from that edge |
| `osd { toastSeconds, cardSeconds }` | `1.6`, `2.8` | how long a short message and a connection's card show |
| `osd { inFullscreen }` | `false` | connections shown over a fullscreen window too |
| `osd { onConnection { e -> } }` | | told of each connection (`e.kind`, `e.connected`, `e.name`) |

## The bar's popups: power { }, brightness { }, battery { }, calendar { }, quickSettings { }, caffeine { }, network { }

| Setting | Default | What |
|---|---|---|
| `power { entries }` | `["lock", "suspend", "hibernate", "logout", "reboot", "poweroff"]` | the power menu's entries, in order |
| `power { confirm }` | `["logout", "reboot", "poweroff"]` | those that ask once more |
| `power { lock, suspend, hibernate, logout, reboot, poweroff }` | newm's; `energy { suspendCommand }`; systemctl (loginctl without it) | a command each; or `onLock { }`, `onSuspend { }`, ... an action |
| `brightness { wheelStep, minimum }` | `5`, `1` | percent a notch of the wheel over the quick settings' button; the lowest the bar sets |
| `brightness { device }` | the first | a name in `/sys/class/backlight` |
| `brightness { set, get }` | brightnessctl; `/sys/class/backlight` | your own commands (`{percent}`, `{device}`) |
| `battery { low, critical }` | `20`, `10` | the warning and danger colours below these |
| `battery { onCritical { } }` | | runs once each time it falls to `critical`, not charging |
| `calendar { weekend }` | `["SATURDAY", "SUNDAY"]` | the days drawn faint |
| `calendar { firstDay }` | the language's | the week's first day (`"MONDAY"`) |
| `calendar { weekNumbers }` | `false` | the weeks' numbers |
| `quickSettings { tiles }` | `["wifi", "bluetooth", "caffeine", "dark", "screenshot", "clipboard", "colors", "keys", "dnd", "power_saver"]` | the tiles, in order |
| `quickSettings { tile("name", icon, label, wide) { onClick { }; isOn { } } }` | | a tile of your own (`isOn("command")`: on when it succeeds) |
| `caffeine { choices }` | `[30, 60, 120, 0]` | the caffeine tile's times, minutes (0: until turned off) |
| `network { wifiName }` | nmcli, else iwgetid | a command saying the Wi-Fi's name |
| `network { wifiNameEverySeconds }` | `15` | how often the Wi-Fi's name is asked (the air is never scanned for it) |
| `network { scanOnOpen }` | `true` | opening the Wi-Fi popup looks for networks (false: only its refresh button) |
| `tray { maxItemsPerProgram }` | `8` | how many tray icons one program may show |
| `tray { maxIconSize }` | `1024` | the biggest picture (pixels each way) taken from a tray program |

## energy { }

| Setting | Default | What |
|---|---|---|
| `idleTimes` | `[120, 300, 600]` | seconds without input until: "idle" (told to `onIdle`), lock, suspend |
| `dimAfter` | `idleTimes[0]` | seconds until "idle" is told to `onIdle` (to dim); `0`: never |
| `lockAfter` | `idleTimes[1]` | seconds until the screen locks; `0`: never |
| `suspendAfter` | `idleTimes[2]` | seconds until the computer is suspended; `0`: never |
| `after(900) { launch("playerctl pause") }` | | something of your own after so many seconds (once, until you are back) |
| `screensOffAfter` | `0` | seconds until the screens go dark (0: never) |
| `suspendCommand` | `"systemctl suspend"` | how to suspend |
| `onIdle { what -> }` | | told `"idle"`, `"idle-lock"`, `"idle-presuspend"`, `"idle-suspend"`, `"active"`, `"sleep"`, `"wakeup"` |

## gestures { }

| Setting | Default | What |
|---|---|---|
| `bind(GestureAction.SWIPE, null, "swipe-3")` | three fingers | move over the wall |
| `bind(GestureAction.SWIPE_TO_ZOOM, null, "swipe-4")` | four fingers | zoom |
| `bind(GestureAction.MOVE_RESIZE, "L", "move-1", "swipe-2")` | Logo + drag / two fingers | move, resize |
| `bind(GestureAction.LAUNCHER, null, "swipe-5")` | five fingers | the launcher |
| `lpInertia` | `0.8` | smoother (and slower) with more, 0..1 (the animation `gestureSmoothing`: off, the fingers are followed exactly) |
| `lpFreq` | `60.0` | how often a gesture is looked at, per second |
| `enabled` | `true` | touchpad gestures at all |
| `dbusEnabled` | `true` | gestures sent by another program over D-Bus |
| `scalePx` | `800.0` | finger movement, in pixels, that makes one screen |
| `on("swipe-4", "down") { toggleOverview() }` | | a gesture of your own, see below |

`on(kind, direction, description, modifier) { action }` runs the action once the fingers have
gone far enough that way: `kind` is `"swipe-3"`, `"swipe-4"` or `"swipe-5"`; `direction` is
`"up"`, `"down"`, `"left"`, `"right"`, or `null` for any way; `modifier` is a key held with it
(`"L"`: Logo), which `"move-1"` and `"swipe-2"` need (alone they are the mouse and two-finger
scrolling). The ways you bind are yours; the other ways of that gesture keep what `bind` gives
them: with `on("swipe-4", "down") { toggleOverview() }`, four fingers up still zoom.
`description` is said in the keys' help (Logo+/).

```kotlin
gestures {
    on("swipe-4", "down", "All windows") { toggleOverview() }
    on("swipe-3", "up", "Launcher", modifier = "L") { enterLauncherOverlay() }
}
```

The keys' help and the hints shown while a gesture runs say the gestures as they are bound
(`bind`, `on`, `mouse { }`).

`tuning { swipe { }, swipeZoom { }, move { }, resize { }, moveResize { }, grid { }, interpolation { } }`
holds the finer feel of the gestures (`gestureFactor`, `gridM`, `gridOvr`, `lockDist`, `hyst`,
`minDist`, `timeScale`, `sizeAdjustment`), as the old newm had them. `grid { debug = true }`
writes each step of the snapping to the log.

## session { }

The windows open again after `newm cmd restart keep`, and, if you want, after you log in —
once newm is shown, after the programs of `autostart`, one after another.

| Setting | Default | What |
|---|---|---|
| `restoreAtLogin` | `false` | the windows of your last session open again when you log in (kept every minute and when you log out, so also after the computer was turned off) |
| `maxAgeMinutes` | `10` | windows kept by `restart keep` longer ago than this are not opened again (0: no limit) |
| `maxWindows` | `20` | at most this many programs are started; a notice says how many were left, and the launcher offers them ("Reopen ... windows", or `newm cmd restore-rest`) |
| `exclude` | `[]` | programs never opened again, by app id or program name: `listOf("steam", "org.telegram.desktop")` |
| `staggerMs` | `300` | the programs start one after another, this many milliseconds apart (0: all at once) |
| `arguments` | `"options"` | what of a program's command line is kept: `"options"` (no files, addresses or what looks like a password), `"all"`, `"none"`; a terminal always comes back alone, in its folder |
| `interpreters` | `python`, `java`, `node`, `perl`, `ruby`, `bash`, `sh`, `dash`, `zsh`, `fish`, `lua`, `luajit`, `bun`, `deno`, `php`, `electron`, `wine`, `wine64`, `mono`, `dotnet` | programs that run another one: with `"options"` their program is kept too (`python3 app.py`, `electron /usr/lib/obsidian/app.asar`, `wine game.exe`); version digits at the end of a name do not count (`python3.12`, `electron43`) |
| `keepValues` | `-P`, `-profile`, `--profile`, `--profile-directory`, `--user-data-dir`, `-workdir`, `--class`, `--name`, `--app-id`, `-cp`, `-classpath`, `--class-path`, `--module-path` | options whose value, written after them, is kept with them (`firefox -P work`, `code --user-data-dir /x`); other values written apart are taken for files and left out |
| `secretWords` | `pass`, `password`, `passwd`, `passphrase`, `pwd`, `pw`, `token`, `secret`, `key`, `apikey`, `auth`, `authorization`, `cookie`, `cookies`, `cred`, `creds`, `credential`, `credentials`, `otp`, `pin` | an option is left out, with its value, when a word of its name is one of these (`--api-key`), or its name holds one of five letters or more (`--accesstoken`). Also left out always: an address with a user and password (`http://bob:pw@proxy`), any address in an option (`--app=https://…`), a value that looks like a token (a long word of letters and digits) |
| `secretShortOptions` | `-p` | options with their value right after them (`-phunter2`, as mysql takes it): left out — but not a word of letters and dashes (`-private-window`) |

## autostart { }

Programs started with the session, one after another, once newm is shown — once per session:
saving the configuration starts only the entries you added (and stops restarting the ones you
took out; they keep running). See [The start](startup.md).

| Setting | Default | What |
|---|---|---|
| `stagger` | `400.milliseconds` | the time between two programs starting (`stagger_ms` in Lua and JSON) |
| `afterReveal` | `true` | start them once newm has faded in (false: right away, while dark) |
| `desktopEntries` | `false` | also the `.desktop` files of `~/.config/autostart` and `/etc/xdg/autostart` (Hidden, OnlyShowIn/NotShowIn with `newm`, TryExec are honoured) |
| `afterTimeoutMillis` | `10000` | how long an entry waits for those it starts `after` |
| `app("prog", "arg") { }` | | a program and its arguments (no shell) |
| `script("~/start.sh") { }` | | an executable file |
| `shell("sleep 1; notify-send hi") { }` | | a piece of shell |

Inside an entry's braces: `delay = 2.seconds`, `ifNotRunning = true` (not started when a
program of its name runs already), `restart = OnCrash(maxTimes = 3, within = 1.minutes)` (or
`Always(...)`, `Never`), `after("waybar")`, `env("X", "y")`, `dir = "~"`, `name = "..."`.
In Lua and JSON: `{ "app": ["nm-applet", "--indicator"], "delay_ms": 2000, "if_not_running": true,
"restart": "on_crash", "after": ["waybar"], "env": { "X": "y" }, "dir": "~" }`, or `"shell"`,
`"script"` (with `"arguments"`) instead of `"app"`.

## reveal { }

The start: the screen stays black until newm is ready (its first frames drawn, the bars drawn
once), then newm fades in. The fade has its own setting: `animations { enabled = false }` does not
change it (`reveal { fadeMillis = 0 }` shows newm at once).

| Setting | Default | What |
|---|---|---|
| `enabled` | `true` | false: newm shows as soon as it draws |
| `fadeMillis` | `600` | how long the fade takes (`fade_ms`) |
| `maxWaitMillis` | `4000` | the longest the screen stays dark (`max_wait_ms`) |
| `color` | `"#000000"` | the colour until then |

## startupSound { }

A sound when newm starts, made by newm itself (no recording), or a file of yours. `newm cmd
play-startup-sound` (or `play-startup-sound glass`) plays it now, to try.

| Setting | Default | What |
|---|---|---|
| `enabled` | `true` (`false` on the login screen, newm as greetd's greeter) | play it |
| `preset` | `"airy"` | `"airy"`, `"classic"`, `"glass"` or `"soft"` |
| `file` | none | a sound file of yours instead (`"~/sounds/hello.ogg"`: ogg, wav, flac — what the player plays) |
| `volume` | `0.8` | 0 to 1 |
| `delayMillis` | `0` | wait this long more (`delay_ms`) |
| `at` | `"reveal"` | `"reveal"` (as newm fades in) or `"start"` (right away) |
| `player` | pw-play, paplay, aplay | your player's words: `listOf("mpv", "--no-video", "{file}")` (`{volume}`, `{percent}` too) |

## Events

| Call | When |
|---|---|
| `onStartup { }` | once newm has started: start programs here |
| `onReconfigure { }` | after the configuration was loaded again |
| `everyFrame { }` | about 30 times a second (keep it short) |
| `launch("command")` | starts a program, anywhere in the configuration |

## Everything a key can do

Inside `keys { }`:

| Call | What it does |
|---|---|
| `launch("command")` | start a program |
| `move(di, dj)` | focus the window beside (`-1, 0` left, `0, 1` down) |
| `moveFocusedView(di, dj)` | move the focused window by tiles |
| `resizeFocusedView(di, dj)` | make it wider or taller by tiles (negative: smaller) |
| `basicScale(ds)` | zoom: `1` out, `-1` in |
| `basicMove(di, dj)` | move the view over the wall |
| `fitFocusedView()` | zoom so the window fills the screen |
| `sideBySide()` | the window and its neighbour side by side, and back |
| `toggleOverview(onlyActiveWorkspace = false)` | the overview |
| `toggleFullscreen()` | fullscreen, and back |
| `toggleFocusedViewFloating()` | float, and back |
| `moveInStack(1)` | the next window of a stack |
| `cycleViews(1)`, `cycleViews(-1)`, `gotoView(index)` | windows in turn, or by number |
| `moveWorkspace(1)`, `changeFocusedViewWorkspace(1)` | the next monitor's wall; send the window there |
| `swallowFocusedView()` | the window takes the place of the one it was started from |
| `closeFocusedView()` | close the window |
| `enterLauncherOverlay()` | the launcher |
| `toggleKeyHelp()` | the keys' help |
| `toggleClipboard()` | the clipboard's history |
| `screenshot()`, `screenshotScreen()`, `screenshotWindow()` | the chooser; the screen; the window |
| `restoreRest()` | the windows left out when the session came back (more than `session { maxWindows }`) |
| `scratchpad("name")` | show or hide a scratchpad |
| `ensureLocked(dim = true)` | lock |
| `updateConfig()` | load the configuration again |
| `terminate()` | log out |
| `command("name", "argument")` | one of `newm cmd`'s commands: `command("bar", "power")` |
| `caulis.run("volume +5")` | an action of Caulis: `"volume -5"`, `"volume mute"`, `"mic-mute"`, `"brightness +5"`, `"keyboard-layout next"` |

In Lua and JSON an action is a text. `"terminal"` opens the terminal newm found. Otherwise it
is the call's name in snake_case and its arguments: `"move -1 0"`,
`"toggle_overview only_active_workspace=true"`, `"launch firefox"`, `"screenshot"`,
`"toggle_clipboard"`. Caulis's actions are written as they are: `"volume +5"`,
`"brightness -5"`, `"mic-mute"`.

## newm cmd

From a terminal, a script or a bar inside newm (it talks to the running newm over the
session bus):

```sh
newm cmd debug                  # the layout: every window with its app id and place, the monitors
newm cmd config                 # every setting in use
newm cmd update-config          # load the configuration again
newm cmd lock                   # lock the screen
newm cmd open-launcher          # the launcher (close-launcher closes it)
newm cmd launcher firefox       # start a program as the launcher does
newm cmd inhibit-idle           # no locking or sleeping while it runs (Ctrl+C ends it)
newm cmd caffeine [30|on|off]   # the screen stays on: for minutes, until off, or off now
newm cmd clean                  # tidy up the wall
newm cmd bar power              # a popup of the bar: calendar, volume, network, bluetooth,
                                #   battery, power, quick, colors, tray 0, close
newm cmd colors [nord|wallpaper|#5b8cc8]   # the colours in use, or new ones
newm cmd displays extend        # a second monitor: extend, mirror, laptop, other
newm cmd scratchpad drop        # show or hide a scratchpad
newm cmd special music          # show or hide a special workspace ("special" without a name)
newm cmd restart                # end newm so that it starts again
newm cmd restart keep           # the same, and your windows open again after it
newm cmd restore-rest           # the windows left out then (more than session { maxWindows })
newm cmd autostart              # the programs of autostart { } and how each is
newm cmd play-startup-sound     # the startup sound now (or a preset: play-startup-sound glass)
newm cmd log                    # the log's areas
newm cmd log input 3            # one area of the log in detail (0..3), now; "log input reset": back
newm cmd full-redraw on         # every frame drawn whole (for flicker); off: back
newm cmd unlock                 # unlock without the password: off unless enableUnlockCommand = true
```

`newm cmd unlock` is off by default: any program you run could otherwise unlock the screen
without your password. Turn it on (`enableUnlockCommand = true`, `enable_unlock_command` in
Lua and JSON) only on a computer for testing; otherwise it answers "Disabled".

Other commands of `newm`: `newm help`, `newm --version`, `newm install-session`.
