# newm on Gentoo

**English** · [Русский](../../ru/install/gentoo.md)

This page is for Gentoo on amd64, with OpenRC or systemd. newm needs glibc (its bar,
launcher and lock screen are drawn with Skiko, built for glibc only), so use a glibc profile.

## 1. USE flags

Most defaults are fine. These matter:

```sh
# /etc/portage/package.use/newm
media-libs/libglvnd X          # libGL.so.1: Skiko needs it
sys-auth/seatd     systemd     # on a systemd profile
# sys-auth/seatd   elogind     # on OpenRC with elogind (recommended there)
# sys-auth/seatd   server      # on OpenRC without elogind: the seatd daemon
```

`sys-auth/seatd` takes at most one of `systemd` and `elogind`. Also set `VIDEO_CARDS` in
`/etc/portage/make.conf` for your GPU (for example `VIDEO_CARDS="amdgpu radeonsi"` or
`VIDEO_CARDS="intel"`), as for any desktop.

## 2. What newm needs

```sh
sudo emerge --ask --noreplace dev-vcs/git net-misc/curl \
    media-libs/mesa media-libs/libglvnd x11-libs/libdrm dev-libs/libinput virtual/libudev \
    sys-auth/seatd x11-libs/libxkbcommon x11-misc/xkeyboard-config \
    x11-base/xwayland x11-libs/libxcb x11-libs/libX11 \
    sys-libs/pam media-libs/fontconfig media-libs/freetype media-fonts/dejavu
```

- `sys-auth/seatd` brings libseat: the screen and input without root.
- `x11-base/xwayland` and `x11-libs/libxcb` are for X11 programs.
- `sys-libs/pam`: the lock screen's password (PAM's `login` service).

## 3. Java 27

Java 27 is in testing (`~amd64`). Accept it for this package only:

```sh
echo "dev-java/openjdk-bin:27 ~amd64" | sudo tee /etc/portage/package.accept_keywords/newm-java
sudo emerge --ask dev-java/openjdk-bin:27
```

(`dev-java/openjdk:27` builds it from source instead.) It is linked as
`/usr/lib/jvm/openjdk-bin-27`, where the `newm` command finds it by itself. You do not need
to change the system's Java with `eselect java-vm`. For the build:

```sh
export JAVA_HOME=/usr/lib/jvm/openjdk-bin-27
```

## 4. The seat

- **systemd**: nothing to do; libseat talks to systemd-logind.
- **OpenRC with elogind** (recommended on OpenRC): elogind also sets `XDG_RUNTIME_DIR`,
  which newm needs, and tells newm when the computer goes to sleep, so it can lock first.

  ```sh
  sudo emerge --ask --noreplace sys-auth/elogind sys-apps/dbus
  sudo rc-update add dbus default
  sudo rc-update add elogind boot
  ```

- **OpenRC with the seatd daemon** (`USE=server`):

  ```sh
  sudo rc-update add seatd default && sudo rc-service seatd start
  sudo usermod -aG seat "$USER"
  ```

  Then something else must set `XDG_RUNTIME_DIR` (for example `sys-auth/pam_xdg`).
  Without elogind, newm cannot lock the screen before sleep.

Log out and in again after these.

## 5. Build and install newm

```sh
git clone https://github.com/AristarkhKenebas/newm
cd newm
./install.sh            # for everyone: /usr/local (asks for your password)
```

`./install.sh --user` installs into `~/.local` instead. The script builds newm, puts the
`newm` command on your PATH, and adds newm to the login screen's sessions
(`/usr/share/wayland-sessions/newm.desktop`). More in [Install](../install.md#build-and-install).

To update later: `git pull && ./install.sh`.

## 6. Good to add

A terminal and the helpers of the bar (each is optional):

```sh
sudo emerge --ask --noreplace gui-apps/foot media-video/wireplumber media-libs/libpulse \
    media-sound/playerctl net-misc/networkmanager net-wireless/bluez \
    sys-power/power-profiles-daemon \
    sys-apps/xdg-desktop-portal sys-apps/xdg-desktop-portal-gtk gui-libs/xdg-desktop-portal-wlr
```

- `media-libs/libpulse` brings `pactl`, for the volume popup.
- `brightnessctl` is not in the main Gentoo tree (it is in the GURU overlay). Without it the
  brightness slider and keys do nothing.
- `gui-apps/foot` is in testing (`~amd64`) at the time of writing; any terminal works.
- On OpenRC, PipeWire runs as your user: start it with your session, for example in
  `~/.config/newm/config.kts`: `autostart { app("gentoo-pipewire-launcher") }` (Gentoo's
  wiki, "PipeWire", has the details).

For screen sharing, tell the portal to use wlr, in
`~/.config/xdg-desktop-portal/newm-portals.conf`:

```ini
[preferred]
default=gtk
org.freedesktop.impl.portal.ScreenCast=wlr
org.freedesktop.impl.portal.Screenshot=wlr
```

## Start it

From a text console (Ctrl+Alt+F3, log in):

```sh
newm                    # systemd
dbus-run-session newm   # OpenRC: newm's bar needs a D-Bus session, and nothing else starts one
```

**With a login screen** on OpenRC, put `dbus-run-session` into the session file too:

```sh
sudo sed -i 's|^Exec=|Exec=dbus-run-session |' /usr/share/wayland-sessions/newm.desktop
```

(`install.sh` writes this file again each time: run the line again after updating.)

**greetd** with tuigreet (`gui-libs/greetd`, `gui-apps/tuigreet`). In
`/etc/greetd/config.toml`, keep the `user` line that is there and set the command:

```toml
[default_session]
command = "tuigreet --time --remember --cmd newm"
```

(On OpenRC: `--cmd 'dbus-run-session newm'`.) On systemd: `sudo systemctl enable greetd`.
Gentoo's greetd package has no OpenRC service of its own; Gentoo's wiki ("greetd") shows how
to start it through `display-manager-init`.

The first start writes `~/.config/newm/config.kts`. Press **Logo+/** to see every key.
Next: [Everyday use](../using.md).

## The GPU

- **AMD, Intel**: Mesa, with `VIDEO_CARDS` set as above.
- **NVIDIA**: `x11-drivers/nvidia-drivers` 545 or newer, with kernel modesetting. Drivers
  560 and newer turn it on by themselves; `cat /sys/module/nvidia_drm/parameters/modeset`
  should say `Y`. If it says `N`, add `nvidia-drm.modeset=1` to the kernel's command line.
- **A virtual machine**: a virtual GPU with 3D (virtio with 3D acceleration;
  `VIDEO_CARDS="virgl"`).

## If something goes wrong

- Start newm from a text console: it says what is missing before it gives up.
- The log: `~/.local/state/newm/newm.log` (the start before: `newm.log.old`).
- `Missing system libraries: libGLESv2.so.2` or the like: check `media-libs/libglvnd` and
  `media-libs/mesa`. Skiko failing to load `libGL.so.1`: `media-libs/libglvnd[X]`.
- `XDG_RUNTIME_DIR is not set`: elogind is not running (OpenRC); see [step 4](#4-the-seat).
- `Could not open the seat`: neither logind, elogind nor seatd answers; see [step 4](#4-the-seat).

More in [When something goes wrong](../troubleshooting.md).
