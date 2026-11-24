package de.emilyshub.languages

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.Saving
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.vault.Vault
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Runde (CONTEXT.md; SPEC.md §15; deepening plan 6, step 4), driven as the practice drives it, without the screen:
 * the card shown and where it stands, "Nochmal" at the end, the last answer taken back one step, and a topic practised.
 */
@RunWith(AndroidJUnit4::class)
class PracticeRoundTest {

    private val database = inMemoryDatabase()
    private val clock = TestClock(Instant.parse("2026-11-12T09:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.parse("2026-11-12")
    private val languages = Languages(
        { database }, clock, LessonFiles(Vault(testAttachmentStore(), FakeLessonDocuments(), Dispatchers.Unconfined), Dispatchers.Unconfined), Dispatchers.Unconfined,
    )
    private val cards = Flashcards({ database }, clock, FakeLessonDocuments(), Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a round shows the Tagespensum card by card, counting where it stands, and Nochmal comes again at its end`() = runBlocking {
        val arabic = language("Arabisch")
        card(arabic, "kitab")
        card(arabic, "qalam")
        var round = PracticeRound()

        assertEquals("kitab", shown(round, arabic)?.card?.front)
        assertEquals(1 to 2, round.position(cardsOf(round, arabic)))
        round = round.answered(cards, checkNotNull(shown(round, arabic)), Rating.Again)
        assertEquals("qalam", shown(round, arabic)?.card?.front)
        round = round.answered(cards, checkNotNull(shown(round, arabic)), Rating.Good)

        assertEquals("again at the end", "kitab", shown(round, arabic)?.card?.front)
        round = round.answered(cards, checkNotNull(shown(round, arabic)), Rating.Good)
        assertNull("geschafft", shown(round, arabic))
    }

    @Test
    fun `the last answer is taken back one step, the round as before, the card shown again, nothing else to take back`() = runBlocking {
        val arabic = language("Arabisch")
        card(arabic, "kitab")
        card(arabic, "qalam")
        var round = PracticeRound()
        assertFalse("nothing answered yet", round.canTakeBack)
        round = round.answered(cards, checkNotNull(shown(round, arabic)), Rating.Good)
        assertTrue(round.canTakeBack)

        round = round.takingBack()
        assertFalse("while it is on its way", round.idle)
        assertEquals("shown at once", "kitab", round.shown(cardsOf(round, arabic))?.card?.front)
        round = round.takenBack(cards)
        // Once the Tagespensum has it again, it is the round's card as any.
        round = round.settledBy(cardsOf(round, arabic))

        assertTrue(round.idle)
        assertFalse("one step only", round.canTakeBack)
        assertEquals("kitab", shown(round, arabic)?.card?.front)
        assertEquals(1 to 2, round.position(cardsOf(round, arabic)))
        assertEquals(emptyList<CardReview>(), database.cards().reviewsOf(database.cards().of(arabic).single { it.front == "kitab" }.id))
    }

    @Test
    fun `while an answer is on its way, its card stays shown and nothing else can be done`() = runBlocking {
        val arabic = language("Arabisch")
        card(arabic, "kitab")
        val round = PracticeRound().answering(checkNotNull(shown(PracticeRound(), arabic)))

        assertFalse(round.idle)
        assertFalse(round.canTakeBack)
        assertEquals("kitab", round.shown(cardsOf(round, arabic))?.card?.front)
    }

    @Test
    fun `a topic practised asks each of its cards once, and its new ones do not count against the day's`() = runBlocking {
        val arabic = language("Arabisch")
        val verbs = (languages.createTopic(arabic, "Verben") as Saving.Saved).id
        listOf("kataba", "qaraa").forEach { cards.create(arabic, CardDraft(it, "$it?", topicId = verbs)) }
        var round = PracticeRound(CardSelection.OfTopic(verbs))

        round = round.answered(cards, checkNotNull(shown(round, arabic)), Rating.Good)
        assertEquals("qaraa", shown(round, arabic)?.card?.front)
        round = round.answered(cards, checkNotNull(shown(round, arabic)), Rating.Good)

        assertNull(shown(round, arabic))
        assertEquals("not counted as new cards of the day", listOf(null, null), database.cards().of(arabic).map { it.firstReview })
    }

    @Test
    fun `kept by the screen through a turn or the lock, a round goes on as it was, but for an answer on its way`() = runBlocking {
        val arabic = language("Arabisch")
        card(arabic, "kitab")
        card(arabic, "qalam")
        val answered = PracticeRound().answered(cards, checkNotNull(shown(PracticeRound(), arabic)), Rating.Again)
        val onItsWay = answered.answering(checkNotNull(shown(answered, arabic)))

        val kept = keptAndRead(onItsWay)

        assertEquals(answered, kept)
        assertTrue(kept.idle)
        assertTrue(kept.canTakeBack)
    }

    private suspend fun cardsOf(round: PracticeRound, languageId: Long): Workload = checkNotNull(
        round.cards(checkNotNull(cards.workloads.of(languageId, today).first()), round.selection?.let { checkNotNull(cards.practice(it, today).first()) }, today),
    )

    private suspend fun shown(round: PracticeRound, languageId: Long): Prompt? = round.shown(cardsOf(round, languageId))

    private fun keptAndRead(round: PracticeRound): PracticeRound {
        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { out -> out.writeObject(round) } }.toByteArray()
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as PracticeRound }
    }

    private suspend fun language(name: String) = (languages.createLanguage(name) as Saving.Saved).id

    private suspend fun card(languageId: Long, front: String) = (cards.create(languageId, CardDraft(front, "$front?")) as Saving.Saved).id
}
