package app.nagmedar.ui.player.subtitles.extract

import app.nagmedar.ui.player.subtitles.SavedSubtitle
import app.nagmedar.ui.player.subtitles.tools.FakeToolsIo
import app.nagmedar.ui.player.subtitles.tools.SDH_SRT
import app.nagmedar.ui.player.subtitles.tools.SubtitleToolsController
import app.nagmedar.ui.player.subtitles.tools.ToolsFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The media probe's tracks and WorkManager's jobs, in memory. */
internal class FakeExtractService(private val tracks: List<EmbeddedTrack> = listOf(TRACK)) : ExtractService {
    override val available: Boolean = true
    val jobs = MutableStateFlow<ExtractJob?>(null)
    val started = mutableListOf<Pair<String, ExtractRequest>>()

    override suspend fun tracks(film: ToolsFile): List<EmbeddedTrack> = tracks

    override suspend fun start(id: String, request: ExtractRequest): Boolean {
        started += id to request
        return true
    }

    override fun cancel() = Unit

    override fun job(): Flow<ExtractJob?> = jobs

    /** The job started last, finished with its file written. */
    fun finish(name: String = "Kuzey Işığı.tr.srt") {
        val (id, request) = started.last()
        jobs.value = ExtractJob(id, request, finished = true, fraction = 1f, saved = SavedSubtitle("content://films/$name", name, beside = true), lines = 9)
    }

    companion object {
        val TRACK = EmbeddedTrack(index = 0, codec = "subrip", language = "tur", title = "")
    }
}

/**
 * The embedded subtitles' extraction in the tools' window: the file a job of this window writes
 * is read at once; once the window closes (the player going closes it too), a job that ends
 * later goes on in the background and is not taken in by the window it no longer has.
 */
class SubtitleExtractControllerTest {

    private val film = ToolsFile("content://films/Kuzey%20Isigi.mkv", "Kuzey Işığı.mkv")
    private val service = FakeExtractService()
    private val io = FakeToolsIo(mutableMapOf("content://films/Kuzey Işığı.tr.srt" to SDH_SRT.toByteArray(Charsets.UTF_8)))
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val extraction = SubtitleExtractController(service, io, scope)
    private val tools = SubtitleToolsController(io, scope, "duzenlendi", cpu = Dispatchers.Unconfined, debounceMs = 0, extraction = extraction)

    @Test
    fun theWindowsJobIsReadWhenItIsDone() {
        tools.open(emptyList(), film = film, extract = true)
        assertEquals(listOf(FakeExtractService.TRACK), extraction.state.tracks)
        extraction.extract(FakeExtractService.TRACK)
        assertEquals("Kuzey Işığı.tr.srt", service.started.single().second.outputName)
        service.finish()
        assertTrue(extraction.state.run is ExtractRun.Done)
        assertEquals("Kuzey Işığı.tr.srt", tools.state?.file?.name)
    }

    @Test
    fun aClosedWindowFollowsNoJob() {
        tools.open(emptyList(), film = film, extract = true)
        extraction.extract(FakeExtractService.TRACK)
        tools.close()
        service.finish()
        assertNull(tools.state)
        assertEquals(ExtractRun.Idle, extraction.state.run)
        assertNull(extraction.state.film)
    }
}
