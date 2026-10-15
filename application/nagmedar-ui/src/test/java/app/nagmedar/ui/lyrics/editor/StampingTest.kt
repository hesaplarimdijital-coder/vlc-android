package app.nagmedar.ui.lyrics.editor

import app.nagmedar.ui.lyrics.Lrc
import app.nagmedar.ui.lyrics.LyricLine
import app.nagmedar.ui.lyrics.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1005: the stamping state machine: what a stamp times, where the cursor goes, taking stamps back, nudging them. */
class StampingTest {

    private fun lines(vararg text: String) = StampState.parse(text.joinToString("\n"))

    private fun StampState.times() = lines.map { it.startMs }

    @Test
    fun aStampTimesTheLineAndMovesOn() {
        val s = lines("bir", "iki", "üç").stamp(1_234).stamp(2_000)
        // hundredths, as LRC writes them
        assertEquals(listOf(1_230L, 2_000L, UNSET), s.times())
        assertEquals(StampCursor(2), s.cursor)
        assertEquals(2, s.stampedLines)
        assertFalse(s.complete)
        assertFalse(s.finished)
    }

    @Test
    fun theLastStampFinishesAndMoreStampsDoNothing() {
        val s = lines("bir", "iki").stamp(100).stamp(200)
        assertTrue(s.complete)
        assertTrue(s.finished)
        assertSame(s, s.stamp(300))
        assertEquals(listOf(100L, 200L), s.times())
    }

    @Test
    fun aStampNeverComesBeforeTheOneBefore() {
        val s = lines("bir", "iki").stamp(5_000).stamp(4_000)
        assertEquals(listOf(5_000L, 5_000L), s.times())
    }

    @Test
    fun undoTakesTheLastStampBackAndPutsTheCursorWhereItWas() {
        val s = lines("bir", "iki", "üç").stamp(100).stamp(200)
        val back = s.undo()
        assertEquals(listOf(100L, UNSET, UNSET), back.times())
        assertEquals(StampCursor(1), back.cursor)
        val none = back.undo().undo()
        assertEquals(listOf(UNSET, UNSET, UNSET), none.times())
        assertEquals(StampCursor(0), none.cursor)
        assertFalse(none.canUndo)
        assertSame(none, none.undo())
    }

    @Test
    fun stampingAnEarlierLineAgainTakesTheLaterStampsItWouldPassAway() {
        var s = lines("bir", "iki", "üç", "dört").stamp(1_000).stamp(2_000).stamp(3_000)
        s = s.moveTo(1).stamp(2_500)
        // line 2 (3 s) is still after 2.5 s: it stays; the cursor went on to it
        assertEquals(listOf(1_000L, 2_500L, 3_000L, UNSET), s.times())
        s = s.moveTo(0).stamp(500)
        assertEquals(listOf(500L, 2_500L, 3_000L, UNSET), s.times())
        // a line stamped after the stamps of the lines after it clears those
        s = s.moveTo(1).stamp(3_200)
        assertEquals(listOf(500L, 3_200L, UNSET, UNSET), s.times())
        // and undo brings them back
        assertEquals(listOf(500L, 2_500L, 3_000L, UNSET), s.undo().times())
    }

    @Test
    fun theCursorMovesWithinTheLines() {
        val s = lines("bir", "iki", "üç")
        assertEquals(StampCursor(1), s.moveBy(1).cursor)
        assertEquals(StampCursor(0), s.moveBy(-1).cursor)
        assertEquals(StampCursor(2), s.moveBy(5).cursor)
        assertEquals(StampCursor(2), s.moveTo(9).cursor)
        val done = s.stamp(1).stamp(2).stamp(3)
        assertTrue(done.finished)
        assertSame(done, done.moveBy(1))
        assertEquals(StampCursor(2), done.moveBy(-1).cursor)
        assertEquals(0, StampState(emptyList()).moveBy(1).cursor.line)
    }

    @Test
    fun blankLinesAreLinesToo() {
        val s = lines("bir", "", "iki")
        assertEquals(3, s.lines.size)
        val stamped = s.stamp(1_000).stamp(2_000).stamp(3_000)
        assertTrue(stamped.complete)
        assertEquals("[00:01.00]bir\n[00:02.00]\n[00:03.00]iki", Lrc.write(stamped.lyrics()))
    }

    @Test
    fun wordModeStampsTheNextWordAndThenTheNextLine() {
        var s = lines("Merhaba güzel dünya", "iki").withMode(StampMode.Word)
        s = s.stamp(1_000)
        assertEquals(StampCursor(0, 1), s.cursor)
        // the line starts with its first word
        assertEquals(1_000L, s.lines[0].startMs)
        assertFalse(s.lines[0].wordTimed)
        s = s.stamp(1_500).stamp(2_100)
        assertEquals(StampCursor(1, 0), s.cursor)
        assertTrue(s.lines[0].wordTimed)
        assertEquals(listOf(1_000L, 1_500L, 2_100L), s.lines[0].words.map { it.startMs })
        s = s.stamp(3_000)
        // a one-word line is a line
        assertTrue(s.complete)
        assertEquals(
            listOf(
                LyricLine(1_000, "Merhaba güzel dünya", listOf(LyricWord(1_000, "Merhaba "), LyricWord(1_500, "güzel "), LyricWord(2_100, "dünya"))),
                LyricLine(3_000, "iki", listOf(LyricWord(3_000, "iki"))),
            ),
            s.lyrics(),
        )
    }

    @Test
    fun aWordNeverComesBeforeTheWordBefore() {
        val s = lines("bir iki").withMode(StampMode.Word).stamp(2_000).stamp(1_000)
        assertEquals(listOf(2_000L, 2_000L), s.lines[0].words.map { it.startMs })
    }

    @Test
    fun timingALineAgainWordByWordStartsOver() {
        var s = lines("bir iki üç").withMode(StampMode.Word).stamp(100).stamp(200).stamp(300)
        s = s.moveTo(0).stamp(1_000)
        assertEquals(listOf(1_000L, UNSET, UNSET), s.lines[0].words.map { it.startMs })
        assertEquals(1_000L, s.lines[0].startMs)
        assertEquals(StampCursor(0, 1), s.cursor)
        // undo gives the old words back
        assertEquals(listOf(100L, 200L, 300L), s.undo().lines[0].words.map { it.startMs })
    }

    @Test
    fun changingTheModeStartsTheLineOverFromItsFirstWord() {
        val s = lines("bir iki").withMode(StampMode.Word).stamp(100)
        assertEquals(StampCursor(0, 1), s.cursor)
        val line = s.withMode(StampMode.Line)
        assertEquals(StampCursor(0, 0), line.cursor)
        assertSame(line, line.withMode(StampMode.Line))
        // a line stamp takes the word stamps away
        assertEquals(listOf(UNSET, UNSET), line.stamp(900).lines[0].words.map { it.startMs })
    }

    @Test
    fun theLastStampMovesByTenthsOfASecondWithinItsNeighbours() {
        var s = lines("bir", "iki", "üç").stamp(1_000).stamp(2_000)
        assertEquals(2_000L, s.lastStampMs)
        s = s.nudge(100)
        assertEquals(listOf(1_000L, 2_100L, UNSET), s.times())
        s = s.nudge(-100).nudge(-100)
        assertEquals(1_900L, s.lastStampMs)
        // not before the line before
        s = s.nudge(-5_000)
        assertEquals(1_000L, s.lastStampMs)
        // not after the line after
        val between = lines("bir", "iki", "üç").stamp(1_000).stamp(2_000).stamp(2_050).moveTo(1).stamp(2_000)
        assertEquals(2_000L, between.lastStampMs)
        assertEquals(listOf(1_000L, 2_050L, 2_050L), between.nudge(100).times())
    }

    @Test
    fun nudgingNeedsAStamp() {
        val s = lines("bir")
        assertSame(s, s.nudge(100))
        assertEquals(UNSET, s.lastStampMs)
    }

    @Test
    fun nudgingAWordMovesTheWordAndTheLineWhenItIsTheFirst() {
        var s = lines("bir iki").withMode(StampMode.Word).stamp(1_000)
        s = s.nudge(100)
        assertEquals(1_100L, s.lines[0].startMs)
        assertEquals(1_100L, s.lines[0].words[0].startMs)
        s = s.stamp(1_500).nudge(-100)
        assertEquals(1_400L, s.lines[0].words[1].startMs)
        assertEquals(1_100L, s.lines[0].startMs)
    }

    @Test
    fun lyricsAlreadyTimedAreKeptAndTimingGoesOnAfterThem() {
        val s = StampState.parse("[00:01.00]bir\n[00:02.00]iki\n")
        assertTrue(s.complete)
        assertEquals(StampCursor(0), s.cursor)
        val partial = StampState.of(listOf(LyricLine(1_000, "bir"), LyricLine(-1, "iki"), LyricLine(-1, "üç")))
        assertEquals(StampCursor(1), partial.cursor)
        assertEquals(listOf(LyricLine(1_000, "bir")), partial.stampedLyrics())
        assertEquals(listOf(1_000L, UNSET, UNSET), partial.times())
    }

    @Test
    fun wordTimedLyricsKeepTheirWords() {
        val timed = LyricLine(1_000, "bir iki", listOf(LyricWord(1_000, "bir "), LyricWord(1_400, "iki")))
        val s = StampState.of(listOf(timed))
        assertTrue(s.lines[0].wordTimed)
        assertEquals(listOf(timed), s.lyrics())
    }

    @Test
    fun timedLinesWriteAsLrcAndReadBackTheSame() {
        val s = lines("Birinci satır", "İkinci satır", "Üçüncü").stamp(1_000).stamp(62_340).stamp(125_500)
        val text = Lrc.write(s.lyrics())
        assertEquals("[00:01.00]Birinci satır\n[01:02.34]İkinci satır\n[02:05.50]Üçüncü", text)
        assertEquals(s.lyrics(), Lrc.parse(text))
    }

    @Test
    fun wordTimedLinesWriteWordStampsAndReadBackTheSame() {
        val s = lines("Merhaba güzel dünya").withMode(StampMode.Word).stamp(1_000).stamp(1_500).stamp(62_100)
        val text = Lrc.write(s.lyrics())
        assertEquals("[00:01.00]<00:01.00>Merhaba <00:01.50>güzel <01:02.10>dünya", text)
        assertEquals(s.lyrics(), Lrc.parse(text))
    }
}
