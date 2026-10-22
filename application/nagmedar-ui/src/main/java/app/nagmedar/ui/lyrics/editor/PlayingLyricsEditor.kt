package app.nagmedar.ui.lyrics.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.nagmedar.ui.lyrics.DatabaseLyricsCache
import app.nagmedar.ui.lyrics.LyricsSession
import app.nagmedar.ui.lyrics.LyricsTrack
import app.nagmedar.ui.playback.PlaybackActions
import app.nagmedar.ui.playback.rememberNowPlaying
import app.nagmedar.ui.playback.rememberPlaybackTime
import app.nagmedar.ui.scrobble.Scrobbling
import kotlinx.coroutines.Dispatchers
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.PlaybackService

/** S-1005: whether the lyrics editor is asked for (the lyrics views' "Düzenle" and "Zamanla"); the window shows it over everything. */
object LyricsEditorRequest {
    // process-wide state: made in the global snapshot by ProcessState.warmUp, before any window composes
    var open: Boolean by mutableStateOf(false)
}

/**
 * S-1005: the lyrics editor for the track playing when it was opened. The track is kept when the
 * next one starts: the work is not lost, and stamps wait until it plays again.
 */
@Composable
fun PlayingLyricsEditor(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val nowPlaying = rememberNowPlaying()
    val session = remember(context) { LyricsSession.get(context) }
    val time = rememberPlaybackTime(nowPlaying)
    val scope = rememberCoroutineScope()
    val playingTrack = nowPlaying.media?.takeIf { it.type == MediaWrapper.TYPE_AUDIO }?.let(LyricsTrack::of)
    // the track being edited: the one playing now, kept
    val edited = remember { playingTrack }
    if (edited == null) {
        // nothing plays that has lyrics
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val onTrack = playingTrack?.uri == edited.uri
    val playingNow by rememberUpdatedState(nowPlaying.playing)
    val onTrackNow by rememberUpdatedState(onTrack)
    val controller = remember {
        val player = object : EditorPlayer {
            override val positionMs: Long get() = PlaybackService.instance?.getTime() ?: 0L
            override val playing: Boolean get() = playingNow
            override val onTrack: Boolean get() = onTrackNow
            override fun seekTo(ms: Long) = time.seekTo(ms)
            override fun togglePlay() = PlaybackActions.togglePlayPause()
        }
        LyricsEditorController(
            track = edited,
            current = session.lyrics?.takeIf { session.load.track == edited },
            player = player,
            writer = LyricsWriter(DatabaseLyricsCache(context), DeviceSidecar(context), EngineTags(context)),
            publisher = OkHttpLyricsPublisher(Scrobbling.userAgent()),
            scope = scope,
            io = Dispatchers.IO,
            onSaved = session::again,
        ).also { it.loadOriginals() }
    }
    LyricsEditorScreen(controller, timeMs = { time.ms }, playing = nowPlaying.playing, onClose = onClose, modifier = modifier, onTrack = onTrack)
}
