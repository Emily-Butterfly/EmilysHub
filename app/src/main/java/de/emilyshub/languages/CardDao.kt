package de.emilyshub.languages

import androidx.room.Dao
import androidx.room.Insert
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

    @Query("SELECT * FROM card_reviews WHERE card_id = :cardId ORDER BY id")
    suspend fun reviewsOf(cardId: Long): List<CardReview>

    @Query("SELECT EXISTS(SELECT 1 FROM cards WHERE language_id = :languageId)")
    suspend fun languageHasCards(languageId: Long): Boolean

    @Query("UPDATE languages SET new_cards_per_day = :count WHERE id = :languageId")
    suspend fun setNewCardsPerDay(languageId: Long, count: Int)
}
