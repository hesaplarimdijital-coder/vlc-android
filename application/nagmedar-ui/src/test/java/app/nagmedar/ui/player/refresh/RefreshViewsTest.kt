package app.nagmedar.ui.player.refresh

import android.content.Context
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A film on a tablet's 120 Hz panel, which has no rate the film's divides. */
internal val sampleTablet = RefreshStatus(fps = FILM_FPS, hz = 120.0, screen = ScreenKind.Tablet, rates = listOf(60.0, 120.0))

/** The film on a TV over HDMI, switched to its rate. */
internal val sampleTv = RefreshStatus(fps = FILM_FPS, hz = 23.976, screen = ScreenKind.External, screenName = "LG TV", rates = tvRates)

/** The film on a phone's 60 Hz screen, whose Android switches only seamlessly. */
internal val samplePhone = RefreshStatus(fps = FILM_FPS, hz = 60.0, screen = ScreenKind.Phone, rates = listOf(60.0), system = SystemMatch.Seamless)

/**
 * S-612: the sync tab's refresh rate matching tells the film's rate, the screen's and how often a
 * frame repeats; whether that is a whole multiple; what the choice does and what keeps the screen
 * from it; the choice is the user's, kept, VLC's own switch counting before there is one.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class RefreshViewsTest {

    @get:Rule
    val compose = createComposeRule()

    private val chosen = mutableListOf<RefreshMatch>()

    private fun section(status: RefreshStatus, match: RefreshMatch) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                RefreshSection(status, match) { chosen += it }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aFilmOnATabletsPanel() {
        section(sampleTablet, RefreshMatch.Always)
        compose.onNodeWithText("YENİLEME HIZI EŞLEME").assertExists()
        compose.onNodeWithTag("refresh-badge").assertTextEquals("tam katı değil")
        compose.onNodeWithTag("refresh-content").assertTextEquals("23,976 fps")
        compose.onNodeWithTag("refresh-screen").assertTextEquals("120 Hz · tablet paneli")
        compose.onNodeWithText("Kare tekrarı").assertExists()
        compose.onNodeWithTag("refresh-cadence").assertTextEquals("≈ 8 sn'de bir · göze zor çarpar")
        compose.onNodeWithText("HDMI ile TV'ye bağlıyken ekran 23,976 Hz moduna geçirilir; geçişte ~1 sn siyah ekran olabilir.").assertExists()
        compose.onNodeWithText("Bu ekranın hiçbir hızı filmin tam katı değil: 60 / 120 Hz.").assertExists()
        compose.onNodeWithText("Kapalı").performClick()
        compose.onNodeWithText("Kesintisizse").performClick()
        assertEquals(listOf(RefreshMatch.Off, RefreshMatch.Seamless), chosen)
    }

    @Test
    fun aTvAtTheFilmsRate() {
        section(sampleTv, RefreshMatch.Always)
        compose.onNodeWithTag("refresh-badge").assertTextEquals("tam katı")
        compose.onNodeWithTag("refresh-screen").assertTextEquals("23,976 Hz · LG TV")
        compose.onNodeWithTag("refresh-cadence").assertTextEquals("yok · her kare eşit süre kalır")
        compose.onNodeWithText("Bu ekranın hiçbir hızı", substring = true).assertDoesNotExist()
    }

    @Test
    fun aPhoneShowsAFilmThreeAndTwo() {
        section(samplePhone, RefreshMatch.Always)
        compose.onNodeWithTag("refresh-cadence").assertTextEquals("saniyede ≈ 12 · kaydırmalarda takılır")
        compose.onNodeWithText("Bu ekranın hiçbir hızı filmin tam katı değil: 60 Hz.").assertExists()
        compose.onNodeWithText("Android'in kendi ayarı yalnızca kesintisiz geçişe izin veriyor.").assertExists()
    }

    @Test
    fun offKeepsTheScreensRateAndSaysNoMore() {
        section(samplePhone, RefreshMatch.Off)
        compose.onNodeWithText("Ekran kendi hızında kalır.").assertExists()
        compose.onNodeWithText("Bu ekranın hiçbir hızı", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Android'in kendi ayarı", substring = true).assertDoesNotExist()
    }

    @Test
    fun aSlowerScreenLeavesFramesUnseen() {
        section(RefreshStatus(fps = 60000.0 / 1001, hz = 50.0, screen = ScreenKind.External, screenName = "HDMI", rates = listOf(50.0)), RefreshMatch.Seamless)
        compose.onNodeWithText("Kare atlama").assertExists()
        compose.onNodeWithTag("refresh-cadence").assertTextEquals("saniyede ≈ 10 · hareket kesik görünür")
    }

    @Test
    fun beforeTheFilmIsReadNothingIsGuessed() {
        section(RefreshStatus(), RefreshMatch.Seamless)
        compose.onNodeWithTag("refresh-badge").assertDoesNotExist()
        compose.onNodeWithTag("refresh-content").assertTextEquals("—")
        compose.onNodeWithTag("refresh-cadence").assertTextEquals("—")
    }

    @Test
    fun theChoiceIsKeptAndVlcsSwitchCountsBeforeIt() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("refresh-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        assertEquals(RefreshMatch.Seamless, RefreshMatch.read(prefs))
        prefs.edit().putBoolean("video_match_frame_rate", true).commit()
        assertEquals(RefreshMatch.Always, RefreshMatch.read(prefs))
        prefs.edit().putString(RefreshMatch.KEY, RefreshMatch.Off.key).commit()
        assertEquals(RefreshMatch.Off, RefreshMatch.read(prefs))
    }
}
