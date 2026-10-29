package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.NoPlayerActions
import app.nagmedar.ui.lyrics.LyricsFullScreen
import app.nagmedar.ui.lyrics.LyricsLoad
import app.nagmedar.ui.lyrics.LyricsPane
import app.nagmedar.ui.lyrics.LyricsSamples
import app.nagmedar.ui.lyrics.LyricsSection
import app.nagmedar.ui.music.MusicSamples
import app.nagmedar.ui.music.NowPlayingInfo
import app.nagmedar.ui.music.PhonePlayer
import app.nagmedar.ui.playback.Artwork
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
 * For approval: S-1003's Settings › Lyrics, LRCLIB on with lyrics kept, in DeX; off and nothing
 * kept, on a phone in English. S-1004's lyrics over the whole window in DeX, a line half sung word
 * by word; in the phone's player in English, with a delay; plain lyrics on a phone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LyricsScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

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

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
    fun settingsDex() = capture("settings-lyrics-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
        Box(Modifier.padding(top = 16.dp)) { LyricsSection(online = true, kept = 214, onOnlineChange = {}, onClear = {}) }
    }

    @Test @Config(qualifiers = "en-w390dp-h844dp-hdpi")
    fun settingsPhoneEnglish() = capture("settings-lyrics-phone-light-en", ThemeMode.Light, InputMode.Touch) {
        Box(Modifier.padding(top = 16.dp)) { LyricsSection(online = false, kept = 0, onOnlineChange = {}, onClear = {}) }
    }

    private val playing = NowPlayingInfo(
        title = "Sabaha Kadar",
        artist = "Ada Karan",
        album = "Gece Mavisi",
        format = "FLAC 24/48 · AAudio · 48 kHz",
        lengthMs = 241_600,
        playing = true,
        rating = 4,
    )

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
    fun fullScreenDex() = capture("lyrics-full-screen-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
        LyricsFullScreen(
            title = "Sabaha Kadar",
            subtitle = "Ada Karan · Gece Mavisi",
            load = LyricsLoad.Found(LyricsSamples.track, LyricsSamples.synced),
            timeMs = { LyricsSamples.WORD_TIME_MS },
            onSeek = {},
            onDelay = {},
            onClose = {},
        )
    }

    @Test @Config(qualifiers = "en-w411dp-h891dp-xhdpi")
    fun phoneEnglish() = capture("lyrics-phone-light-en", ThemeMode.Light, InputMode.Touch) {
        PhonePlayer(
            info = playing,
            progress = { 0.09f },
            waveform = MusicSamples.waveform,
            actions = NoPlayerActions,
            onClose = {},
            artwork = { size -> Artwork(null, size, corner = 12.dp, video = false) },
            onRate = {},
            lyrics = { modifier ->
                LyricsPane(
                    load = LyricsLoad.Found(LyricsSamples.track, LyricsSamples.synced.copy(offsetMs = 300)),
                    timeMs = { 18_500L },
                    onSeek = {},
                    onDelay = {},
                    modifier = modifier,
                    onFullScreen = {},
                )
            },
            lyricsShown = true,
            onLyricsShown = {},
        )
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun plainPhone() = capture("lyrics-plain-phone-dark", ThemeMode.Dark, InputMode.Touch) {
        PhonePlayer(
            info = playing,
            progress = { 0.3f },
            waveform = MusicSamples.waveform,
            actions = NoPlayerActions,
            onClose = {},
            artwork = { size -> Artwork(null, size, corner = 12.dp, video = false) },
            onRate = {},
            lyrics = { modifier ->
                LyricsPane(
                    load = LyricsLoad.Found(LyricsSamples.track, LyricsSamples.plain),
                    timeMs = { 70_000L },
                    onSeek = {},
                    onDelay = {},
                    modifier = modifier,
                    onFullScreen = {},
                )
            },
            lyricsShown = true,
            onLyricsShown = {},
        )
    }
}
