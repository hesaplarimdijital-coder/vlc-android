package app.nagmedar.ui.player.subtitles

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * S-708/S-706: a subtitle file is always a NEW file; one that exists is never returned, written to
 * or replaced; a large one (an extracted track) is copied in as it is read.
 */
class NewSubtitleFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val mine = "yeni".toByteArray()

    @Test
    fun aFreeNameIsTaken() {
        val file = writeNewFile(folder.root, "Film.tr.srt", mine, source = null)
        assertEquals("Film.tr.srt", file?.name)
        assertArrayEquals(mine, file?.readBytes())
    }

    @Test
    fun aTakenNameIsNumberedAndTheOldFileStaysAsItWas() {
        val original = folder.newFile("Film.tr.srt").apply { writeText("özgün") }
        val file = writeNewFile(folder.root, "Film.tr.srt", mine, source = original)
        assertEquals("Film.tr (2).srt", file?.name)
        assertEquals("özgün", original.readText())
        assertArrayEquals(mine, file?.readBytes())
    }

    @Test
    fun theNameOfTheOriginalIsNeverWrittenInto() {
        val original = folder.newFile("a.srt").apply { writeText("özgün") }
        repeat(3) { writeNewFile(folder.root, "a.srt", mine, source = original) }
        assertEquals("özgün", original.readText())
        assertEquals(setOf("a.srt", "a (2).srt", "a (3).srt", "a (4).srt"), folder.root.list()!!.toSet())
    }

    @Test
    fun whenEveryNameIsTakenNothingIsReturnedAndNothingIsWritten() {
        val taken = (1..MAX_COPIES).map { n ->
            folder.newFile(if (n == 1) "a.srt" else "a ($n).srt").apply { writeText("dosya $n") }
        }
        assertNull(createUniqueFile(folder.root, "a.srt"))
        assertNull(writeNewFile(folder.root, "a.srt", mine, source = taken.first()))
        taken.forEachIndexed { index, file -> assertEquals("dosya ${index + 1}", file.readText()) }
        assertEquals(MAX_COPIES, folder.root.list()!!.size)
    }

    @Test
    fun aNameWithoutAnExtensionIsNumbered() {
        folder.newFile("altyazi")
        val file = createUniqueFile(folder.root, "altyazi")
        assertNotNull(file)
        assertNotEquals("altyazi", file?.name)
        assertEquals("altyazi (2)", file?.name)
    }

    @Test
    fun aLargeFileIsCopiedIntoItsNewFileAsItIsRead() {
        // an extracted track, millions of lines: never in the heap whole
        val extracted = folder.newFile("job.srt").apply { outputStream().use { out -> repeat(4_000) { out.write(ByteArray(1_024) { (it % 64 + 32).toByte() }) } } }
        val kept = folder.newFile("Film.tr.srt").apply { writeText("özgün") }
        val file = writeNewFile(folder.root, "Film.tr.srt", source = kept) { file -> file.outputStream().use { out -> extracted.inputStream().use { it.copyTo(out) } } }
        assertEquals("Film.tr (2).srt", file?.name)
        assertArrayEquals(extracted.readBytes(), file?.readBytes())
        assertEquals("özgün", kept.readText())
    }

    @Test
    fun aFileThatCannotBeWrittenIsNotLeftBehind() {
        val kept = folder.newFile("a.srt").apply { writeText("özgün") }
        val result = writeNewFile(folder.root, "a.srt", mine, source = kept) { file, _ ->
            file.writeText("yarım")
            throw java.io.IOException("disk full")
        }
        assertNull(result)
        assertEquals(listOf("a.srt"), folder.root.list()!!.toList())
        assertEquals("özgün", kept.readText())
    }
}
