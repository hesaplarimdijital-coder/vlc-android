package app.nagmedar.ui.input

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-406: what a dropped file is, by its name or its type; what a drop on the window does; which
 * target is under a drag.
 */
class DropsTest {

    @Test
    fun filesAreToldByNameOrType() {
        assertEquals(DropKind.Subtitle, dropKind("Kuzey Işığı.tr.srt", "application/octet-stream"))
        assertEquals(DropKind.Subtitle, dropKind("content://x/document/12", "text/vtt"))
        assertEquals(DropKind.Video, dropKind("Film.MKV", null))
        assertEquals(DropKind.Video, dropKind("content://x/document/13", "video/x-matroska"))
        assertEquals(DropKind.Audio, dropKind("Sabaha Kadar.flac", "audio/flac"))
        // a tracker module (S-911) is a track, whatever its sender says of its type
        assertEquals(DropKind.Audio, dropKind("Modül.xm", "application/octet-stream"))
        // a playlist comes as audio/x-mpegurl: it is a list, not a track
        assertEquals(DropKind.Playlist, dropKind("liste.m3u8", "audio/x-mpegurl"))
        assertEquals(DropKind.Other, dropKind("notlar.pdf", "application/pdf"))
    }

    private val film = DroppedFile("content://a/1", "Film.mkv", "video/x-matroska")
    private val track = DroppedFile("content://a/2", "Parça.flac", "audio/flac")
    private val subtitle = DroppedFile("content://a/3", "Film.tr.srt", "application/x-subrip")
    private val pdf = DroppedFile("content://a/4", "notlar.pdf", "application/pdf")

    @Test
    fun aDropOnTheWindowPlaysOrLoadsSubtitles() {
        assertEquals(DropPlan.Play(listOf(film.uri, track.uri)), planDrop(listOf(film, subtitle, track), videoPlaying = false))
        assertEquals(DropPlan.LoadSubtitles(listOf(subtitle.uri)), planDrop(listOf(subtitle, pdf), videoPlaying = true))
        assertEquals(DropPlan.SubtitlesNeedAVideo, planDrop(listOf(subtitle), videoPlaying = false))
        assertEquals(DropPlan.Nothing, planDrop(listOf(pdf), videoPlaying = true))
        assertTrue(film.playable)
        assertFalse(subtitle.playable)
    }

    @Test
    fun theTargetUnderTheDragTakesWhatItAccepts() {
        val targets = DropTargets()
        val bar = DropTarget().apply {
            accepts = ::mayBePlayable
            bounds = Rect(Offset(0f, 700f), Size(1280f, 100f))
        }
        val card = DropTarget().apply {
            accepts = ::mayBeSubtitle
            bounds = Rect(Offset(100f, 100f), Size(200f, 150f))
        }
        targets.put("player-bar", bar)
        targets.put("film-1", card)
        assertEquals("film-1", targets.at(150f, 150f, listOf("application/x-subrip"))?.first)
        // a video is not the card's
        assertNull(targets.at(150f, 150f, listOf("video/mp4")))
        assertEquals("player-bar", targets.at(10f, 750f, listOf("video/mp4"))?.first)

        targets.moved(10f, 750f, listOf("video/mp4"), outside = true)
        assertTrue(targets.incoming)
        assertEquals("player-bar", targets.hovered)
        targets.ended()
        assertFalse(targets.incoming)
        assertNull(targets.hovered)

        // a target that left takes nothing any more
        targets.remove("film-1", card)
        assertNull(targets.at(150f, 150f, listOf("application/x-subrip")))
    }
}
