package app.nagmedar.ui.shell

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import app.nagmedar.ui.theme.InputMode
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
 * 1.0.9 (Desktop Kit 2.9.0's window edge): the phone's chrome reaches the window's bottom edge, with nothing of the page
 * and nothing of the root's own background below it. The bottom glass (the user's B: behind the mini player, the bottom
 * bar and the band behind the system's buttons) and the band reach the edge with three-button and gesture navigation,
 * with the bars shown and slid away by a scroll, on a 1440 px phone, on a page of Settings (the bars stand aside), and
 * when the system's bottom insets do not agree with each other: a cutout or another system bar at the bottom taller
 * than the navigation bar, whose extra pixels the root used to pad as a strip of its own background under the chrome.
 *
 * The window is drawn as [SystemBandTest] draws it (the view into a bitmap), where glass is its translucent form over a
 * page that is green from end to end; a pixel still clearly green is the page without the chrome over it. The kit's
 * same test draws with the hardware renderer and the blur on, which here stopped at the drawing (its first run, CI
 * 43519261081): Robolectric's native runtime for API 34, the Android these tests run on, does not compile the shader of
 * Haze's noise and tint ("'color' is not a valid layout qualifier"), and Robolectric's Android 16, where the kit runs
 * it, needs Java 21; the app's tests run on 17. So 1.0.5's opaque colour under the blur is not looked at here.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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

    /** The app's root around the phone's shell in the dark palette, with the mini player as the app has it, [start] over a green page. */
    private fun phone(start: Destination = Destination.Music) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                WindowRoot(shell = true, belowCaption = false, modifier = Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                    TestShell(WidthClass.Compact, startDestination = start, screen = { _, _ ->
                        val padding = rememberShellContentPadding()
                        LazyColumn(Modifier.fillMaxSize().testTag("list"), rememberLazyListState(10), padding) {
                            items(120) { Box(Modifier.fillMaxWidth().height(56.dp).background(PAGE)) }
                        }
                    })
                }
            }
        }
        compose.waitForIdle()
    }

    /** The window as drawn, from the content view's top left. */
    private fun window(): Bitmap {
        val decor = compose.activity.window.decorView
        val full = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { decor.draw(Canvas(full)) }
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

    /**
     * The band behind the system's [tallest] bottom inset is one surface down to the window's last row and to its last
     * column: every row there as the band's first row below its rule, in the middle and at the end (no strip of the
     * root's own background under it).
     */
    private fun assertBandToTheEdge(bitmap: Bitmap, tallest: Int, what: String) {
        val top = bitmap.height - tallest + RULE_PX
        val band = bitmap.getPixel(bitmap.width / 2, top)
        val other = (top until bitmap.height).flatMap { y -> listOf(bitmap.width / 2, bitmap.width - 1).map { x -> Triple(x, y, bitmap.getPixel(x, y)) } }
            .filter { (_, _, argb) -> argb != band }
        assertTrue(
            "$what: the band (${hex(band)}) ends before the window's edge: ${other.take(4).map { (x, y, argb) -> "($x, $y) ${hex(argb)}" }}",
            other.isEmpty(),
        )
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
        val shown = window()
        assertStatusBarClear(shown, STATUS_PX)
        assertCoveredFrom(shown, glassTopPx() + RULE_PX, "three buttons, bars shown")
        assertBandToTheEdge(shown, 144, "three buttons, bars shown")

        scrollTheBarsAway()
        glassBottomReachesTheWindow("bars slid away")
        val away = window()
        assertStatusBarClear(away, STATUS_PX)
        assertCoveredFrom(away, glassTopPx() + RULE_PX, "three buttons, bars slid away")
        assertBandToTheEdge(away, 144, "three buttons, bars slid away")
    }

    /** Gesture navigation: a 16 dp handle. */
    @Test
    @Config(qualifiers = "tr-w360dp-h780dp-xxhdpi")
    fun gestureNavigation() {
        phone()
        systemBars(status = STATUS_PX, navigation = 48, threeButtons = false)
        glassBottomReachesTheWindow("bars shown")
        val shown = window()
        assertCoveredFrom(shown, glassTopPx() + RULE_PX, "gesture handle, bars shown")
        assertBandToTheEdge(shown, 48, "gesture handle, bars shown")

        scrollTheBarsAway()
        glassBottomReachesTheWindow("bars slid away")
        val away = window()
        assertCoveredFrom(away, glassTopPx() + RULE_PX, "gesture handle, bars slid away")
        assertBandToTheEdge(away, 48, "gesture handle, bars slid away")
    }

    /** A wider phone (1440 px): the chrome also reaches the window's right edge. */
    @Test
    @Config(qualifiers = "tr-w480dp-h1040dp-xxhdpi")
    fun widerPhone() {
        phone()
        systemBars(status = STATUS_PX, navigation = 144, threeButtons = true)
        glassBottomReachesTheWindow("bars shown")
        val bitmap = window()
        assertCoveredFrom(bitmap, glassTopPx() + RULE_PX, "1440 px wide, bars shown")
        assertBandToTheEdge(bitmap, 144, "1440 px wide, bars shown")
    }

    /** A page of Settings: the bottom bar and the mini player stand aside (1.0.1), the band alone down to the edge. */
    @Test
    @Config(qualifiers = "tr-w360dp-h780dp-xxhdpi")
    fun aSettingsPage() {
        phone(start = Destination.Settings)
        systemBars(status = STATUS_PX, navigation = 144, threeButtons = true)
        glassBottomReachesTheWindow("Settings")
        val bitmap = window()
        assertCoveredFrom(bitmap, minOf(glassTopPx(), bitmap.height - 144) + RULE_PX, "Settings, the bars aside")
        assertBandToTheEdge(bitmap, 144, "Settings, the bars aside")
    }

    /** The system's bottom insets disagree (a bottom cutout taller than the navigation bar): the chrome covers the taller. */
    @Test
    @Config(qualifiers = "tr-w360dp-h780dp-xxhdpi")
    fun bottomInsetsThatDisagree() {
        phone()
        systemBars(status = STATUS_PX, navigation = 144, threeButtons = true, cutout = 150)
        glassBottomReachesTheWindow("cutout taller than the navigation bar")
        val bitmap = window()
        assertCoveredFrom(bitmap, bitmap.height - 150, "cutout taller than the navigation bar")
        assertBandToTheEdge(bitmap, 150, "cutout taller than the navigation bar")
    }

    private companion object {
        val PAGE = Color(0xFF00FF00)
        const val STATUS_PX = 117 // 39 dp
        const val RULE_PX = 6 // a rule along the chrome's top (1 dp) and a pixel either side
    }
}
