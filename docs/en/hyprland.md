# Coming from Hyprland

**English** · [Русский](../ru/hyprland.md)

Every Hyprland setting, keyword and dispatcher, and what it is in newm: the setting in
`config.kts` (Lua and JSON: the same names in snake_case, see
[other formats](other-formats.md)), or why it does not apply. The starting `config.kts` is
laid out as Hyprland's example config: your programs, monitors, environment, autostart, look,
animations, input, gestures, layout, window rules, workspaces, permissions, misc, keys.

How to read the last column: **same** — the same thing; **close** — it is there, a little
different; **n/a** — does not fit newm, with the reason; **future** — not there yet.

The biggest difference: newm has no numbered workspaces. Each monitor shows a part of an
endless wall of windows (zoom out to see more of it), or — with `screens { }` — the wall in
whole screens of a few windows each, which is the closest to Hyprland's tiling.

## The file itself (keywords)

| Hyprland | newm | |
|---|---|---|
| `$terminal = foot`, `local terminal = "foot"` | `val terminal = "foot"` at the top of `config.kts` (any Kotlin value), used as `bind("Super+Return", "launch", terminal)`; Lua: `local terminal`; JSON and Lua: `variables = { terminal = "foot" }`, then `"$terminal"` in any text or key | same |
| `$mainMod = SUPER` | `val mod = "Super"`, then `bind("$mod+Return", ...)` (a Kotlin string template) | same |
| `env = XCURSOR_SIZE,24`, `hl.env(...)` | `env("XCURSOR_SIZE", "24")`; Lua/JSON `env = { XCURSOR_SIZE = "24" }` — for every program newm starts and the session's services | same |
| `exec-once`, `hl.on("hyprland.start")` | `autostart { app("waybar") }` (with delays, restarts, `ifNotRunning`), or `onStartup { launch("...") }` | same |
| `exec` (every reload) | `onReconfigure { launch("...") }` | same |
| `source = file` | config.kts is one file; Lua: `dofile`/`require`; or code of yours in Kotlin | close |
| `monitor = ...` | `output("DP-1") { ... }`, see Monitors | same |
| `bind`, `unbind` | `bind("Super+Q", "close-window")`, `unbind("Super+Q")`, see Keys | same |
| `windowrule`, `layerrule` | `view { rules { } }`, `windowRule { }`, `layerRule { }`, see Window rules | close |
| `workspace = ...` | see Workspaces | n/a: no numbered workspaces |
| `permission = ...` | `permissions { rule(...) }`, see Permissions | same |
| `gesture = 3, horizontal, workspace` | `gestures { }`, see Gestures | close |
| `device { name = ... }` | `input { device("name") { ... } }` | same |
| `plugin = ...` | Kotlin in `config.kts` and Caulis' API | n/a: no plugins to load |
| `hyprctl keyword`, `hyprctl dispatch` | save the file (it is read again at once); `newm cmd ...` | close |
| `submap` | Caulis' key modes (`caulis.mode("resize") { }`) | close |

## Monitors

| Hyprland | newm | |
|---|---|---|
| `monitor = name, 2560x1440@144, 1920x0, 1.25` | `output("DP-1") { width = 2560; height = 1440; refreshHz = 144.0; x = 1920; y = 0; scale = 1.25 }` | same |
| `preferred`, `highres`, `highrr`, `auto` position | leave `width`/`height` and `x`/`y` out: the best mode, side by side | close |
| `transform` | `transform = 1` (0–7, as Hyprland's) | same |
| `disable` | Caulis: `caulis.monitor("HDMI-A-1") { enabled = false }` | same |
| `mirror` | the bar's display menu (mirror or extend) | close |
| `bitdepth` | Caulis: `caulis.monitor("DP-1") { maxBpc = 10 }` | close |
| `vrr` (per monitor) | Caulis: `caulis.monitor("DP-1") { adaptiveSync = true }`; all: `misc { vrr = true }` | same |
| `cm`, `sdrbrightness`, `sdrsaturation`, HDR | — | future: no HDR yet |
| `reserved` area | bars reserve their own room | n/a |

## general

| Hyprland | newm | |
|---|---|---|
| `gaps_in`, `gaps_out` | `view { padding }` (around each window: between two it is twice), `view { fullscreenPadding }`, `screens { gaps }` | close |
| `float_gaps`, `gaps_workspaces` | — | n/a: floating windows are placed by you; no workspaces side by side |
| `border_size` | `focus { width; distance }` | same |
| `col.active_border` (gradients, angle) | `focus { color = "rgba(33ccffee) rgba(00ff99ee) 45deg" }`, or a shader of yours: `focus { shader }` | same |
| `col.inactive_border` | — | n/a: newm draws one line, around the focused window |
| `col.nogroup_border*` | — | n/a: no groups |
| `resize_on_border`, `extend_border_grab_area`, `resize_corner` | `resize { onBorder; borderGrabSize; quarters }` | same |
| `hover_icon_on_border` | — | future |
| `layout` (dwindle, master, scrolling, monocle) | `screens { layout = "dwindle" }` ("columns", "rows", "grid", "master", "dwindle", "monocle"); `screens { enabled = false }`: newm's free wall, close to scrolling | same |
| `allow_tearing` | — | future: Caulis has no tearing page flips yet |
| `locale` | `language = "ru"` | close |
| `no_focus_fallback`, `modal_parent_blocking` | `windows { returnFocusToParent; childPlacement }` | close |
| `snap { }` | windows settle on newm's grid of tiles (`tuning { grid { } }`) | close |

## decoration

| Hyprland | newm | |
|---|---|---|
| `rounding` | `view { cornerRadius }`; the screen's own corners: `cornerRadius` | same |
| `rounding_power` | — | n/a: round corners |
| `active_opacity` | `view { rules { rule(opacity = 0.97) } }` for some windows | close |
| `inactive_opacity` | `view { unfocusedOpacity = 0.92 }` | same |
| `fullscreen_opacity` | — | n/a |
| `dim_inactive`, `dim_strength` | — | future |
| `dim_special` | `specialWorkspace { dim = 0.4 }` | same |
| `dim_around` | `layerRule { dimAround = 0.4f }` (launchers, menus) | same |
| `dim_modal` | — | n/a |
| `border_part_of_window` | `focus { distance }` | close |
| `screen_shader` | Caulis: `caulis.screenShader = dev.caulis.core.ScreenShader.GRAYSCALE` (or your own GLSL) | same |
| `blur { enabled; size; passes }` | per window: `rule(blur = Blur(radius = 6, passes = 4))`; the bar: `topBar { blur }`; special workspaces: `specialWorkspace { blurRadius }`; panels: `layerRule { blur = true }` | close |
| `blur { noise; contrast; brightness; vibrancy; xray; popups; input_methods; special; ignore_opacity; new_optimizations }` | — | future (Caulis has Hyprland's blur tone; newm's look does not use it yet) |
| `blur` variants (acrylic, glass, ...) | — | future |
| `shadow { }`, `glow { }` | — | future: newm's look has no window shadows yet |
| `motion_blur`, `wobble` | — | future |

## animations

newm's animations are named as Hyprland's, with Hyprland's curves and springs; see
[Animations](animations.md).

| Hyprland | newm | |
|---|---|---|
| `animations { enabled }` | `animations { enabled }` (or `preset = "newm"` / `"none"`) | same |
| `bezier = name, x1, y1, x2, y2`, `hl.curve(... "bezier")` | `animations { curve("name", x1, y1, x2, y2) }` | same |
| springs (`hl.curve(... "spring")`) | `animations { spring("name", stiffness, damping, mass) }` | same |
| `animation = name, on, speed, curve, style` | `animations { animation("windowsIn", enabled, millis, curve, style, speed) }` (`speed` in Hyprland's units) | same |
| `global` | `animations { millis; curve }` | same |
| `windows`, `windowsIn`, `windowsOut`, `windowsMove` | the same names | same |
| `fade`, `fadeIn`, `fadeOut` | `view { fade }` with `windowsIn`/`windowsOut` | close |
| `border`, `borderangle` | `border`; `focus { animate }` for a shader's turning | same |
| `layers`, `layersIn`, `layersOut`, `fadeLayers*` | newm's own: `popups`, `launcher`, `notifications`, `osd`, `clipboard`, `keyHelp` (each `...In`/`...Out`, styles `fade`, `popin 90%`, `slide`) | close |
| `workspaces`, `workspacesIn`/`Out` | `camera` (the view moving), `overview` | close |
| `specialWorkspace` | `specialWorkspace { animation }` | same |
| `zoomFactor` | `camera` | close |
| `workspace_wraparound` | — | n/a |

## input

| Hyprland | newm | |
|---|---|---|
| `kb_layout`, `kb_variant`, `kb_model`, `kb_options` | `keyboard { layouts; variants; model; options }` | same |
| `kb_rules`, `kb_file` | — | n/a: evdev rules; a keymap file: future |
| `numlock_by_default` | `keyboard { numLock }` | same |
| `repeat_rate`, `repeat_delay` | `keyboard { repeatRate; repeatDelay }` | same |
| `resolve_binds_by_sym` | `keyboard { bindingModifiers }` | close |
| `sensitivity` | `input { pointerSpeed }` (-1.0 to 1.0) | same |
| `accel_profile`, `force_no_accel` | `input { accelProfile = "flat" }` | same |
| `left_handed`, `natural_scroll`, `scroll_factor`, `scroll_method`, `scroll_button` | `input { leftHanded; naturalScroll; scrollFactor; scrollMethod; scrollButton }` | same |
| `scroll_button_lock`, `scroll_points`, `emulate_discrete_scroll` | — | future |
| `follow_mouse` | `input { focusFollowsMouse }` | close (on or off) |
| `follow_mouse_threshold`, `mouse_refocus`, `float_switch_override_focus`, `special_fallthrough`, `off_window_axis_events`, `focus_on_close` | — | n/a: newm's focus moves with the wall |
| `rotation` | — | future |
| `touchpad { disable_while_typing; natural_scroll; scroll_factor; tap-to-click; tap_button_map; clickfinger_behavior; middle_button_emulation; tap-and-drag; drag_lock }` | `input { touchpad { disableWhileTyping; naturalScroll; scrollFactor; …; tapButtons = "lrm"; clickFinger; tapAndDrag; dragLock } }`, `input { tapToClick; middleButtonEmulation }` | same |
| `touchpad { flip_x; flip_y; drag_3fg }` | — | future |
| `touchdevice { output; transform; enabled }` | Caulis: `caulis.touchscreenMonitor = "eDP-1"` | close |
| `tablet { output; region_*; active_area_*; relative_input; left_handed }` | `input { tablet { monitor; area(...); leftHanded; pointerOnly; tipPressure; ... } }` | same |
| `tablettool { pressure_range_* }` | `tablet { tipPressure }` | close |
| `virtualkeyboard { }` | — | n/a: no virtual-keyboard protocol yet |
| `device { name; sensitivity; ... }` | `input { device("name") { pointerSpeed; accelProfile; naturalScroll; leftHanded; enabled; ... } }` | same |

## cursor

| Hyprland | newm | |
|---|---|---|
| `XCURSOR_THEME`, `XCURSOR_SIZE` | `cursor { theme; size }` (programs are told too) | same |
| `inactive_timeout` | `cursor { hideAfter }` | same |
| `hide_on_key_press` | Caulis: `caulis.hideCursorWhileTyping = true` | same |
| `no_hardware_cursors` | Caulis: `caulis.hardwareCursor = false` | same |
| `enable_hyprcursor`, `sync_gsettings_theme` | the theme is handed to programs | close |
| `no_warps`, `warp_on_*`, `persistent_warps`, `default_monitor` | — | n/a: newm moves the view, not the pointer |
| `zoom_factor`, `zoom_rigid`, ... | newm's zoom is of the wall: `zoom-in`, `zoom-out` | close |
| `hide_on_touch`, `hide_on_tablet`, `min_refresh_rate`, `hotspot_padding`, `use_cpu_buffer` | — | future |

## gestures

| Hyprland | newm | |
|---|---|---|
| `gesture = 3, horizontal, workspace` | three fingers move over the wall; `gestures { bind(GestureAction.SWIPE, null, "swipe-3") }` | close |
| other gesture actions (`move`, `resize`, `close`, `fullscreen`, `float`, `dispatcher`) | `gestures { bind(GestureAction.MOVE_RESIZE, "L", "move-1", "swipe-2") }`, gestures of your own: `on("swipe-4", "down", "…") { toggleOverview() }` | same |
| `workspace_swipe_distance`, `_invert`, `_min_speed_to_force`, `_cancel_ratio`, `_forever`, `_direction_lock` | `gestures { scalePx }`, `tuning { swipe { } }` | close |
| `workspace_swipe_touch` | — | future |

## group (tabbed groups)

| Hyprland | newm | |
|---|---|---|
| groups, `groupbar { }`, `group.col.*`, `auto_group`, `drag_into_group`, … | windows on the same tiles form a stack (`next-in-stack`); `screens { layout = "monocle" }` | future: tabbed groups with a tab bar are a larger piece of work |

## misc

| Hyprland | newm | |
|---|---|---|
| `disable_hyprland_logo`, `disable_splash_rendering`, `force_default_wallpaper`, `splash_*`, `col.splash` | `background { path }` (newm's own wallpaper otherwise) | n/a |
| `vrr` | `misc { vrr = true }` | same (new) |
| `mouse_move_enables_dpms`, `key_press_enables_dpms` | `misc { wakeOnInput }` | same (new) |
| `always_follow_on_dnd` | — | n/a: no workspaces to follow to |
| `layers_hog_keyboard_focus` | panels that ask for the keyboard get it | same |
| `animate_manual_resizes`, `animate_mouse_windowdragging` | `animations { on("moveResize") }` | same |
| `disable_autoreload` | — | n/a: saving applies; a mistake leaves the last good settings |
| `enable_swallow`, `swallow_regex`, `swallow_exception_regex` | `misc { swallow = true; swallowApps; swallowExceptTitles }` | same (new) |
| `focus_on_activate` | `misc { focusOnActivate }` | same (new) |
| `mouse_move_focuses_monitor` | `input { focusFollowsMouse }` | close |
| `allow_session_lock_restore`, `lockdead_screen_delay` | `compositor { lockFailure; lockShowMillis }`: a locker that died leaves Caulis' own lock until a locker unlocks | close |
| `session_lock_blur`, `session_lock_xray` | newm's own lock screen: `lock { }` | close |
| `background_color` | `misc { backgroundColor }` | same (new) |
| `close_special_on_empty` | `specialWorkspace { hideWhenEmpty }` | same |
| `new_window_takes_over_fullscreen`, `exit_window_retains_fullscreen`, `on_focus_under_fullscreen` | `view { stickyFullscreen }`; a new window opens beside, the view goes to it | close |
| `initial_workspace_tracking`, `initial_workspace_token_timeout` | windows open where you are (`windows { newPlacement }`) | n/a |
| `middle_click_paste` | `misc { middleClickPaste }` | same (new) |
| `render_unfocused_fps` | — | n/a: every window gets its frames while shown |
| `enable_anr_dialog`, `anr_missed_pings` | — | future |
| `bell_sound`, `font_family` | `panels { topBar { font } }`; `startupSound { }` | close |
| `size_limits_tiled`, `new_float_force_onscreen`, `float_force_onscreen` | `view { floatingMinSize }`; floating windows open on screen | close |
| `name_vk_after_proc`, `disable_watchdog_warning`, `disable_xdg_env_checks`, `disable_scale_notification`, `disable_hyprland_guiutils_check`, `screencopy_force_8b` | — | n/a: Hyprland's own |

## binds

| Hyprland | newm | |
|---|---|---|
| `bind = SUPER, Q, killactive` | `bind("Super+Q", "close-window")` (keys as you say them; [Key bindings](keys.md)) | same |
| `bindl` (locked) | the laptop's keys work on the lock screen (`caulis` actions: volume, brightness) | close |
| `bindr` (release), `bindo` (long press) | `"Super"` alone: Super tapped | close |
| `binde` (repeating) | — | future |
| `bindm` (mouse) | `mouse { superMove; moveButton; resizeButton }` | same |
| `bindd` (description) | `bind(..., description = "...")`, shown by Super+/ | same |
| `bindn`, `bindt`, `bindi`, `bindp`, `bindc`, `bindg` | — | future |
| `workspace_back_and_forth`, `allow_workspace_cycles`, `workspace_center_on`, `hide_special_on_workspace_change` | — | n/a: no numbered workspaces |
| `pass_mouse_when_bound`, `scroll_event_delay`, `drag_threshold`, `focus_preferred_method`, `movefocus_cycles_*`, `window_direction_monitor_fallback`, `allow_pin_fullscreen`, `disable_keybind_grabbing`, `ignore_group_lock` | — | n/a or future |

## Dispatchers (what a key does)

| Hyprland | newm action |
|---|---|
| `exec` | `"launch", "cmd"`, `"terminal"` |
| `killactive`, `window.close` | `"close-window"` |
| `exit` | `"logout"` |
| `movefocus l/r/u/d` | `"focus-left"`, `"focus-right"`, `"focus-up"`, `"focus-down"` |
| `movewindow l/r/u/d`, `swapwindow` | `"move-window-left"` …, `"swap-on-screen"` |
| `resizeactive` | `"window-narrower"`, `"window-wider"`, `"window-shorter"`, `"window-taller"` |
| `togglefloating` | `"float"` |
| `fullscreen` | `"fullscreen"`, `"fit-window"` |
| `workspace n` | `"go-to-window", "n"` (the n-th window), `"next-screen"`, `"previous-screen"` |
| `movetoworkspace n` | `"window-to-next-screen"`, `"window-to-previous-screen"` |
| `togglespecialworkspace`, `movetoworkspace special` | `"special-workspace"`, `"to-special-workspace"`, `"from-special-workspace"` |
| `focusmonitor`, `movewindow mon:` | `"next-monitor"`, `"window-to-next-monitor"` |
| `cyclenext` | `"next-window"`, `"previous-window"` |
| `layoutmsg` (`togglesplit`, `swapwithmaster`, ...) | `"next-layout"`, `"promote-to-master"` |
| `toggle_swallow` | `"swallow"` |
| `reload` | `"reload"` |
| `dpms`, `exec hyprlock` | `"lock"`, `energy { }` |
| `pin`, `pseudo`, `centerwindow`, `tag`, `alterzorder`, `setprop`, `signal`, group dispatchers, `sendshortcut`, `pass`, `global` | future |
| — (newm's own) | `"overview"`, `"zoom-in"`, `"zoom-out"`, `"side-by-side"`, `"launcher"`, `"clipboard"`, `"screenshot"`, `"key-help"` |

## Window rules

newm's `view { rules { } }` decides where a window goes and how it looks; Caulis' `windowRule { }`
how it behaves.

| Hyprland | newm | |
|---|---|---|
| `match:class`, `match:title`, `initial_class`, `initial_title`, `xwayland`, `float`, `fullscreen`, `pin`, `focus` | `view.appId`, `view.title` in `rules { view -> }`; `windowRule { appId(...); title(...); initialAppId(...); isXwayland(); isFloating(); isFullscreen(); isFocused() }` | same |
| `float`, `size`, `move`, `center` | `rule(float = true, floatSize = 900 to 600, floatPos = 0.1 to 0.1)` | same |
| `opacity` | `rule(opacity = 0.97)` | same |
| `no_blur` / blur | `rule(blur = Blur(...))` | same |
| `border_size`, `border_color`, `no_border` | `rule(focus = false)` (no focus line) | close |
| `idle_inhibit` | `windowRule { keepAwake = true }` | same |
| `suppress_event` | `windowRule { ignore(WindowRequest.MAXIMIZE) }` | same |
| `no_screen_share` | `windowRule { hiddenFromCapture = true }` | same |
| `no_initial_focus`, `no_focus` | `windowRule { takesFocus = false }` | same |
| `min_size`, `max_size` | `windowRule { minSize(300, 400); maxSize(...) }` | same |
| `tag` | `windowRule { tags("video") }` + `hasTag("video")` | same |
| `workspace`, `monitor` | — | n/a: windows open where you are |
| `maximize`, `fullscreen`, `pin`, `pseudo`, `group`, `rounding`, `no_shadow`, `no_dim`, `no_anim`, `animation`, `dim_around`, `stay_focused`, `keep_aspect_ratio`, `render_unfocused`, `immediate`, `no_vrr`, `xray`, … | — | future |

Layer rules: `layerRule { namespace("fuzzel"); blur = true; dimAround = 0.4f; opacity; cornerRadius; shadow; hiddenFromCapture }` (Hyprland's `blur`, `dim_around`, `no_screen_share`; `no_anim`, `order`, `above_lock`: future).

## Workspaces and workspace rules

| Hyprland | newm | |
|---|---|---|
| numbered workspaces, `workspace = n, monitor:...`, `persistent`, `default_name`, `default` | each monitor has a wall of its own; `screens { }` divides it into screens | n/a |
| `gaps_in`, `gaps_out`, `border_size`, `rounding`, `decorate`, `no_border`, `no_shadow` per workspace | — | n/a |
| `layout` per workspace | `screens { layout }`, `"next-layout"` per screen | close |
| special workspaces (`special:name`) | `specialWorkspace { }` and `"special-workspace", "name"` | same |
| smart gaps (`w[tv1]`, `f[1]`) | `view { fillFirst }`, `screens { gaps }` | close |

## Permissions (ecosystem)

| Hyprland | newm | |
|---|---|---|
| `permission = binary, screencopy, allow/ask/deny` | `permissions { rule("/usr/bin/grim", "screencopy", "allow") }` (a regular expression for the path, or the program's name); `"ask"` shows a card: Allow / Allow until newm quits / Deny | same (new) |
| `enforce_permissions` | `permissions { default("screencopy", "ask") }` (any feature) | same (new) |
| `keyboard` (new keyboards by name) | `input { device("Rubber Ducky*") { enabled = false } }` | close |
| `plugin` | — | n/a: no plugins |
| `cursorpos`, `input-capture` | — | n/a: no such protocols in Caulis |
| — (newm's own) | `window-list`, `clipboard`, `panels`, `lock`, `monitors`, `gamma` | more |
| `no_update_news`, `no_donation_nag` | — | n/a |

## xwayland, render, opengl, quirks, debug

| Hyprland | newm | |
|---|---|---|
| `xwayland { enabled }` | `input { xwayland }` | same |
| `xwayland { force_zero_scaling; use_nearest_neighbor }` | Caulis: `caulis.sharpJavaScaling`; X11 programs are scaled by Caulis | close |
| `render { direct_scanout }` | Caulis: `caulis.directScanout` | same |
| `render { cm_*, use_fp16, ctm_animation, … }`, `opengl { }`, `quirks { }` | — | future (colour management, HDR) |
| `debug { overlay; damage_blink; disable_logs; ... }` | Caulis: `caulis.stats = true`, `logLevel`, newm's log (`~/.local/state/newm/newm.log`) | close |
| `experimental { }` | — | n/a |
