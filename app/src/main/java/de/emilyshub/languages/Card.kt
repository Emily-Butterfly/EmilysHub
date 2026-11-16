package de.emilyshub.languages

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.emilyshub.data.InputText
import de.emilyshub.wishes.Wish
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate

/**
 * A card of the Vokabeltrainer (SPEC.md §15): of the language [languageId], its [front] in the language, which no other
 * card of it reads the same as, its [back], the translation, and optionally a [transcription], a [note] and a [topicId]
 * of the language's topics, none once that topic is deleted. What FSRS-6 planned for it ([Fsrs]): its [stability] and
 * [difficulty], the day it was first asked ([firstReview]), for the limit of new cards a day, the day it was asked last
 * ([lastReview]), the day it is due ([due]) and the interval planned last, in days ([interval]); a new card has none of
 * them. Asked [bothWays] (#141), it is also asked from its back to its front, planned on its own as the [reverse]
 * fields say, as a second card would be. Its language cannot be deleted while it is there.
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
    @ColumnInfo(name = "both_ways", defaultValue = "0") val bothWays: Boolean = false,
    @ColumnInfo(name = "reverse_stability") val reverseStability: Double? = null,
    @ColumnInfo(name = "reverse_difficulty") val reverseDifficulty: Double? = null,
    @ColumnInfo(name = "reverse_first_review") val reverseFirstReview: LocalDate? = null,
    @ColumnInfo(name = "reverse_last_review") val reverseLastReview: LocalDate? = null,
    @ColumnInfo(name = "reverse_due") val reverseDue: LocalDate? = null,
    @ColumnInfo(name = "reverse_interval") val reverseInterval: Int? = null,
) : Serializable {
    /** It asked from its front, and, [bothWays], from its back too: what a day's workload and a topic take. */
    val prompts: List<Prompt> get() = listOfNotNull(Prompt(this), Prompt(this, reverse = true).takeIf { bothWays })

    /** The day the next of its directions is due, none while all are new: what the tab Karten shows. */
    val nextDue: LocalDate? get() = listOfNotNull(due, reverseDue.takeIf { bothWays }).minOrNull()

    /** Whether it was never asked one of the ways it is asked: its back may come as a new card although its front is due later. */
    val newAnyWay: Boolean get() = new || (bothWays && reverseStability == null)

    /** With what was planned from its back forgotten, as when asking it both ways is ticked off. */
    fun withoutReverse() = copy(
        bothWays = false,
        reverseStability = null,
        reverseDifficulty = null,
        reverseFirstReview = null,
        reverseLastReview = null,
        reverseDue = null,
        reverseInterval = null,
    )

    /** What FSRS knows of it from its front; none while it is new. */
    val memory: Memory? get() = Prompt(this).memory

    /** Whether it was never asked. */
    val new: Boolean get() = memory == null

    /**
     * Whether it is "sicher" (SPEC.md §15): the interval planned last is [SURE_DAYS] or longer, [bothWays] in both
     * directions (#141).
     */
    val sure: Boolean get() = (interval ?: 0) >= SURE_DAYS && (!bothWays || (reverseInterval ?: 0) >= SURE_DAYS)

    /** Whether it is due on [today] from its front: asked before and due then or earlier. */
    fun dueOn(today: LocalDate): Boolean = Prompt(this).dueOn(today)

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

        /** Whether the card `c` is "sicher" ([sure]), as SQL tells it: the queries using it name the cards `c`. */
        const val SURE_SQL = "(c.interval >= $SURE_DAYS AND (c.both_ways = 0 OR c.reverse_interval >= $SURE_DAYS))"
    }
}

/** Which way a card is asked, by its id ([cardId]): from its front, or from its back ([reverse]). Saved by the practice. */
data class PromptKey(val cardId: Long, val reverse: Boolean) : Serializable

/**
 * The [card] asked one way (SPEC.md §15; #141): from its front to its back, or [reverse], from its back to its front,
 * each with what was planned for it alone, as two cards would have.
 */
data class Prompt(val card: Card, val reverse: Boolean = false) : Serializable {
    val key: PromptKey get() = PromptKey(card.id, reverse)

    /** Its card's id. */
    val id: Long get() = card.id

    /** Its card's front, whichever way it is asked. */
    val front: String get() = card.front

    /** What is asked: the front, or reversed the back. */
    val question: String get() = if (reverse) card.back else card.front

    /** What it answers: the back, or reversed the front. */
    val answer: String get() = if (reverse) card.front else card.back

    val stability: Double? get() = if (reverse) card.reverseStability else card.stability
    val difficulty: Double? get() = if (reverse) card.reverseDifficulty else card.difficulty
    val firstReview: LocalDate? get() = if (reverse) card.reverseFirstReview else card.firstReview
    val lastReview: LocalDate? get() = if (reverse) card.reverseLastReview else card.lastReview
    val due: LocalDate? get() = if (reverse) card.reverseDue else card.due
    val interval: Int? get() = if (reverse) card.reverseInterval else card.interval

    /** What FSRS knows of it; none while it is new. */
    val memory: Memory? get() = stability?.let { s -> difficulty?.let { d -> Memory(s, d) } }

    /** Whether it was never asked this way. */
    val new: Boolean get() = memory == null

    /** Whether it is "sicher" this way. */
    val sure: Boolean get() = (interval ?: 0) >= Card.SURE_DAYS

    /** Whether it is due on [today]: asked before and due then or earlier. */
    fun dueOn(today: LocalDate): Boolean = due?.let { !it.isAfter(today) } == true

    companion object {
        /** The due ones in the order asked: the longest due first, then in the order made, a card's front before its back. */
        val BY_DUE: Comparator<Prompt> = compareBy<Prompt>({ it.due }, { it.id }, { it.reverse })

        /** The new ones in the order asked: in the order made, a card's front before its back. */
        val BY_MADE: Comparator<Prompt> = compareBy<Prompt>({ it.id }, { it.reverse })
    }

    /**
     * [card] with what was planned for it this way as this prompt has it, the other way and all else as they are on
     * [card]: an answer taken back (#145).
     */
    fun restoredOnto(card: Card): Card =
        if (reverse) {
            card.copy(
                reverseStability = stability,
                reverseDifficulty = difficulty,
                reverseFirstReview = firstReview,
                reverseLastReview = lastReview,
                reverseDue = due,
                reverseInterval = interval,
            )
        } else {
            card.copy(stability = stability, difficulty = difficulty, firstReview = firstReview, lastReview = lastReview, due = due, interval = interval)
        }

    /** Its card with what was planned for it this way set: [memory], asked first and last, due and [interval]. */
    fun planned(memory: Memory, firstReview: LocalDate?, lastReview: LocalDate, due: LocalDate, interval: Int): Card =
        if (reverse) {
            card.copy(
                reverseStability = memory.stability,
                reverseDifficulty = memory.difficulty,
                reverseFirstReview = firstReview,
                reverseLastReview = lastReview,
                reverseDue = due,
                reverseInterval = interval,
            )
        } else {
            card.copy(stability = memory.stability, difficulty = memory.difficulty, firstReview = firstReview, lastReview = lastReview, due = due, interval = interval)
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
    /** Whether it was asked from its back (#141). */
    @ColumnInfo(defaultValue = "0") val reverse: Boolean = false,
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

/**
 * A card as typed into its editor: [front], [back], [transcription], [note], [topicId] and whether it is asked
 * [bothWays] (#141). Serializable, so the editor keeps it.
 */
data class CardDraft(
    val front: String = "",
    val back: String = "",
    val transcription: String = "",
    val note: String = "",
    val topicId: Long? = null,
    val bothWays: Boolean = false,
) : Serializable {
    /** As it is saved: the front and transcription on a single line, the back and note cleaned. */
    fun cleaned() = copy(
        front = InputText.singleLine(front),
        back = InputText.multiLine(back).trim(),
        transcription = InputText.singleLine(transcription),
        note = InputText.multiLine(note).trim(),
    )

    /**
     * [card] with what is typed here: its sides, transcription, note, topic and whether asked both ways; what was planned
     * for it stays, but for its back once it is no longer asked so.
     */
    fun applyTo(card: Card): Card {
        val typed = card.copy(front = front, back = back, transcription = transcription, note = note, topicId = topicId, bothWays = bothWays)
        return if (bothWays) typed else typed.withoutReverse()
    }

    /** With front and back swapped, for a list written the other way round. */
    fun swapped() = copy(front = back, back = front)

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
        fun of(card: Card) = CardDraft(card.front, card.back, card.transcription, card.note, card.topicId, card.bothWays)
    }
}

/**
 * A goal's Karten-Ziel (SPEC.md §6, §15; #106): [count] cards of the language [languageId] "sicher". One a goal at most;
 * gone with its goal, or with its language.
 */
@Entity(
    tableName = "card_goals",
    foreignKeys = [
        ForeignKey(entity = Wish::class, parentColumns = ["id"], childColumns = ["goal_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Language::class, parentColumns = ["id"], childColumns = ["language_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("language_id")],
)
data class CardGoal(
    @PrimaryKey @ColumnInfo(name = "goal_id") val goalId: Long,
    @ColumnInfo(name = "language_id") val languageId: Long,
    val count: Int,
) {
    companion object {
        /** How many sure cards a Karten-Ziel may ask for. */
        val COUNTS = 1..100_000
    }
}
