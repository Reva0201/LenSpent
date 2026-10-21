package org.umn.lensspent

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.umn.lensspent.data.extraction.CaptureExtractor
import org.umn.lensspent.data.extraction.ExtractionStage
import org.umn.lensspent.data.extraction.ExtractionStep
import org.umn.lensspent.data.extraction.MlKitCaptureExtractor
import org.umn.lensspent.data.local.LensSpentDatabase
import org.umn.lensspent.data.model.Capture
import org.umn.lensspent.data.model.CaptureSource
import org.umn.lensspent.data.model.CaptureStatus
import org.umn.lensspent.data.repository.OfflineTransactionRepository

/**
 * The steps the pipeline reports while it reads a real image on a device.
 */
@RunWith(AndroidJUnit4::class)
class ExtractionTraceFixtureTest {

    @Test
    fun theFastPathStepsAreReportedOnDevice() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, LensSpentDatabase::class.java).build()
        val repository = OfflineTransactionRepository(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            captureDao = database.captureDao()
        )
        val extractor: CaptureExtractor = MlKitCaptureExtractor(
            context = context,
            repository = repository
        )

        val receipt = drawnReceipt(File(context.cacheDir, ReceiptName), Rows)
        val steps = mutableListOf<ExtractionStep>()
        try {
            extractor.extract(
                Capture(
                    id = 1L,
                    sourceUri = Uri.fromFile(receipt).toString(),
                    source = CaptureSource.PICKER,
                    capturedAt = 0L,
                    status = CaptureStatus.CAPTURED
                )
            ) { step -> steps += step }
        } finally {
            database.close()
        }

        steps.forEach { step -> Log.i(Tag, "${step.stage.label}: ${step.detail}") }

        assertEquals(
            "the trace must open with the recognizer",
            ExtractionStage.OCR,
            steps.first().stage
        )
        assertTrue(
            "the fast path stage must be reached",
            steps.any { step -> step.stage == ExtractionStage.FAST_PATH }
        )
    }

    private fun drawnReceipt(target: File, rows: List<String>): File {
        val bitmap = Bitmap.createBitmap(Width, Height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = TextSize
        }
        rows.forEachIndexed { index, row ->
            canvas.drawText(row, Margin, FirstBaseline + index * LineHeight, paint)
        }
        FileOutputStream(target).use { sink ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, sink)
        }
        bitmap.recycle()
        return target
    }

    private companion object {
        const val Tag = "ExtractionTraceFixtureTest"
        const val ReceiptName = "trace-receipt.png"
        const val Width = 720
        const val Height = 1280
        const val Margin = 40f
        const val TextSize = 44f
        const val FirstBaseline = 140f
        const val LineHeight = 80f

        val Rows = listOf(
            "TOKO SUMBER JAYA",
            "Jl. Merdeka No. 5, Bandung",
            "12/09/2026 14:32",
            "Kopi Susu 25.000",
            "Gula 12.000",
            "TOTAL 37.000",
            "Terima kasih"
        )
    }
}
