package de.emilyshub

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import de.emilyshub.places.Area
import de.emilyshub.places.PlaceDraft
import de.emilyshub.routines.ChecklistDraftItem
import de.emilyshub.routines.FollowUps
import de.emilyshub.routines.Occurrence
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.Priority
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.RoutineDraft
import de.emilyshub.routines.RoutineTarget
import de.emilyshub.routines.TimeWindow
import de.emilyshub.security.FakeAuthenticator
import de.emilyshub.todos.Due
import de.emilyshub.todos.Recurrence
import de.emilyshub.todos.RecurrenceEnd
import de.emilyshub.todos.TodoDraft
import de.emilyshub.wishes.WishDraft
import de.emilyshub.wishes.WishStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalDate
import java.time.LocalTime

/** What a user does with the whole app, shared by the Robolectric tests that drive [MainActivity]. */
fun launchApp(): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java)

/** Leaves the app the way the home button does, lets [whileAway] happen, and opens it again. */
fun ActivityScenario<*>.leaveAndReturn(whileAway: () -> Unit) {
    moveToState(Lifecycle.State.CREATED)
    whileAway()
    moveToState(Lifecycle.State.RESUMED)
}

/**
 * A routine at [times] ("07:30"), daily unless given another [repetition], with the default follow-ups, time
 * window and priority unless given others, without a [fullScreen] alarm and with a [checklist], [tags],
 * [places], a [goal] and a [target] only if given, set up before the test opens the app.
 */
fun TestApplication.routine(
    name: String,
    vararg times: String,
    repetition: Repetition = Repetition.Daily,
    followUps: FollowUps = FollowUps(),
    fullScreen: Boolean = false,
    window: TimeWindow = TimeWindow(),
    priority: Priority = Priority.Normal,
    checklist: List<String> = emptyList(),
    tags: Set<Long> = emptySet(),
    places: Set<Long> = emptySet(),
    goal: Long? = null,
    target: RoutineTarget? = null,
    endsOn: LocalDate? = null,
): Long = runBlocking {
    val draft = RoutineDraft(
        name, description = "", times.map(LocalTime::parse), repetition, followUps, fullScreen, window, priority,
        checklist.map { ChecklistDraftItem(text = it) }, tags, places, goal, target, endsOn,
    )
    routines.create(draft)
}

/** A place recognised by the WLANs named [wifis], by its GPS [area], or by both, set up before the test opens the app. */
fun TestApplication.place(name: String, vararg wifis: String, area: Area? = null): Long = runBlocking {
    val draft = PlaceDraft(name, wifis.toList())
    places.create(area?.let { draft.withCenter(it.center).copy(radius = it.radius) } ?: draft)
}

/**
 * A to-do, open and [due] whenever unless given, under a [goal] and coming again by a [recurrence] if given, set up
 * before the test opens the app.
 */
fun TestApplication.todo(
    title: String,
    due: Due = Due.None,
    priority: Priority = Priority.Normal,
    checklist: List<String> = emptyList(),
    tags: Set<Long> = emptySet(),
    places: Set<Long> = emptySet(),
    goal: Long? = null,
    recurrence: Recurrence? = null,
    end: RecurrenceEnd = RecurrenceEnd.Never,
): Long = runBlocking {
    val items = checklist.map { ChecklistDraftItem(text = it) }
    val draft = TodoDraft(title, due = due, priority = priority, checklist = items, tagIds = tags, placeIds = places, goalId = goal)
    todos.create(draft.copy(recurrence = recurrence, end = end))
}

/** A tag, set up before the test opens the app. */
fun TestApplication.tag(name: String): Long = runBlocking { tags.create(name) }

/** A wish, open unless given another [status], under a [parent] goal if given, set up before the test opens the app. */
fun TestApplication.wish(
    title: String,
    period: String = "",
    status: WishStatus = WishStatus.Open,
    doneOn: LocalDate? = null,
    tags: Set<Long> = emptySet(),
    parent: Long? = null,
): Long = runBlocking {
    wishes.create(WishDraft(title, period = period, status = status, doneOn = doneOn, tagIds = tags, parentId = parent))
}

/** Once the prompt is up, the user puts a finger on the sensor and the app shows. */
fun ComposeTestRule.unlockWithFingerprint(authenticator: FakeAuthenticator) {
    waitForIdle()
    authenticator.succeed()
    waitForIdle()
}

/** The names of the routines of [low], [normal] and [high] priority are bolder with each, and the one of high larger. */
fun ComposeTestRule.assertNamesStandOutByPriority(low: String, normal: String, high: String) {
    val styles = listOf(low, normal, high).map { name ->
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText(name, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        layouts.single().layoutInput.style
    }
    val weights = styles.map { it.fontWeight ?: FontWeight.Normal }
    assertTrue("Bolder with each priority: $weights", weights.zipWithNext().all { (lower, higher) -> lower < higher })
    val sizes = styles.map { it.fontSize.value }
    assertTrue("Larger at high priority: $sizes", sizes[0] == sizes[1] && sizes[1] < sizes[2])
}

/** The alarms set for [action] of the reminders' receiver, such as the next reminder or looking around. */
fun ShadowAlarmManager.alarmsFor(action: String): List<ShadowAlarmManager.ScheduledAlarm> =
    scheduledAlarms.filter { shadowOf(it.operation).savedIntent.action == action }

/** The routine's occurrences on [days] gone by, ended as given, at its first time, set up before the test opens the app. */
fun TestApplication.history(routineId: Long, vararg days: Pair<String, OccurrenceStatus>) = runBlocking {
    val time = checkNotNull(routines.get(routineId)).times.first()
    testDatabase.routines().insertOccurrences(
        days.map { (date, status) -> Occurrence(routineId = routineId, date = LocalDate.parse(date), time = time, status = status) },
    )
}
