package app.nagmedar.ui.music

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.RatingStars
import app.nagmedar.ui.shell.LocalWidthClass
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import androidx.compose.ui.input.InputMode as ComposeInputMode

/** S-808: the stars of the music table, of the rating window, and from the keyboard. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h900dp-mdpi-keysexposed-qwerty")
class RatingTest {

    @get:Rule
    val compose = createComposeRule()

    private val rated = mutableListOf<Pair<List<String>, Int>>()

    private val actions = object : MusicActions {
        override fun play(tracks: List<Track>, index: Int) = Unit

        override fun queue(tracks: List<Track>) = Unit

        override fun rate(tracks: List<Track>, rating: Int) {
            rated += tracks.map { it.title } to rating
        }

        override val askRating: ((List<Track>) -> Unit) = {}
    }

    @Test
    fun theTablesStarsSetARating() {
        val library = MusicLibrary(MusicSamples.tracks, Locale.forLanguageTag("tr"))
        val itri = library.groups(MusicView.Albums).first { it.title == "Itrî'nin İzinde" }.key
        val state = MusicBrowserState(view = MusicView.Albums, group = itri, columns = listOf(MusicColumn.Title, MusicColumn.Rating, MusicColumn.Plays))
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                CompositionLocalProvider(LocalWidthClass provides WidthClass.Large) {
                    MusicBrowser(library, state, actions, Modifier, stats = MusicSamples.stats)
                }
            }
        }
        compose.waitForIdle()
        // Segâh Tekbîr has five stars, Neva Kâr four, Nihâvend Saz Semâîsi none; the counts beside them
        compose.onNodeWithText("14").assertExists()
        compose.onNode(hasContentDescription("Puan: 5 üzerinden 4")).performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        assertEquals(listOf(listOf("Neva Kâr") to 2), rated)
        compose.onNode(hasContentDescription("Puan verilmedi")).performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        assertEquals(listOf("Nihâvend Saz Semâîsi") to 5, rated.last())
    }

    @Test
    fun theWindowSetsTheStarsOfASelectionAndTakesThemBack() {
        val tracks = MusicSamples.tracks.filter { it.title == "Segâh Tekbîr" || it.title == "Neva Kâr" }
        val got = mutableListOf<Int>()
        var closed = 0
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Pointer) {
                RatingSurface(tracks, MusicSamples.stats, onRate = { got += it }, onDismiss = { closed++ })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("2 parça").assertExists()
        // five and four: none shown until one is chosen
        compose.onNodeWithText("Puanları farklı; bir yıldız hepsine aynı puanı verir.").assertExists()
        compose.onNodeWithText("Puanı kaldır").assertDoesNotExist()
        compose.onNodeWithTag("rating-stars").performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        assertEquals(listOf(3), got)
        compose.onNodeWithText("Puanı kaldır").performClick()
        assertEquals(listOf(3, 0), got)
        compose.onNodeWithText("Puanı kaldır").assertDoesNotExist()
        compose.onNodeWithTag("rating-done").performClick()
        assertEquals(1, closed)
    }

    @Test
    fun theKeysSetTheStars() {
        val stars = mutableIntStateOf(2)
        lateinit var inputModes: InputModeManager
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                inputModes = LocalInputModeManager.current
                RatingStars(stars.intValue, { stars.intValue = it }, focusable = true, modifier = Modifier.testTag("stars"))
            }
        }
        compose.waitForIdle()
        // a hardware key takes the window out of touch mode; injected keys do not
        compose.runOnIdle { inputModes.requestInputMode(ComposeInputMode.Keyboard) }
        compose.onNodeWithTag("stars").requestFocus()
        compose.onNodeWithTag("stars").performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals(3, stars.intValue)
        compose.onNodeWithTag("stars").performKeyInput { pressKey(Key.Five) }
        assertEquals(5, stars.intValue)
        compose.onNodeWithTag("stars").performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals("no sixth star", 5, stars.intValue)
        compose.onNodeWithTag("stars").performKeyInput { pressKey(Key.Delete) }
        assertEquals(0, stars.intValue)
        compose.onNodeWithTag("stars").performKeyInput { pressKey(Key.DirectionLeft) }
        assertEquals(0, stars.intValue)
    }
}
