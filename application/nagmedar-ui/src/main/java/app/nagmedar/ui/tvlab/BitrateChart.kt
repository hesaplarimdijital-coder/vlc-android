package app.nagmedar.ui.tvlab

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.PlexMono
import java.text.NumberFormat
import java.util.Locale

/** The chart's colours. */
@Immutable
data class ChartPalette(
    val background: Color,
    val text: Color,
    val grid: Color,
    val axis: Color,
    val label: Color,
    val peak: Color,
    val average: Color,
    val threshold: Color,
    val usb2: Color,
    val mark: Color,
    val markText: Color,
)

/** The chart's colours in the current theme. */
@Composable
@ReadOnlyComposable
fun chartPalette(): ChartPalette {
    val colors = NagTheme.colors
    return ChartPalette(
        background = colors.surface,
        text = colors.text,
        grid = colors.border,
        axis = colors.borderStrong,
        label = colors.textTertiary,
        peak = colors.textTertiary,
        average = colors.accent,
        threshold = colors.warning,
        usb2 = colors.textSecondary,
        mark = colors.danger,
        markText = toneColors(StateTone.Error).content,
    )
}

/** The chart's numbers: monospaced, small. */
val ChartLabelStyle = TextStyle(fontFamily = PlexMono, fontSize = 11.sp)

/** The chart as it shows in the TV Lab. */
@Composable
fun BitrateChart(model: ChartModel, description: String, measurer: TextMeasurer, modifier: Modifier = Modifier) {
    val palette = chartPalette()
    // a tablet's and DeX's numbers one sp larger, as all text there
    val style = ChartLabelStyle.copy(color = palette.label, fontSize = NagTheme.type.size(ChartLabelStyle.fontSize.value))
    Canvas(modifier.testTag("tvlab-chart").semantics { contentDescription = description }) {
        drawBitrateChart(model, palette, measurer, style)
    }
}

/**
 * Draws [model]: the grid and its numbers (Mbps), the 1 s line under the 5 s one, the
 * threshold dashed, the USB 2.0 limit dotted, the highest peaks over the threshold as dots, the
 * highest one with its number.
 */
fun DrawScope.drawBitrateChart(model: ChartModel, palette: ChartPalette, measurer: TextMeasurer, style: TextStyle) {
    val mbps = { kbps: Int -> NumberFormat.getIntegerInstance(Locale.ROOT).apply { isGroupingUsed = false }.format(kbps / 1000.0) }
    val yLabels = model.yTicks.map { measurer.measure(mbps(it), style, density = this, layoutDirection = layoutDirection) }
    val unit = measurer.measure(MBPS, style, density = this, layoutDirection = layoutDirection)
    val gutter = maxOf(yLabels.maxOfOrNull { it.size.width } ?: 0, unit.size.width) + 6.dp.toPx()
    val labelHeight = unit.size.height.toFloat()
    val left = gutter
    val right = size.width - 2.dp.toPx()
    // the unit over the top number, which is centred on its line
    val top = labelHeight * 1.5f + 4.dp.toPx()
    val bottom = size.height - labelHeight - 4.dp.toPx()
    if (right <= left || bottom <= top) return
    val seconds = model.seconds.coerceAtLeast(1)
    fun x(second: Float) = left + (right - left) * (if (seconds > 1) second / (seconds - 1) else 0f)
    fun y(kbps: Int) = bottom - (bottom - top) * (kbps.toFloat() / model.top.coerceAtLeast(1))

    // the grid and its numbers, the unit over them
    drawText(unit, topLeft = Offset(left - unit.size.width - 6.dp.toPx(), 0f))
    model.yTicks.forEachIndexed { index, tick ->
        val at = y(tick)
        drawLine(if (tick == 0) palette.axis else palette.grid, Offset(left, at), Offset(right, at), 1.dp.toPx())
        val label = yLabels[index]
        drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), at - label.size.height / 2f))
    }
    model.xTicks.forEachIndexed { index, tick ->
        val label = measurer.measure(tickLabel(tick, model.hours), style, density = this, layoutDirection = layoutDirection)
        val at = x(tick.toFloat())
        val start = when (index) {
            0 -> at
            else -> (at - label.size.width / 2f).coerceAtMost(right - label.size.width)
        }
        drawText(label, topLeft = Offset(start, bottom + 3.dp.toPx()))
    }

    // the lines, thinned to about one point every two pixels
    val buckets = ((right - left) / 2f).toInt().coerceIn(2, 4000)
    drawPath(seriesPath(model.peak, buckets, ::x, ::y, highest = true), palette.peak, style = Stroke(width = 1.dp.toPx()))
    drawPath(seriesPath(model.average, buckets, ::x, ::y, highest = false), palette.average, style = Stroke(width = 1.8.dp.toPx()))

    model.usb2?.let { kbps ->
        val at = y(kbps)
        drawLine(palette.usb2, Offset(left, at), Offset(right, at), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))
        val label = measurer.measure(USB2, style.copy(color = palette.usb2), density = this, layoutDirection = layoutDirection)
        drawText(label, topLeft = Offset(right - label.size.width, at - label.size.height - 1.dp.toPx()))
    }
    if (model.threshold > 0 && model.threshold <= model.top) {
        val at = y(model.threshold)
        drawLine(
            palette.threshold,
            Offset(left, at),
            Offset(right, at),
            1.2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
    }
    model.marks.forEach { second ->
        val center = Offset(x(second.toFloat()), y(model.peak[second]))
        drawCircle(palette.background, radius = 5.dp.toPx(), center = center)
        drawCircle(palette.mark, radius = 3.5.dp.toPx(), center = center)
    }
    model.marks.firstOrNull()?.let { second ->
        val label = measurer.measure(mbps(model.peak[second]), style.copy(color = palette.markText), density = this, layoutDirection = layoutDirection)
        val center = x(second.toFloat())
        val start = (center - label.size.width / 2f).coerceIn(left, right - label.size.width)
        drawText(label, topLeft = Offset(start, (y(model.peak[second]) - label.size.height - 5.dp.toPx()).coerceAtLeast(0f)))
    }
}

/**
 * [series] as a line of at most [buckets] points: each point the highest second of its bucket
 * ([highest], so that no peak is lost) or their mean.
 */
private fun seriesPath(series: IntArray, buckets: Int, x: (Float) -> Float, y: (Int) -> Float, highest: Boolean): Path {
    val path = Path()
    if (series.isEmpty()) return path
    if (series.size <= buckets) {
        series.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(0f), y(v)) else path.lineTo(x(i.toFloat()), y(v)) }
        if (series.size == 1) path.lineTo(x(1f), y(series[0]))
        return path
    }
    val per = series.size.toFloat() / buckets
    for (b in 0 until buckets) {
        val from = (b * per).toInt()
        val to = ((b + 1) * per).toInt().coerceAtMost(series.size).coerceAtLeast(from + 1)
        var value = 0L
        for (i in from until to) value = if (highest) maxOf(value, series[i].toLong()) else value + series[i]
        if (!highest) value /= (to - from)
        val at = x((from + to - 1) / 2f)
        if (b == 0) path.moveTo(at, y(value.toInt())) else path.lineTo(at, y(value.toInt()))
    }
    return path
}

/**
 * The chart as a picture to save (S-1202): [width] × [height] pixels at [density], on the
 * palette's background, [title] above it.
 */
fun renderChart(
    model: ChartModel,
    palette: ChartPalette,
    measurer: TextMeasurer,
    title: String,
    width: Int = 1600,
    height: Int = 640,
    density: Density = Density(2f),
): ImageBitmap {
    val image = ImageBitmap(width, height)
    val canvas = androidx.compose.ui.graphics.Canvas(image)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) {
        drawRect(palette.background)
        val pad = 16.dp.toPx()
        val heading = measurer.measure(title, ChartLabelStyle.copy(color = palette.text, fontSize = 14.sp), density = this, layoutDirection = layoutDirection)
        drawText(heading, topLeft = Offset(pad, pad))
        val top = pad + heading.size.height + 8.dp.toPx()
        inset(pad, top, pad, pad) {
            drawBitrateChart(model, palette, measurer, ChartLabelStyle.copy(color = palette.label))
        }
    }
    return image
}

private const val MBPS = "Mbps"
private const val USB2 = "USB 2.0"
