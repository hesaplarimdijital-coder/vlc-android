package app.nagmedar.ui.audio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.audio.DspSettings
import app.nagmedar.audio.GraphicPresets
import app.nagmedar.audio.HeadphoneProfile
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.videolan.tools.NAGMEDAR_DSP
import org.videolan.tools.NAGMEDAR_DSP_PROFILES

/** S-906: what the equaliser's controls write for the engine. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h1700dp-mdpi-qwerty")
class EqualizerScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var settings: SharedPreferences

    private val written: DspSettings get() = DspSettings.fromJson(settings.getString(NAGMEDAR_DSP, null))

    @Before
    fun show() {
        settings = compose.activity.getSharedPreferences("eq-test", Context.MODE_PRIVATE)
        settings.edit().clear().apply()
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                EqualizerScreen(state = rememberEqualizerState(settings), meter = null)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aGraphicBandMoves() {
        // the graphic EQ is off at first: its switch turns it on
        compose.onNode(hasText("Grafik ekolayzer") and hasClickAction()).performScrollTo().performClick()
        compose.onNode(hasContentDescription("1,6 kHz bandı")).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.75f) }
        compose.waitForIdle()
        val dsp = written
        assertTrue(dsp.graphicEnabled)
        assertEquals(6.0, dsp.graphicDb[12], 0.0)
    }

    @Test
    fun vlcsPresetsAndTheBypass() {
        compose.onNode(hasContentDescription("Ön ayar")).performClick()
        compose.onNodeWithText("Rock").performClick()
        compose.waitForIdle()
        assertEquals(GraphicPresets.bands("rock"), written.graphicDb)
        assertTrue(written.graphicEnabled)
        compose.onNode(hasText("Zinciri atla") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertTrue(written.bypass)
        compose.onNodeWithText("Zincir atlandı: ayarları saklanıyor, müzik olduğu gibi çalıyor.").assertExists()
    }

    @Test
    fun aBandIsAddedAndRemoved() {
        compose.onNode(hasText("Bant ekle") and hasClickAction()).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, written.parametric.size)
        assertTrue(written.parametricEnabled)
        compose.onNode(hasContentDescription("Bandı kaldır")).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(0, written.parametric.size)
    }

    @Test
    fun aProfileIsPasted() {
        val text = "Preamp: -6.4 dB\nFilter 1: ON PK Fc 105 Hz Gain -3.2 dB Q 0.70\nFilter 2: ON HSC Fc 10000 Hz Gain 2.0 dB Q 0.70\n"
        val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AutoEq", text))
        compose.onNode(hasText("Yapıştır") and hasClickAction()).performScrollTo().performClick()
        compose.waitForIdle()
        val dsp = written
        assertTrue(dsp.profileEnabled)
        assertEquals("Yapıştırılan profil", dsp.profileName)
        assertEquals(-6.4, dsp.profilePreampDb, 0.0)
        assertEquals(2, dsp.profile.size)
        assertEquals(listOf("Yapıştırılan profil"), HeadphoneProfile.listFromJson(settings.getString(NAGMEDAR_DSP_PROFILES, null)).map { it.name })
        // removed: forgotten and out of the chain
        compose.onNode(hasContentDescription("Profili kaldır")).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(false, written.profileEnabled)
        assertTrue(HeadphoneProfile.listFromJson(settings.getString(NAGMEDAR_DSP_PROFILES, null)).isEmpty())
    }
}
