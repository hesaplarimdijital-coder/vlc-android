package app.nagmedar.ui.visual

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.nagmedar.audio.ScopeFrame
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.theme.NagColors
import app.nagmedar.ui.theme.NagTheme
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

/**
 * S-611: a visualiser drawn from [state]: the spectrum's bars with their caps, the stereo level
 * meter, or the oscilloscope. [large] (the full-screen view) adds the scales' labels. Only the
 * drawing runs at each frame: it reads [VisualizerState.version].
 */
@Composable
fun VisualizerCanvas(kind: VisualizerKind, state: VisualizerState, modifier: Modifier = Modifier, large: Boolean = false) {
    val colors = NagTheme.colors
    val measurer = rememberTextMeasurer()
    val label = NagTheme.type.labelSmall.copy(color = colors.textTertiary)
    val mono = NagTheme.type.mono.copy(color = colors.textSecondary)
    val locale = LocalContext.current.resources.locale()
    val description = stringResource(R.string.nag_vis_description, stringResource(kind.label))
    val left = stringResource(R.string.nag_vis_left)
    val right = stringResource(R.string.nag_vis_right)
    val texts = remember(locale, left, right) { ScaleTexts(locale, left, right) }
    Canvas(modifier.semantics { contentDescription = description }.testTag("visualizer-canvas")) {
        // read in the drawing: a new frame draws again without composing
        if (state.version < 0L) return@Canvas
        // a tall pane draws the scales as the full-screen view does
        val scales = large || size.height >= LARGE_FROM.toPx()
        when (kind) {
            VisualizerKind.Spectrum -> drawSpectrum(state.spectrum, colors, measurer, label, texts, scales)
            VisualizerKind.Vu -> drawMeter(state.meter, colors, measurer, label, mono, texts, scales)
            VisualizerKind.Scope -> drawScope(state.frame, colors)
        }
    }
}

/** From this height a pane draws the scales' labels. */
private val LARGE_FROM = 200.dp

/** The scales' words and numbers in the interface's language: "−12", "1 kHz", "L". */
private class ScaleTexts(val locale: Locale, val left: String, val right: String) {
    private val number = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1; minimumFractionDigits = 1 }

    fun db(value: Int): String = if (value < 0) "−" + abs(value) else value.toString()

    /** A level's reading, one decimal: "−12,3"; "−∞" for silence. */
    fun reading(db: Float): String = when {
        db <= LevelBallistics.REST_DB -> "−∞"
        db < 0f -> "−" + number.format(abs(db).toDouble())
        else -> number.format(db.toDouble())
    }

    fun hertz(hz: Int): String = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"

    /** The meter's unit, under its readings. */
    val unit = "dBFS"
}

/** The spectrum's scale: 0 dBFS at the top, [SpectrumBands.FLOOR_SHOWN] at the bottom. */
private fun DrawScope.drawSpectrum(
    spectrum: SpectrumBallistics,
    colors: NagColors,
    measurer: TextMeasurer,
    label: TextStyle,
    texts: ScaleTexts,
    large: Boolean,
) {
    val leftPad = if (large) 40.dp.toPx() else 0f
    val bottomPad = if (large) 20.dp.toPx() else 0f
    val width = size.width - leftPad
    val height = size.height - bottomPad
    if (width <= 0f || height <= 0f) return
    val floor = SpectrumBands.FLOOR_SHOWN
    fun y(db: Float) = height * (db.coerceIn(floor, 0f) / floor)
    // the grid: every 12 dB
    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
    for (db in 0 downTo floor.toInt() step 12) {
        val gy = y(db.toFloat())
        drawLine(colors.border, Offset(leftPad, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        if (large) {
            val text = measurer.measure(texts.db(db), label)
            drawText(text, topLeft = Offset(leftPad - text.size.width - 6.dp.toPx(), (gy - text.size.height / 2f).coerceIn(0f, height - text.size.height)))
        }
    }
    val count = spectrum.count
    val slot = width / count
    val gap = max(1.dp.toPx(), slot * 0.22f)
    val bar = slot - gap
    val brush = Brush.verticalGradient(listOf(colors.accent, colors.accent.copy(alpha = 0.45f)), startY = 0f, endY = height)
    val corner = CornerRadius(minOf(2.dp.toPx(), bar / 2f))
    for (i in 0 until count) {
        val x = leftPad + i * slot + gap / 2f
        val top = y(spectrum.bars[i])
        if (top < height) drawRoundRect(brush, Offset(x, top), Size(bar, height - top), corner)
        if (spectrum.caps[i] > floor) {
            val capY = y(spectrum.caps[i])
            drawLine(colors.text.copy(alpha = 0.7f), Offset(x, capY), Offset(x + bar, capY), strokeWidth = 2.dp.toPx())
        }
    }
    if (large) {
        // the frequencies under the bars, on the bands' log scale
        val span = ln(SpectrumBands.MAX_HZ / SpectrumBands.MIN_HZ)
        for (hz in FREQUENCY_MARKS) {
            val x = leftPad + width * (ln(hz / SpectrumBands.MIN_HZ) / span)
            val text = measurer.measure(texts.hertz(hz.toInt()), label)
            drawText(text, topLeft = Offset((x - text.size.width / 2f).coerceIn(leftPad, size.width - text.size.width), height + 4.dp.toPx()))
        }
    }
}

private val FREQUENCY_MARKS = floatArrayOf(50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f)

/** The meter's scale: −60 to 0 dBFS. */
private const val METER_FLOOR = -60f
private val METER_MARKS = intArrayOf(-60, -48, -36, -24, -18, -12, -6, -3, 0)

/**
 * The stereo level meter: each channel's RMS solid, its peak lighter beyond it, the held peak a
 * tick, the clip mark at the end; the colours warm from −9 dBFS to full scale; the peaks read out.
 */
private fun DrawScope.drawMeter(
    meter: LevelMeter,
    colors: NagColors,
    measurer: TextMeasurer,
    label: TextStyle,
    mono: TextStyle,
    texts: ScaleTexts,
    large: Boolean,
) {
    val labelWidth = 20.dp.toPx()
    val readingWidth = measurer.measure("−88,8", mono).size.width + 8.dp.toPx()
    val clipWidth = 10.dp.toPx()
    val left = labelWidth
    val right = size.width - readingWidth - clipWidth - 6.dp.toPx()
    val width = right - left
    if (width <= 0f) return
    val scaleHeight = if (large || size.height >= 72.dp.toPx()) 16.dp.toPx() else 0f
    val gap = 8.dp.toPx()
    val barHeight = ((size.height - scaleHeight - gap) / 2f).coerceIn(4.dp.toPx(), if (large) 56.dp.toPx() else 22.dp.toPx())
    val total = barHeight * 2 + gap + scaleHeight
    val top0 = (size.height - total) / 2f
    fun x(db: Float) = left + width * ((db.coerceIn(METER_FLOOR, 0f) - METER_FLOOR) / -METER_FLOOR)
    val warmth = Brush.horizontalGradient(
        0f to colors.accent,
        ((-9f - METER_FLOOR) / -METER_FLOOR) to colors.accent,
        ((-6f - METER_FLOOR) / -METER_FLOOR) to colors.warning,
        ((-1f - METER_FLOOR) / -METER_FLOOR) to colors.danger,
        1f to colors.danger,
        startX = left,
        endX = right,
    )
    val corner = CornerRadius(3.dp.toPx())
    listOf(meter.left to texts.left, meter.right to texts.right).forEachIndexed { row, (channel, name) ->
        val top = top0 + row * (barHeight + gap)
        val nameText = measurer.measure(name, label)
        drawText(nameText, topLeft = Offset((labelWidth - nameText.size.width) / 2f - 2.dp.toPx(), top + (barHeight - nameText.size.height) / 2f))
        drawRoundRect(colors.surfaceRaised, Offset(left, top), Size(width, barHeight), corner)
        drawRoundRect(colors.border, Offset(left, top), Size(width, barHeight), corner, style = Stroke(1.dp.toPx()))
        val peakX = x(channel.peakDb)
        if (channel.peakDb > METER_FLOOR) {
            drawRoundRect(warmth, Offset(left, top), Size(peakX - left, barHeight), corner, alpha = 0.4f)
        }
        val rmsX = x(channel.rmsDb)
        if (channel.rmsDb > METER_FLOOR) drawRoundRect(warmth, Offset(left, top), Size(rmsX - left, barHeight), corner)
        if (channel.holdDb > METER_FLOOR) {
            val holdX = x(channel.holdDb)
            drawLine(colors.text, Offset(holdX, top + 2.dp.toPx()), Offset(holdX, top + barHeight - 2.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        // the clip mark: lit for two seconds after a full-scale peak
        val clipCentre = Offset(right + 6.dp.toPx() + clipWidth / 2f, top + barHeight / 2f)
        drawCircle(if (channel.clipped) colors.danger else colors.border, radius = clipWidth / 2f, center = clipCentre)
        val reading = measurer.measure(texts.reading(channel.peakDb), mono)
        drawText(reading, topLeft = Offset(size.width - reading.size.width, top + (barHeight - reading.size.height) / 2f))
    }
    if (scaleHeight > 0f) {
        val y = top0 + barHeight * 2 + gap + 2.dp.toPx()
        // from full scale down: a mark that would touch the one drawn before it is left out
        var nextRight = Float.MAX_VALUE
        for (db in METER_MARKS.reversed()) {
            val text = measurer.measure(texts.db(db), label)
            val mx = (x(db.toFloat()) - text.size.width / 2f).coerceIn(left, right - text.size.width)
            if (mx + text.size.width + 4.dp.toPx() > nextRight) continue
            drawText(text, topLeft = Offset(mx, y))
            nextRight = mx
        }
        val unit = measurer.measure(texts.unit, label)
        drawText(unit, topLeft = Offset(size.width - unit.size.width, y))
    }
}

/** The oscilloscope: both channels from a rising zero crossing, the right under the left. */
private fun DrawScope.drawScope(frame: ScopeFrame?, colors: NagColors) {
    val middle = size.height / 2f
    drawLine(colors.border, Offset(0f, middle), Offset(size.width, middle), strokeWidth = 1.dp.toPx())
    val quarter = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
    for (level in floatArrayOf(0.5f, -0.5f)) {
        val y = middle - level * middle * SCOPE_SCALE
        drawLine(colors.border.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = quarter)
    }
    if (frame == null || frame.waveLeft.size < 2) {
        drawLine(colors.accent, Offset(0f, middle), Offset(size.width, middle), strokeWidth = 2.dp.toPx())
        return
    }
    fun trace(points: FloatArray, color: Color, width: Float) {
        val path = Path()
        val step = size.width / (points.size - 1)
        points.forEachIndexed { i, v ->
            val y = middle - v.coerceIn(-1f, 1f) * middle * SCOPE_SCALE
            if (i == 0) path.moveTo(0f, y) else path.lineTo(i * step, y)
        }
        drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round))
    }
    trace(frame.waveRight, colors.accent.copy(alpha = 0.45f), 1.5.dp.toPx())
    trace(frame.waveLeft, colors.accent, 2.dp.toPx())
}

/** Full scale reaches this share of the half height: the peaks stay inside. */
private const val SCOPE_SCALE = 0.92f
