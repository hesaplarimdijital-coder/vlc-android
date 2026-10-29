package app.nagmedar.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.shell.KeyCap
import app.nagmedar.ui.theme.NagTheme

/**
 * A dialog's window: the card in the middle, a tap beside it closes it; its card's close
 * affordance ([DialogDismissButton]) closes it too, shown in [dismissStyle] (by default from the
 * configuration: Esc with a keyboard at hand, × without one).
 */
@Composable
internal fun DialogFrame(
    onDismiss: () -> Unit,
    dismissStyle: DismissStyle = dismissStyleFor(LocalConfiguration.current),
    content: @Composable () -> Unit,
) {
    val dismiss = remember(dismissStyle, onDismiss) { DialogDismiss(dismissStyle, onDismiss) }
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) { CompositionLocalProvider(LocalDialogDismiss provides dismiss, content = content) }
}

/** How a dialog shows that it can be closed (Desktop Kit 1.0's rule, the app's since PR #65): Esc with a hardware keyboard, × otherwise. */
enum class DismissStyle {
    /** A hardware keyboard is attached (DeX, a keyboard cover): the "Esc" key cap. */
    EscKey,

    /** Touch only: a 48 dp × button. */
    CloseButton,
}

/** The close affordance of the surrounding [DialogFrame]; read by [DialogDismissButton]. */
@Immutable
class DialogDismiss(val style: DismissStyle, val onDismiss: () -> Unit)

/** Provided by [DialogFrame]; null outside a frame. */
val LocalDialogDismiss = compositionLocalOf<DialogDismiss?> { null }

/**
 * [DismissStyle.EscKey] when [configuration] reports a hardware QWERTY keyboard that is not
 * hidden (`Configuration.KEYBOARD_QWERTY`), [DismissStyle.CloseButton] otherwise.
 */
fun dismissStyleFor(configuration: Configuration): DismissStyle =
    if (configuration.keyboard == Configuration.KEYBOARD_QWERTY &&
        configuration.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
    ) DismissStyle.EscKey else DismissStyle.CloseButton

/**
 * The close affordance of the dialog it is in: the "Esc" key cap when a keyboard is at hand (Esc
 * closes the dialog, [NagDialog]), a × button that closes it otherwise; outside a [DialogFrame]
 * only the key cap, with a keyboard.
 */
@Composable
fun DialogDismissButton(modifier: Modifier = Modifier) {
    val dismiss = LocalDialogDismiss.current
    // the keyboard at hand where the card is drawn decides, as since PR #65 (a frame's window may say otherwise)
    val keyboard = dismissStyleFor(LocalConfiguration.current) == DismissStyle.EscKey
    when {
        keyboard || dismiss?.style == DismissStyle.EscKey -> KeyCap(ESCAPE, modifier)
        dismiss != null -> NagIconButton(
            NagIcons.Close,
            stringResource(R.string.nag_player_window_close),
            dismiss.onDismiss,
            modifier = modifier.testTag("dialog-close"),
        )
    }
}

/**
 * The card of the player's small dialogs: a title with Esc, then what it asks; [cardWidth] wide where the window has
 * room. Esc is shown with a keyboard at hand; without one (a phone, a tablet by touch) a × closes the dialog instead.
 */
@Composable
internal fun DialogCard(title: String, modifier: Modifier = Modifier, cardWidth: Dp = 380.dp, content: @Composable ColumnScope.() -> Unit) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(12.dp)
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val width = if (maxWidth < cardWidth + 40.dp) maxWidth - 32.dp else cardWidth
        Column(
            modifier
                .width(width)
                .shadow(24.dp, shape)
                .background(colors.surfaceRaised, shape)
                .border(1.dp, colors.borderStrong, shape)
                // taps inside stay inside
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = NagTheme.type.title, color = colors.text, modifier = Modifier.weight(1f))
                DialogDismissButton()
            }
            content()
        }
    }
}

/** The key's name, as keyboards write it. */
private const val ESCAPE = "Esc"
