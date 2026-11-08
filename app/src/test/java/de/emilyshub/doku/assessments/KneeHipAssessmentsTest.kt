package de.emilyshub.doku.assessments

import de.emilyshub.doku.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import de.emilyshub.doku.assessments.Forms.all
import de.emilyshub.doku.assessments.Forms.bandAt
import de.emilyshub.doku.assessments.Forms.chosen
import de.emilyshub.doku.assessments.Forms.form
import de.emilyshub.doku.assessments.Forms.itemsOf
import de.emilyshub.doku.assessments.Forms.scales
import de.emilyshub.doku.assessments.Forms.value
import de.emilyshub.doku.assessments.Forms.values

/**
 * The forms of DIGOTOR's subcategory "Untere Extremität", knee and hip (#68), each scored as its PDF says: the least and
 * the most, a case worked out by hand, missing answers by its rule, and its bands at their bounds.
 */
class KneeHipAssessmentsTest {

    @Test
    fun `every form of knee and hip stands under Untere Extremitaet only, each found by its abbreviation or name`() {
        val ids = setOf("acl-rsi", "harris-hip", "hoos", "hos", "icrs", "koos", "koos-child", "kos-adl", "kujala", "less", "lysholm", "tegner", "visa-p-g")
        val lower = BuiltInAssessments.byCategory(hidden = emptySet()).getValue(AssessmentCategory.LowerExtremity).map(Assessment::id)

        assertTrue(lower.containsAll(ids))
        ids.forEach { assertEquals(it, listOf(AssessmentCategory.LowerExtremity), form(it).categories) }
        mapOf(
            "acl-rsi" to "ACL-RSI", "hoos" to "HOOS", "hos" to "HOS", "icrs" to "ICRS", "koos" to "KOOS", "koos-child" to "KOOS-Child",
            "kos-adl" to "KOS-ADL", "less" to "LESS", "tegner" to "TAS", "visa-p-g" to "VISA-P-G", "harris-hip" to "Harris",
            "kujala" to "Kujala", "lysholm" to "Lysholm",
        ).forEach { (id, typed) -> assertTrue("$typed: $id", id in BuiltInAssessments.search(typed, hidden = emptySet()).map(Assessment::id)) }
        assertEquals(listOf("hos"), BuiltInAssessments.search("hos", hidden = emptySet()).map(Assessment::id))
        assertEquals("KOOS, Stand 11/2026", form("koos").source)
    }

    @Test
    fun `the ACL-RSI takes the mean of twelve scales in tens, below 51 marking those without trust in their knee`() {
        val items = form("acl-rsi").items.filterIsInstance<ScaleItem>()
        assertEquals(12, items.size)
        fun answered(vararg values: Int) = items.zip(values.toList()).associate { (item, v) -> item.id to Answer.OnScale(v) }

        assertEquals(listOf(0L), values("acl-rsi", answered(*IntArray(12) { 0 })))
        assertEquals(listOf(10000L), values("acl-rsi", answered(*IntArray(12) { 100 })))
        // Six times 50 and six times 60: 660 / 12 = 55.
        assertEquals(listOf(5500L), values("acl-rsi", answered(*IntArray(12) { if (it < 6) 50 else 60 })))
        assertEquals(listOf(null), values("acl-rsi", answered(*IntArray(11) { 100 })))

        // Eleven times 50 and once 60 make 50,83; ten times 50 and twice 60 make 51,67.
        assertEquals(listOf(5083L), values("acl-rsi", answered(*IntArray(12) { if (it < 11) 50 else 60 })))
        assertTrue(bandAt("acl-rsi", 5083)!!.startsWith("Personen, die ihrem Kniegelenk noch kein Vertrauen"))
        assertNull(bandAt("acl-rsi", 5167))
    }

    @Test
    fun `the Harris Hip Score sums 100 at most from questions and findings, each part apart, all needed for the total`() {
        val harris = form("harris-hip")
        assertEquals(listOf("Gesamtergebnis", "Schmerzen", "ADL", "Gehfähigkeit", "Deformitäten", "Mobilität"), harris.scores.map(Score::name))
        assertEquals(0L..10000L, Scoring.range(harris, harris.scores.first()))
        val yes = mapOf("verkehrsmittel" to Answer.YesNo(true))
        val no = mapOf("verkehrsmittel" to Answer.YesNo(false))

        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 0L), values("harris-hip", all("harris-hip", 0) + no))
        val best = chosen("harris-hip", 44, 4, 5, 4, 11, 11, 11, 1, 1, 1, 1, 5) + yes
        assertEquals(listOf(10000L, 4400L, 1400L, 3300L, 400L, 500L), values("harris-hip", best))
        // 30 + (2 + 1 + 3 + 2) + (8 + 7 + 8) + (1 + 1 + 0 + 1) + 3 = 67.
        val mixed = chosen("harris-hip", 30, 2, 3, 2, 8, 7, 8, 1, 1, 0, 1, 3) + yes
        assertEquals(listOf(6700L, 3000L, 800L, 2300L, 300L, 300L), values("harris-hip", mixed))
        // Without the examiner's mobility, no total, but the patient's parts.
        assertEquals(listOf(null, 3000L, 800L, 2300L, 300L, null), values("harris-hip", mixed - "mobilitaet"))

        assertEquals("Schlechtes Ergebnis (oder Funktionsstatus)", bandAt("harris-hip", 6900))
        assertEquals("Durchschnittliches Ergebnis (oder Funktionsstatus)", bandAt("harris-hip", 7000))
        assertEquals("Durchschnittliches Ergebnis (oder Funktionsstatus)", bandAt("harris-hip", 7900))
        assertEquals("Gutes Ergebnis (oder Funktionsstatus)", bandAt("harris-hip", 8000))
        assertEquals("Gutes Ergebnis (oder Funktionsstatus)", bandAt("harris-hip", 8900))
        assertEquals("Exzellentes Ergebnis (oder Funktionsstatus)", bandAt("harris-hip", 9000))
    }

    @Test
    fun `the HOOS scores five subscales from 0 to 100, 4 being few symptoms, each once half of it is answered`() {
        val hoos = form("hoos")
        assertEquals(listOf("Symptome", "Schmerz", "Aktivitäten des alltäglichen Lebens", "Sportliche Aktivitäten/Freizeit", "Lebensqualität"), hoos.scores.map(Score::name))
        assertEquals(listOf(5, 9, 17, 4, 4), hoos.scores.map { it.items.size })
        assertTrue(hoos.scores.all { it.direction == Direction.HigherIsBetter })

        assertEquals(List(5) { 10000L }, values("hoos", all("hoos", 4)))
        assertEquals(List(5) { 0L }, values("hoos", all("hoos", 0)))
        // Symptoms 4 + 3 + 2 + 1 + 0 of 20.
        assertEquals(5000L, value("hoos", chosen("hoos", 4, 3, 2, 1, 0), "symptome"))
        // Three of five answered: 4 + 4 + 2 of 12; two are too few.
        assertEquals(8333L, value("hoos", chosen("hoos", 4, 4, 2), "symptome"))
        assertNull(value("hoos", chosen("hoos", 4, 4), "symptome"))
        // Nine of seventeen for the activities, not eight.
        val adl = itemsOf("hoos", "adl")
        assertEquals(10000L, value("hoos", chosen("hoos", *Array<Int?>(9) { 4 }, items = adl), "adl"))
        assertNull(value("hoos", chosen("hoos", *Array<Int?>(8) { 4 }, items = adl), "adl"))
    }

    @Test
    fun `the HOS scores in percent of what is answered, Nicht zutreffend and the two starred left out`() {
        val hos = form("hos")
        assertEquals(listOf("Alltagsaktivitäten", "Sportaktivitäten", "Alle Aktivitäten"), hos.scores.map(Score::name))
        assertEquals(listOf(17, 9, 26), hos.scores.map { it.items.size })
        assertTrue("adl3" !in itemsOf("hos", "adl") && "adl11" !in itemsOf("hos", "adl"))
        assertTrue("funktionsgrad" !in itemsOf("hos", "alle"))
        val scored = itemsOf("hos", "alle")

        assertEquals(listOf(10000L, 10000L, 10000L), values("hos", all("hos", 4, items = scored)))
        assertEquals(listOf(0L, 0L, 0L), values("hos", all("hos", 0, items = scored)))
        // Activities: 15 times 3 of 60, two not applicable; sport 9 times 4 of 36; all 81 of 96 = 84,375.
        val notApplicable = mapOf("adl1" to Answer.Chosen("na"), "adl2" to Answer.Chosen("na"))
        val mixed = all("hos", 3, items = itemsOf("hos", "adl")) + notApplicable + all("hos", 4, items = itemsOf("hos", "sport"))
        assertEquals(listOf(7500L, 10000L, 8438L), values("hos", mixed))
        // Any number answered: one is enough; none, or none with points, is no result.
        assertEquals(listOf(7500L, null, 7500L), values("hos", chosen("hos", 3)))
        assertEquals(listOf(null, null, null), values("hos", notApplicable))
        assertEquals(listOf(null, null, null), values("hos", emptyMap()))
    }

    @Test
    fun `the ICRS takes a grade, without a result`() {
        val icrs = form("icrs")
        assertTrue(icrs.scores.isEmpty())
        assertEquals(listOf("0", "1a", "1b", "2", "3a", "3b", "3c", "3d", "4"), (icrs.items.single() as ChoiceItem).options.map(Option::id))
    }

    @Test
    fun `the KOOS scores five subscales as 100 less 25 times the mean, at most two items of each left out`() {
        val koos = form("koos")
        assertEquals(42, koos.items.size)
        assertEquals(listOf(9, 7, 17, 5, 4), koos.scores.map { it.items.size })

        assertEquals(List(5) { 10000L }, values("koos", all("koos", 0)))
        assertEquals(List(5) { 0L }, values("koos", all("koos", 4)))
        // Pain 0 + 1 + 2 + 3 + 4 + 0 + 1 + 2 + 3 = 16 of 36: 100 − 16 / 9 × 25 = 55,56.
        assertEquals(5556L, value("koos", chosen("koos", 0, 1, 2, 3, 4, 0, 1, 2, 3), "schmerz"))
        // Quality of life with two of four: 0 and 4, a mean of 2, is 50; with one, nothing.
        val quality = itemsOf("koos", "lebensqualitaet")
        assertEquals(5000L, value("koos", chosen("koos", 0, 4, items = quality), "lebensqualitaet"))
        assertNull(value("koos", chosen("koos", 0, items = quality), "lebensqualitaet"))
        // Pain with seven of nine, not six.
        assertEquals(10000L, value("koos", chosen("koos", 0, 0, 0, 0, 0, 0, 0), "schmerz"))
        assertNull(value("koos", chosen("koos", 0, 0, 0, 0, 0, 0), "schmerz"))
    }

    @Test
    fun `the KOOS-Kind scores its five subscales as the KOOS, at most two items of each left out`() {
        val child = form("koos-child")
        assertEquals(listOf("Symptome", "Schmerz", "Alltägliche Aktivitäten", "Sportliche Aktivitäten", "Lebensqualität"), child.scores.map(Score::name))
        assertEquals(listOf(6, 8, 11, 7, 6), child.scores.map { it.items.size })

        assertEquals(List(5) { 10000L }, values("koos-child", all("koos-child", 0)))
        assertEquals(List(5) { 0L }, values("koos-child", all("koos-child", 4)))
        // Sport all 2: 100 − 2 × 25 = 50; symptoms 1 + 2 + 3 + 4 of 16: 100 − 2,5 × 25 = 37,5.
        assertEquals(5000L, value("koos-child", all("koos-child", 2, items = itemsOf("koos-child", "sport")), "sport"))
        assertEquals(3750L, value("koos-child", chosen("koos-child", 1, 2, 3, 4), "symptome"))
        assertNull(value("koos-child", chosen("koos-child", 1, 2, 3), "symptome"))
    }

    @Test
    fun `the KOS-ADL counts 5 to 0 of fourteen items in percent of 70, all needed`() {
        assertEquals(listOf(10000L), values("kos-adl", all("kos-adl", 5)))
        assertEquals(listOf(0L), values("kos-adl", all("kos-adl", 0)))
        // Six symptoms at 4 and eight activities at 3: 48 of 70.
        assertEquals(listOf(6857L), values("kos-adl", chosen("kos-adl", *Array<Int?>(6) { 4 }, *Array<Int?>(8) { 3 })))
        assertEquals(listOf(null), values("kos-adl", chosen("kos-adl", *Array<Int?>(13) { 5 })))
    }

    @Test
    fun `the Kujala scale sums 13 items to 100 at most, all needed`() {
        assertEquals(13, form("kujala").items.size)
        assertEquals(listOf(0L), values("kujala", all("kujala", 0)))
        assertEquals(listOf(10000L), values("kujala", chosen("kujala", 5, 5, 5, 10, 5, 10, 10, 10, 10, 10, 10, 5, 5)))
        // The second answer of each: 3 + 3 + 3 + 8 + 4 + 8 + 7 + 8 + 8 + 8 + 6 + 3 + 3.
        assertEquals(listOf(7200L), values("kujala", chosen("kujala", 3, 3, 3, 8, 4, 8, 7, 8, 8, 8, 6, 3, 3)))
        assertEquals(listOf(null), values("kujala", chosen("kujala", 3, 3, 3, 8, 4, 8, 7, 8, 8, 8, 6, 3)))
    }

    @Test
    fun `the LESS counts the conditions met, in its two planes, 10 excellent and below 5 deficient`() {
        val items = form("less").items.filterIsInstance<YesNoItem>()
        fun met(vararg numbers: Int) = items.withIndex().associate { (i, item) -> item.id to Answer.YesNo(i + 1 in numbers) }

        assertEquals(listOf(0L, 0L, 0L), values("less", met()))
        assertEquals(listOf(1000L, 500L, 500L), values("less", met(*IntArray(10) { it + 1 })))
        assertEquals(listOf(500L, 300L, 200L), values("less", met(1, 2, 3, 6, 7)))
        assertNull(values("less", met(1) - "10").first())

        assertEquals("Defizitär", bandAt("less", 400))
        assertEquals("Moderat", bandAt("less", 500))
        assertEquals("Moderat", bandAt("less", 700))
        assertEquals("Gut", bandAt("less", 800))
        assertEquals("Gut", bandAt("less", 900))
        assertEquals("Exzellent", bandAt("less", 1000))
    }

    @Test
    fun `the Lysholm score sums eight items to 100 at most, all needed`() {
        assertEquals(listOf(0L), values("lysholm", all("lysholm", 0)))
        assertEquals(listOf(10000L), values("lysholm", chosen("lysholm", 5, 5, 15, 25, 25, 10, 10, 5)))
        // The second answer of each: 3 + 2 + 10 + 20 + 20 + 6 + 6 + 4.
        assertEquals(listOf(7100L), values("lysholm", chosen("lysholm", 3, 2, 10, 20, 20, 6, 6, 4)))
        assertEquals(listOf(null), values("lysholm", chosen("lysholm", 3, 2, 10, 20, 20, 6, 6)))
    }

    @Test
    fun `the Tegner scale is one grade from 0 to 10`() {
        val tegner = form("tegner")
        assertEquals(0L..1000L, Scoring.range(tegner, tegner.scores.single()))
        assertEquals(listOf(0L), values("tegner", chosen("tegner", 0)))
        assertEquals(listOf(1000L), values("tegner", chosen("tegner", 10)))
        assertEquals(listOf(700L), values("tegner", chosen("tegner", 7)))
        assertEquals(listOf(null), values("tegner", emptyMap()))
    }

    @Test
    fun `the VISA-P-G sums to 100 at most, with only the one of 8a, 8b and 8c chosen`() {
        val visa = form("visa-p-g")
        assertEquals(100L..10000L, Scoring.range(visa, visa.scores.single()))
        fun answered(sitting: Int, scales: List<Int>, sport: Int, which: String, training: Int?): Map<String, Answer> =
            chosen("visa-p-g", sitting, items = listOf("1")) + (2..6).associate { "$it" to Answer.OnScale(scales[it - 2]) } +
                chosen("visa-p-g", sport, items = listOf("7")) + ("8" to Answer.Chosen(which)) +
                (training?.let { chosen("visa-p-g", it, items = listOf("8$which")) } ?: emptyMap())

        assertEquals(listOf(100L), values("visa-p-g", answered(1, List(5) { 0 }, 0, "c", 0)))
        assertEquals(listOf(10000L), values("visa-p-g", answered(10, List(5) { 10 }, 10, "a", 30)))
        // 40 minutes (4) + 5 + 6 + 7 + 8 + 9 + 7 + 5-10 minutes with pain (10) = 56.
        assertEquals(listOf(5600L), values("visa-p-g", answered(4, listOf(5, 6, 7, 8, 9), 7, "b", 10)))
        // Without the time trained, or with the time of another than chosen, no result.
        assertEquals(listOf(null), values("visa-p-g", answered(4, listOf(5, 6, 7, 8, 9), 7, "b", null)))
        val other = answered(4, listOf(5, 6, 7, 8, 9), 7, "b", null) + chosen("visa-p-g", 30, items = listOf("8a"))
        assertEquals(listOf(null), values("visa-p-g", other))
    }
}
