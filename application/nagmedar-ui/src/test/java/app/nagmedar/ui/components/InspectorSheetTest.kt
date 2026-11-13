package app.nagmedar.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.shell.LocalWidthClass
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.8 (Desktop Kit 2.8.0, the design audit's finding 11): below the width that docks it (a phone, a tablet in split
 * screen, a narrow DeX window) the inspector comes up as a sheet; a window that widens shows it beside the main pane.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp")
class InspectorSheetTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var widthClass by mutableStateOf(WidthClass.Compact)
    private lateinit var state: InspectorState

    private fun layout() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalWidthClass provides widthClass) {
                    state = rememberInspectorState()
                    WorkstationLayout(
                        state = state,
                        main = {
                            Column {
                                Text("liste")
                                InspectorToggleButton(state)
                            }
                        },
                        inspector = { Text("etiketler") },
                        sheetTitle = "Ayrıntı paneli",
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun narrowTheButtonBringsTheInspectorUpAsASheet() {
        layout()
        compose.onNodeWithText("etiketler").assertDoesNotExist()
        compose.onNodeWithContentDescription("Ayrıntı panelini gizle").assertDoesNotExist()
        compose.onNodeWithContentDescription("Ayrıntı panelini göster").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("etiketler").assertExists()
        assertTrue(state.sheetOpen)
        compose.onNodeWithContentDescription("Ayrıntı panelini gizle").assertExists()
        // the window widens: the inspector is beside the main pane, the sheet gone
        widthClass = WidthClass.Expanded
        compose.waitForIdle()
        assertFalse(state.sheetOpen)
        assertFalse(state.collapsed)
        compose.onNodeWithText("etiketler").assertExists()
    }
}
