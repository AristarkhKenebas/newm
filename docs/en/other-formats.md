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
format reloads on save and shows its mistakes the same way.

The names are those of [Every setting](settings.md), in snake_case: `cornerRadius` is
`corner_radius`, `topBar` is `top_bar`.

## Lua

Your folder's `examples/config.lua` is the whole default configuration in Lua:

```lua
view = { corner_radius = 24, padding = 8 }

keyboard = { layouts = "us,ru", options = "grp:alt_shift_toggle" }

rules = {
  { app_id = "mpv", float = true },
  { app_id = "Alacritty", blur = { radius = 6, passes = 4 } },
}

keys = {
  ["L-Return"] = "terminal",
  ["L-h"] = "move -1 0",
  ["L-b"] = function() newm.launch("firefox") end,
}

startup = { "mako" }
```

- An action is a text (`"move -1 0"`, `"launch firefox"`, `"volume +5"`) or a function.
- Functions can call `layout.<method>(...)` (the calls of [the table](settings.md#everything-a-key-can-do), in snake_case: `layout.toggle_overview()`), `newm.launch("command")`, `newm.run("volume +5")` and `newm.notify("text")`.
- `sys.wifi()`, `sys.battery()`, `sys.time()` and the rest give the bar's texts.
- `rules` match by `app_id`, `title`, or by a pattern: `app_id_matches`, `title_matches`.

## JSON

Your folder's `examples/config.json` is the same as data. Bar texts are given by name:
`"texts": ["wifi", "battery", "time"]`.

## Nix

newm's flake has a NixOS module and a home-manager module.

**NixOS** (newm installed, in the login screen, and a configuration for everyone):

```nix
{
  inputs.newm.url = "github:AristarkhKenebas/newm";

  outputs = { nixpkgs, newm, ... }: {
    nixosConfigurations.laptop = nixpkgs.lib.nixosSystem {
      modules = [
        newm.nixosModules.default
        {
          programs.newm = {
            enable = true;
            package = myNewm;            # your newm package (see below)
            settings.view.corner_radius = 24;
          };
        }
      ];
    };
  };
}
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
        "L-h" = "move -1 0";
      };
      rules = [ { app_id = "mpv"; float = true; } ];
      panels.top_bar.enabled = false;
      panels.bar.cmd = "waybar";
      startup = [ "mako" ];
    };
  };
}
```

`settings` is written to `~/.config/newm/config.json`. If you would rather write Kotlin or
Lua in Nix, set `configKts` or `configLua` instead (only one of the three).

The flake does not build newm itself yet. `package` is a newm you build (for example from
`./install.sh`'s output) until a package is there.

## The old config.py

A `config.py` of the old newm still works: put it at `~/.config/newm/config.py`, with no
other configuration file next to it. newm runs it with Python as before, including your key
bindings, rules and `on_startup`.

You may need to change a line or two. Settings for pywm's own renderer
(`pywm.renderer_mode`, `texture_shaders`, and similar) are ignored, because newm no longer
uses pywm.

Moving to `config.kts` is worth it. New settings come there first, editors highlight it,
and its mistakes are clearer. The sections are the same; the names are in camelCase.
