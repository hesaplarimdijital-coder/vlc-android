package app.nagmedar.ui.music

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.AppOwnTop
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.screenshots.ShellScreenshotTest
import app.nagmedar.ui.shell.OnReselect
import app.nagmedar.ui.shell.LocalWidthClass
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.1.3: Radio's and Podcasts' ebru top. On a phone the brand's chip is the band's mark alone while the band lies under
 * it, and names the tab the page is on once the list has taken the band away; on a tablet the band and the masthead fold
 * away as the list scrolls, the tabs staying at the top.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EbruPageTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** The window as it is drawn now, for approval. */
    private fun capture(name: String) {
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    private var width: WidthClass? by mutableStateOf(null)
    private lateinit var list: LazyListState
    private var stripTaps = 0

    private fun page(input: InputMode = InputMode.Touch) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                val shown = width
                if (shown != null) CompositionLocalProvider(LocalWidthClass provides shown) { Page() } else Page()
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun Page() {
        list = rememberLazyListState()
        Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
            EbruPage(
                title = "Radyo",
                subtitle = "Radio Browser · topluluk rehberi",
                section = "Favoriler",
                onSearch = {},
                // the station on the air under the masthead, its button a tap target
                header = { Box(Modifier.fillMaxWidth().height(56.dp).clickable { stripTaps++ }.testTag("strip")) },
                pinned = {
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp).testTag("tabs"),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf("Favoriler", "Son çalınanlar", "Ara").forEach { Text(it, color = NagTheme.colors.text, style = NagTheme.type.body) }
                    }
                },
            ) {
                LazyColumn(Modifier.fillMaxSize().testTag("list"), state = list) {
                    items(60) { i ->
                        Text("İstasyon $i", Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), color = NagTheme.colors.text, style = NagTheme.type.body)
                    }
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun aPhonesChipNamesTheTabOnceTheBandHasGone() {
        page()
        compose.onNodeWithText("Radyo").assertExists()
        compose.onNodeWithTag("music-section", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("music-search").assertExists()
        // no options: no chip for them
        compose.onNodeWithTag("music-options").assertDoesNotExist()
        compose.onNodeWithTag("list").performTouchInput { swipeUp(startY = bottom - 20f, endY = top + 20f, durationMillis = 800) }
        compose.waitForIdle()
        compose.onNodeWithTag("music-section", useUnmergedTree = true).assertTextEquals("Favoriler")
        capture("ebru-top-phone-dark-scrolled")
        // the tabs under the chips (the status bar's room is none here)
        assertEquals(PHONE_CHIPS_ROOM.value, compose.onNodeWithTag("tabs").getUnclippedBoundsInRoot().top.value, 1f)
        repeat(3) { compose.onNodeWithTag("list").performTouchInput { swipeDown(startY = top + 20f, endY = bottom - 20f, durationMillis = 800) } }
        compose.waitForIdle()
        compose.onNodeWithTag("music-section", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "tr-w1280dp-h900dp-mdpi")
    fun aTabletsBandAndMastheadFoldAwayAndTheTabsStay() {
        page()
        val start = compose.onNodeWithTag("tabs").getUnclippedBoundsInRoot().top.value
        // the tall band and the masthead over the tabs
        assertTrue(start > TABLET_BAND.value)
        compose.onNodeWithTag("list").performTouchInput { swipeUp(startY = bottom - 20f, endY = top + 20f, durationMillis = 800) }
        compose.waitForIdle()
        assertEquals(0f, compose.onNodeWithTag("tabs").getUnclippedBoundsInRoot().top.value, 1f)
        assertEquals(48f, compose.onNodeWithTag("list").getUnclippedBoundsInRoot().top.value, 1f)
        capture("ebru-top-tablet-dark-folded")
        repeat(3) { compose.onNodeWithTag("list").performTouchInput { swipeDown(startY = top + 20f, endY = bottom - 20f, durationMillis = 800) } }
        compose.waitForIdle()
        assertEquals(start, compose.onNodeWithTag("tabs").getUnclippedBoundsInRoot().top.value, 1f)
    }

    /** A phone turned (or a DeX window crossing a phone's width): the list stays where it was. */
    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun theListKeepsItsPlaceAcrossWidths() {
        width = WidthClass.Compact
        page()
        repeat(2) { compose.onNodeWithTag("list").performTouchInput { swipeUp(startY = bottom - 20f, endY = top + 20f, durationMillis = 800) } }
        compose.waitForIdle()
        val first = list.firstVisibleItemIndex
        assertTrue(first > 5)
        width = WidthClass.Medium
        compose.waitForIdle()
        assertEquals(first, list.firstVisibleItemIndex)
        width = WidthClass.Compact
        compose.waitForIdle()
        assertEquals(first, list.firstVisibleItemIndex)
    }

    /** Folded under a phone's chips, the faded masthead and the station's strip take no touch and are not read out. */
    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun whatHasFadedUnderTheChipsTakesNoTouch() {
        page()
        compose.onNodeWithTag("strip").performClick()
        assertEquals(1, stripTaps)
        compose.onNodeWithTag("list").performTouchInput { swipeUp(startY = bottom - 20f, endY = top + 20f, durationMillis = 800) }
        compose.waitForIdle()
        // the strip is gone from what TalkBack reads; a tap where it lies now does nothing
        compose.onNodeWithTag("strip").assertDoesNotExist()
        val strip = compose.onNodeWithTag("ebru-header", useUnmergedTree = true).getUnclippedBoundsInRoot()
        compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, (strip.bottom.value - 30f) * density)) }
        compose.waitForIdle()
        assertEquals(1, stripTaps)
    }

    /** A phone on its side: no band in so short a window; its search field's place is a button beside the masthead. */
    @Test
    @Config(qualifiers = "tr-w891dp-h411dp-xhdpi")
    fun aShortWindowHasTheSearchButtonInTheBandsPlace() {
        page()
        compose.onNodeWithTag("music-search").assertExists()
        compose.onNodeWithText("Radyo").assertExists()
    }

    /** The page's tab chosen again takes its list to the top: the folded top comes down with it. */
    @Test
    @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun choosingTheTabAgainBringsTheTopDown() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                // (on the start tab, the one the bar has chosen; the shell's own pages reach the others by their tab)
                TestShell(WidthClass.Compact, ownTop = AppOwnTop) { _, _ ->
                    list = rememberLazyListState()
                    val shown = list
                    OnReselect { shown.scrollToItem(0) }
                    EbruPage(title = "Radyo", subtitle = null, section = "Favoriler") {
                        LazyColumn(Modifier.fillMaxSize().testTag("list"), state = list) {
                            items(60) { i -> Text("İstasyon $i", Modifier.fillMaxWidth().height(56.dp)) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val shown = compose.onNodeWithTag("ebru-header", useUnmergedTree = true).getUnclippedBoundsInRoot().top.value
        // (from the list's middle: its end lies under the shell's bars)
        repeat(2) { compose.onNodeWithTag("list").performTouchInput { swipeUp(startY = centerY, endY = top + 20f, durationMillis = 800) } }
        compose.waitForIdle()
        assertTrue(compose.onNodeWithTag("ebru-header", useUnmergedTree = true).getUnclippedBoundsInRoot().top.value < shown - 100f)
        // the page's tab of the bottom bar, again
        // a little way back up brings the bottom bar back (it slid away as the list went down); the top stays folded
        compose.onNodeWithTag("list").performTouchInput { swipeDown(startY = centerY - 200f, endY = centerY + 200f, durationMillis = 800) }
        compose.waitForIdle()
        assertTrue(list.firstVisibleItemIndex > 0)
        assertTrue(compose.onNodeWithTag("ebru-header", useUnmergedTree = true).getUnclippedBoundsInRoot().top.value < shown - 100f)
        compose.onNode(hasText("Müzik") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performClick()
        compose.waitForIdle()
        assertEquals(0, list.firstVisibleItemIndex)
        assertEquals(shown, compose.onNodeWithTag("ebru-header", useUnmergedTree = true).getUnclippedBoundsInRoot().top.value, 1f)
    }
}
