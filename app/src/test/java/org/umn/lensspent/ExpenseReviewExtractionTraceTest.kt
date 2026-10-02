package org.umn.lensspent

import org.junit.Assert.assertEquals
import org.junit.Test
import org.umn.lensspent.data.extraction.ExtractionStage
import org.umn.lensspent.data.extraction.ExtractionStep

/**
 * The debug trace the processing pane shows while a receipt is being read.
 *
 * The pane's job is to answer a question the sheet cannot: did the on-device model run, and what did
 * it cost. The pipeline reports what happened and the sheet stamps when it arrived, so the two rules
 * worth pinning down are that arrival order survives - a trace that reorders itself would report a
 * guard verdict before the answer it judged - and that the log stays short enough to read.
 */
class ExpenseReviewExtractionTraceTest {

    private fun step(detail: String) = ExtractionStep(ExtractionStage.MODEL, detail)

    @Test
    fun `steps keep the order they were reported in, with the time each one arrived`() {
        var trace = emptyList<ExtractionTraceLine>()

        trace = appendTraceLine(trace, step("loading the model"), 0L)
        trace = appendTraceLine(trace, step("answered"), 8_300L)

        assertEquals(listOf("loading the model", "answered"), trace.map { it.step.detail })
        assertEquals(listOf(0L, 8_300L), trace.map { it.elapsedMillis })
    }

    @Test
    fun `an empty trace takes the first step`() {
        val trace = appendTraceLine(emptyList(), step("reading the image"), 12L)

        assertEquals(1, trace.size)
        assertEquals(ExtractionStage.MODEL, trace.single().step.stage)
        assertEquals(12L, trace.single().elapsedMillis)
    }

    @Test
    fun `the trace keeps the newest lines and drops the oldest ones`() {
        var trace = emptyList<ExtractionTraceLine>()
        repeat(20) { index -> trace = appendTraceLine(trace, step("step $index"), index.toLong()) }

        assertEquals(TraceLineLimit, trace.size)
        assertEquals("step ${20 - TraceLineLimit}", trace.first().step.detail)
        assertEquals("step 19", trace.last().step.detail)
        assertEquals((20 - TraceLineLimit).toLong(), trace.first().elapsedMillis)
    }

    @Test
    fun `a caller can ask for a shorter trace`() {
        var trace = emptyList<ExtractionTraceLine>()
        repeat(5) { index -> trace = appendTraceLine(trace, step("step $index"), 0L, limit = 2) }

        assertEquals(listOf("step 3", "step 4"), trace.map { it.step.detail })
    }

    @Test
    fun `an elapsed time reads as seconds with one decimal`() {
        assertEquals("0.0s", elapsedLabel(0L))
        // 140ms is a tenth of a second as displayed: the fast path must not read as a whole second
        assertEquals("0.1s", elapsedLabel(140L))
        assertEquals("1.0s", elapsedLabel(999L))
        assertEquals("8.3s", elapsedLabel(8_300L))
        assertEquals("75.0s", elapsedLabel(75_000L))
    }
}
