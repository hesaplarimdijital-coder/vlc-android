package app.nagmedar.ui.library

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import app.nagmedar.lab.MediaTech
import app.nagmedar.ui.R
import app.nagmedar.ui.theme.NagTheme
import java.io.File
import java.util.Locale

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
internal fun storageName(key: String): String? {
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

/** The badge in a card's corner: light text on a dark glass, readable on any artwork. */
@Composable
fun TechBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = NagTheme.type.mono,
        color = BadgeText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(BadgeGlass, RoundedCornerShape(4.dp))
            .border(1.dp, BadgeEdge, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

internal val BadgeGlass = androidx.compose.ui.graphics.Color(0xE6090B0E)
internal val BadgeEdge = androidx.compose.ui.graphics.Color(0x1FFFFFFF)
internal val BadgeText = androidx.compose.ui.graphics.Color(0xFFE6E9ED)
