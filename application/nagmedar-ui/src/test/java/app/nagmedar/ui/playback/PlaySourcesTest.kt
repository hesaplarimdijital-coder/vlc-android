package app.nagmedar.ui.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** S-1002: what the phone player's overline says the queue plays from. */
class PlaySourcesTest {

    private val album = PlaySource(PlayFrom.Album, "Itrî'nin İzinde")
    private val started = StartedList(album, setOf("file:///a.flac", "file:///b.flac", "file:///c.flac"))

    @Test
    fun aTrackOfTheListPlaysFromItsSource() {
        assertEquals(album, PlaySources.playingFrom(started, "file:///b.flac", listOf("file:///a.flac", "file:///b.flac", "file:///c.flac")))
    }

    @Test
    fun aTrackAddedAfterTheListPlaysFromTheQueue() {
        val queue = listOf("file:///a.flac", "file:///b.flac", "file:///c.flac", "file:///z.mp3")
        assertEquals(PlaySource(PlayFrom.Queue, ""), PlaySources.playingFrom(started, "file:///z.mp3", queue))
    }

    @Test
    fun aQueueStartedElsewhereSaysNothing() {
        assertNull(PlaySources.playingFrom(started, "file:///z.mp3", listOf("file:///y.mp3", "file:///z.mp3")))
        assertNull(PlaySources.playingFrom(null, "file:///a.flac", listOf("file:///a.flac")))
        assertNull(PlaySources.playingFrom(started, null, listOf("file:///a.flac")))
    }
}
