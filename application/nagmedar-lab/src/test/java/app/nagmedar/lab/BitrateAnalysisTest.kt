package app.nagmedar.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-1202: [BitrateAnalysis] reads the JSON of native/lab/bitrate.c. The sample is a real output
 * of the host build (FFmpeg 6.1) on a 3-second H.264 + AAC Matroska file: a renamed key on
 * either side shows up here.
 */
class BitrateAnalysisTest {

    private val sample = """{"version":1,"error":"","complete":true,"duration_ms":3023,"size":689293,"seconds":4,
        "total":[973,1769,1855,899],"video":[937,1700,1786,863],"average_kbps":1374,"peak_1s_kbps":1855,"peak_5s_kbps":1374,
        "video_average_kbps":1322,"video_peak_1s_kbps":1786,"video_peak_5s_kbps":1322,
        "streams":[{"index":0,"type":"video","codec":"h264","bytes":660757,"average_kbps":1322,"peak_1s_kbps":1786},
        {"index":1,"type":"audio","codec":"aac","bytes":26302,"average_kbps":53,"peak_1s_kbps":69}]}"""

    @Test
    fun theAnalysisIsRead() {
        val analysis = BitrateAnalysis.parse(sample)!!
        assertTrue(analysis.complete)
        assertEquals(4, analysis.seconds)
        assertEquals(listOf(973, 1769, 1855, 899), analysis.total.toList())
        assertEquals(listOf(937, 1700, 1786, 863), analysis.video.toList())
        assertEquals(1855, analysis.peakSecondKbps)
        assertEquals(1374, analysis.peakFiveSecondsKbps)
        assertEquals(1786, analysis.videoPeakSecondKbps)
        assertEquals(689_293L, analysis.sizeBytes)
        assertEquals(listOf("video" to "h264", "audio" to "aac"), analysis.streams.map { it.type to it.codec })
        assertEquals(660_757L, analysis.streams[0].bytes)
    }

    @Test
    fun peaksAndThresholds() {
        val analysis = BitrateAnalysis.parse(sample)!!
        // the native five-second peak of a four-second file is its mean
        assertEquals(analysis.peakFiveSecondsKbps, analysis.peakOf(analysis.total, 5))
        assertEquals(1812, analysis.peakOf(analysis.total, 2))
        assertEquals(1855, analysis.peakOf(analysis.total, 1))
        assertEquals(0, analysis.peakOf(IntArray(0), 5))
        assertEquals(2, analysis.secondsOver(1000))
        assertEquals(0, analysis.secondsOver(BitrateAnalysis.USB2_KBPS))
    }

    @Test
    fun errorsAndOtherVersionsAreNoAnalysis() {
        val missing = """{"version":1,"error":"open: No such file or directory"}"""
        assertNull(BitrateAnalysis.parse(missing))
        assertEquals("open: No such file or directory", BitrateAnalysis.error(missing))
        assertNull(BitrateAnalysis.error(sample))
        assertNull(BitrateAnalysis.parse(sample.replace("\"version\":1", "\"version\":2")))
        assertNull(BitrateAnalysis.parse("not json"))
        val stopped = BitrateAnalysis.parse(sample.replace("\"complete\":true", "\"complete\":false"))!!
        assertFalse(stopped.complete)
    }
}
