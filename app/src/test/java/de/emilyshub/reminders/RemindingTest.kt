package de.emilyshub.reminders

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.places.Whereabouts
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.RoutineDraft
import de.emilyshub.routines.Routines
import de.emilyshub.todos.Due
import de.emilyshub.todos.TodoDraft
import de.emilyshub.todos.Todos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The reminders of routines and to-dos (SPEC.md §3, §5), as one module: driven as the alarm and the answers drive it,
 * and held up against what is on the screen, through [FakeReminderScreen] at its seam.
 */
@RunWith(AndroidJUnit4::class)
class RemindingTest {

    private val database = inMemoryDatabase()

    // Friday, 23 October 2026, 07:00 in Berlin.
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.parse("2026-10-23")
    private val screen = FakeReminderScreen()
    private val routines = Routines({ database }, clock, Dispatchers.Unconfined, onChange = { reminders.update() })
    private val todos = Todos({ database }, clock, Dispatchers.Unconfined, onChange = { reminders.update() })
    private val sounds = Sounds(FakeTones(), { DefaultTones() }, routines, todos)
    private val reminders: Reminders = Reminders(
        ApplicationProvider.getApplicationContext(),
        routines,
        todos,
        clock,
        locate = { Whereabouts.Unknown },
        mayLookAround = { false },
        sounds = sounds,
        screen = screen,
    ) { database.settings() }

    @Before
    fun allowExactAlarms() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `what comes due pops up with a sound, a routine's occurrence and a to-do alike`() = runBlocking {
        reminders.update()
        routines.create(routine("Vitamine", "07:30"))
        val call = todos.create(TodoDraft("Arzt anrufen", due = Due.At(today, LocalTime.parse("07:45"))))
        clock.now = berlin("07:50")

        reminders.remind()

        val vitamins = occurrenceOf("Vitamine")
        assertEquals(listOf(Alarmed.Occurrence(vitamins), Alarmed.Todo(call)), screen.alerted)
        assertEquals(setOf(Alarmed.Occurrence(vitamins), Alarmed.Todo(call)), screen.onScreen.keys)
        assertEquals(Offering.Actions, screen.offering(Alarmed.Todo(call)))
    }

    @Test
    fun `once done, its reminder goes, and the others stay as they are`() = runBlocking {
        reminders.update()
        routines.create(routine("Vitamine", "07:30"))
        val call = todos.create(TodoDraft("Arzt anrufen", due = Due.At(today, LocalTime.parse("07:45"))))
        clock.now = berlin("07:50")
        reminders.remind()

        routines.markDone(occurrenceOf("Vitamine"))

        assertEquals(setOf(Alarmed.Todo(call)), screen.onScreen.keys)
        assertEquals("not popped up again", 2, screen.alerted.size)
    }

    @Test
    fun `asked to put off, it offers when to come back instead, quietly, and still does once brought up to date`() = runBlocking {
        reminders.update()
        routines.create(routine("Vitamine", "07:30"))
        val call = todos.create(TodoDraft("Arzt anrufen", due = Due.At(today, LocalTime.parse("07:45"))))
        clock.now = berlin("07:50")
        reminders.remind()
        val vitamins = Alarmed.Occurrence(occurrenceOf("Vitamine"))

        reminders.offerPutOff(vitamins.id)
        reminders.offerTodoPutOff(call)
        reminders.update()

        assertEquals(Offering.PutOff, screen.offering(vitamins))
        assertEquals(Offering.PutOff, screen.offering(Alarmed.Todo(call)))
        assertEquals(2, screen.alerted.size)
    }

    @Test
    fun `a reminder answered meanwhile goes away rather than offering anything`() = runBlocking {
        reminders.update()
        routines.create(routine("Vitamine", "07:30"))
        clock.now = berlin("07:50")
        reminders.remind()
        val vitamins = occurrenceOf("Vitamine")
        // Ticked off in the app, while its reminder was still up.
        screen.onScreen[Alarmed.Occurrence(vitamins)]?.let { shown -> routines.markDone(vitamins); screen.onScreen[shown.reminder.about] = shown }

        reminders.offerPutOff(vitamins)

        assertEquals(emptySet<Alarmed>(), screen.onScreen.keys)
    }

    @Test
    fun `put off, a reminder goes and comes back then, with a sound`() = runBlocking {
        reminders.update()
        routines.create(routine("Vitamine", "07:30"))
        clock.now = berlin("07:50")
        reminders.remind()
        val vitamins = occurrenceOf("Vitamine")

        reminders.putOff(vitamins, Duration.ofMinutes(30))
        assertEquals(emptySet<Alarmed>(), screen.onScreen.keys)

        clock.now = berlin("08:20")
        reminders.remind()
        assertEquals(listOf(Alarmed.Occurrence(vitamins), Alarmed.Occurrence(vitamins)), screen.alerted)
    }

    private suspend fun occurrenceOf(name: String) = routines.dueOn(today).first().single { it.name == name }.occurrenceId

    private fun berlin(time: String): Instant = LocalDateTime.of(today, LocalTime.parse(time)).atZone(clock.zone).toInstant()

    private fun routine(name: String, time: String) =
        RoutineDraft(name, description = "", listOf(LocalTime.parse(time)), Repetition.Daily)
}
