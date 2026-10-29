# newm on Fedora

**English** · [Русский](../../ru/install/fedora.md)

This page is for Fedora Workstation, the Fedora spins (KDE, Sway, ...) and Nobara. The
commands were checked against Fedora 43 and 44.

## 1. What newm needs

```sh
sudo dnf install git curl java-latest-openjdk-devel \
    mesa-dri-drivers mesa-libEGL mesa-libgbm libglvnd-egl libglvnd-gles libglvnd-glx \
    libdrm libinput systemd-libs libseat libxkbcommon xkeyboard-config \
    xorg-x11-server-Xwayland libxcb libX11 pam fontconfig freetype dejavu-sans-fonts
```

- `java-latest-openjdk-devel` is Java 27 on Fedora (the newest Java, updated as new ones come).
- `mesa-*` and `libglvnd-*`: the GPU driver for AMD, Intel and others.
- `libseat`: the screen and input without root. It talks to systemd-logind: no service to start.
- `xorg-x11-server-Xwayland` and `libxcb` are for X11 programs.
- `pam`: the lock screen's password.

Most of these are already installed on a desktop system.

## 2. Java

`java-latest-openjdk` installs next to Fedora's default Java, in `/usr/lib/jvm`; the `newm`
command finds it there by itself. You do not need to change the default with `alternatives`.

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
sudo dnf install foot wireplumber pulseaudio-utils brightnessctl playerctl \
    NetworkManager bluez \
    xdg-desktop-portal xdg-desktop-portal-gtk xdg-desktop-portal-wlr
```

- `pulseaudio-utils`: only for `pactl`, for the volume popup. It works with PipeWire,
  Fedora's sound.
- **Power modes**: Fedora Workstation runs `tuned-ppd`, which answers newm the same way as
  power-profiles-daemon. Keep it. (On a spin without it: `sudo dnf install
  power-profiles-daemon`; the two cannot be installed together.)

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

Or log out and choose "newm" in your login screen (GDM: the gear button; SDDM: the session
list).

Without a login screen, greetd with tuigreet is a small one:

```sh
sudo dnf install greetd tuigreet
```

In `/etc/greetd/config.toml`, keep the `user` line that is there and set the command:

```toml
[default_session]
command = "tuigreet --time --remember --cmd newm"
```

```sh
sudo systemctl disable gdm       # or sddm: your current login screen
sudo systemctl enable greetd
```

Or newm itself as the login screen, with its own lock screen: [newm as the login screen](../greeter.md).

The first start writes `~/.config/newm/config.kts`. Press **Logo+/** to see every key.
Next: [Everyday use](../using.md).

## The GPU

- **AMD, Intel**: Mesa, from the packages above.
- **NVIDIA**: the proprietary driver 545 or newer, from RPM Fusion (`akmod-nvidia`; Nobara
  has it already). It needs kernel modesetting:
  `sudo grubby --update-kernel=ALL --args="nvidia-drm.modeset=1"`, then reboot.
  `cat /sys/module/nvidia_drm/parameters/modeset` should say `Y`. Drivers 560 and newer
  turn it on by themselves; then the kernel option is not needed.
- **A virtual machine**: a virtual GPU with 3D (virtio with 3D acceleration).

## If something goes wrong

- Start newm from a text console: it says what is missing before it gives up.
- The log: `~/.local/state/newm/newm.log` (the start before: `newm.log.old`).
- `newm needs Java 26 or newer`: `sudo dnf install java-latest-openjdk-devel`.
- `Could not open the seat`: start it from a text console where you logged in, not with
  `su` or `sudo`.

More in [When something goes wrong](../troubleshooting.md).
