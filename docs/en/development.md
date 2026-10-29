# Working on newm

**English** · [Русский](../ru/development.md)

newm is Kotlin, on the [Caulis](https://github.com/AristarkhKenebas/caulis) compositor
library.

```
src/main/kotlin/newm/   newm itself: the layout, gestures, overlays, bar, lock screen, launcher
templates/              what newm writes to ~/.config/newm (config.kts, examples, bars)
nix/                    the package (package.nix, deps.json), the NixOS and home-manager modules
docs/                   this documentation (en, ru)
dev/newm-dev            newm from its sources, for working on newm inside newm
legacy/                 the original Python newm, for reference
```

## Build and test

```sh
./gradlew installDist     # build/install/newm/bin/newm
./gradlew test
```

With Caulis's sources next to this folder (`../caulis`), add `-Pcaulis.local=true` to build
against them. `./install.sh` and `dev/newm-dev` do this by themselves.

To try newm without leaving your desktop, run it in a terminal on a D-Bus session of its own
(so it never touches your desktop's notifications, tray or media keys); it opens as a window:

```sh
dbus-run-session -- build/install/newm/bin/newm
```

Headless (no window at all; for scripts and measurements), give it its own home and bus too:

```sh
env -u WAYLAND_DISPLAY -u DBUS_SESSION_BUS_ADDRESS CAULIS_MODE=headless HOME=$(mktemp -d) \
    dbus-run-session -- build/install/newm/bin/newm
```

(newm nested or headless never closes your notification daemon even on your own bus, but it
would still take names there.) `./gradlew test` keeps the tests off the desktop's buses by
itself.

The Nix package builds offline from the dependencies listed in `nix/deps.json`. After a
change to the dependencies (a new Caulis version in `gradle/libs.versions.toml`), make it
again: `nix run .#update-deps` (it uses the flake's pinned Gradle, input `nixpkgs-gradle`,
and records its version in `nix/deps-gradle-version`).

## Working on newm inside newm

`dev/newm-dev` runs newm from the sources as your session, so you can change newm while
you use it:

```sh
dev/newm-dev install-session     # once: "newm (dev)" in the login screen
```

Log in to "newm (dev)", or run `dev/newm-dev` from a text console. Then, inside:

- **`dev/newm-dev rebuild`** builds newm and, if the build succeeds, restarts newm with it.
  A good key for it:

  ```kotlin
  keys { "L-S-r" { launch("foot --hold ~/projects/newm/dev/newm-dev rebuild") } }
  ```

- **A build with mistakes changes nothing.** The running newm stays, and the mistakes are
  in the terminal.
- **A new build that crashes at the start is rolled back.** The build before starts again,
  and a card tells you so. Its log is in `~/.local/state/newm/newm.log.old`.
- **Hot swap without a restart.** A debugger can attach at `localhost:5005` (IntelliJ:
  *Run → Attach to Process*). *Reload Changed Classes* then puts changed method bodies into
  the running newm, and your windows stay where they are. Changes to classes' shapes (new
  fields or methods) need a rebuild.
- **The configuration reloads on save**, as always.
- Changes to Caulis in `../caulis` are built along with newm.

The builds are kept in `~/.cache/newm-dev`: `good` is the one running, `good.prev` the one
before. The script's own log is `~/.cache/newm-dev/newm-dev.log`.

A restart closes the programs that were open, as logging out does; `newm cmd restart keep`
opens them again afterwards, each in its place. Hot swap keeps them.

To see a change of newm's at work, `newm cmd debug` prints the layout, and `newm cmd log
<area> 3` puts one area of the log in detail while newm runs (see
[Testing newm](testing.md#more-detail-in-the-log)).
