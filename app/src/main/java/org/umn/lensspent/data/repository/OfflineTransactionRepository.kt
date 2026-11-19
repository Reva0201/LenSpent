package org.umn.lensspent.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.umn.lensspent.data.local.CaptureDao
import org.umn.lensspent.data.local.CategoryDao
import org.umn.lensspent.data.local.DefaultCategories
import org.umn.lensspent.data.local.TransactionDao
import org.umn.lensspent.data.local.entity.CaptureEntity
import org.umn.lensspent.data.local.entity.CategoryEntity
import org.umn.lensspent.data.local.entity.TransactionEntity
import org.umn.lensspent.data.model.Capture
import org.umn.lensspent.data.model.CaptureSource
import org.umn.lensspent.data.model.CaptureStatus
import org.umn.lensspent.data.model.Category
import org.umn.lensspent.data.model.Transaction

/**
 * Room-backed [TransactionRepository].
 *
 * Categories are read alongside transactions and joined in memory instead of with a SQL join: the
 * taxonomy is a handful of rows, and every query here stays trivial to read and reason about.
 */
class OfflineTransactionRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val captureDao: CaptureDao
) : TransactionRepository {

    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { entities -> entities.map(CategoryEntity::toModel) }

    override fun observeTransactions(limit: Int): Flow<List<Transaction>> =
        withCategories(transactionDao.observeRecent(limit))

    override fun observeTransactionsBetween(fromMillis: Long, toMillis: Long): Flow<List<Transaction>> =
        withCategories(transactionDao.observeBetween(fromMillis, toMillis))

    override fun observeTransaction(id: Long): Flow<Transaction?> =
        combine(transactionDao.observeById(id), categoryDao.observeAll()) { entity, categories ->
            // Nothing to join when the row is gone: the flow itself answers "gone", and the caller is
            // expected to render that rather than treat it as a load that has not finished.
            if (entity == null) return@combine null
            val category = categories.firstOrNull { it.id == entity.categoryId }?.toModel()
                ?: UncategorisedCategory
            entity.toModel(category)
        }

    override fun observeSumBetween(fromMillis: Long, toMillis: Long): Flow<Long> =
        transactionDao.observeSumBetween(fromMillis, toMillis)

    override fun observeRecentCaptures(limit: Int): Flow<List<Capture>> =
        captureDao.observeRecent(limit).map { entities -> entities.map(CaptureEntity::toModel) }

    override suspend fun addTransaction(
        merchant: String,
        amount: Long,
        categoryId: Long,
        occurredAt: Long,
        note: String?,
        captureId: Long?
    ): Long = transactionDao.insert(
        TransactionEntity(
            merchant = merchant,
            amount = amount,
            categoryId = categoryId,
            occurredAt = occurredAt,
            note = note,
            captureId = captureId
        )
    )

    override suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.updateFields(
            id = transaction.id,
            merchant = transaction.merchant,
            amount = transaction.amount,
            categoryId = transaction.category.id,
            occurredAt = transaction.occurredAt,
            note = transaction.note
        )
    }

    override suspend fun deleteTransaction(id: Long) = transactionDao.deleteById(id)

    /**
     * Deletes the row and its capture together.
     *
     * The capture is only deleted when no other transaction still points at it, which keeps a
     * duplicated scan from removing an image another row still describes. The image URI comes back to
     * the caller because deleting bytes needs an Android `Context`, which this layer does not hold.
     */
    override suspend fun deleteTransactionAndOrphanCapture(id: Long): String? {
        val transaction = transactionDao.findById(id) ?: return null
        transactionDao.deleteById(id)
        return releaseCaptureIfUnreferenced(transaction.captureId)
    }

    override suspend fun captureImageUris(): List<String> = captureDao.allSourceUris()

    override suspend fun recordCapture(
        sourceUri: String,
        source: CaptureSource,
        capturedAt: Long
    ): Long {
        val insertedId = captureDao.insert(
            CaptureEntity(sourceUri = sourceUri, source = source, capturedAt = capturedAt)
        )
        if (insertedId != -1L) return insertedId
        // Insert was ignored because sourceUri is unique: the same image is already tracked.
        return captureDao.findBySourceUri(sourceUri)?.id ?: insertedId
    }

    override suspend fun findCapture(id: Long): Capture? = captureDao.findById(id)?.toModel()

    override suspend fun updateCaptureStatus(captureId: Long, status: CaptureStatus) =
        captureDao.updateStatus(captureId, status)

    override suspend fun saveExtraction(
        captureId: Long,
        status: CaptureStatus,
        ocrText: String?,
        structuredJson: String?,
        modelVersion: String?
    ) = captureDao.updateExtraction(
        id = captureId,
        status = status,
        ocrText = ocrText,
        structuredJson = structuredJson,
        modelVersion = modelVersion
    )

    override suspend fun confirmCaptureAndForgetExtraction(captureId: Long): String? {
        val capture = captureDao.findById(captureId) ?: return null
        captureDao.updateStatus(captureId, CaptureStatus.CONFIRMED)
        captureDao.clearExtraction(captureId)
        // The image is still on disk at this point; the caller deletes it once the confirmation has
        // been shown. Returning the URI is what makes that possible without leaking a file.
        return capture.sourceUri
    }

    override suspend fun discardCapture(captureId: Long): String? {
        val capture = captureDao.findById(captureId) ?: return null
        captureDao.deleteById(captureId)
        return capture.sourceUri
    }

    override suspend fun forgetAllCaptures(): List<String> {
        val uris = captureDao.allSourceUris()
        transactionDao.clearCaptureLinks()
        captureDao.deleteAll()
        return uris
    }

    override suspend fun seedIfEmpty() {
        // The taxonomy only. The ledger starts empty on purpose: a new install shows the user their own
        // spending or an empty state, never rows they did not enter.
        if (categoryDao.count() == 0) {
            categoryDao.insertAll(DefaultCategories)
        }
    }

    /**
     * Removes a capture row once nothing points at it, returning the image URI.
     *
     * Read-then-delete is safe here because both statements run on Room's single writer and the
     * ledger is single-user: there is no interleaving writer that could add a reference in between.
     */
    private suspend fun releaseCaptureIfUnreferenced(captureId: Long?): String? {
        if (captureId == null) return null
        if (transactionDao.countForCapture(captureId) > 0) return null
        val capture = captureDao.findById(captureId) ?: return null
        captureDao.deleteById(captureId)
        return capture.sourceUri
    }

    /** Joins transactions with the taxonomy in one pass, so no row is ever emitted half-built. */
    private fun withCategories(
        source: Flow<List<TransactionEntity>>
    ): Flow<List<Transaction>> = combine(source, categoryDao.observeAll()) { entities, categories ->
        val categoriesById = categories.associateBy { it.id }
        entities.map { entity ->
            // The foreign key makes a missing category impossible, but hiding money would be far worse
            // than showing the row as uncategorised, so fall back instead of dropping it.
            val category = categoriesById[entity.categoryId]?.toModel() ?: UncategorisedCategory
            entity.toModel(category)
        }
    }

    private companion object {
        /** Never expected on screen: expenses are only ever written against a real category row. */
        val UncategorisedCategory = Category(
            id = -1L,
            name = "Uncategorised",
            iconKey = "other",
            colorHex = "#78909C"
        )
    }
}

private fun CategoryEntity.toModel(): Category =
    Category(id = id, name = name, iconKey = iconKey, colorHex = colorHex)

private fun TransactionEntity.toModel(category: Category): Transaction = Transaction(
    id = id,
    merchant = merchant,
    amount = amount,
    category = category,
    occurredAt = occurredAt,
    note = note,
    captureId = captureId
)

private fun CaptureEntity.toModel(): Capture = Capture(
    id = id,
    sourceUri = sourceUri,
    source = source,
    capturedAt = capturedAt,
    status = status,
    ocrText = ocrText,
    structuredJson = structuredJson,
    modelVersion = modelVersion
)
