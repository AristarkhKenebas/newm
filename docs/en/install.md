# Install

**English** · [Русский](../ru/install.md)

- [NixOS](#nixos) (and [home-manager](#home-manager))
- [Arch and other distributions](#arch-and-other-distributions)
- [From source, by hand](#from-source-by-hand)
- [Start it](#start-it) and [the first time](#the-first-time)

## NixOS

newm's flake has a package, a NixOS module and a home-manager module.

### Add it to your system

In your `flake.nix`:

```nix
{
  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
    newm.url = "github:AristarkhKenebas/newm";
  };

  outputs = { nixpkgs, newm, ... }: {
    nixosConfigurations.laptop = nixpkgs.lib.nixosSystem {
      system = "x86_64-linux";
      modules = [
        ./configuration.nix
        newm.nixosModules.default
        { programs.newm.enable = true; }
      ];
    };
  };
}
```

Or, if your `configuration.nix` gets the flake's inputs:

```nix
{ newm, ... }:
{
  imports = [ newm.nixosModules.default ];
  programs.newm.enable = true;
}
```

Then `sudo nixos-rebuild switch`. The first build takes a few minutes: newm is built with
Gradle.

You do not need to set `programs.newm.package`: the module takes newm from the flake by
itself.

### What the module does

- installs the `newm` command;
- adds "newm" to your login screen's sessions (SDDM, GDM, ly, greetd's tuigreet, ...);
- turns on Xwayland (for X11 programs), polkit, and the desktop portals (`xdg-desktop-portal`
  with its gtk and wlr parts: file pickers, screen sharing);
- with `programs.newm.settings`, writes `/etc/newm/config.json` (see [below](#settings-for-everyone)).

The lock screen checks your password with PAM's `login` service, which NixOS always has.

### Good to add

newm's bar uses a few programs and services. Without them newm still runs, but that part
of the bar is empty or does nothing:

```nix
{ pkgs, ... }:
{
  environment.systemPackages = with pkgs; [
    foot            # a terminal (or alacritty, kitty, ...: newm opens the first it finds)
    pulseaudio      # only for its `pactl`: the volume popup and its list of speakers
    brightnessctl   # the brightness slider
    playerctl       # the play / next / previous keys
  ];
  hardware.graphics.enable = true;               # the GPU's drivers (on by default with most desktops)
  services.pipewire = { enable = true; pulse.enable = true; wireplumber.enable = true; };  # sound; the volume keys use wpctl
  networking.networkmanager.enable = true;       # the Wi-Fi popup
  hardware.bluetooth.enable = true;              # the Bluetooth popup
  services.power-profiles-daemon.enable = true;  # power modes in the battery popup
}
```

A login screen is optional. Without one, log in on a text console and type `newm`. With
one, choose "newm" in its list of sessions (for example
`services.displayManager.sddm = { enable = true; wayland.enable = true; };`).

**Screen sharing** (browsers, OBS, video calls) goes through the wlr portal. The module
makes gtk the default portal; add this so that screen sharing uses wlr:

```nix
xdg.portal.config.newm = {
  default = [ "gtk" ];
  "org.freedesktop.impl.portal.ScreenCast" = [ "wlr" ];
  "org.freedesktop.impl.portal.Screenshot" = [ "wlr" ];
};
```

**Keyboard layouts**: newm does not read `services.xserver.xkb`. Without a layout in newm's
configuration it uses your language's layout next to English (with `LANG=ru_RU.UTF-8`:
"us,ru", Alt+Shift switches). To choose, set it in `~/.config/newm/config.kts`:

```kotlin
keyboard { layouts = "us,de"; options = "grp:win_space_toggle" }
```

### Settings for everyone

```nix
programs.newm = {
  enable = true;
  settings = {
    view = { corner_radius = 24; padding = 8; };
    keyboard = { layouts = "us,ru"; options = "grp:alt_shift_toggle"; };
  };
};
```

`settings` becomes `/etc/newm/config.json`. Two things to know:

- **It is the whole configuration, not a change to newm's own.** A `config.json` with only
  the two lines above has no keys and no bar. Start from the full example
  (`~/.config/newm/examples/config.json`, written at newm's first start) and change it. Or
  leave `settings` out: then every user gets `config.kts`, with a note on every setting.
- **It is copied once.** A user who has no `~/.config/newm` gets a copy of `/etc/newm` at
  their first start. After that their own copy counts, and later changes to `settings` do
  not reach them. To start from the system's again, remove `~/.config/newm`.

For the names of the settings in JSON, see [Lua, JSON, Nix](other-formats.md) and
[Every setting](settings.md).

### Try it without installing

On NixOS, from a text console (Ctrl+Alt+F3, log in):

```sh
nix run github:AristarkhKenebas/newm
```

(If flakes are not turned on: `nix --extra-experimental-features 'nix-command flakes' run github:AristarkhKenebas/newm`.)

The package brings its own Java and the libraries newm opens. It does not bring Xwayland, a
terminal, or the bar's helpers above: use what your system has. Logo+Shift+Escape ends it.

On another distribution with Nix, `nix run` usually cannot find your GPU's drivers (they
are not in Nix's paths). Use [install.sh](#arch-and-other-distributions) there.

### How the Nix package is built

The package builds newm from this repository with Gradle, offline, from the dependencies
listed in `nix/deps.json`. Caulis, the compositor library under newm, comes from Maven
Central, in the version written in `gradle/libs.versions.toml`. (`install.sh` uses Caulis's
sources instead when they lie next to newm, in `../caulis`; the Nix package never does.) If
the Nix build stops with "Unresolved reference" in `dev.caulis...`, this newm needs a Caulis
that is not released yet: please open an issue.

If the build stops with "nix/deps.json (the list of Gradle's downloads) is not there yet",
the list was not made for this version of newm yet. Make it in newm's folder (with network,
once the Caulis version in `gradle/libs.versions.toml` is on Maven Central):

```sh
nix build .#default.mitmCache.updateScript && ./result
```

It writes `nix/deps.json`; `nix build` works from then on (with a flake, `git add nix/deps.json`
first: a flake sees only files git knows).

### home-manager

For your own configuration, written in Nix:

```nix
{ newm, ... }:
{
  imports = [ newm.homeManagerModules.default ];

  programs.newm = {
    enable = true;
    # Kotlin, as newm writes it (recommended: keeps every note):
    configKts = builtins.readFile ./newm/config.kts;
    # or Lua:     configLua = builtins.readFile ./newm/config.lua;
    # or JSON:    settings = { ... };   (the whole configuration, as above)
  };
}
```

- Set only one of `configKts`, `configLua` and `settings`.
- `package`: leave it out when the NixOS module installs newm. Without the NixOS module,
  set `package = newm.packages.${pkgs.stdenv.hostPlatform.system}.default;`. (The session in
  the login screen comes only from the NixOS module.)
- newm takes the new file on as soon as home-manager writes it.

## Arch and other distributions

### What you need

- **Java 27** to run newm (26 works too). On Arch: `sudo pacman -S jdk-openjdk`. newm's
  `newm` command finds a new enough Java by itself: `JAVA_HOME`, the `java` on your PATH, or
  the newest under `/usr/lib/jvm`.
- **A GPU driver** with OpenGL ES 3: Mesa (AMD, Intel) or NVIDIA 545 or newer.
- **The libraries newm opens while it runs.** On Arch:

  ```sh
  sudo pacman -S --needed mesa libglvnd libdrm libinput libxkbcommon seatd \
      xorg-xwayland libxcb pam fontconfig freetype2 libpng
  ```

  On Debian and Ubuntu (names differ a little between releases):

  ```sh
  sudo apt install libegl1 libgles2 libgbm1 libdrm2 libinput10 libudev1 libseat1 \
      libxkbcommon0 xwayland libxcb1 libxcb-composite0 libxcb-xfixes0 libxcb-render0 \
      libpam0g libfontconfig1 libfreetype6 libpng16-16
  ```

- **A terminal.** newm opens `$TERMINAL`, or else the first it finds of alacritty, foot,
  kitty, wezterm, ghostty, gnome-terminal, konsole, xfce4-terminal and xterm.
- **For the bar and the laptop's keys** (each is optional; without it, that part does
  nothing):

  | What | Arch | Debian / Ubuntu |
  |---|---|---|
  | volume keys (`wpctl`) | `wireplumber` | `wireplumber` |
  | the volume popup (`pactl`) | `libpulse` | `pulseaudio-utils` |
  | brightness slider and keys | `brightnessctl` | `brightnessctl` |
  | play / next / previous keys | `playerctl` | `playerctl` |
  | Wi-Fi popup | `networkmanager` (running) | `network-manager` |
  | Bluetooth popup | `bluez` (running) | `bluez` |
  | power modes | `power-profiles-daemon` | `power-profiles-daemon` |
  | file pickers, screen sharing | `xdg-desktop-portal-gtk`, `xdg-desktop-portal-wlr` | the same |

Screenshots and the clipboard's history are newm's own: no grim or clipboard manager needed.

### Build and install

```sh
git clone https://github.com/AristarkhKenebas/newm
cd newm
./install.sh            # for everyone: /usr/local (asks for your password)
./install.sh --user     # or just for you: ~/.local
```

This:

- builds newm (Gradle downloads what it needs the first time, a few minutes);
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

## From source, by hand

```sh
./gradlew installDist                       # build/install/newm/bin/newm
./gradlew installDist -Pcaulis.local=true   # the same, with ../caulis's sources
build/install/newm/bin/newm                 # run it (see "Start it")
build/install/newm/bin/newm install-session # optional: the login screen's entry
```

Gradle downloads a JDK 27 for the build if you have none. Running needs Java 26 or newer
installed (see above).

## Start it

- **From a text console** (Ctrl+Alt+F3, log in): type `newm`. This is the best way to try it
  the first time: if something is wrong, you see what newm says.
- **From your login screen** (SDDM, GDM, ly, greetd): choose "newm".
- **Inside your current desktop, to try it**: run `newm` in a terminal there. It opens as a
  window, and nothing of your desktop changes. (Keys like Logo may be taken by your desktop
  first.)

To leave: **Logo+Shift+Escape**, or "Log out" in the bar's power menu.

Other commands:

```sh
newm --version          # which newm
newm help               # what the newm command can do
newm install-session    # add newm to the login screen (not needed on NixOS)
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
