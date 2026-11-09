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
import de.emilyshub.doku.assessments.Forms.on
import de.emilyshub.doku.assessments.Forms.scales
import de.emilyshub.doku.assessments.Forms.values

/**
 * The forms of DIGOTOR's subcategory "Schmerz und Psychosoziales", its part on pain (#71), each scored as its PDF says:
 * the least and the most, a case worked out by hand, every item needed, and its bands at their bounds.
 */
class PainAssessmentsTest {

    /** Yes to the items [yes], no to the rest of [ids]. */
    private fun yes(ids: List<String>, vararg yes: String): Map<String, Answer> = ids.associateWith { Answer.YesNo(it in yes) }

    /** [days] typed for each of [ids], in order. */
    private fun days(ids: List<String>, vararg days: Int): Map<String, Answer> = ids.zip(days.toList()).associate { (id, d) -> id to Answer.Number(d * 100L) }

    private val one2ten = (1..10).map(Int::toString)

    @Test
    fun `every form on pain stands under Schmerz und Psychosoziales, the GCPS and the NPQ also under Kiefer CMD, each found`() {
        val ids = listOf("csi-9", "csi-ge", "dn4", "fess", "gcps", "lanss", "midas", "npq", "oerebro", "pcs", "start-back")
        assertEquals(ids, PAIN.map(Assessment::id).sorted())
        ids.forEach { id ->
            val both = id == "gcps" || id == "npq"
            assertEquals(id, if (both) setOf(AssessmentCategory.PainPsychosocial, AssessmentCategory.JawCmd) else setOf(AssessmentCategory.PainPsychosocial), form(id).categories.toSet())
            val typed = form(id).abbreviation ?: form(id).name
            assertTrue("$typed: $id", id in BuiltInAssessments.search(typed, hidden = emptySet()).map(Assessment::id))
        }
        assertTrue("start-back" in BuiltInAssessments.search("start back", hidden = emptySet()).map(Assessment::id))
        assertTrue("oerebro" in BuiltInAssessments.search("örebro", hidden = emptySet()).map(Assessment::id))
        val byCategory = BuiltInAssessments.byCategory(hidden = emptySet())
        assertTrue(byCategory.getValue(AssessmentCategory.JawCmd).any { it.id == "gcps" })
        assertTrue(byCategory.getValue(AssessmentCategory.PainPsychosocial).any { it.id == "micheli" })
        assertEquals("CSI-GE, Stand 09/2026", form("csi-ge").source)
        assertTrue(PAIN.all { it.source.endsWith("Stand 09/2026") })
    }

    @Test
    fun `the CSI-GE sums part A to 100, all needed, from 40 central sensitization, part B counting nothing`() {
        assertEquals(listOf(0L), values("csi-ge", all("csi-ge", 0)))
        assertEquals(listOf(10000L), values("csi-ge", all("csi-ge", 4)))
        // 2 + 1 + 0 + 3 + 4 five times over.
        val mixed = chosen("csi-ge", *Array<Int?>(25) { listOf(2, 1, 0, 3, 4)[it % 5] })
        assertEquals(listOf(5000L), values("csi-ge", mixed))
        assertEquals(listOf(5000L), values("csi-ge", mixed + mapOf("b1" to Answer.YesNo(true), "b1-jahr" to Answer.Number(201500))))
        assertEquals(listOf(null), values("csi-ge", mixed - "a25"))

        val csi = form("csi-ge")
        assertEquals(listOf("b1", "b1-jahr"), Scoring.visible(csi, mapOf("b1" to Answer.YesNo(true))).map(Item::id).filter { it == "b1" || it == "b1-jahr" })
        assertEquals(listOf("b1"), Scoring.visible(csi, mapOf("b1" to Answer.YesNo(false))).map(Item::id).filter { it == "b1" || it == "b1-jahr" })
        assertEquals(listOf("nie", "selten", "gelegentlich", "oft", "immer"), (csi.item("a1") as ChoiceItem).options.map(Option::label))

        assertNull(bandAt("csi-ge", 3900))
        assertEquals("Mit einer zentralen Sensibilisierung gleichzusetzen (>/=40)", bandAt("csi-ge", 4000))
        assertEquals(Direction.LowerIsBetter, csi.scores.single().direction)
    }

    @Test
    fun `the CSI-9 sums nine items to 36, all needed, mild to 9, moderate to 19, severe from 20`() {
        assertEquals(listOf(0L), values("csi-9", all("csi-9", 0)))
        assertEquals(listOf(3600L), values("csi-9", all("csi-9", 4)))
        assertEquals(listOf(2000L), values("csi-9", chosen("csi-9", 1, 2, 3, 4, 0, 1, 2, 3, 4)))
        assertEquals(listOf(null), values("csi-9", chosen("csi-9", 1, 2, 3, 4, 0, 1, 2, 3)))

        assertEquals(listOf("leicht", "leicht", "moderat", "moderat", "stark", "stark"), listOf(0L, 900, 1000, 1900, 2000, 3600).map { bandAt("csi-9", it) })
    }

    @Test
    fun `the DN4 counts its ten yeses, the examination's among them, all needed, from 4 neuropathic`() {
        assertEquals(listOf(0L), values("dn4", yes(one2ten)))
        assertEquals(listOf(1000L), values("dn4", yes(one2ten, *one2ten.toTypedArray())))
        // Burning, tingling, hypoaesthesia to touch, brushing.
        assertEquals(listOf(400L), values("dn4", yes(one2ten, "1", "4", "8", "10")))
        assertEquals(listOf(null), values("dn4", yes(one2ten, "1", "4", "8", "10") - "9"))
        assertEquals(4, form("dn4").sections.size)

        assertEquals("eher kein neuropathischer Schmerz", bandAt("dn4", 300))
        assertEquals("Hinweis auf neuropathischen Schmerz", bandAt("dn4", 400))
    }

    @Test
    fun `the LANSS sums its symptoms and signs to 24, all needed, from 12 neuropathic`() {
        assertEquals(listOf(0L), values("lanss", all("lanss", 0)))
        assertEquals(listOf(2400L), values("lanss", chosen("lanss", 5, 5, 3, 2, 1, 5, 3)))
        // Pins and needles, touch, burning, pressure: 5 + 3 + 1 + 3.
        assertEquals(listOf(1200L), values("lanss", chosen("lanss", 5, 0, 3, 0, 1, 0, 3)))
        assertEquals(listOf(null), values("lanss", chosen("lanss", 5, 0, 3, 0, 1, 0)))

        assertNull(bandAt("lanss", 1100))
        assertEquals("Neuropathische Schmerzursache (>=12)", bandAt("lanss", 1200))
    }

    private fun gcps(days: Int?, vararg scales: Int): List<Long?> =
        values("gcps", (if (days == null) emptyMap() else mapOf("1" to Answer.Number(days * 100L))) + on(listOf("2", "3", "4", "5", "6", "7"), *scales))

    @Test
    fun `the GCPS grades from I to IV by its impairment points and its pain intensity`() {
        // Days, their BP, impairment, its BP, all BP, intensity, grade.
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 0L, 100L), gcps(0, 0, 0, 0, 0, 0, 0))
        assertEquals(listOf(18400L, 300L, 10000L, 300L, 600L, 10000L, 400L), gcps(184, 10, 10, 10, 10, 10, 10))
        // 10 days 1 BP; impairment 8 / 3 x 10 = 26,67, 0 BP; intensity 18 / 3 x 10 = 60: less than 3 BP, from 50 on, grade II.
        assertEquals(listOf(1000L, 100L, 2667L, 0L, 100L, 6000L, 200L), gcps(10, 5, 6, 7, 3, 3, 2))
        assertEquals(listOf(null, null, 2667L, 0L, null, 6000L, null), gcps(null, 5, 6, 7, 3, 3, 2))
        assertEquals(null, gcps(10, 5, 6, 7, 3, 3).last())
        assertEquals(listOf(null, null), gcps(185, 0, 0, 0, 0, 0, 0).take(2))
        assertEquals(null, values("gcps", mapOf("1" to Answer.Number(1050)) + allScales("gcps", 0)).first())
        assertTrue(form("gcps").item("hauptschmerzen") is TextItem)
    }

    @Test
    fun `the GCPS bands meet at the bounds of the original`() {
        val dayPoints = listOf(6, 7, 14, 15, 30, 31).map { gcps(it, 0, 0, 0, 0, 0, 0)[1] }
        assertEquals(listOf(0L, 100L, 100L, 200L, 200L, 300L), dayPoints)
        // 26,67 and 30; 46,67 and 50; 66,67 and 70.
        val impairmentPoints = listOf(8, 9, 14, 15, 20, 21).map { sum -> gcps(0, 0, 0, 0, sum - 2 * (sum / 3), sum / 3, sum / 3)[3] }
        assertEquals(listOf(0L, 100L, 100L, 200L, 200L, 300L), impairmentPoints)
        // Intensity 46,67 grade I, 50 grade II; 3 BP grade III, 5 BP grade IV.
        assertEquals(100L, gcps(14, 4, 5, 5, 0, 0, 0).last())
        assertEquals(200L, gcps(14, 5, 5, 5, 0, 0, 0).last())
        // 2 BP is still less than 3: grade II from 50 on.
        assertEquals(200L, gcps(15, 5, 5, 5, 0, 0, 0).last())
        assertEquals(300L, gcps(31, 0, 0, 0, 0, 0, 0).last())
        assertEquals(300L, gcps(31, 10, 10, 10, 3, 3, 3).last())
        assertEquals(400L, gcps(15, 0, 0, 0, 10, 10, 10).last())

        val grade = form("gcps").scores.size - 1
        assertEquals("Grad I: Geringe Schmerzintensität, funktionaler chronischer Schmerz", bandAt("gcps", 100, grade))
        assertEquals("Grad IV: Hochgradige Einschränkung, dysfunktionaler chronischer Schmerz", bandAt("gcps", 400, grade))
        assertEquals(100L..400L, Scoring.range(form("gcps"), form("gcps").scores[grade]))
    }

    @Test
    fun `the NPQ counts its right answers to 12, unsure counting none, all needed`() {
        val right = mapOf(3 to "r", 5 to "r", 6 to "r", 12 to "r").withDefault { "f" }
        val answers = (1..12).associate { "$it" to Answer.Chosen(right.getValue(it)) }
        assertEquals(listOf(1200L), values("npq", answers))
        assertEquals(listOf(0L), values("npq", (1..12).associate { "$it" to Answer.Chosen("u") }))
        assertEquals(listOf(400L), values("npq", (1..12).associate { "$it" to Answer.Chosen("r") }))
        assertEquals(listOf(800L), values("npq", (1..12).associate { "$it" to Answer.Chosen("f") }))
        assertEquals(listOf(null), values("npq", answers - "12"))
        assertEquals(Direction.HigherIsBetter, form("npq").scores.single().direction)
    }

    @Test
    fun `the MIDAS sums the days of questions 1 to 5, not A and B, all needed, in grades I to IV`() {
        val counted = listOf("1", "2", "3", "4", "5")
        assertEquals(listOf(0L), values("midas", days(counted, 0, 0, 0, 0, 0)))
        assertEquals(listOf(46000L), values("midas", days(counted, 92, 92, 92, 92, 92)))
        assertEquals(listOf(1000L), values("midas", days(counted, 2, 3, 0, 1, 4) + mapOf("a" to Answer.Number(3000), "b" to Answer.OnScale(7))))
        assertEquals(listOf(null), values("midas", days(counted, 2, 3, 0, 1)))
        assertEquals(listOf(null), values("midas", days(counted, 2, 3, 0, 1, 93)))

        assertEquals(
            listOf("Grad I", "Grad I", "Grad II", "Grad II", "Grad III", "Grad III", "Grad IV", "Grad IV"),
            listOf(0L, 500, 600, 1000, 1100, 2000, 2100, 46000).map { bandAt("midas", it)!!.substringBefore(':') },
        )
    }

    @Test
    fun `the Oerebro sums duration and nine scales to 100, three reversed, all needed, above 50 a higher risk`() {
        val reversed = listOf("3", "4", "8")
        val scaleIds = (2..10).map(Int::toString)
        val least = chosen("oerebro", 1) + allScales("oerebro", 0) + allScales("oerebro", 10, items = reversed)
        assertEquals(listOf(100L), values("oerebro", least))
        val most = chosen("oerebro", 10) + allScales("oerebro", 10) + allScales("oerebro", 0, items = reversed)
        assertEquals(listOf(10000L), values("oerebro", most))
        // 6-8 weeks 5; 6, 10 - 7, 10 - 4, 5, 2, 8, 10 - 9, 3, 0.
        val mixed = chosen("oerebro", 5) + on(scaleIds, 6, 7, 4, 5, 2, 8, 9, 3, 0)
        assertEquals(listOf(3900L), values("oerebro", mixed))
        assertEquals(listOf(null), values("oerebro", mixed - "10"))
        assertEquals("über 1 Jahr", (form("oerebro").item("1") as ChoiceItem).options.single { it.points == 10 }.label)

        assertNull(bandAt("oerebro", 5000))
        assertTrue(bandAt("oerebro", 5100)!!.startsWith("Höheres geschätztes Risiko"))
    }

    /** Yes to [yes] of 1 to 8, and to 9 "mäßig" (0) or, if [bothering], "extrem" (1). */
    private fun startBack(bothering: Int, vararg yes: String) =
        values("start-back", yes((1..8).map(Int::toString), *yes) + mapOf("9" to Answer.Chosen(if (bothering == 1) "4" else "2")))

    @Test
    fun `the STarT Back sums nine to its total and 5 to 9 to its part, and grades the risk low, medium or high by both`() {
        // Total, part, risk.
        assertEquals(listOf(0L, 0L, 100L), startBack(0))
        assertEquals(listOf(900L, 500L, 300L), startBack(1, "1", "2", "3", "4", "5", "6", "7", "8"))
        // Three yes and "mäßig": low at 3.
        assertEquals(listOf(300L, 0L, 100L), startBack(0, "1", "2", "3"))
        // From 4 on, three of 5 to 9: medium; four: high.
        assertEquals(listOf(400L, 300L, 200L), startBack(0, "1", "5", "6", "7"))
        assertEquals(listOf(400L, 400L, 300L), startBack(1, "5", "6", "7"))
        assertEquals(listOf(null, null, null), values("start-back", yes((1..8).map(Int::toString), "1")))
        assertEquals(listOf(0, 0, 0, 1, 1), (form("start-back").item("9") as ChoiceItem).options.map(Option::points))

        assertEquals(listOf("Geringes Chronifizierungsrisiko", "Mittleres Chronifizierungsrisiko", "Hohe Chronifizierungsgefahr"), listOf(100L, 200, 300).map { bandAt("start-back", it, score = 2) })
    }

    @Test
    fun `the PCS sums thirteen to 52 and its three subscales, each needing its own items`() {
        // Total, helplessness, magnification, rumination.
        assertEquals(listOf(0L, 0L, 0L, 0L), values("pcs", all("pcs", 0)))
        assertEquals(listOf(5200L, 2400L, 1200L, 1600L), values("pcs", all("pcs", 4)))
        // 1-5 and 12: 1 + 2 + 3 + 4 + 0 + 2; 6, 7, 13: 1 + 2 + 3; 8-11: 3 + 4 + 0 + 1.
        val mixed = chosen("pcs", 1, 2, 3, 4, 0, 1, 2, 3, 4, 0, 1, 2, 3)
        assertEquals(listOf(2600L, 1200L, 600L, 800L), values("pcs", mixed))
        assertEquals(listOf(null, 1200L, null, 800L), values("pcs", mixed - "13"))
        assertTrue(form("pcs").scores.all { it.direction == Direction.LowerIsBetter })
    }

    @Test
    fun `the FESS sums ten from 0 to 6 to 60, higher better, all needed, its limits extreme to minimal`() {
        assertEquals(listOf(0L), values("fess", allScales("fess", 0)))
        assertEquals(listOf(6000L), values("fess", allScales("fess", 6)))
        assertEquals(listOf(2700L), values("fess", scales("fess", 1, 2, 3, 4, 5, 6, 0, 1, 2, 3)))
        assertEquals(listOf(null), values("fess", scales("fess", 1, 2, 3, 4, 5, 6, 0, 1, 2)))
        assertEquals(Direction.HigherIsBetter, form("fess").scores.single().direction)

        assertEquals(
            listOf("extrem", "moderat", "moderat", "gering", "gering", "minimal/nicht"),
            listOf(1900L, 2000, 3000, 3100, 4000, 4100).map { bandAt("fess", it)!!.substringAfter(": ") },
        )
    }
}
