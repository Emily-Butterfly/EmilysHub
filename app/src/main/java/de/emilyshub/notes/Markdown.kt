package de.emilyshub.notes

/** A block of a note's Markdown (SPEC.md §7), to show one below the other. */
sealed interface Block {
    data class Heading(val level: Int, val text: List<Inline>) : Block

    data class Paragraph(val text: List<Inline>) : Block

    data class Bullets(val items: List<ListItem>) : Block

    data class Numbered(val start: Int, val items: List<ListItem>) : Block

    /** Quoted text; quotes within quotes are one quote. */
    data class Quote(val text: List<Inline>) : Block

    /** Text as written, in a monospace font. */
    data class Code(val text: String) : Block

    data object Rule : Block
}

/** An item of a list, ticked off or not if it is a task ("- [x]"), else neither. */
data class ListItem(val text: List<Inline>, val checked: Boolean? = null)

/** A part of a line of Markdown. */
sealed interface Inline {
    data class Text(val text: String) : Inline

    data class Bold(val content: List<Inline>) : Inline

    data class Italic(val content: List<Inline>) : Inline

    data class Code(val text: String) : Inline

    /** The words of a link, or the description of an image; where it leads is left out, as nothing is opened (SPEC.md §9). */
    data class Link(val content: List<Inline>) : Inline
}

/**
 * The Markdown of notes (SPEC.md §7, §9): headings, paragraphs, lists with tasks, quotes, code and rules; bold, italic,
 * code and links within lines. There is no HTML: it stays text, as written, and nothing is ever opened or loaded. Read
 * in one pass with a bounded depth, so that hostile or very large text is read in time.
 */
object Markdown {

    fun parse(text: String): List<Block> = BlockReader(text.replace("\r\n", "\n").split('\n')).read()

    fun inline(text: String): List<Inline> = InlineReader(text).read()

    private class BlockReader(private val lines: List<String>) {
        private var at = 0
        private val blocks = mutableListOf<Block>()

        fun read(): List<Block> {
            while (at < lines.size) {
                val line = lines[at]
                when {
                    line.isBlank() -> at++
                    isFence(line) -> code(fence = line.trimStart().take(FENCE.length))
                    isRule(line) -> {
                        blocks += Block.Rule
                        at++
                    }
                    heading(line) != null -> {
                        blocks += checkNotNull(heading(line))
                        at++
                    }
                    line.trimStart().startsWith('>') -> quote()
                    bullet(line) != null -> bullets()
                    numbered(line) != null -> numbered()
                    else -> paragraph()
                }
            }
            return blocks
        }

        private fun code(fence: String) {
            at++
            val code = mutableListOf<String>()
            while (at < lines.size && !lines[at].trimStart().startsWith(fence)) code += lines[at++]
            at++
            blocks += Block.Code(code.joinToString("\n"))
        }

        private fun quote() {
            val quoted = mutableListOf<String>()
            while (at < lines.size && lines[at].trimStart().startsWith('>')) {
                quoted += lines[at++].trimStart().trimStart('>', ' ')
            }
            blocks += Block.Quote(inline(quoted.joinToString("\n")))
        }

        private fun bullets() {
            val items = mutableListOf<ListItem>()
            while (at < lines.size) items += bullet(lines[at])?.also { at++ } ?: break
            blocks += Block.Bullets(items)
        }

        private fun numbered() {
            val start = checkNotNull(numbered(lines[at])).first
            val items = mutableListOf<ListItem>()
            while (at < lines.size) items += numbered(lines[at])?.second?.also { at++ } ?: break
            blocks += Block.Numbered(start, items)
        }

        private fun paragraph() {
            val text = mutableListOf<String>()
            while (at < lines.size && startsParagraphLine(lines[at], first = text.isEmpty())) text += lines[at++].trim()
            blocks += Block.Paragraph(inline(text.joinToString("\n")))
        }

        /** Whether the line goes on the paragraph: the first always does; the others unless blank or starting a block. */
        private fun startsParagraphLine(line: String, first: Boolean) = first || !line.isBlank() && !isFence(line) &&
            !isRule(line) && heading(line) == null && !line.trimStart().startsWith('>') &&
            bullet(line) == null && numbered(line) == null
    }

    private fun isFence(line: String) = line.trimStart().let { it.startsWith(FENCE) || it.startsWith(TILDE_FENCE) }

    /** Three or more of the same of "-", "*" or "_", with nothing else but spaces. */
    private fun isRule(line: String): Boolean {
        val marks = line.filterNot(Char::isWhitespace)
        return marks.length >= RULE_MARKS && marks.first() in "-*_" && marks.all { it == marks.first() }
    }

    private fun heading(line: String): Block.Heading? {
        val trimmed = line.trimStart()
        val level = trimmed.takeWhile { it == '#' }.length
        if (level !in 1..MAX_HEADING || trimmed.length > level && !trimmed[level].isWhitespace()) return null
        return Block.Heading(level, inline(trimmed.drop(level).trim().trimEnd('#').trim()))
    }

    /** A bullet ("- ", "* " or "+ "), maybe a task ("- [ ] ", "- [x] "). */
    private fun bullet(line: String): ListItem? {
        val trimmed = line.trimStart()
        if (trimmed.length < 2 || trimmed[0] !in "-*+" || !trimmed[1].isWhitespace()) return null
        val rest = trimmed.drop(2).trimStart()
        val isTask = rest.length >= TASK.length && rest[0] == '[' && rest[1] in " xX" && rest[2] == ']' &&
            (rest.length == TASK.length || rest[TASK.length].isWhitespace())
        return if (isTask) ListItem(inline(rest.drop(TASK.length).trimStart()), checked = rest[1] != ' ') else ListItem(inline(rest))
    }

    /** A numbered item ("1. ", "2) "), with its number. */
    private fun numbered(line: String): Pair<Int, ListItem>? {
        val trimmed = line.trimStart()
        val digits = trimmed.takeWhile(Char::isDigit)
        if (digits.isEmpty() || digits.length > MAX_DIGITS) return null
        val rest = trimmed.drop(digits.length)
        if (rest.length < 2 || rest[0] !in ".)" || !rest[1].isWhitespace()) return null
        return digits.toInt() to ListItem(inline(rest.drop(2).trimStart()))
    }

    /**
     * Reads a line's emphasis, code and links in one pass: an opening mark waits on a stack for its closing one, up to a
     * bounded depth; one never closed stays as written.
     */
    private class InlineReader(private val text: String) {
        private class Frame(val mark: String, val content: MutableList<Inline> = mutableListOf())

        private val stack = mutableListOf(Frame(""))
        private val plain = StringBuilder()
        private val lastBacktick = text.lastIndexOf('`')
        private var at = 0

        fun read(): List<Inline> {
            while (at < text.length) {
                val c = text[at]
                when {
                    c == '\\' && at + 1 < text.length && text[at + 1] in ESCAPABLE -> {
                        plain.append(text[at + 1])
                        at += 2
                    }
                    c == '`' -> code()
                    c == '*' || c == '_' -> emphasis(c)
                    c == '!' && text.startsWith("[", at + 1) -> {
                        at++
                        open("[")
                    }
                    c == '[' -> open("[")
                    c == ']' && text.startsWith("(", at + 1) -> link()
                    else -> {
                        plain.append(c)
                        at++
                    }
                }
            }
            flush()
            while (stack.size > 1) collapseTop()
            return merged(stack.single().content)
        }

        private fun code() {
            val end = if (at < lastBacktick) text.indexOf('`', at + 1) else -1
            if (end < 0) {
                plain.append('`')
                at++
                return
            }
            flush()
            top().content += Inline.Code(text.substring(at + 1, end))
            at = end + 1
        }

        private fun emphasis(c: Char) {
            val mark = if (text.startsWith("$c$c", at)) "$c$c" else "$c"
            val before = text.getOrNull(at - 1)
            val after = text.getOrNull(at + mark.length)
            // Not within a word for "_", and not before or after a space, as in "2 * 3".
            val canOpen = after != null && !after.isWhitespace() && (c == '*' || before?.isLetterOrDigit() != true)
            val canClose = before != null && !before.isWhitespace() && (c == '*' || after?.isLetterOrDigit() != true)
            val opened = stack.indexOfLast { it.mark == mark }
            at += mark.length
            when {
                canClose && opened > 0 -> close(opened) { if (mark.length == 2) Inline.Bold(it) else Inline.Italic(it) }
                canOpen -> open(mark, advance = false)
                else -> plain.append(mark)
            }
        }

        /** "](" after "[": a link whose target goes up to its ")" is closed; its target is left out. */
        private fun link() {
            val opened = stack.indexOfLast { it.mark == "[" }
            val end = if (opened > 0) targetEnd(at + 2) else -1
            if (end < 0) {
                plain.append("](")
                at += 2
                return
            }
            at = end + 1
            close(opened) { Inline.Link(it) }
        }

        /** Where the target starting at [from] ends with its ")", parentheses within counted; none if not soon or not at all. */
        private fun targetEnd(from: Int): Int {
            var depth = 0
            for (i in from until minOf(text.length, from + MAX_TARGET)) {
                when (text[i]) {
                    '(' -> depth++
                    ')' -> if (depth == 0) return i else depth--
                    ' ', '\n', '\t' -> return -1
                }
            }
            return -1
        }

        private fun open(mark: String, advance: Boolean = true) {
            if (advance) at += mark.length
            if (stack.size > MAX_DEPTH) {
                plain.append(mark)
                return
            }
            flush()
            stack += Frame(mark)
        }

        private fun close(opened: Int, make: (List<Inline>) -> Inline) {
            flush()
            while (stack.size - 1 > opened) collapseTop()
            val frame = stack.removeAt(stack.lastIndex)
            top().content += make(merged(frame.content))
        }

        /** A mark never closed: it and what came after it stay as written. */
        private fun collapseTop() {
            flush()
            val frame = stack.removeAt(stack.lastIndex)
            top().content += Inline.Text(frame.mark)
            top().content += frame.content
        }

        private fun flush() {
            if (plain.isEmpty()) return
            top().content += Inline.Text(plain.toString())
            plain.clear()
        }

        private fun top() = stack.last()

        /** [parts] with text next to text as one, and without empty text. */
        private fun merged(parts: List<Inline>): List<Inline> {
            val merged = mutableListOf<Inline>()
            parts.forEach { part ->
                val last = merged.lastOrNull()
                if (part is Inline.Text && last is Inline.Text) {
                    merged[merged.lastIndex] = Inline.Text(last.text + part.text)
                } else {
                    merged += part
                }
            }
            return merged.filterNot { it is Inline.Text && it.text.isEmpty() }
        }
    }

    private const val FENCE = "```"
    private const val TILDE_FENCE = "~~~"
    private const val RULE_MARKS = 3
    private const val MAX_HEADING = 6
    private const val MAX_DIGITS = 9

    /** As a task starts, "[ ]" or "[x]". */
    private const val TASK = "[ ]"

    /** As deep as emphasis and links nest; deeper, the marks stay as written. */
    private const val MAX_DEPTH = 32

    /** The longest target of a link; a longer one is no link. */
    private const val MAX_TARGET = 2_000
    private const val ESCAPABLE = "\\`*_[]()#+-.!>~|{}"
}
