package de.emilyshub.doku

import de.emilyshub.data.InputText
import java.time.LocalDate

/**
 * A measurement of [test] as its form asks for it (SPEC.md §14): a field for each of the [fields], the values not
 * retired and, when changing one, a retired value it has a number for; and the sum, if the test [formsSum]. A number
 * left as it was [initial]ly keeps counting, though its value has changed since to take it no longer: old measurements
 * keep their numbers.
 */
data class MeasurementForm(val test: ClinicalTest, val fields: List<TestValue>, val formsSum: Boolean, val initial: MeasurementDraft) {

    /** What keeps [draft] from being saved: a number not fit for its value, or none at all. */
    fun problems(draft: MeasurementDraft): MeasurementProblems {
        val values = buildMap {
            fields.forEach { value ->
                val typed = draft.typed[value.id].orEmpty()
                if (typed.isBlank() || keeps(value, typed)) return@forEach
                val number = TestNumber.parse(typed, value.decimals)
                when {
                    number == null -> put(value.id, FieldProblem.NotANumber)
                    !value.allows(number) -> put(value.id, FieldProblem.OutOfRange)
                }
            }
        }
        val anything = fields.any { draft.typed[it.id]?.isNotBlank() == true }
        return MeasurementProblems(values, nothingEntered = !anything, remarkTooLong = InputText.lengthOf(draft.cleanedRemark()) > REMARK_MAX)
    }

    /** The numbers of [draft], by value, those left empty or not fit left out. */
    fun numbers(draft: MeasurementDraft): Map<Long, Long> = fields.mapNotNull { value ->
        val typed = draft.typed[value.id].orEmpty()
        val number = if (keeps(value, typed)) TestNumber.parse(typed, decimals = true) else TestNumber.parse(typed, value.decimals)?.takeIf(value::allows)
        number?.let { value.id to it }
    }.toMap()

    /** Whether [typed] is the number [value] had on opening, kept as it was. */
    private fun keeps(value: TestValue, typed: String): Boolean = typed.isNotBlank() && typed == initial.typed[value.id]

    /** The sum of [draft] as it is typed: once every value not retired has its number; none until then, or without a sum. */
    fun sum(draft: MeasurementDraft): Long? = if (formsSum) sumOf(fields.filterNot(TestValue::retired), numbers(draft)) else null

    companion object {
        /** Some half a page. */
        const val REMARK_MAX = 2_000
    }
}

/** A measurement as typed: the [typed] numbers by value id, and a [remark]. */
data class MeasurementDraft(val typed: Map<Long, String> = emptyMap(), val remark: String = "") {
    /** The remark without trailing spaces. */
    fun cleanedRemark(): String = InputText.multiLine(remark)
}

/** What keeps a measurement from being saved, the [values]' by id. */
data class MeasurementProblems(val values: Map<Long, FieldProblem>, val nothingEntered: Boolean, val remarkTooLong: Boolean) {
    /** Whether there is none: it can be saved. */
    val isEmpty: Boolean get() = values.isEmpty() && !nothingEntered && !remarkTooLong
}

/** What is wrong with a number typed for a value. */
enum class FieldProblem {
    /** No number, or one with more decimals than the value allows. */
    NotANumber,

    /** Outside the value's range. */
    OutOfRange,
}

/** Whether [number] lies within the value's range, as far as it has one. */
fun TestValue.allows(number: Long): Boolean = (min == null || number >= min) && (max == null || number <= max)

/** The sum of [numbers] over [values], once each of them has one; none until then. */
fun sumOf(values: List<TestValue>, numbers: Map<Long, Long>): Long? =
    if (values.isNotEmpty() && values.all { it.id in numbers }) values.sumOf { numbers.getValue(it.id) } else null

/** How a value, or a sum, changed from its first measurement to its last. */
sealed interface Change {
    /** Measured once only: nothing to compare. */
    data object MeasuredOnce : Change

    /** The same as at first. */
    data object Unchanged : Change

    /** By [by], in hundredths, the [better] way or not, since [since]. */
    data class Changed(val by: Long, val better: Boolean, val since: LocalDate) : Change
}

/** The change from [first] to [last], first measured on [since], which is better the [direction] way; none if [measuredOnce]. */
fun changeOf(first: Long, last: Long, since: LocalDate, direction: Direction, measuredOnce: Boolean): Change {
    if (measuredOnce) return Change.MeasuredOnce
    val by = last - first
    if (by == 0L) return Change.Unchanged
    return Change.Changed(by, better = (by > 0) == (direction == Direction.HigherIsBetter), since = since)
}
