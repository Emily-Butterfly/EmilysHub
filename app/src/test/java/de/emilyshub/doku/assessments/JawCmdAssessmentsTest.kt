package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction
import de.emilyshub.doku.assessments.Forms.all
import de.emilyshub.doku.assessments.Forms.bandAt
import de.emilyshub.doku.assessments.Forms.chosen
import de.emilyshub.doku.assessments.Forms.form
import de.emilyshub.doku.assessments.Forms.values
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The forms of DIGOTOR's subcategory "Kiefer / CMD" (#73), each scored as its source says: the least and the most, a case
 * worked out by hand, every item needed, and its bands at their bounds; the DC/TMD forms and the Schmerzbild without a result.
 */
class JawCmdAssessmentsTest {

    @Test
    fun `every jaw form stands under Kiefer CMD, with the forms of other slices sorted in there too, each found`() {
        val ids = listOf("cmd-schmerzbild", "dc-tmd-sq", "dc-tmd-untersuchung", "dhi", "gad-7", "obc")
        assertEquals(ids, JAW_CMD.map(Assessment::id).sorted())
        ids.forEach { id ->
            assertEquals(id, listOf(AssessmentCategory.JawCmd), form(id).categories)
            val typed = form(id).abbreviation ?: form(id).name
            assertTrue("$typed: $id", id in BuiltInAssessments.search(typed, hidden = emptySet()).map(Assessment::id))
        }
        assertTrue("dc-tmd-untersuchung" in BuiltInAssessments.search("dc/tmd", hidden = emptySet()).map(Assessment::id))
        val jaw = BuiltInAssessments.byCategory(hidden = emptySet()).getValue(AssessmentCategory.JawCmd).map(Assessment::id).toSet()
        assertEquals(ids.toSet() + setOf("fabq", "gcps", "npq", "phq-15", "phq-4", "phq-9"), jaw)
        assertEquals("GAD-7, deutsche Version (Löwe 2015)", form("gad-7").source)
        assertEquals("DC/TMD Symptombezogener Fragebogen, deutsche Version 12/2018", form("dc-tmd-sq").source)
    }

    @Test
    fun `the DHI sums physical, emotional and functional by 4, 2 and 0, and all of them to 100`() {
        // P, E, F, total.
        assertEquals(listOf(0L, 0L, 0L, 0L), values("dhi", all("dhi", 0)))
        assertEquals(listOf(2800L, 3600L, 3600L, 10000L), values("dhi", all("dhi", 4)))
        // P all "manchmal", E all "ja", F all "nein": 14 + 36 + 0.
        val dhi = form("dhi").scores
        val mixed = all("dhi", 2, items = dhi[0].items) + all("dhi", 4, items = dhi[1].items) + all("dhi", 0, items = dhi[2].items)
        assertEquals(listOf(1400L, 3600L, 0L, 5000L), values("dhi", mixed))
        assertEquals(listOf(null, 3600L, 0L, null), values("dhi", mixed - "1"))
        assertEquals(listOf(7, 9, 9), dhi.take(3).map { it.items.size })
        assertEquals(listOf("1", "4", "8", "11", "13", "17", "25"), dhi[0].items)
        assertEquals(0L..10000L, Scoring.range(form("dhi"), dhi[3]))
        assertTrue(dhi.all { it.direction == Direction.LowerIsBetter && it.bands.isEmpty() })
    }

    @Test
    fun `the OBC sums its 21 from 0 to 84, banded keine, gering and ausgepraegt`() {
        assertEquals(listOf(0L), values("obc", all("obc", 0)))
        assertEquals(listOf(8400L), values("obc", all("obc", 4)))
        // 1 + 2 + 3 + 4 + 0, four times, and a 1.
        val mixed = chosen("obc", 1, 2, 3, 4, 0, 1, 2, 3, 4, 0, 1, 2, 3, 4, 0, 1, 2, 3, 4, 0, 1)
        assertEquals(listOf(4100L), values("obc", mixed))
        assertEquals(listOf(null), values("obc", mixed - "21"))
        assertEquals(listOf("keine", "gering", "gering", "ausgeprägt", "ausgeprägt"), listOf(0L, 100L, 2400L, 2500L, 8400L).map { bandAt("obc", it) })
        assertEquals("4-7 Nächte/Woche", (form("obc").item("1") as ChoiceItem).options.last().label)
        assertEquals("Immer", (form("obc").item("3") as ChoiceItem).options.last().label)
        assertEquals(Direction.LowerIsBetter, form("obc").scores.single().direction)
    }

    @Test
    fun `the GAD-7 sums seven from 0 to 21, banded by 5, 10 and 15`() {
        assertEquals(listOf(0L), values("gad-7", all("gad-7", 0)))
        assertEquals(listOf(2100L), values("gad-7", all("gad-7", 3)))
        assertEquals(listOf(900L), values("gad-7", chosen("gad-7", 0, 1, 2, 3, 2, 1, 0)))
        assertEquals(listOf(null), values("gad-7", chosen("gad-7", 0, 1, 2, 3, 2, 1)))
        assertEquals(
            listOf("minimale Ängste", "minimale Ängste", "leichte Ängste", "leichte Ängste", "mäßige Ängste", "mäßige Ängste", "schwere Ängste", "schwere Ängste"),
            listOf(0, 4, 5, 9, 10, 14, 15, 21).map { bandAt("gad-7", it * 100L) },
        )
        assertEquals(Direction.LowerIsBetter, form("gad-7").scores.single().direction)
    }

    @Test
    fun `the DC-TMD symptom questionnaire skips as its notes say and has no result`() {
        val sq = form("dc-tmd-sq")
        fun shown(answers: Map<String, Answer>) = Scoring.visible(sq, answers).map(Item::id)
        assertEquals(listOf("1", "5", "8", "9", "13"), shown(emptyMap()))
        val pain = mapOf("1" to Answer.YesNo(true))
        assertEquals(listOf("1", "2-jahre", "2-monate", "3", "5", "8", "9", "13"), shown(pain))
        assertEquals(shown(pain), shown(pain + ("3" to Answer.Chosen("0"))))
        assertEquals(listOf("4a", "4b", "4c", "4d"), shown(pain + ("3" to Answer.Chosen("1"))) - shown(pain).toSet())
        assertEquals(listOf("6-jahre", "6-monate", "7a", "7b", "7c", "7d"), shown(mapOf("5" to Answer.YesNo(true))) - shown(emptyMap()).toSet())
        assertEquals(listOf("8-seite"), shown(mapOf("8" to Answer.YesNo(true))) - shown(emptyMap()).toSet())
        assertEquals(listOf("10", "11"), shown(mapOf("9" to Answer.YesNo(true))) - shown(emptyMap()).toSet())
        assertEquals(listOf("10", "11", "12"), shown(mapOf("9" to Answer.YesNo(true), "11" to Answer.YesNo(true))) - shown(emptyMap()).toSet())
        assertEquals(listOf("14"), shown(mapOf("13" to Answer.YesNo(true))) - shown(emptyMap()).toSet())
        assertTrue(sq.scores.isEmpty())
    }

    @Test
    fun `the DC-TMD examination asks where by the jaw's regions and each row of its grid as a checklist, without a result`() {
        val exam = form("dc-tmd-untersuchung")
        val where = exam.item("1a") as BodyRegionsItem
        assertEquals(
            listOf(
                BodyRegion.TemporalisRight, BodyRegion.MasseterRight, BodyRegion.JawJointRight, BodyRegion.OtherMasticatoryRight, BodyRegion.NonMasticatoryRight,
                BodyRegion.TemporalisLeft, BodyRegion.MasseterLeft, BodyRegion.JawJointLeft, BodyRegion.OtherMasticatoryLeft, BodyRegion.NonMasticatoryLeft,
            ),
            where.regions,
        )
        assertEquals(listOf(BodyRegion.TemporalisRight, BodyRegion.TemporalisLeft), (exam.item("1b") as BodyRegionsItem).regions)
        val rows = exam.items.filterIsInstance<ChecklistItem>()
        // 4 B and C, 5 A to C: 5 rows a side each; 6 and 7: 2 a side; 8: 2 a side; 9: 2 + 2 + 4 a side.
        assertEquals(5 * 10 + 4 + 4 + 4 + 16 + 3, rows.size)
        assertEquals(listOf("Schmerz", "Bekannter Schmerz", "Bekannter Kopfschmerz"), rows.single { it.id == "4b-temporalis-r" }.options.map(Option::label))
        assertEquals(listOf("Schmerz", "Bekannter Schmerz"), rows.single { it.id == "5c-masseter-l" }.options.map(Option::label))
        assertEquals(
            listOf("Schmerz", "Bekannter Schmerz", "Bekannter Kopfschmerz", "Ausbreitender Schmerz", "Übertragener Schmerz"),
            rows.single { it.id == "9-temporalis-r" }.options.map(Option::label),
        )
        assertTrue(rows.none(ChecklistItem::scored))
        assertTrue(exam.scores.isEmpty())
    }

    @Test
    fun `the Schmerzbild asks for the face and mouth by the jaw's regions and for the body by its regions`() {
        val drawing = form("cmd-schmerzbild")
        val (face, body) = drawing.items.map { it as BodyRegionsItem }
        assertEquals(BodyRegion.jaw, face.regions)
        assertEquals(BodyRegion.body, body.regions)
        assertTrue(drawing.scores.isEmpty())
    }

    @Test
    fun `the regions of the body and of the jaw are apart, the body's the default, and both stored and read back`() {
        assertEquals(BodyRegion.entries.toSet(), (BodyRegion.body + BodyRegion.jaw).toSet())
        assertTrue(BodyRegion.body.none { it in BodyRegion.jaw })
        assertEquals(BodyRegion.body, BodyRegionsItem("1", "Wo?").regions)
        val answer = Answer.Regions(setOf(BodyRegion.MouthAndTeeth, BodyRegion.JawJointLeft, BodyRegion.KneeLeft), "Ohr")
        assertEquals(answer, AnswerCodec.decode(checkNotNull(AnswerCodec.encode(answer))))
    }
}
