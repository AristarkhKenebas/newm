# Переход с Hyprland

[English](../en/hyprland.md) · **Русский**

Каждая настройка, ключевое слово и диспетчер Hyprland — и что им соответствует в newm:
настройка в `config.kts` (в Lua и JSON — те же имена в snake_case, см.
[другие форматы](other-formats.md)) или почему она не подходит. Стартовый `config.kts`
устроен как пример конфигурации Hyprland: ваши программы, мониторы, окружение, автозапуск,
внешний вид, анимации, ввод, жесты, раскладка окон, правила окон, рабочие столы, разрешения,
разное, клавиши.

Как читать последний столбец: **так же** — то же самое; **близко** — есть, немного иначе;
**не подходит** — не вписывается в newm, с причиной; **позже** — пока нет.

Главное отличие: в newm нет нумерованных рабочих столов. Каждый монитор показывает часть
бесконечной стены окон (отдалите вид, чтобы видеть больше), или — с `screens { }` — стену
целыми экранами по несколько окон, что ближе всего к тайлингу Hyprland.

## Сам файл (ключевые слова)

| Hyprland | newm | |
|---|---|---|
| `$terminal = foot`, `local terminal = "foot"` | `val terminal = "foot"` в начале `config.kts` (любое значение Kotlin), используется как `bind("Super+Return", "launch", terminal)`; Lua: `local terminal`; JSON и Lua: `variables = { terminal = "foot" }`, затем `"$terminal"` в любом тексте или клавише | так же |
| `$mainMod = SUPER` | `val mod = "Super"`, затем `bind("$mod+Return", ...)` (строковый шаблон Kotlin) | так же |
| `env = XCURSOR_SIZE,24`, `hl.env(...)` | `env("XCURSOR_SIZE", "24")`; Lua/JSON `env = { XCURSOR_SIZE = "24" }` — для каждой программы, которую запускает newm, и служб сеанса | так же |
| `exec-once`, `hl.on("hyprland.start")` | `autostart { app("waybar") }` (с задержками, перезапусками, `ifNotRunning`) или `onStartup { launch("...") }` | так же |
| `exec` (при каждой перезагрузке) | `onReconfigure { launch("...") }` | так же |
| `source = файл` | config.kts — один файл; Lua: `dofile`/`require`; или свой код на Kotlin | близко |
| `monitor = ...` | `output("DP-1") { ... }`, см. «Мониторы» | так же |
| `bind`, `unbind` | `bind("Super+Q", "close-window")`, `unbind("Super+Q")`, см. «Клавиши» | так же |
| `windowrule`, `layerrule` | `view { rules { } }`, `windowRule { }`, `layerRule { }`, см. «Правила окон» | близко |
| `workspace = ...` | см. «Рабочие столы» | не подходит: нет нумерованных рабочих столов |
| `permission = ...` | `permissions { rule(...) }`, см. «Разрешения» | так же |
| `gesture = 3, horizontal, workspace` | `gestures { }`, см. «Жесты» | близко |
| `device { name = ... }` | `input { device("имя") { ... } }` | так же |
| `plugin = ...` | Kotlin в `config.kts` и API Caulis | не подходит: плагины не загружаются |
| `hyprctl keyword`, `hyprctl dispatch` | сохраните файл (он перечитывается сразу); `newm cmd ...` | близко |
| `submap` | режимы клавиш Caulis (`caulis.mode("resize") { }`) | близко |

## Мониторы

| Hyprland | newm | |
|---|---|---|
| `monitor = имя, 2560x1440@144, 1920x0, 1.25` | `output("DP-1") { width = 2560; height = 1440; refreshHz = 144.0; x = 1920; y = 0; scale = 1.25 }` | так же |
| `preferred`, `highres`, `highrr`, позиция `auto` | не указывайте `width`/`height` и `x`/`y`: лучший режим, рядом друг с другом | близко |
| `transform` | `transform = 1` (0–7, как в Hyprland) | так же |
| `disable` | Caulis: `caulis.monitor("HDMI-A-1") { enabled = false }` | так же |
| `mirror` | меню экранов на панели (повторить или расширить) | близко |
| `bitdepth` | Caulis: `caulis.monitor("DP-1") { maxBpc = 10 }` | близко |
| `vrr` (для монитора) | Caulis: `caulis.monitor("DP-1") { adaptiveSync = true }`; для всех: `misc { vrr = true }` | так же |
| `cm`, `sdrbrightness`, `sdrsaturation`, HDR | — | позже: HDR пока нет |
| область `reserved` | панели сами резервируют место | не подходит |

## general

| Hyprland | newm | |
|---|---|---|
| `gaps_in`, `gaps_out` | `view { padding }` (вокруг каждого окна: между двумя — вдвое), `view { fullscreenPadding }`, `screens { gaps }` | близко |
| `float_gaps`, `gaps_workspaces` | — | не подходит: плавающие окна ставите вы; рабочих столов рядом нет |
| `border_size` | `focus { width; distance }` | так же |
| `col.active_border` (градиенты, угол) | `focus { color = "rgba(33ccffee) rgba(00ff99ee) 45deg" }` или свой шейдер: `focus { shader }` | так же |
| `col.inactive_border` | — | не подходит: newm рисует одну линию — вокруг окна в фокусе |
| `col.nogroup_border*` | — | не подходит: групп нет |
| `resize_on_border`, `extend_border_grab_area`, `resize_corner` | `resize { onBorder; borderGrabSize; quarters }` | так же |
| `hover_icon_on_border` | — | позже |
| `layout` (dwindle, master, scrolling, monocle) | `screens { layout = "dwindle" }` ("columns", "rows", "grid", "master", "dwindle", "monocle"); `screens { enabled = false }`: свободная стена newm, близкая к scrolling | так же |
| `allow_tearing` | — | позже: в Caulis пока нет tearing |
| `locale` | `language = "ru"` | близко |
| `no_focus_fallback`, `modal_parent_blocking` | `windows { returnFocusToParent; childPlacement }` | близко |
| `snap { }` | окна встают на сетку плиток newm (`tuning { grid { } }`) | близко |

## decoration

| Hyprland | newm | |
|---|---|---|
| `rounding` | `view { cornerRadius }`; углы самого экрана: `cornerRadius` | так же |
| `rounding_power` | — | не подходит: углы круглые |
| `active_opacity` | `view { rules { rule(opacity = 0.97) } }` для отдельных окон | близко |
| `inactive_opacity` | `view { unfocusedOpacity = 0.92 }` | так же |
| `fullscreen_opacity` | — | не подходит |
| `dim_inactive`, `dim_strength` | — | позже |
| `dim_special` | `specialWorkspace { dim = 0.4 }` | так же |
| `dim_around` | `layerRule { dimAround = 0.4f }` (лаунчеры, меню) | так же |
| `dim_modal` | — | не подходит |
| `border_part_of_window` | `focus { distance }` | близко |
| `screen_shader` | Caulis: `caulis.screenShader = dev.caulis.core.ScreenShader.GRAYSCALE` (или свой GLSL) | так же |
| `blur { enabled; size; passes }` | для окна: `rule(blur = Blur(radius = 6, passes = 4))`; панель: `topBar { blur }`; особые рабочие столы: `specialWorkspace { blurRadius }`; панели: `layerRule { blur = true }` | близко |
| `blur { noise; contrast; brightness; vibrancy; xray; popups; input_methods; special; ignore_opacity; new_optimizations }` | — | позже (в Caulis есть тон размытия Hyprland; вид newm его пока не использует) |
| варианты размытия (acrylic, glass, ...) | — | позже |
| `shadow { }`, `glow { }` | — | позже: у окон в newm пока нет теней |
| `motion_blur`, `wobble` | — | позже |

## animations

Анимации newm названы как в Hyprland, с кривыми и пружинами Hyprland; см.
[Анимации](animations.md).

| Hyprland | newm | |
|---|---|---|
| `animations { enabled }` | `animations { enabled }` (или `preset = "newm"` / `"none"`) | так же |
| `bezier = имя, x1, y1, x2, y2`, `hl.curve(... "bezier")` | `animations { curve("имя", x1, y1, x2, y2) }` | так же |
| пружины (`hl.curve(... "spring")`) | `animations { spring("имя", stiffness, damping, mass) }` | так же |
| `animation = имя, on, speed, curve, style` | `animations { animation("windowsIn", enabled, millis, curve, style, speed) }` (`speed` в единицах Hyprland) | так же |
| `global` | `animations { millis; curve }` | так же |
| `windows`, `windowsIn`, `windowsOut`, `windowsMove` | те же имена | так же |
| `fade`, `fadeIn`, `fadeOut` | `view { fade }` вместе с `windowsIn`/`windowsOut` | близко |
| `border`, `borderangle` | `border`; `focus { animate }` для вращения шейдера | так же |
| `layers`, `layersIn`, `layersOut`, `fadeLayers*` | свои у newm: `popups`, `launcher`, `notifications`, `osd`, `clipboard`, `keyHelp` (у каждой `...In`/`...Out`, стили `fade`, `popin 90%`, `slide`) | близко |
| `workspaces`, `workspacesIn`/`Out` | `camera` (движение вида), `overview` | близко |
| `specialWorkspace` | `specialWorkspace { animation }` | так же |
| `zoomFactor` | `camera` | близко |
| `workspace_wraparound` | — | не подходит |

## input

| Hyprland | newm | |
|---|---|---|
| `kb_layout`, `kb_variant`, `kb_model`, `kb_options` | `keyboard { layouts; variants; model; options }` | так же |
| `kb_rules`, `kb_file` | — | не подходит: правила evdev; файл раскладки — позже |
| `numlock_by_default` | `keyboard { numLock }` | так же |
| `repeat_rate`, `repeat_delay` | `keyboard { repeatRate; repeatDelay }` | так же |
| `resolve_binds_by_sym` | `keyboard { bindingModifiers }` | близко |
| `sensitivity` | `input { pointerSpeed }` (от -1.0 до 1.0) | так же |
| `accel_profile`, `force_no_accel` | `input { accelProfile = "flat" }` | так же |
| `left_handed`, `natural_scroll`, `scroll_factor`, `scroll_method`, `scroll_button` | `input { leftHanded; naturalScroll; scrollFactor; scrollMethod; scrollButton }` | так же |
| `scroll_button_lock`, `scroll_points`, `emulate_discrete_scroll` | — | позже |
| `follow_mouse` | `input { focusFollowsMouse }` | близко (вкл. или выкл.) |
| `follow_mouse_threshold`, `mouse_refocus`, `float_switch_override_focus`, `special_fallthrough`, `off_window_axis_events`, `focus_on_close` | — | не подходит: фокус newm движется вместе со стеной |
| `rotation` | — | позже |
| `touchpad { disable_while_typing; natural_scroll; scroll_factor; tap-to-click; tap_button_map; clickfinger_behavior; middle_button_emulation; tap-and-drag; drag_lock }` | `input { touchpad { disableWhileTyping; naturalScroll; scrollFactor; …; tapButtons = "lrm"; clickFinger; tapAndDrag; dragLock } }`, `input { tapToClick; middleButtonEmulation }` | так же |
| `touchpad { flip_x; flip_y; drag_3fg }` | — | позже |
| `touchdevice { output; transform; enabled }` | Caulis: `caulis.touchscreenMonitor = "eDP-1"` | близко |
| `tablet { output; region_*; active_area_*; relative_input; left_handed }` | `input { tablet { monitor; area(...); leftHanded; pointerOnly; tipPressure; ... } }` | так же |
| `tablettool { pressure_range_* }` | `tablet { tipPressure }` | близко |
| `virtualkeyboard { }` | — | не подходит: протокола виртуальной клавиатуры пока нет |
| `device { name; sensitivity; ... }` | `input { device("имя") { pointerSpeed; accelProfile; naturalScroll; leftHanded; enabled; ... } }` | так же |

## cursor

| Hyprland | newm | |
|---|---|---|
| `XCURSOR_THEME`, `XCURSOR_SIZE` | `cursor { theme; size }` (программам тоже сообщается) | так же |
| `inactive_timeout` | `cursor { hideAfter }` | так же |
| `hide_on_key_press` | Caulis: `caulis.hideCursorWhileTyping = true` | так же |
| `no_hardware_cursors` | Caulis: `caulis.hardwareCursor = false` | так же |
| `enable_hyprcursor`, `sync_gsettings_theme` | тема передаётся программам | близко |
| `no_warps`, `warp_on_*`, `persistent_warps`, `default_monitor` | — | не подходит: newm двигает вид, а не указатель |
| `zoom_factor`, `zoom_rigid`, ... | масштаб newm — масштаб стены: `zoom-in`, `zoom-out` | близко |
| `hide_on_touch`, `hide_on_tablet`, `min_refresh_rate`, `hotspot_padding`, `use_cpu_buffer` | — | позже |

## gestures

| Hyprland | newm | |
|---|---|---|
| `gesture = 3, horizontal, workspace` | три пальца двигают по стене; `gestures { bind(GestureAction.SWIPE, null, "swipe-3") }` | близко |
| другие действия жестов (`move`, `resize`, `close`, `fullscreen`, `float`, `dispatcher`) | `gestures { bind(GestureAction.MOVE_RESIZE, "L", "move-1", "swipe-2") }`, свои жесты: `on("swipe-4", "down", "…") { toggleOverview() }` | так же |
| `workspace_swipe_distance`, `_invert`, `_min_speed_to_force`, `_cancel_ratio`, `_forever`, `_direction_lock` | `gestures { scalePx }`, `tuning { swipe { } }` | близко |
| `workspace_swipe_touch` | — | позже |

## group (группы с вкладками)

| Hyprland | newm | |
|---|---|---|
| группы, `groupbar { }`, `group.col.*`, `auto_group`, `drag_into_group`, … | окна на одних плитках образуют стопку (`next-in-stack`); `screens { layout = "monocle" }` | позже: группы с полосой вкладок — большая работа |

## misc

| Hyprland | newm | |
|---|---|---|
| `disable_hyprland_logo`, `disable_splash_rendering`, `force_default_wallpaper`, `splash_*`, `col.splash` | `background { path }` (иначе свои обои newm) | не подходит |
| `vrr` | `misc { vrr = true }` | так же (новое) |
| `mouse_move_enables_dpms`, `key_press_enables_dpms` | `misc { wakeOnInput }` | так же (новое) |
| `always_follow_on_dnd` | — | не подходит: нет рабочих столов, за которыми следовать |
| `layers_hog_keyboard_focus` | панели, просящие клавиатуру, получают её | так же |
| `animate_manual_resizes`, `animate_mouse_windowdragging` | `animations { on("moveResize") }` | так же |
| `disable_autoreload` | — | не подходит: сохранение применяет; при ошибке остаются последние верные настройки |
| `enable_swallow`, `swallow_regex`, `swallow_exception_regex` | `misc { swallow = true; swallowApps; swallowExceptTitles }` | так же (новое) |
| `focus_on_activate` | `misc { focusOnActivate }` | так же (новое) |
| `mouse_move_focuses_monitor` | `input { focusFollowsMouse }` | близко |
| `allow_session_lock_restore`, `lockdead_screen_delay` | `compositor { lockFailure; lockShowMillis }`: упавший блокировщик оставляет блокировку самого Caulis, пока блокировщик не разблокирует | близко |
| `session_lock_blur`, `session_lock_xray` | свой экран блокировки newm: `lock { }` | близко |
| `background_color` | `misc { backgroundColor }` | так же (новое) |
| `close_special_on_empty` | `specialWorkspace { hideWhenEmpty }` | так же |
| `new_window_takes_over_fullscreen`, `exit_window_retains_fullscreen`, `on_focus_under_fullscreen` | `view { stickyFullscreen }`; новое окно открывается рядом, вид переходит к нему | близко |
| `initial_workspace_tracking`, `initial_workspace_token_timeout` | окна открываются там, где вы (`windows { newPlacement }`) | не подходит |
| `middle_click_paste` | `misc { middleClickPaste }` | так же (новое) |
| `render_unfocused_fps` | — | не подходит: каждое показанное окно получает свои кадры |
| `enable_anr_dialog`, `anr_missed_pings` | — | позже |
| `bell_sound`, `font_family` | `panels { topBar { font } }`; `startupSound { }` | близко |
| `size_limits_tiled`, `new_float_force_onscreen`, `float_force_onscreen` | `view { floatingMinSize }`; плавающие окна открываются на экране | близко |
| `name_vk_after_proc`, `disable_watchdog_warning`, `disable_xdg_env_checks`, `disable_scale_notification`, `disable_hyprland_guiutils_check`, `screencopy_force_8b` | — | не подходит: свои у Hyprland |

## binds

| Hyprland | newm | |
|---|---|---|
| `bind = SUPER, Q, killactive` | `bind("Super+Q", "close-window")` (клавиши как вы их называете; [Клавиши](keys.md)) | так же |
| `bindl` (при блокировке) | клавиши ноутбука работают на экране блокировки (действия `caulis`: громкость, яркость) | близко |
| `bindr` (отпускание), `bindo` (долгое нажатие) | `"Super"` отдельно: Super нажата и отпущена | близко |
| `binde` (повтор) | — | позже |
| `bindm` (мышь) | `mouse { superMove; moveButton; resizeButton }` | так же |
| `bindd` (описание) | `bind(..., description = "...")`, показывается по Super+/ | так же |
| `bindn`, `bindt`, `bindi`, `bindp`, `bindc`, `bindg` | — | позже |
| `workspace_back_and_forth`, `allow_workspace_cycles`, `workspace_center_on`, `hide_special_on_workspace_change` | — | не подходит: нет нумерованных рабочих столов |
| `pass_mouse_when_bound`, `scroll_event_delay`, `drag_threshold`, `focus_preferred_method`, `movefocus_cycles_*`, `window_direction_monitor_fallback`, `allow_pin_fullscreen`, `disable_keybind_grabbing`, `ignore_group_lock` | — | не подходит или позже |

## Диспетчеры (что делает клавиша)

| Hyprland | действие newm |
|---|---|
| `exec` | `"launch", "cmd"`, `"terminal"` |
| `killactive`, `window.close` | `"close-window"` |
| `exit` | `"logout"` |
| `movefocus l/r/u/d` | `"focus-left"`, `"focus-right"`, `"focus-up"`, `"focus-down"` |
| `movewindow l/r/u/d`, `swapwindow` | `"move-window-left"` …, `"swap-on-screen"` |
| `resizeactive` | `"window-narrower"`, `"window-wider"`, `"window-shorter"`, `"window-taller"` |
| `togglefloating` | `"float"` |
| `fullscreen` | `"fullscreen"`, `"fit-window"` |
| `workspace n` | `"go-to-window", "n"` (n-е окно), `"next-screen"`, `"previous-screen"` |
| `movetoworkspace n` | `"window-to-next-screen"`, `"window-to-previous-screen"` |
| `togglespecialworkspace`, `movetoworkspace special` | `"special-workspace"`, `"to-special-workspace"`, `"from-special-workspace"` |
| `focusmonitor`, `movewindow mon:` | `"next-monitor"`, `"window-to-next-monitor"` |
| `cyclenext` | `"next-window"`, `"previous-window"` |
| `layoutmsg` (`togglesplit`, `swapwithmaster`, ...) | `"next-layout"`, `"promote-to-master"` |
| `toggle_swallow` | `"swallow"` |
| `reload` | `"reload"` |
| `dpms`, `exec hyprlock` | `"lock"`, `energy { }` |
| `pin`, `pseudo`, `centerwindow`, `tag`, `alterzorder`, `setprop`, `signal`, диспетчеры групп, `sendshortcut`, `pass`, `global` | позже |
| — (свои у newm) | `"overview"`, `"zoom-in"`, `"zoom-out"`, `"side-by-side"`, `"launcher"`, `"clipboard"`, `"screenshot"`, `"key-help"` |

## Правила окон

`view { rules { } }` newm решает, куда окно попадает и как выглядит; `windowRule { }` Caulis —
как оно себя ведёт.

| Hyprland | newm | |
|---|---|---|
| `match:class`, `match:title`, `initial_class`, `initial_title`, `xwayland`, `float`, `fullscreen`, `pin`, `focus` | `view.appId`, `view.title` в `rules { view -> }`; `windowRule { appId(...); title(...); initialAppId(...); isXwayland(); isFloating(); isFullscreen(); isFocused() }` | так же |
| `float`, `size`, `move`, `center` | `rule(float = true, floatSize = 900 to 600, floatPos = 0.1 to 0.1)` | так же |
| `opacity` | `rule(opacity = 0.97)` | так же |
| `no_blur` / размытие | `rule(blur = Blur(...))` | так же |
| `border_size`, `border_color`, `no_border` | `rule(focus = false)` (без линии фокуса) | близко |
| `idle_inhibit` | `windowRule { keepAwake = true }` | так же |
| `suppress_event` | `windowRule { ignore(WindowRequest.MAXIMIZE) }` | так же |
| `no_screen_share` | `windowRule { hiddenFromCapture = true }` | так же |
| `no_initial_focus`, `no_focus` | `windowRule { takesFocus = false }` | так же |
| `min_size`, `max_size` | `windowRule { minSize(300, 400); maxSize(...) }` | так же |
| `tag` | `windowRule { tags("video") }` + `hasTag("video")` | так же |
| `workspace`, `monitor` | — | не подходит: окна открываются там, где вы |
| `maximize`, `fullscreen`, `pin`, `pseudo`, `group`, `rounding`, `no_shadow`, `no_dim`, `no_anim`, `animation`, `dim_around`, `stay_focused`, `keep_aspect_ratio`, `render_unfocused`, `immediate`, `no_vrr`, `xray`, … | — | позже |

Правила слоёв: `layerRule { namespace("fuzzel"); blur = true; dimAround = 0.4f; opacity; cornerRadius; shadow; hiddenFromCapture }` (`blur`, `dim_around`, `no_screen_share` Hyprland; `no_anim`, `order`, `above_lock` — позже).

## Рабочие столы и их правила

| Hyprland | newm | |
|---|---|---|
| нумерованные рабочие столы, `workspace = n, monitor:...`, `persistent`, `default_name`, `default` | у каждого монитора своя стена; `screens { }` делит её на экраны | не подходит |
| `gaps_in`, `gaps_out`, `border_size`, `rounding`, `decorate`, `no_border`, `no_shadow` для рабочего стола | — | не подходит |
| `layout` для рабочего стола | `screens { layout }`, `"next-layout"` для экрана | близко |
| особые рабочие столы (`special:имя`) | `specialWorkspace { }` и `"special-workspace", "имя"` | так же |
| «умные зазоры» (`w[tv1]`, `f[1]`) | `view { fillFirst }`, `screens { gaps }` | близко |

## Разрешения (ecosystem)

| Hyprland | newm | |
|---|---|---|
| `permission = binary, screencopy, allow/ask/deny` | `permissions { rule("/usr/bin/grim", "screencopy", "allow") }` (регулярное выражение для пути или имя программы); `"ask"` показывает карточку: Разрешить / Разрешить до выхода из newm / Запретить | так же (новое) |
| `enforce_permissions` | `permissions { default("screencopy", "ask") }` (любая возможность) | так же (новое) |
| `keyboard` (новые клавиатуры по имени) | `input { device("Rubber Ducky*") { enabled = false } }` | близко |
| `plugin` | — | не подходит: плагинов нет |
| `cursorpos`, `input-capture` | — | не подходит: таких протоколов в Caulis нет |
| — (свои у newm) | `window-list`, `clipboard`, `panels`, `lock`, `monitors`, `gamma` | больше |
| `no_update_news`, `no_donation_nag` | — | не подходит |

## xwayland, render, opengl, quirks, debug

| Hyprland | newm | |
|---|---|---|
| `xwayland { enabled }` | `input { xwayland }` | так же |
| `xwayland { force_zero_scaling; use_nearest_neighbor }` | Caulis: `caulis.sharpJavaScaling`; программы X11 масштабирует Caulis | близко |
| `render { direct_scanout }` | Caulis: `caulis.directScanout` | так же |
| `render { cm_*, use_fp16, ctm_animation, … }`, `opengl { }`, `quirks { }` | — | позже (управление цветом, HDR) |
| `debug { overlay; damage_blink; disable_logs; ... }` | Caulis: `caulis.stats = true`, `logLevel`, журнал newm (`~/.local/state/newm/newm.log`) | близко |
| `experimental { }` | — | не подходит |
