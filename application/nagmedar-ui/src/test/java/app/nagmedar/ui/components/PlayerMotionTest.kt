package app.nagmedar.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.playback.ProgressTimeline
import app.nagmedar.ui.playback.TimelineStyle
import app.nagmedar.ui.playback.rememberSmoothProgress
import app.nagmedar.ui.shell.BackButton
import app.nagmedar.ui.shell.ChromeGlass
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.holdBackButton
import app.nagmedar.ui.shell.releaseBackButton
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 1.0.3 (Desktop Kit 2.3.0's #52 and #48 on the app's floating card), frame by frame in the phone's shell: a tap grows
 * the strip's card into the page, the surface where [PlayerSurface] has it on every frame, its colour going from the
 * card's to the page's, its corners round on the way; the artwork marked with [playerSharedElement] flies from the
 * strip to the full player and back, and the strip's copy in the sheet is not placed once it has faded; with the bars
 * hidden by a scroll the player opens from where the strip is drawn, the bars stay, and it lands there again. On the
 * shell's bottom glass (the user's glass B) the card is the glass, the page's colour coming over it. A timeline
 * composed while a track plays starts at the latest reading (the sheet's first frame showed "0:00 / −4:18"), and a
 * file of an unknown length shows the time played alone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class PlayerMotionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var player: ExpandablePlayerState
    private var cardColor = Color.Unspecified
    private var pageColor = Color.Unspecified

    /** The artwork's colour: the same picture on the strip and in the full player. */
    private val cover = Color(0xFFD08C2E)

    /** The screen behind the player, in a colour neither the card nor the page has. */
    private val screenColor = Color(0xFF2E6F95)

    private fun phone(glass: ChromeGlass = ChromeGlass.Solid, screen: @Composable () -> Unit = { Box(Modifier.fillMaxSize().background(screenColor)) }) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                cardColor = NagTheme.colors.surfaceRaised
                pageColor = NagTheme.colors.background
                player = rememberExpandablePlayerState()
                TestShell(
                    WidthClass.Compact,
                    chromeGlass = glass,
                    player = { kind ->
                        if (kind == NavKind.BottomBar) {
                            ExpandablePlayer(
                                player,
                                mini = { Mini() },
                                full = { Full() },
                                modifier = Modifier.testTag("mini"),
                                card = PlayerCard(CARD_X.dp, CARD_Y.dp, CARD_CORNER.dp),
                            )
                        }
                    },
                ) { _, _ -> screen() }
            }
        }
        compose.waitForIdle()
    }

    /** The strip's content, its card left to the sheet as the app's bar leaves it in the sheet: the artwork and the title on the left. */
    @Composable
    private fun Mini() {
        Box(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = CARD_X.dp, vertical = CARD_Y.dp)) {
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .playerSharedElement(PlayerShared.Artwork, RoundedCornerShape(6.dp))
                        .size(40.dp)
                        .background(cover, RoundedCornerShape(6.dp))
                        .testTag("cover-mini"),
                )
                Spacer(Modifier.width(12.dp))
                Text("Sabaha Kadar", Modifier.playerSharedElement(PlayerShared.Title), color = NagTheme.colors.text)
            }
        }
    }

    /** A full player without a backdrop of its own (the sheet's page shows through): the artwork and the title in the middle. */
    @Composable
    private fun Full() {
        Column(Modifier.fillMaxSize().padding(top = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .playerSharedElement(PlayerShared.Artwork, RoundedCornerShape(12.dp))
                    .size(280.dp)
                    .background(cover, RoundedCornerShape(12.dp))
                    .testTag("cover-full"),
            )
            Spacer(Modifier.height(24.dp))
            Text("Sabaha Kadar", Modifier.playerSharedElement(PlayerShared.Title), style = NagTheme.type.headline, color = NagTheme.colors.text)
        }
    }

    private fun shot(): Bitmap {
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        return bitmap
    }

    /** The placed nodes with [tag] (one laid out but not placed is neither drawn, touched nor read out). */
    private fun placed(tag: String): List<Rect> =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().filter { it.layoutInfo.isPlaced }.map { it.boundsInWindow }

    /** Where the strip is drawn (its bars' slide included). */
    private fun strip(): Rect = compose.onNodeWithTag("mini").fetchSemanticsNode().boundsInWindow

    private fun Bitmap.at(x: Float, y: Float): Int = getPixel(x.roundToInt().coerceIn(0, width - 1), y.roundToInt().coerceIn(0, height - 1))

    private fun assertNear(message: String, expected: Rect, actual: Rect) {
        val apart = maxOf(abs(expected.left - actual.left), abs(expected.top - actual.top), abs(expected.right - actual.right), abs(expected.bottom - actual.bottom))
        assertTrue("$message: $expected, was $actual", apart <= 0.5f)
    }

    private fun near(pixel: Int, color: Color, tolerance: Int = 6): Boolean {
        val argb = color.toArgb()
        return listOf(16, 8, 0).all { shift -> abs((pixel shr shift and 0xff) - (argb shr shift and 0xff)) <= tolerance }
    }

    @Test
    fun theSurfaceIsTheCardAtTheStartAndTheWindowAtTheEnd() {
        // a phone's 1080 × 2340 window, the strip 2000 px down and 108 px high, its card 24 and 18 px inside it
        val start = PlayerSurface.bounds(0f, 2000f, 108f, 1080f, 2340f, 24f, 18f)
        assertEquals("the card, in the sheet's own coordinates", Rect(24f, 18f, 1056f, 90f), start)
        assertEquals("the whole window", Rect(0f, 0f, 1080f, 2340f), PlayerSurface.bounds(1f, 2000f, 108f, 1080f, 2340f, 24f, 18f))
        // on the way it only grows: in the window its top rises, its bottom goes down, its sides go out
        var before = start.translate(0f, 2000f)
        for (step in 1..20) {
            val p = step / 20f
            val now = PlayerSurface.bounds(p, 2000f, 108f, 1080f, 2340f, 24f, 18f).translate(0f, 2000f * (1f - p))
            assertTrue("p=$p: $before → $now", now.left <= before.left && now.right >= before.right && now.top <= before.top && now.bottom >= before.bottom)
            before = now
        }
        // the corners: the card's at the start, rounder half way, square as the page
        assertEquals(30f, PlayerSurface.radius(0f, 30f, 40f), 0.001f)
        assertEquals(40f, PlayerSurface.radius(0.5f, 30f, 40f), 0.001f)
        assertEquals(0f, PlayerSurface.radius(1f, 30f, 40f), 0.001f)
        val red = Color(0xFFB0413E)
        val blue = Color(0xFF2E6F95)
        assertEquals(red.toArgb(), PlayerSurface.fill(0f, red, blue).toArgb())
        assertEquals(blue.toArgb(), PlayerSurface.fill(1f, red, blue).toArgb())
    }

    @Test
    fun aTapGrowsTheCardIntoThePage() {
        phone()
        val density = compose.activity.resources.displayMetrics.density
        val window = compose.onRoot().fetchSemanticsNode().size
        val rest = strip()
        val overlayTop = rest.top - player.collapsedTop
        val first = PlayerSurface.bounds(0f, player.collapsedTop, rest.height, window.width.toFloat(), window.height.toFloat(), CARD_X * density, CARD_Y * density)
        assertNear(
            "the surface starts as the card: its margins in the strip",
            Rect(rest.left + CARD_X * density, rest.top + CARD_Y * density, rest.right - CARD_X * density, rest.bottom - CARD_Y * density),
            first.translate(rest.left, rest.top),
        )
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("mini").performClick()
        var checked = 0
        var rounded = 0
        repeat(90) { frame ->
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val p = player.progress
            if (p <= 0f || p >= 1f) return@repeat
            val surface = PlayerSurface.bounds(p, player.collapsedTop, rest.height, window.width.toFloat(), window.height.toFloat(), CARD_X * density, CARD_Y * density)
                .translate(rest.left, overlayTop + player.collapsedTop * (1f - p))
            val bitmap = shot()
            val fill = PlayerSurface.fill(p, cardColor, pageColor)
            val middle = surface.top + surface.height / 2f
            assertTrue("frame $frame (p=$p): the surface's colour inside its edge, at $surface", near(bitmap.at(surface.right - 4f, middle), fill))
            if (surface.right + 4f < window.width - 1) {
                assertFalse("frame $frame (p=$p): beside the surface the screen shows, at $surface", near(bitmap.at(surface.right + 4f, middle), fill, 12))
            }
            if (PlayerSurface.radius(p, CARD_CORNER * density, 16f * density) >= 6f * density) {
                assertFalse("frame $frame (p=$p): its corner is round, at $surface", near(bitmap.at(surface.left + 1f, surface.top + 1f), fill, 3))
                rounded++
            }
            checked++
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("the surface was checked in $checked frames, round in $rounded", checked >= 6 && rounded >= 3)
        assertTrue(player.isExpanded)
        // open, it is the page from edge to edge, its corners square
        val open = shot()
        assertTrue("the page's colour at the window's edge", near(open.at(2f, overlayTop + window.height / 2f), pageColor))
        assertTrue("a square corner", near(open.at(1f, overlayTop + 1f), pageColor))
    }

    /**
     * On the shell's bottom glass (the user's glass B, the product's chrome) the card is that glass: the surface keeps it
     * under the page's colour while the player rises, a region of its own over the screen; open, it is the page.
     */
    @Test
    fun onTheBottomGlassTheCardGrowsIntoThePage() {
        phone(glass = ChromeGlass.Bottom)
        val density = compose.activity.resources.displayMetrics.density
        val window = compose.onRoot().fetchSemanticsNode().size
        val rest = strip()
        val overlayTop = rest.top - player.collapsedTop
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("mini").performClick()
        var checked = 0
        repeat(90) { frame ->
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val p = player.progress
            if (p <= 0f || p >= 1f) return@repeat
            val surface = PlayerSurface.bounds(p, player.collapsedTop, rest.height, window.width.toFloat(), window.height.toFloat(), CARD_X * density, CARD_Y * density)
                .translate(rest.left, overlayTop + player.collapsedTop * (1f - p))
            // across its top edge: above it the screen (beside the card, at the strip's height, the shell's own bottom glass
            // lies: the card on the glass is that glass, its rule apart)
            if (surface.top - 4f <= overlayTop) return@repeat
            val bitmap = shot()
            val above = bitmap.at(surface.center.x, surface.top - 4f)
            assertFalse(
                "frame $frame (p=$p): the glass under the page's colour stands out from the screen above it, at $surface",
                near(bitmap.at(surface.center.x, surface.top + 4f), Color(above)),
            )
            checked++
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("the surface was checked in $checked frames", checked >= 4)
        assertTrue(player.isExpanded)
        // open, its glass is gone under the page: the page's colour from edge to edge
        assertTrue("the page's colour at the window's edge", near(shot().at(2f, overlayTop + window.height / 2f), pageColor))
    }

    @Test
    fun theArtworkFliesFromTheStripToTheFullPlayerAndBack() {
        phone()
        val strip = placed("cover-mini").single()
        assertTrue("the strip shows the artwork", near(shot().at(strip.center.x, strip.center.y), cover))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("mini").performClick()
        var flown = 0
        var gone = 0
        repeat(90) { frame ->
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val p = player.progress
            if (p <= 0f || p >= 1f) return@repeat
            val full = placed("cover-full").singleOrNull() ?: return@repeat
            val minis = placed("cover-mini")
            // the strip's copy in the sheet rises with the sheet: where the strip has it, moved up by the way
            val inSheet = strip.translate(0f, -p * player.collapsedTop)
            if (p < MINI_GONE) {
                assertEquals("frame $frame (p=$p): the sheet carries a copy of the strip while it is placed", 2, minis.size)
                assertEquals("frame $frame (p=$p): the copy is where the sheet is", inSheet.top, minis.minBy { it.top }.top, 1.5f)
            } else {
                assertEquals("frame $frame (p=$p): the strip's controls in the sheet are not placed once faded", 1, minis.size)
                gone++
            }
            val flying = lerp(inSheet, full, p)
            assertTrue("frame $frame (p=$p): the artwork is drawn where the way has it, at $flying", near(shot().at(flying.center.x, flying.center.y), cover))
            flown++
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("the flight was checked in $flown frames, $gone of them past the strip's fade", flown >= 6 && gone >= 2)
        assertTrue(player.isExpanded)
        assertTrue("the full player shows the artwork at its place", near(shot().at(placed("cover-full").single().center.x, placed("cover-full").single().center.y), cover))

        // and back down, Back's way: the same in reverse, the strip showing the artwork again at the end
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        var back = 0
        repeat(90) { frame ->
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val p = player.progress
            if (p <= 0f || p >= 1f) return@repeat
            val full = placed("cover-full").singleOrNull() ?: return@repeat
            val flying = lerp(strip.translate(0f, -p * player.collapsedTop), full, p)
            assertTrue("frame $frame (p=$p) on the way back: the artwork is where the way has it, at $flying", near(shot().at(flying.center.x, flying.center.y), cover))
            back++
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("the way back was checked in $back frames", back >= 6)
        assertFalse(player.isExpanded)
        assertTrue("the strip shows the artwork again", near(shot().at(strip.center.x, strip.center.y), cover))
        assertEquals(1, placed("cover-mini").size)
    }

    @Test
    fun withTheBarsHiddenItOpensFromTheStripAndLandsThere() {
        phone(screen = {
            LazyColumn(Modifier.fillMaxSize().background(screenColor).testTag("list")) {
                items((0 until 80).toList()) { i -> Text("Satır $i", Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)) }
            }
        })
        val shown = strip().top
        val overlayTop = shown - player.collapsedTop
        // a scroll slides the bars away, the strip down with them
        compose.onNodeWithTag("list").performTouchInput { swipeUp(startY = height * 0.7f, endY = height * 0.3f, durationMillis = 400) }
        compose.waitForIdle()
        val hidden = strip().top
        assertTrue("the scroll slid the bars and the strip down: $shown → $hidden", hidden > shown + 24f)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("mini").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        // the sheet opens from where the strip is drawn; the bars stay where the scroll left them (no 64 dp jump)
        assertEquals("the anchor is where the strip is drawn", hidden - overlayTop, player.collapsedTop, 1f)
        assertEquals("the strip did not jump back up with the bars", hidden, strip().top, 1f)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue(player.isExpanded)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertFalse(player.isExpanded)
        // it landed where the strip is, which has not moved
        assertEquals(hidden, strip().top, 1f)
        assertEquals(hidden - overlayTop, player.collapsedTop, 1f)
    }

    /**
     * 1.0.4: the Back button is no finger. Android 16 and later send the three buttons' Back as a predictive Back
     * whose progress runs by itself while the button is down ([BackButton]); the open player took it for a swipe's
     * and shrank, its corners round, before it went down. Under the button it stays the page from edge to edge and
     * the release takes it down to the strip; a finger's Back at the same progress still shrinks it.
     */
    @Test
    fun theBackButtonTakesThePlayerDownWithoutShrinkingIt() {
        phone()
        val window = compose.onRoot().fetchSemanticsNode().size
        val overlayTop = strip().top - player.collapsedTop
        compose.onNodeWithTag("mini").performClick()
        compose.waitForIdle()
        assertTrue(player.isExpanded)
        // the page's colour in both upper corners and at the edge: a shrunk player, its corners round, shows the scrim there
        fun whole(bitmap: Bitmap): Boolean =
            near(bitmap.at(1f, overlayTop + 1f), pageColor) && near(bitmap.at(window.width - 2f, overlayTop + 1f), pageColor) &&
                near(bitmap.at(2f, overlayTop + window.height / 2f), pageColor)
        assertTrue("open, the player is the page from edge to edge", whole(shot()))
        compose.mainClock.autoAdvance = false
        compose.holdBackButton(frames = 10) { frame ->
            assertTrue("frame $frame: the player shrinks under the Back button", whole(shot()))
            assertTrue("frame $frame: the button down takes nothing down", player.isExpanded)
        }
        compose.releaseBackButton()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertFalse("the button's release takes the player down", player.isExpanded)

        compose.onNodeWithTag("mini").performClick()
        compose.waitForIdle()
        assertTrue(player.isExpanded)
        val dispatcher = compose.activity.onBackPressedDispatcher
        fun finger(progress: Float) = BackEventCompat(40f, 600f, progress, BackEventCompat.EDGE_LEFT)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(finger(0f)) }
        for (frame in 1..10) {
            compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(finger(BackButton.progressAtFrame(frame))) }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        assertFalse("a finger's Back at the same progress shrinks the player", whole(shot()))
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("given up, the player stays open", player.isExpanded)
    }

    @Test
    fun aTimelineComposedWhileATrackPlaysStartsAtTheLatestReading() {
        var first = -1f
        compose.setContent {
            val smooth = rememberSmoothProgress({ 0.42f }, 258_000L)
            if (first < 0f) first = smooth.fraction(false, 258_000L, 1f)
        }
        compose.waitForIdle()
        assertEquals("the first composition's position, not 0", 0.42f, first, 0.0001f)
    }

    @Test
    fun aFileOfAnUnknownLengthShowsTheTimePlayedAlone() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                Column {
                    ProgressTimeline({ 0f }, 0L, {}, style = TimelineStyle.Bar, elapsed = { 83_000L })
                    // a timeline given no reading stays as it was
                    ProgressTimeline({ 0f }, 0L, {}, style = TimelineStyle.Bar)
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("1:23").assertExists()
        compose.onAllNodesWithText("0:00").assertCountEquals(1)
        // nothing to seek in it: only the other one can be set
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).assertCountEquals(1)
    }

    private companion object {
        /** The test card's margins in its strip and its corners, in dp (the app's bar's). */
        const val CARD_X = 8f
        const val CARD_Y = 6f
        const val CARD_CORNER = 12f
    }
}
