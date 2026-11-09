package de.emilyshub.doku

import de.emilyshub.doku.assessments.Answer
import de.emilyshub.doku.assessments.BuiltInAssessments
import de.emilyshub.doku.assessments.Goal
import de.emilyshub.doku.assessments.isNothing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * A form filled in, as the page shows it and the Doku stores it: what is shown under its conditions, what an answer given
 * makes of it, the answers its form no longer has, its results, and what is kept.
 */
class AssessmentSheetTest {

    /** The MS depression screening, whose symptoms show once question 1 or 2 is yes, filled in with [answers]. */
    private fun sheet(answers: Map<String, Answer> = emptyMap(), assessmentId: String = "ms-depression") = AssessmentSheet(
        FilledAssessment(1, 2, LocalDate.of(2026, 11, 9), assessmentId, BuiltInAssessments.byId(assessmentId), answers),
        canChange = true,
    )

    private fun AssessmentSheet.shownIds() = sections.flatMap { it.items }.map { it.item.id }

    @Test
    fun `items under a condition show once it holds, each with its answer`() {
        assertEquals(listOf("1", "2"), sheet().shownIds())
        val yes = sheet().answering("1", Answer.YesNo(true))
        assertEquals(listOf("1", "2", "kern", "weitere"), yes.shownIds())
        assertEquals(Answer.YesNo(true), yes.sections.flatMap { it.items }.single { it.item.id == "1" }.answer)
        assertNull(yes.sections.flatMap { it.items }.single { it.item.id == "2" }.answer)
    }

    @Test
    fun `an answer that says nothing is no answer`() {
        val ticked = sheet().answering("1", Answer.YesNo(true)).answering("kern", Answer.Checked(setOf("1")))
        assertEquals(setOf("1", "kern"), ticked.answers.keys)
        val unticked = ticked.answering("kern", Answer.Checked(emptySet(), other = "  "))
        assertEquals(setOf("1"), unticked.answers.keys)
        assertEquals(setOf<String>(), unticked.answering("1", null).answers.keys)
        assertTrue(Answer.Text(" ").isNothing)
        assertTrue(Answer.Goals(listOf(Goal(""), Goal(" "))).isNothing)
        assertFalse(Answer.Goals(listOf(Goal("", level = 0))).isNothing)
        assertFalse(Answer.YesNo(false).isNothing)
    }

    @Test
    fun `answers its form no longer has, or of another kind, are orphans, shown apart, not on the item, and kept`() {
        val sheet = sheet(mapOf("1" to Answer.Text("früher Text"), "alt" to Answer.YesNo(true), "2" to Answer.YesNo(false)))
        assertEquals(setOf("1", "alt"), sheet.orphans.keys)
        assertNull(sheet.sections.flatMap { it.items }.single { it.item.id == "1" }.answer)
        assertEquals(setOf("1", "alt", "2"), sheet.stored.keys)
    }

    @Test
    fun `answers to items no longer shown are neither counted nor kept`() {
        val sheet = sheet().answering("1", Answer.YesNo(true)).answering("kern", Answer.Checked(setOf("1", "2"))).answering("1", Answer.YesNo(false))
        assertEquals(listOf("1", "2"), sheet.shownIds())
        assertEquals(setOf("1"), sheet.stored.keys)
        // Kept in the page while it is open, so that they come back with the condition.
        assertEquals(setOf("1", "kern"), sheet.answers.keys)
        assertEquals(setOf("1", "kern"), sheet.answering("1", Answer.YesNo(true)).stored.keys)
    }

    @Test
    fun `its results are those of its answers, and nothing of a form this version does not know`() {
        val phq = sheet(mapOf("1" to Answer.Chosen("2")), assessmentId = "phq-9")
        assertEquals(null, phq.results.single().value)
        val all = (1..9).fold(phq) { s, i -> s.answering("$i", Answer.Chosen("1")) }
        assertEquals(900L, all.results.single().value)
        val gone = AssessmentSheet(FilledAssessment(1, 2, LocalDate.of(2026, 11, 9), "weg", null, mapOf("1" to Answer.YesNo(true))), canChange = false)
        assertEquals(emptyList<Any>(), gone.sections)
        assertEquals(emptyList<Any>(), gone.results)
        assertEquals(setOf("1"), gone.orphans.keys)
        assertEquals(setOf("1"), gone.stored.keys)
    }

    @Test
    fun `it is changed once its answers differ from those it was read with`() {
        assertFalse(sheet().changed)
        assertTrue(sheet().answering("1", Answer.YesNo(true)).changed)
        assertFalse(sheet().answering("1", Answer.YesNo(true)).answering("1", null).changed)
    }
}
