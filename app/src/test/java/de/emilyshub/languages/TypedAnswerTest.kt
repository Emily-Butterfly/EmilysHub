package de.emilyshub.languages

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * An answer typed in the practice (SPEC.md §15; #153), compared as the search compares: case, spaces at its edges and
 * Arabic vowel marks do not count; of several meanings split by comma or semicolon, one is enough.
 */
class TypedAnswerTest {

    @Test
    fun `case and spaces at the edges do not count, a letter does`() {
        assertEquals(TypedAnswer.Right, TypedAnswer.check("  buch ", "Buch"))
        assertEquals(TypedAnswer.Right, TypedAnswer.check("STRASSE", "Straße"))
        assertEquals(TypedAnswer.Wrong, TypedAnswer.check("Bauch", "Buch"))
        assertEquals(TypedAnswer.Wrong, TypedAnswer.check("Buc", "Buch"))
    }

    @Test
    fun `nothing typed is wrong`() {
        assertEquals(TypedAnswer.Wrong, TypedAnswer.check("", "Buch"))
        assertEquals(TypedAnswer.Wrong, TypedAnswer.check("   ", "Buch"))
    }

    @Test
    fun `Arabic typed without its vowel marks or with others is right but for the marks`() {
        assertEquals(TypedAnswer.Right, TypedAnswer.check("كَتَبَ", "كَتَبَ"))
        assertEquals(TypedAnswer.RightButMarks, TypedAnswer.check("كتب", "كَتَبَ"))
        assertEquals(TypedAnswer.RightButMarks, TypedAnswer.check("كُتُب", "كَتَبَ"))
        assertEquals(TypedAnswer.Wrong, TypedAnswer.check("كتاب", "كَتَبَ"))
    }

    @Test
    fun `of several meanings split by comma or semicolon one is enough, and all of them too`() {
        assertEquals(TypedAnswer.Right, TypedAnswer.check("heft", "Buch, Heft"))
        assertEquals(TypedAnswer.Right, TypedAnswer.check("Buch", "Buch; Heft"))
        assertEquals(TypedAnswer.Right, TypedAnswer.check("Buch, Heft", "Buch, Heft"))
        assertEquals(TypedAnswer.Wrong, TypedAnswer.check("Buch Heft", "Buch, Heft"))
        assertEquals(TypedAnswer.RightButMarks, TypedAnswer.check("كتاب", "كِتَاب، كُتُب; كِتَابٌ"))
    }

    @Test
    fun `a meaning right is right even where another is right but for the marks`() {
        assertEquals(TypedAnswer.Right, TypedAnswer.check("كتب", "كَتَبَ, كتب"))
    }

    @Test
    fun `the stretching tatweel is no vowel mark, and does not count`() {
        assertEquals(TypedAnswer.Right, TypedAnswer.check("كتـاب", "كتاب"))
        assertEquals(TypedAnswer.RightButMarks, TypedAnswer.check("كتـاب", "كِتَاب"))
    }
}
