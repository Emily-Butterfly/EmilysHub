package de.emilyshub.languages

import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InputText
import de.emilyshub.data.Saving
import de.emilyshub.wishes.GoalWatch
import de.emilyshub.wishes.watchingGoals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.Serializable
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.coroutines.CoroutineContext

/** What making many cards at once came to: how many were [created], and how many [skipped] as there already. */
data class CardsMade(val created: Int, val skipped: Int)

/** A card on the tab Karten, with its [topic], if it has one, and whether it hakt ([stuck], #149). */
data class CardRow(val card: Card, val topic: Topic?, val stuck: Boolean = false) {
    /**
     * Whether [query] finds it (SPEC.md §15; #107): every word typed in its front, back, transcription, note or
     * Beispielsatz (#155), in whatever case, and Arabic with or without its vowel marks, as the search over all areas
     * compares; anything for nothing typed.
     */
    fun matches(query: String): Boolean {
        val words = InputText.searchable(InputText.singleLine(query)).split(' ').filter(String::isNotEmpty)
        val text = InputText.searchable(listOf(card.front, card.back, card.transcription, card.note, card.example).joinToString(" "))
        return words.all { it in text }
    }
}

/** How many [cards] a topic has, and how many of them are [sure] (SPEC.md §15; #107). */
data class TopicCards(val cards: Int, val sure: Int) {
    companion object {
        /** What the topics [topicIds] of [counts] come to together, as an exam's topics do. */
        fun sumOf(topicIds: Collection<Long>, counts: Map<Long, TopicCards>): TopicCards {
            val own = topicIds.mapNotNull(counts::get)
            return TopicCards(own.sumOf { it.cards }, own.sumOf { it.sure })
        }
    }
}

/**
 * A language's cards as its evaluation tells them (SPEC.md §15; #107): all of them ([total]), those [sure], those
 * [dueToday], those that hakt ([stuck], #149), how regularly they were [practised] and, unless the language is archived,
 * what is due in the days to come ([preview], #150).
 */
data class CardsSummary(
    val total: Int,
    val sure: Int,
    val dueToday: Int,
    val stuck: Int,
    val practised: Practised,
    val preview: DuePreview?,
)

/**
 * The tab Karten of a language (SPEC.md §15): its [cards] by front, the [topics] of the language to filter them by, by
 * name, how many new cards a day takes ([newPerDay]) and how many ways a day's workload asks at most ([dailyLimit], #156).
 */
data class CardsTab(val cards: List<CardRow>, val topics: List<Topic>, val newPerDay: Int, val dailyLimit: Int? = null)

/**
 * A language's workload on a day (SPEC.md §15): the cards [remaining], each the way it is asked ([Prompt]), those due
 * first, the longest due first, then the new ones it takes, in the order they were made, each from its front before its
 * back; those asked that day already ([doneToday]); and whether the language is practised for the first time, no card
 * of it asked before that day ([firstTime]). A card asked both ways comes one way a day at most (#141). Under a limit
 * (#156), the due ones beyond it [waiting].
 */
data class Workload(val remaining: List<Prompt>, val doneToday: List<Prompt>, val firstTime: Boolean = false, val waiting: Int = 0) {
    /** Whether nothing of it is open any more. */
    val done: Boolean get() = remaining.isEmpty()

    /** How many of [remaining] are new, a way of a card never asked ([Prompt.new]); each way of a card counts for itself (#143). */
    val newCount: Int get() = remaining.count { it.new }

    /** How many of [remaining] are due, all that are not new (#143). */
    val dueCount: Int get() = remaining.size - newCount

    companion object {
        /**
         * Of [cards], all of one language that takes [newPerDay] new cards a day, the workload on [today]. With a
         * [limit] (#156), the ways it takes on the day, those it [asked] already counted, each once: the longest due
         * first up to it, and new ones only while nothing due waits beyond it, as far as it reaches.
         */
        fun of(cards: List<Card>, newPerDay: Int, today: LocalDate, limit: Int? = null, asked: AskedToday = AskedToday()): Workload {
            val prompts = cards.flatMap { it.prompts }
            val askedToday = prompts.filter { it.lastReview == today }
            // A card asked one way today is not asked the other (#141).
            val busy = askedToday.map { it.id }.toMutableSet()
            val allDue = prompts.filter { it.dueOn(today) }.sortedWith(Prompt.BY_DUE).filter { busy.add(it.id) }
            val keys = prompts.map { it.key }.toSet()
            val room = limit?.let { (it - asked.inWorkload.count { key -> key in keys }).coerceAtLeast(0) } ?: Int.MAX_VALUE
            val due = allDue.take(room)
            val waiting = allDue.size - due.size
            val newToday = prompts.count { it.firstReview == today }
            // While a backlog waits beyond the limit, no new ones.
            val newRoom = if (waiting > 0) 0 else minOf((newPerDay - newToday).coerceAtLeast(0), room - due.size)
            val new = prompts.filter { it.new }.sortedWith(Prompt.BY_MADE)
                .filter { busy.add(it.id) }
                .take(newRoom)
            // Asked on a day before, in the day's workload or in a topic.
            val askedBefore = prompts.any { it.lastReview?.isBefore(today) == true }
            return Workload(due + new, askedToday, firstTime = !askedBefore, waiting = waiting)
        }

        /**
         * A topic or an exam practised on [today] (SPEC.md §15; #107, #148): all its [cards] the ways they are asked,
         * in the order [Flashcards.practice] gives them, those asked today or in the [round] already done, so that each comes
         * once, again only after "Nochmal", and a round taken up again goes on where it was; of a card asked both ways,
         * one way a day (#141).
         */
        fun ofSelection(cards: List<Prompt>, round: Round, today: LocalDate): Workload {
            val asked = round.asked.toSet() + cards.filter { it.lastReview == today }.map { it.key }
            val busy = cards.filter { it.key in asked }.map { it.id }.toMutableSet()
            return Workload(cards.filter { it.key !in asked && busy.add(it.id) }, cards.filter { it.key in asked })
        }
    }
}

/**
 * How a round came out (SPEC.md §15; #147): of the ways asked that day, how many were [known] at their first answer of
 * that day, and how many not ([again], "Nochmal"); one known only when asked again in the round counts as "Nochmal".
 */
data class RoundBalance(val known: Int, val again: Int) {
    companion object {
        /** Of the ways [asked], by their first answer on [today] in the log ([reviews]), told in [zone]; one with none there is not counted. */
        fun of(asked: Collection<PromptKey>, reviews: List<CardReview>, today: LocalDate, zone: ZoneId): RoundBalance {
            val firstGrades = reviews.filter { it.at.atZone(zone).toLocalDate() == today }
                .sortedWith(compareBy(CardReview::at, CardReview::id))
                .groupBy { PromptKey(it.cardId, it.reverse) }
                .mapValues { (_, answers) -> answers.first().grade }
            val grades = asked.toSet().mapNotNull(firstGrades::get)
            val again = grades.count { it == Rating.Again.grade }
            return RoundBalance(known = grades.size - again, again = again)
        }
    }
}

/**
 * A language with something still [open] today, by its [id] and [name]: practised next once one is done (SPEC.md §15;
 * #147), and offered by Heute (#158).
 */
data class OpenLanguage(val id: Long, val name: String, val open: Int)

/**
 * An answer given (SPEC.md §15; #145): to the card asked the way [before] has it, as it was planned before, its entry in
 * the log ([reviewId]), and whether it did the day's workload and told so ([didWorkload]); what taking it back needs,
 * kept by the practice as long as it is open.
 */
data class Answered(val before: Prompt, val reviewId: Long, val didWorkload: Boolean = false) : Serializable

/**
 * A round of practice (SPEC.md §15): the cards answered "Nochmal" in it, which come again at its end, in that order
 * ([again]), and all those it [asked], for a topic or an exam practised (SPEC.md §15; #107, #148). Kept by the screen, not saved: left,
 * the round is over, and such a card is due the next day.
 */
data class Round(val again: List<PromptKey> = emptyList(), val asked: List<PromptKey> = emptyList()) : Serializable {
    /** The card to show of [workload], the way it is asked: the next of it, then those answered "Nochmal"; none once all are done. */
    fun current(workload: Workload): Prompt? =
        workload.remaining.firstOrNull() ?: again.firstNotNullOfOrNull { key -> workload.doneToday.find { it.key == key } }

    /**
     * Which card of how many the one shown is ("7 von 23"): counting those asked today already, so that a round taken
     * up again goes on where it was, and a card again in the round where it was first.
     */
    fun position(workload: Workload): Pair<Int, Int> {
        val total = workload.doneToday.size + workload.remaining.size
        val shown = current(workload) ?: return total to total
        val counted = if (workload.doneToday.any { it.key == shown.key }) 1 else 0
        return (workload.doneToday.size + 1 - counted) to total
    }

    /** After [prompt] was answered [rating]: "Nochmal" puts it at the end, anything else takes it out. */
    fun answered(prompt: Prompt, rating: Rating): Round {
        val key = prompt.key
        return Round(
            again = again.filter { it != key } + listOfNotNull(key.takeIf { rating == Rating.Again }),
            asked = if (key in asked) asked else asked + key,
        )
    }
}

/**
 * The Vokabeltrainer of Sprachen (SPEC.md §15): the cards of each language, no two of one reading the same on their
 * front, planned by [Fsrs] in days of the phone's calendar: [clock]'s, in its time zone. Each day a language's workload
 * is what is due and up to its number of new cards; an archived language's cards stay, but are neither asked nor
 * counted. Every answer is kept on its card at once and logged ([CardReview]); the Karten-Ziele of goals follow how
 * many cards are sure, and the answer that does a day's workload tells so ([onWorkloadDone]), for the routines it does.
 * Cards are made one by one, or many at once from a lesson's note or a text file read through [documents] and kept
 * nowhere (#105). [database] is only called on [io]; writes run to the end once started.
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

    /** The voice that reads the cards of the language [languageId] aloud (SPEC.md §15; #157), as it changes; none if none is chosen. */
    fun voice(languageId: Long): Flow<String?> = flow {
        emitAll(languages.observeLanguages().map { all -> all.find { it.id == languageId }?.voice })
    }.flowOn(io)

    /**
     * The languages not archived that an inbox entry can become a card of (SPEC.md §16; #152), as they change: the one
     * the newest card of them was made for first, the others by name.
     */
    fun cardLanguages(): Flow<List<Language>> = flow {
        val offered = combine(languages.observeLanguages(), dao.observeAll()) { all, cards ->
            val open = all.filterNot { it.archived }
            val ids = open.map { it.id }.toSet()
            val last = cards.filter { it.languageId in ids }.maxByOrNull { it.id }?.languageId
            open.sortedWith(compareBy<Language> { it.id != last }.then(compareBy(InputText.byName) { it.name }))
        }
        emitAll(offered)
    }.flowOn(io)

    /**
     * How the language [languageId] asks its cards (SPEC.md §15; #153), as it changes: whether the answer is typed
     * rather than uncovered, the language a keyboard is asked for to type in it, and whether its side is asked without
     * its vowel marks (#154); uncovered, with the marks, once it is gone.
     */
    fun answering(languageId: Long): Flow<Answering> = flow {
        emitAll(
            languages.observeLanguages().map { all ->
                all.find { it.id == languageId }?.let { Answering(it.typedAnswers, Language.keyboardLocale(it.name), it.hideVowelMarks) } ?: Answering()
            }.distinctUntilChanged(),
        )
    }.flowOn(io)

    /** Whether the practice of the language [languageId] asks its side without its vowel marks (#154). */
    suspend fun setHideVowelMarks(languageId: Long, hide: Boolean) = write { languages.setHideVowelMarks(languageId, hide) }

    /** Whether the practice of the language [languageId] asks for the answer typed rather than uncovered (#153). */
    suspend fun setTypedAnswers(languageId: Long, typed: Boolean) = write { languages.setTypedAnswers(languageId, typed) }

    /** The tab Karten of the language [languageId], as it changes; none once it is gone. */
    fun tab(languageId: Long): Flow<CardsTab?> = flow {
        val tab = combine(
            languages.observeLanguages(), dao.observeOf(languageId), languages.observeTopics(), dao.observeReviewsOf(languageId),
        ) { all, cards, topics, reviews ->
            val language = all.find { it.id == languageId } ?: return@combine null
            val own = topics.filter { it.languageId == languageId }
            val byId = own.associateBy { it.id }
            val stuck = Stuck.cards(reviews)
            CardsTab(
                cards = cards.sortedWith(compareBy(InputText.byName) { it.front }).map { CardRow(it, it.topicId?.let(byId::get), it.id in stuck) },
                topics = own.sortedWith(compareBy(InputText.byName) { it.name }),
                newPerDay = language.newCardsPerDay,
                dailyLimit = language.dailyLimit,
            )
        }
        emitAll(tab)
    }.flowOn(io)

    /**
     * The workload of the language [languageId] on [today], as it changes; none while it is archived, or once it is
     * gone.
     */
    fun workload(languageId: Long, today: LocalDate): Flow<Workload?> = flow {
        val workload = observeWorkloads(today).map { it.workloads[languageId] }
        emitAll(workload)
    }.flowOn(io)

    /**
     * How many cards of each topic of the language [languageId] there are, and how many are sure, by topic (SPEC.md
     * §15; #107).
     */
    fun topicCounts(languageId: Long): Flow<Map<Long, TopicCards>> = flow {
        emitAll(
            dao.observeOf(languageId).map { cards ->
                cards.filter { it.topicId != null }.groupBy { checkNotNull(it.topicId) }
                    .mapValues { (_, own) -> TopicCards(own.size, own.count { it.sure }) }
            },
        )
    }.flowOn(io)

    /**
     * All the cards of [selection] to practise on [today] (SPEC.md §15): of a topic (#107), or of all the topics of an
     * exam (#148), done or not, of an exam taken or not, each the ways it is asked (#141): those due first, the longest
     * due first, then the new ones in the order made, then the others, the soonest due first. None once the topic or
     * exam is gone or its language archived.
     */
    fun practice(selection: CardSelection, today: LocalDate): Flow<List<Prompt>?> = flow {
        val practice = combine(
            languages.observeTopics(),
            languages.observeExams(),
            languages.observeLanguages(),
            dao.observeAll(),
        ) { topics, exams, all, cards ->
            val (languageId, topicIds) = when (selection) {
                is CardSelection.OfTopic -> topics.find { it.id == selection.topicId }?.let { it.languageId to setOf(it.id) }
                is CardSelection.OfExam -> exams.find { it.id == selection.examId }
                    ?.let { exam -> exam.languageId to topics.filter { it.examId == exam.id }.map { it.id }.toSet() }
            } ?: return@combine null
            all.find { it.id == languageId }?.takeIf { !it.archived } ?: return@combine null
            val own = cards.filter { it.topicId in topicIds }.flatMap { it.prompts }
            val (due, notDue) = own.partition { it.dueOn(today) }
            val (new, later) = notDue.partition { it.new }
            due.sortedWith(Prompt.BY_DUE) + new.sortedWith(Prompt.BY_MADE) + later.sortedWith(Prompt.BY_DUE)
        }
        emitAll(practice)
    }.flowOn(io)

    /**
     * The cards of the language [languageId] as its evaluation counts them on [today] (SPEC.md §15; #107), with how
     * regularly they were practised and what is due in the days to come (#150), as they change.
     */
    fun summary(languageId: Long, today: LocalDate): Flow<CardsSummary> = flow {
        val summary = combine(languages.observeLanguages(), dao.observeOf(languageId), dao.observeReviewsOf(languageId)) { all, cards, reviews ->
            val language = all.find { it.id == languageId }
            val newPerDay = language?.newCardsPerDay ?: Language.NEW_CARDS_DEFAULT
            CardsSummary(
                total = cards.size,
                sure = cards.count { it.sure },
                dueToday = Workload.of(cards, newPerDay, today).remaining.size,
                stuck = Stuck.cards(reviews).size,
                practised = Practised.of(reviews, today, clock.zone),
                preview = DuePreview.of(cards, today).takeIf { language?.archived != true },
            )
        }
        emitAll(summary)
    }.flowOn(io)

    /** The workload on [today] of each language not archived that has cards, by its id, as they change. */
    fun workloads(today: LocalDate): Flow<Map<Long, Workload>> = flow {
        emitAll(observeWorkloads(today).map { day -> day.workloads.filterKeys { it in day.withCards } })
    }.flowOn(io)

    /**
     * The workloads on [today] of all languages not archived, as they change: under their limits, the overall one shared
     * out by backlog (#156), so that each depends on all the others.
     */
    private fun observeWorkloads(today: LocalDate): Flow<WorkloadsOn> = combine(
        languages.observeLanguages(), dao.observeAll(), dao.observeReviewsSince(sinceBefore(today)), dao.observeSettings(),
    ) { all, cards, reviews, settings -> workloadsOn(all, cards, reviews, settings, today) }

    /** The workloads on [today] as they stand now, as [observeWorkloads] has them. */
    private suspend fun workloadsNow(today: LocalDate): WorkloadsOn =
        workloadsOn(languages.languages(), dao.all(), dao.reviewsSince(sinceBefore(today)), dao.settings(), today)

    /** The workloads on [today] of the languages [all], of their [cards], with the [reviews] and the overall limit of [settings]. */
    private fun workloadsOn(all: List<Language>, cards: List<Card>, reviews: List<CardReview>, settings: CardSettings?, today: LocalDate) =
        WorkloadsOn(
            DailyLimits.workloads(all, cards, reviews, settings?.dailyLimit, today, clock.zone),
            withCards = cards.map { it.languageId }.toSet(),
        )

    /** From when the answers of [today] are read: from a day before, which is safe whatever the time zone; [AskedToday] keeps those of the day. */
    private fun sinceBefore(today: LocalDate): Instant = today.minusDays(1).atStartOfDay(clock.zone).toInstant()

    /**
     * The languages not archived with something still open on [today] but [except], the most open first, by name if
     * alike: those Heute offers to practise (#158), the first of them after another language's round (#147).
     */
    fun openLanguages(today: LocalDate, except: Long? = null): Flow<List<OpenLanguage>> = flow {
        val open = combine(languages.observeLanguages(), observeWorkloads(today)) { all, day ->
            all.filter { !it.archived && it.id != except }
                .map { OpenLanguage(it.id, it.name, day.workloads[it.id]?.remaining?.size ?: 0) }
                .filter { it.open > 0 }
                .sortedWith(compareByDescending(OpenLanguage::open).then(compareBy(InputText.byName, OpenLanguage::name)))
        }
        emitAll(open)
    }.flowOn(io)

    /**
     * The language to practise after [languageId] on [today] (SPEC.md §15; #147): of the others not archived, the one
     * with the most still open, by name if alike; none while nothing is open elsewhere.
     */
    fun nextLanguage(languageId: Long, today: LocalDate): Flow<OpenLanguage?> =
        openLanguages(today, except = languageId).map { it.firstOrNull() }

    /** Whether the card [cardId] hakt (SPEC.md §15; #149), for its editor to say so. */
    suspend fun stuck(cardId: Long): Boolean = withContext(io) { Stuck.cards(dao.reviewsOf(cardId)).isNotEmpty() }

    /**
     * How the ways [asked] came out on [today], from the log (SPEC.md §15; #147): each by its first answer of the day,
     * the day the practice shows.
     */
    suspend fun balance(asked: Collection<PromptKey>, today: LocalDate): RoundBalance = withContext(io) {
        val reviews = asked.map { it.cardId }.distinct().flatMap { dao.reviewsOf(it) }
        RoundBalance.of(asked, reviews, today, clock.zone)
    }

    /** A new card of the language [languageId], as [draft] has it; refused, with nothing saved, for its problems. */
    suspend fun create(languageId: Long, draft: CardDraft): Saving<CardProblem> = write {
        database().withTransaction {
            val clean = draft.cleaned()
            val problems = problemsOf(languageId, clean, except = null)
            if (problems.isNotEmpty()) return@withTransaction Saving.Refused(problems)
            Saving.Saved(dao.insert(clean.applyTo(Card(languageId = languageId, front = "", back = ""))))
        }
    }

    /**
     * Changes the card [id] to what [draft] has, what was planned for it staying; refused as [create] is. No longer
     * asked both ways, what was planned and logged for its back is gone (#141); as that changes whether it is sure and
     * what the day's workload holds, the Karten-Ziele follow, and a workload done by it tells so: of any language, as
     * a backlog changed shares the overall limit out anew (#156).
     */
    suspend fun save(id: Long, draft: CardDraft): Saving<CardProblem> = tellingWorkloadsDone {
        val today = today()
        database().withTransaction {
            val saved = dao.card(id) ?: return@withTransaction Saving.Saved(id)
            val clean = draft.cleaned()
            val problems = problemsOf(saved.languageId, clean, except = id)
            if (problems.isNotEmpty()) return@withTransaction Saving.Refused(problems)
            database().watchingGoals(today) { watch ->
                watchGoalsOf(saved.languageId, watch)
                dao.update(clean.applyTo(saved))
                if (saved.bothWays && !clean.bothWays) dao.deleteReverseReviews(id)
            }
            Saving.Saved(id)
        }
    }

    /**
     * Of [drafts] to make as cards of the language [languageId], in their order, whether each is taken: its front reads
     * as that of a card of the language, or of one before it among them.
     */
    suspend fun taken(languageId: Long, drafts: List<CardDraft>): List<Boolean> = withContext(io) { takenNow(languageId, drafts) }

    /**
     * Whether another card of the language [languageId] than [except] has a front reading as [front] does, as saving
     * would refuse it ("Diese Karte gibt es schon"): what the editor tells while the front is typed. Nothing typed is
     * taken by none.
     */
    suspend fun frontTaken(languageId: Long, front: String, except: Long?): Boolean = withContext(io) {
        val clean = InputText.singleLine(front)
        InputText.isVisible(clean) && dao.of(languageId).any { it.id != except && InputText.readTheSame(it.front, clean) }
    }

    /** [taken], on the thread it is called on, as a transaction needs it. */
    private suspend fun takenNow(languageId: Long, drafts: List<CardDraft>): List<Boolean> {
        val fronts = dao.of(languageId).map { InputText.comparable(it.front) }.toMutableSet()
        return drafts.map { !fronts.add(InputText.comparable(it.cleaned().front)) }
    }

    /**
     * Makes [drafts] cards of the language [languageId], new ones, all with the topic [topicId] if it is of the
     * language, else with none, and all asked [bothWays] or not (#141); those [taken] are skipped and counted. All at
     * once or, should one of them not be fit to save, none.
     */
    suspend fun createAll(languageId: Long, drafts: List<CardDraft>, topicId: Long?, bothWays: Boolean = false): CardsMade = write {
        val clean = drafts.map { it.cleaned().copy(bothWays = bothWays) }
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

    /**
     * Deletes the card [id], and the answers to it. The screen asks first. A workload done by it tells so, of any
     * language, as its backlog shares the overall limit out (#156).
     */
    suspend fun delete(id: Long) = tellingWorkloadsDone {
        database().withTransaction {
            val card = dao.card(id) ?: return@withTransaction
            database().watchingGoals(today()) { watch ->
                watchGoalsOf(card.languageId, watch)
                dao.delete(id)
            }
        }
    }

    /**
     * How many ways of the cards of the language [languageId] a day's workload asks at most (#156), kept within
     * [DailyLimits.PER_LANGUAGE]; none for no limit. A workload done by it tells so, as by an answer.
     */
    suspend fun setDailyLimit(languageId: Long, limit: Int?) = tellingWorkloadsDone {
        languages.setDailyLimit(languageId, limit?.coerceIn(DailyLimits.PER_LANGUAGE))
    }

    /** The overall limit (#156), as it changes; none for no limit. */
    fun overallLimit(): Flow<Int?> = flow { emitAll(dao.observeSettings().map { it?.dailyLimit }) }.flowOn(io)

    /**
     * How many ways of the cards of all languages together the day's workloads ask at most (#156), kept within
     * [DailyLimits.OVERALL]; none for no limit. Workloads done by it tell so.
     */
    suspend fun setOverallLimit(limit: Int?) = tellingWorkloadsDone {
        dao.setSettings(CardSettings(dailyLimit = limit?.coerceIn(DailyLimits.OVERALL)))
    }

    /**
     * Does [change], and tells each workload of today that it made done (#106), of whichever language: a change to one
     * may share the overall limit out anew among all (#156).
     */
    private suspend fun <T> tellingWorkloadsDone(change: suspend () -> T): T = write {
        val today = today()
        val before = workloadsNow(today).workloads
        val changed = change()
        val after = workloadsNow(today).workloads
        after.filter { (id, workload) -> workload.done && before[id]?.done == false }.keys.forEach { onWorkloadDone(it) }
        changed
    }

    /** How many new cards a day the language [languageId] takes, kept within [Language.NEW_CARDS]. */
    suspend fun setNewPerDay(languageId: Long, count: Int) = write {
        languages.setNewCardsPerDay(languageId, count.coerceIn(Language.NEW_CARDS))
    }

    /**
     * The card [cardId] answered [rating] now: planned anew by [Fsrs], from the days since it was last asked; "Nochmal"
     * makes it due the next day, as it comes again in the round only. Kept at once, and logged. A new one asked in a
     * topic practised (not [countsAsNew]) leaves the new cards of the day as they were. [reverse], the answer is to it
     * asked from its back, planned on its own (#141); nothing if it is not asked so. What it was before, to take it back
     * ([takeBack]); none if nothing was answered. Not [tellsWorkloadDone], it does not tell the workload done: a practice
     * tells so once, also after an answer taken back and given again (#145).
     */
    suspend fun answer(
        cardId: Long,
        rating: Rating,
        countsAsNew: Boolean = true,
        reverse: Boolean = false,
        tellsWorkloadDone: Boolean = true,
    ): Answered? = write {
        val today = today()
        val key = PromptKey(cardId, reverse)
        val languageOf = dao.card(cardId)?.languageId ?: return@write null
        // Only an answer to a card of the day's workload that does it tells: not those asked again after it, as
        // "Nochmal" brings them, nor one asked in a topic on a day with nothing due and nothing new (#106, #107).
        val ofWorkload = workloadOf(languageOf, today)?.remaining.orEmpty().any { it.key == key }
        val answered = database().withTransaction {
            val prompt = dao.card(cardId)?.prompts?.find { it.reverse == reverse } ?: return@withTransaction null
            val card = prompt.card
            val planned = plan(prompt, rating, today)
            database().watchingGoals(today) { watch ->
                // How sure its cards are may reach a Karten-Ziel, or fall below it again (#106).
                watchGoalsOf(card.languageId, watch)
                dao.update(
                    prompt.planned(
                        memory = planned.memory,
                        // A new one counts against the new cards of the day, but not practised in its topic (SPEC.md
                        // §15; #107); one first asked so never does, as it is new no more.
                        firstReview = prompt.firstReview ?: today.takeIf { countsAsNew && prompt.new },
                        lastReview = today,
                        due = today.plusDays(planned.interval.toLong()),
                        interval = planned.interval,
                    ),
                )
            }
            val review = CardReview(
                cardId = cardId,
                at = clock.instant(),
                grade = rating.grade,
                reverse = reverse,
                // Kept in the day's backlog, and counted against a limit only as the day's workload asked it (#156).
                wasDue = prompt.dueOn(today),
                inWorkload = countsAsNew && ofWorkload,
            )
            Answered(prompt, dao.insert(review))
        } ?: return@write null
        val languageId = answered.before.card.languageId
        val didWorkload = ofWorkload && workloadOf(languageId, today)?.done == true
        if (didWorkload && tellsWorkloadDone) onWorkloadDone(languageId)
        answered.copy(didWorkload = didWorkload)
    }

    /**
     * Takes back the [answer] given (SPEC.md §15; #145): its card planned that way as it was before, the other way and
     * all else as they are, and its entry in the log deleted; Karten-Ziele follow. A routine the answer did stays done.
     * Nothing once the card is gone; its back no longer asked, only the entry goes.
     */
    suspend fun takeBack(answer: Answered) = write {
        database().withTransaction {
            val card = dao.card(answer.before.id) ?: return@withTransaction
            if (!answer.before.reverse || card.bothWays) {
                database().watchingGoals(today()) { watch ->
                    watchGoalsOf(card.languageId, watch)
                    dao.update(answer.before.restoredOnto(card))
                }
            }
            dao.deleteReview(answer.reviewId)
        }
    }

    /** Names the Karten-Ziele on the cards of the language [languageId] to [watch], as how sure they are may change. */
    private suspend fun watchGoalsOf(languageId: Long, watch: GoalWatch) =
        dao.goalsOf(languageId).forEach { watch.cardGoal(it.goalId) }

    /** The workload of the language [languageId] on [today] as it stands; none for an archived language. */
    private suspend fun workloadOf(languageId: Long, today: LocalDate): Workload? = workloadsNow(today).workloads[languageId]

    /**
     * The interval in days each answer to [prompt] would give now, as each button shows it and [answer] then plans it.
     */
    fun intervals(prompt: Prompt): Map<Rating, Int> = Rating.entries.associateWith { plan(prompt, it, today()).interval }

    /** What answering [prompt] [rating] on [today] plans: "Nochmal" the next day, whatever FSRS would give. */
    private fun plan(prompt: Prompt, rating: Rating, today: LocalDate): Scheduled {
        val days = prompt.lastReview?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) } ?: 0
        val scheduled = Fsrs.review(prompt.memory, days, rating)
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

/**
 * How a language asks its cards: [typed], the answer is typed rather than uncovered, with a keyboard in [locale] where
 * known (#153); [hideVowelMarks], its side is asked without its vowel marks (#154).
 */
data class Answering(val typed: Boolean = false, val locale: Locale? = null, val hideVowelMarks: Boolean = false)

/** The [workloads] of a day of all languages not archived, and the languages [withCards]. */
private data class WorkloadsOn(val workloads: Map<Long, Workload>, val withCards: Set<Long>)
