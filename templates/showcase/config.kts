// newm — the showcase: everything set up to the max, in newm's own "Jadefire" look.
//
// A starting point to copy and adjust: move this file to ~/.config/newm/config.kts (keep your
// own as config.kts.bak) and save. Every line is a setting of newm's; the notes say what it does
// and what else it can be. The full list: docs/en/settings.md, and docs/en/showcase.md shows it.
//
// What it turns on: a moving wallpaper that watches the pointer, newm's Jadefire colours on the
// bar, the menus, the cards and the lock screen, a glowing focus border, rich animations with
// curves and springs, blur and round corners, a special workspace for music, screens of two
// windows, a fuller bar, a sound and a fade at the start, and programs started with the session.

// --- Your programs --------------------------------------------------------------------------

val terminal = defaultTerminal()             // $TERMINAL, else the first installed: alacritty, foot, kitty, ...
val fileManager = "nautilus"
val browser = "firefox"
val music = "spotify"                        // lives in the special workspace "music" (Super+M)

// --- The look: Jadefire ---------------------------------------------------------------------
// newm's own look: jade and amber on near-black green, an ember red for what is urgent. It sets
// the colours (bar, menus, cards, lock screen), the focus border's shader and the wallpaper —
// each of them can still be set below, and what is set here wins.
theme = "jadefire"
colorScheme = "dark"                         // Jadefire is made for the dark; "auto" follows the quick settings
cornerRadius = 20                            // the screen's own corners rounded

// A moving wallpaper drawn by the GPU: a dragon's eye whose slit pupil follows the pointer.
// Others of newm's: "ember-sky", "scales" (ripples from clicks), "deep-forest", "molten";
// or a GLSL file of your own next to this one ("my-wallpaper.glsl", read again when saved).
background {
    shader = "dragon-eye"
    mode = "follow"                          // it moves and zooms with the view, as a picture does
    fps = 30                                 // frames a second while it is seen (never under a fullscreen window)
    fpsOnBattery = 15                        // fewer on battery
    pauseAfter = 120                         // it stands still after two minutes without input
    // resolution = 0.5                      // half the pixels each way: a quarter of the GPU's work (4K: by itself)
    speed = 1.0                              // its pace
    intensity = 1.0                          // how strongly it glows
    shaderValues = mapOf("eyeSize" to 0.38, "blink" to 1.0)   // the eye's size; blink now and then (0: never)
    // shaderColors = listOf("#3ddc97", "#f2b33d", "#e8552d", "#060908")   // jade, amber, ember, the dark
}

// Windows: gaps, round corners, a little see-through when not focused.
view {
    cornerRadius = 16.0
    padding = 10.0
    unfocusedOpacity = 0.94                  // windows without the focus a touch see-through
    fade = true                              // windows fade in and out
}

// The focus border: the theme's shader — jade flowing into amber, a spark running round,
// an urgent window pulsing ember. A plain gradient would be: color = "rgba(3ddc97ff) rgba(f2b33dff) 45deg".
focus {
    enabled = true
    distance = 5.0                           // how far outside the window
    width = 3.0
    animateOnChange = true                   // the border glides to the next window
    urgentColor = "rgb(e8552d)"              // a window wanting attention
    fps = 30                                 // its flow drawn 30 times a second: half the work on a 60 Hz screen
    // shader = "jadefire"                   // set by the theme; or "border.glsl" of your own
}

// --- Animations: curves and springs -----------------------------------------------------------
animations {
    preset = "newm"                          // newm's usual set, all on
    millis = 320                             // the global time
    curve("jade", 0.05, 0.9, 0.1, 1.05)      // a Bézier curve of your own (Hyprland's numbers): quick, a hair past
    spring("ember", stiffness = 260.0, damping = 18.0)   // a spring: settles with a soft bounce
    curve = "jade"                           // the global curve
    animation("windows", millis = 360, curve = "ember")   // windows spring into their places
    animation("windowsOut", millis = 220, curve = "ease-in-out")
    animation("camera", millis = 380, curve = "jade")      // the view gliding over the wall
    animation("overview", millis = 420, curve = "ember")
    animation("border", millis = 260, curve = "jade")      // the focus border's glide
    animation("popupsIn", millis = 260, style = "popin 92%")
    animation("popupsOut", millis = 160, style = "fade")
    animation("notificationsIn", style = "slide down 12")
    animation("launcher", millis = 300, curve = "ember")
    animation("backgroundIn", millis = 900)  // the wallpaper fading in the first time
}

// --- Start: dark, then a fade, with a sound ------------------------------------------------------
reveal {
    enabled = true
    fadeMillis = 900
    color = "#060908"                        // Jadefire's abyss until newm is ready
}
startupSound {
    enabled = true
    preset = "glass"                         // "airy", "classic", "glass", "soft", or file = "~/sounds/hello.ogg"
    volume = 0.6
    at = "reveal"                            // as newm fades in
}

// Programs started with the session, one after another once newm shows.
autostart {
    // app("nm-applet", "--indicator") { delay = 2.seconds; ifNotRunning = true; restart = OnCrash(maxTimes = 3, within = 1.minutes) }
    // app("telegram-desktop", "-startintray") { delay = 3.seconds }
    // app(music) { delay = 4.seconds }      // the music player, straight into its special workspace
    // shell("sleep 2; notify-send 'Welcome back'")
    desktopEntries = true                    // also the programs of ~/.config/autostart
}

// --- The bar: islands in jade ----------------------------------------------------------------------
panels {
    topBar {
        enabled = true
        height = 34
        margin = 8
        cornerRadius = 14
        fontSize = 13
        font = "Inter"                       // any installed font; unset: the system's
        blur = true                          // what is behind the islands blurred
        left = listOf("menu", "wall", "window")   // the launcher's button, a small map of your wall, the window
        center = listOf("clock")
        right = listOf("tray", "load", "layout", "bluetooth", "network", "volume", "battery", "notifications", "power")
        clock = "HH:mm"
        date = "EEE d MMM"
        showPercent = true
        // A module of your own: the processor's load, every 5 seconds; a click opens btop.
        module("load") {
            text { "%.1f".format(java.lang.management.ManagementFactory.getOperatingSystemMXBean().systemLoadAverage) }
            everySeconds = 5
            icon = "gauge"
            onClick { launch("$terminal -e btop") }
        }
    }

    // The lock screen: Jadefire's colours (set by the theme too), a big clock.
    lock {
        w = 0.62; h = 0.62
        cornerRadius = 36.0
        clock = "HH:mm"
        date = "EEEE, d MMMM"
        background = "#0b110eF2"
        text = "#e8f0ea"
        accent = "#3ddc97"
    }

    // The launcher (Super+D): your programs, those you start most first.
    launcher {
        w = 0.62; h = 0.66
        cornerRadius = 28.0
        frequent = 8
    }
}

notifications {
    enabled = true
    seconds = 6
    corner = "top_right"
}

osd {
    connections = "cards"                    // Wi-Fi, a cable or Bluetooth coming or going: a card
    position = "bottom"
}

clipboard { enabled = true; size = 80 }

// A cursor theme that suits Jadefire (if installed): Bibata Modern Classic or Ice.
// cursor { theme = "Bibata-Modern-Classic"; size = 24; hideAfter = 5 }

// --- Layout: screens of two windows ---------------------------------------------------------------
windows {
    newPlacement = "compact"
    childPlacement = "below"
}
screens {
    windowsPerScreen = 2
    layout = "columns"                       // "rows", "grid", "master", "dwindle", "monocle"
    cycle = listOf("columns", "master", "grid", "monocle")   // what Super+Space goes through
    masterRatio = 0.6
}

// --- Special workspace: music above the wall -----------------------------------------------------
specialWorkspace {
    dim = 0.45                               // the wall behind darker
    blurRadius = 8                           // and blurred
    gap = 48
    size = 0.92
}
windowRule { appId(music); specialWorkspace = "music" }   // the player always opens there, hidden until Super+M

// --- Window rules: floating tools, see-through terminals with blur -----------------------------------
view {
    rules { view ->
        when (view.appId) {
            "pavucontrol", "mpv", "imv", "org.gnome.Calculator" -> rule(float = true)
            "Alacritty", "foot", "kitty", "org.wezfurlong.wezterm" -> rule(blur = Blur(radius = 8, passes = 4))
            else -> null
        }
    }
}

// --- Input and gestures -----------------------------------------------------------------------------
input {
    tapToClick = true
    naturalScroll = true
    focusFollowsMouse = true
}
gestures {
    hints = true                             // what a gesture does, shown at the bottom while it runs
    on("swipe-4", "left", "Next monitor") { moveWorkspace(1) }
    on("swipe-4", "right", "Previous monitor") { moveWorkspace(-1) }
}

energy {
    idleTimes = listOf(180, 420, 900)        // seconds without input: dim, lock, suspend
    screensOffAfter = 450
}

// --- Keys ------------------------------------------------------------------------------------------
keys {
    bind("Super+Return",          "launch", terminal, description = "Open a terminal")
    bind("Super+D",               "launcher")
    bind("Super+E",               "launch", fileManager, description = "Files")
    bind("Super+B",               "launch", browser, description = "Web browser")

    bind("Super+H",               "focus-left")
    bind("Super+J",               "focus-down")
    bind("Super+K",               "focus-up")
    bind("Super+L",               "focus-right")
    bind("Super+Shift+H",         "move-window-left")
    bind("Super+Shift+J",         "move-window-down")
    bind("Super+Shift+K",         "move-window-up")
    bind("Super+Shift+L",         "move-window-right")
    bind("Super+Tab",             "next-window")

    bind("Super",                 "overview")                  // Super tapped alone: all windows
    bind("Super+U",               "zoom-out")
    bind("Super+N",               "zoom-in")
    bind("Super+0",               "fit-window")
    bind("Super+S",               "side-by-side")

    bind("Super+Space",           "next-layout")               // the screen's next layout (cycle above)
    bind("Super+PageDown",        "next-screen")
    bind("Super+PageUp",          "previous-screen")

    bind("Super+Q",               "close-window")
    bind("Super+F",               "fullscreen")
    bind("Super+V",               "clipboard")
    bind("Super+Shift+V",         "float")

    bind("Super+M",               "special-workspace", "music")   // the music, and back
    bind("Super+Shift+M",         "to-special-workspace", "music")
    bind("Super+Grave",           "special-workspace")
    bind("Super+Shift+Grave",     "to-special-workspace")

    bind("Super+Slash",           "key-help")
    bind("Super+P",               "lock")
    bind("Super+Shift+C",         "reload")
    bind("Super+Shift+Escape",    "logout")

    bind("Super+Shift+S",         "screenshot")
    bind("Print",                 "screenshot")

    bind("BrightnessUp",          "caulis", "brightness +5", description = "Brighter")
    bind("BrightnessDown",        "caulis", "brightness -5", description = "Darker")
    bind("VolumeUp",              "caulis", "volume +5", description = "Louder")
    bind("VolumeDown",            "caulis", "volume -5", description = "Quieter")
    bind("Mute",                  "caulis", "volume mute", description = "Sound off, and on")

    // A key with code of your own inside { }: any of newm's functions, any Kotlin.
    "Super+Ctrl+M"("Music: show it, start the player if it is not running") {
        toggleSpecialWorkspace("music")
        if (ProcessHandle.allProcesses().noneMatch { it.info().command().orElse("").endsWith("/$music") }) launch(music)
    }
}
