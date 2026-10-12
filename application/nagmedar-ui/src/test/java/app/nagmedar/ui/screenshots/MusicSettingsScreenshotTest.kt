package app.nagmedar.ui.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.screens.MusicSection
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.videolan.tools.NAGMEDAR_CROSSFADE_MS
import org.videolan.tools.NAGMEDAR_MUSIC_ENGINE

/**
 * S-901, S-905: Settings › Music: the engine behind the local music library and the crossfade,
 * in DeX, on a phone in English, and in a build without the engine.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MusicSettingsScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, theme: ThemeMode, input: InputMode, available: Boolean, crossfadeMs: Int = 0) {
        // the settings the section reads, as a user left them
        val settings = compose.activity.getSharedPreferences("music-shot-$name", Context.MODE_PRIVATE)
        settings.edit().putInt(NAGMEDAR_CROSSFADE_MS, crossfadeMs).putBoolean(NAGMEDAR_MUSIC_ENGINE, true).apply()
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background).padding(top = 16.dp)) { MusicSection(engineAvailable = available, settings = settings) }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h560dp-mdpi")
    fun dex() = capture("settings-music-dex-dark", ThemeMode.Dark, InputMode.Pointer, available = true, crossfadeMs = 4_000)

    @Test @Config(qualifiers = "en-w390dp-h900dp-hdpi")
    fun phoneEnglish() = capture("settings-music-phone-light-en", ThemeMode.Light, InputMode.Touch, available = true, crossfadeMs = 2_000)

    @Test @Config(qualifiers = "tr-w800dp-h460dp-mdpi")
    fun withoutTheEngine() = capture("settings-music-tablet-dark-missing", ThemeMode.Dark, InputMode.Touch, available = false)
}
