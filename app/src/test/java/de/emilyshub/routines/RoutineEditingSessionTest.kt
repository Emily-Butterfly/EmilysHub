package de.emilyshub.routines

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.Saving
import de.emilyshub.data.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
import java.time.ZoneId

/**
 * Editing a routine (SPEC.md §2, §9): what the editor opens with, what keeps a draft from being saved then, by the day
 * and the routine as it was, and what saving does with it; without the editor.
 */
@RunWith(AndroidJUnit4::class)
class RoutineEditingSessionTest {

    private val database = inMemoryDatabase()

    // Friday, 23 October 2026, 12:00 in Berlin.
    private val clock = TestClock(Instant.parse("2026-10-23T10:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.parse("2026-10-23")
    private val routines = Routines({ database }, clock, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a routine opens to edit as it was saved, with its checklist, coupling and how it is in use`() = runBlocking {
        val other = saved(draft("Laufen", "07:00"))
        val id = saved(
            draft("Zähne", "08:00").copy(
                checklist = listOf(ChecklistDraftItem(text = "Putzen"), ChecklistDraftItem(text = "Zahnseide")),
                coupledWith = setOf(other),
            ),
        )
        routines.pause(id)

        val editing = checkNotNull(routines.editing(id))

        assertEquals(id, editing.id)
        assertEquals(RoutineStatus.Paused, editing.status)
        val original = checkNotNull(editing.original)
        assertEquals("Zähne", original.name)
        assertEquals(listOf("Putzen", "Zahnseide"), original.checklist.map { it.text })
        assertEquals(setOf(other), original.coupledWith)
    }

    @Test
    fun `a routine deleted meanwhile does not open`() = runBlocking {
        val id = saved(draft("Zähne", "08:00"))
        routines.delete(id)

        assertNull(routines.editing(id))
    }

    @Test
    fun `a one-time routine whose times are all over is gone by, as new or with new times or a new date`() {
        val past = draft("Arzt", "08:00", repetition = Repetition.Once(today))

        assertEquals(setOf(DraftProblem.GoneBy), routines.problems(RoutineEditing.New, past))
        assertEquals(emptySet<DraftProblem>(), routines.problems(RoutineEditing.New, past.copy(times = listOf(LocalTime.of(18, 0)))))

        val kept = RoutineEditing(id = 1, original = past, status = RoutineStatus.Active)
        // Kept as it was after its date, it may still be renamed.
        assertEquals(emptySet<DraftProblem>(), routines.problems(kept, past.copy(name = "Zahnarzt")))
        assertEquals(setOf(DraftProblem.GoneBy), routines.problems(kept, past.copy(times = listOf(LocalTime.of(9, 0)))))
    }

    @Test
    fun `an end before today keeps it from being saved only once it is moved there`() {
        val ended = draft("Lesen", "20:00").copy(endsOn = today.minusDays(1))

        assertEquals(setOf(DraftProblem.EndDateTooEarly), routines.problems(RoutineEditing.New, ended))

        val kept = RoutineEditing(id = 1, original = ended, status = RoutineStatus.Active)
        assertEquals(emptySet<DraftProblem>(), routines.problems(kept, ended.copy(name = "Buch lesen")))
        assertEquals(
            setOf(DraftProblem.EndDateTooEarly),
            routines.problems(kept, ended.copy(endsOn = today.minusDays(2))),
        )
    }

    @Test
    fun `the draft's own problems count too`() {
        assertEquals(setOf(DraftProblem.NameMissing), routines.problems(RoutineEditing.New, draft(" ", "08:00")))
    }

    @Test
    fun `saving a new routine creates it, and saving one opened saves its changes`() = runBlocking {
        val id = saved(draft("Zähne", "08:00"))
        val editing = checkNotNull(routines.editing(id))

        assertEquals(Saving.Saved(id), routines.save(editing, checkNotNull(editing.original).copy(name = "Zähne putzen")))

        assertEquals("Zähne putzen", routines.get(id)?.name)
    }

    @Test
    fun `a draft with problems is refused, and nothing is saved`() = runBlocking {
        val saving = routines.save(RoutineEditing.New, draft(" ", "08:00"))

        assertEquals(Saving.Refused(setOf(DraftProblem.NameMissing)), saving)
        assertTrue(routines.all().first().isEmpty())
    }

    @Test
    fun `a one-time routine whose time passed while it was saved is saved still`() = runBlocking {
        // The editor found it in time; the moment has passed by when it saves.
        val saving = routines.save(RoutineEditing.New, draft("Arzt", "08:00", repetition = Repetition.Once(today)))

        assertTrue(saving is Saving.Saved)
    }

    private suspend fun saved(draft: RoutineDraft): Long = (routines.save(RoutineEditing.New, draft) as Saving.Saved).id

    private fun draft(name: String, vararg times: String, repetition: Repetition = Repetition.Daily) =
        RoutineDraft(name, description = "", times.map(LocalTime::parse), repetition)
}
