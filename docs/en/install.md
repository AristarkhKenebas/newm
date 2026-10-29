# Install

**English** · [Русский](../ru/install.md)

newm runs on any Linux with a GPU driver, glibc and Java 27. Pick your system for exact
commands; this page has what is the same everywhere.

| Your system | Page |
|---|---|
| NixOS (flake, home-manager, without flakes) | [NixOS](install/nixos.md) |
| Arch, EndeavourOS, Manjaro, CachyOS | [Arch](install/arch.md) |
| Debian 13+, Ubuntu 24.04+, Linux Mint, Pop!_OS | [Debian and Ubuntu](install/debian-ubuntu.md) |
| Fedora, Nobara | [Fedora](install/fedora.md) |
| openSUSE Tumbleweed | [openSUSE](install/opensuse.md) |
| Void Linux | [Void](install/void.md) |
| Gentoo (OpenRC or systemd) | [Gentoo](install/gentoo.md) |
| Alpine Linux (musl: untested) | [Alpine](install/alpine.md) |
| Anything else | [Other systems](install/other.md) |

On this page:

- [What you need](#what-you-need)
- [Java 27](#java-27)
- [Build and install](#build-and-install)
- [From source, by hand](#from-source-by-hand)
- [Start it](#start-it) and [the first time](#the-first-time)

## What you need

- **Java 27.** Building needs JDK 27; running needs Java 26 or newer. See [below](#java-27).
- **A GPU driver** with OpenGL ES 3 and GBM: Mesa (AMD, Intel, and others) or NVIDIA 545
  or newer.
- **A seat.** newm opens the screen and the input devices through libseat, without root.
  libseat talks to systemd-logind, elogind or seatd. On a distribution with systemd there is
  nothing to set up.
- **The libraries newm opens while it runs.** newm does not link them when it is built; it
  looks for them by name when it starts. Each distribution page has the exact package names;
  [Other systems](install/other.md#the-libraries) has the full list of files.
- **glibc.** newm's own windows (the bar, the launcher, the lock screen) are drawn with
  Skiko, which is built for glibc only. On musl (Alpine, Void musl) newm is untested.
- **Fonts.** Any fonts your system has; newm uses them through fontconfig.
- **A terminal.** newm opens `$TERMINAL`, or else the first it finds of alacritty, foot,
  kitty, wezterm, ghostty, gnome-terminal, konsole, xfce4-terminal and xterm.
- **For the bar and the laptop's keys** (each is optional; without it, that part does
  nothing):

  | What | Program |
  |---|---|
  | volume keys | `wpctl` (WirePlumber) |
  | the volume popup, the list of speakers | `pactl` (PulseAudio's tools; works with PipeWire) |
  | brightness slider and keys | `brightnessctl` |
  | play / next / previous keys | `playerctl` |
  | Wi-Fi popup | NetworkManager, running |
  | Bluetooth popup | BlueZ, running |
  | power modes | power-profiles-daemon (or something with its D-Bus name, such as tuned-ppd) |
  | file pickers, screen sharing | `xdg-desktop-portal` with `xdg-desktop-portal-gtk` and `xdg-desktop-portal-wlr` |

Screenshots and the clipboard's history are newm's own: no grim or clipboard manager needed.

## Java 27

First, look at your distribution's page: many have JDK 27 (or 26) as a package.

When yours has none, take Eclipse Temurin's build. Put it in `/usr/lib/jvm`: the `newm`
command looks there by itself, also when a login screen starts it.

```sh
sudo mkdir -p /usr/lib/jvm/temurin-27
curl -L https://api.adoptium.net/v3/binary/latest/27/ga/linux/x64/jdk/hotspot/normal/eclipse \
  | sudo tar -xz -C /usr/lib/jvm/temurin-27 --strip-components=1
/usr/lib/jvm/temurin-27/bin/java -version
```

On ARM (aarch64), write `aarch64` instead of `x64`. To update it later, remove the folder and
run the same commands again.

[SDKMAN](https://sdkman.io) works too (`sdk list java`, then `sdk install java` with a
`27...-tem` version). It puts Java in your home folder, so a login screen may not find it.
Then set `JAVA_HOME` where your session reads it, or use the tarball above.

How the `newm` command picks a Java: `JAVA_HOME` if it is 26 or newer; else the `java` on
your PATH if it is new enough; else the newest one in `/usr/lib/jvm`. With none, it says
"newm needs Java 26 or newer".

For building, Gradle runs on any JDK from 17 to 27 and compiles with JDK 27. It finds one
in `JAVA_HOME` or `/usr/lib/jvm`, or downloads one by itself.

## Build and install

```sh
git clone https://github.com/AristarkhKenebas/newm
cd newm
./install.sh            # for everyone: /usr/local (asks for your password)
./install.sh --user     # or just for you: ~/.local
```

If `java` is not on your PATH, tell the build where Java is first, for example
`export JAVA_HOME=/usr/lib/jvm/temurin-27`.

`install.sh`:

- builds newm (Gradle downloads what it needs the first time: a few minutes);
- puts the `newm` command on your PATH (`/usr/local/bin/newm`, or `~/.local/bin/newm`);
- runs `newm install-session`: adds newm to the login screen's sessions
  (`/usr/share/wayland-sessions/newm.desktop`) and puts the starting configuration into
  `/etc/newm` (files there already stay).

With Caulis's sources next to newm (`../caulis`), `install.sh` builds against them. Use this
when the build says something in `dev.caulis...` is missing: that newm needs a Caulis newer
than the released one.

```sh
git clone https://github.com/AristarkhKenebas/caulis ../caulis
./install.sh
```

To update later: `git pull && ./install.sh`.

**Screen sharing** goes through the wlr portal. Tell the portal so, in
`~/.config/xdg-desktop-portal/newm-portals.conf`:

```ini
[preferred]
default=gtk
org.freedesktop.impl.portal.ScreenCast=wlr
org.freedesktop.impl.portal.Screenshot=wlr
```

newm sets `XDG_CURRENT_DESKTOP=newm`, so the portal reads this file, and newm passes its
environment to the portals by itself.

## From source, by hand

```sh
./gradlew installDist                       # build/install/newm/bin/newm
./gradlew installDist -Pcaulis.local=true   # the same, with ../caulis's sources
build/install/newm/bin/newm                 # run it (see "Start it")
build/install/newm/bin/newm install-session # optional: the login screen's entry
```

## Start it

- **From a text console** (Ctrl+Alt+F3, log in): type `newm`. This is the best way to try it
  the first time: if something is wrong, you see what newm says.
- **From your login screen** (SDDM, GDM, ly, greetd): choose "newm". newm can also be the
  login screen itself: [newm as the login screen](greeter.md).
- **Inside your current desktop, to try it**: run `newm` in a terminal there. It opens as a
  window, and nothing of your desktop changes. (Keys like Logo may be taken by your desktop
  first.)

On a system without systemd nothing starts a D-Bus session bus, which newm's tray,
notifications and portals need. newm handles that by itself: `newm install-session` writes the
login screen's session as `dbus-run-session newm` where no systemd user session starts a bus,
and newm's start script starts newm inside `dbus-run-session` when there is no bus (with the
`dbus` package installed). To decide it yourself: `newm install-session --dbus-run-session` or
`--no-dbus-run-session` (also options of `install.sh`), and `NEWM_DBUS=off` in the environment
for the start script.

To leave: **Logo+Shift+Escape**, or "Log out" in the bar's power menu.

Other commands:

```sh
newm --version          # which newm
newm help               # what the newm command can do
newm install-session    # add newm to the login screen (not needed on NixOS); --dbus-run-session / --no-dbus-run-session
newm cmd <command>      # talk to the running newm (see Every setting)
```

## The first time

newm writes your configuration folder, `~/.config/newm/` (from `/etc/newm` if there is one,
else from its own templates):

- `config.kts`: every setting, with a note on each. Open it in any editor while newm runs.
  Each time you save it, newm takes it on at once.
- `README.md`: which file of the folder newm reads.
- `examples/`: the configuration in Lua and JSON.
- `bars/`: bars to choose from (see [Bars](bars.md)).

A card says "Welcome to newm" with the first keys. The most important ones:

- **Logo+/**: every key, with what it does;
- **Logo+Return**: a terminal;
- **Logo+d**: the launcher;
- **Logo** tapped alone: every window at once;
- **Logo+q**: close a window;
- **Logo+Shift+Escape**: log out.

"Logo" is the key with the Windows or Super logo. Next: [Everyday use](using.md). Trying
newm for someone? [Testing newm](testing.md) has a checklist.

A note on the defaults: after 5 minutes without input the screen locks, after 5½ the
screens go dark, after 10 the computer suspends (`energy { }` in
[the configuration](configuration.md#lock-and-sleep-when-idle)).
