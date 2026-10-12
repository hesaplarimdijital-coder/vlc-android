package app.nagmedar.ui.tvlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1202: what the bitrate chart draws, and the CSV it saves. */
class ChartModelTest {

    @Test
    fun theFiveSecondMean() {
        assertEquals(listOf(10, 15, 20, 25, 30, 40), ChartModel.movingAverage(intArrayOf(10, 20, 30, 40, 50, 60), 5).toList())
        assertEquals(emptyList<Int>(), ChartModel.movingAverage(IntArray(0), 5).toList())
    }

    @Test
    fun roundSteps() {
        assertEquals(1.0, ChartModel.niceStep(0.7), 0.0)
        assertEquals(25_000.0, ChartModel.niceStep(22_000.0), 0.0)
        assertEquals(50_000.0, ChartModel.niceStep(31_860.0), 0.0)
        assertEquals(100.0, ChartModel.niceStep(64.0), 0.0)
    }

    @Test
    fun timeTicks() {
        // a film of two hours: every half hour, as the mockup
        val film = ChartModel.timeTicks(7_905)
        assertEquals(listOf(0, 1800, 3600, 5400, 7200), film)
        assertEquals(listOf("0:00", "0:30", "1:00", "1:30", "2:00"), film.map { tickLabel(it, hours = true) })
        // four seconds: every second
        assertEquals(listOf(0, 1, 2, 3), ChartModel.timeTicks(4))
        assertEquals("0:03", tickLabel(3, hours = false))
        assertEquals(listOf(0, 120, 240, 360, 480), ChartModel.timeTicks(600))
        assertEquals(listOf(0), ChartModel.timeTicks(1))
    }

    @Test
    fun theSampleFilmsChart() {
        val analysis = sampleBitrate()
        val model = ChartModel.of(analysis, 100_000, SourceLink.Usb2)
        // up to 118 Mbps: an axis to 125 by 25
        assertEquals(125_000, model.top)
        assertEquals(listOf(0, 25_000, 50_000, 75_000, 100_000, 125_000), model.yTicks)
        // the three seconds over the threshold, the highest first
        assertEquals(listOf(118_000, 109_000, 104_000), model.marks.map { analysis.total[it] })
        assertEquals(model.marks.first(), model.highest)
        assertTrue(model.hours)
        // USB 2.0's 240 Mbps is far over the film: not drawn
        assertNull(model.usb2)
        // near it, it is
        assertEquals(240_000, ChartModel.of(analysis.copy(total = intArrayOf(100_000, 230_000)), 100_000, SourceLink.Usb2).usb2)
        // another link has no such line; no threshold, no marks
        assertNull(ChartModel.of(analysis.copy(total = intArrayOf(100_000, 230_000)), 100_000, SourceLink.Usb3).usb2)
        assertEquals(emptyList<Int>(), ChartModel.of(analysis, 0, SourceLink.Local).marks)
    }

    @Test
    fun marksAreApart() {
        // the two highest seconds are neighbours: one mark for them
        val series = IntArray(120) { 50 }.also {
            it[10] = 130
            it[11] = 128
            it[90] = 120
        }
        assertEquals(listOf(10, 90), ChartModel.marks(series, 100))
    }

    @Test
    fun theCsv() {
        val analysis = sampleBitrate(3).copy(total = intArrayOf(973, 1769, 1855), video = intArrayOf(937, 1700, 1786))
        assertEquals(
            listOf("second,total_kbps,video_kbps,average_5s_kbps", "0,973,937,973", "1,1769,1700,1371", "2,1855,1786,1532"),
            chartCsv(analysis).trimEnd().lines(),
        )
    }
}
