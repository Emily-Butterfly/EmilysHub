package de.emilyshub.data

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** What the strict input checks (SPEC.md §9) make of typed text, the same in every area. */
object InputText {
    /** Characters as people count them: an emoji is one, not the two UTF-16 units it takes. */
    fun lengthOf(text: String): Int = text.codePointCount(0, text.length)

    /**
     * On a single line, without surrounding spaces, an "Ä" typed as "A" with a combining umlaut as one "Ä", and without
     * the [STRAY] characters that print nothing.
     */
    fun singleLine(text: String): String =
        Normalizer.normalize(text.filterNot { it in STRAY }.trim().replace(WHITESPACE, " "), Normalizer.Form.NFC)

    /** Text of several lines, such as a description, as [singleLine] has it but on its lines and with leading spaces kept. */
    fun multiLine(text: String): String = Normalizer.normalize(text.filterNot { it in STRAY }.trimEnd(), Normalizer.Form.NFC)

    /** Whether it shows anything: zero-width spaces and joiners are "format" characters, which print nothing on their own. */
    fun isVisible(text: String): Boolean = text.any { !it.isWhitespace() && !isFormat(it) }

    /** Whether [a] and [b] read the same: in whatever case, however their letters were typed, and leaving out what prints nothing. */
    fun readTheSame(a: String, b: String): Boolean = comparable(a) == comparable(b)

    /**
     * [text] as [readTheSame] compares it: in lower case, its letters typed one way, without what prints nothing, and
     * "ß" as "ss", as "STRASSE" reads "Straße".
     */
    fun comparable(text: String) =
        Normalizer.normalize(text, Normalizer.Form.NFKC).filterNot(::isFormat).lowercase(Locale.GERMAN).replace("ß", "ss")

    private fun isFormat(char: Char) = Character.getType(char) == Character.FORMAT.toInt()

    /**
     * What keeps [name], on a single line, from naming something of which no two may read the same, such as a tag: one
     * of the names [taken], or more than [max] characters; none if it can.
     */
    fun nameProblem(name: String, max: Int, taken: Collection<String>): NameProblem? {
        val cleaned = singleLine(name)
        return when {
            !isVisible(cleaned) -> NameProblem.Missing
            lengthOf(cleaned) > max -> NameProblem.TooLong
            taken.any { readTheSame(it, cleaned) } -> NameProblem.Taken
            else -> null
        }
    }

    /** German alphabetical order, with umlauts next to their base letters. */
    val byName: Collator = Collator.getInstance(Locale.GERMAN)

    private val WHITESPACE = Regex("\\s+")

    /**
     * What prints nothing and only gets in the way, mostly pasted along: zero-width spaces, word joiners, byte order marks
     * and soft hyphens. The joiners that emoji and some scripts need, and the marks of writing direction, stay.
     */
    private val STRAY = setOf('\u200B', '\u2060', '\uFEFF', '\u00AD')
}

/** What keeps a name from being given, as [InputText.nameProblem] finds it. */
enum class NameProblem {
    Missing,
    TooLong,
    Taken,
}
