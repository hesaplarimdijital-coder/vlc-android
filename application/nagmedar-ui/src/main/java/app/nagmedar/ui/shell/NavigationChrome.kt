package app.nagmedar.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.HoverTooltip
import app.nagmedar.ui.components.InteractionState
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.PaneDivider
import app.nagmedar.ui.components.SectionCaption
import app.nagmedar.ui.components.VerticalRule
import app.nagmedar.ui.components.animateStateColor
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.pressScale
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.keyHint
import app.nagmedar.ui.theme.GlassThickness
import app.nagmedar.ui.theme.NagGlass
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.glass

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

/**
 * The phone's bottom bar: Müzik · Podcast · Radyo · ⋯ (Desktop Kit 1.0: glass inside the shell, the
 * chrome colour elsewhere; the shell opens its "⋯" sheet for [Destination.More]).
 */
@Composable
fun BottomNavBar(current: Destination, onSelect: (Destination) -> Unit, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val label = stringResource(R.string.nag_main_navigation)
    Column(modifier.fillMaxWidth().glass(NagGlass.chrome(colors, GlassThickness.Regular))) {
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
        NavIndicator(selected, state, pill, Modifier.width(56.dp).height(30.dp)) {
            Icon(
                // the phone's last tab is "⋯", its sheet of the other pages (Desktop Kit 1.0)
                if (destination == Destination.More) NagIcons.MoreHorizontal else destination.icon,
                contentDescription = null,
                tint = animateStateColor(if (selected) colors.onSelection else colors.textSecondary, "navIcon"),
                modifier = Modifier.size(dimens.icon),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(destination.shortLabel),
            // the selected tab's label a shade heavier (Desktop Kit 0.2.0)
            style = if (selected) NagTheme.type.caption.copy(fontWeight = FontWeight.Medium) else NagTheme.type.caption,
            color = animateStateColor(if (selected) colors.text else colors.textSecondary, "navLabel"),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The pill behind a navigation icon (Desktop Kit 0.2.0). Hover and press tint the whole pill; the
 * selection pill grows out from the middle on a soft spring when the item is chosen and shrinks
 * back when it is left, as in Android's navigation bar and rail. By touch a press also shrinks
 * the icon slightly. The pill is drawn behind, so its spring does not recompose the item.
 */
@Composable
private fun NavIndicator(selected: Boolean, state: InteractionState, shape: RoundedCornerShape, modifier: Modifier, content: @Composable () -> Unit) {
    val colors = NagTheme.colors
    val fraction = animateFloatAsState(if (selected) 1f else 0f, NagTheme.motion.spatial(), label = "navIndicator")
    val feedback = animateStateColor(
        when {
            state.pressed -> colors.pressed
            state.hovered && !selected -> colors.hover
            else -> colors.pressed.copy(alpha = 0f)
        },
        "navFeedback",
    )
    val selection = colors.selection
    Box(
        modifier = modifier
            .pressScale(state.pressed, 0.94f)
            .focusRing(state, shape)
            .drawBehind {
                val f = fraction.value.coerceIn(0f, 1f)
                if (f > 0.001f) {
                    val width = size.width * (0.4f + 0.6f * f)
                    drawRoundRect(
                        color = selection.copy(alpha = selection.alpha * f),
                        topLeft = Offset((size.width - width) / 2f, 0f),
                        size = Size(width, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                }
            }
            .background(feedback, shape),
        contentAlignment = Alignment.Center,
    ) { content() }
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
        NavIndicator(selected, state, pill, Modifier.width(48.dp).height(28.dp)) {
            Icon(
                destination.icon,
                contentDescription = null,
                tint = animateStateColor(if (selected) colors.onSelection else colors.textSecondary, "navIcon"),
                modifier = Modifier.size(dimens.icon),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(destination.shortLabel),
            style = NagTheme.type.caption,
            color = animateStateColor(if (selected) colors.text else colors.textSecondary, "navLabel"),
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
 * when [width] is given; collapsed, it shows icons only. Collapsing and expanding moves its edge
 * over [NagMotion.LONG] on the emphasized curve, the icons keep their place and the labels show
 * once there is room for them (Desktop Kit 0.2.0); a drag of the edge follows the pointer at once.
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
    val motion = NagTheme.motion
    val expandedWidth = width?.widthDp?.coerceIn(SidebarWidthRange)?.dp ?: dimens.sidebarWidth
    val target = if (collapsed) dimens.sidebarCollapsedWidth else expandedWidth
    val shown = remember { Animatable(target, Dp.VectorConverter) }
    var lastCollapsed by remember { mutableStateOf(collapsed) }
    LaunchedEffect(target) {
        if (collapsed != lastCollapsed) {
            lastCollapsed = collapsed
            shown.animateTo(target, motion.emphasized())
        } else {
            // the edge dragged, or the density changed: no animation
            shown.snapTo(target)
        }
    }
    val span = (expandedWidth - dimens.sidebarCollapsedWidth).value
    val open = if (span <= 0f) 1f else ((shown.value - dimens.sidebarCollapsedWidth).value / span).coerceIn(0f, 1f)
    Box(modifier.fillMaxHeight()) {
        SidebarColumn(
            current = current,
            onSelect = onSelect,
            collapsed = collapsed,
            onToggleCollapsed = onToggleCollapsed,
            badges = badges,
            width = shown.value,
            open = open,
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

/** Labels stay hidden in the first half of the opening, so they never show squeezed. */
private fun labelAlpha(open: Float): Float = ((open - 0.5f) * 2f).coerceIn(0f, 1f)

@Composable
private fun SidebarColumn(
    current: Destination,
    onSelect: (Destination) -> Unit,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    badges: NavBadges,
    width: Dp,
    open: Float,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val label = stringResource(R.string.nag_sidebar)
    Row(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(width)
                .fillMaxHeight()
                .clipToBounds()
                .background(colors.chrome)
                .semantics { paneTitle = label },
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = lerp(6.dp, dimens.gapS, open), vertical = dimens.gapS)
                    .selectableGroup(),
            ) {
                NavSection.entries.filter { it != NavSection.Footer }.forEach { section ->
                    val items = Destination.sidebar.filter { it.section == section }
                    if (items.isEmpty()) return@forEach
                    val title = section.title
                    if (!collapsed && title != null) {
                        SectionCaption(
                            stringResource(title),
                            Modifier.padding(start = 10.dp, top = dimens.gapM, bottom = 4.dp).alpha(labelAlpha(open)),
                        )
                    } else {
                        Spacer(Modifier.height(dimens.gapS))
                    }
                    items.forEach { destination ->
                        SidebarItem(destination, destination == current, collapsed, badges[destination], open) { onSelect(destination) }
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
                        SidebarItem(Destination.Settings, current == Destination.Settings, collapsed = false, badge = null, open = open) {
                            onSelect(Destination.Settings)
                        }
                    }
                }
                NagIconButton(
                    icon = if (collapsed) NagIcons.ChevronRight else NagIcons.ChevronLeft,
                    contentDescription = stringResource(if (collapsed) R.string.nag_sidebar_expand else R.string.nag_sidebar_collapse),
                    onClick = onToggleCollapsed,
                    shortcut = ShortcutId.ToggleSidebar,
                )
            }
            if (collapsed) {
                Box(Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp)) {
                    SidebarItem(Destination.Settings, current == Destination.Settings, collapsed = true, badge = null, open = open) {
                        onSelect(Destination.Settings)
                    }
                }
            }
        }
        VerticalRule(Modifier.fillMaxHeight())
    }
}

/**
 * [open] runs from 0 (collapsed) to 1 (expanded) while the sidebar moves: a collapsed item's icon
 * keeps its place and goes smoothly to where an expanded item has it.
 */
@Composable
private fun SidebarItem(destination: Destination, selected: Boolean, collapsed: Boolean, badge: String?, open: Float, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    val label = stringResource(destination.label)
    // a collapsed item centres its icon; the same start padding keeps it still while the sidebar opens
    val collapsedStart = ((dimens.sidebarCollapsedWidth - 12.dp - dimens.icon) / 2).coerceAtLeast(0.dp)
    val showLabel = !collapsed && open > 0.5f
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
            .padding(start = lerp(collapsedStart, 10.dp, open), end = if (showLabel) 10.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            destination.icon,
            contentDescription = null,
            tint = animateStateColor(if (selected) colors.accentText else colors.textSecondary, "sidebarIcon"),
            modifier = Modifier.size(dimens.icon),
        )
        if (showLabel) {
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f).alpha(labelAlpha(open)), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = if (selected) NagTheme.type.bodyStrong else NagTheme.type.body,
                    color = animateStateColor(if (selected) colors.onSelection else colors.text, "sidebarLabel"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (badge != null) {
                    Text(badge, style = NagTheme.type.caption, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (collapsed) {
            // only the icon shows: a resting mouse brings the name (S-404)
            HoverTooltip(state.hovered && !state.pressed, label, badge)
        }
    }
}

// ---------------------------------------------------------------- top bars

/**
 * Compact and medium top bar: page title and search ([onSearch] null: none, on the search page
 * itself). In a desktop window's [caption] bar (S-306) it is the bar: the title moves the window,
 * the search button takes its clicks, the system's buttons keep their place. [scrolled] (a phone's
 * content has left its top): the bar takes the chrome colour and a thin rule shows under it, as
 * Android's top app bar does (Desktop Kit 0.2.0); its height stays. With [glass] (the phone's shell,
 * where the content lies under the bar) it turns to glass once [scrolled] instead (Desktop Kit 1.0).
 * The bar is at least its height, so larger text is not cut.
 */
@Composable
fun PageTopBar(
    title: String,
    onSearch: (() -> Unit)?,
    modifier: Modifier = Modifier,
    caption: CaptionBar? = null,
    scrolled: Boolean = false,
    glass: Boolean = false,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val motion = NagTheme.motion
    // content scrolled beneath: the bar takes the chrome colour, or turns to glass over content lying under it (the phone's, Desktop Kit 1.0)
    val rest = if (glass) colors.background else Color.Transparent
    val background by animateColorAsState(
        if (scrolled) (if (glass) Color.Transparent else colors.chrome) else colors.background,
        motion.standard(NagMotion.MEDIUM),
        label = "topBar",
    )
    val ruleAlpha = animateFloatAsState(if (scrolled) 1f else 0f, motion.standard(NagMotion.MEDIUM), label = "topBarRule")
    val rule = colors.border
    Column(
        modifier
            .fillMaxWidth()
            .glass(NagGlass.chrome(colors, GlassThickness.Regular), enabled = glass && scrolled, rest = rest)
            .background(background)
            // over the bottom edge, so the bar keeps its height with the rule or without it
            .drawBehind {
                val alpha = ruleAlpha.value
                if (alpha > 0f) {
                    val line = 1.dp.toPx()
                    drawRect(rule.copy(alpha = rule.alpha * alpha), topLeft = Offset(0f, size.height - line), size = Size(size.width, line))
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxOf(dimens.topBarHeight, caption?.height ?: 0.dp))
                .captionPadding(caption)
                .padding(start = dimens.pagePadding, end = dimens.gapS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = NagTheme.type.headline, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            if (onSearch != null) NagIconButton(NagIcons.Search, stringResource(R.string.nag_search), onSearch, Modifier.captionTarget(caption))
        }
    }
}

/**
 * Large and extra-large: brand, application menus and the search/command field (Ctrl+K). In a
 * desktop window's [caption] bar (S-306) it is the bar: the brand and the space between move the
 * window, the menus and the field take their clicks, the system's buttons keep their place.
 */
@Composable
fun MenuBarChrome(
    menus: @Composable RowScope.() -> Unit,
    search: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    caption: CaptionBar? = null,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Column(modifier.fillMaxWidth().background(colors.chrome)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxOf(dimens.menuBarHeight, caption?.height ?: 0.dp))
                .captionPadding(caption)
                .padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.nagmedar_shell_title), style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(14.dp))
            Row(Modifier.captionTarget(caption), verticalAlignment = Alignment.CenterVertically, content = menus)
            Spacer(Modifier.weight(1f))
            Box(Modifier.captionTarget(caption)) { search() }
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
            .heightIn(min = dimens.controlHeight.coerceAtLeast(dimens.minTarget))
            .focusRing(state, shape)
            .background(stateBackground(state, rest = colors.surface), shape)
            .nagClickable(interaction, onClick, onClickLabel = stringResource(R.string.nag_search))
            .padding(start = 10.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NagIcons.Search, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(dimens.icon))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.nag_search_hint), style = NagTheme.type.body, color = colors.textTertiary, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        // 1.0.1: the keys with a keyboard only
        keyHint(shortcut)?.let { KeyCap(it) }
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
        Text(text, style = NagTheme.type.mono, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun isSelected(destination: Destination, current: Destination, shown: List<Destination>): Boolean = when {
    // the search page belongs to no tab; about, the guide, the shortcut card and all settings are Settings'; the music engine's equaliser is Music's
    current == Destination.Search -> false
    current == Destination.About || current == Destination.Guide || current == Destination.ShortcutCard || current == Destination.AllSettings ->
        destination == Destination.Settings
    current == Destination.Equalizer -> destination == Destination.Music
    destination == Destination.More -> current !in shown || current == Destination.More
    else -> destination == current
}

@Composable
internal fun ChromeFill(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(NagTheme.colors.chrome))
}
