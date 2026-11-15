package de.emilyshub.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** The history of Tracking (SPEC.md §17, "Verlauf"), as pure logic, with made-up values only. */
class TrackingHistoryTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    /** Sunday, 15 November 2026. */
    private val today = LocalDate.parse("2026-11-15")

    @Test
    fun `4 or 12 weeks back, Monday to Sunday, the current one counted`() {
        assertEquals(LocalDate.parse("2026-10-19"), TrackingHistory.firstDay(HistoryRange.Four, today))
        assertEquals(LocalDate.parse("2026-08-24"), TrackingHistory.firstDay(HistoryRange.Twelve, today))
        val history = TrackingHistory.of(TrackingRecords(berlin), HistoryRange.Four, today)
        assertEquals(28, history.series.single { it.kind == SeriesKind.MorningPulse }.points.size)
    }

    @Test
    fun `a day without a value is a gap`() {
        val records = TrackingRecords(
            berlin,
            mornings = listOf(MorningEntry(date = today.minusDays(2), restingPulse = 70, recordedOn = today), MorningEntry(date = today, restingPulse = 80, recordedOn = today)),
        )
        val pulse = TrackingHistory.of(records, HistoryRange.Four, today).series.single { it.kind == SeriesKind.MorningPulse }
        assertEquals(listOf(70.0, null, 80.0), pulse.points.takeLast(3).map { it.second })
        assertNull(pulse.tier)
    }

    @Test
    fun `a line for each symptom shown or with values, scaled 0 to 3`() {
        val shown = Symptom(1, "Erschöpfung", 0)
        val hidden = Symptom(2, "Kopfschmerz", 1, hidden = true)
        val hiddenWithValue = Symptom(3, "Übelkeit", 2, hidden = true)
        val records = TrackingRecords(berlin, symptoms = listOf(shown, hidden, hiddenWithValue), symptomValues = listOf(SymptomValue(today, 3, 2)))
        val symptoms = TrackingHistory.of(records, HistoryRange.Four, today).series.filter { it.kind is SeriesKind.OfSymptom }
        assertEquals(listOf(shown, hiddenWithValue), symptoms.map { (it.kind as SeriesKind.OfSymptom).symptom })
        assertEquals(0..3, symptoms.first().scale)
    }

    @Test
    fun `Garmin's values with their tier, only those with data, deep sleep marked as an estimate`() {
        val records = TrackingRecords(
            berlin,
            garmin = GarminDays(steps = mapOf(today to 5000L), restingHeartRate = mapOf(today to 60.0), minutesAbove = mapOf(today to 12L), deepSleepMinutes = mapOf(today to 70L)),
        )
        val garmin = TrackingHistory.of(records, HistoryRange.Four, today).series.filter { it.tier != null }
        assertEquals(
            listOf(SeriesKind.Steps to Tier.A, SeriesKind.GarminRestingPulse to Tier.B, SeriesKind.MinutesAbove to Tier.B, SeriesKind.GarminDeepSleep to Tier.C),
            garmin.map { it.kind to it.tier },
        )
        assertEquals(listOf(false, false, false, true), garmin.map { it.estimate })
    }

    @Test
    fun `medications' starts, dose changes and ends in the range as marks`() {
        val a = Medication(id = 1, name = "Medikament A", start = today.minusDays(10), end = today.minusDays(1))
        val b = Medication(id = 2, name = "Medikament B", start = today.minusDays(200))
        val records = TrackingRecords(
            berlin,
            medications = listOf(a, b),
            doseChanges = listOf(DoseChange(1, 2, today.minusDays(5), "5 mg", "10 mg"), DoseChange(2, 2, today.minusDays(100), "1 mg", "5 mg")),
        )
        assertEquals(
            listOf(
                Marker(today.minusDays(10), "Medikament A", MarkerKind.Start),
                Marker(today.minusDays(5), "Medikament B", MarkerKind.DoseChange, "5 mg", "10 mg"),
                Marker(today.minusDays(1), "Medikament A", MarkerKind.End),
            ),
            TrackingHistory.of(records, HistoryRange.Four, today).markers,
        )
    }

    @Test
    fun `episodes as bands, cut to the range, a running one up to today`() {
        val from = TrackingHistory.firstDay(HistoryRange.Four, today)
        val records = TrackingRecords(
            berlin,
            episodes = listOf(
                Episode(id = 1, kind = EpisodeKind.Crash, startDate = from.minusDays(3), endDate = from.plusDays(1)),
                Episode(id = 2, kind = EpisodeKind.Pem, startDate = today.minusDays(2)),
                Episode(id = 3, kind = EpisodeKind.Pem, startDate = from.minusDays(10), endDate = from.minusDays(1)),
            ),
        )
        val bands = TrackingHistory.of(records, HistoryRange.Four, today).bands
        assertEquals(
            listOf(Band(from, from.plusDays(1), EpisodeKind.Crash, running = false), Band(today.minusDays(2), today, EpisodeKind.Pem, running = true)),
            bands,
        )
        assertTrue(bands.last().running)
        assertFalse(bands.first().running)
    }
}
