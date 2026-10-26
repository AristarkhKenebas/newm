package newm

/**
 * newm's default_config.py: what newm does without a configuration of your own — its keys,
 * the lock screen and launcher panels, brightness and volume with a bar shown by wob.
 */
fun defaultConfig(layout: Layout): Map<String, Any?> {
    val wobRunner = WobRunner("wob -a bottom -M 100") { command -> layout.caulis.spawn(command) }
    val backlightManager = BacklightManager(animTime = 1.0, barDisplay = wobRunner)
    val kbdlightManager = BacklightManager(args = "--device='*::kbd_backlight'", animTime = 1.0, barDisplay = wobRunner)
    val pactl = PaCtl(0, wobRunner)

    val keyBindings: (Layout) -> List<Pair<String, () -> Unit>> = { l ->
        listOf(
            "L-h" to { l.move(-1, 0) },
            "L-j" to { l.move(0, 1) },
            "L-k" to { l.move(0, -1) },
            "L-l" to { l.move(1, 0) },
            "L-u" to { l.basicScale(1) },
            "L-n" to { l.basicScale(-1) },
            "L-t" to { l.moveInStack(1) },

            "L-H" to { l.moveFocusedView(-1, 0) },
            "L-J" to { l.moveFocusedView(0, 1) },
            "L-K" to { l.moveFocusedView(0, -1) },
            "L-L" to { l.moveFocusedView(1, 0) },

            "L-C-h" to { l.resizeFocusedView(-1, 0) },
            "L-C-j" to { l.resizeFocusedView(0, 1) },
            "L-C-k" to { l.resizeFocusedView(0, -1) },
            "L-C-l" to { l.resizeFocusedView(1, 0) },

            "L-Return" to { l.caulis.launch("alacritty") },
            "L-q" to { l.closeFocusedView() },

            "L-p" to { l.ensureLocked(dim = true) },
            "L-P" to { l.terminate() },
            "L-C" to { l.updateConfig() },

            "L-f" to { l.toggleFullscreen() },

            "L-" to { l.toggleOverview() },

            "XF86MonBrightnessUp" to { backlightManager.set(backlightManager.get() + 0.1) },
            "XF86MonBrightnessDown" to { backlightManager.set(backlightManager.get() - 0.1) },
            "XF86KbdBrightnessUp" to { kbdlightManager.set(kbdlightManager.get() + 0.1) },
            "XF86KbdBrightnessDown" to { kbdlightManager.set(kbdlightManager.get() - 0.1) },
            "XF86AudioRaiseVolume" to { pactl.volumeAdj(5) },
            "XF86AudioLowerVolume" to { pactl.volumeAdj(-5) },
            "XF86AudioMute" to { pactl.mute() },
        )
    }

    // (Typed: a lambda as a map's value would not know its parameters.)
    val synchronousUpdate: () -> Unit = {
        backlightManager.update()
        kbdlightManager.update()
    }
    val topBarTexts: () -> List<String> = {
        listOf(System.getProperty("user.name"), java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy")))
    }
    val bottomBarTexts: () -> List<String> = { listOf("newm", "powered by Caulis") }
    val idleCallback: (String) -> Unit = { code -> backlightManager.callback(code) }

    return mapOf(
        "background" to mapOf("path" to "resource:wallpaper.jpg", "anim" to true),
        "synchronous_update" to synchronousUpdate,
        "key_bindings" to keyBindings,
        "panels" to mapOf(
            "lock" to mapOf("cmd" to "alacritty -e newm-panel-basic lock"),
            "launcher" to mapOf("cmd" to "alacritty -e newm-panel-basic launcher"),
            "top_bar" to mapOf(
                "native" to mapOf(
                    "enabled" to true,
                    "texts" to topBarTexts,
                ),
            ),
            "bottom_bar" to mapOf(
                "native" to mapOf(
                    "enabled" to true,
                    "texts" to bottomBarTexts,
                ),
            ),
        ),
        "energy" to mapOf("idle_callback" to idleCallback),
    )
}
