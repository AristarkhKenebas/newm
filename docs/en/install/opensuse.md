# newm on openSUSE Tumbleweed

**English** · [Русский](../../ru/install/opensuse.md)

This page is for openSUSE Tumbleweed (and Slowroll, which takes Tumbleweed's packages
later). Leap's libraries and Java are too old for newm.

## 1. What newm needs

```sh
sudo zypper install git-core curl java-26-openjdk-devel \
    Mesa Mesa-dri Mesa-libEGL1 libgbm1 libglvnd libdrm2 libinput10 libudev1 libseat1 \
    libxkbcommon0 xkeyboard-config xwayland libxcb1 libxcb-composite0 libxcb-xfixes0 \
    libxcb-render0 libX11-6 pam libfontconfig1 libfreetype6 dejavu-fonts
```

- `java-26-openjdk-devel`: Tumbleweed has Java 26 now, which is enough to run newm. When
  `java-27-openjdk-devel` comes, take that instead.
- `Mesa*`, `libgbm1`, `libglvnd`: the GPU driver for AMD, Intel and others.
- `libseat1`: the screen and input without root. It talks to systemd-logind: no service to
  start.
- `xwayland` and the `libxcb*` packages are for X11 programs.
- `pam`: the lock screen's password.

## 2. Java

The build compiles with JDK 27. With only Java 26 installed, Gradle downloads JDK 27 for
the build by itself (into `~/.gradle/jdks`); newm then runs on your Java 26. To have Java 27
yourself instead, see [the general page](../install.md#java-27).

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
sudo zypper install foot wireplumber pulseaudio-utils brightnessctl playerctl \
    NetworkManager bluez power-profiles-daemon \
    xdg-desktop-portal xdg-desktop-portal-gtk xdg-desktop-portal-wlr
```

- `pulseaudio-utils`: only for `pactl`, for the volume popup. It works with PipeWire.
- The Wi-Fi popup needs NetworkManager to be what runs your network (YaST, "Network
  Settings", "Global Options" shows which one does).

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

Or log out and choose "newm" in your login screen (SDDM, GDM).

Without a login screen, greetd with tuigreet is a small one:

```sh
sudo zypper install greetd tuigreet
```

In `/etc/greetd/config.toml`, keep the `user` line that is there and set the command:

```toml
[default_session]
command = "tuigreet --time --remember --cmd newm"
```

openSUSE starts its login screen through its own `display-manager` service, so switching
to greetd takes more than enabling it: follow openSUSE's documentation for that. Starting
newm from a text console always works.

The first start writes `~/.config/newm/config.kts`. Press **Logo+/** to see every key.
Next: [Everyday use](../using.md).

## The GPU

- **AMD, Intel**: Mesa, from the packages above.
- **NVIDIA**: the proprietary driver 545 or newer, from NVIDIA's openSUSE repository
  (see openSUSE's wiki, "SDB:NVIDIA drivers"), with kernel modesetting: add
  `nvidia-drm.modeset=1` to the kernel's command line (YaST "Boot Loader", or
  `/etc/default/grub` and `sudo grub2-mkconfig -o /boot/grub2/grub.cfg`), and reboot.
  `cat /sys/module/nvidia_drm/parameters/modeset` should say `Y`. Drivers 560 and newer
  turn it on by themselves; then the kernel option is not needed.
- **A virtual machine**: a virtual GPU with 3D (virtio with 3D acceleration).

## If something goes wrong

- Start newm from a text console: it says what is missing before it gives up.
- The log: `~/.local/state/newm/newm.log` (the start before: `newm.log.old`).
- `newm needs Java 26 or newer`: `sudo zypper install java-26-openjdk-devel`.
- `Could not open the seat`: start it from a text console where you logged in, not with
  `su` or `sudo`.

More in [When something goes wrong](../troubleshooting.md).
