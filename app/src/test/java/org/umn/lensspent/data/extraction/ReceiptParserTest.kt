package org.umn.lensspent.data.extraction

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The fast path sits between OCR and someone's ledger, so each of these cases is a shape a real
 * Indonesian receipt actually prints. A miss here is not a crash: it is the signal that Stage 2
 * (the on-device LLM) has to take over, which the last test pins down.
 */
class ReceiptParserTest {

    @Test
    fun `reads the merchant from the top line and the total from the total line`() {
        val draft = ReceiptParser.parseLines(
            lines = listOf(
                "TOKO SUMBER JAYA",
                "Jl. Merdeka No. 5, Bandung",
                "12/09/2026 14:32",
                "Kopi Susu          25.000",
                "Total              25.000"
            )
        )

        assertEquals("TOKO SUMBER JAYA", draft.merchant)
        assertEquals(25_000L, draft.amountRupiah)
    }

    @Test
    fun `skips leading dates, times and bare numbers before naming the merchant`() {
        val draft = ReceiptParser.parseLines(
            lines = listOf(
                "12/09/2026",
                "14:32:05",
                "1250000",
                "WARUNG PADANG SEDERHANA",
                "JUMLAH BAYAR  1.250.000"
            )
        )

        assertEquals("WARUNG PADANG SEDERHANA", draft.merchant)
        assertEquals(1_250_000L, draft.amountRupiah)
    }

    @Test
    fun `keeps looking past a receipt number for the store name`() {
        // A till prints its own number above the store's name, and that number has no letters in it,
        // so the search has to continue rather than give the whole receipt up.
        val draft = ReceiptParser.parseLines(
            lines = listOf(
                "#281",
                "18.7.2024 08:49",
                "Alfamart Jogja",
                "Total Rp38.000"
            )
        )

        assertEquals("Alfamart Jogja", draft.merchant)
        assertEquals(38_000L, draft.amountRupiah)
    }

    @Test
    fun `reads the amount from the line directly below the total label`() {
        val draft = ReceiptParser.parseLines(
            lines = listOf(
                "APOTEK SEHAT",
                "TOTAL",
                "Rp 87.500"
            )
        )

        assertEquals("APOTEK SEHAT", draft.merchant)
        assertEquals(87_500L, draft.amountRupiah)
    }

    @Test
    fun `never mistakes a sub total for the amount actually paid`() {
        val draft = ReceiptParser.parseLines(
            lines = listOf(
                "RESTORAN NUSANTARA",
                "Sub Total      150.000",
                "Diskon         -10.000",
                "GRAND TOTAL    140.000"
            )
        )

        assertEquals("RESTORAN NUSANTARA", draft.merchant)
        assertEquals(140_000L, draft.amountRupiah)
    }

    @Test
    fun `returns a draft with partial data when the fast path cannot find everything`() {
        val draft = ReceiptParser.parseLines(
            lines = listOf("TOKO ABC", "Kopi Susu 25.000", "Terima kasih")
        )

        assertEquals("TOKO ABC", draft.merchant)
        assertEquals(0L, draft.amountRupiah)
    }
}
