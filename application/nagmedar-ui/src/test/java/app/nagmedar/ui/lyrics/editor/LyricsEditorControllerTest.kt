package app.nagmedar.ui.lyrics.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.LyricsSource
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.lyrics.LyricLine
import app.nagmedar.ui.lyrics.Lyrics
import app.nagmedar.ui.lyrics.LyricsOrigin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** S-1005: the editor's shortcuts against the player, saving, importing and publishing. */
@RunWith(AndroidJUnit4::class)
class LyricsEditorControllerTest {

    private val player = FakePlayer()
    private val cache = MemoryLyricsCache()
    private val sidecar = FakeSidecar()
    private val tags = FakeTags()
    private val publisher = Publisher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun stop() {
        scope.cancel()
    }
    private var saved = 0

    private class Publisher : LyricsPublisher {
        var request: PublishRequest? = null
        override suspend fun challenge() = PublishChallenge.of("abcDEF123", "00" + "FF".repeat(31))!!
        override suspend fun publish(request: PublishRequest, challenge: PublishChallenge, nonce: String) {
            this.request = request
        }
    }

    private fun controller(current: Lyrics? = null) = LyricsEditorController(
        SAMPLE_TRACK, current, player, LyricsWriter(cache, sidecar, tags), publisher, scope, Dispatchers.Unconfined, workers = 1, onSaved = { saved++ },
    )

    private fun LyricsEditorController.timed(): LyricsEditorController {
        import(SAMPLE_TEXT)
        for (ms in listOf(1_000L, 2_000L, 3_000L, 4_000L)) {
            player.positionMs = ms
            stamp()
        }
        return this
    }

    @Test
    fun withoutLyricsTheEditorStartsWithTheImport() {
        val c = controller()
        assertTrue(c.importing)
        assertTrue(c.stamps.lines.isEmpty())
        c.import("  \n ")
        assertEquals(EditorMessage.NothingToImport, c.message)
        c.import(SAMPLE_TEXT)
        assertFalse(c.importing)
        assertEquals(4, c.stamps.lines.size)
        // pasted text is a change: closing without stamping or saving asks first
        assertTrue(c.dirty)
    }

    @Test
    fun lyricsTheTrackHasAreTheStart() {
        val c = controller(Lyrics(listOf(LyricLine(1_000, "bir"), LyricLine(2_000, "iki")), LyricsOrigin.File, offsetMs = 500))
        assertFalse(c.importing)
        // the user's delay is part of the times
        assertEquals(listOf(1_500L, 2_500L), c.stamps.lines.map { it.startMs })
        assertTrue(c.stamps.complete)
    }

    @Test
    fun theKeysStampAtThePlayersPosition() {
        val c = controller()
        c.import(SAMPLE_TEXT)
        player.positionMs = 1_234
        assertTrue(c.perform(ShortcutId.LrcStamp))
        player.positionMs = 2_500
        c.perform(ShortcutId.LrcStamp)
        assertEquals(listOf(1_230L, 2_500L, -1L, -1L), c.stamps.lines.map { it.startMs })
        assertTrue(c.dirty)
        c.perform(ShortcutId.LrcNudgeLater)
        assertEquals(2_600L, c.stamps.lines[1].startMs)
        c.perform(ShortcutId.LrcNudgeEarlier)
        c.perform(ShortcutId.LrcUnstamp)
        assertEquals(-1L, c.stamps.lines[1].startMs)
        c.perform(ShortcutId.LrcLineDown)
        assertEquals(2, c.stamps.cursor.line)
        c.perform(ShortcutId.LrcLineUp)
        c.perform(ShortcutId.LrcLineUp)
        assertEquals(0, c.stamps.cursor.line)
        c.perform(ShortcutId.LrcWordMode)
        assertEquals(StampMode.Word, c.stamps.mode)
        c.perform(ShortcutId.LrcWordMode)
        assertEquals(StampMode.Line, c.stamps.mode)
    }

    @Test
    fun spaceIsStillPlayAndPause() {
        val c = controller()
        assertTrue(c.perform(ShortcutId.PlayPause))
        assertEquals(1, player.toggles)
        assertFalse(c.perform(ShortcutId.Mute))
    }

    @Test
    fun noStampIsMadeWhileAnotherTrackPlays() {
        val c = controller()
        c.import(SAMPLE_TEXT)
        player.onTrack = false
        c.stamp()
        assertFalse(c.stamps.stampedLines > 0)
    }

    @Test
    fun aClickGoesToTheLineAndItsTime() {
        val c = controller().timed()
        c.select(2)
        assertEquals(listOf(3_000L), player.seeks)
        assertEquals(2, c.stamps.cursor.line)
        // a line not stamped has no time to go to
        c.perform(ShortcutId.LrcUnstamp)
        c.select(3)
        assertEquals(listOf(3_000L), player.seeks)
        assertEquals(3, c.stamps.cursor.line)
    }

    @Test
    fun savingNeedsEveryLineStamped() {
        val c = controller()
        c.import(SAMPLE_TEXT)
        player.positionMs = 1_000
        c.stamp()
        c.save()
        assertEquals(EditorMessage.Incomplete(3), c.message)
        assertNull(sidecar.written)
    }

    @Test
    fun savingBesideTheFileWritesItAndCleansTheEditor() {
        val c = controller().timed()
        assertTrue(c.dirty)
        c.save()
        assertEquals(EditorMessage.Saved(SaveTarget.File, "Parça.lrc"), c.message)
        assertTrue(sidecar.written!!.startsWith("[ti:Parça]"))
        assertFalse(c.dirty)
        assertEquals(1, saved)
        assertNotNull(cache.text(SAMPLE_TRACK, LyricsSource.USER))
    }

    @Test
    fun savingIntoTheTagsAndTheFolderAskedFor() {
        val c = controller().timed()
        c.target = SaveTarget.Tag
        c.save()
        assertEquals(EditorMessage.Saved(SaveTarget.Tag, null), c.message)
        assertEquals(4, tags.written!!.size)
        c.target = SaveTarget.File
        sidecar.result = { SidecarResult.NeedsFolder }
        c.save()
        assertEquals(EditorMessage.NeedsFolder, c.message)
        sidecar.result = { SidecarResult.Failed }
        c.save()
        assertEquals(EditorMessage.SaveFailed, c.message)
    }

    @Test
    fun theOriginalsAreOfferedAfterSaving() {
        tags.lyrics = Lyrics.of("tag sözü", LyricsOrigin.Embedded)
        val c = controller().timed()
        c.target = SaveTarget.Tag
        c.save()
        assertEquals(listOf(OriginalLyrics(LyricsSource.ORIGINAL_TAG, "tag sözü")), c.originals)
        c.restore(c.originals.single())
        assertEquals(listOf("tag sözü"), c.stamps.lines.map { it.text })
    }

    @Test
    fun publishingAsksFirstThenSolvesAndSends() = runBlocking {
        val c = controller().timed()
        c.askPublish()
        val asked = c.publish as PublishState.Confirming
        assertEquals("Parça", asked.request.trackName)
        // nothing is sent before the yes
        assertNull(publisher.request)
        c.confirmPublish()
        withTimeout(20_000) { while (c.publish != PublishState.Done) delay(10) }
        assertEquals(asked.request, publisher.request)
        c.dismissPublish()
        assertEquals(PublishState.Idle, c.publish)
    }

    @Test
    fun publishingCanBeCancelledWhileItSolves() = runBlocking {
        val impossible = object : LyricsPublisher {
            override suspend fun challenge() = PublishChallenge("x", ByteArray(32))
            override suspend fun publish(request: PublishRequest, challenge: PublishChallenge, nonce: String) = error("not solved")
        }
        val c = LyricsEditorController(SAMPLE_TRACK, null, player, null, impossible, scope, Dispatchers.Unconfined, workers = 1)
        c.import(SAMPLE_TEXT)
        for (ms in listOf(1_000L, 2_000L, 3_000L, 4_000L)) {
            player.positionMs = ms
            c.stamp()
        }
        c.askPublish()
        c.confirmPublish()
        withTimeout(20_000) { while ((c.publish as? PublishState.Solving)?.tries?.let { it > 0 } != true) delay(10) }
        c.cancelPublish()
        assertEquals(PublishState.Idle, c.publish)
    }

    @Test
    fun lrclibNeedsEveryLineStampedAndTheTracksDetails() {
        val c = controller()
        c.import(SAMPLE_TEXT)
        c.askPublish()
        assertEquals(EditorMessage.CannotPublish, c.message)
        assertEquals(PublishState.Idle, c.publish)
    }
}
