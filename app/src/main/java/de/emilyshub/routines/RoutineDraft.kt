package de.emilyshub.routines

import java.io.Serializable
import java.time.LocalTime

enum class DraftProblem {
    NameMissing,
    NameTooLong,
    DescriptionTooLong,
    TimesMissing,
    TooManyTimes,
    WeekdaysMissing,
    IntervalOutOfRange,
}

/**
 * A routine as typed into the editor, before it is checked. The checks apply to the [cleaned]
 * draft, and saving refuses a draft with [problems] (SPEC.md §9: strict input checks).
 * Serializable, so that the editor keeps it across rotation and the app lock.
 */
data class RoutineDraft(
    val name: String,
    val description: String,
    val times: List<LocalTime>,
    val repetition: Repetition,
) : Serializable {

    val problems: Set<DraftProblem>
        get() = with(cleaned()) {
            buildSet {
                if (name.none(::isVisible)) add(DraftProblem.NameMissing)
                if (lengthOf(name) > NAME_MAX) add(DraftProblem.NameTooLong)
                if (lengthOf(description) > DESCRIPTION_MAX) add(DraftProblem.DescriptionTooLong)
                if (times.isEmpty()) add(DraftProblem.TimesMissing)
                if (times.size > TIMES_MAX) add(DraftProblem.TooManyTimes)
                when (val repetition = repetition) {
                    is Repetition.Weekdays -> if (repetition.days.isEmpty()) add(DraftProblem.WeekdaysMissing)
                    is Repetition.EveryNDays -> if (repetition.days !in INTERVAL_DAYS) add(DraftProblem.IntervalOutOfRange)
                    Repetition.Daily, is Repetition.Once -> Unit
                }
            }
        }

    /** Without surrounding spaces; the name on a single line; the times in the order of the day, each once. */
    fun cleaned() = copy(
        name = name.trim().replace(WHITESPACE, " "),
        description = description.trim(),
        times = times.distinct().sorted(),
    )

    companion object {
        const val NAME_MAX = 80
        const val DESCRIPTION_MAX = 1000

        /** One every hour. */
        const val TIMES_MAX = 24

        /** Every day is "daily"; a year is the longest gap. */
        val INTERVAL_DAYS = 2..365

        private val WHITESPACE = Regex("\\s+")

        /** Characters as people count them: an emoji is one, not the two UTF-16 units it takes. */
        fun lengthOf(text: String): Int = text.codePointCount(0, text.length)

        // Zero-width spaces and joiners are "format" characters: they print nothing on their own.
        private fun isVisible(char: Char) = !char.isWhitespace() && Character.getType(char) != Character.FORMAT.toInt()
    }
}
