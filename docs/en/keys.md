# Key bindings

**English** · [Русский](../ru/keys.md)

## The first one: close a window with Super+Backspace

Put this line into `~/.config/newm/config.kts` (anywhere, or inside `keys { }`) and save:

```kotlin
bind("Super+Backspace", "close-window")
```

That is all: newm takes it on at once. Logo+/ shows it among the other keys, and
`newm cmd keys` lists every key bound now.

## Writing keys

A key is written as you would say it: modifiers, then the key, joined with `+`.

```kotlin
bind("Super+Return", "terminal")
bind("Super+Shift+Q", "close-window")
bind("Ctrl+Alt+T", "launch", "foot")
bind("Print", "screenshot")
bind("Super", "overview")              // Super tapped alone
bind("Super+A Super+B", "launch", "firefox")   // a sequence: Super+A, then Super+B
```

- **Modifiers** (any case): `Super` (also `Logo`, `Win`, `Mod`, `Meta`), `Ctrl` (also
  `Control`), `Alt`, `Shift`. Their order does not matter.
- **Keys** (any case): a letter or digit, `Return` (`Enter`), `Space`, `Tab`, `Escape`
  (`Esc`), `Backspace`, `Delete`, `Insert`, `Home`, `End`, `PageUp`, `PageDown`, `Left`,
  `Right`, `Up`, `Down`, `F1` … `F35`, `Print`, `Minus` (`-`), `Equal` (`=`), `Plus`
  (`Super++`), `Comma`, `Period`, `Slash`, `Backslash`, `Grave`, `Semicolon`, `Apostrophe`,
  `BracketLeft`, `BracketRight`, `Menu`, and the laptop's keys: `VolumeUp`, `VolumeDown`,
  `Mute`, `MicMute`, `BrightnessUp`, `BrightnessDown`, `Play`, `NextSong`, `PreviousSong`.
  Any xkb name starting with `XF86` (`XF86Calculator`) works too.
- Keys are the same whatever the keyboard layout: Super+Q is Super+Q on a Russian layout.
- The laptop's keys (`XF86...`) work on the lock screen too.

A key newm cannot read is shown on screen with its line, and what it could have meant:
`config.kts:42: "Super+Bakspace": no key is called "Bakspace" (did you mean BackSpace?)`.

newm's own way of writing keys works as before: `"L-q"` is Super+Q, `"L-S-h"` Super+Shift+H,
`"L-"` Super tapped, `"C-A-t"` Ctrl+Alt+T.

## Actions

`bind(key, action, arguments...)`; the words the keys' help (Logo+/) shows come with the
action, or are your own: `bind("Super+B", "launch", "firefox", description = "Firefox")`.

| Action | Takes | What it does |
|---|---|---|
| `close-window` |  | Close the window |
| `terminal` |  | Open a terminal |
| `launch` | `command` | Start a program |
| `launcher` |  | Find and start a program |
| `focus-left` |  | Window to the left |
| `focus-right` |  | Window to the right |
| `focus-up` |  | Window above |
| `focus-down` |  | Window below |
| `next-window` |  | Next window |
| `previous-window` |  | Previous window |
| `go-to-window` | `number` | Window number n |
| `next-in-stack` |  | Next window of a stack |
| `overview` |  | All windows |
| `zoom-out` |  | Zoom out (see more) |
| `zoom-in` |  | Zoom in (see fewer) |
| `fit-window` |  | Fit the window to the screen |
| `side-by-side` |  | Two windows side by side, and back |
| `move-window-left` |  | Move the window left |
| `move-window-right` |  | Move the window right |
| `move-window-up` |  | Move the window up |
| `move-window-down` |  | Move the window down |
| `next-screen` |  | Next screen |
| `previous-screen` |  | Previous screen |
| `window-to-next-screen` |  | Move the window onto the next screen |
| `window-to-previous-screen` |  | Move the window onto the previous screen |
| `swap-on-screen` |  | Swap the window with the next one on its screen |
| `promote-to-master` |  | Make the window the screen's master |
| `next-layout` | `[name]` | Next layout of the screen |
| `window-narrower` |  | Window narrower |
| `window-wider` |  | Window wider |
| `window-shorter` |  | Window shorter |
| `window-taller` |  | Window taller |
| `fullscreen` |  | Fullscreen, and back |
| `float` |  | Float, and back |
| `swallow` |  | Swallow the window into the terminal that started it |
| `next-monitor` |  | Next monitor |
| `previous-monitor` |  | Previous monitor |
| `window-to-next-monitor` |  | Window onto the next monitor |
| `window-to-previous-monitor` |  | Window onto the previous monitor |
| `special-workspace` | `[name]` | Special workspace, and back |
| `to-special-workspace` | `[name]` | Window into the special workspace |
| `from-special-workspace` |  | Window back onto the wall |
| `clipboard` |  | Clipboard history |
| `screenshot` |  | Screenshot: choose |
| `screenshot-screen` |  | Screenshot of the screen |
| `screenshot-window` |  | Screenshot of the window |
| `key-help` |  | These keys |
| `lock` |  | Lock the screen |
| `reload` |  | Reload this configuration |
| `logout` |  | Log out |
| `caulis` | `action` | An action of Caulis (volume +5, brightness -5, mic-mute) |

`unbind("Super+S")` takes a key away (one of the template's you do not want); binding a key
again replaces what it did.

The screens' keys of the template (screens { }): Super+Space `next-layout`, Super+Shift+Space
`swap-on-screen`, Super+Ctrl+Return `promote-to-master`, Super+PageDown / Super+PageUp
`next-screen` / `previous-screen`, Super+Shift+PageDown / Super+Shift+PageUp
`window-to-next-screen` / `window-to-previous-screen`. On screens Super+Shift+arrows swap the
window with its neighbour on the screen (at the screen's edge: onto the next screen), and
Super+Ctrl+arrows resize it within its screen.

## With code of your own

Anything newm can do, as before — the key, its words for the help, and Kotlin inside `{ }`:

```kotlin
keys {
    "Super+Shift+Return"("A terminal in my projects") { launch("foot -D ~/projects") }
    "Super+Z" { basicScale(1); toggleOverview() }
}
```

Every function there: [Every setting](settings.md#everything-a-key-can-do).

## Ready-made sets

The starting `config.kts` has newm's keys, one per line, and a Hyprland-like set below them
(Super+C or Super+Backspace close, Super+R the launcher, Super+1 … Super+9 the windows in
turn): remove the `//` in front of the lines you want.

## config.json and config.lua

The same keys and action names, the action and its arguments in one text; `false` takes a
key away:

```json
"keys": {
  "Super+Backspace": "close-window",
  "Super+Return": "launch foot",
  "Super+1": "go-to-window 1",
  "XF86AudioMute": "volume mute",
  "Super+S": false
}
```

```lua
keys = {
  ["Super+Backspace"] = "close-window",
  ["Super+Return"] = "launch foot",
}
```

Besides these names, newm's layout methods (`"move -1 0"`, `"toggle_overview"`) and Caulis's
actions (`"volume +5"`, `"brightness -5"`) work as before.
