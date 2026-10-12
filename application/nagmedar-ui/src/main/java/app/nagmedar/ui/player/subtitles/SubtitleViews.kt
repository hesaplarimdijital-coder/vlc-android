package app.nagmedar.ui.player.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.theme.NagTheme
import java.util.Locale

/**
 * Subtitles found in the film's folder, more than one (S-701): a card over the video, at its
 * bottom left as the screenshot's is, listing them by language with their files' names, the
 * user's language first; one chosen shows, the card closes.
 */
@Composable
fun SubtitleChooser(found: List<SubtitleCandidate>, onChoose: (SubtitleCandidate) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (found.isEmpty()) return
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .widthIn(max = 380.dp)
            .shadow(12.dp, shape)
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.borderStrong, shape)
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("subtitle-chooser"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(NagIcons.Subtitle, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(pluralStringResource(R.plurals.nag_sub_found, found.size, found.size), style = NagTheme.type.label, color = colors.text, maxLines = 1)
                Text(stringResource(R.string.nag_sub_found_hint), style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1)
            }
            NagIconButton(NagIcons.Close, stringResource(R.string.nag_sub_dismiss), onDismiss, size = 24.dp, iconSize = 14.dp)
        }
        Column(Modifier.padding(top = 6.dp, end = 6.dp)) {
            found.forEach { candidate -> CandidateRow(candidate) { onChoose(candidate) } }
        }
    }
}

/** "Türkçe", "zorunlu", the file's name: one of the subtitles found, chosen with a click. */
@Composable
private fun CandidateRow(candidate: SubtitleCandidate, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(6.dp)
    val language = languageName(candidate.language)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .background(stateBackground(state), shape)
            .nagClickable(interaction, onClick, onClickLabel = language)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("subtitle-candidate"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(language, style = NagTheme.type.label, color = colors.text, maxLines = 1, modifier = Modifier.width(92.dp))
        Column(Modifier.weight(1f)) {
            val tags = listOfNotNull(
                stringResource(R.string.nag_sub_forced).takeIf { candidate.forced },
                stringResource(R.string.nag_sub_sdh).takeIf { candidate.sdh },
            )
            if (tags.isNotEmpty()) Text(tags.joinToString(" · "), style = NagTheme.type.labelSmall, color = colors.accentText, maxLines = 1)
            Text(candidate.name, style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "Türkçe" in a Turkish interface, "Turkish" in an English one; "Language not given" without one. */
@Composable
fun languageName(code: String?): String {
    val locale = currentLocale()
    val name = code?.let { Locale.forLanguageTag(it).getDisplayLanguage(locale) }?.takeIf { it.isNotEmpty() && it != code }
    return name?.replaceFirstChar { it.titlecase(locale) } ?: stringResource(R.string.nag_sub_language_unknown)
}

/**
 * Files carried over the video (S-701): dropped, subtitles load at once and stay with the film;
 * a video dropped plays instead.
 */
@Composable
fun SubtitleDropOverlay(modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .fillMaxSize()
            .background(colors.scrim)
            .padding(24.dp)
            .border(2.dp, colors.accent, shape)
            .testTag("subtitle-drop"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 380.dp)
                .background(colors.surfaceRaised, shape)
                .padding(24.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(NagIcons.Subtitle, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(32.dp))
            Text(stringResource(R.string.nag_sub_drop_here), style = NagTheme.type.title, color = colors.text, textAlign = TextAlign.Center)
            Text(stringResource(R.string.nag_sub_drop_hint), style = NagTheme.type.body, color = colors.textSecondary, textAlign = TextAlign.Center)
        }
    }
}
