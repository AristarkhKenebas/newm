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

## Keys

| Keys | What they do |
|---|---|
| Logo+Return | open a terminal |
| Logo+d | the launcher: type a program's name, Enter starts it, Escape closes it |
| Logo+h / j / k / l (or the arrows) | focus the window to the left / below / above / to the right |
| Logo+Shift+h / j / k / l | move the focused window that way |
| Logo+Ctrl+h / j / k / l | make it narrower / taller / shorter / wider, by tiles |
| Logo+u / Logo+n | zoom out / in |
| Logo (tapped alone) | the overview: every window at once |
| Logo+Tab, Logo+Shift+Tab | every window in turn |
| Logo+t | the next window of a stack |
| Logo+f | fullscreen, and back |
| Logo+space | the window floats above the wall, and back |
| Logo+q | close the window |
| Logo+p | lock the screen |
| Logo+Shift+c | load the configuration again (saving it does this too) |
| Logo+Shift+Escape | log out |
| Brightness, volume and mute keys | work, on the lock screen too |
| Print | a screenshot (with grim) |

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
