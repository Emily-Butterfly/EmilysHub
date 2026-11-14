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
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The Vokabeltrainer (SPEC.md §15; #104): cards of a language, unique by their front; the day's workload of cards due
 * and new ones up to a limit; answers planned by FSRS-6 in calendar days, kept and logged; "Nochmal" again in the round.
 */
@RunWith(AndroidJUnit4::class)
class FlashcardsTest {

    private val database = inMemoryDatabase()

    /** Thursday, 12 November 2026, 10:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-11-12T09:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.parse("2026-11-12")
    private val languages = Languages(
        { database }, clock, LessonFiles(testAttachmentStore(), FakeLessonDocuments(), Dispatchers.Unconfined), Dispatchers.Unconfined,
    )
    private val cards = Flashcards({ database }, clock, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a card needs its front and back, within their lengths, and no other of its language reads the same`() = runBlocking {
        val arabic = language("Arabisch")
        val italian = language("Italienisch")
        card(arabic, "Buch")

        assertEquals(Saving.Refused(setOf(CardProblem.FrontTaken)), cards.create(arabic, CardDraft(" buch ", "kitab")))
        assertEquals(Saving.Refused(setOf(CardProblem.FrontMissing, CardProblem.BackMissing)), cards.create(arabic, CardDraft(" ", "")))
        assertEquals(
            setOf(CardProblem.FrontTooLong, CardProblem.BackTooLong, CardProblem.TranscriptionTooLong, CardProblem.NoteTooLong),
            CardDraft("a".repeat(201), "b".repeat(501), "c".repeat(201), "d".repeat(501)).problems,
        )
        assertTrue(cards.create(italian, CardDraft("Buch", "libro")) is Saving.Saved)
        val pen = card(arabic, "Stift")
        assertEquals(Saving.Refused(setOf(CardProblem.FrontTaken)), cards.save(pen, CardDraft("BUCH", "qalam")))
        assertEquals(Saving.Saved(pen), cards.save(pen, CardDraft("Stift", "qalam", transcription = "qalam")))
        assertEquals(CardDraft("Stift", "qalam", transcription = "qalam"), cards.draft(pen))
    }

    @Test
    fun `a card's topic is one of its language, and a topic deleted leaves its cards without one`() = runBlocking {
        val arabic = language("Arabisch")
        val italian = language("Italienisch")
        val colours = topic(arabic, "Farben")
        val numbers = topic(italian, "Zahlen")

        assertEquals(Saving.Refused(setOf(CardProblem.TopicNotOffered)), cards.create(arabic, CardDraft("rot", "ahmar", topicId = numbers)))
        val red = checkNotNull((cards.create(arabic, CardDraft("rot", "ahmar", topicId = colours)) as Saving.Saved).id)
        languages.deleteTopic(colours)

        assertNull(cards.draft(red)?.topicId)
    }

    @Test
    fun `the tab lists the cards by front, with their topic, when each is due and whether it is sure`() = runBlocking {
        val arabic = language("Arabisch")
        val colours = topic(arabic, "Farben")
        cards.create(arabic, CardDraft("rot", "ahmar", topicId = colours))
        card(arabic, "Buch")
        answer("Buch", Rating.Easy)

        val tab = checkNotNull(cards.tab(arabic).first())
        assertEquals(listOf("Buch", "rot"), tab.cards.map { it.card.front })
        assertEquals(listOf(null, "Farben"), tab.cards.map { it.topic?.name })
        assertEquals(today.plusDays(8), tab.cards.first().card.due)
        assertNull(tab.cards.last().card.due)
        assertEquals(listOf("Farben"), tab.topics.map { it.name })
        assertEquals(10, tab.newPerDay)
    }

    @Test
    fun `sure from an interval of 21 days on`() {
        assertFalse(Card(languageId = 1, front = "a", back = "b", interval = 20).sure)
        assertTrue(Card(languageId = 1, front = "a", back = "b", interval = 21).sure)
        assertFalse(Card(languageId = 1, front = "a", back = "b").sure)
    }

    @Test
    fun `the workload is every card due, the longest due first, and new ones up to the limit in the order made`() = runBlocking {
        val arabic = language("Arabisch")
        (1..12).forEach { card(arabic, "neu $it") }
        card(arabic, "alt")
        card(arabic, "älter")
        answer("alt", Rating.Good) // due in 2 days
        answer("älter", Rating.Hard) // due tomorrow
        assertEquals("two new asked today already", 8, workload(arabic).remaining.count { it.new })

        clock.now = berlin("2026-11-15T09:00")

        val due = workload(arabic, LocalDate.parse("2026-11-15"))
        assertEquals(listOf("älter", "alt") + (1..10).map { "neu $it" }, due.remaining.map { it.front })
    }

    @Test
    fun `the limit of new cards is the language's own, from 0 to 50`() = runBlocking {
        val arabic = language("Arabisch")
        (1..12).forEach { card(arabic, "neu $it") }

        cards.setNewPerDay(arabic, 3)
        assertEquals(3, workload(arabic).remaining.size)
        cards.setNewPerDay(arabic, 0)
        assertEquals(0, workload(arabic).remaining.size)
        cards.setNewPerDay(arabic, 80)
        assertEquals("kept within 50", 12, workload(arabic).remaining.size)
        assertEquals(50, cards.tab(arabic).first()?.newPerDay)
    }

    @Test
    fun `an answer plans the card, keeps what FSRS knows of it, and is logged`() = runBlocking {
        val arabic = language("Arabisch")
        val book = card(arabic, "Buch")

        cards.answer(book, Rating.Good)

        val saved = checkNotNull(database.cards().card(book))
        assertEquals(2.3065, checkNotNull(saved.stability), 0.0001)
        assertEquals(2.1181, checkNotNull(saved.difficulty), 0.0001)
        assertEquals(today, saved.firstReview)
        assertEquals(today, saved.lastReview)
        assertEquals(today.plusDays(2), saved.due)
        assertEquals(2, saved.interval)
        assertEquals(listOf(clock.now to Rating.Good.grade), database.cards().reviewsOf(book).map { it.at to it.grade })
        assertEquals(emptyList<Card>(), workload(arabic).remaining)
    }

    @Test
    fun `the intervals each answer would give, under its button`() = runBlocking {
        val arabic = language("Arabisch")
        val book = card(arabic, "Buch")
        val new = checkNotNull(database.cards().card(book))

        assertEquals(mapOf(Rating.Again to 1, Rating.Hard to 1, Rating.Good to 2, Rating.Easy to 8), cards.intervals(new, today))
    }

    @Test
    fun `days count by the phone's calendar, so that 23 00 and the next morning are a day apart`() = runBlocking {
        val arabic = language("Arabisch")
        val book = card(arabic, "Buch")
        clock.now = berlin("2026-11-12T23:00")
        cards.answer(book, Rating.Hard) // due the next day
        clock.now = berlin("2026-11-13T06:30")

        val next = LocalDate.parse("2026-11-13")
        assertEquals(listOf("Buch"), workload(arabic, next).remaining.map { it.front })
        cards.answer(book, Rating.Good)

        // As C of the research has it: Schwer, then Gut a day later.
        assertEquals(4.5460, checkNotNull(database.cards().card(book)?.stability), 0.0001)
        assertEquals(next.plusDays(5), database.cards().card(book)?.due)
    }

    @Test
    fun `Nochmal brings the card again at the end of the round, as an answer of the same day`() = runBlocking {
        val arabic = language("Arabisch")
        card(arabic, "Buch")
        card(arabic, "Stift")
        var round = Round()

        assertEquals("Buch", round.current(workload(arabic))?.front)
        assertEquals(1 to 2, round.position(workload(arabic)))
        round = answerInRound(round, Rating.Again)
        assertEquals("Stift", round.current(workload(arabic))?.front)
        assertEquals(2 to 2, round.position(workload(arabic)))
        round = answerInRound(round, Rating.Good)
        assertEquals("again at the end", "Buch", round.current(workload(arabic))?.front)
        assertEquals(2 to 2, round.position(workload(arabic)))
        round = answerInRound(round, Rating.Good)

        assertNull(round.current(workload(arabic)))
        assertEquals(2, workload(arabic).doneToday.size)
        val book = database.cards().of(arabic).single { it.front == "Buch" }
        assertEquals(listOf(Rating.Again.grade, Rating.Good.grade), database.cards().reviewsOf(book.id).map { it.grade })
    }

    @Test
    fun `left after Nochmal, the card is due the next day, and Ueben goes on where it was`() = runBlocking {
        val arabic = language("Arabisch")
        card(arabic, "Buch")
        card(arabic, "Stift")
        card(arabic, "Tisch")
        answerInRound(Round(), Rating.Again)
        // The round is left; a new one begins.
        val round = Round()

        assertEquals("Stift", round.current(workload(arabic))?.front)
        assertEquals("goes on at the second", 2 to 3, round.position(workload(arabic)))
        assertEquals(today.plusDays(1), database.cards().of(arabic).single { it.front == "Buch" }.due)
        assertEquals(1, database.cards().of(arabic).single { it.front == "Buch" }.interval)

        clock.now = berlin("2026-11-13T08:00")
        assertEquals(listOf("Buch"), workload(arabic, LocalDate.parse("2026-11-13")).remaining.filterNot { it.new }.map { it.front })
    }

    @Test
    fun `at midnight the workload of the new day begins`() = runBlocking {
        val arabic = language("Arabisch")
        cards.setNewPerDay(arabic, 1)
        card(arabic, "Buch")
        card(arabic, "Stift")
        clock.now = berlin("2026-11-12T23:59")
        cards.answer(database.cards().of(arabic).single { it.front == "Buch" }.id, Rating.Easy)
        assertEquals(emptyList<Card>(), workload(arabic).remaining)

        clock.now = berlin("2026-11-13T00:00")

        assertEquals(listOf("Stift"), workload(arabic, LocalDate.parse("2026-11-13")).remaining.map { it.front })
        assertEquals(0, workload(arabic, LocalDate.parse("2026-11-13")).doneToday.size)
    }

    @Test
    fun `the cards of an archived language stay, but are neither asked nor counted`() = runBlocking {
        val arabic = language("Arabisch")
        val italian = language("Italienisch")
        card(arabic, "Buch")
        card(italian, "libro")

        languages.setLanguageArchived(arabic, true)

        assertNull(cards.workload(arabic, today).first())
        assertEquals(setOf(italian), cards.workloads(today).first().keys)
        assertEquals(listOf("Buch"), cards.tab(arabic).first()?.cards?.map { it.card.front })
    }

    @Test
    fun `a language with cards cannot be deleted, and a card deleted takes its answers along`() = runBlocking {
        val arabic = language("Arabisch")
        val book = card(arabic, "Buch")
        cards.answer(book, Rating.Good)

        assertFalse(languages.deleteLanguage(arabic))
        cards.delete(book)

        assertEquals(emptyList<CardReview>(), database.cards().reviewsOf(book))
        assertTrue(languages.deleteLanguage(arabic))
    }

    private suspend fun answer(front: String, rating: Rating) =
        cards.answer(database.cards().of(languageOf(front)).single { it.front == front }.id, rating)

    private suspend fun languageOf(front: String) = database.cards().card(
        database.cards().observeAll().first().single { it.front == front }.id,
    )!!.languageId

    /** Answers the card the [round] shows of the only language, and returns the round after it. */
    private suspend fun answerInRound(round: Round, rating: Rating): Round {
        val language = database.languages().languages().first { !it.archived }.id
        val card = checkNotNull(round.current(workload(language)))
        cards.answer(card.id, rating)
        return round.answered(card.id, rating)
    }

    private suspend fun workload(languageId: Long, on: LocalDate = today): Workload = checkNotNull(cards.workload(languageId, on).first())

    private suspend fun language(name: String) = (languages.createLanguage(name) as Saving.Saved).id

    private suspend fun topic(languageId: Long, name: String) = (languages.createTopic(languageId, name) as Saving.Saved).id

    private suspend fun card(languageId: Long, front: String) = (cards.create(languageId, CardDraft(front, "$front?")) as Saving.Saved).id

    private fun berlin(time: String): Instant = LocalDateTime.parse(time).atZone(clock.zone).toInstant()
}
