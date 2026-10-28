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
 * S-1404: the records kept (the newest ten, the newest first, what cannot be read left
 * out), the log's lines the macrobenchmarks read, and the card's labels and report. Robolectric
 * for org.json and the preferences.
 */
@RunWith(AndroidJUnit4::class)
class PerfRecordsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun store(name: String) = PrefsPerfStore(context.getSharedPreferences(name, Context.MODE_PRIVATE))

    @Test
    fun recordsComeBackAsTheyWent() {
        assertEquals(sampleStartups, PerfCodec.startups(PerfCodec.encodeStartups(sampleStartups)))
        // a hand-broken entry is left, the others stay
        val broken = """[{"at":1,"ms":640},{"at":"x"},7,{"at":2,"ms":702}]"""
        assertEquals(listOf(StartupRecord(1, 640), StartupRecord(2, 702)), PerfCodec.startups(broken))
        assertEquals(emptyList<StartupRecord>(), PerfCodec.startups("not json"))
    }

    @Test
    fun theNewestTenAreKeptFirst() {
        val store = store("perf-ten")
        repeat(12) { store.add(StartupRecord(it.toLong(), 600L + it)) }
        assertEquals((11 downTo 2).map { StartupRecord(it.toLong(), 600L + it) }, store.startups())
        var told = 0
        val stop = store.listen { told++ }
        store.keep(StartupRecord(99, 640))
        assertEquals(1, told)
        store.clear()
        assertEquals(2, told)
        stop()
        store.add(StartupRecord(100, 640))
        assertEquals(2, told)
        assertEquals(listOf(StartupRecord(100, 640)), store.startups())
    }

    @Test
    fun theLogsLines() {
        assertEquals("startup cold_ms=640", PerfLog.line(StartupRecord(SAMPLE_TIME, 640)))
    }

    @Test
    fun theTargets() {
        assertTrue(PerfTargets.startupOk(1_000))
        assertTrue(!PerfTargets.startupOk(1_001))
        assertEquals(640L, samplePerfView().medianStartupMs)
        assertEquals(null, PerfView(emptyList()).medianStartupMs)
        // of an even count, the lower middle
        assertEquals(640L, PerfView(sampleStartups.take(4)).medianStartupMs)
    }

    @Test
    @Config(qualifiers = "tr")
    fun theLabelsInTurkish() {
        val res = context.resources
        val tr = java.util.Locale.forLanguageTag("tr-TR")
        assertEquals("0,64 sn", secondsLabel(res, 640, tr))
        assertEquals("1 sn", secondsLabel(res, 1_000, tr, 0))
        val report = perfReport(res, samplePerfView(), tr).lines()
        assertEquals("Nağmedar performans raporu", report[0])
        assertTrue(report[1], report[1].startsWith("Cihaz: "))
        assertTrue(report.toString(), "Soğuk açılışlar, ilk kareye kadar (hedef ≤ 1 sn):" in report)
        assertTrue(report.toString(), report.any { it.endsWith(" · 1,21 sn") })
    }

    @Test
    @Config(qualifiers = "en")
    fun theLabelsInEnglish() {
        val res = context.resources
        val en = java.util.Locale.UK
        assertEquals("0.64 s", secondsLabel(res, 640, en))
        val report = perfReport(res, PerfView(emptyList()), en).lines()
        assertTrue(report.toString(), "  Not measured yet: close Nağmedar and open it again from the launcher." in report)
    }
}
