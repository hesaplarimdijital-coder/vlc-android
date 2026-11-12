package app.nagmedar.ui.shell

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 1.0.3 (Desktop Kit 2.3.0's DetailTransition tests, with 2.4.0's way in): a phone's detail slides in from the
 * end edge over its sinking list, the predictive Back gesture seeks its closing with the list really behind it,
 * a change of mind runs the way back, the cover flies between the row and the detail, a plain depth's list has
 * no lookahead pass, and with reduced motion the detail simply appears. What the frames look like (both
 * screens opaque, the list dark under a scrim) is [DetailNoDoubleExposureTest]'s.
 *
 * 1.0.5, the owner's phone (12 Nov, "Podcastte sayfası böyle oluyor", with a recording: his show's page slid in and
 * was gone in the frame its opening ended): the last tests here, where what flies with the detail lands after the
 * detail itself (an animation of its side that outlasts the detail's own way). The covers of the tests above never
 * did; nor did the walk see it, its animations off. And where the page was not lost it would not scroll ("o zaman
 * da aşağı scroll yapılmıyordu"): a lazy list taken out of the shared elements' layout after a way no longer takes
 * a scroll. Until 1.0.4 a test here held that a shared depth's list at rest had no lookahead pass; it has one now,
 * and scrolls.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class DetailDepthTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var detail by mutableStateOf<String?>(null)

    /** "Animasyonları kaldır" (or the battery saver) turned on while the screen is there. */
    private var reducedLater by mutableStateOf(false)

    private val plainList: @Composable () -> Unit = { Box(Modifier.fillMaxSize().testTag("list")) { Text("Liste") } }

    private val plainDetail: @Composable (String) -> Unit = { Box(Modifier.fillMaxSize().testTag("detail")) { Text(it) } }

    /** The transition in a phone's window ([tier] null: the theme's own, as the app has it). */
    private fun show(
        tier: MotionTier? = MotionTier.Gesture,
        reduced: Boolean = false,
        shared: Boolean = false,
        list: @Composable () -> Unit = plainList,
        content: @Composable (String) -> Unit = plainDetail,
    ) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, reduceMotion = reduced || reducedLater) {
                val body: @Composable () -> Unit = {
                    DetailTransition(detail = detail, onBack = { detail = null }, sharedElements = shared, list = list) { content(it) }
                }
                if (tier != null) CompositionLocalProvider(LocalMotionTier provides tier, content = body) else body()
            }
        }
        compose.waitForIdle()
        // 1.0.4: a finger's Back needs a system that keeps the window's edges for it
        compose.gestureNavigation()
    }

    private fun frame() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    /** Where the node tagged [tag] starts and how wide it is, in dp, while it is there. */
    private fun place(tag: String): Pair<Float, Float>? =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInWindow?.let {
            it.left / compose.density.density to it.width / compose.density.density
        }

    /**
     * Where the node tagged [tag] starts, in dp, while it is there: its own place, also when all of it is outside
     * the window (the bounds are clipped to what shows: nothing, at zero, for a detail still wholly beyond the edge).
     */
    private fun left(tag: String): Float? =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.positionInRoot?.let { it.x / compose.density.density }

    private fun back(progress: Float) = BackEventCompat(40f, 600f, progress, BackEventCompat.EDGE_LEFT)

    @Test
    fun aPhonesDetailComesFromTheEndOverTheSinkingList() {
        show()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { detail = "Albüm" }
        repeat(2) { frame() }
        // its first frames: from the end edge itself, all of its width away (Desktop Kit 2.3.0's came 12 % of it, fading)
        val first = left("detail")!!
        assertTrue("the detail comes from the end edge: $first", first > 300f)
        repeat(3) { frame() }
        // on its way: over the list, not yet in place; the list sinks under it
        val early = left("detail")!!
        assertTrue("the detail is on its way: $first → $early", early > 0.5f && early < first)
        val list = bounds("list")
        assertTrue("the list sinks under it: ${list.right - list.left}", list.right - list.left < 410.dp)
        repeat(40) { frame() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(0f, bounds("detail").left.value, 0.5f)
        // covered: the list is neither drawn nor read out
        compose.onNodeWithTag("list").assertDoesNotExist()
    }

    @Test
    fun theBackGestureSeeksTheClosingWithTheListBehindAndCancelRestoresIt() {
        detail = "Albüm"
        show()
        val full = bounds("detail")
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f))
            dispatcher.dispatchOnBackProgressed(back(0.5f))
        }
        compose.waitForIdle()
        // half way: the detail has shrunk half of the way to 90 %, and the list is really there behind it
        val half = bounds("detail")
        val ratio = (half.right - half.left) / (full.right - full.left)
        assertTrue("width at half way: $ratio of full", abs(ratio - 0.95f) < 0.01f)
        compose.onNodeWithTag("list").assertExists()
        // given up: back to full size, still open, the list covered again
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        val restored = bounds("detail")
        assertEquals((full.right - full.left).value, (restored.right - restored.left).value, 0.5f)
        assertEquals(0f, restored.left.value, 0.5f)
        assertEquals("Albüm", detail)
        compose.onNodeWithTag("list").assertDoesNotExist()
        // a whole gesture closes it, from where the finger let go
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f))
            dispatcher.dispatchOnBackProgressed(back(1f))
            dispatcher.onBackPressed()
        }
        compose.waitForIdle()
        assertNull(detail)
        compose.onNodeWithTag("detail").assertDoesNotExist()
        compose.onNodeWithTag("list").assertExists()
    }

    @Test
    fun aDetailKeptAfterBackRunsTheGestureBack() {
        detail = "Albüm"
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark) {
                CompositionLocalProvider(LocalMotionTier provides MotionTier.Gesture) {
                    // an app that keeps its detail (a step inside it went back instead)
                    DetailTransition(detail = detail, onBack = {}, list = plainList, detailContent = plainDetail)
                }
            }
        }
        compose.waitForIdle()
        compose.gestureNavigation()
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f))
            dispatcher.dispatchOnBackProgressed(back(0.6f))
            dispatcher.onBackPressed()
        }
        compose.waitForIdle()
        assertEquals("Albüm", detail)
        assertEquals(411f, bounds("detail").let { (it.right - it.left).value }, 0.5f)
        compose.onNodeWithTag("list").assertDoesNotExist()
    }

    @Test
    fun aChangeOfMindRunsTheWayBackFromWhereItIs() {
        show()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { detail = "Albüm" }
        repeat(4) { frame() }
        val earlier = left("detail")!!
        frame()
        val before = left("detail")!!
        assertTrue("on its way: $earlier → $before", before > 0.5f && before < earlier)
        // Back right after the tap
        compose.runOnIdle { detail = null }
        val lefts = mutableListOf<Float>()
        repeat(40) {
            frame()
            left("detail")?.let { lefts += it }
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        // (the opening moves on in the frame the change arrives in, about as far as in the frame before, its spring
        // still gathering speed that early, then it turns)
        val aFrame = earlier - before
        assertTrue(
            "the way runs back from where it was, no jump: $earlier → $before → $lefts",
            lefts.isNotEmpty() && abs(lefts.first() - before) < aFrame * 1.5f + 4f,
        )
        assertTrue("it runs back towards the end: $lefts", lefts.zipWithNext().all { (a, b) -> b >= a - 0.5f })
        compose.onNodeWithTag("detail").assertDoesNotExist()
        assertEquals(0f, bounds("list").left.value, 0.5f)
        assertEquals(411f, bounds("list").let { (it.right - it.left).value }, 0.5f)
    }

    /** A list whose row has a cover, and a detail with the same cover larger and lower. */
    private val coverList: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.detailSharedElement("cover").size(56.dp).background(Color.Red).testTag("row-cover"))
        }
    }

    private val coverDetail: @Composable (String) -> Unit = {
        Column(Modifier.fillMaxSize().testTag("detail")) {
            Spacer(Modifier.height(120.dp))
            Box(Modifier.detailSharedElement("cover").size(200.dp).background(Color.Blue).testTag("page-cover"))
        }
    }

    /** The node tagged [tag] in dp (left, top, width), while it is there. */
    private fun rect(tag: String): Triple<Float, Float, Float>? =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInWindow?.let {
            val d = compose.density.density
            Triple(it.left / d, it.top / d, it.width / d)
        }

    @Test
    fun theCoverFliesFromTheRowToItsPlaceInTheDetail() {
        show(shared = true, list = coverList, content = coverDetail)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { detail = "Albüm" }
        val widths = mutableListOf<Float>()
        repeat(40) {
            frame()
            place("page-cover")?.let { widths += it.second }
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("the cover grew from the row's size through sizes in between: $widths", widths.any { it > 60f && it < 196f })
        assertEquals(200f, place("page-cover")!!.second, 0.5f)
    }

    /** The strips showed it: after a gesture given up, the detail's cover stayed at the row's place and size. */
    @Test
    fun aGestureGivenUpPutsTheCoverBackAndASecondOneStillCloses() {
        detail = "Albüm"
        show(shared = true, list = coverList, content = coverDetail)
        val rest = rect("page-cover")!!
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f))
            dispatcher.dispatchOnBackProgressed(back(0.3f))
        }
        compose.waitForIdle()
        compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(back(0.6f)) }
        compose.waitForIdle()
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertEquals("Albüm", detail)
        val again = rect("page-cover")!!
        assertEquals("the cover's size is its own again", rest.third, again.third, 0.5f)
        assertEquals("the cover is back in its place", rest.first, again.first, 0.5f)
        assertEquals("the cover is back in its place", rest.second, again.second, 0.5f)
        compose.onNodeWithTag("list").assertDoesNotExist()
        // the same gesture again, let go: it closes, and the row's cover is the row's
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f))
            dispatcher.dispatchOnBackProgressed(back(0.3f))
        }
        compose.waitForIdle()
        compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(back(0.6f)) }
        compose.waitForIdle()
        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.waitForIdle()
        assertNull(detail)
        compose.onNodeWithTag("detail").assertDoesNotExist()
        assertEquals(56f, rect("row-cover")!!.third, 0.5f)
        assertEquals(0f, rect("row-cover")!!.second, 0.5f)
    }

    @Test
    fun withReducedMotionTheDetailSimplyAppears() {
        show(tier = null, reduced = true)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { detail = "Albüm" }
        repeat(4) { frame() }
        assertEquals(0f, bounds("detail").left.value, 0.5f)
        compose.onNodeWithTag("list").assertDoesNotExist()
        compose.runOnIdle { detail = null }
        repeat(4) { frame() }
        compose.onNodeWithTag("detail").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    /**
     * A depth without shared elements is a plain box: its list is measured once a frame. (Until 1.0.4 this held a
     * shared depth's list at rest to it too; see [aSharedDepthsListScrollsAtRestAndOnceItsDetailHasComeAndGone].)
     */
    @Test
    fun aPlainDepthsListIsMeasuredOnceAFrame() {
        var lookahead = 0
        var main = 0
        show(
            shared = false,
            list = {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .testTag("scroll")
                        .layout { measurable, constraints ->
                            if (isLookingAhead) lookahead++ else main++
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        },
                ) { items(200) { Text("Satır $it", Modifier.fillMaxWidth().height(48.dp)) } }
            },
        )
        lookahead = 0
        main = 0
        compose.onNodeWithTag("scroll").performTouchInput { swipeUp(durationMillis = 600) }
        compose.waitForIdle()
        assertTrue("the scroll was measured: $main", main > 5)
        assertEquals("no lookahead pass in a plain depth", 0, lookahead)
    }

    // ------------------------------------------------------------ 1.0.5: at rest after a flight that outlasts the detail's way

    /** A list of [count] rows tagged "<name>-<index>", 48 dp each; the whole is tagged [name]. */
    @Composable
    private fun Rows(name: String, count: Int = 200) {
        LazyColumn(Modifier.fillMaxSize().testTag(name)) {
            items(count) { Text("Satır $it", Modifier.fillMaxWidth().height(48.dp).testTag("$name-$it")) }
        }
    }

    /** The rows of the list [name] that are there, lowest first: each one's index and where it starts (dp from the top). */
    private fun rows(name: String): List<Pair<Int, Float>> =
        compose.onAllNodes(SemanticsMatcher("a row of $name") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("$name-") == true }, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.TestTag].removePrefix("$name-").toInt() to it.positionInRoot.y / compose.density.density }
            .sortedBy { it.first }

    /** A swipe up the list [name]: its first rows must have gone under the finger. */
    private fun assertScrolls(name: String, message: String) {
        val before = rows(name)
        assertTrue("$message: the list $name is there, at its top: $before", before.isNotEmpty() && before.first().first == 0)
        compose.onNodeWithTag(name).performTouchInput { swipeUp(durationMillis = 600) }
        compose.waitForIdle()
        val after = rows(name)
        assertTrue("$message: the rows of $name move under the finger: before $before, after $after", after.isNotEmpty() && after.first().first >= 3)
    }

    /** A detail whose side has [AFlightThatOutlastsTheWay], and rows to scroll. */
    private val longFlightDetail: @Composable (String) -> Unit = {
        AFlightThatOutlastsTheWay()
        Rows("detail")
    }

    /** Opens the detail and plays its opening to the end, frame by frame (the long flight lasts 900 ms). */
    private fun openOverALongFlight() {
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { detail = "Albüm" }
        repeat(80) { frame() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    /** The owner's page: in the frame the opening ended the detail was gone, a whole width beyond the end edge. */
    @Test
    fun aDetailStaysInPlaceWhenWhatFliesWithItLandsLater() {
        show(shared = true, content = longFlightDetail)
        openOverALongFlight()
        assertEquals("the detail is in its place once all has landed", 0f, left("detail")!!, 0.5f)
        assertEquals(411f, bounds("detail").let { (it.right - it.left).value }, 0.5f)
        compose.onNodeWithTag("list").assertDoesNotExist()
        // and it stays there
        repeat(10) { frame() }
        assertEquals("the detail stays in its place", 0f, left("detail")!!, 0.5f)
    }

    /** And it scrolls there (the owner's first opening, where the page was not lost: it stood still under his finger). */
    @Test
    fun afterALongFlightTheDetailScrolls() {
        show(shared = true, content = longFlightDetail)
        openOverALongFlight()
        assertScrolls("detail", "after a long flight")
    }

    /** The same after a way no longer than the detail's own: the page that came, and stayed, and would not scroll. */
    @Test
    fun afterItsOwnWayASharedDepthsDetailScrolls() {
        show(shared = true, content = { Rows("detail") })
        compose.runOnIdle { detail = "Albüm" }
        compose.waitForIdle()
        assertEquals(0f, left("detail")!!, 0.5f)
        assertScrolls("detail", "after the detail's own way")
    }

    /**
     * The list under it too: it scrolls at rest, and still once its detail has come and gone. (Taken out of the shared
     * elements' layout after a way, as until 1.0.4, a lazy list no longer took a scroll.)
     */
    @Test
    fun aSharedDepthsListScrollsAtRestAndOnceItsDetailHasComeAndGone() {
        show(shared = true, list = { Rows("list") }, content = longFlightDetail)
        assertScrolls("list", "at rest")
        compose.onNodeWithTag("list").performScrollToIndex(0)
        compose.waitForIdle()
        openOverALongFlight()
        compose.runOnIdle { detail = null }
        compose.waitForIdle()
        compose.onNodeWithTag("detail").assertDoesNotExist()
        assertScrolls("list", "once its detail has come and gone")
    }

    /** And once motion is reduced while the screen is there (the battery saver): its shared elements end, its lists still scroll. */
    @Test
    fun aSharedDepthsListsStillScrollOnceMotionIsReduced() {
        show(shared = true, list = { Rows("list") }, content = longFlightDetail)
        openOverALongFlight()
        compose.runOnIdle { detail = null }
        compose.waitForIdle()
        compose.runOnIdle { reducedLater = true }
        compose.waitForIdle()
        assertScrolls("list", "motion reduced")
        compose.runOnIdle { detail = "Albüm" }
        compose.waitForIdle()
        assertEquals("the detail simply appears", 0f, left("detail")!!, 0.5f)
        assertScrolls("detail", "motion reduced")
    }

    /** The way back is played too (its values start where the detail is, not where a stale transition left them), and the next opening lands. */
    @Test
    fun afterALongFlightTheDetailLeavesOnItsWayAndOpensAgain() {
        show(shared = true, content = longFlightDetail)
        openOverALongFlight()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { detail = null }
        val lefts = mutableListOf<Float>()
        repeat(80) {
            frame()
            left("detail")?.let { lefts += it }
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("the detail starts its way out from its place: $lefts", lefts.isNotEmpty() && lefts.first() < 40f)
        assertTrue("the detail is seen on its way out: $lefts", lefts.count { it > 20f && it < 390f } >= 3)
        assertTrue("towards the end edge, never back: $lefts", lefts.zipWithNext().all { (a, b) -> b >= a - 0.5f })
        compose.onNodeWithTag("detail").assertDoesNotExist()
        assertEquals(0f, bounds("list").left.value, 0.5f)
        // and again
        openOverALongFlight()
        assertEquals("opened again, the detail is in its place", 0f, left("detail")!!, 0.5f)
        compose.onNodeWithTag("list").assertDoesNotExist()
    }

    /**
     * The same on the depth itself: once the shared elements have landed (and their sides' animations, the longest of
     * the transition, have gone with their scopes), the depth's values are the open detail's, and nothing is said to
     * move.
     */
    @Test
    fun theDepthIsAtRestOnceItsSharedElementsHaveLanded() {
        var wanted by mutableStateOf(false)
        var depth: DepthState? = null
        var shared: Boolean? = null
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark) {
                val state = rememberDepth(wanted)
                depth = state
                LaunchedEffect(wanted) { state.drive(wanted, animated = true) }
                DepthHost(state, wanted, shared = true, Modifier.fillMaxSize()) { scopes ->
                    shared = scopes != null
                    if (scopes != null) {
                        scopes.detail.visibility.transition.animateFloat(transitionSpec = { tween(900, easing = LinearEasing) }, label = "a long flight") {
                            if (it == EnterExitState.Visible) 1f else 0f
                        }
                    }
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        compose.waitForIdle()
        assertEquals("at rest, closed: no scopes for shared elements", false, shared)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { wanted = true }
        repeat(6) { frame() }
        assertEquals("on its way: the sides' scopes", true, shared)
        repeat(80) { frame() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle {
            val state = depth!!
            val values = state.values!!
            // in thousandths. The detail's way: 0 in place, 1000 a whole width beyond the end edge; a card's detail: 1000 opaque;
            // a card's list: 0 gone
            val seen = "the detail's way ${(values.detailShift.value * 1000).roundToInt()}, a card's detail ${(values.cardAlpha.value * 1000).roundToInt()}, " +
                "a card's list ${(values.listAlpha.value * 1000).roundToInt()}, moving ${state.moving(true)}, shared elements' scopes $shared"
            assertEquals(
                "the depth at rest, open, after a flight that outlasted its way (its transition says it is running: ${state.transition.isRunning})",
                "the detail's way 0, a card's detail 1000, a card's list 0, moving false, shared elements' scopes false",
                seen,
            )
        }
    }
}

/**
 * What a shared element that lands after the detail is to the transition: an animation of its side that outlasts
 * the detail's own way (see [DetailDepthTest]).
 */
@Composable
private fun AFlightThatOutlastsTheWay() {
    val scopes = LocalDetailSharedScopes.current ?: return
    scopes.visibility.transition.animateFloat(transitionSpec = { tween(900, easing = LinearEasing) }, label = "a long flight") {
        if (it == EnterExitState.Visible) 1f else 0f
    }
}
