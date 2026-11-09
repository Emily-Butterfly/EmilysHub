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
import de.emilyshub.doku.assessments.Forms.scales
import de.emilyshub.doku.assessments.Forms.values

/**
 * The forms of DIGOTOR's subcategory "Schmerz und Psychosoziales", its psychosocial part (#72), each scored as its PDF
 * says: the least and the most, a case worked out by hand, every item needed, and its bands at their bounds.
 */
class PsychosocialAssessmentsTest {

    /** Each item of [id] with options answered by its option labelled [label]. */
    private fun labelled(id: String, label: String): Map<String, Answer> =
        form(id).items.filterIsInstance<ChoiceItem>().associate { item -> item.id to Answer.Chosen(item.options.single { it.label == label }.id) }

    @Test
    fun `every psychosocial form stands under Schmerz und Psychosoziales, FABQ, PHQ-15 and PHQ-4 also under Kiefer CMD, each found`() {
        val ids = listOf("fabq", "i-prrs", "ies-r", "phq-15", "phq-4", "psychologische-screeningfragen", "stress-spannung", "stressbelastung", "tsk")
        assertEquals(ids, PSYCHOSOCIAL.map(Assessment::id).sorted())
        val jaw = setOf("fabq", "phq-15", "phq-4")
        ids.forEach { id ->
            val expected = if (id in jaw) setOf(AssessmentCategory.PainPsychosocial, AssessmentCategory.JawCmd) else setOf(AssessmentCategory.PainPsychosocial)
            assertEquals(id, expected, form(id).categories.toSet())
            val typed = form(id).abbreviation ?: form(id).name
            assertTrue("$typed: $id", id in BuiltInAssessments.search(typed, hidden = emptySet()).map(Assessment::id))
        }
        assertTrue("tsk" in BuiltInAssessments.search("tampa", hidden = emptySet()).map(Assessment::id))
        assertTrue(PSYCHOSOCIAL.all { it.source.endsWith("Stand 09/2026") || it.source.endsWith("Stand 10/2026") })
        assertEquals("PHQ-4, Stand 10/2026", form("phq-4").source)
    }

    @Test
    fun `the FABQ sums physical activity of 2 to 5 and work of 6, 7, 9 to 12 and 15, the rest not counted`() {
        assertEquals(listOf(0L, 0L), values("fabq", allScales("fabq", 0)))
        assertEquals(listOf(2400L, 4200L), values("fabq", allScales("fabq", 6)))
        // PA 1 + 2 + 3 + 4; W 5 + 6 + 1 + 2 + 3 + 4 + 0.
        val mixed = scales("fabq", 6, 1, 2, 3, 4, 5, 6, 0, 1, 2, 3, 4, 5, 6, 0, 6)
        assertEquals(listOf(1000L, 2100L), values("fabq", mixed))
        assertEquals(listOf(1000L, 2100L), values("fabq", mixed - "1" - "8" - "13" - "14" - "16"))
        assertEquals(listOf(null, 2100L), values("fabq", mixed - "2"))

        assertNull(bandAt("fabq", 1500))
        assertEquals("Höheres Chronifizierungsrisiko (> 15)", bandAt("fabq", 1600))
        assertNull(bandAt("fabq", 3400, score = 1))
        assertEquals("Erhöhtes Chronifizierungsrisiko (> 34)", bandAt("fabq", 3500, score = 1))
        assertTrue(form("fabq").scores.all { it.direction == Direction.LowerIsBetter })
    }

    @Test
    fun `the Tampa sums eleven from A 1 to D 4, none reversed, all needed`() {
        assertEquals(listOf(1100L), values("tsk", all("tsk", 1)))
        assertEquals(listOf(4400L), values("tsk", all("tsk", 4)))
        assertEquals(listOf(2600L), values("tsk", chosen("tsk", 1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3)))
        assertEquals(listOf(null), values("tsk", chosen("tsk", 1, 2, 3, 4, 1, 2, 3, 4, 1, 2)))
    }

    @Test
    fun `the IES-R sums its three subscales by 0, 1, 3, 5 and suspects PTSD above 0 by Maercker's formula`() {
        // Intrusion, avoidance, hyperarousal, X.
        assertEquals(listOf(0L, 0L, 0L, -436L), values("ies-r", all("ies-r", 0)))
        // -0,7 + 2,8 + 5,25 - 4,36.
        assertEquals(listOf(3500L, 4000L, 3500L, 299L), values("ies-r", all("ies-r", 5)))
        // All "manchmal": -0,42 + 1,68 + 3,15 - 4,36.
        assertEquals(listOf(2100L, 2400L, 2100L, 5L), values("ies-r", all("ies-r", 3)))
        assertEquals(listOf(null, 2400L, 2100L, null), values("ies-r", all("ies-r", 3) - "1"))
        // No intrusion, avoidance all "oft", hyperarousal all "manchmal": 0 + 2,8 + 3,15 - 4,36.
        val ies = form("ies-r").scores
        val mixed = all("ies-r", 5, items = ies[1].items) + all("ies-r", 0, items = ies[0].items) + all("ies-r", 3, items = ies[2].items)
        assertEquals(listOf(0L, 4000L, 2100L, 159L), values("ies-r", mixed))
        assertEquals(listOf(0, 1, 3, 5), (form("ies-r").item("1") as ChoiceItem).options.map(Option::points))
        assertTrue(form("ies-r").item("vorfall") is TextItem)

        assertNull(bandAt("ies-r", 0, score = 3))
        assertEquals("Verdachtsdiagnose auf PTB", bandAt("ies-r", 1, score = 3))
        assertEquals(-506L..369L, Scoring.range(form("ies-r"), form("ies-r").scores[3]))
    }

    @Test
    fun `the I-PRRS sums six ratings in tens and divides by 10, below 50 not yet ready`() {
        assertEquals(listOf(0L), values("i-prrs", allScales("i-prrs", 0)))
        assertEquals(listOf(6000L), values("i-prrs", allScales("i-prrs", 100)))
        assertEquals(listOf(3300L), values("i-prrs", scales("i-prrs", 50, 60, 70, 80, 40, 30)))
        assertEquals(listOf(null), values("i-prrs", scales("i-prrs", 50, 60, 70, 80, 40)))
        assertEquals((0..100 step 10).toList(), (form("i-prrs").item("1") as ScaleItem).steps)

        assertTrue(bandAt("i-prrs", 4900)!!.startsWith("Psychisch noch nicht"))
        assertNull(bandAt("i-prrs", 5000))
        assertEquals(Direction.HigherIsBetter, form("i-prrs").scores.single().direction)
    }

    @Test
    fun `the PHQ-15 sums fifteen to 30, its question for women only left out if need be, the others all needed`() {
        assertEquals(listOf(0L), values("phq-15", all("phq-15", 0)))
        assertEquals(listOf(3000L), values("phq-15", all("phq-15", 2)))
        assertEquals(listOf(2800L), values("phq-15", all("phq-15", 2) - "d"))
        assertEquals(listOf(null), values("phq-15", all("phq-15", 2) - "a"))
        assertEquals(listOf(1200L), values("phq-15", chosen("phq-15", 1, 0, 2, 1, 0, 0, 1, 2, 0, 0, 1, 1, 0, 2, 1)))

        assertEquals(
            listOf("Normal", "Normal", "Milde", "Milde", "Mittelgradig", "Mittelgradig", "Schwer", "Schwer"),
            listOf(0L, 400, 500, 900, 1000, 1400, 1500, 3000).map { bandAt("phq-15", it)!!.substringBefore(' ') },
        )
    }

    @Test
    fun `the PHQ-4 sums four to 12, depression of 1 and 2, anxiety of 3 and 4, each positive from 3`() {
        assertEquals(listOf(0L, 0L, 0L), values("phq-4", all("phq-4", 0)))
        assertEquals(listOf(1200L, 600L, 600L), values("phq-4", all("phq-4", 3)))
        assertEquals(listOf(600L, 300L, 300L), values("phq-4", chosen("phq-4", 1, 2, 0, 3)))
        assertEquals(listOf(null, 300L, null), values("phq-4", chosen("phq-4", 1, 2, 0)))

        assertEquals(
            listOf("Keiner", "Keiner", "Gering", "Gering", "Moderat", "Moderat", "Stark", "Stark"),
            listOf(0L, 200, 300, 500, 600, 800, 900, 1200).map { bandAt("phq-4", it) },
        )
        assertNull(bandAt("phq-4", 200, score = 1))
        assertTrue(bandAt("phq-4", 300, score = 2)!!.startsWith("Positives Screeningergebnis"))
    }

    @Test
    fun `the screening questions sum each pair, positive from its cutoff on`() {
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L), values("psychologische-screeningfragen", allScales("psychologische-screeningfragen", 0)))
        assertEquals(listOf(1000L, 2000L, 1000L, 2000L, 2000L), values("psychologische-screeningfragen", allScales("psychologische-screeningfragen", 10)))
        val mixed = scales("psychologische-screeningfragen", 5, 2, 2, 6, 3, 3, 3, 3)
        assertEquals(listOf(500L, 400L, 600L, 600L, 600L), values("psychologische-screeningfragen", mixed))
        assertEquals(listOf(500L, null, 600L, 600L, 600L), values("psychologische-screeningfragen", mixed - "3"))

        // Anxiety from 5, fear of movement from 5, stress from 7, catastrophizing from 7, depression from 6.
        listOf(500L, 500L, 700L, 700L, 600L).forEachIndexed { score, cutoff ->
            assertTrue(bandAt("psychologische-screeningfragen", cutoff - 100, score)!!.startsWith("Negativ"))
            assertTrue(bandAt("psychologische-screeningfragen", cutoff, score)!!.startsWith("Positiv"))
        }
        assertEquals("Positiv: Wahrscheinlichkeit einer Problematik bei positivem Test (43,3%)", bandAt("psychologische-screeningfragen", 500))
        assertEquals("Negativ: Wahrscheinlichkeit einer Problematik bei negativem Test (6,5%)", bandAt("psychologische-screeningfragen", 400))
    }

    @Test
    fun `the stress load sums each of its eight factors to 120 by its own points`() {
        assertEquals(List(8) { 0L }, values("stressbelastung", labelled("stressbelastung", "Stimmt nicht")))
        assertEquals(List(8) { 12000L }, values("stressbelastung", labelled("stressbelastung", "Ist fast immer so")))
        // "Manchmal": 20 x 2, 10 x 3, 30 x 1, 10 x 3, 10 x 3, 30 x 1, 20 x 2, 30 x 1.
        val sometimes = labelled("stressbelastung", "Manchmal")
        assertEquals(listOf(4000L, 3000L, 3000L, 3000L, 3000L, 3000L, 4000L, 3000L), values("stressbelastung", sometimes))
        assertEquals(listOf(null, 3000L, 3000L, 3000L, 3000L, 3000L, 4000L, 3000L), values("stressbelastung", sometimes - "ernaehrung-1"))
        assertEquals(listOf(4, 2, 1, 0), (form("stressbelastung").item("partnerschaft-1") as ChoiceItem).options.map(Option::points))
        // Eats in haste always (6), gulps often (3), too much sometimes (2); smokes always (12); a boss to fear often (2).
        fun at(item: String, label: String) = item to Answer.Chosen((form("stressbelastung").item(item) as ChoiceItem).options.single { it.label == label }.id)
        val mixed = labelled("stressbelastung", "Stimmt nicht") + listOf(
            at("ernaehrung-1", "Ist fast immer so"), at("ernaehrung-2", "Ist oft so"), at("ernaehrung-3", "Manchmal"),
            at("gewohnheiten-1", "Ist fast immer so"), at("arbeit-1", "Ist oft so"),
        )
        assertEquals(listOf(1100L, 1200L, 200L, 0L, 0L, 0L, 0L, 0L), values("stressbelastung", mixed))
    }

    /** The tension questionnaire: 1 to 33 by [rated], the reactions at work and at home by their option ids, and the counts of 35. */
    private fun tension(rated: String, work: String, home: String, vararg counts: Int?): Map<String, Answer> {
        val items = form("stress-spannung").items.filterIsInstance<ChoiceItem>().filter { it.id.startsWith("t") && !it.id.startsWith("t34") }
        return items.associate { it.id to Answer.Chosen(it.options.single { o -> o.label == rated }.id) } +
            mapOf("t34-betrieb-reaktion" to Answer.Chosen(work), "t34-zuhause-reaktion" to Answer.Chosen(home)) +
            listOf("t35-etwas", "t35-belastend", "t35-stark").zip(counts.toList()).mapNotNull { (id, n) -> n?.let { id to Answer.Number(it * 100L) } }
    }

    @Test
    fun `the stress and tension questionnaire sums each, tension with its reactions and burdens, all needed`() {
        val stress = form("stress-spannung").items.filterIsInstance<ChoiceItem>().filter { it.id.startsWith("s") }
        fun stressAll(label: String) = stress.associate { it.id to Answer.Chosen(it.options.single { o -> o.label == label }.id) }

        assertEquals(listOf(0L, 0L), values("stress-spannung", stressAll("nie") + tension("nie", "0", "0", 0, 0, 0)))
        assertEquals(listOf(5000L, 13400L), values("stress-spannung", stressAll("häufig") + tension("häufig", "2", "2", 10, 10, 10)))
        // 33 x 1, upset 2 at work, withdrawn 4 at home, two problems a little, one burdening, one much: 2 + 2 + 3.
        assertEquals(listOf(2500L, 4600L), values("stress-spannung", stressAll("manchmal") + tension("manchmal", "1", "2", 2, 1, 1)))
        assertEquals(listOf(null, null), values("stress-spannung", (stressAll("manchmal") - "s25") + tension("manchmal", "1", "2", 2, 1)))

        assertTrue(bandAt("stress-spannung", 900)!!.startsWith("Mit Stresssituationen"))
        assertTrue(bandAt("stress-spannung", 1000)!!.startsWith("Sie sind stressgefährdet"))
        assertTrue(bandAt("stress-spannung", 2900)!!.startsWith("Ihre Stressmomente"))
        assertTrue(bandAt("stress-spannung", 3000)!!.startsWith("Sie leben in dauerndem Stress"))
        assertTrue(bandAt("stress-spannung", 1400, score = 1)!!.startsWith("Sie können sich"))
        assertTrue(bandAt("stress-spannung", 1500, score = 1)!!.startsWith("Die Kettenreaktionen"))
        assertTrue(bandAt("stress-spannung", 3000, score = 1)!!.startsWith("Sie stecken bereits"))
        assertEquals(0L..13400L, Scoring.range(form("stress-spannung"), form("stress-spannung").scores[1]))
    }
}
