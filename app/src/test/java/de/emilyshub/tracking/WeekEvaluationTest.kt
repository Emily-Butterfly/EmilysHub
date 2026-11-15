package de.emilyshub.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The week evaluation of Tracking (SPEC.md §17), as pure logic, with made-up values only. */
class WeekEvaluationTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    /** Monday, 19 October 2026: the clocks go back on its Sunday, the 25th. */
    private val monday = LocalDate.parse("2026-10-19")
    private val week = Week(monday)

    private fun morning(day: LocalDate, pulse: Int? = null, quality: Int? = null, recordedOn: LocalDate = day) =
        MorningEntry(date = day, restingPulse = pulse, sleepQuality = quality, recordedOn = recordedOn)

    private fun evening(day: LocalDate, fluid: Int? = null, recordedOn: LocalDate = day) =
        EveningEntry(date = day, fluidDecilitres = fluid, recordedOn = recordedOn)

    private fun day(offset: Long) = monday.plusDays(offset)

    private fun of(records: TrackingRecords) = WeekEvaluation.of(records, week)

    @Test
    fun `a week runs from Monday to Sunday`() {
        assertEquals(week, Week.of(LocalDate.parse("2026-10-25")))
        assertEquals(week, Week.of(monday))
        assertEquals(Week(LocalDate.parse("2026-10-26")), week.next())
        assertEquals(Week(LocalDate.parse("2026-10-12")), week.previous())
        assertTrue(LocalDate.parse("2026-10-25") in week)
        assertFalse(LocalDate.parse("2026-10-26") in week)
    }

    @Test
    fun `an empty week shows no figure, never 0`() {
        val figures = of(TrackingRecords(berlin, symptoms = listOf(Symptom(1, "Erschöpfung", 0))))
        listOf(
            figures.pulseMean, figures.pulseMin, figures.pulseMax, figures.pulseHighDays, figures.standTests, figures.standMaxRise,
            figures.standRiseTwice, figures.standDropEarly, figures.standDropLate, figures.events, figures.eventsBreathless,
            figures.eventsSkin, figures.skinFadeMinutes, figures.exertions, figures.episodes, figures.episodeGapHours,
            figures.fluidLowDays, figures.poorNights, figures.deviations, figures.stepsPerDay, figures.minutesAbovePerDay,
            figures.backfilledShare, figures.symptoms.single().mean,
        ).forEach { assertEquals(Figure.NONE, it) }
    }

    @Test
    fun `the morning pulse, its mean, least, most and days above 100, from the days that have one`() {
        val records = TrackingRecords(
            berlin,
            mornings = listOf(morning(day(0), 90), morning(day(1), 101), morning(day(2), 100), morning(day(3)), morning(day(7), 140)),
        )
        val figures = of(records)
        assertEquals(Figure(97.0, 3), figures.pulseMean)
        assertEquals(Figure(90.0, 3), figures.pulseMin)
        assertEquals(Figure(101.0, 3), figures.pulseMax)
        assertEquals("100 exactly is not above", Figure(1.0, 3), figures.pulseHighDays)
    }

    @Test
    fun `a week with mornings but no pulse above 100 has 0 days, not none`() {
        assertEquals(Figure(0.0, 1), of(TrackingRecords(berlin, mornings = listOf(morning(day(0), 70)))).pulseHighDays)
    }

    private fun standRecord(id: Long, at: String, rises: List<Int>, lying: Int = 70, dropEarly: Int? = null, dropLate: Int? = null): StandRecord {
        val test = StandTest(id = id, variant = StandVariant.Standing, startedAt = Instant.parse(at), standingAt = Instant.parse(at).plusSeconds(600), lyingPulse = lying, finished = true)
        val points = StandVariant.Standing.minutes.mapIndexed { i, minute -> StandPoint(id, minute, pulse = lying + rises.getOrElse(i) { 0 }) }
        val result = StandTestEvaluation.evaluate(test, points, emptyList()).copy(systolicDropEarly = dropEarly, systolicDropLate = dropLate)
        return StandRecord(test, points, result)
    }

    @Test
    fun `the Stehtests, their largest rise, those rising twice by 30 and those dropping early or late`() {
        val records = TrackingRecords(
            berlin,
            standTests = listOf(
                standRecord(1, "2026-10-19T08:00:00Z", listOf(10, 30, 31, 5, 0), dropEarly = 25),
                standRecord(2, "2026-10-20T08:00:00Z", listOf(10, 30, 0, 0, 0), dropLate = 20),
                standRecord(3, "2026-10-21T08:00:00Z", listOf(45, 0, 0, 0, 0)),
                // The week after.
                standRecord(4, "2026-10-26T08:00:00Z", listOf(60, 60, 60, 60, 60)),
            ),
        )
        val figures = of(records)
        assertEquals(Figure(3.0, 3), figures.standTests)
        assertEquals(Figure(45.0, 3), figures.standMaxRise)
        assertEquals(Figure(1.0, 3), figures.standRiseTwice)
        assertEquals("only those with a pressure count", Figure(1.0, 2), figures.standDropEarly)
        assertEquals(Figure(1.0, 2), figures.standDropLate)
    }

    @Test
    fun `a test dropping early and late counts as early only`() {
        val records = TrackingRecords(berlin, standTests = listOf(standRecord(1, "2026-10-19T08:00:00Z", listOf(0), dropEarly = 20, dropLate = 30)))
        assertEquals(Figure(1.0, 1), of(records).standDropEarly)
        assertEquals(Figure(0.0, 1), of(records).standDropLate)
    }

    @Test
    fun `an event late on the Sunday the clocks go back is of this week, one after midnight of the next`() {
        val sunday = TrackingEvent(id = 1, kind = EventKind.Shower, at = Instant.parse("2026-10-25T22:30:00Z"), breathlessness = 2, skinAreas = setOf(SkinArea.Legs), skinFadeMinutes = 10)
        val nextMonday = TrackingEvent(id = 2, kind = EventKind.Shower, at = Instant.parse("2026-10-25T23:30:00Z"), breathlessness = 3)
        val before = TrackingEvent(id = 3, kind = EventKind.Heat, at = Instant.parse("2026-10-21T10:00:00Z"), breathlessness = 1, skinFadeMinutes = 30)
        val figures = of(TrackingRecords(berlin, events = listOf(sunday, nextMonday, before)))
        assertEquals(Figure(2.0, 2), figures.events)
        assertEquals(Figure(1.0, 2), figures.eventsBreathless)
        assertEquals(Figure(1.0, 2), figures.eventsSkin)
        assertEquals(Figure(20.0, 2), figures.skinFadeMinutes)
    }

    @Test
    fun `exertions and episodes with the mean hours from their triggers`() {
        val exertion = Exertion(id = 1, date = day(1), time = LocalTime.of(10, 0), kind = ExertionKind.Physical, strength = 3)
        val other = Exertion(id = 2, date = day(2), time = LocalTime.of(10, 0), kind = ExertionKind.Cognitive, strength = 2)
        val episode = Episode(id = 1, kind = EpisodeKind.Pem, startDate = day(3), startTime = LocalTime.of(10, 0))
        val records = TrackingRecords(
            berlin,
            evenings = (0L..6L).map { evening(day(it)) },
            exertions = listOf(exertion, other),
            episodes = listOf(episode),
            triggers = listOf(EpisodeTrigger(1, 1, Certainty.Sure), EpisodeTrigger(1, 2, Certainty.Likely)),
        )
        val figures = of(records)
        assertEquals(Figure(2.0, 7), figures.exertions)
        assertEquals(Figure(1.0, 7), figures.episodes)
        assertEquals("48 and 24 hours", Figure(36.0, 2), figures.episodeGapHours)
    }

    @Test
    fun `a week with evenings and no exertion has 0, without evenings none`() {
        assertEquals(Figure(0.0, 1), of(TrackingRecords(berlin, evenings = listOf(evening(day(0))))).exertions)
        assertEquals(Figure.NONE, of(TrackingRecords(berlin)).exertions)
    }

    @Test
    fun `each symptom's mean from the evenings that have it, hidden ones only with values`() {
        val shown = Symptom(1, "Erschöpfung", 0)
        val hidden = Symptom(2, "Kopfschmerz", 1, hidden = true)
        val hiddenWithValue = Symptom(3, "Übelkeit", 2, hidden = true)
        val records = TrackingRecords(
            berlin,
            symptoms = listOf(shown, hidden, hiddenWithValue),
            symptomValues = listOf(SymptomValue(day(0), 1, 1), SymptomValue(day(1), 1, 2), SymptomValue(day(2), 3, 0), SymptomValue(day(8), 1, 3)),
        )
        assertEquals(
            listOf(SymptomFigure(shown, Figure(1.5, 2)), SymptomFigure(hiddenWithValue, Figure(0.0, 1))),
            of(records).symptoms,
        )
    }

    @Test
    fun `days below 1,5 l, nights of poor sleep, intakes otherwise than planned`() {
        val medication = Medication(id = 1, name = "Medikament A", start = day(2))
        val records = TrackingRecords(
            berlin,
            mornings = listOf(morning(day(0), quality = 2), morning(day(1), quality = 1), morning(day(2), quality = 3), morning(day(3))),
            evenings = listOf(evening(day(0), fluid = 14), evening(day(1), fluid = 15), evening(day(2)), evening(day(3), fluid = 20)),
            medications = listOf(medication),
            deviations = listOf(IntakeDeviation(day(3), 1, Deviation.Forgotten)),
        )
        val figures = of(records)
        assertEquals("1,5 l exactly is not below", Figure(1.0, 3), figures.fluidLowDays)
        assertEquals(Figure(2.0, 3), figures.poorNights)
        assertEquals("of the evenings with a medication taken", Figure(1.0, 2), figures.deviations)
    }

    @Test
    fun `Garmin's steps and minutes above the threshold per day with data`() {
        val records = TrackingRecords(berlin, garmin = GarminDays(steps = mapOf(day(0) to 4000L, day(1) to 6000L, day(7) to 1L), minutesAbove = mapOf(day(0) to 0L)))
        assertEquals(Figure(5000.0, 2), of(records).stepsPerDay)
        assertEquals("a day with heart rates but none above has 0", Figure(0.0, 1), of(records).minutesAbovePerDay)
    }

    @Test
    fun `the share of entries made up later`() {
        val records = TrackingRecords(
            berlin,
            mornings = listOf(morning(day(0), recordedOn = day(1)), morning(day(1), recordedOn = day(4))),
            evenings = listOf(evening(day(0), recordedOn = day(0)), evening(day(1), recordedOn = day(5))),
        )
        assertEquals(Figure(0.5, 4), of(records).backfilledShare)
    }

    @Test
    fun `Garmin's days count by the wall clock, the day the clocks go back having 25 hours`() {
        fun sample(key: String, kind: SampleKind, start: String, end: String, value: Long) =
            GarminSample.of(key, kind, Instant.parse(start), Instant.parse(end), value, "garmin", DeviceKind.Watch)
        val days = GarminDays.of(
            listOf(
                // 23:30 and 00:30 by the wall clock on the night the clocks go back.
                sample("s1", SampleKind.Steps, "2026-10-25T22:30:00Z", "2026-10-25T22:31:00Z", 100),
                sample("s2", SampleKind.Steps, "2026-10-25T23:30:00Z", "2026-10-25T23:31:00Z", 50),
                // 00:30 summer time, on the 25th.
                sample("s3", SampleKind.Steps, "2026-10-24T22:30:00Z", "2026-10-24T22:31:00Z", 7),
                sample("h1", SampleKind.HeartRate, "2026-10-25T10:00:10Z", "2026-10-25T10:00:10Z", 120),
                sample("h2", SampleKind.HeartRate, "2026-10-25T10:00:40Z", "2026-10-25T10:00:40Z", 125),
                sample("h3", SampleKind.HeartRate, "2026-10-25T11:00:00Z", "2026-10-25T11:00:00Z", 80),
                sample("h4", SampleKind.HeartRate, "2026-10-24T11:00:00Z", "2026-10-24T11:00:00Z", 80),
                sample("r1", SampleKind.RestingHeartRate, "2026-10-25T06:00:00Z", "2026-10-25T06:00:00Z", 60),
                sample("r2", SampleKind.RestingHeartRate, "2026-10-25T07:00:00Z", "2026-10-25T07:00:00Z", 63),
                sample("n1", SampleKind.Sleep, "2026-10-24T21:00:00Z", "2026-10-25T05:00:00Z", 480),
                sample("d1#0", SampleKind.SleepStage, "2026-10-25T01:00:00Z", "2026-10-25T02:00:00Z", GarminDays.DEEP_SLEEP_STAGE),
                sample("d1#1", SampleKind.SleepStage, "2026-10-25T02:00:00Z", "2026-10-25T03:00:00Z", 4),
            ),
            berlin,
            thresholdBpm = 100,
        )
        val sunday = LocalDate.parse("2026-10-25")
        assertEquals(mapOf(sunday to 107L, sunday.plusDays(1) to 50L), days.steps)
        assertEquals("two heart rates in one minute are one", mapOf(sunday to 1L, sunday.minusDays(1) to 0L), days.minutesAbove)
        assertEquals(mapOf(sunday to 61.5), days.restingHeartRate)
        assertEquals("by the day it ended", mapOf(sunday to 480L), days.sleepMinutes)
        assertEquals(mapOf(sunday to 60L), days.deepSleepMinutes)
        assertNull(GarminDays.of(emptyList(), berlin, null).minutesAbove[sunday])
    }
}
