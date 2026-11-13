package app.nagmedar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.delay
import app.nagmedar.ui.input.keyHint
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme

/** How long the mouse rests on something before its tooltip shows (S-404); Off shows none. */
enum class TooltipDelay(val millis: Long?) {
    Off(null),
    Short(300),
    Normal(550),
    Long(1000),
}

/** The tooltip delay of the window, in milliseconds; null when tooltips are off. */
val LocalTooltipDelay = staticCompositionLocalOf<Long?> { TooltipDelay.Normal.millis }

/** 1.0.8 (Desktop Kit 2.8.0): where a tooltip appears: under its element (buttons, toolbars) or beside it (a collapsed sidebar's entries). */
enum class TooltipPlacement { Below, End }

/**
 * 1.0.8 (Desktop Kit 2.8.0): when tooltips appear. The first after the window's rest ([LocalTooltipDelay]); the next
 * ones at once ([HANDOFF_MS]) while one is up or within [HANDOFF_WINDOW_MS] of it closing, as along a desktop toolbar.
 */
internal object TooltipTiming {
    const val HANDOFF_MS = 100L
    const val HANDOFF_WINDOW_MS = 1000L

    private var lastClosed = Long.MIN_VALUE / 2
    private var open = 0

    fun delayFor(rest: Long): Long {
        val now = android.os.SystemClock.uptimeMillis()
        return if (open > 0 || now - lastClosed < HANDOFF_WINDOW_MS) minOf(rest, HANDOFF_MS) else rest
    }

    fun opened() {
        open++
    }

    fun closed() {
        lastClosed = android.os.SystemClock.uptimeMillis()
        if (open > 0) open--
    }

    /** Forgets the last tooltip (tests run one after another in one process). */
    fun reset() {
        lastClosed = Long.MIN_VALUE / 2
        open = 0
    }
}

/** 1.0.8: whether the content is inside a [TooltipArea]: an icon button there leaves its own tooltip out. */
internal val LocalInTooltipArea = staticCompositionLocalOf { false }

/** 1.0.8 (Desktop Kit 2.8.0): whether the keyboard is what moves the focus now: a focus it brings names the element. */
@Composable
internal fun keyboardFocusing(): Boolean = LocalInputModeManager.current.inputMode == androidx.compose.ui.input.InputMode.Keyboard

/**
 * Desktop tooltip: appears after the mouse rests on [content] for the window's delay
 * ([LocalTooltipDelay]), shows the label and, when there is one, the keyboard shortcut (so
 * menus, buttons and the shortcut list agree, S-401). Touch input never opens it; long press is
 * the context menu. 1.0.8 (Desktop Kit 2.8.0): the keyboard's focus reaching it opens it too
 * ([showOnFocus]); a neighbour's follows at once ([TooltipTiming]); [placement]; with [enabled] false
 * it never opens.
 */
@Composable
fun TooltipArea(
    text: String,
    modifier: Modifier = Modifier,
    shortcut: String? = null,
    delayMillis: Long? = LocalTooltipDelay.current,
    placement: TooltipPlacement = TooltipPlacement.Below,
    showOnFocus: Boolean = true,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    var hovering by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val keyboard = keyboardFocusing()
    val wanted = enabled && (hovering || (showOnFocus && focused && keyboard))
    LaunchedEffect(wanted, delayMillis) {
        if (wanted && delayMillis != null) {
            delay(TooltipTiming.delayFor(delayMillis))
            if (!visible) TooltipTiming.opened()
            visible = true
        } else {
            if (visible) TooltipTiming.closed()
            visible = false
        }
    }
    DisposableEffect(Unit) { onDispose { if (visible) TooltipTiming.closed() } }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.hasFocus }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val mouse = event.changes.any { it.type == PointerType.Mouse }
                        when (event.type) {
                            PointerEventType.Enter -> if (mouse) hovering = true
                            PointerEventType.Exit -> hovering = false
                            PointerEventType.Press -> {
                                hovering = false
                                focused = false
                                if (visible) TooltipTiming.closed()
                                visible = false
                            }
                            else -> Unit
                        }
                    }
                }
            },
    ) {
        // an icon button inside names itself with this tooltip only, not with one of its own as well
        CompositionLocalProvider(LocalInTooltipArea provides enabled) { content() }
        if (visible) TooltipPopup(text, shortcut, placement)
    }
}

/**
 * The tooltip of an element whose hover state is known ([hovered], from its interaction source):
 * placed in the element, it shows under it once the mouse has rested for the window's delay, and
 * goes when the mouse leaves or presses. Hover comes from a mouse or a stylus, never from touch.
 * 1.0.8 (Desktop Kit 2.8.0): [focused] (the element's own focus) opens it too when the keyboard
 * brought the focus; a neighbour's follows at once ([TooltipTiming]); [placement].
 */
@Composable
fun HoverTooltip(
    hovered: Boolean,
    text: String,
    shortcut: String? = null,
    focused: Boolean = false,
    placement: TooltipPlacement = TooltipPlacement.Below,
) {
    val delayMillis = LocalTooltipDelay.current
    val wanted = hovered || (focused && keyboardFocusing())
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(wanted, delayMillis) {
        if (wanted && delayMillis != null) {
            delay(TooltipTiming.delayFor(delayMillis))
            if (!visible) TooltipTiming.opened()
            visible = true
        } else {
            if (visible) TooltipTiming.closed()
            visible = false
        }
    }
    DisposableEffect(Unit) { onDispose { if (visible) TooltipTiming.closed() } }
    if (visible) TooltipPopup(text, shortcut, placement)
}

/** The bubble under its element (or beside it); it fades in growing a little, and goes at once (Desktop Kit 0.2.0). */
@Composable
private fun TooltipPopup(text: String, shortcut: String?, placement: TooltipPlacement = TooltipPlacement.Below) {
    val motion = NagTheme.motion
    Popup(popupPositionProvider = remember(placement) { if (placement == TooltipPlacement.End) EndOfAnchorPositionProvider() else BelowAnchorPositionProvider() }) {
        val appear = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = appear,
            enter = fadeIn(motion.enter(NagMotion.SHORT)) + scaleIn(motion.enter(NagMotion.COLOR), initialScale = 0.94f),
            label = "tooltip",
        ) {
            TooltipBubble(text, shortcut)
        }
    }
}

@Composable
internal fun TooltipBubble(text: String, shortcut: String?, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.borderStrong, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = NagTheme.type.labelSmall, color = colors.text, maxLines = 2)
        // 1.0.1: the keys with a keyboard only (a mouse alone has none to press)
        val keys = keyHint(shortcut)
        if (keys != null) {
            Spacer(Modifier.width(8.dp))
            Text(keys, style = NagTheme.type.mono, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 1.0.8 (Desktop Kit 2.8.0): beside the anchor at its end, vertically centred; on the other side when there is no room. */
private class EndOfAnchorPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val gap = 8
        val ltr = layoutDirection == LayoutDirection.Ltr
        var x = if (ltr) anchorBounds.right + gap else anchorBounds.left - gap - popupContentSize.width
        if (x < 0 || x + popupContentSize.width > windowSize.width) {
            x = if (ltr) anchorBounds.left - gap - popupContentSize.width else anchorBounds.right + gap
        }
        val y = anchorBounds.top + (anchorBounds.height - popupContentSize.height) / 2
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

/** Centred under the anchor with a small gap; above it when there is no room below. */
private class BelowAnchorPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val gap = 6
        var x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        var y = anchorBounds.bottom + gap
        if (y + popupContentSize.height > windowSize.height) y = anchorBounds.top - gap - popupContentSize.height
        x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        return IntOffset(x, y.coerceAtLeast(0))
    }
}
