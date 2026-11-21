package de.emilyshub.languages

import de.emilyshub.data.InputText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The vowel marks left out of the question (SPEC.md §15; #154): the marks the search passes over and the tatweel go,
 * the letters stay, whatever else the text holds.
 */
class VowelMarksTest {

    @Test
    fun `the vowel marks and the tatweel go, the letters stay`() {
        assertEquals("كتب", InputText.withoutVowelMarks("كَتَبَ"))
        assertEquals("كتاب", InputText.withoutVowelMarks("كِتَـابٌ"))
        assertEquals("مدرسة", InputText.withoutVowelMarks("مَدْرَسَة"))
    }

    @Test
    fun `text without them stays as it is, Latin letters and spaces too`() {
        assertEquals("كتب", InputText.withoutVowelMarks("كتب"))
        assertEquals("al-kitāb ist كتاب", InputText.withoutVowelMarks("al-kitāb ist كِتَاب"))
    }

    @Test
    fun `whether a text has any`() {
        assertTrue(InputText.hasVowelMarks("كَتَبَ"))
        assertFalse(InputText.hasVowelMarks("كتب"))
        assertFalse(InputText.hasVowelMarks("Buch"))
    }
}
