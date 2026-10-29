package newm

import dev.caulis.log.Logger
import java.util.Locale

private val log = Logger("install")

/**
 * The notes of newm's config.kts in the user's language. config.kts is written in English;
 * templates/i18n/config.<language>.txt has, for each note, its English words and the same in
 * that language. The code stays as it is, only the notes change — so every language has the
 * same configuration, and a language more is one file more (and its name in [LANGUAGES]).
 *
 * The table's form: a line "en: <the note in English>", the next "<language>: <its words>";
 * lines starting with # and empty lines are left out. A note the table does not know (one of
 * your own in /etc/newm/config.kts) stays as it is.
 */
object TemplateText {
    /** The languages config.kts's notes are written in besides English. */
    val LANGUAGES = listOf("ru")

    /**
     * The user's language for the notes: the first of LANGUAGE, LC_ALL, LC_MESSAGES and LANG
     * that says one ("ru_RU.UTF-8" is "ru"); "en" when it is none of [LANGUAGES].
     */
    fun systemLanguage(env: (String) -> String? = System::getenv): String {
        val said = listOf("LANGUAGE", "LC_ALL", "LC_MESSAGES", "LANG").firstNotNullOfOrNull { name ->
            env(name)?.split(':')?.firstOrNull { it.isNotBlank() && it != "C" && it != "POSIX" && !it.startsWith("C.") }
        }
        val code = said?.substringBefore('_')?.substringBefore('.')?.substringBefore('@')?.lowercase() ?: Locale.getDefault().language
        return if (code in LANGUAGES) code else "en"
    }

    // Each language's table: the English note → its words (read once).
    private val tables = HashMap<String, Map<String, String>>()

    /** The table of [language]: each English note and its words there (empty for English or a language without one). */
    fun table(language: String): Map<String, String> = tables.getOrPut(language) {
        if (language == "en") return@getOrPut emptyMap()
        val text = TemplateText::class.java.getResourceAsStream("/newm/templates/i18n/config.$language.txt")?.use { String(it.readAllBytes()) }
            ?: return@getOrPut emptyMap<String, String>().also { log.warn("No notes in \"$language\" for config.kts: English") }
        parse(text, language)
    }

    /** A table's text read: English note → its words. */
    internal fun parse(text: String, language: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var english: String? = null
        for ((n, raw) in text.lines().withIndex()) {
            val line = raw.trimEnd()
            if (line.isBlank() || line.startsWith("#")) continue
            when {
                line.startsWith("en: ") -> english = line.removePrefix("en: ").trim()
                line.startsWith("$language: ") -> {
                    val en = english ?: continue.also { log.warn("config.$language.txt line ${n + 1}: no \"en: \" line before it") }
                    out[en] = line.removePrefix("$language: ").trim()
                    english = null
                }
                else -> log.warn("config.$language.txt line ${n + 1}: neither \"en: \" nor \"$language: \"")
            }
        }
        return out
    }

    // Any language's words of a note → its English (to write a template in another language
    // from one already translated, the system's /etc/newm).
    private fun english(note: String): String {
        for (language in LANGUAGES) table(language).entries.firstOrNull { it.value == note }?.let { return it.key }
        return note
    }

    /** [template] (config.kts, its notes in any language newm knows) with its notes in [language]. */
    fun translate(template: String, language: String): String {
        val table = table(language)
        return mapNotes(template) { note ->
            val en = english(note)
            if (language == "en") en else table[en] ?: note
        }
    }

    /** Every note of [template] (in order, each once). */
    fun notes(template: String): List<String> {
        val out = LinkedHashSet<String>()
        mapNotes(template) { out += it; it }
        return out.toList()
    }

    /** [template] with the code alone: every note left out (the same for each language). */
    fun code(template: String): String = mapNotes(template) { "" }.lines().joinToString("\n") { it.trimEnd() }

    // A commented line that is a setting turned off ("// numLock = true"), not words.
    private val CODE = Regex("""^(?:[A-Za-z_][A-Za-z0-9_]*(?:\s*=|\(|\s*\{|\.[A-Za-z(]|;)|"[^"]*"\s*(?:\(|\{|->)|\}|val\s)""")

    /** True if the commented [text] (after its //) is code: a setting or a key turned off. */
    fun isCode(text: String): Boolean = CODE.containsMatchIn(text.trimStart())

    // [template] with each note (its words, trimmed) replaced by [change]'s; the code as it is.
    private fun mapNotes(template: String, change: (String) -> String): String = template.lines().joinToString("\n") { line ->
        val indent = line.takeWhile { it == ' ' }
        val rest = line.substring(indent.length)
        if (rest.startsWith("//")) {
            val inner = rest.substring(2)
            if (isCode(inner)) {
                // A setting turned off: its own note after it, if any.
                indent + "//" + trailing(inner, change)
            } else {
                // Words: the slashes and spaces before them stay ("//   // more words").
                val lead = Regex("""^[\s/]*""").find(inner)!!.value
                val words = inner.substring(lead.length)
                if (words.isBlank()) line else indent + "//" + lead + change(words.trimEnd()).let { if (it.isEmpty()) "" else it }
            }
        } else {
            indent + trailing(rest, change)
        }
    }

    // Code with a note after it ("x = 1   // a note"): the note changed, the code and the room before the note kept.
    private fun trailing(code: String, change: (String) -> String): String {
        val at = noteStart(code) ?: return code
        val note = code.substring(at + 2).trim()
        if (note.isEmpty()) return code
        val changed = change(note)
        return if (changed.isEmpty()) code.substring(0, at).trimEnd() else code.substring(0, at + 2) + " " + changed
    }

    // Where "//" starts a note in [code] (not inside a string), or null.
    private fun noteStart(code: String): Int? {
        var inString = false
        var i = 0
        while (i < code.length - 1) {
            val c = code[i]
            when {
                c == '\\' && inString -> i++
                c == '"' -> inString = !inString
                !inString && c == '/' && code[i + 1] == '/' -> return i
            }
            i++
        }
        return null
    }
}
