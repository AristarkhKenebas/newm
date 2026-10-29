# When something goes wrong

**English** · [Русский](../ru/troubleshooting.md)

## First: where newm says what is wrong

- **The log**: `~/.local/state/newm/newm.log`. The start before is in `newm.log.old` (each
  start moves the last log there), so after a crash look at `newm.log.old`.
- **More detail**: the log normally has only problems. For one area in detail, while newm
  runs: `newm cmd log input 3` (`newm cmd log` lists the areas; `newm cmd log input reset`
  goes back). For everything from the start: `CAULIS_LOG=2 newm` (or `3`: very much). newm's
  own parts log under their own names, which can be raised at the start too:
  `CAULIS_LOG="1,bar=3,sound=3,network=3,bluetooth=3" newm`.
- **Start it from a text console** (Ctrl+Alt+F3, log in, type `newm`) when it does not start
  from the login screen: what newm says before its log is open shows right there.
- **How to get out**: Logo+Shift+Escape logs out. From another console (Ctrl+Alt+F3, log in),
  `pkill newm` ends it (its start script passes it on to newm). Still there (Java itself stuck):
  `pkill -KILL -f newm.MainKt`.

A log at level 3 of `input` records the keys you press (never while the screen is locked).
Do not share one in which you typed a password.

## newm does not start, or the screen stays black

Started from a text console, newm says what is missing before it gives up:

| It says | What to do |
|---|---|
| `Missing system libraries: ...` | install them (see [Install](install.md#what-you-need)) |
| `No GPU was found (/dev/dri is missing)` | install your GPU's driver: Mesa for AMD and Intel, NVIDIA 545 or newer. On NixOS: `hardware.graphics.enable = true;` |
| `libseat is missing` | install seatd / libseat (it works with systemd-logind) |
| `XDG_RUNTIME_DIR is not set` | log in normally (a login screen or a text console), not with `su` or `sudo` |
| `Running as root` | start it as your normal user |
| `Could not open the seat` | start it from a text console of a logged-in user |
| `newm needs Java 26 or newer` | install Java 27 (on Arch: `sudo pacman -S jdk-openjdk`), or set `JAVA_HOME` to it |
| `The GPU driver lacks ...` | the graphics driver is too old or missing |
| `No D-Bus session bus` (a card, and the log) | no tray, notifications or portals: install D-Bus (the package with `dbus-run-session`); newm's start script then starts newm inside it by itself, unless `NEWM_DBUS=off` |

More cases:

- **On NVIDIA**: the driver needs kernel modesetting (`nvidia-drm.modeset=1`; on NixOS
  `hardware.nvidia.modesetting.enable = true;`).
- **In a virtual machine**: newm needs a virtual GPU with 3D (QEMU / virt-manager: virtio
  with 3D acceleration turned on). Without it there is no GPU to draw with.
- **Everything is slow, the pointer lags**: look for `Drawing on the processor` in the log.
  The GPU's driver did not start, and newm draws without it. Check the driver as above.
- **Back at the login screen at once**: start newm from a text console to see why, then look
  at `newm.log.old`.
- **A black screen with a pointer**: wait a few seconds (the first start compiles the
  configuration). Still black? Ctrl+Alt+F3, log in, look at the log, `pkill newm`.

## The configuration

- **A card in the top right says the configuration has a mistake.** It names the file and
  the line. Fix the line and save; the card goes away. Until then newm keeps the settings
  that worked. Lost? Rename `~/.config/newm` and start again: newm writes a fresh one.
- **No keys, or no bar, with a `config.json` or `config.lua`** (for example from NixOS's
  `programs.newm.settings` or home-manager): such a file is the whole configuration. Without
  `keys` there are no keys; without `top_bar = { enabled = true }` no bar. Start from
  `~/.config/newm/examples/` or use `config.kts`. To get out meanwhile: Ctrl+Alt+F3,
  `pkill newm`.
- **A change to `programs.newm.settings` (NixOS) does nothing**: it reaches only users who
  have no `~/.config/newm` yet. Remove (or rename) yours to start from the system's again.
- **A key does nothing**: `keys { }` is your whole list. Is the key in it? Logo+/ shows the
  keys that have words. In a running newm, `newm cmd log input 3` writes each key pressed to
  the log.

## The keyboard

- **The wrong layout, or no second layout**: without `keyboard { layouts }` newm takes the
  system's layouts from `/etc/X11/xorg.conf.d` (written by `localectl set-x11-keymap`), else
  your language's next to English. NixOS's `services.xserver.xkb` is not read. Set it
  yourself:

  ```kotlin
  keyboard { layouts = "us,ru"; options = "grp:alt_shift_toggle" }
  ```

  Alt+Shift switches then; the layout's island in the bar does too.
- **Logo shortcuts do not work in a nested newm** (newm in a window inside another desktop):
  the desktop outside takes Logo first. Try it from a text console.

## The lock screen

- **The password is not accepted**: newm checks it with PAM's `login` service
  (`/etc/pam.d/login`). Is that file there? If `login` asks for a fingerprint first (fprintd),
  touch the reader or remove fingerprints from `login`'s stack. Started with
  `CAULIS_LOG="1,auth=2" newm`, the log says "PAM Backend: not successful" for each password
  refused.
- **Locked and cannot get in**: switch to another console (Ctrl+Alt+F3), log in,
  `pkill newm`. Your open programs end with it.
- **A screen locker of your own** (swaylock, ...): `panels { lock { program = "swaylock -f" } }`.
- **It locks or sleeps too soon**: `energy { idleTimes = listOf(120, 300, 600) }` is in
  seconds: "idle", lock, suspend. `listOf(600, 1200)` locks after 20 minutes and never
  suspends. Caffeine in the quick settings holds all of it off for a while.

## The bar and its popups

Each popup reads the computer through a usual service. Missing one, that popup is empty or
does nothing (the rest works):

- **Wi-Fi popup says "NetworkManager is not running"**: newm uses NetworkManager. Start it
  (`systemctl enable --now NetworkManager`; NixOS: `networking.networkmanager.enable = true;`).
  With iwd or systemd-networkd alone the popup cannot show networks.
- **No Bluetooth island**: there is no Bluetooth adapter, or BlueZ is not running
  (`systemctl enable --now bluetooth`; NixOS: `hardware.bluetooth.enable = true;`).
- **The volume popup is empty, "No sound device"**: it uses `pactl`. Install it (Arch:
  `libpulse`, Debian: `pulseaudio-utils`, NixOS: `pkgs.pulseaudio`) and run PipeWire with its
  pulse part, or PulseAudio.
- **The volume keys do nothing**: they use `wpctl` (WirePlumber).
- **The brightness does not change**: install `brightnessctl`. The brightness slider shows only
  when `/sys/class/backlight` has a screen.
- **No power modes**: power-profiles-daemon is not running.
- **A tray icon is missing**: nm-applet and blueman are hidden on purpose (`trayHide`). Others
  need the program to support the tray (StatusNotifierItem); some show an icon only when
  started with a flag of their own.
- **Programs' notifications do not show**: newm shows only its own messages. Start a
  notification program: `onStartup { launch("mako") }`.

## Programs

- **A program does not open, or closes at once**: it may be an X11 program. Is Xwayland
  installed (`xorg-xwayland` on Arch, `xwayland` on Debian; the NixOS module turns it on)?
  Start the program from a terminal inside newm to see what it says.
- **An X11 program looks small**: newm does not stretch X11 programs, so they stay sharp. It
  tells them the screen's real DPI; a program that ignores it stays small. Most programs can
  run as Wayland ones instead: newm already asks GTK, Qt, Firefox and Electron programs
  started from it to. For one started elsewhere, try `--ozone-platform=wayland` (Electron,
  Chromium) or `QT_QPA_PLATFORM=wayland`.
- **A program looks blurry**: is it an X11 program on a screen scaled by a fraction (1.25)?
  Run it as a Wayland program (above).
- **A window is bigger than its tile, or squeezed**: some programs cannot be made small
  (Spotify: 800 × 600). newm draws them in their tile's shape and shrinks them; this is
  `view { minSizeKeepsShape, minSizeShrink }`.
- **Screen sharing or the file picker does not work**: install `xdg-desktop-portal`,
  `xdg-desktop-portal-gtk` and `xdg-desktop-portal-wlr`, and tell the portal to use wlr for
  screen sharing (see [Install](install.md#build-and-install), or for NixOS
  [here](install/nixos.md#good-to-add)). newm gives the portals its environment by itself.
- **A game or a video in fullscreen stutters or tears**: say so in an issue with
  `newm cmd debug` and the log; try `newm cmd full-redraw on` to see whether it changes.

## The screen

- **Windows flicker, or parts are not redrawn**: `newm cmd full-redraw on` draws every frame
  whole. If the flicker is gone while it is on, say so in an issue. `off` goes back.
- **Text in the terminal is tiny**: your terminal decides its own font size. For foot, set
  it in `~/.config/foot/foot.ini` (`font=monospace:size=11`). On a sharp screen,
  `output("eDP-1") { scale = 1.25 }` makes everything bigger.
- **A bar program covers windows**: it probably does not reserve its room (an exclusive
  zone). In waybar this is `"exclusive": true`, which is its default.
- **A second monitor shows nothing, or the same picture**: the display key (F7 on many
  laptops) or `newm cmd displays extend` (or `mirror`, `laptop`, `other`).

## Memory and the processor

- **How much memory newm uses.** Idle with a few windows newm's Java takes about 400 MB
  (`grep VmRSS /proc/$(pgrep -f newm.MainKt)/status`). Its heap is kept small (G1, aiming at under
  96 MB, at most 1 GB); different memory options: `NEWM_GC_OPTS="-XX:+UseZGC -Xmx1g" newm`
  (they replace newm's own), more options: `JAVA_OPTS`.
- **config.kts** is compiled by a Java of its own that ends right after (a few seconds, the first
  start and after each change); then newm only loads the compiled script from
  `~/.cache/newm/config-kts`. Its options: `compositor { configCompilerJvmOptions;
  configCompilerTimeoutSeconds }`.
- **A processor busy while nothing moves.** Start newm with `CAULIS_LOG=render=2`: every 10
  seconds in which something was drawn, `~/.local/state/newm/newm.log` says how many frames each
  monitor drew and what kept moving ("director": the windows' animations; "drawing or widget
  ...": a bar, a popup, a notice). With `render=3`, each frame says which part of the screen
  changed. Please send these lines with a report.

## The touchpad

- **Tapping does not click**: `input { tapToClick = true }`.
- **Scrolling goes the wrong way**: `input { naturalScroll = true }` (or `false`).
- **Three or four fingers do nothing**: is it a touchpad libinput knows? Run
  `newm cmd log gestures 3`, swipe, and look at the log.

## newm is not in the login screen

Run `newm install-session` (it asks for your password). On NixOS the module adds it; you need
a display manager turned on, or start newm from a text console.

## Something else

Open an issue at https://github.com/AristarkhKenebas/newm/issues. What to put in it is in
[Testing newm](testing.md#reporting-a-problem).
