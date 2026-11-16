package de.emilyshub.languages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Where a card's front stands in its Beispielsatz, and the sentence with a gap there (SPEC.md §15; #155). */
class ExampleSentenceTest {

    private fun wordOf(sentence: String, front: String): String? = ExampleSentence.wordIn(sentence, front)?.let(sentence::substring)

    @Test
    fun `the front is found in whatever case`() {
        assertEquals("Hund", wordOf("Der Hund bellt.", "hund"))
        assertEquals("Hund", wordOf("Der Hund bellt.", "HUND"))
    }

    @Test
    fun `the front is found with or without its vowel marks, and the gap takes the marks along`() {
        // Fronts and sentences written with and without the vowel marks.
        assertEquals("كِتَابٌ", wordOf("هذا كِتَابٌ جديد", "كتاب"))
        assertEquals("كتاب", wordOf("هذا كتاب جديد", "كِتَاب"))
        assertEquals("هذا … جديد", ExampleSentence.withGap("هذا كِتَابٌ جديد", "كتاب"))
    }

    @Test
    fun `an ss typed finds an Eszett, as the search does`() {
        assertEquals("Straße", wordOf("Die Straße ist lang.", "Strasse"))
    }

    @Test
    fun `a front not in the sentence, as when it is inflected there, is not found`() {
        assertNull(ExampleSentence.wordIn("Ich ging nach Hause.", "gehen"))
        assertNull(ExampleSentence.withGap("Ich ging nach Hause.", "gehen"))
        assertNull(ExampleSentence.wordIn("", "gehen"))
    }

    @Test
    fun `a whole word is found before the front within another word`() {
        assertEquals(14, ExampleSentence.wordIn("Der Mann kam, an der Tür.", "an")?.first)
        // Only within another word, it is found there all the same, as the search finds it.
        assertEquals("Haus", wordOf("Die Hausaufgabe ist schwer.", "Haus"))
    }

    @Test
    fun `the gap stands where the front stood, the rest of the sentence as it is`() {
        assertEquals("Der … bellt.", ExampleSentence.withGap("Der Hund bellt.", "Hund"))
        assertEquals("… bellt.", ExampleSentence.withGap("Hund bellt.", "hund"))
    }
}
