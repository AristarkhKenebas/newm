# newm on Alpine Linux

**English** · [Русский](../../ru/install/alpine.md)

**Untested.** Alpine uses musl instead of glibc. Java, Gradle, Caulis and the libraries newm
opens all exist for musl, but newm's own windows (the bar, the launcher, the lock screen) are
drawn with Skiko, whose native part is built for glibc only. Alpine's `gcompat` lets some
glibc programs run on musl; whether it is enough for Skiko nobody has tried yet. If you try,
please [open an issue](https://github.com/AristarkhKenebas/newm/issues) with what happened.
The steps below are what we expect to be needed. They are checked against Alpine 3.24's
package lists.

## 1. What newm needs

Alpine's `setup-desktop` (or `setup-devd udev`) sets up eudev, which newm needs for its
devices. Then, with the `community` repository enabled:

```sh
doas apk add git curl coreutils gcompat libstdc++ \
    mesa-dri-gallium mesa-egl mesa-gles mesa-gbm mesa-gl libdrm libinput-libs eudev-libs \
    libseat libxkbcommon xkeyboard-config xwayland libxcb libx11 \
    linux-pam fontconfig freetype font-dejavu dbus
```

- `mesa-*`: the GPU driver for AMD, Intel and others.
- `libseat`: the screen and input without root. Alpine builds it to talk to seatd or elogind.
- `xwayland`, `libxcb`: for X11 programs.
- `linux-pam`: the lock screen's password (it brings PAM's `login` service).
- `gcompat`, `libstdc++`: for Skiko (see above).
- `coreutils`: newm's start script uses options of `ls` and `sort` that busybox may not have.

## 2. The seat

Choose one.

**seatd** (Alpine's usual way):

```sh
doas apk add seatd seatd-openrc
doas rc-update add seatd
doas rc-service seatd start
doas adduser "$USER" seat
```

**elogind**:

```sh
doas apk add elogind elogind-openrc
doas rc-update add elogind
doas rc-service elogind start
```

newm also needs `XDG_RUNTIME_DIR`. elogind sets it. With seatd, `pam-rundir` can
(`doas apk add pam-rundir`, then follow Alpine's wiki, "Wayland", for where to enable it).

Log out and in again; `echo $XDG_RUNTIME_DIR` should print a folder.

## 3. Java 27

Alpine has no Java 26 or 27 package yet. Temurin has a build for musl
(`alpine-linux`). Put it in `/usr/lib/jvm`, where the `newm` command looks:

```sh
doas mkdir -p /usr/lib/jvm/temurin-27
curl -L https://api.adoptium.net/v3/binary/latest/27/ga/alpine-linux/x64/jdk/hotspot/normal/eclipse \
  | doas tar -xz -C /usr/lib/jvm/temurin-27 --strip-components=1
export JAVA_HOME=/usr/lib/jvm/temurin-27     # for the build below
```

On ARM, write `aarch64` instead of `x64`.

## 4. Build and install newm

`install.sh` calls `sudo`. On Alpine, either install `sudo`, or build and install by hand:

```sh
git clone https://github.com/AristarkhKenebas/newm
cd newm
./gradlew installDist
doas mkdir -p /usr/local/lib/newm
doas cp -r build/install/newm/. /usr/local/lib/newm/
doas ln -sf /usr/local/lib/newm/bin/newm /usr/local/bin/newm
```

`newm install-session` (the login screen's entry and `/etc/newm`) also uses `sudo`, or
`pkexec` when there is no `sudo`. Run it as root instead: `doas newm install-session`.

## 5. Good to add

```sh
doas apk add foot pipewire pipewire-pulse wireplumber pulseaudio-utils brightnessctl \
    playerctl networkmanager bluez power-profiles-daemon \
    xdg-desktop-portal xdg-desktop-portal-gtk xdg-desktop-portal-wlr
```

PipeWire runs as your user on Alpine; Alpine's wiki ("PipeWire") shows how to start it with
your session. newm's `autostart { }` in `~/.config/newm/config.kts` can start it.

For screen sharing, tell the portal to use wlr, in
`~/.config/xdg-desktop-portal/newm-portals.conf`:

```ini
[preferred]
default=gtk
org.freedesktop.impl.portal.ScreenCast=wlr
org.freedesktop.impl.portal.Screenshot=wlr
```

## Start it

Nothing starts a D-Bus session for you on Alpine, and newm's bar needs one. From a text
console:

```sh
dbus-run-session newm
```

For a login screen (`greetd` with `greetd-tuigreet` and `greetd-openrc`, or SDDM), put
`dbus-run-session` in front of `newm` the same way, as the [Void](void.md#start-it) page
shows.

## If something goes wrong

- The log: `~/.local/state/newm/newm.log` (the start before: `newm.log.old`).
- Errors about `libskiko`, `libc.so.6`, `ld-linux` or missing symbols in the log: Skiko does
  not load on musl. This is the known open question above; please report it.
- `Could not open the seat`: seatd or elogind is not running, or you are not in `seat`.
- `XDG_RUNTIME_DIR is not set`: see [step 2](#2-the-seat).

More in [When something goes wrong](../troubleshooting.md).
