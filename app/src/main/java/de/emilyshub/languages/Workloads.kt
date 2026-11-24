package de.emilyshub.languages

import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InputText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext

/**
 * The Tagespensum of each language (CONTEXT.md; SPEC.md §15): what it has to practise on a day, under its own limit and
 * its share of the overall one, shared out by backlog (#156), so that each depends on all the others; and, once one is
 * geschafft, telling so ([onDone]) to the routines done by vocabulary, which take it once a day however often told.
 * It is geschafft by an answer that asks the last of it ([doneBy]), by a change that makes it so ([changing]), and,
 * at its limit with more waiting, also without an answer that day ([tellDoneAtLimit]). [database] is only called on
 * [io].
 */
class Workloads(
    private val database: () -> HubDatabase,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
    private val onDone: suspend (languageId: Long) -> Unit = {},
) {
    private val dao get() = database().cards()
    private val languages get() = database().languages()

    /** The Tagespensum of the language [languageId] on [today], as it changes; none while it is archived, or once it is gone. */
    fun of(languageId: Long, today: LocalDate): Flow<Workload?> = flow {
        emitAll(observe(today).map { it.workloads[languageId] })
    }.flowOn(io)

    /** The Tagespensum on [today] of each language not archived that has cards, by its id, as they change. */
    fun all(today: LocalDate): Flow<Map<Long, Workload>> = flow {
        emitAll(observe(today).map { day -> day.workloads.filterKeys { it in day.withCards } })
    }.flowOn(io)

    /**
     * The languages not archived with something still open on [today] but [except], the most open first, by name if
     * alike: those Heute offers to practise (#158), the first of them after another language's round (#147).
     */
    fun open(today: LocalDate, except: Long? = null): Flow<List<OpenLanguage>> = flow {
        val open = combine(languages.observeLanguages(), observe(today)) { all, day ->
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
    fun next(languageId: Long, today: LocalDate): Flow<OpenLanguage?> = open(today, except = languageId).map { it.firstOrNull() }

    /** Whether the Tagespensum of the language [languageId] on [today] asks [key] still: an answer to it counts against it. */
    suspend fun asks(languageId: Long, key: PromptKey, today: LocalDate): Boolean = withContext(io) {
        now(today).workloads[languageId]?.remaining.orEmpty().any { it.key == key }
    }

    /**
     * After an answer to the language [languageId] on [today]: if it was one the Tagespensum asked ([ofWorkload]) and
     * nothing of it is open now, it is geschafft, and told so; whether it was.
     */
    suspend fun doneBy(languageId: Long, ofWorkload: Boolean, today: LocalDate): Boolean = withContext(io + NonCancellable) {
        // Not those asked again after it, as "Nochmal" brings them, nor one asked in a topic on a day with nothing due
        // and nothing new (#106, #107).
        val done = ofWorkload && now(today).workloads[languageId]?.done == true
        if (done) onDone(languageId)
        done
    }

    /**
     * Does [change], and tells each Tagespensum of today that it made geschafft (#106), of whichever language: a change
     * to one may share the overall limit out anew among all (#156).
     */
    suspend fun <T> changing(today: LocalDate, change: suspend () -> T): T = withContext(io + NonCancellable) {
        val before = now(today).workloads
        val changed = change()
        now(today).workloads.filter { (id, workload) -> workload.done && before[id]?.done == false }.keys.forEach { onDone(it) }
        changed
    }

    /**
     * While it runs, on each of [days]: tells each Tagespensum geschafft at its limit with more waiting beyond it (#156),
     * also without an answer that day, as when its share is none or shrinks as another language comes back from the
     * archive. The app runs it while it is shown, so that it happens as the app opens, at midnight, and as the shares move.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun tellDoneAtLimit(days: Flow<LocalDate>) {
        days.flatMapLatest(::doneAtLimit).collect { languages -> languages.forEach { onDone(it) } }
    }

    /** The languages whose Tagespensum on [today] is geschafft at its limit, with more due waiting beyond it, as they change. */
    internal fun doneAtLimit(today: LocalDate): Flow<Set<Long>> = flow {
        emitAll(observe(today).map { day -> day.workloads.filterValues { it.done && it.waiting > 0 }.keys }.distinctUntilChanged())
    }.flowOn(io)

    private fun observe(today: LocalDate): Flow<WorkloadsOn> = combine(
        languages.observeLanguages(), dao.observeAll(), dao.observeReviewsSince(sinceBefore(today)), dao.observeSettings(),
    ) { all, cards, reviews, settings -> workloadsOn(all, cards, reviews, settings, today) }

    /** The Tagespensen on [today] as they stand now. */
    private suspend fun now(today: LocalDate): WorkloadsOn =
        workloadsOn(languages.languages(), dao.all(), dao.reviewsSince(sinceBefore(today)), dao.settings(), today)

    private fun workloadsOn(all: List<Language>, cards: List<Card>, reviews: List<CardReview>, settings: CardSettings?, today: LocalDate) =
        WorkloadsOn(
            DailyLimits.workloads(all, cards, reviews, settings?.dailyLimit, today, clock.zone),
            withCards = cards.map { it.languageId }.toSet(),
        )

    /** From when the answers of [today] are read: from a day before, which is safe whatever the time zone; [AskedToday] keeps those of the day. */
    private fun sinceBefore(today: LocalDate): Instant = today.minusDays(1).atStartOfDay(clock.zone).toInstant()

    /** The Tagespensen of a day, by language, and the languages that have cards at all. */
    private data class WorkloadsOn(val workloads: Map<Long, Workload>, val withCards: Set<Long>)
}
