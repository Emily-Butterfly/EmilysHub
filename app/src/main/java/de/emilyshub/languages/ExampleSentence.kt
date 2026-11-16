package de.emilyshub.languages

import de.emilyshub.data.InputText

/**
 * A card's Beispielsatz (SPEC.md §15; #155): where its front stands in it, found as the search finds a word, in
 * whatever case and Arabic with or without its vowel marks ([InputText.searchable]); and the sentence with a gap there,
 * "…", for the practice asking from the back. A front that does not stand so in the sentence, as one inflected there,
 * is not found: the practice then shows the sentence only once the card is uncovered.
 */
object ExampleSentence {
    /** What stands in a sentence's place of the front, asked from the back. */
    const val GAP = "…"

    /**
     * Where [front] stands in [sentence], from its first letter to its last with the marks on that: the first place
     * where it is a word of its own, else the first within another word, as the search finds it there too; none if it
     * does not stand there.
     */
    fun wordIn(sentence: String, front: String): IntRange? {
        val word = InputText.searchable(front).trim()
        if (word.isEmpty()) return null
        // The sentence as the search compares it, with the place in the sentence each of its letters comes from.
        val folded = StringBuilder()
        val origins = mutableListOf<Int>()
        var at = 0
        while (at < sentence.length) {
            val next = sentence.offsetByCodePoints(at, 1)
            InputText.searchable(sentence.substring(at, next)).forEach {
                folded.append(it)
                origins += at
            }
            at = next
        }
        val found = generateSequence(folded.indexOf(word).takeIf { it >= 0 }) { from ->
            folded.indexOf(word, from + 1).takeIf { it >= 0 }
        }.map { start -> rangeOf(sentence, origins[start], origins[start + word.length - 1]) }.toList()
        return found.firstOrNull { wholeWord(sentence, it) } ?: found.firstOrNull()
    }

    /**
     * From [first] to the end of the character at [last] in [sentence], and over the marks on it, such as the vowel
     * marks on a last letter, which the search leaves out, or a dot above that no letter of its own has.
     */
    private fun rangeOf(sentence: String, first: Int, last: Int): IntRange {
        var end = sentence.offsetByCodePoints(last, 1)
        while (end < sentence.length && isMark(sentence, end)) end = sentence.offsetByCodePoints(end, 1)
        return first until end
    }

    /** Whether [range] of [sentence] has no letter or digit right before or after it, a letter's marks with it. */
    private fun wholeWord(sentence: String, range: IntRange): Boolean {
        var start = range.first
        while (start > 0 && isMark(sentence, sentence.offsetByCodePoints(start, -1))) {
            start = sentence.offsetByCodePoints(start, -1)
        }
        val before = start > 0 && Character.isLetterOrDigit(sentence.codePointBefore(start))
        val after = range.last + 1 < sentence.length && Character.isLetterOrDigit(sentence.codePointAt(range.last + 1))
        return !before && !after
    }

    /**
     * Whether the character at [at] of [sentence] belongs to the letter before it: a mark, or what the search leaves
     * out.
     */
    private fun isMark(sentence: String, at: Int): Boolean {
        val char = sentence.codePointAt(at)
        return Character.getType(char).toByte() in MARKS || InputText.searchable(Character.toString(char)).isEmpty()
    }

    /** The kinds of characters Unicode has for marks on a letter. */
    private val MARKS = setOf(Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK)
}
