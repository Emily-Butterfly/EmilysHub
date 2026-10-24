package de.emilyshub.data

import java.text.Collator
import java.util.Locale

/** What the strict input checks (SPEC.md §9) make of typed text, the same in every area. */
object InputText {
    /** Characters as people count them: an emoji is one, not the two UTF-16 units it takes. */
    fun lengthOf(text: String): Int = text.codePointCount(0, text.length)

    /** On a single line, without surrounding spaces. */
    fun singleLine(text: String): String = text.trim().replace(WHITESPACE, " ")

    /** Whether it shows anything: zero-width spaces and joiners are "format" characters, which print nothing on their own. */
    fun isVisible(text: String): Boolean = text.any { !it.isWhitespace() && Character.getType(it) != Character.FORMAT.toInt() }

    /** German alphabetical order, with umlauts next to their base letters. */
    val byName: Collator = Collator.getInstance(Locale.GERMAN)

    private val WHITESPACE = Regex("\\s+")
}
