package app.nagmedar.ui.perf

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.SamplePlayerBar
import app.nagmedar.ui.music.MusicActions
import app.nagmedar.ui.music.MusicBrowser
import app.nagmedar.ui.music.MusicBrowserState
import app.nagmedar.ui.music.MusicLibrary
import app.nagmedar.ui.music.MusicSamples
import app.nagmedar.ui.music.MusicView
import app.nagmedar.ui.music.Track
import app.nagmedar.ui.shell.LocalWidthClass
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.AccentTones
import app.nagmedar.ui.theme.ArtworkAccent
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
import java.util.Locale

/**
 * 1.0's review, measured: how often the phone's mini player is composed again while a new track's
 * artwork accent comes in (it was on every frame of the 150 ms cross-fade, in the middle of the
 * strip's swipe), and whether a new playback history composes the music list again (it rebuilt the
 * whole library).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class RecompositionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** [content], counting its compositions in [onComposed]; a test-only wrapper. */
    @Composable
    private fun Counted(onComposed: () -> Unit, content: @Composable () -> Unit) {
        SideEffect { onComposed() }
        content()
    }

    @Test
    fun aNewTracksAccentComposesTheMiniPlayerOnceNotOnEveryFrame() {
        val first = Color(0xFF2E7D32)
        val second = Color(0xFFC0392B)
        var seed by mutableStateOf<Color?>(first)
        var compositions = 0
        var accent = Color.Unspecified
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                ArtworkAccent(seed) {
                    val colors = NagTheme.colors
                    Counted({
                        compositions++
                        accent = colors.accent
                    }) { SamplePlayerBar(NavKind.BottomBar) }
                }
            }
        }
        // the dark palette's accent is the tones' base
        compose.waitUntil(5_000) { accent == AccentTones.fromSeed(first).base }
        compose.waitForIdle()
        compositions = 0
        seed = second
        compose.waitUntil(5_000) { accent == AccentTones.fromSeed(second).base }
        // what was left of a cross-fade, had it been composed
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertTrue("composed $compositions times for one accent change", compositions in 1..2)
    }

    @Test
    fun aNewHistoryComposesTheShelfNotTheMusicList() {
        var history by mutableStateOf(listOf(MusicSamples.tracks[1].uri))
        var rows = 0
        var shelves = 0
        val actions = object : MusicActions {
            override fun play(tracks: List<Track>, index: Int) = Unit

            override fun queue(tracks: List<Track>) = Unit

            // called as each row of the table is composed
            override fun rowModifier(track: Track, others: () -> List<Track>): Modifier {
                rows++
                return Modifier
            }
        }
        val library = MusicLibrary(MusicSamples.tracks, Locale.forLanguageTag("tr"))
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalWidthClass provides WidthClass.Compact) {
                    // as the Music screen hands it over: the shelf reads the history itself
                    val shelf = remember {
                        @Composable {
                            SideEffect { shelves++ }
                            Text("${history.size} parça", Modifier.testTag("shelf"))
                        }
                    }
                    MusicBrowser(library, remember { MusicBrowserState(view = MusicView.Tracks) }, actions, Modifier, shelf = shelf)
                }
            }
        }
        compose.waitForIdle()
        // the shelf is the list's first item, over the rows
        val shelfBottom = compose.onNodeWithTag("shelf").getBoundsInRoot().bottom
        val firstRow = compose.onAllNodesWithText(MusicSamples.tracks.first().title)[0].getBoundsInRoot().top
        assertTrue(shelfBottom <= firstRow)
        val rowsBefore = rows
        val shelvesBefore = shelves
        assertTrue(rowsBefore > 0)
        history = listOf(MusicSamples.tracks[2].uri) + history
        compose.waitForIdle()
        assertEquals(shelvesBefore + 1, shelves)
        assertEquals(rowsBefore, rows)
    }
}
