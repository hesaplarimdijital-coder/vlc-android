package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.music.AddToPlaylistSurface
import app.nagmedar.ui.music.FakePlaylistStore
import app.nagmedar.ui.music.FakeSmartListStore
import app.nagmedar.ui.music.MusicActions
import app.nagmedar.ui.music.MusicSamples
import app.nagmedar.ui.music.PlaylistSummary
import app.nagmedar.ui.music.PlaylistsBrowser
import app.nagmedar.ui.music.PlaylistsState
import app.nagmedar.ui.music.SmartField
import app.nagmedar.ui.music.SmartListSurface
import app.nagmedar.ui.music.SmartListsState
import app.nagmedar.ui.music.SmartOp
import app.nagmedar.ui.music.SmartRule
import app.nagmedar.ui.music.SmartTemplate
import app.nagmedar.ui.music.Track
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-804, for approval: the playlists' tabs over the open one's table with its handles in DeX, the
 * same on a phone in English, the empty screen on a tablet, and "Listeye ekle" from the library.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaylistsScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private object NoMusicActions : MusicActions {
        override fun play(tracks: List<Track>, index: Int) = Unit

        override fun queue(tracks: List<Track>) = Unit
    }

    private fun capture(name: String, theme: ThemeMode, input: InputMode, content: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) { content() }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Composable
    private fun Playlists(store: FakePlaylistStore, smartStore: FakeSmartListStore? = null) {
        val scope = rememberCoroutineScope()
        val state = remember { PlaylistsState(store, scope) }
        LaunchedEffect(Unit) { state.reload() }
        val smart = smartStore?.let { remember { SmartListsState(it, scope) } }
        if (smart != null) LaunchedEffect(Unit) { smart.follow() }
        PlaylistsBrowser(
            state, NoMusicActions, smart = smart,
            library = MusicSamples.tracks, stats = MusicSamples.stats, now = { 1_760_000_000_000L },
        )
    }

    /** S-805: the templates' lists, the most played first. */
    private fun smartLists() = FakeSmartListStore(
        SmartTemplate.MostPlayed.build("En çok çalınanlar"),
        SmartTemplate.Favourites.build("En sevilenler"),
        SmartTemplate.HiRes.build("Yüksek çözünürlük"),
    )

    private fun lists() = FakePlaylistStore(
        "Akşam" to listOf(1, 4, 8, 0, 9),
        "Yol" to listOf(3, 4, 5, 6),
        "Itrî" to listOf(0, 1, 2),
    )

    @Test @Config(qualifiers = "tr-w1440dp-h900dp-mdpi-keysexposed-qwerty")
    fun dex() {
        val store = lists()
        capture("playlists-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
            TestShell(WidthClass.Large, startDestination = Destination.Playlists) { _, _ -> Playlists(store) }
        }
    }

    @Test @Config(qualifiers = "en-w411dp-h891dp-xhdpi")
    fun phoneEnglish() {
        val store = lists()
        capture("playlists-phone-light-en", ThemeMode.Light, InputMode.Touch) {
            TestShell(WidthClass.Compact, startDestination = Destination.Playlists) { _, _ -> Playlists(store) }
        }
    }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tabletEmpty() {
        val store = FakePlaylistStore()
        capture("playlists-tablet-light-empty", ThemeMode.Light, InputMode.Touch) {
            TestShell(WidthClass.Expanded, startDestination = Destination.Playlists) { _, _ -> Playlists(store) }
        }
    }

    @Test @Config(qualifiers = "tr-w1440dp-h900dp-mdpi-keysexposed-qwerty")
    fun smartDex() {
        val smart = smartLists()
        capture("playlists-smart-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
            TestShell(WidthClass.Large, startDestination = Destination.Playlists) { _, _ -> Playlists(FakePlaylistStore(), smart) }
        }
    }

    @Test @Config(qualifiers = "tr-w1280dp-h900dp-mdpi-keysexposed-qwerty")
    fun smartEditor() {
        val forgotten = SmartTemplate.Forgotten.build("Unutulan sevilenler").let {
            it.copy(rules = it.rules + SmartRule(SmartField.Genre, SmartOp.Contains, "klasik"), limit = 25)
        }
        capture("playlists-smart-editor-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SmartListSurface(forgotten, MusicSamples.tracks, MusicSamples.stats, 1_760_000_000_000L, onSave = {}, onDismiss = {})
            }
        }
    }

    @Test @Config(qualifiers = "en-w411dp-h891dp-xhdpi")
    fun smartNewPhoneEnglish() {
        capture("playlists-smart-new-phone-light-en", ThemeMode.Light, InputMode.Touch) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SmartListSurface(null, MusicSamples.tracks, MusicSamples.stats, 1_760_000_000_000L, onSave = {}, onDismiss = {})
            }
        }
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
    fun addToPlaylist() {
        val playlists = listOf(
            PlaylistSummary(1, "Akşam", 5, 1_401_000),
            PlaylistSummary(2, "Yol", 4, 970_000),
            PlaylistSummary(3, "Itrî", 3, 1_211_000),
        )
        capture("playlists-add-dark", ThemeMode.Dark, InputMode.Pointer) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AddToPlaylistSurface(count = 3, playlists = playlists, onChoose = {}, onCreate = {}, onDismiss = {})
            }
        }
    }
}
