package de.emilyshub.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The questionnaire in the doctor's summary (SPEC.md §17): the latest with the one before, and the evenings' monthly means beside it. */
class FuncapSummaryTest {

    private fun result(id: Long, day: String, a: Int) =
        FuncapResult(FuncapForm(id = id, startedOn = LocalDate.parse(day), completedOn = LocalDate.parse(day)), Funcap.score(mapOf(1 to a)))

    private val fatigue = Symptom(id = 1, name = "Erschöpfung", position = 0)
    private val muscles = Symptom(id = 5, name = "Muskelschmerzen", position = 4)
    private val head = Symptom(id = 4, name = "Kopfschmerz", position = 3)

    @Test
    fun `the latest up to the summary's end, with the one before`() {
        val results = listOf(result(3, "2026-12-20", 5), result(2, "2026-11-20", 4), result(1, "2026-10-20", 3))
        val summary = FuncapSummary.of(results, LocalDate.parse("2026-12-01"), emptyList(), emptyList())!!
        assertEquals(2L, summary.latest.form.id)
        assertEquals(1L, summary.previous!!.form.id)
        assertNull(FuncapSummary.of(results, LocalDate.parse("2026-10-19"), emptyList(), emptyList()))
    }

    @Test
    fun `the means of fatigue and pain over the 28 days up to the latest, by their names`() {
        val day = LocalDate.parse("2026-11-28")
        val values = listOf(
            SymptomValue(day, 1, 3),
            SymptomValue(day.minusDays(27), 1, 1),
            // A day before the month does not count.
            SymptomValue(day.minusDays(28), 1, 0),
            SymptomValue(day, 5, 2),
            SymptomValue(day, 4, 3),
        )
        val summary = FuncapSummary.of(listOf(result(1, "2026-11-28", 4)), day, listOf(fatigue, head, muscles), values)!!
        assertNull(summary.previous)
        // Joint pain is not among the symptoms: it is left out.
        assertEquals(listOf("Erschöpfung", "Muskelschmerzen"), summary.monthMeans.map { it.symptom.name })
        assertEquals(2.0, summary.monthMeans[0].mean.value!!, 1e-9)
        assertEquals(2, summary.monthMeans[0].mean.basis)
        assertEquals(2.0, summary.monthMeans[1].mean.value!!, 1e-9)
    }
}
