package app.nagmedar.ui.screenshots

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.audio.SignalPath
import app.nagmedar.ui.audio.MusicSection
import app.nagmedar.ui.audio.UsbDac
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.videolan.tools.NAGMEDAR_AUDIO_BALANCE
import org.videolan.tools.NAGMEDAR_AUDIO_MONO
import org.videolan.tools.NAGMEDAR_AUDIO_PITCH
import org.videolan.tools.NAGMEDAR_CROSSFADE_MS
import org.videolan.tools.NAGMEDAR_MUSIC_ENGINE
import org.videolan.tools.NAGMEDAR_MUSIC_OUTPUT
import org.videolan.tools.NAGMEDAR_OUTPUT_USB
import org.videolan.tools.NAGMEDAR_REPLAYGAIN_ALBUM
import org.videolan.tools.NAGMEDAR_REPLAYGAIN_MODE
import org.videolan.tools.NAGMEDAR_REPLAYGAIN_PREAMP
import org.videolan.tools.NAGMEDAR_REPLAYGAIN_TRACK
import org.videolan.vlc.media.ReplayGainScan

/**
 * S-901 and the engine's stories (S-902, S-903, S-905, S-907, S-908, S-909): Settings › Music
 * in DeX with a USB DAC waiting for permission and a scan under way, on a phone in English
 * with pitch, balance and mono set, and in a build without the engine.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MusicSettingsScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val bitPerfectUsb = SignalPath.parse(
        """{"state":1,"source":{"id":7,"url":"/a.flac","codec":"flac","container":"flac","rate":96000,"channels":2,"bits":24,"float":false,"duration_ms":3000,"bitrate":2110618},"output":{"sink":"usb","rate":96000,"channels":2,"format":"s32","bits":32,"exclusive":true},"volume":1.0,"crossfade_ms":0,"replaygain":{"mode":"off","preamp_db":0,"gain_db":0,"tagged":false,"clipping_prevented":false},"tempo":1.0,"pitch":0,"balance":0,"mono":false,"dsp":[],"dsp_active":false,"dsp_level_db":0,"ab_side":"A","ab_gain_db":0,"bit_perfect":true,"not_bit_perfect_because":""}""",
    )

    private val replayGainOnAAudio = SignalPath.parse(
        """{"state":1,"source":{"id":3,"url":"/b.flac","codec":"flac","container":"flac","rate":44100,"channels":2,"bits":16,"float":false,"duration_ms":3000,"bitrate":900000},"output":{"sink":"aaudio","rate":44100,"channels":2,"format":"s32","bits":32,"exclusive":false},"volume":1.0,"crossfade_ms":2000,"replaygain":{"mode":"track","preamp_db":0,"gain_db":-3.2,"tagged":true,"clipping_prevented":false},"tempo":1.0,"pitch":2,"balance":-0.3,"mono":true,"dsp":[],"dsp_active":false,"dsp_level_db":0,"ab_side":"A","ab_gain_db":0,"bit_perfect":false,"not_bit_perfect_because":"replaygain"}""",
    )

    private fun capture(
        name: String,
        theme: ThemeMode,
        input: InputMode,
        available: Boolean,
        signalPath: SignalPath? = null,
        usbDacs: List<UsbDac> = emptyList(),
        device: String? = null,
        scan: ReplayGainScan.Progress = ReplayGainScan.Progress(),
        fontScale: Float? = null,
        prefs: SharedPreferences.Editor.() -> Unit = {},
    ) {
        // the settings the section reads, as a user left them
        val settings = compose.activity.getSharedPreferences("music-shot-$name", Context.MODE_PRIVATE)
        settings.edit().clear().putBoolean(NAGMEDAR_MUSIC_ENGINE, true).also { it.prefs() }.apply()
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                // 1.0.1: One UI's largest font, as the phone's settings would have it
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides if (fontScale != null) Density(density.density, fontScale) else density) {
                    Box(Modifier.fillMaxSize().background(NagTheme.colors.background).padding(top = 16.dp)) {
                        MusicSection(
                            engineAvailable = available,
                            settings = settings,
                            usbDacs = usbDacs,
                            signalPath = signalPath,
                            outputDevice = device,
                            scan = scan,
                            onOpenEqualizer = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h1100dp-mdpi-keysexposed-qwerty")
    fun dex() = capture(
        "settings-music-dex-dark", ThemeMode.Dark, InputMode.Pointer, available = true,
        signalPath = bitPerfectUsb,
        usbDacs = listOf(UsbDac("Topping D90", permitted = false)),
        device = "Topping D90",
        scan = ReplayGainScan.Progress(running = true, albums = 12, albumsDone = 2, tracksDone = 21, current = "Gece Mavisi"),
    ) {
        putInt(NAGMEDAR_CROSSFADE_MS, 4_000)
        putString(NAGMEDAR_MUSIC_OUTPUT, NAGMEDAR_OUTPUT_USB)
        putString(NAGMEDAR_REPLAYGAIN_MODE, NAGMEDAR_REPLAYGAIN_ALBUM)
        putFloat(NAGMEDAR_REPLAYGAIN_PREAMP, -3f)
    }

    @Test @Config(qualifiers = "en-w390dp-h1900dp-hdpi")
    fun phoneEnglish() = capture(
        "settings-music-phone-light-en", ThemeMode.Light, InputMode.Touch, available = true,
        signalPath = replayGainOnAAudio,
        scan = ReplayGainScan.Progress(finished = true, albums = 12, albumsDone = 12, tracksDone = 140, tracksSkipped = 31, tracksFailed = 2),
    ) {
        putInt(NAGMEDAR_CROSSFADE_MS, 2_000)
        putString(NAGMEDAR_REPLAYGAIN_MODE, NAGMEDAR_REPLAYGAIN_TRACK)
        putFloat(NAGMEDAR_AUDIO_PITCH, 2f)
        putFloat(NAGMEDAR_AUDIO_BALANCE, -0.3f)
        putBoolean(NAGMEDAR_AUDIO_MONO, true)
    }

    /** 1.0.1: the S24's width in Turkish: "Çıkış"'s four options go on two lines, none cut. */
    @Test @Config(qualifiers = "tr-w384dp-h1900dp-hdpi")
    fun phone() = capture("settings-music-phone-dark", ThemeMode.Dark, InputMode.Touch, available = true, signalPath = replayGainOnAAudio)

    /** 1.0.1: the narrowest phone with the largest font: every option whole, a long one on two lines of its own. */
    @Test @Config(qualifiers = "tr-w320dp-h2400dp-hdpi")
    fun narrowLargestFont() = capture(
        "settings-music-phone-narrow-large-light", ThemeMode.Light, InputMode.Touch, available = true,
        signalPath = replayGainOnAAudio, fontScale = 1.3f,
    )

    @Test @Config(qualifiers = "tr-w800dp-h460dp-mdpi")
    fun withoutTheEngine() = capture("settings-music-tablet-dark-missing", ThemeMode.Dark, InputMode.Touch, available = false)
}
