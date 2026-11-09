package de.emilyshub.doku.assessments

/** What was answered to one item of a form; nothing answered is no answer at all. */
sealed interface Answer {
    /** Option [optionId] of a [ChoiceItem]. */
    data class Chosen(val optionId: String) : Answer

    /** Yes or no, to a [YesNoItem]. */
    data class YesNo(val yes: Boolean) : Answer

    /** The number on a [ScaleItem]. */
    data class OnScale(val value: Int) : Answer

    /** The options checked of a [ChecklistItem], and something [other] in words. */
    data class Checked(val optionIds: Set<String>, val other: String = "") : Answer

    /** A number, in hundredths, for a [NumberItem]. */
    data class Number(val hundredths: Long) : Answer

    /** Words, for a [TextItem]. */
    data class Text(val text: String) : Answer

    /** The [regions] checked of a [BodyRegionsItem], and where else in words. */
    data class Regions(val regions: Set<BodyRegion>, val text: String = "") : Answer

    /** The goals of a [GoalsItem], in their order. */
    data class Goals(val goals: List<Goal>) : Answer
}

/** A goal in [text], rated at the [level] of its points, such as +1 "Ziel übertroffen"; none while unrated. */
data class Goal(val text: String, val level: Int? = null) {
    /** Without words and level: no goal at all, neither stored nor counted. */
    val isEmpty: Boolean get() = text.isBlank() && level == null
}

/** With option [optionId] of [item] checked, or unchecked if it was: an exclusive one, such as "nein", stands alone. */
fun Answer.Checked.toggled(item: ChecklistItem, optionId: String): Answer.Checked {
    if (optionId in optionIds) return copy(optionIds = optionIds - optionId)
    val exclusive = item.options.filter(Option::exclusive).map(Option::id).toSet()
    val kept = if (optionId in exclusive) emptySet() else optionIds - exclusive
    return copy(optionIds = kept + optionId)
}
