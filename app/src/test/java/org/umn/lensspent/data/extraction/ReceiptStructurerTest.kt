package org.umn.lensspent.data.extraction

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.umn.lensspent.data.model.Capture
import org.umn.lensspent.data.model.CaptureSource
import org.umn.lensspent.data.model.CaptureStatus
import org.umn.lensspent.data.model.ExpenseDraft
import org.umn.lensspent.data.repository.FakeTransactionRepository

class ReceiptStructurerTest {

    private val repository = FakeTransactionRepository()
    private val steps = mutableListOf<ExtractionStep>()

    private val fastPathReceipt = RecognizedText(
        text = "TOKO SUMBER JAYA\nKopi Susu 25.000\nTOTAL 25.000",
        lines = listOf("TOKO SUMBER JAYA", "Kopi Susu 25.000", "TOTAL 25.000")
    )

    private val capture = Capture(
        id = 7L,
        sourceUri = "content://lensspent/receipt.jpg",
        source = CaptureSource.CAMERA,
        capturedAt = 1_700_000_000_000L,
        status = CaptureStatus.CAPTURED
    )

    private fun structurer() = ReceiptStructurer(
        repository = repository,
        ocrModelVersion = MlKitCaptureExtractor.ModelVersion
    )

    private fun structure(recognized: RecognizedText): ExpenseDraft {
        steps.clear()
        return runBlocking {
            structurer().structure(capture, recognized) { step -> steps += step }
        }
    }

    @Test
    fun `the fast path answers`() {
        val draft = structure(fastPathReceipt)

        assertEquals("TOKO SUMBER JAYA", draft.merchant)
        assertEquals(25_000L, draft.amountRupiah)
        assertEquals(1, repository.savedExtractions.size)
        assertEquals(CaptureStatus.OCR_DONE, repository.savedExtractions.single().status)
        assertEquals(MlKitCaptureExtractor.ModelVersion, repository.savedExtractions.single().modelVersion)
    }

    @Test
    fun `the raw text is recorded before anything tries to interpret it`() {
        structure(fastPathReceipt)

        val first = repository.savedExtractions.first()
        assertEquals(CaptureStatus.OCR_DONE, first.status)
        assertEquals(fastPathReceipt.text, first.ocrText)
        assertEquals(capture.id, first.captureId)
    }

    @Test
    fun `the fast path reports what it read`() {
        structure(fastPathReceipt)

        val fastPath = steps.single()
        assertEquals(ExtractionStage.FAST_PATH, fastPath.stage)
        assertTrue(fastPath.detail, fastPath.detail.contains("TOKO SUMBER JAYA"))
        assertTrue(fastPath.detail, fastPath.detail.contains("25000"))
    }
}
