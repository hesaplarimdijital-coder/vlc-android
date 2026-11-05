package app.nagmedar.ui.shell

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 1.0.3: how a phone's chrome is drawn (Desktop Kit 2.3's glass). The product draws [Bottom], the
 * user's decision of 6 Nov ("B olsun", from screenshots of the three on the emulator): the mini
 * player's card, the bottom bar and the band behind the system's buttons lie on the shell's one
 * glass; the top bar stays opaque and never changes colour (1.0.1's decision); menus, sheets and
 * dialogs stay solid. Tablets and DeX are as they were. Where the device draws no glass (battery
 * saver, "Animasyonları kaldır", no hardware acceleration) the glass is the opaque chrome colour.
 * [Solid] and [Kit] stay for the emulator walk's comparisons: only a debuggable build switches to
 * them, by [EXTRA_CHROME_GLASS] on its launch intent ([DebugChrome]); no setting shows them.
 */
enum class ChromeGlass {
    /** A: every bar opaque, the top bar never changing colour (1.0.1–1.0.2). */
    Solid,

    /** B, the product: the mini player, the bottom bar and the band behind the system's buttons on one glass; the top bar opaque. */
    Bottom,

    /** C, the kit's: [Bottom], and a top bar that fades to glass while content passes under it. */
    Kit;

    companion object {
        /** The product's mode, the user's decision of 6 Nov: [Bottom]. */
        val Product: ChromeGlass = Bottom

        /** The mode an intent extra names ("solid", "bottom", "kit"; or "a", "b", "c"); null for none. */
        fun parse(value: String?): ChromeGlass? = when (value?.trim()?.lowercase()) {
            "solid", "a" -> Solid
            "bottom", "b" -> Bottom
            "kit", "c" -> Kit
            else -> null
        }
    }
}

/** The launch intent's extra that switches a debuggable build's [ChromeGlass] (the emulator walk's glass pass). */
const val EXTRA_CHROME_GLASS = "app.nagmedar.debug.CHROME_GLASS"

/**
 * The app's [ChromeGlass]: the product's ([ChromeGlass.Product]), or in a debuggable build what its
 * last launch intent asked ([EXTRA_CHROME_GLASS]). A build that is not debuggable (the personal
 * one) never leaves the product's.
 */
object DebugChrome {
    var glass by mutableStateOf(ChromeGlass.Product)
        private set

    /** Takes the mode [intent] asks for, in a debuggable build only; an intent without the extra changes nothing. */
    fun follow(context: Context, intent: Intent?) {
        val asked = ChromeGlass.parse(intent?.getStringExtra(EXTRA_CHROME_GLASS)) ?: return
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        glass = asked
    }
}

/**
 * The phone's bottom chrome (the mini player's card, the bottom bar) lies on the shell's one bottom
 * glass ([ChromeGlass.Bottom], [ChromeGlass.Kit]): it draws no opaque surface of its own.
 */
val LocalOnBottomGlass = staticCompositionLocalOf { false }
