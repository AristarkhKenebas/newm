// newm — your configuration (~/.config/newm/config.kts).
//
// newm made this file the first time it started. Change anything and save: newm takes it on
// at once, no need to log out. A mistake shows up on screen with its line, and the settings
// before stay until it is fixed. Lines starting with // are notes: remove the // to use one.
//
// newm in short: windows are tiles on an endless wall. The screen shows a part of the wall —
// zoom out (Logo+u, four fingers up) to see more of it, in (Logo+n) to see less. New windows
// take the next free tile. Logo alone shows everything (the overview).
//
// The guide: https://github.com/AristarkhKenebas/newm/blob/master/docs/en/README.md

// --- Looks -----------------------------------------------------------------------------------

background {
    path = "resource:wallpaper.jpg"         // newm's own; or "~/Pictures/wallpaper.jpg"
    anim = true                             // the wallpaper moves a little with the view
    // timeScale = 0.15                     // how quickly it follows (seconds)
}

cornerRadius = 24                           // the screen's own corners rounded (0: square)
animTime = 0.3                              // how long moving and zooming take (seconds)
// blendTime = 1.0                          // fading in at the start, out at the end

view {
    cornerRadius = 24.0                     // windows' corners
    padding = 8.0                           // the gap around each window
    // fullscreenPadding = 0.0
    // sendFullscreen = true                // programs are told when they are fullscreen
    // acceptFullscreen = true              // programs may ask for it (a video's button)
    // floatingMinSize = true               // floating windows open at their smallest size

    // Rules for some windows, decided when they open (their app id: `newm cmd debug` lists them).
    rules { view ->
        when (view.appId) {
            "pavucontrol", "mpv", "imv", "org.gnome.Calculator" -> rule(float = true)
            // See-through terminals with what is behind them blurred (set the terminal's own
            // opacity too, e.g. alacritty: window.opacity = 0.85).
            "Alacritty", "foot", "kitty", "org.wezfurlong.wezterm" -> rule(blur = Blur(radius = 6, passes = 4))
            else -> null
        }
    }
}

focus {
    enabled = true                          // a line around the focused window
    color = "#E5E9F0AA"                     // "#RRGGBB" or "#RRGGBBAA" (see-through)
    distance = 4.0                          // how far outside the window
    width = 2.0
    // animTime = 0.3
    // animateOnChange = false              // the line glides to the next window
}

panels {
    // newm's own bar at the top: see-through, what is behind it blurred.
    topBar {
        enabled = true
        height = 28
        fontSize = 12
        // font = "JetBrains Mono"
        background = "#ECEFF4B0"
        foreground = "#2E3440"
        blur = true
        texts { listOf(wifi(), cpu(), memory(), temperature(), battery(), date(), time()) }
    }
    // A bar at the bottom too:
    // bottomBar { enabled = true; texts { listOf(user(), "newm") } }

    // Or a bar program of your own instead (turn topBar off then; a waybar in newm's look
    // is in bars/waybar of this folder):
    // bar { cmd = "waybar"; visibleNormal = true; visibleFullscreen = false }

    lock {
        w = 0.6; h = 0.6                    // its size: parts of the screen
        cornerRadius = 32.0
    }
    launcher {
        w = 0.7; h = 0.7
        cornerRadius = 24.0
    }
}

// --- Keyboard, mouse, monitors -----------------------------------------------------------

keyboard {
    layouts = "us"                          // "us,ru"
    // options = "grp:alt_shift_toggle"     // switch layouts with Alt+Shift
    // numLock = true                       // Num Lock on from the start
}

input {
    tapToClick = true
    naturalScroll = false
    focusFollowsMouse = false               // the window under the mouse gets the keyboard
    // xwayland = true                      // X11 programs
}

// cursor { theme = "Adwaita"; size = 24; hideAfter = 5 }   // hideAfter: seconds the mouse is still

// A monitor of its own (connectors: `newm cmd debug`):
// output("eDP-1") { scale = 1.25 }
// output("HDMI-A-1") { width = 2560; height = 1440; refreshHz = 144.0; x = 1920; y = 0 }

// --- Keys ------------------------------------------------------------------------------------
// "L" is the Logo (Super) key, "S" Shift, "C" Ctrl, "A" Alt: "L-S-h" is Logo+Shift+h, and
// "L-H" means the same. "L-" is Logo tapped alone. Inside { } the layout is at hand: move(...),
// toggleOverview(), ... (all of them: docs/en/settings.md).

val terminal = defaultTerminal()             // $TERMINAL, else the first installed: alacritty, foot, kitty, ...; or "foot"

keys {
    // Programs
    "L-Return" { launch(terminal) }
    "L-d" { enterLauncherOverlay() }        // newm's launcher (also: five fingers)

    // Focus: the window beside
    "L-h" { move(-1, 0) }
    "L-j" { move(0, 1) }
    "L-k" { move(0, -1) }
    "L-l" { move(1, 0) }
    "L-Left" { move(-1, 0) }
    "L-Down" { move(0, 1) }
    "L-Up" { move(0, -1) }
    "L-Right" { move(1, 0) }
    "L-t" { moveInStack(1) }                // the next window of a stack
    "L-Tab" { cycleViews(1) }               // every window in turn
    "L-S-Tab" { cycleViews(-1) }

    // Moving windows on the wall
    "L-S-h" { moveFocusedView(-1, 0) }
    "L-S-j" { moveFocusedView(0, 1) }
    "L-S-k" { moveFocusedView(0, -1) }
    "L-S-l" { moveFocusedView(1, 0) }

    // Resizing windows (in tiles)
    "L-C-h" { resizeFocusedView(-1, 0) }
    "L-C-j" { resizeFocusedView(0, 1) }
    "L-C-k" { resizeFocusedView(0, -1) }
    "L-C-l" { resizeFocusedView(1, 0) }

    // Seeing more or less of the wall
    "L-u" { basicScale(1) }
    "L-n" { basicScale(-1) }
    "L-" { toggleOverview() }

    // Windows
    "L-q" { closeFocusedView() }
    "L-f" { toggleFullscreen() }
    "L-space" { toggleFocusedViewFloating() }

    // The session
    "L-p" { ensureLocked(dim = true) }      // lock the screen
    "L-C" { updateConfig() }                // load this file again (saving does it too)
    "L-S-Escape" { terminate() }            // log out

    // The laptop's keys work on the lock screen too
    "XF86MonBrightnessUp" { caulis.run("brightness +5") }
    "XF86MonBrightnessDown" { caulis.run("brightness -5") }
    "XF86AudioRaiseVolume" { caulis.run("volume +5") }
    "XF86AudioLowerVolume" { caulis.run("volume -5") }
    "XF86AudioMute" { caulis.run("volume mute") }
    "XF86AudioMicMute" { caulis.run("mic-mute") }
    "Print" { launch("grim") }
}

// --- Gestures (touchpads) ----------------------------------------------------------------------

gestures {
    // bind(GestureAction.SWIPE, null, "swipe-3")                    // move over the wall
    // bind(GestureAction.SWIPE_TO_ZOOM, null, "swipe-4")            // up and down: zoom
    // bind(GestureAction.MOVE_RESIZE, "L", "move-1", "swipe-2")     // Logo + fingers: move, resize
    // bind(GestureAction.LAUNCHER, null, "swipe-5")
    // lpInertia = 0.8                                               // smoother (and slower) feel
}

// --- When things happen --------------------------------------------------------------------------

onStartup {
    // launch("mako")                       // notifications
    // launch("nm-applet --indicator")
}

// onReconfigure { launch("notify-send newm 'Configuration loaded'") }

energy {
    idleTimes = listOf(120, 300, 600)       // seconds without input: dim, lock, suspend
    screensOffAfter = 330                   // and the screens go dark (0: never)
    // suspendCommand = "systemctl suspend"
}
// lockOnWakeup = true
