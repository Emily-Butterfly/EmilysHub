package de.emilyshub.ledger

import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InputText
import de.emilyshub.data.NameProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
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
        withContext(NonCancellable) { suggest() }
        emitAll(dao.observeCategories().map { it.sortedWith(CATEGORY_ORDER) })
    }.flowOn(io)

    /** The [month]'s sums, its expenses by category and its bookings. */
    fun month(month: YearMonth): Flow<MonthSummary> = flow {
        emitAll(dao.observeBetween(month.atDay(1), month.atEndOfMonth()).map(::summary))
    }.flowOn(io)

    /** The month of today, which the household book opens on. */
    fun thisMonth(): YearMonth = YearMonth.now(clock)

    /** A new booking of [kind], dated today. */
    fun newDraft(kind: BookingKind) = BookingDraft(kind, amount = "", date = LocalDate.now(clock))

    suspend fun get(id: Long): Booking? = withContext(io) { dao.get(id) }

    /** The booking as the editor starts out with it, or null once it is deleted. */
    suspend fun draftOf(id: Long): BookingDraft? = withContext(io) {
        val booking = dao.get(id) ?: return@withContext null
        booking.toDraft(checkNotNull(dao.category(booking.categoryId)).kind)
    }

    /** How many bookings the category has, which deleting it moves. */
    suspend fun bookingsIn(categoryId: Long): Int = withContext(io) { dao.countIn(categoryId) }

    /** Saves a new booking; refuses a draft with problems, which the editor shows before. */
    suspend fun create(draft: BookingDraft): Long = write {
        database().withTransaction { dao.insert(checked(draft)) }
    }

    suspend fun update(id: Long, draft: BookingDraft) = write {
        database().withTransaction { dao.update(checked(draft).copy(id = id)) }
    }

    suspend fun delete(id: Long) = write { dao.delete(id) }

    /** A new category of [kind] named [name] on a single line; refuses a name with a problem. */
    suspend fun createCategory(kind: BookingKind, name: String): Long = write {
        database().withTransaction {
            suggest()
            dao.insertCategory(LedgerCategory(kind = kind, name = checkedName(name, kind, except = null)))
        }
    }

    suspend fun renameCategory(id: Long, name: String) = write {
        database().withTransaction {
            val category = requireNotNull(dao.category(id)) { "No such category" }
            dao.renameCategory(id, checkedName(name, category.kind, except = id))
        }
    }

    /** Deletes the category: its bookings go to "Sonstiges" of its kind, which cannot be deleted itself. */
    suspend fun deleteCategory(id: Long) = write {
        database().withTransaction {
            val category = dao.category(id) ?: return@withTransaction
            require(!category.isOther) { "\"Sonstiges\" stays" }
            val other = dao.categories().single { it.kind == category.kind && it.isOther }
            dao.moveBookings(from = id, to = other.id)
            dao.deleteCategory(id)
        }
    }

    /** What keeps [name] from naming a category of [kind] besides the others, [except] the one renamed; none if it can. */
    suspend fun nameProblem(name: String, kind: BookingKind, except: Long?): NameProblem? = withContext(io) {
        categoryNameProblem(name, dao.categories().filter { it.kind == kind && it.id != except })
    }

    /** The suggested categories, for a kind that has none yet: on first use, or after a backup from before them. */
    private suspend fun suggest() {
        val present = dao.categories().filter { it.isOther }.map { it.kind }.toSet()
        val missing = SUGGESTED.filterKeys { it !in present }
        if (missing.isEmpty()) return
        dao.insertCategories(
            missing.flatMap { (kind, names) ->
                names.map { LedgerCategory(kind = kind, name = it) } + LedgerCategory(kind = kind, name = OTHER, isOther = true)
            },
        )
    }

    private suspend fun checked(draft: BookingDraft): Booking {
        require(draft.problems.isEmpty()) { "Invalid booking: ${draft.problems}" }
        val category = requireNotNull(dao.category(checkNotNull(draft.categoryId))) { "No such category" }
        require(category.kind == draft.kind) { "A category of the other kind" }
        return Booking(
            amountCents = checkNotNull(draft.cents), date = draft.date, categoryId = category.id, description = draft.cleanedDescription,
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
