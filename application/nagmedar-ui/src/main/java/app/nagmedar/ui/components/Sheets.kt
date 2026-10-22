package app.nagmedar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import androidx.compose.ui.input.InputMode as ComposeInputMode

/** The tallest a menu line is on a sheet: a thumb's row. */
private val SHEET_ROW_HEIGHT = 56.dp

/**
 * S-1601: a menu as a sheet at the bottom of the window, for the phone: [entries] in rows of a
 * thumb's height under an optional [title], the window dimmed behind it; a tap outside or Back
 * closes it. The desktop's menus stay [MenuPopup].
 */
@Composable
fun MenuSheet(visible: Boolean, onDismiss: () -> Unit, entries: List<MenuEntry>, title: String? = null) {
    if (!visible || entries.isEmpty()) return
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surfaceRaised, shape)
                    // taps inside stay inside
                    .pointerInput(Unit) { detectTapGestures { } }
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .background(colors.borderStrong, RoundedCornerShape(2.dp)),
                )
                if (title != null) {
                    Text(
                        title,
                        style = NagTheme.type.bodyStrong,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                entries.forEach { entry ->
                    when (entry) {
                        is MenuEntry.Item -> MenuItemRow(entry, Modifier.heightIn(min = SHEET_ROW_HEIGHT)) {
                            onDismiss()
                            entry.onClick()
                        }
                        MenuEntry.Separator -> HorizontalRule(Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

/**
 * S-1601: an icon button that opens a menu of [entries]: a sheet at the bottom where the input is
 * touch, the desktop's pop-up under the button otherwise. The menu's [state] is the caller's, so
 * it can keep the button on screen while the menu is open.
 */
@Composable
fun ActionMenuButton(
    state: MenuState,
    icon: ImageVector,
    contentDescription: String,
    entries: () -> List<MenuEntry>,
    modifier: Modifier = Modifier,
    sheetTitle: String? = null,
) {
    var height by remember { mutableIntStateOf(0) }
    val inputModes = LocalInputModeManager.current
    var byKeys by remember { mutableStateOf(false) }
    Box(modifier) {
        NagIconButton(
            icon,
            contentDescription,
            {
                byKeys = inputModes.inputMode == ComposeInputMode.Keyboard
                state.open(IntOffset(0, height + 4))
            },
            modifier = Modifier.onGloballyPositioned { height = it.size.height },
            selected = state.isOpen,
        )
        if (NagTheme.inputMode == InputMode.Touch) {
            MenuSheet(visible = state.isOpen, onDismiss = state::close, entries = if (state.isOpen) entries() else emptyList(), title = sheetTitle)
        } else {
            MenuPopup(
                visible = state.isOpen,
                onDismiss = state::close,
                entries = if (state.isOpen) entries() else emptyList(),
                offset = state.offset ?: IntOffset.Zero,
                minWidth = 180.dp,
                focusFirst = byKeys,
            )
        }
    }
}

/** [ActionMenuButton] with a menu state of its own. */
@Composable
fun ActionMenuButton(
    icon: ImageVector,
    contentDescription: String,
    entries: () -> List<MenuEntry>,
    modifier: Modifier = Modifier,
    sheetTitle: String? = null,
) = ActionMenuButton(rememberMenuState(), icon, contentDescription, entries, modifier, sheetTitle)
