package app.nagmedar.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.PaneDivider
import app.nagmedar.ui.components.SectionCaption
import app.nagmedar.ui.components.VerticalRule
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.theme.NagTheme

/** Item counts and badges shown next to sidebar entries ("4 812", "4 yeni"). */
typealias NavBadges = Map<Destination, String>

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_nagmedar_mark),
        contentDescription = null,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------- compact: bottom bar

@Composable
fun BottomNavBar(current: Destination, onSelect: (Destination) -> Unit, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val label = stringResource(R.string.nag_main_navigation)
    Column(modifier.fillMaxWidth().background(colors.chrome)) {
        HorizontalRule()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NagTheme.dimens.bottomBarHeight)
                .semantics { paneTitle = label }
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.bottomBar.forEach { destination ->
                BottomBarItem(destination, selected = isSelected(destination, current, Destination.bottomBar), onClick = { onSelect(destination) })
            }
        }
    }
}

@Composable
private fun RowScope.BottomBarItem(destination: Destination, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val pill = RoundedCornerShape(dimens.radiusPill)
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics { this.selected = selected }
            .nagClickable(interaction, onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(30.dp)
                .focusRing(state, pill)
                .background(stateBackground(state, selected), pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                destination.icon,
                contentDescription = null,
                tint = if (selected) colors.onSelection else colors.textSecondary,
                modifier = Modifier.size(dimens.icon),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(destination.shortLabel),
            style = NagTheme.type.caption,
            color = if (selected) colors.text else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------- medium/expanded: rail

@Composable
fun NavRail(current: Destination, onSelect: (Destination) -> Unit, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val label = stringResource(R.string.nag_main_navigation)
    Row(modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(dimens.railWidth)
                .fillMaxHeight()
                .background(colors.chrome)
                .semantics { paneTitle = label }
                .padding(vertical = dimens.gapS),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(Modifier.padding(vertical = dimens.gapS).size(28.dp))
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).selectableGroup(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Destination.rail.forEach { destination ->
                    RailItem(destination, isSelected(destination, current, Destination.rail + Destination.railFooter)) { onSelect(destination) }
                }
            }
            Destination.railFooter.forEach { destination ->
                RailItem(destination, isSelected(destination, current, Destination.rail + Destination.railFooter)) { onSelect(destination) }
            }
        }
        VerticalRule(Modifier.fillMaxHeight())
    }
}

@Composable
private fun RailItem(destination: Destination, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val pill = RoundedCornerShape(dimens.radiusPill)
    Column(
        modifier = Modifier
            .width(dimens.railWidth)
            .heightIn(min = dimens.minTarget.coerceAtLeast(52.dp))
            .semantics { this.selected = selected }
            .nagClickable(interaction, onClick, role = Role.Tab)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(28.dp)
                .focusRing(state, pill)
                .background(stateBackground(state, selected), pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(destination.icon, contentDescription = null, tint = if (selected) colors.onSelection else colors.textSecondary,
                modifier = Modifier.size(dimens.icon))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(destination.shortLabel),
            style = NagTheme.type.caption,
            color = if (selected) colors.text else colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

// ---------------------------------------------------------------- large/extra-large: sidebar

/** Widths the sidebar can be dragged to (S-303). */
val SidebarWidthRange = 200f..360f

/**
 * The sidebar of large windows. Expanded, its end edge can be dragged between 200 and 360 dp
 * when [width] is given; collapsed, it shows icons only.
 */
@Composable
fun Sidebar(
    current: Destination,
    onSelect: (Destination) -> Unit,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    badges: NavBadges,
    modifier: Modifier = Modifier,
    width: PaneWidthState? = null,
) {
    val dimens = NagTheme.dimens
    Box(modifier.fillMaxHeight()) {
        SidebarColumn(
            current = current,
            onSelect = onSelect,
            collapsed = collapsed,
            onToggleCollapsed = onToggleCollapsed,
            badges = badges,
            width = when {
                collapsed -> dimens.sidebarCollapsedWidth
                width != null -> width.widthDp.coerceIn(SidebarWidthRange).dp
                else -> dimens.sidebarWidth
            },
        )
        if (width != null && !collapsed) {
            // over the sidebar's padding and its edge, so the content does not move
            PaneDivider(
                widthDp = width.widthDp.coerceIn(SidebarWidthRange),
                range = SidebarWidthRange,
                onResize = { towardsEnd -> width.resize(towardsEnd, SidebarWidthRange) },
                onSet = { width.widthDp = it.coerceIn(SidebarWidthRange) },
                onResizeEnd = width::save,
                label = stringResource(R.string.nag_sidebar_divider),
                modifier = Modifier.align(Alignment.CenterEnd).width(6.dp),
                line = Alignment.CenterEnd,
            )
        }
    }
}

@Composable
private fun SidebarColumn(
    current: Destination,
    onSelect: (Destination) -> Unit,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    badges: NavBadges,
    width: Dp,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val label = stringResource(R.string.nag_sidebar)
    Row(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(width)
                .fillMaxHeight()
                .background(colors.chrome)
                .semantics { paneTitle = label },
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (collapsed) 6.dp else dimens.gapS, vertical = dimens.gapS)
                    .selectableGroup(),
            ) {
                NavSection.entries.filter { it != NavSection.Footer }.forEach { section ->
                    val items = Destination.sidebar.filter { it.section == section }
                    if (items.isEmpty()) return@forEach
                    val title = section.title
                    if (!collapsed && title != null) {
                        SectionCaption(stringResource(title), Modifier.padding(start = 10.dp, top = dimens.gapM, bottom = 4.dp))
                    } else {
                        Spacer(Modifier.height(dimens.gapS))
                    }
                    items.forEach { destination ->
                        SidebarItem(destination, destination == current, collapsed, badges[destination]) { onSelect(destination) }
                    }
                }
            }
            HorizontalRule()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = if (collapsed) 6.dp else dimens.gapS, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!collapsed) {
                    Box(Modifier.weight(1f)) {
                        SidebarItem(Destination.Settings, current == Destination.Settings, collapsed = false, badge = null) {
                            onSelect(Destination.Settings)
                        }
                    }
                }
                NagIconButton(
                    icon = if (collapsed) NagIcons.ChevronRight else NagIcons.ChevronLeft,
                    contentDescription = stringResource(if (collapsed) R.string.nag_sidebar_expand else R.string.nag_sidebar_collapse),
                    onClick = onToggleCollapsed,
                )
            }
            if (collapsed) {
                Box(Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp)) {
                    SidebarItem(Destination.Settings, current == Destination.Settings, collapsed = true, badge = null) {
                        onSelect(Destination.Settings)
                    }
                }
            }
        }
        VerticalRule(Modifier.fillMaxHeight())
    }
}

@Composable
private fun SidebarItem(destination: Destination, selected: Boolean, collapsed: Boolean, badge: String?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    val label = stringResource(destination.label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.navItemHeight.coerceAtLeast(dimens.minTarget))
            .padding(vertical = 1.dp)
            .focusRing(state, shape)
            .background(stateBackground(state, selected), shape)
            .semantics {
                this.selected = selected
                if (collapsed) contentDescription = label
            }
            .nagClickable(interaction, onClick, role = Role.Tab)
            .padding(horizontal = if (collapsed) 0.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start,
    ) {
        Icon(
            destination.icon,
            contentDescription = null,
            tint = if (selected) colors.accentText else colors.textSecondary,
            modifier = Modifier.size(dimens.icon),
        )
        if (!collapsed) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = if (selected) NagTheme.type.bodyStrong else NagTheme.type.body,
                color = if (selected) colors.onSelection else colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (badge != null) {
                Text(badge, style = NagTheme.type.caption, color = colors.textTertiary, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------- top bars

/** Compact and medium top bar: page title and search. */
@Composable
fun PageTopBar(title: String, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Column(modifier.fillMaxWidth().background(colors.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(dimens.topBarHeight).padding(start = dimens.pagePadding, end = dimens.gapS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = NagTheme.type.headline, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            NagIconButton(NagIcons.Search, stringResource(R.string.nag_search), onSearch)
        }
    }
}

/** Large and extra-large: brand, application menus and the search/command field (Ctrl+K). */
@Composable
fun MenuBarChrome(
    menus: @Composable RowScope.() -> Unit,
    search: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Column(modifier.fillMaxWidth().background(colors.chrome)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(dimens.menuBarHeight).padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.nagmedar_shell_title), style = NagTheme.type.bodyStrong, color = colors.text)
            Spacer(Modifier.width(14.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = menus)
            search()
        }
        HorizontalRule()
    }
}

/** Field-looking button that opens search / the command palette. */
@Composable
fun SearchField(onClick: () -> Unit, shortcut: String?, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(7.dp)
    Row(
        modifier = modifier
            .height(dimens.controlHeight.coerceAtLeast(dimens.minTarget))
            .focusRing(state, shape)
            .background(if (state.hovered) colors.hover else colors.surface, shape)
            .nagClickable(interaction, onClick, onClickLabel = stringResource(R.string.nag_search))
            .padding(start = 10.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NagIcons.Search, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(dimens.icon))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.nag_search_hint), style = NagTheme.type.body, color = colors.textTertiary, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (shortcut != null) KeyCap(shortcut)
    }
}

/** Keyboard key cap, e.g. "Ctrl+K", in the monospace face. */
@Composable
fun KeyCap(text: String, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    Box(
        modifier
            .background(colors.surfaceRaised, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text, style = NagTheme.type.mono, color = colors.textSecondary, maxLines = 1)
    }
}

private fun isSelected(destination: Destination, current: Destination, shown: List<Destination>): Boolean =
    if (destination == Destination.More) current !in shown || current == Destination.More else destination == current

@Composable
internal fun ChromeFill(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(NagTheme.colors.chrome))
}
