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
    mode = "follow"                         // "follow": moves and zooms with the view; "static": stands still
    // timeScale = 0.15                     // how quickly it follows (seconds; with the animation "backgroundMove" on)
}

cornerRadius = 24                           // the screen's own corners rounded (0: square)

// --- Animations ------------------------------------------------------------------------------
//
// Off: every change is there at once (fingers are still followed exactly — only what comes after
// they lift is at once). One line turns on newm's usual animations, as newm always looked:
//
//     animations { preset = "newm" }
//
// Or, as in Hyprland, all of them (`enabled = true`) or each by name. A setting for a name is
// for those under it too ("windows" for windowsIn, windowsOut, ...), unless they say otherwise.
// Each takes: enabled, millis (its time; or speed = Hyprland's tenths of a second), curve (by
// name) and, for the menus and cards, a look (style): "fade", "popin 90%", "slide up 20".
// Curves: "linear" (newm's own), "ease-out" (fast, then gently), "ease-in-out", "smooth",
// "overshoot" (a little past and back), or your own: curve("name", x1, y1, x2, y2) is a
// Bézier curve with Hyprland's (and CSS's) numbers, spring("name", stiffness, damping) a spring.
animations {
    // preset = "newm"                      // all of newm's usual animations on
    // enabled = true                       // all on (as preset = "newm"); false: all off
    // millis = 300                         // the global time (ms) of those without their own
    // curve = "linear"                     // the global curve
    // curve("snappy", 0.05, 0.9, 0.1, 1.05)   // a curve of your own, used below by its name
    // spring("bouncy", stiffness = 300.0, damping = 14.0)

    // Windows (time: the global one, 300 ms; curve: linear)
    // animation("windows", millis = 250, curve = "snappy")   // all window animations below
    // on("windowsIn")                      // a window appearing: it takes its place, the others make room
    // on("windowsOut")                     // a window closing: the others take its place
    // on("windowsMove")                    // moved, resized, floating/tiled, side by side, onto another workspace, swallowed
    // on("fullscreen")                     // going fullscreen and back
    // on("moveResize")                     // moved or resized with mouse or fingers: settling on the grid when let go

    // The view and gestures
    // on("camera")                         // the view moving: to the next window, zooming in and out (Logo+u/n), a bar coming
    // on("overview")                       // the overview opening and closing (Logo alone)
    // on("gestureSnap")                    // after a swipe or pinch: gliding on to where it settles (time from the fingers' speed)
    // on("gestureSmoothing")               // fingers smoothed: the view follows softly (75 ms); off: exactly under them

    // newm's own parts
    // on("border")                         // the focus border gliding to the focused window (focus.animTime, 300 ms)
    // on("lock")                           // the lock screen sliding in (the screen is locked before) and out
    // on("launcher")                       // the launcher coming and going (ease-out)
    // on("launcherList")                   // its first rows coming in one after another (220 ms)
    // on("backgroundMove")                 // the wallpaper gliding behind the view (background.timeScale, 150 ms)
    // on("backgroundIn")                   // the wallpaper fading in the first time (600 ms)
    // on("startup"); on("quit")            // newm fading in after it started and out when it quits (blendTime, 1 s)
    // on("sessionRestore")                 // windows of the last session going back to their places
    // on("brightness")                     // the screen's brightness gliding to a new level (config.py's BacklightManager)

    // The bar, menus and cards: each "...In" and "...Out" on its own too (times: in / out)
    // on("popups")                         // the bar's menus (280 / 180 ms); popupsResize: growing as they change
    // animation("popupsIn", style = "popin 90%")   // a look: "fade", "popin 90%", "slide up 20", "slide down 8"
    // on("notifications")                  // notification cards (260 / 220 ms)
    // on("osd")                            // the volume and brightness slider and toasts (140 / 220 ms)
    // on("clipboard")                      // the clipboard's history, Logo+V (260 / 180 ms)
    // on("keyHelp")                        // the keys' help, Logo+/ (260 / 180 ms)
    // on("widgets")                        // small motions: hover (colours under the pointer, 100–140 ms),
    //                                      // toggles (switches and sliders, 160 ms), scroll (lists to the chosen entry),
    //                                      // spinner (the refresh icon turning, 900 ms a turn)
}
// blendTime = 1.0                          // fading in at the start, out at the end (with startup and quit on)

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
    color = "auto"                          // "auto": the colours' accent; or "#RRGGBB" / "#RRGGBBAA" (see-through)
    distance = 4.0                          // how far outside the window
    width = 2.0
    // animTime = 0.3                      // its glide (with the animation "border" on)
    // animateOnChange = false              // the line glides to the next window
    // color = "rgba(33ccffee) rgba(00ff99ee) 45deg"   // a gradient, written as Hyprland's col.active_border
    // urgentColor = "rgb(ff5555)"          // a window wanting attention, not focused
    // shader = "border.glsl"               // your own GLSL (next to this file), read again when saved;
    //                                      // see Caulis's border shaders: rainbow, pulsing glow, marching ants:
    //   vec4 paint(vec2 at) { float h = fract(progress + time * 0.2);
    //       return vec4(clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0), 1.0); }
    //   vec4 paint(vec2 at) { return vec4(colors[0].rgb * (0.55 + 0.45 * sin(time * 3.0)), 1.0); }
    //   vec4 paint(vec2 at) { float d = step(0.5, fract((progress * 2.0 * (size.x + size.y) - time * 40.0) / 16.0));
    //       return mix(vec4(0.1, 0.1, 0.1, 1.0), vec4(1.0, 0.85, 0.2, 1.0), d); }
    // animate = true                       // false: the shader's own time stands still
}

// Special workspaces (Hyprland's "special"): above the wall on the focused monitor, at the
// screen's own place, with a small layout of their own; the overview hides them.
// specialWorkspace {
//     dim = 0.4                            // the wall behind darker
//     blurRadius = 0                       // and blurred (0: not)
//     gap = 40                             // room to the screen's edges
//     size = 1.0                           // how much of the screen
//     hideWhenEmpty = true
//     newWindowsJoin = true                // windows opening while it is shown go into it
// }

// The colours of the bar, the menus and the focus border: made from the wallpaper, as phones do.
// The quick settings (the bar's grid button) have a palette tile to choose among them; set them here to keep them fixed.
// colors {
//     source = "wallpaper"                // "wallpaper", "nord" (the classic ones) or "#5b8cc8"
//     style = "tonal"                     // "tonal", "vibrant", "expressive", "neutral", "monochrome"
// }

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
        left = listOf("window")             // also "wall": a small map of your wall
        center = listOf("clock")
        right = listOf("tray", "layout", "bluetooth", "network", "volume", "battery", "notifications", "power")
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
// "L-H" means the same. "L-" is Logo tapped alone. "L-x"("what it does") { } shows it in the
// help (Logo+/); a key without words is left out of it. Inside { } the layout is at hand: move(...),
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
    "L-v"("Clipboard history") { toggleClipboard() }
    "L-S-v"("Float, and back") { toggleFocusedViewFloating() }

    // Special workspaces (Hyprland's "special"): a layer above the wall with windows of its own
    // (L-s and L-S-s are taken above: change them if you want Hyprland's Super+S / Super+Shift+S)
    // "L-grave"("Special workspace, and back") { toggleSpecialWorkspace() }
    // "L-S-grave"("Window into the special workspace") { moveToSpecialWorkspace() }
    // "L-C-grave"("Window back onto the wall") { moveOutOfSpecialWorkspace() }
    // "L-m"("Music, and back") { toggleSpecialWorkspace("music") }
    // "L-S-m"("Window into music, unseen") { moveToSpecialWorkspace("music", silent = true) }

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
    // Screenshots, as Windows has them
    "L-S-s"("Screenshot: choose a part") { screenshot() }
    "L-Print"("Screenshot of the screen") { screenshotScreen() }
    "Print"("Screenshot: choose") { screenshot() }
    "A-Print"("Screenshot of the window") { screenshotWindow() }
}

// --- Gestures (touchpads) ----------------------------------------------------------------------

gestures {
    // bind(GestureAction.SWIPE, null, "swipe-3")                    // move over the wall
    // bind(GestureAction.SWIPE_TO_ZOOM, null, "swipe-4")            // up and down: zoom
    // bind(GestureAction.MOVE_RESIZE, "L", "move-1", "swipe-2")     // Logo + fingers: move, resize
    // bind(GestureAction.LAUNCHER, null, "swipe-5")
    // lpInertia = 0.8                                               // smoother (and slower) feel
}

// --- The start ------------------------------------------------------------------------------------

// Programs started with the session: one after another once newm is shown, once per session
// (saving this file starts only what you added). `newm cmd autostart` lists them.
autostart {
    // app("waybar")                                          // a bar of your own
    // app("nm-applet", "--indicator") { delay = 2.seconds; ifNotRunning = true; restart = OnCrash(maxTimes = 3, within = 1.minutes) }
    // shell("sleep 1; notify-send 'Hello'")                  // a piece of shell
    // desktopEntries = true                                  // also ~/.config/autostart's programs
}

// Dark until newm is ready, then it fades in (on by default).
reveal {
    // enabled = true
    // fadeMillis = 600
}

// A sound as newm fades in (on by default): "airy", "classic", "glass", "soft", or a file of yours.
startupSound {
    // preset = "airy"
    // volume = 0.8
    // file = "~/sounds/hello.ogg"
    // enabled = false                                        // silent
}

// --- When things happen --------------------------------------------------------------------------

onStartup {
    // launch("mako")                       // notifications (or in autostart { } above)
    // launch("nm-applet --indicator")
}

// onReconfigure { launch("notify-send newm 'Configuration loaded'") }

energy {
    idleTimes = listOf(120, 300, 600)       // seconds without input: dim, lock, suspend
    screensOffAfter = 330                   // and the screens go dark (0: never)
    // suspendCommand = "systemctl suspend"
}
// lockOnWakeup = true
