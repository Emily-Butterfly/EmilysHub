package de.emilyshub.tracking

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * The week evaluation, the history and the hints of Tracking (SPEC.md §17) read from its database, with made-up values
 * only. The fixture's clock stands at Sunday, 15 November 2026, 11:00 in Berlin.
 */
@RunWith(AndroidJUnit4::class)
class TrackingEvaluationTest {

    private val fixture = TrackingFixture(ApplicationProvider.getApplicationContext())
    private val evaluation = fixture.evaluation
    private val today = LocalDate.parse("2026-11-15")
    private val week = Week.of(today)

    @After
    fun close() = fixture.close()

    /** A morning entry for [day] made on that day, by moving the clock there. */
    private fun morning(day: LocalDate, pulse: Int) = runBlocking {
        fixture.at("${day}T08:00:00Z")
        fixture.entries.saveMorning(MorningEntry(date = day, restingPulse = pulse, recordedOn = day), replacing = null)
        fixture.at("2026-11-15T10:00:00Z")
    }

    @Test
    fun `the week's figures from what is stored`() = runBlocking {
        morning(today, 70)
        morning(today.minusDays(1), 104)
        fixture.entries.saveEvening(EveningEntry(date = today, fluidDecilitres = 12, loadPhysical = 2, recordedOn = today), emptyMap(), emptyList(), replacing = null)
        val event = checkNotNull(fixture.events.create(EventKind.Shower))
        fixture.events.save(checkNotNull(fixture.events.get(event)).copy(breathlessness = 2))

        val figures = checkNotNull(evaluation.week(week))
        assertEquals(Figure(87.0, 2), figures.pulseMean)
        assertEquals(Figure(1.0, 2), figures.pulseHighDays)
        assertEquals(Figure(1.0, 1), figures.fluidLowDays)
        assertEquals(Figure(1.0, 1), figures.eventsBreathless)
        assertEquals("the evening's load is an exertion", Figure(1.0, 1), figures.exertions)
        assertEquals(Figure.NONE, checkNotNull(evaluation.week(week.previous())).pulseMean)
    }

    @Test
    fun `the history with a medication's start and a running episode`() = runBlocking {
        morning(today.minusDays(2), 72)
        fixture.medications.save(Medication(name = "Medikament A", times = listOf(LocalTime.of(8, 0)), start = today.minusDays(5)))
        fixture.episodes.create(EpisodeKind.Crash, today.minusDays(1))
        val history = checkNotNull(evaluation.history(HistoryRange.Four))
        assertEquals(today, history.to)
        assertEquals(72.0, history.series.single { it.kind == SeriesKind.MorningPulse }.points.single { it.first == today.minusDays(2) }.second)
        assertEquals(listOf(today.minusDays(5)), history.markers.map { it.date })
        assertEquals(listOf(Band(today.minusDays(1), today, EpisodeKind.Crash, running = true)), history.bands)
        assertEquals(TrackingHistory.firstDay(HistoryRange.Twelve, today), checkNotNull(evaluation.history(HistoryRange.Twelve)).from)
    }

    @Test
    fun `Garmin's values count by day in the history`() = runBlocking {
        val steps = GarminSample.of("s1", SampleKind.Steps, Instant.parse("2026-11-14T09:00:00Z"), Instant.parse("2026-11-14T10:00:00Z"), 4200, "garmin", DeviceKind.Watch)
        fixture.tracking.changing { it.garmin().put(listOf(steps)) }
        val line = checkNotNull(evaluation.history(HistoryRange.Four)).series.single { it.kind == SeriesKind.Steps }
        assertEquals(Tier.A, line.tier)
        assertEquals(4200.0, line.points.single { it.first == today.minusDays(1) }.second)
    }

    @Test
    fun `a hint marked done stays hidden until a new day adds to what it rests on`() = runBlocking {
        (1L..14L).forEach { morning(today.minusDays(it), if (it <= 8) 105 else 80) }
        assertTrue(checkNotNull(evaluation.hints()).shown.any { it.rule == HintRule.MorningPulse })

        evaluation.markDone(HintRule.MorningPulse)
        assertTrue(checkNotNull(evaluation.hints()).shown.none { it.rule == HintRule.MorningPulse })
        assertEquals("its page still shows it", HintRule.MorningPulse, evaluation.hint(HintRule.MorningPulse)?.rule)

        morning(today, 110)
        assertTrue(checkNotNull(evaluation.hints()).shown.any { it.rule == HintRule.MorningPulse })
    }

    @Test
    fun `the basics, ticked, stay ticked, and a Stehtest short of data says so`() = runBlocking {
        evaluation.markDone(HintRule.Basics)
        val hints = checkNotNull(evaluation.hints())
        assertTrue(hints.shown.none { it.rule == HintRule.Basics })
        assertTrue(MissingData("Stehtest", "noch 3 Stehtests") in hints.missing)
    }

    @Test
    fun `PEM from strong exertions with the evenings after answering worse`() = runBlocking {
        val days = listOf(20L, 12L, 6L).map { today.minusDays(it) }
        days.forEach { day ->
            val id = checkNotNull(fixture.exertions.save(Exertion(date = day, time = LocalTime.of(10, 0), kind = ExertionKind.Physical, strength = 3)))
            fixture.at("${day.plusDays(1)}T18:00:00Z")
            fixture.entries.saveEvening(EveningEntry(date = day.plusDays(1), recordedOn = day.plusDays(1)), emptyMap(), emptyList(), null, mapOf(id to FollowUpAnswer.Yes))
            fixture.at("2026-11-15T10:00:00Z")
        }
        val pem = checkNotNull(evaluation.hints()).shown.single { it.rule == HintRule.Pem }
        assertEquals(3, pem.basis!!.count)
        assertNull(checkNotNull(evaluation.hints()).missing.firstOrNull { it.title == "Verschlechterung nach Belastung" })
    }
}
