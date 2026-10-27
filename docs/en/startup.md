# The start

**English** · [Русский](../ru/startup.md)

When newm starts, the screen stays black — no text console, no half-drawn bar — until newm is
ready: its first frames drawn and its bars drawn once. Then newm fades in with a soft sound,
and only then the programs you start with the session come up, one after another, so the
start does not stutter. All of it is in `config.kts` (see [Every setting](settings.md)):

```kotlin
autostart {
    app("waybar")
    app("nm-applet", "--indicator") {
        delay = 2.seconds                                     // waits this long more
        ifNotRunning = true                                   // not if one runs already
        restart = OnCrash(maxTimes = 3, within = 1.minutes)   // started again after a crash
    }
    shell("sleep 1; notify-send 'Hello'")
    desktopEntries = true    // also the programs of ~/.config/autostart (as other desktops)
}
reveal { fadeMillis = 600 }                       // enabled = false: no black start
startupSound { preset = "classic"; volume = 0.6 }  // enabled = false: silent
```

- **Once per session.** Saving the configuration does not start the programs again: only
  entries you added start, and those you took out are no longer restarted (they keep
  running). `newm cmd autostart` lists them and how each is.
- **The order.** One after another, `stagger` apart (400 ms); `after("waybar")` waits until
  waybar has started. The windows of `newm cmd restart keep` (and, with
  `session { restoreAtLogin = true }`, of your last session) open after them, the same way.
- **The sounds** are made by newm itself — `"airy"`, `"classic"`, `"glass"`, `"soft"` — and
  kept in `~/.cache/newm/sounds`; `file = "~/sounds/hello.ogg"` plays your own instead.
  `newm cmd play-startup-sound glass` plays one now. They are played with pw-play, paplay or
  aplay (or your `player`).

## A faster start

newm's start script keeps a cache of the Java classes newm loaded (a JDK AOT cache, in
`~/.cache/newm/aot/`), one for each build of newm: the first start after an update makes it
(when newm ends: logging out takes 5 to 20 seconds longer, once), and every start after
that is faster — on the owner's laptop, to the first frame in about 0.9 s instead of 2.1 s.
A cache that does not fit is only noted in its log, and made anew. It never keeps newm from
starting: with a cache folder that cannot be written (made by root, a read-only disk), or
whose path has a `:` or `,`, newm starts without one, and when Java does not start with the
cache, once more without it. `NEWM_AOT=0` turns it off. Builds are told apart by the Java,
where newm lies (a Nix store path is another one for every build, though Nix gives every
file the same time), its jars' sizes and times, and the Java options (`JAVA_OPTS`,
`NEWM_OPTS`).

The start script stays while newm runs (as its parent: `newm cmd restart` ends newm with code 3,
and the script starts it again). What the script is sent — `pkill newm`, the end of the login —
it passes on to newm, and it ends with it. Under `dev/newm-dev` (which restarts newm itself,
`NEWM_SUPERVISED=1`) newm takes the script's place: there `pkill -f newm.MainKt` ends it.
