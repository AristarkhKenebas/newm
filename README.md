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
  with smooth animations.
- **It looks good from the start.** Rounded windows with smooth edges, blur behind
  see-through windows, and its own lock screen and launcher.
- **A bar of its own.** Islands floating at the top: a small map of your wall, the window,
  the clock with a calendar, the tray of background programs with their menus, and the
  network, volume, battery and power menus. Light or dark, as your programs are.
- **Change it while it runs.** Save your configuration and newm applies it at once. A
  mistake shows as a card with its line, and newm keeps the settings that worked. A broken
  configuration never gives you a black screen.
- **Your configuration in your language.** Kotlin (`config.kts`, newm's own), Lua, JSON,
  or Nix, fully declarative. A `config.py` of the old newm still works.
- **Works with what you have.** waybar or any other bar (or none), mako, swaybg, X11
  programs, screen sharing through the portals.

## Quick start

You need Java 27 and a terminal (alacritty by default).

```sh
git clone https://github.com/AristarkhKenebas/newm
cd newm
./install.sh
```

Then type `newm` on a text console, or choose "newm" in your login screen. The first start
writes your configuration to `~/.config/newm/config.kts`, with a note on every setting.

First keys:

- **Logo+Return**: a terminal;
- **Logo+d**: the launcher;
- **Logo** alone: every window at once;
- **Logo+h/j/k/l**: move around;
- **Logo+Shift+Escape**: log out.

## Documentation

- [Install](docs/en/install.md)
- [Everyday use: keys and gestures](docs/en/using.md)
- [Your configuration](docs/en/configuration.md)
- [Every setting](docs/en/settings.md)
- [Bars](docs/en/bars.md)
- [Lua, JSON, Nix and the old config.py](docs/en/other-formats.md)
- [When something goes wrong](docs/en/troubleshooting.md)
- [Working on newm](docs/en/development.md)

## About this newm

newm was written in Python on pywm by Jonas Bucher ([jbuchermn/newm](https://github.com/jbuchermn/newm)),
and is no longer maintained there. This is newm carried on:

- ported to Kotlin, one to one in how it behaves;
- built on the [Caulis](https://github.com/AristarkhKenebas/caulis) compositor library
  instead of pywm and wlroots;
- with fixes to the old newm's problems, and new parts: the bar, the lock screen, the
  launcher, configuration that reloads on save, the other configuration languages, the
  documentation.

The original Python code is kept in [`legacy/`](legacy) for reference.

## License

MIT, as newm. See [LICENSE](LICENSE).
