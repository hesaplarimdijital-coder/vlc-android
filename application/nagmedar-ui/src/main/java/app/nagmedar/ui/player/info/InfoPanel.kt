package app.nagmedar.ui.player.info

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.player.ChapterMark
import app.nagmedar.ui.player.FramePreviews
import app.nagmedar.ui.player.navigate.ChaptersSection
import app.nagmedar.ui.player.panel.PanelCaption
import app.nagmedar.ui.player.panel.PanelDisabledAlpha
import app.nagmedar.ui.player.panel.PanelRule
import app.nagmedar.ui.player.panel.PanelSection
import app.nagmedar.ui.player.panel.PendingNote
import app.nagmedar.ui.player.panel.SectionTitle
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.PlexMono
import kotlinx.coroutines.delay

/**
 * The Info tab of the player's panel (S-605), after the mockups' "Panel · Info": the file, its
 * streams with their codecs, profiles and HDR metadata, how it is decoded and output, the last
 * minute live (the input bitrate's graph, the pictures shown and lost) and the chapters (S-608),
 * with "Show over the video" and "Copy the report" at its foot.
 */
@Composable
fun InfoPanel(
    file: FileFacts?,
    streams: List<StreamRow>,
    decoding: List<InfoRow>,
    live: LiveStats,
    overlay: Boolean,
    onOverlay: (Boolean) -> Unit,
    report: () -> String,
    modifier: Modifier = Modifier,
    /** Under the video in a wide window: two columns of sections. */
    columns: Int = 1,
    /** What the native side cannot tell yet (the video output, the audio decoder, the buffer) shows faded, with a note. */
    pending: Boolean = true,
    /** S-608: the film's chapters, the one playing at [nowMs] lit; a click goes to one ([onChapter]). */
    chapters: List<ChapterMark> = emptyList(),
    nowMs: Long = 0L,
    previews: FramePreviews? = null,
    onChapter: (Long) -> Unit = {},
) {
    Column(modifier.fillMaxSize().testTag("info-panel")) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            val about = @Composable {
                if (file != null) FileHeader(file) else PanelCaption(stringResource(R.string.nag_info_no_media))
                if (streams.isNotEmpty()) StreamsSection(streams)
                DecodingSection(decoding)
            }
            if (columns >= 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(13.dp)) { about() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        LiveSection(live, pending)
                        ChaptersSection(chapters, nowMs, previews, onChapter)
                    }
                }
            } else {
                about()
                PanelRule()
                LiveSection(live, pending)
                ChaptersSection(chapters, nowMs, previews, onChapter)
            }
            // once, under everything: which lines wait for the native side
            if (pending) PendingNote("info-pending", stringResource(R.string.nag_info_pending))
        }
        Footer(overlay, onOverlay, report)
    }
}

@Composable
private fun FileHeader(file: FileFacts) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            file.name,
            style = NagTheme.type.label.copy(fontFamily = PlexMono),
            color = NagTheme.colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("info-file"),
        )
        if (file.details.isNotEmpty()) PanelCaption(file.details)
    }
}

@Composable
private fun StreamsSection(streams: List<StreamRow>) {
    PanelSection(spacing = 4.dp) {
        SectionTitle(stringResource(R.string.nag_info_streams))
        streams.forEach { StreamLine(it) }
    }
}

/** A stream: its mark ("V", "A1", "S"), what it is and its details, and a tag ("hardware", "playing"). */
@Composable
private fun StreamLine(row: StreamRow) {
    val colors = NagTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (row.current) colors.accent.copy(alpha = 0.09f) else Color.Transparent, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("info-stream"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .widthIn(min = 24.dp)
                .background(colors.surfaceRaised, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(row.mark, style = NagTheme.type.mono, color = colors.textSecondary, maxLines = 1)
        }
        Column(Modifier.weight(1f)) {
            Text(row.title, style = NagTheme.type.label, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (row.meta.isNotEmpty()) Text(row.meta, style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        row.tag?.let { tag ->
            val (background, foreground) = when (row.tone) {
                TagTone.Accent -> colors.accent.copy(alpha = 0.2f) to colors.text
                TagTone.Good -> colors.success.copy(alpha = 0.14f) to colors.success
                TagTone.Warn -> colors.warning.copy(alpha = 0.14f) to colors.warning
            }
            Text(
                tag,
                style = NagTheme.type.labelSmall,
                color = foreground,
                maxLines = 1,
                modifier = Modifier.background(background, RoundedCornerShape(9.dp)).padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
    }
}

/** The decoders and outputs, as the mockup lists them; what the native side cannot tell yet is faded. */
@Composable
private fun DecodingSection(rows: List<InfoRow>) {
    PanelSection(spacing = 4.dp) {
        SectionTitle(stringResource(R.string.nag_info_decoding))
        rows.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 20.dp)
                    .alpha(if (row.value != null) 1f else PanelDisabledAlpha),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(row.label, style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary, maxLines = 1, modifier = Modifier.width(96.dp))
                Text(row.value ?: "—", style = NagTheme.type.labelSmall, color = NagTheme.colors.text, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** A sample a second: the input bitrate and its last minute; the buffer and the pictures shown and lost side by side. */
@Composable
private fun LiveSection(live: LiveStats, pending: Boolean) {
    val colors = NagTheme.colors
    val locale = currentLocale()
    val last = live.last
    PanelSection(spacing = 8.dp) {
        SectionTitle(stringResource(R.string.nag_info_live)) {
            Box(Modifier.size(6.dp).background(if (last != null) colors.success else colors.textTertiary, CircleShape))
            PanelCaption(stringResource(R.string.nag_info_every_second), Modifier.padding(start = 6.dp))
        }
        Card {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nag_info_input_bitrate), style = NagTheme.type.labelSmall, color = colors.textSecondary, modifier = Modifier.weight(1f))
                Text(
                    last?.let { mbpsLabel(it.inputMbps, locale) } ?: "—",
                    style = NagTheme.type.mono,
                    color = colors.text,
                    modifier = Modifier.testTag("info-bitrate"),
                )
            }
            Sparkline(live.history, Modifier.fillMaxWidth().height(40.dp).padding(vertical = 4.dp))
            Row(Modifier.fillMaxWidth()) {
                PanelCaption(stringResource(R.string.nag_info_graph_start), Modifier.weight(1f))
                PanelCaption(stringResource(R.string.nag_info_graph_now))
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // the buffer: the native side's (S-504), faded until then
            if (pending) {
                Card(Modifier.weight(1f).fillMaxHeight().alpha(PanelDisabledAlpha)) {
                    Text(stringResource(R.string.nag_info_buffer), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                    Text("—", style = NagTheme.type.title.copy(fontFamily = PlexMono), color = colors.textTertiary)
                }
            }
            Card(Modifier.weight(1f).fillMaxHeight()) {
                Text(stringResource(R.string.nag_info_frames), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                val lost = last?.lost ?: 0
                Text(
                    stringResource(R.string.nag_info_frames_lost, countLabel(lost, locale)),
                    style = NagTheme.type.title.copy(fontFamily = PlexMono),
                    color = if (last == null) colors.textTertiary else if (lost == 0L) colors.success else colors.warning,
                    maxLines = 1,
                    modifier = Modifier.testTag("info-lost"),
                )
                PanelCaption(stringResource(R.string.nag_info_frames_shown, countLabel(last?.displayed ?: 0, locale)))
            }
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier.fillMaxWidth(), content: @Composable () -> Unit) {
    Column(
        modifier
            .border(1.dp, NagTheme.colors.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) { content() }
}

/** The last minute of the input bitrate: a line over a light fill, scaled to its highest second. */
@Composable
private fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    val line = NagTheme.colors.accent
    val baseline = NagTheme.colors.border
    Canvas(modifier.testTag("info-graph")) {
        drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        if (values.size < 2) return@Canvas
        val top = values.max().coerceAtLeast(1f) * 1.15f
        val step = size.width / (CAPACITY - 1)
        val start = size.width - step * (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = start + step * index
            val y = size.height - (value / top) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(start, size.height)
            close()
        }
        drawPath(fill, line.copy(alpha = 0.12f))
        drawPath(path, line, style = Stroke(width = 1.5.dp.toPx()))
    }
}

/** "Show over the video" and "Copy the report", which says so a moment. */
@Composable
private fun Footer(overlay: Boolean, onOverlay: (Boolean) -> Unit, report: () -> String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NagCheckbox(overlay, onOverlay, stringResource(R.string.nag_info_overlay), Modifier.weight(1f).testTag("info-overlay"))
        NagButton(
            stringResource(if (copied) R.string.nag_info_copied else R.string.nag_info_copy_report),
            onClick = {
                clipboard.setText(AnnotatedString(report()))
                copied = true
            },
            icon = if (copied) NagIcons.Check else null,
            modifier = Modifier.testTag("info-copy"),
        )
    }
}

/**
 * The statistics over the video (S-605's overlay), at its top left under the title, as the mockup
 * draws it: a small dark card with the input bitrate and the pictures lost of those shown, every
 * second; none lost in green, some in amber.
 */
@Composable
fun StatsOverlay(stats: StatsSample?, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier
            .width(236.dp)
            .background(OverlayBackground, shape)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .testTag("stats-overlay"),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        OverlayLine(stringResource(R.string.nag_info_overlay_bitrate), stats?.let { mbpsLabel(it.inputMbps, locale) } ?: "—", colors.text)
        OverlayLine(
            stringResource(R.string.nag_info_overlay_lost),
            stats?.let { countLabel(it.lost, locale) + " / " + countLabel(it.displayed, locale) } ?: "—",
            when {
                stats == null -> colors.text
                stats.lost > 0 -> colors.warning
                else -> colors.success
            },
        )
    }
}

@Composable
private fun OverlayLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = NagTheme.type.mono, color = NagTheme.colors.textTertiary, maxLines = 1, modifier = Modifier.weight(1f))
        Text(value, style = NagTheme.type.mono, color = color, maxLines = 1)
    }
}

/** The mockup's card over the video: almost black, a little see-through. */
private val OverlayBackground = Color(0xC70C0F12)

private const val CAPACITY = 60
