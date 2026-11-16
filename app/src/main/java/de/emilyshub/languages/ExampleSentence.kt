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

    /** [sentence] with [GAP] in place of [front]; none if the front does not stand in it. */
    fun withGap(sentence: String, front: String): String? = wordIn(sentence, front)?.let { sentence.replaceRange(it, GAP) }

    /**
     * From [first] to the end of the character at [last] in [sentence], and over what follows it that the search leaves
     * out, such as the vowel marks on a last letter.
     */
    private fun rangeOf(sentence: String, first: Int, last: Int): IntRange {
        var end = sentence.offsetByCodePoints(last, 1)
        while (end < sentence.length) {
            val next = sentence.offsetByCodePoints(end, 1)
            if (InputText.searchable(sentence.substring(end, next)).isNotEmpty()) break
            end = next
        }
        return first until end
    }

    /** Whether [range] of [sentence] has no letter or digit right before or after it. */
    private fun wholeWord(sentence: String, range: IntRange): Boolean {
        val before = range.first > 0 && Character.isLetterOrDigit(sentence.codePointBefore(range.first))
        val after = range.last + 1 < sentence.length && Character.isLetterOrDigit(sentence.codePointAt(range.last + 1))
        return !before && !after
    }
}
