package org.umn.lensspent

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where a receipt photo lives while it is being read, and how long it lives there.
 *
 * Receipts are personal-data rich: a photo shows a merchant, a time, often a card fragment and the
 * full basket. They therefore stay in **app-private internal storage** (`filesDir/receipts`), never
 * in the shared gallery, so no other app - and no media scanner or cloud photo backup - can read
 * them. Nothing here needs a storage permission, and no content provider is exported to hand them
 * out.
 *
 * Retention, deliberately short:
 *  - the image is kept only while the capture is being reviewed;
 *  - it is deleted when the user confirms the expense (the ledger row is the record that matters),
 *    when they discard the capture, and when a transaction created from it is deleted;
 *  - [deleteOrphans] sweeps files with no capture row, which covers a crash or cancellation between
 *    writing the file and recording the capture.
 */
object ImageStorage {

    /** Subdirectory under `filesDir`; the backup rules exclude it explicitly. */
    private const val ReceiptDirectory = "receipts"

    /** The directory itself, created on demand. Never shared. */
    fun receiptDirectory(context: Context): File =
        File(context.filesDir, ReceiptDirectory).apply { if (!exists()) mkdirs() }

    /**
     * Writes [source]'s bytes into private storage and returns the URI of the copy.
     *
     * Returns null when the read or write fails, having removed any partial file first: a capture row
     * pointing at a half-written image is worse than no capture, because the review screen would show
     * a broken frame with no way to tell why. Callers treat null as "nothing to record".
     */
    suspend fun importToPrivateStorage(
        context: Context,
        source: Uri,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Uri? = withContext(dispatcher) {
        val target = newReceiptFile(context)
        try {
            val copied = context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false

            if (copied && target.length() > 0L) {
                Uri.fromFile(target)
            } else {
                target.delete()
                null
            }
        } catch (e: IOException) {
            target.delete()
            AppLog.warn("Could not copy a picked image into private storage")
            null
        } catch (e: SecurityException) {
            // The picker grant can be withdrawn before the copy runs; that is not a crash.
            target.delete()
            AppLog.warn("The picked image was no longer readable")
            null
        }
    }

    /** A fresh, unique receipt file. Unique names keep two fast captures from overwriting each other. */
    fun newReceiptFile(context: Context): File =
        File(receiptDirectory(context), "receipt-${UUID.randomUUID()}.jpg")

    /**
     * Deletes the file behind [uri], if it is one of ours.
     *
     * Refuses anything outside [receiptDirectory]: a capture row could hold an old `content://` URI
     * from a previous version, and deleting an arbitrary provider row because a string was stored
     * would be a destructive surprise. Unknown URIs are simply left alone.
     */
    suspend fun deleteReceipt(
        context: Context,
        uri: String?,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Boolean = withContext(dispatcher) {
        val file = privateFileFor(context, uri) ?: return@withContext false
        val deleted = file.delete()
        if (!deleted) AppLog.warn("A receipt image could not be deleted")
        deleted
    }

    /**
     * Deletes every receipt file that [isReferenced] says no capture row points at.
     *
     * The sweep is what closes the crash/cancel window: the file is written first, then the capture
     * row, so a failure between those two steps leaves an untracked image. It is only ever started
     * from an explicit cleanup (app start or the Settings action), never while a review is open.
     */
    suspend fun deleteOrphans(
        context: Context,
        isReferenced: (String) -> Boolean,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Int = withContext(dispatcher) {
        val directory = receiptDirectory(context)
        directory.listFiles()
            ?.filter { file -> file.isFile && !isReferenced(Uri.fromFile(file).toString()) }
            ?.count { file -> file.delete() }
            ?: 0
    }

    /** Deletes every receipt image. Used by the "remove stored receipt images" setting. */
    suspend fun deleteAll(
        context: Context,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Int = withContext(dispatcher) {
        val directory = receiptDirectory(context)
        directory.listFiles()?.count { it.isFile && it.delete() } ?: 0
    }

    /**
     * Total bytes held by receipt images, for the Settings row that offers to remove them.
     *
     * Counts files only; never opens one. A number is enough to let the user decide.
     */
    suspend fun storedBytes(
        context: Context,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Long = withContext(dispatcher) {
        receiptDirectory(context).listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    }

    /**
     * The file behind [uri], but only when it resolves inside [receiptDirectory].
     *
     * Both the `file:` spelling and an older `content://` spelling of one of our receipts map back to
     * a path inside the directory; anything else - a gallery URI, a provider row, a traversal attempt
     * - resolves outside it and is refused. Deletion is therefore limited to files this app created.
     */
    internal fun privateFileFor(context: Context, uri: String?): File? {
        if (uri.isNullOrBlank()) return null
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return null
        val directory = receiptDirectory(context).canonicalFile

        val candidate = when (parsed.scheme) {
            "file" -> parsed.path?.let(::File)
            // Legacy rows from the shared-gallery build pointed at MediaStore. They are deliberately
            // not deleted here: those rows belong to the user's gallery, not to this app.
            else -> null
        } ?: return null

        val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        return canonical.takeIf { it.parentFile == directory }
    }
}
