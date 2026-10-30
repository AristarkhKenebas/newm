# Changelog

All notable changes to newm on Caulis are written down here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

newm has no tagged releases yet (the Nix package calls itself 0.1.0). The sections below were
reconstructed afterwards from the Git history: they are grouped by date and by the
[Caulis](https://github.com/AristarkhKenebas/caulis) version newm moved to, not by releases.
From now on every change people would notice gets a line under [Unreleased] in the same change.

## [Unreleased]

### Changed
- Behind newm's own lock screen, programs' panels (a waybar, mako's pop-ups), menus and tooltips
  and X11's unmanaged windows are hidden as the windows are: what they show (a notification, a
  window's title) may be private. To keep them drawn: `lock { showPanels = true }` (config.json:
  `"panels": { "lock": { "show_panels": true } }`).

### Fixed
- `misc { swallow = true }` works: a window started from a terminal (mpv from foot)
  takes the terminal's place. It looked for windows started *by* the new window and so never
  found its terminal. A terminal server's new window (another foot of the same server) is not
  swallowing the others. The key action `swallow` is described as what it does: the focused
  terminal hides behind the program started from it.
- A swallowed terminal (hidden behind the window started from it) never gets the focus:
  moving the focus towards the window, closing a neighbour, or going through the windows in turn
  went to the invisible terminal. It now moves along with that window on the free wall too, and
  comes back where the window was when it closes; closing it while hidden no longer makes the
  screens close a gap that is not there.
- `keyboard { layouts = "us,ru" }` without `options` switches layouts with Alt+Shift, as the
  documentation says (before, only layouts found on the system did; set ones had no switch key).
  Options taken out of the configuration are gone after saving it, not kept.
- config.json / config.lua: a misspelt setting in `keyboard`, `cursor` or `input`
  (`keyboard.layout` for `layouts`) is told on screen with the settings there are, as in
  `compositor`, instead of being left out without a word.

### Security
- `newm cmd restart` is refused while the screen is locked. The start script starts newm again
  after a restart, and the new newm came up unlocked: any program of the session could unlock
  the screen that way, and a restart from a script (a rebuild, an update) left the computer
  open. Unlock first, then restart.
- `newm cmd open-launcher` and `newm cmd bar <popup>` (any program can send them over D-Bus)
  do nothing while the screen is locked: the launcher and the bar's popups showed over the lock
  screen (they took no input).
- A password check still running when the screen was unlocked another way (`newm cmd unlock`)
  no longer unlocks the next lock when PAM answers late: each lock takes only the answers to what
  was typed on it.

## [2026-10-30, on Caulis 0.1.7]

### Added
- The screenshot chooser shows the area chosen last again (as on a Mac): press Enter (or
  Capture) to take it again at once, move or resize it, or clear it with the new clear button on
  the toolbar (shown while there is an area) or Esc. It is forgotten when the monitors change.
  Settings: `screenshots { rememberSelection = true; rememberAcrossRestarts = false;
  escapeClearsSelection = true }` (config.json/Lua: `remember_selection`,
  `remember_across_restarts`, `escape_clears_selection`).
- `keyboard { shortcutsOnLatinLayout = true }` (config.json/Lua `shortcuts_on_latin_layout`):
  key bindings work on every layout by the key they have in the first Latin layout, as in GNOME
  and KDE — with "us,ru" Super+D, Super+Shift+M and Ctrl+Alt+T work on the Russian layout too,
  for newm's own keys and Caulis's alike. A binding on the layout's own symbol
  (`"L-Cyrillic_ve"`) still wins on that layout. `false`: only the symbols of the layout in use.
- `clipboard { pasteOnLatinLayout = true }` (`paste_on_latin_layout`): the clipboard history's
  paste keys (`onChoose = "paste"`) go in the first Latin layout while the Russian (or another
  non-Latin) layout is on, and the layout comes back right after.
- Super+drag a window of a shown special workspace onto the dimmed wall around it: it comes
  back onto the wall (dropped inside, it goes back to its place).
- `specialWorkspace { cornerRadius; padding }` (config.json/Lua `corner_radius`, `padding`; -1,
  the default: as `view { }`).

### Changed
- In the screenshot chooser the first Esc clears an area shown and a second Esc closes. To close
  with one Esc again (forgetting the area): `screenshots { escapeClearsSelection = false }`.
- Windows in a special workspace look like the wall's: rounded corners (`view { cornerRadius }`),
  room around each (`view { padding }`, twice it between two), the focus border of `focus { }`,
  and the blur and opacity of `view { rules }`. They are tiled inside `gap`, centred, as big as
  `size` says.

### Fixed
- Pasting from the clipboard history (`onChoose = "paste"`) works on the Russian layout in
  Alacritty too (it matches Ctrl+Shift+V in the layout in use only), and on Dvorak/AZERTY the
  paste key is the key that types V, not the key where a US keyboard has it.
- Super+M (`special-workspace`) in the overview showed nothing (the overview hid it again at
  once): the overview now ends and the special workspace shows. Moving a window in (not
  silently) from the overview does the same.
- `from-special-workspace` did nothing when the window behind had the focus (focus follows the
  mouse took it as the pointer crossed the dimmed wall) or with the special workspace hidden: it
  now moves the focused window out, else the one focused last in the special workspace; it
  comes back onto the wall as a new window, next to the window focused before, focused.
- A stall right after login while the mouse moved ("The main loop is stuck ... quickCursor"):
  movements waiting are merged into one (Caulis).

## [2026-10-30, on Caulis 0.1.6]

### Changed
- New windows open where the wall stays compact (`windows { newPlacement = "compact" }`, the new
  default, with screens and on the free wall alike): next to the focused window, in the free
  place that keeps the windows close together and near a square, with the smallest move of the
  view. Nothing moves aside unless no place next to the focused window is free (then as few
  windows as can be). The wall no longer grows into a long row, and opening a window next to the
  bottom-left one of four no longer leaves a hole above it. On screens a full screen sends the
  new window to a neighbouring screen with room before a new screen is put in. To get the old
  behaviour back: `windows { newPlacement = "right-of-focused" }` (config.json:
  `"windows": { "new_placement": "right-of-focused" }`).
- A child window (`childPlacement = "below"` or `"right"`) takes free room below (right of) its
  window without moving anything; only when that is taken do the windows there move aside. On
  screens, a child of a window on a full screen goes to the next screen if that has room.
- Closing a window on screens (`screens { pullBack }`) now closes the gap compactly: a
  neighbouring screen whose windows all fit into the room left moves in, and nothing else moves
  (before, every following screen gave a window to the one before it). The old way:
  `screens { pullBackMode = "shift" }`.
- The menus are drawn ahead after the start on a thread of their own (`warmup { ownThread }`,
  on): newm no longer stops for ~0.1-0.4 s at a time during the first seconds after login (the
  pointer stood still meanwhile). Drawing ahead no longer scans for Wi-Fi networks, asks
  Bluetooth or the sound server, reads power profiles or runs your quick tiles' commands at
  every login. `ownThread = false` draws them on newm's own thread as before.
- The bars are made once at the start (they were made two or three times: once more when the
  first monitor was announced and again when the wallpaper had been read), and their first
  scene is composed and drawn on a thread of its own before newm shows: newm's own thread only
  puts its pixels on screen (~20 ms instead of ~0.25-0.4 s with the pointer standing still).
- A notification's card is drawn ahead too: the first card no longer stops newm for ~0.4 s.
- Less memory: Java's heap grows only once collecting takes a third of the time
  (`-XX:GCTimeRatio=2`): the start's garbage made it 130-200 MB for a few seconds, the highest
  newm used all day; now ~30-60 MB. Memory freed by the C library is given back every 2
  minutes (`compositor { trimMemoryEveryMinutes }`, was 10), and big blocks go back the moment
  they are freed (`compositor { mallocMapAboveKb }`, 256: the pictures drawn each frame had
  kept 30-60 MB in glibc's pools). Only the EGL driver for your GPU
  is loaded (with NVIDIA's and Mesa's both installed, the other one took ~20-35 MB).
- The windows kept for the next login (or by `restart keep`) are read from disk on a thread of
  their own.
- Built on Caulis's new input thread (`compositor { inputThread }`, on): keyboards, mice and
  touchpads are read on a thread of their own and the cursor keeps moving while newm is busy.

### Added
- `windows { newPlacement = "compact" }` and `screens { pullBackMode }` (`"compact"`, `"shift"`;
  config.json/config.lua: `pull_back_mode`).
- `rule(specialWorkspace = "music")` in `view { rules { } }` (config.json/config.lua rules:
  `"special_workspace": "music"`): the window opens hidden in that special workspace, as
  `windowRule { appId("spotify"); specialWorkspace = "music" }` does. Either way it opens straight
  in there, never on the wall first. The guide shows Spotify in "music" with
  Super+M / Super+Shift+M.
- `compositor { mallocMapAboveKb }` (`malloc_map_above_kb`).
- `compositor { inputThread; glVendorByDriver; keepHiddenMenusSeconds }` (also in config.json and
  config.lua as `input_thread`, `gl_vendor_by_driver`, `keep_hidden_menus_seconds`): input on a
  thread of its own; only the EGL driver for the GPU in use is loaded (~35 MB less where NVIDIA's
  and Mesa's are both installed); a menu, the launcher or the cards not shown for 60 s give their
  memory back.
- `warmup { ownThread }`.

### Fixed
- Without a sound server the log said "The volume cannot be read" again and again (every few
  seconds): the tools' own timestamps made each failure look new. The same failure is now said
  once (then only at level 3), and asked again less and less often as before.

## [2026-10-29, on Caulis 0.1.5]

### Added
- Everything Hyprland's configuration has, or a note why not: docs/en/hyprland.md maps every
  Hyprland setting, keyword and dispatcher to newm.
- `env("XCURSOR_SIZE", "24")`: environment for the programs newm starts and the session's services.
- `permissions { rule("/usr/bin/grim", "screencopy", "allow") }`: which programs may take
  pictures of the screen, see the window list, read the clipboard, show panels, lock, arrange
  monitors; "ask" shows a card (Allow / Allow until newm quits / Deny).
- `misc { }`: window swallowing (a window started from a terminal takes its place), focus on
  activation, waking the screens on input, VRR on every monitor, middle-click paste, the
  background colour.
- `windowRule { }` and `layerRule { }`: Caulis' rules for how windows and panels behave (keep
  the screen awake, ignore maximize requests, hidden from screen sharing, blur behind a launcher).
- `input { pointerSpeed; accelProfile; leftHanded; scrollFactor; scrollMethod; touchpad { };
  device("name") { } }` and `keyboard { repeatRate; repeatDelay }`.
- config.json and config.lua: `variables = { menu = "wofi" }` and `"$menu"` in any text or key.
- Videos keep the screen on however the player asks: newm offers `org.freedesktop.ScreenSaver`
  and `org.freedesktop.PowerManagement.Inhibit` on the session bus (Firefox, Chromium, Telegram;
  a program that quits lets go), follows media players that play while a window of theirs shows
  (MPRIS; music players left out), and logind's idle inhibitors (`systemd-inhibit --what=idle`).
  `energy { inhibitFrom; mprisIgnore; logindCheckSeconds }` choose. The bar shows what keeps the
  screen on (a cup by the battery, its hint, the Caffeine tile: "Firefox: video-playing").
- The lock screen shows the keyboard layout ("EN", "RU": a click switches), "Caps Lock is on",
  PAM's own words, the wrong passwords since locking and how many more pam_faillock allows, and a
  countdown while pam_faillock has the account locked (no password is taken then: one would only
  make the lock longer). An eye shows the password while held. New `lock { }` settings:
  `layoutOnLock` ("keep" or "first"), `revealButton`, `onScreenKeyboard` (a small keyboard
  following the layout, off by default), `faillock`, `faillockConf`.

### Fixed
- The screen locked while a video played: a player that began keeping the screen on after newm
  had started counting idle time (a video started a second after the click) was not heard, and
  browsers' D-Bus requests were not answered at all.
- When caffeine or `newm cmd inhibit-idle` ended, the screen dimmed, locked or suspended at once
  for the time that had passed meanwhile; the idle time now counts from the end.
- Enter on the lock screen while a password was being checked threw away what had been typed;
  it stays now, to be checked with the next Enter.

### Changed
- The starting config.kts is laid out as Hyprland's: your programs (`val terminal`,
  `fileManager`, `browser`, `menu`, used by the keys), monitors, environment, autostart, look,
  input, gestures, layout, window rules, workspaces, permissions, misc, and the keys last.

## [2026-10-29, on Caulis 0.1.4]

### Changed
- Built on Caulis 0.1.4, fetched from Maven Central or Caulis' own Maven repository on GitHub
  Pages; the Nix dependency list covers both.
- Programs are started through Caulis, so starting programs keeps working after the system
  updated Java while newm runs.
- The sound control never disappears from the bar; the quick settings and notification buttons
  are always in the bar.

## [2026-10-29, on Caulis 0.1.3]

**Changed defaults:** window placement is newm's original again. If you liked the newer behaviour,
set it back in `config.kts` (`fillFirst`, `resizePushes`, `crispResize`, `fitDrawnSize`, and
`windows { newPlacement, childPlacement }`; see "Your configuration"). The starting `config.kts`
now turns newm's animations on (`animations { preset = "newm" }`); an existing config keeps
what it says.

### Added
- newm as greetd's login screen: its own lock screen asks for the user and password (docs and a
  NixOS option; works with any greeter user).
- Key bindings written as you say them (`"Super+Shift+Q"`, `"Ctrl+Alt+T"`, any case), actions by
  name (`bind("Super+B", "launch", "firefox")`, `unbind(...)`), ready-made sets, and
  `newm cmd keys` to list the keys bound now. newm's old way (`"L-q"`, `"L-S-h"`) still works.
- `windows { newPlacement, childPlacement, returnFocusToParent }`: dialogs and other child windows
  open below their parent by default, and closing one gives the focus back to its window.
- A full starting configuration, translated (English and Russian).
- A CC0 wallpaper and new screenshots.

### Changed
- newm starts inside `dbus-run-session` when there is no session bus.
- The screenshot tool starts on choosing an area.
- Less memory and CPU while idle (from Caulis 0.1.3, which also fixes terminals that could not start).
- The JDK AOT cache is used only for the compositor itself.
- Nix: newm is built with its own pinned Gradle (`nixpkgs-gradle`; never make it follow your
  nixpkgs); dependencies are updated with `nix run .#update-deps`; libstdc++ is in the wrapper.

## [2026-10-27, on Caulis 0.1.2]

**Changed defaults:** animations are off by default; `animations { preset = "newm" }` brings
newm's look back, or turn on each animation by name as in Hyprland.

### Added
- A real lock screen on Caulis' internal lock, with PAM working through NSS.
- A notification server of newm's own.
- Configurable animations with a `newm` preset.
- Autostart, a smooth startup (with a JDK AOT cache) and an optional startup sound.
- A special workspace, border gradients and shaders.
- Session restore (`restart keep`) without saving secrets.
- The overview follows the pointer; floating windows keep their size; a `log` command.
- A Nix package with a dependency list for offline builds and `flake.lock`.

### Fixed
- Review fixes: the lock screen covers menus, the unlock command is off, configuration loading,
  clipboard size limits, greetd, the bar and the layout.

### Security
- The lock screen can no longer be bypassed by menus over it or by an unlock command.
- The clipboard history has size limits.

## [2026-10-26, on Caulis 0.1.1] - the Kotlin port

newm rewritten in Kotlin on Caulis, one to one in how it behaves. The Python code is kept in
`legacy/` for reference.

### Added
- The port itself: the endless wall of tiles, views, layout, overview, gestures (three fingers to
  move, four to zoom), widgets, D-Bus endpoint and `newm cmd`, authentication via PAM.
- The old newm's `config.py` still works, reloaded on save, with mistakes shown on screen.
- `config.kts` as the main configuration, with a starting config that looks like newm's
  screenshot; also JSON and Lua.
- A new bar of islands: wall map, window, clock with calendar, tray with menus, Wi-Fi, Bluetooth,
  sound (with device picker), battery, power, keyboard layout, and Android-style quick settings.
- Built in: lock screen, launcher, screenshots, clipboard history (Super+V), the keys' help
  (Super+/), caffeine, power modes, hibernate.
- Window handling: resizing pushes neighbours, scratchpads, side-by-side, a finer grid for
  smaller windows, crisp resizing, programs with a smallest size drawn in their tile's shape,
  Super + left/right drag moves and resizes, `restart keep` reopens windows in their places.
- SVG and static wallpapers, soft shadows, light or dark colour scheme kept across starts.
- A Russian translation of newm and its documentation (`docs/en`, `docs/ru`).
- `install.sh`, a start script that finds a new enough Java, a development session
  (`dev/newm-dev`), the `displays` and `full-redraw` commands.
- Fixes for several issues left open in the original newm, and adopted unmerged upstream pull
  requests (`cycle_views`, `move_prev_view`/`goto_view`).

### Changed
- Frames are drawn only when something moves; animations run in the compositor.

## Before the Kotlin port (Python newm)

newm was created by Jonas Bucher ([jbuchermn/newm](https://github.com/jbuchermn/newm)) in Python
on pywm and wlroots, with contributions from Atharv Gupte, Crag, Beni Cherniavsky-Paskin and
others. Its history (2018 to 2023) is kept in this repository. A few milestones:

- **2018:** started as pywm (a wlroots compositor in C) with a Python window manager: XWayland,
  widgets, HiDPI, the first touchpad gestures.
- **2021, v0.1:** panels, configuration in `config.py`, a lock screen with PAM, blur;
  pywm moved into its own repository; packaged for the AUR.
- **December 2021, v0.2:** pywm's workspaces, monitor configuration, virtual outputs.
- **May 2022, v0.3:** focus borders, a D-Bus endpoint for commands and authentication, touchpad
  gestures behind a gesture provider, `newm.helper` for backlight and similar, window swallowing,
  a display-manager session entry.
- **October 2022:** the last code changes; the original project is no longer maintained.

[Unreleased]: https://github.com/AristarkhKenebas/newm/compare/c734841...HEAD
[2026-10-29, on Caulis 0.1.4]: https://github.com/AristarkhKenebas/newm/compare/3c83df5...c734841
[2026-10-29, on Caulis 0.1.3]: https://github.com/AristarkhKenebas/newm/compare/26682c1...3c83df5
[2026-10-27, on Caulis 0.1.2]: https://github.com/AristarkhKenebas/newm/compare/7e247e1...26682c1
[2026-10-26, on Caulis 0.1.1]: https://github.com/AristarkhKenebas/newm/compare/d120fcc...7e247e1
