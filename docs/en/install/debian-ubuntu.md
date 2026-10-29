# newm on Debian and Ubuntu

**English** · [Русский](../../ru/install/debian-ubuntu.md)

This page is for Debian 13 (trixie) and newer, Ubuntu 24.04 and newer, and the systems built
on them: Linux Mint 22, Pop!_OS 24.04, and others. Older releases have an Xwayland or
libraries that are too old.

## 1. What newm needs

On Ubuntu and its relatives, some packages are in "universe". It is on by default on
desktops; if `apt` cannot find a package, turn it on with `sudo add-apt-repository universe`.

```sh
sudo apt update
sudo apt install git curl \
    libegl1 libgles2 libgl1 libegl-mesa0 libgl1-mesa-dri libgbm1 libdrm2 \
    libinput10 libudev1 libseat1 libxkbcommon0 xkb-data \
    xwayland libxcb1 libxcb-composite0 libxcb-xfixes0 libxcb-render0 libx11-6 \
    libpam0g libfontconfig1 libfreetype6 fonts-dejavu-core
```

- `libegl-mesa0`, `libgl1-mesa-dri`, `libgbm1`: Mesa, the GPU driver for AMD, Intel and others.
- `libseat1`: the screen and input without root. It talks to systemd-logind: no service to start.
- `xwayland` and the `libxcb` packages are for X11 programs.
- `libpam0g`: the lock screen's password.
- `libgl1`, `libx11-6`, `libfontconfig1`: Skiko, which draws newm's bar, launcher and lock
  screen, needs them.

Most of these are already installed on a desktop system.

## 2. Java 27

Which release has which Java:

| Release | Command |
|---|---|
| Debian 14 (forky, testing), Debian unstable | `sudo apt install openjdk-27-jdk` |
| Ubuntu 26.10 | `sudo apt install openjdk-27-jdk` |
| Ubuntu 26.04 | `sudo apt install openjdk-26-jdk` (enough to run newm; the build downloads JDK 27 by itself) |
| Debian 13, Ubuntu 24.04, Mint 22, Pop!_OS 24.04 | no package: use Temurin, below |

Temurin, Eclipse's build of Java 27, into `/usr/lib/jvm` (where the `newm` command looks):

```sh
sudo mkdir -p /usr/lib/jvm/temurin-27
curl -L https://api.adoptium.net/v3/binary/latest/27/ga/linux/x64/jdk/hotspot/normal/eclipse \
  | sudo tar -xz -C /usr/lib/jvm/temurin-27 --strip-components=1
export JAVA_HOME=/usr/lib/jvm/temurin-27     # for the build below
```

On ARM, write `aarch64` instead of `x64`.

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
sudo apt install foot wireplumber pulseaudio-utils brightnessctl playerctl \
    network-manager bluez power-profiles-daemon \
    xdg-desktop-portal xdg-desktop-portal-gtk xdg-desktop-portal-wlr
```

- `wireplumber`: the volume keys (`wpctl`), with PipeWire (the default sound on current
  Debian and Ubuntu desktops).
- `pulseaudio-utils`: only for `pactl`, for the volume popup. It works with PipeWire.

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

Or log out and choose "newm" in your login screen. GDM (Ubuntu, Debian GNOME) shows it
behind the gear button; SDDM and LightDM-based screens in their session list.

Without a login screen, greetd with tuigreet is a small one:

```sh
sudo apt install greetd tuigreet
```

In `/etc/greetd/config.toml`, keep the `user` line that is there and set the command:

```toml
[default_session]
command = "tuigreet --time --remember --cmd newm"
user = "_greetd"
```

```sh
sudo systemctl enable greetd     # disable your other login screen first, if any
```

Or newm itself as the login screen, with its own lock screen: [newm as the login screen](../greeter.md).

(`tuigreet` is packaged in Debian 13 and from Ubuntu 26.04 on. On Ubuntu 24.04 and its
relatives, keep the login screen you have.)

The first start writes `~/.config/newm/config.kts`. Press **Logo+/** to see every key.
Next: [Everyday use](../using.md).

## The GPU

- **AMD, Intel**: Mesa, from the packages above.
- **NVIDIA**: the proprietary driver 545 or newer (Ubuntu: "Additional Drivers"; Debian: the
  `nvidia-driver` package from non-free), with kernel modesetting: add
  `nvidia-drm.modeset=1` to the kernel's command line (`GRUB_CMDLINE_LINUX_DEFAULT` in
  `/etc/default/grub`, then `sudo update-grub`), and reboot.
  `cat /sys/module/nvidia_drm/parameters/modeset` should say `Y`. Drivers 560 and newer
  turn it on by themselves; then the kernel option is not needed.
- **A virtual machine**: a virtual GPU with 3D (virtio with 3D acceleration).

## If something goes wrong

- Start newm from a text console: it says what is missing before it gives up.
- The log: `~/.local/state/newm/newm.log` (the start before: `newm.log.old`).
- `newm needs Java 26 or newer`: see [Java 27](#2-java-27).
- `Could not open the seat`: start it from a text console where you logged in. If it still
  says so, run seatd: `sudo apt install seatd && sudo systemctl enable --now seatd`, and add
  yourself to the group seatd uses (`systemctl cat seatd` shows it after `-g`), then log in
  again.

More in [When something goes wrong](../troubleshooting.md).
