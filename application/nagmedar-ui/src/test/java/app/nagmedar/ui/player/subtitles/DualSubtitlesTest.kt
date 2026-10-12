package app.nagmedar.ui.player.subtitles

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The player of the tests: its subtitle tracks, and which show, as libVLC lists them. */
internal class FakeSubtitleTracks(var tracks: List<SubtitleTrackChoice>, var selected: List<String> = emptyList()) : SubtitleTrackTarget {
    override fun tracks() = tracks

    override fun selected() = selected

    override fun select(ids: List<String>) {
        selected = ids
    }
}

internal class FakeSecondaryStore : SecondaryStore {
    val kept = HashMap<String, String?>()

    override suspend fun load(uri: String): String? = kept[uri]

    override suspend fun save(uri: String, track: String?) {
        kept[uri] = track
    }
}

/** Kuzey Işığı's subtitle tracks: Turkish, its forced lines, English. */
internal val sampleSubtitleTracks = listOf(
    SubtitleTrackChoice("spu/2", "Türkçe · SRT", "tur"),
    SubtitleTrackChoice("spu/3", "Türkçe · zorunlu · SRT", "tur"),
    SubtitleTrackChoice("spu/4", "English · PGS", "eng"),
)

/**
 * S-703: a secondary track shows with the primary; Alt+V turns it on and off (the first time in
 * another language); the player's own keys are followed; the secondary made primary leaves its
 * place; a film shows its secondary again as it plays again.
 */
class DualSubtitlesTest {

    private val film = "file:///films/Kuzey%20I%C5%9F%C4%B1%C4%9F%C4%B1.mkv"

    private fun dual(target: FakeSubtitleTracks, store: FakeSecondaryStore = FakeSecondaryStore()) =
        DualSubtitlesController(target, store, CoroutineScope(Dispatchers.Unconfined), waitStepMs = 0).also { it.onMediaStarted(film) }

    @Test
    fun aSecondaryTrackShowsWithThePrimaryAndIsKept() {
        val target = FakeSubtitleTracks(sampleSubtitleTracks, selected = listOf("spu/2"))
        val store = FakeSecondaryStore()
        val dual = dual(target, store)
        assertEquals("spu/2", dual.state.primary)
        assertFalse(dual.state.secondaryOn)
        dual.setSecondary("spu/4")
        assertEquals(listOf("spu/2", "spu/4"), target.selected)
        assertEquals("English · PGS", dual.state.secondaryTrack?.name)
        assertEquals("spu/4", store.kept[film])
        // the player's V: one track again, the secondary remembered for Alt+V
        target.selected = listOf("spu/3")
        dual.refresh()
        assertEquals("spu/3", dual.state.primary)
        assertFalse(dual.state.secondaryOn)
        assertEquals("spu/4", dual.state.secondary)
        // none: off, and the film forgets it
        dual.setSecondary(null)
        assertEquals(listOf("spu/3"), target.selected)
        assertNull(store.kept[film])
    }

    @Test
    fun altVTurnsItOnAndOffTheFirstTimeInAnotherLanguage() {
        val target = FakeSubtitleTracks(sampleSubtitleTracks, selected = listOf("spu/2"))
        val dual = dual(target)
        assertTrue(dual.toggleSecondary())
        // English, not the Turkish forced lines
        assertEquals(listOf("spu/2", "spu/4"), target.selected)
        assertFalse(dual.toggleSecondary())
        assertEquals(listOf("spu/2"), target.selected)
        assertTrue(dual.toggleSecondary())
        assertEquals(listOf("spu/2", "spu/4"), target.selected)
    }

    @Test
    fun withoutAPrimaryOrAnotherTrackThereIsNoSecondary() {
        val one = FakeSubtitleTracks(sampleSubtitleTracks.take(1), selected = listOf("spu/2"))
        assertFalse(dual(one).toggleSecondary())
        assertEquals(listOf("spu/2"), one.selected)
        val off = FakeSubtitleTracks(sampleSubtitleTracks, selected = emptyList())
        assertFalse(dual(off).toggleSecondary())
        assertEquals(emptyList<String>(), off.selected)
    }

    @Test
    fun theSecondaryMadePrimaryLeavesItsPlace() {
        val target = FakeSubtitleTracks(sampleSubtitleTracks, selected = listOf("spu/2", "spu/4"))
        val dual = dual(target)
        assertEquals("spu/2", dual.state.primary)
        assertEquals("spu/4", dual.state.secondary)
        assertTrue(dual.state.secondaryOn)
        dual.setPrimary("spu/4")
        assertEquals(listOf("spu/4"), target.selected)
        assertFalse(dual.state.secondaryOn)
        dual.setPrimary("spu/3")
        assertEquals(listOf("spu/3"), target.selected)
        dual.setPrimary(null)
        assertEquals(emptyList<String>(), target.selected)
        assertNull(dual.state.primary)
    }

    @Test
    fun aFilmShowsItsSecondaryAgain() {
        val store = FakeSecondaryStore().apply { kept[film] = "spu/4" }
        val target = FakeSubtitleTracks(sampleSubtitleTracks, selected = listOf("spu/2"))
        val dual = dual(target, store)
        assertEquals(listOf("spu/2", "spu/4"), target.selected)
        assertTrue(dual.state.secondaryOn)
    }

    @Test
    fun twoLanguagesOnTheBarsChip() {
        assertEquals("TR", shortLanguage("tur", "Türkçe · SRT"))
        assertEquals("EN", shortLanguage("eng", "English"))
        assertEquals("Commentary", shortLanguage("", "Commentary"))
    }
}
