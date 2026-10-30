package newm

/**
 * How the launcher finds what was typed: the words as they are, the same keys on the other
 * keyboard layout ("ашкуащч" is "firefox", "ntktuhfv" is "телеграм"), and — with
 * launcher { typos } — a letter or two wrong, missing, extra or swapped ("fierfox", "firfox").
 * Pure functions: tested without a screen.
 */
internal object LauncherSearch {

    // The same keys on the Russian and the Latin (US) layout.
    private const val RU = "йцукенгшщзхъфывапролджэячсмитьбюё"
    private const val EN = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`"

    /** [text] typed on the Russian layout meaning the Latin one: "ашкуащч" → "firefox" (other letters as they are). */
    fun latinOf(text: String): String = text.map { c -> RU.indexOf(c).let { if (it >= 0) EN[it] else c } }.joinToString("")

    /** [text] typed on the Latin layout meaning the Russian one: "ntktuhfv" → "телеграм". */
    fun cyrillicOf(text: String): String = text.map { c -> EN.indexOf(c).let { if (it >= 0) RU[it] else c } }.joinToString("")

    /** What [query] may have meant: itself, then on the other layout (when that differs). */
    fun variants(query: String): List<String> {
        val q = query.trim().lowercase()
        return listOf(q, latinOf(q), cyrillicOf(q)).distinct().filter { it.isNotEmpty() }
    }

    /**
     * How many letters may be wrong in a word of [length] letters with launcher { typos = [most] }:
     * none below 4 letters (too many words would match), one up to 7, then [most].
     */
    fun typosAllowed(length: Int, most: Int): Int = when {
        most <= 0 || length < 4 -> 0
        length < 8 -> minOf(1, most)
        else -> most
    }

    /**
     * The fewest edits (a letter changed, added, removed, or two neighbours swapped) that make
     * [a] into [b] — Damerau's distance, the restricted kind — or [limit] + 1 when more.
     */
    fun distance(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        // Three rows of the table: the one before last, the last, this one.
        var prev2 = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var best = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var d = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d = minOf(d, prev2[j - 2] + 1)
                cur[j] = d
                if (d < best) best = d
            }
            // (A row's smallest never shrinks below the next row's: past the limit, it stays past.)
            if (best > limit + 1) return limit + 1
            val oldest = prev2
            prev2 = prev
            prev = cur
            cur = oldest
        }
        return prev[b.length].coerceAtMost(limit + 1)
    }

    /** Whether [word] starts with [q] give or take [typos] letters (its start as long as [q], a letter more or less). */
    fun startsAlmost(word: String, q: String, typos: Int): Boolean {
        if (typos <= 0) return word.startsWith(q)
        for (len in (q.length - typos).coerceAtLeast(1)..minOf(word.length, q.length + typos)) {
            if (distance(q, word.substring(0, len), typos) <= typos) return true
        }
        return false
    }

    /** Whether the letters of [q] are in [text] in this order (a word's first letters: "gte" in "gnome text editor"). */
    fun inOrder(text: String, q: String): Boolean {
        var i = 0
        for (c in text) if (i < q.length && c == q[i]) i++
        return i == q.length
    }

    /** The words of a name: split at spaces, dashes, dots and the like. */
    fun words(text: String): List<String> = text.lowercase().split(' ', '-', '_', '.', '/', '(', ')').filter { it.isNotEmpty() }

    /**
     * How well [query] finds something called [name] (with [extra] words: keywords, a comment)
     * and run by [command]: 0 is best; null: not at all. Exact kinds first — the name starts with
     * it (0), a word of it does (1), it is in the name (2), in the words (3), in the command (4) —
     * then a word's first letters in order (5), then with typos (6, 7). On the other layout the
     * same, a little after ([LAYOUT_PENALTY]).
     */
    fun score(name: String, extra: String, command: String, query: String, typos: Int): Int? {
        var best: Int? = null
        for ((i, q) in variants(query).withIndex()) {
            val s = scoreExactly(name.lowercase(), extra.lowercase(), command.lowercase(), q, typos) ?: continue
            val total = s * 10 + if (i == 0) 0 else LAYOUT_PENALTY
            if (best == null || total < best) best = total
        }
        return best
    }

    /** What the other keyboard layout costs: after everything matched as typed of the same kind. */
    const val LAYOUT_PENALTY = 5

    private fun scoreExactly(name: String, extra: String, command: String, q: String, typos: Int): Int? {
        val nameWords = words(name)
        return when {
            name.startsWith(q) -> 0
            nameWords.any { it.startsWith(q) } -> 1
            q in name -> 2
            q in extra -> 3
            q in command -> 4
            q.length >= 2 && inOrder(nameWords.joinToString("") { it.take(1) }, q) && q.length <= nameWords.size -> 5
            else -> {
                val allowed = typosAllowed(q.length, typos)
                when {
                    allowed == 0 -> null
                    nameWords.any { startsAlmost(it, q, allowed) } || startsAlmost(name.replace(" ", ""), q, allowed) -> 6
                    words(extra).any { startsAlmost(it, q, allowed) } -> 7
                    else -> null
                }
            }
        }
    }
}
