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

- The first window fills the screen. A new window goes **to the right of the one you are
  in** (the first free tile in its row), and the view moves to it.
- A new window always gets one ordinary tile, even after you made the grid finer by
  making a window smaller (see [below](#sizes)).
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

All of them can be changed: see [Your configuration](configuration.md#keys).

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

- **Logo+Ctrl+arrows** make a window bigger or smaller by tiles. Making it bigger pushes its
  neighbours aside.
- A window as small as one tile can still be made narrower or shorter: the grid is made
  finer first (each tile 2 × 2), and nothing moves on screen.
- A window is never made smaller than its program allows. A program that cannot be as small
  as its tile (Spotify will not go below 800 × 600) is drawn at its tile's shape, a little
  bigger, and shrunk to fill the tile. No empty bands at its sides.

## Floating windows

**Logo+Shift+V** takes a window off the wall: it floats above it, and Logo + drag moves it.
Dialogs and some programs (mpv, pavucontrol, the calculator) float by themselves. A floating
window keeps the size its program asks for; a program that asks for none opens at its
smallest.

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

**Logo+Shift+S** or **Print** opens the chooser. The screen stands still, and a toolbar at
the bottom offers:

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

## Leaving

**Logo+Shift+Escape** or "Log out" in the power menu. From another console (Ctrl+Alt+F3),
`pkill newm` ends it too.
