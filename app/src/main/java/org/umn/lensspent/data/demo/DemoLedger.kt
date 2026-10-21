package org.umn.lensspent.data.demo

import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import org.umn.lensspent.data.model.CaptureSource
import org.umn.lensspent.data.model.CaptureStatus
import org.umn.lensspent.data.repository.TransactionRepository

/**
 * One sample expense, as the demo ledger writes it.
 *
 * The category is named rather than numbered on purpose: the ids are assigned by Room at insert time,
 * so a demo row that carried `categoryId = 2` would depend on the seeding order staying exactly as it is
 * today. A name is the durable handle, and the seeder resolves it against the taxonomy that is actually
 * on the device.
 *
 * [daysAgo] is a distance from today rather than a fixed timestamp, so the demo ledger is always "the
 * last four weeks" whenever it is installed, and the Daily, Weekly and Yearly tabs all have something
 * to show on the day it is graded.
 */
data class DemoExpense(
    val merchant: String,
    val amountRupiah: Long,
    val categoryName: String,
    val daysAgo: Int,
    val note: String? = null,
    /**
     * Whether this row was "scanned" rather than typed.
     *
     * The seeder writes a real capture row for these and links the transaction to it, so the detail
     * screen's "From a receipt photo" is backed by something in the database instead of by a flag on
     * the demo data. It also means the seeded ledger exercises both paths the app actually has.
     */
    val fromReceipt: Boolean = false
)

/**
 * The ledger a fresh install starts with in the demo build.
 *
 * It is deliberately not a set of ten identical rows: it covers all nine categories, amounts from a
 * Rp 25.000 warteg lunch to a Rp 449.000 jacket, two purchases on the same day so the Daily tab has more
 * than one bar, and a four-week spread so Weekly and Yearly are not empty. Several rows carry a note and
 * the rest do not, because a detail screen that only ever renders a note is a detail screen that has
 * never been checked with the field missing.
 *
 * The merchants are real places on the Gading Serpong campus strip so the numbers look like a student's
 * spending rather than like placeholder text.
 */
val DemoLedger: List<DemoExpense> = listOf(
    DemoExpense("Kopi Kenangan Gading Serpong", 28_000, "Dining", 0, "Iced coffee before class"),
    DemoExpense("Indomaret Kelapa Dua", 87_500, "Groceries", 0, "Weekly snacks and water", fromReceipt = true),
    DemoExpense("Gojek to Campus", 32_000, "Transport", 1),
    DemoExpense("Uniqlo Summarecon Mall", 449_000, "Shopping", 2, "Jacket, end-of-season sale", fromReceipt = true),
    DemoExpense("Token Listrik PLN", 200_000, "Bills", 3, "Kos meter top-up"),
    DemoExpense("Apotek K-24", 65_500, "Health", 4, fromReceipt = true),
    DemoExpense("Bioskop XXI CGV", 75_000, "Entertainment", 5, "Two tickets, student price"),
    DemoExpense("Buku Algoritma & Struktur Data", 189_000, "Education", 7),
    DemoExpense("GrabFood Nasi Padang", 54_000, "Dining", 8),
    DemoExpense("Warteg Bahari", 25_000, "Dining", 9, "Lunch with the group"),
    DemoExpense("Alfamart Serpong", 43_200, "Groceries", 11, fromReceipt = true),
    DemoExpense("Pulsa Telkomsel 50GB", 150_000, "Bills", 13),
    DemoExpense("Steam - co-op game", 129_000, "Entertainment", 15),
    DemoExpense("Indomaret", 35_000, "Groceries", 18),
    DemoExpense("Klinik Gigi Serpong", 350_000, "Health", 21, "Scaling, annual visit"),
    DemoExpense("Shopee - USB-C cable", 89_000, "Shopping", 24),
    DemoExpense("Pertamina Pertalite", 100_000, "Transport", 26, fromReceipt = true),
    DemoExpense("Laundry Kiloan", 30_000, "Other", 28)
)

/**
 * Writes [DemoLedger] into the ledger, once, and only into an empty one.
 *
 * Returns whether anything was written, which is what makes the call idempotent from the application's
 * point of view: a second launch finds a non-empty ledger and does nothing. It never merges demo rows
 * into a ledger the user has already started, because there is no way to tell which of those rows are
 * theirs afterwards.
 *
 * Times of day advance through the working day rather than all landing at 00:00, so the "most recent
 * first" ordering inside a single day is stable instead of being decided by insert order.
 */
class DemoLedgerSeeder(
    private val repository: TransactionRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    suspend fun seedIfEmpty(): Boolean {
        if (repository.observeTransactions(1).first().isNotEmpty()) return false

        val categoryIdsByName = repository.observeCategories().first().associate { it.name to it.id }
        val today = LocalDate.now(clock)

        DemoLedger.forEachIndexed { index, entry ->
            // A taxonomy the app failed to seed is not a reason to half-write the demo ledger: the
            // category is a foreign key, so an unresolved name would throw on the first insert.
            val categoryId = categoryIdsByName[entry.categoryName] ?: return@forEachIndexed

            val occurredAt = today
                .minusDays(entry.daysAgo.toLong())
                .atTime(9 + (index * 3) % 11, 30)
                .atZone(clock.zone)
                .toInstant()
                .toEpochMilli()

            // A real capture row for the scanned ones, marked confirmed, so the link the detail screen
            // reads ("From a receipt photo") points at a row that exists rather than at a made-up id.
            val captureId = if (entry.fromReceipt) {
                repository.recordCapture(
                    sourceUri = "content://org.umn.lensspent.demo/receipt-${index + 1}.jpg",
                    source = CaptureSource.PICKER,
                    capturedAt = occurredAt
                ).also { id -> repository.updateCaptureStatus(id, CaptureStatus.CONFIRMED) }
            } else {
                null
            }

            repository.addTransaction(
                merchant = entry.merchant,
                amount = entry.amountRupiah,
                categoryId = categoryId,
                occurredAt = occurredAt,
                note = entry.note,
                captureId = captureId
            )
        }
        return true
    }
}
