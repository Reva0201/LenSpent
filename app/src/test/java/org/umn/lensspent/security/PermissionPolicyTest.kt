package org.umn.lensspent.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Permission policy, asserted against the manifest and the two screens that request anything.
 *
 * These are static checks because the failure they guard against is a *permission being added*, which
 * no runtime test on a granting device would notice. The rules encode the decisions in
 * docs/SECURITY.md:
 *
 *  - only CAMERA and INTERNET are declared,
 *  - CAMERA is requested from the camera screen, not from the dashboard,
 *  - gallery selection uses the system photo picker, never a media-library permission.
 */
class PermissionPolicyTest {

    private val main = File("src/main")
    private val manifest = File(main, "AndroidManifest.xml").readText()

    /**
     * The manifest with comments removed.
     *
     * The file documents *why* storage permissions were removed, so a naive scan would match the
     * explanation rather than a declaration. Stripping comments keeps the assertions about what the
     * manifest actually declares.
     */
    private val manifestDeclarations: String =
        manifest.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
    private val home = File(main, "java/org/umn/lensspent/HomeScreen.kt").readText()
    private val camera = File(main, "java/org/umn/lensspent/CameraScreen.kt").readText()

    @Test
    fun `only camera and internet are declared`() {
        val declared = Regex("""<uses-permission android:name="([^"]+)"""")
            .findAll(manifestDeclarations)
            .map { it.groupValues[1] }
            .toList()

        assertTrue("camera is needed to scan: $declared", declared.contains("android.permission.CAMERA"))
        assertTrue("the optional AI backend needs network: $declared", declared.contains("android.permission.INTERNET"))
        assertTrue("unexpected permissions were added: $declared", declared.size == 2)
    }

    @Test
    fun `no storage or media permission is declared`() {
        listOf(
            "READ_MEDIA_IMAGES",
            "READ_MEDIA_VISUAL_USER_SELECTED",
            "READ_EXTERNAL_STORAGE",
            "WRITE_EXTERNAL_STORAGE",
            "MANAGE_EXTERNAL_STORAGE",
            "ACCESS_MEDIA_LOCATION",
            "POST_NOTIFICATIONS",
            "QUERY_ALL_PACKAGES"
        ).forEach { permission ->
            assertFalse(
                "$permission must not be declared; receipt images are app-private",
                manifestDeclarations.contains(permission)
            )
        }
    }

    /** Denial and partial denial are handled in the screen, so this pins that the screen is the owner. */
    @Test
    fun `the camera permission is requested from the camera screen`() {
        assertTrue(camera.contains("rememberPermissionState(Manifest.permission.CAMERA)"))
        assertTrue(
            "the request happens on entry, not at app start",
            camera.contains("launchPermissionRequest()")
        )
        assertTrue(
            "a denied camera must still offer the picker and manual entry",
            camera.contains("Choose a photo instead")
        )
    }

    @Test
    fun `the dashboard no longer requests permissions`() {
        assertFalse(
            "opening the scanner must be what triggers the prompt",
            home.contains("launchMultiplePermissionRequest")
        )
        assertFalse(home.contains("rememberMultiplePermissionsState"))
        assertFalse(home.contains("READ_MEDIA_IMAGES"))
    }

    @Test
    fun `gallery selection uses the system photo picker`() {
        assertTrue(
            "PickVisualMedia needs no storage permission",
            camera.contains("ActivityResultContracts.PickVisualMedia")
        )
        assertFalse(
            "the app must not query the media library for a thumbnail",
            camera.contains("latestNativePhoto"))
    }

    /** Receipts must not be written to shared storage at all. */
    @Test
    fun `captures are written to app-private storage`() {
        val storage = File(main, "java/org/umn/lensspent/ImageStorage.kt").readText()

        assertTrue(storage.contains("context.filesDir"))
        assertFalse(
            "Environment.getExternalStorage* is shared storage",
            storage.contains("getExternalStorage"))
        assertFalse(
            "MediaStore rows are written to the shared gallery",
            storage.contains("contentResolver.insert"))
    }
}
