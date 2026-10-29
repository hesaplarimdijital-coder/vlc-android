package app.nagmedar.ui.shell

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.SampleScreen
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.screens.MoreAction
import app.nagmedar.ui.screens.MoreActionGroup
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * 1.0.1 on a phone: "⋯" is the "Daha fazla" page; Settings opens as a page of its own, without the
 * bottom bar and the mini player, with a back arrow; Back returns to the page that opened it; a page
 * lights the tab it was opened in; the top bar keeps its colour while a list scrolls beneath it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class PhoneNavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navigator: ShellNavigator

    private fun phone(theme: ThemeMode = ThemeMode.Light) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, screen = { destination, nav ->
                    navigator = nav
                    when (destination) {
                        // a long list lying under the bars, its rows dark and light by turns
                        Destination.Music -> {
                            val padding = rememberShellContentPadding()
                            LazyColumn(Modifier.fillMaxSize().testTag("tracks"), contentPadding = padding) {
                                items((1..80).toList()) { i ->
                                    Text("Parça $i", Modifier.fillMaxWidth().height(56.dp).background(if (i % 2 == 0) Color(0xFF102030) else Color(0xFFE0C040)))
                                }
                            }
                        }
                        Destination.Settings -> Box(Modifier.fillMaxSize().testTag("settings-page"))
                        else -> SampleScreen(
                            destination,
                            nav,
                            listOf(
                                MoreActionGroup(
                                    "Oynatma",
                                    listOf(MoreAction("Ekolayzer", NagIcons.Equalizer, { nav.open(Destination.Equalizer) }, opensPage = true)),
                                ),
                            ),
                        )
                    }
                })
            }
        }
        compose.waitForIdle()
    }

    private fun tab(label: String) = compose.onNode(hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun theMoreTabIsAPage() {
        phone()
        tab("Daha fazla").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("more-page").assertIsDisplayed()
        tab("Daha fazla").assertIsSelected()
        listOf("Kütüphane", "Çalma listeleri", "Kaynaklar", "Bu cihaz", "Ağ", "Akışlar", "Araçlar", "İndirmeler", "Ayarlar", "Oynatma", "Ekolayzer")
            .forEach { compose.onNodeWithText(it).assertExists() }
        // a tab's first page: no back arrow
        compose.onNodeWithContentDescription("Geri").assertDoesNotExist()
    }

    @Test
    fun settingsIsAPageOfItsOwnAndBackReturnsToMore() {
        phone()
        tab("Daha fazla").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ayarlar").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("more-page").assertDoesNotExist()
        // the page reaches the window's bottom: no bottom bar, no mini player
        val root = compose.onRoot().getBoundsInRoot()
        val page = compose.onNodeWithTag("settings-page").getBoundsInRoot()
        assertTrue("page bottom ${page.bottom} vs ${root.bottom}", abs((root.bottom - page.bottom).value) < 1f)
        compose.onNodeWithText("Radyo").assertDoesNotExist()
        compose.onNodeWithTag("mini-player").assertDoesNotExist()
        compose.onNodeWithContentDescription("Geri").assertIsDisplayed()
        // the system's Back: "Daha fazla" again, with its bar
        back()
        compose.onNodeWithTag("more-page").assertIsDisplayed()
        tab("Radyo").assertIsDisplayed()
        // the arrow does the same
        compose.onNodeWithText("Ayarlar").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Geri").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("more-page").assertIsDisplayed()
        // and Back from "Daha fazla" goes to Müzik
        back()
        compose.onNodeWithTag("tracks").assertIsDisplayed()
        tab("Müzik").assertIsSelected()
    }

    @Test
    fun aPageLightsTheTabItWasOpenedIn() {
        phone()
        tab("Daha fazla").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ekolayzer").performClick()
        compose.waitForIdle()
        assertEquals(Destination.Equalizer.route, navigator.currentRoute)
        tab("Daha fazla").assertIsSelected()
        tab("Müzik").assertIsNotSelected()
        // a content page keeps the bar and the mini player, with the arrow
        compose.onNodeWithTag("mini-player").assertIsDisplayed()
        compose.onNodeWithContentDescription("Geri").assertIsDisplayed()
        // the equaliser opened while in Müzik (the player's button): Müzik
        tab("Müzik").performClick()
        compose.waitForIdle()
        compose.runOnIdle { navigator.open(Destination.Equalizer) }
        compose.waitForIdle()
        tab("Müzik").assertIsSelected()
        tab("Daha fazla").assertIsNotSelected()
    }

    @Test
    fun theTabChosenAgainGoesToItsFirstPage() {
        phone()
        tab("Daha fazla").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ağ").performClick()
        compose.waitForIdle()
        assertEquals(Destination.Network.route, navigator.currentRoute)
        // another tab and back: the page is still there
        tab("Radyo").performClick()
        compose.waitForIdle()
        tab("Daha fazla").performClick()
        compose.waitForIdle()
        assertEquals(Destination.Network.route, navigator.currentRoute)
        // the tab on screen chosen again
        tab("Daha fazla").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("more-page").assertIsDisplayed()
    }

    @Test
    fun theTopBarKeepsItsColourWhileTheListScrolls() {
        phone(ThemeMode.Light)
        val atRest = topBarPixel()
        compose.onNodeWithTag("tracks").performTouchInput { swipeUp(startY = bottom - 300f, endY = top + 300f, durationMillis = 400) }
        compose.waitForIdle()
        val scrolled = topBarPixel()
        assertEquals("top bar ${Integer.toHexString(atRest)} → ${Integer.toHexString(scrolled)}", atRest, scrolled)
        // and in between, while the content moves the other way
        compose.onNodeWithTag("tracks").performTouchInput { swipeUp(startY = top + 300f, endY = bottom - 300f, durationMillis = 400) }
        compose.waitForIdle()
        assertEquals(atRest, topBarPixel())
    }

    /** The colour of the top bar between its title and its search button, from the window drawn into a bitmap. */
    private fun topBarPixel(): Int {
        val title = compose.onNode(hasText("Müzik") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).getBoundsInRoot()
        val density = compose.activity.resources.displayMetrics.density
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        val content = IntArray(2).also { compose.activity.findViewById<android.view.View>(android.R.id.content).getLocationInWindow(it) }
        val x = ((title.right.value + 24f) * density).toInt() + content[0]
        val y = (((title.top + title.bottom).value / 2f) * density).toInt() + content[1]
        return bitmap.getPixel(x, y)
    }
}
