package de.emilyshub.ledger

import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InputText
import de.emilyshub.data.NameProblem
import de.emilyshub.data.Saving
import de.emilyshub.todos.RecurrenceEnd
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
import java.time.Month
import java.time.Year
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

    /**
     * The [month] as its page shows it ([LedgerMonth]), the fixed ones due by today entered first: its sums, its
     * expenses by category with their budgets, and its bookings; whether it is closed, may be closed now, what it
     * skipped while closed, and the earlier months still open. An open month has the budgets of now, those of its own
     * among them, a closed one those it had when it was closed. All of one read of the months closed.
     */
    fun month(month: YearMonth): Flow<LedgerMonth> = flow {
        enterDue()
        val (from, to) = month.atDay(1) to month.atEndOfMonth()
        val booked = combine(
            dao.observeBetween(from, to), dao.observeCategories(), dao.observeClosedBudgets(month), dao.observeMonthBudgets(month), ::Booked,
        )
        val earlier = combine(dao.observeBookingMonths(), dao.observeSkippedMonths(), dao.observeSkippedBetween(from, to), ::Earlier)
        val (previous, next) = month.minusMonths(1) to month.plusMonths(1)
        val previousBalance = dao.observeBetween(previous.atDay(1), previous.atEndOfMonth()).map { summary(it).balance }
        val openingsAndFixed = combine(dao.observeOpenings(), dao.observeTemplates(), ::Pair)
        emitAll(
            combine(
                dao.observeClosed(), booked, earlier, openingsAndFixed, previousBalance,
            ) { closedMonths, (bookings, categories, kept, ownBudgets), earlierMonths, (openings, fixed), balanceBefore ->
                val closed = closedMonths.map { it.month }.toSet()
                val isClosed = month in closed
                val own = ownBudgets.associate { it.categoryId to it.budgetCents }
                // Only while open: a closed month keeps its budgets as they were.
                val budgetsInMonth = if (isClosed) {
                    emptyList()
                } else {
                    categories.filter { it.kind == BookingKind.Expense }.sortedWith(CATEGORY_ORDER)
                        .map { CategoryBudget(it, budgetIn(it, own)) }
                }
                val budgets = if (isClosed) {
                    val byId = categories.associateBy { it.id }
                    kept.mapNotNull { budget -> byId[budget.categoryId]?.let { it to budget.budgetCents } }.toMap()
                } else {
                    budgetsInMonth.mapNotNull { budget -> budget.inMonth?.let { budget.category to it } }.toMap()
                }
                LedgerMonth(
                    month = month,
                    summary = summary(bookings, budgets),
                    opening = openings.find { it.month == month },
                    previous = PreviousMonth(
                        previous, previous in closed, (openings.find { it.month == previous }?.cents ?: 0) + balanceBefore,
                    ),
                    next = NextMonth(next, next in closed, openings.find { it.month == next }),
                    closed = isClosed,
                    canClose = canClose(month),
                    // Only for this month while open: those of a month gone by are entered, those of one to come all are.
                    upcoming = if (month == thisMonth() && !isClosed) stillToCome(fixed, to) else null,
                    // Entered by hand once it is open again; none while closed.
                    skipped = if (isClosed) 0 else earlierMonths.skippedHere.size,
                    stillOpen = (earlierMonths.booked + earlierMonths.skipped).distinct().map(YearMonth::parse)
                        .filter { it.isBefore(thisMonth()) && it !in closed && it != month }
                        .sortedDescending(),
                    budgets = budgetsInMonth,
                )
            },
        )
    }.flowOn(io)

    /**
     * What the [fixed] bookings still to be entered after today, up to [end], bring in less what they take out; none if
     * none is to come. Each comes once, one entered and deleted since not again.
     */
    private fun stillToCome(fixed: List<RecurringWithCategory>, end: LocalDate): Long? {
        val today = today()
        val coming = fixed.flatMap { (template, category) ->
            val sign = if (category.kind == BookingKind.Income) 1 else -1
            template.dueDates().takeWhile { !it.isAfter(end) }.filter { it.isAfter(today) }.map { sign * template.amountCents }.toList()
        }
        return coming.takeIf { it.isNotEmpty() }?.sum()
    }

    private data class Booked(
        val bookings: List<BookingWithCategory>,
        val categories: List<LedgerCategory>,
        val kept: List<ClosedBudget>,
        val own: List<MonthBudget>,
    )

    /** The months with bookings and with fixed bookings skipped, and those skipped in the month shown. */
    private data class Earlier(val booked: List<String>, val skipped: List<String>, val skippedHere: List<SkippedEntry>)

    /**
     * The [year]'s months with their sums and its expenses by category (SPEC.md §13), the fixed ones due by today
     * entered first. Months open and closed count the same.
     */
    fun year(year: Year): Flow<YearSummary> = flow {
        enterDue()
        val bookings = dao.observeBetween(year.atDay(1), year.atMonth(Month.DECEMBER).atEndOfMonth())
        emitAll(combine(bookings, dao.observeOpenings()) { booked, openings -> yearSummary(year, booked, openings) })
    }.flowOn(io)

    /** Today, the day a new booking is dated, and a new fixed one comes on. */
    fun today(): LocalDate = LocalDate.now(clock)

    /** The month of today, which the household book opens on. */
    fun thisMonth(): YearMonth = YearMonth.now(clock)

    /** A new booking of [kind], dated today. */
    fun newDraft(kind: BookingKind) = BookingDraft(kind, amount = "", date = today())

    /** The booking, or null once it is deleted. */
    suspend fun get(id: Long): Booking? = withContext(io) { dao.get(id) }

    /** The booking [id] as it is saved, to edit; null once it is deleted. */
    suspend fun editing(id: Long): BookingEditing? = draftOf(id)?.let { BookingEditing(id, it) }

    /**
     * What keeps [draft] from being saved as [editing], with the months [closed] as they are: its own problems, and a
     * month closed it was saved in or is dated in ([BookingEditing.closedMonth]).
     */
    fun problems(editing: BookingEditing, draft: BookingDraft, closed: Set<YearMonth>): Set<BookingProblem> =
        draft.problems + listOfNotNull(BookingProblem.MonthClosed.takeIf { editing.closedMonth(draft, closed) != null })

    /**
     * Saves [draft] as [editing] has it: a new booking, or the changes to the one opened; refused, with nothing saved,
     * for its problems, a month closed meanwhile among them. One deleted meanwhile stays deleted.
     */
    suspend fun save(editing: BookingEditing, draft: BookingDraft): Saving<BookingProblem> = write {
        database().withTransaction {
            val id = editing.id
            val closed = closedNow()
            val saved = id?.let { dao.get(it) }
            // The month it is in now too, should it have been moved elsewhere meanwhile.
            val movedFromClosed = saved != null && YearMonth.from(saved.date) in closed
            val problems = problems(editing, draft, closed) + listOfNotNull(BookingProblem.MonthClosed.takeIf { movedFromClosed })
            when {
                problems.isNotEmpty() -> Saving.Refused(problems)
                id == null -> Saving.Saved(dao.insert(checked(draft)))
                else -> {
                    if (saved != null) updateBooking(id, draft)
                    Saving.Saved(id)
                }
            }
        }
    }

    /** Deletes the booking opened, and says so; one in a month closed meanwhile stays, and the editor says nothing. */
    suspend fun delete(editing: BookingEditing): Boolean = write {
        database().withTransaction {
            val booking = editing.id?.let { dao.get(it) } ?: return@withTransaction false
            if (YearMonth.from(booking.date) in closedNow()) return@withTransaction false
            dao.delete(booking.id)
            true
        }
    }

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
        database().withTransaction { updateBooking(id, draft) }
    }

    private suspend fun updateBooking(id: Long, draft: BookingDraft) {
        // One entered from a fixed booking stays marked so; the template stays as it is.
        val before = dao.get(id) ?: return
        // Neither changed in a month closed, nor moved out of one or into one.
        requireOpen(before.date, draft.date)
        dao.update(checked(draft).copy(id = id, recurringId = before.recurringId))
    }

    /** Deletes the booking; the editor asks first. One in a month closed stays. */
    suspend fun delete(id: Long) = write {
        database().withTransaction {
            val booking = dao.get(id) ?: return@withTransaction
            requireOpen(booking.date)
            dao.delete(id)
        }
    }

    /**
     * Sets the monthly budget of the expense category, [cents] above zero, or takes it away with null (SPEC.md §13). It
     * holds for every month open but those with one of their own; a closed one keeps the budget it had when it was closed.
     */
    suspend fun setBudget(categoryId: Long, cents: Long?) = write {
        database().withTransaction {
            val category = requireNotNull(dao.category(categoryId)) { "No such category" }
            require(category.kind == BookingKind.Expense) { "Budgets are only for expenses" }
            require(cents == null || cents in 1..Money.MAX) { "A budget is above zero" }
            dao.setBudget(categoryId, cents)
        }
    }

    /**
     * Sets the budget the expense category has in the open [month] alone (SPEC.md §13): [cents] above zero, or none with
     * null, in place of its monthly one. The same as its monthly one, the month has none of its own any more, and
     * follows the monthly one when that changes.
     */
    suspend fun setBudgetIn(month: YearMonth, categoryId: Long, cents: Long?) = write {
        database().withTransaction {
            val category = requireNotNull(dao.category(categoryId)) { "No such category" }
            require(category.kind == BookingKind.Expense) { "Budgets are only for expenses" }
            require(cents == null || cents in 1..Money.MAX) { "A budget is above zero" }
            requireOpen(month.atDay(1))
            if (cents == category.budgetCents) {
                dao.removeMonthBudget(month, categoryId)
            } else {
                dao.setMonthBudget(MonthBudget(month, categoryId, cents))
            }
        }
    }

    /**
     * By how much saving [draft], in place of the booking [replacing] if it is saved already, takes its category over
     * its budget in its month (SPEC.md §13), to tell it briefly; null if it has none, stays within it, or the save does
     * not raise what the category spends that month.
     */
    suspend fun overrunOf(draft: BookingDraft, replacing: Long? = null): Long? = withContext(io) {
        val categoryId = draft.categoryId ?: return@withContext null
        val cents = draft.cents ?: return@withContext null
        if (draft.kind != BookingKind.Expense) return@withContext null
        val month = YearMonth.from(draft.date)
        // That of now, the month's own if it has one: a month closed takes no booking anyway.
        val category = dao.category(categoryId) ?: return@withContext null
        val own = dao.monthBudget(month, categoryId)
        val budget = (if (own != null) own.budgetCents else category.budgetCents) ?: return@withContext null
        val (from, to) = month.atDay(1) to month.atEndOfMonth()
        val others = dao.spentIn(categoryId, from, to, except = replacing ?: NONE)
        // What the booking saved already adds there, which the save replaces.
        val before = replacing?.let { dao.get(it) }
            ?.takeIf { it.categoryId == categoryId && YearMonth.from(it.date) == month }?.amountCents ?: 0L
        val after = others + cents
        (after - budget).takeIf { it > 0 && cents > before }
    }

    /** The months closed (SPEC.md §13), only to read. */
    fun closedMonths(): Flow<Set<YearMonth>> = flow {
        emitAll(dao.observeClosed().map { closed -> closed.map { it.month }.toSet() })
    }.flowOn(io)

    /** Whether [month] can be closed: one gone by, or the current one from its last day on. */
    private fun canClose(month: YearMonth): Boolean = !month.atEndOfMonth().isAfter(today())

    /**
     * Closes [month]: it goes to the archive, only to read, keeping the budgets of now; the screen asks first. The fixed
     * bookings due by today are entered before, so that none of its last day is lost. With [carryOver], its end balance
     * goes into the month after as its opening balance, replacing one it has, all or nothing: into a closed one it does
     * not, nor is [month] closed then.
     */
    suspend fun close(month: YearMonth, carryOver: Boolean = false) = write {
        require(canClose(month)) { "$month has not ended yet" }
        enterDue()
        database().withTransaction {
            if (carryOver) {
                val next = month.plusMonths(1)
                requireOpen(next.atDay(1))
                dao.setOpening(OpeningBalance(next, endBalance(month), carried = true))
            }
            // Closed already, it keeps the budgets it had then.
            if (month !in closedNow()) {
                dao.close(ClosedMonth(month))
                dao.keepBudgets(month)
            }
        }
    }

    /**
     * Whether the category has bookings in a month closed, or a budget one keeps, which keep it from being deleted until
     * it is open again.
     */
    suspend fun inClosedMonths(categoryId: Long): Boolean = withContext(io) { lockedByClosedMonths(categoryId) }

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

    /** Sets the opening balance of the open [month] by hand to [cents], zero or below too, replacing one it has. */
    suspend fun setOpening(month: YearMonth, cents: Long) = write {
        require(cents in -Money.MAX..Money.MAX) { "No amount: $cents" }
        database().withTransaction {
            requireOpen(month.atDay(1))
            dao.setOpening(OpeningBalance(month, cents, carried = false))
        }
    }

    /** Removes the opening balance of the open [month], if it has one. */
    suspend fun removeOpening(month: YearMonth) = write {
        database().withTransaction {
            requireOpen(month.atDay(1))
            dao.removeOpening(month)
        }
    }

    /**
     * Carries the end balance of the month before over into the open [month] as its opening balance, replacing one it
     * has; that month may still be open, and the balance stays as carried whatever it does after.
     */
    suspend fun carryOver(month: YearMonth) = write {
        database().withTransaction {
            requireOpen(month.atDay(1))
            dao.setOpening(OpeningBalance(month, endBalance(month.minusMonths(1)), carried = true))
        }
    }

    /** What [month] ends with: its opening balance, none counting as zero, and what came in less what went out. */
    private suspend fun endBalance(month: YearMonth): Long =
        (dao.opening(month)?.cents ?: 0) + dao.balanceBetween(month.atDay(1), month.atEndOfMonth())

    /** Opens the closed [month] again, with the budgets of now, its own among them; the screen asks first. */
    suspend fun reopen(month: YearMonth) = write { dao.reopen(month) }

    /** The months closed, the latest first, each with its sums (SPEC.md §13). */
    fun archive(): Flow<List<MonthSums>> = flow {
        emitAll(dao.observeArchive().map { months -> months.sortedByDescending { it.month } })
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
            // Its bookings would move, and its budget go: neither of a month closed does.
            require(!lockedByClosedMonths(id)) { "It has bookings or a budget in a month closed" }
            suggest()
            val other = otherOf(category.kind)
            dao.moveBookings(from = id, to = other.id)
            dao.moveTemplates(from = id, to = other.id)
            dao.deleteCategory(id)
        }
    }

    /** The fixed bookings (SPEC.md §13), the next due first, those ended last, each with when it comes next. */
    fun templates(): Flow<List<RecurringTemplate>> = flow {
        emitAll(
            dao.observeTemplates().map { all ->
                all.map { (recurring, category) ->
                    val next = recurring.dueDates().firstOrNull()
                    RecurringTemplate(
                        recurring.id, recurring.toDraft(category.kind), recurring.amountCents, category, next,
                        next?.let(recurring::numberOf),
                    )
                }.sortedWith(
                    compareBy<RecurringTemplate, LocalDate?>(nullsLast()) { it.next }.thenBy(InputText.byName) { it.draft.description },
                )
            },
        )
    }.flowOn(io)

    /** A new fixed booking: an expense from this month on, on today's day. */
    fun newTemplate() = RecurringDraft(BookingKind.Expense, amount = "", firstMonth = thisMonth(), day = today().dayOfMonth)

    /** The fixed booking [id] as it is saved, to edit; null once it is deleted. */
    suspend fun editingTemplate(id: Long): RecurringEditing? = templateDraftOf(id)?.let { RecurringEditing(id, it) }

    /**
     * The first month a fixed booking edited as [editing] may start from: a year back at most, as all months since are
     * entered at once; one saved keeps its own, if it lies further back.
     */
    fun earliestFirstMonth(editing: RecurringEditing): YearMonth {
        val yearBack = thisMonth().minusMonths(MONTHS_BACK)
        return editing.original?.firstMonth?.let { minOf(it, yearBack) } ?: yearBack
    }

    /** Which of its bookings one saved as [draft] would enter next, by its schedule: those by today have come. */
    fun nextNumber(draft: RecurringDraft): Int =
        if (draft.day in RecurringBooking.DAYS) {
            schedule(draft.interval, draft.day, draft.firstMonth).takeWhile { !it.isAfter(today()) }.count() + 1
        } else {
            1
        }

    /** What keeps [draft] from being saved as [editing]: its own problems, and a first month too far back. */
    fun problems(editing: RecurringEditing, draft: RecurringDraft): Set<RecurringProblem> =
        draft.problems + listOfNotNull(RecurringProblem.FirstMonthTooEarly.takeIf { draft.firstMonth < earliestFirstMonth(editing) })

    /**
     * Saves [draft] as [editing] has it: a new fixed booking, entered from its first day on, or the changes to the one
     * opened; refused, with nothing saved, for its problems.
     */
    suspend fun saveTemplate(editing: RecurringEditing, draft: RecurringDraft): Saving<RecurringProblem> {
        val problems = problems(editing, draft)
        if (problems.isNotEmpty()) return Saving.Refused(problems)
        val id = editing.id ?: return Saving.Saved(createTemplate(draft))
        updateTemplate(id, draft)
        return Saving.Saved(id)
    }

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

    private suspend fun lockedByClosedMonths(categoryId: Long): Boolean {
        val closed = closedNow()
        return dao.hasClosedBudgets(categoryId) || dao.datesIn(categoryId).any { YearMonth.from(it) in closed }
    }

    private suspend fun closedNow(): Set<YearMonth> = dao.closed().map { it.month }.toSet()

    private suspend fun checkedTemplate(draft: RecurringDraft): RecurringBooking {
        require(draft.problems.isEmpty()) { "Invalid fixed booking: ${draft.problems}" }
        val booking = checked(draft.asBooking)
        return RecurringBooking(
            amountCents = booking.amountCents, categoryId = booking.categoryId, description = booking.description,
            interval = draft.interval, day = draft.day, firstMonth = draft.firstMonth,
            endsAfter = (draft.end as? RecurrenceEnd.AfterTimes)?.times, endsOn = (draft.end as? RecurrenceEnd.OnDate)?.date,
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

        /** No booking: an id none has. */
        private const val NONE = 0L

        /** How far back a new fixed booking may start: all months since are entered at once. */
        private const val MONTHS_BACK = 12L

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

/** The sums of [bookings], a month's, with the [budgets] of its expense categories that month. */
internal fun summary(bookings: List<BookingWithCategory>, budgets: Map<LedgerCategory, Long> = emptyMap()): MonthSummary {
    val (income, expenses) = bookings.partition { it.category.kind == BookingKind.Income }
    val spent = expenses.groupBy { it.category.id }
        .map { (_, inCategory) -> inCategory.first().category to inCategory.sumOf { it.booking.amountCents } }
    // Those with a budget and no expenses too, to see what is left of it.
    val unspent = budgets.keys.filter { budgeted -> spent.none { it.first.id == budgeted.id } }.map { it to 0L }
    val byId = budgets.entries.associate { (category, cents) -> category.id to cents }
    return MonthSummary(
        income = income.sumOf { it.booking.amountCents },
        expenses = expenses.sumOf { it.booking.amountCents },
        expensesByCategory = (spent + unspent)
            .map { (category, cents) -> CategoryTotal(category, cents, byId[category.id]) }
            .sortedWith(compareByDescending<CategoryTotal> { it.cents }.thenBy(InputText.byName) { it.category.name }),
        bookings = bookings.sortedWith(compareByDescending<BookingWithCategory> { it.booking.date }.thenByDescending { it.booking.id }),
    )
}

/**
 * The sums of [bookings], the [year]'s, each of its months with its opening balance among the [openings], and its
 * expenses by category, as a month has them.
 */
internal fun yearSummary(year: Year, bookings: List<BookingWithCategory>, openings: List<OpeningBalance> = emptyList()): YearSummary {
    val byMonth = bookings.groupBy { YearMonth.from(it.booking.date) }
    val months = Month.entries.map { year.atMonth(it) }.map { month ->
        val (income, expenses) = byMonth[month].orEmpty().partition { it.category.kind == BookingKind.Income }
        val opening = openings.find { it.month == month }?.cents
        MonthSums(month, income.sumOf { it.booking.amountCents }, expenses.sumOf { it.booking.amountCents }, opening)
    }
    return YearSummary(year, months, summary(bookings).expensesByCategory)
}
