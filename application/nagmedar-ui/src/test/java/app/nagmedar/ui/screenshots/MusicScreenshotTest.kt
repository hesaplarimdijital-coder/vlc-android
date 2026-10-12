package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.NoPlayerActions
import app.nagmedar.ui.R
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.music.Depth
import app.nagmedar.ui.music.MusicActions
import app.nagmedar.ui.music.MusicBrowser
import app.nagmedar.ui.music.MusicBrowserState
import app.nagmedar.ui.music.MusicColumn
import app.nagmedar.ui.music.MusicLibrary
import app.nagmedar.ui.music.MusicSamples
import app.nagmedar.ui.music.MusicView
import app.nagmedar.ui.music.NowPlayingInfo
import app.nagmedar.ui.music.NowPlayingPanel
import app.nagmedar.ui.music.PhonePlayer
import app.nagmedar.ui.music.RatingSurface
import app.nagmedar.ui.music.TechFilter
import app.nagmedar.ui.music.Track
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.playback.QueueEntry
import app.nagmedar.ui.screens.PageHeader
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
import java.util.Locale

/**
 * S-803, S-1001, S-1002, for approval: the music library in DeX as three panes (the albums, an
 * album's table with its format columns, now playing with its waveform), on a phone by artist in
 * English, on a tablet by genre with the 24-bit filter, and the phone's player.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MusicScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private object NoMusicActions : MusicActions {
        override fun play(tracks: List<Track>, index: Int) = Unit

        override fun queue(tracks: List<Track>) = Unit
    }

    private fun library(language: String) = MusicLibrary(MusicSamples.tracks, Locale.forLanguageTag(language))

    private val playing = NowPlayingInfo(
        title = "Neva Kâr",
        artist = "Kadri Erdem",
        album = "Itrî'nin İzinde",
        format = "FLAC 24/96 · USB DAC · 96 kHz · Bit-perfect",
        lengthMs = 498_000,
        playing = true,
        rating = 4,
    )

    private val upNext = listOf(
        QueueEntry("a#1", "Nihâvend Saz Semâîsi", "Kadri Erdem", 401_000),
        QueueEntry("b#1", "Gece Yolcusu", "Cemre Kaya", 254_000),
        QueueEntry("c#1", "Yağmur", "Cemre Kaya", 231_000),
    )

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
    private fun Library(library: MusicLibrary, state: MusicBrowserState, nowPlaying: (@Composable () -> Unit)? = null) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.nag_nav_music),
                subtitle = pluralStringResource(R.plurals.nag_tracks_count, library.tracks.size, library.tracks.size),
            )
            MusicBrowser(library, state, NoMusicActions, nowPlaying = nowPlaying, stats = MusicSamples.stats)
        }
    }

    @Test @Config(qualifiers = "tr-w1440dp-h900dp-mdpi")
    fun dex() {
        val library = library("tr")
        val itri = library.groups(MusicView.Albums).first { it.title == "Itrî'nin İzinde" }.key
        val state = MusicBrowserState(
            view = MusicView.Albums,
            group = itri,
            columns = listOf(MusicColumn.Title, MusicColumn.Number, MusicColumn.Composer, MusicColumn.Format, MusicColumn.Duration, MusicColumn.Rating, MusicColumn.Plays),
        )
        capture("music-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
            TestShell(WidthClass.Large, startDestination = Destination.Music) { _, _ ->
                Library(library, state) {
                    NowPlayingPanel(
                        info = playing,
                        progress = { 0.38f },
                        waveform = MusicSamples.waveform,
                        upNext = upNext,
                        onSeek = {},
                        onPlayUpNext = {},
                        artwork = { size -> Artwork(null, size, corner = 8.dp, video = false) },
                        onRate = {},
                    )
                }
            }
        }
    }

    @Test @Config(qualifiers = "en-w411dp-h891dp-xhdpi")
    fun phoneEnglish() {
        val library = library("en")
        capture("music-phone-light-en", ThemeMode.Light, InputMode.Touch) {
            TestShell(WidthClass.Compact, startDestination = Destination.Music) { _, _ ->
                Library(library, MusicBrowserState(view = MusicView.Artists))
            }
        }
    }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tabletGenres() {
        val library = library("tr")
        capture("music-tablet-dark-genres", ThemeMode.Dark, InputMode.Touch) {
            TestShell(WidthClass.Expanded, startDestination = Destination.Music) { _, _ ->
                Library(library, MusicBrowserState(view = MusicView.Genres, filter = TechFilter(depths = setOf(Depth.Bits24))))
            }
        }
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun ratingWindow() {
        val tracks = MusicSamples.tracks.filter { it.title == "Neva Kâr" }
        capture("music-rating-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                RatingSurface(tracks, MusicSamples.stats, onRate = {}, onDismiss = {})
            }
        }
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun phonePlayer() {
        capture("music-player-phone-dark", ThemeMode.Dark, InputMode.Touch) {
            PhonePlayer(
                info = playing.copy(format = "FLAC 24/96 · AAudio · 48 kHz"),
                progress = { 0.42f },
                waveform = MusicSamples.waveform,
                actions = NoPlayerActions,
                onClose = {},
                shuffling = true,
                artwork = { size -> Artwork(null, size, corner = 12.dp, video = false) },
                onRate = {},
            )
        }
    }
}
