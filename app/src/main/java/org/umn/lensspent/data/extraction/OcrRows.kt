package org.umn.lensspent.data.extraction

/**
 * One line as the recognizer read it, and where on the image it was printed.
 *
 * Only the vertical extent and the left edge are kept. A receipt reads top to bottom and a printed
 * row reads left to right, so those three numbers are all that is needed to put a line back where
 * the receipt put it.
 */
internal data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val bottom: Int,
    val right: Int = 0
)

internal data class ProcessedRow(
    val text: String,
    val left: Int,
    val top: Int,
    val bottom: Int,
    val right: Int
) {
    val height: Int get() = bottom - top
}

/**
 * Puts the recognizer's lines back into the rows the receipt was printed in.
 *
 * ML Kit groups its lines into blocks, and a block is not a printed row. On a real photo it splits one
 * row into several blocks (item name on the left, price on the right) and returns them in an order that
 * has nothing to do with where they were printed, so "Total" ends up far from its figure and
 * [ReceiptParser] has nothing to read.
 *
 * Lines are placed by the band of the image they occupy instead. A line joins the row above it when the
 * two overlap vertically by at least a third of the shorter one. Anchoring each row on its topmost line
 * stops the tilted right edge of a row from walking into the next one.
 *
 * The text itself is unchanged, only reordered.
 */
internal fun rowsOf(lines: List<OcrLine>): List<String> {
    return processedRowsOf(lines).map { it.text }
}

internal fun processedRowsOf(lines: List<OcrLine>): List<ProcessedRow> {
    val rows = mutableListOf<Row>()
    for (line in lines.sortedWith(compareBy({ it.top }, { it.left }))) {
        val openRow = rows.lastOrNull()
        if (openRow != null && openRow.accepts(line)) {
            openRow.add(line)
        } else {
            rows += Row(line)
        }
    }
    return rows.map { row ->
        val txt = row.text
        val l = row.parts.minOfOrNull { it.left } ?: 0
        val t = row.parts.minOfOrNull { it.top } ?: 0
        val b = row.parts.maxOfOrNull { it.bottom } ?: 0
        val r = row.parts.maxOfOrNull { it.right } ?: 0
        ProcessedRow(text = txt, left = l, top = t, bottom = b, right = r)
    }
}

/** A printed row being built up out of the lines that belong to it. */
private class Row(anchor: OcrLine) {

    val parts = mutableListOf(anchor)

    val text: String
        get() = parts.sortedBy { it.left }.joinToString(" ") { it.text }

    /** Whether [line] was printed on this row rather than on the one below it. */
    fun accepts(line: OcrLine): Boolean {
        val anchor = parts.first()
        val overlap = minOf(anchor.bottom, line.bottom) - maxOf(anchor.top, line.top)
        val shortest = minOf(anchor.bottom - anchor.top, line.bottom - line.top)
        return overlap * 3 >= shortest
    }

    fun add(line: OcrLine) {
        parts += line
    }
}
