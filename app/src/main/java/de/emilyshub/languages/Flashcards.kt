package de.emilyshub.languages

import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InputText
import de.emilyshub.data.Saving
import de.emilyshub.wishes.watchingGoals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.Serializable
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.coroutines.CoroutineContext

/** What making many cards at once came to: how many were [created], and how many [skipped] as there already. */
data class CardsMade(val created: Int, val skipped: Int)

/** A card on the tab Karten, with its [topic], if it has one. */
data class CardRow(val card: Card, val topic: Topic?)

/**
 * The tab Karten of a language (SPEC.md §15): its [cards] by front, the [topics] of the language to filter them by, by
 * name, and how many new cards a day takes ([newPerDay]).
 */
data class CardsTab(val cards: List<CardRow>, val topics: List<Topic>, val newPerDay: Int)

/**
 * A language's workload on a day (SPEC.md §15): the cards [remaining], those due first, the longest due first, then the
 * new ones it takes, in the order they were made; those asked that day already ([doneToday]); and whether the language
 * is practised for the first time, no card of it asked before that day ([firstTime]).
 */
data class Workload(val remaining: List<Card>, val doneToday: List<Card>, val firstTime: Boolean = false) {
    /** Whether nothing of it is open any more. */
    val done: Boolean get() = remaining.isEmpty()

    companion object {
        /** Of [cards], all of one language that takes [newPerDay] new cards a day, the workload on [today]. */
        fun of(cards: List<Card>, newPerDay: Int, today: LocalDate): Workload {
            val due = cards.filter { it.dueOn(today) }.sortedWith(compareBy<Card> { it.due }.thenBy { it.id })
            val newToday = cards.count { it.firstReview == today }
            val new = cards.filter { it.new }.sortedBy { it.id }.take((newPerDay - newToday).coerceAtLeast(0))
            val askedBefore = cards.any { first -> first.firstReview?.isBefore(today) == true }
            return Workload(due + new, cards.filter { it.lastReview == today }, firstTime = !askedBefore)
        }
    }
}

/**
 * A round of practice (SPEC.md §15): the cards answered "Nochmal" in it, which come again at its end, in that order
 * ([again]). Kept by the screen, not saved: left, the round is over, and such a card is due the next day.
 */
data class Round(val again: List<Long> = emptyList()) : Serializable {
    /** The card to show of [workload]: the next of it, then those answered "Nochmal"; none once all are done. */
    fun current(workload: Workload): Card? =
        workload.remaining.firstOrNull() ?: again.firstNotNullOfOrNull { id -> workload.doneToday.find { it.id == id } }

    /**
     * Which card of how many the one shown is ("7 von 23"): counting those asked today already, so that a round taken up
     * again goes on where it was, and a card again in the round where it was first.
     */
    fun position(workload: Workload): Pair<Int, Int> {
        val total = workload.doneToday.size + workload.remaining.size
        val shown = current(workload) ?: return total to total
        val counted = if (workload.doneToday.any { it.id == shown.id }) 1 else 0
        return (workload.doneToday.size + 1 - counted) to total
    }

    /** After the card [cardId] was answered [rating]: "Nochmal" puts it at the end, anything else takes it out. */
    fun answered(cardId: Long, rating: Rating) =
        Round(again.filter { it != cardId } + listOfNotNull(cardId.takeIf { rating == Rating.Again }))
}

/**
 * The Vokabeltrainer of Sprachen (SPEC.md §15): the cards of each language, no two of one reading the same on their
 * front, planned by [Fsrs] in days of the phone's calendar: [clock]'s, in its time zone. Each day a language's workload
 * is what is due and up to its number of new cards; an archived language's cards stay, but are neither asked nor
 * counted. Every answer is kept on its card at once and logged ([CardReview]); the Karten-Ziele of goals follow how
 * many cards are sure, and a workload done by the day's answers is told ([onWorkloadDone]), for the routines it does. Cards are made one by one, or many at
 * once from a lesson's note or a text file read through [documents] and kept nowhere (#105). [database] is only called
 * on [io]; writes run to the end once started.
 */
class Flashcards(
    private val database: () -> HubDatabase,
    private val clock: Clock,
    private val documents: LessonDocuments,
    private val io: CoroutineContext = Dispatchers.IO,
    private val onWorkloadDone: suspend (languageId: Long) -> Unit = {},
) {
    private val dao get() = database().cards()
    private val languages get() = database().languages()

    /** Today, by the phone's calendar, the day an answer counts on. */
    private fun today(): LocalDate = clock.instant().atZone(clock.zone).toLocalDate()

    /** The name of the language [languageId], as it changes; empty once it is gone. */
    fun languageName(languageId: Long): Flow<String> = flow {
        emitAll(languages.observeLanguages().map { all -> all.find { it.id == languageId }?.name.orEmpty() })
    }.flowOn(io)

    /** The tab Karten of the language [languageId], as it changes; none once it is gone. */
    fun tab(languageId: Long): Flow<CardsTab?> = flow {
        val tab = combine(languages.observeLanguages(), dao.observeOf(languageId), languages.observeTopics()) { all, cards, topics ->
            val language = all.find { it.id == languageId } ?: return@combine null
            val own = topics.filter { it.languageId == languageId }
            val byId = own.associateBy { it.id }
            CardsTab(
                cards = cards.sortedWith(compareBy(InputText.byName) { it.front }).map { CardRow(it, it.topicId?.let(byId::get)) },
                topics = own.sortedWith(compareBy(InputText.byName) { it.name }),
                newPerDay = language.newCardsPerDay,
            )
        }
        emitAll(tab)
    }.flowOn(io)

    /** The workload of the language [languageId] on [today], as it changes; none while it is archived, or once it is gone. */
    fun workload(languageId: Long, today: LocalDate): Flow<Workload?> = flow {
        val workload = combine(languages.observeLanguages(), dao.observeOf(languageId)) { all, cards ->
            all.find { it.id == languageId }?.takeIf { !it.archived }?.let { Workload.of(cards, it.newCardsPerDay, today) }
        }
        emitAll(workload)
    }.flowOn(io)

    /** The workload on [today] of each language not archived that has cards, by its id, as they change. */
    fun workloads(today: LocalDate): Flow<Map<Long, Workload>> = flow {
        val workloads = combine(languages.observeLanguages(), dao.observeAll()) { all, cards ->
            val byLanguage = cards.groupBy { it.languageId }
            all.filter { !it.archived && it.id in byLanguage }
                .associate { it.id to Workload.of(byLanguage.getValue(it.id), it.newCardsPerDay, today) }
        }
        emitAll(workloads)
    }.flowOn(io)

    /** A new card of the language [languageId], as [draft] has it; refused, with nothing saved, for its problems. */
    suspend fun create(languageId: Long, draft: CardDraft): Saving<CardProblem> = write {
        database().withTransaction {
            val clean = draft.cleaned()
            val problems = problemsOf(languageId, clean, except = null)
            if (problems.isNotEmpty()) return@withTransaction Saving.Refused(problems)
            Saving.Saved(dao.insert(clean.applyTo(Card(languageId = languageId, front = "", back = ""))))
        }
    }

    /** Changes the card [id] to what [draft] has, what was planned for it staying; refused as [create] is. */
    suspend fun save(id: Long, draft: CardDraft): Saving<CardProblem> = write {
        database().withTransaction {
            val saved = dao.card(id) ?: return@withTransaction Saving.Saved(id)
            val clean = draft.cleaned()
            val problems = problemsOf(saved.languageId, clean, except = id)
            if (problems.isNotEmpty()) return@withTransaction Saving.Refused(problems)
            dao.update(clean.applyTo(saved))
            Saving.Saved(id)
        }
    }

    /**
     * Of [drafts] to make as cards of the language [languageId], in their order, whether each is taken: its front reads as
     * that of a card of the language, or of one before it among them.
     */
    suspend fun taken(languageId: Long, drafts: List<CardDraft>): List<Boolean> = withContext(io) { takenNow(languageId, drafts) }

    /** [taken], on the thread it is called on, as a transaction needs it. */
    private suspend fun takenNow(languageId: Long, drafts: List<CardDraft>): List<Boolean> {
        val fronts = dao.of(languageId).map { InputText.comparable(it.front) }.toMutableSet()
        return drafts.map { !fronts.add(InputText.comparable(it.cleaned().front)) }
    }

    /**
     * Makes [drafts] cards of the language [languageId], new ones, all with the topic [topicId] if it is of the language,
     * else with none; those [taken] are skipped and counted. All at once or, should one of them not be fit to save, none.
     */
    suspend fun createAll(languageId: Long, drafts: List<CardDraft>, topicId: Long?): CardsMade = write {
        val clean = drafts.map { it.cleaned() }
        if (clean.any { it.problems.isNotEmpty() }) return@write CardsMade(created = 0, skipped = 0)
        database().withTransaction {
            val topic = topicId?.takeIf { languages.topic(it)?.languageId == languageId }
            val taken = takenNow(languageId, clean)
            val made = clean.filterIndexed { i, _ -> !taken[i] }
            made.forEach { dao.insert(it.copy(topicId = topic).applyTo(Card(languageId = languageId, front = "", back = ""))) }
            CardsMade(created = made.size, skipped = clean.size - made.size)
        }
    }

    /** The cards of the text file at [address], picked in Android's file dialog: read, never kept. */
    suspend fun readFile(address: String): CardFile = withContext(io) {
        val picked = documents.read(address, CardImport.FILE_MAX_BYTES + 1) ?: return@withContext CardFile.NotText
        CardImport.fromFile(picked.bytes)
    }

    /** The card [id] as it is saved; none once it is gone. */
    suspend fun card(id: Long): Card? = withContext(io) { dao.card(id) }

    /** Deletes the card [id], and the answers to it. The screen asks first. */
    suspend fun delete(id: Long) = write {
        database().withTransaction {
            val card = dao.card(id) ?: return@withTransaction
            database().watchingGoals(today()) { watch ->
                dao.goalsOf(card.languageId).forEach { watch.cardGoal(it.goalId) }
                dao.delete(id)
            }
        }
    }

    /** How many new cards a day the language [languageId] takes, kept within [Language.NEW_CARDS]. */
    suspend fun setNewPerDay(languageId: Long, count: Int) = write {
        languages.setNewCardsPerDay(languageId, count.coerceIn(Language.NEW_CARDS))
    }

    /**
     * The card [cardId] answered [rating] now: planned anew by [Fsrs], from the days since it was last asked; "Nochmal"
     * makes it due the next day, as it comes again in the round only. Kept at once, and logged.
     */
    suspend fun answer(cardId: Long, rating: Rating) = write {
        val today = today()
        val languageId = database().withTransaction {
            val card = dao.card(cardId) ?: return@withTransaction null
            val planned = plan(card, rating, today)
            database().watchingGoals(today) { watch ->
                // How sure its cards are may reach a Karten-Ziel, or fall below it again (#106).
                dao.goalsOf(card.languageId).forEach { watch.cardGoal(it.goalId) }
                dao.update(
                    card.copy(
                        stability = planned.memory.stability,
                        difficulty = planned.memory.difficulty,
                        firstReview = card.firstReview ?: today,
                        lastReview = today,
                        due = today.plusDays(planned.interval.toLong()),
                        interval = planned.interval,
                    ),
                )
            }
            dao.insert(CardReview(cardId = cardId, at = clock.instant(), grade = rating.grade))
            card.languageId
        } ?: return@write
        if (workloadDoneByAnswers(languageId, today)) onWorkloadDone(languageId)
    }

    /**
     * Whether the workload of the language [languageId] on [today] is done by what was answered that day: nothing of it
     * open, and something asked; a day with nothing due and nothing new is not one. Never for an archived language.
     */
    private suspend fun workloadDoneByAnswers(languageId: Long, today: LocalDate): Boolean {
        val language = languages.language(languageId)?.takeIf { !it.archived } ?: return false
        val workload = Workload.of(dao.of(languageId), language.newCardsPerDay, today)
        return workload.done && workload.doneToday.isNotEmpty()
    }

    /** The interval in days each answer to [card] would give now, as each button shows it and [answer] then plans it. */
    fun intervals(card: Card): Map<Rating, Int> = Rating.entries.associateWith { plan(card, it, today()).interval }

    /** What answering [card] [rating] on [today] plans: "Nochmal" the next day, whatever FSRS would give. */
    private fun plan(card: Card, rating: Rating, today: LocalDate): Scheduled {
        val days = card.lastReview?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) } ?: 0
        val scheduled = Fsrs.review(card.memory, days, rating)
        return if (rating == Rating.Again) scheduled.copy(interval = 1) else scheduled
    }

    /** What keeps [draft] from being saved as a card of the language [languageId], the card [except] aside. */
    private suspend fun problemsOf(languageId: Long, draft: CardDraft, except: Long?): Set<CardProblem> = buildSet {
        addAll(draft.problems)
        if (dao.of(languageId).any { it.id != except && InputText.readTheSame(it.front, draft.front) }) add(CardProblem.FrontTaken)
        val topicId = draft.topicId
        if (topicId != null && languages.topic(topicId)?.languageId != languageId) add(CardProblem.TopicNotOffered)
    }

    /** Writes on [io], to the end once started. */
    private suspend fun <T> write(block: suspend () -> T): T = withContext(io + NonCancellable) { block() }
}
