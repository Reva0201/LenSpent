package org.umn.lensspent.data.repository

/**
 * Deletes the bytes behind a capture's image URI.
 *
 * A one-method seam rather than passing `Context` into a ViewModel: the retention rule ("a confirmed
 * or discarded capture must not leave its photo behind") belongs to the screen's state machine, while
 * the platform call belongs to `ImageStorage`. Tests implement this with a recording fake and assert
 * that cleanup happened without touching the filesystem.
 */
fun interface CaptureImageDeleter {

    /** Deletes the image at [uri] if it is one this app created. Unknown or null URIs are ignored. */
    suspend fun delete(uri: String?)

    companion object {
        /** A deleter that does nothing, for callers with no files to manage. */
        val None: CaptureImageDeleter = CaptureImageDeleter { }
    }
}
