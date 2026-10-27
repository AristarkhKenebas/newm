# newm on Arch Linux

**English** · [Русский](../../ru/install/arch.md)

This page is for Arch Linux and the systems built on its packages: EndeavourOS, Manjaro and
CachyOS. The commands are the same on all of them.

## 1. What newm needs

```sh
sudo pacman -S --needed git jdk-openjdk \
    mesa libglvnd libdrm libinput systemd-libs seatd libxkbcommon xkeyboard-config \
    xorg-xwayland libxcb libx11 pam fontconfig freetype2 ttf-dejavu
```

- `jdk-openjdk` is Java 27 on Arch.
- `seatd` brings libseat. On Arch it talks to systemd-logind, so you do **not** need to start
  the seatd service.
- `xorg-xwayland` and `libxcb` are for X11 programs.
- `pam` is for the lock screen's password.
- `ttf-dejavu` is just a font; any fonts will do.

**Manjaro**: its stable branch gets packages a few weeks after Arch. If `pacman -Si jdk-openjdk`
shows a version older than 26, take Java from [the general page](../install.md#java-27).

## 2. Java

You can keep another Java as the default (`archlinux-java status`). The `newm` command finds
Java 27 in `/usr/lib/jvm` by itself.

## 3. Build and install newm

```sh
git clone https://github.com/AristarkhKenebas/newm
cd newm
./install.sh            # for everyone: /usr/local (asks for your password)
```

`./install.sh --user` installs into `~/.local` instead. The script builds newm, puts the
`newm` command on your PATH, and adds newm to the login screen's sessions
(`/usr/share/wayland-sessions/newm.desktop`). More in [Install](../install.md#build-and-install).

To update later: `git pull && ./install.sh`.

## 4. Good to add

A terminal and the helpers of the bar (each is optional):

```sh
sudo pacman -S --needed foot wireplumber libpulse brightnessctl playerctl \
    networkmanager bluez power-profiles-daemon \
    xdg-desktop-portal xdg-desktop-portal-gtk xdg-desktop-portal-wlr
sudo systemctl enable --now NetworkManager bluetooth power-profiles-daemon
```

- `wireplumber`: the volume keys (`wpctl`). It needs PipeWire running, as on most desktops.
- `libpulse`: `pactl`, for the volume popup.
- Only enable NetworkManager if nothing else (iwd alone, systemd-networkd) runs your network.

For screen sharing, tell the portal to use wlr, in
`~/.config/xdg-desktop-portal/newm-portals.conf`:

```ini
[preferred]
default=gtk
org.freedesktop.impl.portal.ScreenCast=wlr
org.freedesktop.impl.portal.Screenshot=wlr
```

## 5. Start it

From a text console (Ctrl+Alt+F3, log in):

```sh
newm
```

Or choose "newm" in your login screen (SDDM, GDM, ly, greetd). Without a login screen,
greetd with tuigreet is a small one:

```sh
sudo pacman -S greetd greetd-tuigreet
```

In `/etc/greetd/config.toml`, keep the `user` line that is there and set the command:

```toml
[default_session]
command = "tuigreet --time --remember --cmd newm"
user = "greeter"
```

```sh
sudo systemctl enable greetd     # disable your other login screen first, if any
```

The first start writes `~/.config/newm/config.kts`. Press **Logo+/** to see every key.
Next: [Everyday use](../using.md).

## The GPU

- **AMD, Intel**: `mesa` is all. (Vulkan drivers are not needed by newm.)
- **NVIDIA**: driver 545 or newer: `nvidia-open` (or `nvidia-open-dkms`) and `nvidia-utils`,
  which brings the GBM part newm draws with. Kernel modesetting must be on: add
  `nvidia-drm.modeset=1` to the kernel's command line (in your boot loader's entry), then
  reboot. `cat /sys/module/nvidia_drm/parameters/modeset` should say `Y`. Drivers 560 and newer
  turn it on by themselves; then the kernel option is not needed.
- **A virtual machine**: a virtual GPU with 3D (virtio with 3D acceleration).

## If something goes wrong

- Start newm from a text console: it says what is missing before it gives up.
- The log: `~/.local/state/newm/newm.log` (the start before: `newm.log.old`).
- `newm needs Java 26 or newer`: `sudo pacman -S jdk-openjdk`.
- `Could not open the seat`: start it from a text console where you logged in, not with
  `su` or `sudo`.

More in [When something goes wrong](../troubleshooting.md).
