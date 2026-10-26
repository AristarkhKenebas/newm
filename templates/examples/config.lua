-- newm — your configuration in Lua (~/.config/newm/config.lua).
--
-- Change anything and save: newm takes it on at once. A mistake shows up on screen with its
-- line, and the settings before stay. Lines starting with -- are notes.
-- (newm reads config.kts first: to use this file, move it next to config.kts and remove that one.)
--
-- The guide: https://github.com/AristarkhKenebas/newm/blob/master/docs/en/other-formats.md (the names: docs/en/settings.md, in snake_case).

background = {
  path = "resource:wallpaper.jpg",   -- newm's own; or "~/Pictures/wallpaper.jpg"
  anim = true,                       -- the wallpaper moves a little with the view
}

corner_radius = 24                   -- the screen's own corners
anim_time = 0.3                      -- how long moving and zooming take (seconds)

view = {
  corner_radius = 14,
  padding = 8,
}

-- Rules for some windows, by their app id (`newm cmd debug` lists them).
rules = {
  { app_id = "pavucontrol", float = true },
  { app_id = "mpv", float = true },
  { app_id = "imv", float = true },
  { app_id = "Alacritty", blur = { radius = 6, passes = 4 } },
  { app_id = "foot", blur = { radius = 6, passes = 4 } },
}

focus = {
  enabled = true,
  color = "#E5E9F0AA",
  distance = 4,
  width = 2,
}

panels = {
  top_bar = {                        -- newm's own bar (enabled = false: none, or another bar program)
    enabled = true,
    height = 32, margin = 6, corner_radius = 12,
    theme = "auto",                  -- "light", "dark"
    left = { "window" },
    center = { "clock" },
    right = { "tray", "layout", "bluetooth", "network", "volume", "battery", "power" },
    clock = "HH:mm", date = "EEE d MMM",
    -- style = "classic",            -- the original newm's bar, with texts = function() return { sys.cpu(), sys.time() } end
  },
  -- bar = { cmd = "waybar" },       -- a bar program of your own (waybar in newm's look: bars/waybar)
  lock = { w = 0.6, h = 0.6, corner_radius = 32 },
  launcher = { w = 0.7, h = 0.7, corner_radius = 24 },
}

-- keyboard = { layouts = "us,ru", options = "grp:alt_shift_toggle" }   -- unset: the system's layouts
input = { tap_to_click = true, natural_scroll = false, focus_follows_mouse = true }

-- Keys: "L" Logo, "S" Shift, "C" Ctrl, "A" Alt; "L-" is Logo tapped alone.
-- An action is a text ("move -1 0", "launch firefox", "volume +5") or a function.
local terminal = newm.terminal()     -- $TERMINAL, else the first installed (alacritty, foot, kitty, ...); or "foot"

keys = {
  ["L-Return"] = "launch " .. terminal,
  ["L-d"] = "enter_launcher_overlay",
  ["L-h"] = "move -1 0", ["L-j"] = "move 0 1", ["L-k"] = "move 0 -1", ["L-l"] = "move 1 0",
  ["L-Left"] = "move -1 0", ["L-Down"] = "move 0 1", ["L-Up"] = "move 0 -1", ["L-Right"] = "move 1 0",
  ["L-t"] = "move_in_stack 1",
  ["L-Tab"] = "cycle_views 1", ["L-S-Tab"] = "cycle_views -1",
  ["L-S-h"] = "move_focused_view -1 0", ["L-S-j"] = "move_focused_view 0 1",
  ["L-S-k"] = "move_focused_view 0 -1", ["L-S-l"] = "move_focused_view 1 0",
  ["L-C-h"] = "resize_focused_view -1 0", ["L-C-j"] = "resize_focused_view 0 1",
  ["L-C-k"] = "resize_focused_view 0 -1", ["L-C-l"] = "resize_focused_view 1 0",
  ["L-u"] = "basic_scale 1", ["L-n"] = "basic_scale -1",
  ["L-"] = "toggle_overview",
  ["L-q"] = "close_focused_view",
  ["L-f"] = "toggle_fullscreen",
  ["L-v"] = "toggle_focused_view_floating",
  ["L-0"] = "fit_focused_view", ["L-s"] = "side_by_side",
  ["L-minus"] = "basic_scale 1", ["L-equal"] = "basic_scale -1",
  ["L-slash"] = "toggle_key_help",
  ["L-p"] = "ensure_locked dim=true",
  ["L-C"] = "update_config",
  ["L-S-Escape"] = "terminate",
  ["XF86MonBrightnessUp"] = "brightness +5", ["XF86MonBrightnessDown"] = "brightness -5",
  ["XF86AudioRaiseVolume"] = "volume +5", ["XF86AudioLowerVolume"] = "volume -5",
  ["XF86AudioMute"] = "volume mute", ["XF86AudioMicMute"] = "mic-mute",
  ["Print"] = function() newm.launch("grim") end,
}

-- Programs at the start.
startup = {
  -- "mako",
}

energy = { idle_times = { 120, 300, 600 } }  -- seconds: dim, lock, suspend
