<div align="center">

# newm

**A Wayland compositor for laptops and touchpads: your windows on an endless wall.**

**English** · [Русский](README.ru.md)

[![newm](docs/assets/screenshot.png)](https://youtu.be/4I_IHbP-iNQ)

</div>

Most window managers fit your windows into the screen. newm lays them out on an endless
wall of tiles, and the screen is a window onto that wall. Three fingers move over it, four
fingers zoom in and out. When you zoom out, the windows get their new size, so they stay
usable at any zoom.

## What you get

- **Made for the touchpad.** Moving, zooming, the overview and the launcher are all gestures,
  following the fingers; animations are off by default, newm's smooth ones are one line away
  (`animations { preset = "newm" }`, or each by name as in Hyprland). Logo + drag moves and
  resizes windows with the mouse.
- **The overview.** Tap Logo: every window at once. Point at one, click, and you are there.
- **It looks good from the start.** Rounded windows with smooth edges, blur behind
  see-through windows, colours made from your wallpaper, light or dark for newm and your
  programs together.
- **A bar of its own.** Islands floating at the top: the window, the clock with a calendar,
  the tray with its menus, Wi-Fi, Bluetooth, sound, battery, power, and quick settings as on
  a phone (brightness, caffeine, light or dark, ...).
- **Built in, no extra programs:** a lock screen, a launcher, a screenshot tool (an area, a
  window, the screen, or several whole windows from the overview), a clipboard history
  (Logo+V), and the keys' help (Logo+/).
- **Programs that will not get small** (Spotify) are drawn in their tile's shape, not cut off.
- **Change it while it runs.** Save your configuration and newm applies it at once. A
  mistake shows as a card with its line, and newm keeps the settings that worked. A broken
  configuration never gives you a black screen.
- **Your configuration in your language.** Kotlin (`config.kts`, newm's own), Lua, JSON,
  or Nix. A `config.py` of the old newm still works.
- **Works with what you have.** waybar or any other bar (or none), mako, swaybg, X11
  programs, screen sharing through the portals.

## Quick start

### NixOS

In your flake:

```nix
inputs.newm.url = "github:AristarkhKenebas/newm";
```

and in your system's configuration:

```nix
imports = [ newm.nixosModules.default ];
programs.newm.enable = true;
```

`sudo nixos-rebuild switch`, then choose "newm" in your login screen, or type `newm` on a text
console. To try it first without installing: `nix run github:AristarkhKenebas/newm` from a
text console. More in [Install](docs/en/install.md#nixos).

### Arch and others

You need Java 27, a GPU driver (Mesa, or NVIDIA 545+), and a terminal.

```sh
git clone https://github.com/AristarkhKenebas/newm
cd newm
./install.sh
```

Then type `newm` on a text console, or choose "newm" in your login screen. The libraries and
helpers it uses are listed in [Install](docs/en/install.md#arch-and-other-distributions).

### The first keys

The first start writes your configuration to `~/.config/newm/config.kts`, with a note on every
setting.

- **Logo+/**: every key, with what it does;
- **Logo+Return**: a terminal;
- **Logo+D**: the launcher;
- **Logo** alone: every window at once;
- **Logo+H/J/K/L**: move around; **Logo+S**: two windows side by side;
- **Logo+Shift+Escape**: log out.

## Documentation

- [Install](docs/en/install.md): NixOS, Arch and others, from source
- [Everyday use: keys, gestures, the overview, screenshots, clipboard](docs/en/using.md)
- [Your configuration](docs/en/configuration.md)
- [Every setting](docs/en/settings.md), and `newm cmd`
- [Bars](docs/en/bars.md)
- [Lua, JSON, Nix and the old config.py](docs/en/other-formats.md)
- [When something goes wrong](docs/en/troubleshooting.md)
- [Testing newm, and reporting problems](docs/en/testing.md)
- [Working on newm](docs/en/development.md)

Found a problem? Please open an issue: https://github.com/AristarkhKenebas/newm/issues
([what to put in it](docs/en/testing.md#reporting-a-problem)).

## About this newm

newm was written in Python on pywm by Jonas Bucher ([jbuchermn/newm](https://github.com/jbuchermn/newm)),
and is no longer maintained there. This is newm carried on:

- ported to Kotlin, one to one in how it behaves;
- built on the [Caulis](https://github.com/AristarkhKenebas/caulis) compositor library
  instead of pywm and wlroots;
- with fixes to the old newm's problems, and new parts: the bar and quick settings, the lock
  screen, the launcher, screenshots, the clipboard's history, configuration that reloads on
  save, the other configuration languages, the documentation.

The original Python code is kept in [`legacy/`](legacy) for reference.

## License

MIT, as newm. See [LICENSE](LICENSE).
