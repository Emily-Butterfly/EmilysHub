package de.emilyshub.languages

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.emilyshub.data.InputText
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate

/**
 * A card of the Vokabeltrainer (SPEC.md §15): of the language [languageId], its [front] in the language, which no other
 * card of it reads the same as, its [back], the translation, and optionally a [transcription], a [note] and a [topicId]
 * of the language's topics, none once that topic is deleted. What FSRS-6 planned for it ([Fsrs]): its [stability] and
 * [difficulty], the day it was first asked ([firstReview]), for the limit of new cards a day, the day it was asked last
 * ([lastReview]), the day it is due ([due]) and the interval planned last, in days ([interval]); a new card has none of
 * them. Its language cannot be deleted while it is there.
 */
@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(entity = Language::class, parentColumns = ["id"], childColumns = ["language_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = Topic::class, parentColumns = ["id"], childColumns = ["topic_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("language_id"), Index("topic_id")],
)
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "language_id") val languageId: Long,
    val front: String,
    val back: String,
    val transcription: String = "",
    val note: String = "",
    @ColumnInfo(name = "topic_id") val topicId: Long? = null,
    val stability: Double? = null,
    val difficulty: Double? = null,
    @ColumnInfo(name = "first_review") val firstReview: LocalDate? = null,
    @ColumnInfo(name = "last_review") val lastReview: LocalDate? = null,
    val due: LocalDate? = null,
    val interval: Int? = null,
) {
    /** What FSRS knows of it; none while it is new. */
    val memory: Memory? get() = if (stability != null && difficulty != null) Memory(stability, difficulty) else null

    /** Whether it was never asked. */
    val new: Boolean get() = memory == null

    /** Whether it is "sicher" (SPEC.md §15): the interval planned last is [SURE_DAYS] or longer. */
    val sure: Boolean get() = (interval ?: 0) >= SURE_DAYS

    /** Whether it is due on [today]: asked before and due then or earlier. */
    fun dueOn(today: LocalDate): Boolean = due?.let { !it.isAfter(today) } == true

    companion object {
        /** The longest a card's front may be, in characters. */
        const val FRONT_MAX = 200

        /** The longest a card's back may be, in characters. */
        const val BACK_MAX = 500

        /** The longest a card's transcription may be, in characters. */
        const val TRANSCRIPTION_MAX = 200

        /** The longest a card's note may be, in characters. */
        const val NOTE_MAX = 500

        /** From how long an interval on a card is "sicher", in days, as Anki calls a card "mature". */
        const val SURE_DAYS = 21
    }
}

/**
 * One answer to a card (SPEC.md §15): the card [cardId], when ([at]) and what ([grade], of [Rating]); kept so that the
 * parameters can be fitted later and the cards reckoned anew. Shown nowhere. Gone with its card.
 */
@Entity(
    tableName = "card_reviews",
    foreignKeys = [
        ForeignKey(entity = Card::class, parentColumns = ["id"], childColumns = ["card_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("card_id")],
)
data class CardReview(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "card_id") val cardId: Long,
    val at: Instant,
    val grade: Int,
)

/** What keeps a [CardDraft] from being saved, each shown at its field in the editor. */
enum class CardProblem {
    FrontMissing,
    FrontTooLong,

    /** Another card of the language reads the same: "Diese Karte gibt es schon". */
    FrontTaken,
    BackMissing,
    BackTooLong,
    TranscriptionTooLong,
    NoteTooLong,

    /** Its topic is not of its language, or gone: only when saving. */
    TopicNotOffered,
}

/** A card as typed into its editor: [front], [back], [transcription], [note] and [topicId]. Serializable, so the editor keeps it. */
data class CardDraft(
    val front: String = "",
    val back: String = "",
    val transcription: String = "",
    val note: String = "",
    val topicId: Long? = null,
) : Serializable {
    /** As it is saved: the front and transcription on a single line, the back and note cleaned. */
    fun cleaned() = copy(
        front = InputText.singleLine(front),
        back = InputText.multiLine(back).trim(),
        transcription = InputText.singleLine(transcription),
        note = InputText.multiLine(note).trim(),
    )

    /** [card] with what is typed here: its sides, transcription, note and topic; what was planned for it stays. */
    fun applyTo(card: Card) = card.copy(front = front, back = back, transcription = transcription, note = note, topicId = topicId)

    /** What keeps it from being saved, as far as it alone tells; none if it can be. */
    val problems: Set<CardProblem>
        get() = with(cleaned()) {
            buildSet {
                when {
                    !InputText.isVisible(front) -> add(CardProblem.FrontMissing)
                    InputText.lengthOf(front) > Card.FRONT_MAX -> add(CardProblem.FrontTooLong)
                }
                when {
                    !InputText.isVisible(back) -> add(CardProblem.BackMissing)
                    InputText.lengthOf(back) > Card.BACK_MAX -> add(CardProblem.BackTooLong)
                }
                if (InputText.lengthOf(transcription) > Card.TRANSCRIPTION_MAX) add(CardProblem.TranscriptionTooLong)
                if (InputText.lengthOf(note) > Card.NOTE_MAX) add(CardProblem.NoteTooLong)
            }
        }

    companion object {
        /** [card] as its editor opens it. */
        fun of(card: Card) = CardDraft(card.front, card.back, card.transcription, card.note, card.topicId)
    }
}
