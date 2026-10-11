package app.nagmedar.ui.library

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import app.nagmedar.lab.MediaTech
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.theme.NagTheme
import java.io.File
import java.util.Locale

/** Whether a probe result can still come: a probe is queued or running for the item. */
enum class TechState { Ready, Probing, Unavailable }

/** "2 sa 18 dk", "48 dk". */
@Composable
fun durationWords(millis: Long): String {
    val minutes = (millis / 60_000).toInt()
    return if (minutes >= 60) {
        stringResource(R.string.nag_duration_hours_minutes, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.nag_duration_minutes, minutes)
    }
}

/** One label and value of the technical facts, the value in monospace. */
class TechRow(val label: String, val value: String)

/** The technical facts of [tech] as the panel lists them (S-802). */
@Composable
fun techRows(tech: MediaTech, key: String): List<TechRow> {
    val locale = currentLocale()
    val commentary = stringResource(R.string.nag_tech_commentary)
    val rows = mutableListOf<TechRow>()
    tech.video?.let { video ->
        rows += TechRow(stringResource(R.string.nag_tech_video), videoLine(video))
        if (video.fps > 0) rows += TechRow(stringResource(R.string.nag_tech_frame_rate), frameRateLabel(video.fps, locale))
        rows += TechRow(stringResource(R.string.nag_tech_range), rangeLine(video, locale))
    }
    tech.audio.forEachIndexed { index, track ->
        rows += TechRow(stringResource(R.string.nag_tech_audio, index + 1), audioLine(track, locale, commentary))
    }
    if (tech.subtitles.isNotEmpty()) rows += TechRow(stringResource(R.string.nag_tech_subtitles), subtitleLine(tech.subtitles, locale))
    if (tech.bitrate > 0) {
        rows += TechRow(stringResource(R.string.nag_tech_bitrate), stringResource(R.string.nag_tech_average, bitrateLabel(tech.bitrate, locale)))
    }
    if (tech.sizeBytes > 0) {
        rows += TechRow(stringResource(R.string.nag_tech_file), listOf(sizeLabel(tech.sizeBytes, locale), tech.container).filter { it.isNotEmpty() }.joinToString(" · "))
    }
    storageName(key)?.let { rows += TechRow(stringResource(R.string.nag_tech_location), it) }
    return rows
}

/** The name of the drive a file is on ("SAMSUNG T7", "Internal shared storage"). */
@Composable
private fun storageName(key: String): String? {
    val context = LocalContext.current
    return remember(key) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) volumeName(context, key) else null }
}

@RequiresApi(Build.VERSION_CODES.N)
private fun volumeName(context: Context, key: String): String? = runCatching {
    val path = Uri.parse(key).takeIf { it.scheme == null || it.scheme == "file" }?.path ?: return null
    context.getSystemService(StorageManager::class.java)?.getStorageVolume(File(path))?.getDescription(context)
}.getOrNull()

/** The interface's language, for numbers and language names. */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()

/**
 * The details of a video (S-802; S-303 places it among the panes): artwork, title, where it was
 * left, play, and the technical facts from the probe. [state] says what to show while there is
 * no probe yet.
 */
@Composable
fun MediaDetailsPanel(
    item: VideoItem,
    tech: MediaTech?,
    state: TechState,
    onPlay: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val title = stringResource(R.string.nag_details_title)
    Column(
        modifier
            .fillMaxHeight()
            .background(colors.chrome)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .semantics { paneTitle = title }
            .testTag("details"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(title, Modifier.weight(1f))
            NagIconButton(NagIcons.Close, stringResource(R.string.nag_details_close), onClose)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(dimens.radiusControl))) {
                Artwork(item.media, size = 320.dp, modifier = Modifier.fillMaxSize(), corner = 0.dp)
            }
            Spacer(Modifier.height(4.dp))
            Text(item.title, style = NagTheme.type.headline, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() })
            val summary = listOfNotNull(
                item.durationMs.takeIf { it > 0 }?.let { durationWords(it) },
                tech?.let(::techSummary)?.takeIf { it.isNotEmpty() },
            ).joinToString(" · ")
            if (summary.isNotEmpty()) Text(summary, style = NagTheme.type.body, color = colors.textSecondary)
            if (item.progress > 0f) {
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(stringResource(R.string.nag_details_resume_at), style = NagTheme.type.labelSmall, color = colors.textSecondary,
                        modifier = Modifier.weight(1f))
                    Text(formatDuration(item.resumeMs), style = NagTheme.type.mono, color = colors.textSecondary)
                }
                NagProgressBar(item.progress, label = stringResource(R.string.nag_details_resume_at))
            }
            Spacer(Modifier.height(6.dp))
            NagButton(
                stringResource(if (item.progress > 0f) R.string.nag_details_resume else R.string.nag_play),
                onPlay,
                icon = NagIcons.Play,
                primary = true,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(stringResource(R.string.nag_tech_title))
            when {
                tech != null -> techRows(tech, item.key).forEach { row -> TechLine(row) }
                state == TechState.Probing -> Text(stringResource(R.string.nag_tech_probing), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                else -> {
                    // what the medialibrary knows, and why there is no more
                    resolutionOf(item.width, item.height)?.let { TechLine(TechRow(stringResource(R.string.nag_tech_video), "${item.width}×${item.height}")) }
                    Text(stringResource(R.string.nag_tech_unavailable), style = NagTheme.type.labelSmall, color = colors.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(currentLocale()),
        style = NagTheme.type.caption,
        color = NagTheme.colors.textTertiary,
        modifier = modifier.semantics { heading() },
    )
}

/** "Video   HEVC Main 10 · 3840×2160": read as one line by screen readers. */
@Composable
private fun TechLine(row: TechRow) {
    val colors = NagTheme.colors
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "${row.label}: ${row.value}" }) {
        Text(row.label, style = NagTheme.type.labelSmall, color = colors.textTertiary, modifier = Modifier.width(72.dp))
        Text(row.value, style = NagTheme.type.mono, color = colors.text, modifier = Modifier.weight(1f))
    }
}

/** The badge in a card's corner: light text on a dark glass, readable on any artwork. */
@Composable
fun TechBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = NagTheme.type.mono,
        color = BadgeText,
        maxLines = 1,
        modifier = modifier
            .background(BadgeGlass, RoundedCornerShape(4.dp))
            .border(1.dp, BadgeEdge, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private val BadgeGlass = androidx.compose.ui.graphics.Color(0xE6090B0E)
private val BadgeEdge = androidx.compose.ui.graphics.Color(0x1FFFFFFF)
private val BadgeText = androidx.compose.ui.graphics.Color(0xFFE6E9ED)
