package de.emilyshub.notes

import de.emilyshub.notes.Inline.Bold
import de.emilyshub.notes.Inline.Code
import de.emilyshub.notes.Inline.Italic
import de.emilyshub.notes.Inline.Link
import de.emilyshub.notes.Inline.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Markdown of notes (SPEC.md §7, §9): the usual blocks and emphasis, shown as text only; HTML stays as written, and
 * links keep their words but no target, so that nothing can be opened. Hostile or very large text is read in time.
 */
class MarkdownTest {

    @Test
    fun `headings, paragraphs, lists, quotes, code and rules are read as blocks`() {
        val blocks = Markdown.parse(
            """
            # Arabisch
            ## Grammatik

            Verben im Präsens,
            jeden Tag üben.

            - Lektion 1
            - [x] Lektion 2
            * [ ] Lektion 3
            1. Hören
            2. Sprechen
            > Übung macht den Meister
            > – Sprichwort

            ```
            fun main() = println("**nicht fett**")
            ```
            ---
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                Block.Heading(1, listOf(Text("Arabisch"))),
                Block.Heading(2, listOf(Text("Grammatik"))),
                Block.Paragraph(listOf(Text("Verben im Präsens,\njeden Tag üben."))),
                Block.Bullets(
                    listOf(
                        ListItem(listOf(Text("Lektion 1"))),
                        ListItem(listOf(Text("Lektion 2")), checked = true),
                        ListItem(listOf(Text("Lektion 3")), checked = false),
                    ),
                ),
                Block.Numbered(start = 1, listOf(ListItem(listOf(Text("Hören"))), ListItem(listOf(Text("Sprechen"))))),
                Block.Quote(listOf(Text("Übung macht den Meister\n– Sprichwort"))),
                Block.Code("fun main() = println(\"**nicht fett**\")"),
                Block.Rule,
            ),
            blocks,
        )
    }

    @Test
    fun `bold, italic, code and nested emphasis are read within a line`() {
        assertEquals(
            listOf(Text("Ganz "), Bold(listOf(Text("fett"))), Text(" und "), Italic(listOf(Text("schräg"))), Text(".")),
            inline("Ganz **fett** und *schräg*."),
        )
        assertEquals(listOf(Bold(listOf(Text("fett und "), Italic(listOf(Text("schräg")))))), inline("**fett und _schräg_**"))
        assertEquals(listOf(Text("Mit "), Code("**Sternen**")), inline("Mit `**Sternen**`"))
        assertEquals("Unclosed stays as written", listOf(Text("2 * 3 = 6 und **offen")), inline("2 * 3 = 6 und **offen"))
        assertEquals("Escaped stays as written", listOf(Text("*kein* Stern")), inline("\\*kein\\* Stern"))
    }

    @Test
    fun `code between runs of backticks ends at the run as long as the one it began with`() {
        assertEquals(listOf(Text("Mit "), Code("a ` b"), Text(".")), inline("Mit ``a ` b``."))
        assertEquals("A space each side is left out", listOf(Code("`x`")), inline("`` `x` ``"))
        assertEquals("Unclosed stays as written", listOf(Text("a `` b ` c")), inline("a `` b ` c"))
    }

    @Test
    fun `an exclamation mark before brackets that are no image stays`() {
        assertEquals(listOf(Text("Toll![1] und Achtung![Hinweis] folgt")), inline("Toll![1] und Achtung![Hinweis] folgt"))
        assertEquals(listOf(Text("Wow! "), Link(listOf(Text("Bild")))), inline("Wow! ![Bild](x.png)"))
    }

    @Test
    fun `HTML is no markup but text, shown as written`() {
        val html = "<script>alert('x')</script><img src=x onerror=alert(1)><b>fett?</b>"

        assertEquals(listOf(Block.Paragraph(listOf(Text(html)))), Markdown.parse(html))
    }

    @Test
    fun `a link keeps its words but not its target, whatever that is`() {
        assertEquals(listOf(Text("Siehe "), Link(listOf(Text("hier"))), Text(".")), inline("Siehe [hier](javascript:alert(1))."))
        assertEquals(listOf(Link(listOf(Bold(listOf(Text("Bild")))))), inline("![**Bild**](file:///data/data/de.emilyshub/x)"))
        assertEquals(
            "A bare address stays plain text",
            listOf(Text("https://example.com und intent://x#Intent;end")),
            inline("https://example.com und intent://x#Intent;end"),
        )
        val everything = Markdown.parse("[a](javascript:alert(1)) <a href=\"javascript:alert(2)\">b</a>").toString()
        assertTrue("No target of a link is kept: $everything", "javascript:alert(1)" !in everything)
    }

    @Test
    fun `very large and hostile text is read in time, without running out of stack`() {
        val hostile = listOf(
            "*".repeat(100_000),
            "**a".repeat(33_000),
            "[".repeat(100_000),
            "](".repeat(50_000),
            "`".repeat(100_000),
            ">".repeat(100_000),
            "#".repeat(100_000),
            "- ".repeat(50_000),
            "_*".repeat(50_000),
            "\n".repeat(100_000),
            "![a](".repeat(20_000),
            "abc def ".repeat(12_500),
            (1..440).joinToString(" ") { "`".repeat(it) },
            "``a`".repeat(25_000),
        )

        for (text in hostile) {
            val started = System.nanoTime()
            Markdown.parse(text)
            val millis = (System.nanoTime() - started) / 1_000_000
            assertTrue("${text.take(10)}… took $millis ms", millis < 2_000)
        }
    }

    private fun inline(text: String): List<Inline> = (Markdown.parse(text).single() as Block.Paragraph).text
}
