package app.nagmedar.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.NagTheme

/**
 * A one-line text field under its [label], as the settings ask for text (S-1008); [modifier] is the field's: a border that
 * thickens and takes the accent when the keyboard is in it, [placeholder] while empty, dots for a
 * [secret] (a password, a token) and no suggestions or capitals for it. [onDone] answers the
 * keyboard's Done and the Enter key (the field's own, as for any one-line field); Tab moves on to the next control.
 */
@Composable
fun NagTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    secret: Boolean = false,
    enabled: Boolean = true,
    onDone: (() -> Unit)? = null,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = NagTheme.type.labelSmall, color = colors.textSecondary)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = NagTheme.type.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (onDone != null) onDone() else focusManager.clearFocus() }),
            interactionSource = interaction,
            modifier = modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .defaultMinSize(minHeight = dimens.minTarget.coerceAtLeast(dimens.controlHeight))
                .border(if (focused) 2.dp else 1.dp, if (focused) colors.accent else colors.borderStrong, shape)
                .semantics { contentDescription = label }
                .tabMovesFocus(focusManager),
            decorationBox = { inner ->
                Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder != null) Text(placeholder, style = NagTheme.type.body, color = colors.textTertiary)
                    inner()
                }
            },
        )
    }
}

private const val DISABLED_ALPHA = 0.45f
