package de.emilyshub.doku.assessments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What holds of every built-in form, whatever its subcategory: each score has a range, answers of the fewest and of the
 * most points stay within it, and a plain sum of items always shown reaches its ends. The net under each change to
 * [Scoring]'s rules, as the forms' own tests check only their own scores.
 */
class AllFormsTest {

    /** The answers [item] may take that make points, at their ends: what a score counts of it. */
    private fun candidates(item: Item): List<Answer> = when (item) {
        is ChoiceItem -> item.options.filterNot(Option::notApplicable).map { Answer.Chosen(it.id) }
        is YesNoItem -> listOf(Answer.YesNo(true), Answer.YesNo(false))
        is ScaleItem -> listOf(Answer.OnScale(item.min), Answer.OnScale(item.max))
        is ChecklistItem -> listOf(
            Answer.Checked(item.options.filter { it.points < 0 }.map(Option::id).toSet()),
            Answer.Checked(item.options.filter { it.points > 0 }.map(Option::id).toSet()),
        )
        is NumberItem -> listOf(Answer.Number(0), Answer.Number((item.max?.toLong() ?: 10_000L) * 100))
        is TimeItem -> listOf(Answer.Time(0), Answer.Time(TimeItem.DAY - 1))
        else -> emptyList()
    }

    /** Each scorable item of [form] answered by its fewest points, or its most if [most]. */
    private fun extreme(form: Assessment, most: Boolean): Map<String, Answer> = form.items.mapNotNull { item ->
        val scored = candidates(item).mapNotNull { answer -> Scoring.points(item, answer)?.let { answer to it } }
        (if (most) scored.maxByOrNull { it.second } else scored.minByOrNull { it.second })?.let { item.id to it.first }
    }.toMap()

    @Test
    fun `every score of every form has a range, and its least and most answers stay within it`() {
        BuiltInAssessments.all.forEach { form ->
            form.scores.forEach { score ->
                val range = Scoring.range(form, score)
                assertTrue("${form.id}/${score.id}: $range", range.first <= range.last)
            }
            listOf(false, true).forEach { most ->
                Scoring.results(form, extreme(form, most)).forEach { result ->
                    val range = Scoring.range(form, result.score)
                    result.value?.let { assertTrue("${form.id}/${result.score.id}: $it outside $range", it in range) }
                }
            }
        }
    }

    @Test
    fun `a plain sum of items always shown reaches both ends of its range`() {
        var checked = 0
        BuiltInAssessments.all.forEach { form ->
            form.scores.filter { score ->
                score.items.isNotEmpty() && score.rule == ScoreRule.Sum && score.worseOf.isEmpty() && score.steps == null &&
                    score.items.all { form.item(it)?.showIf == null }
            }.forEach { score ->
                val range = Scoring.range(form, score)
                listOf(false to range.first, true to range.last).forEach { (most, end) ->
                    val value = Scoring.results(form, extreme(form, most)).single { it.score === score }.value
                    assertEquals("${form.id}/${score.id} most=$most", end, value)
                }
                checked++
            }
        }
        assertTrue("$checked sums checked", checked > 50)
    }
}
