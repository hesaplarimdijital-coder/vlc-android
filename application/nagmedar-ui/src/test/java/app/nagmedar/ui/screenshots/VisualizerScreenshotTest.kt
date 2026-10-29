package app.nagmedar.ui.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.audio.ScopeAnalysis
import app.nagmedar.audio.ScopeFrame
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.NoPlayerActions
import app.nagmedar.ui.music.MusicSamples
import app.nagmedar.ui.music.NowPlayingInfo
import app.nagmedar.ui.music.NowPlayingPanel
import app.nagmedar.ui.music.PhonePlayer
import app.nagmedar.ui.playback.Artwork
import app.nagmedar.ui.playback.QueueEntry
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import app.nagmedar.ui.visual.ScopeProblem
import app.nagmedar.ui.visual.VisualizerFps
import app.nagmedar.ui.visual.VisualizerFullScreen
import app.nagmedar.ui.visual.VisualizerKind
import app.nagmedar.ui.visual.VisualizerPane
import app.nagmedar.ui.visual.VisualizerSection
import app.nagmedar.ui.visual.VisualizerSettings
import app.nagmedar.ui.visual.VisualizerState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * S-611, for approval: still frames of the visualisers: the spectrum in the now playing pane
 * (DeX), the level meter in the phone's player, each kind over the whole window (a tablet, DeX in
 * English, a phone), the video player over sound alone and beside a picture, the permission's
 * notice, and Settings › Visualiser with its battery warning.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualizerScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** A still of music: a few notes with their overtones over a noise that falls with frequency, the left a little louder. */
    private fun musicFrame(): ScopeFrame {
        val rate = 48_000
        val random = Random(611)
        val notes = doubleArrayOf(110.0, 164.8, 220.0, 293.7, 440.0, 659.3)
        fun channel(gain: Double): FloatArray {
            var brown = 0.0
            return FloatArray(4096) { i ->
                var v = 0.0
                notes.forEachIndexed { n, f ->
                    for (h in 1..14) v += 0.16 / (n + 1) / h * sin(2.0 * PI * f * h * i / rate + n + h)
                }
                // a little of white noise, more of a reddened one: the treble's air
                brown = 0.97 * brown + 0.03 * (random.nextDouble() - 0.5)
                (gain * (v + 0.25 * brown + 0.006 * (random.nextDouble() - 0.5))).toFloat()
            }
        }
        val left = channel(0.62)
        val right = channel(0.5)
        val frame = ScopeAnalysis.analyse(left, right, rate, wavePoints = 256)
        return ScopeFrame(frame.rate, frame.fftSize, frame.spectrumDb, floatArrayOf(-4.2f, -6.1f), floatArrayOf(-14.3f, -16.0f), frame.waveLeft, frame.waveRight, 1)
    }

    private fun still(): VisualizerState = VisualizerState().apply { show(musicFrame()) }

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

    private val playing = NowPlayingInfo(
        title = "Neva Kâr",
        artist = "Kadri Erdem",
        album = "Itrî'nin İzinde",
        format = "FLAC 24/96 · USB DAC · 96 kHz · Bit-perfect",
        lengthMs = 498_000,
        playing = true,
        rating = 4,
    )

    @Test @Config(qualifiers = "tr-w1280dp-h900dp-mdpi-keysexposed-qwerty")
    fun nowPlayingDex() {
        val state = still()
        capture("visualizer-now-playing-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                Box(Modifier.width(380.dp).fillMaxHeight()) {
                    NowPlayingPanel(
                        info = playing,
                        progress = { 0.38f },
                        waveform = MusicSamples.waveform,
                        upNext = listOf(QueueEntry("a#1", "Nihâvend Saz Semâîsi", "Kadri Erdem", 401_000)),
                        onSeek = {},
                        onPlayUpNext = {},
                        artwork = { size -> Artwork(null, size, corner = 8.dp, video = false) },
                        visualizer = { modifier -> VisualizerPane(VisualizerKind.Spectrum, state, onKind = {}, modifier = modifier, onFullScreen = {}) },
                        visualizerShown = true,
                        onVisualizerShown = {},
                    )
                }
            }
        }
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun phonePlayer() {
        val state = still()
        capture("visualizer-phone-dark-vu", ThemeMode.Dark, InputMode.Touch) {
            PhonePlayer(
                info = playing.copy(format = "FLAC 24/96 · AAudio · 48 kHz"),
                progress = { 0.42f },
                waveform = MusicSamples.waveform,
                actions = NoPlayerActions,
                onClose = {},
                artwork = { size -> Artwork(null, size, corner = 12.dp, video = false) },
                visualizer = { modifier -> VisualizerPane(VisualizerKind.Vu, state, onKind = {}, modifier = modifier, onFullScreen = {}) },
                visualizerShown = true,
                onVisualizerShown = {},
            )
        }
    }

    private fun fullScreen(name: String, theme: ThemeMode, input: InputMode, kind: VisualizerKind, cost: String) {
        val state = still()
        capture(name, theme, input) {
            VisualizerFullScreen(
                title = playing.title,
                subtitle = "Kadri Erdem · Itrî'nin İzinde",
                kind = kind,
                state = state,
                cost = cost,
                onKind = {},
                onClose = {},
            )
        }
    }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun spectrumTablet() = fullScreen("visualizer-full-tablet-light-spectrum", ThemeMode.Light, InputMode.Touch, VisualizerKind.Spectrum, "≈ 30 fps'de saatte pilin %0,4 kadarı")

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi-keysexposed-qwerty")
    fun scopeDexEnglish() = fullScreen("visualizer-full-dex-dark-scope-en", ThemeMode.Dark, InputMode.Pointer, VisualizerKind.Scope, "≈ 1.4% of the battery an hour at 60 fps")

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun vuPhone() = fullScreen("visualizer-full-phone-light-vu", ThemeMode.Light, InputMode.Touch, VisualizerKind.Vu, "≈ 15 fps'de saatte pilin %0,3 kadarı")

    private fun settings(name: String, theme: ThemeMode, input: InputMode, changed: Boolean) {
        val prefs = NagmedarPreferences(compose.activity.getSharedPreferences("visualizer-shot-$name", Context.MODE_PRIVATE))
        if (changed) prefs.updateVisualizer(VisualizerSettings(music = true, video = true, kind = VisualizerKind.Vu, fps = VisualizerFps.Fps60))
        capture(name, theme, input) {
            Box(Modifier.padding(top = 16.dp)) { VisualizerSection(prefs, batteryWh = 44.66) }
        }
    }

    @Test @Config(qualifiers = "tr-w1280dp-h720dp-mdpi-keysexposed-qwerty")
    fun settingsDex() = settings("settings-visualizer-dex-dark", ThemeMode.Dark, InputMode.Pointer, changed = true)

    @Test @Config(qualifiers = "tr-w800dp-h720dp-mdpi")
    fun settingsTablet() = settings("settings-visualizer-tablet-light", ThemeMode.Light, InputMode.Touch, changed = false)

    @Test @Config(qualifiers = "tr-w390dp-h1000dp-hdpi")
    fun settingsPhone() = settings("settings-visualizer-phone-dark", ThemeMode.Dark, InputMode.Touch, changed = false)

    @Test @Config(qualifiers = "en-w800dp-h720dp-mdpi")
    fun settingsTabletEnglish() = settings("settings-visualizer-tablet-light-en", ThemeMode.Light, InputMode.Touch, changed = true)
}
