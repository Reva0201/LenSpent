package org.umn.lensspent

import android.util.Log
import org.umn.lensspent.BuildConfig

/**
 * The app's only logging entry point.
 *
 * Everything here is dropped in release builds. The reason is not noise but data: this app handles
 * receipts, merchants, amounts and a person's name, and a log line is a copy of that data in a place
 * the app no longer controls. Logcat is readable by other tooling on a debuggable device and can be
 * captured by support tooling, so a release build must not emit it at all.
 *
 * Rules for call sites:
 *  - pass a short, fixed message describing *what failed*, never the data;
 *  - never pass OCR text, merchant names, amounts, notes or URIs;
 *  - pass the throwable to [warn]/[error] only where the exception type is itself the diagnosis,
 *    and never a message built by string-concatenating user data.
 *
 * `d`/`i`/`w`/`e` are no-ops in release: R8 also strips their bodies because [isEnabled] is a
 * compile-time constant, but the guard is what makes that guarantee independent of the shrinker.
 */
object AppLog {

    private const val Tag = "LensSpent"

    /** True in debug builds only. */
    val isEnabled: Boolean = BuildConfig.DEBUG

    fun debug(message: String) {
        if (isEnabled) Log.d(Tag, message)
    }

    fun info(message: String) {
        if (isEnabled) Log.i(Tag, message)
    }

    /** A recoverable failure. [throwable] is logged, but callers must not build [message] from data. */
    fun warn(message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        if (throwable == null) Log.w(Tag, message) else Log.w(Tag, message, throwable)
    }

    /** An unexpected failure. Same rule as [warn]: the message is a fixed description. */
    fun error(message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        if (throwable == null) Log.e(Tag, message) else Log.e(Tag, message, throwable)
    }
}
