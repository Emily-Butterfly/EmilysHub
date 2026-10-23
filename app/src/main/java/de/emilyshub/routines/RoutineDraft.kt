package de.emilyshub.routines

import java.io.Serializable
import java.time.LocalTime

enum class DraftProblem { NameMissing, NameTooLong, DescriptionTooLong }

/**
 * A routine as typed into the editor, before it is checked. The checks apply to the [cleaned]
 * texts, and saving refuses a draft with [problems] (SPEC.md §9: strict input checks).
 * Serializable, so that the editor keeps it across rotation and the app lock.
 */
data class RoutineDraft(val name: String, val description: String, val time: LocalTime) : Serializable {

    val problems: Set<DraftProblem>
        get() = with(cleaned()) {
            buildSet {
                if (name.none(::isVisible)) add(DraftProblem.NameMissing)
                if (lengthOf(name) > NAME_MAX) add(DraftProblem.NameTooLong)
                if (lengthOf(description) > DESCRIPTION_MAX) add(DraftProblem.DescriptionTooLong)
            }
        }

    /** Without surrounding spaces; the name on a single line. */
    fun cleaned() = copy(name = name.trim().replace(WHITESPACE, " "), description = description.trim())

    companion object {
        const val NAME_MAX = 80
        const val DESCRIPTION_MAX = 1000
        private val WHITESPACE = Regex("\\s+")

        /** Characters as people count them: an emoji is one, not the two UTF-16 units it takes. */
        fun lengthOf(text: String): Int = text.codePointCount(0, text.length)

        // Zero-width spaces and joiners are "format" characters: they print nothing on their own.
        private fun isVisible(char: Char) = !char.isWhitespace() && Character.getType(char) != Character.FORMAT.toInt()
    }
}
