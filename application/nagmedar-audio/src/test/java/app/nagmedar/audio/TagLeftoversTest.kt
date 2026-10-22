package app.nagmedar.audio

import app.nagmedar.audio.TagLeftovers.Fate
import app.nagmedar.audio.TagLeftovers.Stat
import org.junit.Assert.assertEquals
import org.junit.Test

/** S-1005: a copy a dead tag write left goes back only over what that write left; otherwise it is kept aside. */
class TagLeftoversTest {

    private val started = 1_760_000_000_000L
    private val startedS = started / 1000
    private val before = Stat(size = 4_000_000, modifiedS = 1_700_000_000)

    @Test
    fun aFileTheWriteLeftGetsItsCopyBack() {
        // changed by the write: a new size, its time while the write ran
        assertEquals(Fate.PutBack, TagLeftovers.damaged(Stat(4_004_096, startedS + 3), before, started))
        // a FAT card's time, two seconds coarse: just before the write began
        assertEquals(Fate.PutBack, TagLeftovers.damaged(Stat(4_004_096, startedS - 2), before, started))
        // a long write: TagLib moving the audio of a big file on a slow card
        assertEquals(Fate.PutBack, TagLeftovers.damaged(Stat(4_000_000, startedS + 50 * 60), before, started))
        // a note of the version before, without the file's time
        assertEquals(Fate.PutBack, TagLeftovers.damaged(Stat(4_004_096, startedS + 3), null, started))
    }

    @Test
    fun aFileChangedElsewhereSinceStaysAsItIs() {
        // edited on a computer the next day
        assertEquals(Fate.KeepAside, TagLeftovers.damaged(Stat(4_100_000, startedS + 24 * 60 * 60), before, started))
        // the size and the time it had before the write: not the write's doing
        assertEquals(Fate.KeepAside, TagLeftovers.damaged(before, before, started))
        // changed an hour before the write began
        assertEquals(Fate.KeepAside, TagLeftovers.damaged(Stat(4_004_096, startedS - 60 * 60), before, started))
        // fstat could not tell
        assertEquals(Fate.KeepAside, TagLeftovers.damaged(null, before, started))
    }

    @Test
    fun aFileNotThereIsWaitedForAWeek() {
        assertEquals(Fate.Wait, TagLeftovers.missing(started, started + 6L * 24 * 60 * 60 * 1000))
        assertEquals(Fate.KeepAside, TagLeftovers.missing(started, started + TagLeftovers.KEEP_MS + 1))
    }

    @Test
    fun aKeptCopyTakesANameOfItsOwn() {
        val there = setOf("Gece Yolcusu.flac", "Gece Yolcusu (2).flac", "_")
        assertEquals("Gece Yolcusu (3).flac", TagLeftovers.freeName("Gece Yolcusu.flac") { it in there })
        assertEquals("Işık.mp3", TagLeftovers.freeName("Işık.mp3") { it in there })
        assertEquals("a_b.opus", TagLeftovers.freeName("a/b.opus") { it in there })
        assertEquals("README (2)", TagLeftovers.freeName("README") { it == "README" })
        assertEquals(".nomedia (2)", TagLeftovers.freeName(".nomedia") { it == ".nomedia" })
        // never a name that leaves the folder or is none
        assertEquals("_ (2)", TagLeftovers.freeName("..") { it in there })
        assertEquals("_ (2)", TagLeftovers.freeName("  ") { it in there })
    }
}
