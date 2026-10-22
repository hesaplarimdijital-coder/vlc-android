package app.nagmedar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.NagTheme

/**
 * S-1601: a one-line search field for filtering a list on the page: a magnifier, [placeholder]
 * while empty and a button that clears it ([clearLabel]). Esc empties it (with it empty the key
 * is the window's); Tab moves on to the next control.
 */
@Composable
fun NagSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(
        modifier
            .heightIn(min = dimens.controlHeight.coerceAtLeast(dimens.minTarget))
            .background(colors.surface, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.focusRing else colors.borderStrong, shape)
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NagIcons.Search, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(dimens.icon))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = NagTheme.type.body, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = NagTheme.type.body.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                interactionSource = interaction,
                modifier = Modifier
                    .fillMaxWidth()
                    .tabMovesFocus(focusManager)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && value.isNotEmpty()) {
                            onValueChange("")
                            true
                        } else {
                            false
                        }
                    }
                    .semantics { contentDescription = placeholder },
            )
        }
        if (value.isNotEmpty()) {
            NagIconButton(NagIcons.Close, clearLabel, { onValueChange("") }, size = dimens.controlHeight.coerceAtLeast(dimens.minTarget))
        } else {
            Spacer(Modifier.width(10.dp))
        }
    }
}

/**
 * S-1601: a name that is changed where it stands. At rest it is text that the pointer or a touch
 * turns into a field ([renameLabel] says what pressing it does); while [editing] the field has the
 * keyboard with the name selected, Enter or leaving it keeps what was typed, Esc leaves the name as
 * it was. A blank name is not kept. [onEditingChange] hears when editing starts and ends.
 */
@Composable
fun EditableTitle(
    text: String,
    onCommit: (String) -> Unit,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    renameLabel: String,
    modifier: Modifier = Modifier,
    style: TextStyle = NagTheme.type.title,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    if (!editing) {
        val interaction = remember { MutableInteractionSource() }
        val state = interaction.collectState()
        Box(
            modifier
                .heightIn(min = dimens.minTarget)
                .focusRing(state, shape)
                .background(stateBackground(state), shape)
                .nagClickable(interaction, { onEditingChange(true) }, role = Role.Button, onClickLabel = renameLabel)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text, style = style, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
        }
        return
    }
    var value by remember { mutableStateOf(TextFieldValue(text, TextRange(0, text.length))) }
    val focus = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val finish = { keep: Boolean ->
        if (!finished) {
            finished = true
            val name = value.text.trim()
            if (keep && name.isNotEmpty() && name != text) onCommit(name)
            onEditingChange(false)
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BasicTextField(
        value = value,
        onValueChange = { value = it },
        singleLine = true,
        textStyle = style.copy(color = colors.text),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { finish(true) }),
        modifier = modifier
            .heightIn(min = dimens.minTarget)
            .border(2.dp, colors.focusRing, shape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .focusRequester(focus)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) hadFocus = true else if (hadFocus) finish(true)
            }
            .tabMovesFocus(focusManager)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> finish(true)
                    Key.Escape -> finish(false)
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .semantics { contentDescription = renameLabel },
    )
}
