package app.nagmedar.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.HoverTooltip
import app.nagmedar.ui.components.InteractionState
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuPopup
import app.nagmedar.ui.components.MenuSpec
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.PaneDivider
import app.nagmedar.ui.components.SectionCaption
import app.nagmedar.ui.components.TooltipPlacement
import app.nagmedar.ui.components.VerticalRule
import app.nagmedar.ui.components.animateInteractionColor
import app.nagmedar.ui.components.animateStateColor
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.pressScale
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.keyHint
import app.nagmedar.ui.theme.GlassMode
import app.nagmedar.ui.theme.NagGlass
import app.nagmedar.ui.theme.LocalMotionTier
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
 * The phone's bottom bar: Müzik · Podcast · Radyo · ⋯, a solid surface with a hairline over it
 * (1.0.1: it never takes the colour of what passes beneath). [selected] is the tab lit: the one
 * whose pages are on screen, none on the search page. 1.0.3 (Desktop Kit 2.0): at least its height,
 * taller with large text so the labels keep their line (at 200 % a label too long ends in "…");
 * [onGlass]: it lies on the shell's bottom glass (the user's glass decision) and draws no surface.
 */
@Composable
fun BottomNavBar(selected: Destination?, onSelect: (Destination) -> Unit, modifier: Modifier = Modifier, onGlass: Boolean = false) {
    val colors = NagTheme.colors
    val label = stringResource(R.string.nag_main_navigation)
    Column(modifier.fillMaxWidth().then(if (onGlass) Modifier else Modifier.background(colors.chrome))) {
        HorizontalRule()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .semantics { paneTitle = label }
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.bottomBar.forEach { destination ->
                BottomBarItem(destination, selected = destination == selected, onClick = { onSelect(destination) })
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
            .heightIn(min = dimens.bottomBarHeight)
            .fillMaxHeight()
            .semantics { this.selected = selected }
            .nagClickable(interaction, onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NavIndicator(selected, state, pill, Modifier.width(56.dp).height(30.dp)) {
            Icon(
                // the phone's last tab is "⋯", the page of the other pages
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
    // a fraction: the pill's spring stops a thousandth from its end (Part 2a's rule)
    val fraction = animateFloatAsState(if (selected) 1f else 0f, NagTheme.motion.spatial(NagMotion.FRACTION), label = "navIndicator")
    // with a mouse the hover and the press show at once, the selection still eases (Desktop Kit 0.2.1)
    val feedback by animateInteractionColor(
        when {
            state.pressed -> colors.pressed
            state.hovered && !selected -> colors.hover
            else -> Color.Transparent
        },
        selected,
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
fun NavRail(selected: Destination?, onSelect: (Destination) -> Unit, modifier: Modifier = Modifier) {
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
                    RailItem(destination, destination == selected) { onSelect(destination) }
                }
            }
            Destination.railFooter.forEach { destination ->
                RailItem(destination, destination == selected) { onSelect(destination) }
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
    selected: Destination?,
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
            selected = selected,
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
    selected: Destination?,
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
                        SidebarItem(destination, destination == selected, collapsed, badges[destination], open) { onSelect(destination) }
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
                        SidebarItem(Destination.Settings, selected == Destination.Settings, collapsed = false, badge = badges[Destination.Settings], open = open) {
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
                    SidebarItem(Destination.Settings, selected == Destination.Settings, collapsed = true, badge = badges[Destination.Settings], open = open) {
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
            // only the icon shows: a resting mouse brings the name (S-404); 1.0.8 (Desktop Kit 2.8.0): so does the
            // keyboard's focus, and the name stands beside the entry
            HoverTooltip(state.hovered && !state.pressed, label, badge, focused = state.focused && !state.pressed, placement = TooltipPlacement.End)
        }
    }
}

// ---------------------------------------------------------------- top bars

/**
 * Compact and medium top bar: [onBack]'s arrow (a page that is not a tab's first page, 1.0.1), the
 * page title and search ([onSearch] null: none, on the search page itself). One solid colour at
 * all times: nothing scrolling beneath it changes it (1.0.1). The arrow, the title and the search
 * button change with the page, fading through, so two titles never show at once. In a desktop
 * window's [caption] bar (S-306) it is the bar: the title moves the window, the buttons take their
 * clicks, the system's buttons keep their place. The bar is at least its height, so larger text
 * is not cut.
 *
 * [glass] (1.0.3, C of the user's glass decision; null in the product): Desktop Kit 2.3's phone top
 * bar, glass of that mode once content has [scrolled] beneath it, with a rule along its foot; the way
 * there and back fades the page colour's opacity alone, and the glass stays until the colour is back.
 */
@Composable
fun PageTopBar(
    title: String,
    onSearch: (() -> Unit)?,
    modifier: Modifier = Modifier,
    caption: CaptionBar? = null,
    onBack: (() -> Unit)? = null,
    glass: GlassMode? = null,
    scrolled: () -> Boolean = { false },
    /** 1.0.8 (Desktop Kit 2.8.0): the menu bar's menus where there is no menu bar (the rail), behind a "≡". */
    menus: List<MenuSpec> = emptyList(),
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val motion = NagTheme.motion
    val latestBack by rememberUpdatedState(onBack)
    val latestSearch by rememberUpdatedState(onSearch)
    Box(modifier.fillMaxWidth().then(if (glass == null) Modifier.background(colors.background) else Modifier.topBarGlass(glass, scrolled()))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxOf(dimens.topBarHeight, caption?.height ?: 0.dp))
                .captionPadding(caption)
                .padding(start = dimens.gapXs, end = dimens.gapS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1.0.8 (Desktop Kit 2.8.0): the back arrow slides in or out before the title, and the title moves along
            // with it; the title fades only when its words change. Until then the whole bar faded out and in when the
            // arrow alone came or went.
            val tier = LocalMotionTier.current
            val slide = NagMotion.fade(tier, NagMotion.open(IntSize.VisibilityThreshold))
            val inset by animateDpAsState(
                if (onBack != null) 0.dp else (dimens.pagePadding - dimens.gapXs).coerceAtLeast(0.dp),
                NagMotion.fade(tier, NagMotion.open(Dp.VisibilityThreshold)),
                label = "topBarTitleInset",
            )
            AnimatedVisibility(
                visible = onBack != null,
                enter = expandHorizontally(slide, expandFrom = Alignment.Start) + fadeIn(NagMotion.fade(tier, tween(NagMotion.CROSSFADE))),
                exit = shrinkHorizontally(slide, shrinkTowards = Alignment.Start) + fadeOut(NagMotion.fade(tier, tween(NagMotion.FADE_OUT))),
                label = "topBarBack",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NagIconButton(
                        NagIcons.ChevronLeft,
                        stringResource(R.string.nag_nav_back),
                        { latestBack?.invoke() },
                        Modifier.captionTarget(caption).testTag("top-bar-back"),
                        size = BACK_TARGET,
                        tint = colors.text,
                    )
                    Spacer(Modifier.width(dimens.gapXs))
                }
            }
            // the old title is gone before the new one fades in: two titles never show at once
            AnimatedContent(
                targetState = title,
                transitionSpec = { motion.topBarChange() },
                contentAlignment = Alignment.CenterStart,
                label = "topBarTitle",
                modifier = Modifier.weight(1f).padding(start = inset),
            ) { shown ->
                Text(shown, style = NagTheme.type.headline, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().semantics { heading() })
            }
            AnimatedVisibility(
                visible = onSearch != null,
                enter = fadeIn(NagMotion.fade(tier, tween(NagMotion.CROSSFADE, delayMillis = NagMotion.FADE_OUT))),
                exit = fadeOut(NagMotion.fade(tier, tween(NagMotion.FADE_OUT))),
                label = "topBarSearch",
            ) {
                NagIconButton(NagIcons.Search, stringResource(R.string.nag_search), { latestSearch?.invoke() }, Modifier.captionTarget(caption))
            }
            if (menus.isNotEmpty()) TopBarMenus(menus, Modifier.captionTarget(caption))
        }
    }
}

/**
 * 1.0.8 (Desktop Kit 2.8.0, the design audit's finding 15): the menu bar's menus in a window too narrow for the bar (the
 * rail's top bar: a tablet, a DeX window): a "≡" button ("Menüler") lists their titles, and a title opens its menu there.
 */
@Composable
internal fun TopBarMenus(menus: List<MenuSpec>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<MenuSpec?>(null) }
    var height by remember { mutableIntStateOf(0) }
    Box(modifier.onSizeChanged { height = it.height }) {
        NagIconButton(NagIcons.Menu, stringResource(R.string.nag_menus), { open = true }, Modifier.testTag("top-bar-menus"), selected = open || chosen != null)
        MenuPopup(
            visible = open,
            onDismiss = { open = false },
            entries = menus.map { menu ->
                MenuEntry.Item(menu.title, {
                    open = false
                    chosen = menu
                })
            },
            offset = IntOffset(0, height),
        )
        val menu = chosen
        MenuPopup(
            visible = menu != null,
            onDismiss = { chosen = null },
            entries = menu?.entries?.invoke().orEmpty(),
            offset = IntOffset(0, height),
        )
    }
}

/** Desktop Kit 2.3's top bar over content scrolled beneath it: glass of [mode], a rule along its foot, faded in and out. */
@Composable
private fun Modifier.topBarGlass(mode: GlassMode, scrolled: Boolean): Modifier {
    val colors = NagTheme.colors
    val motion = NagTheme.motion
    val background by animateColorAsState(
        if (scrolled) colors.background.copy(alpha = 0f) else colors.background,
        motion.color(),
        label = "topBarGlass",
    )
    val ruleAlpha by animateFloatAsState(if (scrolled) 1f else 0f, if (motion.reduced) tween(0) else tween(NagMotion.MEDIUM), label = "topBarRule")
    val rule = colors.border
    return this
        .glass(NagGlass.chrome(colors), mode = mode, enabled = scrolled || background.alpha < 0.999f, rest = colors.background)
        .background(background)
        .drawBehind {
            if (ruleAlpha > 0f) {
                val line = 1.dp.toPx()
                drawRect(rule.copy(alpha = rule.alpha * ruleAlpha), topLeft = Offset(0f, size.height - line), size = Size(size.width, line))
            }
        }
}

/** The back arrow's target: a thumb's, with a pointer too. */
private val BACK_TARGET = 48.dp

/**
 * Large and extra-large: brand, application menus and the search/command field (Ctrl+K). In a
 * desktop window's [caption] bar (S-306) it is the bar: the brand and the space between move the
 * window, the menus and the field take their clicks, the system's buttons keep their place. Where
 * the system shows its app chip at the bar's start (DeX, 1.0.3: Desktop Kit 2.3), the chip names
 * the app and the bar starts with the menus.
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
    val chip = caption.showsAppChip()
    Column(modifier.fillMaxWidth().background(colors.chrome)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxOf(dimens.menuBarHeight, caption?.height ?: 0.dp))
                .captionPadding(caption)
                .padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!chip) {
                BrandMark(Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.nagmedar_shell_title), style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(14.dp))
            }
            Row(Modifier.captionTarget(caption), verticalAlignment = Alignment.CenterVertically, content = menus)
            Spacer(Modifier.weight(1f))
            Box(Modifier.captionTarget(caption)) { search() }
        }
        HorizontalRule()
    }
}

/**
 * Field-looking button that opens search / the command palette. Outlined as a field is (Desktop Kit
 * 2.0): in the light theme its surface is as white as the menu bar around it.
 */
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
            // the focus ring takes the outline's place
            .then(if (state.focused) Modifier else Modifier.border(1.dp, colors.borderStrong, shape))
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

/**
 * Keyboard key cap, e.g. "Ctrl+K", in the monospace face. Outlined (Desktop Kit 2.0): the light
 * theme's raised surface is as white as the bars, and the outline shows the key there.
 */
@Composable
fun KeyCap(text: String, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text, style = NagTheme.type.mono, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun ChromeFill(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(NagTheme.colors.chrome))
}
