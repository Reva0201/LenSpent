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
 *
 * The capture methods that end in `ForCapture` exist so the *screen* can own the retention decision:
 * deleting an image is a filesystem action, and this interface deliberately knows nothing about
 * `Context` or files.
 */
interface TransactionRepository {

    fun observeCategories(): Flow<List<Category>>

    /** Most recent expenses first; [limit] keeps the dashboard query cheap. */
    fun observeTransactions(limit: Int = AllTransactions): Flow<List<Transaction>>

    /**
     * One expense by id, for the detail screen.
     *
     * Emits null when the row does not exist - the detail screen is reachable by a navigation argument
     * that outlives the row, so "gone" is a state the screen has to render rather than an error.
     */
    fun observeTransaction(id: Long): Flow<Transaction?>

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

    /**
     * Deletes the expense and, when it was created from a capture, that capture row too.
     *
     * Returns the capture's stored image URI when there is one, so the caller can delete the file.
     * Deleting the file is not this layer's job, but leaving the row behind would be an orphan the
     * sweep could never attribute; both halves happen here and the caller only removes the bytes.
     */
    suspend fun deleteTransactionAndOrphanCapture(id: Long): String?

    /** Deletes the expense only. Kept for callers that genuinely have no capture to clean up. */
    suspend fun deleteTransaction(id: Long)

    fun observeRecentCaptures(limit: Int = RecentCaptures): Flow<List<Capture>>

    suspend fun findCapture(id: Long): Capture?

    /** Every image URI a capture row still points at, for the periodic orphan-file sweep. */
    suspend fun captureImageUris(): List<String>

    /**
     * Records an image the moment it is taken or picked, before any extraction has run on it.
     *
     * Returns the id of the existing row when the same image URI is recorded twice, so callers always
     * get a usable id rather than a silent no-op.
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

    /**
     * Marks a capture confirmed and drops its OCR text and structured JSON.
     *
     * The transaction is now the record; keeping raw receipt text around after confirmation is
     * retention without a purpose. Returns the image URI so the caller can delete the file.
     */
    suspend fun confirmCaptureAndForgetExtraction(captureId: Long): String?

    /**
     * Marks a capture failed and deletes its row, returning the image URI to remove.
     *
     * Used by Discard and by the review screen's back-out path: a receipt the user rejected should
     * not linger in storage.
     */
    suspend fun discardCapture(captureId: Long): String?

    /**
     * Removes all receipt data: capture rows, their stored image URIs, and the links from
     * transactions that pointed at them.
     *
     * Returns the URIs whose files the caller must delete. The ledger rows themselves are kept: the
     * user asked to remove photos, not their spending history.
     */
    suspend fun forgetAllCaptures(): List<String>

    /**
     * Seeds the category taxonomy.
     *
     * The ledger itself is never seeded: an empty dashboard is the honest state for a new install, and
     * both the dashboard and the charts have an empty state for it.
     */
    suspend fun seedIfEmpty()

    companion object {
        const val AllTransactions = Int.MAX_VALUE
        const val RecentCaptures = 20
    }
}
