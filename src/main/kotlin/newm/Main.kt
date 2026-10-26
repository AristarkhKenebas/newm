package newm

import dev.caulis.wm.caulis

/**
 * newm on Caulis: the compositor with newm's layout and its default configuration;
 * `newm cmd <command>` talks to a running one instead (newm-cmd).
 */
fun main(args: Array<String>) {
    if (args.firstOrNull() == "cmd") return newmCmd(args.drop(1))
    caulis {
        configName = "newm"
        desktopName = "newm"
        val layout = Layout(this)
        Config.loadConfig(defaultConfig(layout))
    }
}
