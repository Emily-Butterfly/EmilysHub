package de.emilyshub.ledger

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger_categories")
    fun observeCategories(): Flow<List<LedgerCategory>>

    @Query("SELECT * FROM ledger_categories")
    suspend fun categories(): List<LedgerCategory>

    @Query("SELECT * FROM ledger_categories WHERE id = :id")
    suspend fun category(id: Long): LedgerCategory?

    @Insert
    suspend fun insertCategories(categories: List<LedgerCategory>): List<Long>

    @Insert
    suspend fun insertCategory(category: LedgerCategory): Long

    @Query("UPDATE ledger_categories SET name = :name WHERE id = :id")
    suspend fun renameCategory(id: Long, name: String)

    @Query("UPDATE ledger_categories SET is_other = 1 WHERE id = :id")
    suspend fun makeOther(id: Long)

    @Query("DELETE FROM ledger_categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("UPDATE bookings SET category_id = :to WHERE category_id = :from")
    suspend fun moveBookings(from: Long, to: Long)

    @Query("UPDATE recurring_bookings SET category_id = :to WHERE category_id = :from")
    suspend fun moveTemplates(from: Long, to: Long)

    @Transaction
    @Query("SELECT * FROM recurring_bookings")
    fun observeTemplates(): Flow<List<RecurringWithCategory>>

    @Query("SELECT * FROM recurring_bookings")
    suspend fun templates(): List<RecurringBooking>

    @Query("SELECT * FROM recurring_bookings WHERE id = :id")
    suspend fun template(id: Long): RecurringBooking?

    @Insert
    suspend fun insertTemplate(template: RecurringBooking): Long

    @Update
    suspend fun updateTemplate(template: RecurringBooking)

    @Query("DELETE FROM recurring_bookings WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Query("SELECT date FROM bookings WHERE category_id = :categoryId")
    suspend fun datesIn(categoryId: Long): List<LocalDate>

    @Query("SELECT COUNT(*) FROM bookings WHERE category_id = :categoryId")
    suspend fun countIn(categoryId: Long): Int

    @Query("SELECT COUNT(*) FROM recurring_bookings WHERE category_id = :categoryId")
    suspend fun countTemplatesIn(categoryId: Long): Int

    @Transaction
    @Query("SELECT * FROM bookings WHERE date BETWEEN :from AND :to")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<BookingWithCategory>>

    /** The months with bookings, as "2026-10". */
    @Query("SELECT DISTINCT substr(date, 1, 7) FROM bookings")
    fun observeBookingMonths(): Flow<List<String>>

    /** The months closed, each with its sums. */
    @Query(
        "SELECT m.month, " +
            "IFNULL(SUM(CASE WHEN c.kind = 'income' THEN b.amount_cents END), 0) AS income, " +
            "IFNULL(SUM(CASE WHEN c.kind = 'expense' THEN b.amount_cents END), 0) AS expenses " +
            "FROM ledger_closed_months m LEFT JOIN bookings b ON substr(b.date, 1, 7) = m.month " +
            "LEFT JOIN ledger_categories c ON c.id = b.category_id GROUP BY m.month",
    )
    fun observeArchive(): Flow<List<ArchivedMonth>>

    @Query("SELECT * FROM ledger_closed_months")
    fun observeClosed(): Flow<List<ClosedMonth>>

    @Query("SELECT * FROM ledger_closed_months")
    suspend fun closed(): List<ClosedMonth>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun close(month: ClosedMonth)

    @Query("DELETE FROM ledger_closed_months WHERE month = :month")
    suspend fun reopen(month: YearMonth)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSkipped(entries: List<SkippedEntry>)

    @Query("SELECT * FROM ledger_skipped WHERE date BETWEEN :from AND :to")
    suspend fun skippedBetween(from: LocalDate, to: LocalDate): List<SkippedEntry>

    @Query("SELECT * FROM ledger_skipped WHERE date BETWEEN :from AND :to")
    fun observeSkippedBetween(from: LocalDate, to: LocalDate): Flow<List<SkippedEntry>>

    /** The months with fixed bookings skipped, as "2026-10". */
    @Query("SELECT DISTINCT substr(date, 1, 7) FROM ledger_skipped")
    fun observeSkippedMonths(): Flow<List<String>>

    @Query("DELETE FROM ledger_skipped WHERE date BETWEEN :from AND :to")
    suspend fun forgetSkippedBetween(from: LocalDate, to: LocalDate)

    @Query("SELECT * FROM bookings WHERE id = :id")
    suspend fun get(id: Long): Booking?

    @Insert
    suspend fun insert(booking: Booking): Long

    @Update
    suspend fun update(booking: Booking)

    @Query("DELETE FROM bookings WHERE id = :id")
    suspend fun delete(id: Long)
}
