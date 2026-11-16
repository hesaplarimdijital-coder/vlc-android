package app.nagmedar.ui.music

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.tags.artwork.LibraryCoverState
import app.nagmedar.tags.quality.LibraryQualityState
import app.nagmedar.tags.quality.LosslessVerdict
import app.nagmedar.tags.quality.TrackQuality
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.shell.LocalWidthClass
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.4.0: the filter's "Denetim": Metamatik's lists from what the library's checks found: the songs to put right, the
 * fake FLACs, those without a cover, those with garbled text; the songs on any of the lists chosen.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h900dp-mdpi")
class LibraryCheckFilterTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun track(id: Long, title: String, uri: String) =
        Track(id, uri, title, "Moğollar", "Moğollar", "Moğollar", "Anadolu rock", 1971, id.toInt(), 1, 200_000, "/storage/emulated/0/Music")

    private val real = track(1, "Bir Sana Bir Bana", "file:///m/01.flac")
    private val fake = track(2, "Garip", "file:///m/02.flac")
    private val bare = track(3, "Haliç", "file:///m/03.mp3")
    private val sheet = "file:///m/album.flac"
    private val cue = track(4, "Ternaril", "$sheet#cue=2")

    private val findings = CheckFindings(
        quality = LibraryQualityState(
            qualities = mapOf(
                real.uri to TrackQuality.Checked(LosslessVerdict.Lossless),
                fake.uri to TrackQuality.Checked(LosslessVerdict.Transcoded),
                bare.uri to TrackQuality.NotApplicable,
                sheet to TrackQuality.Checked(LosslessVerdict.ProbablyTranscoded),
            ),
            checked = 3,
            total = 3,
        ),
        tags = LibraryCoverState(withoutCover = setOf(bare.uri), needsAttention = setOf(bare.uri, fake.uri), garbled = setOf(real.uri), checked = 4, total = 4),
    )

    private val library = MusicLibrary(listOf(real, fake, bare, cue), Locale.forLanguageTag("tr"))

    @Test
    fun eachListHasWhatItsCheckFoundACueSheetsTrackByItsFile() {
        fun titles(vararg checks: LibraryCheck) = library.checked(checks.toSet(), findings).tracks.map { it.title }
        assertEquals(listOf("Garip", "Ternaril"), titles(LibraryCheck.Fake))
        assertEquals(listOf("Haliç"), titles(LibraryCheck.NoCover))
        assertEquals(listOf("Garip", "Haliç"), titles(LibraryCheck.NeedsFix))
        assertEquals(listOf("Bir Sana Bir Bana"), titles(LibraryCheck.Garbled))
        // two lists: the songs on either
        assertEquals(listOf("Bir Sana Bir Bana", "Haliç"), titles(LibraryCheck.Garbled, LibraryCheck.NoCover))
        // none: the library itself
        assertEquals(library, library.checked(emptySet(), findings))
        assertEquals(mapOf(LibraryCheck.NeedsFix to 2, LibraryCheck.Fake to 2, LibraryCheck.NoCover to 1, LibraryCheck.Garbled to 1), findings.counts())
    }

    private val actions = object : MusicActions {
        override fun play(tracks: List<Track>, index: Int) = Unit
        override fun queue(tracks: List<Track>) = Unit
        override fun menu(track: Track, play: () -> Unit, select: (() -> Unit)?): List<MenuEntry> = emptyList()
    }

    @Test
    fun theFiltersDenetimListsThemWithTheirCountsAndLetsThroughTheChosen() {
        val state = MusicBrowserState(view = MusicView.Tracks)
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Pointer) {
                CompositionLocalProvider(LocalWidthClass provides WidthClass.Large, LocalCheckFindings provides findings) {
                    MusicBrowser(library, state, actions, Modifier)
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Garip").assertExists()
        compose.onNodeWithTag("music-filter").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Denetim").assertExists()
        compose.onNodeWithText("Sahte FLAC · 2").performClick()
        compose.waitForIdle()
        assertEquals(setOf(LibraryCheck.Fake), state.filter.checks)
        compose.onNodeWithText("Garip").assertExists()
        compose.onNodeWithText("Ternaril").assertExists()
        compose.onNodeWithText("Haliç").assertDoesNotExist()
        compose.onNodeWithText("Bir Sana Bir Bana").assertDoesNotExist()
    }

    @Test
    fun aListWithNothingOnItSaysSoAndClearsTheFilters() {
        val none = CheckFindings()
        val state = MusicBrowserState(view = MusicView.Tracks, filter = TechFilter(checks = setOf(LibraryCheck.NoCover)))
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Pointer) {
                CompositionLocalProvider(LocalWidthClass provides WidthClass.Large, LocalCheckFindings provides none) {
                    MusicBrowser(library, state, actions, Modifier)
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Filtreleri temizle").performClick()
        compose.waitForIdle()
        assertEquals(TechFilter(), state.filter)
        compose.onNodeWithText("Garip").assertExists()
    }
}
