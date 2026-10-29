# Lua, JSON, Nix и старый config.py

[English](../en/other-formats.md) · **Русский**

`config.kts` — родной формат newm, и в нём больше всего пояснений. Ту же конфигурацию можно
написать и на другом языке. newm читает **один** файл из `~/.config/newm/`, первый найденный:

1. `config.kts`
2. `config.lua`
3. `config.json` (его пишет Nix)
4. `config.py` (конфигурация старого newm на Python)

Чтобы перейти на другой формат, положите туда его файл и удалите (или переименуйте)
`config.kts`. Любой формат перечитывается при сохранении и показывает ошибки одинаково.
`config.json` рядом с `config.kts` не читается, и карточка говорит об этом.

Имена — как в [Все настройки](settings.md), в snake_case: `cornerRadius` — это
`corner_radius`, `topBar` — `top_bar`.

**Файл в любом формате — это вся конфигурация.** То, чего в нём нет, берёт встроенное значение
newm, а оно не всегда совпадает со стартовым `config.kts`: без таблицы `keys` клавиш нет
совсем, а без `top_bar = { enabled = true }` нет панели. Начинайте с `examples/` в вашей
папке: там есть и то, и другое.

## Lua

`examples/config.lua` в вашей папке — конфигурация на Lua:

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

- Действие — это текст (`"move -1 0"`, `"launch firefox"`, `"volume +5"`) или функция.
- В функциях доступны `layout.<метод>(...)` (вызовы из [таблицы](settings.md#всё-что-может-клавиша),
  в snake_case: `layout.toggle_overview()`), `newm.launch("команда")`, `newm.run("volume +5")`,
  `newm.terminal()` и `newm.notify("текст")`.
- `sys.wifi()`, `sys.battery()`, `sys.time("HH:mm")` и другие дают тексты для панели.
- `rules` сопоставляются по `app_id` или `title` точно, или по шаблону: `app_id_matches`,
  `title_matches`. Действует первое подходящее правило.

## JSON

`examples/config.json` в вашей папке — то же самое в виде данных. Разделы — это таблицы;
клавиши, правила и программы при запуске записываются как в Lua:

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

Тексты панели задаются по имени: `"texts": ["wifi", "battery", "time"]`. Строки, начинающиеся
с `//`, — пояснения.

## Имена и формы блоков

Каждый блок `config.kts` — таблица с тем же именем, настройки в ней — в snake_case. Где в
`config.kts` функция или блок внутри блока, в Lua и JSON пишется так (старые имена newm,
справа, тоже работают):

| `config.kts` | Lua / JSON | собственное имя newm |
|---|---|---|
| `gestures { enabled; scalePx }` | `gestures.enabled`, `gestures.scale_px` | `gestures.c.enabled`, `gestures.c.scale_px` |
| `gestures { dbusEnabled }` | `gestures.dbus_enabled` | `gestures.dbus.enabled` |
| `gestures { bind(GestureAction.SWIPE, null, "swipe-3") }` | `gestures.bind.swipe = [null, "swipe-3"]` (или `{ "modifier": "L", "kinds": ["move-1", "swipe-2"] }`) | `gesture_bindings.swipe` |
| `gestures { on("swipe-4", "down") { toggleOverview() } }` | `gestures.on = [ { "kind": "swipe-4", "direction": "down", "action": "toggle_overview" } ]` (ещё `modifier`, `description`) | |
| `tuning { swipe { gestureFactor = 4.0 } }` | `tuning.swipe.gesture_factor` | `swipe.gesture_factor` |
| `output("eDP-1") { x; y; refreshHz }` | `outputs = [ { "name": "eDP-1", "x": 0, "y": 0, "refresh_hz": 60 } ]` | `pos_x`, `pos_y`, `mHz` (миллигерцы) |
| `input { tablet { leftHanded = true } }` | `input.tablet.left_handed` | |
| `energy { onIdle { } }` | `energy.on_idle` (функция Lua с одним аргументом) | `energy.idle_callback` |
| `energy { after(900) { ... } }` | `energy.after = { [900] = "launch playerctl pause" }`; в JSON `"after": { "900": "..." }` | |
| `everyFrame { }`, `onStartup { }`, `onReconfigure { }` | `every_frame`, `on_startup`, `on_reconfigure` (функции Lua); `startup` (список команд) | `synchronous_update` |
| `launcher { }`, `lock { }` | `launcher`, `lock` (или внутри `panels`) | `panels.launcher`, `panels.lock` |
| `launcher { entries { "Lock" { ensureLocked() } } }` | `launcher.entries = { Lock = "ensure_locked" }` (или список `{ name, comment, action }`) | |
| `launcher { keys { "C-n" { next() } } }` | `launcher.keys = { ["C-n"] = "next" }`: `next`, `previous`, `first`, `last`, `start`, `run_typed`, `close` | |
| `panels { topBarProgram { cmd } }` | `panels.top_bar.cmd` (рядом с настройками собственной панели newm) | |
| `terminal = "foot"` | `terminal = "foot"` | |
| `val menu = "wofi"`, затем `menu` в клавише | `variables = { menu = "wofi --show drun" }`, затем `"$menu"` в любом тексте или клавише (`"L-d": "launch $menu"`); переменная может использовать предыдущие; прочие `$ИМЯ` (`$HOME`) остаются как есть | |
| `env("XCURSOR_SIZE", "24")` | `env = { XCURSOR_SIZE = "24" }` | |
| `permissions { rule("/usr/bin/grim", "screencopy", "allow"); default("screencopy", "ask") }` | `permissions = { rules = { { program = "/usr/bin/grim", feature = "screencopy", mode = "allow" } }, defaults = { screencopy = "ask" } }` | |
| `misc { swallow = true; vrr = true }` | `misc = { swallow = true, vrr = true }` (`swallow_apps`, `swallow_except_titles`, `focus_on_activate`, `wake_on_input`, `middle_click_paste`, `background_color`) | |

## Nix

У flake newm есть пакет, модуль NixOS и модуль home-manager. Как их подключить — в
[Установке](install/nixos.md); здесь — что они пишут.

**NixOS**: `programs.newm.settings` становится `/etc/newm/config.json`. Пользователь без
`~/.config/newm` при первом запуске получает его копию; дальше действует его копия.

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

**home-manager** (ваша собственная, полностью декларативная конфигурация):

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

`settings` записывается в `~/.config/newm/config.json`, и newm подхватывает его, как только
home-manager его запишет. Чтобы писать в Nix на Kotlin или Lua, задайте вместо этого
`configKts` или `configLua` (только что-то одно из трёх): `configKts = builtins.readFile ./config.kts;`
сохраняет все пояснения файла newm. Ваш собственный `config.kts` или `config.lua` в этой папке
читается раньше `config.json`; тогда newm сообщает на экране, что `config.json` не
используется.

## Старый config.py

`config.py` от старого newm по-прежнему работает: положите его в `~/.config/newm/config.py`,
без других файлов конфигурации рядом. newm запускает его через Python, как раньше, вместе с
вашими клавишами, правилами и `on_startup`.

Возможно, придётся поправить строку-другую. Настройки собственного рендерера pywm
(`pywm.renderer_mode`, `texture_shaders` и подобные) игнорируются, потому что newm больше не
использует pywm.

Перейти на `config.kts` стоит. Новые настройки появляются там первыми, редакторы его
подсвечивают, а ошибки в нём понятнее. Разделы те же; имена в camelCase.
