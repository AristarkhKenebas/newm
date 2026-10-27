package newm

import dev.caulis.core.Notice
import dev.caulis.core.NoticeLevel
import dev.caulis.log.Logger
import dev.caulis.wm.Caulis
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds

private val log = Logger("config")

/**
 * The user's configuration while newm runs: ~/.config/newm/config.kts, else a config.py of
 * before (run by Python as newm did; a line or two may need changing). With neither, newm
 * writes one there (the folder too) — the system's /etc/newm (config.kts, config.lua or config.json), or its own — and uses it.
 *
 * Saving a file of the folder loads it again — no new session needed. A mistake never leaves
 * a black screen: at the start newm runs with its default configuration, later with the last
 * one that worked, and the mistake (with its line) is shown on screen until a save loads.
 */
class ConfigLoader(private val caulis: Caulis, private val handler: PyConfig.Handler) {
    private val home = Path.of(System.getenv("HOME") ?: System.getProperty("user.home"))
    private val folder = home.resolve(".config/newm")
    private var python: PyConfig? = null
    private var errorNotice: Notice? = null
    private var pendingError: String? = null
    private var running = false

    /** The file in use (found, or made). */
    val path: Path = locate()

    /** Set by main: what a save does (newm's update_config, with [loadAgain] inside). */
    var reload: () -> Unit = {}

    private fun locate(): Path {
        val kts = folder.resolve("config.kts")
        // The system's: /etc/newm/config.kts, config.lua or config.json (NixOS's programs.newm.settings writes that one).
        val system = SYSTEM_NAMES.map { Path.of("/etc/newm", it) }.firstOrNull(Files::isRegularFile)
        // The user's own (config.kts first, then config.lua, config.json — Nix writes that —
        // and a config.py of before), else the system's.
        NAMES.map(folder::resolve).firstOrNull(Files::isRegularFile)?.let { return it }
        // None of the user's own: theirs from the system's (/etc/newm), or newm's own templates,
        // written for them to change (config.kts, examples/, a README).
        try {
            val from = if (system != null) "/etc/newm" else "newm's templates"
            if (system != null) Templates.copyFolder(system.parent, folder) else Templates.copyTo(folder)
            log.info { "There was no configuration: $folder written from $from" }
            NAMES.map(folder::resolve).firstOrNull(Files::isRegularFile)?.let { return it }
        } catch (e: Exception) {
            log.warn("$folder cannot be written ($e)")
        }
        return NAMES.map { Path.of("/etc/newm", it) }.firstOrNull(Files::isRegularFile) ?: kts
    }

    private companion object {
        val NAMES = listOf("config.kts", "config.lua", "config.json", "config.py")
        val SYSTEM_NAMES = listOf("config.kts", "config.lua", "config.json")
    }

    // A config.json (home-manager's settings) beside the config.kts or config.lua in use: it is not read, and that is said.
    private val unusedJson: String? = folder.resolve("config.json").takeIf { path.parent == folder && path.fileName.toString() != "config.json" && Files.isRegularFile(it) }
        ?.let { "$it is not used: ${path.fileName} is read first (remove ${path.fileName} for config.json's settings, e.g. home-manager's)" }

    // config.kts compiles one at a time; a compilation for an older save is dropped when a newer one has begun.
    private val compiler = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "newm-config-compile").apply { isDaemon = true } }
    private val generation = java.util.concurrent.atomic.AtomicInteger()

    private val kind get() = path.fileName.toString().substringAfterLast('.')

    /**
     * The configuration now (config.kts at the start: compiled here, a few seconds the first time).
     * A config.py is run on the compositor's thread, at a reload too: what it calls on `layout`
     * while it runs has to be answered there, so it cannot move to a thread of its own.
     */
    fun load(): Map<String, Any?>? = when (kind) {
        "kts" -> runKts(KtsConfig.compile(path))
        "lua", "json" -> loadData()
        else -> loadPython()
    }

    // config.lua and config.json: read here, quickly.
    private fun loadData(): Map<String, Any?>? {
        val (settings, problems) = try {
            if (kind == "lua") {
                LuaConfig.load(path, caulis) { (handler as? LayoutApiSource)?.api() ?: error("newm is not ready") }
            } else {
                // (JSON as Nix writes it; lines starting with // are notes, for a hand-written one.)
                val text = Files.readAllLines(path).filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
                DataConfig.settings(Json.parseObject(text), caulis, path.fileName.toString())
            }
        } catch (e: Exception) {
            failed(e.message ?: e.toString())
            return null
        }
        if (problems.isNotEmpty()) {
            // (What could be read still counts; the rest is shown.)
            show("${path.fileName}: ${problems.take(3).joinToString("\n")}", NoticeLevel.WARNING)
        } else {
            clearError()
        }
        // On top of newm's own configuration: a config.json of a few lines (NixOS'
        // programs.newm.settings = { view.corner_radius = 24; }) changes those, and the keys,
        // the bar and the rest stay — they were all gone. A section given whole replaces it.
        val base = defaults ?: loadDefault().also { defaults = it } ?: return settings
        return merged(base, settings)
    }

    // newm's own configuration (the template), made once: what config.json and config.lua add to.
    private var defaults: Map<String, Any?>? = null

    // [over] on top of [base]: maps in both are merged the same way, anything else is [over]'s.
    @Suppress("UNCHECKED_CAST")
    private fun merged(base: Map<String, Any?>, over: Map<String, Any?>): Map<String, Any?> {
        val out = LinkedHashMap(base)
        for ((key, value) in over) {
            val below = out[key]
            out[key] = if (below is Map<*, *> && value is Map<*, *>) merged(below as Map<String, Any?>, value as Map<String, Any?>) else value
        }
        return out
    }

    /** newm's own default (when the user's cannot be loaded at the start). */
    fun loadDefault(): Map<String, Any?>? {
        val copy = Files.createTempFile("newm-default", ".kts")
        return try {
            ConfigLoader::class.java.getResourceAsStream("/newm/templates/config.kts")!!.use { Files.copy(it, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
            val compiled = KtsConfig.compile(copy)
            val config = NewmConfig(caulis)
            if (KtsConfig.run(compiled, config).isEmpty()) config.values else null
        } finally {
            Files.deleteIfExists(copy)
        }
    }

    private fun runKts(compiled: KtsConfig.Compiled): Map<String, Any?>? {
        val config = NewmConfig(caulis)
        val problems = KtsConfig.run(compiled, config, path.fileName.toString())
        if (problems.isNotEmpty()) {
            failed(problems.take(3).joinToString("\n"))
            return null
        }
        clearError()
        return config.values
    }

    private fun loadPython(): Map<String, Any?>? {
        val py = python?.takeIf { it.alive } ?: PyConfig.start(handler)?.also { started ->
            python?.close()
            python = started
            started.onError = { text -> caulis.post { show("${path.fileName}: $text", NoticeLevel.WARNING) } }
            if (running) started.environment(caulis.programEnvironment())
        }
        if (py == null) {
            show("python3 cannot be started: ${path.fileName} is not used (newm runs with its default configuration)", NoticeLevel.ERROR)
            return null
        }
        return try {
            py.load(path).also { clearError() }
        } catch (e: Exception) {
            failed(e.message ?: e.toString())
            null
        }
    }

    private fun failed(what: String) {
        val after = if (running) "The settings before stay" else "newm runs with its default configuration"
        show("${path.fileName} cannot be loaded — $what\n$after until it is saved without mistakes.", NoticeLevel.ERROR)
    }

    /**
     * Loads the file again for a reload: config.kts is compiled on a thread of its own (the
     * screen does not stop), then [apply] runs on the compositor's thread with the settings.
     */
    fun loadAgain(apply: (Map<String, Any?>) -> Unit) {
        if (kind != "kts") {
            load()?.let(apply)
            return
        }
        val mine = generation.incrementAndGet()
        compiler.execute {
            // (A newer save is already waiting: this one would only be thrown away.)
            if (generation.get() != mine) return@execute
            val compiled = try {
                KtsConfig.compile(path)
            } catch (e: Throwable) {
                KtsConfig.Compiled(null, listOf("${path.fileName} cannot be compiled: ${e.message ?: e}"))
            }
            caulis.post {
                if (generation.get() != mine) return@post log.info { "${path.fileName}: a compilation for an older save dropped" }
                runKts(compiled)?.let(apply)
            }
        }
    }

    /** The compositor runs: what Python starts runs inside it; mistakes so far are shown; saves are followed. */
    fun started() {
        running = true
        python?.environment(caulis.programEnvironment())
        unusedJson?.let { text ->
            log.info { text }
            caulis.notify(text, NoticeLevel.WARNING, "newm")
        }
        pendingError?.let { text ->
            pendingError = null
            show(text, NoticeLevel.ERROR)
        }
        watch()
    }

    fun close() {
        compiler.shutdownNow()
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

    // A file of the config's folder saved (.kts, .py): loaded again, once, a moment after the last save.
    private fun watch() {
        val dir = path.parent
        val watcher = try {
            FileSystems.getDefault().newWatchService().also { dir.register(it, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE) }
        } catch (e: Exception) {
            return log.warn("$dir cannot be followed ($e): saving the configuration needs updateConfig() to take it on")
        }
        var pending: AutoCloseable? = null
        Thread({
            while (true) {
                val key = try {
                    watcher.take()
                } catch (_: InterruptedException) {
                    return@Thread
                }
                val changed = key.pollEvents().map { it.context().toString() }.filter { name -> listOf(".py", ".kts", ".lua", ".json").any(name::endsWith) }
                key.reset()
                if (changed.isEmpty()) continue
                caulis.post {
                    pending?.close()
                    pending = caulis.after(300) {
                        log.info { "${changed.distinct().joinToString()} saved: loading ${path.fileName} again" }
                        reload()
                    }
                }
            }
        }, "newm-config-watch").apply { isDaemon = true }.start()
        log.info { "Following $dir: saving ${path.fileName} loads it again" }
    }
}

/** A handler that can give the layout's API (config.lua's `layout` table needs it). */
interface LayoutApiSource {
    fun api(): LayoutApi
}
