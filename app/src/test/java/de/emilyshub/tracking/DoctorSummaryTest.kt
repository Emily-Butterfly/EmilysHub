package de.emilyshub.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The doctor's summary (SPEC.md §17, "Arzt-Zusammenfassung") as pure logic, with made-up values and names only. */
class DoctorSummaryTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.parse("2026-11-15")

    private fun day(back: Long) = today.minusDays(back)

    private fun at(back: Long, hour: Int) = day(back).atTime(hour, 0).atZone(berlin).toInstant()

    @Test
    fun `the weeks tracked count from the first day with anything, begun weeks counting`() {
        assertEquals("nothing tracked yet", 0, SummaryWeeks.tracked(null, today))
        assertEquals("today only", 1, SummaryWeeks.tracked(today, today))
        assertEquals("seven days are one week", 1, SummaryWeeks.tracked(day(6), today))
        assertEquals("the eighth day begins the second", 2, SummaryWeeks.tracked(day(7), today))
        assertEquals(10, SummaryWeeks.tracked(day(69), today))
    }

    @Test
    fun `4 weeks unless fewer are tracked, and never more than are or fewer than 1`() {
        assertEquals(4, SummaryWeeks.default(10))
        assertEquals(2, SummaryWeeks.default(2))
        assertEquals(10, SummaryWeeks.within(12, tracked = 10))
        assertEquals(1, SummaryWeeks.within(0, tracked = 10))
        assertEquals(1, SummaryWeeks.within(3, tracked = 0))
    }

    @Test
    fun `the weeks end today`() {
        assertEquals(day(27) to today, SummaryWeeks.days(4, today))
        assertEquals(day(6) to today, SummaryWeeks.days(1, today))
    }

    @Test
    fun `the strongest symptoms by their mean, the five highest, none felt left out`() {
        val names = listOf("Symptom A", "Symptom B", "Symptom C", "Symptom D", "Symptom E", "Symptom F", "Symptom G")
        val symptoms = names.mapIndexed { index, name -> Symptom(id = index + 1L, name = name, position = index) }
        val levels = listOf(3, 1, 2, 0, 2, 1, 1)
        val values = levels.mapIndexed { index, level -> SymptomValue(day(1), index + 1L, level) } +
            SymptomValue(day(2), 2, 3) + SymptomValue(day(40), 4, 3)
        val records = TrackingRecords(berlin, symptoms = symptoms, symptomValues = values)

        val summary = DoctorSummary.of(records, day(27), today, hints = emptyList())

        assertEquals(listOf("Symptom A", "Symptom B", "Symptom C", "Symptom E", "Symptom F"), summary.strongest.map { it.symptom.name })
        assertEquals(2.0, summary.strongest[1].mean.value!!, 0.001)
        assertEquals(2, summary.strongest[1].mean.basis)
    }

    @Test
    fun `what lies in the weeks, the first first, and the medications taken in them with their timeline`() {
        val before = Exertion(id = 1, date = day(30), kind = ExertionKind.Physical, strength = 3)
        val trigger = Exertion(id = 2, date = day(5), time = LocalTime.of(18, 0), kind = ExertionKind.Social, strength = 2)
        val episode = Episode(id = 1, kind = EpisodeKind.Pem, startDate = day(4), startTime = LocalTime.of(12, 0), severity = 2)
        val oldEvent = TrackingEvent(id = 1, kind = EventKind.Sauna, at = at(40, 10))
        val event = TrackingEvent(id = 2, kind = EventKind.Shower, at = at(3, 8), pulseBefore = 80, pulseAfter = 120)
        val stopped = Medication(id = 1, name = "Medikament A", start = day(100), end = day(50))
        val taken = Medication(id = 2, name = "Medikament B", start = day(100))
        val change = DoseChange(medicationId = 2, date = day(10), from = "1 Tablette", to = "2 Tabletten")
        val test = StandTest(id = 1, variant = StandVariant.Standing, startedAt = at(2, 9), standingAt = at(2, 9).plusSeconds(600), lyingPulse = 60, finished = true)
        val points = listOf(StandPoint(1, 1, pulse = 95), StandPoint(1, 3, pulse = 100))
        val records = TrackingRecords(
            berlin,
            events = listOf(event, oldEvent),
            exertions = listOf(trigger, before),
            episodes = listOf(episode),
            triggers = listOf(EpisodeTrigger(1, 2, Certainty.Likely)),
            medications = listOf(stopped, taken),
            doseChanges = listOf(change),
            standTests = listOf(StandRecord(test, points, StandTestEvaluation.evaluate(test, points, emptyList()))),
        )

        val summary = DoctorSummary.of(records, day(27), today, hints = emptyList())

        assertEquals(listOf(2L), summary.events.map { it.id })
        assertEquals(listOf(2L), summary.exertions.map { it.id })
        val shown = summary.episodes.single()
        assertEquals(episode, shown.episode)
        assertEquals(listOf(Triple(trigger, Certainty.Likely, 18L)), shown.triggers.map { Triple(it.exertion, it.certainty, it.hoursAfter) })
        assertEquals(listOf("Medikament B"), summary.medications.map { it.medication.name })
        assertEquals(listOf(change), summary.medications.single().changes)
        assertEquals(40, summary.standTests.single().result.maxRise)
        assertEquals(1.0, summary.figures.events.value!!, 0.001)
        assertTrue(summary.photos.isEmpty())
    }

    @Test
    fun `Garmin's values with their tier, only those with data`() {
        val garmin = GarminDays(
            steps = mapOf(day(1) to 4000L, day(2) to 6000L, day(60) to 30000L),
            sleepMinutes = mapOf(day(1) to 420L),
            deepSleepMinutes = mapOf(day(1) to 60L),
        )
        val summary = DoctorSummary.of(TrackingRecords(berlin, garmin = garmin), day(27), today, hints = emptyList())

        assertEquals(
            listOf(GarminValue.Steps to Tier.A, GarminValue.Sleep to Tier.B, GarminValue.DeepSleep to Tier.C),
            summary.garmin.map { it.value to it.tier },
        )
        assertEquals(Figure(5000.0, 2), summary.garmin.first().mean)
    }

    @Test
    fun `the photos go along only as given`() {
        val photo = SummaryPhoto(eventId = 2, takenAt = Instant.parse("2026-11-12T07:10:00Z"), jpeg = byteArrayOf(1))

        val summary = DoctorSummary.of(TrackingRecords(berlin), day(27), today, hints = emptyList(), photos = listOf(photo))

        assertEquals(listOf(photo), summary.photos)
    }
}
