package de.emilyshub.ledger

import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InputText
import de.emilyshub.data.Saving
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
import kotlin.coroutines.CoroutineContext

/**
 * An Abo as its list shows it on a day (SPEC.md §13): how it [status]es, what it [cost]s every so often, its own or that
 * of the fixed booking it is [linked] to, none without an amount; and the day it ends on if cancelled now
 * ([endIfCancelled]): the end of its term, or its single date.
 */
data class SubscriptionItem(
    val subscription: Subscription,
    val status: SubscriptionStatus,
    val cost: SubscriptionCost?,
    val linked: LinkChoice?,
    val endIfCancelled: LocalDate,
)

/**
 * The Abos (SPEC.md §13): the [running] ones by their next last day to cancel, the [cancelled] ones by the day they end,
 * and what the running ones cost a month ([perMonth]), those without an amount not counting.
 */
data class SubscriptionOverview(val running: List<SubscriptionItem>, val cancelled: List<SubscriptionItem>, val perMonth: Long)

/** A fixed booking an Abo can be linked to: its [name], its description or else its category's, and its cost. */
data class LinkChoice(val id: Long, val name: String, val cost: SubscriptionCost)

/**
 * An Abo being edited (SPEC.md §13): a new one ([New]), or the one saved under [id] as it was when the editor opened
 * ([original]). Serializable, so that the editor keeps it.
 */
data class SubscriptionEditing(val id: Long?, val original: SubscriptionDraft?) : Serializable {
    companion object {
        val New = SubscriptionEditing(id = null, original = null)
    }
}

/**
 * The Abos of the household book (SPEC.md §13): contracts to cancel before they renew, each with its last day to cancel,
 * a single date or reckoned from the end of its term, which moves on to the next term by itself once gone by; linked to
 * a fixed booking if wanted, at most one Abo to each. [database] is only called on [io]; writes run to the end once
 * started.
 */
class Subscriptions(
    private val database: () -> HubDatabase,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val dao get() = database().subscriptions()

    /** Today, by which each Abo stands. */
    fun today(): LocalDate = LocalDate.now(clock)

    /** The Abos as their list shows them today. */
    fun overview(): Flow<SubscriptionOverview> = flow {
        emitAll(
            combine(dao.observeAll(), dao.observeTemplates()) { all, templates ->
                val today = today()
                val choices = templates.associate { it.recurring.id to choiceOf(it) }
                val items = all.map { (subscription, booking) ->
                    val linked = booking?.let { choices[it.id] }
                    SubscriptionItem(
                        subscription = subscription,
                        status = subscription.statusOn(today),
                        cost = linked?.cost ?: subscription.amountCents?.let { SubscriptionCost(it, checkNotNull(subscription.interval)) },
                        linked = linked,
                        endIfCancelled = when (val deadline = subscription.deadline) {
                            is Deadline.OnDate -> deadline.cancelBy
                            is Deadline.Term -> deadline.current(today).termEnd
                        },
                    )
                }
                val (cancelled, running) = items.partition { it.status is SubscriptionStatus.Cancelled }
                SubscriptionOverview(
                    running = running.sortedWith(compareBy<SubscriptionItem> { dayOf(it.status) }.thenBy(InputText.byName) { it.subscription.name }),
                    cancelled = cancelled.sortedWith(compareBy<SubscriptionItem> { dayOf(it.status) }.thenBy(InputText.byName) { it.subscription.name }),
                    perMonth = perMonth(running.mapNotNull { it.cost }),
                )
            },
        )
    }.flowOn(io)

    /** The fixed bookings an Abo may be linked to: those without one, and that of the Abo [except], if it has one. */
    fun linkable(except: Long?): Flow<List<LinkChoice>> = flow {
        emitAll(
            combine(dao.observeTemplates(), dao.observeAll()) { templates, all ->
                val taken = all.filter { it.subscription.id != except }.mapNotNull { it.subscription.recurringId }.toSet()
                templates.filter { it.recurring.id !in taken }.map(::choiceOf).sortedWith(compareBy(InputText.byName) { it.name })
            },
        )
    }.flowOn(io)

    /** A new Abo, with nothing typed yet. */
    fun newDraft() = SubscriptionDraft(name = "")

    /** A new Abo linked to the fixed booking [recurringId], by its name ("Als Abo führen"); null once it is deleted. */
    suspend fun draftFor(recurringId: Long): SubscriptionDraft? = withContext(io) {
        val template = database().ledger().template(recurringId) ?: return@withContext null
        val category = checkNotNull(database().ledger().category(template.categoryId))
        SubscriptionDraft(name = template.description.ifEmpty { category.name }, recurringId = recurringId)
    }

    /** The Abo linked to the fixed booking [recurringId], if it has one. */
    suspend fun ofRecurring(recurringId: Long): Long? = withContext(io) { dao.ofRecurring(recurringId)?.id }

    /** The Abo linked to the fixed booking [recurringId], if it has one, as it changes. */
    fun linkedTo(recurringId: Long): Flow<Long?> = flow {
        emitAll(dao.observeAll().map { all -> all.find { it.subscription.recurringId == recurringId }?.subscription?.id })
    }.flowOn(io)

    /**
     * The Abo [id] as its editor opens it, null once it is deleted: a reckoned one with the term that holds today, its own
     * amount and interval only if it is not linked.
     */
    suspend fun editing(id: Long): SubscriptionEditing? = withContext(io) {
        val saved = dao.get(id) ?: return@withContext null
        val base = SubscriptionDraft(
            name = saved.name,
            amount = saved.amountCents?.let(Money::typed).orEmpty(),
            interval = saved.interval ?: BookingInterval.Monthly,
            recurringId = saved.recurringId,
            note = saved.note,
            lead = saved.leadDays.toString(),
        )
        val draft = when (val deadline = saved.deadline) {
            is Deadline.OnDate -> base.copy(cancelBy = deadline.cancelBy)
            is Deadline.Term -> deadline.current(today()).let { term ->
                base.copy(
                    reckoned = true, termEnd = term.termEnd, notice = term.notice.count.toString(), noticeUnit = term.notice.unit,
                    renewal = term.renewalMonths.toString(),
                )
            }
        }
        SubscriptionEditing(id, draft)
    }

    /**
     * Saves [draft] as [editing] has it: a new Abo, or the changes to the one opened, which waits for a new date no more
     * once it has one;
     * refused, with nothing saved, for its problems, a fixed booking with another Abo among them. One deleted meanwhile
     * stays deleted; a fixed booking deleted meanwhile is left out.
     */
    suspend fun save(editing: SubscriptionEditing, draft: SubscriptionDraft): Saving<SubscriptionProblem> = write {
        database().withTransaction {
            val id = editing.id
            val linkedTo = draft.recurringId?.takeIf { database().ledger().template(it) != null }
            val taken = linkedTo?.let { dao.ofRecurring(it) }?.takeIf { it.id != id }
            val problems = draft.problems + listOfNotNull(SubscriptionProblem.RecurringTaken.takeIf { taken != null })
            if (problems.isNotEmpty()) return@withTransaction Saving.Refused(problems)
            val subscription = draft.copy(recurringId = linkedTo).toSubscription()
            if (id == null) {
                Saving.Saved(dao.insert(subscription))
            } else {
                // Cancelled it stays so; let run on, it waits for a new deadline until one is saved.
                dao.get(id)?.let { before ->
                    dao.update(subscription.copy(id = id, endsOn = before.endsOn, runsOn = before.runsOn && before.deadline == subscription.deadline))
                }
                Saving.Saved(id)
            }
        }
    }

    /** Deletes the Abo; its fixed booking stays. The screen asks first. */
    suspend fun delete(id: Long) = write { dao.delete(id) }

    /** Cancels the Abo, ending on [endsOn]: it moves to the cancelled ones and reminds no more. */
    suspend fun cancel(id: Long, endsOn: LocalDate) = write {
        database().withTransaction { dao.get(id)?.let { dao.update(it.copy(endsOn = endsOn)) } }
    }

    /** Takes back that the Abo was cancelled. */
    suspend fun uncancel(id: Long) = write {
        database().withTransaction { dao.get(id)?.let { dao.update(it.copy(endsOn = null)) } }
    }

    /**
     * Lets the Abo run on ("Läuft weiter"), its reminders of this term ending: a reckoned one moves on to the term after
     * the one that holds today, one by a single date waits for a new one.
     */
    suspend fun runOn(id: Long) = write {
        database().withTransaction {
            val saved = dao.get(id) ?: return@withTransaction
            dao.update(
                when (val deadline = saved.deadline) {
                    is Deadline.OnDate -> saved.copy(runsOn = true)
                    is Deadline.Term -> saved.with(deadline.current(today()).next())
                },
            )
        }
    }

    private fun choiceOf(template: RecurringWithCategory) = LinkChoice(
        id = template.recurring.id,
        name = template.recurring.description.ifEmpty { template.category.name },
        cost = SubscriptionCost(template.recurring.amountCents, template.recurring.interval),
    )

    /** The day an Abo is ordered by: its last day to cancel, its single date, or the day it ends. */
    private fun dayOf(status: SubscriptionStatus): LocalDate = when (status) {
        is SubscriptionStatus.Due -> status.lastDay
        is SubscriptionStatus.Lapsed -> status.cancelBy
        is SubscriptionStatus.RunsOn -> status.cancelBy
        is SubscriptionStatus.Cancelled -> status.endsOn
    }

    private suspend fun <T> write(block: suspend () -> T): T = withContext(io + NonCancellable) { block() }
}
