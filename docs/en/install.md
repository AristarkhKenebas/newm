# Install

**English** · [Русский](../ru/install.md)

## What you need

- **Java 27** (on Arch: `sudo pacman -S jdk-openjdk`).
- **A terminal.** newm opens the one you have: `$TERMINAL`, or else the first it finds of
  alacritty, foot, kitty, wezterm, ... To choose one, set it in the
  configuration (`val terminal = "foot"`).
- Good to have:
  - `xorg-xwayland` for X11 programs;
  - `xdg-desktop-portal-gtk` (or `-wlr`) for screen sharing and file pickers;
  - `grim` (screenshots), `brightnessctl`, `wireplumber` or `pulseaudio` (the brightness and volume keys).

## Build and install

```sh
git clone https://github.com/AristarkhKenebas/newm
cd newm
./install.sh            # for everyone: /usr/local (asks for your password)
./install.sh --user     # or just for you: ~/.local
```

This does three things:

- builds newm;
- puts the `newm` command on your PATH;
- adds newm to your login screen's sessions, and its starting configuration to `/etc/newm`.

To update later: `git pull && ./install.sh`.

On NixOS, use the module instead (see [Nix](other-formats.md#nix)).

## Start it

- **From a text console** (Ctrl+Alt+F2, log in): type `newm`.
- **From your login screen** (ly, SDDM, GDM, greetd): choose "newm".
- **Inside your current desktop, to try it**: run `newm` in a terminal there. It opens as
  a window, and nothing of your desktop changes.

## The first time

newm writes your configuration folder, `~/.config/newm/`:

- `config.kts`: every setting, with a note on each. Open it in any editor while newm runs.
  Each time you save it, newm takes it on at once.
- `README.md`: which file of the folder newm reads.
- `examples/`: the same configuration in Lua and JSON.
- `bars/`: bars to choose from (see [Bars](bars.md)).

Your first keys:

- **Logo+Return**: a terminal;
- **Logo+d**: the launcher;
- **Logo+q**: close a window;
- **Logo+Shift+Escape**: log out.

"Logo" is the key with the Windows or Super logo. Next: [Everyday use](using.md).
