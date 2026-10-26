package newm

import dev.caulis.log.Logger
import dev.caulis.wm.Caulis
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.JsePlatform
import java.nio.file.Files
import java.nio.file.Path

private val log = Logger("config")

/**
 * config.lua: newm's settings as Lua — the same sections and names as config.py and
 * config.json, with functions where a function is wanted:
 *
 * ```lua
 * view = { corner_radius = 24, padding = 8 }
 * keys = {
 *   ["L-Return"] = "launch alacritty",
 *   ["L-h"] = function() layout.move(-1, 0) end,
 * }
 * rules = { { app_id = "mpv", float = true } }
 * startup = { "mako" }
 * ```
 *
 * `layout.<method>(...)` is any of newm's (as in config.py: layout.toggle_overview(true)),
 * `newm.launch(command)`, `newm.run(action)` (Caulis's: "volume +5") and `newm.notify(text)`.
 */
object LuaConfig {

    /** The file could not be run at all (a Lua error, or it cannot be read): nothing of it counts. */
    class Failed(message: String) : Exception(message)

    /**
     * Runs [file]: its settings and what is wrong in them. When Lua itself stops on a mistake
     * (or the file cannot be read), [Failed]: half a file's settings would leave newm without keys.
     */
    fun load(file: Path, caulis: Caulis, api: () -> LayoutApi): Pair<Map<String, Any?>, List<String>> {
        val name = file.fileName.toString()
        val globals = JsePlatform.standardGlobals()
        val before = keys(globals)
        globals.set("layout", layoutTable(api))
        globals.set("newm", newmTable(caulis))
        globals.set("sys", sysTable())
        try {
            globals.load(Files.readString(file), "@$name").call()
        } catch (e: LuaError) {
            throw Failed(e.message ?: e.toString())
        } catch (e: Exception) {
            throw Failed("$name: ${e.message ?: e}")
        }
        // What the file set (not Lua's own, not layout and newm): newm's settings.
        val data = LinkedHashMap<String, Any?>()
        for (key in keys(globals) - before - setOf("layout", "newm", "sys")) data[key] = toKotlin(globals.get(key), api)
        return DataConfig.settings(data, caulis, name)
    }

    private fun keys(table: LuaTable): Set<String> {
        val out = HashSet<String>()
        var k = LuaValue.NIL
        while (true) {
            val n = table.next(k)
            k = n.arg1()
            if (k.isnil()) break
            if (k.type() == LuaValue.TSTRING) out += k.tojstring()
        }
        return out
    }

    // A Lua value as newm's settings hold them: tables as maps (or lists), functions callable.
    internal fun toKotlin(value: LuaValue, api: () -> LayoutApi): Any? = when {
        value.isnil() -> null
        value.isboolean() -> value.toboolean()
        value.isinttype() -> value.tolong()
        value.isnumber() -> value.todouble()
        value.isstring() -> value.tojstring()
        value.isfunction() -> LuaCallable(value.checkfunction(), api)
        value.istable() -> {
            val t = value.checktable()
            val n = t.length()
            // (An array — or an empty table — as a list, else a map.)
            if (keys(t).isEmpty() && (n > 0 || t.next(LuaValue.NIL).arg1().isnil())) (1..n).map { toKotlin(t.get(it), api) } else {
                val map = LinkedHashMap<String, Any?>()
                var k = LuaValue.NIL
                while (true) {
                    val e = t.next(k)
                    k = e.arg1()
                    if (k.isnil()) break
                    map[k.tojstring()] = toKotlin(e.arg(2), api)
                }
                map
            }
        }
        else -> value.tojstring()
    }

    internal fun toLua(value: Any?): LuaValue = when (value) {
        null, Unit -> LuaValue.NIL
        is Boolean -> LuaValue.valueOf(value)
        is Int -> LuaValue.valueOf(value)
        is Long -> LuaValue.valueOf(value.toDouble())
        is Number -> LuaValue.valueOf(value.toDouble())
        is String -> LuaValue.valueOf(value)
        is View -> LuaTable().apply {
            set("app_id", value.appId)
            set("title", value.title)
            set("role", value.role)
            set("pid", value.pid)
            value.panel?.let { set("panel", it) }
            set("handle", value.handle)
        }
        is Map<*, *> -> LuaTable().apply { value.forEach { (k, v) -> set(k.toString(), toLua(v)) } }
        is Iterable<*> -> LuaTable().apply { value.forEachIndexed { i, v -> set(i + 1, toLua(v)) } }
        else -> LuaValue.valueOf(value.toString())
    }

    private fun layoutTable(api: () -> LayoutApi): LuaTable {
        val table = LuaTable()
        val meta = LuaTable()
        meta.set("__index", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val method = args.checkjstring(2)
                return object : VarArgFunction() {
                    override fun invoke(call: Varargs): Varargs {
                        // layout.move(-1, 0) and layout:move(-1, 0) both.
                        val start = if (call.narg() > 0 && call.arg1().istable() && call.arg1() === table) 2 else 1
                        val params = (start..call.narg()).map { toKotlinPlain(call.arg(it)) }
                        val kwargs = (params.lastOrNull() as? Map<*, *>)?.let { m -> m.entries.associate { it.key.toString() to it.value } }
                        val positional = if (kwargs != null) params.dropLast(1) else params
                        return toLua(api().layout(method, positional, kwargs ?: emptyMap()))
                    }
                }
            }
        })
        table.setmetatable(meta)
        return table
    }

    private fun toKotlinPlain(value: LuaValue): Any? = when {
        value.isnil() -> null
        value.isboolean() -> value.toboolean()
        value.isinttype() -> value.tolong()
        value.isnumber() -> value.todouble()
        value.isstring() -> value.tojstring()
        value.istable() -> {
            val t = value.checktable()
            val map = LinkedHashMap<String, Any?>()
            var k = LuaValue.NIL
            while (true) {
                val e = t.next(k)
                k = e.arg1()
                if (k.isnil()) break
                map[k.tojstring()] = toKotlinPlain(e.arg(2))
            }
            map
        }
        else -> value.tojstring()
    }

    private fun newmTable(caulis: Caulis): LuaTable = LuaTable().apply {
        set("launch", fn { args -> caulis.launch(args.checkjstring(1)); LuaValue.NONE })
        set("terminal", fn { LuaValue.valueOf(defaultTerminal()) })
        set("run", fn { args -> caulis.run(args.checkjstring(1)); LuaValue.NONE })
        set("notify", fn { args -> caulis.notify(args.checkjstring(1), title = "newm"); LuaValue.NONE })
    }

    // What a bar shows (SystemInfo): sys.wifi(), sys.time("HH:mm"), ...
    private fun sysTable(): LuaTable = LuaTable().apply {
        for ((name, f) in SYSTEM_TEXTS) set(name, fn { LuaValue.valueOf(f()) })
        set("date", fn { args -> LuaValue.valueOf(if (args.isstring(1)) SystemInfo.date(args.checkjstring(1)) else SystemInfo.date()) })
        set("time", fn { args -> LuaValue.valueOf(if (args.isstring(1)) SystemInfo.time(args.checkjstring(1)) else SystemInfo.time()) })
    }

    private fun fn(body: (Varargs) -> Varargs) = object : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs = body(args)
    }
}

/** A function of config.lua, callable from Kotlin with any number of arguments (see [PyFunction]). */
class LuaCallable(private val fn: LuaFunction, private val api: () -> LayoutApi) : () -> Any?, (Any?) -> Any?, (Any?, Any?) -> Any? {
    private fun call(vararg args: Any?): Any? = try {
        val result = fn.invoke(LuaValue.varargsOf(args.map { if (it is Layout) LuaValue.NIL else LuaConfig.toLua(it) }.toTypedArray())).arg1()
        LuaConfig.toKotlin(result, api)
    } catch (e: LuaError) {
        log.error("config.lua: ${e.message}")
        null
    }

    override fun invoke(): Any? = call()
    override fun invoke(p1: Any?): Any? = call(p1)
    override fun invoke(p1: Any?, p2: Any?): Any? = call(p1, p2)
}
