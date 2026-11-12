package app.nagmedar.ui.motion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
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
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.SamplePlayerBar
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.components.ActionGroup
import app.nagmedar.ui.components.ExpandablePlayer
import app.nagmedar.ui.components.ExpandablePlayerState
import app.nagmedar.ui.components.GlassBottomSheet
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.PlayerShared
import app.nagmedar.ui.components.playerSharedElement
import app.nagmedar.ui.components.rememberExpandablePlayerState
import app.nagmedar.ui.playback.FakeQueue
import app.nagmedar.ui.playback.QueuePage
import app.nagmedar.ui.playback.QueuePanel
import app.nagmedar.ui.music.MusicActions
import app.nagmedar.ui.music.MusicBrowser
import app.nagmedar.ui.music.MusicBrowserState
import app.nagmedar.ui.music.MusicLibrary
import app.nagmedar.ui.music.MusicSamples
import app.nagmedar.ui.music.MusicView
import app.nagmedar.ui.music.Track
import app.nagmedar.ui.playback.compactPlayerCard
import app.nagmedar.ui.podcast.PodcastActions
import app.nagmedar.ui.podcast.PodcastSamples
import app.nagmedar.ui.podcast.PodcastsContent
import app.nagmedar.ui.sampleTracks
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.shell.ChromeGlass
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.NagmedarShell
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.shell.ShellNavigator
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.holdBackButton
import app.nagmedar.ui.shell.releaseBackButton
import app.nagmedar.ui.shell.rememberShellContentPadding
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 1.0.3 (Desktop Kit 2.3.0's strips): the phone's transitions frame by frame, as the app plays them in the
 * real shell: the tab switch's fade-through, a page pushed and popped, Back from another tab's first page
 * (the key, and the predictive gesture let go or given up), the player's expand and collapse (a tap's
 * spring and Back's; with the bars shown and with the bars hidden by a scroll: the player grows out of the
 * bar's card, its artwork and title flying), a sheet's open, close and fling, the phone's "Sıradaki" flung
 * down. Each test plays one with the clock advanced a frame at a time and saves the frames side by side as
 * build/outputs/screenshots/motion/<name>.png,
 * which CI pushes with the screenshots to ci/shots-pr-<n> (motion/). Nothing is asserted beyond the strip
 * being made: the strips are there to be looked at, before and after a change of motion. The frames are
 * those of the frame clock (16 ms apart); each frame lists the milliseconds since the change, with a "•"
 * while something still waits for the next frame (a spring that has not arrived), and the strip's last
 * label says when the window went still ("durdu").
 *
 * 1.0.3 (Desktop Kit 2.3.0's detail strips): a podcast's page opening on a phone (the row's cover flying to
 * the page's header, the page coming in from 12 % over the sinking list), the predictive Back gesture on it
 * (followed by the finger, then let go) and given up, an album opened and turned around by Back on its way,
 * an album's tracks and the albums of another way in arriving in turn.
 *
 * 1.0.4: Back is the Back button as the owner's phone sends it ([buttonStrip]: Android 16 and later press it as a
 * predictive Back with a progress of the system's own; the strips of 1.0.3 pressed a bare `onBackPressed()`, a
 * keyboard's key at most, and never showed what the button did to a detail). The episode's page opened from a
 * show's and closed by the button, the show's page and an album closed by it, the player taken down by it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class MotionStripsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** The milliseconds after the change whose frames make a strip: the springs' first frames, their middle and their tail. */
    private val times = listOf(16L, 48L, 96L, 144L, 208L, 304L, 400L, 512L)

    private lateinit var view: View

    private fun phoneContent(content: @Composable () -> Unit) {
        // the test activity's theme has an action bar; the app's theme has none
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) { content() }
            }
        }
        compose.waitForIdle()
    }

    private fun shot(): Bitmap {
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        return bitmap
    }

    /** The first frame (ms after the change) on which nothing waited for another frame; null while it still moves. */
    private var stillAt: Long? = null

    /** Something of the window waits for the next frame: an animation running, a spring not yet arrived. */
    private fun moving(): Boolean = compose.runOnIdle { Recomposer.runningRecomposers.value.any { it.hasPendingWork } }

    /** Frame by frame up to [t] ms (a run of frames without an idle in between leaves Robolectric's recomposer asleep). */
    private fun advanceTo(t: Long, now: Long): Long {
        var at = now
        while (at < t) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            at += FRAME_MS
            if (stillAt == null && !moving()) stillAt = at
        }
        return at
    }

    /** "96 ms", with a "•" while the window still waits for frames. */
    private fun label(now: Long): String = if (stillAt == null) "$now ms •" else "$now ms"

    /**
     * The window before [change] and [at] ms after it, as one strip; [change] runs on the UI thread unless it
     * drives the test's own input ([onUiThread] false: a finger's swipe), which keeps in step with the clock itself.
     */
    private fun strip(name: String, at: List<Long> = times, onUiThread: Boolean = true, change: () -> Unit) {
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf("önce" to shot())
        stillAt = null
        if (onUiThread) compose.runOnIdle { change() } else change()
        var now = 0L
        for (t in at) {
            now = advanceTo(t, now)
            frames += label(now) to shot()
        }
        // how long it went on after the last frame shown
        while (stillAt == null && now < STILL_LIMIT_MS) now = advanceTo(now + FRAME_MS, now)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        saveStrip(name, frames, stillAt?.let { "durdu: $it ms" } ?: "durmadı: > $STILL_LIMIT_MS ms")
    }

    /**
     * 1.0.4: the Back button as Android 16 and later send it ([BackButton]): a predictive Back with no edge whose
     * progress the system runs by itself while the button is down. The window before the press, 48 and 96 ms into
     * it (a tap's length; under the button nothing of the app's follows that progress but navigation's own page), and
     * [at] ms after the release.
     */
    private fun buttonStrip(name: String, at: List<Long> = times) {
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf("önce" to shot())
        compose.holdBackButton { frame ->
            val ms = (frame + 1) * FRAME_MS
            if (ms == 48L || ms == 96L) frames += "düğme basılı $ms ms" to shot()
        }
        stillAt = null
        compose.releaseBackButton()
        var now = 0L
        for (t in at) {
            now = advanceTo(t, now)
            frames += "bırakınca +" + label(now) to shot()
        }
        while (stillAt == null && now < STILL_LIMIT_MS) now = advanceTo(now + FRAME_MS, now)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        saveStrip(name, frames, stillAt?.let { "durdu: $it ms" } ?: "durmadı: > $STILL_LIMIT_MS ms")
    }

    // ---------------------------------------------------------------- the tabs and the pages

    private var navigator: ShellNavigator? = null

    /** The phone's shell with a list under the bars on Müzik and Podcast, the sample pages elsewhere. */
    private fun shell(player: (@Composable (NavKind) -> Unit)? = null, glass: ChromeGlass = ChromeGlass.Solid, overlay: @Composable () -> Unit = {}) {
        phoneContent {
            TestShell(
                WidthClass.Compact,
                player = player,
                overlay = { overlay() },
                chromeGlass = glass,
            ) { destination, nav ->
                navigator = nav
                when (destination) {
                    Destination.Music -> TrackList("Müzik")
                    Destination.Podcasts -> EpisodeList()
                    else -> Column(Modifier.fillMaxSize()) {
                        PageHeader(title = destination.name)
                        repeat(6) { i -> SampleRow(i, "${destination.name} ${i + 1}", "Ayar", Color(0xFF3A4A5A)) }
                    }
                }
            }
        }
    }

    /** The bottom bar's Müzik → Podcast: the fade-through (the old one fades out in 90 ms, the new one fades in growing from 96 %). */
    @Test
    fun tabSwitch() {
        shell()
        strip("tab-switch") { navigator?.selectTab(Destination.Podcasts) }
    }

    /**
     * A page opened from a page (the equaliser from "Daha fazla"): opaque, it slides in from the end edge over the one
     * it leaves, which sinks in its place and darkens under a scrim (1.0.3, Desktop Kit 2.4.0).
     */
    @Test
    fun pagePush() {
        shell()
        compose.runOnIdle { navigator?.selectTab(Destination.More) }
        compose.waitForIdle()
        strip("page-push") { navigator?.open(Destination.Equalizer) }
    }

    /**
     * Back from that page (the Back button: navigation plays the way out with the button's own progress and finishes
     * it on the release): it leaves over the end edge, the page under it rises in its place out of the scrim.
     */
    @Test
    fun pagePop() {
        shell()
        compose.runOnIdle { navigator?.selectTab(Destination.More) }
        compose.waitForIdle()
        compose.runOnIdle { navigator?.open(Destination.Equalizer) }
        compose.waitForIdle()
        buttonStrip("page-pop")
    }

    /** The same Back from the page's own arrow in the top bar: what a tap plays. */
    @Test
    fun pagePopArrow() {
        shell()
        compose.runOnIdle { navigator?.selectTab(Destination.More) }
        compose.waitForIdle()
        compose.runOnIdle { navigator?.open(Destination.Equalizer) }
        compose.waitForIdle()
        strip("page-pop-arrow") { navigator?.back() }
    }

    /**
     * Back (the key, three-button navigation's Back) on another tab's first page: to Müzik as it was left, fading
     * through as a tab chosen does (1.0.3: it slid away sideways as a page's Back does, Müzik coming in beside it).
     */
    @Test
    fun tabBack() {
        shell()
        compose.runOnIdle { navigator?.selectTab(Destination.Podcasts) }
        compose.waitForIdle()
        buttonStrip("tab-back")
    }

    /**
     * The predictive Back gesture on another tab's first page (gesture navigation: the system's insets have the
     * edges' Back gesture): with the finger, then let go.
     */
    @Test
    fun tabBackGesture() {
        shell()
        edgeBackGesture()
        compose.runOnIdle { navigator?.selectTab(Destination.Podcasts) }
        compose.waitForIdle()
        gestureStrip("tab-back-gesture", release = true)
    }

    /** The same gesture given up half way: the tab comes back to its place. */
    @Test
    fun tabBackGestureCancelled() {
        shell()
        edgeBackGesture()
        compose.runOnIdle { navigator?.selectTab(Destination.Podcasts) }
        compose.waitForIdle()
        gestureStrip("tab-back-gesture-cancelled", release = false)
    }

    /** What a phone with gesture navigation sends: the left and right edges take the Back gesture. */
    private fun edgeBackGesture() {
        val edge = with(compose.density) { 24.dp.roundToPx() }
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemGestures(), Insets.of(edge, 0, edge, 0))
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, insets) }
        compose.waitForIdle()
    }

    /** A Back gesture from the left edge: the finger's progress frame by frame, then let go ([release]) or given up. */
    private fun gestureStrip(name: String, release: Boolean) {
        val dispatcher = compose.activity.onBackPressedDispatcher
        fun back(progress: Float) = BackEventCompat(40f, 600f, progress, BackEventCompat.EDGE_LEFT)
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf("önce" to shot())
        stillAt = null
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(back(0f)) }
        for (p in listOf(0.2f, 0.4f, 0.6f, 0.8f)) {
            compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(back(p)) }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            frames += "parmak $p" to shot()
        }
        compose.runOnUiThread { if (release) dispatcher.onBackPressed() else dispatcher.dispatchOnBackCancelled() }
        val word = if (release) "bırakınca" else "vazgeçince"
        var now = 0L
        for (t in listOf(16L, 48L, 96L, 160L, 304L)) {
            now = advanceTo(t, now)
            frames += "$word +$now ms" to shot()
        }
        while (stillAt == null && now < STILL_LIMIT_MS) now = advanceTo(now + FRAME_MS, now)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        saveStrip(name, frames, stillAt?.let { "durdu: $it ms" } ?: "durmadı: > $STILL_LIMIT_MS ms")
    }

    // ---------------------------------------------------------------- the player

    private var playerScope: CoroutineScope? = null

    private fun playerShell(glass: ChromeGlass = ChromeGlass.Solid): ExpandablePlayerState {
        var player: ExpandablePlayerState? = null
        shell(glass = glass, player = { kind ->
            if (kind == NavKind.BottomBar) {
                val state = rememberExpandablePlayerState()
                player = state
                playerScope = rememberCoroutineScope()
                ExpandablePlayer(
                    state,
                    mini = { SamplePlayerBar(kind) },
                    full = { FullPlayer() },
                    modifier = Modifier.testTag("mini"),
                    // as the app's bar: the player grows out of its card
                    card = compactPlayerCard(),
                )
            }
        })
        return player!!
    }

    /** A tap on the mini player (the app's opens it with expand()): the sheet grows from the strip, the bottom bar pushed away under it. */
    @Test
    fun playerExpand() {
        val player = playerShell()
        strip("player-expand", at = times + 640L) { playerScope?.launch { player.expand() } }
    }

    /** The full player open (the sample bar's own tap opens nothing: through its state, as the app's tap does). */
    private fun openPlayer(player: ExpandablePlayerState) {
        compose.runOnIdle { playerScope?.launch { player.expand() } }
        compose.waitForIdle()
        check(player.isExpanded) { "the player did not open" }
    }

    /** The full player's collapse (its button, the notification): back down onto the strip. */
    @Test
    fun playerCollapse() {
        val player = playerShell()
        openPlayer(player)
        strip("player-collapse", at = times + 640L) { playerScope?.launch { player.collapse() } }
    }

    /** Back (three-button navigation's Back button) on the full player: whole under the button, down on its release. */
    @Test
    fun playerBack() {
        val player = playerShell()
        openPlayer(player)
        buttonStrip("player-back", at = times + 640L)
    }

    /** The bars slid away by a scroll of the list, as the walk's phone leaves them. */
    private fun hideBars() {
        compose.onNodeWithTag("tracks").performTouchInput { swipeUp(startY = height * 0.7f, endY = height * 0.35f, durationMillis = 400) }
        compose.waitForIdle()
    }

    /** A tap on the mini player with the bars hidden: the sheet grows from where the strip is drawn, the bars stay hidden under it. */
    @Test
    fun playerTapBarsHidden() {
        val player = playerShell()
        hideBars()
        strip("player-tap-bars-hidden", at = times + 640L) { playerScope?.launch { player.expand() } }
    }

    /** The collapse of a player opened with the bars hidden: it lands where the strip is, still with the bars hidden. */
    @Test
    fun playerCollapseBarsHidden() {
        val player = playerShell()
        hideBars()
        openPlayer(player)
        strip("player-collapse-bars-hidden", at = times + 640L) { playerScope?.launch { player.collapse() } }
    }

    // ---------------------------------------------------------------- the player on the bottom glass (the user's glass B)

    /** The bar's card on the shell's one bottom glass: the player grows out of it, the page's colour coming over the glass. */
    @Test
    fun playerExpandGlass() {
        val player = playerShell(ChromeGlass.Bottom)
        strip("player-expand-glass", at = times + 640L) { playerScope?.launch { player.expand() } }
    }

    /** Back on the full player over the glass: down onto the card on the glass. */
    @Test
    fun playerBackGlass() {
        val player = playerShell(ChromeGlass.Bottom)
        openPlayer(player)
        buttonStrip("player-back-glass", at = times + 640L)
    }

    /** A tap with the bars hidden by a scroll, on the glass. */
    @Test
    fun playerTapBarsHiddenGlass() {
        val player = playerShell(ChromeGlass.Bottom)
        hideBars()
        strip("player-tap-bars-hidden-glass", at = times + 640L) { playerScope?.launch { player.expand() } }
    }

    // ---------------------------------------------------------------- a sheet

    private fun sheetShell(open: () -> Boolean, close: () -> Unit) {
        shell(overlay = { SampleSheet(open, close) })
    }

    /** A track's sheet: its title and six actions. */
    @Composable
    private fun SampleSheet(open: () -> Boolean, close: () -> Unit) {
        GlassBottomSheet(visible = open(), onDismiss = close, label = "Sabaha Kadar") {
            Text("Sabaha Kadar", style = NagTheme.type.title, color = NagTheme.colors.text, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            sheetActions.forEach { group ->
                group.actions.forEach { action ->
                    Text(action.label, style = NagTheme.type.body, color = NagTheme.colors.text, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp))
                }
            }
        }
    }

    @Test
    fun sheetOpen() {
        var open by mutableStateOf(false)
        sheetShell({ open }) { open = false }
        strip("sheet-open") { open = true }
    }

    @Test
    fun sheetClose() {
        var open by mutableStateOf(true)
        sheetShell({ open }) { open = false }
        strip("sheet-close") { open = false }
    }

    /** A finger flinging the sheet down from one of its rows: it carries on at the finger's speed until it is out. */
    @Test
    fun sheetFling() {
        var open by mutableStateOf(true)
        sheetShell({ open }) { open = false }
        strip("sheet-fling", onUiThread = false) {
            compose.onNodeWithText("Albüme git").performTouchInput { swipe(center, center + Offset(0f, 150f), durationMillis = 60) }
        }
    }

    /** The phone's "Sıradaki" page flung down from its list: as the sheet. */
    @Test
    fun queueFling() {
        var open by mutableStateOf(true)
        val queue = FakeQueue(FakeQueue.sample, current = 0)
        shell(overlay = {
            QueuePage(open, onClose = { open = false }) { pageModifier ->
                QueuePanel(queue.entries, queue.current, queue, { open = false }, pageModifier, background = NagTheme.colors.background, page = true)
            }
        })
        strip("queue-fling", onUiThread = false) {
            compose.onNodeWithTag("queue-Fener").performTouchInput { swipe(center, center + Offset(0f, 200f), durationMillis = 60) }
        }
    }

    // ---------------------------------------------------------------- over the bottom glass (the user's B, 6 Nov)

    /**
     * The phone's shell with its bottom chrome on one glass ([ChromeGlass.Bottom], the user's decision B): the mini
     * player's card, the bottom bar and the band over a list of covers (Robolectric draws the glass's translucent
     * form, not the blur); the sheets stay opaque over it.
     */
    private fun glassShell(overlay: @Composable () -> Unit) {
        phoneContent {
            NagmedarShell(
                widthClass = WidthClass.Compact,
                sidebarCollapsed = false,
                onSidebarCollapsedChange = {},
                badges = emptyMap(),
                onSearch = {},
                menus = {},
                player = { kind -> SamplePlayerBar(kind) },
                screen = { destination, nav ->
                    navigator = nav
                    if (destination == Destination.Music) TrackList("Müzik") else EpisodeList()
                },
                navController = rememberNavController(),
                overlay = { overlay() },
                chromeGlass = ChromeGlass.Bottom,
            )
        }
    }

    /** A sheet coming up over the bottom glass: its opaque surface and its scrim over the glass. */
    @Test
    fun sheetOpenOverBottomGlass() {
        var open by mutableStateOf(false)
        glassShell { SampleSheet({ open }) { open = false } }
        strip("sheet-open-bottom-glass") { open = true }
    }

    /** The same sheet flung down over the bottom glass. */
    @Test
    fun sheetFlingOverBottomGlass() {
        var open by mutableStateOf(true)
        glassShell { SampleSheet({ open }) { open = false } }
        strip("sheet-fling-bottom-glass", onUiThread = false) {
            compose.onNodeWithText("Albüme git").performTouchInput { swipe(center, center + Offset(0f, 150f), durationMillis = 60) }
        }
    }

    // ---------------------------------------------------------------- list → detail (1.0.3)

    private var podcasts by mutableStateOf(PodcastSamples.model())

    private val podcastActions = PodcastActions(
        onOpen = { podcasts = podcasts.copy(page = PodcastSamples.page) },
        onClosePage = { podcasts = podcasts.copy(page = null) },
        onOpenEpisode = { id -> podcasts = podcasts.copy(episodeId = id, episode = PodcastSamples.episodePage()) },
        onCloseEpisode = { podcasts = podcasts.copy(episodeId = null, episode = null) },
    )

    /** The Podcast page in the phone's shell, as the app has it: its subscriptions, and a show's page over them. */
    private fun podcastShell() {
        phoneContent {
            TestShell(WidthClass.Compact, startDestination = Destination.Podcasts) { _, _ -> PodcastsContent(podcasts, podcastActions) }
        }
    }

    /** A show opened from its row: the cover flies to the page's header, the page comes in from 12 % over the sinking list. */
    @Test
    fun detailOpen() {
        podcastShell()
        strip("detail-open") { podcasts = podcasts.copy(page = PodcastSamples.page) }
    }

    /** 1.0.4: the show's page closed by the Back button: whole under the button, out over the end edge on its release. */
    @Test
    fun detailBackButton() {
        podcasts = podcasts.copy(page = PodcastSamples.page)
        podcastShell()
        buttonStrip("detail-back-button")
    }

    /** 1.0.4: an episode's page opened from the show's page: opaque, over the show's page, which sinks and darkens. */
    @Test
    fun episodeOpen() {
        podcasts = podcasts.copy(page = PodcastSamples.page)
        podcastShell()
        strip("episode-open") { podcastActions.onOpenEpisode(PodcastSamples.episodes[0].id) }
    }

    /**
     * 1.0.4, the owner's report ("podcast bölümüne tıklayıp geri gelince animasyon eskisi gibi"): the episode's page
     * closed by the Back button. Under the button it stays whole (1.0.3 shrank it into a rounded card, as under a
     * finger); the release slides it out over the show's page.
     */
    @Test
    fun episodeBackButton() {
        podcasts = podcasts.copy(page = PodcastSamples.page, episodeId = PodcastSamples.episodes[0].id, episode = PodcastSamples.episodePage())
        podcastShell()
        buttonStrip("episode-back-button")
    }

    /** Frames of the predictive Back gesture on the open show's page: the finger at 25, 50 and 80 %, then let go or given up. */
    private fun backStrip(name: String, letGo: Boolean) {
        podcasts = podcasts.copy(page = PodcastSamples.page)
        podcastShell()
        // 1.0.4: a finger's Back needs a system that keeps the window's edges for it
        edgeBackGesture()
        val dispatcher = compose.activity.onBackPressedDispatcher
        fun back(progress: Float) = BackEventCompat(40f, 600f, progress, BackEventCompat.EDGE_LEFT)
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf("önce" to shot())
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(back(0f)) }
        for (progress in listOf(0.25f, 0.5f, 0.8f)) {
            compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(back(progress)) }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            frames += "parmak $progress" to shot()
        }
        stillAt = null
        compose.runOnUiThread { if (letGo) dispatcher.onBackPressed() else dispatcher.dispatchOnBackCancelled() }
        var now = 0L
        for (t in listOf(16L, 64L, 144L, 224L, 304L)) {
            now = advanceTo(t, now)
            frames += (if (letGo) "bırakınca +" else "vazgeçince +") + label(now) to shot()
        }
        while (stillAt == null && now < STILL_LIMIT_MS) now = advanceTo(now + FRAME_MS, now)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        saveStrip(name, frames, stillAt?.let { "durdu: $it ms" } ?: "durmadı: > $STILL_LIMIT_MS ms")
    }

    /** Back with the finger: the list is really behind the shrinking page; let go, the page leaves from where it was. */
    @Test
    fun detailBack() = backStrip("detail-back", letGo = true)

    /** The same gesture given up half way: everything runs back the way it came. */
    @Test
    fun detailBackCancelled() = backStrip("detail-back-cancelled", letGo = false)

    private val library by lazy { MusicLibrary(MusicSamples.tracks, Locale.forLanguageTag("tr")) }

    private val musicActions = object : MusicActions {
        override fun play(tracks: List<Track>, index: Int) = Unit

        override fun queue(tracks: List<Track>) = Unit
    }

    /** The Music page's browser in the phone's shell. */
    private fun musicShell(state: MusicBrowserState) {
        phoneContent {
            TestShell(WidthClass.Compact) { _, _ -> MusicBrowser(library, state, musicActions, Modifier.fillMaxSize()) }
        }
    }

    private val albumKey by lazy { library.groups(MusicView.Albums).first { it.title == "Itrî'nin İzinde" }.key }

    /**
     * An album opened from the albums: opaque, it slides in from the end edge over the albums, which sink and darken
     * under a scrim (1.0.3, Desktop Kit 2.4.0); its tracks arrive in turn.
     */
    @Test
    fun albumOpen() {
        val state = MusicBrowserState(view = MusicView.Albums)
        musicShell(state)
        strip("album-open") { state.group = albumKey }
    }

    /**
     * Back from an open album (the Back button): whole under the button, on its release it slides out over the end
     * edge, the albums rise out of the scrim.
     */
    @Test
    fun albumClose() {
        val state = MusicBrowserState(view = MusicView.Albums)
        musicShell(state)
        compose.runOnIdle { state.group = albumKey }
        compose.waitForIdle()
        buttonStrip("album-close")
    }

    /** Back right after the tap: the way is run back from where it was, the albums rise again. */
    @Test
    fun albumTurnAround() {
        val state = MusicBrowserState(view = MusicView.Albums)
        musicShell(state)
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf("önce" to shot())
        compose.runOnIdle { state.group = albumKey }
        var now = advanceTo(96, 0)
        frames += "açılırken $now ms" to shot()
        stillAt = null
        compose.runOnIdle { state.group = null }
        for (t in listOf(16L, 48L, 96L, 160L, 240L, 320L)) {
            now = advanceTo(96 + t, now)
            frames += "geri +" + label(now - 96) to shot()
        }
        while (stillAt == null && now < STILL_LIMIT_MS) now = advanceTo(now + FRAME_MS, now)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        saveStrip("album-turn-around", frames, stillAt?.let { "durdu: ${it - 96} ms" } ?: "durmadı: > $STILL_LIMIT_MS ms")
    }

    /** Another way into the library (Parçalar → Albümler): the first albums arrive in turn, 15 ms apart. */
    @Test
    fun listAppear() {
        val state = MusicBrowserState(view = MusicView.Tracks)
        musicShell(state)
        strip("list-appear", at = listOf(16L, 48L, 80L, 112L, 160L, 208L, 256L, 320L)) { state.open(MusicView.Albums) }
    }

    // ---------------------------------------------------------------- the pieces

    private val sheetActions = listOf(
        ActionGroup(null, listOf("Sıradaki olarak çal", "Sıranın sonuna ekle", "Listeye ekle…").map { MenuEntry.Item(it, {}) }),
        ActionGroup(null, listOf("Albüme git", "Sanatçıya git", "Bilgi").map { MenuEntry.Item(it, {}) }),
    )

    private val artwork = listOf(Color(0xFFB0413E), Color(0xFF2E6F95), Color(0xFF6B8E23), Color(0xFFD08C2E), Color(0xFF7A4E9C), Color(0xFF2F8F83))

    /** A list lying under the bars as the app's do: it asks for the shell's padding and pads its own ends. */
    @Composable
    private fun TrackList(title: String) {
        val padding = rememberShellContentPadding()
        LazyColumn(Modifier.fillMaxSize().testTag("tracks"), contentPadding = padding) {
            item { PageHeader(title = title, subtitle = "${sampleTracks.size * 3} parça") }
            items((0 until sampleTracks.size * 3).toList()) { i ->
                val track = sampleTracks[i % sampleTracks.size]
                SampleRow(i, track.title, track.artist, artwork[i % artwork.size])
            }
        }
    }

    @Composable
    private fun EpisodeList() {
        val padding = rememberShellContentPadding()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item { PageHeader(title = "Podcast", subtitle = "6 abonelik") }
            items((0 until 12).toList()) { i ->
                SampleRow(i, "Bölüm ${24 - i}: ${sampleTracks[i % sampleTracks.size].title}", "${38 + i * 3} dk", artwork[(i + 2) % artwork.size])
            }
        }
    }

    @Composable
    private fun SampleRow(index: Int, title: String, subtitle: String, color: Color) {
        val colors = NagTheme.colors
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(color, RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = NagTheme.type.body, color = colors.text, maxLines = 1)
                Text(subtitle, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1)
            }
            Text("${3 + index % 4}:${10 + index}", style = NagTheme.type.labelSmall, color = colors.textTertiary)
        }
    }

    /** A full player look-alike: the artwork, the title, a seek line and three buttons' places; the artwork and the title fly from the bar's. */
    @Composable
    private fun FullPlayer() {
        val colors = NagTheme.colors
        Column(Modifier.fillMaxSize().background(colors.background).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Box(Modifier.playerSharedElement(PlayerShared.Artwork, RoundedCornerShape(12.dp)).size(300.dp).background(artwork[0], RoundedCornerShape(12.dp)))
            Spacer(Modifier.height(28.dp))
            Text(sampleTracks[0].title, Modifier.playerSharedElement(PlayerShared.Title), style = NagTheme.type.headline, color = colors.text)
            Text(sampleTracks[0].artist, style = NagTheme.type.body, color = colors.textSecondary)
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).background(colors.border, RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxWidth(0.42f).height(4.dp).background(colors.accent, RoundedCornerShape(2.dp)))
            }
            Spacer(Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(colors.surfaceRaised, RoundedCornerShape(24.dp)))
                Spacer(Modifier.width(28.dp))
                Box(Modifier.size(72.dp).background(colors.accent, RoundedCornerShape(36.dp)))
                Spacer(Modifier.width(28.dp))
                Box(Modifier.size(48.dp).background(colors.surfaceRaised, RoundedCornerShape(24.dp)))
            }
        }
    }

    private fun saveStrip(name: String, frames: List<Pair<String, Bitmap>>, end: String) {
        val scale = 0.5f
        val width = (frames.first().second.width * scale).roundToInt()
        val height = (frames.first().second.height * scale).roundToInt()
        val label = 28
        val gap = 6
        val strip = Bitmap.createBitmap(frames.size * (width + gap) - gap, height + label, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(strip)
        canvas.drawColor(android.graphics.Color.rgb(32, 32, 36))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 18f
        }
        frames.forEachIndexed { index, (text, bitmap) ->
            val x = index * (width + gap)
            canvas.drawText(if (index == frames.lastIndex) "$text · $end" else text, x + 6f, 20f, paint)
            canvas.drawBitmap(bitmap, null, Rect(x, label, x + width, label + height), paint)
        }
        val dir = File(File(System.getProperty("nagmedar.screenshotDir") ?: "build/outputs/screenshots"), "motion")
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { strip.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        /** The frame clock's step. */
        const val FRAME_MS = 16L

        /** How long a strip waits for the window to go still. */
        const val STILL_LIMIT_MS = 2_000L
    }
}
