package app.nagmedar.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import org.videolan.tools.Notices

/**
 * Puts [text] on the clipboard under [label] and says so with [notice]: below Android 13 only, since from 13
 * (TIRAMISU) the system shows its own confirmation of every copy and a second notice of the app's is one too many.
 */
fun copyToClipboard(context: Context, label: CharSequence?, text: String, @StringRes notice: Int) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Notices.show(context, notice)
}
