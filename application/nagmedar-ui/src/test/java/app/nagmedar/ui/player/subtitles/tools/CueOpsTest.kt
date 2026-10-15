package app.nagmedar.ui.player.subtitles.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-708: the line timing editor's operations: times and text, shifting, splitting, merging, and the check of times. */
class CueOpsTest {

    private fun document(vararg cues: TimedCue) = SubtitleDocument(SubtitleFormat.Srt, cues.toList())

    private val sample = document(
        TimedCue(1, 1_000, 3_000, "Bir"),
        TimedCue(2, 4_000, 6_000, "İki"),
        TimedCue(3, 7_000, 9_000, "Üç"),
    )

    @Test
    fun timesAndTextAreSet() {
        assertEquals(1_500L, CueOps.setStart(sample, 1, 1_500).cues[0].startMs)
        assertEquals(3_000L, CueOps.setStart(sample, 1, 1_500).cues[0].endMs)
        assertEquals(3_500L, CueOps.setEnd(sample, 1, 3_500).cues[0].endMs)
        assertEquals("Dört", CueOps.setText(sample, 2, "Dört").cues[1].text)
        // a start past the end carries the end along, the cue as long as it was
        val moved = CueOps.setStart(sample, 1, 5_000).cues.first { it.id == 1L }
        assertEquals(5_000L, moved.startMs)
        assertEquals(7_000L, moved.endMs)
        // the cues stay in start order
        assertEquals(listOf(2L, 1L, 3L), CueOps.setStart(sample, 1, 5_000).cues.map { it.id })
    }

    @Test
    fun severalCuesShiftTogether() {
        val shifted = CueOps.shift(sample, setOf(2, 3), 500)
        assertEquals(listOf(1_000L, 4_500L, 7_500L), shifted.cues.map { it.startMs })
        assertEquals(listOf(3_000L, 6_500L, 9_500L), shifted.cues.map { it.endMs })
        // none goes before zero: the shift stops where the first reaches it
        val early = CueOps.shift(sample, setOf(1, 2), -5_000)
        assertEquals(listOf(0L, 3_000L, 7_000L), early.cues.map { it.startMs })
        assertEquals(sample, CueOps.shift(sample, emptySet(), 100))
    }

    @Test
    fun aCueSplitsInTwo() {
        val edit = CueOps.split(document(TimedCue(1, 0, 4_000, "Gece yarısı buluşalım şimdi")), 1)!!
        val (a, b) = edit.document.cues
        assertEquals(0L to 2_000L, a.startMs to a.endMs)
        assertEquals(2_000L to 4_000L, b.startMs to b.endMs)
        assertEquals("Gece yarısı", a.text)
        assertEquals("buluşalım şimdi", b.text)
        assertEquals(b.id, edit.focus)
        assertEquals(3L, edit.document.nextId)
    }

    @Test
    fun aCueOfTwoLinesSplitsBetweenThem() {
        val edit = CueOps.split(document(TimedCue(1, 0, 4_000, "Birinci\nİkinci")), 1, 1_000)!!
        assertEquals(listOf("Birinci", "İkinci"), edit.document.cues.map { it.text })
        assertEquals(listOf(0L, 1_000L), edit.document.cues.map { it.startMs })
    }

    @Test
    fun anItalicSplitIsClosedAndOpenedAgain() {
        val edit = CueOps.split(document(TimedCue(1, 0, 4_000, "<i>Gece yarısı buluşalım</i>")), 1)!!
        assertEquals(listOf("<i>Gece yarısı</i>", "<i>buluşalım</i>"), edit.document.cues.map { it.text })
        for (cue in edit.document.cues) {
            assertEquals(cue.text, 1, Regex("<i>").findAll(cue.text).count())
            assertEquals(cue.text, 1, Regex("</i>").findAll(cue.text).count())
        }
    }

    @Test
    fun aSplitOutsideTheCueDoesNothing() {
        assertNull(CueOps.split(sample, 1, 500))
        assertNull(CueOps.split(sample, 1, 3_000))
        assertNull(CueOps.split(sample, 99))
    }

    @Test
    fun cuesMerge() {
        val edit = CueOps.merge(sample, setOf(1, 2))!!
        assertEquals(2, edit.document.cues.size)
        val merged = edit.document.cues[0]
        assertEquals(1_000L to 6_000L, merged.startMs to merged.endMs)
        assertEquals("Bir\nİki", merged.text)
        assertEquals(1L, edit.focus)
        // one alone joins the one after it
        val next = CueOps.merge(sample, setOf(2))!!
        assertEquals("İki\nÜç", next.document.cues[1].text)
        assertEquals(4_000L to 9_000L, next.document.cues[1].startMs to next.document.cues[1].endMs)
        // the last alone has none after it
        assertNull(CueOps.merge(sample, setOf(3)))
        assertNull(CueOps.merge(sample, emptySet()))
    }

    @Test
    fun cuesAreDeleted() {
        assertEquals(listOf(1L, 3L), CueOps.delete(sample, setOf(2)).cues.map { it.id })
    }

    @Test
    fun theTimesAreChecked() {
        val bad = document(
            TimedCue(1, 1_000, 1_000, "zero length"),
            TimedCue(2, 2_000, 5_000, "long"),
            TimedCue(3, 4_000, 6_000, "overlaps the one before"),
            TimedCue(4, 6_000, 7_000, "touches, does not overlap"),
            TimedCue(5, 8_000, 9_000, "  "),
            TimedCue(6, -500, 100, "before the film"),
        )
        val issues = CueOps.validate(bad.cues)
        assertEquals(setOf(CueIssue.EndNotAfterStart), issues[1])
        assertEquals(setOf(CueIssue.Overlap), issues[2])
        assertEquals(setOf(CueIssue.Overlap), issues[3])
        assertEquals(emptySet<CueIssue>(), issues[4])
        assertEquals(setOf(CueIssue.Empty), issues[5])
        assertEquals(setOf(CueIssue.NegativeStart), issues[6])
        assertEquals(2, issues.errors)
        assertEquals(2, issues.overlaps)
        assertEquals(1, issues.empty)
        assertFalse(issues.saveable)
        assertTrue(CueOps.validate(sample.cues).saveable)
        assertTrue(CueOps.validate(sample.cues).none)
    }

    @Test
    fun theNextCueWithAnIssueIsFoundRoundTheList() {
        val issues = CueOps.validate(listOf(TimedCue(1, 0, 1_000, "a"), TimedCue(2, 2_000, 2_000, "b"), TimedCue(3, 3_000, 4_000, "c"), TimedCue(4, 5_000, 5_000, "d")))
        val order = listOf(1L, 2L, 3L, 4L)
        assertEquals(2L, issues.next(order, null))
        assertEquals(4L, issues.next(order, 2))
        assertEquals(2L, issues.next(order, 4))
        assertNull(CueOps.validate(sample.cues).next(sample.cues.map { it.id }, null))
    }
}
