package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.BigPlayerWindow
import app.nagmedar.ui.SamplePlayerBar
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.playback.PlayerBarInfo
import app.nagmedar.ui.podcast.Chapter
import app.nagmedar.ui.samplePlayerInfo
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.ArtworkAccent
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
 * 1.0.2: the big player over a tablet's window (landscape light and dark, upright) and a desktop's
 * (an external monitor), opened from the bar; and the phone's bar, the tablet's laid out for a phone
 * (music in light and dark, a podcast episode with chapters, a station on the air, 320 dp with the
 * largest text), its seek bar in the artwork's colour.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BigPlayerScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, theme: ThemeMode, input: InputMode, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) { content() }
                }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    private fun big(name: String, widthClass: WidthClass, theme: ThemeMode, input: InputMode, tint: Color, lyrics: Boolean = false) {
        val window = BigPlayerWindow(widthClass, open = true)
        capture(name, theme, input) { window.Content(tint = tint, lyrics = lyrics) }
    }

    /** The user's Tab S11 Ultra held sideways: the sidebar's window, by touch. */
    @Test @Config(qualifiers = "tr-w1392dp-h870dp-mdpi")
    fun tabletLight() = big("music-player-tablet-light", WidthClass.Large, ThemeMode.Light, InputMode.Touch, Color(0xFF2E86C1))

    @Test @Config(qualifiers = "tr-w1392dp-h870dp-mdpi")
    fun tabletDark() = big("music-player-tablet-dark", WidthClass.Large, ThemeMode.Dark, InputMode.Touch, Color(0xFFC0392B), lyrics = true)

    /** Upright: the rail's window, the phone's composition scaled up. */
    @Test @Config(qualifiers = "tr-w870dp-h1392dp-mdpi")
    fun tabletUpright() = big("music-player-tablet-portrait-light", WidthClass.Expanded, ThemeMode.Light, InputMode.Touch, Color(0xFF27AE60), lyrics = true)

    /** DeX on an external monitor: the pair in the middle of a wide window, by mouse. */
    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi-keysexposed-qwerty")
    fun dex() = big("music-player-dex-dark", WidthClass.ExtraLarge, ThemeMode.Dark, InputMode.Pointer, Color(0xFF8E44AD), lyrics = true)

    // ---------------------------------------------------------------- the phone's bar

    /** The phone's music page with the bar as the app shows it: its accent from the artwork. */
    private fun phone(name: String, theme: ThemeMode, info: PlayerBarInfo, seed: Color?, fontScale: Float = 1f) =
        capture(name, theme, InputMode.Touch, fontScale) {
            TestShell(WidthClass.Compact, startDestination = Destination.Music, player = { kind -> ArtworkAccent(seed) { SamplePlayerBar(kind, info) } })
        }

    @Test @Config(qualifiers = "tr-w384dp-h832dp-xhdpi")
    fun phoneBarLight() = phone("player-bar-phone-light", ThemeMode.Light, samplePlayerInfo, Color(0xFF2E86C1))

    @Test @Config(qualifiers = "tr-w384dp-h832dp-xhdpi")
    fun phoneBarDark() = phone("player-bar-phone-dark", ThemeMode.Dark, samplePlayerInfo, Color(0xFFC0392B))

    /** A podcast episode: its chapters' ticks on the seek bar, the chapter playing in the second line. */
    @Test @Config(qualifiers = "en-w384dp-h832dp-xhdpi")
    fun phoneBarPodcast() = phone(
        "player-bar-phone-podcast-dark-en",
        ThemeMode.Dark,
        samplePlayerInfo.copy(
            title = "Episode 42: Makam and memory",
            subtitle = "Sound Archive",
            lengthMs = 2_940_000,
            format = "MP3 128",
            chapters = listOf(
                Chapter(0, "Opening"),
                Chapter(420_000, "The first recordings"),
                Chapter(1_100_000, "Interview"),
                Chapter(2_400_000, "Listener letters"),
            ),
        ),
        Color(0xFF16A085),
    )

    /** A station on the air: live, nothing to seek; it opens its page and does not skip. */
    @Test @Config(qualifiers = "tr-w384dp-h832dp-xhdpi")
    fun phoneBarRadio() = phone(
        "player-bar-phone-radio-light",
        ThemeMode.Light,
        samplePlayerInfo.copy(title = "Nağme FM", subtitle = "Şimdi çalıyor", lengthMs = 0L, format = "AAC 128", live = true),
        null,
    )

    /** The narrowest phone with the largest text: the words shrink to an ellipsis, nothing else gives. */
    @Test @Config(qualifiers = "tr-w320dp-h640dp-xhdpi")
    fun phoneBarNarrowLargeText() = phone("player-bar-phone-narrow-large-light", ThemeMode.Light, samplePlayerInfo, Color(0xFFF1C40F), fontScale = 2f)
}
