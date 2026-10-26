#!/bin/sh
# Builds newm and installs it, so that `newm` starts it from a text console and the login
# screen (ly, SDDM, GDM) lists it:
#
#   ./install.sh            for everyone: /usr/local/lib/newm, /usr/local/bin/newm (asks for sudo)
#   ./install.sh --user     for you alone: ~/.local/lib/newm, ~/.local/bin/newm
#
# Then: `newm` on a text console, or "newm" in the login screen. Your configuration is
# ~/.config/newm/config.kts (newm writes it the first time; saved changes apply at once).
set -eu
cd "$(dirname "$0")"

# Built against Caulis's sources when they are next to this folder (until the Caulis it needs is released).
local_caulis=""
[ -d ../caulis/caulis-core ] && local_caulis="-Pcaulis.local=true"
./gradlew installDist $local_caulis

if [ "${1:-}" = "--user" ]; then
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
"$bin/newm" install-session
