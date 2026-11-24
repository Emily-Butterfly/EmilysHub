package de.emilyshub.languages

import de.emilyshub.data.InputText
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * A vocable of a Vokabeldatei (SPEC.md §15, Abgleich; #165), as entry [entry] of it reads: its [key], by which it finds
 * its card again, its `id` if it has one, else its unvocalised writing ([plain]); and the card it makes: its [front],
 * [back], [note] and the name of its [topic], none for no `kategorie`. An empty [note] leaves a card's note as it is.
 */
data class Vocab(
    val entry: Int,
    val key: String,
    val plain: String,
    val front: String,
    val back: String,
    val note: String = "",
    val topic: String? = null,
) {
    /** Whether it has an `id`, so that [plain] is a second way to find its card by. */
    val hasId: Boolean get() = key != plain
}

/** What reading a Vokabeldatei came to (SPEC.md §15, Abgleich). */
sealed interface VocabFile {
    /**
     * Its [vocabs], the numbers of the entries that do not read as one ([unreadable]), and of those left out as their
     * key stands more than once in it ([duplicate]).
     */
    data class Read(val vocabs: List<Vocab>, val unreadable: List<Int> = emptyList(), val duplicate: List<Int> = emptyList()) : VocabFile

    /** Over [VocabFiles.MAX_BYTES]. */
    data object TooLarge : VocabFile

    /** Over [VocabFiles.MAX_VOCABS] entries. */
    data object TooMany : VocabFile

    /** It is no JSON object with a list `vokabeln`, or cannot be read. */
    data object NotVocabFile : VocabFile
}

/**
 * Vokabeldateien (SPEC.md §15, Abgleich; #165): a JSON object with a list `vokabeln`, each entry with its
 * `ar_unvokalisiert` and `bedeutung`, optionally an `id`, its `ar_vokalisiert`, `wortart`, `wurzel` and `kategorie`;
 * all else in it is passed over. Nothing read is cut short: a field too long leaves its entry unread.
 */
object VocabFiles {
    /** The largest file read, in bytes. */
    const val MAX_BYTES = 3 * 1024 * 1024

    /** The most vocables a file may have. */
    const val MAX_VOCABS = 5_000

    /** What a `wurzel` holds for none. */
    private val NO_ROOT = setOf("—", "–", "-")

    /** The vocables of a file of [bytes], or why it is refused. */
    fun read(bytes: ByteArray): VocabFile {
        if (bytes.size > MAX_BYTES) return VocabFile.TooLarge
        val text = utf8Text(bytes)?.takeUnless { '\u0000' in it }?.removePrefix("﻿") ?: return VocabFile.NotVocabFile
        val entries = try {
            (JSONTokener(text).nextValue() as? JSONObject)?.optJSONArray("vokabeln")
        } catch (_: JSONException) {
            null
        } ?: return VocabFile.NotVocabFile
        if (entries.length() > MAX_VOCABS) return VocabFile.TooMany
        val read = mutableListOf<Vocab>()
        val unreadable = mutableListOf<Int>()
        for (index in 0 until entries.length()) {
            val vocab = entries.optJSONObject(index)?.let { vocabOf(index + 1, it) }
            if (vocab == null) unreadable += index + 1 else read += vocab
        }
        val counts = read.groupingBy { InputText.comparable(it.key) }.eachCount()
        val (vocabs, duplicate) = read.partition { counts.getValue(InputText.comparable(it.key)) == 1 }
        return VocabFile.Read(vocabs, unreadable, duplicate.map { it.entry })
    }

    /** Entry [entry] of a file, [json], as a vocable; none if it lacks what it needs or has a field too long. */
    private fun vocabOf(entry: Int, json: JSONObject): Vocab? {
        val plain = InputText.singleLine(text(json, "ar_unvokalisiert"))
        val id = InputText.singleLine(text(json, "id"))
        val front = InputText.singleLine(text(json, "ar_vokalisiert")).takeIf(InputText::isVisible) ?: plain
        val back = InputText.multiLine(text(json, "bedeutung")).trim()
        val kind = InputText.singleLine(text(json, "wortart"))
        val root = InputText.singleLine(text(json, "wurzel")).takeUnless { it in NO_ROOT }.orEmpty()
        val note = listOfNotNull(kind.takeIf(InputText::isVisible), root.takeIf(InputText::isVisible)?.let { "Wurzel $it" }).joinToString(" · ")
        val topic = InputText.singleLine(text(json, "kategorie")).takeIf(InputText::isVisible)
        if (!InputText.isVisible(plain) || InputText.lengthOf(plain) > Card.FRONT_MAX) return null
        if (InputText.lengthOf(id) > Card.FRONT_MAX) return null
        if (topic != null && InputText.lengthOf(topic) > Topic.NAME_MAX) return null
        if (CardDraft(front, back, note = note).problems.isNotEmpty()) return null
        return Vocab(entry, key = id.takeIf(InputText::isVisible) ?: plain, plain = plain, front = front, back = back, note = note, topic = topic)
    }

    /** The field [name] of [json] as text: a text as it is, a number as written; empty for none or anything else. */
    private fun text(json: JSONObject, name: String): String = when (val value = json.opt(name)) {
        is String -> value
        is Number -> value.toString()
        else -> ""
    }
}

/** A field of a card a vocable sets. */
enum class VocabField { Front, Back, Note, Topic }

/** That [field] of a card goes from [old] to [new]; a topic by its name, empty for none. */
data class FieldChange(val field: VocabField, val old: String, val new: String)

/**
 * The [card] of the vocable [vocab] as it changes: its [fields], and whether it was made by hand ([handMade]) and is
 * only now linked to its vocable. With no field changing, only the key it knows its vocable by does.
 */
data class VocabChange(val card: Card, val vocab: Vocab, val fields: List<FieldChange>, val handMade: Boolean)

/**
 * What a Vokabeldatei's [vocabs] do to the cards of a language (SPEC.md §15, Abgleich; #165): those with no card of
 * theirs are [added], those whose card differs are [changed], [unchanged] counts the others; those whose front another
 * card has already are [refused]. The topics named that the language does not have yet are [newTopics]. No card goes,
 * and nothing planned changes.
 */
data class Reconciliation(
    val added: List<Vocab>,
    val changed: List<VocabChange>,
    val unchanged: Int,
    val refused: List<Vocab>,
    val newTopics: List<String>,
) {
    /** Whether there is nothing to take over. */
    val upToDate: Boolean get() = added.isEmpty() && changed.isEmpty()

    companion object {
        /**
         * What [vocabs] do to [cards], of a language with [topics]. A vocable finds its card by its key; with an `id`
         * no card knows yet, also by its unvocalised writing, as it was keyed before it had one; else a card made by
         * hand, keyed by nothing, whose front reads without its vowel marks as the vocable's writing either way does.
         * Each card is a vocable's once at most. A front the vocable would bring that another card has, or one the
         * file has given another vocable already, refuses it.
         */
        fun of(cards: List<Card>, topics: List<Topic>, vocabs: List<Vocab>): Reconciliation {
            val byKey = cards.filter { it.vocabKey != null }.associateBy { InputText.comparable(it.vocabKey!!) }
            val handMade = cards.filter { it.vocabKey == null }
            val claimed = mutableSetOf<Long>()
            // The fronts taken, each by whom: a card by its id, a vocable added by its entry as a negative number.
            val fronts = cards.associate { InputText.comparable(it.front) to it.id }.toMutableMap()
            val topicNames = topics.associateBy({ InputText.comparable(it.name) }, { it.name })
            val topicById = topics.associateBy { it.id }
            val added = mutableListOf<Vocab>()
            val changed = mutableListOf<VocabChange>()
            val refused = mutableListOf<Vocab>()
            var unchanged = 0
            for (vocab in vocabs) {
                val card = listOfNotNull(
                    byKey[InputText.comparable(vocab.key)],
                    byKey[InputText.comparable(vocab.plain)]?.takeIf { vocab.hasId && byKey[InputText.comparable(vocab.key)] == null },
                ).firstOrNull { it.id !in claimed } ?: handMade.firstOrNull { it.id !in claimed && it.isMadeFor(vocab) }
                val owner = card?.id ?: -vocab.entry.toLong()
                val front = InputText.comparable(vocab.front)
                if (fronts[front]?.let { it != owner } == true) {
                    refused += vocab
                    continue
                }
                fronts[front] = owner
                if (card == null) {
                    added += vocab
                    continue
                }
                claimed += card.id
                val oldTopic = card.topicId?.let { topicById[it]?.name }.orEmpty()
                val fields = listOfNotNull(
                    FieldChange(VocabField.Front, card.front, vocab.front).takeIf { card.front != vocab.front },
                    FieldChange(VocabField.Back, card.back, vocab.back).takeIf { card.back != vocab.back },
                    FieldChange(VocabField.Note, card.note, vocab.note).takeIf { vocab.note.isNotEmpty() && card.note != vocab.note },
                    vocab.topic?.let { topicNames[InputText.comparable(it)] ?: it }
                        ?.takeIf { !InputText.readTheSame(it, oldTopic) }
                        ?.let { FieldChange(VocabField.Topic, oldTopic, it) },
                )
                val handMadeCard = card.vocabKey == null
                if (fields.isEmpty() && card.vocabKey == vocab.key) unchanged++ else changed += VocabChange(card, vocab, fields, handMadeCard)
            }
            val newTopics = (added.mapNotNull { it.topic } + changed.mapNotNull { change -> change.fields.find { it.field == VocabField.Topic }?.new })
                .filter { InputText.comparable(it) !in topicNames }
                .distinctBy(InputText::comparable)
            return Reconciliation(added, changed, unchanged, refused, newTopics)
        }

        /** Whether this card, made by hand, is the one of [vocab]: its front without its vowel marks reads as its writing either way does. */
        private fun Card.isMadeFor(vocab: Vocab): Boolean {
            val front = InputText.searchable(front)
            return front == InputText.searchable(vocab.plain) || front == InputText.searchable(vocab.front)
        }
    }
}

/** What taking over a Vokabeldatei came to: how many cards were [created] and how many [changed]. */
data class VocabsTaken(val created: Int, val changed: Int)
