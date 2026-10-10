package app.nagmedar.lab

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reports read the JSON that native/lab writes. The samples below are real outputs of the
 * host build (dvconvert on a profile 7 MEL stream from dovi_tool's test assets); a renamed key
 * on either side shows up here.
 */
class ReportsTest {

    private val dv = """{"input":"/proc/self/fd/71","output":"/proc/self/fd/72","input_profile":7,"input_el_type":"MEL",
        "output_profile":8,"video_packets":260,"rpus_converted":259,"rpus_failed":0,"el_nals_dropped":795,
        "el_bytes_dropped":16583,"bytes_in":97759,"bytes_out":70998,"streams_copied":1,"streams_skipped":0,
        "media_seconds":10.360,"wall_seconds":0.005,"peak_rss_kb":5956,"first_rpu_error":"","error":""}"""

    @Test
    fun dolbyVisionReportReadsTheConverterJson() {
        val text = DvConversion.report(JSONObject(dv), "film.mkv", "film.P8.mkv", 60.0, mp4 = false)
        assertTrue(text, text.contains("Kaynak profil: P7 (MEL)"))
        assertTrue(text, text.contains("Çıktı profil: P8.1"))
        assertTrue(text, text.contains("Dönüştürülen RPU: 259, başarısız: 0"))
        assertTrue(text, text.contains("Atılan EL NAL: 795"))
        assertTrue(text, text.contains("ilk 60 sn"))
    }
}
