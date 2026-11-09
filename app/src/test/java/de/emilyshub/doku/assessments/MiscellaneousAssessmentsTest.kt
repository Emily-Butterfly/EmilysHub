package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction
import de.emilyshub.doku.assessments.Forms.all
import de.emilyshub.doku.assessments.Forms.chosen
import de.emilyshub.doku.assessments.Forms.form
import de.emilyshub.doku.assessments.Forms.values
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The forms of DIGOTOR's subcategory "Verschiedenes" (#76), each scored as its PDF says, or as asked where it falls short:
 * the least and the most, a case worked out by hand, what is needed, and its bands at their bounds.
 */
class MiscellaneousAssessmentsTest {

    /** The band of the only score of [id] at [hundredths]. */
    private fun band(id: String, hundredths: Long) = ScoreResult(form(id).scores.single(), hundredths).band

    /** The criteria of a Wells-Score [id] checked by their [labels]' starts. */
    private fun checked(id: String, vararg labels: String): Map<String, Answer> {
        val list = form(id).items.single() as ChecklistItem
        return mapOf(list.id to Answer.Checked(labels.map { start -> list.options.single { it.label.startsWith(start) }.id }.toSet()))
    }

    /** Each yes or no of the GAQ-PP's questions, in order, by [yes]; those left out unanswered. */
    private fun gaq(vararg yes: Boolean): Map<String, Answer> =
        form("gaq-pp").scores.single().items.zip(yes.toList()).associate { (id, y) -> id to Answer.YesNo(y) }

    @Test
    fun `every form of Verschiedenes stands there alone, each found by its name or abbreviation`() {
        val ids = listOf("gaq-pp", "iciq-ui-sf", "wells-le", "wells-tvt")
        assertEquals(ids, MISCELLANEOUS.map(Assessment::id).sorted())
        ids.forEach { assertEquals(it, listOf(AssessmentCategory.Miscellaneous), form(it).categories) }
        fun found(typed: String) = BuiltInAssessments.search(typed, hidden = emptySet()).map(Assessment::id)
        assertTrue("gaq-pp" in found("GAQ-PP"))
        assertTrue("iciq-ui-sf" in found("ICIQ"))
        assertTrue("iciq-ui-sf" in found("incontinence"))
        assertEquals(listOf("wells-le", "wells-tvt"), found("wells").sorted())
        assertTrue("wells-tvt" in found("TVT"))
        assertTrue(MISCELLANEOUS.all { it.source.contains("Stand 11/2026") })
    }

    @Test
    fun `the Wells-Score for pulmonary embolism adds up the criteria checked, in tenths, from 0 to 10,5`() {
        assertEquals(listOf(0L), values("wells-le", checked("wells-le")))
        assertTrue(Scoring.reachesResultUnanswered(form("wells-le")))
        val list = form("wells-le").items.single() as ChecklistItem
        assertEquals(listOf(1050L), values("wells-le", mapOf(list.id to Answer.Checked(list.options.map(Option::id).toSet()))))
        // 3,0 + 1,5 + 1,0: signs of a DVT, a pulse above 100 and haemoptysis.
        assertEquals(listOf(550L), values("wells-le", checked("wells-le", "Symptome", "Tachykardie", "Hämoptyse")))
        assertEquals(0L..1050L, Scoring.range(form("wells-le"), form("wells-le").scores.single()))
        assertEquals(
            listOf("geringe Wahrscheinlichkeit", "geringe Wahrscheinlichkeit", "mäßige Wahrscheinlichkeit", "mäßige Wahrscheinlichkeit", "hohe Wahrscheinlichkeit", "hohe Wahrscheinlichkeit"),
            listOf(0L, 150L, 200L, 600L, 650L, 1050L).map { band("wells-le", it) },
        )
        assertEquals(Direction.LowerIsBetter, form("wells-le").scores.single().direction)
    }

    @Test
    fun `the Wells-Score for a deep vein thrombosis adds up the criteria checked, an alternative diagnosis taking off 2`() {
        assertEquals(listOf(0L), values("wells-tvt", checked("wells-tvt")))
        assertEquals(listOf(-200L), values("wells-tvt", checked("wells-tvt", "Alternative")))
        val list = form("wells-tvt").items.single() as ChecklistItem
        val positive = list.options.filter { it.points > 0 }.map { it.label.take(12) }.toTypedArray()
        assertEquals(listOf(900L), values("wells-tvt", checked("wells-tvt", *positive)))
        assertEquals(listOf(700L), values("wells-tvt", checked("wells-tvt", *positive, "Alternative")))
        // Swelling of the whole leg, pitting oedema and an earlier DVT, less 2: 1.
        assertEquals(listOf(100L), values("wells-tvt", checked("wells-tvt", "Schwellung ganzes", "Eindrückbares", "Frühere", "Alternative")))
        assertEquals(-200L..900L, Scoring.range(form("wells-tvt"), form("wells-tvt").scores.single()))
        val unlikely = "TVT unwahrscheinlich (D-Dimer-Test sinnvoll)"
        val middle = "Mittleres Risiko (D-Dimere und evtl. Ultraschall empfohlen)"
        val likely = "TVT wahrscheinlich (Ultraschall/Phlebografie notwendig)"
        assertEquals(
            listOf(unlikely, unlikely, middle, middle, likely, likely),
            listOf(-200L, 0L, 100L, 200L, 300L, 900L).map { band("wells-tvt", it) },
        )
    }

    @Test
    fun `the ICIQ sums its first three questions from 0 to 21 in Klovning's bands, the fourth unscored`() {
        val scale = mapOf("3" to Answer.OnScale(0))
        assertEquals(listOf(0L), values("iciq-ui-sf", all("iciq-ui-sf", 0) + scale))
        assertEquals(listOf(2100L), values("iciq-ui-sf", chosen("iciq-ui-sf", 5, 6) + mapOf("3" to Answer.OnScale(10))))
        // About once a day 3, a moderate amount 4, and 7 on the scale: 14.
        assertEquals(listOf(1400L), values("iciq-ui-sf", chosen("iciq-ui-sf", 3, 4) + mapOf("3" to Answer.OnScale(7))))
        assertEquals(listOf(null), values("iciq-ui-sf", chosen("iciq-ui-sf", 3, 4)))
        assertEquals(listOf(null), values("iciq-ui-sf", chosen("iciq-ui-sf", 3) + scale))
        assertEquals(
            listOf("Keine Inkontinenz", "Leichte Inkontinenz", "Leichte Inkontinenz", "Mäßige Inkontinenz", "Mäßige Inkontinenz",
                "Starke Inkontinenz", "Starke Inkontinenz", "Sehr starke Inkontinenz", "Sehr starke Inkontinenz"),
            listOf(0, 1, 5, 6, 12, 13, 18, 19, 21).map { band("iciq-ui-sf", it * 100L) },
        )
        val whenLost = form("iciq-ui-sf").item("4") as ChecklistItem
        assertFalse(whenLost.scored)
        assertEquals(8, whenLost.options.size)
        assertTrue(whenLost.options.first().exclusive)
    }

    @Test
    fun `the GAQ-PP recommends activity once all its questions are no, else to speak first with a doctor or midwife`() {
        val questions = form("gaq-pp").scores.single().items
        assertEquals(18, questions.size)
        val yes = BooleanArray(18) { true }
        val no = BooleanArray(18)
        val recommended = "Körperliche Aktivität wird empfohlen."
        val speak = "Vor Beginn oder Fortsetzung körperlicher Aktivität mit Arzt/Ärztin oder Hebamme sprechen."
        assertEquals(listOf(0L), values("gaq-pp", gaq(*no)))
        assertEquals(listOf(1800L), values("gaq-pp", gaq(*yes)))
        // Yes to 1c and 3 alone.
        assertEquals(listOf(200L), values("gaq-pp", gaq(*BooleanArray(18) { it == 2 || it == 17 })))
        assertEquals(listOf(null), values("gaq-pp", gaq(*BooleanArray(17))))
        assertEquals(listOf(recommended, speak, speak), listOf(0L, 100L, 1800L).map { band("gaq-pp", it) })

        val cleared = "abgesprochen"
        fun shown(answers: Map<String, Answer>) = Scoring.visible(form("gaq-pp"), answers).any { it.id == cleared }
        assertFalse(shown(gaq(*no)))
        assertTrue(shown(gaq(*BooleanArray(18) { it == 17 })))
        assertTrue(shown(gaq(*BooleanArray(18) { it == 0 })))
        // Asked of all who said yes, it changes nothing of the recommendation.
        assertEquals(listOf(100L), values("gaq-pp", gaq(*BooleanArray(18) { it == 0 }) + (cleared to Answer.YesNo(true))))
    }
}
