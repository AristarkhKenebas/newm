# Анимации

[English](../en/animations.md) · **Русский**

Анимации newm **по умолчанию выключены**: открылось окно, сдвинулся вид, обзор, меню — всякое
изменение происходит сразу. За пальцами вид по-прежнему следует точно: свайп или щипок двигают
вид вместе с ними; анимации — только то, что после того, как пальцы отпущены (доводка), и их
сглаживание.

Одна строка возвращает привычный вид newm:

```kotlin
animations { preset = "newm" }
```

Остальное на этой странице — для выбора: как в Hyprland, все анимации сразу или каждая по
имени, у каждой своё время, своя кривая и (у меню и карточек) свой вид.

## Как устроено

```kotlin
animations {
    enabled = true                           // все включены (то же, что preset = "newm"); false — все выключены
    millis = 300                             // общее время — для тех, у кого нет своего
    curve = "linear"                         // общая кривая
    curve("snappy", 0.05, 0.9, 0.1, 1.05)    // своя кривая Безье (числа как в Hyprland и CSS)
    spring("bouncy", stiffness = 300.0, damping = 14.0)

    animation("windows", millis = 250, curve = "snappy")    // и windowsIn, windowsOut, ...
    animation("popupsIn", style = "popin 90%")
    animation("osd", speed = 2.0)            // speed как в Hyprland: десятые доли секунды
    off("gestureSmoothing")                  // вид точно под пальцами
}
animations { on("overview") }               // только обзор, остальные выключены
```

- `preset`: `"newm"` (все привычные анимации newm) или `"none"` (по умолчанию); `"on"` и
  `"off"` — это `enabled = true` и `false`.
- `enabled`: все включены или выключены; важнее, чем `preset`. Включённая по имени (`on("camera")`)
  работает, даже когда остальные выключены; выключенная по имени остаётся выключенной.
- Настройка для имени действует и на всё, что под ним (`windows` — на `windowsIn`, `windowsOut`, ...),
  если ниже не сказано иначе. Чего никто не сказал — обычное значение анимации (таблица ниже),
  затем общие `millis` и `curve`.
- `millis`: время в миллисекундах, от 0 до 10000 (0 — сразу). `speed` — то же по-хайпрлендовски,
  в десятых долях секунды.
- `curve`: по имени — `"linear"` (собственная кривая newm: одна скорость всю дорогу), `"ease-out"`
  (быстро в начале, мягко в конце), `"ease-in-out"`, `"smooth"`, `"overshoot"` (чуть дальше конца
  и обратно) или своя. Пружина длится, пока не успокоится, если время не задано. Раскладка newm
  обрезает кривые на конце: «перелёт» и прыгучая пружина там не заходят дальше.
- `style`: как появляются и исчезают меню, карточки и подсказка по клавишам — `"fade"` (только
  прозрачность), `"popin 90%"` (вырастает из 90%), `"slide up 20"` (поднимается на 20 единиц на
  место), `"slide down 8"` (опускается). У остальных вид один.
- `animTime` (`anim_time` из config.py) по-прежнему общее время, если `millis` не задан;
  `blendTime`, `focus { animTime }`, `background { timeScale }` и `gestures { lpInertia }` —
  обычные времена `startup`/`quit`, `border`, `backgroundMove` и `gestureSmoothing`.

## Все анимации

| Имя | Под | Что анимирует | Обычное время, кривая |
|---|---|---|---|
| `windows` | | все анимации окон ниже | |
| `windowsIn` | windows | окно появляется: занимает своё место, остальные расступаются | общее (300 мс), linear |
| `windowsOut` | windows | окно закрывается: остальные занимают его место | общее, linear |
| `windowsMove` | windows | окно сдвинуто, изменён размер, стало плавающим или плиткой, рядом с другим, на другой рабочий стол, поглощено и обратно; `clean` | общее, linear |
| `fullscreen` | windows | окно на весь экран и обратно | общее, linear |
| `moveResize` | windows | окно, сдвинутое или растянутое мышью или пальцами, встаёт на сетку, когда его отпустили | общее, linear |
| `camera` | | вид движется: к следующему окну, к окну с фокусом, приближение и отдаление, место для панели, перетаскиваемое окно остаётся на виду | общее, linear |
| `overview` | | обзор открывается и закрывается, выбор окна в нём | общее, linear |
| `gestures` | | две ниже | |
| `gestureSnap` | gestures | после свайпа или щипка вид или окно доезжает туда, где остановится | по скорости пальцев (`grid { timeScale }`), linear |
| `gestureSmoothing` | gestures | движение пальцев сглажено: вид мягко идёт чуть позади; время — как скоро он догоняет | 75 мс (`gestures { lpInertia = 0.8 }` при 60 Гц) |
| `border` | | рамка фокуса переезжает к окну с фокусом (с `focus { animateOnChange }`; вместе с анимацией окна — так же, как оно) | `focus { animTime }`, 300 мс, linear |
| `lock` | | экран блокировки выезжает и уезжает после разблокировки — экран заблокирован раньше, чем он двинется | общее, linear |
| `launcher` | | меню запуска newm появляется и исчезает | общее, ease-out |
| `launcherList` | launcher | его первые строки появляются одна за другой | 220 мс, ease (Compose) |
| `background` | | две ниже | |
| `backgroundMove` | background | обои плавно едут за видом, мягко догоняя | `background { timeScale }`, 150 мс |
| `backgroundIn` | background | обои проявляются в первый раз | 600 мс, ease-out |
| `session` | | три ниже | |
| `startup` | session | newm плавно появляется после запуска | `blendTime`, 1 с, linear |
| `quit` | session | newm плавно гаснет при выходе | `blendTime`, 1 с, linear |
| `sessionRestore` | session | окна прошлой сессии возвращаются на свои места | 250 мс, 300 мс |
| `popups` | | окошки панели (календарь, быстрые настройки, меню трея, пароль Wi-Fi) | |
| `popupsIn` | popups | окошко открывается | 280 мс (пароль 200), ease-out |
| `popupsOut` | popups | окошко закрывается | 180 мс (пароль 140), smooth |
| `popupsResize` | popups | окошко растёт, когда меняется содержимое, части разворачиваются | пружина (≈ Compose 700, 0.9) или 260 мс |
| `notifications` | | карточки уведомлений | |
| `notificationsIn` | notifications | карточка появляется (и все вместе) | 260 мс (240), ease |
| `notificationsOut` | notifications | карточка уходит | 220 мс, ease |
| `osd` | | ползунок громкости и яркости, всплывающие надписи, карточка сети или устройства | |
| `osdIn` | osd | появляется | 140 мс (надпись 180, карточка 260), ease |
| `osdOut` | osd | исчезает | 220 мс (надпись 260, карточка 300), ease |
| `clipboard` / `clipboardIn` / `clipboardOut` | | история буфера обмена (Logo+V) | 260 / 180 мс |
| `keyHelp` / `keyHelpIn` / `keyHelpOut` | | подсказка по клавишам (Logo+/) | 260 / 180 мс |
| `widgets` | | мелкие движения, ниже | |
| `hover` | widgets | цвета под указателем, появление полосы прокрутки | 100–150 мс, ease |
| `toggles` | widgets | переключатели, ползунки, кнопки переходят в новое состояние | 160 мс, ease |
| `scroll` | widgets | список прокручивается к выбранному (меню запуска, буфер обмена) | своё у Compose |
| `spinner` | widgets | значок обновления крутится, пока ищет; стрелка поворачивается, когда часть разворачивается (выключено — стоит) | 900 мс на оборот, linear; 260 мс |
| `brightness` | | яркость экрана плавно меняется (`BacklightManager` из config.py) | `anim_time`, 300 мс |

«Ease» — обычная кривая Compose (Безье 0.4, 0, 0.2, 1). Анимации Caulis под newm — смена
масштаба монитора (`fadeScale`), уведомления самого Caulis — следуют `enabled` (и пресетам
`"on"` и `"none"`, это `enabled = true` и `false`); но не анимации окон самого Caulis: окна
newm двигает сам. Один `preset = "newm"` оставляет их как было (выключенными): точно прежний
вид newm. По имени они в `caulis.animations { }` (см. руководство Caulis).

## config.json и config.lua

Тот же раздел, имена анимаций как есть (camelCase, как в Hyprland — единственное место, где в
JSON и Lua не snake_case). Анимация — `true`/`false`, её вид или таблица:

```json
"animations": {
  "preset": "newm",
  "curves": { "snappy": { "bezier": [0.05, 0.9, 0.1, 1.05] }, "bouncy": { "spring": { "stiffness": 300, "damping": 14 } } },
  "windows": { "millis": 250, "curve": "snappy" },
  "popupsIn": "popin 90%",
  "osd": { "speed": 2 },
  "gestureSmoothing": false
}
```

```lua
animations = {
  enabled = true,
  curves = { snappy = { bezier = { 0.05, 0.9, 0.1, 1.05 } } },
  camera = { millis = 200, curve = "snappy" },
  lock = false,
}
```

Несуществующее имя, неизвестная кривая, вид, которого у анимации нет, или время вне пределов
называются при загрузке конфигурации (со списком того, что есть).

## Если вы из Hyprland

| Hyprland | newm |
|---|---|
| `animations { enabled = yes }` | `animations { enabled = true }` |
| `bezier = snappy, 0.05, 0.9, 0.1, 1.05` | `curve("snappy", 0.05, 0.9, 0.1, 1.05)` |
| `animation = windowsIn, 1, 3, snappy, popin 80%` | `animation("windowsIn", enabled = true, speed = 3.0, curve = "snappy")` |
| `animation = workspaces, 0` | `off("camera")` (переключать рабочие столы в newm не нужно: движется вид) |
| `windows`, `windowsIn`, `windowsOut`, `windowsMove`, `border`, `fade` | те же имена (затухания: `popups`, `notifications`, `osd`, ...) |
| `layers`, `layersIn`, `layersOut` | `popups`, `launcher`, `lock`, `osd` (собственные панели newm) |

Вид для окон в newm не выбирается (они едут по стене); `style` — для меню и карточек.
