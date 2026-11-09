package de.emilyshub.reminders

import android.content.res.Resources
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.todos.TodoReminder
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.fields.placesLabel
import de.emilyshub.ui.todos.dueText
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * How a due reminder shows (SPEC.md §3, §5, §9), in its notification and in its full-screen alarm alike: its [title] and
 * [text]; what the lock screen shows of it, where it allows that ([onLockScreen]), else only that one is due; for a
 * joint reminder of coupled routines, each of them by name ([each]), to say which one was done; and whether it can be
 * skipped, as a routine's can and a to-do's cannot ([skippable]). The one place that decides it; the notification and
 * the alarm only render it.
 */
data class ReminderFace(
    val title: String,
    val text: String?,
    val skippable: Boolean,
    val onLockScreen: OnLockScreen? = null,
    val each: List<Choice> = emptyList(),
) {
    /** What the lock screen shows of a reminder that allows its name there: the name as [title], and when, [text]. */
    data class OnLockScreen(val title: String, val text: String?)

    /** One of the routines a joint reminder reminds of: its occurrence, by its [id], and its routine's [name]. */
    data class Choice(val id: Long, val name: String)

    companion object {
        /** How [due] shows on [today], in [words]. */
        fun of(due: DueReminder, today: LocalDate, words: ReminderWords): ReminderFace = when (due) {
            is DueReminder.OfOccurrence -> {
                val reminder = due.reminder
                val dueAt = words.dueAt(reminder.dueAt)
                ReminderFace(
                    title = reminder.title,
                    text = dueAt,
                    skippable = true,
                    onLockScreen = OnLockScreen(reminder.title, dueAt).takeIf { reminder.titleOnLockScreen },
                    each = if (due.joint) {
                        reminder.occurrences.sortedWith(compareBy(InputText.byName) { it.name }).map { Choice(it.occurrenceId, it.name) }
                    } else {
                        emptyList()
                    },
                )
            }
            is DueReminder.OfTodo -> {
                val reminder = due.reminder
                val whenDue = words.todoDue(reminder, today)
                ReminderFace(
                    title = reminder.title,
                    // When it is due; without a due, where it reminds, if it still has a place.
                    text = whenDue ?: words.places(reminder.places),
                    skippable = false,
                    // When it is due, but never where.
                    onLockScreen = OnLockScreen(reminder.title, whenDue).takeIf { reminder.style.nameOnLockScreen },
                )
            }
        }
    }
}

/** The words a reminder's face is told in: the app's own in [ResourceWords]; plain ones in tests. */
interface ReminderWords {
    /** "Fällig um 18:00". */
    fun dueAt(time: ZonedDateTime): String

    /** "Fällig: heute, 18:00", as the To-dos list says it on [today]; none without a due. */
    fun todoDue(reminder: TodoReminder, today: LocalDate): String?

    /** The places a to-do reminds at; none without any. */
    fun places(places: List<String>): String?
}

/** The app's own words for a reminder's face. */
class ResourceWords(private val resources: Resources) : ReminderWords {
    override fun dueAt(time: ZonedDateTime): String = resources.getString(R.string.reminder_due_at, time.clockTime())

    override fun todoDue(reminder: TodoReminder, today: LocalDate): String? =
        resources.dueText(reminder.due, today)?.let { resources.getString(R.string.todo_due_label, it) }

    override fun places(places: List<String>): String? = resources.placesLabel(places)
}
