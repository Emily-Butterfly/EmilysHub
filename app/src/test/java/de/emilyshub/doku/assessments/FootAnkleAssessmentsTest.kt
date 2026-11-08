package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The forms of DIGOTOR's subcategory "Untere Extremität", foot, ankle and the rest (#69), each scored as its PDF says: the
 * least and the most, a case worked out by hand, missing answers by its rule, and its bands at their bounds.
 */
class FootAnkleAssessmentsTest {

    private fun form(id: String) = checkNotNull(BuiltInAssessments.byId(id)) { id }

    /** The values of the results of [id] with [answers], in the order of its scores. */
    private fun values(id: String, answers: Map<String, Answer>) = Scoring.results(form(id), answers).map(ScoreResult::value)

    /** The value of result [score] of [id] with [answers]. */
    private fun value(id: String, answers: Map<String, Answer>, score: String) =
        Scoring.results(form(id), answers).single { it.score.id == score }.value

    /** The items of [id] with options among [items] (all if none), answered in order by the option of these [points], those left out unanswered. */
    private fun chosen(id: String, vararg points: Int?, items: List<String>? = null): Map<String, Answer> =
        form(id).items.filterIsInstance<ChoiceItem>().filter { items == null || it.id in items }.zip(points.toList()).mapNotNull { (item, p) ->
            p?.let { item.id to Answer.Chosen(item.options.single { o -> o.points == p && !o.notApplicable }.id) }
        }.toMap()

    /** Each item of [id] with options among [items] (all if none) answered by the option of [points]. */
    private fun all(id: String, points: Int, items: List<String>? = null): Map<String, Answer> {
        val count = form(id).items.filterIsInstance<ChoiceItem>().count { items == null || it.id in items }
        return chosen(id, *Array<Int?>(count) { points }, items = items)
    }

    /** The ids of the items of score [score] of [id]. */
    private fun itemsOf(id: String, score: String) = form(id).scores.single { it.id == score }.items

    /** Each scale of [id] answered by [values], in order. */
    private fun scales(id: String, vararg values: Int): Map<String, Answer> =
        form(id).items.filterIsInstance<ScaleItem>().zip(values.toList()).associate { (item, v) -> item.id to Answer.OnScale(v) }

    /** The band of score [score] of [id] at [hundredths]. */
    private fun bandAt(id: String, hundredths: Long, score: Int = 0) = ScoreResult(form(id).scores[score], hundredths).band

    @Test
    fun `every form of foot and ankle stands under Untere Extremitaet only, each found by its abbreviation`() {
        val ids = listOf("alr-rsi", "ankle-go", "atrs", "cait", "faam-g", "fadi", "faos", "lefs", "pgq", "rrs", "sefas", "visa-a", "visa-g")
        val lower = BuiltInAssessments.byCategory(hidden = emptySet()).getValue(AssessmentCategory.LowerExtremity).map(Assessment::id)

        assertTrue(lower.containsAll(ids))
        ids.forEach { id ->
            assertEquals(id, listOf(AssessmentCategory.LowerExtremity), form(id).categories)
            val typed = checkNotNull(form(id).abbreviation)
            assertTrue("$typed: $id", id in BuiltInAssessments.search(typed, hidden = emptySet()).map(Assessment::id))
        }
        assertEquals("ATRS, Stand 10/2026", form("atrs").source)
    }

    @Test
    fun `the ATRS sums ten scales from 0 to 10 to 100 at most, all needed`() {
        assertEquals(listOf(0L), values("atrs", scales("atrs", *IntArray(10) { 0 })))
        assertEquals(listOf(10000L), values("atrs", scales("atrs", *IntArray(10) { 10 })))
        // 0 + 1 + … + 9.
        assertEquals(listOf(4500L), values("atrs", scales("atrs", *IntArray(10) { it })))
        assertEquals(listOf(null), values("atrs", scales("atrs", *IntArray(9) { 10 })))
    }

    @Test
    fun `the ALR-RSI is the sum of twelve in percent of their most, all needed`() {
        assertEquals(listOf(0L), values("alr-rsi", scales("alr-rsi", *IntArray(12) { 0 })))
        assertEquals(listOf(10000L), values("alr-rsi", scales("alr-rsi", *IntArray(12) { 100 })))
        // Six times 50 and six times 60: 660 of 1200, as 66 of 120 on the original's 0 to 10.
        assertEquals(listOf(5500L), values("alr-rsi", scales("alr-rsi", *IntArray(12) { if (it < 6) 50 else 60 })))
        assertEquals(listOf(null), values("alr-rsi", scales("alr-rsi", *IntArray(11) { 100 })))
    }

    @Test
    fun `the Ankle-GO sums its tests and questionnaires by their ranges to 25, below 8 marking a higher risk`() {
        fun yes(vararg ids: String) = listOf("sls-stabil", "sebt-ant", "sebt-pm", "sebt-stabil", "sht-stabil", "f8t-stabil")
            .associateWith { Answer.YesNo(it in ids) }
        val every = arrayOf("sls-stabil", "sebt-ant", "sebt-pm", "sebt-stabil", "sht-stabil", "f8t-stabil")

        assertEquals(listOf(0L), values("ankle-go", all("ankle-go", 0) + yes()))
        assertEquals(listOf(2500L), values("ankle-go", chosen("ankle-go", 2, 4, 4, 2, 2, 2, 3) + yes(*every)))
        // SLS 1 + 1, SEBT 2 + ANT 1, SHT 2, F8T 1 + 1, FAAM 1 + 2, ALR-RSI 2: 14.
        val mixed = chosen("ankle-go", 1, 2, 2, 1, 1, 2, 2) + yes("sls-stabil", "sebt-ant", "f8t-stabil")
        assertEquals(listOf(1400L), values("ankle-go", mixed))
        assertEquals(listOf(null), values("ankle-go", mixed - "alr-rsi"))

        assertTrue(bandAt("ankle-go", 700)!!.startsWith("Ein Wert unter 8 Punkten"))
        assertNull(bandAt("ankle-go", 800))
    }

    @Test
    fun `the CAIT scores each side to 30, never having turned the ankle as 3, below 25 marking chronic instability`() {
        /** Each item of the side [prefix] answered by the option at these [indexes]. */
        fun side(prefix: String, vararg indexes: Int): Map<String, Answer> =
            form("cait").items.filter { it.id.startsWith(prefix) }.zip(indexes.toList()).associate { (item, i) -> item.id to Answer.Chosen((item as ChoiceItem).options[i].id) }

        assertEquals(listOf(3000L, null), values("cait", side("l", *IntArray(9) { 0 })))
        // Nie for 8, "In mehr als 2 Tagen" for 9: the least.
        assertEquals(listOf(null, 0L), values("cait", side("r", 5, 4, 3, 3, 2, 3, 4, 3, 3)))
        // The last of each: 0 for 1 to 7, 3 and 3 for never having turned it.
        assertEquals(listOf(600L, null), values("cait", side("l", 5, 4, 3, 3, 2, 3, 4, 4, 4)))
        // The second of each: 4 + 3 + 2 + 2 + 1 + 2 + 3 + 2 + 2.
        assertEquals(listOf(2100L, 2100L), values("cait", side("l", *IntArray(9) { 1 }) + side("r", *IntArray(9) { 1 })))
        assertEquals(listOf(null, null), values("cait", side("l", *IntArray(8) { 0 })))

        assertTrue(bandAt("cait", 2400)!!.startsWith("eignet sich"))
        assertNull(bandAt("cait", 2500))
    }

    @Test
    fun `the FAAM-G scores its two parts in percent of what is answered, nicht zutreffend left out`() {
        val adl = itemsOf("faam-g", "adl")
        val sport = itemsOf("faam-g", "sport")
        assertEquals(listOf(21, 8), listOf(adl.size, sport.size))

        assertEquals(listOf(10000L, 10000L), values("faam-g", all("faam-g", 4, items = adl + sport)))
        assertEquals(listOf(0L, 0L), values("faam-g", all("faam-g", 0, items = adl + sport)))
        // 19 times 3 of 76, two not applicable; sport one 2 of 4.
        val mixed = all("faam-g", 3, items = adl) + mapOf("adl1" to Answer.Chosen("na"), "adl2" to Answer.Chosen("na")) +
            chosen("faam-g", 2, items = sport)
        assertEquals(listOf(7500L, 5000L), values("faam-g", mixed))
        assertEquals(listOf(null, null), values("faam-g", emptyMap()))
    }

    @Test
    fun `the FADI sums to 104 in daily life and 32 in sport, all needed, nicht anwendbar leaving no result`() {
        assertEquals(listOf(10400L, 3200L), values("fadi", all("fadi", 4)))
        assertEquals(listOf(0L, 0L), values("fadi", all("fadi", 0)))
        assertEquals(listOf(7800L, 2400L), values("fadi", all("fadi", 3)))
        assertEquals(listOf(null, 2400L), values("fadi", all("fadi", 3) + ("alltag1" to Answer.Chosen("na"))))
        assertEquals(listOf(null, 2400L), values("fadi", all("fadi", 3) - "alltag26"))
    }

    @Test
    fun `the FAOS scores five subscales from 0 to 100, 4 being few symptoms, each once half of it is answered`() {
        val faos = form("faos")
        assertEquals(listOf(7, 9, 17, 5, 4), faos.scores.map { it.items.size })
        assertTrue(faos.scores.all { it.direction == Direction.HigherIsBetter })

        assertEquals(List(5) { 10000L }, values("faos", all("faos", 4)))
        assertEquals(List(5) { 0L }, values("faos", all("faos", 0)))
        // Symptoms 4 + 3 + 2 + 1 + 0 + 4 + 4 = 18 of 28.
        assertEquals(6429L, value("faos", chosen("faos", 4, 3, 2, 1, 0, 4, 4), "symptome"))
        assertEquals(10000L, value("faos", chosen("faos", 4, 4, 4, 4), "symptome"))
        assertNull(value("faos", chosen("faos", 4, 4, 4), "symptome"))
    }

    @Test
    fun `the LEFS sums twenty items to 80, at most four left out, below 20 marking a need of help`() {
        assertEquals(listOf(8000L), values("lefs", all("lefs", 4)))
        assertEquals(listOf(0L), values("lefs", all("lefs", 0)))
        assertEquals(listOf(6400L), values("lefs", chosen("lefs", *Array<Int?>(16) { 4 })))
        assertEquals(listOf(null), values("lefs", chosen("lefs", *Array<Int?>(15) { 4 })))
        // Ten at 1 and ten at 3.
        assertEquals(listOf(4000L), values("lefs", chosen("lefs", *Array<Int?>(20) { if (it < 10) 1 else 3 })))

        assertEquals("Grundlegende Hilfsbedürftigkeit", bandAt("lefs", 1900))
        assertNull(bandAt("lefs", 2000))
    }

    @Test
    fun `the PGQ is its sum in percent of 75, those left out counting 0, higher being worse`() {
        val pgq = form("pgq")
        assertEquals(25, pgq.items.size)
        assertEquals(Direction.LowerIsBetter, pgq.scores.single().direction)

        assertEquals(listOf(10000L), values("pgq", all("pgq", 3)))
        assertEquals(listOf(0L), values("pgq", all("pgq", 0)))
        // Sport (16) and sex (19) left out: 23 times 3 = 69 of 75.
        assertEquals(listOf(9200L), values("pgq", all("pgq", 3) - "16" - "19"))
        assertEquals(listOf(400L), values("pgq", chosen("pgq", 3)))
        assertEquals(listOf(null), values("pgq", emptyMap()))
    }

    @Test
    fun `the Running Readiness Scale counts the tests passed, 4 and more ready`() {
        assertEquals(listOf(500L), values("rrs", all("rrs", 1)))
        assertEquals(listOf(0L), values("rrs", all("rrs", 0)))
        assertEquals(listOf(300L), values("rrs", chosen("rrs", 1, 1, 0, 1, 0)))
        assertEquals(listOf(null), values("rrs", chosen("rrs", 1, 1, 1, 1)))

        assertEquals("Geringe bzw. unzureichende Laufbereitschaft", bandAt("rrs", 300))
        assertEquals("Hohe Bereitschaft für das Joggen", bandAt("rrs", 400))
    }

    @Test
    fun `the SEFAS sums twelve items scored 4 to 0 to 48, all needed`() {
        assertEquals(listOf(4800L), values("sefas", all("sefas", 4)))
        assertEquals(listOf(0L), values("sefas", all("sefas", 0)))
        assertEquals(listOf(2700L), values("sefas", chosen("sefas", 4, 3, 2, 1, 0, 4, 3, 2, 1, 0, 4, 3)))
        assertEquals(listOf(null), values("sefas", chosen("sefas", 4, 3, 2, 1, 0, 4, 3, 2, 1, 0, 4)))
    }

    @Test
    fun `the VISA-A sums to 100 with only the one of 8a, 8b and 8c chosen`() {
        val visa = form("visa-a")
        assertEquals(0L..10000L, Scoring.range(visa, visa.scores.single()))
        fun answered(stiffness: Int, pains: List<Int>, hops: Int, sport: Int, which: String, training: Int?): Map<String, Answer> =
            chosen("visa-a", stiffness, items = listOf("1")) + (2..5).associate { "$it" to Answer.OnScale(pains[it - 2]) } +
                chosen("visa-a", hops, items = listOf("6")) + chosen("visa-a", sport, items = listOf("7")) + ("8" to Answer.Chosen(which)) +
                (training?.let { chosen("visa-a", it, items = listOf("8$which")) } ?: emptyMap())

        assertEquals(listOf(0L), values("visa-a", answered(0, List(4) { 0 }, 0, 0, "c", 0)))
        assertEquals(listOf(10000L), values("visa-a", answered(10, List(4) { 10 }, 10, 10, "a", 30)))
        // 30 minutes stiff (7) + 5 + 6 + 7 + 8 + 4 hops + 7 + 11-20 minutes with pain (10) = 54.
        assertEquals(listOf(5400L), values("visa-a", answered(7, listOf(5, 6, 7, 8), 4, 7, "b", 10)))
        assertEquals(listOf(null), values("visa-a", answered(7, listOf(5, 6, 7, 8), 4, 7, "b", null)))
        assertEquals("30 Minuten", (visa.item("1") as ChoiceItem).options.single { it.points == 7 }.label)
    }

    @Test
    fun `the VISA-G sums to 100, its pain counted the other way round, with only the one of 8a, 8b and 8c chosen`() {
        val visa = form("visa-g")
        assertEquals(0L..10000L, Scoring.range(visa, visa.scores.single()))
        fun answered(pain: Int, points: List<Int>, which: String, training: Int?): Map<String, Answer> =
            mapOf("1" to Answer.OnScale(pain)) + chosen("visa-g", *points.toTypedArray(), items = (2..7).map { "$it" }) + ("8" to Answer.Chosen(which)) +
                (training?.let { chosen("visa-g", it, items = listOf("8$which")) } ?: emptyMap())

        assertEquals(listOf(0L), values("visa-g", answered(10, List(6) { 0 }, "a", 0)))
        assertEquals(listOf(10000L), values("visa-g", answered(0, List(6) { 10 }, "c", 30)))
        // Pain 3 counts 7, then 7 + 5 + 7 + 10 + 2 + 4 and 10 to 19 minutes with pain (10) = 52.
        assertEquals(listOf(5200L), values("visa-g", answered(3, listOf(7, 5, 7, 10, 2, 4), "b", 10)))
        assertEquals(listOf(null), values("visa-g", answered(3, listOf(7, 5, 7, 10, 2, 4), "b", null)))
    }
}
