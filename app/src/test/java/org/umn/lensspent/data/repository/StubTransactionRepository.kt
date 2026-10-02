package org.umn.lensspent.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.umn.lensspent.data.model.Capture
import org.umn.lensspent.data.model.CaptureSource
import org.umn.lensspent.data.model.CaptureStatus
import org.umn.lensspent.data.model.Category
import org.umn.lensspent.data.model.Transaction

/**
 * The ledger reads, answered from a fixed list.
 *
 * The counterpart to [FakeTransactionRepository]: that one records what the extraction pipeline wrote
 * and refuses to be read, this one is read and refuses to be written. A feature that only ever reads
 * spending - AI Insights, today - can then be tested without a database, and a write that creeps into it
 * fails loudly instead of quietly succeeding.
 *
 * The rows are handed back as given, newest first, exactly as the Room query would order them.
 */
internal class StubTransactionRepository(
    private val rows: List<Transaction> = emptyList()
) : TransactionRepository {

    /** Every limit a read asked for, in the order they were asked: pins how much history leaves. */
    val requestedLimits = mutableListOf<Int>()

    override fun observeTransactions(limit: Int): Flow<List<Transaction>> {
        requestedLimits += limit
        return flowOf(rows.take(limit))
    }

    override fun observeCategories(): Flow<List<Category>> = unused()

    override fun observeTransactionsBetween(fromMillis: Long, toMillis: Long): Flow<List<Transaction>> =
        unused()

    override fun observeSumBetween(fromMillis: Long, toMillis: Long): Flow<Long> = unused()

    override fun observeRecentCaptures(limit: Int): Flow<List<Capture>> = unused()

    override suspend fun findCapture(id: Long): Capture? = unused()

    override suspend fun addTransaction(
        merchant: String,
        amount: Long,
        categoryId: Long,
        occurredAt: Long,
        note: String?,
        captureId: Long?
    ): Long = unused()

    override suspend fun updateTransaction(transaction: Transaction) = unused()

    override suspend fun deleteTransaction(id: Long) = unused()

    override suspend fun recordCapture(
        sourceUri: String,
        source: CaptureSource,
        capturedAt: Long
    ): Long = unused()

    override suspend fun updateCaptureStatus(captureId: Long, status: CaptureStatus) = unused()

    override suspend fun saveExtraction(
        captureId: Long,
        status: CaptureStatus,
        ocrText: String?,
        structuredJson: String?,
        modelVersion: String?
    ) = unused()

    override suspend fun seedIfEmpty() = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException(
        "AI Insights reads transactions, and nothing else"
    )
}
