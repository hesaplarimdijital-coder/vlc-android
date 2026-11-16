package app.nagmedar.ui.tags

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.tags.quality.LosslessVerdict
import app.nagmedar.ui.quality.LosslessCard
import app.nagmedar.ui.theme.NagmedarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 1.4.0: Metamatik's card in the editor: the verdict, why, the spectrum; nothing for a file there is no question about. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w420dp-h900dp-mdpi")
class LosslessCardTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aFakeSaysWhereItsSoundStopsAndWhatItWasMadeFrom() {
        compose.setContent { NagmedarTheme { LosslessCard(LosslessCheckState.Done(losslessReport())) } }
        compose.onNodeWithTag("tag-lossless-verdict", useUnmergedTree = true).assertTextEquals("Kayıplı bir dosyadan yapılmış")
        compose.onNodeWithText("Ses 16 kHz'de birden kesiliyor; yaklaşık 128 kbps'lik bir MP3 ya da AAC gibi. Dosya gerçeği kadar büyük, ama sesi kayıplı olanın sesi.").assertExists()
        compose.onNodeWithText("Kaynağı yaklaşık 128 kbps").assertExists()
        compose.onNodeWithText("FLAC · 16 bit · 44,1 kHz").assertExists()
        compose.onNodeWithTag("tag-lossless-chart").assertExists()
    }

    @Test
    fun aRealOneFillsTheBand() {
        compose.setContent { NagmedarTheme { LosslessCard(LosslessCheckState.Done(losslessReport(LosslessVerdict.Lossless))) } }
        compose.onNodeWithTag("tag-lossless-verdict", useUnmergedTree = true).assertTextEquals("Gerçek kayıpsız görünüyor")
        compose.onNodeWithText("Kayıplı sıkıştırma izi yok: ses bandın tamamını, 22,1 kHz'e kadar dolduruyor.").assertExists()
    }

    @Test
    fun whileItLooksAndWhenItCannotRead() {
        var state: LosslessCheckState by mutableStateOf(LosslessCheckState.Checking)
        compose.setContent { NagmedarTheme { LosslessCard(state) } }
        compose.onNodeWithText("Ses gerçekten kayıpsız mı, bakılıyor…").assertExists()
        state = LosslessCheckState.Failed
        compose.onNodeWithTag("tag-lossless-verdict", useUnmergedTree = true).assertTextEquals("Gerçekten kayıpsız mı diye bakmak için ses okunamadı")
        state = LosslessCheckState.None
        compose.onNodeWithTag("tag-lossless").assertDoesNotExist()
    }
}
