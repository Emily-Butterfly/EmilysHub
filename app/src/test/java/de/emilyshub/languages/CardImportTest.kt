package de.emilyshub.languages

import org.junit.Assert.assertEquals
import org.junit.Test

/** Reading cards from a lesson's note and from a text file (SPEC.md §15, Anlegen; #105). */
class CardImportTest {

    @Test
    fun `a line of a note reads as a card with a dash, an en dash, an equals sign or a tab between its two sides`() {
        val note = "kitab – Buch\nqalam - Stift\nbab = Tür\nbait\tHaus\nnur ein Satz ohne Trenner\nbinta-ammi ist kein Trenner"

        assertEquals(
            listOf(CardDraft("kitab", "Buch"), CardDraft("qalam", "Stift"), CardDraft("bab", "Tür"), CardDraft("bait", "Haus")),
            CardImport.fromNote(note),
        )
    }

    @Test
    fun `Markdown at the start of a line and bold type do not count`() {
        val note = """
            ## Wortschatz
            - **kitab** – Buch
            * qalam = __Stift__
            > bab – Tür
            - [ ] bait – Haus
            - [x] madrasa – Schule
            # nur eine Überschrift
        """.trimIndent()

        assertEquals(
            listOf("kitab" to "Buch", "qalam" to "Stift", "bab" to "Tür", "bait" to "Haus", "madrasa" to "Schule"),
            CardImport.fromNote(note).map { it.front to it.back },
        )
    }

    @Test
    fun `only the first separator splits, and a side left empty is no card`() {
        assertEquals(listOf(CardDraft("a", "b – c")), CardImport.fromNote("a – b – c\n – nur hinten\nnur vorn – "))
    }

    @Test
    fun `Arabic reads as it was written, and its sides can be swapped`() {
        val cards = CardImport.fromNote("كتاب – Buch\nقلم = Stift")

        assertEquals(listOf(CardDraft("كتاب", "Buch"), CardDraft("قلم", "Stift")), cards)
        assertEquals(listOf(CardDraft("Buch", "كتاب"), CardDraft("Stift", "قلم")), cards.map(CardDraft::swapped))
    }

    @Test
    fun `a file has a card on each line, with tab or semicolon, transcription and note optional`() {
        val text = "kitab\tBuch\tkitāb\tPlural kutub\nqalam;Stift\n\n# eine Bemerkung\nbab;Tür;;offen\r\n"

        val read = CardImport.fromFile(text.toByteArray()) as CardFile.Read

        assertEquals(
            listOf(
                CardDraft("kitab", "Buch", "kitāb", "Plural kutub"),
                CardDraft("qalam", "Stift"),
                CardDraft("bab", "Tür", note = "offen"),
            ),
            read.cards.map { it.draft },
        )
        assertEquals(listOf(1, 2, 5), read.cards.map { it.line })
        assertEquals(emptyList<Int>(), read.unreadable)
    }

    @Test
    fun `a fifth field is the Beispielsatz, after the note, and one too long leaves its line unread`() {
        val text = "kitab;Buch;;;Das ist ein kitab.\nqalam\tStift\tqalam\t\tMein qalam ist rot.\nbab;Tür;;;" + "a".repeat(501)

        val read = CardImport.fromFile(text.toByteArray()) as CardFile.Read

        assertEquals(
            listOf(CardDraft("kitab", "Buch", example = "Das ist ein kitab."), CardDraft("qalam", "Stift", "qalam", example = "Mein qalam ist rot.")),
            read.cards.map { it.draft },
        )
        assertEquals(listOf(3), read.unreadable)
    }

    @Test
    fun `a field in quotes may hold the separator and quotes of its own`() {
        val read = CardImport.fromFile("\"Guten Tag; Herr\";\"sabah \"\"al\"\" khair\"\n\"a\tb\"\tc".toByteArray()) as CardFile.Read

        // The front is on a single line, as typed ones are: a tab in it reads as a space.
        assertEquals(listOf(CardDraft("Guten Tag; Herr", "sabah \"al\" khair"), CardDraft("a b", "c")), read.cards.map { it.draft })
    }

    @Test
    fun `a quote within a field is a letter like any other, and does not hide the tab`() {
        val read = CardImport.fromFile("5\" Zoll\tinch\nsie sagt \"ja\";yes".toByteArray()) as CardFile.Read

        assertEquals(listOf(CardDraft("5\" Zoll", "inch"), CardDraft("sie sagt \"ja\"", "yes")), read.cards.map { it.draft })
    }

    @Test
    fun `a line with one field, more than five, an empty side or a field too long is not readable, and nothing is cut`() {
        val text = listOf("nur eins", "a;b;c;d;e;f", ";leer vorn", "a".repeat(201) + ";zu lang", "gut;ok", "kitab;\"offen").joinToString("\n")

        val read = CardImport.fromFile(text.toByteArray()) as CardFile.Read

        assertEquals(listOf(CardDraft("gut", "ok")), read.cards.map { it.draft })
        assertEquals(listOf(1, 2, 3, 4, 6), read.unreadable)
    }

    @Test
    fun `a file over 1 MB or 2 000 lines is refused, and one that is no UTF-8 text`() {
        assertEquals(CardFile.TooLarge, CardImport.fromFile(ByteArray(CardImport.FILE_MAX_BYTES + 1) { 'a'.code.toByte() }))
        assertEquals(CardFile.TooManyLines, CardImport.fromFile(List(CardImport.FILE_MAX_LINES + 1) { "a$it;b" }.joinToString("\n").toByteArray()))
        val atTheLimit = CardImport.fromFile(List(CardImport.FILE_MAX_LINES) { "a$it;b" }.joinToString("\n").toByteArray())
        assertEquals(CardImport.FILE_MAX_LINES, (atTheLimit as CardFile.Read).cards.size)
        assertEquals(CardFile.NotText, CardImport.fromFile(byteArrayOf(0xC3.toByte(), 0x28)))
        assertEquals(CardFile.NotText, CardImport.fromFile("a;b\u0000".toByteArray()))
    }

    @Test
    fun `an Arabic file reads as written, a byte order mark at its start aside`() {
        val read = CardImport.fromFile(("﻿كتاب;Buch;kitāb").toByteArray()) as CardFile.Read

        assertEquals(listOf(CardDraft("كتاب", "Buch", "kitāb")), read.cards.map { it.draft })
    }
}
