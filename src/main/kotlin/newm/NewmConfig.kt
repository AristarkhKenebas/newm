package newm

import dev.caulis.core.Notice
import dev.caulis.core.NoticeLevel
import dev.caulis.log.Logger
import dev.caulis.wm.Caulis
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds

private val log = Logger("config")

/**
 * The user's config.py while newm runs: loaded at the start, and again whenever a .py file
 * in its folder is saved — no new session needed. A mistake never leaves a black screen: at
 * the start newm runs with its default configuration, later with the last one that worked,
 * and the mistake (with its line) is shown on screen until a saved file loads.
 */
class NewmConfig(private val caulis: Caulis, private val path: Path, private val handler: PyConfig.Handler) {
    private var python: PyConfig? = null
    private var errorNotice: Notice? = null
    private var pendingError: String? = null
    private var running = false

    /** config.py's values, or null (its mistake is shown). */
    fun load(): Map<String, Any?>? {
        val py = python?.takeIf { it.alive } ?: PyConfig.start(handler)?.also { started ->
            python?.close()
            python = started
            started.onError = { text -> caulis.post { show("config.py: $text", NoticeLevel.WARNING) } }
            if (running) started.environment(caulis.programEnvironment())
        }
        if (py == null) {
            show("python3 cannot be started: config.py is not used (newm runs with its default configuration)", NoticeLevel.ERROR)
            return null
        }
        return try {
            py.load(path).also { clearError() }
        } catch (e: Exception) {
            show("config.py cannot be loaded — ${e.message}. ${if (running) "The settings before stay" else "newm runs with its default configuration"} until it is saved without mistakes.", NoticeLevel.ERROR)
            null
        }
    }

    /** The compositor runs: what Python starts runs inside it; mistakes so far are shown; saves are followed. */
    fun started() {
        running = true
        python?.environment(caulis.programEnvironment())
        pendingError?.let { text ->
            pendingError = null
            show(text, NoticeLevel.ERROR)
        }
        watch()
    }

    fun close() {
        python?.close()
    }

    private fun show(text: String, level: NoticeLevel) {
        // (Info in the log: an error line there would become a second notice.)
        log.info { text }
        if (!running) {
            pendingError = text
            return
        }
        if (level == NoticeLevel.ERROR) errorNotice?.let(caulis.desktop::dismiss)
        val notice = caulis.notify(text, level, "newm")
        if (level == NoticeLevel.ERROR) errorNotice = notice
    }

    private fun clearError() {
        pendingError = null
        errorNotice?.let { caulis.desktop.dismiss(it) }
        errorNotice = null
    }

    // A .py file of the config's folder saved: loaded again (once, a moment after the last save).
    private fun watch() {
        val folder = path.parent
        val watcher = FileSystems.getDefault().newWatchService()
        folder.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE)
        var pending: AutoCloseable? = null
        Thread({
            while (true) {
                val key = try {
                    watcher.take()
                } catch (_: InterruptedException) {
                    return@Thread
                }
                val changed = key.pollEvents().map { it.context().toString() }.filter { it.endsWith(".py") }
                key.reset()
                if (changed.isEmpty()) continue
                caulis.post {
                    pending?.close()
                    pending = caulis.after(300) {
                        log.info { "${changed.distinct().joinToString()} saved: loading config.py again" }
                        reload()
                    }
                }
            }
        }, "newm-config-watch").apply { isDaemon = true }.start()
        log.info { "Following $folder: saving config.py loads it again" }
    }

    /** Set by main: what a reload does (newm's update_config). */
    var reload: () -> Unit = {}
}
