package de.emilyshub.languages

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** The cards of the Vokabeltrainer and the answers to them (SPEC.md §15). */
@Dao
interface CardDao {
    @Query("SELECT * FROM cards")
    fun observeAll(): Flow<List<Card>>

    @Query("SELECT * FROM cards WHERE language_id = :languageId")
    fun observeOf(languageId: Long): Flow<List<Card>>

    @Query("SELECT * FROM cards WHERE language_id = :languageId")
    suspend fun of(languageId: Long): List<Card>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun card(id: Long): Card?

    @Insert
    suspend fun insert(card: Card): Long

    @Update
    suspend fun update(card: Card)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insert(review: CardReview): Long

    @Query("SELECT * FROM card_goals WHERE goal_id = :goalId")
    suspend fun goal(goalId: Long): CardGoal?

    @Query("SELECT * FROM card_goals WHERE language_id = :languageId")
    suspend fun goalsOf(languageId: Long): List<CardGoal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setGoal(goal: CardGoal)

    @Query("DELETE FROM card_goals WHERE goal_id = :goalId")
    suspend fun deleteGoal(goalId: Long)

    /** How many cards of the language are "sicher" (SPEC.md §15), both ways if asked so (#141). */
    @Query("SELECT COUNT(*) FROM cards c WHERE c.language_id = :languageId AND ${Card.SURE_SQL}")
    suspend fun sureCount(languageId: Long): Int

    /** Deletes the answers to the card [cardId] asked from its back (#141). */
    @Query("DELETE FROM card_reviews WHERE card_id = :cardId AND reverse = 1")
    suspend fun deleteReverseReviews(cardId: Long)

    @Query("DELETE FROM card_reviews WHERE id = :id")
    suspend fun deleteReview(id: Long)

    @Query("SELECT * FROM card_reviews WHERE card_id = :cardId ORDER BY id")
    suspend fun reviewsOf(cardId: Long): List<CardReview>

    /** The answers to the cards of the language [languageId], as they change: what tells which hakt (#149). */
    @Query("SELECT r.* FROM card_reviews r JOIN cards c ON c.id = r.card_id WHERE c.language_id = :languageId")
    fun observeReviewsOf(languageId: Long): Flow<List<CardReview>>

}
