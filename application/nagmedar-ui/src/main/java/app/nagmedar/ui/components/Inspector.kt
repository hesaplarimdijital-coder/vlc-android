package app.nagmedar.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.shell.LocalPaneWidths
import app.nagmedar.ui.shell.WORKSTATION_PANE
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme

/**
 * Desktop Kit 1.0: the width and visibility of a workstation's inspector pane, and its selected
 * tab. The divider's moves are kept while dragging and stored when the drag ends ([onCommit]);
 * hiding and showing it is stored at once.
 */
@Stable
class InspectorState(
    initialWidthDp: Float,
    initialCollapsed: Boolean = false,
    initialTab: Int = 0,
    private val onCommit: (widthDp: Float, collapsed: Boolean) -> Unit = { _, _ -> },
) {
    var widthDp by mutableFloatStateOf(initialWidthDp)
        private set
    var collapsed by mutableStateOf(initialCollapsed)
        private set
    var selectedTab by mutableIntStateOf(initialTab)

    /** Moves the divider: the pane gets [deltaDp] wider (negative: narrower), within [min]…[max]. */
    fun resizeBy(deltaDp: Float, min: Dp, max: Dp) {
        val range = min.value..max.value.coerceAtLeast(min.value)
        widthDp = (widthDp.coerceIn(range) + deltaDp).coerceIn(range)
    }

    /** The pane [targetDp] wide (a screen reader's slider), within [min]…[max]. */
    fun resizeTo(targetDp: Float, min: Dp, max: Dp) {
        widthDp = targetDp.coerceIn(min.value, max.value.coerceAtLeast(min.value))
    }

    /** Stores the current width and visibility. */
    fun commit() = onCommit(widthDp, collapsed)

    fun updateCollapsed(value: Boolean) {
        collapsed = value
        commit()
    }

    fun toggle() = updateCollapsed(!collapsed)
}

/**
 * An [InspectorState] whose width and visibility are kept under [key] in the window's pane store
 * ([LocalPaneWidths]: the app's preferences), so they come back after a restart; the tab lasts as
 * long as the window.
 */
@Composable
fun rememberInspectorState(key: String = WORKSTATION_PANE, defaultWidth: Dp = 384.dp): InspectorState {
    val store = LocalPaneWidths.current
    val commit: (Float, Boolean) -> Unit = { width, collapsed ->
        store.savePaneWidth(key, width)
        store.savePaneCollapsed(key, collapsed)
    }
    val saver = Saver<InspectorState, List<Any>>(
        save = { listOf(it.widthDp, it.collapsed, it.selectedTab) },
        restore = { InspectorState(it[0] as Float, it[1] as Boolean, it[2] as Int, commit) },
    )
    return rememberSaveable(key, store, saver = saver) {
        InspectorState(store.paneWidth(key) ?: defaultWidth.value, store.paneCollapsed(key), 0, commit)
    }
}

private const val MAIN = "main"
private const val DIVIDER = "divider"
private const val INSPECTOR = "inspector"

/**
 * Desktop Kit 1.0's workstation ("MusicBee"), for tablet and DeX windows: the shell's sidebar on
 * the left (already there), [main] in the middle and an inspector pane on the right ([inspector],
 * usually an [InspectorPane]). The divider between them is dragged, or moved with the arrow keys
 * when focused (S-303's [PaneDivider]); [state] keeps the width. Hidden, the inspector slides shut
 * (300 ms on the emphasized curve, at once with reduced motion). [main] keeps at least [minMain]:
 * the inspector gives way first. Below [showFrom] (phones, narrow windows) only [main] shows.
 */
@Composable
fun WorkstationLayout(
    state: InspectorState,
    main: @Composable () -> Unit,
    inspector: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    minMain: Dp = 360.dp,
    minInspector: Dp = 260.dp,
    maxInspector: Dp = 560.dp,
    showFrom: WidthClass = WidthClass.Expanded,
) {
    val shown = currentWidthClass() >= showFrom
    val visible = shown && !state.collapsed
    val tier = LocalMotionTier.current
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible, tier) {
        val target = if (visible) 1f else 0f
        if (tier == MotionTier.Reduced) progress.snapTo(target) else progress.animateTo(target, tween(NagMotion.LONG, easing = NagMotion.Emphasized))
    }
    val open by remember { derivedStateOf { progress.value > 0f } }
    val label = stringResource(R.string.nag_inspector_resize)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val handle = paneHandleWidth()
        // the most the inspector may take here, the main pane keeping its least
        val most = (maxWidth - minMain - handle).coerceIn(minInspector, maxInspector)
        val width = state.widthDp.dp.coerceIn(minInspector, most)
        Layout(
            modifier = Modifier.clipToBounds(),
            content = {
                Box(Modifier.layoutId(MAIN), propagateMinConstraints = true) { main() }
                if (visible) {
                    PaneDivider(
                        widthDp = width.value,
                        range = minInspector.value..most.value,
                        // the inspector is at the end: moving the divider towards the end narrows it
                        onResize = { towardsEnd -> state.resizeBy(-towardsEnd, minInspector, most) },
                        onSet = { target -> state.resizeTo(target, minInspector, most) },
                        label = label,
                        modifier = Modifier.layoutId(DIVIDER).width(handle),
                        onResizeEnd = state::commit,
                    )
                }
                Box(Modifier.layoutId(INSPECTOR).clipToBounds(), propagateMinConstraints = true) {
                    if (open) inspector()
                }
            },
        ) { measurables, constraints ->
            val total = constraints.maxWidth
            val height = constraints.maxHeight
            val dividerPx = if (visible) handle.roundToPx() else 0
            // the pane's width once open, the main pane keeping its least
            val paneWidth = width.roundToPx().coerceAtMost((total - dividerPx - minMain.roundToPx()).coerceAtLeast(0))
            // read while measuring: the slide re-measures without composing anything again
            val inspectorPx = (paneWidth * progress.value).toInt()
            val mainPx = (total - dividerPx - inspectorPx).coerceAtLeast(0)
            val mainP = measurables.first { it.layoutId == MAIN }.measure(Constraints.fixed(mainPx, height))
            val dividerP = measurables.firstOrNull { it.layoutId == DIVIDER }?.measure(Constraints.fixed(dividerPx, height))
            // the pane keeps its width while it slides: the window's edge cuts it, nothing is squeezed
            val paneP = measurables.first { it.layoutId == INSPECTOR }.measure(Constraints.fixed(paneWidth, height))
            layout(total, height) {
                mainP.placeRelative(0, 0)
                dividerP?.placeRelative(mainPx, 0)
                paneP.placeRelative(mainPx + dividerPx, 0)
            }
        }
    }
}

/** One tab of an [InspectorPane]: its stable [id] and what it says. */
@Immutable
data class InspectorTab(val id: String, val label: String)

/**
 * The inspector of a [WorkstationLayout] (Desktop Kit 1.0): a row of [tabs] (the selected one
 * underlined in the accent; ← and → move between them when one has the focus), the hide button
 * ([onCollapse], Ctrl+I) and the selected tab's [content], cross-faded. Nağmedar's music view puts
 * four tabs here: tags and spectrum, signal path, lyrics, queue.
 */
@Composable
fun InspectorPane(
    tabs: List<InspectorTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onCollapse: (() -> Unit)? = null,
    background: Color = NagTheme.colors.surface,
    content: @Composable (InspectorTab) -> Unit,
) {
    val dimens = NagTheme.dimens
    val label = stringResource(R.string.nag_inspector)
    val index = selected.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val reduced = NagTheme.motion.reduced
    // the selected tab is scrolled into sight when the tabs are wider than the pane
    val scroll = rememberScrollState()
    val spans = remember { mutableStateMapOf<Int, IntRange>() }
    var viewport by remember { mutableIntStateOf(0) }
    val span = spans[index]
    LaunchedEffect(index, span, viewport) {
        if (span == null || viewport <= 0) return@LaunchedEffect
        val target = when {
            span.first < scroll.value -> span.first
            span.last > scroll.value + viewport -> span.last - viewport
            else -> return@LaunchedEffect
        }
        if (reduced) scroll.scrollTo(target) else scroll.animateScrollTo(target)
    }
    Column(modifier.fillMaxSize().background(background).semantics { paneTitle = label }.testTag("inspector")) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = dimens.minTarget.coerceAtLeast(40.dp)).padding(horizontal = dimens.gapXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .onSizeChanged { viewport = it.width }
                    .horizontalScroll(scroll)
                    .selectableGroup()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || tabs.isEmpty()) return@onKeyEvent false
                        // the next tab is the one towards the end, in either direction of reading
                        val step = when (event.key) {
                            Key.DirectionRight -> if (rtl) -1 else 1
                            Key.DirectionLeft -> if (rtl) 1 else -1
                            else -> return@onKeyEvent false
                        }
                        onSelect((index + step + tabs.size) % tabs.size)
                        true
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { i, tab ->
                    InspectorTabButton(
                        tab,
                        i == index,
                        Modifier.onPlaced { placed ->
                            val x = placed.positionInParent().x.toInt()
                            val placedSpan = x..(x + placed.size.width)
                            // written only when it moved: a write each layout would compose again and again
                            if (spans[i] != placedSpan) spans[i] = placedSpan
                        },
                    ) { onSelect(i) }
                }
            }
            if (onCollapse != null) {
                NagIconButton(
                    NagIcons.SidebarToggle,
                    stringResource(R.string.nag_inspector_hide),
                    onCollapse,
                    selected = true,
                    shortcut = ShortcutId.ToggleInspector,
                    modifier = Modifier.testTag("inspector-hide"),
                )
            }
        }
        HorizontalRule()
        val tab = tabs.getOrNull(index)
        if (tab != null) {
            val spec = if (NagTheme.motion.reduced) snap<Float>() else tween<Float>(NagMotion.CROSSFADE)
            Crossfade(targetState = tab, animationSpec = spec, label = "inspectorTab", modifier = Modifier.fillMaxSize()) { shownTab ->
                Box(Modifier.fillMaxSize()) { content(shownTab) }
            }
        }
    }
}

@Composable
private fun InspectorTabButton(tab: InspectorTab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    Column(
        modifier
            .heightIn(min = dimens.minTarget.coerceAtLeast(36.dp))
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .semantics { this.selected = selected }
            .nagClickable(interaction, onClick, role = Role.Tab)
            .padding(horizontal = 6.dp)
            .testTag("inspector-tab-${tab.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            tab.label,
            style = if (selected) NagTheme.type.bodyStrong else NagTheme.type.body,
            color = if (selected) colors.text else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
        )
        Box(Modifier.width(24.dp).height(2.dp).background(if (selected) colors.accent else Color.Transparent, RoundedCornerShape(1.dp)))
    }
}

/** A toolbar button that shows or hides the inspector of [state] (Ctrl+I). */
@Composable
fun InspectorToggleButton(state: InspectorState, modifier: Modifier = Modifier) {
    NagIconButton(
        icon = NagIcons.SidebarToggle,
        contentDescription = stringResource(if (state.collapsed) R.string.nag_inspector_show else R.string.nag_inspector_hide),
        onClick = state::toggle,
        modifier = modifier.testTag("inspector-toggle"),
        selected = !state.collapsed,
        shortcut = ShortcutId.ToggleInspector,
    )
}
