package app.nagmedar.ui.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.RatingStars
import app.nagmedar.ui.player.navigate.DialogCard
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.stats.TrackStats
import app.nagmedar.ui.theme.NagTheme

/**
 * S-808: "Puan ver…": the rating of [tracks] (one, or a selection) set with the stars, by a click,
 * a touch or the keys (← →, 0–5); each change is kept at once ([onRate]), Enter or "Tamam" closes.
 */
@Composable
fun RatingDialog(tracks: List<Track>, stats: Map<String, TrackStats>, onRate: (Int) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogFrame(onDismiss) { RatingSurface(tracks, stats, onRate, onDismiss) }
    }
}

@Composable
fun RatingSurface(tracks: List<Track>, stats: Map<String, TrackStats>, onRate: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = NagTheme.colors
    // the tracks' rating when they share one; none when they differ
    val ratings = remember(tracks) { tracks.map { stats[it.uri]?.rating ?: 0 }.distinct() }
    var stars by remember { mutableIntStateOf(ratings.singleOrNull() ?: 0) }
    val set = { value: Int ->
        stars = value
        onRate(value)
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    DialogCard(stringResource(R.string.nag_rate_title), Modifier.testTag("rating-dialog")) {
        Text(
            if (tracks.size == 1) tracks[0].title else pluralStringResource(R.plurals.nag_tracks_count, tracks.size, tracks.size),
            style = NagTheme.type.body,
            color = colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (ratings.size > 1) Text(stringResource(R.string.nag_rate_mixed), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        RatingStars(
            rating = stars,
            onRate = set,
            starSize = 28.dp,
            target = 44.dp,
            focusable = true,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .focusRequester(focus)
                .testTag("rating-stars")
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                },
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (stars > 0) NagButton(stringResource(R.string.nag_rate_clear), { set(0) })
            NagButton(stringResource(R.string.nag_sc_close), onDismiss, primary = true, modifier = Modifier.testTag("rating-done"))
        }
    }
}
