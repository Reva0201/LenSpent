package org.umn.lensspent.data.extraction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading the recognizer's output back into rows.
 *
 * The fixture is a real, hand-held photograph of an Alfamart till slip, together with the boxes the
 * packaged recognizer returned for it. It is the receipt that first showed the problem: every figure
 * and every label was recognised correctly, yet "Total" and the "Rp38.000" printed beside it came
 * back fourteen lines apart, in different blocks, so the fast path refused a receipt it had in fact
 * read perfectly. A synthetic receipt would not have caught that - only this one did.
 */
class OcrRowsTest {

    @Test
    fun `rebuilds a real receipt's rows in the order they are printed`() {
        val rows = rowsOf(AlfamartReceipt)

        assertEquals("Alfamart", rows.first())
        // Exactly as the recognizer read it, spacing and all: this pass reorders, it never rewrites.
        assertEquals("Terima kas ih", rows.last())
        // The receipt number is printed on the same row as the date, and belongs to that row rather
        // than to a row of its own above the store name.
        assertEquals("18.7.2024 08:49 #281", rows[4])
        // The row the problem was all about: label and figure reported from different blocks are one
        // printed row again, and the cash-tendered row below it is still below it.
        val total = rows.indexOf("Total Rp38.0U")
        assertTrue("the total row should have been rebuilt", total >= 0)
        assertEquals("Tunai Rp38.000", rows[total + 1])
    }

    @Test
    fun `reads the merchant and the total once the rows are rebuilt`() {
        val draft = ReceiptParser.parseLines(rowsOf(AlfamartReceipt))

        assertEquals("Alfamart", draft.merchant)
        // The total figure itself was misread as "Rp38.0U", so this is the fast path reading the
        // figure printed on the cash-tendered row directly beneath it - which is what the user paid.
        assertEquals(38_000L, draft.amountRupiah)
    }

    /**
     * The pipeline's own path: the boxes ML Kit returned, not the flattened rows the test above feeds
     * the parser. `rowsOf` drops every box, and flattened lines then all measure the same, so a
     * size-ranked merchant pick says "Alfamart" above while still naming an item off a real capture.
     * Reading order is what the paper printed, and the store's name is the first thing on it.
     */
    @Test
    fun `names the store on the top row rather than a taller item row below it`() {
        val recognized = RecognizedText(
            text = AlfamartReceipt.joinToString("\n") { it.text },
            lines = rowsOf(AlfamartReceipt),
            ocrLines = AlfamartReceipt
        )

        val draft = ReceiptParser.parseLines(recognized)

        assertEquals("Alfamart", draft.merchant)
        assertEquals(38_000L, draft.amountRupiah)
    }

    @Test
    fun `merges the halves of one printed row`() {
        // The recognizer reports the left half and the right half of a row as separate lines, a few
        // pixels apart vertically because the receipt is photographed at an angle.
        val rows = rowsOf(
            listOf(
                OcrLine("Total", left = 40, top = 300, bottom = 312),
                OcrLine("38.000", left = 280, top = 306, bottom = 318)
            )
        )

        assertEquals(listOf("Total 38.000"), rows)
    }

    @Test
    fun `keeps the row below out of the row above it`() {
        val rows = rowsOf(
            listOf(
                OcrLine("Total", left = 40, top = 300, bottom = 312),
                OcrLine("Tunai", left = 40, top = 316, bottom = 328)
            )
        )

        assertEquals(listOf("Total", "Tunai"), rows)
    }

    @Test
    fun `a tilted row does not swallow the row under it`() {
        // Every line overlaps the next one a little - which is exactly why a row is anchored on its
        // topmost line instead of on everything already gathered into it.
        val rows = rowsOf(
            listOf(
                OcrLine("Kanzler", left = 20, top = 400, bottom = 412),
                OcrLine("10.000", left = 260, top = 406, bottom = 418),
                OcrLine("Le Minerale", left = 20, top = 416, bottom = 428),
                OcrLine("4.000", left = 260, top = 422, bottom = 434)
            )
        )

        assertEquals(listOf("Kanzler 10.000", "Le Minerale 4.000"), rows)
    }

    @Test
    fun `leaves a receipt whose lines are already one per row alone`() {
        val rows = rowsOf(
            listOf(
                OcrLine("TOKO SUMBER JAYA", left = 20, top = 10, bottom = 26),
                OcrLine("Jl. Merdeka No. 5", left = 20, top = 30, bottom = 46),
                OcrLine("Total 25.000", left = 20, top = 50, bottom = 66)
            )
        )

        assertEquals(
            listOf("TOKO SUMBER JAYA", "Jl. Merdeka No. 5", "Total 25.000"),
            rows
        )
    }

    @Test
    fun `nothing read means nothing to arrange`() {
        assertTrue(rowsOf(emptyList()).isEmpty())
    }

    /**
     * What the recognizer returned for the fixture: text, and the box of the image it was read from.
     *
     * Kept in the order ML Kit handed it back, because that order is the thing under test.
     */
    private val AlfamartReceipt = listOf(
        OcrLine("Cimory", 127, 294, 304),
        OcrLine("Alfamart", 163, 116, 138),
        OcrLine("18.7.2024 08:49", 127, 260, 276),
        OcrLine("Frestea", 128, 350, 360),
        OcrLine("Alfamart Jogja", 174, 188, 204),
        OcrLine("J1. Kenangan", 180, 207, 218),
        OcrLine("1x 2.000", 140, 305, 317),
        OcrLine("Cimory hazelnut", 127, 316, 333),
        OcrLine("1x 9.000", 140, 334, 344),
        OcrLine("089974B2739", 183, 226, 246),
        OcrLine("tea haou", 152, 351, 364),
        OcrLine("Kanzler", 129, 406, 418),
        OcrLine("ICe cream aice", 128, 375, 387),
        OcrLine("1x 5.000", 141, 391, 403),
        OcrLine("Total", 130, 480, 493),
        OcrLine("Tunai", 131, 496, 508),
        OcrLine("1x 10.000", 142, 420, 432),
        OcrLine("Le Minerale", 130, 435, 448),
        OcrLine("1x 4.000", 143, 450, 462),
        OcrLine("Kasir : Sinta", 188, 582, 599),
        OcrLine("#281", 284, 258, 268),
        OcrLine("Terima kas ih", 193, 614, 630),
        OcrLine("2.000", 280, 300, 311),
        OcrLine("9.000", 281, 329, 340),
        OcrLine("8.000", 283, 358, 368),
        OcrLine("5.000", 284, 387, 398),
        OcrLine("10.000", 281, 416, 427),
        OcrLine("4.000", 289, 446, 457),
        OcrLine("Rp38.0U", 271, 478, 492),
        OcrLine("Rp38.000", 271, 491, 504),
        OcrLine("Barang yang sudah dibeli tidak", 131, 532, 556),
        OcrLine("jsa dikembal ikan lagi", 165, 551, 568),
        OcrLine("Lunas", 290, 507, 518)
    )
}
