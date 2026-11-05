package app.nagmedar.ui.shell

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 1.0.3: how a phone's chrome is drawn, for the user's decision on glass (Desktop Kit 2.3). The
 * product draws [Solid], 1.0.1's decision: every bar one opaque colour whatever passes beneath it.
 * [Bottom] and [Kit] exist so that he can decide from screenshots of the real app: only a
 * debuggable build switches to them, by [EXTRA_CHROME_GLASS] on its launch intent ([DebugChrome]);
 * no setting shows them. Menus, sheets and dialogs stay solid in all three.
 */
enum class ChromeGlass {
    /** A: every bar opaque, the top bar never changing colour (the product). */
    Solid,

    /** B: the mini player, the bottom bar and the band behind the system's buttons on one glass; the top bar opaque. */
    Bottom,

    /** C, the kit's: [Bottom], and a top bar that fades to glass while content passes under it. */
    Kit;

    companion object {
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
 * The [ChromeGlass] of a debuggable build, as its last launch intent asked ([EXTRA_CHROME_GLASS]);
 * [ChromeGlass.Solid] otherwise. A build that is not debuggable (the personal one) never leaves it.
 */
object DebugChrome {
    var glass by mutableStateOf(ChromeGlass.Solid)
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
