package app.nagmedar.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.8 (Desktop Kit 2.8.0, the design audit's GEZ-16): tapping the entry of the screen already shown, on its first page,
 * tells that screen (it goes to its top), and only it; another entry switches screens and tells nobody.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class ReselectTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun tab(label: String) = compose.onNode(hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))

    @Test
    fun theShownTabHearsItsEntryAgain() {
        val heard = mutableListOf<Destination>()
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, screen = { destination, _ ->
                    OnReselect { heard += destination }
                    Text("ekran-${destination.route}")
                })
            }
        }
        compose.waitForIdle()
        // Müzik is the start screen: its entry again
        tab("Müzik").performClick()
        compose.waitForIdle()
        assertEquals(listOf(Destination.Music), heard)
        // another entry switches screens and tells nobody
        tab("Podcastler").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("ekran-${Destination.Podcasts.route}").assertExists()
        assertEquals(listOf(Destination.Music), heard)
        // and that screen's own entry again tells it
        tab("Podcastler").performClick()
        compose.waitForIdle()
        assertEquals(listOf(Destination.Music, Destination.Podcasts), heard)
    }
}
