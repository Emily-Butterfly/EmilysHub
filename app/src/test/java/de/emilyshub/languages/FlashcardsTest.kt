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
import java.time.temporal.ChronoUnit

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
    private val documents = FakeLessonDocuments()
    private val cards = Flashcards({ database }, clock, documents, Dispatchers.Unconfined)

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
        assertEquals(CardDraft("Stift", "qalam", transcription = "qalam"), cards.card(pen)?.let(CardDraft::of))
    }

    @Test
    fun `while a front is typed, the editor learns whether another card of the language has it`() = runBlocking {
        val arabic = language("Arabisch")
        val italian = language("Italienisch")
        val book = card(arabic, "Buch")

        assertTrue(cards.frontTaken(arabic, "  BUCH ", except = null))
        assertFalse("the card itself, being changed", cards.frontTaken(arabic, "Buch", except = book))
        assertFalse("another language", cards.frontTaken(italian, "Buch", except = null))
        assertFalse("nothing typed", cards.frontTaken(arabic, " ", except = null))
        assertFalse(cards.frontTaken(arabic, "Bücher", except = null))
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

        assertNull(cards.card(red)?.topicId)
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

        assertEquals(mapOf(Rating.Again to 1, Rating.Hard to 1, Rating.Good to 2, Rating.Easy to 8), cards.intervals(Prompt(new)))
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

    @Test
    fun `of cards to make, those whose front the language has, or an earlier one of them, are taken`() = runBlocking {
        val arabic = language("Arabisch")
        card(arabic, "Buch")

        val taken = cards.taken(arabic, listOf(CardDraft("buch", "x"), CardDraft("Stift", "y"), CardDraft("STIFT", "z"), CardDraft("Tür", "w")))

        assertEquals(listOf(true, false, true, false), taken)
    }

    @Test
    fun `cards made at once are new, with the topic chosen, and those taken are counted as skipped`() = runBlocking {
        val arabic = language("Arabisch")
        val colours = topic(arabic, "Farben")
        card(arabic, "rot")

        val made = cards.createAll(arabic, listOf(CardDraft("rot", "ahmar"), CardDraft("blau", "azraq"), CardDraft("grün", "akhdar")), colours)

        assertEquals(CardsMade(created = 2, skipped = 1), made)
        val tab = checkNotNull(cards.tab(arabic).first())
        assertEquals(listOf("blau", "grün", "rot"), tab.cards.map { it.card.front })
        assertEquals(listOf("Farben", "Farben", null), tab.cards.map { it.topic?.name })
        assertTrue(tab.cards.take(2).all { it.card.new })
        assertEquals("they come with the workload", listOf("rot", "blau", "grün"), workload(arabic).remaining.map { it.front })
    }

    @Test
    fun `made at once, all or nothing, and a topic not of the language leaves them without one`() = runBlocking {
        val arabic = language("Arabisch")
        val italian = language("Italienisch")
        val numbers = topic(italian, "Zahlen")

        assertEquals(CardsMade(created = 1, skipped = 0), cards.createAll(arabic, listOf(CardDraft("eins", "wahid")), numbers))
        assertNull(database.cards().of(arabic).single().topicId)
        assertEquals(
            "one that cannot be saved makes none",
            CardsMade(created = 0, skipped = 0),
            cards.createAll(arabic, listOf(CardDraft("zwei", "ithnan"), CardDraft("", "leer")), topicId = null),
        )
        assertEquals(listOf("eins"), database.cards().of(arabic).map { it.front })
    }

    @Test
    fun `a file picked is read as cards, and kept nowhere`() = runBlocking {
        val address = documents.put("vokabeln.txt", "kitab;Buch\nqalam;Stift".toByteArray())

        val read = cards.readFile(address) as CardFile.Read

        assertEquals(listOf("kitab", "qalam"), read.cards.map { it.draft.front })
        assertEquals(CardFile.NotText, cards.readFile("content://test/missing"))
        assertEquals(CardFile.TooLarge, cards.readFile(documents.put("gross.txt", ByteArray(CardImport.FILE_MAX_BYTES + 1) { 'a'.code.toByte() })))
        assertEquals(emptyMap<String, ByteArray>(), documents.written)
    }

    @Test
    fun `each topic counts its cards and those sure, and an exam the sum of its topics`() = runBlocking {
        val arabic = language("Arabisch")
        val exam = (languages.createExam(arabic, ExamDraft("A2", LocalDate.parse("2026-12-20"))) as Saving.Saved).id
        val colours = (languages.createTopic(arabic, "Farben", exam) as Saving.Saved).id
        val numbers = (languages.createTopic(arabic, "Zahlen", exam) as Saving.Saved).id
        topic(arabic, "Leer")
        cards.create(arabic, CardDraft("rot", "ahmar", topicId = colours))
        cards.create(arabic, CardDraft("blau", "azraq", topicId = colours))
        cards.create(arabic, CardDraft("eins", "wahid", topicId = numbers))
        card(arabic, "ohne")
        makeSure("rot")

        val counts = cards.topicCounts(arabic).first()

        assertEquals(mapOf(colours to TopicCards(2, 1), numbers to TopicCards(1, 0)), counts)
        assertEquals(TopicCards(3, 1), TopicCards.sumOf(listOf(colours, numbers), counts))
        assertEquals(TopicCards(0, 0), TopicCards.sumOf(emptyList(), counts))
    }

    @Test
    fun `a topic is practised whole, those due first, then the new, then those due soonest, each once in the round`() = runBlocking {
        val arabic = language("Arabisch")
        val colours = topic(arabic, "Farben")
        listOf("rot", "blau", "grün", "gelb").forEach { cards.create(arabic, CardDraft(it, "$it?", topicId = colours)) }
        card(arabic, "anders")
        answer("rot", Rating.Easy) // due in 8 days
        answer("blau", Rating.Good) // due in 2 days
        answer("grün", Rating.Hard) // due tomorrow
        clock.now = clock.now.plus(1, ChronoUnit.DAYS)
        val today = LocalDate.parse("2026-11-13")

        val ordered = cards.topicPractice(colours, today).first().orEmpty()
        assertEquals(listOf("grün", "gelb", "blau", "rot"), ordered.map { it.front })

        var round = Round()
        val seen = mutableListOf<String>()
        while (true) {
            val workload = Workload.ofTopic(cards.topicPractice(colours, today).first().orEmpty(), round, today)
            val card = round.current(workload) ?: break
            seen += card.front
            cards.answer(card.id, Rating.Good, countsAsNew = false)
            round = round.answered(card, Rating.Good)
        }
        assertEquals("each once, also those not due", listOf("grün", "gelb", "blau", "rot"), seen)
    }

    @Test
    fun `a card asked early grows little, and a new one practised in its topic does not count against the new cards of the day`() =
        runBlocking {
            val arabic = language("Arabisch")
            val colours = topic(arabic, "Farben")
            cards.setNewPerDay(arabic, 1)
            cards.create(arabic, CardDraft("rot", "ahmar", topicId = colours))
            card(arabic, "neu")
            answer("rot", Rating.Good) // due in 2 days, through the daily workload, so it counts
            assertEquals(emptyList<Card>(), workload(arabic).remaining)

            // As D of the research has it: Gut, then Gut one day later, early.
            clock.now = clock.now.plus(1, ChronoUnit.DAYS)
            cards.create(arabic, CardDraft("blau", "azraq", topicId = colours))
            cards.answer(database.cards().of(arabic).single { it.front == "blau" }.id, Rating.Good, countsAsNew = false)
            val red = database.cards().of(arabic).single { it.front == "rot" }
            cards.answer(red.id, Rating.Good, countsAsNew = false)

            // Asked a day early, as FSRS-6 reckons it: less than on the day it was due.
            val early = Fsrs.review(red.memory, 1, Rating.Good).memory.stability
            assertEquals(early, checkNotNull(database.cards().card(red.id)?.stability), 0.0001)
            assertTrue(early < Fsrs.review(red.memory, 2, Rating.Good).memory.stability)
            assertEquals("blau counts not, so neu is still the one new today", listOf("neu"), workload(arabic, LocalDate.parse("2026-11-13")).remaining.map { it.front })
        }

    @Test
    fun `a new card first asked in its topic never counts against the new cards of a day, also once due`() = runBlocking {
        val arabic = language("Arabisch")
        val colours = topic(arabic, "Farben")
        cards.setNewPerDay(arabic, 1)
        cards.create(arabic, CardDraft("rot", "ahmar", topicId = colours))
        card(arabic, "neu 1")
        card(arabic, "neu 2")
        val red = database.cards().of(arabic).single { it.front == "rot" }.id
        cards.answer(red, Rating.Hard, countsAsNew = false) // due tomorrow
        clock.now = clock.now.plus(1, ChronoUnit.DAYS)
        val tomorrow = LocalDate.parse("2026-11-13")
        cards.answer(red, Rating.Good) // in the day's workload now

        assertEquals("still one new card that day", listOf("neu 1"), workload(arabic, tomorrow).remaining.map { it.front })
    }

    @Test
    fun `a topic practised goes on where it was, those asked today counting as asked`() = runBlocking {
        val arabic = language("Arabisch")
        val colours = topic(arabic, "Farben")
        listOf("rot", "blau", "grün").forEach { cards.create(arabic, CardDraft(it, "$it?", topicId = colours)) }
        val first = checkNotNull(cards.topicPractice(colours, today).first()).first()
        cards.answer(first.id, Rating.Good, countsAsNew = false)

        // Left, and taken up again.
        val workload = Workload.ofTopic(cards.topicPractice(colours, today).first().orEmpty(), Round(), today)

        assertEquals(listOf("blau", "grün"), workload.remaining.map { it.front })
        assertEquals(2 to 3, Round().position(workload))
    }

    @Test
    fun `practising a topic only once, the day's practice is the first no more`() = runBlocking {
        val arabic = language("Arabisch")
        val colours = topic(arabic, "Farben")
        cards.create(arabic, CardDraft("rot", "ahmar", topicId = colours))
        card(arabic, "neu")
        cards.answer(database.cards().of(arabic).single { it.front == "rot" }.id, Rating.Good, countsAsNew = false)
        clock.now = clock.now.plus(1, ChronoUnit.DAYS)

        assertFalse(workload(arabic, LocalDate.parse("2026-11-13")).firstTime)
    }

    @Test
    fun `the cards of the tab are searched in all their fields, Arabic with or without vowel marks`() {
        val row = CardRow(Card(languageId = 1, front = "كِتَاب", back = "Buch", transcription = "kitāb", note = "Plural kutub"), topic = null)

        listOf("كتاب", "buch", "KITĀB", "kutub", "plural kut", "").forEach { assertTrue(it, row.matches(it)) }
        listOf("Stift", "buch stift").forEach { assertFalse(it, row.matches(it)) }
    }

    @Test
    fun `the evaluation counts the language's cards, those sure, and those due today`() = runBlocking {
        val arabic = language("Arabisch")
        card(arabic, "kitab")
        card(arabic, "qalam")
        card(arabic, "bab")
        makeSure("kitab")
        val now = clock.now.atZone(clock.zone).toLocalDate()

        assertEquals("the two new ones due", CardsSummary(total = 3, sure = 1, dueToday = 2), cards.summary(arabic, now).first())
    }

    @Test
    fun `a card asked both ways comes from its front first and from its back the next day, never both on one day`() = runBlocking {
        val arabic = language("Arabisch")
        val book = both(arabic, "Buch")

        assertEquals(listOf(book to false), workload(arabic).remaining.map { it.id to it.reverse })
        cards.answer(book, Rating.Good) // due in 2 days from its front
        assertEquals("not from its back the same day", emptyList<Prompt>(), workload(arabic).remaining)

        clock.now = berlin("2026-11-13T10:00")
        val tomorrow = LocalDate.parse("2026-11-13")
        assertEquals(listOf(book to true), workload(arabic, tomorrow).remaining.map { it.id to it.reverse })
        cards.answer(book, Rating.Good, reverse = true) // due in 2 days from its back

        val saved = checkNotNull(database.cards().card(book))
        assertEquals(LocalDate.parse("2026-11-14"), saved.due)
        assertEquals("planned on its own", LocalDate.parse("2026-11-15"), saved.reverseDue)
        assertEquals(tomorrow, saved.reverseFirstReview)
        assertEquals(listOf(false, true), database.cards().reviewsOf(book).map { it.reverse })
        assertEquals(LocalDate.parse("2026-11-14"), cards.tab(arabic).first()!!.cards.single().card.nextDue)
    }

    @Test
    fun `the back of a card counts as a new card of the day, and of two due on one day only one comes`() = runBlocking {
        val arabic = language("Arabisch")
        cards.setNewPerDay(arabic, 1)
        val book = both(arabic, "Buch")
        card(arabic, "Stift")
        cards.answer(book, Rating.Hard) // due tomorrow from its front

        clock.now = berlin("2026-11-13T10:00")
        val tomorrow = LocalDate.parse("2026-11-13")
        // Due from its front, so its back waits; the one new card of the day is Stift.
        assertEquals(listOf("Buch" to false, "Stift" to false), workload(arabic, tomorrow).remaining.map { it.front to it.reverse })
        cards.answer(book, Rating.Hard) // due the next day again

        clock.now = berlin("2026-11-14T10:00")
        val third = LocalDate.parse("2026-11-14")
        // Not due from its front; its back new, taking the one new card of the day before Stift.
        assertEquals(listOf("Buch" to true), workload(arabic, third).remaining.map { it.front to it.reverse })
        cards.answer(book, Rating.Hard, reverse = true)

        // Due both ways on one day: from its front only, its back the next day.
        val saved = checkNotNull(database.cards().card(book))
        database.cards().update(saved.copy(due = LocalDate.parse("2026-11-20"), reverseDue = LocalDate.parse("2026-11-20")))
        clock.now = berlin("2026-11-20T10:00")
        val later = LocalDate.parse("2026-11-20")
        assertEquals(listOf("Buch" to false, "Stift" to false), workload(arabic, later).remaining.map { it.front to it.reverse })
    }

    @Test
    fun `a topic practised asks a card one way a day`() = runBlocking {
        val arabic = language("Arabisch")
        val colours = topic(arabic, "Farben")
        cards.create(arabic, CardDraft("rot", "ahmar", topicId = colours, bothWays = true))

        var round = Round()
        val seen = mutableListOf<Pair<String, Boolean>>()
        while (true) {
            val workload = Workload.ofTopic(cards.topicPractice(colours, today).first().orEmpty(), round, today)
            val prompt = round.current(workload) ?: break
            seen += prompt.question to prompt.reverse
            cards.answer(prompt.id, Rating.Good, countsAsNew = false, reverse = prompt.reverse)
            round = round.answered(prompt, Rating.Good)
        }
        assertEquals(listOf("rot" to false), seen)

        clock.now = berlin("2026-11-13T10:00")
        val tomorrow = LocalDate.parse("2026-11-13")
        val next = Round().current(Workload.ofTopic(cards.topicPractice(colours, tomorrow).first().orEmpty(), Round(), tomorrow))
        assertEquals("ahmar" to true, next?.question to next?.reverse)
        assertEquals("rot", next?.answer)
    }

    @Test
    fun `a card asked both ways is sure only both ways, and ticked off its back is forgotten`() = runBlocking {
        val sure = Card(languageId = 1, front = "a", back = "b", interval = 30, bothWays = true, reverseInterval = 30)
        assertTrue(sure.sure)
        assertFalse(sure.copy(reverseInterval = 20).sure)
        assertFalse(sure.copy(reverseInterval = null).sure)
        assertTrue(sure.copy(bothWays = false, reverseInterval = null).sure)

        val arabic = language("Arabisch")
        val book = both(arabic, "Buch")
        database.cards().update(checkNotNull(database.cards().card(book)).copy(interval = 30, reverseInterval = 10, reverseDue = today))
        assertEquals(0, database.cards().sureCount(arabic))
        assertEquals(Saving.Saved(book), cards.save(book, CardDraft("Buch", "Buch?", bothWays = false)))
        val saved = checkNotNull(database.cards().card(book))
        assertFalse(saved.bothWays)
        assertNull(saved.reverseDue)
        assertNull(saved.reverseInterval)
        assertEquals(30, saved.interval)
        assertEquals(1, database.cards().sureCount(arabic))
    }

    @Test
    fun `ticking off a card's back forgets its answers that way and may do the day's workload`() = runBlocking {
        val done = mutableListOf<Long>()
        val told = Flashcards({ database }, clock, documents, Dispatchers.Unconfined, onWorkloadDone = { done += it })
        val arabic = language("Arabisch")
        val book = both(arabic, "Buch")
        told.answer(book, Rating.Good)
        clock.now = berlin("2026-11-13T10:00")
        told.answer(book, Rating.Good, reverse = true)
        assertEquals(listOf(false, true), database.cards().reviewsOf(book).map { it.reverse })

        // The next day its back is due, and nothing else.
        val saved = checkNotNull(database.cards().card(book))
        database.cards().update(saved.copy(due = LocalDate.parse("2026-11-20"), reverseDue = LocalDate.parse("2026-11-14")))
        clock.now = berlin("2026-11-14T10:00")
        assertEquals(listOf(true), workload(arabic, LocalDate.parse("2026-11-14")).remaining.map { it.reverse })

        // Each answer before did its day's workload.
        done.clear()
        told.save(book, CardDraft("Buch", "Buch?", bothWays = false))
        assertEquals(listOf(false), database.cards().reviewsOf(book).map { it.reverse })
        assertEquals("the workload is done with it", listOf(arabic), done)
    }

    @Test
    fun `cards made many at once are asked both ways if ticked`() = runBlocking {
        val arabic = language("Arabisch")
        cards.createAll(arabic, listOf(CardDraft("rot", "ahmar"), CardDraft("blau", "azraq")), topicId = null, bothWays = true)
        assertEquals(listOf(true, true), database.cards().of(arabic).map { it.bothWays })
    }

    /** Asks the card [front] until it is sure, the days going by as it is due. */
    private suspend fun makeSure(front: String) {
        repeat(5) {
            val card = database.cards().observeAll().first().single { it.front == front }
            if (card.sure) return
            card.due?.let { due -> clock.now = due.atTime(10, 0).atZone(clock.zone).toInstant() }
            cards.answer(card.id, Rating.Easy)
        }
    }

    private suspend fun answer(front: String, rating: Rating) =
        cards.answer(database.cards().of(languageOf(front)).single { it.front == front }.id, rating)

    private suspend fun languageOf(front: String) = database.cards().observeAll().first().single { it.front == front }.languageId

    /** Answers the card the [round] shows of the only language, and returns the round after it. */
    private suspend fun answerInRound(round: Round, rating: Rating): Round {
        val language = database.languages().languages().first { !it.archived }.id
        val card = checkNotNull(round.current(workload(language)))
        cards.answer(card.id, rating, reverse = card.reverse)
        return round.answered(card, rating)
    }

    private suspend fun workload(languageId: Long, on: LocalDate = today): Workload = checkNotNull(cards.workload(languageId, on).first())

    private suspend fun language(name: String) = (languages.createLanguage(name) as Saving.Saved).id

    private suspend fun topic(languageId: Long, name: String) = (languages.createTopic(languageId, name) as Saving.Saved).id

    private suspend fun card(languageId: Long, front: String) = (cards.create(languageId, CardDraft(front, "$front?")) as Saving.Saved).id

    private suspend fun both(languageId: Long, front: String) = (cards.create(languageId, CardDraft(front, "$front?", bothWays = true)) as Saving.Saved).id

    private fun berlin(time: String): Instant = LocalDateTime.parse(time).atZone(clock.zone).toInstant()
}
