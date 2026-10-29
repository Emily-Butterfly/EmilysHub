package de.emilyshub.data

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** What the strict input checks (SPEC.md §9) make of typed text, the same in every area. */
object InputText {
    /** Characters as people count them: an emoji is one, not the two UTF-16 units it takes. */
    fun lengthOf(text: String): Int = text.codePointCount(0, text.length)

    /** On a single line, without surrounding spaces, and an "Ä" typed as "A" with a combining umlaut as one "Ä". */
    fun singleLine(text: String): String = Normalizer.normalize(text.trim().replace(WHITESPACE, " "), Normalizer.Form.NFC)

    /** Whether it shows anything: zero-width spaces and joiners are "format" characters, which print nothing on their own. */
    fun isVisible(text: String): Boolean = text.any { !it.isWhitespace() && !isFormat(it) }

    /** Whether [a] and [b] read the same: in whatever case, however their letters were typed, and leaving out what prints nothing. */
    fun readTheSame(a: String, b: String): Boolean = comparable(a) == comparable(b)

    /** [text] as [readTheSame] compares it: in lower case, its letters typed one way, without what prints nothing. */
    fun comparable(text: String) =
        Normalizer.normalize(text, Normalizer.Form.NFKC).filterNot(::isFormat).lowercase(Locale.GERMAN)

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
}

/** What keeps a name from being given, as [InputText.nameProblem] finds it. */
enum class NameProblem {
    Missing,
    TooLong,
    Taken,
}
