package newm

/**
 * newm's actions by name, for a key in one line: `bind("Super+Backspace", "close-window")`,
 * `bind("Super+Return", "launch", "foot")` — and the same names in config.json and config.lua
 * (`"Super+Backspace": "close-window"`, `"Super+Return": "launch foot"`). Each with its words
 * for the keys' help (Logo+/). Names not here are newm's layout methods ("move -1 0") and
 * Caulis's actions ("volume +5") as before.
 */
object KeyActions {

    /** One action: its [name], what it takes ([args], in words), what it does ([description]) and how. */
    class Action(val name: String, val args: String, val description: String, val run: Layout.(List<String>) -> Unit)

    private fun int(args: List<String>, i: Int, default: Int): Int = args.getOrNull(i)?.toIntOrNull() ?: default

    /** Every action, in the order the guide lists them. */
    val ALL: List<Action> = listOf(
        Action("close-window", "", "Close the window") { closeFocusedView() },
        Action("terminal", "", "Open a terminal") { launch(defaultTerminal()) },
        Action("launch", "command", "Start a program") { a -> if (a.isNotEmpty()) launch(a.joinToString(" ")) },
        Action("launcher", "", "Find and start a program") { enterLauncherOverlay() },
        Action("focus-left", "", "Window to the left") { move(-1, 0) },
        Action("focus-right", "", "Window to the right") { move(1, 0) },
        Action("focus-up", "", "Window above") { move(0, -1) },
        Action("focus-down", "", "Window below") { move(0, 1) },
        Action("next-window", "", "Next window") { cycleViews(1) },
        Action("previous-window", "", "Previous window") { cycleViews(-1) },
        Action("go-to-window", "number", "Window number n") { a -> gotoView(int(a, 0, 1) - 1) },
        Action("next-in-stack", "", "Next window of a stack") { moveInStack(1) },
        Action("overview", "", "All windows") { toggleOverview() },
        Action("zoom-out", "", "Zoom out (see more)") { basicScale(1) },
        Action("zoom-in", "", "Zoom in (see fewer)") { basicScale(-1) },
        Action("fit-window", "", "Fit the window to the screen") { fitFocusedView() },
        Action("side-by-side", "", "Two windows side by side, and back") { sideBySide() },
        Action("move-window-left", "", "Move the window left") { moveFocusedView(-1, 0) },
        Action("move-window-right", "", "Move the window right") { moveFocusedView(1, 0) },
        Action("move-window-up", "", "Move the window up") { moveFocusedView(0, -1) },
        Action("move-window-down", "", "Move the window down") { moveFocusedView(0, 1) },
        Action("next-screen", "", "Next screen") { focusScreen(1) },
        Action("previous-screen", "", "Previous screen") { focusScreen(-1) },
        Action("window-to-next-screen", "", "Move the window onto the next screen") { moveFocusedViewToScreen(1) },
        Action("window-to-previous-screen", "", "Move the window onto the previous screen") { moveFocusedViewToScreen(-1) },
        Action("swap-on-screen", "", "Swap the window with the next one on its screen") { swapOnScreen(1) },
        Action("promote-to-master", "", "Make the window the screen's master") { promoteToMaster() },
        Action("next-layout", "[name]", "Next layout of the screen") { a -> nextScreenLayout(a.firstOrNull()) },
        Action("window-narrower", "", "Window narrower") { resizeFocusedView(-1, 0) },
        Action("window-wider", "", "Window wider") { resizeFocusedView(1, 0) },
        Action("window-shorter", "", "Window shorter") { resizeFocusedView(0, -1) },
        Action("window-taller", "", "Window taller") { resizeFocusedView(0, 1) },
        Action("fullscreen", "", "Fullscreen, and back") { toggleFullscreen() },
        Action("float", "", "Float, and back") { toggleFocusedViewFloating() },
        Action("swallow", "", "Swallow the window into the terminal that started it") { swallowFocusedView() },
        Action("next-monitor", "", "Next monitor") { moveWorkspace(1) },
        Action("previous-monitor", "", "Previous monitor") { moveWorkspace(-1) },
        Action("window-to-next-monitor", "", "Window onto the next monitor") { changeFocusedViewWorkspace(1) },
        Action("window-to-previous-monitor", "", "Window onto the previous monitor") { changeFocusedViewWorkspace(-1) },
        Action("special-workspace", "[name]", "Special workspace, and back") { a -> toggleSpecialWorkspace(a.firstOrNull() ?: "special") },
        Action("to-special-workspace", "[name]", "Window into the special workspace") { a -> moveToSpecialWorkspace(a.firstOrNull() ?: "special") },
        Action("from-special-workspace", "", "Window back onto the wall") { moveOutOfSpecialWorkspace() },
        Action("clipboard", "", "Clipboard history") { toggleClipboard() },
        Action("screenshot", "", "Screenshot: choose") { screenshot() },
        Action("screenshot-screen", "", "Screenshot of the screen") { screenshotScreen() },
        Action("screenshot-window", "", "Screenshot of the window") { screenshotWindow() },
        Action("key-help", "", "These keys") { toggleKeyHelp() },
        Action("lock", "", "Lock the screen") { ensureLocked(dim = true) },
        Action("reload", "", "Reload this configuration") { updateConfig() },
        Action("logout", "", "Log out") { terminate() },
        Action("caulis", "action", "An action of Caulis (volume +5, brightness -5, mic-mute)") { a -> caulis.run(a.joinToString(" ")) },
    )

    private val BY_NAME = ALL.associateBy { it.name }

    /** The action called [name] (case, "_" and "-" do not matter: closeWindow, close_window), or null. */
    fun byName(name: String): Action? = BY_NAME[name.trim().replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase().replace('_', '-')]

    /** Every name, for a mistake's words. */
    fun names(): String = ALL.joinToString(", ") { it.name }
}
