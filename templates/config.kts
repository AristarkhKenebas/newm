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
    cornerRadius = 14.0                     // windows' corners (bigger cuts into what programs draw at their edges)
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
    // newm's own bar at the top: three see-through islands floating at the edge — the wall
    // (each window a tile; click: all windows) and the focused window, the clock (click: the
    // calendar), the tray (background programs) and the network, volume, battery, power.
    topBar {
        enabled = true
        height = 32                         // an island's height
        margin = 6                          // the gap to the screen's edges
        cornerRadius = 12
        theme = "auto"                      // "auto" (follows light/dark), "light", "dark"
        fontSize = 13
        // font = "Inter"
        left = listOf("wall", "window")
        center = listOf("clock")
        right = listOf("tray", "layout", "bluetooth", "network", "volume", "battery", "power")
        clock = "HH:mm"                     // java.time patterns: "h:mm a", "HH:mm:ss"
        date = "EEE d MMM"                  // "" for none
        // accentColor = "#BF616A"          // and islandColor, textColor: your own colours
        // texts { listOf(cpu(), memory()) }   // shown by the module "texts"
        // style = "classic"                // the original newm's bar (bars/classic.kts)
    }
    // A bar at the bottom too:
    // bottomBar { enabled = true; left = listOf("window"); center = emptyList(); right = listOf("tray") }

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
    // Unset: the system's layouts (with Alt+Shift switching). Your own:
    // layouts = "us,ru"
    // options = "grp:alt_shift_toggle"     // Alt+Shift switches; "grp:caps_toggle": Caps Lock
    // numLock = true                       // Num Lock on from the start
}

input {
    tapToClick = true
    naturalScroll = false
    focusFollowsMouse = true                // the window under the mouse gets the keyboard (false: a click)
    // xwayland = true                      // X11 programs
}

// cursor { theme = "Adwaita"; size = 24; hideAfter = 5 }   // hideAfter: seconds the mouse is still

// A monitor of its own (connectors: `newm cmd debug`):
// output("eDP-1") { scale = 1.25 }
// output("HDMI-A-1") { width = 2560; height = 1440; refreshHz = 144.0; x = 1920; y = 0 }

// --- Keys ------------------------------------------------------------------------------------
// "L" is the Logo (Super) key, "S" Shift, "C" Ctrl, "A" Alt: "L-S-h" is Logo+Shift+h, and
// "L-H" means the same. "L-" is Logo tapped alone. "L-x"("what it does") { } says it in the
// help (Logo+/). Inside { } the layout is at hand: move(...),
// toggleOverview(), ... (all of them: docs/en/settings.md).

val terminal = defaultTerminal()             // $TERMINAL, else the first installed: alacritty, foot, kitty, ...; or "foot"

keys {
    // Each key with what it does in words: Logo+/ shows them all.

    // Programs
    "L-Return"("Open a terminal") { launch(terminal) }
    "L-d"("Find and start a program") { enterLauncherOverlay() }

    // Going around: the window beside
    "L-h"("Window to the left") { move(-1, 0) }
    "L-j"("Window below") { move(0, 1) }
    "L-k"("Window above") { move(0, -1) }
    "L-l"("Window to the right") { move(1, 0) }
    "L-Left"("Window to the left") { move(-1, 0) }
    "L-Down"("Window below") { move(0, 1) }
    "L-Up"("Window above") { move(0, -1) }
    "L-Right"("Window to the right") { move(1, 0) }
    "L-Tab"("Next window") { cycleViews(1) }
    "L-S-Tab"("Previous window") { cycleViews(-1) }
    "L-t"("Next window of a stack") { moveInStack(1) }

    // Seeing more or less of the wall
    "L-"("All windows (tap Super alone)") { toggleOverview() }
    "L-minus"("Zoom out (see more)") { basicScale(1) }
    "L-equal"("Zoom in (see fewer)") { basicScale(-1) }
    "L-u"("Zoom out") { basicScale(1) }
    "L-n"("Zoom in") { basicScale(-1) }
    "L-0"("Fit the window to the screen") { fitFocusedView() }
    "L-s"("Two windows side by side, and back") { sideBySide() }

    // Moving a window on the wall
    "L-S-h"("Move the window left") { moveFocusedView(-1, 0) }
    "L-S-j"("Move the window down") { moveFocusedView(0, 1) }
    "L-S-k"("Move the window up") { moveFocusedView(0, -1) }
    "L-S-l"("Move the window right") { moveFocusedView(1, 0) }
    "L-S-Left"("Move the window left") { moveFocusedView(-1, 0) }
    "L-S-Down"("Move the window down") { moveFocusedView(0, 1) }
    "L-S-Up"("Move the window up") { moveFocusedView(0, -1) }
    "L-S-Right"("Move the window right") { moveFocusedView(1, 0) }

    // Its size, in tiles (one tile: a screen at zoom 1)
    "L-C-h"("Window narrower") { resizeFocusedView(-1, 0) }
    "L-C-l"("Window wider") { resizeFocusedView(1, 0) }
    "L-C-k"("Window shorter") { resizeFocusedView(0, -1) }
    "L-C-j"("Window taller") { resizeFocusedView(0, 1) }
    "L-C-Left"("Window narrower") { resizeFocusedView(-1, 0) }
    "L-C-Right"("Window wider") { resizeFocusedView(1, 0) }
    "L-C-Up"("Window shorter") { resizeFocusedView(0, -1) }
    "L-C-Down"("Window taller") { resizeFocusedView(0, 1) }

    // Windows
    "L-q"("Close the window") { closeFocusedView() }
    "L-f"("Fullscreen, and back") { toggleFullscreen() }
    "L-v"("Float, and back") { toggleFocusedViewFloating() }

    // The session
    "L-slash"("These keys") { toggleKeyHelp() }
    "L-p"("Lock the screen") { ensureLocked(dim = true) }
    "L-C"("Reload this configuration") { updateConfig() }
    "L-S-Escape"("Log out") { terminate() }

    // The laptop's keys work on the lock screen too
    "XF86MonBrightnessUp"("Brighter") { caulis.run("brightness +5") }
    "XF86MonBrightnessDown"("Darker") { caulis.run("brightness -5") }
    "XF86AudioRaiseVolume"("Louder") { caulis.run("volume +5") }
    "XF86AudioLowerVolume"("Quieter") { caulis.run("volume -5") }
    "XF86AudioMute"("Sound off, and on") { caulis.run("volume mute") }
    "XF86AudioMicMute"("Microphone off, and on") { caulis.run("mic-mute") }
    "Print"("Screenshot") { launch("grim") }
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
