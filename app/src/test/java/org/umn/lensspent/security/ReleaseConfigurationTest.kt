package org.umn.lensspent.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Release hardening, asserted against the module's build script.
 *
 * This is a static test because the properties live in Gradle, not in the APK's behaviour: shrinking
 * and R8 are build-time facts. The assertions are deliberately narrow - they check what can silently
 * regress (someone turning optimization off, or deleting the keep rules) and nothing they cannot
 * prove.
 */
class ReleaseConfigurationTest {

    private val buildScript: String = File("build.gradle.kts").readText()

    @Test
    fun `release builds enable code and resource shrinking`() {
        val releaseBlock = releaseBlock()

        assertTrue("optimization must stay on for release", releaseBlock.contains("enable = true"))
        assertTrue(releaseBlock.contains("isMinifyEnabled = true"))
        assertTrue(releaseBlock.contains("isShrinkResources = true"))
    }

    @Test
    fun `debug builds are never minified`() {
        val debugBlock = debugBlock()

        assertTrue(debugBlock.contains("isMinifyEnabled = false"))
        assertTrue(debugBlock.contains("isShrinkResources = false"))
    }

    /**
     * The API key must not reappear under any spelling. This looks at the whole script, including
     * comments, so a "temporary" field cannot be reintroduced quietly.
     */
    @Test
    fun `the build script carries no provider credential`() {
        assertFalse(
            "no DEEPSEEK_API_KEY field may exist",
            buildScript.contains("DEEPSEEK_API_KEY")
        )
        assertFalse(
            "no api key may be read from local.properties",
            buildScript.contains("deepseek.api.key")
        )
        assertFalse(
            "no environment key may be baked in",
            buildScript.contains("environmentVariable(\"DEEPSEEK_API_KEY\")")
        )
        // The only build-config string is the non-secret backend address.
        assertTrue(buildScript.contains("AI_PROXY_BASE_URL"))
        assertTrue(
            "the proxy URL must be validated as https",
            buildScript.contains("startsWith(\"https://\"")
        )
    }

    @Test
    fun `no signing material is configured`() {
        assertFalse(
            "signing must not be configured from checked-in files",
            buildScript.contains("storeFile") || buildScript.contains("storePassword")
        )
        assertFalse(buildScript.contains("keyPassword"))
    }

    @Test
    fun `the keep-rule file exists and is not the empty template`() {
        val rules = File("src/main/keepRules/rules.keep")
        assertTrue("keep rules live in src/main/keepRules", rules.isFile)
        val text = rules.readText()
        assertFalse("the R8 rules must not be the sample placeholder", text.contains("fqcn.of.javascript"))
        assertTrue(text.contains("Room"))
        assertTrue(text.contains("Retrofit"))
    }

    /** A minimal keep file must not shrink the serializers the AI request depends on. */
    @Test
    fun `serialization and reflection-sensitive entry points are kept`() {
        val text = File("src/main/keepRules/rules.keep").readText()

        listOf(
            "kotlinx.serialization",
            "retrofit2",
            "androidx.room",
            "com.google.mlkit",
            "Compose"
        ).forEach { subject ->
            assertTrue("the keep rules must mention $subject:\n$text", text.contains(subject))
        }
    }

    private fun releaseBlock(): String = blockAfter("release {")

    private fun debugBlock(): String = blockAfter("debug {")

    /** Takes the text between the first `marker` and the matching brace, tolerating nested blocks. */
    private fun blockAfter(marker: String): String {
        val start = buildScript.indexOf(marker)
        assertTrue("the build script must contain '$marker'", start >= 0)
        var depth = 0
        val builder = StringBuilder()
        for (index in start until buildScript.length) {
            val char = buildScript[index]
            when (char) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        builder.append(char)
                        return builder.toString()
                    }
                }
            }
            builder.append(char)
        }
        return builder.toString()
    }
}
