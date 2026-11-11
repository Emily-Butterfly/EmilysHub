package de.emilyshub.ledger

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Transaction
    @Query("SELECT * FROM subscriptions")
    fun observeAll(): Flow<List<SubscriptionWithBooking>>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun get(id: Long): Subscription?

    @Query("SELECT * FROM subscriptions WHERE recurring_id = :recurringId")
    suspend fun ofRecurring(recurringId: Long): Subscription?

    @Insert
    suspend fun insert(subscription: Subscription): Long

    @Update
    suspend fun update(subscription: Subscription)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun delete(id: Long)

    /** The fixed bookings with their categories, and the Abo linked to each, if any. */
    @Transaction
    @Query("SELECT * FROM recurring_bookings")
    fun observeTemplates(): Flow<List<RecurringWithCategory>>

    @Query("SELECT recurring_id FROM subscriptions WHERE recurring_id IS NOT NULL")
    fun observeLinked(): Flow<List<Long>>
}

/** An Abo with the fixed booking it is linked to, if any. */
data class SubscriptionWithBooking(
    @Embedded val subscription: Subscription,
    @Relation(parentColumn = "recurring_id", entityColumn = "id") val booking: RecurringBooking?,
)
