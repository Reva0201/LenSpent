package org.umn.lensspent.security

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup policy, asserted against the files a build actually packages.
 *
 * These are static checks on purpose: nothing here needs a device, and the failure mode they guard
 * against - someone "fixing backups" by turning them back on - is a configuration regression that no
 * runtime test would notice.
 */
class BackupConfigurationTest {

    private val main = File("src/main")

    private val manifest: String = File(main, "AndroidManifest.xml").readText()

    @Test
    fun `cloud backup and device transfer are off`() {
        assertTrue(
            "allowBackup must be explicitly false: $manifest",
            Regex("""android:allowBackup="false"""").containsMatchIn(manifest)
        )
        assertFalse(
            "allowBackup must not be left true",
            Regex("""android:allowBackup="true"""").containsMatchIn(manifest)
        )
    }

    @Test
    fun `the manifest points at both rule files, so no API level is left unruled`() {
        assertTrue(manifest.contains("""android:fullBackupContent="@xml/backup_rules""""))
        assertTrue(manifest.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""))
    }

    @Test
    fun `the referenced rule files exist`() {
        assertTrue(File(main, "res/xml/backup_rules.xml").isFile)
        assertTrue(File(main, "res/xml/data_extraction_rules.xml").isFile)
    }

    @Test
    fun `the legacy rules exclude the ledger, preferences and receipts`() {
        val rules = File(main, "res/xml/backup_rules.xml").readText()

        listOf(
            """<exclude domain="root" path="." />""",
            """<exclude domain="database" path="lensspent.db" />""",
            """<exclude domain="file" path="datastore/user_preferences.preferences_pb" />""",
            """<exclude domain="file" path="receipts/" />"""
        ).forEach { rule ->
            assertTrue("missing backup exclusion: $rule", rules.contains(rule))
        }
        assertFalse("no sample TODO may ship", rules.contains("TODO"))
        assertFalse("cache is not a valid backup domain", rules.contains("""domain="cache""""))
    }

    @Test
    fun `the android 12 rules exclude both cloud backup and device transfer`() {
        val rules = File(main, "res/xml/data_extraction_rules.xml").readText()

        assertTrue(rules.contains("<cloud-backup>"))
        assertTrue(
            "a transfer is still a copy leaving the device",
            rules.contains("<device-transfer>")
        )
        listOf(
            """<exclude domain="root" path="." />""",
            """<exclude domain="database" path="lensspent.db" />""",
            """<exclude domain="file" path="datastore/user_preferences.preferences_pb" />""",
            """<exclude domain="file" path="receipts/" />"""
        ).forEach { rule ->
            assertEquals(
                "each exclusion must appear in both sections: $rule",
                2,
                rules.windowed(rule.length).count { it == rule }
            )
        }
        assertFalse("no sample TODO may ship", rules.contains("TODO"))
        assertFalse("cache is not a valid backup domain", rules.contains("""domain="cache""""))
    }

    /**
     * The rules name the same database the app opens and the same directory the images are written
     * to. If either is renamed, this fails instead of silently backing the data up.
     */
    @Test
    fun `the excluded paths match the real storage names`() {
        val database = File(
            main,
            "java/org/umn/lensspent/data/local/LensSpentDatabase.kt"
        ).readText()
        assertTrue(
            "LensSpentDatabase.Name must stay in sync with the backup rules",
            database.contains("""const val Name = "lensspent.db"""")
        )

        val imageStorage = File(main, "java/org/umn/lensspent/ImageStorage.kt").readText()
        assertTrue(imageStorage.contains("""private const val ReceiptDirectory = "receipts""""))
    }
}
