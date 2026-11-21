package de.emilyshub.languages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * The language the keyboard is asked for when an answer is typed in the language (SPEC.md §15; #153): the one called
 * in German as the language of the app is, as for its voices; none for a name that is no language.
 */
class KeyboardLocaleTest {

    @Test
    fun `a language called in German as the language of the app is asked for`() {
        assertEquals(Locale.forLanguageTag("ar"), Language.keyboardLocale("Arabisch"))
        assertEquals(Locale.forLanguageTag("it"), Language.keyboardLocale(" italienisch "))
    }

    @Test
    fun `a name that is no language asks for none`() {
        assertNull(Language.keyboardLocale("Arabisch bei Amal"))
        assertNull(Language.keyboardLocale(""))
    }
}
