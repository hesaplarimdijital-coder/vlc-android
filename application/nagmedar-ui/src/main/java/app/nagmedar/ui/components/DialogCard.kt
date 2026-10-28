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
import androidx.compose.runtime.staticCompositionLocalOf
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

/** A dialog's window: the card in the middle, a tap beside it closes it; its card's × closes it too ([LocalDialogDismiss]). */
@Composable
internal fun DialogFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) { CompositionLocalProvider(LocalDialogDismiss provides onDismiss, content = content) }
}

/** How the [DialogFrame] around a [DialogCard] closes: its × on a device without a keyboard; null outside a frame. */
internal val LocalDialogDismiss = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * The card of the player's small dialogs: a title with Esc, then what it asks; [cardWidth] wide where the window has
 * room. Esc is shown with a keyboard at hand; without one (a phone, a tablet by touch) a × closes the dialog instead.
 */
@Composable
internal fun DialogCard(title: String, modifier: Modifier = Modifier, cardWidth: Dp = 380.dp, content: @Composable ColumnScope.() -> Unit) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val configuration = LocalConfiguration.current
    val keyboard = configuration.keyboard == Configuration.KEYBOARD_QWERTY && configuration.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
    val dismiss = LocalDialogDismiss.current
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
                when {
                    keyboard -> KeyCap(ESCAPE)
                    dismiss != null -> NagIconButton(NagIcons.Close, stringResource(R.string.nag_player_window_close), dismiss, modifier = Modifier.testTag("dialog-close"))
                }
            }
            content()
        }
    }
}

/** The key's name, as keyboards write it. */
private const val ESCAPE = "Esc"
