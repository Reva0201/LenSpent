package org.umn.lensspent

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.umn.lensspent.ui.theme.LensSpentTheme

/**
 * The confirmation the sheet shows once the expense is written, rendered on a device as a user reads it.
 *
 * [ExpenseReviewSaveConfirmationTest] pins which drafts can be confirmed and out of what; what a plain
 * JVM test cannot show is that the thing on screen says it. So the dialog is composed here over the
 * expense it is confirming, and read back by its own text: the merchant and the amount as stored, the
 * category it was filed under, the day in the stepper's wording, and the one action that lets the sheet
 * go - which must not have happened while the dialog was only being read.
 *
 * The rendering is also written to the app's external files as a PNG, in the same spirit as the receipt
 * the pipeline is tested with, so it can be looked at rather than only asserted about:
 *
 * ```
 * adb shell am instrument -w -e class org.umn.lensspent.SaveConfirmationDialogTest \
 *     org.umn.lensspent.test/androidx.test.runner.AndroidJUnitRunner
 * adb pull /sdcard/Android/data/org.umn.lensspent/files/save-confirmation.png
 * ```
 */
@RunWith(AndroidJUnit4::class)
class SaveConfirmationDialogTest {

    @get:Rule
    val compose = createComposeRule()

    /** The day being confirmed: Friday 18 September 2026, in the label the stepper would show. */
    private val day = LocalDate.of(2026, 9, 18)

    @Test
    fun theSheetConfirmsWhatWasSavedBeforeItLeaves() {
        var acknowledged = false

        compose.setContent {
            LensSpentTheme(dynamicColor = false) {
                SaveConfirmationDialog(
                    saved = SavedExpense(
                        merchant = "Warung Sederhana",
                        amountRupiah = 57_500L,
                        categoryId = 7L,
                        categoryName = "Food",
                        date = day
                    ),
                    onDismiss = { acknowledged = true }
                )
            }
        }

        compose.onNodeWithText("Expense saved").assertIsDisplayed()
        compose.onNodeWithText("Warung Sederhana - ${formatIDR(57_500L)}").assertIsDisplayed()
        compose.onNodeWithText("Added to Food spending.").assertIsDisplayed()
        // The stepper's own label, so the confirmation and the field above it cannot disagree
        compose.onNodeWithText(dayLabel(day)).assertIsDisplayed()
        compose.onNodeWithText("It's in your recent transactions now.").assertIsDisplayed()

        // Reading is not acknowledging: nothing may navigate home while the dialog is merely on screen
        assertFalse("the sheet left while the confirmation was still being read", acknowledged)

        writeScreenForReview("save-confirmation.png")

        compose.onNodeWithText("Done").performClick()
        assertTrue("\"Done\" is what lets the sheet leave", acknowledged)
    }

    @Test
    fun aCategoryTheSheetCannotNameIsLeftOutRatherThanMadeUp() {
        compose.setContent {
            LensSpentTheme(dynamicColor = false) {
                SaveConfirmationDialog(
                    saved = SavedExpense(
                        merchant = "Toko Kelontong",
                        amountRupiah = 12_000L,
                        categoryId = 99L,
                        categoryName = null,
                        date = day
                    ),
                    onDismiss = {}
                )
            }
        }

        // The row was written either way, so the confirmation still says what it can - and says
        // nothing it cannot support
        compose.onNodeWithText("Toko Kelontong - ${formatIDR(12_000L)}").assertIsDisplayed()
        compose.onNodeWithText("Saved without a category name.").assertIsDisplayed()
        compose.onNodeWithText(dayLabel(day)).assertIsDisplayed()
    }

    /**
     * Writes the screen as it stands to the app's external files, next to the receipt fixture.
     *
     * Best effort on purpose: a device that will not hand back a screenshot must not fail the readings
     * above, so a failure is logged and nothing more.
     */
    private fun writeScreenForReview(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), name)
        runCatching {
            val screen: Bitmap = InstrumentationRegistry.getInstrumentation()
                .uiAutomation
                .takeScreenshot()
            FileOutputStream(file).use { out ->
                screen.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }.onSuccess {
            Log.i(Tag, "wrote $file")
        }.onFailure {
            Log.i(Tag, "no screenshot for $name: $it")
        }
    }

    private companion object {
        const val Tag = "SaveConfirmationDialogTest"
    }
}
