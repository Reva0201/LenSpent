package org.umn.lensspent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Decodes [uri] downsampled to roughly [targetPx], so a small preview never allocates a full
 * resolution frame (a 12 MP JPEG is ~48 MB as ARGB_8888).
 *
 * Call this off the main thread: it reads the image twice, once for the bounds and once for the pixels.
 */
fun decodeSampledBitmap(context: Context, uri: Uri, targetPx: Int): ImageBitmap? {
    return try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sampleSize = 1
        while (bounds.outWidth / 2 / sampleSize >= targetPx && bounds.outHeight / 2 / sampleSize >= targetPx) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
