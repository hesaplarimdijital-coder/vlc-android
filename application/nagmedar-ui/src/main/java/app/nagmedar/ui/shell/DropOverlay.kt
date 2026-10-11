package app.nagmedar.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.theme.NagTheme

/**
 * What a drop on the window does (S-406), shown while files from another app or window are
 * carried over it: videos and music play, subtitles join the video playing. The films and the
 * player bar under the files say what they would do themselves.
 */
@Composable
fun DropOverlay(modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    Box(
        modifier
            .fillMaxSize()
            .background(colors.scrim)
            .padding(24.dp)
            .border(2.dp, colors.accent, shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .background(colors.surfaceRaised, shape)
                .padding(24.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(NagIcons.Play, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(32.dp))
            Text(stringResource(R.string.nag_drop_here), style = NagTheme.type.title, color = colors.text, textAlign = TextAlign.Center)
            Text(stringResource(R.string.nag_drop_hint), style = NagTheme.type.body, color = colors.textSecondary, textAlign = TextAlign.Center)
        }
    }
}
