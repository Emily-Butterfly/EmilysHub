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
        val settled = combine(
            dao.observeOpenings(), dao.observeTemplates(), dao.observeStandardTotal(), dao.observeMonthTotals(), ::Settled,
        )
        emitAll(
            combine(
                dao.observeClosed(), booked, earlier, settled, previousBalance,
            ) { closedMonths, (bookings, categories, kept, ownBudgets), earlierMonths, settledNow, balanceBefore ->
                val (openings, fixed, standard, totals) = settledNow
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
                val summary = summary(bookings, budgets)
                val ownTotal = totals.find { it.month == month }?.budget
                // While open, what holds now; closed, the amount it kept.
                val totalSetting = if (isClosed) null else ownTotal ?: standardOf(standard)
                val incomeToCome = incomeToCome(fixed.incomeOnly(), month)
                LedgerMonth(
                    month = month,
                    summary = summary,
                    opening = openings.find { it.month == month },
                    previous = PreviousMonth(
                        previous, previous in closed, LedgerMonth.endBalance(openings.find { it.month == previous }?.cents, balanceBefore),
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
                    totalBudget = if (isClosed) {
                        closedMonths.single { it.month == month }.totalBudgetCents
                    } else {
                        totalSetting?.amount(summary.income + incomeToCome)
                    },
                    totalSetting = totalSetting,
                    ownTotal = !isClosed && ownTotal != null,
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

    /**
     * What the [templates], fixed bookings of income, bring in [month] after today, that is not entered yet: none in a
     * month gone by, all of a month to come.
     */
    private fun incomeToCome(templates: List<RecurringBooking>, month: YearMonth): Long {
        val today = today()
        val (from, to) = month.atDay(1) to month.atEndOfMonth()
        return templates.sumOf { template ->
            template.dueDates().dropWhile { it.isBefore(from) }.takeWhile { !it.isAfter(to) }.count { it.isAfter(today) } *
                template.amountCents
        }
    }

    /** The opening balances, fixed bookings, standard total budget and total budgets of months' own, as read. */
    private data class Settled(
        val openings: List<OpeningBalance>,
        val fixed: List<RecurringWithCategory>,
        val standard: List<StandardTotalBudget>,
        val totals: List<MonthTotalBudget>,
    )

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
        val totals = combine(
            dao.observeClosed(), dao.observeTemplates(), dao.observeStandardTotal(), dao.observeMonthTotals(), ::Totals,
        )
        emitAll(
            combine(bookings, dao.observeOpenings(), totals) { booked, openings, (closed, fixed, standard, own) ->
                val incomeByMonth = booked.filter { it.category.kind == BookingKind.Income }
                    .groupBy { YearMonth.from(it.booking.date) }.mapValues { (_, inMonth) -> inMonth.sumOf { it.booking.amountCents } }
                val fixedIncome = fixed.incomeOnly()
                // A month closed has the amount it kept, one open that of now, as its page has it.
                val totalOf = Month.entries.map { year.atMonth(it) }.associateWith { month ->
                    val kept = closed.find { it.month == month }
                    if (kept != null) {
                        kept.totalBudgetCents
                    } else {
                        (own.find { it.month == month }?.budget ?: standardOf(standard))
                            .amount(incomeByMonth[month].orZero() + incomeToCome(fixedIncome, month))
                    }
                }
                yearSummary(year, booked, openings, totalOf)
            },
        )
    }.flowOn(io)

    /** The months closed, the fixed bookings, the standard total budget and those of months' own, as read. */
    private data class Totals(
        val closed: List<ClosedMonth>,
        val fixed: List<RecurringWithCategory>,
        val standard: List<StandardTotalBudget>,
        val own: List<MonthTotalBudget>,
    )

    /**
     * The budget calculator (SPEC.md §13) for the monthly budgets against the standard total budget, with [month] null,
     * or for the budgets of the open [month] against its own: each expense category with its budget and the average of
     * its expenses in the last three months closed.
     */
    suspend fun budgetPlan(month: YearMonth?): BudgetPlan = write {
        database().withTransaction {
            suggest()
            if (month != null) requireOpen(month.atDay(1))
            val categories = dao.categories().filter { it.kind == BookingKind.Expense }.sortedWith(CATEGORY_ORDER)
            val own = month?.let { dao.monthBudgets(it) }.orEmpty().associate { it.categoryId to it.budgetCents }
            val averages = averagesOf(categories)
            val standard = storedStandard()
            val incomeMonth = month ?: thisMonth()
            BudgetPlan(
                month = month,
                lines = categories.map { PlanLine(it, budgetIn(it, own), averages[it.id]) },
                total = if (month == null) standard else dao.monthTotal(month)?.budget,
                standard = standard,
                income = incomeOf(incomeMonth),
                incomeMonth = incomeMonth,
                averageMonths = minOf(closedNow().size, AVERAGE_MONTHS),
            )
        }
    }

    /**
     * Saves the budget calculator as [check] has it (SPEC.md §13), all at once, over the total budget too; refused if it
     * cannot be saved. Without a month, the monthly budgets and the standard total budget; with one, still open, its
     * budgets, those as the monthly one none of its own, and its total budget. A category deleted meanwhile is left out.
     */
    suspend fun saveBudgetPlan(check: PlanCheck) = write {
        require(check.canSave) { "The calculator has problems" }
        val month = check.plan.month
        database().withTransaction {
            check.budgets.forEach { (id, cents) ->
                if (dao.category(id) == null) return@forEach
                if (month == null) setMonthly(id, cents) else setBudgetInMonth(month, id, cents)
            }
            if (month == null) {
                val total = requireNotNull(check.total) { "The standard is no total budget as usual" }
                require(total.fits) { "No total budget: $total" }
                dao.setStandardTotal(StandardTotalBudget.of(total))
            } else {
                setTotalIn(month, check.total)
            }
        }
    }

    /**
     * The average of what each of [categories] spent in the last three months closed, fewer if there are fewer, rounded to
     * the cent; none without a month closed.
     */
    private suspend fun averagesOf(categories: List<LedgerCategory>): Map<Long, Long> {
        val last = closedNow().sortedDescending().take(AVERAGE_MONTHS)
        if (last.isEmpty()) return emptyMap()
        val spent = last.flatMap { dao.spentByCategory(it.atDay(1), it.atEndOfMonth()) }
            .groupBy { it.categoryId }.mapValues { (_, sums) -> sums.sumOf { it.cents } }
        val months = last.size.toLong()
        return categories.associate { it.id to (spent[it.id].orZero() * 2 + months) / (2 * months) }
    }

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
    private suspend fun draftOf(id: Long): BookingDraft? = withContext(io) {
        val booking = dao.get(id) ?: return@withContext null
        booking.toDraft(checkNotNull(dao.category(booking.categoryId)).kind)
    }

    /** How many bookings and fixed bookings the category has, which deleting it moves. */
    suspend fun usesOf(categoryId: Long): Int = withContext(io) { dao.countIn(categoryId) + dao.countTemplatesIn(categoryId) }

    private suspend fun updateBooking(id: Long, draft: BookingDraft) {
        // One entered from a fixed booking stays marked so; the template stays as it is.
        val before = dao.get(id) ?: return
        // Neither changed in a month closed, nor moved out of one or into one.
        requireOpen(before.date, draft.date)
        dao.update(checked(draft).copy(id = id, recurringId = before.recurringId))
    }

    /**
     * Sets the monthly budget of the expense category, [cents] above zero, or takes it away with null (SPEC.md §13). It
     * holds for every month open but those with one of their own; a closed one keeps the budget it had when it was closed.
     */
    suspend fun setBudget(categoryId: Long, cents: Long?) = write {
        database().withTransaction { setMonthly(categoryId, cents) }
    }

    private suspend fun setMonthly(categoryId: Long, cents: Long?) {
        val category = requireNotNull(dao.category(categoryId)) { "No such category" }
        require(category.kind == BookingKind.Expense) { "Budgets are only for expenses" }
        require(BudgetInput.fits(cents)) { "A budget is above zero" }
        dao.setBudget(categoryId, cents)
    }

    /**
     * Sets the budget the expense category has in the open [month] alone (SPEC.md §13): [cents] above zero, or none with
     * null, in place of its monthly one. The same as its monthly one, the month has none of its own any more, and
     * follows the monthly one when that changes.
     */
    suspend fun setBudgetIn(month: YearMonth, categoryId: Long, cents: Long?) = write {
        database().withTransaction { setBudgetInMonth(month, categoryId, cents) }
    }

    private suspend fun setBudgetInMonth(month: YearMonth, categoryId: Long, cents: Long?) {
        val category = requireNotNull(dao.category(categoryId)) { "No such category" }
        require(category.kind == BookingKind.Expense) { "Budgets are only for expenses" }
        require(BudgetInput.fits(cents)) { "A budget is above zero" }
        requireOpen(month.atDay(1))
        if (cents == category.budgetCents) {
            dao.removeMonthBudget(month, categoryId)
        } else {
            dao.setMonthBudget(MonthBudget(month, categoryId, cents))
        }
    }

    /** The standard total budget (SPEC.md §13), for every month open without one of its own. */
    fun totalBudget(): Flow<TotalBudget> = flow {
        emitAll(dao.observeStandardTotal().map(::standardOf))
    }.flowOn(io)

    /**
     * Sets the standard total budget (SPEC.md §13): it holds for every month open but those with one of their own; a
     * closed one keeps the amount it had when it was closed.
     */
    suspend fun setTotalBudget(budget: TotalBudget) = write {
        require(budget.fits) { "No total budget: $budget" }
        dao.setStandardTotal(StandardTotalBudget.of(budget))
    }

    /**
     * Sets the total budget the open [month] has of its own (SPEC.md §13), in place of the standard; null, or the same as
     * the standard, takes it back ("Wie sonst"), and the month follows the standard when that changes.
     */
    suspend fun setTotalBudgetIn(month: YearMonth, budget: TotalBudget?) = write {
        database().withTransaction { setTotalIn(month, budget) }
    }

    private suspend fun setTotalIn(month: YearMonth, budget: TotalBudget?) {
        require(budget?.fits != false) { "No total budget: $budget" }
        requireOpen(month.atDay(1))
        if (budget == null || budget == storedStandard()) {
            dao.removeMonthTotal(month)
        } else {
            dao.setMonthTotal(MonthTotalBudget.of(month, budget))
        }
    }

    /** The total budget that holds in the open [month] now: its own, else the standard. */
    private suspend fun totalSettingIn(month: YearMonth): TotalBudget =
        dao.monthTotal(month)?.budget ?: storedStandard()

    /** The amount the total budget of the open [month] comes to now; none if it has none. */
    private suspend fun totalAmountIn(month: YearMonth): Long? = totalSettingIn(month).amount(incomeOf(month))

    /** The income of [month] for a total budget from income: that entered, and the fixed income still to come in it. */
    private suspend fun incomeOf(month: YearMonth): Long {
        val incomeIds = dao.categories().filter { it.kind == BookingKind.Income }.map { it.id }.toSet()
        val fixed = dao.templates().filter { it.categoryId in incomeIds }
        return dao.incomeBetween(month.atDay(1), month.atEndOfMonth()) + incomeToCome(fixed, month)
    }

    /**
     * By how much saving [draft], in place of the booking [replacing] if it is saved already, takes the expenses of its
     * month over its total budget (SPEC.md §13), to tell it briefly; null if it has none, they stay within it, or the save
     * does not raise what the month spends.
     */
    suspend fun totalOverrunOf(draft: BookingDraft, replacing: Long? = null): Long? = withContext(io) {
        val cents = draft.cents ?: return@withContext null
        if (draft.kind != BookingKind.Expense) return@withContext null
        val month = YearMonth.from(draft.date)
        val total = totalAmountIn(month) ?: return@withContext null
        val (from, to) = month.atDay(1) to month.atEndOfMonth()
        val others = dao.expensesBetween(from, to, except = replacing ?: NONE)
        // What the booking saved already adds there as an expense, which the save replaces.
        val before = replacing?.let { dao.get(it) }
            ?.takeIf { YearMonth.from(it.date) == month && dao.category(it.categoryId)?.kind == BookingKind.Expense }
            ?.amountCents ?: 0L
        (others + cents - total).takeIf { it > 0 && cents > before }
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
     * Closes [month]: it goes to the archive, only to read, keeping the budgets of now and the amount its total budget
     * comes to, and the total budget that held in it as its own; the screen asks first. The fixed bookings due by today are entered before, so that none of its last day
     * is lost. With [carryOver], its end balance goes into the month after as its opening balance, replacing one it has,
     * all or nothing: into a closed one it does not, nor is [month] closed then.
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
                val setting = totalSettingIn(month)
                dao.close(ClosedMonth(month, setting.amount(incomeOf(month))))
                // The standard of now becomes its own, so that it has it again once opened, whatever the standard is then.
                if (dao.monthTotal(month) == null) dao.setMonthTotal(MonthTotalBudget.of(month, setting))
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

    /**
     * Sets the opening balance of the open [month] by hand to [cents], zero or below too, replacing one it has; the same
     * amount again changes nothing, so that one carried over stays carried over.
     */
    suspend fun setOpening(month: YearMonth, cents: Long) = write {
        require(cents in -Money.MAX..Money.MAX) { "No amount: $cents" }
        database().withTransaction {
            requireOpen(month.atDay(1))
            if (dao.opening(month)?.cents != cents) dao.setOpening(OpeningBalance(month, cents, carried = false))
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
        LedgerMonth.endBalance(dao.opening(month)?.cents, dao.balanceBetween(month.atDay(1), month.atEndOfMonth()))

    /**
     * Opens the closed [month] again, with the budgets of now, its own among them; the screen asks first. Its total budget
     * is the one that held when it was closed, not a standard changed meanwhile; the same as the standard of now, it
     * follows the standard again.
     */
    suspend fun reopen(month: YearMonth) = write {
        database().withTransaction {
            dao.reopen(month)
            if (dao.monthTotal(month)?.budget == storedStandard()) dao.removeMonthTotal(month)
        }
    }

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

    /** Deletes the fixed booking: no more come, those entered stay; its Abo stays, with its amount and interval as its own. */
    suspend fun deleteTemplate(id: Long) = write {
        database().withTransaction {
            dao.unlinkSubscription(id)
            dao.deleteTemplate(id)
        }
    }

    /**
     * Ends the fixed booking with the last on [date], that day included (SPEC.md §13), as when its Abo is cancelled; not
     * if that is before its first booking, and says whether it did.
     */
    suspend fun endTemplateOn(id: Long, date: LocalDate): Boolean = write {
        database().withTransaction {
            val template = dao.template(id) ?: return@withTransaction false
            if (date.isBefore(template.dates().firstOrNull() ?: return@withTransaction false)) return@withTransaction false
            dao.updateTemplate(template.copy(endsAfter = null, endsOn = date))
            true
        }
    }

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

    /** The standard total budget stored: none without one. */
    private suspend fun storedStandard(): TotalBudget = standardOf(listOfNotNull(dao.standardTotal()))

    /** The standard total budget as read: none without one stored. */
    private fun standardOf(stored: List<StandardTotalBudget>): TotalBudget = stored.firstOrNull()?.budget ?: TotalBudget.None

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

        /** How many months closed the average of a category's expenses in the calculator is of, at most. */
        private const val AVERAGE_MONTHS = 3

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
 * The sums of [bookings], the [year]'s, each of its months with its opening balance among the [openings] and its total
 * budget ([totalOf]), and its expenses by category, as a month has them.
 */
internal fun yearSummary(
    year: Year,
    bookings: List<BookingWithCategory>,
    openings: List<OpeningBalance> = emptyList(),
    totalOf: Map<YearMonth, Long?> = emptyMap(),
): YearSummary {
    val byMonth = bookings.groupBy { YearMonth.from(it.booking.date) }
    val months = Month.entries.map { year.atMonth(it) }.map { month ->
        val (income, expenses) = byMonth[month].orEmpty().partition { it.category.kind == BookingKind.Income }
        val opening = openings.find { it.month == month }?.cents
        MonthSums(month, income.sumOf { it.booking.amountCents }, expenses.sumOf { it.booking.amountCents }, opening, totalOf[month])
    }
    return YearSummary(year, months, summary(bookings).expensesByCategory)
}

private fun Long?.orZero(): Long = this ?: 0

/** Those of the fixed bookings that bring income in. */
private fun List<RecurringWithCategory>.incomeOnly(): List<RecurringBooking> =
    filter { it.category.kind == BookingKind.Income }.map { it.recurring }
