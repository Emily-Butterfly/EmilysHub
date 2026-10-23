package de.emilyshub.routines

import java.time.LocalTime

enum class DraftProblem { NameMissing, NameTooLong, DescriptionTooLong }

/**
 * A routine as typed into the editor, before it is checked. The checks apply to the [cleaned]
 * texts, and saving refuses a draft with [problems] (SPEC.md §9: strict input checks).
 */
data class RoutineDraft(val name: String, val description: String, val time: LocalTime) {

    val problems: Set<DraftProblem>
        get() = with(cleaned()) {
            buildSet {
                if (name.isEmpty()) add(DraftProblem.NameMissing)
                if (name.length > NAME_MAX) add(DraftProblem.NameTooLong)
                if (description.length > DESCRIPTION_MAX) add(DraftProblem.DescriptionTooLong)
            }
        }

    /** Without surrounding spaces; the name on a single line. */
    fun cleaned() = copy(name = name.trim().replace(WHITESPACE, " "), description = description.trim())

    companion object {
        const val NAME_MAX = 80
        const val DESCRIPTION_MAX = 1000
        private val WHITESPACE = Regex("\\s+")
    }
}
