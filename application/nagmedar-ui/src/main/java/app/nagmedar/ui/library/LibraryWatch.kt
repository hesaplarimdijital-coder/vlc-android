package app.nagmedar.ui.library

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import app.nagmedar.ui.onboarding.MediaAccess
import org.videolan.vlc.recheckLibrary

/**
 * 1.0.3: while a window of the app is in front, a change in the system's media index of audio (a
 * new album copied in, a file deleted or retagged) asks the library to look again: the service
 * compares the index with its last full look and goes over the folders quietly when they may have
 * changed. What it finds reaches the Music's list as the medialibrary reports it, within seconds.
 * A burst of changes (an album's files arriving one by one) asks once, [SETTLE_MS] after the last.
 */
class LibraryWatch(context: Context) {

    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    // without media access the medialibrary scans nothing: the picked folders are walked on their own (SafLibrary)
    private val ask = Runnable { if (MediaAccess.hasMediaAccess(app)) app.recheckLibrary() }

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            handler.removeCallbacks(ask)
            handler.postDelayed(ask, SETTLE_MS)
        }
    }

    private var watching = false

    fun start() {
        if (watching) return
        watching = runCatching {
            app.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        }.isSuccess
    }

    fun stop() {
        handler.removeCallbacks(ask)
        if (!watching) return
        watching = false
        runCatching { app.contentResolver.unregisterContentObserver(observer) }
    }

    private companion object {
        const val SETTLE_MS = 2_000L
    }
}
