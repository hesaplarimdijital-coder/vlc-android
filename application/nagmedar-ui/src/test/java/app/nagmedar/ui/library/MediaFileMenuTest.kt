package app.nagmedar.ui.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.runtime.CompositionLocalProvider
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.music.MusicActions
import app.nagmedar.ui.music.MusicBrowser
import app.nagmedar.ui.music.MusicBrowserState
import app.nagmedar.ui.music.MusicLibrary
import app.nagmedar.ui.music.MusicSamples
import app.nagmedar.ui.music.MusicView
import app.nagmedar.ui.music.Track
import app.nagmedar.ui.shell.LocalWidthClass
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import java.util.Locale

/**
 * A media's menu (right click on DeX, long press, the Menu key) has "Paylaş…" and "Dosyada göster"
 * when the media is a file of this device; "Paylaş…" hands the file to the system's share sheet,
 * a picked folder's document as it is. A stream has neither.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class MediaFileMenuTest {

    @get:Rule
    val compose = createComposeRule()

    private val application: Application = ApplicationProvider.getApplicationContext()

    /** A film of a folder picked with the system picker, as the library lists it. */
    private val film = media(
        "content://com.android.externalstorage.documents/tree/primary%3AFilmler/document/primary%3AFilmler%2FKuzey%20I%C5%9F%C4%B1%C4%9F%C4%B1.mkv",
        "Kuzey Işığı",
    )

    private fun media(uri: String, title: String): MediaWrapper = MLServiceLocator.getAbstractMediaWrapper(Uri.parse(uri)).apply {
        type = MediaWrapper.TYPE_VIDEO
        this.title = title
    }

    private fun menu(entries: (android.content.Context) -> List<MenuEntry>) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                val context = LocalContext.current
                ContextMenuArea(entries = { entries(context) }) { Box(Modifier.size(240.dp).testTag("item")) }
            }
        }
        compose.onNodeWithTag("item").performMouseInput {
            moveTo(center)
            press(MouseButton.Secondary)
            release(MouseButton.Secondary)
        }
        compose.waitForIdle()
    }

    private fun entry(label: String) = compose.onNode(hasText(label) and hasClickAction())

    @Test
    fun aFilmsMenuSharesItAndShowsItInItsFolder() {
        menu { context -> mediaMenu(context, film, play = {}) }
        entry("Dosyada göster").assertIsEnabled()
        entry("Paylaş…").assertIsEnabled().performClick()
        // the files are looked at off the main thread; the sheet opens back on it, whose looper the
        // wait does not run by itself
        compose.waitUntil(5_000) {
            ShadowLooper.idleMainLooper()
            shadowOf(application).peekNextStartedActivity() != null
        }
        val chooser = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals("“Kuzey Işığı” paylaşılsın", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE).toString())
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals(film.uri, send.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertTrue(send.type.orEmpty(), send.type.orEmpty().startsWith("video/"))
        assertNotEquals(0, send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    @Test
    fun aStreamHasNeither() {
        menu { context -> mediaMenu(context, media("https://yayin.example/canli.m3u8", "Canlı yayın"), play = {}) }
        compose.onNodeWithText("Medya bilgisi").assertExists()
        compose.onNodeWithText("Paylaş…").assertDoesNotExist()
        compose.onNodeWithText("Dosyada göster").assertDoesNotExist()
    }

    /** An album's menu shares its tracks together and shows the first one in its folder. */
    @Test
    fun anAlbumsMenuHasThemToo() {
        val shared = mutableListOf<List<String>>()
        val shown = mutableListOf<String>()
        val actions = object : MusicActions {
            override fun play(tracks: List<Track>, index: Int) = Unit

            override fun queue(tracks: List<Track>) = Unit

            override val share: (List<Track>) -> Unit get() = { tracks -> shared += tracks.map { it.title } }

            override val reveal: (Track) -> Unit get() = { track -> shown += track.title }
        }
        val library = MusicLibrary(MusicSamples.tracks, Locale.forLanguageTag("tr"))
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                // the albums alone, as a narrow window lists them: the album's name is its row's only
                CompositionLocalProvider(LocalWidthClass provides WidthClass.Compact) {
                    MusicBrowser(library, MusicBrowserState(view = MusicView.Albums), actions, Modifier)
                }
            }
        }
        compose.waitForIdle()
        val album = hasText("Itrî'nin İzinde") and hasClickAction()
        fun openMenu() {
            compose.onNode(album).performMouseInput {
                moveTo(center)
                press(MouseButton.Secondary)
                release(MouseButton.Secondary)
            }
            compose.waitForIdle()
        }
        openMenu()
        entry("Paylaş…").performClick()
        compose.waitForIdle()
        openMenu()
        entry("Dosyada göster").performClick()
        compose.waitForIdle()
        assertEquals(setOf("Segâh Tekbîr", "Neva Kâr", "Nihâvend Saz Semâîsi"), shared.single().toSet())
        assertEquals(listOf(shared.single().first()), shown)
    }
}
