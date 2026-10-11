package app.nagmedar.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * S-204: the status and navigation bar icons follow the theme chosen in Settings, not only the
 * system's: the light theme on a dark system gets dark icons at once, and the other way round.
 * S-306: in a desktop window (Android 15) the caption bar's buttons follow it too, and when the
 * app [drawsCaption], the bar is transparent: the menu bar goes up into it.
 */
@Composable
fun SystemBarsFollowTheme(drawsCaption: Boolean = false) {
    val dark = NagTheme.colors.isDark
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, dark, drawsCaption) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val transparent = WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND
            val light = WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS
            activity.window.insetsController?.setSystemBarsAppearance(
                (if (drawsCaption) transparent else 0) or (if (dark) 0 else light),
                transparent or light,
            )
        }
        onDispose {}
    }
}

/** The navigation bar scrims of enableEdgeToEdge()'s defaults, for three-button navigation. */
private val LIGHT_SCRIM = android.graphics.Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = android.graphics.Color.argb(0x80, 0x1B, 0x1B, 0x1B)

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
