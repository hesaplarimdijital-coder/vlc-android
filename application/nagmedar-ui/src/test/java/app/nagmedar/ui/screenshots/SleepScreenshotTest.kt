package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.music.PlaylistSummary
import app.nagmedar.ui.sleep.Alarm
import app.nagmedar.ui.sleep.AlarmSection
import app.nagmedar.ui.sleep.AlarmSource
import app.nagmedar.ui.sleep.SleepTarget
import app.nagmedar.ui.sleep.SleepTimer
import app.nagmedar.ui.sleep.SleepTimerSurface
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

/**
 * S-1007, for approval: the sleep timer's window to set it in DeX, and running on a phone in
 * English; Settings › Alarm on weekdays with a playlist, the exact-alarm permission refused, in
 * DeX, and once, from a stream, on a phone in English.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SleepScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private object Playing : SleepTarget {
        override val playing = true
        override val volume = 100
        override val trackLeftMs: Long? = null

        override fun setVolume(volume: Int) = Unit

        override fun stop() = Unit

        override fun stopAtTrackEnd() = true

        override fun goOnAfterTrack() = Unit
    }

    /** A timer on a still clock: what is left stays as set. */
    private fun timer() = SleepTimer(target = { Playing }, scope = CoroutineScope(Job().apply { cancel() }), now = { 1_000_000L })

    /** Monday 12 October 2026, 22:15 where the test runs. */
    private val evening = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, 12, 22, 15)
    }.timeInMillis

    private val playlists = listOf(PlaylistSummary(3, "Sabah", 12, 2_400_000), PlaylistSummary(4, "Yol", 8, 1_900_000))

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

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-qwerty")
    fun timerDex() {
        val timer = timer()
        capture("sleep-timer-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SleepTimerSurface(timer) {} }
        }
    }

    @Test @Config(qualifiers = "en-w411dp-h891dp-xhdpi")
    fun timerRunningPhoneEnglish() {
        val timer = timer().apply { start(minutes = 45, atTrackEnd = true, fade = true) }
        capture("sleep-timer-running-phone-light-en", ThemeMode.Light, InputMode.Touch) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SleepTimerSurface(timer) {} }
        }
    }

    @Test @Config(qualifiers = "tr-w1280dp-h1000dp-mdpi-qwerty")
    fun alarmDex() = capture("settings-alarm-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
        Box(Modifier.padding(top = 16.dp)) {
            AlarmSection(
                initial = Alarm(
                    enabled = true, hour = 6, minute = 30, days = setOf(1, 2, 3, 4, 5),
                    source = AlarmSource.Playlist(3, "Sabah"), rampSeconds = 120, loudness = 40,
                ),
                exact = false,
                playlists = playlists,
                now = evening,
                onChange = {},
            )
        }
    }

    @Test @Config(qualifiers = "en-w390dp-h1400dp-hdpi")
    fun alarmPhoneEnglish() = capture("settings-alarm-phone-light-en", ThemeMode.Light, InputMode.Touch) {
        Box(Modifier.padding(top = 16.dp)) {
            AlarmSection(
                initial = Alarm(enabled = true, hour = 7, minute = 15, source = AlarmSource.Stream("https://radyo.example/canli"), rampSeconds = 300, loudness = 65),
                exact = true,
                playlists = playlists,
                now = evening,
                onChange = {},
            )
        }
    }
}
