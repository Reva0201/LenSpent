package org.umn.lensspent.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Amount parsing sits between a human (or an OCR engine) and money in the database, so every one of
 * these cases is a real thing that gets typed or read off a receipt.
 */
class RupiahParserTest {

    @Test
    fun `parses the formats a person types`() {
        assertEquals(1_250_000L, parseRupiahAmount("Rp 1.250.000"))
        assertEquals(1_250_000L, parseRupiahAmount("1.250.000"))
        assertEquals(1_250_000L, parseRupiahAmount("1250000"))
        assertEquals(1_250_000L, parseRupiahAmount("  1 250 000  "))
        assertEquals(1_250_000L, parseRupiahAmount("Rp1.250.000,-"))
        assertEquals(1_250_000L, parseRupiahAmount("rp 1.250.000"))
    }

    @Test
    fun `returns null when there is no number to read`() {
        assertNull(parseRupiahAmount(""))
        assertNull(parseRupiahAmount("   "))
        assertNull(parseRupiahAmount("Rp"))
        assertNull(parseRupiahAmount("total bayar"))
    }

    /**
     * The reason this parser refuses instead of scraping digits: "125rb" means 125.000, and reading it
     * as 125 would be a thousand-fold error in someone's ledger.
     */
    @Test
    fun `refuses text it does not fully understand`() {
        assertNull(parseRupiahAmount("125rb"))
        assertNull(parseRupiahAmount("1,5jt"))
        assertNull(parseRupiahAmount("total bayar 1.250.000"))
        assertNull(parseRupiahAmount("1.250.000 rupiah"))
    }

    @Test
    fun `zero parses to zero so validation can reject it separately`() {
        assertEquals(0L, parseRupiahAmount("0"))
        assertEquals(0L, parseRupiahAmount("0.000"))
    }
}
