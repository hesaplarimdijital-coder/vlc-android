package app.nagmedar.ui.shell

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 1.0.3: the phone's chrome is the user's decision of 6 Nov, its bottom on one glass (B) in the
 * product, the personal build included; only a debuggable build's launch intent switches it to the
 * others for the walk's comparisons ([DebugChrome]); the system's navigation bar counts in a phone's
 * bottom padding, full pages too, and not on a tablet.
 */
@RunWith(AndroidJUnit4::class)
class ChromeGlassTest {

    @After
    fun backToTheProduct() {
        DebugChrome.follow(ApplicationProvider.getApplicationContext(), Intent().putExtra(EXTRA_CHROME_GLASS, "bottom"))
    }

    @Test
    fun theModesByTheirNamesAndLetters() {
        assertEquals(ChromeGlass.Solid, ChromeGlass.parse("solid"))
        assertEquals(ChromeGlass.Solid, ChromeGlass.parse("A"))
        assertEquals(ChromeGlass.Bottom, ChromeGlass.parse(" bottom "))
        assertEquals(ChromeGlass.Bottom, ChromeGlass.parse("b"))
        assertEquals(ChromeGlass.Kit, ChromeGlass.parse("KIT"))
        assertEquals(ChromeGlass.Kit, ChromeGlass.parse("c"))
        assertNull(ChromeGlass.parse(null))
        assertNull(ChromeGlass.parse("glass"))
    }

    @Test
    fun theProductIsTheBottomGlass() {
        assertEquals(ChromeGlass.Bottom, ChromeGlass.Product)
        assertEquals(ChromeGlass.Product, DebugChrome.glass)
    }

    @Test
    fun anIntentWithoutTheExtraChangesNothing() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        DebugChrome.follow(context, Intent())
        DebugChrome.follow(context, null)
        DebugChrome.follow(context, Intent().putExtra(EXTRA_CHROME_GLASS, "frosted"))
        assertEquals(ChromeGlass.Product, DebugChrome.glass)
    }

    @Test
    fun aDebuggableBuildFollowsTheExtra() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // the unit tests' application is the debug build's, debuggable
        val debuggable = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        DebugChrome.follow(context, Intent().putExtra(EXTRA_CHROME_GLASS, "kit"))
        assertEquals(if (debuggable) ChromeGlass.Kit else ChromeGlass.Product, DebugChrome.glass)
        // and back to the opaque bars of 1.0.1, the walk's A
        DebugChrome.follow(context, Intent().putExtra(EXTRA_CHROME_GLASS, "a"))
        assertEquals(if (debuggable) ChromeGlass.Solid else ChromeGlass.Product, DebugChrome.glass)
    }

    @Test
    fun aNonDebuggableBuildKeepsTheProduct() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val flags = context.applicationInfo.flags
        context.applicationInfo.flags = flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE.inv()
        try {
            DebugChrome.follow(context, Intent().putExtra(EXTRA_CHROME_GLASS, "solid"))
            assertEquals(ChromeGlass.Product, DebugChrome.glass)
            DebugChrome.follow(context, Intent().putExtra(EXTRA_CHROME_GLASS, "kit"))
            assertEquals(ChromeGlass.Product, DebugChrome.glass)
        } finally {
            context.applicationInfo.flags = flags
        }
    }

    @Test
    fun thePhonesBottomPaddingIncludesTheButtons() {
        val metrics = ShellChromeMetrics().apply {
            underBottom = true
            navPx = 160
            playerPx = 240
            systemBottomPx = { 126 }
        }
        assertEquals(160 + 240 + 126, metrics.bottomPadPx(bars = true))
        // a full page (Settings) has no bottom bar and no mini player, but the buttons still cover its foot
        assertEquals(126, metrics.bottomPadPx(bars = false))
    }

    @Test
    fun aTabletsContentEndsAboveTheButtons() {
        val metrics = ShellChromeMetrics().apply {
            underFloatingPlayer = true
            playerPx = 80
            floatingMarginPx = 12
            systemBottomPx = { 72 }
        }
        // the tablet's content is laid out above the system's bar: only the floating player covers it
        assertEquals(80 + 2 * 12, metrics.bottomPadPx(bars = true))
    }
}
