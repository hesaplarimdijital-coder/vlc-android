package app.nagmedar.ui.components

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.delay
import app.nagmedar.ui.theme.NagTheme

/**
 * Desktop tooltip: appears after the mouse rests on [content] for [delayMillis], shows the
 * label and, when there is one, the keyboard shortcut (so menus, buttons and the shortcut list
 * agree, S-401). Touch input never opens it; long press is the context menu.
 */
@Composable
fun TooltipArea(
    text: String,
    modifier: Modifier = Modifier,
    shortcut: String? = null,
    delayMillis: Long = 550,
    content: @Composable () -> Unit,
) {
    var hovering by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(hovering) {
        if (hovering) {
            delay(delayMillis)
            visible = true
        } else {
            visible = false
        }
    }
    Box(
        modifier = modifier
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
                                visible = false
                            }
                            else -> Unit
                        }
                    }
                }
            },
    ) {
        content()
        if (visible) {
            Popup(popupPositionProvider = remember { BelowAnchorPositionProvider() }) {
                TooltipBubble(text, shortcut)
            }
        }
    }
}

@Composable
private fun TooltipBubble(text: String, shortcut: String?) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.borderStrong, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = NagTheme.type.labelSmall, color = colors.text, maxLines = 2)
        if (shortcut != null) {
            Spacer(Modifier.width(8.dp))
            Text(shortcut, style = NagTheme.type.mono, color = colors.textTertiary, maxLines = 1)
        }
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
