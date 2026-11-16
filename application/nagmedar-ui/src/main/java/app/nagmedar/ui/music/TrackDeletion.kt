package app.nagmedar.ui.music

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.audio.cue.CueUri
import app.nagmedar.ui.R
import app.nagmedar.ui.components.DialogCard
import app.nagmedar.ui.components.DialogFrame
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.ui.onboarding.MediaAccess
import app.nagmedar.ui.tags.openAllFilesAccess
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.AppScope
import org.videolan.tools.Notices
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.MediaUtils

/**
 * 1.3.0 (the owner, 16 Nov: "nağmedar içerisinde müzikleri silme özelliği olsun. Dosyadan da silsin"): tracks deleted
 * from the device. A track's or the selection's "Sil…" asks first, saying how many files go; their files are deleted
 * (and the system's media index told), they leave the queue, and the library looks over their folders again. A CUE
 * sheet's track is not deleted: its whole album is one file.
 */
object TrackDeleteRequest {
    /** The tracks asked to be deleted, while the question is up. */
    var pending: DeleteAsk? by mutableStateOf(null)
        private set

    fun ask(tracks: List<Track>, media: List<MediaWrapper>) {
        if (tracks.isNotEmpty()) pending = DeleteAsk(tracks, media)
    }

    internal fun answered() {
        pending = null
    }
}

/** Tracks to delete and the library's items for them (a CUE sheet's track is no file of its own: left out). */
@Immutable
class DeleteAsk(val tracks: List<Track>, val media: List<MediaWrapper>) {
    val deletable: List<Track> get() = tracks.filterNot { CueUri.isTrack(it.uri) }
    val cueTracks: Int get() = tracks.size - deletable.size
}

/** 1.3.0: the deleting itself, in the app's scope: a file after another, said at the end. */
object TrackDeletion {
    fun delete(context: Context, media: List<MediaWrapper>) {
        val app = context.applicationContext
        val files = media.filterNot { CueUri.isTrack(it.uri) }
        if (files.isEmpty()) return
        AppScope.launch {
            var deleted = 0
            files.forEach { item ->
                // out of the queue first: what plays is not a file that is gone
                runCatching { PlaybackService.instance?.removeLocation(item.location) }
                if (runCatching { MediaUtils.deleteMedia(item) }.getOrDefault(false)) deleted++
            }
            val failed = files.size - deleted
            val res = app.resources
            if (deleted > 0) Notices.show(app, res.getQuantityString(R.plurals.nag_deleted, deleted, deleted))
            when {
                failed == 0 -> Unit
                // Android 11 and later: another app's file is deleted only with access to all files
                MediaAccess.canAskAllFiles && !MediaAccess.hasAllFiles(app) -> Notices.show(
                    app, res.getQuantityString(R.plurals.nag_delete_needs_access, failed, failed), long = true, error = true,
                    actionLabel = res.getString(R.string.nag_tag_grant), action = { openAllFilesAccess(app) },
                )
                else -> Notices.show(app, res.getQuantityString(R.plurals.nag_delete_failed, failed, failed), long = true, error = true)
            }
        }
    }
}

/** The question before deleting, while one is asked; [delete] does it ([TrackDeletion]'s in the app). */
@Composable
fun TrackDeleteHost(delete: (List<MediaWrapper>) -> Unit = defaultDelete()) {
    val ask = TrackDeleteRequest.pending ?: return
    DeleteTracksDialog(
        ask,
        onDelete = {
            TrackDeleteRequest.answered()
            delete(ask.media.filterNot { CueUri.isTrack(it.uri) })
        },
        onDismiss = TrackDeleteRequest::answered,
    )
}

@Composable
private fun defaultDelete(): (List<MediaWrapper>) -> Unit {
    val context = LocalContext.current
    return { media -> TrackDeletion.delete(context, media) }
}

/** How many files go, the first of them by name, that it cannot be undone; a CUE sheet's tracks said to stay. */
@Composable
internal fun DeleteTracksDialog(ask: DeleteAsk, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val colors = NagTheme.colors
    val count = ask.deletable.size
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) {
            DialogCard(pluralStringResource(R.plurals.nag_delete_title, count, count), Modifier.testTag("delete-tracks")) {
                Text(stringResource(R.string.nag_delete_body), style = NagTheme.type.body, color = colors.textSecondary)
                val shown = ask.deletable.take(SHOWN)
                shown.forEach { track ->
                    Text(
                        listOfNotNull(track.title, track.artist?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = NagTheme.type.labelSmall,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val more = count - shown.size
                if (more > 0) Text(pluralStringResource(R.plurals.nag_delete_more, more, more), style = NagTheme.type.labelSmall, color = colors.textTertiary)
                if (ask.cueTracks > 0) {
                    Text(pluralStringResource(R.plurals.nag_delete_cue, ask.cueTracks, ask.cueTracks), style = NagTheme.type.labelSmall, color = colors.warning)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    NagButton(stringResource(R.string.nag_delete_cancel), onDismiss, modifier = Modifier.testTag("delete-cancel"))
                    NagButton(stringResource(R.string.nag_delete_confirm), onDelete, destructive = true, enabled = count > 0, modifier = Modifier.testTag("delete-confirm"))
                }
            }
        }
    }
}

/** The tracks named in the question; the rest counted. */
private const val SHOWN = 5
