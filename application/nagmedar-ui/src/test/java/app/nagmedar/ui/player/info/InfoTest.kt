package app.nagmedar.ui.player.info

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.library.TechSamples
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Kuzey Işığı playing its Turkish DTS track with Turkish subtitles, as libVLC lists them. */
internal val sampleTracks = PlayerTracks(
    video = VideoTrackFacts("hevc", 3840, 2160, 23.976),
    audio = listOf(
        TrackFacts("truehd", "eng", "English", selected = false, channels = 8, rate = 48000),
        TrackFacts("dts", "tur", "Türkçe", selected = true, channels = 6, rate = 48000),
        TrackFacts("ac3", "", "Yorum", selected = false, channels = 2, rate = 48000),
    ),
    subtitles = listOf(
        TrackFacts("pgs", "tur", "Türkçe", selected = true),
        TrackFacts("pgs", "eng", "English", selected = false),
        TrackFacts("subrip", "tur", "Türkçe · SRT", selected = false),
    ),
)

internal val sampleFile = FileFacts("Kuzey_Isigi_2160p_UHD_Remux.mkv", "58,4 GB · MKV · 2:18:10 · SAMSUNG T7")

/**
 * S-605: the Info tab lists the streams with their codecs, profiles and HDR metadata (from the
 * media probe, else from libVLC), the playing ones tagged; the statistics keep their last
 * minute; the report and the overlay say the same as the tab.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class InfoTest {

    @get:Rule
    val compose = createComposeRule()

    private fun rows(tech: app.nagmedar.lab.MediaTech?, tracks: PlayerTracks, decoding: VideoDecoding?): List<StreamRow> {
        var result = emptyList<StreamRow>()
        compose.setContent { result = streamRows(tech, tracks, decoding) }
        compose.waitForIdle()
        return result
    }

    @Test
    fun theProbesStreamsWithTheirHdrMetadata() {
        val rows = rows(TechSamples.kuzeyIsigi, sampleTracks, VideoDecoding.Hardware)
        assertEquals(listOf("V", "A1", "A2", "A3", "S"), rows.map { it.mark })
        val video = rows.first()
        assertEquals("HEVC Main 10 · 3840×2160 · 23,976", video.title)
        assertTrue(video.meta, video.meta.startsWith("Dolby Vision P7 FEL"))
        assertTrue(video.meta, "BT.2020 PQ" in video.meta && "MaxCLL 1000" in video.meta && "MaxFALL 400" in video.meta)
        assertEquals("donanım", video.tag)
        assertEquals(TagTone.Good, video.tone)
        // the Turkish track plays: libVLC's second audio track is the probe's second
        assertEquals(listOf(false, true, false), rows.filter { it.mark.startsWith("A") }.map { it.current })
        assertEquals("çalıyor", rows[2].tag)
        assertEquals("48 kHz · 24 bit", rows[1].meta)
        val subtitles = rows.last()
        assertEquals("Altyazı · 3 iz", subtitles.title)
        assertEquals("PGS Tür, İng · SRT Tür", subtitles.meta)
        assertEquals("Tür", subtitles.tag)
    }

    @Test
    fun withoutAProbeLibVlcsTracks() {
        val rows = rows(null, sampleTracks, VideoDecoding.SoftwareFallback)
        assertEquals("HEVC · 3840×2160 · 23,976", rows.first().title)
        assertEquals("yazılım", rows.first().tag)
        assertEquals(TagTone.Warn, rows.first().tone)
        assertEquals("DTS · Tür · Türkçe", rows[2].title)
        assertEquals("6 kanal · 48 kHz", rows[2].meta)
        assertEquals("çalıyor", rows[2].tag)
        assertEquals("Altyazı · 3 iz", rows.last().title)
    }

    @Test
    fun theStatisticsKeepTheirLastMinute() {
        val live = LiveStats()
        repeat(70) { live.add(StatsSample(it.toFloat(), it * 24L, 0)) }
        assertEquals(60, live.history.size)
        assertEquals(69f, live.history.last())
        assertEquals(10f, live.history.first())
        live.add(null)
        assertEquals(69f, live.last?.inputMbps)
        live.clear()
        assertTrue(live.history.isEmpty())
        assertNull(live.last)
    }

    @Test
    fun theReportSaysWhatTheTabShows() {
        val streams = listOf(StreamRow("V", "HEVC Main 10 · 3840×2160 · 23,976", "HDR10 · BT.2020 PQ", "donanım"))
        val report = infoReport(
            sampleFile,
            streams,
            // what the native side cannot tell yet is left out
            listOf(InfoRow("Video çözücü", "MediaCodec · donanım"), InfoRow("Video çıkışı", null)),
            StatsSample(61.8f, 103_216, 0),
            ReportLabels("Akışlar", "Çözme ve çıkış", "Canlı", "Giriş bit hızı: 61,8 Mbps", "Kare: 103 216 gösterildi, 0 kayıp"),
        )
        assertEquals(
            listOf(
                "Kuzey_Isigi_2160p_UHD_Remux.mkv",
                "58,4 GB · MKV · 2:18:10 · SAMSUNG T7",
                "",
                "Akışlar",
                "V  HEVC Main 10 · 3840×2160 · 23,976 · HDR10 · BT.2020 PQ [donanım]",
                "",
                "Çözme ve çıkış",
                "Video çözücü: MediaCodec · donanım",
                "",
                "Canlı",
                "Giriş bit hızı: 61,8 Mbps",
                "Kare: 103 216 gösterildi, 0 kayıp",
            ),
            report.lines(),
        )
    }

    private var overlay by mutableStateOf(false)

    @Test
    fun theTabShowsTheFilmAndItsLastSecond() {
        val live = LiveStats().apply {
            listOf(58f, 64f, 61.8f).forEach { add(StatsSample(it, 103_216, 0)) }
        }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Box(Modifier.width(360.dp).height(885.dp)) {
                    InfoPanel(
                        file = sampleFile,
                        streams = streamRows(TechSamples.kuzeyIsigi, sampleTracks, VideoDecoding.Hardware),
                        decoding = listOf(InfoRow("Video çözücü", "MediaCodec · donanım")),
                        live = live,
                        overlay = overlay,
                        onOverlay = { overlay = it },
                        report = { "rapor" },
                    )
                }
            }
        }
        compose.onNodeWithText("Kuzey_Isigi_2160p_UHD_Remux.mkv").assertExists()
        compose.onNodeWithText("HEVC Main 10 · 3840×2160 · 23,976").assertExists()
        compose.onNodeWithTag("info-bitrate").assertExists()
        compose.onNodeWithText("61,8 Mbps").assertExists()
        compose.onNodeWithText("0 kayıp").assertExists()
        compose.onNodeWithText("103.216 gösterildi").assertExists()
        compose.onNodeWithTag("info-overlay").assertIsOff().performClick()
        assertTrue(overlay)
        compose.onNodeWithTag("info-overlay").assertIsOn()
        compose.onNodeWithTag("info-copy").performClick()
        compose.onNodeWithText("Kopyalandı").assertExists()
        val clipboard = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
        assertEquals("rapor", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun theOverlaySaysTheBitrateAndTheLostFrames() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                StatsOverlay(StatsSample(61.8f, 103_216, 2))
            }
        }
        compose.onNodeWithText("Bit hızı").assertExists()
        compose.onNodeWithText("61,8 Mbps").assertExists()
        compose.onNodeWithText("2 / 103.216").assertExists()
        assertFalse(overlay)
    }
}
