# Lua, JSON, Nix and the old config.py

**English** · [Русский](../ru/other-formats.md)

`config.kts` is newm's own format, and the one with the most notes. You can also write the
same configuration in another language. newm reads **one** file of `~/.config/newm/`, the
first it finds:

1. `config.kts`
2. `config.lua`
3. `config.json` (what Nix writes)
4. `config.py` (a configuration of the old, Python newm)

So to use another format, put its file there and remove (or rename) `config.kts`. Every
format reloads on save and shows its mistakes the same way. A `config.json` lying next to a
`config.kts` is not read, and a card says so.

The names are those of [Every setting](settings.md), in snake_case: `cornerRadius` is
`corner_radius`, `topBar` is `top_bar`.

**A file in any format is the whole configuration.** What it leaves out has newm's built-in
default, which is not always what the starting `config.kts` has: without a `keys` table there
are no keys at all, and without `top_bar = { enabled = true }` there is no bar. Start from
your folder's `examples/`, which have both.

## Lua

Your folder's `examples/config.lua` is the configuration in Lua:

```lua
view = { corner_radius = 14, padding = 8 }

keyboard = { layouts = "us,ru", options = "grp:alt_shift_toggle" }

panels = { top_bar = { enabled = true } }

rules = {
  { app_id = "mpv", float = true },
  { app_id = "Alacritty", blur = { radius = 6, passes = 4 } },
}

keys = {
  ["L-Return"] = "terminal",
  ["L-h"] = "move -1 0",
  ["L-v"] = "toggle_clipboard",
  ["L-S-v"] = "toggle_focused_view_floating",
  ["L-S-s"] = "screenshot",
  ["L-b"] = function() newm.launch("firefox") end,
}

startup = { "mako" }
```

- An action is a text (`"move -1 0"`, `"launch firefox"`, `"volume +5"`) or a function.
- Functions can call `layout.<method>(...)` (the calls of [the table](settings.md#everything-a-key-can-do),
  in snake_case: `layout.toggle_overview()`), `newm.launch("command")`, `newm.run("volume +5")`,
  `newm.terminal()` and `newm.notify("text")`.
- `sys.wifi()`, `sys.battery()`, `sys.time("HH:mm")` and the rest give the bar's texts.
- `rules` match by `app_id` or `title`, exactly, or by a pattern: `app_id_matches`,
  `title_matches`. The first rule that fits counts.

## JSON

Your folder's `examples/config.json` is the same as data. Sections are tables; keys,
rules and startup programs are written as in Lua:

```json
{
  "view": { "corner_radius": 14, "padding": 8 },
  "keyboard": { "layouts": "us,ru", "options": "grp:alt_shift_toggle" },
  "input": { "tap_to_click": true, "natural_scroll": false },
  "cursor": { "theme": "Adwaita", "size": 24 },
  "outputs": [ { "name": "eDP-1", "scale": 1.25 } ],
  "panels": { "top_bar": { "enabled": true, "right": ["tray", "network", "volume", "battery", "power"] } },
  "rules": [ { "app_id": "mpv", "float": true } ],
  "keys": { "L-Return": "terminal", "L-d": "enter_launcher_overlay", "L-S-Escape": "terminate" },
  "startup": [ "mako" ]
}
```

Bar texts are given by name: `"texts": ["wifi", "battery", "time"]`. Lines starting with `//`
are notes.

## Names, and the forms of blocks

Every block of `config.kts` is a table of the same name, its settings in snake_case. Where
`config.kts` has a function or a block inside a block, Lua and JSON write it so (the old
names of newm, on the right, keep working):

| `config.kts` | Lua / JSON | newm's own name |
|---|---|---|
| `gestures { enabled; scalePx }` | `gestures.enabled`, `gestures.scale_px` | `gestures.c.enabled`, `gestures.c.scale_px` |
| `gestures { dbusEnabled }` | `gestures.dbus_enabled` | `gestures.dbus.enabled` |
| `gestures { bind(GestureAction.SWIPE, null, "swipe-3") }` | `gestures.bind.swipe = [null, "swipe-3"]` (also `{ "modifier": "L", "kinds": ["move-1", "swipe-2"] }`) | `gesture_bindings.swipe` |
| `gestures { on("swipe-4", "down") { toggleOverview() } }` | `gestures.on = [ { "kind": "swipe-4", "direction": "down", "action": "toggle_overview" } ]` (also `modifier`, `description`) | |
| `tuning { swipe { gestureFactor = 4.0 } }` | `tuning.swipe.gesture_factor` | `swipe.gesture_factor` |
| `output("eDP-1") { x; y; refreshHz }` | `outputs = [ { "name": "eDP-1", "x": 0, "y": 0, "refresh_hz": 60 } ]` | `pos_x`, `pos_y`, `mHz` (millihertz) |
| `input { tablet { leftHanded = true } }` | `input.tablet.left_handed` | |
| `energy { onIdle { } }` | `energy.on_idle` (a Lua function of one argument) | `energy.idle_callback` |
| `energy { after(900) { ... } }` | `energy.after = { [900] = "launch playerctl pause" }`; JSON `"after": { "900": "..." }` | |
| `everyFrame { }`, `onStartup { }`, `onReconfigure { }` | `every_frame`, `on_startup`, `on_reconfigure` (Lua functions); `startup` (a list of commands) | `synchronous_update` |
| `launcher { }`, `lock { }` | `launcher`, `lock` (or inside `panels`) | `panels.launcher`, `panels.lock` |
| `launcher { entries { "Lock" { ensureLocked() } } }` | `launcher.entries = { Lock = "ensure_locked" }` (or a list of `{ name, comment, action }`) | |
| `launcher { keys { "C-n" { next() } } }` | `launcher.keys = { ["C-n"] = "next" }`: `next`, `previous`, `first`, `last`, `start`, `run_typed`, `close` | |
| `panels { topBarProgram { cmd } }` | `panels.top_bar.cmd` (next to newm's own bar's settings) | |
| `terminal = "foot"` | `terminal = "foot"` | |

## Nix

newm's flake has a package, a NixOS module and a home-manager module. How to add them is in
[Install](install/nixos.md); here is what they write.

**NixOS**: `programs.newm.settings` becomes `/etc/newm/config.json`. A user without a
`~/.config/newm` gets a copy of it at their first start; after that their copy counts.

```nix
programs.newm = {
  enable = true;
  settings = {
    view = { corner_radius = 24; padding = 8; };
    panels.top_bar.enabled = true;
    keys = { "L-Return" = "terminal"; "L-d" = "enter_launcher_overlay"; "L-S-Escape" = "terminate"; };
  };
};
```

**home-manager** (your own configuration, fully declarative):

```nix
{
  imports = [ newm.homeManagerModules.default ];

  programs.newm = {
    enable = true;
    settings = {
      view = { corner_radius = 24; padding = 8; };
      keyboard = { layouts = "us,ru"; options = "grp:alt_shift_toggle"; };
      keys = {
        "L-Return" = "terminal";
        "L-d" = "enter_launcher_overlay";
        "L-h" = "move -1 0";
        "L-S-Escape" = "terminate";
      };
      rules = [ { app_id = "mpv"; float = true; } ];
      panels.top_bar.enabled = false;
      panels.bar.cmd = "waybar";
      startup = [ "mako" ];
    };
  };
}
```

`settings` is written to `~/.config/newm/config.json`, and newm takes it on as soon as
home-manager writes it. To write Kotlin or Lua in Nix, set `configKts` or `configLua` instead
(only one of the three): `configKts = builtins.readFile ./config.kts;` keeps every note of
newm's own file. A `config.kts` or `config.lua` of your own in that folder is read before
`config.json`; newm then says on screen that `config.json` is not used.

## The old config.py

A `config.py` of the old newm still works: put it at `~/.config/newm/config.py`, with no
other configuration file next to it. newm runs it with Python as before, including your key
bindings, rules and `on_startup`.

You may need to change a line or two. Settings for pywm's own renderer
(`pywm.renderer_mode`, `texture_shaders`, and similar) are ignored, because newm no longer
uses pywm.

Moving to `config.kts` is worth it. New settings come there first, editors highlight it,
and its mistakes are clearer. The sections are the same; the names are in camelCase.
