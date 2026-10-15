package app.nagmedar.ui.podcast

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Response
import java.io.IOException

/**
 * S-1101: runs this call and reads its answer with [read], off the main thread, and ties both to the
 * coroutine: when the caller is cancelled the call is cancelled too, so the connection and a body half
 * way through reading stop at once instead of running to their timeout. What [read] throws (an
 * IOException from a body that breaks half way included) is the caller's to map; the response is closed
 * either way.
 */
internal suspend fun <T> Call.executeCancellable(read: (Response) -> T): T {
    val call = this
    return try {
        withContext(Dispatchers.IO) {
            coroutineScope {
                // undispatched: the watcher is waiting before the call starts, so a cancel can never slip in ahead of it
                val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        call.cancel()
                    }
                }
                try {
                    call.execute().use(read)
                } finally {
                    watcher.cancel()
                }
            }
        }
    } catch (e: IOException) {
        // a cancelled call fails with an IOException, which beats the cancellation in a coroutine's outcome: cancelled it is
        currentCoroutineContext().ensureActive()
        throw e
    }
}
