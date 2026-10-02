package org.umn.lensspent.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.umn.lensspent.data.local.entity.TransactionEntity

/**
 * Transaction queries.
 *
 * Every read is a [Flow] so the dashboard, the charts and the history list all repaint themselves the
 * moment a row changes - including while a capture is still being confirmed.
 */
@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE occurredAt >= :from AND occurredAt < :to " +
            "ORDER BY occurredAt DESC, id DESC"
    )
    fun observeBetween(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM transactions " +
            "WHERE occurredAt >= :from AND occurredAt < :to"
    )
    fun observeSumBetween(from: Long, to: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entity: TransactionEntity): Long

    @Insert
    suspend fun insertAll(entities: List<TransactionEntity>)

    /**
     * Updates only the user-editable columns on purpose: rewriting the whole row would reset
     * `createdAt` to "now" and quietly destroy the audit trail of when the expense was recorded.
     */
    @Query(
        "UPDATE transactions SET merchant = :merchant, amount = :amount, categoryId = :categoryId, " +
            "occurredAt = :occurredAt, note = :note WHERE id = :id"
    )
    suspend fun updateFields(
        id: Long,
        merchant: String,
        amount: Long,
        categoryId: Long,
        occurredAt: Long,
        note: String?
    )

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
