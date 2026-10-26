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

- A new window takes the next free tile.
- **Zoom out** to see more tiles at once, **zoom in** to see fewer. The windows get their
  new size, so they stay usable at any zoom.
- **Move** over the wall to reach the windows beside you.
- **Tap Logo alone** to see every window at once (the overview).

Two programs side by side? Put them in tiles next to each other and zoom out. One at a
time again? Zoom back in.

## Two windows on one screen

Open the second program (it comes next to the first), then press **Super+S**: the two share
the screen, half each. Super+S again gives each the whole screen back. Another way: zoom out
(Super+−) to see two by two, and make windows taller or wider with Super+Ctrl+arrows.

## Keys

| Keys | What they do |
|---|---|
| Super+/ | **every key**, with what it does (any key closes it) |
| Super+Return | open a terminal |
| Super+D | the launcher: type a few letters, Enter starts it; Super+D again or Escape closes it |
| Super+H / J / K / L (or the arrows) | the window to the left / below / above / to the right |
| Super+Tab, Super+Shift+Tab | every window in turn |
| Super (tapped alone) | the overview: every window at once |
| Super+S | **two windows side by side** on one screen (the focused one and its neighbour), and back |
| Super+0 | fit: the window fills the screen |
| Super+− / Super+= (or U / N) | zoom out (see more windows) / in |
| Super+Shift+H / J / K / L (or arrows) | move the window on the wall |
| Super+Ctrl+H / J / K / L (or arrows) | make the window narrower / taller / shorter / wider, by tiles |
| Super+F | fullscreen, and back |
| Super+V | the window floats above the wall, and back |
| Super+Q | close the window |
| Super+P | lock the screen |
| Super+Shift+Escape | log out |
| Brightness, volume and mute keys | work, on the lock screen too |

Shortcuts work whatever the keyboard layout: Super+D is Super+D on the Russian layout too.
The window under the mouse gets the keys (no need to click it); `input { focusFollowsMouse = false }` turns that off.

All of them can be changed: see [Your configuration](configuration.md#keys).

## Touchpad

| Fingers | What they do |
|---|---|
| three, any direction | move over the wall |
| four, up / down | zoom out / in |
| five | the launcher |
| Logo held + one finger (or the mouse) | move the focused window |
| Logo held + two fingers | resize it |

One and two fingers stay with your programs (clicking, scrolling).

## Lock screen and launcher

- **Logo+p** locks. Type your password and press Enter.
- **Logo+d** (or five fingers) opens the launcher. It lists your installed programs: type
  a few letters, then press Enter.
