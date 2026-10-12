package app.nagmedar.ui.perf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * S-1404: the records kept (the newest ten of each, the newest first, what cannot be read left
 * out), the log's lines the macrobenchmarks read, and the card's labels and report. Robolectric
 * for org.json and the preferences.
 */
@RunWith(AndroidJUnit4::class)
class PerfRecordsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun store(name: String) = PrefsPerfStore(context.getSharedPreferences(name, Context.MODE_PRIVATE))

    @Test
    fun recordsComeBackAsTheyWent() {
        assertEquals(samplePlaybacks, PerfCodec.playbacks(PerfCodec.encodePlaybacks(samplePlaybacks)))
        assertEquals(sampleStartups, PerfCodec.startups(PerfCodec.encodeStartups(sampleStartups)))
        // a hand-broken entry is left, the others stay
        val broken = """[{"at":1,"ms":640},{"at":"x"},7,{"at":2,"ms":702}]"""
        assertEquals(listOf(StartupRecord(1, 640), StartupRecord(2, 702)), PerfCodec.startups(broken))
        assertEquals(emptyList<StartupRecord>(), PerfCodec.startups("not json"))
        assertEquals(emptyList<PlaybackRecord>(), PerfCodec.playbacks(null))
    }

    @Test
    fun theNewestTenAreKeptFirst() {
        val store = store("perf-ten")
        repeat(12) { store.add(StartupRecord(it.toLong(), 600L + it)) }
        assertEquals((11 downTo 2).map { StartupRecord(it.toLong(), 600L + it) }, store.startups())
        samplePlaybacks.reversed().forEach(store::add)
        assertEquals(samplePlaybacks, store.playbacks())
        var told = 0
        val stop = store.listen { told++ }
        store.keep(StartupRecord(99, 640))
        assertEquals(1, told)
        // both lists go: told for each
        store.clear()
        assertEquals(3, told)
        stop()
        store.add(StartupRecord(100, 640))
        assertEquals(3, told)
        assertEquals(listOf(StartupRecord(100, 640)), store.startups())
        assertEquals(emptyList<PlaybackRecord>(), store.playbacks())
    }

    @Test
    fun theLogsLines() {
        assertEquals("startup cold_ms=640", PerfLog.line(StartupRecord(SAMPLE_TIME, 640)))
        assertEquals(
            "playback file=\"stress_2160p23976_hevc10_100mbps.mkv\" width=3840 height=2160 range=HDR10 seconds=15 " +
                "shown=352 dropped=8 dropped_pct=2.222 audio_lost=2 battery_pct_h=-",
            PerfLog.line(samplePlaybacks[2]),
        )
        assertEquals(
            "playback file=\"Kuzey_Isigi_2160p_UHD_Remux.mkv\" width=3840 height=2160 range=DV_P8 seconds=6735 " +
                "shown=161480 dropped=12 dropped_pct=0.007 audio_lost=0 battery_pct_h=9.8",
            PerfLog.line(samplePlaybacks[0]),
        )
        assertEquals("range=-", PerfLog.line(samplePlaybacks[1].copy(range = "")).split(' ').first { it.startsWith("range=") })
        assertTrue(PerfLog.line(samplePlaybacks[1].copy(name = "a \"b\".mkv")).startsWith("playback file=\"a 'b'.mkv\" "))
    }

    @Test
    fun theTargets() {
        assertTrue(PerfTargets.startupOk(1_000))
        assertTrue(!PerfTargets.startupOk(1_001))
        assertTrue(PerfTargets.playbackOk(samplePlaybacks[0]))
        assertTrue(!PerfTargets.playbackOk(samplePlaybacks[2]))
        assertEquals(640L, samplePerfView().medianStartupMs)
        assertEquals(null, PerfView(emptyList(), emptyList()).medianStartupMs)
        // of an even count, the lower middle
        assertEquals(640L, PerfView(sampleStartups.take(4), emptyList()).medianStartupMs)
    }

    @Test
    @Config(qualifiers = "tr")
    fun theLabelsInTurkish() {
        val res = context.resources
        val tr = java.util.Locale.forLanguageTag("tr-TR")
        assertEquals("0,64 sn", secondsLabel(res, 640, tr))
        assertEquals("1 sn", secondsLabel(res, 1_000, tr, 0))
        assertEquals("%0,1", perfPercent(0.1, tr))
        assertEquals("%0,007 kayıp", droppedLabel(res, samplePlaybacks[0], tr))
        assertEquals("Kayıp yok", droppedLabel(res, samplePlaybacks[1], tr))
        assertEquals("%2,22 kayıp", droppedLabel(res, samplePlaybacks[2], tr))
        assertEquals("3840×2160 · DV P8 · 1 sa 52 dk · pil saatte %9,8", playbackDetails(res, samplePlaybacks[0], tr))
        assertEquals("3840×2160 · HDR10 · 10 sn · pil ölçülmedi", playbackDetails(res, samplePlaybacks[1], tr))
        assertEquals("42 dk", playedLabel(res, 2_520))
        val report = perfReport(res, samplePerfView(), tr).lines()
        assertEquals("Nağmedar performans raporu", report[0])
        assertTrue(report[1], report[1].startsWith("Cihaz: "))
        assertTrue(report.toString(), "Soğuk açılışlar, ilk kareye kadar (hedef ≤ 1 sn):" in report)
        assertTrue(report.toString(), report.any { it.endsWith(" · 1,21 sn") })
        assertTrue(report.toString(), "Oynatmalar (hedef: kayıp kare ≤ %0,1):" in report)
        assertTrue(report.toString(), "    161.480 kare, 12 kayıp · %0,007 kayıp" in report)
    }

    @Test
    @Config(qualifiers = "en")
    fun theLabelsInEnglish() {
        val res = context.resources
        val en = java.util.Locale.UK
        assertEquals("0.64 s", secondsLabel(res, 640, en))
        assertEquals("2.22% dropped", droppedLabel(res, samplePlaybacks[2], en))
        assertEquals("1920×1080 · SDR · 42 min · battery 6.1% an hour", playbackDetails(res, samplePlaybacks[3], en))
        val report = perfReport(res, PerfView(emptyList(), emptyList()), en).lines()
        assertTrue(report.toString(), "  Not measured yet: close Nağmedar and open it again from the launcher." in report)
        assertTrue(report.toString(), "  No playbacks yet: play a film and close it, and its summary comes here." in report)
    }
}
