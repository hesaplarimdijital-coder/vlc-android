package app.nagmedar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SectionCaption
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.LocalNavKind
import app.nagmedar.ui.shell.LocalShellBottomPadding
import app.nagmedar.ui.shell.LocalShellTopPadding
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.shell.NavSection
import app.nagmedar.ui.shell.plus
import app.nagmedar.ui.theme.NagTheme

/**
 * Title row of a screen. In the sidebar layout the menu bar carries no page title, so the
 * screen shows it here; on smaller windows the top bar already shows it and only [actions] remain.
 * On a phone a header with neither a subtitle nor actions takes no room at all (Desktop Kit 0.1.3).
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val dimens = NagTheme.dimens
    val navKind = LocalNavKind.current
    val showTitle = navKind == NavKind.Sidebar
    val vertical = if (navKind == NavKind.BottomBar && subtitle == null) {
        Modifier.verticalPaddingUnlessEmpty(dimens.gapS)
    } else {
        Modifier.padding(top = if (showTitle) dimens.gapL else dimens.gapS, bottom = dimens.gapS)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = dimens.pagePadding, end = dimens.pagePadding)
            .then(vertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showTitle) {
            Text(title, style = NagTheme.type.display, color = NagTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() })
            if (subtitle != null) Spacer(Modifier.width(dimens.gapS))
        }
        // the actions keep their room; a long subtitle is cut short instead
        if (subtitle != null) {
            Text(
                subtitle,
                style = if (showTitle) NagTheme.type.body else NagTheme.type.labelSmall,
                color = NagTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gapXs), content = actions)
    }
}

/** [padding] above and below what is in it, and no room at all when it is empty (a phone's header without actions). */
private fun Modifier.verticalPaddingUnlessEmpty(padding: Dp): Modifier = layout { measurable, constraints ->
    val pad = padding.roundToPx()
    val inner = constraints.copy(
        minHeight = (constraints.minHeight - 2 * pad).coerceAtLeast(0),
        maxHeight = if (constraints.hasBoundedHeight) (constraints.maxHeight - 2 * pad).coerceAtLeast(0) else constraints.maxHeight,
    )
    val placeable = measurable.measure(inner)
    val height = if (placeable.height == 0) 0 else placeable.height + 2 * pad
    layout(placeable.width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) { placeable.placeRelative(0, pad) }
}

/**
 * An entry of a "More" page's own groups (1.0.1: "Oynatma"), after the pages: [opensPage] draws the
 * chevron of a row that opens a page; a row that opens a dialog or a panel has none.
 */
@Immutable
data class MoreAction(val label: String, val icon: ImageVector, val onClick: () -> Unit, val opensPage: Boolean = false)

/** A titled group of [MoreAction]s. */
@Immutable
data class MoreActionGroup(val title: String, val actions: List<MoreAction>)

/**
 * "More" (the phone's "⋯" tab, "Daha fazla", and the rail's): every destination that the current
 * navigation chrome does not show, by the sidebar's sections under their captions (Settings last,
 * after a rule), then [actions]' groups. Rows of a thumb's height with the page's icon, its name,
 * its description where it has one and a chevron where a page opens (1.0.1).
 */
@Composable
fun MoreScreen(destinations: List<Destination>, onSelect: (Destination) -> Unit, actions: List<MoreActionGroup> = emptyList()) {
    val dimens = NagTheme.dimens
    val sections = NavSection.entries.mapNotNull { section ->
        destinations.filter { it.section == section }.takeIf { it.isNotEmpty() }?.let { section to it }
    }
    // the list scrolls under the phone's bars (Desktop Kit 1.0)
    LazyColumn(
        Modifier.fillMaxSize().testTag("more-page"),
        contentPadding = LocalShellTopPadding.current + PaddingValues(vertical = dimens.gapS) + LocalShellBottomPadding.current,
    ) {
        sections.forEachIndexed { index, (section, pages) ->
            val title = section.title
            item(key = "section:${section.name}", contentType = "caption") {
                if (title != null) MoreCaption(stringResource(title), first = index == 0) else MoreRule()
            }
            items(pages, key = { it.route }, contentType = { "row" }) { destination ->
                MoreRow(
                    label = stringResource(destination.label),
                    icon = destination.icon,
                    description = destination.description?.let { stringResource(it) },
                    opensPage = true,
                    onClick = { onSelect(destination) },
                )
            }
        }
        actions.filter { it.actions.isNotEmpty() }.forEachIndexed { g, group ->
            item(key = "group:$g", contentType = "caption") { MoreCaption(group.title, first = sections.isEmpty() && g == 0) }
            group.actions.forEachIndexed { a, action ->
                item(key = "action:$g:$a", contentType = "row") {
                    MoreRow(action.label, action.icon, description = null, opensPage = action.opensPage, onClick = action.onClick)
                }
            }
        }
    }
}

@Composable
private fun MoreCaption(text: String, first: Boolean) {
    val dimens = NagTheme.dimens
    SectionCaption(
        text,
        Modifier
            .padding(start = dimens.gapL + 4.dp, end = dimens.gapL, top = if (first) dimens.gapXs else dimens.gapL, bottom = dimens.gapXs)
            .semantics { heading() },
    )
}

/** Between the sections where the next has no caption (Settings). */
@Composable
private fun MoreRule() {
    val dimens = NagTheme.dimens
    HorizontalRule(Modifier.padding(horizontal = dimens.gapL, vertical = dimens.gapS))
}

@Composable
private fun MoreRow(label: String, icon: ImageVector, description: String?, opensPage: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gapS)
            .heightIn(min = MORE_ROW_HEIGHT)
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .nagClickable(interaction, onClick, role = Role.Button)
            .padding(horizontal = dimens.gapM, vertical = dimens.gapXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(dimens.iconLarge))
        Spacer(Modifier.width(dimens.gapL))
        Column(Modifier.weight(1f)) {
            Text(label, style = NagTheme.type.body, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (description != null) {
                Text(description, style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (opensPage) {
            Spacer(Modifier.width(dimens.gapS))
            Icon(NagIcons.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(dimens.icon))
        }
    }
}

/** A "More" row: a thumb's height. */
private val MORE_ROW_HEIGHT = 56.dp
