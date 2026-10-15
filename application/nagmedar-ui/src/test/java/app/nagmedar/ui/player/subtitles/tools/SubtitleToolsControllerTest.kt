package app.nagmedar.ui.player.subtitles.tools

import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.player.subtitles.SubtitleEncoding
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-708: the tools' window as a state machine: a file is read in its character set, cleaned,
 * converted and edited step by step (every step can be taken back), and written as a new file
 * beside the original, or where "save as" puts it; the original is never written.
 */
class SubtitleToolsControllerTest {

    private fun SubtitleToolsController.document() = checkNotNull(state?.document)

    private fun SubtitleToolsController.texts() = document().cues.map { it.text }

    private fun String.utf8() = toByteArray(Charsets.UTF_8)

    // ---------------------------------------------------------------- opening

    @Test
    fun aFileIsReadAndNamedForItsNewVersion() {
        val controller = toolsController()
        val state = checkNotNull(controller.state)
        assertEquals(SubtitleFormat.Srt, state.document?.format)
        assertEquals(9, state.document?.cues?.size)
        assertEquals("Kuzey Işığı.tr.duzenlendi.srt", state.name)
        assertEquals(SubtitleEncoding.Utf8, state.encoding)
        assertFalse(state.changed)
        assertEquals(1L, state.focus)
        assertTrue(state.issues.none)
    }

    @Test
    fun aTurkishFileInWindows1254IsReadRightAndWrittenAsUtf8() {
        val text = "1\n00:00:01,000 --> 00:00:03,000\nŞafak sökerken gölün üstünde ışık doğdu.\n\n2\n00:00:04,000 --> 00:00:06,000\nİçeride kimse yoktu, “dedi” sesi…\n"
        val file = ToolsFile("content://subs/a.srt", "a.srt")
        val io = FakeToolsIo(mutableMapOf(file.uri to text.toByteArray(SubtitleEncoding.Windows1254.charset)))
        val controller = toolsController(file = file, io = io)
        assertEquals(SubtitleEncoding.Windows1254, controller.state?.encoding)
        assertEquals("Şafak sökerken gölün üstünde ışık doğdu.", controller.texts()[0])
        controller.save()
        val written = io.saved.single().bytes
        // a file that was not UTF-8 is written in UTF-8 with its mark, so that the player reads it right
        assertTrue(SubtitleToolsController.hasUtf8Bom(written))
        assertEquals(text.trimEnd(), String(written, 3, written.size - 3, Charsets.UTF_8).trimEnd())
    }

    @Test
    fun aFileThatCannotBeReadIsSaidSo() {
        val controller = SubtitleToolsController(FakeToolsIo(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), "duzenlendi", cpu = kotlinx.coroutines.Dispatchers.Unconfined)
        controller.open(listOf(SAMPLE_FILE))
        assertEquals(ToolsProblem.Unreadable, controller.state?.problem)
        assertNull(controller.state?.document)
        assertFalse(controller.state?.loading == true)
    }

    @Test
    fun aFileThatIsNoSubtitleIsSaidSo() {
        val controller = toolsController(text = "just some words, no times at all")
        assertEquals(ToolsProblem.Unsupported, controller.state?.problem)
    }

    @Test
    fun anotherFileReplacesTheOneBeingWorkedOn() {
        val other = ToolsFile("content://subs/other.srt", "other.srt")
        val io = FakeToolsIo(mutableMapOf(SAMPLE_FILE.uri to SDH_SRT.utf8(), other.uri to FAULTY_SRT.utf8()))
        val controller = toolsController(io = io)
        controller.applySdh()
        assertTrue(controller.state?.changed == true)
        controller.choose(other)
        val state = checkNotNull(controller.state)
        assertEquals(other, state.file)
        assertFalse(state.changed)
        assertEquals(5, state.document?.cues?.size)
        assertEquals(listOf(SAMPLE_FILE, other), state.files)
    }

    @Test
    fun newNamesFollowTheOriginals() {
        assertEquals("film.tr.duzenlendi.srt", SubtitleToolsController.suggestName("film.tr.srt", "duzenlendi", SubtitleFormat.Srt))
        assertEquals("film.tr.duzenlendi.srt", SubtitleToolsController.suggestName("film.tr.duzenlendi.srt", "duzenlendi", SubtitleFormat.Srt))
        assertEquals("film.en.edited.vtt", SubtitleToolsController.suggestName("film.en.srt", "edited", SubtitleFormat.Vtt))
        assertEquals("film.duzenlendi.ass", SubtitleToolsController.suggestName("film", "duzenlendi", SubtitleFormat.Ass))
        assertEquals("a_b.srt", SubtitleToolsController.namedFor("a/b", SubtitleFormat.Srt))
        assertEquals("film.srt", SubtitleToolsController.namedFor("film.vtt", SubtitleFormat.Srt))
        assertEquals("film.v2.srt", SubtitleToolsController.namedFor(" film.v2 ", SubtitleFormat.Srt))
        assertEquals("subtitles.srt", SubtitleToolsController.namedFor("  ", SubtitleFormat.Srt))
    }

    // ---------------------------------------------------------------- cleaning

    @Test
    fun cleaningShowsWhatChangesBeforeItIsApplied() {
        val controller = toolsController()
        val result = checkNotNull(controller.state?.sdhResult)
        assertEquals(9, result.before)
        assertEquals(7, result.after)
        assertEquals(2, result.removed)
        assertEquals(3, result.edited)
        assertEquals(listOf(1, 2, 3, 6, 8), result.changes.map { it.number })
        // nothing is done yet
        assertEquals(9, controller.document().cues.size)
        assertFalse(controller.state?.changed == true)
    }

    @Test
    fun cleaningAppliedCanBeTakenBack() {
        val controller = toolsController()
        controller.applySdh()
        assertEquals(7, controller.document().cues.size)
        assertEquals("Yine mi geç kaldın?", controller.texts()[0])
        assertEquals(1, controller.state?.steps)
        // the result is shown on what is left: nothing more to clean
        assertTrue(controller.state?.sdhResult?.changes?.isEmpty() == true)
        controller.undo()
        assertEquals(9, controller.document().cues.size)
        assertEquals(0, controller.state?.steps)
    }

    @Test
    fun theRulesAreChosenOneByOne() {
        val controller = toolsController()
        controller.setSdh(SdhOptions(brackets = true, parentheses = false, speakerLabels = false, namedSpeakers = false, music = true, fontTags = false))
        val result = checkNotNull(controller.state?.sdhResult)
        // the square-bracket lines, and the line of music
        assertEquals(listOf(1, 4, 6), result.changes.map { it.number })
        controller.setSdh(SdhOptions(false, false, false, false, false, false))
        assertTrue(controller.state?.sdhResult?.changes?.isEmpty() == true)
    }

    // ---------------------------------------------------------------- frame rate

    @Test
    fun theFrameRateShowsTheFirstAndLastTimesBeforeAndAfter() {
        val controller = toolsController()
        controller.setRates(FrameRate.Fps23976, FrameRate.Fps25)
        val preview = checkNotNull(controller.state?.ratePreview)
        assertEquals(2_400, preview.firstBefore)
        assertEquals(30_100, preview.lastBefore)
        // 23.976 → 25: the times shrink by 23.976 / 25
        assertEquals(2_400 * (24_000.0 / 1001) / 25, preview.firstAfter.toDouble(), 1.0)
        assertEquals(30_100 * (24_000.0 / 1001) / 25, preview.lastAfter.toDouble(), 1.0)
        assertEquals(0, preview.dropped)
    }

    @Test
    fun theConversionIsAppliedAndTheShiftIsSpent() {
        val controller = toolsController()
        controller.setRates(FrameRate.Fps25, FrameRate.Fps25)
        controller.setShift(1_500)
        controller.applyRates()
        assertEquals(3_900, controller.document().cues.first().startMs)
        assertEquals(0L, controller.state?.shiftMs)
        assertEquals(1, controller.state?.steps)
        // the same rates and no shift: nothing to do
        controller.applyRates()
        assertEquals(1, controller.state?.steps)
    }

    @Test
    fun aShiftIsLimited() {
        val controller = toolsController()
        controller.setShift(Long.MAX_VALUE)
        assertEquals(SubtitleToolsController.MAX_SHIFT_MS, controller.state?.shiftMs)
        controller.setShift(-Long.MAX_VALUE)
        assertEquals(-SubtitleToolsController.MAX_SHIFT_MS, controller.state?.shiftMs)
    }

    @Test
    fun theToolsWorkOneAfterAnother() {
        val controller = toolsController()
        controller.applySdh()
        controller.setRates(FrameRate.Fps24, FrameRate.Fps25)
        controller.applyRates()
        assertEquals(2, controller.state?.steps)
        assertEquals(7, controller.document().cues.size)
        controller.revert()
        assertEquals(9, controller.document().cues.size)
        assertEquals(2_400, controller.document().cues.first().startMs)
        assertEquals(0, controller.state?.steps)
    }

    // ---------------------------------------------------------------- the editor

    @Test
    fun aClickSelectsAndControlAndShiftExtend() {
        val controller = toolsController()
        controller.click(2)
        assertEquals(setOf(2L), controller.state?.selected)
        controller.click(4, ctrl = true)
        assertEquals(setOf(2L, 4L), controller.state?.selected)
        controller.click(4, ctrl = true)
        assertEquals(setOf(2L), controller.state?.selected)
        controller.click(2)
        controller.click(5, shift = true)
        assertEquals(setOf(2L, 3L, 4L, 5L), controller.state?.selected)
        assertEquals(5L, controller.state?.focus)
        controller.selectAll()
        assertEquals(9, controller.state?.selected?.size)
    }

    @Test
    fun theKeysStepFromLineToLineAndStopAtTheEnds() {
        val playback = FakePlayback()
        val controller = toolsController(playback = playback)
        controller.step(forward = false)
        assertEquals(1L, controller.state?.focus)
        controller.step(forward = true, seek = true)
        assertEquals(2L, controller.state?.focus)
        assertEquals(listOf(5_000L), playback.seeks)
        repeat(20) { controller.step(forward = true) }
        assertEquals(9L, controller.state?.focus)
    }

    @Test
    fun enterOnALineSeeksTheFilmToIt() {
        val playback = FakePlayback()
        val controller = toolsController(playback = playback)
        controller.click(3)
        controller.seekToFocus()
        assertEquals(listOf(8_100L), playback.seeks)
    }

    @Test
    fun typedTimesChangeOnlyWhatIsTyped() {
        val controller = toolsController()
        controller.setStart(2, 4_000)
        val cue = controller.document().cues.first { it.id == 2L }
        assertEquals(4_000, cue.startMs)
        // the end stays: a start after it is a mistake to be shown, not made good
        assertEquals(7_600, cue.endMs)
        controller.setEnd(2, 3_000)
        assertTrue(CueIssue.EndNotAfterStart in checkNotNull(controller.state).issues[2])
    }

    @Test
    fun theFilmsPositionBecomesAStartOrAnEnd() {
        val playback = FakePlayback(position = 9_000)
        val controller = toolsController(playback = playback)
        controller.click(3)
        controller.setStartHere()
        val cue = controller.document().cues.first { it.id == 3L }
        // the start is the film's; the end stays
        assertEquals(9_000, cue.startMs)
        assertEquals(10_300, cue.endMs)
        playback.position = 10_800
        controller.setEndHere()
        assertEquals(10_800, controller.document().cues.first { it.id == 3L }.endMs)
        // a start past the end carries the end along: the line keeps its length
        playback.position = 20_000
        controller.setStartHere()
        val moved = controller.document().cues.first { it.id == 3L }
        assertEquals(20_000, moved.startMs)
        assertEquals(21_800, moved.endMs)
    }

    @Test
    fun withoutAFilmThereIsNoPositionToTake() {
        val controller = toolsController()
        controller.click(3)
        controller.setStartHere()
        controller.setEndHere()
        controller.seekToFocus()
        assertFalse(controller.state?.changed == true)
        assertNull(controller.playback)
    }

    @Test
    fun aLineIsSplitAtTheFilmsPositionAndMergedBack() {
        val playback = FakePlayback(position = 4_000)
        val controller = toolsController(playback = playback)
        controller.click(9)
        playback.position = 28_000
        controller.split()
        assertEquals(10, controller.document().cues.size)
        val first = controller.document().cues.first { it.id == 9L }
        val second = controller.document().cues.last()
        assertEquals(28_000, first.endMs)
        assertEquals(28_000, second.startMs)
        assertEquals(30_100, second.endMs)
        assertEquals("Gelmeyecek misin?", first.text)
        assertEquals("Seni bekliyorum.", second.text)
        // the new line is the one chosen
        assertEquals(second.id, controller.state?.focus)
        controller.click(9)
        controller.merge()
        assertEquals(9, controller.document().cues.size)
        val merged = controller.document().cues.last()
        assertEquals(27_300, merged.startMs)
        assertEquals(30_100, merged.endMs)
        assertEquals("Gelmeyecek misin?\nSeni bekliyorum.", merged.text)
    }

    @Test
    fun aPositionOutsideTheLineSplitsItInTheMiddle() {
        val controller = toolsController(playback = FakePlayback(position = 1))
        controller.click(7)
        controller.split()
        val cues = controller.document().cues
        assertEquals(10, cues.size)
        assertEquals(22_400, cues.first { it.id == 7L }.endMs)
    }

    @Test
    fun selectedLinesShiftTogetherAndNeverBeforeZero() {
        val controller = toolsController()
        controller.click(1)
        controller.click(2, shift = true)
        controller.shiftSelected(500)
        assertEquals(2_900, controller.document().cues[0].startMs)
        assertEquals(5_500, controller.document().cues[1].startMs)
        assertEquals(8_100, controller.document().cues[2].startMs)
        controller.shiftSelected(-60_000)
        assertEquals(0, controller.document().cues[0].startMs)
        // the second moved by the same: the gap between them stays
        assertEquals(2_600, controller.document().cues[1].startMs)
    }

    @Test
    fun removingALineChoosesTheOneAfterIt() {
        val controller = toolsController()
        controller.click(3)
        controller.deleteSelected()
        assertEquals(8, controller.document().cues.size)
        assertEquals(4L, controller.state?.focus)
        controller.click(9)
        controller.deleteSelected()
        assertEquals(8L, controller.state?.focus)
    }

    @Test
    fun typingInOneLineIsOneStepBack() {
        val controller = toolsController()
        controller.setText(1, "[kapı")
        controller.setText(1, "[kapı çarp")
        controller.setText(1, "[kapı çarpıldı]")
        assertEquals(1, controller.state?.steps)
        controller.setText(2, "başka")
        assertEquals(2, controller.state?.steps)
        controller.undo()
        controller.undo()
        assertEquals("[kapı çarpılır]", controller.texts()[0])
        controller.undo()
        assertEquals(0, controller.state?.steps)
    }

    @Test
    fun theStepsBackAreLimited() {
        val controller = toolsController()
        repeat(150) { controller.shiftSelected(1) }
        assertEquals(100, controller.state?.steps)
    }

    @Test
    fun overlapsAndWrongTimesAreFoundAndStepped() {
        val controller = toolsController(text = FAULTY_SRT)
        val issues = checkNotNull(controller.state).issues
        assertEquals(1, issues.errors)
        assertEquals(2, issues.overlaps)
        assertFalse(issues.saveable)
        controller.focusIssue()
        assertEquals(2L, controller.state?.focus)
        controller.focusIssue()
        assertEquals(3L, controller.state?.focus)
        controller.focusIssue()
        assertEquals(4L, controller.state?.focus)
        controller.focusIssue()
        assertEquals(2L, controller.state?.focus)
    }

    @Test
    fun theLinePlayingIsFollowed() {
        val playback = FakePlayback(position = 6_000)
        val controller = toolsController(playback = playback)
        controller.follow()
        assertEquals(2L, controller.state?.playing)
        playback.position = 13_500
        controller.follow()
        assertNull(controller.state?.playing)
    }

    // ---------------------------------------------------------------- shortcuts

    @Test
    fun theEditorsShortcutsActAndOthersAreLeftAlone() {
        val playback = FakePlayback(position = 9_500)
        val controller = toolsController(playback = playback)
        controller.click(3)
        assertTrue(controller.perform(ShortcutId.CueSetStart))
        assertEquals(9_500, controller.document().cues.first { it.id == 3L }.startMs)
        assertTrue(controller.perform(ShortcutId.CueSetEnd))
        assertTrue(controller.perform(ShortcutId.CueNext))
        assertEquals(4L, controller.state?.focus)
        assertTrue(controller.perform(ShortcutId.CuePrevious))
        assertEquals(3L, controller.state?.focus)
        assertTrue(controller.perform(ShortcutId.CueMerge))
        assertFalse(controller.perform(ShortcutId.PlayPause))
    }

    @Test
    fun theShortcutsDoNothingWithoutAFile() {
        val controller = SubtitleToolsController(FakeToolsIo(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), "duzenlendi", cpu = kotlinx.coroutines.Dispatchers.Unconfined)
        assertFalse(controller.perform(ShortcutId.CueSave))
        controller.open(listOf(SAMPLE_FILE))
        assertFalse(controller.perform(ShortcutId.CueSave))
    }

    // ---------------------------------------------------------------- saving

    @Test
    fun theResultIsANewFileBesideTheOriginalAndTheOriginalIsNotTouched() {
        val io = FakeToolsIo(mutableMapOf(SAMPLE_FILE.uri to SDH_SRT.utf8()))
        val controller = toolsController(io = io)
        controller.applySdh()
        controller.save()
        val saved = io.saved.single()
        assertEquals(SAMPLE_FILE, saved.original)
        assertEquals("Kuzey Işığı.tr.duzenlendi.srt", saved.name)
        val text = String(saved.bytes, Charsets.UTF_8)
        assertTrue(text.startsWith("1\n00:00:05,000 --> 00:00:07,600\nYine mi geç kaldın?\n"))
        assertFalse(text.contains("[kapı"))
        // the original's bytes are as they were, and nothing was written to its address
        assertArrayEquals(SDH_SRT.utf8(), io.files.getValue(SAMPLE_FILE.uri))
        assertTrue(io.written.isEmpty())
        val state = checkNotNull(controller.state)
        assertEquals(true, state.saved?.beside)
        assertFalse(state.saving)
        assertNull(state.pickName)
    }

    @Test
    fun theNameCanBeEditedAndIsKeptClean() {
        val io = FakeToolsIo(mutableMapOf(SAMPLE_FILE.uri to SDH_SRT.utf8()))
        val controller = toolsController(io = io)
        controller.setName("benim: altyazım?")
        controller.save()
        assertEquals("benim_ altyazım_.srt", io.saved.single().name)
        assertEquals("benim_ altyazım_.srt", controller.state?.name)
    }

    @Test
    fun wrongTimesBlockTheSaveUntilTheyAreMended() {
        val io = FakeToolsIo(mutableMapOf(SAMPLE_FILE.uri to FAULTY_SRT.utf8()))
        val controller = toolsController(io = io)
        controller.save()
        assertTrue(io.saved.isEmpty())
        controller.setEnd(4, 10_900)
        assertTrue(controller.state?.issues?.saveable == true)
        controller.save()
        assertEquals(1, io.saved.size)
        // overlaps are shown, not forbidden
        assertEquals(2, controller.state?.issues?.overlaps)
    }

    @Test
    fun theFormatOfASubtitleFileCanBeChosenAndTheByteOrderMarkAsked() {
        val io = FakeToolsIo(mutableMapOf(SAMPLE_FILE.uri to SDH_SRT.utf8()))
        val controller = toolsController(io = io)
        controller.setFormat(SubtitleFormat.Vtt)
        assertEquals("Kuzey Işığı.tr.duzenlendi.vtt", controller.state?.name)
        controller.setBom(true)
        controller.save()
        val bytes = io.saved.single().bytes
        assertEquals(listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.take(3))
        val text = String(bytes, Charsets.UTF_8).removePrefix("﻿")
        assertTrue(text.startsWith("WEBVTT"))
        assertTrue(text.contains("00:00:02.400 --> 00:00:04.900"))
        assertEquals("Kuzey Işığı.tr.duzenlendi.vtt", io.saved.single().name)
    }

    @Test
    fun whereTheAppMayNotWriteSaveAsIsAskedForAndThenWritten() {
        val io = FakeToolsIo(mutableMapOf(SAMPLE_FILE.uri to SDH_SRT.utf8()), beside = false)
        val controller = toolsController(io = io)
        controller.save()
        assertTrue(io.saved.isEmpty())
        assertEquals("Kuzey Işığı.tr.duzenlendi.srt", controller.state?.pickName)
        assertFalse(controller.state?.saving == true)
        controller.savedTo("content://picked/new")
        val (uri, bytes) = io.written.single()
        assertEquals("content://picked/new", uri)
        assertEquals(SDH_SRT.trimEnd(), String(bytes, Charsets.UTF_8).trimEnd())
        val state = checkNotNull(controller.state)
        assertEquals(false, state.saved?.beside)
        assertEquals("Kuzey Işığı.tr.duzenlendi.srt", state.saved?.name)
        assertNull(state.pickName)
        assertArrayEquals(SDH_SRT.utf8(), io.files.getValue(SAMPLE_FILE.uri))
    }

    @Test
    fun dismissingSaveAsSavesNothing() {
        val io = FakeToolsIo(mutableMapOf(SAMPLE_FILE.uri to SDH_SRT.utf8()), beside = false)
        val controller = toolsController(io = io)
        controller.save()
        controller.savedTo(null)
        assertTrue(io.written.isEmpty())
        assertNull(controller.state?.pickName)
        assertNull(controller.state?.saved)
        assertNull(controller.state?.saveProblem)
    }

    @Test
    fun aFailedWriteIsSaid() {
        val io = FakeToolsIo(mutableMapOf(SAMPLE_FILE.uri to SDH_SRT.utf8()), beside = false, writes = false)
        val controller = toolsController(io = io)
        controller.save()
        controller.savedTo("content://picked/new")
        assertEquals(SaveProblem.Failed, controller.state?.saveProblem)
        assertNull(controller.state?.saved)
        assertFalse(controller.state?.saving == true)
    }

    @Test
    fun theSavedFileCanBeShownInTheFilm() {
        val playback = FakePlayback()
        val io = FakeToolsIo(mutableMapOf(SAMPLE_FILE.uri to SDH_SRT.utf8()))
        val controller = toolsController(io = io, playback = playback)
        assertTrue(controller.state?.loadAfter == true)
        controller.save()
        assertEquals("Kuzey Işığı.tr.duzenlendi.srt", playback.loaded.single().name)
        assertTrue(controller.state?.loaded == true)
    }

    @Test
    fun theSavedFileStaysOutOfTheFilmWhenNotAskedFor() {
        val playback = FakePlayback()
        val io = FakeToolsIo(mutableMapOf(SAMPLE_FILE.uri to SDH_SRT.utf8()))
        val controller = toolsController(io = io, playback = playback)
        controller.setLoadAfter(false)
        controller.save()
        assertTrue(playback.loaded.isEmpty())
        assertFalse(controller.state?.loaded == true)
        assertNotNull(controller.state?.saved)
    }

    @Test
    fun aFilmThatRefusesTheFileStillHasItSaved() {
        val playback = FakePlayback(loads = false)
        val controller = toolsController(playback = playback)
        controller.save()
        assertFalse(controller.state?.loaded == true)
        assertNotNull(controller.state?.saved)
    }

    @Test
    fun anEditAfterSavingMakesTheFileUnsaved() {
        val controller = toolsController()
        controller.save()
        assertNotNull(controller.state?.saved)
        controller.setText(1, "değişti")
        assertNull(controller.state?.saved)
    }

    @Test
    fun closingLeavesNothing() {
        val controller = toolsController()
        controller.applySdh()
        controller.close()
        assertNull(controller.state)
        controller.undo()
        controller.save()
        assertNull(controller.state)
    }

    // ---------------------------------------------------------------- review fixes: the saved bytes

    private val bomSrt = "1\n00:00:01,000 --> 00:00:03,000\nŞafak sökerken.\n\n2\n00:00:04,000 --> 00:00:06,000\nİçeride kimse yoktu.\n"

    private fun saved(file: ToolsFile, bytes: ByteArray): ByteArray {
        val io = FakeToolsIo(mutableMapOf(file.uri to bytes))
        val controller = toolsController(file = file, io = io)
        controller.save()
        return io.saved.single().bytes
    }

    @Test
    fun aUtf8FileWithItsMarkIsWrittenWithItsMark() {
        val file = ToolsFile("content://subs/a.srt", "a.srt")
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + bomSrt.utf8()
        val controller = toolsController(file = file, io = FakeToolsIo(mutableMapOf(file.uri to bytes)))
        assertTrue(controller.state?.document?.bom == true)
        assertTrue(controller.state?.bom == true)
        val written = saved(file, bytes)
        assertTrue(SubtitleToolsController.hasUtf8Bom(written))
        // the text itself has no mark inside
        assertFalse(String(written, 3, written.size - 3, Charsets.UTF_8).startsWith("\uFEFF"))
    }

    @Test
    fun aUtf8FileWithoutAMarkIsWrittenWithout() {
        val file = ToolsFile("content://subs/a.srt", "a.srt")
        val written = saved(file, bomSrt.utf8())
        assertFalse(SubtitleToolsController.hasUtf8Bom(written))
        assertEquals(bomSrt.trimEnd(), String(written, Charsets.UTF_8).trimEnd())
    }

    @Test
    fun aUtf16FileIsWrittenAsUtf8WithAMark() {
        val file = ToolsFile("content://subs/a.srt", "a.srt")
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + bomSrt.toByteArray(Charsets.UTF_16LE)
        val controller = toolsController(file = file, io = FakeToolsIo(mutableMapOf(file.uri to bytes)))
        assertEquals(SubtitleEncoding.Utf16, controller.state?.encoding)
        val written = saved(file, bytes)
        assertTrue(SubtitleToolsController.hasUtf8Bom(written))
        assertEquals(bomSrt.trimEnd(), String(written, 3, written.size - 3, Charsets.UTF_8).trimEnd())
    }

    @Test
    fun anAssFileWithItsMarkComesBackByteForByte() {
        val text = "[Script Info]\r\nScriptType: v4.00+\r\n\r\n[Events]\r\n" +
            "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\r\n" +
            "Dialogue: 0,0:00:01.00,0:00:03.50,Default,,0,0,0,,{\\an8}Merhaba\r\n"
        val file = ToolsFile("content://subs/a.ass", "a.ass")
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.utf8()
        val io = FakeToolsIo(mutableMapOf(file.uri to bytes))
        val controller = toolsController(file = file, io = io)
        controller.save()
        assertArrayEquals(bytes, io.saved.single().bytes)
    }

    // ---------------------------------------------------------------- review fixes: the original is not written

    @Test
    fun aSaveAsThatIsTheOriginalIsRefusedAndNothingIsWritten() {
        val io = FakeToolsIo(mutableMapOf(SAMPLE_FILE.uri to SDH_SRT.utf8()), beside = false)
        val controller = toolsController(io = io)
        controller.save()
        assertNotNull(controller.state?.pickName)
        controller.savedTo(SAMPLE_FILE.uri)
        assertEquals(SaveProblem.SameFile, controller.state?.saveProblem)
        assertTrue(io.written.isEmpty())
        assertNull(controller.state?.saved)
        assertFalse(controller.state?.saving == true)
    }

    @Test
    fun aSaveAsThatTheSystemSaysIsNotNewIsRefused() {
        val io = FakeToolsIo(mutableMapOf(SAMPLE_FILE.uri to SDH_SRT.utf8()), beside = false, sameFile = true)
        val controller = toolsController(io = io)
        controller.save()
        controller.savedTo("content://subs/other.srt")
        assertEquals(SaveProblem.SameFile, controller.state?.saveProblem)
        assertTrue(io.written.isEmpty())
    }

    // ---------------------------------------------------------------- review fixes: closing with edits

    @Test
    fun closingWithUnsavedEditsAsksFirst() {
        val controller = toolsController()
        assertTrue(controller.askClose())
        controller.setText(1, "değişti")
        assertFalse(controller.askClose())
        assertTrue(controller.state?.confirmClose == true)
        controller.cancelClose()
        assertFalse(controller.state?.confirmClose == true)
        assertTrue(controller.state?.changed == true)
    }

    @Test
    fun closingAfterSavingDoesNotAsk() {
        val controller = toolsController()
        controller.setText(1, "değişti")
        controller.save()
        assertNotNull(controller.state?.saved)
        assertTrue(controller.askClose())
    }

    @Test
    fun theQuestionOfClosingCanBeAnsweredWithSave() {
        val io = FakeToolsIo(mutableMapOf(SAMPLE_FILE.uri to SDH_SRT.utf8()))
        val controller = toolsController(io = io)
        controller.setText(1, "değişti")
        assertFalse(controller.askClose())
        controller.saveInstead()
        assertFalse(controller.state?.confirmClose == true)
        assertEquals(1, io.saved.size)
        assertNotNull(controller.state?.saved)
    }

    // ---------------------------------------------------------------- review fixes: blank lines

    @Test
    fun editedTextWithABlankLineStillMakesOneCueInSrt() {
        val io = FakeToolsIo(mutableMapOf(SAMPLE_FILE.uri to SDH_SRT.utf8()))
        val again = toolsController(io = io)
        again.setText(1, "Bir\n\n\nİki")
        again.save()
        val reread = SubtitleCodec.parse("a.srt", String(io.saved.single().bytes, Charsets.UTF_8))!!
        assertEquals(9, reread.cues.size)
        assertEquals("Bir\nİki", reread.cues[0].text)
    }
}
