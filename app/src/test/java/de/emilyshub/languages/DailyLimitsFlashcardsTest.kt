package de.emilyshub.languages

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.Saving
import de.emilyshub.data.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The limits of a day's workload as the Vokabeltrainer keeps them (SPEC.md §15; #156): a language's own and its share of
 * the overall one, which stays the same while it is practised; the workload done at the limit, which tells the routine
 * done by the cards; a topic practised without a limit; an archived language counting nowhere.
 */
@RunWith(AndroidJUnit4::class)
class DailyLimitsFlashcardsTest {

    private val database = inMemoryDatabase()

    /** Thursday, 12 November 2026, 10:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-11-12T09:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val languages = Languages(
        { database }, clock, LessonFiles(testAttachmentStore(), FakeLessonDocuments(), Dispatchers.Unconfined), Dispatchers.Unconfined,
    )
    private val done = mutableListOf<Long>()
    private val cards = Flashcards({ database }, clock, FakeLessonDocuments(), Dispatchers.Unconfined, onWorkloadDone = { done += it })

    private fun today(): LocalDate = LocalDate.now(clock)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a language's own limit takes the longest due first, the rest waits, and reached it tells the workload done`() = runBlocking {
        val arabic = language("Arabisch")
        dueCards(arabic, 5)
        cards.create(arabic, CardDraft("neu", "new"))
        cards.setDailyLimit(arabic, 3)
        // Below what a limit may be, it is the least.
        assertEquals(20, run { cards.setDailyLimit(arabic, 5); cards.tab(arabic).first()?.dailyLimit })
        cards.setDailyLimit(arabic, null)
        assertEquals(6, workload(arabic).remaining.size)

        database.languages().setDailyLimit(arabic, 3)
        val workload = workload(arabic)
        assertEquals(3, workload.remaining.size)
        assertEquals(2, workload.waiting)
        assertTrue("no new cards while a backlog waits", workload.remaining.none { it.new })

        repeat(3) { answerNext(arabic, Rating.Good) }
        assertTrue(workload(arabic).done)
        assertEquals(2, workload(arabic).waiting)
        assertEquals(listOf(arabic), done)
    }

    @Test
    fun `"Nochmal" counts once against the limit, and the card comes again in the round as before`() = runBlocking {
        val arabic = language("Arabisch")
        dueCards(arabic, 4)
        database.languages().setDailyLimit(arabic, 2)

        answerNext(arabic, Rating.Again)
        assertEquals(1, workload(arabic).remaining.size)
        answerNext(arabic, Rating.Good)
        assertTrue(workload(arabic).done)
    }

    @Test
    fun `the overall limit is shared by backlog, and the shares stay the same while practising`() = runBlocking {
        val arabic = language("Arabisch")
        val italian = language("Italienisch")
        dueCards(arabic, 30, prefix = "a")
        dueCards(italian, 10, prefix = "i")
        cards.setOverallLimit(20)

        assertEquals(15, workload(arabic).remaining.size)
        assertEquals(5, workload(italian).remaining.size)
        repeat(4) { answerNext(arabic, Rating.Good) }
        assertEquals("the share stays 15", 11, workload(arabic).remaining.size)
        assertEquals(5, workload(italian).remaining.size)

        // The smaller of its own and its share.
        cards.setDailyLimit(italian, 20)
        assertEquals(5, workload(italian).remaining.size)
        assertEquals(20, cards.overallLimit().first())
        cards.setOverallLimit(null)
        assertNull(cards.overallLimit().first())
        assertEquals(10, workload(italian).remaining.size)
    }

    @Test
    fun `a topic practised knows no limit and does not count against it`() = runBlocking {
        val arabic = language("Arabisch")
        val topic = (languages.createTopic(arabic, "Verben") as Saving.Saved).id
        dueCards(arabic, 4, topicId = topic)
        database.languages().setDailyLimit(arabic, 2)

        val practice = checkNotNull(cards.practice(CardSelection.OfTopic(topic), today()).first())
        assertEquals(4, practice.size)
        cards.answer(practice.first().id, Rating.Good, countsAsNew = false)

        val workload = workload(arabic)
        assertEquals(2, workload.remaining.size)
        assertEquals(1, workload.waiting)
    }

    @Test
    fun `an archived language counts nowhere, neither has a workload nor takes a share`() = runBlocking {
        val arabic = language("Arabisch")
        val italian = language("Italienisch")
        dueCards(arabic, 10, prefix = "a")
        dueCards(italian, 10, prefix = "i")
        cards.setOverallLimit(20)
        languages.setLanguageArchived(arabic, archived = true)

        assertNull(cards.workload(arabic, today()).first())
        assertEquals(10, workload(italian).remaining.size)
        assertFalse(arabic in cards.workloads(today()).first())
    }

    private suspend fun workload(languageId: Long): Workload = checkNotNull(cards.workload(languageId, today()).first())

    /** Answers the next card of the language's workload [rating], as the practice does. */
    private suspend fun answerNext(languageId: Long, rating: Rating) {
        val next = workload(languageId).remaining.first()
        cards.answer(next.id, rating, reverse = next.reverse)
    }

    /** [count] cards of [languageId], answered a few days ago and due today. */
    private suspend fun dueCards(languageId: Long, count: Int, prefix: String = "k", topicId: Long? = null) {
        val start = clock.now
        clock.now = start.minus(10, ChronoUnit.DAYS)
        repeat(count) {
            val id = (cards.create(languageId, CardDraft("$prefix$it", "$prefix$it?", topicId = topicId)) as Saving.Saved).id
            cards.answer(id, Rating.Good)
        }
        clock.now = start
        // What those answers did then is not what the test is about.
        done.clear()
    }

    private suspend fun language(name: String) = (languages.createLanguage(name) as Saving.Saved).id
}
