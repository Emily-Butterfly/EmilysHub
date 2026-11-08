package de.emilyshub.doku

import de.emilyshub.data.InputText
import de.emilyshub.data.NameProblem

/**
 * A test as typed in its editor, before it is checked (SPEC.md §9): its [name], its [values] in their order, the
 * retired ones among them, and whether to form the [sum].
 */
data class TestDraft(
    val name: String = "",
    val values: List<ValueDraft> = listOf(ValueDraft()),
    val sum: Boolean = false,
    val sumDirection: Direction = Direction.HigherIsBetter,
) {
    /** The values new measurements ask for. */
    val activeValues: List<ValueDraft> get() = values.filterNot(ValueDraft::retired)

    /** Whether a sum can be formed: of two values or more; asked for with fewer, it waits for a second one. */
    val canSum: Boolean get() = activeValues.size >= 2

    /**
     * Whether it changes what measurements of [stored], the test as it was, read by: its name, its sum, or a value it
     * had, changed or removed. Adding a value or reordering them does not.
     */
    fun changesStored(stored: TestDraft): Boolean {
        val values = values.filter { it.id != null }.associateBy(ValueDraft::id)
        return name != stored.name || sum != stored.sum || sumDirection != stored.sumDirection ||
            stored.values.any { it != values[it.id] }
    }

    /** What keeps it from being saved, the names of the other tests [taken]. */
    fun problems(taken: Collection<String>): TestProblems = TestProblems(
        name = InputText.nameProblem(name, NAME_MAX, taken),
        values = values.mapIndexed { index, value ->
            value.problems(taken = values.filterIndexed { other, _ -> other != index }.map(ValueDraft::name))
        },
        noActiveValue = activeValues.isEmpty(),
    )

    companion object {
        /** As long as a test's name may be. */
        const val NAME_MAX = 80
    }
}

/**
 * A value as typed: [id] if it is stored already; its [min] and [max] as the digits typed, none for none; [retired] if
 * it was measured and then removed, until it is activated again.
 */
data class ValueDraft(
    val id: Long? = null,
    val name: String = "",
    val unit: String = "",
    val min: String = "",
    val max: String = "",
    val decimals: Boolean = false,
    val direction: Direction = Direction.HigherIsBetter,
    val retired: Boolean = false,
) {
    /** What keeps it from being saved, the names of the test's other values [taken]. */
    fun problems(taken: Collection<String>): Set<ValueProblem> = buildSet {
        when (InputText.nameProblem(name, NAME_MAX, taken)) {
            NameProblem.Missing -> add(ValueProblem.NameMissing)
            NameProblem.TooLong -> add(ValueProblem.NameTooLong)
            NameProblem.Taken -> add(ValueProblem.NameTaken)
            null -> Unit
        }
        if (InputText.lengthOf(cleanedUnit()) > UNIT_MAX) add(ValueProblem.UnitTooLong)
        if (!isNumberOrNone(min)) add(ValueProblem.MinInvalid)
        if (!isNumberOrNone(max)) add(ValueProblem.MaxInvalid)
        val from = cleanedMin()
        val to = cleanedMax()
        if (from != null && to != null && from > to) add(ValueProblem.RangeReversed)
    }

    /** The name on a single line, without surrounding spaces. */
    fun cleanedName(): String = InputText.singleLine(name)

    /** The unit on a single line, without surrounding spaces. */
    fun cleanedUnit(): String = InputText.singleLine(unit)

    /** The lower end of the range, none if none, or no number, was typed. */
    fun cleanedMin(): Long? = TestNumber.parse(min, decimals)

    /** The upper end of the range, none if none, or no number, was typed. */
    fun cleanedMax(): Long? = TestNumber.parse(max, decimals)

    private fun isNumberOrNone(typed: String) = typed.isBlank() || TestNumber.parse(typed, decimals) != null

    companion object {
        /** As long as a value's name may be. */
        const val NAME_MAX = 80

        /** As long as a unit need be, "Punkte" or "mmHg". */
        const val UNIT_MAX = 20
    }
}

/** The draft of [value], as stored. */
fun TestValue.toDraft() = ValueDraft(
    id = id,
    name = name,
    unit = unit,
    min = min?.let(TestNumber::typed).orEmpty(),
    max = max?.let(TestNumber::typed).orEmpty(),
    decimals = decimals,
    direction = direction,
    retired = retired,
)

/** What keeps a [TestDraft] from being saved, each shown where it is. */
data class TestProblems(
    /** The name's, if any. */
    val name: NameProblem?,
    /** Those of each value, in the draft's order. */
    val values: List<Set<ValueProblem>>,
    /** Every value is retired, or there is none. */
    val noActiveValue: Boolean,
) {
    /** Whether there is none: it can be saved. */
    val isEmpty: Boolean get() = name == null && values.all(Set<ValueProblem>::isEmpty) && !noActiveValue
}

/** What keeps a [ValueDraft] from being saved. */
enum class ValueProblem { NameMissing, NameTooLong, NameTaken, UnitTooLong, MinInvalid, MaxInvalid, RangeReversed }
