package de.emilyshub.doku.assessments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How an answer is stored, as a value and a text, and read back, without its item: even once the item is gone. */
class AnswerCodecTest {

    private val every = listOf(
        Answer.Chosen("2"),
        Answer.YesNo(true),
        Answer.YesNo(false),
        Answer.OnScale(7),
        Answer.Checked(setOf("ruhe", "nacht"), "beim Bücken"),
        Answer.Checked(emptySet(), "nur Sonstiges"),
        Answer.Number(-1250),
        Answer.Text("seit März, zeilen\nweise"),
        Answer.Regions(setOf(BodyRegion.KneeLeft, BodyRegion.LumbarSpine), "Leiste"),
        Answer.Goals(listOf(Goal("Treppe mit Geländer", 1), Goal("Allein einkaufen", null), Goal("", -2))),
        Answer.Goals(listOf(Goal("Nur eins", null))),
    )

    @Test
    fun `every answer is read back as it was stored`() {
        every.forEach { answer -> assertEquals(answer, AnswerCodec.decode(checkNotNull(AnswerCodec.encode(answer)))) }
    }

    @Test
    fun `nothing answered is stored as nothing`() {
        listOf(Answer.Checked(emptySet()), Answer.Text(""), Answer.Text("   "), Answer.Regions(emptySet()), Answer.Goals(emptyList()), Answer.Goals(listOf(Goal("  ", null)))).forEach {
            assertNull(it.toString(), AnswerCodec.encode(it))
        }
    }

    @Test
    fun `a stored value it does not know reads as no answer`() {
        assertNull(AnswerCodec.decode(StoredAnswer("x:1", "")))
        assertNull(AnswerCodec.decode(StoredAnswer("s:viel", "")))
    }

    @Test
    fun `only words are what the search reads`() {
        assertEquals("beim Bücken", AnswerCodec.encode(Answer.Checked(setOf("ruhe"), "beim Bücken"))?.text)
        assertEquals("", AnswerCodec.encode(Answer.Chosen("2"))?.text)
    }

    @Test
    fun `goals keep their order, their words on one line each, which the search reads, and drop those left empty`() {
        val stored = checkNotNull(AnswerCodec.encode(Answer.Goals(listOf(Goal(" Gehen\nohne Stock ", 2), Goal("", null), Goal("Treppe", -1)))))

        assertEquals("Gehen ohne Stock\nTreppe", stored.text)
        assertEquals(Answer.Goals(listOf(Goal("Gehen ohne Stock", 2), Goal("Treppe", -1))), AnswerCodec.decode(stored))
        assertNull(AnswerCodec.decode(StoredAnswer("g:1,2", "nur eins")))
    }
}
