package de.emilyshub.languages

import de.emilyshub.data.InputText

/**
 * How an answer typed in the practice compares with the side asked for (SPEC.md §15; #153): only a suggestion, the
 * rating stays with the person. Compared as the search compares ([InputText.searchable]): case, spaces at the edges and
 * Arabic vowel marks do not count. Of a side with several meanings split by comma or semicolon ("Buch, Heft"), one is
 * enough, as is all of it.
 */
enum class TypedAnswer {
    /** The same, but for case and spaces. */
    Right,

    /** The same but for the Arabic vowel marks, which the person may want to see. */
    RightButMarks,

    Wrong,
    ;

    companion object {
        /** What splits a side into its meanings: comma and semicolon, also as Arabic writes them. */
        private val MEANINGS = Regex("[,;،؛]")

        /** How [typed] compares with [answer], the side asked for. */
        fun check(typed: String, answer: String): TypedAnswer {
            val given = InputText.singleLine(typed)
            if (!InputText.isVisible(given)) return Wrong
            val meanings = (answer.split(MEANINGS) + answer).map(InputText::singleLine).filter(InputText::isVisible)
            return meanings.minOfOrNull { compare(given, it) } ?: Wrong
        }

        private fun compare(given: String, meaning: String): TypedAnswer = when {
            spelt(given) == spelt(meaning) -> Right
            InputText.searchable(given) == InputText.searchable(meaning) -> RightButMarks
            else -> Wrong
        }

        /** [text] as compared with its marks: as the search has it, but with them. */
        private fun spelt(text: String) = InputText.comparable(text).replace("ß", "ss")
    }
}
