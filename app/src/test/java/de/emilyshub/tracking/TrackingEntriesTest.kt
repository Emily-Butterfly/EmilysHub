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
import java.time.LocalDate
import java.time.LocalTime

/** The morning and evening entries (SPEC.md §17, "Tageseinträge"), with made-up values only. */
@RunWith(AndroidJUnit4::class)
class TrackingEntriesTest {

    private val fixture = TrackingFixture(ApplicationProvider.getApplicationContext())
    private val entries = fixture.entries
    private val symptoms = fixture.symptoms
    private val today = LocalDate.parse("2026-11-15")

    @After
    fun close() = fixture.close()

    private fun morning(day: LocalDate = today) = checkNotNull(runBlocking { entries.morning(day) }).entry

    private fun evening(day: LocalDate = today) = checkNotNull(runBlocking { entries.evening(day) })

    private fun symptom(name: String) = checkNotNull(runBlocking { symptoms.all() }).first { it.name == name }

    @Test
    fun `a new entry is for the day before until 04 00`() {
        fixture.at("2026-11-15T02:30:00Z") // 03:30 in Berlin
        assertEquals(LocalDate.parse("2026-11-14"), entries.suggestedDay())
        fixture.at("2026-11-15T03:00:00Z") // 04:00 in Berlin
        assertEquals(today, entries.suggestedDay())
    }

    @Test
    fun `what is left empty stays not recorded, unlike 0`() {
        assertTrue(runBlocking { entries.saveMorning(morning().copy(sleepQuality = 0), replacing = null) })

        val saved = morning()
        assertEquals(0, saved.sleepQuality)
        assertNull(saved.restingPulse)
        assertNull(saved.rested)

        val headache = symptom("Kopfschmerz")
        assertTrue(runBlocking { entries.saveEvening(evening().entry, mapOf(headache.id to 0), emptyList(), replacing = null) })
        val sheet = evening()
        assertEquals(0, sheet.levels[headache.id])
        assertNull(sheet.levels[symptom("Atemnot").id])
        assertNull(sheet.entry.loadPhysical)
    }

    @Test
    fun `the status of the day says which entries are made`() {
        assertEquals(DayStatus(today, morningDone = false, eveningDone = false), runBlocking { entries.status(today) })

        runBlocking { entries.saveMorning(morning().copy(restingPulse = 64), replacing = null) }

        assertEquals(DayStatus(today, morningDone = true, eveningDone = false), runBlocking { entries.status(today) })
    }

    @Test
    fun `values out of their range are not saved`() {
        runBlocking { entries.saveMorning(morning().copy(restingPulse = 20, systolic = 120, sleepQuality = 4), replacing = null) }

        val saved = morning()
        assertNull(saved.restingPulse)
        assertEquals(120, saved.systolic)
        assertNull(saved.sleepQuality)
    }

    @Test
    fun `entries go back seven days, not further and not ahead`() {
        assertTrue(runBlocking { entries.saveMorning(morning(today.minusDays(7)), replacing = null) })
        assertFalse(runBlocking { entries.saveMorning(morning(today.minusDays(8)), replacing = null) })
        assertFalse(runBlocking { entries.saveMorning(morning(today.plusDays(1)), replacing = null) })
    }

    @Test
    fun `an entry recorded later than the day after is marked as made up`() {
        runBlocking {
            entries.saveMorning(morning(today.minusDays(1)), replacing = null)
            entries.saveMorning(morning(today.minusDays(2)), replacing = null)
        }

        assertFalse(morning(today.minusDays(1)).backfilled)
        assertTrue(morning(today.minusDays(2)).backfilled)
    }

    @Test
    fun `an entry moved to another day keeps the day it was first recorded, and there is one per day`() {
        runBlocking { entries.saveMorning(morning().copy(restingPulse = 64), replacing = null) }
        val yesterday = today.minusDays(1)
        runBlocking { entries.saveMorning(morning(yesterday).copy(restingPulse = 70), replacing = null) }

        assertFalse("the day before has one", runBlocking { entries.saveMorning(morning().copy(date = yesterday), replacing = today) })
        val earlier = today.minusDays(3)
        assertTrue(runBlocking { entries.saveMorning(morning().copy(date = earlier), replacing = today) })

        assertNull(runBlocking { fixture.tracking.withDatabase { it.entries().morning(today) } })
        val moved = morning(earlier)
        assertEquals(64, moved.restingPulse)
        assertEquals(today, moved.recordedOn)
        assertEquals(setOf(yesterday, earlier), runBlocking { entries.takenDays(EntryKind.Morning) })
    }

    @Test
    fun `an evening moved to another day takes its symptoms along`() {
        val fatigue = symptom("Erschöpfung")
        runBlocking { entries.saveEvening(evening().entry.copy(meals = 3), mapOf(fatigue.id to 2), emptyList(), replacing = null) }

        val earlier = today.minusDays(2)
        assertTrue(runBlocking { entries.saveEvening(evening().entry.copy(date = earlier), evening().levels, emptyList(), replacing = today) })

        assertFalse(evening().stored)
        assertEquals(2, evening(earlier).levels[fatigue.id])
        assertEquals(3, evening(earlier).entry.meals)
    }

    @Test
    fun `a hidden symptom is no longer asked, but keeps its values and shows where it has one`() {
        val nausea = symptom("Übelkeit")
        runBlocking { entries.saveEvening(evening(today.minusDays(1)).entry, mapOf(nausea.id to 1), emptyList(), replacing = null) }

        runBlocking { symptoms.setHidden(nausea.id, true) }

        assertFalse(evening().symptoms.any { it.id == nausea.id })
        val old = evening(today.minusDays(1))
        assertTrue(old.symptoms.any { it.id == nausea.id })
        assertEquals(1, old.levels[nausea.id])
    }

    @Test
    fun `a symptom renamed keeps its values`() {
        val fatigue = symptom("Erschöpfung")
        runBlocking { entries.saveEvening(evening().entry, mapOf(fatigue.id to 3), emptyList(), replacing = null) }

        assertTrue(runBlocking { symptoms.rename(fatigue.id, "Müdigkeit") })

        val sheet = evening()
        assertEquals("Müdigkeit", sheet.symptoms.first { it.id == fatigue.id }.name)
        assertEquals(3, sheet.levels[fatigue.id])
    }

    @Test
    fun `the start list comes once, and symptoms of ones own are added, ordered and not taken twice`() {
        val start = checkNotNull(runBlocking { symptoms.all() })
        assertEquals(TrackingSymptoms.START_LIST, start.map { it.name })
        assertTrue(start.map { it.name }.containsAll(listOf("Muskelschmerzen", "Gelenkschmerzen")))

        assertTrue(runBlocking { symptoms.add("Symptom A") })
        assertFalse(runBlocking { symptoms.add(" symptom a ") })
        assertFalse(runBlocking { symptoms.add("  ") })
        val own = symptom("Symptom A")
        runBlocking { symptoms.move(own.id, up = true) }

        val names = checkNotNull(runBlocking { symptoms.all() }).map { it.name }
        assertEquals(listOf("Symptom A", "Angst/innere Unruhe"), names.takeLast(2))
    }

    @Test
    fun `intakes otherwise than planned are kept, as planned is no row, and only of medications taken that day`() {
        val taken = checkNotNull(runBlocking { fixture.medications.save(Medication(name = "Medikament A", times = listOf(LocalTime.of(8, 0)), start = today.minusDays(5))) })
        val later = checkNotNull(runBlocking { fixture.medications.save(Medication(name = "Medikament B", times = listOf(LocalTime.of(8, 0)), start = today.plusDays(1))) })
        val other = checkNotNull(runBlocking { fixture.medications.save(Medication(name = "Medikament C", times = listOf(LocalTime.of(20, 0)), start = today.minusDays(5))) })

        assertEquals(listOf(taken, other), evening().medications.map { it.id })
        runBlocking {
            entries.saveEvening(
                evening().entry,
                emptyMap(),
                listOf(
                    TrackingEntries.deviation(today, taken, LocalTime.of(10, 30)),
                    TrackingEntries.deviation(today, later, null),
                ),
                replacing = null,
            )
        }

        val deviations = evening().deviations
        assertEquals(IntakeDeviation(today, taken, Deviation.OtherTime, LocalTime.of(10, 30)), deviations[taken])
        assertNull("as planned", deviations[other])
        assertNull("not taken that day", deviations[later])
    }
}
