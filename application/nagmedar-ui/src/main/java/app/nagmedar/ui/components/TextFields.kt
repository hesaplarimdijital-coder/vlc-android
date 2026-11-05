package app.nagmedar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.theme.NagTheme

/**
 * A one-line text field under its [label], as the settings ask for text (S-1008); [modifier] is the field's: a border that
 * thickens and takes the accent when the keyboard is in it, [placeholder] while empty, dots for a
 * [secret] (a password, a token) and no suggestions or capitals for it. [onDone] answers the
 * keyboard's Done and the Enter key (the field's own, as for any one-line field); Tab moves on to the next control.
 * With [lines] above one it is a text box of that many lines (S-1005), in which Enter starts a line.
 *
 * 1.0.3 (Desktop Kit 2.0's form fields, K1): [clearable] adds a button that empties it while it holds text; [error]
 * turns its border to the danger colour, and [supportingText] is a line under it (an error's words in the danger
 * colour, which TalkBack reads as the field's error and announces as they come); [keyboardType] is what the screen's
 * keyboard offers (digits for a number) and [imeAction] its action key (Next moves on); [filled] lays the field on the
 * surface colour, as a form's fields are; [textStyle], [contentPadding] and [contentAlignment] are for a field drawn
 * otherwise (the alarm's large digits); without [showLabel] the label is not drawn (one of the caller's names it)
 * but still names the field for TalkBack.
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
    lines: Int = 1,
    clearable: Boolean = false,
    error: Boolean = false,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = if (lines <= 1) ImeAction.Done else ImeAction.Default,
    autoCorrect: Boolean = !secret,
    filled: Boolean = false,
    textStyle: TextStyle = NagTheme.type.body,
    contentPadding: PaddingValues = FieldPadding,
    contentAlignment: Alignment = Alignment.CenterStart,
    showLabel: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val look = FieldLook(label, placeholder, secret, enabled, lines, clearable && enabled && value.isNotEmpty(), error, supportingText,
        filled, textStyle, contentPadding, contentAlignment, showLabel, fillWidth = true)
    FieldColumn(look) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = lines <= 1,
            minLines = lines,
            maxLines = if (lines <= 1) 1 else lines * 2,
            textStyle = textStyle.copy(color = NagTheme.colors.text),
            cursorBrush = SolidColor(NagTheme.colors.accent),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = fieldKeyboard(secret, keyboardType, imeAction, autoCorrect),
            keyboardActions = fieldActions(onDone, focusManager),
            interactionSource = interaction,
            modifier = modifier.field(look, interaction, focusManager),
            decorationBox = { inner -> FieldDecoration(look, value.isEmpty(), { onValueChange("") }, inner) },
        )
    }
}

/**
 * [NagTextField] over a [TextFieldValue], for a field that places the cursor itself (the alarm's hour and minute,
 * whose digits come in from the right as on a clock). Without [fillWidth] it is as wide as its [modifier] makes it.
 */
@Composable
fun NagTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    onDone: (() -> Unit)? = null,
    error: Boolean = false,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    filled: Boolean = false,
    textStyle: TextStyle = NagTheme.type.body,
    contentPadding: PaddingValues = FieldPadding,
    contentAlignment: Alignment = Alignment.CenterStart,
    showLabel: Boolean = true,
    fillWidth: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val look = FieldLook(label, placeholder, secret = false, enabled, lines = 1, clearShown = false, error, supportingText,
        filled, textStyle, contentPadding, contentAlignment, showLabel, fillWidth)
    FieldColumn(look) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = textStyle.copy(color = NagTheme.colors.text),
            cursorBrush = SolidColor(NagTheme.colors.accent),
            keyboardOptions = fieldKeyboard(secret = false, keyboardType, imeAction, autoCorrect = false),
            keyboardActions = fieldActions(onDone, focusManager),
            interactionSource = interaction,
            modifier = modifier.field(look, interaction, focusManager),
            decorationBox = { inner -> FieldDecoration(look, value.text.isEmpty(), {}, inner) },
        )
    }
}

/** What a field draws around its text, the same for both kinds of [NagTextField]. */
private class FieldLook(
    val label: String,
    val placeholder: String?,
    val secret: Boolean,
    val enabled: Boolean,
    val lines: Int,
    val clearShown: Boolean,
    val error: Boolean,
    val supportingText: String?,
    val filled: Boolean,
    val textStyle: TextStyle,
    val contentPadding: PaddingValues,
    val contentAlignment: Alignment,
    val showLabel: Boolean,
    val fillWidth: Boolean,
)

/** The field's label over it and its supporting line under it. */
@Composable
private fun FieldColumn(look: FieldLook, field: @Composable () -> Unit) {
    val colors = NagTheme.colors
    Column(if (look.fillWidth) Modifier.fillMaxWidth() else Modifier) {
        if (look.showLabel) Text(look.label, style = NagTheme.type.labelSmall, color = colors.textSecondary)
        field()
        if (look.supportingText != null) {
            val failing = look.error && look.enabled
            Text(
                look.supportingText,
                style = NagTheme.type.labelSmall,
                color = if (failing) colors.danger else colors.textTertiary,
                // an error's words are said as they come, as the network form's were; 6 dp under the field, as there
                modifier = Modifier.padding(top = 6.dp).then(if (failing) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
            )
        }
    }
}

/** The field itself: its frame (the accent's ring with the keyboard in it, the danger's with an error), its name for TalkBack, Tab. */
@Composable
private fun Modifier.field(look: FieldLook, interaction: MutableInteractionSource, focusManager: FocusManager): Modifier {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val failing = look.error && look.enabled
    return this
        .then(if (look.fillWidth) Modifier.fillMaxWidth() else Modifier)
        .alpha(if (look.enabled) 1f else DISABLED_ALPHA)
        .defaultMinSize(minHeight = dimens.minTarget.coerceAtLeast(dimens.controlHeight))
        .then(if (look.filled) Modifier.background(colors.surface, shape) else Modifier)
        .border(if (focused || failing) 2.dp else 1.dp, if (failing) colors.danger else if (focused) colors.accent else colors.borderStrong, shape)
        .semantics {
            contentDescription = look.label
            // TalkBack says what is wrong with the field with it; the line under it says it on screen
            if (failing) error(look.supportingText ?: "")
        }
        .tabMovesFocus(focusManager)
}

/** The placeholder, the text and the clear button inside the frame. */
@Composable
private fun FieldDecoration(look: FieldLook, empty: Boolean, clear: () -> Unit, inner: @Composable () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).padding(look.contentPadding), contentAlignment = look.contentAlignment) {
            if (empty && look.placeholder != null) {
                Text(look.placeholder, style = look.textStyle, color = colors.textTertiary, maxLines = look.lines, overflow = TextOverflow.Ellipsis)
            }
            inner()
        }
        if (look.clearShown) {
            // as the search field's clear button: the field's height, a finger's whole target by touch
            NagIconButton(NagIcons.Close, stringResource(R.string.nag_field_clear), clear, size = dimens.controlHeight.coerceAtLeast(dimens.minTarget))
        }
    }
}

private fun fieldKeyboard(secret: Boolean, keyboardType: KeyboardType, imeAction: ImeAction, autoCorrect: Boolean) = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = autoCorrect && !secret,
    keyboardType = if (secret) KeyboardType.Password else keyboardType,
    imeAction = imeAction,
)

/** Done (and Enter in one line) runs [onDone] or lets the field go; Next moves on, as the system's would. */
private fun fieldActions(onDone: (() -> Unit)?, focusManager: FocusManager) = KeyboardActions(
    onDone = { if (onDone != null) onDone() else focusManager.clearFocus() },
    onNext = { focusManager.moveFocus(FocusDirection.Next) },
)

/** Where a field's text sits in its frame. */
private val FieldPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)

private const val DISABLED_ALPHA = 0.45f
