package app.nagmedar.ui.music

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.AudioTechEntry
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * 1.0.3: the inspector's tags tab shows a file's ReplayGain tags (the track's and the album's gain
 * and peak) as the probe read them, "—" for a tag the file has not; a probe of an earlier version
 * has not read them, and the rows wait for the file to be read again.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReplayGainTagsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val turkish = Locale.forLanguageTag("tr-TR")

    @Test
    fun gainsAndPeaksAreWordedAsTheTypographyAsks() {
        val lines = replayGainLines(ReplayGainTags(-6.62, 0.988312, 3.0, 1.12), turkish, "—")
        // the minus sign of the typography rules, the Turkish decimal comma, a space before the unit
        assertEquals("−6,6 dB", lines.trackGain)
        assertEquals("0,988 · −0,1 dBFS", lines.trackPeak)
        assertEquals("+3 dB", lines.albumGain)
        // a peak over full scale (a lossy file's) says so
        assertEquals("1,120 · +1 dBFS", lines.albumPeak)
        assertEquals("0.988 · −0.1 dBFS", replayGainLines(ReplayGainTags(trackPeak = 0.988312), Locale.ENGLISH, "—").trackPeak)
    }

    @Test
    fun whatTheFileHasNotIsADash() {
        val none = replayGainLines(ReplayGainTags(), turkish, "—")
        assertEquals(ReplayGainLines("—", "—", "—", "—"), none)
        // an Opus file's R128 gain has no peak; a silent file's peak is no level in dB
        val opus = replayGainLines(ReplayGainTags(trackGainDb = 0.0, trackPeak = 0.0, albumGainDb = Double.NaN), turkish, "—")
        assertEquals("0 dB", opus.trackGain)
        assertEquals("0,000", opus.trackPeak)
        assertEquals("—", opus.albumGain)
    }

    private fun row(version: Int, gain: Double? = -6.62) = AudioTechEntry(
        "file:///storage/emulated/0/Music/Itrî/Neva Kâr.flac", fileSize = 31_200_000, modifiedAt = 1_700_000_000_000,
        probeVersion = version, codec = "flac", container = "flac", rate = 96_000, channels = 2, bits = 24, isFloat = false,
        dsdRate = 0, bitrate = 2_620_000, durationMs = 498_000, composer = null, probedAt = 5,
        trackGainDb = gain, trackPeak = 0.988312, albumGainDb = null, albumPeak = null,
    )

    @Test
    fun aRowOfAnEarlierProbeKeepsItsTechAndWaitsForTheTags() {
        with(TrackTechIndex) {
            // read again in the background; meanwhile the table keeps the format, depth and rate it showed
            val earlier = row(version = 1, gain = null)
            assertEquals(24, earlier.toTech()!!.bits)
            assertNull("not read for them yet", earlier.gains())
            // read again, the file's technical side is the same: the music list is not made again for it
            val read = row(version = TrackTechIndex.PROBE_VERSION)
            assertEquals(earlier.toTech(), read.toTech())
            assertEquals(ReplayGainTags(trackGainDb = -6.62, trackPeak = 0.988312), read.gains())
            // a file the probe could not read, a row of a later version (an older app's database)
            assertNull(read.copy(rate = 0).toTech())
            assertNull(read.copy(rate = 0).gains())
            assertNull(row(version = TrackTechIndex.PROBE_VERSION + 1).toTech())
            assertNull(row(version = TrackTechIndex.PROBE_VERSION + 1).gains())
        }
    }

    private val neva = MusicSamples.tracks.first { it.title == "Neva Kâr" }
    private val yagmur = MusicSamples.tracks.first { it.title == "Yağmur" }

    private fun tags(track: Track, gains: ReplayGainTags?) {
        compose.setContent {
            NagmedarTheme(inputMode = InputMode.Pointer) {
                Box(Modifier.width(384.dp)) { TrackTags(track, replayGain = gains) }
            }
        }
        compose.waitForIdle()
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
    fun theTagsTabShowsTheGainsAndPeaks() {
        tags(neva, MusicSamples.gains[neva.uri])
        compose.onNodeWithText("Parça kazancı").assertExists()
        compose.onNodeWithText("−6,6 dB").assertExists()
        compose.onNodeWithText("0,988 · −0,1 dBFS").assertExists()
        compose.onNodeWithText("Albüm kazancı").assertExists()
        compose.onNodeWithText("−7,1 dB").assertExists()
        compose.onNodeWithText("Albüm tepesi").assertExists()
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
    fun anUntaggedFileShowsDashes() {
        tags(yagmur, MusicSamples.gains[yagmur.uri])
        compose.onNodeWithText("Parça tepesi").assertExists()
        compose.onAllNodesWithText("—").assertCountEquals(4)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
    fun aFileNotReadForThemYetShowsNoRows() {
        tags(neva, null)
        compose.onAllNodesWithText("Parça kazancı").assertCountEquals(0)
        compose.onAllNodesWithText("—").assertCountEquals(0)
    }

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi-keysexposed-qwerty")
    fun inEnglish() {
        tags(neva, MusicSamples.gains[neva.uri])
        compose.onNodeWithText("Track gain").assertExists()
        compose.onNodeWithText("−6.6 dB").assertExists()
        compose.onNodeWithText("Album peak").assertExists()
        compose.onNodeWithText("0.999 · 0 dBFS").assertExists()
    }
}
