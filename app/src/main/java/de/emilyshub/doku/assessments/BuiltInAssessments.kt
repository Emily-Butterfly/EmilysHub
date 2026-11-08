package de.emilyshub.doku.assessments

import de.emilyshub.data.InputText

/**
 * The forms built into the app (SPEC.md §14), from the "Scores" of DIGOTOR's downloads; their content, not the PDFs.
 * The rest follow by subcategory (#67–#76).
 */
object BuiltInAssessments {

    /** Every built-in form, by name. */
    val all: List<Assessment> = (listOf(ANAMNESIS_V8, PHQ_9) + SPINE + KNEE_HIP).sortedWith(compareBy(InputText.byName) { it.name })

    private val byId = all.associateBy(Assessment::id)

    /** The form [id] names, if this version of the app has it. */
    fun byId(id: String): Assessment? = byId[id]

    /** The forms to offer, but those [hidden], by category in DIGOTOR's order, each by name; one in several in each. */
    fun byCategory(hidden: Set<String>): Map<AssessmentCategory, List<Assessment>> =
        AssessmentCategory.entries.associateWith { category -> all.filter { category in it.categories && it.id !in hidden } }
            .filterValues(List<Assessment>::isNotEmpty)

    /** The forms to offer whose name or abbreviation holds every word of [typed], in whatever case; nothing for nothing typed. */
    fun search(typed: String, hidden: Set<String>): List<Assessment> {
        val words = InputText.searchable(typed).split(' ').filter(String::isNotEmpty)
        if (words.isEmpty()) return emptyList()
        return all.filter { form ->
            val text = InputText.searchable(listOfNotNull(form.name, form.abbreviation).joinToString(" "))
            form.id !in hidden && words.all { it in text }
        }
    }
}
