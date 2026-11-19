package org.umn.lensspent.data.repository

import kotlinx.coroutines.flow.Flow
import org.umn.lensspent.data.model.Capture
import org.umn.lensspent.data.model.CaptureSource
import org.umn.lensspent.data.model.CaptureStatus
import org.umn.lensspent.data.model.Category
import org.umn.lensspent.data.model.Transaction

/** One call to [TransactionRepository.saveExtraction], kept whole so a test can assert on all of it. */
internal data class SavedExtraction(
    val captureId: Long,
    val status: CaptureStatus,
    val ocrText: String?,
    val structuredJson: String?,
    val modelVersion: String?
)

/**
 * Records what the extraction pipeline wrote, and refuses everything else.
 *
 * The pipeline is only allowed to do two things to storage: record what it extracted, and move a
 * capture's status. Every other method throws rather than quietly succeeding, so if a later change
 * starts reading categories or adding transactions in the middle of an extraction, these tests say
 * so instead of passing over it.
 *
 * Records rather than stores: nothing is ever read back, because what matters here is the sequence
 * of writes and what was in them.
 */
internal class FakeTransactionRepository : TransactionRepository {

    val savedExtractions = mutableListOf<SavedExtraction>()
    val statusUpdates = mutableListOf<Pair<Long, CaptureStatus>>()

    /** The capture [findCapture] answers with, if its id is asked for. */
    var capture: Capture? = null

    override suspend fun findCapture(id: Long): Capture? = capture?.takeIf { it.id == id }

    override suspend fun saveExtraction(
        captureId: Long,
        status: CaptureStatus,
        ocrText: String?,
        structuredJson: String?,
        modelVersion: String?
    ) {
        savedExtractions += SavedExtraction(captureId, status, ocrText, structuredJson, modelVersion)
    }

    override suspend fun updateCaptureStatus(captureId: Long, status: CaptureStatus) {
        statusUpdates += captureId to status
    }

    override fun observeCategories(): Flow<List<Category>> = unused()

    override fun observeTransactions(limit: Int): Flow<List<Transaction>> = unused()

    override fun observeTransaction(id: Long): Flow<Transaction?> = unused()

    override fun observeTransactionsBetween(fromMillis: Long, toMillis: Long): Flow<List<Transaction>> =
        unused()

    override fun observeSumBetween(fromMillis: Long, toMillis: Long): Flow<Long> = unused()

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

    override suspend fun deleteTransactionAndOrphanCapture(id: Long): String? = unused()

    override suspend fun captureImageUris(): List<String> = unused()

    override suspend fun confirmCaptureAndForgetExtraction(captureId: Long): String? = unused()

    override suspend fun discardCapture(captureId: Long): String? = unused()

    override suspend fun forgetAllCaptures(): List<String> = unused()

    override fun observeRecentCaptures(limit: Int): Flow<List<Capture>> = unused()

    override suspend fun recordCapture(
        sourceUri: String,
        source: CaptureSource,
        capturedAt: Long
    ): Long = unused()

    override suspend fun seedIfEmpty() = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException(
        "Extraction reads and writes captures, and nothing else"
    )
}
