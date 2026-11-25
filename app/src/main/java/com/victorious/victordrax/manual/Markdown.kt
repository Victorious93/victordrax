package com.victorious.victordrax.manual

/**
 * Tiny, dependency-free Markdown block parser for the in-app manual. Supports the small subset the
 * manual pages use: ATX headings (#, ##, ###), bullet lists (- / *), ordered lists (1.), and
 * paragraphs. Inline bold (**text**) and links [text](url) are parsed by [parseInline]. Kept pure
 * so it is unit tested; rendering lives in the Compose layer.
 */
object Markdown {
    sealed interface Block {
        data class Heading(val level: Int, val text: String) : Block
        data class Paragraph(val text: String) : Block
        data class Bullet(val text: String) : Block
        data class Numbered(val number: Int, val text: String) : Block
    }

    sealed interface Span {
        data class Text(val text: String) : Span
        data class Bold(val text: String) : Span
        data class Link(val text: String, val url: String) : Span
    }

    private val ORDERED = Regex("^(\\d+)\\.\\s+(.*)$")
    private val LINK = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")

    fun parse(text: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val para = StringBuilder()
        fun flush() {
            if (para.isNotBlank()) blocks += Block.Paragraph(para.toString().trim())
            para.setLength(0)
        }
        for (raw in text.lines()) {
            val line = raw.trimEnd()
            when {
                line.isBlank() -> flush()
                line.startsWith("#") -> {
                    flush()
                    val level = line.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    blocks += Block.Heading(level, line.drop(level).trim())
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    flush()
                    blocks += Block.Bullet(line.drop(2).trim())
                }
                ORDERED.matches(line.trimStart()) -> {
                    flush()
                    val m = ORDERED.find(line.trimStart())!!
                    blocks += Block.Numbered(m.groupValues[1].toInt(), m.groupValues[2].trim())
                }
                else -> {
                    if (para.isNotEmpty()) para.append(' ')
                    para.append(line.trim())
                }
            }
        }
        flush()
        return blocks
    }

    /** Split a line into text / bold / link spans. */
    fun parseInline(text: String): List<Span> {
        val spans = mutableListOf<Span>()
        var i = 0
        while (i < text.length) {
            val linkMatch = LINK.find(text, i)
            val boldStart = text.indexOf("**", i)
            // Choose whichever marker comes first.
            val linkStart = linkMatch?.range?.first ?: Int.MAX_VALUE
            val boldAt = if (boldStart >= 0) boldStart else Int.MAX_VALUE
            if (linkStart == Int.MAX_VALUE && boldAt == Int.MAX_VALUE) {
                spans += Span.Text(text.substring(i)); break
            }
            if (linkStart <= boldAt) {
                val lm = linkMatch!!
                if (linkStart > i) spans += Span.Text(text.substring(i, linkStart))
                spans += Span.Link(lm.groupValues[1], lm.groupValues[2])
                i = lm.range.last + 1
            } else {
                val end = text.indexOf("**", boldAt + 2)
                if (end < 0) { spans += Span.Text(text.substring(i)); break }
                if (boldAt > i) spans += Span.Text(text.substring(i, boldAt))
                spans += Span.Bold(text.substring(boldAt + 2, end))
                i = end + 2
            }
        }
        return spans
    }
}
