package app.nagmedar.ui.sleep

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Android 17: an app that targets it makes no sound from the background the user did not start, so the alarm rings
 * through its page, over the lock screen, which starts the music; an app that targets 36 rings as before.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-xxhdpi")
class AlarmPageTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun onlyAnAppThatTargets17RingsThroughThePage() {
        assertFalse(Alarms.ringsThroughPage(sdk = 36, targetSdk = 36))
        // the shipped build on the owner's Android 17 devices: the music as before
        assertFalse(Alarms.ringsThroughPage(sdk = 37, targetSdk = 36))
        assertTrue(Alarms.ringsThroughPage(sdk = 37, targetSdk = 37))
        assertFalse(Alarms.ringsThroughPage(sdk = 36, targetSdk = 37))
    }

    @Test
    fun thePageStopsTheMusicOrLeavesItPlaying() {
        var stopped = 0
        var kept = 0
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                AlarmPage(time = "07:00", onStop = { stopped++ }, onKeepPlaying = { kept++ })
            }
        }
        compose.onNodeWithTag("alarm-page").assertExists()
        compose.onNodeWithText("Alarm").assertExists()
        compose.onNodeWithText("07:00").assertExists()
        compose.onNodeWithText("Müziği durdur").performClick()
        assertEquals(1, stopped)
        compose.onNodeWithText("Çalmaya devam et").performClick()
        assertEquals(1, kept)
    }
}
