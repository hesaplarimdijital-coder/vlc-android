package app.nagmedar.ui.tvlab

import androidx.compose.runtime.Immutable
import app.nagmedar.lab.BitrateAnalysis
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The bitrate chart's numbers (S-1202), without pixels: every second's kilobits ([peak], the
 * 1 s line) and their 5-second mean ([average]), the profile's [threshold] and, from a USB 2.0
 * port, the port's own limit ([usb2]) when it is another line; the axes' top and ticks, and the
 * seconds marked: the highest peaks over the threshold.
 */
@Immutable
class ChartModel(
    val peak: IntArray,
    val average: IntArray,
    /** kbps; 0 for none. */
    val threshold: Int,
    val usb2: Int?,
    val top: Int,
    /** kbps. */
    val yTicks: List<Int>,
    /** Seconds. */
    val xTicks: List<Int>,
    /** Seconds over the threshold to mark, the highest first. */
    val marks: List<Int>,
) {
    val seconds: Int get() = peak.size

    /** The highest second. */
    val highest: Int get() = peak.indices.maxByOrNull { peak[it] } ?: 0

    /** Whether the ticks read as hours ("1:30") rather than minutes ("12:30"). */
    val hours: Boolean get() = seconds >= 3600

    companion object {
        /** The model of [analysis] against a profile whose threshold is [thresholdKbps] (0: none), fed over [link]. */
        fun of(analysis: BitrateAnalysis, thresholdKbps: Int, link: SourceLink): ChartModel {
            val peak = analysis.total
            val average = movingAverage(peak, 5)
            val usb2 = SourceLink.Usb2.kbps?.takeIf { link == SourceLink.Usb2 && it != thresholdKbps }
            val highest = peak.maxOrNull() ?: 0
            // a little room over the highest line, then round ticks: five or six of them
            val raw = max(highest, thresholdKbps).coerceAtLeast(1) * 1.04
            val step = niceStep(raw / 5)
            val top = (ceil(raw / step) * step).toInt()
            val yTicks = generateSequence(0.0) { it + step }.takeWhile { it <= top + 0.5 }.map { it.roundToInt() }.toList()
            // the USB 2.0 line shows when the film comes near it
            val usb2Shown = usb2?.takeIf { it <= top }
            return ChartModel(
                peak = peak,
                average = average,
                threshold = thresholdKbps,
                usb2 = usb2Shown,
                top = top,
                yTicks = yTicks,
                xTicks = timeTicks(peak.size),
                marks = marks(peak, thresholdKbps),
            )
        }

        /** The mean of each second and the [window] - 1 before it. */
        fun movingAverage(series: IntArray, window: Int): IntArray {
            var sum = 0L
            return IntArray(series.size) { i ->
                sum += series[i]
                if (i >= window) sum -= series[i - window]
                (sum / minOf(i + 1, window)).toInt()
            }
        }

        /** 1, 2, 2.5 or 5 times a power of ten, at least [raw]. */
        fun niceStep(raw: Double): Double {
            if (raw <= 0) return 1.0
            val base = 10.0.pow(floor(log10(raw)))
            val fraction = raw / base
            val nice = when {
                fraction <= 1 -> 1.0
                fraction <= 2 -> 2.0
                fraction <= 2.5 -> 2.5
                fraction <= 5 -> 5.0
                else -> 10.0
            }
            return nice * base
        }

        /** Ticks of the time axis: at most six, on round seconds, minutes or hours. */
        fun timeTicks(seconds: Int): List<Int> {
            if (seconds <= 1) return listOf(0)
            val step = TIME_STEPS.firstOrNull { (seconds - 1) / it <= 5 } ?: ((seconds - 1) / 5 / 3600 + 1) * 3600
            return (0 until seconds step step).toList()
        }

        /** The highest seconds over [threshold], at most three and apart from each other; none without a threshold. */
        fun marks(series: IntArray, threshold: Int): List<Int> {
            if (series.isEmpty() || threshold <= 0) return emptyList()
            val apart = max(series.size / 12, 1)
            val chosen = mutableListOf<Int>()
            for (second in series.indices.filter { series[it] > threshold }.sortedByDescending { series[it] }) {
                if (chosen.all { abs(it - second) >= apart }) chosen += second
                if (chosen.size == 3) break
            }
            return chosen
        }

        private val TIME_STEPS = listOf(1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600)
    }
}

/** A tick of the time axis: "1:30" in a film of hours, "12:30" in one of minutes. */
fun tickLabel(second: Int, hours: Boolean): String =
    if (hours) "%d:%02d".format(Locale.ROOT, second / 3600, second % 3600 / 60) else "%d:%02d".format(Locale.ROOT, second / 60, second % 60)

/** The analysis as CSV (S-1202): a second a row, the total and the video in kbps, and the 5 s mean. */
fun chartCsv(analysis: BitrateAnalysis): String = buildString {
    append("second,total_kbps,video_kbps,average_5s_kbps\n")
    val average = ChartModel.movingAverage(analysis.total, 5)
    analysis.total.forEachIndexed { second, kbps ->
        append(second).append(',').append(kbps).append(',').append(analysis.video.getOrElse(second) { 0 }).append(',').append(average[second]).append('\n')
    }
}
