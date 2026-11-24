package de.emilyshub.languages

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.Saving
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.vault.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

/**
 * Taking a Vokabeldatei over (SPEC.md §15, Abgleich; #165): new vocables become cards, the others change theirs, what
 * was planned stays, and no card goes.
 */
@RunWith(AndroidJUnit4::class)
class VocabSyncTest {

    private val database = inMemoryDatabase()
    private val clock = TestClock(Instant.parse("2026-11-24T09:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val languages = Languages(
        { database }, clock, LessonFiles(Vault(testAttachmentStore(), FakeLessonDocuments(), Dispatchers.Unconfined), Dispatchers.Unconfined), Dispatchers.Unconfined,
    )
    private val cards = Flashcards({ database }, clock, FakeLessonDocuments(), Dispatchers.Unconfined)

    private val write = Vocab(1, "كتب", "كتب", "كَتَبَ", "schreiben", "Verb (Form I) · Wurzel ك-ت-ب", "Basis-Verben")
    private val coffee = Vocab(2, "قهوة", "قهوة", "قَهْوَةٌ", "der Kaffee", "Nomen (f.)", "Essen")

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `new vocables become new cards with their key, asked both ways as chosen, their topics made`() = runBlocking {
        val arabic = language()

        val plan = cards.reconciliation(arabic, listOf(write, coffee))
        assertEquals(listOf(write, coffee), plan.added)
        assertEquals(listOf("Basis-Verben", "Essen"), plan.newTopics)

        assertEquals(VocabsTaken(created = 2, changed = 0), cards.takeOver(arabic, listOf(write, coffee), leftOut = emptySet(), bothWays = true))
        val made = all(arabic)
        assertEquals(listOf("كتب", "قهوة"), made.map { it.vocabKey })
        assertTrue(made.all { it.bothWays && it.new })
        val topics = database.languages().topicsOf(arabic).associate { it.id to it.name }
        assertEquals(listOf("Basis-Verben", "Essen"), made.map { topics[it.topicId] })
        assertEquals("Verb (Form I) · Wurzel ك-ت-ب", made.first().note)

        assertTrue("taken over, it is up to date", cards.reconciliation(arabic, listOf(write, coffee)).upToDate)
    }

    @Test
    fun `a new fassung changes its cards' fields only, what was planned and all else staying, and leaves out none`() = runBlocking {
        val arabic = language()
        cards.takeOver(arabic, listOf(write, coffee), emptySet(), bothWays = false)
        val writing = all(arabic).first()
        cards.answer(writing.id, Rating.Good)
        cards.save(writing.id, CardDraft.of(checkNotNull(cards.card(writing.id))).copy(transcription = "kataba", example = "كَتَبَ رسالة", bothWays = true))
        val planned = checkNotNull(cards.card(writing.id))

        val newer = write.copy(front = "كَتَبَ / يَكْتُبُ", back = "schreiben, aufschreiben", topic = "Wichtige Verben")
        val plan = cards.reconciliation(arabic, listOf(newer))
        assertEquals(0, plan.unchanged)
        assertEquals(
            listOf(VocabField.Front, VocabField.Back, VocabField.Topic),
            plan.changed.single().fields.map { it.field },
        )
        assertEquals(VocabsTaken(created = 0, changed = 1), cards.takeOver(arabic, listOf(newer), emptySet(), bothWays = false))

        val changed = checkNotNull(cards.card(writing.id))
        assertEquals("كَتَبَ / يَكْتُبُ" to "schreiben, aufschreiben", changed.front to changed.back)
        assertEquals("Wichtige Verben", database.languages().topic(checkNotNull(changed.topicId))?.name)
        assertEquals(
            planned.copy(front = changed.front, back = changed.back, topicId = changed.topicId),
            changed,
        )
        assertEquals("the coffee, gone from the file, stays", 2, all(arabic).size)
    }

    @Test
    fun `a card made by hand is found by its front without its vowel marks, keeps its plan and is linked`() = runBlocking {
        val arabic = language()
        val hand = (cards.create(arabic, CardDraft("قَهْوَة", "Kaffee")) as Saving.Saved).id
        cards.answer(hand, Rating.Easy)

        val change = cards.reconciliation(arabic, listOf(coffee)).changed.single()
        assertTrue(change.handMade)
        cards.takeOver(arabic, listOf(coffee), emptySet(), bothWays = true)

        val linked = all(arabic).single()
        assertEquals(hand, linked.id)
        assertEquals("قهوة", linked.vocabKey)
        assertEquals("asked both ways only as a new card would be", false, linked.bothWays)
        assertNotNull(linked.stability)
    }

    @Test
    fun `a vocable given an id finds its card by its writing and keys it by the id from then on`() = runBlocking {
        val arabic = language()
        cards.takeOver(arabic, listOf(write), emptySet(), bothWays = false)

        val withId = write.copy(key = "v0001")
        val plan = cards.reconciliation(arabic, listOf(withId))
        assertEquals(emptyList<FieldChange>(), plan.changed.single().fields)
        cards.takeOver(arabic, listOf(withId), emptySet(), bothWays = false)
        assertEquals(listOf("v0001"), all(arabic).map { it.vocabKey })

        // Its writing changed, the id still finds it.
        val respelled = withId.copy(plain = "كتب٠", front = "كَتَبَ ٠")
        cards.takeOver(arabic, listOf(respelled), emptySet(), bothWays = false)
        assertEquals(listOf("كَتَبَ ٠"), all(arabic).map { it.front })
    }

    @Test
    fun `a front another card has refuses the vocable, the others are taken over`() = runBlocking {
        val arabic = language()
        cards.create(arabic, CardDraft("كَتَبَ", "von Hand", note = "eigene"))
        cards.takeOver(arabic, listOf(write.copy(key = "v1", plain = "كتب١")), emptySet(), bothWays = false)

        val homograph = write.copy(entry = 3, key = "v2", plain = "كتب٢")
        val plan = cards.reconciliation(arabic, listOf(homograph, coffee))
        assertEquals(listOf(homograph), plan.refused)
        assertEquals(listOf(coffee), plan.added)
    }

    @Test
    fun `a new card left out is not made, nor the topic only it named, and a card deleted is offered again`() = runBlocking {
        val arabic = language()
        assertEquals(VocabsTaken(created = 1, changed = 0), cards.takeOver(arabic, listOf(write, coffee), leftOut = setOf(2), bothWays = true))
        assertEquals(listOf("Basis-Verben"), database.languages().topicsOf(arabic).map { it.name })

        cards.delete(all(arabic).single().id)
        assertEquals(listOf(write, coffee), cards.reconciliation(arabic, listOf(write, coffee)).added)
    }

    private suspend fun language() = (languages.createLanguage("Arabisch") as Saving.Saved).id

    private suspend fun all(languageId: Long) = database.cards().of(languageId).sortedBy { it.id }
}
