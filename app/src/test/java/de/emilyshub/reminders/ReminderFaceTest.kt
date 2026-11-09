package de.emilyshub.reminders

import de.emilyshub.routines.JointOccurrence
import de.emilyshub.routines.Reminder
import de.emilyshub.todos.Due
import de.emilyshub.todos.TodoReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * How a due reminder shows (SPEC.md §3, §5, §9), the same in its notification and its alarm: a routine's with its time,
 * joint ones by name to say which was done, a to-do's with when it is due or else where, and on the lock screen only
 * what each allows there.
 */
class ReminderFaceTest {

    private val today = LocalDate.parse("2026-11-09")
    private val dueAt = ZonedDateTime.of(today, LocalTime.parse("18:00"), ZoneId.of("Europe/Berlin"))

    /** Plain words, so that the face is seen without the app's strings. */
    private val words = object : ReminderWords {
        override fun dueAt(time: ZonedDateTime) = "at ${time.toLocalTime()}"
        override fun todoDue(reminder: TodoReminder, today: LocalDate) = reminder.due.date?.let { "due $it" }
        override fun places(places: List<String>) = places.takeIf { it.isNotEmpty() }?.joinToString(prefix = "at ")
    }

    private fun routine(name: String = "Yoga", onLockScreen: Boolean = false, together: List<JointOccurrence> = emptyList()) =
        DueReminder.OfOccurrence(
            Reminder(1, name, dueAt, dueAt.plusHours(1).toInstant(), RemindingStyle(nameOnLockScreen = onLockScreen), together),
        )

    private fun todo(due: Due = Due.On(today), places: List<String> = emptyList(), onLockScreen: Boolean = false) =
        DueReminder.OfTodo(TodoReminder(7, "Steuer", due, places, RemindingStyle(nameOnLockScreen = onLockScreen)))

    @Test
    fun `a routine's shows its time, can be skipped, and keeps its name off the lock screen unless allowed`() {
        val face = ReminderFace.of(routine(), today, words)

        assertEquals(ReminderFace("Yoga", "at 18:00", skippable = true), face)
        assertEquals(
            ReminderFace.OnLockScreen("Yoga", "at 18:00"),
            ReminderFace.of(routine(onLockScreen = true), today, words).onLockScreen,
        )
    }

    @Test
    fun `a joint one names each routine, by name, and shows on the lock screen only if each allows it`() {
        val joint = routine(name = "Zähne", onLockScreen = true, together = listOf(JointOccurrence(2, "Atmen", nameOnLockScreen = false)))

        val face = ReminderFace.of(joint, today, words)

        assertEquals(listOf(ReminderFace.Choice(2, "Atmen"), ReminderFace.Choice(1, "Zähne")), face.each)
        assertEquals("Atmen · Zähne", face.title)
        assertNull(face.onLockScreen)
    }

    @Test
    fun `a to-do's says when it is due, else where, is never skipped, and shows on the lock screen when but never where`() {
        val dueToday = ReminderFace.of(todo(places = listOf("Büro"), onLockScreen = true), today, words)
        assertEquals("due $today", dueToday.text)
        assertFalse(dueToday.skippable)
        assertTrue(dueToday.each.isEmpty())
        assertEquals(ReminderFace.OnLockScreen("Steuer", "due $today"), dueToday.onLockScreen)

        val somewhere = ReminderFace.of(todo(due = Due.None, places = listOf("Büro"), onLockScreen = true), today, words)
        assertEquals("at Büro", somewhere.text)
        assertEquals(ReminderFace.OnLockScreen("Steuer", null), somewhere.onLockScreen)

        assertNull(ReminderFace.of(todo(), today, words).onLockScreen)
    }
}
