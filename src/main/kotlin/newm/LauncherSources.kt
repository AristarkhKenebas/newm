package newm

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * A calculator for the launcher: `2+2`, `(1.5 + 2) * 3`, `2^10`, `15 % 4`, `7/2`, `-3*-3`,
 * `sqrt(16)`; a decimal comma works as a point ("2,5*2"). Numbers, + - * / % ^ × ÷, brackets,
 * sqrt — no names, no functions besides, nothing run: only arithmetic.
 */
internal object Calculator {

    /** Whether [text] looks like a sum to work out (a digit and an operator; or "=" before it). */
    fun looksLikeSum(text: String): Boolean {
        val t = text.trim()
        if (t.startsWith("=")) return t.length > 1
        val bare = t.lowercase().replace("sqrt", "")
        if (!bare.all { it.isDigit() || it in "+-*/%^×÷()., " }) return false
        // (A number alone, or "-5", is no sum.)
        return t.any { it.isDigit() } && (bare.drop(1).any { it in "+-*/%^×÷" } || "sqrt" in t.lowercase())
    }

    /** What [text] comes to, or null when it is no sum (or divides by zero). */
    fun evaluate(text: String): Double? = try {
        val p = Parser(text.trim().removePrefix("=").replace(',', '.').replace('×', '*').replace('÷', '/').lowercase())
        val v = p.expression()
        p.skipSpaces()
        if (p.at < p.s.length || v.isNaN() || v.isInfinite()) null else v
    } catch (_: IllegalArgumentException) {
        null
    }

    /** [v] as people write it: no ".0" for a whole number, at most 10 significant digits. */
    fun format(v: Double): String {
        if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) return v.toLong().toString()
        val s = java.math.BigDecimal(v).round(java.math.MathContext(10)).stripTrailingZeros().toPlainString()
        return s
    }

    private class Parser(val s: String) {
        var at = 0
        // (Deep brackets: refused, not a stack overflow.)
        private var depth = 0

        fun skipSpaces() { while (at < s.length && s[at].isWhitespace()) at++ }

        private fun eat(c: Char): Boolean {
            skipSpaces()
            if (at < s.length && s[at] == c) { at++; return true }
            return false
        }

        fun expression(): Double {
            var v = term()
            while (true) v = when {
                eat('+') -> v + term()
                eat('-') -> v - term()
                else -> return v
            }
        }

        private fun term(): Double {
            var v = power()
            while (true) v = when {
                eat('*') -> v * power()
                eat('/') -> power().let { d -> require(d != 0.0); v / d }
                eat('%') -> power().let { d -> require(d != 0.0); v % d }
                else -> return v
            }
        }

        private fun power(): Double {
            val base = unary()
            return if (eat('^')) Math.pow(base, power()) else base
        }

        private fun unary(): Double = when {
            eat('-') -> -unary()
            eat('+') -> unary()
            else -> primary()
        }

        private fun primary(): Double {
            skipSpaces()
            require(++depth < 64)
            try {
                if (s.startsWith("sqrt", at)) {
                    at += 4
                    require(eat('('))
                    val v = expression()
                    require(eat(')') && v >= 0)
                    return Math.sqrt(v)
                }
                if (eat('(')) {
                    val v = expression()
                    require(eat(')'))
                    return v
                }
                val start = at
                while (at < s.length && (s[at].isDigit() || s[at] == '.')) at++
                require(at > start)
                return s.substring(start, at).toDoubleOrNull() ?: throw IllegalArgumentException()
            } finally {
                depth--
            }
        }
    }
}

/** A file used lately (by any program: GTK's and KDE's list, ~/.local/share/recently-used.xbel). */
internal data class RecentFile(val path: String, val name: String, val modified: String)

/** The files used lately, as GTK and most programs keep them (recently-used.xbel). */
internal object RecentFiles {

    /** The list's place: $XDG_DATA_HOME/recently-used.xbel. */
    fun defaultFile(): Path {
        val home = System.getenv("HOME") ?: System.getProperty("user.home")
        val data = System.getenv("XDG_DATA_HOME")?.takeIf { it.startsWith("/") } ?: "$home/.local/share"
        return Path.of(data, "recently-used.xbel")
    }

    private val BOOKMARK = Regex("""<bookmark\b([^>]*)>""")
    private fun attribute(tag: String, name: String): String? = Regex("""\b$name="([^"]*)"""").find(tag)?.groupValues?.get(1)

    /**
     * The newest [most] local files of [xbel] that still exist (newest first). Only the list is
     * read, never the files; a list too big (over [maxBytes]) is not read at all.
     */
    fun read(xbel: Path = defaultFile(), most: Int, maxBytes: Long = 4L shl 20): List<RecentFile> {
        if (most <= 0) return emptyList()
        val text = try {
            if (!Files.isRegularFile(xbel) || Files.size(xbel) > maxBytes) return emptyList()
            Files.readString(xbel)
        } catch (_: Exception) {
            return emptyList()
        }
        return parse(text).filter { Files.exists(Path.of(it.path)) }.take(most)
    }

    /** The local files of an xbel text, newest first (by "modified", else "visited", else "added"). */
    fun parse(text: String): List<RecentFile> = BOOKMARK.findAll(text).mapNotNull { m ->
        val tag = m.groupValues[1]
        val href = attribute(tag, "href") ?: return@mapNotNull null
        if (!href.startsWith("file://")) return@mapNotNull null
        val path = try {
            Path.of(URI(unescape(href))).toString()
        } catch (_: Exception) {
            return@mapNotNull null
        }
        val time = attribute(tag, "modified") ?: attribute(tag, "visited") ?: attribute(tag, "added") ?: ""
        RecentFile(path, path.substringAfterLast('/'), time)
    }.sortedByDescending { it.modified }.distinctBy { it.path }.toList()

    // XML's escapes in an attribute.
    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">")
}

/** The heading of a kind of result in the launcher (English; shown through tr). */
internal val DesktopApp.group: String get() = when (kind) {
    "action" -> "Actions"
    "file" -> "Files"
    "calc" -> "Calculator"
    "command" -> "Run"
    else -> "Programs"
}

/** [text] quoted for /bin/sh: one word, whatever is in it. */
internal fun shellQuoted(text: String): String = "'" + text.replace("'", "'\\''") + "'"

/**
 * Everything the launcher finds for [query] (see launcher { search }): a command after
 * [prefix] alone ("> htop": that and nothing else); else a sum worked out first ("2+2"), then
 * the programs and entries ([programs], all of them), newm's [actions] ("Lock the screen",
 * "Screenshot") and the [files] used lately, at most [perKind] of each kind besides programs.
 * [copy] puts a sum's result on the clipboard; [openWith] opens a file.
 */
internal fun universalResults(
    programs: List<DesktopApp>, actions: List<DesktopApp>, files: List<RecentFile>, query: String, sources: Set<String>,
    typos: Int, perKind: Int, prefix: String, openWith: String, copy: (Layout, String) -> Unit,
): List<DesktopApp> {
    val q = query.trim()
    if (q.isEmpty()) return programs
    if ("command" in sources && prefix.isNotEmpty() && q.startsWith(prefix)) {
        val command = q.removePrefix(prefix).trim()
        if (command.isEmpty()) return emptyList()
        return listOf(DesktopApp(command, "Run as a command", command, "", icon = "utilities-terminal", id = "command:", kind = "command",
            run = { l -> l.launch(command) }))
    }
    val out = ArrayList<DesktopApp>()
    if ("calculator" in sources && Calculator.looksLikeSum(q)) Calculator.evaluate(q)?.let { v ->
        val result = Calculator.format(v)
        out += DesktopApp("= $result", "Enter copies it", "", "", icon = "accessories-calculator", id = "calc:", kind = "calc", run = { l -> copy(l, result) })
    }
    val limit = perKind.coerceAtLeast(0)
    if ("programs" in sources) out += DesktopApp.matching(programs, q, typos)
    if ("actions" in sources && limit > 0) out += DesktopApp.matching(actions, q, typos).take(limit)
    if ("files" in sources && limit > 0 && files.isNotEmpty()) {
        val home = (System.getenv("HOME") ?: System.getProperty("user.home")).orEmpty().trimEnd('/')
        val asApps = files.map { f ->
            val folder = f.path.substringBeforeLast('/')
            DesktopApp(f.name, if (home.isNotEmpty() && (folder == home || folder.startsWith("$home/"))) "~" + folder.removePrefix(home) else folder, f.path, "",
                icon = "text-x-generic", id = "file:${f.path}", kind = "file", run = { l -> l.launch("$openWith ${shellQuoted(f.path)}") })
        }
        // (Only the name counts: a folder's name would find all the files in it.)
        out += asApps.mapNotNull { a -> LauncherSearch.score(a.name, "", "", q, typos)?.let { it to a } }.sortedBy { it.first }.take(limit).map { it.second }
    }
    return out
}

/** newm's actions the launcher finds by their words ("Lock the screen", "Screenshot of the window"): those that need nothing more. */
internal fun launcherActions(): List<DesktopApp> = KeyActions.ALL.filter { it.args.isEmpty() && it.name != "launcher" }.map { a ->
    DesktopApp(newm.bar.tr(a.description), "newm", "", a.description + " " + a.name.replace('-', ' '), icon = "", id = "action:${a.name}", kind = "action",
        run = { l -> a.run(l, emptyList()) })
}

/**
 * The programs of launcher { pinned } in the order given: each by its .desktop id
 * ("firefox", "org.gnome.Nautilus", with or without ".desktop") or its name ("Files"), case not
 * minding; one not installed is left out.
 */
internal fun pinnedApps(all: List<DesktopApp>, pinned: List<String>): List<DesktopApp> {
    if (pinned.isEmpty()) return emptyList()
    val byId = all.associateBy { it.id.lowercase() }
    val byName = all.associateBy { it.name.lowercase() }
    return pinned.mapNotNull { p ->
        val k = p.trim().lowercase().removeSuffix(".desktop")
        byId[k] ?: byName[k]
    }.distinct()
}
