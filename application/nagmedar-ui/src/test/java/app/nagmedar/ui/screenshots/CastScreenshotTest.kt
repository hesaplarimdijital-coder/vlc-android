package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.NoPlayerActions
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.cast.CastDevice
import app.nagmedar.ui.cast.CastNotice
import app.nagmedar.ui.cast.CastPickerSurface
import app.nagmedar.ui.cast.CastPickerView
import app.nagmedar.ui.components.VerticalRule
import app.nagmedar.ui.music.NowPlayingInfo
import app.nagmedar.ui.music.NowPlayingPanel
import app.nagmedar.ui.music.PhonePlayer
import app.nagmedar.ui.music.MusicSamples
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.playback.QueueEntry
import app.nagmedar.ui.samplePlayerInfo
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
 * S-1302, for approval: the device picker searching, with the devices found (the one cast to
 * marked, what is not cast as it is said), as a sheet on a phone in English, nothing found and no
 * local network; and casting: the music view and the transport bar on DeX, the mini player on a
 * tablet and the phone's music player in English.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CastScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val salon = CastDevice.of("Salon TV", "chromecast")
    private val kitchen = CastDevice.of("Mutfak", "stream_out_dlna")
    private val study = CastDevice.of("Çalışma odası", "chromecast")

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

    /** The picker over the shell of [widthClass]. */
    @Composable
    private fun Picker(widthClass: WidthClass, view: CastPickerView, active: CastDevice? = null, notices: List<CastNotice> = emptyList()) {
        val casting = samplePlayerInfo.copy(canCast = true, casting = active)
        TestShell(widthClass, startDestination = Destination.Music, playerInfo = casting)
        Box(Modifier.fillMaxSize().background(NagTheme.colors.scrim)) {
            CastPickerSurface(view = view, active = active, notices = notices, onChoose = {}, onThisDevice = {}, onClose = {})
        }
    }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun searchingTablet() = capture("cast-picker-searching-tablet-dark", ThemeMode.Dark, InputMode.Touch) {
        Picker(WidthClass.Expanded, CastPickerView.Searching)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
    fun devicesDex() = capture("cast-picker-devices-dex-light", ThemeMode.Light, InputMode.Pointer) {
        Picker(WidthClass.Large, CastPickerView.Devices(listOf(salon, kitchen, study), searching = true), active = salon, notices = listOf(CastNotice.DsdAsPcm, CastNotice.EngineSettings))
    }

    @Test @Config(qualifiers = "en-w411dp-h891dp-xhdpi")
    fun devicesPhoneEnglish() = capture("cast-picker-devices-phone-dark-en", ThemeMode.Dark, InputMode.Touch) {
        Picker(WidthClass.Compact, CastPickerView.Devices(listOf(salon, kitchen), searching = false), notices = listOf(CastNotice.TrackerHere))
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun emptyPhone() = capture("cast-picker-empty-phone-light", ThemeMode.Light, InputMode.Touch) {
        Picker(WidthClass.Compact, CastPickerView.Empty)
    }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun noNetworkTablet() = capture("cast-picker-nolan-tablet-light", ThemeMode.Light, InputMode.Touch) {
        Picker(WidthClass.Expanded, CastPickerView.NoNetwork)
    }

    private val track = NowPlayingInfo(
        title = "Neva Kâr",
        artist = "Kadri Erdem",
        album = "Itrî'nin İzinde",
        format = "FLAC 24/96",
        lengthMs = 498_000,
        playing = true,
        rating = 4,
    )

    private val upNext = listOf(
        QueueEntry("b#1", "Gece Yolcusu", "Cemre Kaya", 254_000),
        QueueEntry("c#1", "Yağmur", "Cemre Kaya", 231_000),
    )

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
    fun musicDex() = capture("casting-music-dex-light", ThemeMode.Light, InputMode.Pointer) {
        TestShell(WidthClass.Large, startDestination = Destination.Music, playerInfo = samplePlayerInfo.copy(canCast = true, casting = salon)) { _, _ ->
            Row(Modifier.fillMaxSize()) {
                Spacer(Modifier.weight(1f))
                VerticalRule()
                NowPlayingPanel(
                    info = track,
                    progress = { 0.42f },
                    waveform = MusicSamples.waveform,
                    upNext = upNext,
                    onSeek = {},
                    onPlayUpNext = {},
                    modifier = Modifier.width(340.dp).fillMaxHeight(),
                    artwork = { size -> Artwork(null, size, corner = 8.dp, video = false) },
                    canCast = true,
                    casting = salon,
                )
            }
        }
    }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun miniPlayerTablet() = capture("casting-mini-tablet-light", ThemeMode.Light, InputMode.Touch) {
        TestShell(WidthClass.Expanded, startDestination = Destination.Music, playerInfo = samplePlayerInfo.copy(canCast = true, casting = kitchen))
    }

    @Test @Config(qualifiers = "en-w411dp-h891dp-xhdpi")
    fun phonePlayerEnglish() = capture("casting-music-phone-dark-en", ThemeMode.Dark, InputMode.Touch) {
        PhonePlayer(
            info = track,
            progress = { 0.42f },
            waveform = MusicSamples.waveform,
            actions = NoPlayerActions,
            onClose = {},
            artwork = { size -> Artwork(null, size, corner = 12.dp, video = false) },
            canCast = true,
            casting = salon,
        )
    }
}
