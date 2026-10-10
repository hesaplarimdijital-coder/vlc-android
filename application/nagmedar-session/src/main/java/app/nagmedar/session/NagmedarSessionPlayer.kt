package app.nagmedar.session

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * The playback service seen as a Media3 [Player]. State is read from [source] each time it is
 * invalidated; commands from controllers go back to [source]. The queue is published as a window
 * of at most [WINDOW] items around the current one, like the legacy session's queue, so a
 * 10 000-track queue does not cost a 10 000-item timeline on every update.
 *
 * Next and previous always go to the service, whatever index Media3 computed: shuffle and
 * podcast chapter jumps are decided there.
 */
@OptIn(UnstableApi::class)
class NagmedarSessionPlayer(looper: Looper, private val source: SessionSource) : SimpleBasePlayer(looper) {

    /** Queue index of the first item of the published window. */
    private var windowStart = 0

    /** Called by the service whenever its state or metadata changed. */
    fun invalidate() = invalidateState()

    override fun getState(): State {
        val builder = State.Builder()
            .setAvailableCommands(commands())
            .setRepeatMode(source.repeatMode)
            .setShuffleModeEnabled(source.shuffling)
            .setPlaybackParameters(PlaybackParameters(source.speed.takeIf { it > 0f } ?: 1f))
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
        val size = source.queueSize
        if (!source.hasMedia || size <= 0) {
            windowStart = 0
            return builder
                .setPlaylist(emptyList())
                .setPlaybackState(Player.STATE_IDLE)
                .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build()
        }
        val current = source.currentIndex.coerceIn(0, size - 1)
        val start = windowStartFor(current, size)
        val end = minOf(size, start + WINDOW)
        windowStart = start
        val playlist = (start until end).map { index -> itemData(index, current) }
        val active = source.isPlaying || source.isPaused
        return builder
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(current - start)
            .setPlaybackState(if (active) Player.STATE_READY else Player.STATE_IDLE)
            .setPlayWhenReady(source.isPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setContentPositionMs(PositionSupplier { source.positionMs.coerceAtLeast(0L) })
            .build()
    }

    private fun itemData(index: Int, current: Int): MediaItemData {
        val item = source.queueItem(index)
        val mediaId = item?.mediaId ?: "queue:$index"
        val metadata = MediaMetadata.Builder()
            .setTitle(item?.title)
            .setArtist(item?.artist)
            .setAlbumTitle(item?.album)
            .setArtworkUri(item?.artworkUri)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(if (item?.isVideo == true) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
        val durationMs = if (index == current && source.durationMs > 0) source.durationMs else item?.durationMs ?: 0L
        return MediaItemData.Builder(/* uid= */ "$index/$mediaId")
            .setMediaItem(MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(metadata).build())
            .setDurationUs(if (durationMs > 0) Util.msToUs(durationMs) else C.TIME_UNSET)
            .setIsSeekable(if (index == current) source.isSeekable else true)
            .build()
    }

    private fun commands(): Player.Commands {
        val commands = Player.Commands.Builder().addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            Player.COMMAND_RELEASE,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SET_SPEED_AND_PITCH,
            Player.COMMAND_SET_MEDIA_ITEM,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,
        )
        if (source.hasMedia && source.isSeekable) {
            commands.addAll(
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
            )
        }
        val repeating = source.repeatMode != Player.REPEAT_MODE_OFF
        if (source.hasMedia && (source.hasNext || repeating)) {
            commands.addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }
        if (source.hasMedia && (source.hasPrevious || repeating || source.isSeekable)) {
            commands.addAll(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        }
        if (source.canRepeat) commands.add(Player.COMMAND_SET_REPEAT_MODE)
        if (source.canShuffle) commands.add(Player.COMMAND_SET_SHUFFLE_MODE)
        return commands.build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) source.play() else source.pause()
        return done()
    }

    /** Controllers prepare before playing from STATE_IDLE; the service prepares when it plays. */
    override fun handlePrepare(): ListenableFuture<*> = done()

    override fun handleStop(): ListenableFuture<*> {
        source.stop()
        return done()
    }

    override fun handleRelease(): ListenableFuture<*> = done()

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        source.setRepeatMode(repeatMode)
        return done()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        source.setShuffle(shuffleModeEnabled)
        return done()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        source.setSpeed(playbackParameters.speed)
        return done()
    }

    /** Android Auto and Assistant requests arrive as one item carrying a media id or a search. */
    override fun handleSetMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        val item = mediaItems.getOrNull(startIndex.coerceAtLeast(0)) ?: mediaItems.firstOrNull() ?: return done()
        val query = item.requestMetadata.searchQuery
        when {
            query != null -> source.playFromSearch(query, item.requestMetadata.extras)
            item.mediaId.isNotEmpty() && item.mediaId != MediaItem.DEFAULT_MEDIA_ID -> source.playFromMediaId(item.mediaId, item.requestMetadata.extras)
        }
        return done()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val currentInWindow = source.currentIndex - windowStart
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> source.next()
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                // Media3 asks for position 0 of the current item when it is past the first seconds
                if (mediaItemIndex == currentInWindow && positionMs == 0L && seekCommand == Player.COMMAND_SEEK_TO_PREVIOUS) source.seekTo(0L)
                else source.previous()
            else -> when {
                mediaItemIndex == C.INDEX_UNSET -> Unit
                mediaItemIndex != currentInWindow -> source.skipToIndex(windowStart + mediaItemIndex)
                positionMs != C.TIME_UNSET -> source.seekTo(positionMs.coerceAtLeast(0L))
                else -> source.seekTo(0L)
            }
        }
        return done()
    }

    private fun done(): ListenableFuture<*> = Futures.immediateVoidFuture()

    companion object {
        /** Items published around the current one (the legacy session publishes 15 + 15). */
        const val WINDOW = 31
        const val SEEK_INCREMENT_MS = 10_000L

        /** First queue index of a window of [WINDOW] items that keeps [current] near its middle. */
        fun windowStartFor(current: Int, size: Int): Int =
            if (size <= WINDOW) 0 else (current - WINDOW / 2).coerceIn(0, size - WINDOW)
    }
}
