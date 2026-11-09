package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction
import de.emilyshub.doku.assessments.Forms.all
import de.emilyshub.doku.assessments.Forms.bandAt
import de.emilyshub.doku.assessments.Forms.chosen
import de.emilyshub.doku.assessments.Forms.form
import de.emilyshub.doku.assessments.Forms.itemsOf
import de.emilyshub.doku.assessments.Forms.value
import de.emilyshub.doku.assessments.Forms.values
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The forms of DIGOTOR's subcategory "Neurologie" (#74), each scored as its PDF says: the least and the most, a case worked
 * out by hand, every item needed, and its bands at their bounds.
 */
class NeurologyAssessmentsTest {

    @Test
    fun `every neurological form stands under Neurologie alone, each found`() {
        val ids = listOf("ems", "fac", "fog", "fugl-meyer", "gas", "mfis", "mini-bestest", "mos-pes", "ms-depression")
        assertEquals(ids, NEUROLOGY.map(Assessment::id).sorted())
        ids.forEach { id ->
            assertEquals(id, listOf(AssessmentCategory.Neurology), form(id).categories)
            val typed = form(id).abbreviation ?: form(id).name
            assertTrue("$typed: $id", id in BuiltInAssessments.search(typed, hidden = emptySet()).map(Assessment::id))
        }
        assertTrue("ms-depression" in BuiltInAssessments.search("depression multiple", hidden = emptySet()).map(Assessment::id))
        assertTrue("fugl-meyer" in BuiltInAssessments.search("fugl", hidden = emptySet()).map(Assessment::id))
        assertTrue(NEUROLOGY.all { it.source.contains("Stand 10/2026") })
    }

    @Test
    fun `the EMS sums A to E from 5 to 18 and grades 3, 2, 1 and normal`() {
        assertEquals(listOf(500L), values("ems", chosen("ems", 1, 1, 1, 1, 1)))
        assertEquals(listOf(1800L), values("ems", chosen("ems", 5, 3, 4, 3, 3)))
        assertEquals(listOf(1200L), values("ems", chosen("ems", 4, 2, 3, 1, 2)))
        assertEquals(listOf(null), values("ems", chosen("ems", 4, 2, 3, 1)))
        assertEquals(
            listOf("Grad 3", "Grad 3", "Grad 2", "Grad 2", "Grad 1", "Grad 1", "Normal", "Normal"),
            listOf(5, 8, 9, 12, 13, 16, 17, 18).map { bandAt("ems", it * 100L) },
        )
        assertEquals(Direction.HigherIsBetter, form("ems").scores.single().direction)
    }

    @Test
    fun `the FOG sums six from 0 to 24, the higher the worse`() {
        assertEquals(listOf(0L), values("fog", all("fog", 0)))
        assertEquals(listOf(2400L), values("fog", all("fog", 4)))
        assertEquals(listOf(1100L), values("fog", chosen("fog", 1, 2, 3, 4, 1, 0)))
        assertEquals(listOf(null), values("fog", chosen("fog", 1, 2, 3, 4, 1)))
        assertEquals(Direction.LowerIsBetter, form("fog").scores.single().direction)
    }

    @Test
    fun `the FAC is its category, from 0 to 5, named`() {
        assertEquals(listOf(0L), values("fac", chosen("fac", 0)))
        assertEquals(listOf(500L), values("fac", chosen("fac", 5)))
        assertEquals(listOf(null), values("fac", emptyMap()))
        assertEquals("Nicht Funktionell / nicht möglich", bandAt("fac", 0))
        assertEquals("Abhängig - Aufsicht", bandAt("fac", 300))
        assertEquals(
            listOf("Nicht Funktionell / nicht möglich", "Abhängig Stufe 2", "Abhängig Stufe 1", "Abhängig - Aufsicht", "Unabhängig - auf ebenen Boden", "Unabhängig"),
            (0..5).map { bandAt("fac", it * 100L) },
        )
    }

    @Test
    fun `the GAS rates goals named for the patient and reckons their T-score`() {
        fun t(vararg levels: Int?) = value("gas", mapOf("ziele" to Answer.Goals(levels.mapIndexed { i, level -> Goal("Ziel $i", level) })), "t")
        val goals = form("gas").item("ziele") as GoalsItem

        assertEquals(listOf(2, 1, 0, -1, -2), goals.levels.map(Option::points))
        assertEquals("+2 Ziel deutlich übertroffen", goals.levels.first().label)
        assertEquals(5000L, t(0))
        assertEquals(3000L, t(-2))
        assertEquals(7000L, t(2))
        // 50 + 10 * (1 + 0 - 1 + 2) / sqrt(0,7 * 4 + 0,3 * 16) = 50 + 20 / sqrt(7,6) = 57,25…
        assertEquals(5725L, t(1, 0, -1, 2))
        assertNull(t(1, null))
        assertNull(t())
        // A row left empty is no goal.
        assertEquals(7000L, value("gas", mapOf("ziele" to Answer.Goals(listOf(Goal("Gehen", 2), Goal("")))), "t"))
        assertEquals(Direction.HigherIsBetter, form("gas").scores.single().direction)
    }

    @Test
    fun `the Mini-BESTest sums four parts to 28, of 3 and 6 the worse side`() {
        val scored = form("mini-bestest").items.filterIsInstance<ChoiceItem>()
        fun everything(points: Int) = scored.associate { it.id to Answer.Chosen(it.options.single { o -> o.points == points }.id) }
        // Anticipation, reactive, sensory, gait, total.
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L), values("mini-bestest", everything(0)))
        assertEquals(listOf(600L, 600L, 600L, 1000L, 2800L), values("mini-bestest", everything(2)))
        fun at(id: String, points: Int) = id to Answer.Chosen(scored.single { it.id == id }.options.single { it.points == points }.id)
        // 3 left 2, right 1: anticipation loses one; 14 at 1.
        val mixed = everything(2) + at("3-rechts", 1) + at("14", 1)
        assertEquals(listOf(500L, 600L, 600L, 900L, 2600L), values("mini-bestest", mixed))
        assertEquals(listOf(null, 600L, 600L, 900L, null), values("mini-bestest", mixed - "3-links"))
        assertEquals(0L..600L, Scoring.range(form("mini-bestest"), form("mini-bestest").scores[0]))
        assertEquals(0L..2800L, Scoring.range(form("mini-bestest"), form("mini-bestest").scores[4]))
        assertEquals("(1) Moderat: < 30 sec.", (form("mini-bestest").item("8") as ChoiceItem).options[1].label)
        assertTrue(form("mini-bestest").scores.all { it.direction == Direction.HigherIsBetter })
    }

    @Test
    fun `the MFIS sums its 21 as physical, cognitive and psychosocial, their total and the MFIS-5`() {
        // Physical, cognitive, psychosocial, total, MFIS-5.
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L), values("mfis", all("mfis", 0)))
        assertEquals(listOf(3600L, 4000L, 800L, 8400L, 2000L), values("mfis", all("mfis", 4)))
        assertEquals(listOf(9, 10, 2), form("mfis").scores.take(3).map { it.items.size })
        // Each statement scored by its number's last digit, capped at 4: 1 1, 9 4, 10 0, 17 4, 19 4 makes the MFIS-5 13.
        val mixed = chosen("mfis", *(1..21).map { minOf(it % 10, 4) }.toTypedArray())
        // Physical 4 + 4 + 4 + 0 + 3 + 4 + 4 + 0 + 1; cognitive 1 + 2 + 3 + 4 + 1 + 2 + 4 + 4 + 4 + 4; psychosocial 4 + 4.
        assertEquals(listOf(2400L, 2900L, 800L, 6100L, 1300L), values("mfis", mixed))
        assertEquals(listOf(null, 2900L, 800L, null, null), values("mfis", mixed - "10"))
        assertEquals(listOf("1", "9", "10", "17", "19"), itemsOf("mfis", "mfis-5"))
        assertEquals("1. ... war ich weniger wachsam.", form("mfis").item("1")?.text)
        assertTrue(form("mfis").scores.all { it.direction == Direction.LowerIsBetter })
    }

    @Test
    fun `the MOS-PES sums six from 6 to 30, the higher the worse`() {
        assertEquals(listOf(600L), values("mos-pes", all("mos-pes", 1)))
        assertEquals(listOf(3000L), values("mos-pes", all("mos-pes", 5)))
        assertEquals(listOf(1800L), values("mos-pes", chosen("mos-pes", 1, 2, 3, 4, 5, 3)))
        assertEquals(listOf(null), values("mos-pes", chosen("mos-pes", 1, 2, 3, 4, 5)))
    }

    @Test
    fun `the MS screening asks of the nine symptoms once either question is yes, and needs five of them, one the first two`() {
        val form = form("ms-depression")
        fun shown(answers: Map<String, Answer>) = Scoring.visible(form, answers).map(Item::id)
        val no = mapOf("1" to Answer.YesNo(false), "2" to Answer.YesNo(false))
        assertEquals(listOf("1", "2"), shown(no))
        assertEquals(listOf("1", "2", "kern", "weitere"), shown(no + ("2" to Answer.YesNo(true))))

        // Screening, symptoms, of them the first two, DSM-IV.
        assertEquals(listOf(0L, null, null, null), values("ms-depression", no))
        // One question unanswered: no screening yet, the symptoms shown if the other is yes.
        assertEquals(listOf(null, 0L, 0L, 0L), values("ms-depression", mapOf("1" to Answer.YesNo(true))))
        assertEquals(listOf("1", "2", "kern", "weitere"), shown(mapOf("1" to Answer.YesNo(true))))
        val yes = no + ("1" to Answer.YesNo(true))
        assertEquals(listOf(100L, 0L, 0L, 0L), values("ms-depression", yes))
        fun checked(core: Set<String>, further: Set<String>) = yes + mapOf("kern" to Answer.Checked(core), "weitere" to Answer.Checked(further))
        assertEquals(listOf(100L, 500L, 100L, 100L), values("ms-depression", checked(setOf("1"), setOf("3", "4", "5", "6"))))
        assertEquals(listOf(100L, 400L, 100L, 0L), values("ms-depression", checked(setOf("1"), setOf("3", "4", "5"))))
        assertEquals(listOf(100L, 500L, 0L, 0L), values("ms-depression", checked(emptySet(), setOf("3", "4", "5", "6", "7"))))
        assertEquals(listOf(200L, 900L, 200L, 100L), values("ms-depression", checked(setOf("1", "2"), (3..9).map(Int::toString).toSet()) + ("2" to Answer.YesNo(true))))
        assertEquals(listOf("negativ", "positiv", "positiv"), listOf(0L, 100L, 200L).map { bandAt("ms-depression", it) })
        assertEquals(listOf("Kriterien nicht erfüllt", "Kriterien erfüllt"), listOf(0L, 100L).map { bandAt("ms-depression", it, score = 3) })
    }

    @Test
    fun `the Fugl-Meyer sums its parts to 66 for motor function, and sensation, motion and pain apart`() {
        // A, B, C, D, A-D, H, J motion, J pain.
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L), values("fugl-meyer", all("fugl-meyer", 0)))
        assertEquals(listOf(3600L, 1000L, 1400L, 600L, 6600L, 1200L, 2400L, 2400L), values("fugl-meyer", all("fugl-meyer", 2)))
        // The reflexes of A 1) take 0 or 2 only; with every other 1: A 2 + 2 + 9 + 3 + 3 + 1 = 20, B 5, C 7, D 3, H 6, J 12 and 12.
        val reflexes = itemsOf("fugl-meyer", "a").take(2)
        val mixed = all("fugl-meyer", 1, items = form("fugl-meyer").items.map(Item::id) - reflexes.toSet()) + all("fugl-meyer", 2, items = reflexes)
        assertEquals(listOf(2000L, 500L, 700L, 300L, 3500L, 600L, 1200L, 1200L), values("fugl-meyer", mixed))
        assertEquals(listOf(null, 500L, 700L, 300L, null, 600L, 1200L, 1200L), values("fugl-meyer", mixed - "a5"))
        assertEquals(listOf(0, 1, 2), (form("fugl-meyer").item("a2-retraktion") as ChoiceItem).options.map(Option::points))
        assertEquals(
            listOf("kann nicht durchgeführt werden oder Hand vor SIAS" to 0, "Hand hinter SIAS (ohne Kompensation)" to 1, "Hand an Lendenwirbelsäule (ohne Kompensation)" to 2),
            (form("fugl-meyer").item("a3-lws") as ChoiceItem).options.map { it.label to it.points },
        )
        assertEquals(6, itemsOf("fugl-meyer", "h").size)
        assertTrue(form("fugl-meyer").scores.all { it.direction == Direction.HigherIsBetter })
    }
}
