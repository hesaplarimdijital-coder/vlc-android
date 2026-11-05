package app.nagmedar.ui.music

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.library.LibraryAccess
import app.nagmedar.ui.library.LocalLibraryAccess
import app.nagmedar.ui.library.ScanState
import app.nagmedar.ui.shell.LocalWidthClass
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * 1.0.3: the Music page shows the list it knows (the one kept from the last start) at once and
 * keeps it on screen while the medialibrary scans: no loading state in front of it, the strip of
 * a scan the user asked for above it, and the list as it was when the scan ends.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w390dp-h844dp-hdpi")
class MusicKnownListTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = object : MusicActions {
        override fun play(tracks: List<Track>, index: Int) {}

        override fun queue(tracks: List<Track>) {}

        override fun menu(track: Track, play: () -> Unit, select: (() -> Unit)?): List<MenuEntry> = emptyList()
    }

    private val known = MusicLibrary(MusicSamples.tracks, Locale.forLanguageTag("tr"))

    @Test
    fun theKnownListStaysWhileTheLibraryIsScanned() {
        var library by mutableStateOf<MusicLibrary?>(known)
        var scan by mutableStateOf(ScanState(active = true, discovering = true))
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                CompositionLocalProvider(
                    LocalWidthClass provides WidthClass.Compact,
                    LocalLibraryAccess provides LibraryAccess(true, emptySet(), {}, {}),
                ) {
                    MusicContent(library, scan, onAddFolder = {}) { list -> MusicBrowser(list, MusicBrowserState(), actions, Modifier) }
                }
            }
        }
        compose.waitForIdle()
        // the tracks from the first frame, the strip above them, no loading state
        compose.onNodeWithText("Yağmur").assertExists()
        compose.onNodeWithText("Medya klasörleri aranıyor").assertExists()
        compose.onNodeWithText("Kütüphane yükleniyor…").assertDoesNotExist()
        // the scan parses what it found: the list stays
        scan = ScanState(active = true, fraction = 0.4f, done = 26, total = 64)
        compose.waitForIdle()
        compose.onNodeWithText("Yağmur").assertExists()
        compose.onNodeWithText("Kütüphane taranıyor").assertExists()
        // over: the strip goes, the list stays
        scan = ScanState.Idle
        compose.waitForIdle()
        compose.onNodeWithText("Yağmur").assertExists()
        compose.onNodeWithText("Kütüphane taranıyor").assertDoesNotExist()
        // only a page that knows no list yet (the very first start) waits for one
        library = null
        compose.waitForIdle()
        compose.onNodeWithText("Yağmur").assertDoesNotExist()
        compose.onNodeWithText("Kütüphane yükleniyor…").assertExists()
    }
}
