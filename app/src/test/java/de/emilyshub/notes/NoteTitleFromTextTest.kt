package de.emilyshub.notes

import org.junit.Assert.assertEquals
import org.junit.Test

/** A note left without a title takes the first line of its text as one (#88). */
class NoteTitleFromTextTest {

    @Test
    fun `a heading gives the title without its hashes, and the text stays as it is`() {
        val draft = NoteDraft(title = "", text = "\n## Einkauf\n- Milch")

        val titled = draft.withTitleFromText()

        assertEquals("Einkauf", titled.title)
        assertEquals("\n## Einkauf\n- Milch", titled.text)
    }

    @Test
    fun `a list item, a task and a quote give theirs without the marks in front`() {
        assertEquals("Milch kaufen", NoteDraft(title = "", text = "- Milch kaufen").withTitleFromText().title)
        assertEquals("Brot holen", NoteDraft(title = "", text = "- [ ] Brot holen").withTitleFromText().title)
        assertEquals("Wer fragt, führt", NoteDraft(title = "", text = "> Wer fragt, führt").withTitleFromText().title)
    }

    @Test
    fun `a text of empty lines only gives no title`() {
        assertEquals("", NoteDraft(title = "", text = "\n   \n#\n").withTitleFromText().title)
    }

    @Test
    fun `Arabic gives its own words`() {
        assertEquals("مفردات الدرس", NoteDraft(title = "", text = "# مفردات الدرس\nكتب").withTitleFromText().title)
    }

    @Test
    fun `a long first line is cut to 80 characters`() {
        val line = "ä".repeat(79) + "😀" + "danach"

        assertEquals("ä".repeat(79) + "😀", NoteDraft(title = "", text = line).withTitleFromText().title)
    }

    @Test
    fun `a title typed stays`() {
        assertEquals("Rezepte", NoteDraft(title = "Rezepte", text = "# Brot").withTitleFromText().title)
    }
}
