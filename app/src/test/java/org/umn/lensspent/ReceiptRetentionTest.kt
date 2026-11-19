package org.umn.lensspent

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.umn.lensspent.data.extraction.CaptureExtractor
import org.umn.lensspent.data.model.Capture
import org.umn.lensspent.data.model.CaptureSource
import org.umn.lensspent.data.model.CaptureStatus
import org.umn.lensspent.data.model.Category
import org.umn.lensspent.data.model.ExpenseDraft
import org.umn.lensspent.data.model.Transaction
import org.umn.lensspent.data.repository.CaptureImageDeleter
import org.umn.lensspent.data.repository.StubTransactionRepository
import org.umn.lensspent.data.repository.TransactionRepository

/**
 * The retention rules, at the point they are decided.
 *
 * A receipt photo is the most sensitive artifact this app creates, and every exit from the review
 * flow must remove it: confirm, discard, and a delete of the transaction it became. These tests use a
 * recording repository and a recording deleter rather than a filesystem, so they assert the decision
 * ("the image URI reached the deleter") instead of re-testing `File.delete()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptRetentionTest {

    private val deleted = mutableListOf<String?>()

    /** Mirrors `ImageStorage.deleteReceipt`: a null URI means "nothing to delete", not a delete. */
    private val deleter = CaptureImageDeleter { uri -> if (uri != null) deleted += uri }

    /**
     * The Main dispatcher does not exist in a JVM unit test; the ViewModels under test use
     * `viewModelScope`, which needs one. An unconfined test dispatcher runs each launch inline, which
     * is what lets these tests make an assertion immediately after calling a ViewModel method.
     */
    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private val category = Category(id = 1L, name = "Dining", iconKey = "dining", colorHex = "#FF7043")

    private val capture = Capture(
        id = 7L,
        sourceUri = ImageUri,
        source = CaptureSource.CAMERA,
        capturedAt = 1_700_000_000_000L,
        status = CaptureStatus.STRUCTURED
    )

    // ---------------------------------------------------------------------------------------------
    // Confirming a reviewed expense.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `confirming keeps the transaction and deletes the receipt image`() = runBlocking {
        val ledger = ReviewRepository(captures = mutableMapOf(7L to capture))
        val viewModel = reviewViewModel(ledger)
        viewModel.start(7L)
        awaitEditing(viewModel)

        viewModel.save()
        awaitSaved(viewModel)

        assertEquals("the ledger must have the expense", 1, ledger.added.size)
        assertEquals("the photo must be gone", listOf(ImageUri), deleted)
        assertEquals("the capture row is kept for provenance", 1, ledger.confirmed.size)
        assertEquals(
            "raw OCR text must not be retained after confirmation",
            listOf(7L),
            ledger.extractionsCleared
        )
    }

    /**
     * The single most important retention assertion: a save that fails leaves the receipt reviewable,
     * and its photo is NOT deleted out from under the user.
     */
    @Test
    fun `a failed save leaves the receipt image in place`() = runBlocking {
        val ledger = ReviewRepository(
            captures = mutableMapOf(7L to capture),
            failAdd = true
        )
        val viewModel = reviewViewModel(ledger)
        viewModel.start(7L)
        awaitEditing(viewModel)

        viewModel.save()
        awaitIdle()

        assertTrue("the photo is still needed to retry", deleted.isEmpty())
        assertTrue(
            "the sheet stays open on the user's input",
            viewModel.uiState.value is ExpenseReviewUiState.Editing
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Regressions: a save must write money exactly once.
    // ---------------------------------------------------------------------------------------------

    /**
     * The write is not idempotent, so a second tap before the first finishes must not add a row.
     * `canSave` alone does not cover this: it is only re-evaluated on recomposition, and two taps can
     * land in the same frame.
     */
    @Test
    fun `a double tap on save writes one expense`() = runBlocking {
        val ledger = ReviewRepository(captures = mutableMapOf(7L to capture))
        val viewModel = reviewViewModel(ledger)
        viewModel.start(7L)
        awaitEditing(viewModel)

        viewModel.save()
        viewModel.save()
        awaitSaved(viewModel)

        assertEquals("one tap is one row", 1, ledger.added.size)
    }

    /**
     * Cleanup runs after the row is committed, so a failure there must not make Save available again:
     * a retry would insert the same expense a second time. The photo is recoverable elsewhere.
     */
    @Test
    fun `a failed cleanup does not invite a duplicate write`() = runBlocking {
        val ledger = ReviewRepository(
            captures = mutableMapOf(7L to capture),
            failCleanup = true
        )
        val viewModel = reviewViewModel(ledger)
        viewModel.start(7L)
        awaitEditing(viewModel)

        viewModel.save()
        awaitSaved(viewModel)
        viewModel.save()
        awaitIdle()

        assertEquals("the row is written once", 1, ledger.added.size)
        assertEquals("the confirmation is still shown", 1, ledger.confirmed.size)
    }

    // ---------------------------------------------------------------------------------------------
    // Discarding a review.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `discarding deletes the capture row and the receipt image`() = runBlocking {
        val ledger = ReviewRepository(captures = mutableMapOf(7L to capture))
        val viewModel = reviewViewModel(ledger)
        viewModel.start(7L)
        awaitEditing(viewModel)

        viewModel.discard()
        awaitFinished(viewModel)

        assertEquals("an abandoned capture must not linger", emptyList<Long>(), ledger.captures.keys.toList())
        assertEquals(listOf(ImageUri), deleted)
        assertTrue(ledger.added.isEmpty())
    }

    // ---------------------------------------------------------------------------------------------
    // Deleting the transaction later.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `deleting a transaction deletes its capture and image`() = runBlocking {
        val ledger = DetailRepository(captureUri = ImageUri)
        val viewModel = TransactionDetailViewModel(
            repository = ledger,
            transactionId = 1L,
            images = deleter
        )
        awaitIdle()

        viewModel.confirmDelete()
        awaitFinished(viewModel)

        assertEquals(listOf(1L), ledger.deletedTransactions)
        assertEquals(listOf(ImageUri), deleted)
    }

    /**
     * A manual entry has no capture, so deletion must not pretend it found one - and must not throw.
     */
    @Test
    fun `deleting a manually added transaction deletes no image`() = runBlocking {
        val ledger = DetailRepository(captureUri = null)
        val viewModel = TransactionDetailViewModel(
            repository = ledger,
            transactionId = 1L,
            images = deleter
        )
        awaitIdle()

        viewModel.confirmDelete()
        awaitFinished(viewModel)

        assertEquals(listOf(1L), ledger.deletedTransactions)
        assertTrue(deleted.isEmpty())
    }

    /** Deleting is still two-step at the ViewModel level: asking is not deleting. */
    @Test
    fun `asking to delete changes nothing until confirmed`() = runBlocking {
        val ledger = DetailRepository(captureUri = ImageUri)
        val viewModel = TransactionDetailViewModel(
            repository = ledger,
            transactionId = 1L,
            images = deleter
        )
        awaitIdle()

        viewModel.requestDelete()
        awaitIdle()

        assertTrue(viewModel.confirmingDelete.value)
        assertTrue(ledger.deletedTransactions.isEmpty())
        assertTrue(deleted.isEmpty())

        viewModel.cancelDelete()
        assertFalse(viewModel.confirmingDelete.value)
        assertTrue(ledger.deletedTransactions.isEmpty())
    }

    // ---------------------------------------------------------------------------------------------
    // Harness.
    // ---------------------------------------------------------------------------------------------

    private fun reviewViewModel(ledger: ReviewRepository) = ExpenseReviewViewModel(
        repository = ledger,
        extractor = StubExtractor,
        images = deleter,
        clock = FixedClock
    )

    /**
     * A ViewModel launch is asynchronous; these helpers spin the dispatcher by yielding until the
     * state the test needs is reachable, with a hard bound so a stuck test fails rather than hangs.
     * With an unconfined Main dispatcher most of these return on the first check.
     */
    private suspend fun awaitEditing(viewModel: ExpenseReviewViewModel) {
        repeat(200) {
            if (viewModel.uiState.value is ExpenseReviewUiState.Editing) return
            kotlinx.coroutines.yield()
        }
        error("review never became editable; state=${viewModel.uiState.value}")
    }

    private suspend fun awaitSaved(viewModel: ExpenseReviewViewModel) {
        repeat(200) {
            if (viewModel.savedExpense.value != null) return
            kotlinx.coroutines.yield()
        }
        error("review never saved; state=${viewModel.uiState.value}")
    }

    private suspend fun awaitFinished(viewModel: ExpenseReviewViewModel) {
        repeat(200) {
            if (viewModel.finished.value) return
            kotlinx.coroutines.yield()
        }
        error("review never finished")
    }

    private suspend fun awaitFinished(viewModel: TransactionDetailViewModel) {
        repeat(200) {
            if (viewModel.finished.value) return
            kotlinx.coroutines.yield()
        }
        error("detail screen never finished deleting")
    }

    private suspend fun awaitIdle() {
        repeat(50) { kotlinx.coroutines.yield() }
    }

    private companion object {
        const val ImageUri = "file:///data/user/0/org.umn.lensspent/files/receipts/receipt-7.jpg"

        /** 2026-09-22 12:00 in Jakarta: after the sample expense's date, so it is saveable. */
        val FixedClock: Clock = Clock.fixed(Instant.parse("2026-09-22T05:00:00Z"), ZoneId.of("Asia/Jakarta"))

        val StubExtractor = object : CaptureExtractor {
            override suspend fun extract(
                capture: Capture,
                onStep: (org.umn.lensspent.data.extraction.ExtractionStep) -> Unit
            ): ExpenseDraft = ExpenseDraft(
                merchant = "Warung Sederhana",
                amountRupiah = 57_500L,
                categoryId = 1L,
                occurredAt = Instant.parse("2026-09-20T05:00:00Z").toEpochMilli()
            )
        }
    }
}

/**
 * A repository that answers the review flow: one capture, categories, and recorded writes.
 *
 * It also records the retention calls, which is the actual subject of these tests. Everything it does
 * not need throws, so a future change that starts reading something unexpected is visible.
 */
private class ReviewRepository(
    val captures: MutableMap<Long, Capture>,
    private val failAdd: Boolean = false,
    private val failCleanup: Boolean = false
) : TransactionRepository by StubTransactionRepository() {

    data class Added(
        val merchant: String,
        val amount: Long,
        val categoryId: Long,
        val occurredAt: Long,
        val note: String?,
        val captureId: Long?
    )

    val added = mutableListOf<Added>()
    val confirmed = mutableListOf<Long>()
    val extractionsCleared = mutableListOf<Long>()

    override fun observeCategories(): Flow<List<Category>> =
        flowOf(listOf(Category(1L, "Dining", "dining", "#FF7043")))

    override suspend fun findCapture(id: Long): Capture? = captures[id]

    override suspend fun addTransaction(
        merchant: String,
        amount: Long,
        categoryId: Long,
        occurredAt: Long,
        note: String?,
        captureId: Long?
    ): Long {
        if (failAdd) error("disk full")
        added += Added(merchant, amount, categoryId, occurredAt, note, captureId)
        return 1L
    }

    override suspend fun confirmCaptureAndForgetExtraction(captureId: Long): String? {
        confirmed += captureId
        if (failCleanup) error("cleanup failed after the row was written")
        extractionsCleared += captureId
        return captures[captureId]?.sourceUri
    }

    override suspend fun discardCapture(captureId: Long): String? {
        val removed = captures.remove(captureId)
        return removed?.sourceUri
    }
}

/** A repository for the detail screen's delete path. */
private class DetailRepository(private val captureUri: String?) : TransactionRepository by StubTransactionRepository() {

    val deletedTransactions = mutableListOf<Long>()

    override fun observeTransaction(id: Long): Flow<Transaction?> = flowOf(
        Transaction(
            id = id,
            merchant = "Warung Sederhana",
            amount = 57_500L,
            category = Category(1L, "Dining", "dining", "#FF7043"),
            occurredAt = 1_700_000_000_000L,
            captureId = if (captureUri != null) 7L else null
        )
    )

    override suspend fun deleteTransactionAndOrphanCapture(id: Long): String? {
        deletedTransactions += id
        return captureUri
    }

    override suspend fun deleteTransaction(id: Long) {
        deletedTransactions += id
    }
}
