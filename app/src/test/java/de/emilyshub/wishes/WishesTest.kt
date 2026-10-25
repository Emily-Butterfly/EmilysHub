package de.emilyshub.wishes

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.tags.Tags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The wishlist (SPEC.md §6): things to do some day, open, in progress or done on a day, and never reminded of. */
@RunWith(AndroidJUnit4::class)
class WishesTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.parse("2026-10-23")
    private val wishes = Wishes({ database }, clock, Dispatchers.Unconfined)
    private val tags = Tags({ database }, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a wish is saved with its title, description, period and tags, as typed, and starts out open`() = runBlocking {
        val travel = tags.create("Reisen")

        val id = wishes.create(
            WishDraft(title = " Nordlichter sehen ", description = "In Tromsø ", period = " Winter  2027 ", tagIds = setOf(travel)),
        )

        val saved = checkNotNull(wishes.get(id))
        assertEquals("Nordlichter sehen", saved.title)
        assertEquals("In Tromsø", saved.description)
        assertEquals("Winter 2027", saved.period)
        assertEquals(WishStatus.Open, saved.status)
        assertNull(saved.doneOn)
        assertEquals(setOf(travel), wishes.tagIdsOf(id))
    }

    @Test
    fun `a wish needs a title of up to 80 characters, and keeps to the limits of description and period`() {
        assertEquals(setOf(WishProblem.TitleMissing), WishDraft(" ").problems)
        assertEquals(setOf(WishProblem.TitleTooLong), WishDraft("a".repeat(81)).problems)
        assertEquals(emptySet<WishProblem>(), WishDraft("a".repeat(80), period = "b".repeat(40)).problems)
        assertEquals(setOf(WishProblem.DescriptionTooLong), WishDraft("Reise", description = "a".repeat(1001)).problems)
        assertEquals(setOf(WishProblem.PeriodTooLong), WishDraft("Reise", period = "a".repeat(41)).problems)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { wishes.create(WishDraft(" ")) } }
    }

    @Test
    fun `the wishlist shows those in progress first, then the open ones, each in alphabetical order, and none done`() = runBlocking {
        wishes.create(WishDraft("Zelten am See"))
        wishes.create(WishDraft("Arabisch lernen", status = WishStatus.InProgress))
        wishes.create(WishDraft("Ölgemälde malen"))
        wishes.create(WishDraft("Bergtour", status = WishStatus.InProgress))
        wishes.create(WishDraft("Marathon laufen", status = WishStatus.Done))

        val listed = wishes.current().first().map { it.wish.title to it.wish.status }

        assertEquals(
            listOf(
                "Arabisch lernen" to WishStatus.InProgress,
                "Bergtour" to WishStatus.InProgress,
                "Ölgemälde malen" to WishStatus.Open,
                "Zelten am See" to WishStatus.Open,
            ),
            listed,
        )
    }

    @Test
    fun `a wish done is done today unless another day is given, and the day goes once it is open again`() = runBlocking {
        val id = wishes.create(WishDraft("Bergtour"))

        wishes.update(id, WishDraft("Bergtour", status = WishStatus.Done))
        assertEquals(today, wishes.get(id)?.doneOn)

        wishes.update(id, WishDraft("Bergtour", status = WishStatus.Done, doneOn = LocalDate.parse("2026-09-12")))
        assertEquals(LocalDate.parse("2026-09-12"), wishes.get(id)?.doneOn)

        wishes.update(id, WishDraft("Bergtour", status = WishStatus.InProgress, doneOn = LocalDate.parse("2026-09-12")))
        assertNull(wishes.get(id)?.doneOn)
    }

    @Test
    fun `a wish cannot have come true on a day still ahead, which counts as today`() = runBlocking {
        // As after a move west, or in a backup from further east.
        val id = wishes.create(WishDraft("Bergtour", status = WishStatus.Done, doneOn = today.plusDays(1)))

        assertEquals(today, wishes.get(id)?.doneOn)
    }

    @Test
    fun `the review lists the wishes come true, the last first, with the day each came true`() = runBlocking {
        wishes.create(WishDraft("Bergtour", status = WishStatus.Done, doneOn = LocalDate.parse("2026-08-01")))
        wishes.create(WishDraft("Marathon laufen", status = WishStatus.Done, doneOn = LocalDate.parse("2026-10-03")))
        wishes.create(WishDraft("Arabisch lernen", status = WishStatus.InProgress))

        val review = wishes.fulfilled().first().map { it.title to it.doneOn }

        assertEquals(
            listOf("Marathon laufen" to LocalDate.parse("2026-10-03"), "Bergtour" to LocalDate.parse("2026-08-01")),
            review,
        )
    }

    @Test
    fun `a tag deleted is taken off the wishes, and a wish deleted goes with its tags`() = runBlocking {
        val travel = tags.create("Reisen")
        val sport = tags.create("Sport")
        val trip = wishes.create(WishDraft("Nordlichter sehen", tagIds = setOf(travel, sport)))

        tags.delete(travel)
        assertEquals(setOf(sport), wishes.tagIdsOf(trip))

        wishes.delete(trip)
        assertNull(wishes.get(trip))
        assertEquals(emptyList<WishWithTags>(), wishes.current().first())
    }

    @Test
    fun `a tag deleted meanwhile is left out on saving`() = runBlocking {
        val travel = tags.create("Reisen")
        tags.delete(travel)

        val id = wishes.create(WishDraft("Nordlichter sehen", tagIds = setOf(travel)))

        assertEquals(emptySet<Long>(), wishes.tagIdsOf(id))
    }
}
