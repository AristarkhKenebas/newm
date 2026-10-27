# newm on Void Linux

**English** · [Русский](../../ru/install/void.md)

Void has no systemd, so a few things that other systems do by themselves are set up by hand
here: the seat, `XDG_RUNTIME_DIR`, and the session's D-Bus. It takes five minutes.

**glibc or musl?** Use Void's **glibc** edition. newm's own windows (the bar, the launcher,
the lock screen) are drawn with Skiko, which is built for glibc only, and there is no Java 27
for musl in Void. newm on musl is untested; see [Alpine](alpine.md) for what is known.

## 1. What newm needs

```sh
sudo xbps-install -S git curl \
    mesa-dri libglvnd libgbm libdrm libinput eudev-libudev libseat \
    libxkbcommon xkeyboard-config xorg-server-xwayland libxcb libX11 \
    pam-libs fontconfig freetype dejavu-fonts-ttf \
    dbus elogind
```

- `mesa-dri`, `libgbm`, `libglvnd`: the GPU driver for AMD, Intel and others.
- `libseat`: the screen and input without root. Void builds it to talk to elogind or seatd.
- `xorg-server-xwayland` and `libxcb` are for X11 programs.
- `pam-libs`: the lock screen's password (PAM's `login` service).
- `dbus`, `elogind`: see the next step.

## 2. The seat: elogind or seatd

newm works with either. **elogind is the easier one**: it also sets `XDG_RUNTIME_DIR`
(newm cannot start without it) and lets newm lock the screen before the computer sleeps.

**With elogind** (recommended):

```sh
sudo ln -s /etc/sv/dbus /var/service/
sudo ln -s /etc/sv/elogind /var/service/
```

Log out and in again. `echo $XDG_RUNTIME_DIR` should print `/run/user/<your id>`.

**With seatd** instead (no elogind):

```sh
sudo xbps-install -S seatd
sudo ln -s /etc/sv/dbus /var/service/
sudo ln -s /etc/sv/seatd /var/service/
sudo usermod -aG _seatd "$USER"
```

Then something must set `XDG_RUNTIME_DIR`: install `turnstile` and enable its service
(`/etc/sv/turnstiled`), or make the folder yourself (Void's handbook, "Session and Seat
Management", shows how). Without elogind, newm does not hear that the computer is about to
sleep, so it cannot lock the screen first.

## 3. Java 27

Void has no Java 26 or 27 package. Take Temurin, Eclipse's build of Java 27, into
`/usr/lib/jvm` (where the `newm` command looks):

```sh
sudo mkdir -p /usr/lib/jvm/temurin-27
curl -L https://api.adoptium.net/v3/binary/latest/27/ga/linux/x64/jdk/hotspot/normal/eclipse \
  | sudo tar -xz -C /usr/lib/jvm/temurin-27 --strip-components=1
export JAVA_HOME=/usr/lib/jvm/temurin-27     # for the build below
```

On ARM, write `aarch64` instead of `x64`.

## 4. Build and install newm

```sh
git clone https://github.com/AristarkhKenebas/newm
cd newm
./install.sh            # for everyone: /usr/local (asks for your password)
```

`./install.sh --user` installs into `~/.local` instead. The script builds newm, puts the
`newm` command on your PATH, and adds newm to the login screen's sessions
(`/usr/share/wayland-sessions/newm.desktop`). More in [Install](../install.md#build-and-install).

To update later: `git pull && ./install.sh`.

## 5. Good to add

A terminal and the helpers of the bar (each is optional):

```sh
sudo xbps-install -S foot pipewire wireplumber pulseaudio-utils brightnessctl playerctl \
    NetworkManager bluez power-profiles-daemon \
    xdg-desktop-portal xdg-desktop-portal-gtk xdg-desktop-portal-wlr
```

- Services are runit links: `sudo ln -s /etc/sv/NetworkManager /var/service/`, the same for
  `bluetoothd` and `power-profiles-daemon`. (Turn off `dhcpcd` or `wpa_supplicant` services
  that would fight NetworkManager.)
- **Sound**: PipeWire runs as your user, not as a service. Void's handbook ("PipeWire") shows
  how to make it start WirePlumber and its PulseAudio part. Then let newm start it with your
  session, in `~/.config/newm/config.kts`:

  ```kotlin
  autostart {
      app("pipewire")
  }
  ```

- `pulseaudio-utils`: only for `pactl`, for the volume popup. It works with PipeWire.

For screen sharing, tell the portal to use wlr, in
`~/.config/xdg-desktop-portal/newm-portals.conf`:

```ini
[preferred]
default=gtk
org.freedesktop.impl.portal.ScreenCast=wlr
org.freedesktop.impl.portal.Screenshot=wlr
```

## Start it

Without systemd, nothing starts a D-Bus session for you. newm's bar (the tray, notifications,
the portals) needs one, so start newm inside `dbus-run-session`. From a text console
(Ctrl+Alt+F3, log in):

```sh
dbus-run-session newm
```

**With a login screen**, do the same in the session file:

```sh
sudo sed -i 's|^Exec=|Exec=dbus-run-session |' /usr/share/wayland-sessions/newm.desktop
```

(`install.sh` writes this file again each time: run the line again after updating.)

**greetd** with tuigreet:

```sh
sudo xbps-install -S greetd tuigreet
```

`/etc/greetd/config.toml` (keep the `vt` and `user` lines that are there; Void's greetd runs
the greeter as `_greeter`):

```toml
[default_session]
command = "tuigreet --time --remember --cmd 'dbus-run-session newm'"
user = "_greeter"
```

Turn off the text console's `agetty` service on greetd's `vt` (for `vt = 7` nothing is
needed; for `vt = 1`: `sudo rm /var/service/agetty-tty1`), then
`sudo ln -s /etc/sv/greetd /var/service/`.

SDDM works the same way (`/etc/sv/sddm`).

The first start writes `~/.config/newm/config.kts`. Press **Logo+/** to see every key.
Next: [Everyday use](../using.md).

## The GPU

- **AMD, Intel**: `mesa-dri`, from the packages above.
- **NVIDIA**: the proprietary driver 545 or newer, from the `void-repo-nonfree` repository
  (`nvidia`), with kernel modesetting: add `nvidia-drm.modeset=1` to the kernel's command
  line (`GRUB_CMDLINE_LINUX_DEFAULT` in `/etc/default/grub`, then `sudo update-grub`), and
  reboot. `cat /sys/module/nvidia_drm/parameters/modeset` should say `Y`. Drivers 560 and newer
  turn it on by themselves; then the kernel option is not needed.
- **A virtual machine**: a virtual GPU with 3D (virtio with 3D acceleration).

## If something goes wrong

- Start newm from a text console: it says what is missing before it gives up.
- The log: `~/.local/state/newm/newm.log` (the start before: `newm.log.old`).
- `XDG_RUNTIME_DIR is not set`: elogind (or turnstile) is not running; see [step 2](#2-the-seat-elogind-or-seatd).
- `Could not open the seat`: neither elogind nor seatd answers. Check
  `sudo sv status elogind` (or `seatd`), and that you are in `_seatd` when you use seatd.
- No tray, no notifications: newm was started without `dbus-run-session`.

More in [When something goes wrong](../troubleshooting.md).
