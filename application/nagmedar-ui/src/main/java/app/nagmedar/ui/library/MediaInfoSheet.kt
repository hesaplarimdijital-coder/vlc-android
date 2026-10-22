package app.nagmedar.ui.library

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nagmedar.lab.NativeLab
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.player.navigate.DialogCard
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.theme.NagTheme
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/** The media whose information is asked for from a menu ("Medya bilgisi", Ctrl+I); the shell shows it. */
object MediaInfoRequests {
    var media by mutableStateOf<MediaWrapper?>(null)
        private set

    fun open(item: MediaWrapper) {
        media = item
    }

    fun close() {
        media = null
    }
}

/** The information window of the media asked for, over the shell. */
@Composable
fun MediaInfoHost() {
    MediaInfoRequests.media?.let { media -> MediaInfoDialog(media, onDismiss = MediaInfoRequests::close) }
}

/**
 * A media's information in a small window: its cover or picture, title, artist and album, its tags,
 * the file and where it is, and the technical facts of the media probe (the tech badge of the
 * films, the streams' lines). It replaces VLC's InfoActivity for the menus without a details pane.
 */
@Composable
fun MediaInfoDialog(media: MediaWrapper, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogFrame(onDismiss) {
            DialogCard(stringResource(R.string.nag_sc_media_info), Modifier.testTag("media-info"), cardWidth = 520.dp) {
                MediaInfoContent(media)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    NagButton(stringResource(R.string.nag_info_close), onDismiss, primary = true)
                }
            }
        }
    }
}

@Composable
private fun MediaInfoContent(media: MediaWrapper) {
    val colors = NagTheme.colors
    val context = LocalContext.current
    val key = media.uri?.toString()
    val tech = rememberTechOf(key)
    val video = media.type == MediaWrapper.TYPE_VIDEO
    Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(media, size = if (video) 128.dp else 96.dp, video = video)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(media.title ?: media.fileName.orEmpty(), style = NagTheme.type.headline, color = colors.text, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() })
                val byline = listOfNotNull(media.artistName?.takeIf { it.isNotBlank() }, media.albumName?.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (byline.isNotEmpty()) Text(byline, style = NagTheme.type.body, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val summary = listOfNotNull(
                    media.length.takeIf { it > 0 }?.let { durationWords(it) },
                    tech?.let(::techSummary)?.takeIf { it.isNotEmpty() },
                ).joinToString(" · ")
                if (summary.isNotEmpty()) Text(summary, style = NagTheme.type.labelSmall, color = colors.textTertiary)
                techBadge(tech, media.width, media.height)?.takeIf { video }?.let { TechBadge(it) }
            }
        }
        val tags = listOfNotNull(
            media.albumArtistName?.takeIf { it.isNotBlank() && it != media.artistName }?.let { InfoFact(stringResource(R.string.nag_info_album_artist), it) },
            media.genre?.takeIf { it.isNotBlank() }?.let { InfoFact(stringResource(R.string.nag_info_genre), it) },
            media.date?.takeIf { it.isNotBlank() }?.let { InfoFact(stringResource(R.string.nag_info_year), it) },
            media.trackNumber.takeIf { it > 0 }?.let { InfoFact(stringResource(R.string.nag_info_track_number), it.toString()) },
            media.discNumber.takeIf { it > 0 }?.let { InfoFact(stringResource(R.string.nag_info_disc), it.toString()) },
        )
        if (tags.isNotEmpty()) InfoSection(stringResource(R.string.nag_info_tags), tags)
        val location = media.uri?.let { if (it.scheme == "file" || it.scheme == null) it.path else it.toString() }
        InfoSection(
            stringResource(R.string.nag_info_file),
            listOfNotNull(
                media.fileName?.takeIf { it.isNotBlank() }?.let { InfoFact(stringResource(R.string.nag_info_file_name), it) },
                location?.let { InfoFact(stringResource(R.string.nag_info_location), it) },
                key?.let { storageName(it) }?.let { InfoFact(stringResource(R.string.nag_tech_location), it) },
            ),
        )
        if (location != null) {
            NagButton(stringResource(R.string.nag_info_copy_location), {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(media.title, location))
                Toast.makeText(context, R.string.nag_info_location_copied, Toast.LENGTH_SHORT).show()
            }, icon = NagIcons.Info)
        }
        val facts = tech?.let { techRows(it, key.orEmpty()) }?.map { InfoFact(it.label, it.value) }
        when {
            facts != null -> InfoSection(stringResource(R.string.nag_tech_title), facts)
            // a file of this device or a document is probed; a stream is not
            key != null && NativeLab.available && media.uri?.scheme in PROBED_SCHEMES -> InfoNote(stringResource(R.string.nag_tech_title), stringResource(R.string.nag_tech_probing))
            else -> InfoNote(stringResource(R.string.nag_tech_title), stringResource(R.string.nag_tech_unavailable))
        }
    }
}

private val PROBED_SCHEMES = setOf(null, "file", "content")

private class InfoFact(val label: String, val value: String)

@Composable
private fun InfoHeading(text: String) {
    Text(text.uppercase(currentLocale()), style = NagTheme.type.caption, color = NagTheme.colors.textTertiary, modifier = Modifier.semantics { heading() })
}

@Composable
private fun InfoSection(title: String, facts: List<InfoFact>) {
    if (facts.isEmpty()) return
    val colors = NagTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        InfoHeading(title)
        facts.forEach { fact ->
            Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "${fact.label}: ${fact.value}" }) {
                Text(fact.label, style = NagTheme.type.labelSmall, color = colors.textTertiary, modifier = Modifier.width(110.dp))
                Text(fact.value, style = NagTheme.type.mono, color = colors.text, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InfoNote(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        InfoHeading(title)
        Text(text, style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
    }
}
