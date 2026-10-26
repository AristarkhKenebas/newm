package newm

import dev.caulis.log.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private val log = Logger("config")

/**
 * newm's own configuration: a config.py, run by Python as newm ran it — whatever it imports
 * and does. Its values (numbers, texts, lists, dicts) come over as they are; its functions
 * (key bindings, rules, callbacks) stay in Python and are called from here, and what they call
 * on `layout` and views comes back to newm. The `newm` and `pywm` modules it imports are newm
 * on Caulis's (pywm's constants, newm's Layout and View, newm's own helpers).
 *
 * The messages go over the Python process's stdin and stdout, one JSON line each (see host.py).
 */
class PyConfig private constructor(private val process: Process, private val handler: Handler) : AutoCloseable {

    /** What Python asks of newm while it runs a function: a layout's or a view's method. */
    interface Handler {
        fun layout(method: String, args: List<Any?>, kwargs: Map<String, Any?>): Any?
        fun view(handle: Int, method: String, args: List<Any?>, kwargs: Map<String, Any?>): Any?

        /** A view as Python sees it (app_id, title, role, pid, panel, parent, up_state). */
        fun viewData(view: View): Map<String, Any?>

        /** The view by its handle (a View Python gave back). */
        fun viewOf(handle: Int): View?
    }

    /** Told every mistake of config.py's functions while they run (a key binding's NameError). */
    var onError: (String) -> Unit = {}

    /** False once the Python process ended. */
    val alive: Boolean get() = !broken && process.isAlive

    private val lines = LinkedBlockingQueue<Map<String, Any?>>()
    private var nextId = 0
    @Volatile
    private var broken = false

    init {
        Thread({
            try {
                process.inputStream.bufferedReader().forEachLine { line ->
                    try {
                        lines.put(Json.parseObject(line))
                    } catch (e: Exception) {
                        log.warn("config.py's host said something unreadable: ${line.take(200)}")
                    }
                }
            } finally {
                broken = true
                lines.put(mapOf("eof" to true))
            }
        }, "newm-config-reader").apply { isDaemon = true }.start()
    }

    private fun send(message: Map<String, Any?>) {
        val out = process.outputStream
        out.write((Json.write(message) + "\n").toByteArray())
        out.flush()
    }

    // Sends a request and waits for its answer, doing what Python asks meanwhile — [seconds] in
    // all (what Python asks in between does not give it more time: newm's screen waits meanwhile).
    private fun request(message: Map<String, Any?>, seconds: Long = TIMEOUT_SECONDS): Any? {
        if (broken) throw IllegalStateException("config.py's Python is not running")
        val id = ++nextId
        send(message + ("id" to id))
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (true) {
            val left = deadline - System.nanoTime()
            val msg = (if (left > 0) lines.poll(left, TimeUnit.NANOSECONDS) else null)
                ?: throw IllegalStateException("config.py did not answer in $seconds s (is it waiting for something?)")
            if (msg["eof"] == true) {
                broken = true
                throw IllegalStateException("config.py's Python ended")
            }
            if (msg["id"] == id.toLong()) {
                msg["error"]?.let { throw PyError(it.toString()) }
                return decode(msg["result"])
            }
            if (msg["op"] != null) answer(msg)
        }
    }

    // What Python asks: a method of the layout or of a view.
    private fun answer(msg: Map<String, Any?>) {
        val rid = msg["rid"]
        try {
            @Suppress("UNCHECKED_CAST")
            val args = (decode(msg["args"]) as? List<Any?>) ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val kwargs = (decode(msg["kwargs"]) as? Map<String, Any?>) ?: emptyMap()
            val method = msg["method"] as String
            val result = when (msg["op"]) {
                "layout" -> handler.layout(method, args, kwargs)
                "view" -> handler.view((msg["handle"] as Number).toInt(), method, args, kwargs)
                else -> throw IllegalArgumentException("unknown request ${msg["op"]}")
            }
            send(mapOf("rid" to rid, "result" to encode(result)))
        } catch (e: Exception) {
            log.warn("config.py asked for ${msg["op"]}.${msg["method"]}: $e")
            send(mapOf("rid" to rid, "error" to e.toString()))
        }
    }

    private fun encode(value: Any?): Any? = when (value) {
        is Layout -> mapOf("__layout__" to true)
        is View -> mapOf("__view__" to handler.viewData(value))
        is PyFunction -> mapOf("__callable__" to value.id)
        is Pair<*, *> -> listOf(encode(value.first), encode(value.second))
        is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to encode(v) }
        is Iterable<*> -> value.map(::encode)
        is Unit -> null
        is Double, is Float, is Number, is String, is Boolean, null -> value
        else -> value.toString()
    }

    private fun decode(value: Any?): Any? = when (value) {
        is Map<*, *> -> when {
            value.containsKey("__callable__") -> PyFunction(this, (value["__callable__"] as Number).toInt())
            value.containsKey("__view__") -> handler.viewOf((value["__view__"] as Number).toInt())
            value.containsKey("__layout__") -> null
            else -> value.entries.associate { (k, v) -> k.toString() to decode(v) }
        }
        is List<*> -> value.map(::decode)
        else -> value
    }

    /** Calls the Python function [id] with [args]; its result (a failure is logged and gives null). */
    internal fun call(id: Int, args: List<Any?>): Any? = try {
        request(mapOf("op" to "call", "fn" to id, "args" to args.map(::encode)))
    } catch (e: PyError) {
        log.error("config.py: ${e.message}")
        onError(e.message ?: e.toString())
        null
    } catch (e: IllegalStateException) {
        log.error(e.message ?: e.toString())
        onError(e.message ?: e.toString())
        null
    }

    /** Runs config.py at [path] (again): its values, its functions callable. */
    @Suppress("UNCHECKED_CAST")
    fun load(path: Path): Map<String, Any?> = request(mapOf("op" to "load", "path" to path.toString()), LOAD_SECONDS) as Map<String, Any?>

    /** What Python's os.environ gets (programs config.py starts are then inside the compositor). */
    fun environment(env: Map<String, String>) {
        try {
            request(mapOf("op" to "env", "env" to env))
        } catch (e: Exception) {
            log.warn("config.py's environment: $e")
        }
    }

    override fun close() {
        process.destroy()
    }

    class PyError(message: String) : Exception(message)

    companion object {
        private const val TIMEOUT_SECONDS = 5L

        // Running the whole config.py (its imports too) may take a little longer than a function.
        private const val LOAD_SECONDS = 15L

        /**
         * The config.py newm would load: ~/.config/newm/config.py, else /etc/newm/config.py,
         * else none (the default configuration).
         */
        fun path(): Path? {
            val home = System.getenv("HOME") ?: "/"
            return listOf(Path.of(home, ".config", "newm", "config.py"), Path.of("/etc", "newm", "config.py")).firstOrNull(Files::isRegularFile)
        }

        /** Starts Python with newm on Caulis's modules (null if there is no python3). */
        fun start(handler: Handler): PyConfig? {
            val folder = unpackModules()
            return try {
                val process = ProcessBuilder("python3", "-u", folder.resolve("host.py").toString())
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .apply {
                        // Our newm and pywm win over any installed ones.
                        environment()["PYTHONPATH"] = (listOf(folder.toString()) + listOfNotNull(System.getenv("PYTHONPATH"))).joinToString(":")
                        environment()["PYTHONDONTWRITEBYTECODE"] = "1"
                    }
                    .start()
                PyConfig(process, handler)
            } catch (e: java.io.IOException) {
                // (Info: ConfigLoader shows it, once.)
                log.info { "python3 cannot be started ($e): config.py is not used" }
                null
            }
        }

        private val MODULES = listOf(
            "host.py", "pywm/__init__.py", "newm/__init__.py", "newm/layout.py", "newm/view.py",
            "newm/helper/__init__.py", "newm/helper/execute.py", "newm/helper/backlight_manager.py",
            "newm/helper/bar_display.py", "newm/helper/pactl.py",
        )

        // The Python modules, from newm's resources into a folder of their own.
        private fun unpackModules(): Path {
            val folder = Files.createTempDirectory("newm-python")
            // (Removed with what is in it when newm ends: deleteOnExit leaves a folder that is not empty.)
            Runtime.getRuntime().addShutdownHook(Thread({ runCatching { folder.toFile().deleteRecursively() } }, "newm-python-cleanup"))
            for (name in MODULES) {
                val target = folder.resolve(name)
                Files.createDirectories(target.parent)
                PyConfig::class.java.getResourceAsStream("/newm/python/$name")!!.use { Files.copy(it, target) }
            }
            return folder
        }
    }
}

/** A function of config.py, callable from Kotlin with any number of arguments. */
class PyFunction(private val config: PyConfig, val id: Int) : () -> Any?, (Any?) -> Any?, (Any?, Any?) -> Any? {
    override fun invoke(): Any? = config.call(id, emptyList())
    override fun invoke(p1: Any?): Any? = config.call(id, listOf(p1))
    override fun invoke(p1: Any?, p2: Any?): Any? = config.call(id, listOf(p1, p2))
    override fun toString() = "<config.py function $id>"
}
