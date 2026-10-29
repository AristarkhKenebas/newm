# newm as the login screen (greetd)

**English** · [Русский](../ru/greeter.md)

newm can be the login screen itself. [greetd](https://sr.ht/~kennylevinsen/greetd/) is a small
login daemon that runs a *greeter* program and lets it log users in; newm is such a greeter.
You then see newm's own lock screen right after the computer starts: choose your user, type
your password, and your newm session starts. There is no second program to set up and no
change of look between the login screen and the desktop.

How it goes:

1. greetd starts `newm` as its greeter user (usually `greeter`; Void's is `_greeter`).
2. newm sees that it is the greeter and shows its lock screen, asking for the user (a list of
   the users who can log in) and then the password.
3. greetd checks the password with its PAM service (newm never sees your system's password
   files).
4. newm asks greetd to start your session and ends. greetd then starts that session as you:
   by default newm again (`greeterCommand` says otherwise).

## Requirements

- **greetd** 0.8 or newer, from your distribution (`greetd`; Gentoo: `gui-libs/greetd`).
- **newm installed for everyone**: `./install.sh` (without `--user`: `/usr/local/bin/newm`),
  your distribution's package, or the NixOS module. The greeter user cannot run a newm in your
  home folder.
- **Java 27** where the greeter user finds it: the system's (`/usr/lib/jvm/...`) or
  `JAVA_HOME`; newm's start script looks in `/usr/lib/jvm` by itself.
- **The seat**: greetd opens a login session for its greeter, so with systemd-logind or
  elogind there is nothing to do. With **seatd**, add the greeter user to seatd's group
  (`sudo usermod -aG _seatd _greeter` on Void, `seat` on most others), as you did for yourself.

## Set it up

`/etc/greetd/config.toml` (keep the `[terminal]` section and the `user` line your package
wrote; only the command changes):

```toml
[terminal]
vt = 1

[default_session]
command = "/usr/local/bin/newm"   # or /usr/bin/newm from a package
user = "greeter"                  # Void: "_greeter", Debian: "_greetd"
```

Then turn on greetd instead of your current login screen:

```sh
sudo systemctl disable sddm gdm ly 2>/dev/null; sudo systemctl enable greetd   # systemd
sudo ln -s /etc/sv/greetd /var/service/                                        # Void (runit)
sudo rc-update add greetd default                                              # Alpine (greetd-openrc)
```

There is no `--greeter` option: newm knows it is the greeter because greetd gives it its
socket (`GREETD_SOCK`), or because it runs as the user named by `greeterUser`.

## The greeter user and its configuration

The greeter user is a system user without a password and without rights of its own: greetd
creates it (`greeter`, `_greeter`, `_greetd`). newm running as that user can only show the
login screen and talk to greetd; it cannot read your files.

Its configuration is found as for anybody: the greeter's own `~/.config/newm/config.kts`
when its home folder can be written (newm writes it from `/etc/newm` the first time), else
**`/etc/newm/config.kts`** (or `config.lua`, `config.json`) directly. So the simplest place for
the login screen's look is `/etc/newm/config.kts`, which `newm install-session` writes and new
users start from. Its log: the greeter's `~/.local/state/newm/newm.log` when it can be written,
else greetd's own log (`journalctl -u greetd`, or the console greetd runs on).

## Settings

| Setting | Default | Meaning |
|---|---|---|
| `greeterUser` | `"greeter"` | the user greetd runs its greeter as. Only needed when greetd does not give newm its socket; with greetd it is found by itself. |
| `greeterCommand` | this newm | what greetd starts once a user is let in: `"newm"`, `"/usr/local/bin/newm"`, a script of your own. Its words are split at spaces (no quotes). |
| `lock { }` | newm's lock screen | the login screen's look and size are the lock screen's: `w`, `h`, `cornerRadius`, `clock`, `showOnLock`, `program`/`cmd` ([Every setting](settings.md#lock--launcher-)). |
| `startupSound { enabled }` | `false` on the login screen | the start's sound (else it would sound twice: the greeter, then your session). |

In `config.kts`:

```kotlin
greeterCommand = "/usr/local/bin/newm"
lock {
    clock = "HH:mm"
}
```

In `config.json` (NixOS's `programs.newm.settings`): `"greeter_command"`, `"greeter_user"`.

## How your session starts

When the password is right, newm sends greetd `start_session` with `greeterCommand` (unset:
the newm the greeter itself runs, its full path) and ends; greetd ends the greeter's login
session and starts yours with that command, as you, with your own environment. newm then starts
as usual with your configuration.

On a system without systemd (Void, Gentoo with OpenRC, Alpine) nothing starts a D-Bus session
bus at login; newm's start script notices and starts newm inside `dbus-run-session` by itself
(see [Install](install.md#start-it)). Nothing to add to `greeterCommand` for that.

If greetd does not accept the command (a program that is not there), the greeter's log says
`greetd did not take the session`, and greetd shows the login screen again.

## NixOS

The newm module sets greetd up for you:

```nix
programs.newm = {
  enable = true;
  greeter.enable = true;   # greetd, with newm as its greeter (user "greeter")
  # The login screen's look, and new users' configuration (/etc/newm/config.json):
  settings = { panels.lock.corner_radius = 30; };
};
```

It turns on `services.greetd` with `default_session.command` set to newm (a default: your own
`services.greetd.settings.default_session.command` wins). Turn off any other display manager
(`services.displayManager.sddm.enable = false`, ...).

## When something goes wrong

- **A text console, or greetd starting the greeter again and again**: newm could not start as
  the greeter. Log in on another console (Ctrl+Alt+F2) and read greetd's log:
  `journalctl -u greetd -b` with systemd, the console greetd runs on otherwise. Usual causes:
  no Java for that user, newm installed with `--user`, no seat rights (seatd's group).
- **The log says `Not in a greetd session`**: newm runs as `greeterUser` but greetd did not
  start it (no `GREETD_SOCK`). Start it through greetd, or set `greeterUser` to a user that
  is not yours.
- **Your user is missing from the list**: the list is `getent passwd` without the greeter and
  without users whose shell is `nologin` or `false`.
- **The password is right but nothing starts**: the greeter's log says why (`greetd did not
  take the session ...`): check `greeterCommand`, and that the command exists for your user.
- **No tray or notifications after logging in, without systemd**: install D-Bus
  (`dbus-run-session`), see [Install](install.md#start-it).
- **The login screen is slow to appear**: Java's AOT cache lives in the greeter's
  `~/.cache/newm/aot`; when its home folder cannot be written there is none and every start is
  a cold one. Giving the greeter user a writable home (greetd's packages often use
  `/var/lib/greetd`) makes later starts faster.
