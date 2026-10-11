/*
 * Nağmedar (S-406): files dropped on the video player's window.
 */
package org.videolan.vlc.gui.video

import android.annotation.TargetApi
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.DragEvent
import android.view.View
import android.widget.Toast
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.repository.SlaveRepository

/**
 * Files dropped on the player (DeX, a second window, a file manager): a subtitle is loaded into
 * the video playing at once and kept with it, a video or a track replaces what plays.
 */
@TargetApi(Build.VERSION_CODES.N)
class PlayerDropListener(private val activity: VideoPlayerActivity) : View.OnDragListener {

    override fun onDrag(view: View, event: DragEvent): Boolean = when (event.action) {
        DragEvent.ACTION_DRAG_STARTED -> {
            val description = event.clipDescription
            description != null && (0 until description.mimeTypeCount).any { accepted(description.getMimeType(it)) }
        }
        DragEvent.ACTION_DROP -> drop(event)
        else -> true
    }

    private fun accepted(mime: String) = mime.startsWith("video/") || mime.startsWith("audio/") || mime.startsWith("text/") ||
        mime.startsWith("application/")

    private fun drop(event: DragEvent): Boolean {
        val clip = event.clipData ?: return false
        // kept for the activity's life: the player reads the files while it plays them
        activity.requestDragAndDropPermissions(event)
        val uris = (0 until clip.itemCount).mapNotNull { index ->
            val item = clip.getItemAt(index)
            item.uri ?: item.text?.toString()?.trim()?.takeIf { it.contains("://") }?.let(Uri::parse)
        }
        if (uris.isEmpty()) return false
        val subtitles = uris.filter { isSubtitle(nameOf(it)) }
        val media = uris - subtitles.toSet()
        val service = activity.service
        val playing = service?.currentMediaWrapper
        when {
            media.isNotEmpty() -> {
                MediaUtils.openList(activity, media.map { MLServiceLocator.getAbstractMediaWrapper(it) }, 0)
            }
            subtitles.isNotEmpty() && service != null && playing != null -> {
                subtitles.forEach { uri ->
                    keep(uri)
                    service.addSubtitleTrack(uri, true)
                    SlaveRepository.getInstance(activity).saveSlave(playing.location, IMedia.Slave.Type.Subtitle, 2, uri.toString())
                }
                Toast.makeText(activity, R.string.nagmedar_player_subtitle_loaded, Toast.LENGTH_SHORT).show()
            }
            else -> return false
        }
        return true
    }

    /** A content uri's own name, else its last segment: what tells a subtitle. */
    private fun nameOf(uri: Uri): String {
        if (uri.scheme == "content") {
            runCatching {
                activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) return cursor.getString(0) ?: ""
                }
            }
        }
        return uri.lastPathSegment.orEmpty()
    }

    /** Access the sender allows to keep (a SAF document) is kept, so the subtitle loads next time too. */
    private fun keep(uri: Uri) {
        if (uri.scheme != "content") return
        runCatching { activity.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    companion object {
        private val subtitleExtensions = setOf("srt", "ass", "ssa", "vtt", "sub", "idx", "sup", "smi", "sami", "ttml", "dfxp", "usf")

        fun isSubtitle(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in subtitleExtensions

        /** Files dropped on the player's window go to it (Android 7 and later). */
        fun attach(activity: VideoPlayerActivity) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
            activity.window.decorView.setOnDragListener(PlayerDropListener(activity))
        }
    }
}
