package app.nagmedar.ui.i18n

/** A string literal of a Kotlin source: its line, its text ("${}" for each template) and the code before it on its line. */
internal class Literal(val line: Int, val text: String, val before: String)

/**
 * A Kotlin source read just enough to find the texts written in it (S-1501): its string literals,
 * nested ones in templates too, and its code with the comments blanked out. Char literals, raw
 * strings, escapes and nested block comments are understood; nothing else is.
 */
internal class KotlinSource(val file: String, private val source: String) {
    val literals = mutableListOf<Literal>()

    /** The code with every comment's characters turned into spaces: offsets and lines stay. */
    val code: String

    private val out = StringBuilder(source.length)
    private var i = 0
    private val lineStarts: List<Int> = listOf(0) + source.indices.filter { source[it] == '\n' }.map { it + 1 }

    init {
        code(inTemplate = false)
        code = out.toString()
    }

    /** The line of [offset], from 1. */
    fun lineOf(offset: Int): Int {
        val index = lineStarts.binarySearch(offset)
        return if (index >= 0) index + 1 else -index - 1
    }

    private fun code(inTemplate: Boolean) {
        var depth = 0
        while (i < source.length) {
            val c = source[i]
            when {
                source.startsWith("//", i) -> while (i < source.length && source[i] != '\n') blank()
                source.startsWith("/*", i) -> blockComment()
                source.startsWith("\"\"\"", i) -> string(raw = true)
                c == '"' -> string(raw = false)
                c == '\'' -> charLiteral()
                c == '{' -> {
                    depth++
                    keep()
                }
                c == '}' -> {
                    keep()
                    if (inTemplate && depth == 0) return
                    depth--
                }
                else -> keep()
            }
        }
    }

    private fun keep(count: Int = 1) {
        repeat(count) {
            if (i < source.length) out.append(source[i])
            i++
        }
    }

    private fun blank() {
        out.append(if (source[i] == '\n') '\n' else ' ')
        i++
    }

    private fun blockComment() {
        var nesting = 0
        while (i < source.length) {
            when {
                source.startsWith("/*", i) -> {
                    nesting++
                    blank()
                    blank()
                }
                source.startsWith("*/", i) -> {
                    nesting--
                    blank()
                    blank()
                    if (nesting == 0) return
                }
                else -> blank()
            }
        }
    }

    private fun charLiteral() {
        keep()
        if (i < source.length && source[i] == '\\') keep(2)
        while (i < source.length && source[i] != '\'') keep()
        keep()
    }

    private fun string(raw: Boolean) {
        val start = i
        val before = out.substring(lineStarts[lineOf(start) - 1], start)
        val text = StringBuilder()
        keep(if (raw) 3 else 1)
        while (i < source.length) {
            val c = source[i]
            if (raw && source.startsWith("\"\"\"", i)) {
                // a raw string may end in more quotes than three: the others are its own
                var quotes = 0
                while (i < source.length && source[i] == '"') {
                    quotes++
                    keep()
                }
                repeat(quotes - 3) { text.append('"') }
                break
            }
            if (!raw && c == '"') {
                keep()
                break
            }
            if (!raw && c == '\\' && i + 1 < source.length) {
                text.append(source, i, i + 2)
                keep(2)
                continue
            }
            if (c == '$' && i + 1 < source.length && source[i + 1] == '{') {
                text.append("\${}")
                keep(2)
                code(inTemplate = true)
                continue
            }
            text.append(c)
            keep()
        }
        literals += Literal(lineOf(start), text.toString(), before)
    }
}
