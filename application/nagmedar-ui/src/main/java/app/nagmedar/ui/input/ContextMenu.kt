package app.nagmedar.ui.input

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuPopup
import app.nagmedar.ui.components.rememberMenuState
import kotlin.math.roundToInt

/**
 * Context menu gesture (S-403, ADR-04):
 * - mouse right click and touchpad two-finger tap (a secondary button press);
 * - long press on touch screens.
 *
 * The secondary press is consumed in the initial pass, so the element's own click handler does
 * not fire; after a long press the release is consumed too. Primary mouse clicks pass through.
 * [onContextMenu] receives the position inside the element, so the menu opens under the pointer.
 */
fun Modifier.contextMenuGesture(enabled: Boolean = true, onContextMenu: (Offset) -> Unit): Modifier =
    if (!enabled) this else pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (currentEvent.buttons.isSecondaryPressed) {
                down.consume()
                onContextMenu(down.position)
                consumeUntilAllUp()
                return@awaitEachGesture
            }
            if (down.type != PointerType.Touch) return@awaitEachGesture
            // null when the finger stayed down and still past the long-press timeout
            val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var ended = false
                while (!ended) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    ended = change == null || !change.pressed ||
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                }
                true
            }
            if (endedEarly == null) {
                onContextMenu(down.position)
                consumeUntilAllUp()
            }
        }
    }

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.consumeUntilAllUp() {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        event.changes.forEach { it.consume() }
        if (event.changes.none { it.pressed }) return
    }
}

/**
 * Wraps [content] with a context menu built by [entries] when it opens. Besides the pointer
 * gestures, the Menu key or Shift+F10 opens it for the focused element, and TalkBack gets a
 * custom action with the same label.
 */
@Composable
fun ContextMenuArea(
    entries: () -> List<MenuEntry>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accessibilityLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val menu = rememberMenuState()
    val currentEntries by rememberUpdatedState(entries)
    var height by remember { mutableIntStateOf(0) }
    Box(
        modifier = modifier
            .onSizeChanged { height = it.height }
            .contextMenuGesture(enabled) { position -> menu.open(IntOffset(position.x.roundToInt(), position.y.roundToInt())) }
            .onKeyEvent { event ->
                val opens = event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Menu || (event.key == Key.F10 && event.isShiftPressed))
                if (opens && enabled) menu.open(IntOffset(0, height))
                opens && enabled
            }
            .semantics {
                if (enabled && accessibilityLabel != null) {
                    customActions = listOf(CustomAccessibilityAction(accessibilityLabel) { menu.open(IntOffset(0, height)); true })
                }
            },
    ) {
        content()
        MenuPopup(
            visible = menu.isOpen,
            onDismiss = menu::close,
            entries = if (menu.isOpen) currentEntries() else emptyList(),
            offset = menu.offset ?: IntOffset.Zero,
        )
    }
}
