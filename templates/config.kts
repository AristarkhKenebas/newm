// newm — your configuration (~/.config/newm/config.kts).
//
// newm made this file the first time it started. Change anything and save: newm takes it on
// at once, no need to log out. A mistake shows up on screen with its line, and the settings
// before stay until it is fixed. Lines starting with // are notes or settings turned off:
// remove the // in front of a setting to use it.
//
// newm in short: windows are tiles on an endless wall. The screen shows a part of the wall —
// zoom out (Logo+u, four fingers up) to see more of it, in (Logo+n) to see less. A new window
// opens right of the one you are in. Logo alone shows everything (the overview).
//
// The guide: https://github.com/AristarkhKenebas/newm/blob/master/docs/en/README.md
// Every setting: https://github.com/AristarkhKenebas/newm/blob/master/docs/en/settings.md

// --- Your programs --------------------------------------------------------------------------
// The programs you use, each by a name of your own (as Hyprland's $terminal, $menu): the keys
// at the end start them, bind("Super+E", "launch", fileManager). Any Kotlin works in this file.

val terminal = defaultTerminal()             // $TERMINAL, else the first installed: alacritty, foot, kitty, ...; or "foot"
val fileManager = "nautilus"                 // Super+E
val browser = "firefox"                      // Super+B
// val menu = "fuzzel"                       // a launcher of your own for Super+D ("wofi --show drun"); without: newm's own

// --- Monitors -------------------------------------------------------------------------------
// Unset: each monitor at its best mode, side by side. A monitor of its own (connectors: `newm cmd debug`):
// output("eDP-1") { scale = 1.25 }
// output("HDMI-A-1") { width = 2560; height = 1440; refreshHz = 144.0; x = 1920; y = 0 }
// output("DP-1") { transform = 1 }         // turned (1: 90°, 2: 180°, 3: 270°)

// --- Environment ----------------------------------------------------------------------------
// Variables for the programs newm starts, also handed to the session's services (Hyprland's env).
// env("XCURSOR_SIZE", "24")
// env("QT_QPA_PLATFORMTHEME", "qt6ct")
// env("MOZ_ENABLE_WAYLAND", "1")

// --- Autostart ------------------------------------------------------------------------------
// Programs started with the session: one after another once newm is shown, once per session
// (saving this file starts only what you added). `newm cmd autostart` lists them.
autostart {
    // app("waybar")                                          // a bar of your own
    // app("nm-applet", "--indicator") { delay = 2.seconds; ifNotRunning = true; restart = OnCrash(maxTimes = 3, within = 1.minutes) }
    // app("telegram-desktop", "-startintray") { delay = 3.seconds }
    // shell("sleep 1; notify-send 'Hello'")                  // a piece of shell
    // desktopEntries = true                                  // also the programs of ~/.config/autostart
}

// Dark until newm is ready, then it fades in.
reveal {
    enabled = true
    // fadeMillis = 600
}

// A sound as newm fades in: "airy", "classic", "glass", "soft", or a file of yours.
startupSound {
    enabled = true                                            // false: silent
    preset = "airy"
    volume = 0.8
    // file = "~/sounds/hello.ogg"
}

// The windows opened again after `newm cmd restart keep` (and, if you like, after logging in).
session {
    // restoreAtLogin = false                                 // true: your last session's windows come back at login
    // exclude = listOf("org.keepassxc.KeePassXC")            // programs never started again
}

onStartup {
    // launch("nm-applet --indicator")      // (or in autostart { } above)
}

// onReconfigure { launch("notify-send newm 'Configuration loaded'") }

// --- Look -----------------------------------------------------------------------------------

background {
    path = "resource:wallpaper.jpg"         // newm's own; or "~/Pictures/wallpaper.jpg" (an SVG stays sharp)
    mode = "follow"                         // "follow": moves and zooms with the view; "static": stands still
    // timeScale = 0.15                     // how quickly it follows (seconds)
    // shader = "dragon-eye"                // a moving wallpaper instead: "dragon-eye", "ember-sky", "scales", "deep-forest", "molten" or a .glsl file
    // fps = 30                             // how often it is drawn a second (fpsOnBattery = 15 on battery)
}

// theme = "jadefire"                      // newm's own look: colours, focus border and a moving wallpaper at once
cornerRadius = 24                           // the screen's own corners rounded (0: square)
// colorScheme = "auto"                     // "auto" (the quick settings' choice), "dark" or "light"
// language = "auto"                        // newm's menus: "auto" (the system's), "en" or "ru"
// timeFormat = "HH:mm"                     // times in the menus and on the lock screen ("h:mm a": 3:45 PM)

// The colours of the bar, the menus and the focus border: made from the wallpaper, as phones do.
// The quick settings (the bar's grid button) choose among them; set them here to keep them fixed.
// colors {
//     source = "wallpaper"                 // "wallpaper", "nord" (the classic ones), "jadefire" or "#5b8cc8"
//     style = "tonal"                      // "tonal", "vibrant", "expressive", "neutral", "monochrome"
// }

// Windows: the gaps, corners and see-through (Hyprland's general and decoration)
view {
    cornerRadius = 14.0                     // windows' corners (bigger cuts into what programs draw at their edges)
    padding = 8.0                           // the gap around each window
    // fullscreenPadding = 0.0              // the gap around a fullscreen window
    // unfocusedOpacity = 0.92              // windows without the focus a little see-through (1.0: not)
    // fade = true                          // windows fade in and out
    // sendFullscreen = true                // programs are told when they are fullscreen
    // acceptFullscreen = true              // programs may ask for it (a video's button)
    // floatingMinSize = true               // floating windows open at their smallest size
    // fillFirst = false                    // true: the first window fills the screen (false: a quarter, as newm)
    // resizePushes = false                 // true: a window made bigger by keys pushes its neighbours aside
    // crispResize = false                  // true: a window being resized is drawn at its own size, not stretched
    // fitDrawnSize = false                 // true: a program that drew another size is fitted in its shape, not stretched
    // stickyFullscreen = false             // true: fullscreen stays when the view moves away
}

// The line around the focused window (Hyprland's border; a gradient as col.active_border).
focus {
    enabled = true                          // a line around the focused window
    color = "auto"                          // "auto": the colours' accent; or "#RRGGBB" / "#RRGGBBAA" (see-through)
    distance = 4.0                          // how far outside the window
    width = 2.0
    // animateOnChange = false              // the line glides to the next window
    // color = "rgba(33ccffee) rgba(00ff99ee) 45deg"   // a gradient, written as Hyprland's col.active_border
    // urgentColor = "rgb(ff5555)"          // a window wanting attention, not focused
    // shader = "border.glsl"               // your own GLSL (next to this file), read again when saved;
    //                                      // with vec4 paint(vec2 at): progress, time, size and colors are at hand
    // animate = true                       // false: the shader's own time stands still
}

// --- Animations -----------------------------------------------------------------------------
//
// newm's usual animations are on: windows glide to their places, the view glides when it moves.
// Turn them off with preset = "none" (every change is there at once; fingers are still followed).
// As in Hyprland, each can be changed by name: a setting for a name is for those under it too
// ("windows" for windowsIn, windowsOut, ...), unless they say otherwise. Each takes enabled,
// its time in millis, a curve by its name and, for the menus and cards, a look (style): "fade",
// "popin 90%", "slide up 20". Curves: "linear" (newm's own), "ease-out", "ease-in-out",
// "smooth", "overshoot" (a little past and back), or your own: a Bézier curve with Hyprland's
// numbers, or a spring.
animations {
    preset = "newm"                         // newm's usual animations; "none": all off
    // enabled = true                       // all on (as preset = "newm"); false: all off
    // millis = 300                         // the global time (ms) of those without their own
    // curve = "linear"                     // the global curve
    // curve("snappy", 0.05, 0.9, 0.1, 1.05)   // a curve of your own, used below by its name
    // spring("bouncy", stiffness = 300.0, damping = 14.0)

    // Windows (time: the global one, 300 ms; curve: linear)
    // animation("windows", millis = 250, curve = "snappy")   // all window animations below
    // on("windowsIn")                      // a window appearing: it takes its place, the others make room
    // on("windowsOut")                     // a window closing: the others take its place
    // on("windowsMove")                    // moved, resized, floating or tiled, side by side, onto another workspace
    // on("fullscreen")                     // going fullscreen and back
    // on("moveResize")                     // moved or resized with the mouse or fingers: settling on the grid when let go

    // The view and gestures
    // on("camera")                         // the view moving: to the next window, zooming in and out, a bar coming
    // on("overview")                       // the overview opening and closing (Logo alone)
    // on("gestureSnap")                    // after a swipe or pinch: gliding on to where it settles (time from the fingers' speed)
    // on("gestureSmoothing")               // fingers smoothed: the view follows softly (75 ms); off: exactly under them

    // newm's own parts
    // on("border")                         // the focus border gliding to the focused window (300 ms)
    // on("lock")                           // the lock screen sliding in (the screen is locked before) and out
    // on("launcher")                       // the launcher coming and going (ease-out)
    // on("launcherList")                   // its first rows coming in one after another (220 ms)
    // on("backgroundMove")                 // the wallpaper gliding behind the view (150 ms)
    // on("backgroundIn")                   // the wallpaper fading in the first time (600 ms)
    // on("startup"); on("quit")            // newm fading in after it started and out when it quits (1 s)
    // on("sessionRestore")                 // windows of the last session going back to their places
    // on("brightness")                     // the screen's brightness gliding to a new level

    // The bar, menus and cards: each "...In" and "...Out" on its own too (times: in / out)
    // on("popups")                         // the bar's menus (280 / 180 ms); popupsResize: growing as they change
    // animation("popupsIn", style = "popin 90%")   // a look: "fade", "popin 90%", "slide up 20", "slide down 8"
    // on("notifications")                  // notification cards (260 / 220 ms)
    // on("osd")                            // the volume and brightness slider and toasts (140 / 220 ms)
    // on("clipboard")                      // the clipboard's history, Logo+V (260 / 180 ms)
    // on("keyHelp")                        // the keys' help, Logo+/ (260 / 180 ms)
    // on("widgets")                        // small motions: hover, toggles, lists scrolling, the refresh icon turning
}
// blendTime = 1.0                          // fading in at the start, out at the end (seconds)

// --- The bar, notifications, screenshots ----------------------------------------------------

panels {
    // newm's own bar at the top: see-through islands floating at the edge — the focused window,
    // the clock (click: the calendar), the tray (background programs) and the network, volume,
    // battery, notifications and power.
    topBar {
        enabled = true
        height = 32                         // an island's height
        margin = 6                          // the gap to the screen's edges
        cornerRadius = 12
        theme = "auto"                      // "auto" (follows light or dark), "light", "dark"
        fontSize = 13
        // font = "Inter"
        left = listOf("window")             // also "wall": a small map of your wall; "media", "cpu", "memory", "temperature", "screens", "weather"
        center = listOf("clock")
        right = listOf("tray", "layout", "bluetooth", "network", "volume", "battery", "notifications", "power")
        clock = "HH:mm"                     // java.time patterns: "h:mm a", "HH:mm:ss"
        date = "EEE d MMM"                  // "" for none
        // showPercent = true               // the battery's percent beside it
        // accentColor = "#BF616A"          // and islandColor, textColor: your own colours
        // texts { listOf(cpu(), memory()) }   // shown by the module "texts"
        // style = "solid"                  // the look: "islands", "solid", "floating", "minimal", "macos", "classic"
    }
    // A bar at the bottom too:
    // bottomBar { enabled = true; left = listOf("window"); center = emptyList(); right = listOf("tray") }

    // Or a bar program of your own instead (turn topBar off then; a waybar in newm's look
    // is in bars/waybar of this folder):
    // bar { cmd = "waybar"; visibleNormal = true; visibleFullscreen = false }

    lock {
        w = 0.6; h = 0.6                    // its size: parts of the screen
        cornerRadius = 32.0
        // clock = "HH:mm"
        // layoutOnLock = "first"           // the first keyboard layout while locked ("keep": the one in use; shown either way)
        // onScreenKeyboard = true          // a small keyboard on the lock screen (touch screens)
        // showPanels = true                // panels (waybar, mako) stay drawn while locked (hidden by default)
    }
    launcher {
        w = 0.7; h = 0.7
        cornerRadius = 24.0
        // frequent = 8                     // how many programs you start most are shown first
        // hide = listOf("htop")            // programs left out of it
        // typos = 1                        // letters a word typed may have wrong ("fierfox" finds Firefox; 0: exact only)
        // search = listOf("programs", "actions", "files", "calculator", "command")   // what the search finds
        // commandPrefix = ">"              // "> htop" runs htop
        // pinned = listOf("firefox", "foot")   // always first
        // recent = 4                       // the programs started last, next
        // view = "grid"                    // "list" (newm's), "grid" (pages of icons) or "categories"
        // compact = true                   // smaller rows
    }
}

// Notifications of programs (Telegram's messages, a download done): cards at the top right,
// a history behind the bar's bell.
notifications {
    enabled = true                          // false: leave them to mako or dunst
    seconds = 6                             // how long a card stays
    // doNotDisturb = false                 // true: no cards, only the history
    // quietFrom = "22:00"; quietUntil = "07:00"   // do not disturb by itself every night
    // corner = "top_right"                 // or "top_left", "top", "bottom_right", "bottom_left", "bottom"
    // showInFullscreen = false
}

// What shows on the screen by itself.
osd {
    connections = "cards"                   // Wi-Fi, a cable or Bluetooth coming or going: "cards", "text" or "off"
    // seconds = 1.3                        // how long the volume and brightness slider shows
    // position = "bottom"                  // where: "bottom", "top", "center"
}

// The clipboard's history (Logo+V).
clipboard {
    enabled = true
    size = 50                               // how many are kept (pinned ones on top of these)
    // pictures = true; files = true
    // ignore = listOf("org.keepassxc.KeePassXC")   // programs whose copies are never kept
}

// Screenshots (Logo+Shift+S or Print: choose; Logo+Print: the screen; Alt+Print: the window).
screenshots {
    // folder = "~/Pictures/Screenshots"
    start = "region"                        // the chooser starts choosing a part; W chooses a window
    // rememberKind = false                 // true: it starts with the kind used last
    // clickPicksWindow = false             // true: a click (not a drag) takes the window under it
    // rememberSelection = true             // the area chosen last is there again (Esc clears it)
    // pointer = false                      // the pointer in the picture
    // then = "satty --filename {file}"     // a command run with each file saved
}

// --- Input ----------------------------------------------------------------------------------

keyboard {
    // Unset: the system's layouts (with Alt+Shift switching). Your own:
    // layouts = "us,ru"
    // options = "grp:alt_shift_toggle"     // Alt+Shift switches; "grp:caps_toggle": Caps Lock
    // numLock = true                       // Num Lock on from the start
    // repeatRate = 25; repeatDelay = 600   // held keys: how fast they repeat, and after how long (ms)
}

input {
    tapToClick = true
    naturalScroll = false
    focusFollowsMouse = true                // the window under the mouse gets the keyboard (false: a click)
    // xwayland = true                      // X11 programs
    // pointerSpeed = 0.0                   // -1.0 to 1.0 (Hyprland's sensitivity)
    // accelProfile = "adaptive"            // or "flat": no acceleration
    // touchpad { disableWhileTyping = true; clickFinger = false; scrollFactor = 1.0 }
    // device("epic-mouse-v1") { pointerSpeed = -0.5 }   // one device by its name (libinput list-devices)
}

mouse {
    superMove = "button"                    // Logo + left drag moves a window, Logo + right drag resizes it
    // moveButton = "left"; resizeButton = "right"
}

// cursor { theme = "Adwaita"; size = 24; hideAfter = 5 }   // hideAfter: seconds the mouse is still

// --- Gestures (touchpads) ----------------------------------------------------------------------

gestures {
    // Three fingers move over the wall, four zoom (up and down), five open the launcher,
    // Logo with one finger moves a window and with two resizes it.
    // bind(GestureAction.SWIPE, null, "swipe-3")                    // move over the wall
    // bind(GestureAction.SWIPE_TO_ZOOM, null, "swipe-4")            // up and down: zoom
    // bind(GestureAction.MOVE_RESIZE, "L", "move-1", "swipe-2")     // Logo + fingers: move, resize
    // bind(GestureAction.LAUNCHER, null, "swipe-5")
    hints = true                                                     // what a gesture does, shown at the bottom while it runs
    // lpInertia = 0.8                                               // smoother (and slower) feel
    // Gestures of your own: the way the fingers go first decides; other ways stay newm's own.
    // on("swipe-4", "left", "Next monitor") { moveWorkspace(1) }
    // on("swipe-4", "right", "Previous monitor") { moveWorkspace(-1) }
}

// --- Layout ---------------------------------------------------------------------------------
// newm's wall: tiles on an endless wall, or whole screens of a few windows each.

// Where windows open.
windows {
    newPlacement = "compact"                // next to yours, the wall kept compact, nothing moves; "right-of-focused", "beside-focused", "end", "original" (newm's)
    childPlacement = "below"                // a dialog or a photo viewer: "below" its window, "right", "over" (floating, as newm)
    returnFocusToParent = true              // closing it gives the focus back to its window
    // childBySameApp = true                // a window of the focused program without a parent (Telegram's viewer) is its child
    // childBySameAppExcept = listOf("foot", "kitty", "firefox")   // but not for these: their windows are their own
}

// The wall in whole screens: each screen holds a few windows, the next one opens on the next screen.
screens {
    windowsPerScreen = 2                    // how many windows share a screen (4: as Hyprland's quarters)
    layout = "columns"                      // "rows", "grid" (halves, quarters), "master" (as i3), "dwindle", "monocle"
    // masterRatio = 0.6                    // the master's share ("master")
    // masterSide = "left"                  // "right", "top", "bottom"
    // gaps = 0                             // more room between windows, pixels
    // overflow = "right"                   // where the next screen is: "right" or "down"
    // pullBack = true                      // a window closing: the gap it leaves is closed
    // pullBackMode = "compact"             // a neighbouring screen that fits moves in; "shift": every next screen moves up
    // windowsUnderBar = false              // true: the wall goes on under the bar, as newm
    // cycle = listOf("columns", "rows", "grid", "master", "monocle")   // what Super+Space goes through
    // enabled = false                      // newm's free wall of tiles instead
}

// Resizing windows on screens: only within their screen; zoom out for room.
resize {
    quarters = true                         // the quarter grabbed decides the corner, as Hyprland
    onBorder = true                         // the room beside a window, dragged, resizes it
    // borderGrabSize = 10                  // how far from a window, pixels
    // clampToScreen = true                 // nothing grows past its screen
    // keepAspect = false                   // a corner keeps the window's shape
    // mouseButton = "right"                // with Super held
}

// A small map of the wall in a corner; a click on it goes there.
// minimap {
//     enabled = true
//     corner = "bottom-right"              // top-left, top-right, bottom-left, bottom-right
//     showFor = 2.0                        // seconds after the view moved; 0: always
// }

// The overview (Logo alone): every window at once.
overview {
    input = false                           // true: the windows take clicks there
    choose = true                           // a click chooses a window and ends the overview
    followPointer = true                    // the window under the pointer is the chosen one
    drag = true                             // a window dragged moves, onto another monitor too
}

// --- Window rules ---------------------------------------------------------------------------
view {
    // Rules for some windows, decided when they open (their app id: `newm cmd debug` lists them).
    rules { view ->
        when (view.appId) {
            "pavucontrol", "mpv", "imv", "org.gnome.Calculator" -> rule(float = true)
            // See-through terminals with what is behind them blurred (set the terminal's own
            // opacity too, for alacritty that is window.opacity = 0.85).
            "Alacritty", "foot", "kitty", "org.wezfurlong.wezterm" -> rule(blur = Blur(radius = 6, passes = 4))
            // "org.keepassxc.KeePassXC" -> rule(float = true, floatSize = 900 to 600)
            // "firefox" -> rule(opacity = 0.97)
            else -> null
        }
    }
}

// Caulis' rules for how windows behave (Hyprland's windowrule): stay awake, ignore requests, ...
// windowRule { appId("mpv"); keepAwake = true }                              // the screen stays on (idle_inhibit)
// windowRule { appId(Regex(".*")); ignore(WindowRequest.MAXIMIZE) }       // suppress_event maximize
// windowRule { title("Firefox — Sharing Indicator"); hiddenFromCapture = true }   // not in screen sharing
// layerRule { namespace("fuzzel"); blur = true; dimAround = 0.4f }        // panels and launchers (Hyprland's layerrule)

// --- Workspaces -----------------------------------------------------------------------------
// newm has one wall per monitor instead of numbered workspaces (Hyprland's workspace rules:
// docs/en/hyprland.md).

// Special workspaces (Hyprland's "special"): a layer above the wall with windows of its own,
// shown with the keys below (Logo+grave).
specialWorkspace {
    dim = 0.4                               // the wall behind darker
    // blurRadius = 8                       // and blurred (0: not)
    gap = 40                                // room to the screen's edges
    // size = 1.0                           // how much of the screen
    // hideWhenEmpty = true
    // newWindowsJoin = true                // windows opening while it is shown go into it
    // clickOutsideHides = true
    // cornerRadius = 12.0                  // its windows' corners (else as view { cornerRadius })
    // padding = 6.0                        // room around each of its windows (else as view { padding })
}
// A program that always opens there, hidden until shown (Super+M below):
// windowRule { appId("spotify"); specialWorkspace = "music" }

// --- Permissions ----------------------------------------------------------------------------
// Which programs may take pictures of the screen, see the window list, read the clipboard, show
// panels, lock, arrange monitors (as Hyprland's permissions). Nothing said: every program may.
// A program is its path or its name (regular expressions); "ask" shows a card (screencopy only).
permissions {
    // rule("/usr/bin/grim", "screencopy", "allow")
    // rule("/usr/(lib|libexec)/xdg-desktop-portal-wlr", "screencopy", "allow")
    // rule(".*", "clipboard", "deny")      // features: screencopy, window-list, clipboard, panels, lock, monitors, gamma
    // default("screencopy", "ask")         // programs no rule is about (Hyprland's enforce_permissions)
}

// --- Misc -----------------------------------------------------------------------------------
misc {
    // swallow = true                       // a window opened from a terminal takes its place (enable_swallow)
    // swallowApps = "foot|kitty|Alacritty"  // the terminals that do (a regular expression for the app id)
    // swallowExceptTitles = "wev"          // not when the terminal's title matches
    // focusOnActivate = true               // a window asking for the focus gets it (false: only marked)
    // wakeOnInput = true                   // keys and the mouse switch dark screens on again
    // vrr = false                          // variable refresh rate (FreeSync) on every monitor that has it
    // middleClickPaste = true              // paste what is selected with the middle button (at the next start)
    // backgroundColor = "#1e1e2e"          // under everything, where no wallpaper is
}

energy {
    idleTimes = listOf(120, 300, 600)       // seconds without input: dim, lock, suspend
    screensOffAfter = 330                   // and the screens go dark (0: never)
    // suspendCommand = "systemctl suspend"
    // after(1800) { ensureLocked() }       // something of your own after so many seconds
    // inhibitFrom = listOf("wayland", "dbus", "logind", "mpris")   // what keeps the screen on (videos): all by default
}
// lockOnWakeup = true

// --- Keybindings ----------------------------------------------------------------------------
// A key in one line: bind("Super+Backspace", "close-window"). Keys are written as you say them:
// Super (also Logo, Win), Ctrl, Alt, Shift and a key: a letter, a digit, Return, Space, Tab,
// Escape, Backspace, Delete, Left, F1, Print and so on (case does not matter). "Super" alone is
// Super tapped. Every action's name: docs/en/keys.md; `newm cmd keys` lists the keys bound now,
// Logo+/ shows them. unbind("Super+Q") takes a key away. newm's own way works too, with any of
// newm's functions inside { }: see the last lines of this block.

keys {
    // Programs
    bind("Super+Return",          "launch", terminal, description = "Open a terminal")
    bind("Super+D",               "launcher")                  // find and start a program
    // bind("Super+D",            "launch", menu)              // your own launcher instead
    bind("Super+E",               "launch", fileManager, description = "Files")
    bind("Super+B",               "launch", browser, description = "Web browser")

    // Going around: the window beside
    bind("Super+H",               "focus-left")
    bind("Super+J",               "focus-down")
    bind("Super+K",               "focus-up")
    bind("Super+L",               "focus-right")
    bind("Super+Left",            "focus-left")
    bind("Super+Down",            "focus-down")
    bind("Super+Up",              "focus-up")
    bind("Super+Right",           "focus-right")
    bind("Super+Tab",             "next-window")
    bind("Super+Shift+Tab",       "previous-window")
    bind("Super+T",               "next-in-stack")             // windows on the same tiles

    // Seeing more or less of the wall
    bind("Super",                 "overview")                  // Super tapped alone: all windows
    bind("Super+Minus",           "zoom-out")                  // see more
    bind("Super+Equal",           "zoom-in")                   // see fewer
    bind("Super+U",               "zoom-out")
    bind("Super+N",               "zoom-in")
    bind("Super+0",               "fit-window")                // the window fills the screen
    bind("Super+S",               "side-by-side")              // two windows side by side, and back

    // Moving a window on the wall
    bind("Super+Shift+H",         "move-window-left")
    bind("Super+Shift+J",         "move-window-down")
    bind("Super+Shift+K",         "move-window-up")
    bind("Super+Shift+L",         "move-window-right")
    bind("Super+Shift+Left",      "move-window-left")
    bind("Super+Shift+Down",      "move-window-down")
    bind("Super+Shift+Up",        "move-window-up")
    bind("Super+Shift+Right",     "move-window-right")

    // Screens: a few windows each
    bind("Super+Space",           "next-layout")               // the screen's next layout
    bind("Super+Shift+Space",     "swap-on-screen")            // the window changes places with the next one
    bind("Super+Ctrl+Return",     "promote-to-master")         // the window becomes the screen's master
    bind("Super+PageDown",        "next-screen")
    bind("Super+PageUp",          "previous-screen")
    bind("Super+Shift+PageDown",  "window-to-next-screen")
    bind("Super+Shift+PageUp",    "window-to-previous-screen")

    // Its size (on screens: within its screen; else in tiles, one tile: a screen at zoom 1)
    bind("Super+Ctrl+H",          "window-narrower")
    bind("Super+Ctrl+L",          "window-wider")
    bind("Super+Ctrl+K",          "window-shorter")
    bind("Super+Ctrl+J",          "window-taller")
    bind("Super+Ctrl+Left",       "window-narrower")
    bind("Super+Ctrl+Right",      "window-wider")
    bind("Super+Ctrl+Up",         "window-shorter")
    bind("Super+Ctrl+Down",       "window-taller")

    // Windows
    bind("Super+Q",               "close-window")
    // bind("Super+Backspace",    "close-window")              // close with Super+Backspace too
    bind("Super+F",               "fullscreen")
    bind("Super+V",               "clipboard")                 // the clipboard's history
    bind("Super+Shift+V",         "float")                     // the window floats, and back
    // bind("Super+W",            "swallow")                   // the terminal hides behind what it started

    // Monitors: each has a wall of its own
    bind("Super+Alt+Left",        "focus-monitor-left")
    bind("Super+Alt+Right",       "focus-monitor-right")
    bind("Super+Alt+Up",          "focus-monitor-up")
    bind("Super+Alt+Down",        "focus-monitor-down")
    bind("Super+Shift+Ctrl+Left", "window-to-monitor-left")    // it keeps its size
    bind("Super+Shift+Ctrl+Right", "window-to-monitor-right")
    bind("Super+Shift+Ctrl+Up",   "window-to-monitor-up")
    bind("Super+Shift+Ctrl+Down", "window-to-monitor-down")
    // bind("Super+Period",       "next-monitor")
    // bind("Super+Shift+Period", "window-to-next-monitor")

    // Special workspaces: a layer above the wall (see specialWorkspace above)
    bind("Super+Grave",           "special-workspace")
    bind("Super+Shift+Grave",     "to-special-workspace")
    bind("Super+Ctrl+Grave",      "from-special-workspace")
    // bind("Super+M",            "special-workspace", "music")
    // bind("Super+Shift+M",      "to-special-workspace", "music")

    // The session
    bind("Super+Slash",           "key-help")                  // these keys
    bind("Super+P",               "lock")
    bind("Super+Shift+C",         "reload")                    // load this file again
    bind("Super+Shift+Escape",    "logout")

    // The laptop's keys (they work on the lock screen too)
    bind("BrightnessUp",          "caulis", "brightness +5", description = "Brighter")
    bind("BrightnessDown",        "caulis", "brightness -5", description = "Darker")
    bind("VolumeUp",              "caulis", "volume +5", description = "Louder")
    bind("VolumeDown",            "caulis", "volume -5", description = "Quieter")
    bind("Mute",                  "caulis", "volume mute", description = "Sound off, and on")
    bind("MicMute",               "caulis", "mic-mute", description = "Microphone off, and on")

    // Screenshots, as Windows has them
    bind("Super+Shift+S",         "screenshot")                // choose a part, a window or the screen
    bind("Print",                 "screenshot")
    bind("Super+Print",           "screenshot-screen")
    bind("Alt+Print",             "screenshot-window")

    // A Hyprland-like set, ready to use: remove the // in front of the lines you want.
    // bind("Super+C",            "close-window")
    // bind("Super+Backspace",    "close-window")
    // bind("Super+R",            "launcher")
    // bind("Super+1",            "go-to-window", "1")         // the first window, and so on
    // bind("Super+2",            "go-to-window", "2")
    // bind("Super+3",            "go-to-window", "3")
    // bind("Super+4",            "go-to-window", "4")
    // bind("Super+5",            "go-to-window", "5")
    // bind("Super+6",            "go-to-window", "6")
    // bind("Super+7",            "go-to-window", "7")
    // bind("Super+8",            "go-to-window", "8")
    // bind("Super+9",            "go-to-window", "9")
    // bind("Super+M",            "logout")
    // unbind("Super+S")                                       // a key of newm's taken away

    // Or with code of your own inside { }:
    // "Super+Shift+Return"("A terminal in my projects") { launch("foot -D ~/projects") }
}
