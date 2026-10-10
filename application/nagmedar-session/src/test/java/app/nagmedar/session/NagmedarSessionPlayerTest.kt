package app.nagmedar.session

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** S-502: the Media3 player mirrors the service and sends controller commands back to it. */
@RunWith(AndroidJUnit4::class)
class NagmedarSessionPlayerTest {

    private class FakeSource(size: Int = 3) : SessionSource {
        val calls = mutableListOf<String>()
        val items = (0 until size).map { i ->
            SessionItem("media:$i", "Parça $i", "Sanatçı", "Albüm", Uri.parse("content://art/$i"), 200_000L + i, isVideo = false)
        }
        override var hasMedia = size > 0
        override var isPlaying = true
        override var isPaused = false
        override var isSeekable = true
        override var positionMs = 42_000L
        override var durationMs = 200_001L
        var speedValue = 1f
        var repeatValue = Player.REPEAT_MODE_OFF
        override val speed get() = speedValue
        override val repeatMode get() = repeatValue
        override var shuffling = false
        override var canRepeat = true
        override var canShuffle = true
        override var hasNext = true
        override var hasPrevious = true
        override val queueSize get() = items.size
        override var currentIndex = 1
        override fun queueItem(index: Int) = items.getOrNull(index)

        override fun play() { calls += "play"; isPlaying = true; isPaused = false }
        override fun pause() { calls += "pause"; isPlaying = false; isPaused = true }
        override fun stop() { calls += "stop" }
        override fun seekTo(positionMs: Long) { calls += "seek $positionMs" }
        override fun skipToIndex(index: Int) { calls += "skip $index" }
        override fun next() { calls += "next" }
        override fun previous() { calls += "previous" }
        override fun setSpeed(speed: Float) { calls += "speed $speed" }
        override fun setRepeatMode(mode: Int) { calls += "repeat $mode" }
        override fun setShuffle(enabled: Boolean) { calls += "shuffle $enabled" }
        override fun playFromMediaId(mediaId: String, extras: Bundle?) { calls += "mediaId $mediaId" }
        override fun playFromSearch(query: String, extras: Bundle?) { calls += "search $query" }
        override fun customAction(action: String, extras: Bundle?) { calls += "custom $action" }
        override fun mediaButton(intent: Intent) = false
    }

    private val players = mutableListOf<NagmedarSessionPlayer>()

    private fun player(source: SessionSource) = NagmedarSessionPlayer(Looper.getMainLooper(), source).also { players += it }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @After
    fun release() = players.forEach { it.release() }

    @Test
    fun mirrorsQueueMetadataAndTransport() {
        val source = FakeSource()
        val player = player(source)
        assertEquals(3, player.mediaItemCount)
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals("media:1", player.currentMediaItem?.mediaId)
        assertEquals("Parça 1", player.mediaMetadata.title.toString())
        assertEquals("Albüm", player.mediaMetadata.albumTitle.toString())
        assertEquals(Uri.parse("content://art/1"), player.mediaMetadata.artworkUri)
        assertEquals(MediaMetadata.MEDIA_TYPE_MUSIC, player.mediaMetadata.mediaType)
        assertEquals(Player.STATE_READY, player.playbackState)
        assertTrue(player.isPlaying)
        assertEquals(42_000L, player.currentPosition)
        // the live length of the current item wins over the library's
        assertEquals(200_001L, player.duration)
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))

        player.pause()
        idle()
        assertEquals(listOf("pause"), source.calls)
        assertFalse(player.isPlaying)
        assertEquals(Player.STATE_READY, player.playbackState)
    }

    @Test
    fun emptyServiceIsIdle() {
        val player = player(FakeSource(size = 0))
        assertEquals(0, player.mediaItemCount)
        assertEquals(Player.STATE_IDLE, player.playbackState)
        assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
    }

    @Test
    fun nextPreviousAndSeeksGoToTheService() {
        val source = FakeSource()
        val player = player(source)
        player.seekToNext()
        player.seekTo(30_000L)
        player.seekTo(/* mediaItemIndex= */ 2, /* positionMs= */ 0L)
        idle()
        assertEquals(listOf("next", "seek 30000", "skip 2"), source.calls)

        // past the first seconds, "previous" restarts the item; at its start it goes back
        source.calls.clear()
        source.positionMs = 42_000L
        player.invalidate()
        player.seekToPrevious()
        source.positionMs = 1_000L
        player.invalidate()
        player.seekToPrevious()
        idle()
        assertEquals(listOf("seek 0", "previous"), source.calls)
    }

    @Test
    fun repeatShuffleAndSpeed() {
        val source = FakeSource()
        val player = player(source)
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.shuffleModeEnabled = true
        player.setPlaybackSpeed(1.5f)
        idle()
        assertEquals(listOf("repeat ${Player.REPEAT_MODE_ALL}", "shuffle true", "speed 1.5"), source.calls)
    }

    @Test
    fun browseAndSearchRequestsReachTheService() {
        val source = FakeSource()
        val player = player(source)
        player.setMediaItem(MediaItem.Builder().setMediaId("album/42").build())
        player.setMediaItem(
            MediaItem.Builder().setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery("Sezen Aksu").build()).build(),
        )
        idle()
        assertEquals(listOf("mediaId album/42", "search Sezen Aksu"), source.calls)
    }

    @Test
    fun longQueuesArePublishedAsAWindowAroundTheCurrentItem() {
        val source = FakeSource(size = 500)
        source.currentIndex = 250
        val player = player(source)
        assertEquals(NagmedarSessionPlayer.WINDOW, player.mediaItemCount)
        assertEquals(NagmedarSessionPlayer.WINDOW / 2, player.currentMediaItemIndex)
        assertEquals("media:250", player.currentMediaItem?.mediaId)
        // an item of the window maps back to its queue index
        player.seekTo(player.currentMediaItemIndex + 3, C.TIME_UNSET)
        idle()
        assertEquals(listOf("skip 253"), source.calls)

        assertEquals(0, NagmedarSessionPlayer.windowStartFor(current = 3, size = 500))
        assertEquals(500 - NagmedarSessionPlayer.WINDOW, NagmedarSessionPlayer.windowStartFor(current = 499, size = 500))
        assertEquals(0, NagmedarSessionPlayer.windowStartFor(current = 7, size = 10))
    }

    @Test
    fun sessionHostPublishesACompatTokenAndCustomActions() {
        val source = FakeSource()
        val host = Media3SessionHost(ApplicationProvider.getApplicationContext(), source, sessionActivity = null)
        try {
            assertNotNull(host.compatToken)
            host.setActions(listOf(SessionAction("bookmark", "Yer imi", android.R.drawable.ic_input_add)))
            idle()
            assertEquals(Media3SessionHost.SESSION_ID, host.session.id)
        } finally {
            host.release()
        }
    }
}
