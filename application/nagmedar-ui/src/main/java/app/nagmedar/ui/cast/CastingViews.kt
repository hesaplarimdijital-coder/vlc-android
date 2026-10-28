package app.nagmedar.ui.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.theme.NagTheme

/**
 * S-1302: the cast button of the player bars and the music panels: it opens the
 * device picker; lit while [casting], with where it plays in its name.
 */
@Composable
fun CastButton(
    casting: CastDevice?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = NagTheme.dimens.minTarget.coerceAtLeast(NagTheme.dimens.controlHeight),
    iconSize: Dp = NagTheme.dimens.icon,
) {
    NagIconButton(
        NagIcons.Cast,
        if (casting != null) stringResource(R.string.nag_cast_casting_to, casting.name) else stringResource(R.string.nag_sc_cast),
        onClick,
        modifier = modifier.testTag("cast-button"),
        selected = casting != null,
        size = size,
        iconSize = iconSize,
        shortcut = ShortcutId.Cast,
    )
}

/**
 * S-1302: "Casting to Salon TV" over a music panel while a renderer plays: the device's kind, its
 * name, and casting stopped (the track comes back to this device where it was); in a narrow pane
 * the stop button is its icon, named by its tooltip.
 */
@Composable
fun CastingStrip(device: CastDevice, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val narrow = maxWidth < STRIP_TEXT_BUTTON
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.selection, RoundedCornerShape(dimens.radiusControl))
                .padding(start = dimens.gapM, end = 4.dp, top = 4.dp, bottom = 4.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .testTag("casting-strip"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(kindIcon(device.kind), contentDescription = null, tint = colors.onSelection, modifier = Modifier.size(dimens.icon))
            Spacer(Modifier.width(dimens.gapS))
            Text(
                stringResource(R.string.nag_cast_casting_to, device.name),
                style = NagTheme.type.bodyStrong,
                color = colors.onSelection,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val stop = stringResource(R.string.nag_cast_stop)
            if (narrow) {
                NagIconButton(NagIcons.Stop, stop, onStop, tint = colors.onSelection)
            } else {
                NagButton(stop, onStop, icon = NagIcons.Stop)
            }
        }
    }
}

/** Below this width the strip's stop button is its icon. */
private val STRIP_TEXT_BUTTON = 360.dp
