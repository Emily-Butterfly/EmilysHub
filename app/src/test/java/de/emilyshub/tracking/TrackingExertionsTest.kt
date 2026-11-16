package de.emilyshub.tracking

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Exertions, suggestions, the pacing threshold, follow-up days and episodes of Tracking (SPEC.md §17), stored, with
 * made-up values only. The fixture's clock stands at Sunday, 15 November 2026, 11:00 in Berlin.
 */
@RunWith(AndroidJUnit4::class)
class TrackingExertionsTest {

    private val fixture = TrackingFixture(ApplicationProvider.getApplicationContext())
    private val exertions = fixture.exertions
    private val episodes = fixture.episodes
    private val today = LocalDate.parse("2026-11-15")
    private val berlin = ZoneId.of("Europe/Berlin")

    @After
    fun close() = fixture.close()

    private fun evening(day: LocalDate, physical: Int? = null, cognitive: Int? = null, replacing: LocalDate? = null) = runBlocking {
        fixture.entries.saveEvening(EveningEntry(date = day, loadPhysical = physical, loadCognitive = cognitive, recordedOn = today), emptyMap(), emptyList(), replacing)
    }

    private fun all() = checkNotNull(runBlocking { exertions.list() })

    @Test
    fun `the evening's load of 2 or more is an exertion, which changes and goes with it`() = runBlocking {
        evening(today, physical = 2, cognitive = 1)
        assertEquals(listOf(ExertionKind.Physical to 2), all().map { it.kind to it.strength })
        val id = all().single().id

        evening(today, physical = 3, cognitive = 2, replacing = today)
        assertEquals(setOf(ExertionKind.Physical to 3, ExertionKind.Cognitive to 2), all().map { it.kind to it.strength }.toSet())
        assertEquals("the same, changed", id, all().single { it.kind == ExertionKind.Physical }.id)

        evening(today, physical = 1, replacing = today)
        assertEquals(emptyList<Exertion>(), all())
    }

    @Test
    fun `an evening moved to another day takes its exertions along`() = runBlocking {
        evening(today, physical = 2)
        evening(today.minusDays(1), physical = 2, replacing = today)

        assertEquals(listOf(today.minusDays(1)), all().map { it.date })
        assertTrue(all().all { it.origin == ExertionOrigin.Evening })
    }

    @Test
    fun `an exertion by hand is kept within its scale, and one of the evening is not changed by hand`() = runBlocking {
        val id = checkNotNull(exertions.save(Exertion(date = today, time = LocalTime.of(9, 0), kind = ExertionKind.StandingHeat, durationMinutes = 30, strength = 3)))
        assertEquals(ExertionOrigin.Manual, all().single().origin)
        assertNull(exertions.save(Exertion(date = today, kind = ExertionKind.Physical, strength = 4)))

        evening(today, physical = 2)
        val fromEvening = all().single { it.origin == ExertionOrigin.Evening }
        assertNull(exertions.save(fromEvening.copy(strength = 3)))
        assertTrue(id > 0)
    }

    @Test
    fun `steps of a day above 150 percent of the 14 before are suggested, until confirmed or dismissed`() = runBlocking {
        fixture.health.grantedNow = HealthSource.PERMISSIONS
        (1L..10L).forEach { steps(today.minusDays(it), 4000) }
        steps(today, 9000)
        fixture.at("2026-11-15T22:00:00Z")
        fixture.garmin.read()

        val suggestions = checkNotNull(exertions.suggestions())
        assertEquals(listOf(today to SuggestionSource.Steps), suggestions.map { it.date to it.source })
        assertEquals(9000L, suggestions.single().check.value)

        val id = checkNotNull(exertions.confirm(today, SuggestionSource.Steps, strength = 2))
        val confirmed = checkNotNull(exertions.get(id))
        assertEquals(ExertionKind.Physical, confirmed.kind)
        assertEquals(ExertionOrigin.Steps, confirmed.origin)
        assertEquals(emptyList<Suggestion>(), exertions.suggestions())
    }

    @Test
    fun `a dismissed suggestion does not come again`() = runBlocking {
        fixture.health.grantedNow = HealthSource.PERMISSIONS
        (1L..10L).forEach { steps(today.minusDays(it), 4000) }
        steps(today, 9000)
        fixture.at("2026-11-15T22:00:00Z")
        fixture.garmin.read()

        exertions.dismiss(today, SuggestionSource.Steps)

        assertEquals(emptyList<Suggestion>(), exertions.suggestions())
        assertEquals(emptyList<Exertion>(), all())
    }

    @Test
    fun `minutes of heart rate above the threshold of a day above 150 percent are suggested`() = runBlocking {
        fixture.health.grantedNow = HealthSource.PERMISSIONS
        // Set before the days it is to hold for.
        fixture.at("2026-10-15T10:00:00Z")
        exertions.setOwnThreshold(100)
        fixture.at("2026-11-15T10:00:00Z")
        // Ten minutes above on each of eight days before, 30 today, each day with data.
        (1L..8L).forEach { heartRates(today.minusDays(it), above = 10) }
        heartRates(today, above = 30)
        fixture.at("2026-11-15T22:00:00Z")
        fixture.garmin.read()

        assertEquals(listOf(today to SuggestionSource.HeartRate), checkNotNull(exertions.suggestions()).map { it.date to it.source })
    }

    @Test
    fun `the threshold is one's own, else the morning pulses of 7 days plus 15, with the hint on pulse-lowering medication`() = runBlocking {
        assertNull(checkNotNull(exertions.threshold()).threshold)
        listOf(0L to 60, 3L to 70, 7L to 200).forEach { (back, pulse) ->
            fixture.entries.saveMorning(MorningEntry(date = today.minusDays(back), restingPulse = pulse, recordedOn = today), null)
        }
        fixture.medications.save(Medication(name = "Medikament A", times = listOf(LocalTime.of(8, 0)), start = today, lowersPulse = true))

        val ruleOfThumb = checkNotNull(exertions.threshold())
        assertEquals("the day 7 back is outside", PacingThreshold(80, own = false), ruleOfThumb.threshold)
        assertTrue(ruleOfThumb.warnsOfPulseLowering)

        exertions.setOwnThreshold(112)
        val own = checkNotNull(exertions.threshold())
        assertEquals(PacingThreshold(112, own = true), own.threshold)
        assertFalse(own.warnsOfPulseLowering)

        exertions.setOwnThreshold(null)
        assertFalse(checkNotNull(exertions.threshold()).threshold!!.own)
    }

    @Test
    fun `each day keeps the threshold that held on it, so a change does not rewrite the days before`() = runBlocking {
        // Further back than an entry can be made for now, as they were made then.
        fixture.tracking.changing { database ->
            listOf(10L to 60, 3L to 80).forEach { (back, pulse) ->
                database.entries().insertMorning(MorningEntry(date = today.minusDays(back), restingPulse = pulse, recordedOn = today.minusDays(back)))
            }
        }
        val before = today.minusDays(1)
        fixture.at(before.atTime(12, 0).atZone(fixture.clock.zone).toInstant().toString())
        exertions.setOwnThreshold(100)
        fixture.at("2026-11-15T10:00:00Z")
        exertions.setOwnThreshold(120)

        val thresholds = fixture.tracking.withDatabase { exertions.thresholdsBetween(it, today.minusDays(12), today) }!!

        assertEquals("the rule of thumb of its mornings", 75, thresholds[today.minusDays(10)])
        assertEquals("the rule of thumb moves with the mornings, not with today's", 95, thresholds[today.minusDays(3)])
        assertEquals("no morning in the 7 days up to it", null, thresholds[today.minusDays(12)])
        assertEquals("one's own as set that day", 100, thresholds[before])
        assertEquals(120, thresholds[today])
        assertEquals(PacingThreshold(120, own = true), checkNotNull(exertions.threshold()).threshold)
    }

    @Test
    fun `the evenings after a strong exertion ask after it, and keep the answer`() = runBlocking {
        val id = checkNotNull(exertions.save(Exertion(date = today.minusDays(2), kind = ExertionKind.Physical, strength = 3)))
        exertions.save(Exertion(date = today.minusDays(1), kind = ExertionKind.Physical, strength = 1))

        val sheet = checkNotNull(fixture.entries.evening(today))
        assertEquals(listOf(id), sheet.followUps.map { it.id })

        fixture.entries.saveEvening(sheet.entry, emptyMap(), emptyList(), null, followUps = mapOf(id to FollowUpAnswer.Yes))
        assertEquals(mapOf(id to FollowUpAnswer.Yes), checkNotNull(fixture.entries.evening(today)).followUpAnswers)
    }

    @Test
    fun `an episode with triggers from the three days before, the gap to each, and ended`() = runBlocking {
        val before = checkNotNull(exertions.save(Exertion(date = today.minusDays(1), time = LocalTime.of(18, 0), kind = ExertionKind.Physical, strength = 3)))
        val tooEarly = checkNotNull(exertions.save(Exertion(date = today.minusDays(5), kind = ExertionKind.Physical, strength = 3)))
        val id = checkNotNull(episodes.create(EpisodeKind.Pem, today))
        val episode = checkNotNull(episodes.sheet(id)).episode.copy(startTime = LocalTime.of(10, 0), severity = 2)

        assertTrue(episodes.save(episode, mapOf(before to Certainty.Likely, tooEarly to Certainty.Sure)))

        val sheet = checkNotNull(episodes.sheet(id))
        assertEquals(listOf(before), sheet.candidates.map { it.exertion.id })
        assertEquals(16L, sheet.candidates.single().gap.hours)
        assertEquals("one outside the three days is not kept", mapOf(before to Certainty.Likely), sheet.triggers)
        assertEquals(listOf(id), checkNotNull(episodes.running()).map { it.id })

        episodes.end(id)
        assertEquals(today, checkNotNull(episodes.sheet(id)).episode.endDate)
        assertEquals(emptyList<Episode>(), episodes.running())
    }

    @Test
    fun `an exertion made from an episode becomes one of its triggers`() = runBlocking {
        val id = checkNotNull(episodes.create(EpisodeKind.Crash, today))
        val exertion = checkNotNull(exertions.save(Exertion(date = today, kind = ExertionKind.Cognitive, strength = 2), triggerOf = id))

        assertEquals(mapOf(exertion to Certainty.Unclear), checkNotNull(episodes.sheet(id)).triggers)
    }

    private fun steps(day: LocalDate, count: Long) {
        val start = day.atTime(12, 0).atZone(berlin).toInstant()
        fixture.health.samples += GarminSample.of("steps $day", SampleKind.Steps, start, start.plusSeconds(600), count, HealthSource.GARMIN_CONNECT, DeviceKind.Watch)
    }

    /** One heart rate a minute for [above] minutes above 100 and ten below, from 08:00 on [day]. */
    private fun heartRates(day: LocalDate, above: Int) {
        val start: Instant = day.atTime(8, 0).atZone(berlin).toInstant()
        (0 until above + 10).forEach { minute ->
            val at = start.plusSeconds(60L * minute)
            val bpm = if (minute < above) 120L else 80L
            fixture.health.samples += GarminSample.of("hr $day $minute", SampleKind.HeartRate, at, at, bpm, HealthSource.GARMIN_CONNECT, DeviceKind.Watch)
        }
    }
}
