package newm

/** Just enough JSON for newm's messages (D-Bus strings, greetd): objects, arrays, strings, numbers, true, false, null. */
object Json {
    fun parseObject(text: String): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return Parser(text).value() as? Map<String, Any?> ?: error("not a JSON object: $text")
    }

    fun write(value: Any?): String = buildString { write(this, value) }

    private fun write(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> {
                out.append('"')
                for (c in value) {
                    when {
                        c == '"' -> out.append("\\\"")
                        c == '\\' -> out.append("\\\\")
                        c == '\n' -> out.append("\\n")
                        c.code < 0x20 -> out.append("\\u%04x".format(c.code))
                        else -> out.append(c)
                    }
                }
                out.append('"')
            }
            is Boolean, is Number -> out.append(value.toString())
            is Map<*, *> -> {
                out.append('{')
                value.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) out.append(',')
                    write(out, k.toString())
                    out.append(':')
                    write(out, v)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) out.append(',')
                    write(out, v)
                }
                out.append(']')
            }
            else -> write(out, value.toString())
        }
    }

    private class Parser(private val s: String) {
        private var at = 0

        private fun skip() {
            while (at < s.length && s[at].isWhitespace()) at++
        }

        fun value(): Any? {
            skip()
            check(at < s.length) { "JSON ends early" }
            return when (val c = s[at]) {
                '{' -> {
                    at++
                    val map = LinkedHashMap<String, Any?>()
                    skip()
                    if (s[at] == '}') return map.also { at++ }
                    while (true) {
                        skip()
                        val key = value() as String
                        skip()
                        check(s[at++] == ':') { "':' expected at $at" }
                        map[key] = value()
                        skip()
                        if (s[at++] == '}') return map
                    }
                    @Suppress("UNREACHABLE_CODE") map
                }
                '[' -> {
                    at++
                    val list = ArrayList<Any?>()
                    skip()
                    if (s[at] == ']') return list.also { at++ }
                    while (true) {
                        list += value()
                        skip()
                        if (s[at++] == ']') return list
                    }
                    @Suppress("UNREACHABLE_CODE") list
                }
                '"' -> {
                    at++
                    val out = StringBuilder()
                    while (s[at] != '"') {
                        if (s[at] == '\\') {
                            at++
                            when (val e = s[at]) {
                                'n' -> out.append('\n')
                                't' -> out.append('\t')
                                'r' -> out.append('\r')
                                'b' -> out.append('\b')
                                'f' -> out.append('\u000c')
                                'u' -> {
                                    out.append(s.substring(at + 1, at + 5).toInt(16).toChar())
                                    at += 4
                                }
                                else -> out.append(e)
                            }
                        } else {
                            out.append(s[at])
                        }
                        at++
                    }
                    at++
                    out.toString()
                }
                't' -> true.also { at += 4 }
                'f' -> false.also { at += 5 }
                'n' -> null.also { at += 4 }
                else -> {
                    val start = at
                    while (at < s.length && (s[at].isDigit() || s[at] in "+-.eE")) at++
                    val number = s.substring(start, at)
                    check(number.isNotEmpty()) { "unexpected '$c' at $start" }
                    number.toLongOrNull() ?: number.toDouble()
                }
            }
        }
    }
}
