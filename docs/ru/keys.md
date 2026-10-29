# Клавиши

[English](../en/keys.md) · **Русский**

## Первая: закрывать окно по Super+Backspace

Добавьте эту строку в `~/.config/newm/config.kts` (куда угодно или внутрь `keys { }`) и сохраните:

```kotlin
bind("Super+Backspace", "close-window")
```

Всё: newm применит это сразу. Logo+/ покажет её среди других клавиш, а `newm cmd keys`
перечислит все назначенные клавиши.

## Как записывать клавиши

Клавиша записывается так, как вы её называете: модификаторы, потом клавиша, через `+`.

```kotlin
bind("Super+Return", "terminal")
bind("Super+Shift+Q", "close-window")
bind("Ctrl+Alt+T", "launch", "foot")
bind("Print", "screenshot")
bind("Super", "overview")              // одна нажатая Super
bind("Super+A Super+B", "launch", "firefox")   // последовательность: Super+A, потом Super+B
```

- **Модификаторы** (регистр не важен): `Super` (а также `Logo`, `Win`, `Mod`, `Meta`), `Ctrl`
  (а также `Control`), `Alt`, `Shift`. Порядок не важен.
- **Клавиши** (регистр не важен): буква или цифра, `Return` (`Enter`), `Space`, `Tab`,
  `Escape` (`Esc`), `Backspace`, `Delete`, `Insert`, `Home`, `End`, `PageUp`, `PageDown`,
  `Left`, `Right`, `Up`, `Down`, `F1` … `F35`, `Print`, `Minus` (`-`), `Equal` (`=`), `Plus`
  (`Super++`), `Comma`, `Period`, `Slash`, `Backslash`, `Grave`, `Semicolon`, `Apostrophe`,
  `BracketLeft`, `BracketRight`, `Menu` и клавиши ноутбука: `VolumeUp`, `VolumeDown`, `Mute`,
  `MicMute`, `BrightnessUp`, `BrightnessDown`, `Play`, `NextSong`, `PreviousSong`. Любое имя
  xkb, начинающееся с `XF86` (`XF86Calculator`), тоже работает.
- Клавиши не зависят от раскладки: Super+Q — это Super+Q и на русской раскладке.
- Клавиши ноутбука (`XF86...`) работают и на экране блокировки.

Клавиша, которую newm не может прочитать, показывается на экране со своей строкой и с тем,
что могло иметься в виду: `config.kts:42: "Super+Bakspace": no key is called "Bakspace" (did you mean BackSpace?)`.

Прежняя запись newm работает как раньше: `"L-q"` — Super+Q, `"L-S-h"` — Super+Shift+H,
`"L-"` — одна нажатая Super, `"C-A-t"` — Ctrl+Alt+T.

## Действия

`bind(клавиша, действие, аргументы...)`; слова для подсказки по клавишам (Logo+/) берутся из
действия или ваши: `bind("Super+B", "launch", "firefox", description = "Firefox")`.

| Действие | Принимает | Что делает |
|---|---|---|
| `close-window` |  | закрыть окно |
| `terminal` |  | открыть терминал |
| `launch` | `command` | запустить программу (команду) |
| `launcher` |  | найти и запустить программу (лаунчер) |
| `focus-left` |  | окно слева |
| `focus-right` |  | окно справа |
| `focus-up` |  | окно выше |
| `focus-down` |  | окно ниже |
| `next-window` |  | следующее окно |
| `previous-window` |  | предыдущее окно |
| `go-to-window` | `number` | окно номер n (по порядку) |
| `next-in-stack` |  | следующее окно стопки |
| `overview` |  | все окна (обзор) |
| `zoom-out` |  | отдалить (видеть больше) |
| `zoom-in` |  | приблизить (видеть меньше) |
| `fit-window` |  | окно во весь экран (масштаб) |
| `side-by-side` |  | два окна рядом и обратно |
| `move-window-left` |  | сдвинуть окно влево |
| `move-window-right` |  | сдвинуть окно вправо |
| `move-window-up` |  | сдвинуть окно вверх |
| `move-window-down` |  | сдвинуть окно вниз |
| `window-narrower` |  | окно уже |
| `window-wider` |  | окно шире |
| `window-shorter` |  | окно ниже ростом |
| `window-taller` |  | окно выше ростом |
| `fullscreen` |  | полный экран и обратно |
| `float` |  | плавающее и обратно |
| `swallow` |  | спрятать окно в запустивший его терминал |
| `next-monitor` |  | следующий монитор |
| `previous-monitor` |  | предыдущий монитор |
| `window-to-next-monitor` |  | окно на следующий монитор |
| `window-to-previous-monitor` |  | окно на предыдущий монитор |
| `special-workspace` | `[name]` | особое рабочее пространство и обратно |
| `to-special-workspace` | `[name]` | окно в особое рабочее пространство |
| `from-special-workspace` |  | окно обратно на стену |
| `clipboard` |  | история буфера обмена |
| `screenshot` |  | снимок экрана: выбрать |
| `screenshot-screen` |  | снимок экрана целиком |
| `screenshot-window` |  | снимок окна |
| `key-help` |  | подсказка по клавишам |
| `lock` |  | заблокировать экран |
| `reload` |  | перечитать конфигурацию |
| `logout` |  | выйти |
| `caulis` | `action` | действие Caulis (volume +5, brightness -5, mic-mute) |

`unbind("Super+S")` убирает клавишу (из шаблона, если она вам не нужна); новое назначение
клавиши заменяет прежнее.

## Со своим кодом

Всё, что умеет newm, как раньше — клавиша, её слова для подсказки и Kotlin внутри `{ }`:

```kotlin
keys {
    "Super+Shift+Return"("Терминал в моих проектах") { launch("foot -D ~/projects") }
    "Super+Z" { basicScale(1); toggleOverview() }
}
```

Все функции: [Все настройки](settings.md#всё-что-может-клавиша).

## Готовые наборы

В стартовом `config.kts` клавиши newm записаны по одной в строке, а под ними — набор как в
Hyprland (Super+C или Super+Backspace закрывают окно, Super+R — лаунчер, Super+1 … Super+9 —
окна по порядку): уберите `//` перед нужными строками.

## config.json и config.lua

Те же клавиши и имена действий; действие и его аргументы одной строкой; `false` убирает клавишу:

```json
"keys": {
  "Super+Backspace": "close-window",
  "Super+Return": "launch foot",
  "Super+1": "go-to-window 1",
  "XF86AudioMute": "volume mute",
  "Super+S": false
}
```

```lua
keys = {
  ["Super+Backspace"] = "close-window",
  ["Super+Return"] = "launch foot",
}
```

Кроме этих имён, как раньше работают методы раскладки newm (`"move -1 0"`, `"toggle_overview"`)
и действия Caulis (`"volume +5"`, `"brightness -5"`).
