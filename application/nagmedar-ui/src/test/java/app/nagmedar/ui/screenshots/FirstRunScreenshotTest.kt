package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.onboarding.AudioRoute
import app.nagmedar.ui.onboarding.FirstRunActions
import app.nagmedar.ui.onboarding.FirstRunFlow
import app.nagmedar.ui.onboarding.FirstRunState
import app.nagmedar.ui.onboarding.FirstRunStep
import app.nagmedar.ui.onboarding.StorageSource
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S-309: the first-run steps on a phone (390 x 844, the mockup's size) and in a DeX window. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FirstRunScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val noop = {}
    private val actions = FirstRunActions(noop, noop, noop, noop, {}, noop, noop, noop, noop)
    private val drives = listOf(
        StorageSource("/storage/emulated/0", "", removable = false, totalBytes = 512_000_000_000),
        StorageSource("/storage/1A2B-3C4D", "SAMSUNG T7", removable = true, totalBytes = 2_000_000_000_000),
    )

    private fun capture(name: String, state: FirstRunState, theme: ThemeMode = ThemeMode.Dark, input: InputMode = InputMode.Touch,
                        folders: List<Pair<String, String>> = emptyList()) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) { FirstRunFlow(state, drives, folders, actions) }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun access() = capture("first-run-1-access", FirstRunState().apply { mediaAccess = true })

    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun accessRefused() = capture(
        "first-run-1-refused",
        FirstRunState().apply { accessDenied = true },
        folders = listOf("content://tree/primary%3AFilmler" to "Filmler"),
    )

    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun sources() = capture("first-run-2-sources", FirstRunState(FirstRunStep.Sources).apply { mediaAccess = true })

    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun sourcesLight() = capture("first-run-2-sources-light", FirstRunState(FirstRunStep.Sources).apply { mediaAccess = true }, ThemeMode.Light)

    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun audio() = capture(
        "first-run-3-audio",
        FirstRunState(FirstRunStep.Audio).apply {
            audioRoute = AudioRoute("USB · FiiO K7", 48_000)
            audioHeard = true
        },
    )

    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun notifications() = capture("first-run-4-notifications", FirstRunState(FirstRunStep.Notifications))

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun dexWindow() = capture("first-run-dex", FirstRunState(FirstRunStep.Sources).apply { mediaAccess = true }, input = InputMode.Pointer)
}
