package newm

import dev.caulis.log.Logger

private val log = Logger("config")

/**
 * What newm does without a configuration of its own and without its shipped config.kts (a
 * build without the templates, or one that failed to compile): newm's own lock screen,
 * launcher, bar, volume and brightness slider (no wob, pactl or newm-panel-basic needed) and
 * the keys of the starting config.kts, each with its words for the keys' help.
 */
fun defaultConfig(layout: Layout): Map<String, Any?> {
    log.warn("No configuration and no shipped config.kts: newm's built-in defaults")
    val keys: List<Triple<String, String, Layout.() -> Unit>> = listOf(
        Triple("L-Return", "Open a terminal") { caulis.launch(defaultTerminal()) },
        Triple("L-d", "Find and start a program") { enterLauncherOverlay() },

        Triple("L-h", "Window to the left") { move(-1, 0) },
        Triple("L-j", "Window below") { move(0, 1) },
        Triple("L-k", "Window above") { move(0, -1) },
        Triple("L-l", "Window to the right") { move(1, 0) },
        Triple("L-Tab", "Next window") { cycleViews(1) },
        Triple("L-t", "Next window of a stack") { moveInStack(1) },

        Triple("L-", "All windows (tap Super alone)") { toggleOverview() },
        Triple("L-u", "Zoom out") { basicScale(1) },
        Triple("L-n", "Zoom in") { basicScale(-1) },
        Triple("L-s", "Two windows side by side, and back") { sideBySide() },

        Triple("L-S-h", "Move the window left") { moveFocusedView(-1, 0) },
        Triple("L-S-j", "Move the window down") { moveFocusedView(0, 1) },
        Triple("L-S-k", "Move the window up") { moveFocusedView(0, -1) },
        Triple("L-S-l", "Move the window right") { moveFocusedView(1, 0) },

        Triple("L-space", "Next layout of the screen") { nextScreenLayout() },
        Triple("L-S-space", "Swap the window with the next one on its screen") { swapOnScreen(1) },
        Triple("L-C-Return", "Make the window the screen's master") { promoteToMaster() },
        Triple("L-Next", "Next screen") { focusScreen(1) },
        Triple("L-Prior", "Previous screen") { focusScreen(-1) },
        Triple("L-S-Next", "Move the window onto the next screen") { moveFocusedViewToScreen(1) },
        Triple("L-S-Prior", "Move the window onto the previous screen") { moveFocusedViewToScreen(-1) },

        Triple("L-C-h", "Window narrower") { resizeFocusedView(-1, 0) },
        Triple("L-C-l", "Window wider") { resizeFocusedView(1, 0) },
        Triple("L-C-k", "Window shorter") { resizeFocusedView(0, -1) },
        Triple("L-C-j", "Window taller") { resizeFocusedView(0, 1) },

        Triple("L-q", "Close the window") { closeFocusedView() },
        Triple("L-f", "Fullscreen, and back") { toggleFullscreen() },
        Triple("L-v", "Clipboard history") { toggleClipboard() },
        Triple("L-S-v", "Float, and back") { toggleFocusedViewFloating() },

        Triple("L-slash", "These keys") { toggleKeyHelp() },
        Triple("L-p", "Lock the screen") { ensureLocked(dim = true) },
        Triple("L-C", "Reload this configuration") { updateConfig() },
        Triple("L-S-Escape", "Log out") { terminate() },

        // newm's own slider shows the volume and the brightness (Caulis' actions).
        Triple("XF86MonBrightnessUp", "Brighter") { caulis.run("brightness +5") },
        Triple("XF86MonBrightnessDown", "Darker") { caulis.run("brightness -5") },
        Triple("XF86AudioRaiseVolume", "Louder") { caulis.run("volume +5") },
        Triple("XF86AudioLowerVolume", "Quieter") { caulis.run("volume -5") },
        Triple("XF86AudioMute", "Sound off, and on") { caulis.run("volume mute") },
        Triple("XF86AudioMicMute", "Microphone off, and on") { caulis.run("mic-mute") },

        Triple("Print", "Screenshot: choose") { screenshot() },
        Triple("L-S-s", "Screenshot: choose a part") { screenshot() },
    )
    val keyBindings: (Layout) -> List<Pair<String, () -> Unit>> = { l -> keys.map { (combo, _, action) -> combo to { l.action() } } }

    return mapOf(
        "background" to mapOf("path" to "resource:wallpaper.jpg", "anim" to true),
        "key_bindings" to keyBindings,
        "key_help" to keys.map { (combo, words, _) -> combo to words },
        // newm's own lock screen and launcher (no panels.lock.cmd / panels.launcher.cmd), and its bar.
        "panels" to mapOf("top_bar" to mapOf("native" to mapOf("enabled" to true))),
    )
}
