package org.umn.lensspent

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.umn.lensspent.data.extraction.CaptureExtractor
import org.umn.lensspent.data.extraction.MlKitCaptureExtractor
import org.umn.lensspent.data.local.LensSpentDatabase
import org.umn.lensspent.data.model.Capture
import org.umn.lensspent.data.model.CaptureSource
import org.umn.lensspent.data.model.CaptureStatus
import org.umn.lensspent.data.repository.OfflineTransactionRepository

/**
 * The one part of the pipeline no JVM test can reach: the recognizer reading a real photograph.
 *
 * A receipt is somebody's private data and does not belong in the repository, so the fixture is
 * supplied by hand - a photographed till slip - and the test skips itself when there is none:
 *
 * ```
 * adb push receipt.jpg /sdcard/Android/data/org.umn.lensspent/files/receipt.jpg
 * adb shell am instrument -w -e class org.umn.lensspent.MlKitReceiptFixtureTest \
 *     org.umn.lensspent.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * What it proves is the half that [org.umn.lensspent.data.extraction.OcrRowsTest] cannot: that ML
 * Kit's boxes reach `rowsOf` intact, and that a photograph - not a hand-written list of lines -
 * produces a merchant and an amount instead of the empty sheet this test was written after.
 */
@RunWith(AndroidJUnit4::class)
class MlKitReceiptFixtureTest {

    @Test
    fun readsAReceiptOffARealPhotograph() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.getExternalFilesDir(null), FixtureName)
        assumeTrue(
            "no $FixtureName pushed to ${fixture.parentFile}; this test reads a real receipt",
            fixture.exists()
        )

        val database = Room.inMemoryDatabaseBuilder(context, LensSpentDatabase::class.java).build()
        val repository = OfflineTransactionRepository(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            captureDao = database.captureDao()
        )
        // Typed as the seam, not the adapter, so the debug tap `extract` offers is left at its no-op
        // default: this test is about what comes out of the pipeline, not about what it reports.
                val extractor: CaptureExtractor = MlKitCaptureExtractor(
            context = context,
            repository = repository
        )

        val draft = try {
            extractor.extract(
                Capture(
                    id = 1L,
                    sourceUri = Uri.fromFile(fixture).toString(),
                    source = CaptureSource.PICKER,
                    capturedAt = 0L,
                    status = CaptureStatus.CAPTURED
                )
            )
        } finally {
            database.close()
        }

        assertTrue("no merchant was read off the photograph", draft.merchant.isNotBlank())
        assertNotEquals("no amount was read off the photograph", 0L, draft.amountRupiah)
    }

    private companion object {
        const val FixtureName = "receipt.jpg"
    }
}
