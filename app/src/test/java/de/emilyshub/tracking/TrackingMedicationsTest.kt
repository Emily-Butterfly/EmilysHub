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

/** The medications with their timeline, and the base data (SPEC.md §17), with made-up names only. */
@RunWith(AndroidJUnit4::class)
class TrackingMedicationsTest {

    private val fixture = TrackingFixture(ApplicationProvider.getApplicationContext())
    private val medications = fixture.medications
    private val today = LocalDate.parse("2026-11-15")
    private val morning = listOf(LocalTime.of(8, 0))

    @After
    fun close() = fixture.close()

    @Test
    fun `a medication is taken from its start up to its end, both included`() {
        val medication = Medication(name = "Medikament A", times = morning, start = today, end = today.plusDays(3))

        assertFalse(medication.isActiveOn(today.minusDays(1)))
        assertTrue(medication.isActiveOn(today))
        assertTrue(medication.isActiveOn(today.plusDays(3)))
        assertFalse(medication.isActiveOn(today.plusDays(4)))
        assertTrue(medication.copy(end = null).isActiveOn(today.plusYears(5)))
    }

    @Test
    fun `a medication needs a name and a time, and no end before its start`() {
        val medication = Medication(name = "Medikament A", times = morning, start = today)

        assertEquals(MedicationProblem.NoName, TrackingMedications.problemOf(medication.copy(name = " ")))
        assertEquals(MedicationProblem.NoTime, TrackingMedications.problemOf(medication.copy(times = emptyList())))
        assertEquals(MedicationProblem.EndBeforeStart, TrackingMedications.problemOf(medication.copy(end = today.minusDays(1))))
        assertNull(runBlocking { medications.save(medication.copy(name = "")) })
        assertNull(TrackingMedications.problemOf(medication))
    }

    @Test
    fun `those still taken come on top, the ones stopped below`() {
        val active = checkNotNull(runBlocking { medications.save(Medication(name = "Medikament B", times = morning, start = today.minusDays(30))) })
        val stopped = checkNotNull(runBlocking { medications.save(Medication(name = "Medikament A", times = morning, start = today.minusDays(30))) })
        runBlocking { medications.end(stopped, today.minusDays(1)) }
        val endsToday = checkNotNull(runBlocking { medications.save(Medication(name = "Medikament C", times = morning, start = today.minusDays(3))) })
        runBlocking { medications.end(endsToday, today) }

        val list = checkNotNull(runBlocking { medications.list() })
        assertEquals(listOf(active, endsToday), list.active.map { it.id })
        assertEquals(listOf(stopped), list.ended.map { it.id })
    }

    @Test
    fun `a medication is changed, its times in order`() {
        val id = checkNotNull(runBlocking { medications.save(Medication(name = "Medikament A", times = morning, start = today)) })

        runBlocking {
            medications.save(
                checkNotNull(medications.timeline(id)).medication.copy(form = "Tablette", times = listOf(LocalTime.of(20, 0), LocalTime.of(8, 0)), lowersPulse = true),
            )
        }

        val changed = checkNotNull(runBlocking { medications.timeline(id) }).medication
        assertEquals("Tablette", changed.form)
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), changed.times)
        assertTrue(changed.lowersPulse)
    }

    @Test
    fun `the dose changes come in the order of their dates`() {
        val id = checkNotNull(runBlocking { medications.save(Medication(name = "Medikament A", times = morning, start = today.minusDays(60))) })

        runBlocking {
            medications.addDoseChange(id, today.minusDays(10), "10 mg", "20 mg")
            medications.addDoseChange(id, today.minusDays(40), "5 mg", "10 mg")
            assertFalse(medications.addDoseChange(id, today, "20 mg", " "))
        }

        val changes = checkNotNull(runBlocking { medications.timeline(id) }).doseChanges
        assertEquals(listOf("5 mg" to "10 mg", "10 mg" to "20 mg"), changes.map { it.from to it.to })

        runBlocking { medications.removeDoseChange(changes.first()) }
        assertEquals(1, checkNotNull(runBlocking { medications.timeline(id) }).doseChanges.size)
    }

    @Test
    fun `the base data keep each field's text`() {
        assertEquals("", checkNotNull(runBlocking { fixture.baseData.all() })[BaseField.Family])

        runBlocking { fixture.tracking.changing { fixture.baseData.write(it, BaseField.Family, "Erfundene Angabe") } }

        assertEquals("Erfundene Angabe", checkNotNull(runBlocking { fixture.baseData.all() })[BaseField.Family])
    }
}
