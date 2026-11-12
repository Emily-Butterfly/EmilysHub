package de.emilyshub.backup

import android.app.AlarmManager
import android.content.Context
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestApplication
import de.emilyshub.alarmsFor
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.Saving
import de.emilyshub.data.Setting
import de.emilyshub.history
import de.emilyshub.languages.AttachmentImport
import de.emilyshub.languages.ExamDraft
import de.emilyshub.languages.LessonDraft
import de.emilyshub.languages.LessonEditing
import de.emilyshub.languages.TeacherDraft
import de.emilyshub.languages.TeacherEditing
import de.emilyshub.ledger.BookingDraft
import de.emilyshub.ledger.BookingInterval
import de.emilyshub.ledger.BookingKind
import de.emilyshub.ledger.RecurringDraft
import de.emilyshub.ledger.SubscriptionDraft
import de.emilyshub.ledger.SubscriptionEditing
import de.emilyshub.ledger.TotalBudget
import de.emilyshub.ledger.saveNew
import de.emilyshub.notes.Container
import de.emilyshub.notes.NoteDraft
import de.emilyshub.notes.NoteLink
import de.emilyshub.notes.NoteSort
import de.emilyshub.place
import de.emilyshub.places.Area
import de.emilyshub.places.Coordinates
import de.emilyshub.reminders.ReminderReceiver
import de.emilyshub.reminders.Tone
import de.emilyshub.routine
import de.emilyshub.routines.OccurrenceStatus.Done
import de.emilyshub.routines.OccurrenceStatus.Dropped
import de.emilyshub.routines.OccurrenceStatus.Missed
import de.emilyshub.routines.RoutineTarget
import de.emilyshub.tag
import de.emilyshub.todo
import de.emilyshub.todos.Due
import de.emilyshub.todos.Recurrence
import de.emilyshub.todos.RecurrenceEnd
import de.emilyshub.todos.WakeTime
import de.emilyshub.wish
import de.emilyshub.wishes.WishStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Backups of all data (SPEC.md §10): made with the backup password set once, opened with it and checked strictly, and
 * only then put in place of all data, or nothing changes.
 */
@RunWith(AndroidJUnit4::class)
class BackupsTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val backups = app.backups

    @Test
    fun `a backup brings back all data as they were when it was made`() {
        val made = someOfEverything()
        val file = exported()
        changeEverything()

        restore(file)

        assertEquals(made, everything())
        assertEquals(listOf("Vitamine", "Yoga", "Vokabeln"), runBlocking { app.routines.inUse().first() }.map { it.name })
    }

    @Test
    fun `opening a backup changes nothing yet`() {
        someOfEverything()
        val file = exported()
        changeEverything()
        val changed = everything()

        val backup = open(file)

        assertEquals(changed, everything())
        assertEquals(Instant.parse("2026-10-23T05:00:00Z"), backup.created.toInstant())
        assertEquals(Backup.Summary(routines = 3, todos = 4, wishes = 3, notes = 2, places = 2, tags = 2), backup.summary)
    }

    @Test
    fun `what shows the data sees those restored`() {
        someOfEverything()
        val file = exported()
        changeEverything()
        val shown = mutableListOf<List<String>>()
        val showing = CoroutineScope(Dispatchers.Unconfined).launch {
            app.routines.inUse().collect { all -> shown += all.map { it.name } }
        }

        restore(file)

        showing.cancel()
        assertEquals(listOf("Vitamine", "Yoga", "Vokabeln"), shown.last())
    }

    @Test
    fun `a wrong password, a changed file or a cut one is refused, and the data stay as they are`() {
        someOfEverything()
        val file = exported()
        changeEverything()
        val changed = everything()

        assertProblem(BackupProblem.WrongPassword) { open(file, password = "Lila und Flieder") }
        assertProblem(BackupProblem.Damaged) { open(file.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }) }
        assertProblem(BackupProblem.Damaged) { open(file.copyOf(file.size - 100)) }
        assertProblem(BackupProblem.NotABackup) { open("Einkaufsliste".toByteArray()) }
        assertEquals(changed, everything())
    }

    @Test
    fun `a backup of a newer app version, or of one before any the app knows, is refused as such`() {
        someOfEverything()
        val file = exported()
        val content = contentOf(file)

        assertProblem(BackupProblem.NewerVersion) { open(sealed(content.put("schema", HubDatabase.VERSION + 1))) }
        assertProblem(BackupProblem.OlderVersion) { open(sealed(content.put("schema", 0), attachmentsOf(file))) }
    }

    @Test
    fun `a backup of an older app version is brought up to this one, as an update of the app would`() {
        val made = someOfEverything(before19 = true)
        val file = sealed(asOf(16, contentOf(exported())))
        changeEverything()

        val backup = open(file)
        runBlocking { backups.restore(backup) }

        // Version 16 had no wishes yet, nor notes, so there are none after the update either.
        assertEquals(Backup.Summary(routines = 2, todos = 2, wishes = 0, notes = 0, places = 2, tags = 2), backup.summary)
        val restored = everything()
        val newer = tablesSince(16)
        assertEquals(made - newer, restored - newer)
        assertEquals(newer.map { emptyList<Any>() }, newer.map(restored::getValue))
    }

    @Test
    fun `a backup several versions old goes through each migration on the way`() {
        val made = someOfEverything(before19 = true)
        val file = sealed(asOf(14, contentOf(exported())))
        changeEverything()

        restore(file)

        // Version 14 had no places for to-dos, nor their reminders: they start as an update would have left them.
        val restored = everything()
        val newer = tablesSince(14) + "todos"
        assertEquals(made - newer, restored - newer)
        assertEquals(emptyList<Any>(), restored.getValue("todo_places"))
        assertEquals(listOf("Milch kaufen", "Steuer machen"), runBlocking { app.todos.open().first() }.map { it.todo.title }.sorted())
    }

    @Test
    fun `a series under way in a backup from before ends could be set counts on from its first to-do`() {
        val market = app.todo("Markt", due = Due.On(LocalDate.parse("2026-10-24")), recurrence = Recurrence.Daily)
        runBlocking { app.todos.markDone(market) }
        runBlocking { app.todos.markDone(app.todos.open().first().single().todo.id) }
        app.todo("Steuer machen")
        val file = sealed(asOf(25, contentOf(exported())))

        restore(file)

        val numbers = runBlocking { app.todos.archived().first() + app.todos.open().first().map { it.todo } }
            .associate { "${it.title} ${it.due.date}" to it.seriesNumber }
        assertEquals(
            mapOf("Markt 2026-10-24" to 1, "Markt 2026-10-25" to 2, "Markt 2026-10-26" to 3, "Steuer machen null" to 1),
            numbers,
        )
    }

    @Test
    fun `a backup from before fixed bookings could end loads them without an end`() {
        someOfEverything()
        val file = sealed(asOf(38, contentOf(exported())))
        changeEverything()

        restore(file)

        assertTrue(runBlocking { app.ledger.templates().first() }.all { it.draft.end == RecurrenceEnd.Never })
    }

    @Test
    fun `a backup from before the archive of notes loads with none archived, and a backup now keeps it`() {
        val verbs = runBlocking { app.notes.create(NoteDraft("Verben")).also { app.notes.archiveNote(it) } }
        val now = exported()
        val older = sealed(asOf(40, contentOf(now)))

        restore(now)
        assertTrue(runBlocking { app.notes.notebook().first().let { it.isArchived(checkNotNull(it.note(verbs))) } })

        restore(older)
        assertTrue(runBlocking { app.notes.notebook().first().all().any { it.id == verbs } })
    }

    @Test
    fun `a backup from before budgets of single months loads without them`() {
        val made = someOfEverything()
        val file = sealed(asOf(39, contentOf(exported())))
        changeEverything()

        restore(file)

        val restored = everything()
        assertEquals(emptyList<Any>(), restored.getValue("ledger_month_budgets"))
        assertEquals(made.getValue("ledger_categories"), restored.getValue("ledger_categories"))
    }

    @Test
    fun `a backup keeps the Abos, and one from before them loads without any`() {
        val made = someOfEverything()
        val now = exported()
        val older = sealed(asOf(42, contentOf(now)))
        changeEverything()

        restore(now)
        assertEquals(made.getValue("subscriptions"), everything().getValue("subscriptions"))

        restore(older)
        val restored = everything()
        assertEquals(emptyList<Any>(), restored.getValue("subscriptions"))
        assertEquals(made.getValue("recurring_bookings"), restored.getValue("recurring_bookings"))
    }

    @Test
    fun `a backup keeps the languages, teachers and lessons, and one from before them loads without any`() {
        val made = someOfEverything()
        val now = exported()
        val older = sealed(asOf(44, contentOf(now)))
        changeEverything()

        restore(now)
        for (table in listOf("languages", "teachers", "teacher_languages", "lessons")) {
            assertEquals(table, made.getValue(table), everything().getValue(table))
        }

        restore(older)
        val restored = everything()
        for (table in listOf("languages", "teachers", "teacher_languages", "lessons")) {
            assertEquals(table, emptyList<Any>(), restored.getValue(table))
        }
        assertEquals(made.getValue("notes"), restored.getValue("notes"))
    }

    @Test
    fun `a backup keeps the topics, exams and the topics of the lessons, and one from before them loads without any`() {
        val made = someOfEverything()
        val now = exported()
        val older = sealed(asOf(45, contentOf(now)))
        changeEverything()

        restore(now)
        for (table in listOf("exams", "topics", "lesson_topics")) {
            assertEquals(table, made.getValue(table), everything().getValue(table))
        }
        assertEquals(2, made.getValue("topics").size)

        restore(older)
        val restored = everything()
        for (table in listOf("exams", "topics", "lesson_topics")) {
            assertEquals(table, emptyList<Any>(), restored.getValue(table))
        }
        assertEquals(made.getValue("lessons"), restored.getValue("lessons"))
    }

    @Test
    fun `a backup keeps from when the Abos remind, and one from before that loads with all their times counting`() {
        someOfEverything()
        val now = exported()
        val older = sealed(asOf(43, contentOf(now)))
        val made = runBlocking { app.subscriptions.overview().first() }.running.map { it.subscription }
        changeEverything()

        restore(now)
        assertEquals(made.map { it.remindsFrom }, runBlocking { app.subscriptions.overview().first() }.running.map { it.subscription.remindsFrom })

        restore(older)
        val restored = runBlocking { app.subscriptions.overview().first() }.running.map { it.subscription }
        assertEquals(made.map { it.copy(remindsFrom = null) }, restored)
    }

    @Test
    fun `a backup keeps the total budgets, and one from before them loads without any`() {
        val made = someOfEverything()
        val now = exported()
        val older = sealed(asOf(41, contentOf(now)))
        changeEverything()

        restore(now)
        assertEquals(made.getValue("ledger_total_budget"), everything().getValue("ledger_total_budget"))
        assertEquals(made.getValue("ledger_closed_months"), everything().getValue("ledger_closed_months"))
        assertEquals(TotalBudget.FromIncome(10_000), runBlocking { app.ledger.month(YearMonth.of(2026, 12)).first().totalSetting })

        restore(older)
        val restored = everything()
        assertEquals(emptyList<Any>(), restored.getValue("ledger_total_budget"))
        assertEquals(emptyList<Any>(), restored.getValue("ledger_month_total_budgets"))
        assertEquals(null, runBlocking { app.ledger.month(YearMonth.of(2026, 9)).first().totalBudget })
        assertEquals(made.getValue("bookings"), restored.getValue("bookings"))
    }

    @Test
    fun `a backup from before opening balances loads without them`() {
        val made = someOfEverything()
        val file = sealed(asOf(37, contentOf(exported())))
        changeEverything()

        restore(file)

        val restored = everything()
        assertEquals(emptyList<Any>(), restored.getValue("ledger_opening_balances"))
        assertEquals(made.getValue("bookings"), restored.getValue("bookings"))
    }

    @Test
    fun `a backup from before budgets loads without them, its months closed with none`() {
        val made = someOfEverything()
        val file = sealed(asOf(31, contentOf(exported())))
        changeEverything()

        restore(file)

        val restored = everything()
        assertEquals(emptyList<Any>(), restored.getValue("ledger_closed_budgets"))
        // The months alone: the total budget they keep came later still.
        assertEquals(made.getValue("ledger_closed_months").map { it.first() }, restored.getValue("ledger_closed_months").map { it.first() })
        assertTrue(runBlocking { app.ledger.categories().first() }.all { it.budgetCents == null })
    }

    @Test
    fun `a backup from before full-screen to-dos loads with their alarms off, and a backup now keeps them`() {
        app.todo("Arzt anrufen", fullScreen = true)
        val now = exported()
        val older = sealed(asOf(32, contentOf(now)))

        restore(now)
        assertEquals(listOf(true), runBlocking { app.todos.open().first() }.map { it.todo.style.fullScreen })

        restore(older)
        assertEquals(listOf(false), runBlocking { app.todos.open().first() }.map { it.todo.style.fullScreen })
    }

    @Test
    fun `a backup from before wake times loads without them, and a backup now keeps them`() {
        app.todo("Müll", due = Due.On(LocalDate.parse("2026-10-30")), wake = WakeTime.Before(1, LocalTime.of(18, 0)))
        val now = exported()
        val older = sealed(asOf(33, contentOf(now)))

        restore(now)
        assertEquals(listOf(WakeTime.Before(1, LocalTime.of(18, 0))), runBlocking { app.todos.open().first() }.map { it.todo.wake })

        restore(older)
        assertEquals(listOf(null), runBlocking { app.todos.open().first() }.map { it.todo.wake })
    }

    @Test
    fun `a backup from before names on the lock screen loads with them off, and a backup now keeps them`() {
        app.routine("Arzttermin", "07:30", nameOnLockScreen = true)
        app.todo("Arzt anrufen", nameOnLockScreen = true)
        val now = exported()
        val older = sealed(asOf(34, contentOf(now)))

        restore(now)
        assertEquals(listOf(true), runBlocking { app.routines.inUse().first() }.map { it.style.nameOnLockScreen })
        assertEquals(listOf(true), runBlocking { app.todos.open().first() }.map { it.todo.style.nameOnLockScreen })

        restore(older)
        assertEquals(listOf(false), runBlocking { app.routines.inUse().first() }.map { it.style.nameOnLockScreen })
        assertEquals(listOf(false), runBlocking { app.todos.open().first() }.map { it.todo.style.nameOnLockScreen })
    }

    @Test
    fun `a backup keeps the tone chosen, not the sound itself, and one from before tones loads with the default`() {
        val morning = Tone.Of("content://media/internal/audio/media/12")
        app.routine("Vitamine", "07:30", tone = morning)
        app.todo("Arzt anrufen", tone = Tone.Silent)
        val now = exported()
        val older = sealed(asOf(35, contentOf(now)))

        restore(now)
        assertEquals(listOf(morning), runBlocking { app.routines.inUse().first() }.map { it.style.tone })
        assertEquals(listOf(Tone.Silent), runBlocking { app.todos.open().first() }.map { it.todo.style.tone })

        restore(older)
        assertEquals(listOf(Tone.Standard), runBlocking { app.routines.inUse().first() }.map { it.style.tone })
        assertEquals(listOf(Tone.Standard), runBlocking { app.todos.open().first() }.map { it.todo.style.tone })
    }

    @Test
    fun `a backup keeps coupled routines and what one done skipped, and one from before loads without couplings`() {
        val home = app.routine("Tabletten zu Hause", "08:00")
        app.routine("Tabletten auf der Arbeit", "09:00", coupledWith = setOf(home))
        runBlocking { app.routines.markDone(app.routines.dueOn(app.routines.today()).first().first().occurrenceId) }
        val now = exported()
        val older = sealed(asOf(36, contentOf(now)))

        restore(now)
        assertEquals(1, runBlocking { app.routines.coupledWith(home) }.size)
        assertEquals("Tabletten zu Hause", runBlocking { app.routines.dueOn(app.routines.today()).first() }.last().coveredBy)

        restore(older)
        assertEquals(emptySet<Long>(), runBlocking { app.routines.coupledWith(home) })
        assertEquals(null, runBlocking { app.routines.dueOn(app.routines.today()).first() }.last().coveredBy)
    }

    @Test
    fun `a backup of an older app version that does not fit how the data were laid out then is damaged`() {
        someOfEverything()
        // The data of this version, said to be of the one before: the wishes are tables too many for then.
        val content = contentOf(exported()).put("schema", 16)
        changeEverything()
        val changed = everything()

        assertProblem(BackupProblem.Damaged) { open(sealed(content)) }
        assertEquals(changed, everything())
    }

    @Test
    fun `a backup that does not fit the tables of the app is refused, and the data stay as they are`() {
        someOfEverything()
        val file = exported()
        changeEverything()
        val changed = everything()
        val unfitting: List<Pair<String, (JSONObject) -> Unit>> = listOf(
            "a table missing" to { it.tables().remove("tags") },
            "a table unknown" to { it.tables().put("passwords", it.tables().getJSONObject("tags")) },
            "a column unknown" to { it.table("tags").getJSONArray("columns").put("colour") },
            "a column missing" to { it.table("tags").put("columns", JSONArray(listOf("id"))) },
            "a row too short" to { it.rowsOf("tags").put(JSONArray(listOf(99))) },
            "text for a number" to { it.rowsOf("tags").getJSONArray(0).put(0, "eins") },
            "a number for text" to { it.rowsOf("tags").getJSONArray(0).put(1, 42) },
            "nothing where it must be" to { it.rowsOf("tags").getJSONArray(0).put(1, JSONObject.NULL) },
            "the same id twice" to { it.rowsOf("tags").put(it.rowsOf("tags").getJSONArray(0)) },
            "a tag on no to-do" to { it.rowsOf("todo_tags").getJSONArray(0).put(0, 999) },
            "a time not a time" to { it.put("created", "gestern") },
            "a list for a value" to { it.rowsOf("tags").getJSONArray(0).put(1, JSONArray()) },
            "a key unknown" to { it.put("passwords", JSONObject()) },
            "a key unknown in a table" to { it.table("tags").put("colour", "lila") },
            "a repetition the app cannot read" to { it.setFirst("routines", "repetition", "jede Woche") },
            "a recurrence the app cannot read" to { it.setFirst("todos", "recurrence", "monthly:32") },
            "a routine target the app cannot read" to { it.setFirst("routines", "target", "streak:0") },
            "a status the app cannot read" to { it.setFirst("occurrences", "status", "vielleicht") },
            "a sort the app cannot read" to { it.setFirst("note_folders", "sort", "zufällig") },
            "a folder within itself" to { it.setFirst("note_folders", "parent_id", it.rowsOf("note_folders").getJSONArray(0)[0]) },
            "a note in another tab than its folder" to { it.setFirst("note_folders", "tab_id", JSONObject.NULL) },
            "a note without a title" to { it.setFirst("notes", "title", " ") },
            "a note too long" to { it.setFirst("notes", "text", "x".repeat(NoteDraft.TEXT_MAX + 1)) },
            "a tab named as a tab the app has" to { it.setFirst("note_tabs", "name", "Alle") },
            "two tabs of the same name" to { it.rowsOf("note_tabs").put(JSONArray(listOf(99, "arabisch", 5, "changed"))) },
            "a folder without a name" to { it.setFirst("note_folders", "name", "") },
            "a time the app cannot read" to { it.setFirst("routines", "times", "halb acht") },
            "a date the app cannot read" to { it.setFirst("occurrences", "date", "2026-02-30") },
        )

        for ((what, change) in unfitting) {
            val content = contentOf(file).also(change)
            assertEquals(what, BackupProblem.Damaged, assertThrows(BackupException::class.java) { open(sealed(content)) }.problem)
        }
        assertProblem(BackupProblem.Damaged) { open(BackupFile.seal("{ kein JSON".toByteArray(), key())) }
        assertEquals(changed, everything())
    }

    @Test
    fun `a backup nested deeper than any the app makes is damaged, not a crash, and nothing changes`() {
        someOfEverything()
        val before = everything()
        // Within the size a backup may have, but nested far deeper than a parser's stack goes.
        val nested = """{"schema": ${HubDatabase.VERSION}, "created": "2026-10-23T05:00:00Z", "tables": {"notes": """ +
            """{"columns": [], "rows": ${"[".repeat(500_000)}"""

        assertProblem(BackupProblem.Damaged) { open(BackupFile.seal(nested.toByteArray(), key())) }
        assertEquals(before, everything())
    }

    @Test
    fun `the backup password is set once, and only its key is kept, sealed with the Keystore key`() {
        someOfEverything()
        assertFalse(runBlocking { backups.hasPassword() })
        assertThrows(IllegalStateException::class.java) { exported(setPassword = false) }

        runBlocking { backups.setPassword(PASSWORD) }

        assertTrue(runBlocking { backups.hasPassword() })
        val stored = app.settingsDao.values.values.joinToString()
        assertFalse(stored.contains(PASSWORD))
        assertThrows(IllegalStateException::class.java) { runBlocking { backups.setPassword("ein anderes Passwort") } }
        open(exported(setPassword = false), PASSWORD)
    }

    @Test
    fun `a new backup password has at least 10 characters, typed the same twice`() {
        assertEquals(PasswordProblem.TooShort, Backups.passwordProblem("Flieder", "Flieder"))
        assertEquals(PasswordProblem.NotRepeated, Backups.passwordProblem("Flieder und Lila", "Flieder und lila"))
        assertNull(Backups.passwordProblem("Fliederbaum", "Fliederbaum"))
    }

    @Test
    fun `restoring on a device without a backup password takes over the one of the backup`() {
        someOfEverything()
        val file = exported()
        app.settingsDao.values.clear()

        restore(file)

        assertTrue(runBlocking { backups.hasPassword() })
        open(exported(setPassword = false), PASSWORD)
    }

    @Test
    fun `restoring keeps the backup password set on this device`() {
        someOfEverything()
        val other = BackupKey.derive("das Passwort vom alten Handy", iterations = FEW_ITERATIONS)
        val made = exported()
        val file = BackupFile.seal(contentOf(made).toString().toByteArray(), other, attachmentsOf(made))

        restore(file, password = "das Passwort vom alten Handy")

        open(exported(setPassword = false), PASSWORD)
        assertProblem(BackupProblem.WrongPassword) { open(exported(setPassword = false), password = "das Passwort vom alten Handy") }
    }

    @Test
    fun `the settings of this device are no part of a backup`() {
        runBlocking { app.testDatabase.settings().put(Setting("design", "dark")) }
        val file = exported()
        runBlocking { app.testDatabase.settings().put(Setting("design", "light")) }

        restore(file)

        assertEquals("light", runBlocking { app.testDatabase.settings().get("design") })
        assertFalse(contentOf(file).tables().has("settings"))
    }

    @Test
    fun `after a restore, the reminders follow the data restored`() {
        val vitamins = app.routine("Vitamine", "07:30")
        val file = exported()
        runBlocking { app.routines.delete(vitamins) }
        assertEquals(emptyList<Any>(), reminderAlarms())

        restore(file)

        assertEquals(listOf(Instant.parse("2026-10-23T05:30:00Z")), reminderAlarms())
    }

    /**
     * Two routines, one with a checklist ticked in part today, tags, places by WLAN and GPS, to-dos at them, wishes, notes
     * in a folder of a tab and in none, and, unless the data are to be as an app [before19] made them, a to-do coming again, ticked
     * off once, and a routine under a goal, aiming at a streak.
     */
    private fun someOfEverything(before19: Boolean = false): Map<String, List<List<Any?>>> {
        val home = app.place("Zuhause", "FRITZ!Box 7590")
        val shop = app.place("Supermarkt", area = Area(Coordinates(52.52, 13.405), radius = 150))
        val health = app.tag("Gesundheit")
        val sport = app.tag("Sport")
        val vitamins = app.routine("Vitamine", "07:30", checklist = listOf("Vitamin D", "Magnesium"), tags = setOf(health))
        app.routine("Yoga", "18:00", tags = setOf(sport), places = setOf(home))
        app.history(vitamins, "2026-10-20" to Dropped, "2026-10-21" to Missed, "2026-10-22" to Done)
        runBlocking {
            val occurrence = app.testDatabase.routines().occurrencesOn(vitamins, LocalDate.parse("2026-10-23")).single()
            app.routines.tick(occurrence.id, app.routines.checklist(vitamins).first().id, ticked = true)
        }
        val milk = app.todo("Milch kaufen", checklist = listOf("Hafermilch"), tags = setOf(health), places = setOf(shop))
        app.todo("Steuer machen")
        val northernLights = app.wish("Nordlichter sehen", period = "Winter 2027")
        if (!before19) {
            val rent = app.todo("Miete überweisen", due = Due.On(LocalDate.parse("2026-10-01")), recurrence = Recurrence.Monthly(1))
            runBlocking { app.todos.markDone(rent) }
            app.routine("Vokabeln", "20:00", goal = northernLights, target = RoutineTarget.Streak(30))
        }
        app.wish("Marathon laufen", status = WishStatus.Done, doneOn = LocalDate.parse("2026-10-03"), tags = setOf(sport))
        app.wish("Flüge buchen", parent = northernLights)
        runBlocking {
            val links = setOf(NoteLink.Todo(milk), NoteLink.Routine(vitamins))
            val shopping = app.notes.create(NoteDraft("Einkauf", "- [ ] Hafermilch", links = links))
            app.notes.pinOnToday(shopping, pinned = true)
            val arabic = app.notes.createTab("Arabisch")
            val grammar = app.notes.createFolder("Grammatik", Container(arabic))
            app.notes.sort(Container(arabic, grammar), NoteSort.Manual)
            app.notes.sort(Container(), NoteSort.Name)
            app.notes.pinFolder(grammar, pinned = true)
            val inGrammar = Container(arabic, grammar)
            val verbs = app.notes.create(NoteDraft("Verben", "## Präsens\n**kataba** – schreiben", container = inGrammar))
            app.notes.update(verbs, NoteDraft("Verben", "## Präsens", inGrammar, setOf(NoteLink.Goal(northernLights)), setOf(sport)))
            app.notes.pinNote(verbs, pinned = true)
            val food = app.ledger.categories().first().first { it.name == "Lebensmittel" }
            app.ledger.saveNew(BookingDraft(BookingKind.Expense, "45,30", LocalDate.parse("2026-10-21"), food.id, "Wocheneinkauf"))
            val rent = app.ledger.categories().first().first { it.name == "Wohnen" }
            app.ledger.createTemplate(
                RecurringDraft(BookingKind.Expense, "850", rent.id, "Miete", BookingInterval.Monthly, 1, YearMonth.of(2026, 10)),
            )
            app.ledger.setBudget(food.id, 30_000)
            app.ledger.setTotalBudget(TotalBudget.Fixed(200_000))
            app.ledger.close(YearMonth.of(2026, 9), carryOver = true)
            app.ledger.setTotalBudgetIn(YearMonth.of(2026, 12), TotalBudget.FromIncome(10_000))
            val rentId = app.ledger.templates().first().first().id
            app.subscriptions.save(
                SubscriptionEditing.New,
                SubscriptionDraft("Miete", recurringId = rentId, note = "Kündigung schriftlich", cancelBy = LocalDate.parse("2027-01-31")),
            )
            val arabicLessons = (app.languages.createLanguage("Arabisch") as Saving.Saved).id
            val amal = app.languages.saveTeacher(TeacherEditing.New, TeacherDraft("Amal", "italki", setOf(arabicLessons))) as Saving.Saved
            val a2 = app.languages.createExam(arabicLessons, ExamDraft("A2", LocalDate.parse("2026-12-25"))) as Saving.Saved
            val numbers = app.languages.createTopic(arabicLessons, "Zahlen", a2.id) as Saving.Saved
            app.languages.setTopicDone(numbers.id, done = true)
            app.languages.save(
                LessonEditing.New,
                LessonDraft(
                    LocalDate.parse("2026-11-02"), "45", arabicLessons, amal.id, price = "12,50", note = "## Verben",
                    topicIds = setOf(numbers.id), newTopics = listOf("Verben"),
                    attachments = listOf(
                        (app.lessonFiles.importPdf(app.lessonDocuments.put("Blatt.pdf", "%PDF-1.7 Arbeitsblatt".toByteArray()), 0) as AttachmentImport.Added)
                            .attachment,
                    ),
                ),
            )
            app.languages.setExamTaken(a2.id, taken = true, result = "bestanden")
            app.ledger.setOpening(YearMonth.of(2026, 11), -8_520)
            app.ledger.setBudgetIn(YearMonth.of(2026, 12), food.id, 45_000)
            app.ledger.createTemplate(
                RecurringDraft(
                    BookingKind.Expense, "9,99", rent.id, "Streaming", BookingInterval.Monthly, 5, YearMonth.of(2026, 9),
                    RecurrenceEnd.AfterTimes(12),
                ),
            )
        }
        return everything().also { all -> assertTrue("Some of everything: $all", all.values.all { it.isNotEmpty() }) }
    }

    private fun changeEverything() = runBlocking {
        app.routines.inUse().first().forEach { app.routines.delete(it.id) }
        app.tags.all().first().forEach { app.tags.delete(it.id) }
        app.todo("Neues To-do")
        app.wish("Neuer Wunsch")
        app.notes.create(NoteDraft("Neue Notiz"))
    }

    /** Every row of every table but the settings, read here on its own, without the backups. */
    private fun everything(): Map<String, List<List<Any?>>> {
        val db = app.testDatabase.openHelper.readableDatabase
        val tables = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table', 'settings') ORDER BY name",
        ).use { cursor -> List(cursor.count) { cursor.moveToNext(); cursor.getString(0) } }
        return tables.associateWith { table ->
            // By the first two columns, or the only one.
            val order = db.query("SELECT * FROM `$table` LIMIT 0").use { if (it.columnCount > 1) "1, 2" else "1" }
            db.query("SELECT * FROM `$table` ORDER BY $order").use { cursor ->
                List(cursor.count) {
                    cursor.moveToNext()
                    List(cursor.columnCount) { column ->
                        when (cursor.getType(column)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(column)
                            android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(column)
                            android.database.Cursor.FIELD_TYPE_NULL -> null
                            else -> cursor.getString(column)
                        }
                    }
                }
            }
        }
    }

    private fun exported(setPassword: Boolean = true): ByteArray {
        if (setPassword) runBlocking { backups.setPassword(PASSWORD) }
        val file = ByteArrayOutputStream()
        runBlocking { backups.export { file } }
        return file.toByteArray()
    }

    private fun open(file: ByteArray, password: String = PASSWORD): Backup =
        runBlocking { backups.open({ ByteArrayInputStream(file) }, password) }

    private fun restore(file: ByteArray, password: String = PASSWORD) = runBlocking { backups.restore(open(file, password)) }

    private fun contentOf(file: ByteArray) = JSONObject(String(BackupFile.open(file, PASSWORD).plaintext))

    /** [content] sealed as a backup, with the [attachments] it lists following it. */
    private fun sealed(content: JSONObject, attachments: List<ByteArray> = emptyList()) =
        BackupFile.seal(content.toString().toByteArray(), key(), attachments)

    /** The attachments [file] carries after its content. */
    private fun attachmentsOf(file: ByteArray) = BackupFile.open(file, PASSWORD).attachments

    private fun key() = BackupKey.derive(PASSWORD, iterations = FEW_ITERATIONS)

    /**
     * [content] as [version] made it (SPEC.md §10): only the tables and columns that version had, as Room exported its
     * schema, so that a schema change needs nothing here.
     */
    private fun asOf(version: Int, content: JSONObject): JSONObject {
        val layout = migration().dataColumnsOf(version)
        val tables = content.tables()
        tables.keys().asSequence().toList().filter { it !in layout }.forEach(tables::remove)
        for ((table, columns) in layout) {
            val names = content.table(table).getJSONArray("columns").let { all -> List(all.length(), all::getString) }
            content.withoutColumns(table, *(names - columns.toSet()).toTypedArray())
        }
        // The attachments came with the version that made them files of their own.
        if (version < 47) content.remove("attachments")
        return content.put("schema", version)
    }

    /** The tables of the app's data that came after [version], as Room exported its schemas. */
    private fun tablesSince(version: Int): Set<String> = migration().let { schemas ->
        schemas.dataColumnsOf(HubDatabase.VERSION).keys - schemas.dataColumnsOf(version).keys
    }

    private fun migration() = BackupMigration(app) { FrameworkSQLiteOpenHelperFactory() }

    private fun JSONObject.withoutColumns(table: String, vararg names: String) = apply {
        val columns = table(table).getJSONArray("columns")
        val rows = rowsOf(table)
        for (name in names) {
            val index = (0 until columns.length()).first { columns.getString(it) == name }
            columns.remove(index)
            for (row in 0 until rows.length()) rows.getJSONArray(row).remove(index)
        }
    }

    private fun JSONObject.tables() = getJSONObject("tables")

    private fun JSONObject.table(name: String) = tables().getJSONObject(name)

    private fun JSONObject.rowsOf(table: String) = table(table).getJSONArray("rows")

    /** Sets the [column] of the first row of [table] to [value]. */
    private fun JSONObject.setFirst(table: String, column: String, value: Any) {
        val columns = table(table).getJSONArray("columns")
        rowsOf(table).getJSONArray(0).put((0 until columns.length()).first { columns.getString(it) == column }, value)
    }

    private fun reminderAlarms() = shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        .alarmsFor(ReminderReceiver.ACTION_REMIND).map { Instant.ofEpochMilli(it.triggerAtMs) }

    private fun assertProblem(problem: BackupProblem, block: () -> Unit) {
        assertEquals(problem, assertThrows(BackupException::class.java, block).problem)
    }

    private companion object {
        const val PASSWORD = "Flieder und Lila"
        const val FEW_ITERATIONS = 1_000
    }
}
