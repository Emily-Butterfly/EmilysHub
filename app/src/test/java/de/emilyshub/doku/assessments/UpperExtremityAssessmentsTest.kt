package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import de.emilyshub.doku.assessments.Forms.all
import de.emilyshub.doku.assessments.Forms.allScales
import de.emilyshub.doku.assessments.Forms.bandAt
import de.emilyshub.doku.assessments.Forms.chosen
import de.emilyshub.doku.assessments.Forms.form
import de.emilyshub.doku.assessments.Forms.itemsOf
import de.emilyshub.doku.assessments.Forms.on
import de.emilyshub.doku.assessments.Forms.scales
import de.emilyshub.doku.assessments.Forms.values

/**
 * The forms of DIGOTOR's subcategory "Obere Extremität" (#70), each scored as its PDF says: the least and the most, a case
 * worked out by hand, missing answers by its rule, and its bands at their bounds.
 */
class UpperExtremityAssessmentsTest {

    @Test
    fun `every form of the upper extremity stands under Obere Extremitaet only, each found by its abbreviation or name`() {
        val ids = listOf("dash", "isis", "karpaltunnel", "nisis", "prtee", "prwe-g", "spadi", "sst", "walch-duplay", "wosi")
        assertEquals(ids, UPPER_EXTREMITY.map(Assessment::id).sorted())
        ids.forEach { id ->
            assertEquals(id, listOf(AssessmentCategory.UpperExtremity), form(id).categories)
            val typed = form(id).abbreviation ?: form(id).name
            assertTrue("$typed: $id", id in BuiltInAssessments.search(typed, hidden = emptySet()).map(Assessment::id))
        }
        assertTrue("karpaltunnel" in BuiltInAssessments.search("karpal", hidden = emptySet()).map(Assessment::id))
        assertTrue("walch-duplay" in BuiltInAssessments.search("walch duplay", hidden = emptySet()).map(Assessment::id))
        assertEquals("ISIS, Stand 10/2026", form("isis").source)
    }

    @Test
    fun `the ISIS sums six items to 10, all needed, above 3 a higher risk and from 6 Balg's high score`() {
        assertEquals(listOf(0L), values("isis", all("isis", 0)))
        assertEquals(listOf(1000L), values("isis", chosen("isis", 2, 2, 1, 1, 2, 2)))
        // Under 20, competitive, other sport, lax, Hill-Sachs unseen, glenoid seen: 2 + 2 + 0 + 1 + 0 + 2.
        assertEquals(listOf(700L), values("isis", chosen("isis", 2, 2, 0, 1, 0, 2)))
        assertEquals(listOf(null), values("isis", chosen("isis", 2, 2, 0, 1, 0)))

        assertNull(bandAt("isis", 300))
        assertTrue(bandAt("isis", 400)!!.startsWith("Höheres Rezidivrisiko"))
        assertTrue(bandAt("isis", 500)!!.startsWith("Höheres Rezidivrisiko"))
        assertTrue(bandAt("isis", 600)!!.startsWith("Hoher Score"))
        assertEquals(Direction.LowerIsBetter, form("isis").scores.single().direction)
    }

    @Test
    fun `the NISIS sums six items to 10, all needed, from 7 a return to sport less likely`() {
        assertEquals(listOf(0L), values("nisis", all("nisis", 0)))
        assertEquals(listOf(1000L), values("nisis", chosen("nisis", 2, 1, 3, 1, 1, 2)))
        // Over 15, female, contact sport, dominant, subluxation, no bony damage: 2 + 0 + 3 + 1 + 0 + 0.
        assertEquals(listOf(600L), values("nisis", chosen("nisis", 2, 0, 3, 1, 0, 0)))
        assertEquals(listOf(null), values("nisis", chosen("nisis", 2, 0, 3, 1, 0)))

        assertNull(bandAt("nisis", 600))
        assertTrue(bandAt("nisis", 700)!!.contains("seltener ein Return to Sport"))
    }

    @Test
    fun `the carpal tunnel scoring sums to 12, the little finger counting 3 for a no and pregnancy 0 for a no, all needed`() {
        val kts = form("karpaltunnel")
        /** Each question answered by its option labelled so, in order. */
        fun answered(vararg labels: String) = kts.items.filterIsInstance<ChoiceItem>().zip(labels.toList())
            .associate { (item, label) -> item.id to Answer.Chosen(item.options.single { it.label == label }.id) }

        assertEquals(listOf(1200L), values("karpaltunnel", answered("JA", "JA", "JA", "JA", "NEIN", "JA", "JA", "JA", "JA")))
        assertEquals(listOf(0L), values("karpaltunnel", answered("NEIN", "NEIN", "NEIN", "NEIN", "JA", "NEIN", "NEIN", "NEIN", "NEIN")))
        assertEquals(listOf(0L), values("karpaltunnel", answered("NEIN", "NEIN", "NEIN", "NEIN", "JA", "NEIN", "NEIN", "NV", "NV")))
        // Woken by tingling (1), moves to shake it off (1), little finger never (3), splint helped (2).
        assertEquals(listOf(700L), values("karpaltunnel", answered("NEIN", "JA", "NEIN", "JA", "NEIN", "NEIN", "NEIN", "NEIN", "JA")))
        assertEquals(listOf(null), values("karpaltunnel", answered("NEIN", "JA", "NEIN", "JA", "NEIN", "NEIN", "NEIN", "NEIN")))

        assertNull(bandAt("karpaltunnel", 200))
        assertTrue(bandAt("karpaltunnel", 300)!!.startsWith("Bei einer Punktzahl von 3-4"))
        assertTrue(bandAt("karpaltunnel", 400)!!.startsWith("Bei einer Punktzahl von 3-4"))
        assertTrue(bandAt("karpaltunnel", 500)!!.startsWith("Ab 5 Punkten"))
    }

    @Test
    fun `the PRTEE sums pain to 50 and halves function to 50, each left out filled by the mean of its part, once half of each is answered`() {
        val pain = itemsOf("prtee", "schmerz")
        val function = itemsOf("prtee", "funktion")
        assertEquals(listOf(5, 10), listOf(pain.size, function.size))

        assertEquals(listOf(5000L, 5000L, 10000L), values("prtee", allScales("prtee", 10)))
        assertEquals(listOf(0L, 0L, 0L), values("prtee", allScales("prtee", 0)))
        // Pain 2 + 4 + 6 + 1 + 8 = 21; tasks 3 × 6 = 18 and daily 5 × 4 = 20, 38 / 2 = 19; together 40.
        val mixed = on(pain, 2, 4, 6, 1, 8) + allScales("prtee", 3, function.take(6)) + allScales("prtee", 5, function.drop(6))
        assertEquals(listOf(2100L, 1900L, 4000L), values("prtee", mixed))
        // Pain a and b left out: 6 + 1 + 8 = 15 of three, filled to five: 25. Tasks 1, 2 and 3 of six answered: 2 × 6 = 12;
        // daily 4 and 6 of four: 5 × 4 = 20; (12 + 20) / 2 = 16.
        val gaps = on(pain.drop(2), 6, 1, 8) + on(function.take(3), 1, 2, 3) + on(function.drop(6).take(2), 4, 6)
        assertEquals(listOf(2500L, 1600L, 4100L), values("prtee", gaps))
        // Two of pain, two of tasks or one of daily life is less than half.
        assertEquals(listOf(null, 1600L, null), values("prtee", gaps - pain[2]))
        assertEquals(listOf(2500L, null, null), values("prtee", gaps - function[0]))
        assertEquals(listOf(2500L, null, null), values("prtee", gaps - function[6]))
        assertEquals(0L..5000L, Scoring.range(form("prtee"), form("prtee").scores[1]))
        assertEquals(0L..10000L, Scoring.range(form("prtee"), form("prtee").scores[2]))
    }

    @Test
    fun `the PRWE-G sums pain to 50 and halves function to 50, trifft nicht zu counted as nothing, all needed`() {
        val pain = itemsOf("prwe-g", "schmerz")
        val function = itemsOf("prwe-g", "funktion")
        assertEquals(listOf(5, 10), listOf(pain.size, function.size))
        fun chose(points: Int, ids: List<String> = function) = ids.associateWith { Answer.Chosen("$points") as Answer }

        assertEquals(listOf(5000L, 5000L, 10000L), values("prwe-g", allScales("prwe-g", 10) + chose(10)))
        assertEquals(listOf(0L, 0L, 0L), values("prwe-g", allScales("prwe-g", 0) + chose(0)))
        // Pain 3 + 5 + 7 + 9 + 4 = 28; function eight times 5 and two not applicable: 40 / 2 = 20; together 48.
        val mixed = on(pain, 3, 5, 7, 9, 4) + chose(5) + mapOf(function[0] to Answer.Chosen("na"), function[9] to Answer.Chosen("na"))
        assertEquals(listOf(2800L, 2000L, 4800L), values("prwe-g", mixed))
        // An odd sum halves to a half: 7 / 2.
        assertEquals(350L, values("prwe-g", chose(0) + (function[0] to Answer.Chosen("7")))[1])
        assertEquals(listOf(null, 2000L, null), values("prwe-g", mixed - pain[0]))
        assertEquals(listOf(2800L, null, null), values("prwe-g", mixed - function[3]))
    }

    @Test
    fun `the SPADI is pain, function and both in percent of their most, all needed, higher being worse`() {
        val spadi = form("spadi")
        assertTrue(spadi.scores.all { it.direction == Direction.LowerIsBetter })
        val pain = itemsOf("spadi", "schmerz")
        val function = itemsOf("spadi", "funktion")

        assertEquals(listOf(10000L, 10000L, 10000L), values("spadi", allScales("spadi", 10)))
        assertEquals(listOf(0L, 0L, 0L), values("spadi", allScales("spadi", 0)))
        // Pain 5 × 5 = 25 of 50; function 8 × 2 = 16 of 80; together 41 of 130 = 31,54 %.
        val mixed = allScales("spadi", 5, pain) + allScales("spadi", 2, function)
        assertEquals(listOf(5000L, 2000L, 3154L), values("spadi", mixed))
        assertEquals(listOf(5000L, null, null), values("spadi", mixed - function[7]))
    }

    @Test
    fun `the SST is the yeses in percent of all twelve, one left out counting as a no, from one answer on`() {
        val yesNo = form("sst").items.filterIsInstance<YesNoItem>().map(YesNoItem::id)
        assertEquals(12, yesNo.size)
        fun answered(vararg yes: Boolean) = yesNo.zip(yes.toList()).associate { (id, y) -> id to Answer.YesNo(y) }

        assertEquals(listOf(10000L), values("sst", answered(*BooleanArray(12) { true })))
        assertEquals(listOf(0L), values("sst", answered(*BooleanArray(12) { false })))
        // Nine yeses of twelve.
        assertEquals(listOf(7500L), values("sst", answered(*BooleanArray(12) { it < 9 })))
        // Three yeses answered and the rest left out: 3 of 12.
        assertEquals(listOf(2500L), values("sst", answered(true, true, true)))
        assertEquals(listOf(null), values("sst", mapOf("schulter" to Answer.Chosen("0"))))
        assertEquals(Direction.HigherIsBetter, form("sst").scores.single().direction)
    }

    @Test
    fun `the Walch-Duplay sums its four parts from -25 to 100, all needed, rated from Schlecht to Exzellent`() {
        val walch = form("walch-duplay")
        assertEquals(-2500L..10000L, Scoring.range(walch, walch.scores.single()))

        assertEquals(listOf(10000L), values("walch-duplay", chosen("walch-duplay", 25, 25, 25, 25, items = listOf("a", "b", "c", "d"))))
        assertEquals(listOf(-2500L), values("walch-duplay", chosen("walch-duplay", 0, -25, 0, 0, items = listOf("a", "b", "c", "d"))))
        // Lower level 15, slight worries 15, pain at strain 15, abduction < 120° 5: 50.
        val mixed = chosen("walch-duplay", 15, 15, 15, 5, items = listOf("a", "b", "c", "d"))
        assertEquals(listOf(5000L), values("walch-duplay", mixed))
        assertEquals(listOf(null), values("walch-duplay", mixed - "d"))

        assertEquals("Schlecht", bandAt("walch-duplay", -2500))
        assertEquals("Schlecht", bandAt("walch-duplay", 5000))
        assertEquals("Mittel", bandAt("walch-duplay", 5100))
        assertEquals("Mittel", bandAt("walch-duplay", 7500))
        assertEquals("Gut", bandAt("walch-duplay", 7600))
        assertEquals("Gut", bandAt("walch-duplay", 9000))
        assertEquals("Exzellent", bandAt("walch-duplay", 9100))
    }

    @Test
    fun `the WOSI sums 21 scales to 210 and each part, all needed, higher being worse`() {
        val wosi = form("wosi")
        assertEquals(listOf(21, 10, 4, 4, 3), wosi.scores.map { it.items.size })

        assertEquals(listOf(21000L, 10000L, 4000L, 4000L, 3000L), values("wosi", allScales("wosi", 10)))
        assertEquals(List(5) { 0L }, values("wosi", allScales("wosi", 0)))
        // 1 … 21 to 231 would pass 210; scales 0 to 9 then 0 to 9 then 0: A 45, B 0 + … + 3 = 6, C 4 + … + 7 = 22, D 8 + 9 + 0 = 17.
        val mixed = scales("wosi", *IntArray(21) { it % 10 })
        assertEquals(listOf(9000L, 4500L, 600L, 2200L, 1700L), values("wosi", mixed))
        assertEquals(listOf(null, 4500L, 600L, 2200L, null), values("wosi", mixed - "21"))
    }

    @Test
    fun `the DASH is 0 to 100 of what is answered with at most three left out, and each module 0 to 100 with none left out`() {
        val dash = form("dash")
        val disability = itemsOf("dash", "dash")
        assertEquals(listOf(30, 4, 4), dash.scores.map { it.items.size })

        assertEquals(listOf(0L, 0L, 0L), values("dash", all("dash", 1)))
        assertEquals(listOf(10000L, 10000L, 10000L), values("dash", all("dash", 5)))
        // Fifteen 2s and fifteen 3s: (75 / 30 - 1) × 25 = 37,5.
        val mixed = chosen("dash", *Array<Int?>(30) { if (it < 15) 2 else 3 }, items = disability)
        assertEquals(3750L, values("dash", mixed)[0])
        // Three left out: 12 × 2 + 15 × 3 = 69 of 27, (69 / 27 - 1) × 25 = 38,89.
        assertEquals(3889L, values("dash", mixed - disability.take(3))[0])
        assertNull(values("dash", mixed - disability.take(4))[0])
        // Sport 1 + 2 + 3 + 4: (10 / 4 - 1) × 25 = 37,5; work with one left out has none.
        val modules = chosen("dash", 1, 2, 3, 4, items = itemsOf("dash", "sport")) + chosen("dash", 2, 2, 2, items = itemsOf("dash", "arbeit"))
        assertEquals(listOf(null, 3750L, null), values("dash", modules))
        assertTrue(dash.scores.all { it.direction == Direction.LowerIsBetter })
    }
}
