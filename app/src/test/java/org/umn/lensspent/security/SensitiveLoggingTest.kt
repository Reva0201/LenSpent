package org.umn.lensspent.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A static check over the production sources for the two ways this app previously leaked data into
 * logcat: logging OCR text / merchant names / amounts, and `printStackTrace()`.
 *
 * A runtime test cannot catch a `Log.d` that only runs on a path nobody exercised, so this reads the
 * sources instead. It is deliberately blunt: a new `android.util.Log` import or a `println` in
 * `app/src/main` fails, and the reviewer is forced to use `AppLog` (which is a no-op in release).
 */
class SensitiveLoggingTest {

    private val mainSources = File("src/main/java")

    private fun kotlinSources(): List<File> =
        mainSources.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `production code does not use android Log directly`() {
        val offenders = kotlinSources().filter { file ->
            // AppLog is the wrapper: it is the one file allowed to import the platform logger.
            if (file.name == "AppLog.kt") return@filter false
            val text = file.readText()
            Regex("""\bLog\.[diwev]\s*\(""").containsMatchIn(text) ||
                text.contains("import android.util.Log")
        }.map { it.path }

        assertTrue(
            "use AppLog instead, which is silent in release: $offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun `production code does not print stack traces or to stdout`() {
        val offenders = kotlinSources().filter { file ->
            val text = file.readText()
            text.contains("printStackTrace()") ||
                Regex("""\bprintln\s*\(""").containsMatchIn(text) ||
                text.contains("System.out.") ||
                text.contains("System.err.")
        }.map { it.path }

        assertTrue("these leak data and bypass the release guard: $offenders", offenders.isEmpty())
    }

    /**
     * The one exception is `AppLog`, whose whole job is to wrap the platform logger - and whose
     * methods are gated on a `BuildConfig.DEBUG` constant.
     */
    @Test
    fun `AppLog is release-gated`() {
        val appLog = File(mainSources, "org/umn/lensspent/AppLog.kt").readText()

        assertTrue(appLog.contains("BuildConfig.DEBUG"))
        assertTrue(appLog.contains("if (isEnabled)"))
    }

    /**
     * The receipt-recognition path must not carry receipt content into a log statement. This looks
     * for the specific shapes that used to be there, so a future edit that reintroduces them fails
     * even if it goes through a different logger. A line *count* is allowed; the lines are not.
     */
    @Test
    fun `extraction does not log OCR text, merchants or amounts`() {
        val extractor = File(
            mainSources,
            "org/umn/lensspent/data/extraction/MlKitCaptureExtractor.kt"
        ).readText()
        val structurer = File(
            mainSources,
            "org/umn/lensspent/data/extraction/ReceiptStructurer.kt"
        ).readText()

        listOf(extractor, structurer).forEach { source ->
            listOf(
                "recognized.text",
                "lines.joinToString",
                "draft.merchant",
                "draft.amountRupiah",
                "capture.sourceUri"
            ).forEach { forbidden ->
                val logged = Regex(
                    """(App)?Log\.[a-z]+\([^)]*${Regex.escape(forbidden)}""",
                    RegexOption.DOT_MATCHES_ALL
                ).containsMatchIn(source)
                assertFalse(
                    "extraction must not log '$forbidden'\n$source",
                    logged
                )
            }
        }
    }

    /**
     * CSV content is the whole ledger. No code may log it, and the export must not be written to a
     * shared location as a side effect of being built.
     */
    @Test
    fun `CSV content is never logged or persisted`() {
        val settings = File(
            mainSources,
            "org/umn/lensspent/ui/settings/SettingsViewModel.kt"
        ).readText()

        assertFalse(
            "the CSV is a complete copy of the ledger and must not reach a log",
            Regex("""AppLog\.[a-z]+\([^)]*(csv|Csv)""").containsMatchIn(settings)
        )
        assertFalse("the export must not write a file itself", settings.contains("FileOutputStream"))
    }
}
