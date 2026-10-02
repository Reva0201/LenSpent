package org.umn.lensspent.data.extraction

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The receipts the pipeline has actually been run over, kept as they were read off the phone.
 */
class RealReceiptFixturesTest {

    private val indomaretRows = listOf(
        "14.09.26-22:24/4.4.2/TGTE-39/BAFI /01",
        "NO. ORDER YUNWY COFFEE G0LD :A01",
        "LTA SUSU UHT CHO1L 1 22200 22,200",
        "S/G TELUR ONGA 3 BTR 10 2990 29.900",
        "SARI ROTI TAVAR JMBO 1 18000 18,000",
        "CHIKI BALL CHEESE 50 1 7500 7,500",
        "GRUDA ROSTA JG.8 90 1 11000 11,000",
        "UOUCHER: (1.500)",
        "GRUDA ROSTA VGYU 90 1 11000 11,000",
        "UOUCHER : (1,500)",
        "GARUDA ROSTA BUG 90 2 11000 22,000",
        "VOUCHER: (3,000)",
        "BOYAL SS DAEGU 556 1 8500 8.500",
        "KzLR BAKSO GCH JNG 55 1 8900 8.900",
        "CLOSE UP GRN F 160 G 1 25000 25.000",
        "HY/8 VIPES ANTBC 2'S 1 17700 17,700",
        "VOUCHER: (2,B00)",
        "YC FRIED CHKN SAYAP 1 12900 12,900",
        "IDH KACANG ALMOND 65 1 19900 19,900",
        "CANCEL (1)(19900) (19,900)",
        "DISKON: (2,500)",
        "TOTAL BELAN JA 183.200",
        "NON TUNAI: 183,200",
        "ANDA HEMAT: 11,400",
        "PPN :DPP= 136,014 PPN= 16,322",
        "PPN DIBEBASKAN : DPP= 27,408",
        "PPN= 3,289",
        "BCA DEBIT MSTRCRD PSPR PLT/PRI-TRXID:222405 HARGA JUAL: 178,278",
        "NO:************0701,PURCHASE:183,203",
        "SMSWA 0811.1600.280 TELP 1600280 LO LAYANAN KONSUMEN",
        "KONTAKOINDOMARET.CO.ID BELANJA LEBIH MUDAH DI",
        "IS ONGKIR 1JAM SAMPAI KLIKINDOMARET"
    )

    private val inventedTotalRows = listOf(
        "BNDARAN PU",
        "HILa d : 006000en000c0",
        "PENTUALAN 369 36916 2400 21/O5/26",
        "369029 19:36:57",
        "36557816 TSSUNFLOAERBTL946M 103,390",
        "61836173 GREENFIELDS 46,990",
        "37745932 CIMCRY STRAABERY 750 26,800",
        "37513567 PRENAGEN MOM STR 180 46.890",
        "36098350 NDOMIE KALDU AYAMT5 9,570",
        "3 @3, 190",
        "61833176 BIOKLL GRK PL 473G 89,390",
        "96010472 LEMCN IMPORT KG 7.510",
        "36362660 TABASCO RED PEPPERS 72,190-",
        "96005004 STRABERY SUPER SAEE 74,900",
        "39709012 UBI MADU MOZZAFELLA 17,000",
        "35764728 HPM FACIAL TISSUE TR 8,580",
        "2 @ 4,290",
        "42000628 KANTONG PLASTIK BER8 300",
        "15 ITEMS TOTAL : 503,510",
        "100000>o000 OtherDebit 503,510",
        "DPP: 337, 036 PPN: 37,074",
        "VOUCHER",
        "Voucher 8 25.030",
        "Harya berlau untuk produk 8 tertentu 1 Voucher untuk 1 transaksi",
        "herlaku aximal har setelah terbit"
    )

    private val paymentWordMerchantRows = listOf(
        "Telp 0702-21075 FRt Pasar i1ie Tohie Tirna",
        "PENJUALAN 09/04/2028 Enpat lsd 1:47:32",
        "etansgan : TUNAI",
        "I/SL-C35239",
        "Nara Prodk",
        "Harga Nett",
        "R OoMUEN KITO 1EsaR 16.000",
        "PCS INCOHIE SOTO SPESLAL",
        "3.000 12.000",
        "3.000 INDONIE INSTAN GORENG RENDANG 910R",
        "2 PCS CHOKI CHOKI CHOCO CASHEN 1068",
        "1.000 2.000",
        "YLPI CHOCO GLEE",
        "2.000",
        "6 PCS GERY SALLUT NAFER HAZELNUT",
        ",000",
        "ah 6 Iten 41.000",
        "Tunai 0 000",
        "Brand Total",
        "Penhavaran 41.000",
        "Sisa",
        "Terinakasth Atas Kunjungan Anda",
        "Harge Kant Bersaing"
    )

    private val indomaret = recognized(indomaretRows)
    private val inventedTotal = recognized(inventedTotalRows)
    private val paymentWordMerchant = recognized(paymentWordMerchantRows)

    private fun recognized(rows: List<String>) =
        RecognizedText(text = rows.joinToString("\n"), lines = rows)

    @Test
    fun `every real slip is read off its true total by the fast path`() {
        assertEquals(183_200L, ReceiptParser.parseLines(indomaret.lines).amountRupiah)
        assertEquals(503_510L, ReceiptParser.parseLines(inventedTotal.lines).amountRupiah)
        assertEquals(41_000L, ReceiptParser.parseLines(paymentWordMerchant.lines).amountRupiah)
    }

    @Test
    fun `the merchant the fast path names is what the receipt prints above the items`() {
        assertEquals(
            "LTA SUSU UHT CHO1L 1 22200 22,200",
            ReceiptParser.parseLines(indomaret.lines).merchant
        )
        assertEquals(
            "BNDARAN PU",
            ReceiptParser.parseLines(inventedTotal.lines).merchant
        )
        assertEquals(
            "etansgan : TUNAI",
            ReceiptParser.parseLines(paymentWordMerchant.lines).merchant
        )
    }
}
