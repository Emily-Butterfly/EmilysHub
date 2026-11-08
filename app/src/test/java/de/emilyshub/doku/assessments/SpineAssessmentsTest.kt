package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The forms of DIGOTOR's subcategory "Wirbelsäule" (#67), each scored as its PDF says: the least and the most, a case
 * worked out by hand, missing answers by its rule, and its bands at their bounds.
 */
class SpineAssessmentsTest {

    private fun form(id: String) = checkNotNull(BuiltInAssessments.byId(id)) { id }

    /** The results of [id] with [answers]. */
    private fun results(id: String, answers: Map<String, Answer>) = Scoring.results(form(id), answers)

    /** Each item of [id] that has options answered by the option of these [points], in order, those left out unanswered. */
    private fun chosen(id: String, vararg points: Int?): Map<String, Answer> =
        form(id).items.filterIsInstance<ChoiceItem>().zip(points.toList()).mapNotNull { (item, p) ->
            p?.let { item.id to Answer.Chosen(item.options.single { it.points == p }.id) }
        }.toMap()

    /** Each yes/no item of [id] answered yes if its number is in [yes], else no. */
    private fun yesNo(id: String, yes: Set<Int>): Map<String, Answer> =
        form(id).items.filterIsInstance<YesNoItem>().withIndex().associate { (i, item) -> item.id to Answer.YesNo(i + 1 in yes) }

    /** The band of the only or first result of [id] at [hundredths]. */
    private fun bandAt(id: String, hundredths: Long, score: Int = 0) = ScoreResult(form(id).scores[score], hundredths).band

    @Test
    fun `every form of the spine stands under Wirbelsaeule, the Micheli scale also under pain, each found by its abbreviation`() {
        val spine = BuiltInAssessments.byCategory(hidden = emptySet()).getValue(AssessmentCategory.Spine).map(Assessment::id)

        assertEquals(setOf("csiq", "hdi", "lsiq", "micheli", "ndi", "odi", "rmdq", "voms"), spine.toSet())
        assertEquals(listOf(AssessmentCategory.Spine, AssessmentCategory.PainPsychosocial), form("micheli").categories)
        listOf("csiq", "hdi", "lsiq", "ndi", "odi", "rmdq", "voms").forEach { id ->
            // "ndi" is in "Landing" too.
            assertTrue(id, id in BuiltInAssessments.search(id, hidden = emptySet()).map(Assessment::id))
        }
        assertEquals(listOf("micheli"), BuiltInAssessments.search("micheli", hidden = emptySet()).map(Assessment::id))
        assertEquals("ODI, Stand 11/2026", form("odi").source)
    }

    @Test
    fun `the CSIQ counts its yeses from 0 to 16, lower being better, and needs all answered`() {
        val score = form("csiq").scores.single()
        assertEquals(Direction.LowerIsBetter, score.direction)
        assertEquals(16, form("csiq").items.size)

        assertEquals(0L, results("csiq", yesNo("csiq", emptySet())).single().value)
        assertEquals(1600L, results("csiq", yesNo("csiq", (1..16).toSet())).single().value)
        assertEquals(300L, results("csiq", yesNo("csiq", setOf(1, 5, 9))).single().value)
        assertNull(results("csiq", yesNo("csiq", setOf(1)) - "16").single().value)
    }

    @Test
    fun `the LSIQ counts its yeses from 0 to 15, and from 9 on marks who responds to motor control`() {
        assertEquals(15, form("lsiq").items.size)
        assertEquals(0L, results("lsiq", yesNo("lsiq", emptySet())).single().value)
        assertNull(results("lsiq", yesNo("lsiq", (1..15).toSet()) - "15").single().value)
        assertEquals(1500L, results("lsiq", yesNo("lsiq", (1..15).toSet())).single().value)
        assertEquals(400L, results("lsiq", yesNo("lsiq", setOf(2, 4, 6, 8))).single().value)

        assertNull(bandAt("lsiq", 800))
        assertTrue(bandAt("lsiq", 900)!!.startsWith("typisch"))
        assertTrue(bandAt("lsiq", 1500)!!.startsWith("typisch"))
    }

    @Test
    fun `the HDI scores 4, 2 and 0 to a total of 100 with its emotional and functional subscales`() {
        val hdi = form("hdi")
        assertEquals(listOf("Gesamtpunktzahl", "Emotional (E)", "Funktional (F)"), hdi.scores.map(Score::name))
        assertEquals(listOf(0L..10000L, 0L..5200L, 0L..4800L), hdi.scores.map { Scoring.range(hdi, it) })

        assertEquals(listOf(0L, 0L, 0L), results("hdi", chosen("hdi", *Array(25) { 0 })).map(ScoreResult::value))
        assertEquals(listOf(10000L, 5200L, 4800L), results("hdi", chosen("hdi", *Array(25) { 4 })).map(ScoreResult::value))
        // 1–5 ja, 6–10 manchmal: E = 4 (1) + 4 (3) + 4 (5) + 2 (6) + 2 (8) + 2 (9) + 2 (10), F = 4 (2) + 4 (4) + 2 (7).
        val mixed = results("hdi", chosen("hdi", 4, 4, 4, 4, 4, 2, 2, 2, 2, 2, *Array(15) { 0 }))
        assertEquals(listOf(3000L, 2000L, 1000L), mixed.map(ScoreResult::value))
        assertEquals("Moderate Beeinträchtigung", mixed[0].band)
        assertNull(results("hdi", chosen("hdi", *Array(24) { 4 })).first().value)
    }

    @Test
    fun `the HDI bands meet at their bounds, 70 counting as severe`() {
        val bands = listOf(8, 10, 28, 30, 48, 50, 70, 72, 100).map { bandAt("hdi", it * 100L) }

        assertEquals(
            listOf(
                null, "Leichte Beeinträchtigung", "Leichte Beeinträchtigung", "Moderate Beeinträchtigung", "Moderate Beeinträchtigung",
                "Schwere Beeinträchtigung", "Schwere Beeinträchtigung", "Vollständige Beeinträchtigung", "Vollständige Beeinträchtigung",
            ),
            bands,
        )
        assertNull(bandAt("hdi", 5200, score = 1))
    }

    @Test
    fun `the Micheli scale counts four times its points to percent, the pain line in whole centimetres`() {
        val micheli = form("micheli")
        assertEquals(listOf(0, 1, 2, 3, 4, 5), (micheli.item("a1") as ChoiceItem).options.map(Option::points))
        val pain = micheli.item("c1") as ScaleItem
        assertEquals(0..10, pain.min..pain.max)
        val with = { a: Int, b1: Int, b2: Int, b3: Int, c: Int? ->
            results("micheli", chosen("micheli", a, b1, b2, b3) + listOfNotNull(c?.let { "c1" to Answer.OnScale(it) })).single().value
        }

        assertEquals(0L, with(0, 0, 0, 0, 0))
        assertEquals(10000L, with(5, 4, 3, 3, 10))
        // (2 + 1 + 2 + 0 + 6) × 4
        assertEquals(4400L, with(2, 1, 2, 0, 6))
        assertNull(with(2, 1, 2, 0, null))
        assertEquals("%", micheli.scores.single().unit)
    }

    @Test
    fun `the NDI is a percent of 50, one section left out lowering the most possible, two giving none`() {
        assertEquals(0L, results("ndi", chosen("ndi", *Array(10) { 0 })).single().value)
        assertEquals(10000L, results("ndi", chosen("ndi", *Array(10) { 5 })).single().value)
        // 11 of 50
        assertEquals(2200L, results("ndi", chosen("ndi", 1, 2, 0, 3, 1, 0, 2, 1, 0, 1)).single().value)
        // 10 of 45
        assertEquals(2222L, results("ndi", chosen("ndi", 1, 2, 0, 3, 1, 0, 2, 1, 0, null)).single().value)
        assertNull(results("ndi", chosen("ndi", 1, 2, 0, 3, 1, 0, 2, 1, null, null)).single().value)
    }

    @Test
    fun `the NDI bands mark little up to 8 percent and very severe above 40`() {
        assertEquals(listOf("Patienten mit einer geringen Symptomatik", null, null, "Patienten mit einer sehr schweren Symptomatik, der auch eine Chronifizierung begünstigt"), listOf(800L, 801L, 4000L, 4001L).map { bandAt("ndi", it) })
    }

    @Test
    fun `the ODI is a percent, one section left out lowering the most possible, two giving none`() {
        val all = results("odi", chosen("odi", *Array(10) { 0 })).single()
        assertEquals(0L, all.value)
        assertEquals("minimale Funktionseinschränkung", all.band)
        assertEquals(10000L, results("odi", chosen("odi", *Array(10) { 5 })).single().value)
        // 17 × 2
        assertEquals(3400L, results("odi", chosen("odi", 2, 1, 3, 2, 1, 0, 2, 3, 1, 2)).single().value)
        // 15 of 45
        assertEquals(3333L, results("odi", chosen("odi", 2, 1, 3, 2, 1, 0, 2, 3, 1, null)).single().value)
        assertNull(results("odi", chosen("odi", 2, 1, 3, 2, 1, 0, 2, 3, null, null)).single().value)
    }

    @Test
    fun `the ODI bands are Fairbank's five`() {
        val bands = listOf(2000L, 2100L, 4000L, 4100L, 6000L, 6100L, 8000L, 8100L, 10000L).map { bandAt("odi", it) }

        assertEquals(
            listOf(
                "minimale Funktionseinschränkung", "moderate Funktionseinschränkung", "moderate Funktionseinschränkung",
                "starke Funktionseinschränkung", "starke Funktionseinschränkung", "sehr starke Funktionseinschränkung",
                "sehr starke Funktionseinschränkung", "pflegebedürftig (bettlägerig) oder psychosozial extrem überlagert",
                "pflegebedürftig (bettlägerig) oder psychosozial extrem überlagert",
            ),
            bands,
        )
    }

    @Test
    fun `the RMDQ counts the statements ticked, none ticked being 0`() {
        val rmdq = form("rmdq")
        val list = rmdq.items.single() as ChecklistItem
        assertEquals(24, list.options.size)
        val ticked = { numbers: Set<Int> -> mapOf(list.id to Answer.Checked(list.options.filterIndexed { i, _ -> i + 1 in numbers }.map(Option::id).toSet())) }

        assertEquals(0L, results("rmdq", emptyMap()).single().value)
        assertEquals(2400L, results("rmdq", ticked((1..24).toSet())).single().value)
        assertEquals(300L, results("rmdq", ticked(setOf(1, 3, 13))).single().value)
        assertEquals(0L..2400L, Scoring.range(rmdq, rmdq.scores.single()))
    }

    @Test
    fun `the VOMS takes the symptoms of each test from 0 to 10 and the convergence three times in centimetres, without a result`() {
        val voms = form("voms")

        assertTrue(voms.scores.isEmpty())
        assertEquals(32, voms.items.filterIsInstance<ScaleItem>().size)
        assertTrue(voms.items.filterIsInstance<ScaleItem>().all { it.min == 0 && it.max == 10 })
        assertEquals(listOf("cm", "cm", "cm"), voms.items.filterIsInstance<NumberItem>().map(NumberItem::unit))
        assertEquals(8, voms.items.filterIsInstance<TextItem>().size)
    }
}
