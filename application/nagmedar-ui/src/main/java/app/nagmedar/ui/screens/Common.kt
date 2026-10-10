package app.nagmedar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.LocalNavKind
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.theme.NagTheme

/**
 * Title row of a screen. In the sidebar layout the menu bar carries no page title, so the
 * screen shows it here; on smaller windows the top bar already shows it and only [actions] remain.
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val dimens = NagTheme.dimens
    val showTitle = LocalNavKind.current == NavKind.Sidebar
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = dimens.pagePadding, end = dimens.pagePadding, top = if (showTitle) dimens.gapL else dimens.gapS, bottom = dimens.gapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showTitle) {
            Text(title, style = NagTheme.type.display, color = NagTheme.colors.text, maxLines = 1,
                modifier = Modifier.semantics { heading() })
            if (subtitle != null) {
                Spacer(Modifier.width(dimens.gapS))
                Text(subtitle, style = NagTheme.type.body, color = NagTheme.colors.textTertiary, maxLines = 1)
            }
        } else if (subtitle != null) {
            Text(subtitle, style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gapXs), content = actions)
    }
}

/** Screen that a later MVP fills in: says what comes and offers the classic interface meanwhile. */
@Composable
fun PlaceholderScreen(destination: Destination, onOpenClassic: (() -> Unit)?) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val label = stringResource(destination.label)
    Column(Modifier.fillMaxSize()) {
        PageHeader(title = label)
        Box(Modifier.fillMaxSize().padding(dimens.pagePadding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 420.dp)) {
                Box(
                    Modifier.size(64.dp).background(colors.surfaceRaised, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(destination.icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(dimens.gapL))
                Text(
                    text = stringResource(R.string.nag_coming_title, label, destination.comingIn ?: ""),
                    style = NagTheme.type.title,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                )
                destination.description?.let {
                    Spacer(Modifier.height(dimens.gapS))
                    Text(stringResource(it), style = NagTheme.type.body, color = colors.textSecondary, textAlign = TextAlign.Center)
                }
                if (onOpenClassic != null) {
                    Spacer(Modifier.height(dimens.gapL))
                    NagButton(text = stringResource(R.string.nag_open_classic), onClick = onOpenClassic)
                }
            }
        }
    }
}

/** "More": every destination that the current navigation chrome does not show. */
@Composable
fun MoreScreen(destinations: List<Destination>, onSelect: (Destination) -> Unit) {
    val dimens = NagTheme.dimens
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = dimens.gapS)) {
        items(destinations, key = { it.route }) { destination ->
            DestinationRow(destination) { onSelect(destination) }
        }
    }
}

@Composable
private fun DestinationRow(destination: Destination, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gapS)
            .heightIn(min = dimens.rowHeight.coerceAtLeast(dimens.minTarget))
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .nagClickable(interaction, onClick)
            .padding(horizontal = dimens.gapM, vertical = dimens.gapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(destination.icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(dimens.iconLarge))
        Spacer(Modifier.width(dimens.gapM))
        Column(Modifier.weight(1f)) {
            Text(stringResource(destination.label), style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1)
            destination.description?.let {
                Text(stringResource(it), style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        destination.comingIn?.let { Text(it, style = NagTheme.type.mono, color = colors.textTertiary) }
    }
}
