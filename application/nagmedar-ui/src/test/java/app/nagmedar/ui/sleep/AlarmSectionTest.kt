package app.nagmedar.ui.sleep

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.music.PlaylistSummary
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar
import androidx.compose.ui.input.InputMode as ComposeInputMode

/**
 * S-1007: Settings › Alarm: turned on, its time typed and stepped, its days, what it plays and how
 * its volume rises; each change kept, and the next ring said.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h1400dp-mdpi")
class AlarmSectionTest {

    @get:Rule
    val compose = createComposeRule()

    private val kept = mutableListOf<Alarm>()
    private val last: Alarm get() = kept.last()
    private lateinit var inputModes: InputModeManager

    /** Monday 12 October 2026, 22:15 where the test runs. */
    private val now = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, 12, 22, 15)
    }.timeInMillis

    private fun render(alarm: Alarm) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                inputModes = LocalInputModeManager.current
                Box(Modifier.verticalScroll(rememberScrollState())) {
                    AlarmSection(
                        initial = alarm,
                        exact = true,
                        playlists = listOf(PlaylistSummary(3, "Sabah", 12, 2_400_000), PlaylistSummary(4, "Yol", 8, 1_900_000)),
                        now = now,
                        onChange = { kept += it },
                    )
                }
            }
        }
        compose.waitForIdle()
        // a hardware key takes the window out of touch mode; injected keys do not
        compose.runOnIdle { inputModes.requestInputMode(ComposeInputMode.Keyboard) }
    }

    @Test
    fun turnedOnAtItsTimeOnItsDays() {
        render(Alarm())
        compose.onNodeWithTag("alarm-hour").assertDoesNotExist()
        compose.onNodeWithTag("alarm-switch").performClick()
        assertTrue(last.enabled)
        compose.onNodeWithTag("alarm-next").assertTextEquals("Sonraki: yarın 07:00 · 8 sa 45 dk sonra")
        // typed: the digits come in from the right
        compose.onNodeWithTag("alarm-hour").performTextReplacement("6")
        assertEquals(6, last.hour)
        // stepped: the minutes by five, round past the hour
        compose.onNodeWithTag("alarm-minute").requestFocus()
        compose.onNodeWithTag("alarm-minute").performKeyInput { pressKey(Key.DirectionUp) }
        assertEquals(5, last.minute)
        compose.onNodeWithTag("alarm-minute").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
        }
        assertEquals(55, last.minute)
        assertEquals(6, last.hour)
        compose.onNodeWithTag("alarm-next").assertTextEquals("Sonraki: yarın 06:55 · 8 sa 40 dk sonra")
        // the weekend only: Saturday is the next, its name said
        compose.onNodeWithText("Cmt").performClick()
        compose.onNodeWithText("Paz").performClick()
        assertEquals(setOf(6, 7), last.days)
        compose.onNodeWithTag("alarm-next").assertTextEquals("Sonraki: Cumartesi 06:55")
    }

    @Test
    fun whatItPlaysAndHowItRises() {
        render(Alarm(enabled = true))
        compose.onNodeWithTag("alarm-source").performClick()
        compose.onNodeWithText("Sabah").performClick()
        assertEquals(AlarmSource.Playlist(3, "Sabah"), last.source)
        compose.onNodeWithTag("alarm-source").performClick()
        compose.onNodeWithText("Akış (internet radyosu)").performClick()
        assertEquals(AlarmSource.Stream(""), last.source)
        compose.onNodeWithTag("alarm-stream").performTextInput("https://radyo.example/canli")
        assertEquals(AlarmSource.Stream("https://radyo.example/canli"), last.source)
        compose.onNodeWithText("2 dk").performClick()
        assertEquals(120, last.rampSeconds)
        compose.onNodeWithTag("alarm-loudness").performSemanticsAction(SemanticsActions.SetProgress) { it(0.3f) }
        assertEquals(30, last.loudness)
        // nothing else changed on the way
        assertEquals(Alarm(enabled = true, source = AlarmSource.Stream("https://radyo.example/canli"), rampSeconds = 120, loudness = 30), last)
    }
}
