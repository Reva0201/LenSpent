package org.umn.lensspent.data.repository

import kotlinx.coroutines.flow.Flow
import org.umn.lensspent.data.model.Capture
import org.umn.lensspent.data.model.CaptureSource
import org.umn.lensspent.data.model.CaptureStatus
import org.umn.lensspent.data.model.Category
import org.umn.lensspent.data.model.Transaction

/**
 * The app's data boundary: screens talk to this and never to Room, so the storage engine, the
 * aggregation strategy (today in Kotlin, later in SQL) or a future encrypted database can change
 * without touching the UI.
 */
interface TransactionRepository {

    fun observeCategories(): Flow<List<Category>>

    /** Most recent expenses first; [limit] keeps the dashboard query cheap. */
    fun observeTransactions(limit: Int = AllTransactions): Flow<List<Transaction>>

    fun observeTransactionsBetween(fromMillis: Long, toMillis: Long): Flow<List<Transaction>>

    /** Sum of [Transaction.amount] in the half-open range [fromMillis, toMillis). */
    fun observeSumBetween(fromMillis: Long, toMillis: Long): Flow<Long>

    suspend fun addTransaction(
        merchant: String,
        amount: Long,
        categoryId: Long,
        occurredAt: Long,
        note: String? = null,
        captureId: Long? = null
    ): Long

    suspend fun updateTransaction(transaction: Transaction)

    suspend fun deleteTransaction(id: Long)

    fun observeRecentCaptures(limit: Int = RecentCaptures): Flow<List<Capture>>

    suspend fun findCapture(id: Long): Capture?

    /**
     * Records an image the moment it is taken or picked, before any extraction has run on it.
     *
     * Returns the id of the existing row when the same image is captured twice (picking the same
     * screenshot again), so callers always get a usable id rather than a silent no-op.
     */
    suspend fun recordCapture(
        sourceUri: String,
        source: CaptureSource,
        capturedAt: Long = System.currentTimeMillis()
    ): Long

    /** Advances a capture through the pipeline without touching its extraction output. */
    suspend fun updateCaptureStatus(captureId: Long, status: CaptureStatus)

    /** Stores what the on-device pipeline produced for a capture. */
    suspend fun saveExtraction(
        captureId: Long,
        status: CaptureStatus,
        ocrText: String? = null,
        structuredJson: String? = null,
        modelVersion: String? = null
    )

    /** Seeds the category taxonomy, and demo rows only while the ledger is completely empty. */
    suspend fun seedIfEmpty()

    companion object {
        const val AllTransactions = Int.MAX_VALUE
        const val RecentCaptures = 20
    }
}
