# Testing newm

**English** · [Русский](../ru/testing.md)

Thank you for trying newm. This page is a list of things to try, and how to tell us what went
wrong. You do not need to do all of it: every part you try helps.

## Before you start

1. Install newm: [Install](install.md). On NixOS, `programs.newm.enable = true;` without
   `settings` is the best start: you get newm's own `config.kts`, with notes.
2. Have a terminal installed (foot, alacritty, kitty, ...).
3. The first time, start newm **from a text console** (Ctrl+Alt+F3, log in, type `newm`).
   If it does not start, you see why right there. Later, try the login screen's "newm"
   session too.
4. Know the way out: **Logo+Shift+Escape** logs out. If newm is stuck: Ctrl+Alt+F3, log in,
   `pkill newm`.
5. Keep a notebook (or a text file) at hand: what you did, what happened.

If something goes wrong, look at [When something goes wrong](troubleshooting.md) first; it may
already say why.

## The checklist

Tick what works; for what does not, write down what happened.

### Starting

- [ ] `newm --version` prints a version.
- [ ] newm starts from a text console; the wallpaper fades in, the bar shows at the top.
- [ ] A "Welcome to newm" card shows (only the very first time).
- [ ] `~/.config/newm/config.kts` was written.
- [ ] newm starts from the login screen ("newm" in its list of sessions).
- [ ] Logo+Shift+Escape logs out and brings you back.

### Keys

- [ ] **Logo+/** shows every key; any key closes it.
- [ ] **Logo+Return** opens a terminal. Open three or four windows.
- [ ] **Logo+H/J/K/L** and **Logo+arrows** go from window to window.
- [ ] **Logo+Tab**, **Logo+Shift+Tab** go through all windows.
- [ ] **Logo+−** / **Logo+=** (and Logo+U / Logo+N) zoom out and in; the windows stay usable.
- [ ] **Logo+0** makes the focused window fill the screen.
- [ ] **Logo+S** puts two windows side by side; again: back.
- [ ] **Logo+Shift+H/J/K/L** moves a window on the wall.
- [ ] **Logo+Ctrl+H/J/K/L** makes a window narrower, taller, shorter, wider, by whole tiles.
- [ ] With a window focused, open another: it opens right of it, the windows right of it move on.
- [ ] A program's dialog (Save as, a file chooser) or Telegram's photo viewer opens below its
      window; closing it gives the focus back to that window.
- [ ] `bind("Super+Backspace", "close-window")` in config.kts, saved: Super+Backspace closes a window.
- [ ] **Logo+F** fullscreen and back.
- [ ] **Logo+Shift+V** makes a window float and back.
- [ ] **Logo+Q** closes a window.
- [ ] The shortcuts work on another keyboard layout too (switch with Alt+Shift).
- [ ] The window under the pointer gets the keys without a click.

### Touchpad and mouse

- [ ] Three fingers move over the wall, in every direction.
- [ ] Four fingers up and down zoom out and in.
- [ ] Five fingers open the launcher.
- [ ] Logo + left drag moves a window; Logo + right drag resizes it (with a mouse).
- [ ] On the touchpad: Logo + press and drag moves; Logo + two fingers resizes.
- [ ] Tap to click, and two-finger scrolling in programs.
- [ ] Moving the pointer with Logo held (no button) does **not** move windows.

### The overview

- [ ] Tap **Logo** alone: every window, small.
- [ ] Moving the pointer over windows gives each the focus border in turn.
- [ ] A click on a window ends the overview on it; the click does not reach the program.
- [ ] Tap Logo again with the pointer on a window: the overview ends on that one.
- [ ] Logo + left drag moves a window there; Logo + right drag resizes it.

### The launcher

- [ ] **Logo+D** opens it, also five fingers and the bar's menu button (three lines).
- [ ] Type a few letters, Enter starts the program.
- [ ] Arrows and the pointer choose another; Escape and Logo+D close it.
- [ ] Programs you start often come first next time.

### The bar and every popup

- [ ] The window's title and icon show; a click on it opens the overview.
- [ ] The clock: a click opens the calendar; the arrows change the month.
- [ ] The tray: icons of background programs (Telegram, Discord, Steam, ...); a click, a
      right click (its menu), the menu's entries work.
- [ ] The keyboard layout (if you have two): shows EN/RU, a click switches.
- [ ] Hovering over each island shows what it is.
- [ ] The power menu: Lock works; Log out, Restart and Shut down ask once more. (Try
      Suspend and Hibernate if you can.)

### Quick settings

The grid button before the power button.

- [ ] The brightness slider changes the screen's brightness; the wheel over the button too.
- [ ] The Wi-Fi tile: the square switches Wi-Fi off and on; the words open the Wi-Fi popup.
- [ ] The Bluetooth tile: the same.
- [ ] Caffeine: on, a cup in the bar; its words offer 30 min, 1 h, 2 h, ∞.
- [ ] Light / dark: the bar, the menus, the launcher and your programs (GTK, Qt, Firefox)
      switch. Log out and in: the choice is kept.
- [ ] The screenshot tile, the clipboard tile, the keys tile open what they say.
- [ ] The colours: choose another style, and the classic colours; the bar follows.
- [ ] Power saving (if power-profiles-daemon runs).

### Wi-Fi, Bluetooth, sound, brightness

- [ ] Wi-Fi: the networks in reach show; connect to one with a password (it asks in a small
      window); disconnect; forget.
- [ ] A card shows in the middle when Wi-Fi connects or disconnects, and when a cable is
      plugged in.
- [ ] Bluetooth: pair a new device (headphones, a mouse), connect, disconnect; its battery
      shows.
- [ ] Sound: the volume keys, mute, the microphone key. A level shows at the bottom.
- [ ] The volume popup: the slider, choosing another speaker or headphones, the microphone.
- [ ] The wheel over the volume island; a middle click mutes.
- [ ] The brightness keys. A level shows at the bottom.
- [ ] The battery popup: the time left, the percent switch, the power modes.
- [ ] Play / pause / next keys with a music player.

### Lock screen and sleep

- [ ] **Logo+P** locks. A wrong password is refused; the right one unlocks.
- [ ] The volume and brightness keys work while locked; other keys do nothing.
- [ ] Suspend (power menu, or closing the lid): after waking up the screen is locked.
- [ ] Leave the computer alone 5 minutes: it locks. (After 10 it suspends.)
- [ ] With caffeine on, it does not.
- [ ] A video playing in a browser keeps the screen on.

### Screenshots

- [ ] **Logo+Shift+S** (or Print): the screen stands still, a toolbar at the bottom.
- [ ] Area: drag a frame, move it, pull its edges, Enter. A message says it was saved and
      copied; the file is in `~/Pictures/Screenshots`; paste it into a program.
- [ ] Window (W): click a window, Enter.
- [ ] Screen (S): Enter.
- [ ] The buttons: pointer (P), timer (T), "a new area each time" (M); Ctrl+C only copies;
      Escape closes.
- [ ] **Logo+Print**: the screen at once. **Alt+Print**: the window at once.
- [ ] In the overview: Logo+Shift+S starts with "Windows, whole". Drag over several windows,
      Enter: each is whole and sharp in the picture, the wallpaper behind. Click to add and
      drop windows; Ctrl+A takes all.
- [ ] In the overview, an Area over windows: sharp, not blurry.

### Clipboard history

- [ ] Copy a few texts, a picture (from a browser) and a file (from a file manager).
- [ ] **Logo+V** shows them, newest first.
- [ ] Type to search; Enter copies one again; paste it.
- [ ] Delete removes one; Ctrl+P pins one; Tab goes through the parts and kinds.
- [ ] "Clear" keeps the pinned ones. Log out and in: pinned texts are still there.
- [ ] A password copied from KeePassXC or Bitwarden is not in the list.

### Several monitors

- [ ] Plug in a monitor: it is used beside the laptop's screen, and a choice shows.
- [ ] Mirror, extend, laptop only, other only (the display key, or `newm cmd displays mirror`).
- [ ] Windows on each monitor; the pointer goes from one to the other; `moveWorkspace` if you
      bound it.
- [ ] Unplug it: can you still reach the windows that were on it?
- [ ] A different scale per monitor (`output("HDMI-A-1") { scale = 1.5 }`).

### X11 programs

- [ ] An X11 program opens (xterm, a Steam game, an older Java program, `xeyes`).
- [ ] Copy and paste between an X11 and a Wayland program, both ways.
- [ ] On a scaled screen: are X11 programs sharp and the right size?

### Fullscreen games and videos

- [ ] A video in a browser: its fullscreen button, Escape back.
- [ ] mpv: fullscreen, it plays smoothly.
- [ ] A game (Steam, native or Proton): fullscreen, smooth, the keys and mouse go to the game.
- [ ] Logo+F on a game; Logo+Tab to another window and back.
- [ ] Anything flickering or tearing? Try `newm cmd full-redraw on` and say whether it changes.

### Configuration

- [ ] Open `~/.config/newm/config.kts`, change `cornerRadius` in `view { }`, save: the windows
      change at once.
- [ ] Make a deliberate mistake (remove a quote), save: a card shows the file and the line;
      newm keeps working as before.
- [ ] Fix it and save: the card goes away, the change applies.
- [ ] Add a key of your own (`"L-b"("Browser") { launch("firefox") }`): it works and shows in
      Logo+/.
- [ ] Set `keyboard { layouts = "us,ru" }` (or your languages).
- [ ] Set `colorScheme = "light"`, save; then back to `"auto"`.

### Restart

- [ ] With several windows open (terminals in different folders, a browser), run
      `newm cmd restart keep` in a terminal. newm ends and starts again by itself; the windows come back, each in its
      place, the terminals in their folders.

## Reporting a problem

Open an issue at **https://github.com/AristarkhKenebas/newm/issues** (one problem per issue
is best). Put in it:

1. **What happened, and what you expected.** "Logo+Shift+S opened nothing" is clear; "the
   screenshots are broken" is not.
2. **The steps**, from the start, so that it can be done again.
3. **Your computer**: the distribution (NixOS 25.11, Arch, ...), the GPU (`lspci | grep -i vga`),
   a laptop or not, one monitor or several and their scale, the touchpad.
4. **`newm --version`**.
5. **How newm was started**: from a text console, from which login screen, or in a window.
6. **The layout at that moment**, if it is about windows: run `newm cmd debug` in a terminal
   inside newm and paste what it prints (`newm cmd debug > ~/newm-debug.txt`).
7. **The log**: `~/.local/state/newm/newm.log`, or `newm.log.old` if newm crashed or you
   restarted it since. Attach the file.
8. **A screenshot or a short video**, if it is about how something looks. If the problem is
   with screenshots themselves, a phone's photo of the screen is fine.

### More detail in the log

The log normally has only problems. To catch more, before you do the steps again:

```sh
newm cmd log                 # the areas: windows, focus, input, gestures, render, xwayland, ...
newm cmd log windows 3       # one area in full detail, at once (0..3)
newm cmd log windows reset   # back to normal
```

Which area: windows opening or their sizes, `windows`; which window gets the keys, `focus`;
touchpad, `gestures`; keys and the mouse, `input` (do not type passwords meanwhile);
drawing and flicker, `render`; monitors, `drm`; X11 programs, `xwayland`; copy and paste,
`clipboard`; screenshots and screen sharing, `capture`; the lock, `lock`.

newm's own parts (the bar, the sound, Wi-Fi, Bluetooth, the screenshots chooser, the
configuration) can be raised when newm starts, from a text console:

```sh
CAULIS_LOG="1,bar=3,sound=3,network=3,bluetooth=3,screenshot=3,config=3" newm
```

or everything: `CAULIS_LOG=2 newm`.

### Flicker

If something flickers or is not redrawn, run `newm cmd full-redraw on`, then try again. Say
in the issue whether the flicker was gone with it. `newm cmd full-redraw off` goes back.

### Before you send a log

The log names your windows' titles and programs. Look it over and remove what you would
rather not share. A log made with `input` at level 3 contains the keys you pressed.
