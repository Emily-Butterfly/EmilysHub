package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The forms built into the app (SPEC.md §14): how they are found, sorted and scored as the originals. */
class BuiltInAssessmentsTest {

    private val phq9 = BuiltInAssessments.byId("phq-9")!!

    private fun phq9(vararg points: Int): ScoreResult =
        Scoring.results(phq9, phq9.items.zip(points.toList()).associate { (item, p) -> item.id to Answer.Chosen("$p") }).single()

    @Test
    fun `the PHQ-9 sums its nine items from 0 to 27, lower being better`() {
        assertEquals(9, phq9.items.size)
        assertEquals(Direction.LowerIsBetter, phq9.scores.single().direction)
        assertEquals(0L..2700L, Scoring.range(phq9, phq9.scores.single()))

        val none = phq9(0, 0, 0, 0, 0, 0, 0, 0, 0)
        assertEquals(0L, none.value)
        assertEquals("gesund", none.band)

        val most = phq9(3, 3, 3, 3, 3, 3, 3, 3, 3)
        assertEquals(2700L, most.value)
        assertEquals("„schwergradige“ Depression", most.band)

        val mixed = phq9(1, 2, 0, 3, 1, 0, 2, 1, 0)
        assertEquals(1000L, mixed.value)
        assertEquals("„leichtgradige“ Depression", mixed.band)
    }

    @Test
    fun `the PHQ-9 bands meet at the bounds of the original`() {
        val bandAt = { sum: Int -> ScoreResult(phq9.scores.single(), sum * 100L).band }

        assertEquals(listOf("gesund", "gesund", "unauffällig", "unauffällig", "„leichtgradige“ Depression", "„leichtgradige“ Depression"), listOf(0, 4, 5, 9, 10, 14).map(bandAt))
        assertEquals(listOf("„mittelgradige“ Depression", "„mittelgradige“ Depression", "„schwergradige“ Depression"), listOf(15, 19, 20).map(bandAt))
    }

    @Test
    fun `the PHQ-9 needs every item answered`() {
        assertEquals(null, Scoring.results(phq9, mapOf("1" to Answer.Chosen("3"))).single().value)
    }

    @Test
    fun `the Anamnesebogen asks where by body regions and has no result`() {
        val form = BuiltInAssessments.byId("anamnese-v8")!!

        assertTrue(form.item("1") is BodyRegionsItem)
        assertEquals(listOf("26-welche", "26-wann", "26-nachuntersuchung"), form.items.filter { it.showIf == Condition.ifYes("26") }.map(Item::id))
        assertTrue(form.scores.isEmpty())
        assertEquals(listOf(AssessmentCategory.Anamnesis), form.categories)
        assertEquals("Anamnesebogen Version 8, Stand 07/2026", form.source)
    }

    @Test
    fun `the PHQ-9 stands under each of its categories, as one form`() {
        val byCategory = BuiltInAssessments.byCategory(hidden = emptySet())

        listOf(AssessmentCategory.JawCmd, AssessmentCategory.PainPsychosocial).forEach { category ->
            assertTrue(byCategory.getValue(category).any { it === phq9 })
        }
        assertEquals(AssessmentCategory.entries.filter { it in byCategory }, byCategory.keys.toList())
    }

    @Test
    fun `hidden forms are not offered`() {
        val byCategory = BuiltInAssessments.byCategory(hidden = setOf("phq-9"))

        assertTrue(byCategory.values.flatten().none { it.id == "phq-9" })
        assertTrue(BuiltInAssessments.search("phq", hidden = setOf("phq-9")).isEmpty())
    }

    @Test
    fun `the search finds forms by name and abbreviation in any case`() {
        assertEquals(listOf("phq-9"), BuiltInAssessments.search("phq", hidden = emptySet()).map(Assessment::id))
        assertEquals(listOf("phq-9"), BuiltInAssessments.search("PATIENT health", hidden = emptySet()).map(Assessment::id))
        assertEquals(listOf("anamnese-v8"), BuiltInAssessments.search("anamnese", hidden = emptySet()).map(Assessment::id))
        assertEquals(listOf("odi"), BuiltInAssessments.search("oswestry", hidden = emptySet()).map(Assessment::id))
        assertTrue(BuiltInAssessments.search("gibt es nicht", hidden = emptySet()).isEmpty())
    }

    @Test
    fun `every built-in form has a stable id of its own`() {
        val ids = BuiltInAssessments.all.map(Assessment::id)

        assertEquals(ids.toSet().size, ids.size)
        assertTrue(BuiltInAssessments.all.all { it.categories.isNotEmpty() && it.source.isNotBlank() })
    }

    @Test
    fun `the lower extremity holds the forms of knee and hip and those of foot and ankle, and no other`() {
        val lower = BuiltInAssessments.byCategory(hidden = emptySet()).getValue(AssessmentCategory.LowerExtremity).map(Assessment::id)
        assertEquals((KNEE_HIP + FOOT_ANKLE).map(Assessment::id).sorted(), lower.sorted())
    }

    @Test
    fun `the upper extremity holds its own forms, and no other`() {
        val upper = BuiltInAssessments.byCategory(hidden = emptySet()).getValue(AssessmentCategory.UpperExtremity).map(Assessment::id)
        assertEquals(UPPER_EXTREMITY.map(Assessment::id).sorted(), upper.sorted())
    }
}
