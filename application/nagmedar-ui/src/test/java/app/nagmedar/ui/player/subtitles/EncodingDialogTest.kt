package app.nagmedar.ui.player.subtitles

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** A Turkish SRT in Windows-1254, as the screenshots open it: detected so, nothing chosen yet. */
internal val sampleEncoding = SubtitleEncoding.Windows1254.let { encoding ->
    EncodingSample("Kuzey Işığı.tr.srt", TURKISH_SRT.toByteArray(encoding.charset), detectEncoding(TURKISH_SRT.toByteArray(encoding.charset)), chosen = null)
}

/**
 * S-702: "Subtitle encoding…" opens on what was detected; another character set shows its
 * reading of the file at once (a Western one breaks the Turkish letters); the one applied is the
 * film's, or none to have it detected.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class EncodingDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val applied = mutableListOf<SubtitleEncoding?>()
    private var closed = 0

    private fun dialog(sample: EncodingSample = sampleEncoding) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                EncodingSurface(sample, onApply = { applied += it }, onDismiss = { closed++ })
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theDetectedOneReadsTheTurkishLetters() {
        dialog()
        compose.onNodeWithText("Kuzey Işığı.tr.srt").assertExists()
        compose.onNodeWithTag("encoding-auto").assertIsSelected()
        compose.onNodeWithText("Otomatik (Türkçe · Windows-1254 algılandı)").assertExists()
        compose.onNodeWithText("Şafak sökerken gölün üstünde ışık doğdu.").assertExists()
        compose.onNodeWithText("Işığı gördün mü, Gülşen?").assertExists()
    }

    @Test
    fun anotherOneShowsItsReadingAndIsApplied() {
        dialog()
        compose.onNodeWithTag("encoding-windows-1252").performClick()
        compose.onNodeWithText("Þafak sökerken gölün üstünde ýþýk doðdu.").assertExists()
        compose.onNodeWithTag("encoding-iso-8859-9").performClick()
        compose.onNodeWithText("Şafak sökerken gölün üstünde ışık doğdu.").assertExists()
        compose.onNodeWithTag("encoding-apply").performClick()
        assertEquals(listOf(SubtitleEncoding.Iso88599), applied)
        assertEquals(1, closed)
    }

    @Test
    fun aFilmsChoiceOpensChosenAndAutomaticTakesItBack() {
        dialog(EncodingSample(sampleEncoding.name, sampleEncoding.head, sampleEncoding.detected, chosen = SubtitleEncoding.Windows1252))
        compose.onNodeWithTag("encoding-windows-1252").assertIsSelected()
        compose.onNodeWithTag("encoding-auto").performClick()
        compose.onNodeWithText("Uygula").performClick()
        assertEquals(listOf<SubtitleEncoding?>(null), applied)
    }
}
