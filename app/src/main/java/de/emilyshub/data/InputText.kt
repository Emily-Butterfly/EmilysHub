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

    private fun comparable(text: String) =
        Normalizer.normalize(text, Normalizer.Form.NFKC).filterNot(::isFormat).lowercase(Locale.GERMAN)

    private fun isFormat(char: Char) = Character.getType(char) == Character.FORMAT.toInt()

    /** German alphabetical order, with umlauts next to their base letters. */
    val byName: Collator = Collator.getInstance(Locale.GERMAN)

    private val WHITESPACE = Regex("\\s+")
}
