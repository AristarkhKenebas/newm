# Lua, JSON, Nix и старый config.py

[English](../en/other-formats.md) · **Русский**

`config.kts` — это собственный формат newm, и в нём больше всего пояснений. Но ту же
конфигурацию можно написать и на другом языке. newm читает **один** файл из `~/.config/newm/` —
первый, который найдёт:

1. `config.kts`
2. `config.lua`
3. `config.json` (его пишет Nix)
4. `config.py` (конфигурация старого newm на Python)

Поэтому, чтобы использовать другой формат, положите туда его файл и удалите (или переименуйте)
`config.kts`. Все форматы перечитываются при сохранении и показывают ошибки одинаково.

Названия те же, что в разделе [Все настройки](settings.md), но в snake_case: `cornerRadius` —
это `corner_radius`, `topBar` — это `top_bar`.

## Lua

В вашей папке файл `examples/config.lua` — это вся конфигурация по умолчанию на Lua:

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

- Действие — это текст (`"move -1 0"`, `"launch firefox"`, `"volume +5"`) или функция.
- Функции могут вызывать `layout.<method>(...)` (вызовы из [таблицы](settings.md#что-умеют-клавиши), в snake_case: `layout.toggle_overview()`), `newm.launch("command")`, `newm.run("volume +5")` и `newm.notify("text")`.
- `sys.wifi()`, `sys.battery()`, `sys.time()` и остальные дают тексты для панели.
- `rules` подбирают окна по `app_id`, `title` или по шаблону: `app_id_matches`, `title_matches`.

## JSON

В вашей папке файл `examples/config.json` — то же самое, только в виде данных. Тексты
панели задаются по имени: `"texts": ["wifi", "battery", "time"]`.

## Nix

У flake newm есть модуль NixOS и модуль home-manager.

**NixOS** (newm установлен, есть на экране входа, и конфигурация для всех пользователей):

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
            package = myNewm;            # ваш пакет newm (см. ниже)
            settings.view.corner_radius = 24;
          };
        }
      ];
    };
  };
}
```

**home-manager** (ваша личная конфигурация, полностью декларативная):

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

`settings` записывается в `~/.config/newm/config.json`. Если вам удобнее писать в Nix на
Kotlin или Lua, задайте вместо этого `configKts` или `configLua` (только что-то одно из трёх).

Сам newm этот flake пока не собирает. `package` — это newm, который вы собираете сами
(например, из результата `./install.sh`), пока готового пакета нет.

## Старый config.py

`config.py` от старого newm по-прежнему работает: положите его в `~/.config/newm/config.py`,
и чтобы рядом не было других файлов конфигурации. newm запускает его через Python, как
и раньше, вместе с вашими сочетаниями клавиш, правилами и `on_startup`.

Возможно, придётся поправить строчку-другую. Настройки собственного рендерера pywm
(`pywm.renderer_mode`, `texture_shaders` и подобные) игнорируются, потому что newm больше
не использует pywm.

Перейти на `config.kts` стоит. Новые настройки появляются там в первую очередь, редакторы
его подсвечивают, а ошибки в нём понятнее. Разделы те же, а названия — в camelCase.
