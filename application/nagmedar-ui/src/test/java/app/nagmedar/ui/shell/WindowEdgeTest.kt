package app.nagmedar.ui.shell

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.theme.GlassMode
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.LocalGlassMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.max

/**
 * 1.0.9 (Desktop Kit 2.9.0's window edge): nothing of the page shows below the phone's bars. The bottom glass (the
 * user's B: behind the mini player, the bottom bar and the band behind the system's buttons) reaches the window's
 * bottom edge, with three-button and gesture navigation, with the bars shown and slid away by a scroll, on a page of
 * Settings (the bars stand aside), and when the system's bottom insets do not agree with each other (a cutout or
 * another system bar at the bottom taller than the navigation bar: the root left the difference as a strip of its own
 * background under the chrome).
 *
 * The window is drawn by the hardware renderer, as PixelCopy draws it under Robolectric's native graphics, and the
 * glass blurs as on a phone ([GlassMode.Blur] pinned: the device's mode is the translucent one in tests). So the
 * blur's own drawing is what is looked at: Haze records what it blurs at 0.79 of the surface's size in whole pixels
 * and draws it back scaled up, up to 0.6 px short of the bottom or end edge; 1.0.5 put the glass's opaque colour under
 * the blur for it (the owner's S24, 12 Nov: "arkası gözüküyor"), which no test could look at then. Desktop Kit's same
 * test was red without that colour, on the S24's last pixel row and a 1440 px phone's last column.
 *
 * The page is one green, which the chrome never is: through the glass it is blurred and tinted to a dark tone, a pixel
 * still clearly green is the page drawn without the chrome over it.
 *
 * On Android 16 (API 36), as the kit's: Robolectric's native runtime for the app's usual API 34 does not compile the
 * shader of Haze's noise and tint ("'color' is not a valid layout qualifier"), so the hardware renderer stopped there.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class WindowEdgeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var view: View

    /** The system bars: a status bar of [status] px, a navigation bar of [navigation] px, a bottom cutout of [cutout] px. */
    private fun systemBars(status: Int, navigation: Int, threeButtons: Boolean, cutout: Int = 0) {
        val builder = WindowInsetsCompat.Builder()
            .setInsets(Type.statusBars(), Insets.of(0, status, 0, 0))
            .setVisible(Type.statusBars(), true)
            .setInsets(Type.navigationBars(), Insets.of(0, 0, 0, navigation))
            .setVisible(Type.navigationBars(), true)
            .setInsets(Type.tappableElement(), Insets.of(0, status, 0, if (threeButtons) navigation else 0))
            .setInsets(Type.mandatorySystemGestures(), Insets.of(0, status, 0, navigation))
            .setInsets(Type.systemGestures(), Insets.of(if (threeButtons) 0 else 60, status, if (threeButtons) 0 else 60, navigation))
        if (cutout > 0) builder.setInsets(Type.displayCutout(), Insets.of(0, 0, 0, cutout))
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, builder.build()) }
        settle()
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    /**
     * The app's root around the phone's shell in the dark palette, blurring, with the mini player as the app has it,
     * [start] open over a list that is green from end to end.
     */
    private fun phone(start: Destination = Destination.Music) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                WindowRoot(shell = true, belowCaption = false, modifier = Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                    CompositionLocalProvider(LocalGlassMode provides GlassMode.Blur) {
                        TestShell(WidthClass.Compact, startDestination = start, screen = { _, _ ->
                            val padding = rememberShellContentPadding()
                            LazyColumn(Modifier.fillMaxSize().testTag("list"), rememberLazyListState(10), padding) {
                                items(120) { Box(Modifier.fillMaxWidth().height(56.dp).background(PAGE)) }
                            }
                        })
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The window as the hardware renderer draws it, from the content view's top left. */
    private fun capture(): Bitmap {
        val window = compose.activity.window
        val decor = window.decorView
        val full = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { PixelCopy.request(window, full, {}, Handler(Looper.getMainLooper())) }
        val at = IntArray(2).also { view.getLocationInWindow(it) }
        return Bitmap.createBitmap(full, at[0], at[1], view.width, view.height)
    }

    /** A pixel of the page seen without the chrome (or through too little of it). */
    private fun isPage(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return g - max(r, b) > 90
    }

    private fun hex(argb: Int) = "#%06X".format(argb and 0xFFFFFF)

    /** No pixel of the page in rows [top] until the window's bottom, across the whole width. */
    private fun assertCoveredFrom(bitmap: Bitmap, top: Int, what: String) {
        val leaks = (top until bitmap.height).mapNotNull { y ->
            val xs = (0 until bitmap.width).filter { isPage(bitmap.getPixel(it, y)) }
            if (xs.isEmpty()) null else "row $y: ${xs.size} px from x ${xs.first()} (${hex(bitmap.getPixel(xs.first(), y))})"
        }
        assertTrue("$what: the page shows under the chrome (${leaks.size} rows): ${leaks.take(4)}", leaks.isEmpty())
    }

    /** Above the shell: the status bar shows the root's background, never the page. */
    private fun assertStatusBarClear(bitmap: Bitmap, status: Int) {
        val leaks = (0 until status).count { y -> (0 until bitmap.width).any { isPage(bitmap.getPixel(it, y)) } }
        assertTrue("the page shows under the status bar in $leaks rows", leaks == 0)
    }

    private fun glassBottomReachesTheWindow(what: String) {
        val window = compose.onRoot().getBoundsInRoot()
        val glass = compose.onNodeWithTag(BOTTOM_GLASS_TAG, useUnmergedTree = true).getBoundsInRoot()
        assertTrue("$what: the bottom glass ends at ${glass.bottom}, the window at ${window.bottom}", glass.bottom >= window.bottom - 0.5.dp)
    }

    /** Where the bottom glass begins now, in the window's pixels: the bars' top, or the band's once they stand aside. */
    private fun glassTopPx(): Int = with(compose.density) {
        compose.onNodeWithTag(BOTTOM_GLASS_TAG, useUnmergedTree = true).getBoundsInRoot().top.roundToPx()
    }

    private fun scrollTheBarsAway() {
        compose.onNodeWithTag("list").performTouchInput { swipeUp(startY = centerY + 300f, endY = top + 200f, durationMillis = 800) }
        settle()
    }

    /** The owner's S24: 1080 × 2340 at density 3, three-button navigation (48 dp). */
    @Test
    @Config(qualifiers = "tr-w360dp-h780dp-xxhdpi")
    fun threeButtonNavigation() {
        phone()
        systemBars(status = STATUS_PX, navigation = 144, threeButtons = true)
        glassBottomReachesTheWindow("bars shown")
        val shown = capture()
        assertStatusBarClear(shown, STATUS_PX)
        assertCoveredFrom(shown, glassTopPx() + RULE_PX, "three buttons, bars shown")

        scrollTheBarsAway()
        glassBottomReachesTheWindow("bars slid away")
        val away = capture()
        assertStatusBarClear(away, STATUS_PX)
        assertCoveredFrom(away, glassTopPx() + RULE_PX, "three buttons, bars slid away")
    }

    /** Gesture navigation: a 16 dp handle. */
    @Test
    @Config(qualifiers = "tr-w360dp-h780dp-xxhdpi")
    fun gestureNavigation() {
        phone()
        systemBars(status = STATUS_PX, navigation = 48, threeButtons = false)
        glassBottomReachesTheWindow("bars shown")
        assertCoveredFrom(capture(), glassTopPx() + RULE_PX, "gesture handle, bars shown")

        scrollTheBarsAway()
        glassBottomReachesTheWindow("bars slid away")
        assertCoveredFrom(capture(), glassTopPx() + RULE_PX, "gesture handle, bars slid away")
    }

    /** A wider phone (1440 px): the glass also reaches the window's right edge. */
    @Test
    @Config(qualifiers = "tr-w480dp-h1040dp-xxhdpi")
    fun widerPhone() {
        phone()
        systemBars(status = STATUS_PX, navigation = 144, threeButtons = true)
        glassBottomReachesTheWindow("bars shown")
        assertCoveredFrom(capture(), glassTopPx() + RULE_PX, "1440 px wide, bars shown")
    }

    /** A page of Settings: the bottom bar and the mini player stand aside (1.0.1), the band alone down to the edge. */
    @Test
    @Config(qualifiers = "tr-w360dp-h780dp-xxhdpi")
    fun aSettingsPage() {
        phone(start = Destination.Settings)
        systemBars(status = STATUS_PX, navigation = 144, threeButtons = true)
        glassBottomReachesTheWindow("Settings")
        val bitmap = capture()
        assertCoveredFrom(bitmap, minOf(glassTopPx(), bitmap.height - 144) + RULE_PX, "Settings, the bars aside")
    }

    /** The system's bottom insets disagree (a bottom cutout taller than the navigation bar): the chrome covers the taller. */
    @Test
    @Config(qualifiers = "tr-w360dp-h780dp-xxhdpi")
    fun bottomInsetsThatDisagree() {
        phone()
        systemBars(status = STATUS_PX, navigation = 144, threeButtons = true, cutout = 150)
        glassBottomReachesTheWindow("cutout taller than the navigation bar")
        val bitmap = capture()
        assertCoveredFrom(bitmap, bitmap.height - 150, "cutout taller than the navigation bar")
    }

    private companion object {
        val PAGE = Color(0xFF00FF00)
        const val STATUS_PX = 117 // 39 dp
        const val RULE_PX = 6 // a rule along the chrome's top (1 dp) and a pixel either side
    }
}
