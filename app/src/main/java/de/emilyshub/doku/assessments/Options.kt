package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction

/** Options scored 0, 1, 2 … in the order given, as the originals number them, such as the KOOS: 0 for few symptoms. */
internal fun numbered(vararg labels: String): List<Option> = labels.mapIndexed { i, label -> Option("$i", label, i) }

/** Options scored from the most for the first down to 0 for the last, as the HOOS scores them: 4 for few symptoms. */
internal fun fourToZero(vararg labels: String): List<Option> = labels.mapIndexed { i, label -> Option("$i", label, labels.size - 1 - i) }

/** Options with the [points] given, in the order of the original, their ids numbered from 0. */
internal fun scored(vararg options: Pair<String, Int>): List<Option> =
    options.mapIndexed { i, (label, points) -> Option("$i", label, points) }

/** Options without points, their ids numbered from 0, for a choice no score counts, such as the side affected. */
internal fun unscored(vararg labels: String): List<Option> = labels.mapIndexed { i, label -> Option("$i", label) }

/** Statements answered yes or no, numbered from [first], 1 unless the original numbers on, as the DN4 through its questions. */
internal fun yesNoItems(vararg statements: String, first: Int = 1): List<Item> =
    statements.mapIndexed { i, text -> YesNoItem("${first + i}", "${first + i}. $text") }

/** Each of [items]' ids. */
internal fun ids(items: List<Item>): List<String> = items.map(Item::id)

/** Half of [n] items, rounded up. */
internal fun half(n: Int): Int = (n + 1) / 2

/** "Nicht zutreffend" and the like: an answer that counts as left out. */
internal fun notApplicable(label: String) = Option("na", label, notApplicable = true)

/**
 * A subscale from 0 to 100 of what is answered of [items], better the higher, once half of them are answered: the HOOS's
 * rule, which the FAOS states in the same words (08.11.2026).
 */
internal fun halfAnsweredSubscale(id: String, name: String, items: List<Item>) = Score(
    id, name, ids(items), rule = ScoreRule.ZeroTo100, unit = "%", direction = Direction.HigherIsBetter, minAnswered = half(items.size),
)

/** Questions 8a, 8b and 8c of a VISA form, each of [labels] with its [points], the one shown chosen by question 8's option a, b or c. */
internal fun visaTraining(texts: List<String>, labels: List<String>, points: List<List<Int>>): List<ChoiceItem> =
    listOf("a", "b", "c").mapIndexed { i, part ->
        ChoiceItem("8$part", texts[i], scored(*labels.zip(points[i]).toTypedArray()), showIf = Condition("8", setOf(part)))
    }
