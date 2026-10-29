# newm on NixOS

**English** · [Русский](../../ru/install/nixos.md)

newm's flake has a package, a NixOS module and a home-manager module. The package brings its
own Java 27 and every library newm opens, so there is nothing else to install by hand.

- [With flakes](#with-flakes)
- [What the module does](#what-the-module-does)
- [Good to add](#good-to-add)
- [Settings for everyone](#settings-for-everyone)
- [home-manager](#home-manager)
- [Without flakes](#without-flakes)
- [Try it without installing](#try-it-without-installing)
- [The GPU](#the-gpu)
- [How the Nix package is built](#how-the-nix-package-is-built)
- [If something goes wrong](#if-something-goes-wrong)

## With flakes

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
itself. The flake has packages for `x86_64-linux` and `aarch64-linux`.

### Which `follows` are safe

newm's flake has three inputs:

| Input | What newm takes from it | Make it follow your nixpkgs? |
|---|---|---|
| `nixpkgs` | the libraries newm opens while running (Mesa, libinput, ...) | **safe**, and good: newm then uses the same Mesa as your system |
| `caulis` | Java 27 and the list of those libraries | leave it as it is (it follows newm's `nixpkgs` already) |
| `nixpkgs-gradle` | the Gradle newm is **built** with | **never**: it is pinned on purpose |

```nix
inputs.newm = {
  url = "github:AristarkhKenebas/newm";
  inputs.nixpkgs.follows = "nixpkgs";            # fine
  # inputs.nixpkgs-gradle.follows = "nixpkgs";   # do not: the build stops
};
```

newm is built offline from a list of Gradle's downloads (`nix/deps.json`), and that list
holds the files of one Gradle version (`nix/deps-gradle-version`). Another Gradle asks for
other files (for example the Kotlin plugin's build for that Gradle) and the build would
stop with "Could not find kotlin-gradle-plugin-...-gradleNNN.jar". So newm always builds
with the Gradle of its own `nixpkgs-gradle`, whatever nixpkgs the rest follows. If that
Gradle is replaced anyway, the build stops at once with "nix/deps.json ... was made for
Gradle X, but this build got Gradle Y" and says what to do.

## What the module does

- installs the `newm` command;
- adds "newm" to your login screen's sessions (SDDM, GDM, ly, greetd's tuigreet, ...);
- turns on Xwayland (for X11 programs), polkit, and the desktop portals (`xdg-desktop-portal`
  with its gtk and wlr parts: file pickers, screen sharing), with wlr chosen for screen
  sharing and screenshots;
- passes `services.xserver.xkb` (layout, variant, options) to newm when it is set and newm's
  configuration names no layout;
- with `programs.newm.settings`, writes `/etc/newm/config.json` (see [below](#settings-for-everyone)).

The lock screen checks your password with PAM's `login` service, which NixOS always has.

## Good to add

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
one, choose "newm" in its list of sessions. For example SDDM:

```nix
services.displayManager.sddm = { enable = true; wayland.enable = true; };
```

or greetd with tuigreet:

```nix
services.greetd = {
  enable = true;
  settings.default_session.command = "${pkgs.tuigreet}/bin/tuigreet --time --remember --cmd newm";
};
```

Or newm itself as the login screen: `programs.newm.greeter.enable = true;` sets greetd up with newm as its greeter, see [newm as the login screen](../greeter.md#nixos).

(In older nixpkgs the package is `pkgs.greetd.tuigreet`.)

**Keyboard layouts**: without a layout in newm's configuration, newm uses
`services.xserver.xkb` when it is set, else your language's layout next to English (with
`LANG=ru_RU.UTF-8`: "us,ru", Alt+Shift switches). To choose, set it in
`~/.config/newm/config.kts`:

```kotlin
keyboard { layouts = "us,de"; options = "grp:win_space_toggle" }
```

## Settings for everyone

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

For the names of the settings in JSON, see [Lua, JSON, Nix](../other-formats.md) and
[Every setting](../settings.md).

## home-manager

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

## Without flakes

newm is published as a flake, but a configuration without flakes can still load it with
`builtins.getFlake`. This needs the `flakes` feature turned on for Nix itself (it changes
nothing else in your setup):

```nix
# configuration.nix
{ ... }:
let
  newm = builtins.getFlake "github:AristarkhKenebas/newm";
in
{
  nix.settings.experimental-features = [ "nix-command" "flakes" ];
  imports = [ newm.nixosModules.default ];
  programs.newm.enable = true;
}
```

The first time, run `sudo nixos-rebuild switch` with the feature given on the command line,
since it is not on yet:

```sh
sudo NIX_CONFIG="experimental-features = nix-command flakes" nixos-rebuild switch
```

`getFlake` takes the newest newm each time it fetches; to stay on one version, write a
commit: `github:AristarkhKenebas/newm/<commit>`.

## Try it without installing

On NixOS, from a text console (Ctrl+Alt+F3, log in):

```sh
nix run github:AristarkhKenebas/newm
```

(If flakes are not turned on: `nix --extra-experimental-features 'nix-command flakes' run github:AristarkhKenebas/newm`.)

The package brings its own Java and the libraries newm opens. It does not bring Xwayland, a
terminal, or the bar's helpers above: use what your system has. Logo+Shift+Escape ends it.

On another distribution with Nix, `nix run` usually cannot find your GPU's drivers (they
are not in Nix's paths). Use your distribution's [install page](../install.md) there.

## The GPU

- **AMD, Intel**: `hardware.graphics.enable = true;` is all.
- **NVIDIA**: the proprietary driver 545 or newer, with kernel modesetting:

  ```nix
  services.xserver.videoDrivers = [ "nvidia" ];
  hardware.nvidia.modesetting.enable = true;
  hardware.nvidia.open = true;   # the open kernel modules (Turing and newer); false for older cards
  ```

- **A virtual machine**: give it a virtual GPU with 3D (virtio with 3D acceleration).

## How the Nix package is built

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
nix run .#update-deps
```

It makes `nix/deps.json` with the flake's pinned Gradle (input `nixpkgs-gradle`) and writes
that Gradle's version to `nix/deps-gradle-version`; to move to another Gradle, change the
revision of `nixpkgs-gradle` in `flake.nix` and run it again. `nix build` works from then on
(with a flake, `git add nix/deps.json nix/deps-gradle-version` first: a flake sees only
files git knows).

If the build stops with "nix/deps.json ... was made for Gradle X, but this build got
Gradle Y", newm's `nixpkgs-gradle` input was made to follow another nixpkgs, or its Gradle
was overridden: see [Which `follows` are safe](#which-follows-are-safe).

## If something goes wrong

- Start newm from a text console (Ctrl+Alt+F3, log in, `newm`): it says what is missing.
- The log: `~/.local/state/newm/newm.log` (the start before: `newm.log.old`).
- "No GPU was found": `hardware.graphics.enable = true;`.
- No keys or no bar with `programs.newm.settings`: that file is the whole configuration
  (see [above](#settings-for-everyone)).

More in [When something goes wrong](../troubleshooting.md).
