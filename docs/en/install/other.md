# newm on other systems

**English** · [Русский](../../ru/install/other.md)

Your system is not on the list? newm needs nothing special: a Linux kernel with DRM, glibc,
Java 27 and a handful of common libraries. This page says what they are and how to find
your system's names for them.

## The libraries

newm does not link these when it is built: it opens them by file name when it starts. So
what counts is that these files exist, from any package.

| File | What for | Needed |
|---|---|---|
| `libEGL.so.1`, `libGLESv2.so.2` | drawing on the GPU (libglvnd or Mesa) | yes |
| `libgbm.so.1` | GPU buffers (Mesa; NVIDIA's driver brings its own GBM part) | yes |
| `libdrm.so.2` | the monitors | yes |
| `libinput.so.10` | keyboards, mice, touchpads | yes |
| `libudev.so.1` | devices coming and going (systemd, eudev or libudev-zero) | yes |
| `libseat.so.1` | the screen and input without root (from seatd) | yes |
| `libxkbcommon.so.0`, and the xkeyboard-config data | keyboard layouts | yes |
| `libGL.so.1`, `libX11.so.6`, `libfontconfig.so.1` | Skiko, which draws newm's bar, launcher and lock screen | yes |
| some fonts | text | yes |
| `libpam.so.0`, and `/etc/pam.d/login` | the lock screen's password | for the lock screen |
| Xwayland 23.1 or newer, `libxcb.so.1`, `libxcb-composite.so.0`, `libxcb-xfixes.so.0`, `libxcb-render.so.0` | X11 programs | for X11 programs |

Check what you have:

```sh
for f in libEGL.so.1 libGLESv2.so.2 libgbm.so.1 libdrm.so.2 libinput.so.10 libudev.so.1 \
         libseat.so.1 libxkbcommon.so.0 libGL.so.1 libX11.so.6 libfontconfig.so.1 libpam.so.0 \
         libxcb.so.1 libxcb-composite.so.0 libxcb-xfixes.so.0 libxcb-render.so.0; do
    ldconfig -p | grep -q "$f" && echo "ok       $f" || echo "MISSING  $f"
done
```

When something is missing, newm itself says so too, when you start it from a text console:
`Missing system libraries: ...`.

## Finding the package names

Ask your package manager which package has a file:

| System | Command |
|---|---|
| Arch and relatives | `pacman -F libseat.so.1` (after `sudo pacman -Fy`) |
| Debian, Ubuntu | `apt-file search libseat.so.1` (after `sudo apt install apt-file && sudo apt-file update`) |
| Fedora, RHEL relatives | `dnf provides '*/libseat.so.1'` |
| openSUSE | `zypper search --provides 'libseat.so.1()(64bit)'` |
| Void | `xlocate libseat.so.1` (from `xtools`) |
| Alpine | `apk search so:libseat.so.1` |
| Gentoo | `e-file libseat.so.1` (from `app-portage/pfl`) |

Or search for the library's name on [pkgs.org](https://pkgs.org) or
[Repology](https://repology.org): names are usually close (`seatd` or `libseat`,
`libinput` or `libinput10`, ...).

## The seat

newm opens the GPU and the input devices through libseat, without root. libseat asks one of:

- **systemd-logind**: on every systemd distribution. Nothing to set up.
- **elogind**: logind without systemd. Run its service (and the system D-Bus). It also sets
  `XDG_RUNTIME_DIR` and tells newm before the computer sleeps, so newm can lock first.
- **seatd**: a small daemon. Run its service and add yourself to the group it uses
  (`seat`, `_seatd` or `video`, depending on the system).

Which ones work depends on how your system built libseat. With seatd alone, you also need
something that sets `XDG_RUNTIME_DIR` (newm cannot start without it): elogind, turnstile,
pam_rundir, or your system's way.

## Java 27

See [Java 27](../install.md#java-27) on the general page: a package if your system has one,
else Temurin's tarball in `/usr/lib/jvm`.

## Build and install

```sh
git clone https://github.com/AristarkhKenebas/newm
cd newm
./install.sh            # for everyone: /usr/local (uses sudo)
./install.sh --user     # or just for you: ~/.local
```

Without `install.sh`: `./gradlew installDist` builds newm into `build/install/newm/`. Copy
that folder anywhere and run its `bin/newm`. More in [Install](../install.md#build-and-install).

## The session file

Login screens find sessions in `/usr/share/wayland-sessions/`. `newm install-session` (run by
`install.sh`) writes `/usr/share/wayland-sessions/newm.desktop`:

```ini
[Desktop Entry]
Name=newm
Comment=Windows on an endless wall, made for touchpads
Exec=/usr/local/bin/newm
TryExec=/usr/local/bin/newm
Type=Application
DesktopNames=newm
```

Without systemd, nothing starts a D-Bus session bus at login, and newm's bar (the tray,
notifications, the portals) needs one. newm takes care of it by itself: `newm install-session`
(which `install.sh` runs) writes the login screen's session as `dbus-run-session newm` when no
systemd user session starts a bus, and newm's start script starts newm inside
`dbus-run-session` when there is no bus, also from a text console. Only the `dbus` package
must be installed (it has `dbus-run-session`). To decide it yourself:
`newm install-session --dbus-run-session` (always) or `--no-dbus-run-session` (never; the same
options for `install.sh`), and `NEWM_DBUS=off` in the environment stops the start script's.

## Login screens

- **GDM, SDDM, LightDM, ly**: they read `/usr/share/wayland-sessions/`; choose "newm" in
  their session list.
- **greetd**: set the greeter's command in `/etc/greetd/config.toml`; keep the `user` line
  your package wrote:

  ```toml
  [default_session]
  command = "tuigreet --time --remember --cmd newm"
  ```

  tuigreet can also offer the sessions of `/usr/share/wayland-sessions/` (its `--sessions`
  option).
- **newm itself**: greetd with newm as its greeter, see [newm as the login screen](../greeter.md).
- **None**: log in on a text console and type `newm`.

## Portals

For file pickers and screen sharing, install `xdg-desktop-portal`, `xdg-desktop-portal-gtk`
and `xdg-desktop-portal-wlr`, and write `~/.config/xdg-desktop-portal/newm-portals.conf`:

```ini
[preferred]
default=gtk
org.freedesktop.impl.portal.ScreenCast=wlr
org.freedesktop.impl.portal.Screenshot=wlr
```

## The GPU

- **Mesa** (AMD, Intel, and others): EGL, GLES and GBM from Mesa are all.
- **NVIDIA**: the proprietary driver 545 or newer, with its GBM part, and kernel modesetting
  (`cat /sys/module/nvidia_drm/parameters/modeset` says `Y`; drivers 560 and newer turn it on
  by themselves, older ones need `nvidia-drm.modeset=1` on the kernel's command line).
- **A virtual machine**: a virtual GPU with 3D (virtio with 3D acceleration).

## If something goes wrong

Start newm from a text console: it says in plain words what is missing. The log is
`~/.local/state/newm/newm.log`. More in [When something goes wrong](../troubleshooting.md).
