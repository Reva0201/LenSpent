package org.umn.lensspent.data.extraction

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

    /**
     * The zone is injected, not read from the device: a receipt printed in Jakarta has to land on the
     * same ledger day no matter where the phone happens to be when it is scanned. "America/New_York"
     * is deliberately not the test host's zone, so a stray ZoneId.systemDefault() fails here.
     */
    @Test
    fun `resolves the receipt's own date in the injected zone`() {
        val zone = ZoneId.of("America/New_York")
        val clock = Clock.fixed(Instant.parse("2026-09-20T02:00:00Z"), zone)
        val draft = ReceiptParser.parseLines(
            lines = listOf(
                "TOKO SUMBER JAYA",
                "12/09/2026 14:32",
                "Total          25.000"
            ),
            clock = clock
        )

        val expected = LocalDate.of(2026, 9, 12).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, draft.occurredAt)
    }

    /** With no usable date on the paper, "now" is the injected clock's now - not the wall clock. */
    @Test
    fun `falls back to the injected clock when the receipt carries no date`() {
        val clock = Clock.fixed(
            Instant.parse("2026-09-20T02:00:00Z"),
            ZoneId.of("America/New_York")
        )
        val draft = ReceiptParser.parseLines(
            lines = listOf("TOKO ABC", "Kopi Susu 25.000"),
            clock = clock
        )

        assertEquals(clock.millis(), draft.occurredAt)
    }

    /**
     * The one-year sanity window is anchored to the injected clock too, so a receipt dated in the
     * clock's future is treated as OCR noise rather than as a purchase.
     */
    @Test
    fun `ignores a receipt date that is newer than the injected clock's today`() {
        val clock = Clock.fixed(
            Instant.parse("2026-09-20T02:00:00Z"),
            ZoneId.of("America/New_York")
        )
        val draft = ReceiptParser.parseLines(
            lines = listOf("TOKO SUMBER JAYA", "30/09/2026", "Total  25.000"),
            clock = clock
        )

        assertEquals(clock.millis(), draft.occurredAt)
    }
}
