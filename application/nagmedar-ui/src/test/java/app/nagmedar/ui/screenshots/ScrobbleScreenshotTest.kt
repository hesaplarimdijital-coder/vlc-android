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
import app.nagmedar.ui.scrobble.ScrobbleActions
import app.nagmedar.ui.scrobble.ScrobbleContent
import app.nagmedar.ui.scrobble.ScrobbleRow
import app.nagmedar.ui.scrobble.ScrobbleServiceId
import app.nagmedar.ui.scrobble.SignInProblem
import app.nagmedar.ui.scrobble.SignInState
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
 * S-1008, for approval: Settings › Scrobble in DeX with Last.fm signed in and ListenBrainz held;
 * on a tablet with Last.fm's sign-in open; on a phone with the sign-in refused; on a phone in
 * English in a build without Last.fm's keys, with ListenBrainz's token form open.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScrobbleScreenshotTest {

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

    private val lastFmIn = ScrobbleRow(ScrobbleServiceId.LastFm, available = true, account = "ada_karan", enabled = true, waiting = 4, held = false)
    private val brainzHeld = ScrobbleRow(ScrobbleServiceId.ListenBrainz, available = true, account = "ada_karan", enabled = true, waiting = 27, held = true)
    private val lastFmOut = ScrobbleRow(ScrobbleServiceId.LastFm, available = true, account = null, enabled = false, waiting = 0, held = false)
    private val brainzOut = ScrobbleRow(ScrobbleServiceId.ListenBrainz, available = true, account = null, enabled = false, waiting = 0, held = false)

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun dex() = capture("settings-scrobble-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
        Box(Modifier.padding(top = 16.dp)) { ScrobbleContent(listOf(lastFmIn, brainzHeld), SignInState.Closed, ScrobbleActions()) }
    }

    @Test @Config(qualifiers = "tr-w800dp-h1000dp-mdpi")
    fun tabletSigningIn() = capture("settings-scrobble-tablet-light", ThemeMode.Light, InputMode.Touch) {
        Box(Modifier.padding(top = 16.dp)) {
            ScrobbleContent(listOf(lastFmOut, brainzOut), SignInState.Open(ScrobbleServiceId.LastFm), ScrobbleActions())
        }
    }

    @Test @Config(qualifiers = "tr-w411dp-h1500dp-xhdpi")
    fun phoneRefused() = capture("settings-scrobble-phone-dark", ThemeMode.Dark, InputMode.Touch) {
        Box(Modifier.padding(top = 16.dp)) {
            ScrobbleContent(listOf(lastFmOut, brainzIn()), SignInState.Open(ScrobbleServiceId.LastFm, SignInProblem.Refused), ScrobbleActions())
        }
    }

    @Test @Config(qualifiers = "en-w390dp-h1500dp-hdpi")
    fun phoneEnglishWithoutLastFmKeys() = capture("settings-scrobble-phone-light-en", ThemeMode.Light, InputMode.Touch) {
        Box(Modifier.padding(top = 16.dp)) {
            ScrobbleContent(listOf(lastFmOut.copy(available = false), brainzOut), SignInState.Open(ScrobbleServiceId.ListenBrainz), ScrobbleActions())
        }
    }

    private fun brainzIn() = ScrobbleRow(ScrobbleServiceId.ListenBrainz, available = true, account = "ada_karan", enabled = false, waiting = 0, held = false)
}
