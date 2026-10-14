package app.nagmedar.ui.player.record

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

/** S-607: a recording's file name, its type and its default folder. */
class RecordingsTest {

    private val utc = TimeZone.getTimeZone("UTC")

    /** 15 Ekim 2026 21:04:33 UTC. */
    private val wall = 1_792_098_273_000L

    @Test
    fun theNameIsWhatPlaysAndWhen() {
        assertEquals("TRT Radyo 1 2026-10-15 21-04-33.mp3", recordingFileName("TRT Radyo 1", wall, "mp3", "Nağmedar", utc))
    }

    @Test
    fun whatFileSystemsRefuseBecomesAnUnderscore() {
        assertEquals("Film_ Bölüm 1_2 2026-10-15 21-04-33.ts", recordingFileName("Film: Bölüm 1/2", wall, "ts", "Nağmedar", utc))
        assertEquals("a_b_c 2026-10-15 21-04-33.mkv", recordingFileName("a\"b\u0007c", wall, ".MKV", "Nağmedar", utc))
    }

    @Test
    fun noTitleIsTheApps() {
        assertEquals("Nağmedar 2026-10-15 21-04-33.ts", recordingFileName(null, wall, "ts", "Nağmedar", utc))
        assertEquals("Nağmedar 2026-10-15 21-04-33.ts", recordingFileName("  ...  ", wall, "ts", "Nağmedar", utc))
    }

    @Test
    fun aStrangeExtensionIsLeftOut() {
        assertEquals("Yayın 2026-10-15 21-04-33", recordingFileName("Yayın", wall, "t s/", "Nağmedar", utc))
        assertEquals("Yayın 2026-10-15 21-04-33", recordingFileName("Yayın", wall, "", "Nağmedar", utc))
    }

    @Test
    fun aLongTitleIsCut() {
        val name = recordingFileName("x".repeat(300), wall, "ts", "Nağmedar", utc)
        assertEquals(96 + " 2026-10-15 21-04-33.ts".length, name.length)
    }

    @Test
    fun theTypeFollowsTheExtension() {
        assertEquals("video/mp2t", recordingMime("ts"))
        assertEquals("video/x-matroska", recordingMime("MKV"))
        assertEquals("audio/mpeg", recordingMime("mp3"))
        assertEquals("audio/aac", recordingMime("aac"))
        assertEquals("audio/ogg", recordingMime("opus"))
        assertEquals("application/octet-stream", recordingMime("xyz"))
    }

    @Test
    fun theDefaultFolderFollowsTheType() {
        assertEquals("Movies/Nağmedar", defaultRecordingFolder("video/mp2t", "Nağmedar"))
        assertEquals("Music/Nağmedar", defaultRecordingFolder("audio/mpeg", "Nağmedar"))
        assertEquals("Download/Nağmedar", defaultRecordingFolder("application/octet-stream", "Nağmedar"))
    }

    @Test
    fun savingSaysHowFar() {
        assertEquals(0.5f, RecordingStatus.Saving(50, 100).fraction)
        assertEquals(0f, RecordingStatus.Saving(50, 0).fraction)
        assertEquals(1f, RecordingStatus.Saving(150, 100).fraction)
    }
}
