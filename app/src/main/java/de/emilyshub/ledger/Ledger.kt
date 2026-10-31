package de.emilyshub.ledger

import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InputText
import de.emilyshub.data.NameProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.coroutines.CoroutineContext

/**
 * The household book (SPEC.md §13): bookings of income and expenses in categories of their own, by month. It starts out
 * with suggested categories of each kind, among them one "Sonstiges" each, which stays. [database] is only called on
 * [io], since the first call opens it. Writes run to the end once started, even if the screen that started them goes
 * away.
 */
class Ledger(
    private val database: () -> HubDatabase,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val dao get() = database().ledger()

    /** All categories, of each kind by name, "Sonstiges" last. */
    fun categories(): Flow<List<LedgerCategory>> = flow {
        // In a transaction, so that two looking at once do not both suggest them.
        write { database().withTransaction { suggest() } }
        emitAll(dao.observeCategories().map { it.sortedWith(CATEGORY_ORDER) })
    }.flowOn(io)

    /** The [month]'s sums, its expenses by category and its bookings, the fixed ones due by today entered first. */
    fun month(month: YearMonth): Flow<MonthSummary> = flow {
        enterDue()
        emitAll(dao.observeBetween(month.atDay(1), month.atEndOfMonth()).map(::summary))
    }.flowOn(io)

    /** Today, the day a new booking is dated, and a new fixed one comes on. */
    fun today(): LocalDate = LocalDate.now(clock)

    /** Now, so that a screen can tell when the day changes. */
    fun now(): Instant = clock.instant()

    /** The day of [instant] here. */
    fun dayOf(instant: Instant): LocalDate = instant.atZone(clock.zone).toLocalDate()

    /** The month of today, which the household book opens on. */
    fun thisMonth(): YearMonth = YearMonth.now(clock)

    /** A new booking of [kind], dated today. */
    fun newDraft(kind: BookingKind) = BookingDraft(kind, amount = "", date = today())

    /** The booking, or null once it is deleted. */
    suspend fun get(id: Long): Booking? = withContext(io) { dao.get(id) }

    /** The booking as the editor starts out with it, or null once it is deleted. */
    suspend fun draftOf(id: Long): BookingDraft? = withContext(io) {
        val booking = dao.get(id) ?: return@withContext null
        booking.toDraft(checkNotNull(dao.category(booking.categoryId)).kind)
    }

    /** How many bookings and fixed bookings the category has, which deleting it moves. */
    suspend fun usesOf(categoryId: Long): Int = withContext(io) { dao.countIn(categoryId) + dao.countTemplatesIn(categoryId) }

    /**
     * Saves a new booking; refuses a draft with problems, which the editor shows before, and one dated in a month
     * closed, which it tells.
     */
    suspend fun create(draft: BookingDraft): Long = write {
        database().withTransaction {
            requireOpen(draft.date)
            dao.insert(checked(draft))
        }
    }

    /** Saves the booking anew, as [create] does a new one. */
    suspend fun update(id: Long, draft: BookingDraft) = write {
        database().withTransaction {
            // One entered from a fixed booking stays marked so; the template stays as it is.
            val before = dao.get(id) ?: return@withTransaction
            // Neither changed in a month closed, nor moved out of one or into one.
            requireOpen(before.date, draft.date)
            dao.update(checked(draft).copy(id = id, recurringId = before.recurringId))
        }
    }

    /** Deletes the booking; the editor asks first. One in a month closed stays. */
    suspend fun delete(id: Long) = write {
        database().withTransaction {
            val booking = dao.get(id) ?: return@withTransaction
            requireOpen(booking.date)
            dao.delete(id)
        }
    }

    /** Whether [month] is closed (SPEC.md §13), only to read. */
    fun isClosed(month: YearMonth): Flow<Boolean> = closedMonths().map { month in it }

    /** The months closed (SPEC.md §13), only to read. */
    fun closedMonths(): Flow<Set<YearMonth>> = flow {
        emitAll(dao.observeClosed().map { closed -> closed.map { it.month }.toSet() })
    }.flowOn(io)

    /** Whether [month] can be closed: one gone by, or the current one from its last day on. */
    fun canClose(month: YearMonth): Boolean = !month.atEndOfMonth().isAfter(today())

    /**
     * Closes [month]: it goes to the archive, only to read; the screen asks first. The fixed bookings due by today are
     * entered before, so that none of its last day is lost.
     */
    suspend fun close(month: YearMonth) = write {
        require(canClose(month)) { "$month has not ended yet" }
        enterDue()
        dao.close(ClosedMonth(month))
    }

    /** Whether the category has bookings in a month closed, which keep it from being deleted until it is open again. */
    suspend fun inClosedMonths(categoryId: Long): Boolean = withContext(io) {
        val closed = closedNow()
        dao.datesIn(categoryId).any { YearMonth.from(it) in closed }
    }

    /**
     * How many fixed bookings were skipped in [month] while it was closed, to enter now it is open again; none while it
     * is closed.
     */
    fun skipped(month: YearMonth): Flow<Int> = flow {
        emitAll(
            dao.observeClosed().combine(dao.observeSkippedBetween(month.atDay(1), month.atEndOfMonth())) { closed, skipped ->
                if (ClosedMonth(month) in closed) 0 else skipped.size
            },
        )
    }.flowOn(io)

    /** Enters the fixed bookings skipped in [month] while it was closed, as their templates are now, each once. */
    suspend fun enterSkipped(month: YearMonth) = write {
        database().withTransaction {
            requireOpen(month.atDay(1))
            val (from, to) = month.atDay(1) to month.atEndOfMonth()
            dao.skippedBetween(from, to).forEach { skipped ->
                dao.template(skipped.recurringId)?.let { dao.insert(it.entryOn(skipped.date)) }
            }
            dao.forgetSkippedBetween(from, to)
        }
    }

    /** Opens the closed [month] again; the screen asks first. */
    suspend fun reopen(month: YearMonth) = write { dao.reopen(month) }

    /** The months closed, the latest first, each with its sums (SPEC.md §13). */
    fun archive(): Flow<List<ArchivedMonth>> = flow {
        emitAll(dao.observeArchive().map { months -> months.sortedByDescending { it.month } })
    }.flowOn(io)

    /**
     * The months before this one with bookings, or with fixed bookings skipped, that are still open, the latest first,
     * to find them easily.
     */
    fun openEarlierMonths(): Flow<List<YearMonth>> = flow {
        emitAll(
            combine(dao.observeClosed(), dao.observeBookingMonths(), dao.observeSkippedMonths()) { closed, booked, skipped ->
                val closedMonths = closed.map { it.month }.toSet()
                (booked + skipped).distinct().map(YearMonth::parse)
                    .filter { it.isBefore(thisMonth()) && it !in closedMonths }
                    .sortedDescending()
            },
        )
    }.flowOn(io)

    /** A new category of [kind] named [name] on a single line; refuses a name with a problem. */
    suspend fun createCategory(kind: BookingKind, name: String): Long = write {
        database().withTransaction {
            suggest()
            dao.insertCategory(LedgerCategory(kind = kind, name = checkedName(name, kind, except = null)))
        }
    }

    /** Renames the category, as [createCategory] names one; "Sonstiges" keeps its name, as the bookings moved go there. */
    suspend fun renameCategory(id: Long, name: String) = write {
        database().withTransaction {
            val category = requireNotNull(dao.category(id)) { "No such category" }
            require(!category.isOther) { "\"Sonstiges\" keeps its name" }
            dao.renameCategory(id, checkedName(name, category.kind, except = id))
        }
    }

    /** Deletes the category: its bookings go to "Sonstiges" of its kind, which cannot be deleted itself. */
    suspend fun deleteCategory(id: Long) = write {
        database().withTransaction {
            val category = dao.category(id) ?: return@withTransaction
            require(!category.isOther) { "\"Sonstiges\" stays" }
            // Its bookings would move: none of a month closed does.
            val closed = closedNow()
            require(dao.datesIn(id).none { YearMonth.from(it) in closed }) { "It has bookings in a month closed" }
            suggest()
            val other = otherOf(category.kind)
            dao.moveBookings(from = id, to = other.id)
            dao.moveTemplates(from = id, to = other.id)
            dao.deleteCategory(id)
        }
    }

    /** The fixed bookings (SPEC.md §13), the next due first, each with when it comes next. */
    fun templates(): Flow<List<RecurringTemplate>> = flow {
        emitAll(
            dao.observeTemplates().map { all ->
                all.map { (recurring, category) ->
                    RecurringTemplate(
                        recurring.id, recurring.toDraft(category.kind), recurring.amountCents, category, recurring.dueDates().first(),
                    )
                }.sortedWith(compareBy<RecurringTemplate> { it.next }.thenBy(InputText.byName) { it.draft.description })
            },
        )
    }.flowOn(io)

    /** The fixed booking as its editor starts out with it, or null once it is deleted. */
    suspend fun templateDraftOf(id: Long): RecurringDraft? = withContext(io) {
        val template = dao.template(id) ?: return@withContext null
        template.toDraft(checkNotNull(dao.category(template.categoryId)).kind)
    }

    /** A new fixed booking; refuses a draft with problems, which the editor shows before. Entered from its first day on. */
    suspend fun createTemplate(draft: RecurringDraft): Long = write {
        database().withTransaction { dao.insertTemplate(checkedTemplate(draft)) }.also { enterDue() }
    }

    /**
     * Saves the fixed booking anew: it holds from the next one entered on; those entered already stay as they are, and
     * none comes again for a month entered already.
     */
    suspend fun updateTemplate(id: Long, draft: RecurringDraft) = write {
        database().withTransaction {
            val before = dao.template(id) ?: return@withTransaction
            val changed = checkedTemplate(draft).copy(id = id)
            dao.updateTemplate(changed.copy(enteredThrough = changed.enteredAfterChange(before.enteredThrough, today())))
        }
        enterDue()
    }

    /** Deletes the fixed booking: no more come, those entered stay. */
    suspend fun deleteTemplate(id: Long) = write { dao.deleteTemplate(id) }

    /**
     * Enters every fixed booking due by today that is not yet: those of days the app did not run on too, each exactly
     * once, as the month entered last is kept with it in the same transaction.
     */
    suspend fun enterDue() = write {
        database().withTransaction {
            val today = today()
            val closed = closedNow()
            dao.templates().forEach { template ->
                val due = template.dueDates().takeWhile { !it.isAfter(today) }.toList()
                if (due.isEmpty()) return@forEach
                // None into a month closed: those are kept, to be entered by hand once it is opened again.
                val (skipped, entered) = due.partition { YearMonth.from(it) in closed }
                entered.forEach { date -> dao.insert(template.entryOn(date)) }
                dao.insertSkipped(skipped.map { SkippedEntry(template.id, it) })
                dao.updateTemplate(template.copy(enteredThrough = YearMonth.from(due.last())))
            }
        }
    }

    /**
     * The suggested categories, for a kind that has none yet: on first use, or after a backup from before them. A kind
     * with categories but without "Sonstiges", as a backup may bring, only gets that: the one named so, if there is one.
     */
    private suspend fun suggest() {
        val all = dao.categories()
        BookingKind.entries.filter { kind -> all.none { it.kind == kind && it.isOther } }.forEach { kind ->
            val ofKind = all.filter { it.kind == kind }
            val named = ofKind.find { InputText.readTheSame(it.name, OTHER) }
            when {
                ofKind.isEmpty() -> dao.insertCategories(
                    SUGGESTED.getValue(kind).map { LedgerCategory(kind = kind, name = it) } +
                        LedgerCategory(kind = kind, name = OTHER, isOther = true),
                )
                named != null -> dao.makeOther(named.id)
                else -> dao.insertCategory(LedgerCategory(kind = kind, name = OTHER, isOther = true))
            }
        }
    }

    private suspend fun otherOf(kind: BookingKind): LedgerCategory = dao.categories().single { it.kind == kind && it.isOther }

    private suspend fun checked(draft: BookingDraft): Booking {
        require(draft.problems.isEmpty()) { "Invalid booking: ${draft.problems}" }
        // One deleted while the editor was open: the booking goes where those of a category deleted go.
        val category = dao.category(checkNotNull(draft.categoryId)) ?: run {
            suggest()
            otherOf(draft.kind)
        }
        require(category.kind == draft.kind) { "A category of the other kind" }
        return Booking(
            amountCents = checkNotNull(draft.cents), date = draft.date, categoryId = category.id, description = draft.cleanedDescription,
        )
    }

    private suspend fun requireOpen(vararg dates: LocalDate) {
        val closed = closedNow()
        dates.forEach { require(YearMonth.from(it) !in closed) { "${YearMonth.from(it)} is closed" } }
    }

    private suspend fun closedNow(): Set<YearMonth> = dao.closed().map { it.month }.toSet()

    private suspend fun checkedTemplate(draft: RecurringDraft): RecurringBooking {
        require(draft.problems.isEmpty()) { "Invalid fixed booking: ${draft.problems}" }
        val booking = checked(draft.asBooking)
        return RecurringBooking(
            amountCents = booking.amountCents, categoryId = booking.categoryId, description = booking.description,
            interval = draft.interval, day = draft.day, firstMonth = draft.firstMonth,
        )
    }

    private suspend fun checkedName(name: String, kind: BookingKind, except: Long?): String {
        val problem = categoryNameProblem(name, dao.categories().filter { it.kind == kind && it.id != except })
        require(problem == null) { "Invalid category name: $problem" }
        return InputText.singleLine(name)
    }

    private suspend fun <T> write(block: suspend () -> T): T = withContext(io + NonCancellable) { block() }

    companion object {
        const val OTHER = "Sonstiges"

        private val SUGGESTED = mapOf(
            BookingKind.Expense to listOf("Lebensmittel", "Wohnen", "Mobilität", "Freizeit", "Gesundheit", "Kleidung"),
            BookingKind.Income to listOf("Gehalt"),
        )

        /** Expenses first, as [BookingKind] has them. */
        private val CATEGORY_ORDER =
            compareBy<LedgerCategory> { it.kind.ordinal }.thenBy { it.isOther }.thenBy(InputText.byName) { it.name }
    }
}

/** What keeps [name], on a single line, from naming a category besides the [others] of its kind; none if it can. */
fun categoryNameProblem(name: String, others: Collection<LedgerCategory>): NameProblem? =
    InputText.nameProblem(name, LedgerCategory.NAME_MAX, others.map { it.name })

/** The sums of [bookings], a month's. */
internal fun summary(bookings: List<BookingWithCategory>): MonthSummary {
    val (income, expenses) = bookings.partition { it.category.kind == BookingKind.Income }
    return MonthSummary(
        income = income.sumOf { it.booking.amountCents },
        expenses = expenses.sumOf { it.booking.amountCents },
        expensesByCategory = expenses.groupBy { it.category.id }
            .map { (_, inCategory) -> CategoryTotal(inCategory.first().category, inCategory.sumOf { it.booking.amountCents }) }
            .sortedWith(compareByDescending<CategoryTotal> { it.cents }.thenBy(InputText.byName) { it.category.name }),
        bookings = bookings.sortedWith(compareByDescending<BookingWithCategory> { it.booking.date }.thenByDescending { it.booking.id }),
    )
}
