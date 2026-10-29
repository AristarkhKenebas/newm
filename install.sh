#!/bin/sh
# Builds newm and installs it, so that `newm` starts it from a text console and the login
# screen (ly, SDDM, GDM) lists it:
#
#   ./install.sh            for everyone: /usr/local/lib/newm, /usr/local/bin/newm (asks for sudo)
#   ./install.sh --user     for you alone: ~/.local/lib/newm, ~/.local/bin/newm
#
# The login screen's session starts newm inside dbus-run-session by itself where no systemd user
# session starts a D-Bus bus (Void, Gentoo with OpenRC, Alpine, ...). To decide that yourself, add
# --dbus-run-session (always) or --no-dbus-run-session (never); both are passed on to
# `newm install-session`.
#
# Then: `newm` on a text console, or "newm" in the login screen. Your configuration is
# ~/.config/newm/config.kts (newm writes it the first time; saved changes apply at once).
set -eu
cd "$(dirname "$0")"

user=""; session_options=""
for arg in "$@"; do
    case "$arg" in
        --user) user=1 ;;
        --dbus-run-session|--no-dbus-run-session) session_options="$session_options $arg" ;;
        *) echo "install.sh: unknown option $arg (--user, --dbus-run-session, --no-dbus-run-session)" >&2; exit 2 ;;
    esac
done

# Built against Caulis's sources when they are next to this folder (until the Caulis it needs is released).
local_caulis=""
[ -d ../caulis/caulis-core ] && local_caulis="-Pcaulis.local=true"
./gradlew installDist $local_caulis

if [ -n "$user" ]; then
    lib="$HOME/.local/lib/newm"; bin="$HOME/.local/bin"; as=""
else
    lib="/usr/local/lib/newm"; bin="/usr/local/bin"; as="sudo"
fi

echo "Installing into $lib (the newm command: $bin/newm)"
$as rm -rf "$lib"
$as mkdir -p "$lib" "$bin"
$as cp -r build/install/newm/. "$lib/"
$as ln -sf "$lib/bin/newm" "$bin/newm"

case ":$PATH:" in
    *":$bin:"*) ;;
    *) echo "Note: $bin is not on your PATH yet (add it in your shell's profile)." ;;
esac

# The login screen's session entry and /etc/newm/config.kts.
# (Its Exec= is worked out anew each time: with dbus-run-session only where it is needed.)
# shellcheck disable=SC2086
"$bin/newm" install-session $session_options
