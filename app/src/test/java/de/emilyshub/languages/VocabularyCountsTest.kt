package de.emilyshub.languages

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.Saving
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.routines.ChecklistDraftItem
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.RoutineDraft
import de.emilyshub.routines.Routines
import de.emilyshub.vault.Vault
import de.emilyshub.wishes.WishDraft
import de.emilyshub.wishes.WishProblem
import de.emilyshub.wishes.WishStatus
import de.emilyshub.wishes.Wishes
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
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * What the Vokabeltrainer counts for (SPEC.md §2, §6, §15; #106): a routine done by a language's workload once that is
 * done, and a goal's Karten-Ziel by how many cards are sure.
 */
@RunWith(AndroidJUnit4::class)
class VocabularyCountsTest {

    private val database = inMemoryDatabase()

    /** Thursday, 12 November 2026, 10:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-11-12T09:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val languages = Languages(
        { database }, clock, LessonFiles(Vault(testAttachmentStore(), FakeLessonDocuments(), Dispatchers.Unconfined), Dispatchers.Unconfined), Dispatchers.Unconfined,
    )
    private val routines = Routines({ database }, clock, Dispatchers.Unconfined)
    private val cards = Flashcards({ database }, clock, FakeLessonDocuments(), Dispatchers.Unconfined, Workloads({ database }, clock, Dispatchers.Unconfined, onDone = routines::doneByVocabulary))
    private val wishes = Wishes({ database }, clock, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `the workload done does the routine's open occurrence of today, its checklist all ticked off`() = runBlocking {
        val arabic = language("Arabisch")
        card(arabic, "kitab")
        card(arabic, "qalam")
        routine("Vokabeln", "18:00", vocabulary = arabic, checklist = listOf("Wörter", "Sätze"))

        answer(arabic, "kitab", Rating.Good)
        assertEquals("one still open", listOf(OccurrenceStatus.Open), statuses("Vokabeln"))

        answer(arabic, "qalam", Rating.Good)
        assertEquals(listOf(OccurrenceStatus.Done), statuses("Vokabeln"))
        assertEquals(listOf(true, true), today("Vokabeln").single().checklist.map { it.ticked })
    }

    @Test
    fun `a workload done at its limit does the routine once, however often told, and not one done already today`() = runBlocking {
        val arabic = language("Arabisch")
        val id = routine("Vokabeln", "12:00", "18:00", vocabulary = arabic)

        routines.doneByVocabulary(arabic)
        routines.doneByVocabulary(arabic)
        val todays = database.routines().occurrencesOn(id, routines.today()).sortedBy { it.time }
        assertEquals(listOf(OccurrenceStatus.Done, OccurrenceStatus.Open), todays.map { it.status })
    }

    @Test
    fun `a card answered Nochmal is due tomorrow, so the workload counts as done, as the start page of Sprachen has it`() = runBlocking {
        val arabic = language("Arabisch")
        card(arabic, "kitab")
        routine("Vokabeln", "18:00", vocabulary = arabic)

        answer(arabic, "kitab", Rating.Again)
        assertEquals(listOf(OccurrenceStatus.Done), statuses("Vokabeln"))
    }

    @Test
    fun `only the answer that does the workload does the routine, not those asked again after it`() = runBlocking {
        val arabic = language("Arabisch")
        card(arabic, "kitab")
        card(arabic, "qalam")
        routine("Vokabeln", "14:00", "18:00", vocabulary = arabic)

        answer(arabic, "kitab", Rating.Again)
        answer(arabic, "qalam", Rating.Good)
        assertEquals(listOf(OccurrenceStatus.Done, OccurrenceStatus.Open), statuses("Vokabeln"))
        routines.reopen(today("Vokabeln").first().occurrenceId)

        // "Nochmal" brings kitab again at the end of the round.
        answer(arabic, "kitab", Rating.Good)

        assertEquals("taken back stays so", listOf(OccurrenceStatus.Open, OccurrenceStatus.Open), statuses("Vokabeln"))
    }

    @Test
    fun `practising a topic on a day with nothing due and nothing new leaves the routine open`() = runBlocking {
        val arabic = language("Arabisch")
        val colours = (languages.createTopic(arabic, "Farben") as Saving.Saved).id
        val red = (cards.create(arabic, CardDraft("rot", "ahmar", topicId = colours)) as Saving.Saved).id
        cards.answer(red, Rating.Easy) // due in 8 days
        clock.now = clock.now.plus(1, ChronoUnit.DAYS)
        routine("Vokabeln", "18:00", vocabulary = arabic)

        cards.answer(red, Rating.Good, countsAsNew = false)

        assertEquals(listOf(OccurrenceStatus.Open), statuses("Vokabeln"))
    }

    @Test
    fun `a day with nothing due and nothing new leaves the routine open, and other routines are not touched`() = runBlocking {
        val arabic = language("Arabisch")
        card(arabic, "kitab")
        routine("Vokabeln", "18:00", vocabulary = arabic)
        routine("Joggen", "18:00")
        answer(arabic, "kitab", Rating.Easy) // due in 8 days

        clock.now = clock.now.plus(1, ChronoUnit.DAYS)

        assertEquals(listOf(OccurrenceStatus.Open), statuses("Vokabeln"))
        assertEquals(listOf(OccurrenceStatus.Open), statuses("Joggen"))
    }

    @Test
    fun `of several occurrences today the earliest open one is done, what was skipped staying so`() = runBlocking {
        val arabic = language("Arabisch")
        card(arabic, "kitab")
        card(arabic, "qalam")
        routine("Vokabeln", "11:00", "14:00", "18:00", vocabulary = arabic)
        routines.skip(today("Vokabeln").first().occurrenceId)

        answer(arabic, "kitab", Rating.Good)
        answer(arabic, "qalam", Rating.Good)

        assertEquals(listOf(OccurrenceStatus.Skipped, OccurrenceStatus.Done, OccurrenceStatus.Open), statuses("Vokabeln"))
    }

    @Test
    fun `done so, it can be opened again, and a paused routine is not done`() = runBlocking {
        val arabic = language("Arabisch")
        card(arabic, "kitab")
        card(arabic, "qalam")
        routine("Vokabeln", "18:00", vocabulary = arabic)
        val paused = routine("Lesen", "18:00", vocabulary = arabic)
        routines.pause(paused)

        answer(arabic, "kitab", Rating.Good)
        answer(arabic, "qalam", Rating.Good)
        routines.reopen(today("Vokabeln").single().occurrenceId)

        assertEquals(listOf(OccurrenceStatus.Open), statuses("Vokabeln"))
        assertEquals(emptyList<OccurrenceStatus>(), statuses("Lesen"))
    }

    @Test
    fun `done by the cards, it does its coupled routines as being done by hand does`() = runBlocking {
        val arabic = language("Arabisch")
        card(arabic, "kitab")
        val reading = routine("Lesen", "18:00")
        routine("Vokabeln", "18:00", vocabulary = arabic, coupledWith = setOf(reading))

        answer(arabic, "kitab", Rating.Good)

        assertEquals(listOf(OccurrenceStatus.Done), statuses("Vokabeln"))
        assertEquals(listOf(OccurrenceStatus.Skipped), statuses("Lesen"))
    }

    @Test
    fun `a language archived makes its routines ordinary ones again`() = runBlocking {
        val arabic = language("Arabisch")
        val vocabulary = routine("Vokabeln", "18:00", vocabulary = arabic)

        languages.setLanguageArchived(arabic, true)

        assertNull(database.routines().get(vocabulary)?.vocabularyLanguageId)
        assertNull(routines.editing(vocabulary)?.original?.vocabularyLanguageId)
    }

    @Test
    fun `a Karten-Ziel counts the share of sure cards, is done once reached, and opens its goal again below it`() = runBlocking {
        val arabic = language("Arabisch")
        card(arabic, "kitab")
        card(arabic, "qalam")
        val goal = wishes.create(WishDraft("Wortschatz", cardLanguageId = arabic, cardCount = "2"))
        assertEquals(0.0, progress(goal), 0.0)

        makeSure(arabic, "kitab")
        assertEquals(0.5, progress(goal), 0.0)
        assertEquals(WishStatus.Open, wishes.get(goal)?.status)
        makeSure(arabic, "qalam")
        assertEquals(1.0, progress(goal), 0.0)
        assertEquals(WishStatus.Done, wishes.get(goal)?.status)

        answer(arabic, "qalam", Rating.Again)
        assertEquals("sure no more", WishStatus.InProgress, wishes.get(goal)?.status)

        makeSure(arabic, "qalam")
        assertEquals(WishStatus.Done, wishes.get(goal)?.status)
        cards.delete(database.cards().of(arabic).single { it.front == "kitab" }.id)
        assertEquals("a card deleted", WishStatus.InProgress, wishes.get(goal)?.status)
    }

    @Test
    fun `an answer taken back that made a card sure takes it from a Karten-Ziel reached`() = runBlocking {
        val arabic = language("Arabisch")
        card(arabic, "kitab")
        val goal = wishes.create(WishDraft("Wortschatz", cardLanguageId = arabic, cardCount = "1"))
        repeat(4) {
            val card = database.cards().of(arabic).single()
            card.due?.let { due -> clock.now = due.atTime(LocalTime.of(10, 0)).atZone(clock.zone).toInstant() }
            val answered = checkNotNull(cards.answer(card.id, Rating.Easy))
            if (database.cards().of(arabic).single().sure) {
                assertEquals(WishStatus.Done, wishes.get(goal)?.status)
                cards.takeBack(answered)
                assertEquals("sure no more", WishStatus.InProgress, wishes.get(goal)?.status)
                return@runBlocking
            }
        }
        error("never sure")
    }

    @Test
    fun `an answer taken back that did the workload leaves the routine it did done`() = runBlocking {
        val arabic = language("Arabisch")
        val book = card(arabic, "kitab")
        routine("Vokabeln", "18:00", vocabulary = arabic)
        val answered = checkNotNull(cards.answer(book, Rating.Good))
        assertEquals(listOf(OccurrenceStatus.Done), statuses("Vokabeln"))

        cards.takeBack(answered)

        assertEquals(listOf(OccurrenceStatus.Done), statuses("Vokabeln"))
    }

    @Test
    fun `given again after it was taken back, the answer that did the workload does not do the routine's next time`() = runBlocking {
        val arabic = language("Arabisch")
        val book = card(arabic, "kitab")
        routine("Vokabeln", "12:00", "18:00", vocabulary = arabic)
        val answered = checkNotNull(cards.answer(book, Rating.Good))
        assertTrue(answered.didWorkload)
        cards.takeBack(answered)

        cards.answer(book, Rating.Good)

        assertEquals(listOf(OccurrenceStatus.Done, OccurrenceStatus.Open), statuses("Vokabeln"))
    }

    @Test
    fun `the Tagespensum geschafft twice in a day, its limit raised between, does the routine once`() = runBlocking {
        val arabic = language("Arabisch")
        card(arabic, "kitab")
        card(arabic, "qalam")
        routine("Vokabeln", "12:00", "18:00", vocabulary = arabic)
        database.languages().setDailyLimit(arabic, 1)

        answer(arabic, "kitab", Rating.Good)
        assertEquals(listOf(OccurrenceStatus.Done, OccurrenceStatus.Open), statuses("Vokabeln"))

        database.languages().setDailyLimit(arabic, null)
        answer(arabic, "qalam", Rating.Good)

        assertEquals(listOf(OccurrenceStatus.Done, OccurrenceStatus.Open), statuses("Vokabeln"))
    }

    @Test
    fun `ticking a sure card both ways takes it from a Karten-Ziel reached, and ticking it off gives it back`() = runBlocking {
        val arabic = language("Arabisch")
        val book = card(arabic, "kitab")
        val goal = wishes.create(WishDraft("Wortschatz", cardLanguageId = arabic, cardCount = "1"))
        makeSure(arabic, "kitab")
        assertEquals(WishStatus.Done, wishes.get(goal)?.status)

        cards.save(book, CardDraft("kitab", "kitab?", bothWays = true))
        assertEquals("its back is new", WishStatus.InProgress, wishes.get(goal)?.status)
        cards.save(book, CardDraft("kitab", "kitab?", bothWays = false))
        assertEquals(WishStatus.Done, wishes.get(goal)?.status)
    }

    @Test
    fun `a Karten-Ziel's number is 1 to 100 000, one a goal, kept in its editor, and gone with its goal, the cards staying`() = runBlocking {
        val arabic = language("Arabisch")
        card(arabic, "kitab")
        assertEquals(setOf(WishProblem.CardCountOutOfRange), WishDraft("Wortschatz", cardLanguageId = arabic, cardCount = "0").problems)
        assertEquals(emptySet<Any>(), WishDraft("Wortschatz", cardLanguageId = arabic, cardCount = "100000").problems)
        val goal = wishes.create(WishDraft("Wortschatz", cardLanguageId = arabic, cardCount = "1000"))
        assertEquals(arabic to "1000", wishes.editing(goal)?.original?.let { it.cardLanguageId to it.cardCount })
        val tree = checkNotNull(wishes.goal(goal).first())
        assertEquals(Triple("Arabisch", 0, 1000), tree.cards?.let { Triple(it.language, it.sure, it.count) })

        wishes.delete(goal)

        assertNull(database.cards().goal(goal))
        assertEquals(1, database.cards().of(arabic).size)
    }

    /** Asks the card [front] of the language [languageId] until it is sure, the days going by as it is due. */
    private suspend fun makeSure(languageId: Long, front: String) {
        repeat(5) {
            val card = database.cards().of(languageId).single { it.front == front }
            if (card.sure) return
            card.due?.let { due -> clock.now = due.atTime(LocalTime.of(10, 0)).atZone(clock.zone).toInstant() }
            cards.answer(card.id, Rating.Easy)
        }
    }

    private suspend fun progress(goal: Long) = checkNotNull(wishes.goal(goal).first()).progress

    private suspend fun answer(languageId: Long, front: String, rating: Rating) =
        cards.answer(database.cards().of(languageId).single { it.front == front }.id, rating)

    private suspend fun today(name: String) = routines.dueOn(routines.today()).first().filter { it.name == name }

    private suspend fun statuses(name: String) = today(name).map { it.status }

    private suspend fun language(name: String) = (languages.createLanguage(name) as Saving.Saved).id

    private suspend fun card(languageId: Long, front: String) = (cards.create(languageId, CardDraft(front, "$front?")) as Saving.Saved).id

    private suspend fun routine(
        name: String,
        vararg times: String,
        vocabulary: Long? = null,
        checklist: List<String> = emptyList(),
        coupledWith: Set<Long> = emptySet(),
    ): Long = routines.create(
        RoutineDraft(
            name, description = "", times.map(LocalTime::parse), Repetition.Daily,
            checklist = checklist.map { ChecklistDraftItem(text = it) }, coupledWith = coupledWith,
            vocabularyLanguageId = vocabulary,
        ),
    )
}
