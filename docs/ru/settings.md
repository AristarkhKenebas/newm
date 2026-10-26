# Все настройки

[English](../en/settings.md) · **Русский**

В `config.kts`, который создаёт newm, перечислены все настройки с пояснениями. Эта страница —
краткий справочник. В Lua и JSON названия те же, но в snake_case (`cornerRadius` →
`corner_radius`).

## Разделы

| Раздел | Настройки |
|---|---|
| (верхний уровень) | `animTime`, `blendTime`, `cornerRadius` (углы экрана), `lockOnWakeup` |
| `background { }` | `path`, `anim`, `timeScale` |
| `view { }` | `cornerRadius`, `padding`, `fullscreenPadding`, `sendFullscreen`, `acceptFullscreen`, `floatingMinSize`, `borderWsSwitch`, `fillFirst`, `fade`, `unfocusedOpacity`, `rules { }` |
| `focus { }` | `enabled`, `color`, `distance`, `width`, `animTime`, `animateOnChange` |
| `panels { }` | `topBar { }`, `bottomBar { }`, `bar { }`, `lock { }`, `launcher { }`: см. ниже |
| `keyboard { }` | `layouts`, `variants`, `options`, `model`, `numLock` |
| `cursor { }` | `theme`, `size`, `hideAfter` |
| `input { }` | `tapToClick`, `naturalScroll`, `focusFollowsMouse`, `xwayland`, `titleBars` |
| `output("name") { }` | `scale`, `width`, `height`, `refreshHz`, `x`, `y`, `transform`, `anim` |
| `energy { }` | `idleTimes`, `screensOffAfter`, `suspendCommand`, `onIdle { }` |
| `gestures { }` | `bind(GestureAction..., modifier, kinds)`, `lpInertia`, `lpFreq`, `enabled`, `scalePx` |
| `tuning { }` | `swipe { }`, `swipeZoom { }`, `move { }`, `resize { }`, `moveResize { }`, `grid { }`: как ощущаются жесты |
| `keys { }` | `"combination" { action }` |
| события | `onStartup { }`, `onReconfigure { }`, `everyFrame { }` |

### panels

| Часть | Настройки |
|---|---|
| `topBar { }`, `bottomBar { }` | собственные панели newm: `enabled`, `height`, `fontSize`, `font`, `background`, `foreground`, `blur`, `texts { }` |
| `bar { }` | программа-панель: `cmd`, `cwd`, `visibleNormal`, `visibleFullscreen` |
| `lock { }` | экран блокировки: `w`, `h` (доли экрана), `cornerRadius`, `program` (сторонний локер вместо своего: `"swaylock"`, `"gtklock"`, `"hyprlock"`) |
| `launcher { }` | лаунчер: `w`, `h`, `cornerRadius`, `cmd` |

## Что умеют клавиши

Внутри `keys { }`:

| Вызов | Что делает |
|---|---|
| `move(di, dj)` | перейти к соседнему окну (`-1, 0` — влево, `0, 1` — вниз) |
| `moveFocusedView(di, dj)` | переместить активное окно на несколько плиток |
| `resizeFocusedView(di, dj)` | сделать его шире или выше на несколько плиток (отрицательные числа — меньше) |
| `basicScale(ds)` | масштаб: `1` — отдалить, `-1` — приблизить |
| `basicMove(di, dj)` | сдвинуть вид по стене |
| `toggleOverview(onlyActiveWorkspace = false)` | обзор |
| `toggleFullscreen()` | во весь экран и обратно |
| `toggleFocusedViewFloating()` | плавающее окно и обратно |
| `moveInStack(1)` | следующее окно в стопке |
| `cycleViews(1)`, `cycleViews(-1)`, `gotoView(index)` | окна по очереди или по номеру |
| `moveWorkspace(1)`, `changeFocusedViewWorkspace(1)` | рабочее пространство следующего монитора; отправить окно туда |
| `swallowFocusedView()` | окно занимает место того окна, из которого его запустили |
| `closeFocusedView()` | закрыть окно |
| `enterLauncherOverlay()` | лаунчер |
| `ensureLocked(dim = true)` | заблокировать |
| `updateConfig()` | перечитать конфигурацию |
| `terminate()` | выйти из сеанса |
| `launch("command")` | запустить программу |
| `command("name")` | одна из команд `newm cmd` |

В Lua и JSON действие — это текст. `"terminal"` открывает терминал, который нашёл newm (`$TERMINAL`, иначе первый установленный из alacritty, foot, kitty, ...). В остальных случаях это название вызова в snake_case и его аргументы,
`"move -1 0"`, `"toggle_overview only_active_workspace=true"`, `"launch firefox"`. Собственные
действия композитора тоже работают: `"volume +5"`, `"brightness -5"`, `"mic-mute"`, `"screenshot"`.

## newm cmd

Из терминала, скрипта или панели внутри newm:

```sh
newm cmd debug            # раскладка: каждое окно с его app id и местом, мониторы
newm cmd config           # все действующие настройки и их значения по умолчанию
newm cmd update-config    # перечитать конфигурацию
newm cmd lock
newm cmd open-launcher
newm cmd inhibit-idle     # без затемнения и блокировки, пока команда работает (Ctrl+C завершает её)
newm cmd clean            # навести порядок на стене
newm cmd restart          # перезапустить newm (так делает dev/newm-dev)
```

Другие команды `newm`: `newm help`, `newm version`, `newm install-session`.
