# Everyday use

**English** · [Русский](../ru/using.md)

## The wall

Most window managers put your windows on the screen. newm puts them on an endless **wall**
of tiles, and the screen shows a part of it:

```
   ┌────────┬────────┬────────┐
   │ editor │ terminal        │      ← a window takes one tile or several
   ├────────┼────────┼────────┤
   │ browser         │ music  │
   └────────┴────────┴────────┘
        the screen shows 2×2 tiles of it; zoom out to see more
```

- The screen shows 2 × 2 tiles to start with; the first window takes one, in the middle. A
  new window opens **to the right of the one you are in** (the windows right of it move one
  tile on), and the view moves to it. A dialog or a photo viewer of a program opens **below
  its window** (see [Where windows open](#where-windows-open)).
- A new window gets one tile (more if its program cannot be that small).
- **Zoom out** to see more tiles at once, **zoom in** to see fewer. The windows get their
  new size, so they stay usable at any zoom.
- **Move** over the wall to reach the windows beside you.
- **Tap Logo alone** to see every window at once (the [overview](#the-overview)).

Two programs side by side? Put them in tiles next to each other and zoom out. One at a
time again? Zoom back in.

## Two windows on one screen

Open the second program (it comes next to the first), then press **Logo+S**: the two share
the screen, half each. Logo+S again gives each the whole screen back. Another way: zoom out
(Logo+−) to see two by two, and make windows taller or wider with Logo+Ctrl+arrows.

## Keys

"Logo" is the key with the Windows or Super logo. **Logo+/** shows all of these on screen
(any key or a click closes it).

| Keys | What they do |
|---|---|
| Logo+/ | every key, with what it does |
| Logo+Return | open a terminal |
| Logo+D | the launcher |
| Logo+H / J / K / L (or the arrows) | the window to the left / below / above / to the right |
| Logo+Tab, Logo+Shift+Tab | every window in turn |
| Logo+T | the next window of a stack (windows on the same tiles) |
| Logo (tapped alone) | the overview: every window at once |
| Logo+− / Logo+= (or U / N) | zoom out (see more windows) / in |
| Logo+0 | fit: the window fills the screen |
| Logo+S | **two windows side by side** on one screen (the focused one and its neighbour), and back |
| Logo+Shift+H / J / K / L (or arrows) | move the window on the wall |
| Logo+Ctrl+H / J / K / L (or arrows) | make the window narrower / taller / shorter / wider, by tiles |
| Logo+Q | close the window |
| Logo+F | fullscreen, and back |
| Logo+Shift+V | the window floats above the wall, and back |
| Logo+V | the clipboard's history |
| Logo+Shift+S, Print | a screenshot: choose what |
| Logo+Print | a screenshot of the screen, at once |
| Alt+Print | a screenshot of the window, at once |
| Logo+P | lock the screen |
| Logo+Shift+C | load the configuration again |
| Logo+Shift+Escape | log out |
| Brightness, volume, mute, microphone keys | work, on the lock screen too |
| Play, next, previous keys | the music player (through playerctl) |
| The display key (F7 on many laptops) | how a second monitor is used: mirror or extend |
| Ctrl+Alt+F1 ... F12 | the text consoles |

Shortcuts work whatever the keyboard layout: Logo+D is Logo+D on the Russian layout too.
The window under the mouse gets the keys (no need to click it); `input { focusFollowsMouse = false }`
turns that off.

All of them can be changed, in words: `bind("Super+Backspace", "close-window")` — see [Key bindings](keys.md).

## Touchpad

| Fingers | What they do |
|---|---|
| three, any direction | move over the wall |
| four, up / down | zoom out / in |
| five | the launcher |
| Logo held + press and drag | move a window |
| Logo held + two fingers | resize the focused window |

One and two fingers stay with your programs (clicking, scrolling). While a gesture runs, a
short hint at the bottom of the screen says what it does.

## The mouse with Logo

- **Logo + left drag** moves the window under the pointer, anywhere: on the wall, in the
  overview, a floating window.
- **Logo + right drag** resizes it.

Logo and the mouse moved alone do nothing (`mouse { superMove = "motion" }` brings back the
old newm's way, where they moved the window).

## The overview

Tap **Logo** alone: you see every window at once, small.

- The windows take no clicks there: a **click chooses a window**, and the overview ends on it.
- The window under the pointer is the chosen one: it gets the focus border. Tap Logo again
  and the overview ends on that window.
- **Logo + left drag** moves a window there, **Logo + right drag** resizes it.
- **Logo+Shift+S** there takes a picture of several windows, whole (see [Screenshots](#screenshots)).

## Sizes

- **Logo+Ctrl+arrows** make a window bigger or smaller by whole tiles; the window goes over
  its neighbours, which make a stack (Logo+T goes through it). `view { resizePushes = true }`
  makes them move aside instead. A window of one tile made narrower becomes two wide,
  reaching left — as in newm.
- A program that cannot be as small as its tile keeps its smallest size, drawn smaller to
  fit its tile.
- Zooming (Logo+−/=, four fingers) shows more or fewer tiles; the programs get their new size
  when the zoom settles on whole tiles.

## Where windows open

- A new window opens **next to the one you are in**, in the free place that keeps your windows
  close together and near a square (a third window goes below the second, not to the end of a
  long row), with the smallest move of the view; nothing moves aside unless no place next to
  yours is free. `windows { newPlacement = "right-of-focused" }` opens it right of yours, the
  windows right of it moving one tile on; `"original"` brings back newm's: the first free tile in view.
- A program's child window — a dialog, a file chooser, a photo viewer — opens **below its
  window** (into free room there, else what is below moves down). A window without a parent counts as one too when the
  program of the focused window opens it (Telegram's photo viewer). `windows { childPlacement
  = "over" }` floats it over its window instead, as newm did; `"right"` puts it beside.
- Closing a child window gives the focus back to its window.

All of it: [Every setting](settings.md#windows-).

## Floating windows

**Logo+Shift+V** takes a window off the wall: it floats above it, and Logo + drag moves it.
Some programs (mpv, pavucontrol, the calculator) float by a rule, windows of a fixed size by
themselves. A floating window keeps the size its program asks for; a program that asks for
none opens at its smallest.

## Fullscreen

**Logo+F**, or the program's own button (a video's). The bar steps aside. Logo+F again comes
back.

## The launcher

**Logo+D**, five fingers, or the bar's menu button (three lines, at the left). Type a few
letters, Enter starts the first; the arrows or the pointer choose another. With nothing
typed it shows the programs you start most, then all. Escape or Logo+D closes it.

## The bar

Islands at the top: the window, the clock, the tray and the computer's state. Each one opens
a popup with a click. See [Bars](bars.md) for all of it. In short:

- the **clock**: a calendar;
- **Wi-Fi**, **Bluetooth**, **sound**, **battery**: their popups;
- the **quick settings** (a grid of four squares, before the power button): the brightness,
  Wi-Fi, Bluetooth, caffeine (the screen stays on), light or dark, a screenshot, the
  clipboard's history, the colours, the keys' help, and power saving;
- the **power** button: lock, suspend, hibernate, log out, restart, shut down. The last three
  ask once more.

## Light or dark

The quick settings' sun-and-moon tile switches newm and your programs between light and
dark. The choice is kept for the next start (it starts dark until you choose). To fix it in
the configuration: `colorScheme = "light"`.

## Screenshots

**Logo+Shift+S** or **Print** opens the chooser. The screen stands still: drag a frame over
the part you want (a window is chosen with W or the toolbar's button, not by where the pointer
happens to be). The toolbar at the bottom offers:

| Kind | Key | What |
|---|---|---|
| Area | A or R | drag a frame; then move it, or pull its edges |
| Window | W | click a window |
| Screen | S | the whole monitor |
| Windows, whole | G | only in the overview: several windows, each whole |

Enter or Space takes it; Ctrl+C only copies it; Escape closes. P puts the pointer in the
picture, T sets a timer, Tab goes through the kinds, M switches between moving the frame
and drawing a new one with each drag. The arrows move the frame (Shift: by 10 pixels, Ctrl:
resize it). Each button names itself and its key when the pointer is on it.

**Logo+Print** takes the screen at once, **Alt+Print** the window.

**In the overview**, the chooser starts with "Windows, whole": drag a frame over windows
(every window it touches is in the picture, whole) or click windows to add or drop them;
Shift adds a frame to those chosen, Ctrl+A takes all. The picture is put together from the
windows at their own size, sharp, in the places they have in the overview, over the
wallpaper.

Screenshots are saved to `~/Pictures/Screenshots` and copied. A short message says so.

## The clipboard's history

**Logo+V** shows what you copied lately, texts, pictures and files, newest first.

- Type to search. Enter or a click copies one again; Delete takes one away.
- Ctrl+P pins one: pinned ones stay on top and are never pushed out.
- Tab goes from part to part of the window (search, the kinds: All / Text / Pictures /
  Pinned, the list, Clear); the arrows or h j k l move inside one.
- "Clear" removes all but the pinned ones; its arrow clears only one kind.

It is kept in memory only (pinned texts excepted), and nothing from password managers
(KeePassXC, Bitwarden, 1Password) is kept.

## Lock, idle and sleep

- **Logo+P** locks (or "Lock" in the power menu). Type your password and press Enter.
- With no input, the screen locks after 5 minutes, the screens go dark after 5½, and the
  computer suspends after 10. Programs that keep the screen on (a video player) hold this off; so does
  **caffeine** in the quick settings (30 minutes, an hour, two, or until turned off).
- After a suspend the screen is locked.

## Messages

- **Cards** in the top right corner, below the bar, in the bar's colours: newm's own messages
  (a mistake in the configuration, the welcome). Programs' notifications need a notification
  program, such as mako or dunst (see [Bars](bars.md#other-panels)).
- **Levels** at the bottom: the volume and the brightness when they change.
- **A card in the middle**, for a moment: Wi-Fi, a cable or a Bluetooth device came or went.

## Several monitors

Each monitor has a wall of its own. The pointer goes from one to the next. A monitor plugged
in for the first time is used beside the laptop's screen, and a choice is shown: mirror,
extend, only the laptop, only the other one. The display key (F7 on many laptops) or
`newm cmd displays mirror` (or `extend`, `laptop`, `other`) changes it later.

**Logo+Alt+arrows** go to the monitor that way (the window focused there last; an empty
monitor: nothing has the keys, and a new window opens there). **Logo+Shift+Ctrl+arrows** take
the focused window onto it: it keeps its size and lands next to the window focused there last.
The special workspace shows on the monitor you work on.
A window dragged past the monitor's edge (Logo + drag, or the fingers' move) goes onto the
next monitor's wall where it is let go, keeping its size; on screens it takes the screen and
the place there, and the screen it left closes the gap. Zoomed out it reaches as far.

## Leaving

**Logo+Shift+Escape** or "Log out" in the power menu. From another console (Ctrl+Alt+F3),
`pkill newm` ends it too.

## Differences from the original newm

newm's window management is the original's (the Python newm, jbuchermn/newm): the same grid
of whole tiles, the same zooming, swiping, snapping, overview, fullscreen and placement
algorithm. What is different on purpose:

- **Screens** (`screens { }`, on by default): the wall is in whole screens, each holding
  `windowsPerScreen` windows (2) tiled by a layout (columns; rows, grid, master, dwindle,
  monocle, ...); the next window opens on the next screen, and the view moves by whole
  screens only. Under the bars only the bars show (`windowsUnderBar`). `screens { enabled =
  false }` gives the original's free wall of tiles, where everything below holds.
- **Where new windows open**: next to the focused window where the wall stays compact, nothing
  moving (the original: the first free tile in view) — `windows { newPlacement = "original" }` for that.
- **Child windows** (dialogs, viewers) are tiled below their window, and the focus goes back
  to it when they close (the original floated every window with a parent over its middle) —
  `windows { childPlacement = "over" }` for that. A window of the focused program without a
  parent counts as its child (`childBySameApp`).
- **Moving between windows from a dialog** starts from its window (the original started
  from the wall's corner), and focusing a dialog keeps its window in view.
- **Moving and resizing with Logo + a mouse button** (left: move, right: resize) instead of
  Logo and the mouse alone (`mouse { superMove = "motion" }` for that); in the overview,
  windows take no clicks — a click chooses one.
- A window's **largest height** is kept when it is resized (the original took the smallest
  width there: a slip).
- **Bars at the left or right** of the screen keep their room too.
- Fullscreen left by moving or zooming away tells the program it is not fullscreen any more.
- Additions that change nothing by default: `view { fillFirst, resizePushes, crispResize,
  fitDrawnSize, fade, unfocusedOpacity }`, `fitFocusedView` (Logo+0), `sideBySide` (Logo+S),
  special workspaces, scratchpads, the bar, lock screen, launcher, notifications, clipboard,
  screenshots, animations by name and autostart.

Gone: the finer grid of an earlier newm in Kotlin (a window of one tile split into 2 × 2
smaller ones, and growing back), with its settings `minSizeKeepsShape` and `minSizeShrink`
(still accepted, doing nothing). It worked badly; a better way of smaller windows will come
later.
