package org.umn.lensspent.ui.settings

import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.umn.lensspent.data.local.UserPreferencesRepository
import org.umn.lensspent.data.repository.CaptureImageDeleter
import org.umn.lensspent.data.repository.StubTransactionRepository
import org.umn.lensspent.data.repository.TransactionRepository

/**
 * The receipt-data removal action.
 *
 * What matters is the split: photos and OCR text go, ledger rows stay, and the transaction rows stop
 * claiming to have come from a photo that no longer exists. A test does not need a real directory to
 * check that - only that every URI the repository hands back reaches the deleter.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptRemovalTest {

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private val deleted = mutableListOf<String?>()

    private fun viewModel(ledger: TransactionRepository = StubTransactionRepository()) =
        SettingsViewModel(
            preferences = UserPreferencesRepository(TestPreferencesStore.create()),
            ledger = ledger,
            images = CaptureImageDeleter { uri -> deleted += uri },
            receiptBytes = { 2048L },
            deleteReceiptImages = { 1 },
            zone = ZoneId.of("Asia/Jakarta")
        )

    @Test
    fun `removal is behind a confirmation`() {
        val viewModel = viewModel()

        viewModel.onReceiptRemovalRequested()

        assertTrue(viewModel.isConfirmingReceiptRemoval.value)
        assertTrue("nothing may be deleted before confirmation", deleted.isEmpty())
    }

    @Test
    fun `dismissing the confirmation deletes nothing`() {
        val viewModel = viewModel()

        viewModel.onReceiptRemovalRequested()
        viewModel.onReceiptRemovalDismissed()

        assertFalse(viewModel.isConfirmingReceiptRemoval.value)
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun `confirming removes every capture image the ledger reports`() {
        val uris = listOf(
            "file:///data/user/0/org.umn.lensspent/files/receipts/a.jpg",
            "file:///data/user/0/org.umn.lensspent/files/receipts/b.jpg"
        )
        val ledger = RecordingCaptureRepository(forgetResult = uris)
        val viewModel = viewModel(ledger)

        viewModel.onReceiptRemovalRequested()
        viewModel.onReceiptRemovalConfirmed()

        assertFalse(viewModel.isConfirmingReceiptRemoval.value)
        assertEquals(uris, deleted)
        assertEquals("the repository must be asked once", 1, ledger.forgetCalls)
    }

    @Test
    fun `a repository with no captures completes without inventing deletions`() {
        val viewModel = viewModel()

        viewModel.onReceiptRemovalConfirmed()

        assertTrue(deleted.isEmpty())
        assertFalse(viewModel.isConfirmingReceiptRemoval.value)
    }
}

/** A repository whose only meaningful method here is [forgetAllCaptures]. */
private class RecordingCaptureRepository(
    private val forgetResult: List<String>
) : TransactionRepository by StubTransactionRepository() {

    var forgetCalls = 0
        private set

    override suspend fun forgetAllCaptures(): List<String> {
        forgetCalls += 1
        return forgetResult
    }
}

/** A throwaway DataStore per test; the screen only reads a handful of keys. */
internal object TestPreferencesStore {
    fun create() = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
        scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()
        ),
        produceFile = {
            java.io.File.createTempFile("lensspent-test-prefs", ".preferences_pb").also {
                it.deleteOnExit()
            }
        }
    )
}

