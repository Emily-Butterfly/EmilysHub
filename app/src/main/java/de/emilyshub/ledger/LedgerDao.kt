package de.emilyshub.ledger

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

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

    @Query("DELETE FROM ledger_categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("UPDATE bookings SET category_id = :to WHERE category_id = :from")
    suspend fun moveBookings(from: Long, to: Long)

    @Query("SELECT COUNT(*) FROM bookings WHERE category_id = :categoryId")
    suspend fun countIn(categoryId: Long): Int

    @Transaction
    @Query("SELECT * FROM bookings WHERE date BETWEEN :from AND :to")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<BookingWithCategory>>

    @Query("SELECT * FROM bookings WHERE id = :id")
    suspend fun get(id: Long): Booking?

    @Insert
    suspend fun insert(booking: Booking): Long

    @Update
    suspend fun update(booking: Booking)

    @Query("DELETE FROM bookings WHERE id = :id")
    suspend fun delete(id: Long)
}
