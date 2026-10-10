package app.nagmedar.ui.lab

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.theme.NagTheme

/** A lab report: monospaced, selectable, with copy and share for the spike's decision note. */
@Composable
fun ReportDialog(title: String, report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.8f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(dimens.gapL)
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .background(colors.surfaceRaised, shape)
                .border(1.dp, colors.borderStrong, shape)
                .padding(dimens.gapL),
        ) {
            Text(title, style = NagTheme.type.title, color = colors.text, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(dimens.gapS))
            HorizontalRule()
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = dimens.gapM),
            ) {
                SelectionContainer {
                    Text(report, style = NagTheme.type.mono, color = colors.textSecondary)
                }
            }
            HorizontalRule()
            Spacer(Modifier.height(dimens.gapM))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimens.gapS, androidx.compose.ui.Alignment.End)) {
                NagButton(stringResource(R.string.nag_lab_copy), { clipboard.setText(AnnotatedString(report)) })
                NagButton(stringResource(R.string.nag_lab_share), { context.shareText(title, report) })
                NagButton(stringResource(R.string.nag_lab_close), onDismiss, primary = true)
            }
        }
    }
}

private fun Context.shareText(subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(send, subject).apply { if (findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
}

/** The activity behind a Compose context (it may be wrapped for theming). */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
