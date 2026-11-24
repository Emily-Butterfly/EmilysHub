package de.emilyshub.languages

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Reading a Vokabeldatei (SPEC.md §15, Abgleich; #165). Robolectric, for Android's org.json. */
@RunWith(AndroidJUnit4::class)
class VocabFileTest {

    @Test
    fun `a vocable reads as a card, its kind and root the note, its category the topic, all else passed over`() {
        val file = read(
            """
            {
              "meta": {"sprache": "Modernes Hocharabisch (Fusha)", "gesamt_neu": 2},
              "vokabeln": [
                {"ar_unvokalisiert": "كتب", "ar_vokalisiert": "كَتَبَ / يَكْتُبُ", "wurzel": "ك-ت-ب", "wortart": "Verb (Form I)",
                 "bedeutung": "schreiben", "kategorie": "Basis-Verben", "status": "bekannt", "fehler_zaehler": 3, "neu_vom_tutor": true},
                {"ar_unvokalisiert": "فانتا", "wurzel": "—", "wortart": "Eigenname", "bedeutung": "Fanta (Getränk)"},
                {"id": 61, "ar_unvokalisiert": "قهوة", "ar_vokalisiert": "  قَهْوَةٌ ", "bedeutung": "der Kaffee"}
              ]
            }
            """,
        )

        assertEquals(
            VocabFile.Read(
                listOf(
                    Vocab(1, "كتب", "كتب", "كَتَبَ / يَكْتُبُ", "schreiben", "Verb (Form I) · Wurzel ك-ت-ب", "Basis-Verben"),
                    Vocab(2, "فانتا", "فانتا", "فانتا", "Fanta (Getränk)", "Eigenname"),
                    Vocab(3, "61", "قهوة", "قَهْوَةٌ", "der Kaffee"),
                ),
            ),
            file,
        )
        assertEquals(true, (file as VocabFile.Read).vocabs[2].hasId)
    }

    @Test
    fun `an entry without its writing or meaning, or with a field too long, does not read, and keys twice are left out`() {
        val file = read(
            """
            {"vokabeln": [
              {"ar_unvokalisiert": "بيت", "bedeutung": "das Haus"},
              {"ar_unvokalisiert": "باب"},
              {"bedeutung": "ohne Wort"},
              "kein Objekt",
              {"ar_unvokalisiert": "قلم", "bedeutung": "${"x".repeat(Card.BACK_MAX + 1)}"},
              {"ar_unvokalisiert": "درس", "bedeutung": "lernen", "kategorie": "${"k".repeat(Topic.NAME_MAX + 1)}"},
              {"ar_unvokalisiert": "أفضل", "bedeutung": "ich bevorzuge"},
              {"ar_unvokalisiert": "أفضل", "bedeutung": "besser"},
              {"id": "v1", "ar_unvokalisiert": "شمس", "bedeutung": "die Sonne"},
              {"id": "v1", "ar_unvokalisiert": "قمر", "bedeutung": "der Mond"}
            ]}
            """,
        ) as VocabFile.Read

        assertEquals(listOf("بيت"), file.vocabs.map { it.key })
        assertEquals(listOf(2, 3, 4, 5, 6), file.unreadable)
        assertEquals(listOf(7, 8, 9, 10), file.duplicate)
    }

    @Test
    fun `a file too large, with too many vocables, or no object with a list vokabeln is refused`() {
        assertEquals(VocabFile.TooLarge, VocabFiles.read(ByteArray(VocabFiles.MAX_BYTES + 1) { ' '.code.toByte() }))
        val many = List(VocabFiles.MAX_VOCABS + 1) { """{"ar_unvokalisiert": "w$it", "bedeutung": "b"}""" }.joinToString(",")
        assertEquals(VocabFile.TooMany, read("""{"vokabeln": [$many]}"""))
        assertEquals(VocabFile.NotVocabFile, read("kitab;Buch"))
        assertEquals(VocabFile.NotVocabFile, read("""[{"ar_unvokalisiert": "كتب", "bedeutung": "schreiben"}]"""))
        assertEquals(VocabFile.NotVocabFile, read("""{"woerter": []}"""))
        assertEquals(VocabFile.NotVocabFile, read("""{"vokabeln": [ """))
        assertEquals(VocabFile.NotVocabFile, VocabFiles.read(byteArrayOf(0xC3.toByte(), 0x28)))
        // A byte order mark ahead of it is no reason.
        assertEquals(1, (read("﻿{\"vokabeln\": [{\"ar_unvokalisiert\": \"كتب\", \"bedeutung\": \"schreiben\"}]}") as VocabFile.Read).vocabs.size)
    }

    private fun read(json: String): VocabFile = VocabFiles.read(json.trimIndent().toByteArray())
}
