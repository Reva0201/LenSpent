package org.umn.lensspent

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared native media content resolver manager utilizing the device's MediaStore.
 */
object ImageStorage {

    /**
     * Returns the most recent image in the shared gallery natively via the MediaStore content provider,
     * or null when there is none.
     *
     * Only a single row is read, and the query always hops to [Dispatchers.IO] so callers can never walk
     * the image table on the main thread.
     */
    suspend fun latestNativePhoto(context: Context): Uri? = withContext(Dispatchers.IO) {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                if (idColumn != -1 && cursor.moveToFirst()) {
                    ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idColumn)
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Collection a capture is inserted into: the primary shared volume on API 29+, the legacy URI below.
     */
    fun captureCollection(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
    }

    /**
     * Prepares standard ContentValues mapping for inserting a new camera capture natively into the shared gallery.
     *
     * On API 29+ the row is inserted as pending, so no gallery or indexer can observe a half-written
     * frame; [publishImage] clears the flag once the capture is complete.
     */
    fun createNativeImageContentValues(): ContentValues {
        return ContentValues().apply {
            val timestamp = System.currentTimeMillis()
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_$timestamp.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "LensSpent")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
    }

    /**
     * Copies an image the user picked into the app's own folder in the shared gallery, and returns the
     * URI of the copy.
     *
     * The grant the photo picker hands back lasts only as long as this process does, and a capture row
     * outlives it: without the copy, reopening an old pick after a restart would point at an image this
     * app is no longer allowed to read, and the OCR stage would have nothing to open.
     *
     * Returns null when nothing could be copied, in which case there is no image worth recording.
     */
    suspend fun importToGallery(context: Context, source: Uri): Uri? {
        val copy = withContext(Dispatchers.IO) {
            try {
                val target = context.contentResolver.insert(
                    captureCollection(),
                    createNativeImageContentValues()
                ) ?: return@withContext null

                val copied = context.contentResolver.openInputStream(source)?.use { input ->
                    context.contentResolver.openOutputStream(target)?.use { output ->
                        input.copyTo(output)
                        true
                    } ?: false
                } ?: false

                if (copied) {
                    target
                } else {
                    // A row with no bytes behind it is worse than no row: it would show a blank frame in
                    // the gallery forever, so the insert is rolled back.
                    context.contentResolver.delete(target, null, null)
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        if (copy != null) publishImage(context, copy)
        return copy
    }

    /**
     * Clears the pending flag of a finished capture so the rest of the device can see it.
     */
    suspend fun publishImage(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        try {
            context.contentResolver.update(uri, values, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
