package org.umn.lensspent.data.extraction

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import org.umn.lensspent.data.model.Capture
import org.umn.lensspent.data.model.ExpenseDraft
import org.umn.lensspent.data.repository.TransactionRepository

/**
 * The recognizer end of the offline pipeline: bundled ML Kit OCR, handed to [ReceiptStructurer].
 *
 * The recognizer is the bundled `com.google.mlkit:text-recognition` model, so a receipt is read
 * entirely on-device with no first-run download and nothing leaving the phone.
 *
 * This class is deliberately thin. Reading the image is the only thing it does that the JVM cannot
 * test; the decisions - persisting the raw text, running the fast path, falling back to the
 * on-device model, and checking what that model says - all live in [ReceiptStructurer], where they
 * are covered by ordinary tests.
 *
 * Nothing here is allowed to fail its way out: a revoked photo permission, a file deleted out from
 * under a capture, a corrupt image or a recognizer that will not start all still end in a draft, so
 * the confirmation sheet always opens and the screen is never left waiting.
 */
class MlKitCaptureExtractor(
    private val context: Context,
    private val repository: TransactionRepository
) : CaptureExtractor {

    private val structurer = ReceiptStructurer(
        repository = repository,
        ocrModelVersion = ModelVersion
    )

    override suspend fun extract(
        capture: Capture,
        onStep: (ExtractionStep) -> Unit
    ): ExpenseDraft {
        // The same steps the sheet is showing are written to logcat, from one place: a receipt that
        // read badly can then be looked at after the pane is gone, which is the only way to see a
        // model load on a device nobody is holding.
        val report: (ExtractionStep) -> Unit = { step ->
            Log.d(Tag, "${step.stage.label}: ${step.detail}")
            onStep(step)
        }

        return try {
            report(ExtractionStep(ExtractionStage.OCR, "reading the image"))
            val recognized = readReceipt(capture)
            // The rows, not the raw block order: when a capture comes back empty this is what the
            // pipeline actually read, and it is the one thing that cannot be seen from the ledger.
            Log.d(Tag, "Captured ${capture.id} rows:\n" + recognized.lines.joinToString("\n"))
            report(ExtractionStep(ExtractionStage.OCR, "${recognized.lines.size} rows read"))
            structurer.structure(capture, recognized, onStep = report)
                .also { draft ->
                Log.i(
                    Tag,
                    "Capture ${capture.id} -> merchant='${draft.merchant}' amount=${draft.amountRupiah}"
                )
            }
        } catch (e: CancellationException) {
            // The screen went away mid-extraction. That is not a failed extraction, and swallowing it
            // would break the scope the sheet's own lifecycle runs on.
            throw e
        } catch (e: Exception) {
            report(ExtractionStep(ExtractionStage.OCR, "could not read the image: ${e.message}"))
            Log.e(Tag, "Could not read capture ${capture.id}; opening an empty sheet", e)
            ExpenseDraft()
        }
    }

    /** The one Android-only step: decode the image the capture points at, and read its text. */
    private suspend fun readReceipt(capture: Capture): RecognizedText {
        val image = InputImage.fromFilePath(context, Uri.parse(capture.sourceUri))

        val recognized = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(image)
            .await()

        val lines = recognized.textBlocks.flatMap { block -> block.lines }
        val placed = lines.mapNotNull { line ->
            line.boundingBox?.let { box -> OcrLine(line.text, box.left, box.top, box.bottom, box.right) }
        }

        return RecognizedText(
            // The recognizer's own text is kept as it came: it is what the capture records as its
            // OCR output, and reordering it would write down something the recognizer never said.
            text = recognized.text,
            lines = if (placed.size == lines.size) rowsOf(placed) else lines.map { line -> line.text },
            ocrLines = placed
        )
    }

    companion object {
        private const val Tag = "MlKitCaptureExtractor"

        /** Stored on the capture so it is visible which model produced its OCR text. */
        const val ModelVersion = "mlkit-text-recognition-16.0.1"
    }
}
