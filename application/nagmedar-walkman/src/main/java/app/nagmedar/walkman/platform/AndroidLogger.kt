/*
 * Nağmedar 1.5.0: from Walkman Sync (app/src/main/kotlin/com/walkmansync/platform/AndroidLogger.kt), the owner's own app.
 */
package app.nagmedar.walkman.platform

import android.util.Log
import app.nagmedar.walkman.runtime.Logger

/** [Logger] portunun android.util.Log uygulaması. Etiket 23 karakter sınırına göre kırpılır. */
object AndroidLogger : Logger {
    private fun tag(raw: String): String = "WS/$raw".take(23)

    override fun d(tag: String, message: String) {
        Log.d(tag(tag), message)
    }

    override fun w(tag: String, message: String, error: Throwable?) {
        if (error != null) Log.w(tag(tag), message, error) else Log.w(tag(tag), message)
    }

    override fun e(tag: String, message: String, error: Throwable?) {
        if (error != null) Log.e(tag(tag), message, error) else Log.e(tag(tag), message)
    }
}
