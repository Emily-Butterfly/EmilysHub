package de.emilyshub.wishes

import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.todos.TodoDraft
import de.emilyshub.todos.Todos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The goal watch (SPEC.md §6): what changes parts of goals names them before, and the watch settles their goals once the
 * change is over, as the parts are then.
 */
@RunWith(AndroidJUnit4::class)
class GoalWatchTest {

    private val database = inMemoryDatabase()

    // Friday, 23 October 2026, 07:00 in Berlin.
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.parse("2026-10-23")
    private val wishes = Wishes({ database }, clock, Dispatchers.Unconfined)
    private val todos = Todos({ database }, clock, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `the last part watched and done makes its goal done, the day the watch is for`() = runBlocking {
        val goal = wishes.create(WishDraft("Umzug"))
        val boxes = todos.create(TodoDraft("Kisten packen", goalId = goal))

        watching { watch ->
            watch.todo(boxes)
            database.todos().setDoneAt(boxes, clock.now)
        }

        assertEquals(WishStatus.Done to today, wishes.get(goal)?.let { it.status to it.doneOn })
    }

    @Test
    fun `a part added opens its goal, done, again`() = runBlocking {
        val goal = wishes.create(WishDraft("Umzug", status = WishStatus.Done, doneOn = today))
        val boxes = todos.create(TodoDraft("Kisten packen")) // under no goal yet

        watching { watch ->
            database.todos().update(checkNotNull(database.todos().get(boxes)).copy(goalId = goal))
            watch.todoAdded(boxes)
        }

        assertEquals(WishStatus.InProgress, wishes.get(goal)?.status)
    }

    @Test
    fun `a part watched and deleted counts as gone`() = runBlocking {
        val goal = wishes.create(WishDraft("Umzug"))
        val boxes = todos.create(TodoDraft("Kisten packen", goalId = goal))
        val van = todos.create(TodoDraft("Transporter mieten", goalId = goal))
        todos.markDone(van)

        watching { watch ->
            watch.todo(boxes)
            database.todos().delete(boxes)
        }

        assertEquals(WishStatus.Done, wishes.get(goal)?.status)
    }

    @Test
    fun `parts changed together are settled once, as they are at the end`() = runBlocking {
        val goal = wishes.create(WishDraft("Umzug"))
        val boxes = todos.create(TodoDraft("Kisten packen", goalId = goal))
        val van = todos.create(TodoDraft("Transporter mieten", goalId = goal))

        watching { watch ->
            watch.todo(boxes)
            watch.todo(van)
            database.todos().setDoneAt(boxes, clock.now)
            database.todos().setDoneAt(van, clock.now)
        }

        assertEquals(WishStatus.Done, wishes.get(goal)?.status)
    }

    @Test
    fun `a watch within a watch joins it, so that a part closed and opened again leaves a goal as the user set it`() = runBlocking {
        // Set done by hand while a part is still open: it stays so until a part opens or the last one closes.
        val goal = wishes.create(WishDraft("Umzug"))
        val boxes = todos.create(TodoDraft("Kisten packen", goalId = goal))
        database.wishes().setStatus(goal, WishStatus.Done, today)

        watching { outer ->
            outer.todo(boxes)
            watching { inner ->
                inner.todo(boxes)
                database.todos().setDoneAt(boxes, clock.now)
            }
            database.todos().setDoneAt(boxes, null)
        }

        assertEquals(WishStatus.Done, wishes.get(goal)?.status)
    }

    @Test
    fun `a part taking the place of another is settled as the one it replaces was`() = runBlocking {
        // Set done by hand while a part is still open, as above: one part in place of the other changes nothing.
        val goal = wishes.create(WishDraft("Umzug"))
        val boxes = todos.create(TodoDraft("Kisten packen", goalId = goal))
        val next = todos.create(TodoDraft("Kisten packen", goalId = goal))
        database.todos().setDoneAt(boxes, clock.now)
        database.wishes().setStatus(goal, WishStatus.Done, today)

        watching { watch ->
            watch.todoTakingPlaceOf(boxes, replaced = next)
            database.todos().delete(next)
            database.todos().setDoneAt(boxes, null)
        }

        assertEquals(WishStatus.Done, wishes.get(goal)?.status)
    }

    private suspend fun watching(change: suspend (GoalWatch) -> Unit) =
        database.withTransaction { database.watchingGoals(today, change) }
}
