package app.nagmedar.tags.files

import app.nagmedar.tags.artwork.CoverCheck
import app.nagmedar.tags.quality.LosslessVerdict
import app.nagmedar.tags.quality.StoredVerdict
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nağmedar 1.4.0: what the library's checks found, kept in files of lines between two runs of the app. */
class FileStoresTest {
    private val dir: File = Files.createTempDirectory("stores").toFile()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val flac = "file:///storage/emulated/0/Music/Mo%C4%9Follar/01%20Bir%20Sana%20Bir%20Bana.flac"
    private val wav = "content://media/external/audio/media/42"

    @Test
    fun verdictsAreReadBackAsTheyWereSavedTheLastOneOfAFileWinning() = runBlocking {
        val file = File(dir, "quality.lines")
        val store = FileQualityStore(file, Dispatchers.Unconfined)
        store.save(StoredVerdict(flac, 31_035, 1_700_000_000, LosslessVerdict.Unclear))
        store.save(StoredVerdict(wav, 1_000, 1_700_000_001, null))
        store.save(StoredVerdict(flac, 31_035, 1_700_000_000, LosslessVerdict.Transcoded))

        val read = FileQualityStore(file, Dispatchers.Unconfined).load().associateBy { it.uri }
        assertEquals(StoredVerdict(flac, 31_035, 1_700_000_000, LosslessVerdict.Transcoded), read[flac])
        assertEquals(StoredVerdict(wav, 1_000, 1_700_000_001, null), read[wav])
    }

    @Test
    fun checksAreReadBackForgottenOnesAreNotAndALineCutShortIsPassedOver() = runBlocking {
        val file = File(dir, "covers.lines")
        val store = FileCoverCheckStore(file, Dispatchers.Unconfined)
        val withCover = CoverCheck(flac, 1_700_000_000, 31_035, hasCover = true, missing = emptySet(), garbled = false)
        val without = CoverCheck(wav, 1_700_000_001, 1_000, hasCover = false, missing = setOf("ALBUMARTIST", "DATE"), garbled = true)
        val unread = CoverCheck("file:///x.ape", 1, 2, hasCover = null, missing = null, garbled = null)
        store.save(listOf(withCover, without, unread))
        store.remove(listOf(wav))
        // the app stopped in the middle of a line
        file.appendText("+\tfile:///cut.flac\t17")

        val read = FileCoverCheckStore(file, Dispatchers.Unconfined).load().associateBy { it.uri }
        assertEquals(setOf(flac, "file:///x.ape"), read.keys)
        assertEquals(withCover, read[flac])
        assertEquals(unread, read["file:///x.ape"])
    }

    @Test
    fun aFileGrownLongIsWrittenAgainWithWhatItHolds() = runBlocking {
        val file = File(dir, "quality.lines")
        val store = FileQualityStore(file, Dispatchers.Unconfined)
        repeat(200) { round -> store.save(StoredVerdict(flac, 31_035, round.toLong(), LosslessVerdict.Lossless)) }
        assertEquals(200, file.readLines().size)

        assertEquals(1, store.load().size)
        assertTrue(file.readLines().size == 1)
        assertEquals(199L, store.load().single().modifiedEpochSeconds)
    }
}
