package newm

import dev.caulis.wm.caulis

/**
 * newm on Caulis: the compositor with newm's layout, configured by a config.py as newm was
 * (~/.config/newm/config.py, else /etc/newm/config.py, else newm's default configuration),
 * loaded again whenever it is saved. `newm cmd <command>` talks to a running one instead.
 */
fun main(args: Array<String>) {
    if (args.firstOrNull() == "cmd") return newmCmd(args.drop(1))
    caulis {
        configName = "newm"
        desktopName = "newm"
        lateinit var layout: Layout
        val config = PyConfig.path()?.let { NewmConfig(this, it, DeferredApi { LayoutApi(layout) }) }
        // Before newm's own start (on_startup): programs config.py starts run inside newm.
        config?.let { c -> onStart { c.started() } }
        layout = Layout(this)

        val settings = config?.load() ?: defaultConfig(layout)
        Config.loadConfig(settings)
        applyHostSettings(settings)

        // update_config, and every save of config.py: on a mistake the settings stay as they were.
        layout.reloadConfig = {
            config?.load()?.let { next ->
                Config.loadConfig(next)
                applyHostSettings(next)
            }
        }
        config?.reload = { layout.updateConfig() }
        config?.let { c -> onUnload { c.close() } }
    }
}

// The pywm and outputs sections, as Caulis's settings.
@Suppress("UNCHECKED_CAST")
private fun dev.caulis.wm.Caulis.applyHostSettings(settings: Map<String, Any?>) {
    (settings["pywm"] as? Map<String, Any?>)?.let { applyPywmSettings(this, it) }
    (settings["outputs"] as? List<Map<String, Any?>>)?.let { applyOutputs(this, it) }
}

// LayoutApi once the layout is there (config.py's Python starts before it).
private class DeferredApi(make: () -> LayoutApi) : PyConfig.Handler {
    private val api by lazy(make)
    override fun layout(method: String, args: List<Any?>, kwargs: Map<String, Any?>) = api.layout(method, args, kwargs)
    override fun view(handle: Int, method: String, args: List<Any?>, kwargs: Map<String, Any?>) = api.view(handle, method, args, kwargs)
    override fun viewData(view: View) = api.viewData(view)
    override fun viewOf(handle: Int) = api.viewOf(handle)
}
