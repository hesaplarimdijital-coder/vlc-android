package app.nagmedar.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** S-301: resizing the window across all five width classes keeps the destination and the screen's state. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellStateTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun stateSurvivesWidthChanges() {
        var widthClass by mutableStateOf(WidthClass.Compact)
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(widthClass, startDestination = Destination.Music, screen = { destination, _ -> CounterScreen(destination) })
            }
        }
        repeat(3) { compose.onNodeWithTag("counter-Music").performClick() }
        compose.onNodeWithTag("counter-Music").assertTextEquals("Music: 3")

        for (next in listOf(WidthClass.Medium, WidthClass.Expanded, WidthClass.Large, WidthClass.ExtraLarge, WidthClass.Compact)) {
            widthClass = next
            compose.waitForIdle()
            compose.onNodeWithTag("counter-Music").assertTextEquals("Music: 3")
        }
    }
}

@Composable
private fun CounterScreen(destination: Destination) {
    var count by rememberSaveable { mutableIntStateOf(0) }
    Text("${destination.name}: $count", Modifier.testTag("counter-${destination.name}").clickable { count++ })
}
