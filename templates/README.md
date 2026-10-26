# Your newm configuration

newm reads **one** file of this folder, the first it finds:

1. `config.kts` — newm's own (Kotlin): every setting, with notes. Start here.
2. `config.lua` — the same in Lua.
3. `config.json` — the same as data (what Nix's `programs.newm.settings` writes).
4. `config.py` — a configuration of the old (Python) newm.

`examples/` has `config.lua` and `config.json` with the same look as `config.kts`. To use
one, move it here and remove (or rename) `config.kts`.

`bars/` has bars to choose from:

- `classic.kts` — the bar of the original newm (top and bottom), to paste into `config.kts`.
- `waybar/` — waybar in newm's look: copy it to `~/.config/waybar/`, then in `config.kts`
  `panels { topBar { enabled = false }; bar { cmd = "waybar" } }`.

Any other bar works the same way (yambar, eww, ags, ...): newm's own off, the program in
`bar { cmd = ... }` or in `onStartup`. Windows make way for it by themselves.

Save a file and newm takes it on at once. A mistake shows up on screen with its line, and the
settings before stay until it is fixed. To start over, delete this folder: newm writes it
again the next time it starts.

The guide: https://github.com/AristarkhKenebas/newm/blob/master/docs/en/README.md
