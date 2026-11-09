package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction
import de.emilyshub.doku.assessments.Forms.all
import de.emilyshub.doku.assessments.Forms.chosen
import de.emilyshub.doku.assessments.Forms.form
import de.emilyshub.doku.assessments.Forms.value
import de.emilyshub.doku.assessments.Forms.values
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The forms of DIGOTOR's subcategory "Schlafverhalten" (#75), each scored as its PDF says, or as asked where it falls
 * short: the least and the most, a case worked out by hand, every item needed, and its bands at their bounds.
 */
class SleepAssessmentsTest {

    /** The band of score [score] of [id] at whole number [whole]. */
    private fun band(id: String, score: String, whole: Int) = ScoreResult(form(id).scores.single { it.id == score }, whole * 100L).band

    @Test
    fun `every sleep form stands under Schlafverhalten alone, each found by its abbreviation`() {
        val ids = listOf("asbq", "assq", "isi", "psqi", "ris")
        assertEquals(ids, SLEEP.map(Assessment::id).sorted())
        ids.forEach { id ->
            assertEquals(id, listOf(AssessmentCategory.Sleep), form(id).categories)
            assertTrue(id, id in BuiltInAssessments.search(checkNotNull(form(id).abbreviation), hidden = emptySet()).map(Assessment::id))
        }
        assertTrue("psqi" in BuiltInAssessments.search("pittsburgh", hidden = emptySet()).map(Assessment::id))
        assertTrue("ris" in BuiltInAssessments.search("regensburger", hidden = emptySet()).map(Assessment::id))
        assertTrue(SLEEP.all { it.source.contains("Stand 10/2026") })
    }

    @Test
    fun `the ASBQ sums 18 from 18 to 90 and names good, middling and bad sleep behaviour`() {
        assertEquals(listOf(1800L), values("asbq", all("asbq", 1)))
        assertEquals(listOf(9000L), values("asbq", all("asbq", 5)))
        // 1+2+3+4+5 three times, then 1, 1, 2: 45 + 4.
        assertEquals(listOf(4900L), values("asbq", chosen("asbq", 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 1, 2)))
        assertEquals(listOf(null), values("asbq", chosen("asbq", *Array<Int?>(17) { 1 })))
        assertEquals(
            listOf("gutes Schlafverhalten", "gutes Schlafverhalten", "mittelmäßiges Schlafverhalten", "mittelmäßiges Schlafverhalten", "schlechtes Schlafverhalten", "schlechtes Schlafverhalten"),
            listOf(18, 36, 37, 41, 42, 90).map { band("asbq", "gesamt", it) },
        )
        assertEquals(Direction.LowerIsBetter, form("asbq").scores.single().direction)
    }

    @Test
    fun `the ISI sums seven from 0 to 28 in four grades of insomnia`() {
        assertEquals(listOf(0L), values("isi", all("isi", 0)))
        assertEquals(listOf(2800L), values("isi", all("isi", 4)))
        assertEquals(listOf(1500L), values("isi", chosen("isi", 3, 2, 1, 4, 2, 0, 3)))
        assertEquals(listOf(null), values("isi", chosen("isi", 3, 2, 1, 4, 2, 0)))
        assertEquals(
            listOf("keine Schlaflosigkeit", "keine Schlaflosigkeit", "unterschwellige Schlaflosigkeit", "unterschwellige Schlaflosigkeit",
                "moderate Schlaflosigkeit", "moderate Schlaflosigkeit", "schwere Schlaflosigkeit", "schwere Schlaflosigkeit"),
            listOf(0, 7, 8, 14, 15, 21, 22, 28).map { band("isi", "gesamt", it) },
        )
    }

    @Test
    fun `the RIS sums ten from 0 to 40, feeling fit counted the other way round, its bed times counted nowhere`() {
        val ris = form("ris")
        assertEquals("ich fühle mich voll leistungsfähig: nie", (ris.item("9") as ChoiceItem).options.single { it.points == 4 }.label.let { "ich fühle mich voll leistungsfähig: $it" })
        assertEquals("immer", (ris.item("3") as ChoiceItem).options.single { it.points == 4 }.label)
        assertEquals(listOf(0L), values("ris", all("ris", 0)))
        assertEquals(listOf(4000L), values("ris", all("ris", 4)))
        val times = mapOf("0-von" to Answer.Time(23 * 60), "0-bis" to Answer.Time(6 * 60 + 30))
        assertEquals(listOf(1800L), values("ris", chosen("ris", 2, 1, 3, 2, 0, 1, 4, 0, 3, 2) + times))
        assertEquals(listOf(null), values("ris", chosen("ris", 2, 1, 3, 2, 0, 1, 4, 0, 3) + times))
        assertEquals(
            listOf("Unauffällig", "Unauffällig", "Auffällig", "Auffällig", "Ausgeprägt", "Ausgeprägt"),
            listOf(0, 12, 13, 24, 25, 40).map { band("ris", "gesamt", it) },
        )
    }

    /** ASSQ answered: each question by the option of these [points], 2, 15 and 16 by their first, those of [left] out. */
    private fun assq(q1: Int, q3: Int, q4: Int, q5: Int, q6: Int, chrono: List<Int>, travel: List<Int>, breathing: List<Int>, left: Set<String> = emptySet()): Map<String, Answer> {
        val points = mapOf("1" to q1, "3" to q3, "4" to q4, "5" to q5, "6" to q6, "7" to chrono[0], "8" to chrono[1], "9" to chrono[2], "10" to chrono[3],
            "11" to travel[0], "12" to travel[1], "13" to breathing[0], "14" to breathing[1], "2" to 0, "15" to 0, "16" to 0)
        return form("assq").items.filterIsInstance<ChoiceItem>().filter { it.id !in left }.associate { item ->
            val p = points.getValue(item.id)
            item.id to Answer.Chosen(if (item.id in setOf("2", "15", "16")) item.options.first().id else item.options.single { it.points == p }.id)
        }
    }

    @Test
    fun `the ASSQ scores sleep difficulty from 0 to 17 with its recommendation, and its modifiers`() {
        val none = assq(0, 0, 0, 0, 0, listOf(0, 0, 0, 0), listOf(0, 0), listOf(0, 0))
        val most = assq(4, 4, 3, 3, 3, listOf(4, 3, 3, 4), listOf(1, 1), listOf(1, 1))
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L), values("assq", none))
        assertEquals(listOf(1700L, 200L, 100L, 100L, 1400L), values("assq", most))
        // 6–7 h (3), etwas unzufrieden (3), 16–30 min (1), 1–2-mal (1), nie (0): 8; chronotype 3+1+1+2 = 7, snoring alone.
        val mixed = assq(3, 3, 1, 1, 0, listOf(3, 1, 1, 2), listOf(0, 1), listOf(1, 0))
        assertEquals(listOf(800L, 100L, 0L, 100L, 700L), values("assq", mixed))
        assertEquals(listOf(null, 100L, 0L, 100L, 700L), values("assq", assq(3, 3, 1, 1, 0, listOf(3, 1, 1, 2), listOf(0, 1), listOf(1, 0), left = setOf("6"))))
        assertEquals(listOf(800L, 100L, 0L, 100L, null), values("assq", assq(3, 3, 1, 1, 0, listOf(3, 1, 1, 2), listOf(0, 1), listOf(1, 0), left = setOf("8"))))
        assertEquals(800L, value("assq", assq(3, 3, 1, 1, 0, listOf(3, 1, 1, 2), listOf(0, 1), listOf(1, 0), left = setOf("2", "15", "16")), "schwierigkeiten"))

        assertEquals(
            listOf("keine Schlafschwierigkeiten: A", "keine Schlafschwierigkeiten: A", "geringe Schlafschwierigkeiten: A + B", "geringe Schlafschwierigkeiten: A + B",
                "mäßige Schlafschwierigkeiten: A + B + C + D + E (wenn indiziert)", "mäßige Schlafschwierigkeiten: A + B + C + D + E (wenn indiziert)",
                "starke Schlafschwierigkeiten: A + B + C + D + E", "starke Schlafschwierigkeiten: A + B + C + D + E"),
            listOf(0, 4, 5, 7, 8, 10, 11, 17).map { band("assq", "schwierigkeiten", it) },
        )
        assertEquals(listOf(null, "weiter untersuchen: D + E", "weiter untersuchen: D + E"), listOf(0, 1, 2).map { band("assq", "atmung", it) })
        assertEquals(listOf(null, "Aufklärung über Reisemanagement und Jetlag: A (spezifisch für Reisen)"), listOf(0, 1).map { band("assq", "reisen-schlaf", it) })
        assertEquals(listOf(null, "Schlafstörung schwerwiegender: A + B + C (wenn indiziert) + D (wenn indiziert)"), listOf(0, 1).map { band("assq", "reisen-tag", it) })
        assertEquals(listOf("Abendtyp", "Abendtyp", null, null), listOf(0, 4, 5, 14).map { band("assq", "chronotyp", it) })
        // The key's "20:15 – 0:30 Uhr" is the question's "22:15 – 0:30 Uhr".
        assertEquals("22:15 – 0:30 Uhr", (form("assq").item("10") as ChoiceItem).options.single { it.points == 2 }.label)
    }

    /** PSQI answered: bed at [bed], [latency] minutes, up at [up], [slept] minutes asleep, and 5a–5j, 6, 7, 8, 9 by [points]; 10 and on unanswered. */
    private fun psqi(bed: Int, latency: Int, up: Int, slept: Int, points: List<Int?>): Map<String, Answer> {
        val ids = listOf("5a", "5b", "5c", "5d", "5e", "5f", "5g", "5h", "5i", "5j", "6", "7", "8", "9")
        val chosen = ids.zip(points).mapNotNull { (id, p) -> p?.let { id to Answer.Chosen("$it") } }.toMap()
        return chosen + mapOf("1" to Answer.Time(bed), "2" to Answer.Number(latency * 100L), "3" to Answer.Time(up), "4" to Answer.Time(slept))
    }

    private val psqiScores = listOf("k1", "k2", "k3", "k4", "k5", "k6", "k7", "gesamt")

    private fun psqiValues(answers: Map<String, Answer>) = psqiScores.map { value("psqi", answers, it) }

    @Test
    fun `the PSQI scores seven components from 0 to 3 and their total from 0 to 21`() {
        val best = psqi(22 * 60, 10, 6 * 60, 8 * 60, List(14) { 0 })
        val worst = psqi(22 * 60, 90, 6 * 60, 4 * 60, List(14) { 3 })
        assertEquals(List(8) { 0L }, psqiValues(best))
        assertEquals(List(7) { 300L } + 2100L, psqiValues(worst))
        // In bed 23:30 to 6:30, 7 h; 25 min (1) and 5a once or twice (2): 3 → 2; slept 6:15 → 1; 375 of 420 = 89 % → 0;
        // 5b–5j 1+2+0+1+0+3+1+2+0 = 10 → 2; quality ziemlich schlecht 2; no pills 0; 8 and 9: 1 + 2 = 3 → 2. Total 9.
        val mixed = psqi(23 * 60 + 30, 25, 6 * 60 + 30, 6 * 60 + 15, listOf(2, 1, 2, 0, 1, 0, 3, 1, 2, 0, 2, 0, 1, 2))
        assertEquals(listOf(200L, 200L, 100L, 0L, 200L, 0L, 200L, 900L), psqiValues(mixed))
    }

    @Test
    fun `the PSQI's components lie at their bounds as Buysse has them`() {
        fun latency(minutes: Int, q5a: Int) = value("psqi", psqi(22 * 60, minutes, 6 * 60, 7 * 60, List(14) { if (it == 0) q5a else 0 }), "k2")
        assertEquals(listOf(0L, 100L, 100L, 100L, 200L, 300L), listOf(15 to 0, 16 to 0, 15 to 1, 31 to 0, 31 to 1, 61 to 3).map { (m, q) -> latency(m, q) })

        fun duration(slept: Int) = value("psqi", psqi(22 * 60, 10, 6 * 60, slept, List(14) { 0 }), "k3")
        assertEquals(listOf(0L, 100L, 100L, 200L, 200L, 300L), listOf(420, 419, 360, 359, 300, 299).map(::duration))

        // In bed 22:00 to 6:00, 480 minutes: 85 % is 408, 75 % 360, 65 % 312.
        fun efficiency(slept: Int) = value("psqi", psqi(22 * 60, 10, 6 * 60, slept, List(14) { 0 }), "k4")
        assertEquals(listOf(0L, 100L, 100L, 200L, 200L, 300L), listOf(408, 407, 360, 359, 312, 311).map(::efficiency))

        fun disturbances(sum: Int) = value("psqi", psqi(22 * 60, 10, 6 * 60, 7 * 60, listOf(0) + List(9) { i -> minOf(3, maxOf(0, sum - 3 * i)) } + List(4) { 0 }), "k5")
        assertEquals(listOf(0L, 100L, 100L, 200L, 200L, 300L), listOf(0, 1, 9, 10, 18, 19).map(::disturbances))

        fun daytime(q8: Int, q9: Int) = value("psqi", psqi(22 * 60, 10, 6 * 60, 7 * 60, List(12) { 0 } + listOf(q8, q9)), "k7")
        assertEquals(listOf(0L, 100L, 100L, 200L, 200L, 300L), listOf(0 to 0, 1 to 0, 1 to 1, 3 to 0, 2 to 2, 3 to 2).map { (a, b) -> daytime(a, b) })

        assertEquals(
            listOf("gute Schläfer", "gute Schläfer", "schlechte Schläfer", "schlechte Schläfer", "chronische Schlafstörung", "chronische Schlafstörung"),
            listOf(0, 5, 6, 10, 11, 21).map { band("psqi", "gesamt", it) },
        )
    }

    @Test
    fun `the PSQI needs every answer but 5j's frequency, which left out counts as none`() {
        val points = listOf(2, 1, 2, 0, 1, 0, 3, 1, 2, null, 2, 0, 1, 2)
        val answers = psqi(23 * 60 + 30, 25, 6 * 60 + 30, 6 * 60 + 15, points)
        // 5b–5i 1+2+0+1+0+3+1+2 = 10 → 2, as with 5j none.
        assertEquals(200L, value("psqi", answers, "k5"))
        assertEquals(900L, value("psqi", answers, "gesamt"))
        assertNull(value("psqi", answers - "5b", "k5"))
        assertNull(value("psqi", answers - "5b", "gesamt"))
        assertNull(value("psqi", answers - "1", "k4"))
        assertNull(value("psqi", answers - "2", "k2"))
        assertNull(value("psqi", answers - "4", "k3"))
        assertNull(value("psqi", answers - "9", "k7"))
        // Up at the time gone to bed: no time in bed, no efficiency.
        assertNull(value("psqi", psqi(6 * 60, 10, 6 * 60, 7 * 60, List(14) { 0 }), "k4"))
        assertTrue(form("psqi").scores.all { it.direction == Direction.LowerIsBetter })
    }
}
