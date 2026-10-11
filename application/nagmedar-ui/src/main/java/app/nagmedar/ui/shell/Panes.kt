package app.nagmedar.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.PaneDivider
import app.nagmedar.ui.components.paneHandleWidth
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import kotlin.math.roundToInt

/** The window's width class, provided by the shell; outside it (previews, tests) it follows the configuration. */
val LocalWidthClass = compositionLocalOf<WidthClass?> { null }

@Composable
@ReadOnlyComposable
fun currentWidthClass(): WidthClass =
    LocalWidthClass.current ?: WidthClass.fromWidth(LocalConfiguration.current.screenWidthDp.toFloat())

/** Where dragged pane widths are kept between sessions (S-303). */
interface PaneWidths {
    /** The width saved for [key], in dp, or null. */
    fun paneWidth(key: String): Float?

    fun savePaneWidth(key: String, widthDp: Float)
}

/** Without a store (previews, tests) a width lasts as long as the screen. */
private object NoPaneWidths : PaneWidths {
    override fun paneWidth(key: String): Float? = null

    override fun savePaneWidth(key: String, widthDp: Float) = Unit
}

val LocalPaneWidths = staticCompositionLocalOf<PaneWidths> { NoPaneWidths }

/** The details pane beside lists; screens share its width. */
const val INSPECTOR_PANE = "inspector"

/** The sidebar of large windows. */
const val SIDEBAR_PANE = "sidebar"

/** The width of a pane that can be dragged, in dp; [save] keeps it for the next session. */
@Stable
class PaneWidthState internal constructor(private val key: String, private val store: PaneWidths, initial: Float) {
    var widthDp by mutableFloatStateOf(initial)

    /** Moves the pane's edge by [delta] dp, within [range]. */
    fun resize(delta: Float, range: ClosedFloatingPointRange<Float>) {
        widthDp = (widthDp.coerceIn(range) + delta).coerceIn(range)
    }

    fun save() = store.savePaneWidth(key, widthDp)
}

@Composable
fun rememberPaneWidth(key: String, default: Dp): PaneWidthState {
    val store = LocalPaneWidths.current
    val saver = Saver<PaneWidthState, Float>(save = { it.widthDp }, restore = { PaneWidthState(key, store, it) })
    return rememberSaveable(key, store, saver = saver) {
        PaneWidthState(key, store, store.paneWidth(key) ?: default.value)
    }
}

private const val LIST = "list"
private const val DETAIL = "detail"
private const val DIVIDER = "divider"

/**
 * A list and the details of what is picked in it (S-303). In expanded windows and wider the
 * details stand beside the list (with the sidebar of large windows, three panes) in a pane whose
 * width is dragged and remembered under [widthKey]; compact and medium windows show the details
 * over the whole content area, and Back returns to the list. The list stays composed in every
 * arrangement, so it keeps its scroll position and selection.
 *
 * [detail] null closes the details; [detailPane] gets the key and whether it fills the content
 * area. The pane slides in over [NagMotion.LONG] and keeps its content while it closes.
 */
@Composable
fun <K : Any> ListDetailPanes(
    detail: K?,
    onCloseDetail: () -> Unit,
    list: @Composable () -> Unit,
    detailPane: @Composable (key: K, fullScreen: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    widthKey: String = INSPECTOR_PANE,
    defaultDetailWidth: Dp = 336.dp,
    minDetailWidth: Dp = 280.dp,
    minListWidth: Dp = 360.dp,
) {
    val side = currentWidthClass() >= WidthClass.Expanded
    val motion = NagTheme.motion
    val width = rememberPaneWidth(widthKey, defaultDetailWidth)
    val progress = remember { Animatable(if (detail != null) 1f else 0f) }
    var retained by remember { mutableStateOf(detail) }
    LaunchedEffect(detail, side) {
        if (detail != null) {
            retained = detail
            progress.animateTo(1f, if (side) motion.standard(NagMotion.LONG) else motion.enter())
        } else {
            progress.animateTo(0f, if (side) motion.standard() else motion.exit())
            retained = null
        }
    }
    BackHandler(enabled = detail != null && !side, onBack = onCloseDetail)
    val shown = detail ?: retained
    val opening = detail != null
    val listCovered by remember(side) { derivedStateOf { !side && progress.value >= 1f } }
    val slidePx = with(LocalDensity.current) { NagMotion.STEP_SLIDE_DP.dp.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val dividerLabel = stringResource(R.string.nag_pane_divider)
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val handle = paneHandleWidth()
        val maxDetail = (maxWidth - minListWidth - handle).coerceAtLeast(minDetailWidth)
        val range = minDetailWidth.value..maxDetail.value
        val detailWidth = width.widthDp.coerceIn(range).dp
        Layout(
            content = {
                Box(
                    // covered by the details: out of the accessibility tree too
                    Modifier.layoutId(LIST).then(if (listCovered && shown != null) Modifier.clearAndSetSemantics {} else Modifier),
                    propagateMinConstraints = true,
                ) { list() }
                if (shown != null) {
                    Box(
                        Modifier
                            .layoutId(DETAIL)
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                    onCloseDetail()
                                    true
                                } else {
                                    false
                                }
                            },
                        propagateMinConstraints = true,
                    ) { detailPane(shown, !side) }
                    if (side) {
                        PaneDivider(
                            widthDp = detailWidth.value,
                            range = range,
                            // the pane is at the end: moving its edge towards the end narrows it
                            onResize = { towardsEnd -> width.resize(-towardsEnd, range) },
                            onSet = { width.widthDp = it.coerceIn(range) },
                            onResizeEnd = width::save,
                            label = dividerLabel,
                            modifier = Modifier.layoutId(DIVIDER).width(handle),
                            line = Alignment.CenterEnd,
                        )
                    }
                }
            },
        ) { measurables, constraints ->
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val p = progress.value
            val listM = measurables.first { it.layoutId == LIST }
            val detailM = measurables.firstOrNull { it.layoutId == DETAIL }
            val dividerM = measurables.firstOrNull { it.layoutId == DIVIDER }
            if (side) {
                // the list takes its new width at once and reflows once; the pane slides over it
                val handlePx = handle.roundToPx()
                val detailPx = detailWidth.roundToPx()
                val reserved = if (detailM != null && opening) detailPx + handlePx else 0
                val list = listM.measure(Constraints.fixed((w - reserved).coerceAtLeast(0), h))
                val divider = dividerM?.measure(Constraints.fixed(handlePx, h))
                val pane = detailM?.measure(Constraints.fixed(detailPx, h))
                val edge = w - ((detailPx + handlePx) * p).roundToInt()
                layout(w, h) {
                    list.placeRelative(0, 0)
                    pane?.placeRelative(edge + handlePx, 0)
                    divider?.placeRelative(edge, 0)
                }
            } else {
                // once the details cover the area the list is neither drawn nor read out, but stays composed
                val covered = detailM != null && p >= 1f
                val list = if (covered) null else listM.measure(Constraints.fixed(w, h))
                val pane = detailM?.measure(Constraints.fixed(w, h))
                layout(w, h) {
                    list?.placeRelative(0, 0)
                    pane?.placeRelativeWithLayer(0, 0) {
                        alpha = p
                        translationX = (1f - p) * slidePx * (if (rtl) -1f else 1f)
                    }
                }
            }
        }
    }
}
